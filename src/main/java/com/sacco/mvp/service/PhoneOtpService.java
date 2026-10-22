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
    private final org.springframework.context.MessageSource messages;

    @Value("${app.auth.otp.ttl-minutes:10}")
    private int otpTtlMinutes;

    @Transactional
    public void issueClaimOtp(String phone, UUID memberId) {
        String normalizedPhone = TanzaniaPhoneNumber.normalizeRequired(phone);
        OffsetDateTime now = OffsetDateTime.now();
        var latest=tokenRepository.findTopByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNullOrderByCreatedAtDesc(normalizedPhone,EmailOtpPurpose.CLAIM_PHONE,memberId);
        if(latest.isPresent() && latest.get().getCreatedAt().isAfter(now.minusSeconds(60)))throw new IllegalArgumentException("profile.contact.wait");
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

        SmsSendResult result;
        try {
            result=smsGateway.send(normalizedPhone,messages.getMessage("profile.contact.sms",
                new Object[]{code,Math.max(1,otpTtlMinutes)},org.springframework.context.i18n.LocaleContextHolder.getLocale()));
        } catch(RuntimeException failure) {throw new IllegalArgumentException("profile.contact.sendFailed");}
        if (!result.consumesUnit()) {
            throw new IllegalArgumentException("profile.contact.sendFailed");
        }
    }

    @Transactional
    public void consumeClaimOtp(String phone, UUID memberId, String code) {
        String normalizedPhone = TanzaniaPhoneNumber.normalizeRequired(phone);
        EmailOtpToken token = tokenRepository
            .findTopByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNullOrderByCreatedAtDesc(
                normalizedPhone, EmailOtpPurpose.CLAIM_PHONE, memberId
            )
            .orElseThrow(() -> new IllegalArgumentException("profile.contact.codeInvalid"));
        OffsetDateTime now = OffsetDateTime.now();
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("profile.contact.codeInvalid");
        }
        if (code == null || !code.matches("[0-9]{6}") || !passwordEncoder.matches(code, token.getCodeHash())) {
            throw new IllegalArgumentException("profile.contact.codeInvalid");
        }
        token.setConsumedAt(now);
    }
}
