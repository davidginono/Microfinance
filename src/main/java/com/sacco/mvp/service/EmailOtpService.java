package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.EmailOtpToken;
import com.sacco.mvp.repository.EmailOtpTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOtpService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailOtpTokenRepository emailOtpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationEmailService notificationEmailService;

    @Value("${app.auth.otp.ttl-minutes:10}")
    private int otpTtlMinutes;

    @Transactional
    public void issueOtp(String email, EmailOtpPurpose purpose, UUID memberId, String subject, String introMessage) {
        String normalizedEmail = normalizeEmail(email);
        OffsetDateTime now = OffsetDateTime.now();
        try {
            activeTokens(normalizedEmail, purpose, memberId)
                .forEach(token -> token.setConsumedAt(now));

            String code = generateCode();
            EmailOtpToken token = EmailOtpToken.builder()
                .id(UUID.randomUUID())
                .email(normalizedEmail)
                .purpose(purpose)
                .memberId(memberId)
                .codeHash(passwordEncoder.encode(code))
                .createdAt(now)
                .expiresAt(now.plusMinutes(Math.max(1, otpTtlMinutes)))
                .build();
            emailOtpTokenRepository.save(token);

            notificationEmailService.sendDirectEmail(
                normalizedEmail,
                subject,
                introMessage + System.lineSeparator() + System.lineSeparator()
                    + "Your OTP code is: " + code + System.lineSeparator()
                    + "This code expires in " + Math.max(1, otpTtlMinutes) + " minutes."
            );
            log.info("Issued {} OTP for {}", purpose, normalizedEmail);
        } catch (DataAccessException ex) {
            log.error("Unable to issue {} OTP for {} due to a data access problem", purpose, normalizedEmail, ex);
            throw new IllegalStateException(
                "OTP setup needs a quick application restart before this action can continue. Restart the app once, then try again."
            );
        }
    }

    @Transactional
    public void consumeOtp(String email, EmailOtpPurpose purpose, String code) {
        EmailOtpToken token = requireValidOtp(email, purpose, null, code);
        token.setConsumedAt(OffsetDateTime.now());
    }

    @Transactional
    public void consumeOtp(String email, EmailOtpPurpose purpose, UUID memberId, String code) {
        EmailOtpToken token = requireValidOtp(email, purpose, memberId, code);
        token.setConsumedAt(OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public UUID validateOtp(String email, EmailOtpPurpose purpose, String code) {
        return requireValidOtp(email, purpose, null, code).getId();
    }

    @Transactional(readOnly = true)
    public UUID validateOtp(String email, EmailOtpPurpose purpose, UUID memberId, String code) {
        return requireValidOtp(email, purpose, memberId, code).getId();
    }

    @Transactional
    public void consumeOtpById(UUID tokenId) {
        EmailOtpToken token = emailOtpTokenRepository.findById(tokenId)
            .orElseThrow(() -> new IllegalStateException("No active OTP code was found. Request a new code."));
        token.setConsumedAt(OffsetDateTime.now());
    }

    private EmailOtpToken requireValidOtp(String email, EmailOtpPurpose purpose, UUID memberId, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = code == null ? "" : code.trim();
        EmailOtpToken token = latestToken(normalizedEmail, purpose, memberId)
            .orElseThrow(() -> new IllegalStateException("No active OTP code was found. Request a new code."));

        OffsetDateTime now = OffsetDateTime.now();
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(now)) {
            throw new IllegalStateException("That OTP code has expired. Request a new code.");
        }
        if (normalizedCode.isBlank() || !passwordEncoder.matches(normalizedCode, token.getCodeHash())) {
            throw new IllegalStateException("The OTP code is invalid.");
        }
        return token;
    }

    private java.util.List<EmailOtpToken> activeTokens(String normalizedEmail, EmailOtpPurpose purpose, UUID memberId) {
        if (memberId == null) {
            return emailOtpTokenRepository.findByEmailIgnoreCaseAndPurposeAndConsumedAtIsNull(normalizedEmail, purpose);
        }
        return emailOtpTokenRepository.findByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNull(normalizedEmail, purpose, memberId);
    }

    private java.util.Optional<EmailOtpToken> latestToken(String normalizedEmail, EmailOtpPurpose purpose, UUID memberId) {
        if (memberId == null) {
            return emailOtpTokenRepository.findTopByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(normalizedEmail, purpose);
        }
        return emailOtpTokenRepository.findTopByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNullOrderByCreatedAtDesc(normalizedEmail, purpose, memberId);
    }

    private String generateCode() {
        int value = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(value);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
