package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryServiceTest {

    @Test
    void deliversSmsWhenStationUsesSmsFallbackAndSmsSucceeds() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        NotificationEmailService emailService = mock(NotificationEmailService.class);
        SmsGateway smsGateway = mock(SmsGateway.class);
        SmsUnitTransactionService unitService = mock(SmsUnitTransactionService.class);
        SmsUsageAlertService alertService = mock(SmsUsageAlertService.class);
        StationOtpSettingsService stationOtpSettingsService = mock(StationOtpSettingsService.class);
        NotificationDeliveryService service = new NotificationDeliveryService(
            memberRepository,
            emailService,
            smsGateway,
            unitService,
            alertService,
            stationOtpSettingsService
        );
        UUID memberId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        Member member = Member.builder()
            .id(memberId)
            .phone("0673054445")
            .phoneVerifiedAt(OffsetDateTime.now())
            .build();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.channel("SACCO-1", "STN001")).thenReturn(OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK);
        when(unitService.reserve("SACCO-1", "STN001", notificationId, "LOAN_STATUS"))
            .thenReturn(new SmsUnitTransactionService.ReservationResult(true, accountId, ledgerId, null, false, null, 9));
        when(smsGateway.send("255673054445", "Loan update: Approved")).thenReturn(SmsSendResult.sent("message-id"));
        when(unitService.complete(accountId, ledgerId, SmsSendResult.sent("message-id")))
            .thenReturn(new SmsUnitTransactionService.CompletionResult(SmsUnitStatus.HEALTHY, 9, null));

        service.deliver("SACCO-1", "STN001", notificationId, memberId, "LOAN_STATUS", "Loan update", "Approved");

        verify(emailService, never()).sendNotificationEmail(memberId, "Loan update", "Approved", null);
        verify(unitService).reserve("SACCO-1", "STN001", notificationId, "LOAN_STATUS");
        verify(smsGateway).send("255673054445", "Loan update: Approved");
    }

    @Test
    void deliversSmsWhenStationUsesSmsOnlyAndSmsSucceeds() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        NotificationEmailService emailService = mock(NotificationEmailService.class);
        SmsGateway smsGateway = mock(SmsGateway.class);
        SmsUnitTransactionService unitService = mock(SmsUnitTransactionService.class);
        SmsUsageAlertService alertService = mock(SmsUsageAlertService.class);
        StationOtpSettingsService stationOtpSettingsService = mock(StationOtpSettingsService.class);
        NotificationDeliveryService service = new NotificationDeliveryService(
            memberRepository,
            emailService,
            smsGateway,
            unitService,
            alertService,
            stationOtpSettingsService
        );
        UUID memberId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        Member member = Member.builder()
            .id(memberId)
            .phone("+255 673 054 445")
            .phoneVerifiedAt(OffsetDateTime.now())
            .build();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.channel("SACCO-1", "STN001")).thenReturn(OtpDeliveryChannel.SMS);
        when(unitService.reserve("SACCO-1", "STN001", notificationId, "LOAN_STATUS"))
            .thenReturn(new SmsUnitTransactionService.ReservationResult(true, accountId, ledgerId, null, false, null, 9));
        when(smsGateway.send("255673054445", "Loan update: Approved")).thenReturn(SmsSendResult.sent("message-id"));
        when(unitService.complete(accountId, ledgerId, SmsSendResult.sent("message-id")))
            .thenReturn(new SmsUnitTransactionService.CompletionResult(SmsUnitStatus.HEALTHY, 9, null));

        service.deliver("SACCO-1", "STN001", notificationId, memberId, "LOAN_STATUS", "Loan update", "Approved");

        verify(emailService, never()).sendNotificationEmail(any(), any(), any(), any());
        verify(unitService).reserve("SACCO-1", "STN001", notificationId, "LOAN_STATUS");
        verify(smsGateway).send("255673054445", "Loan update: Approved");
    }

    @Test
    void depletedStationNeverCallsSmsGateway() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        NotificationEmailService emailService = mock(NotificationEmailService.class);
        SmsGateway smsGateway = mock(SmsGateway.class);
        SmsUnitTransactionService unitService = mock(SmsUnitTransactionService.class);
        SmsUsageAlertService alertService = mock(SmsUsageAlertService.class);
        StationOtpSettingsService stationOtpSettingsService = mock(StationOtpSettingsService.class);
        NotificationDeliveryService service = new NotificationDeliveryService(
            memberRepository,
            emailService,
            smsGateway,
            unitService,
            alertService,
            stationOtpSettingsService
        );
        UUID memberId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(Member.builder()
            .id(memberId)
            .phone("0673054445")
            .phoneVerifiedAt(OffsetDateTime.now())
            .build()));
        when(stationOtpSettingsService.channel("SACCO-1", "STN001")).thenReturn(OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK);
        when(unitService.reserve("SACCO-1", "STN001", notificationId, "LOAN_STATUS"))
            .thenReturn(new SmsUnitTransactionService.ReservationResult(false, null, null, "SMS units depleted", false, SmsUnitStatus.DEPLETED, 0));

        service.deliver("SACCO-1", "STN001", notificationId, memberId, "LOAN_STATUS", "Loan update", "Approved");

        verify(emailService).sendNotificationEmail(memberId, "Loan update", "Approved", null);
        verify(smsGateway, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(alertService).alertStatus("SACCO-1", "STN001", SmsUnitStatus.DEPLETED, 0);
    }

    @Test
    void deliveryContentUsesSmsTextAndHtmlFallbackEmail() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        NotificationEmailService emailService = mock(NotificationEmailService.class);
        SmsGateway smsGateway = mock(SmsGateway.class);
        SmsUnitTransactionService unitService = mock(SmsUnitTransactionService.class);
        SmsUsageAlertService alertService = mock(SmsUsageAlertService.class);
        StationOtpSettingsService stationOtpSettingsService = mock(StationOtpSettingsService.class);
        NotificationDeliveryService service = new NotificationDeliveryService(
            memberRepository,
            emailService,
            smsGateway,
            unitService,
            alertService,
            stationOtpSettingsService
        );
        UUID memberId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Member member = Member.builder()
            .id(memberId)
            .phone("0673054445")
            .phoneVerifiedAt(OffsetDateTime.now())
            .build();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.channel("SACCO-1", "STN001")).thenReturn(OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK);
        when(unitService.reserve("SACCO-1", "STN001", notificationId, "LOAN_STATUS"))
            .thenReturn(new SmsUnitTransactionService.ReservationResult(false, null, null, "SMS units depleted", false, SmsUnitStatus.DEPLETED, 0));
        NotificationDeliveryService.DeliveryContent content = new NotificationDeliveryService.DeliveryContent(
            "Loan update",
            "Plain email body",
            "<p>HTML email body</p>",
            "Compact SMS body"
        );

        service.deliver("SACCO-1", "STN001", notificationId, memberId, "LOAN_STATUS", content);

        verify(emailService).sendNotificationEmail(memberId, "Loan update", "Plain email body", "<p>HTML email body</p>");
        verify(smsGateway, never()).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void smsFallbackUsesEmailWithoutConsumingUnitsWhenRecipientPhoneIsUnverified() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        NotificationEmailService emailService = mock(NotificationEmailService.class);
        SmsGateway smsGateway = mock(SmsGateway.class);
        SmsUnitTransactionService unitService = mock(SmsUnitTransactionService.class);
        SmsUsageAlertService alertService = mock(SmsUsageAlertService.class);
        StationOtpSettingsService stationOtpSettingsService = mock(StationOtpSettingsService.class);
        NotificationDeliveryService service = new NotificationDeliveryService(
            memberRepository,
            emailService,
            smsGateway,
            unitService,
            alertService,
            stationOtpSettingsService
        );
        UUID memberId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Member member = Member.builder()
            .id(memberId)
            .phone("0673054445")
            .build();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.channel("SACCO-1", "STN001")).thenReturn(OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK);

        service.deliver("SACCO-1", "STN001", notificationId, memberId, "LOAN_STATUS", "Loan update", "Approved");

        verify(emailService).sendNotificationEmail(memberId, "Loan update", "Approved", null);
        verify(unitService, never()).reserve(any(), any(), any(), any());
        verify(smsGateway, never()).send(any(), any());
    }
}
