package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.EmailOtpToken;
import com.sacco.mvp.repository.EmailOtpTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneOtpServiceTest {
    @Mock private EmailOtpTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SmsGateway smsGateway;

    private PhoneOtpService phoneOtpService;

    @BeforeEach
    void setUp() {
        phoneOtpService = new PhoneOtpService(tokenRepository, passwordEncoder, smsGateway);
        ReflectionTestUtils.setField(phoneOtpService, "otpTtlMinutes", 10);
    }

    @Test
    void issueClaimOtpStoresResendCountForNotNullColumn() {
        UUID memberId = UUID.randomUUID();
        when(tokenRepository.findByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNull(
            "255746359369", EmailOtpPurpose.CLAIM_PHONE, memberId
        )).thenReturn(List.of());
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.anyString())).thenReturn("hashed-code");
        when(smsGateway.send(org.mockito.ArgumentMatchers.eq("255746359369"), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(SmsSendResult.sent("sms-1"));

        phoneOtpService.issueClaimOtp("255746359369", memberId);

        ArgumentCaptor<EmailOtpToken> tokenCaptor = ArgumentCaptor.forClass(EmailOtpToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getResendCount()).isZero();
    }
}
