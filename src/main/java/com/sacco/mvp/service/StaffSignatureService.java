package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StaffSignatureService {
    private final MemberRepository memberRepository;
    private final NameSignatureService nameSignatureService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public boolean canRegister(AppUserPrincipal actor, Member member) {
        return actor != null && member != null && !actor.isPlatformIdentity()
            && actor.getMemberId().equals(member.getId())
            && member.getStatus() == MemberStatus.ACTIVE
            && !actor.getStaffRoles().isEmpty() && !member.getActiveStaffRolesResolved().isEmpty()
            && Objects.equals(actor.getSaccoId(), member.getSaccoId())
            && Objects.equals(actor.getStationId(), member.getStationId());
    }

    @Transactional
    public void register(AppUserPrincipal actor, String fullName, String currentPassword, boolean confirmed) {
        if (actor == null) throw new AccessDeniedException("Staff account unavailable");
        Member member = memberRepository.findForSignatureRegistration(actor.getMemberId()).orElseThrow(() -> new AccessDeniedException("Staff account unavailable"));
        requireOwner(actor, member);
        if (!confirmed) throw new IllegalArgumentException("profile.signature.confirmRequired");
        String normalizedName;
        try {
            if (fullName == null || fullName.length() > 120) throw new IllegalStateException();
            normalizedName = nameSignatureService.requireFullName(fullName, "Full name required");
            if (normalizedName.length() > 120 || !normalizedName.matches("\\p{L}[\\p{L}\\p{M}.'’-]*( \\p{L}[\\p{L}\\p{M}.'’-]*)+")) throw new IllegalStateException();
        } catch (IllegalStateException ex) { throw new IllegalArgumentException("profile.signature.invalidName"); }
        if (currentPassword == null || currentPassword.length() > 128
            || member.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw new IllegalArgumentException("profile.signature.passwordError");
        }
        if (member.getSignatureText() != null && !member.getSignatureText().isBlank()) return;
        Map<String, Object> before = state(member);
        member.setFullName(normalizedName);
        member.setSignatureText(nameSignatureService.signatureFromFullName(normalizedName));
        member.setSignatureRegisteredAt(OffsetDateTime.now());
        memberRepository.save(member);
        auditService.log("STAFF_USER", member.getId(), "STAFF_REGISTER_OWN_SIGNATURE", actor.getMemberId(), before, state(member));
    }

    private void requireOwner(AppUserPrincipal actor, Member member) {
        if (!canRegister(actor, member)) throw new AccessDeniedException("Staff signature registration unavailable");
    }

    private Map<String, Object> state(Member member) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("saccoId", member.getSaccoId()); state.put("stationId", member.getStationId());
        state.put("fullName", member.getFullName()); state.put("signatureText", member.getSignatureText());
        state.put("signatureRegisteredAt", member.getSignatureRegisteredAt());
        return state;
    }
}
