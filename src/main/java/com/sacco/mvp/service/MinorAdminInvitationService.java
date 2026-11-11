package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.MinorAdminInvitation;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.MinorAdminInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinorAdminInvitationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final MinorAdminInvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final EmailOtpService emailOtpService;
    private final NotificationEmailService notificationEmailService;
    private final PasswordEncoder passwordEncoder;
    private final NameSignatureService nameSignatureService;
    private final MessageSource messages;

    @Value("${app.auth.invitation.ttl-hours:72}")
    private int invitationTtlHours;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public MinorAdminInvitation issueInvitation(Member member, UUID invitedBy) {
        OffsetDateTime now = OffsetDateTime.now();
        int ttl = Math.max(1, invitationTtlHours);
        invitationRepository.findByMemberIdAndClaimedAtIsNullAndRevokedAtIsNull(member.getId())
            .ifPresent(existing -> {
                existing.setRevokedAt(now);
                existing.setRevokedBy(invitedBy);
                invitationRepository.save(existing);
            });

        String rawToken = generateRawToken();
        MinorAdminInvitation invitation = MinorAdminInvitation.builder()
            .id(UUID.randomUUID())
            .memberId(member.getId())
            .tokenHash(hashToken(rawToken))
            .invitedBy(invitedBy)
            .invitedAt(now)
            .expiresAt(now.plusHours(ttl))
            .build();
        invitationRepository.save(invitation);

        sendInviteEmail(member, rawToken, ttl);
        log.info("Issued staff invitation for memberId={} invitedBy={} expiresAt={}",
            member.getId(), invitedBy, invitation.getExpiresAt());
        return invitation;
    }

    @Transactional(noRollbackFor = ExpiredInvitationException.class)
    public ClaimContext loadClaimContext(String rawToken) {
        MinorAdminInvitation invitation = requirePendingInvitation(rawToken);
        Member member = memberRepository.findById(invitation.getMemberId())
            .orElseThrow(() -> new IllegalStateException("This invitation is no longer valid."));
        if (!claimable(member)) {
            throw new IllegalStateException("This account has already been activated.");
        }
        return new ClaimContext(invitation, member);
    }

    @Transactional(noRollbackFor = ExpiredInvitationException.class)
    public StationOtpDeliveryService.DeliveryReceipt requestOtp(String rawToken) {
        ClaimContext ctx = loadClaimContext(rawToken);
        Member member = ctx.member();
        String role = invitationRoleLabel(member);
        return emailOtpService.issueOtp(
            member.getEmail(),
            EmailOtpPurpose.CLAIM_ACCOUNT,
            member.getId(),
            messages.getMessage("staffInvitation.otpSubject", new Object[]{role}, LocaleContextHolder.getLocale()),
            messages.getMessage("staffInvitation.otpMessage", new Object[]{role}, LocaleContextHolder.getLocale()),
            member.getSaccoId(),
            member.getStationId(),
            member.getPhone()
        );
    }

    @Transactional(noRollbackFor = ExpiredInvitationException.class)
    public Member claimInvitation(String rawToken, String otpCode, String password) {
        MinorAdminInvitation invitation = requirePendingInvitation(rawToken);
        Member member = memberRepository.findById(invitation.getMemberId())
            .orElseThrow(() -> new IllegalStateException("This invitation is no longer valid."));
        if (!claimable(member)) {
            throw new IllegalStateException("This account has already been activated.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalStateException("Password must be at least 8 characters.");
        }
        String normalizedSignature = nameSignatureService.signatureFromFullName(member.getFullName());
        emailOtpService.consumeOtp(member.getEmail(), EmailOtpPurpose.CLAIM_ACCOUNT, otpCode);

        OffsetDateTime now = OffsetDateTime.now();
        member.setStatus(MemberStatus.ACTIVE);
        member.setStaffAccessStatus(StaffAccessStatus.ACTIVE);
        member.setStaffAccessActivatedAt(now);
        if (member.getStaffAccessAssignedAt() == null) {
            member.setStaffAccessAssignedAt(now);
        }
        member.setPhoneVerifiedAt(now);
        member.setPasswordHash(passwordEncoder.encode(password));
        if (member.getSignatureText() == null || member.getSignatureText().isBlank()) {
            member.setSignatureText(normalizedSignature);
            member.setSignatureRegisteredAt(now);
        }
        memberRepository.save(member);

        invitation.setClaimedAt(now);
        invitationRepository.save(invitation);

        log.info("Staff invitation claimed for memberId={} staffNo={}",
            member.getId(), member.getStaffNo());
        return member;
    }

    @Transactional
    public void revokeInvitation(UUID memberId, UUID revokedBy) {
        invitationRepository.findByMemberIdAndClaimedAtIsNullAndRevokedAtIsNull(memberId)
            .ifPresent(existing -> {
                OffsetDateTime now = OffsetDateTime.now();
                existing.setRevokedAt(now);
                existing.setRevokedBy(revokedBy);
                invitationRepository.save(existing);
                log.info("Minor admin invitation revoked for memberId={} revokedBy={}", memberId, revokedBy);
            });
    }

    @Transactional(readOnly = true)
    public Optional<MinorAdminInvitation> findActiveInvitation(UUID memberId) {
        return invitationRepository.findByMemberIdAndClaimedAtIsNullAndRevokedAtIsNull(memberId);
    }

    public java.util.Map<UUID, MinorAdminInvitation> findActiveInvitations(java.util.Collection<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return java.util.Map.of();
        }
        return invitationRepository.findByMemberIdInAndClaimedAtIsNullAndRevokedAtIsNull(memberIds).stream()
            .collect(java.util.stream.Collectors.toMap(
                MinorAdminInvitation::getMemberId,
                invitation -> invitation,
                (left, right) -> left));
    }

    @Transactional
    public MinorAdminInvitation resendInvitation(UUID memberId, UUID invitedBy) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalStateException("Minor admin account not found."));
        if (member.getStatus() != MemberStatus.INVITED) {
            throw new IllegalStateException("Only accounts awaiting activation can be resent.");
        }
        return issueInvitation(member, invitedBy);
    }

    @Scheduled(fixedDelayString = "${app.auth.invitation.cleanup-delay-ms:60000}",
        initialDelayString = "${app.auth.invitation.cleanup-initial-delay-ms:60000}")
    @Transactional
    public void cleanupExpiredInvitations() {
        OffsetDateTime now = OffsetDateTime.now();
        invitationRepository.findByClaimedAtIsNullAndRevokedAtIsNullAndExpiresAtBefore(now)
            .forEach(invitation -> cleanupExpiredInvitation(invitation, now));
    }

    private MinorAdminInvitation requirePendingInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalStateException("This activation link is missing its token.");
        }
        String hash = hashToken(rawToken);
        MinorAdminInvitation invitation = invitationRepository
            .findByTokenHashAndClaimedAtIsNullAndRevokedAtIsNull(hash)
            .orElseThrow(() -> new IllegalStateException("This activation link is invalid or has already been used."));
        OffsetDateTime now = OffsetDateTime.now();
        if (invitation.getExpiresAt() == null || invitation.getExpiresAt().isBefore(now)) {
            cleanupExpiredInvitation(invitation, now);
            throw new ExpiredInvitationException("This activation link has expired. Ask your Super Admin to create the staff account again.");
        }
        return invitation;
    }

    private void cleanupExpiredInvitation(MinorAdminInvitation invitation, OffsetDateTime now) {
        if (invitation == null || invitation.getMemberId() == null) {
            return;
        }
        invitation.setRevokedAt(now);
        invitationRepository.save(invitation);
        memberRepository.findById(invitation.getMemberId()).filter(this::claimable).ifPresent(member -> {
            if (!member.isMemberAccess()) member.setStatus(MemberStatus.INACTIVE);
            member.setStaffAccessStatus(StaffAccessStatus.NONE);
            member.setStaffAccessActivatedAt(null);
            memberRepository.save(member);
        });
    }

    private boolean claimable(Member member) {
        return !member.getStaffRolesResolved().isEmpty() && (member.getStatus() == MemberStatus.INVITED
            || member.isMemberAccess() && member.getStatus() == MemberStatus.ACTIVE && "OTP_ONLY_LOGIN".equals(member.getPasswordHash()));
    }

    private void sendInviteEmail(Member member, String rawToken, int ttlHours) {
        String link = buildClaimUrl(rawToken);
        String staffNo = member.getStaffNo() == null || member.getStaffNo().isBlank() ? member.getMemberNo() : member.getStaffNo();
        String role = invitationRoleLabel(member);
        String body = messages.getMessage("staffInvitation.emailBody",
            new Object[]{member.getFullName(), role, staffNo, ttlHours, link, member.getEmail()}, LocaleContextHolder.getLocale());
        notificationEmailService.sendRequiredDirectEmail(
            member.getSaccoId(),
            member.getEmail(),
            messages.getMessage("staffInvitation.activateRole", new Object[]{role}, LocaleContextHolder.getLocale()),
            body
        );
    }

    public String invitationRoleLabel(Member member) {
        String roles = Position.normalizeStaffRoles(member.getStaffRolesResolved()).stream().map(Position::userGroup).distinct()
            .map(role -> messages.getMessage("admin.role." + role.name(), null, role.getDisplayName(), LocaleContextHolder.getLocale()))
            .collect(java.util.stream.Collectors.joining(", "));
        return roles.isBlank() ? messages.getMessage("staffInvitation.staffRole", null, LocaleContextHolder.getLocale()) : roles;
    }

    private String buildClaimUrl(String rawToken) {
        String trimmedBase = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl.trim();
        if (trimmedBase.endsWith("/")) {
            trimmedBase = trimmedBase.substring(0, trimmedBase.length() - 1);
        }
        return trimmedBase + "/auth/claim?token=" + rawToken;
    }

    private String generateRawToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record ClaimContext(MinorAdminInvitation invitation, Member member) {
    }

    private static class ExpiredInvitationException extends IllegalStateException {
        private ExpiredInvitationException(String message) {
            super(message);
        }
    }
}
