package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsUsageAlertServiceTest {

    @Test
    void verifiedMinorAdminReceivesStationFundedSmsAlert() {
        RoleDirectoryService roleDirectory = mock(RoleDirectoryService.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationEmailService emailService = mock(NotificationEmailService.class);
        SmsGateway smsGateway = mock(SmsGateway.class);
        SmsUnitTransactionService unitService = mock(SmsUnitTransactionService.class);
        UUID memberId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        var minorAdmin = RoleDirectoryService.RoleAccountRef.builder()
            .id(memberId)
            .identifier("MINOR001")
            .phone("255712345678")
            .phoneVerifiedAt(OffsetDateTime.now())
            .build();
        when(roleDirectory.activePlatformAdminsByClaim(UserClaim.SMS_USAGE_VIEW)).thenReturn(List.of());
        when(roleDirectory.activeWorkspaceAdminsByAnyClaimInStation("SACCO-1", "ST-1", List.of(UserClaim.SMS_USAGE_VIEW)))
            .thenReturn(List.of(minorAdmin));
        when(unitService.reserveAlert("SACCO-1", "ST-1", SmsUnitStatus.DEPLETED))
            .thenReturn(new SmsUnitTransactionService.ReservationResult(true, accountId, ledgerId, null, false, null, 0));
        when(smsGateway.send(any(), any())).thenReturn(SmsSendResult.sent("provider-id"));
        SmsUsageAlertService service = new SmsUsageAlertService(
            roleDirectory, notificationRepository, emailService, smsGateway, unitService, new ObjectMapper()
        );

        service.alertStatus("SACCO-1", "ST-1", SmsUnitStatus.DEPLETED, 0);

        verify(smsGateway).send("255712345678",
            "SMS units for SACCO-1 / ST-1 are depleted. Further SMS notifications are blocked.");
        verify(unitService).completeAlert(accountId, ledgerId, SmsSendResult.sent("provider-id"));
    }

    @Test
    void unverifiedMinorAdminDoesNotConsumeAlertReserve() {
        RoleDirectoryService roleDirectory = mock(RoleDirectoryService.class);
        var minorAdmin = RoleDirectoryService.RoleAccountRef.builder()
            .id(UUID.randomUUID())
            .identifier("MINOR001")
            .phone("255712345678")
            .build();
        when(roleDirectory.activePlatformAdminsByClaim(UserClaim.SMS_USAGE_VIEW)).thenReturn(List.of());
        when(roleDirectory.activeWorkspaceAdminsByAnyClaimInStation("SACCO-1", "ST-1", List.of(UserClaim.SMS_USAGE_VIEW)))
            .thenReturn(List.of(minorAdmin));
        SmsGateway smsGateway = mock(SmsGateway.class);
        SmsUnitTransactionService unitService = mock(SmsUnitTransactionService.class);
        SmsUsageAlertService service = new SmsUsageAlertService(
            roleDirectory, mock(NotificationRepository.class), mock(NotificationEmailService.class),
            smsGateway, unitService, new ObjectMapper()
        );

        service.alertStatus("SACCO-1", "ST-1", SmsUnitStatus.LOW, 10);

        verify(unitService, never()).reserveAlert(any(), any(), any());
        verify(smsGateway, never()).send(any(), any());
    }
}
