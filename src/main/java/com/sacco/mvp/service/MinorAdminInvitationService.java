package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.MinorAdminInvitation;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.MinorAdminInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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
        log.info("Issued SACCOS admin invitation for memberId={} invitedBy={} expiresAt={}",
            member.getId(), invitedBy, invitation.getExpiresAt());
        return invitation;
    }

    @Transactional(noRollbackFor = ExpiredInvitationException.class)
    public ClaimContext loadClaimContext(String rawToken) {
        MinorAdminInvitation invitation = requirePendingInvitation(rawToken);
        Member member = memberRepository.findById(invitation.getMemberId())
            .orElseThrow(() -> new IllegalStateException("This invitation is no longer valid."));
        if (member.getStatus() != MemberStatus.INVITED) {
            throw new IllegalStateException("This account has already been activated.");
        }
        return new ClaimContext(invitation, member);
    }

    @Transactional(noRollbackFor = ExpiredInvitationException.class)
    public StationOtpDeliveryService.DeliveryReceipt requestOtp(String rawToken) {
        ClaimContext ctx = loadClaimContext(rawToken);
        Member member = ctx.member();
        return emailOtpService.issueOtp(
            member.getEmail(),
            EmailOtpPurpose.CLAIM_ACCOUNT,
            member.getId(),
            "Activate your SACCO admin account",
            "Use the code below to complete the activation of your SACCOS Admin account for "
                + member.getSaccoId() + ".",
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
        if (member.getStatus() != MemberStatus.INVITED) {
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
        member.setSignatureText(normalizedSignature);
        member.setSignatureRegisteredAt(now);
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
        memberRepository.findById(invitation.getMemberId())
            .filter(member -> member.getStatus() == MemberStatus.INVITED)
            .ifPresent(member -> {
                invitationRepository.delete(invitation);
                memberRepository.delete(member);
                log.info("Removed expired unclaimed staff invitation memberId={} staffNo={} expiredAt={} cleanupAt={}",
                    member.getId(), member.getStaffNo(), invitation.getExpiresAt(), now);
            });
    }

    private void sendInviteEmail(Member member, String rawToken, int ttlHours) {
        String link = buildClaimUrl(rawToken);
        String staffNo = member.getStaffNo() == null || member.getStaffNo().isBlank() ? member.getMemberNo() : member.getStaffNo();
        String body = "Hello " + member.getFullName() + "," + System.lineSeparator() + System.lineSeparator()
            + "A Super Admin has registered a staff account for you on SACCO " + member.getSaccoId() + "." + System.lineSeparator()
            + "Your Staff Number is " + staffNo + "." + System.lineSeparator()
            + "To activate your account, open the link below within " + ttlHours + " hours, request a one-time code, and create your password:" + System.lineSeparator()
            + link + System.lineSeparator() + System.lineSeparator()
            + "After activation, you can sign in with your staff number and password or with an email sign-in code." + System.lineSeparator() + System.lineSeparator()
            + "If you did not expect this invitation, please ignore this email.";
        notificationEmailService.sendDirectEmail(
            member.getEmail(),
            "Activate your SACCO staff account",
            body
        );
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
