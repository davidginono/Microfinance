package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StationOtpDeliveryServiceTest {
    @Mock private StationOtpSettingsService settingsService;
    @Mock private NotificationEmailService emailService;
    @Mock private SmsGateway smsGateway;
    @Mock private SmsUnitTransactionService unitService;
    @Mock private SmsUsageAlertService alertService;

    private StationOtpDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new StationOtpDeliveryService(settingsService, emailService, smsGateway, unitService, alertService);
    }

    @Test
    void emailChannelDoesNotReserveSmsUnits() {
        when(settingsService.channel("SACCO-1", "ST-1")).thenReturn(OtpDeliveryChannel.EMAIL);

        var receipt = service.deliver("SACCO-1", "ST-1", "member@example.com", "255700000001",
            EmailOtpPurpose.LOGIN, "Subject", "Intro", "123456", 10);

        assertThat(receipt.deliveredBy()).isEqualTo(OtpDeliveryChannel.EMAIL);
        verify(emailService).sendDirectEmail(eq("member@example.com"), eq("Subject"), any());
        verify(unitService, never()).reserve(any(), any(), any(), any());
    }

    @Test
    void smsChannelConsumesNormalStationUnit() {
        UUID accountId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        when(settingsService.channel("SACCO-1", "ST-1")).thenReturn(OtpDeliveryChannel.SMS);
        when(unitService.reserve(eq("SACCO-1"), eq("ST-1"), any(), eq("OTP_LOGIN")))
            .thenReturn(new SmsUnitTransactionService.ReservationResult(true, accountId, ledgerId, null, false, null, 4));
        when(smsGateway.send(eq("255700000001"), any())).thenReturn(SmsSendResult.sent("provider-1"));
        when(unitService.complete(accountId, ledgerId, SmsSendResult.sent("provider-1")))
            .thenReturn(new SmsUnitTransactionService.CompletionResult(com.sacco.mvp.domain.SmsUnitStatus.HEALTHY, 4, null));

        var receipt = service.deliver("SACCO-1", "ST-1", "member@example.com", "255700000001",
            EmailOtpPurpose.LOGIN, "Subject", "Intro", "123456", 10);

        assertThat(receipt.deliveredBy()).isEqualTo(OtpDeliveryChannel.SMS);
        verify(emailService, never()).sendDirectEmail(any(), any(), any());
        verify(smsGateway).send(eq("255700000001"), argThat(message ->
            message.contains("Intro")
                && message.contains("OTP: 123456")
                && message.contains("10 minutes")
        ));
    }

    @Test
    void fallbackUsesEmailWhenStationUnitsAreDepleted() {
        when(settingsService.channel("SACCO-1", "ST-1")).thenReturn(OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK);
        when(unitService.reserve(eq("SACCO-1"), eq("ST-1"), any(), eq("OTP_LOGIN")))
            .thenReturn(new SmsUnitTransactionService.ReservationResult(false, null, null, "SMS units depleted", false, null, 0));

        var receipt = service.deliver("SACCO-1", "ST-1", "member@example.com", "255700000001",
            EmailOtpPurpose.LOGIN, "Subject", "Intro", "123456", 10);

        assertThat(receipt.deliveredBy()).isEqualTo(OtpDeliveryChannel.EMAIL);
        verify(smsGateway, never()).send(any(), any());
        verify(emailService).sendDirectEmail(eq("member@example.com"), eq("Subject"), any());
    }

    @Test
    void smsOnlyBlocksWhenStationUnitsAreDepleted() {
        when(settingsService.channel("SACCO-1", "ST-1")).thenReturn(OtpDeliveryChannel.SMS);
        when(unitService.reserve(eq("SACCO-1"), eq("ST-1"), any(), eq("OTP_LOGIN")))
            .thenReturn(new SmsUnitTransactionService.ReservationResult(false, null, null, "SMS units depleted", false, null, 0));

        assertThatThrownBy(() -> service.deliver("SACCO-1", "ST-1", "member@example.com", "255700000001",
            EmailOtpPurpose.LOGIN, "Subject", "Intro", "123456", 10))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("depleted");

        verify(smsGateway, never()).send(any(), any());
        verify(emailService, never()).sendDirectEmail(any(), any(), any());
    }
}
