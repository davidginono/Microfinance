package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.EmailOtpToken;
import com.sacco.mvp.repository.EmailOtpTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhoneOtpService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailOtpTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsGateway smsGateway;

    @Value("${app.auth.otp.ttl-minutes:10}")
    private int otpTtlMinutes;

    @Transactional
    public void issueClaimOtp(String phone, UUID memberId) {
        String normalizedPhone = TanzaniaPhoneNumber.normalizeRequired(phone);
        OffsetDateTime now = OffsetDateTime.now();
        tokenRepository.findByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNull(
            normalizedPhone, EmailOtpPurpose.CLAIM_PHONE, memberId
        ).forEach(token -> token.setConsumedAt(now));

        String code = String.valueOf(RANDOM.nextInt(900000) + 100000);
        tokenRepository.save(EmailOtpToken.builder()
            .id(UUID.randomUUID())
            .email(normalizedPhone)
            .purpose(EmailOtpPurpose.CLAIM_PHONE)
            .memberId(memberId)
            .codeHash(passwordEncoder.encode(code))
            .createdAt(now)
            .expiresAt(now.plusMinutes(Math.max(1, otpTtlMinutes)))
            .resendCount(0)
            .build());

        SmsSendResult result = smsGateway.send(normalizedPhone,
            "Your SACCO Minor Admin phone verification code is " + code + ". It expires in "
                + Math.max(1, otpTtlMinutes) + " minutes.");
        if (!result.consumesUnit()) {
            throw new IllegalStateException("The phone verification code could not be sent. Confirm the phone number and try again.");
        }
    }

    @Transactional
    public void consumeClaimOtp(String phone, UUID memberId, String code) {
        String normalizedPhone = TanzaniaPhoneNumber.normalizeRequired(phone);
        EmailOtpToken token = tokenRepository
            .findTopByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNullOrderByCreatedAtDesc(
                normalizedPhone, EmailOtpPurpose.CLAIM_PHONE, memberId
            )
            .orElseThrow(() -> new IllegalStateException("No active phone verification code was found. Request new codes."));
        OffsetDateTime now = OffsetDateTime.now();
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(now)) {
            throw new IllegalStateException("The phone verification code has expired. Request new codes.");
        }
        if (code == null || !passwordEncoder.matches(code.trim(), token.getCodeHash())) {
            throw new IllegalStateException("The phone verification code is invalid.");
        }
        token.setConsumedAt(now);
    }
}
