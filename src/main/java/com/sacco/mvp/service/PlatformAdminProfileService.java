package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlatformAdminProfileService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public Member updateContact(UUID memberId, String email, String phone, String currentPassword) {
        Member member = memberRepository.findById(memberId)
            .filter(existing -> existing.getActiveStaffRolesResolved().contains(Position.ADMIN))
            .orElseThrow(() -> new IllegalStateException("Only the System Admin can update this contact profile."));
        if (currentPassword == null || currentPassword.isBlank()
            || !passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw new IllegalArgumentException("Enter your current password to update your contact details.");
        }

        String normalizedEmail = normalizeEmail(email);
        String normalizedPhone = normalizePhone(phone);
        if (memberRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, memberId)) {
            throw new IllegalArgumentException("That email address is already used by another account.");
        }
        if (normalizedPhone != null && memberRepository.existsByPhoneAndIdNot(normalizedPhone, memberId)) {
            throw new IllegalArgumentException("That phone number is already used by another account.");
        }

        Map<String, Object> before = contactState(member);
        if (!java.util.Objects.equals(member.getPhone(), normalizedPhone)) {
            member.setPhoneVerifiedAt(null);
        }
        member.setEmail(normalizedEmail);
        member.setPhone(normalizedPhone);
        Member saved = memberRepository.save(member);
        auditService.log("STAFF_USER", memberId, "PLATFORM_ADMIN_UPDATE_OWN_CONTACT", memberId, before, contactState(saved));
        return saved;
    }

    private String normalizeEmail(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.length() > 160 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return normalized;
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = TanzaniaPhoneNumber.normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Enter a valid phone number in the format 255XXXXXXXXX.");
        }
        return normalized;
    }

    private Map<String, Object> contactState(Member member) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("email", member.getEmail());
        state.put("phone", member.getPhone());
        return state;
    }
}
