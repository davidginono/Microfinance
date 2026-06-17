package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.EmailOtpToken;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.repository.EmailOtpTokenRepository;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceTest {
    @Mock private EmailOtpTokenRepository emailOtpTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MemberRepository memberRepository;
    @Mock private StationOtpDeliveryService stationOtpDeliveryService;

    private EmailOtpService emailOtpService;

    @BeforeEach
    void setUp() {
        emailOtpService = new EmailOtpService(
            emailOtpTokenRepository,
            passwordEncoder,
            memberRepository,
            stationOtpDeliveryService
        );
        ReflectionTestUtils.setField(emailOtpService, "otpTtlMinutes", 10);
    }

    @Test
    void issueOtpWithMetadataResendsActiveCodeUntilLimit() {
        UUID memberId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(6);
        EmailOtpToken activeToken = EmailOtpToken.builder()
            .id(UUID.randomUUID())
            .email("member@example.com")
            .purpose(EmailOtpPurpose.APPLICANT_SIGNATURE)
            .memberId(memberId)
            .codeHash("hash")
            .createdAt(OffsetDateTime.now().minusMinutes(4))
            .expiresAt(expiresAt)
            .resendCount(1)
            .build();
        when(emailOtpTokenRepository.findByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNull(
            "member@example.com",
            EmailOtpPurpose.APPLICANT_SIGNATURE,
            memberId
        )).thenReturn(List.of(activeToken));
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.any())).thenReturn("new-hash");
        when(stationOtpDeliveryService.deliver(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(new StationOtpDeliveryService.DeliveryReceipt(
            OtpDeliveryChannel.EMAIL,
            "We sent an OTP code to your registered email."
        ));

        EmailOtpService.OtpIssueResult result = emailOtpService.issueOtpWithMetadata(
            "MEMBER@example.com",
            EmailOtpPurpose.APPLICANT_SIGNATURE,
            memberId,
            "Subject",
            "Intro",
            "SACCO-1",
            "ST-1",
            "+255700000001"
        );

        assertThat(result.issued()).isTrue();
        assertThat(result.resendCount()).isEqualTo(2);
        assertThat(result.maxResends()).isEqualTo(3);
        assertThat(result.resendAttemptsRemaining()).isEqualTo(1);
        assertThat(result.secondsUntilExpiry()).isPositive();
        assertThat(activeToken.getConsumedAt()).isNotNull();
        verify(emailOtpTokenRepository).save(org.mockito.ArgumentMatchers.argThat(token ->
            token.getResendCount() != null && token.getResendCount() == 2
        ));
    }

    @Test
    void issueOtpWithMetadataRejectsMoreThanThreeResends() {
        UUID memberId = UUID.randomUUID();
        EmailOtpToken activeToken = EmailOtpToken.builder()
            .id(UUID.randomUUID())
            .email("member@example.com")
            .purpose(EmailOtpPurpose.APPLICANT_SIGNATURE)
            .memberId(memberId)
            .codeHash("hash")
            .createdAt(OffsetDateTime.now().minusMinutes(1))
            .expiresAt(OffsetDateTime.now().plusMinutes(5))
            .resendCount(3)
            .build();
        when(emailOtpTokenRepository.findByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNull(
            "member@example.com",
            EmailOtpPurpose.APPLICANT_SIGNATURE,
            memberId
        )).thenReturn(List.of(activeToken));

        assertThatThrownBy(() -> emailOtpService.issueOtpWithMetadata(
            "member@example.com",
            EmailOtpPurpose.APPLICANT_SIGNATURE,
            memberId,
            "Subject",
            "Intro",
            "SACCO-1",
            "ST-1",
            "+255700000001"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OTP resend limit reached. Use the latest code or request a new one after it expires.");

        verify(emailOtpTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(stationOtpDeliveryService, never()).deliver(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt()
        );
    }

    @Test
    void issueOtpWithMetadataIssuesNewCodeWhenNoActiveCodeExists() {
        UUID memberId = UUID.randomUUID();
        when(emailOtpTokenRepository.findByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNull(
            "member@example.com",
            EmailOtpPurpose.APPLICANT_SIGNATURE,
            memberId
        )).thenReturn(List.of());
        when(passwordEncoder.encode(org.mockito.ArgumentMatchers.any())).thenReturn("hash");
        when(stationOtpDeliveryService.deliver(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt()
        )).thenReturn(new StationOtpDeliveryService.DeliveryReceipt(
            OtpDeliveryChannel.EMAIL,
            "We sent an OTP code to your registered email."
        ));

        EmailOtpService.OtpIssueResult result = emailOtpService.issueOtpWithMetadata(
            "member@example.com",
            EmailOtpPurpose.APPLICANT_SIGNATURE,
            memberId,
            "Subject",
            "Intro",
            "SACCO-1",
            "ST-1",
            "+255700000001"
        );

        assertThat(result.issued()).isTrue();
        assertThat(result.expiresAt()).isNotNull();
        assertThat(result.resendCount()).isZero();
        assertThat(result.maxResends()).isEqualTo(3);
        assertThat(result.resendAttemptsRemaining()).isEqualTo(3);
        assertThat(result.secondsUntilExpiry()).isPositive();
        verify(emailOtpTokenRepository).save(org.mockito.ArgumentMatchers.any(EmailOtpToken.class));
    }
}
