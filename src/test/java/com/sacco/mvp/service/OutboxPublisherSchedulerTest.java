package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.NotificationRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherSchedulerTest {

    @Test
    void queuedLegacySyncEventIsDiscardedWithoutCreatingOrDeliveringNotification() {
        ObjectMapper objectMapper = new ObjectMapper();
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
        OutboxEvent event = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("LOAN")
            .aggregateId(UUID.randomUUID())
            .eventType("PAID")
            .payload("""
                {"recipientId":"00000000-0000-0000-0000-000000000001","details":{"source":"SYNC"}}
                """)
            .status(OutboxStatus.NEW)
            .createdAt(OffsetDateTime.now())
            .build();
        when(outboxRepository.findNextPublishBatch(anyInt())).thenReturn(List.of(event));
        when(outboxRepository.saveAll(List.of(event))).thenReturn(List.of(event));

        OutboxPublisherScheduler scheduler = new OutboxPublisherScheduler(
            new OutboxPublishService(outboxRepository),
            new NotificationPublishService(notificationRepository, mock(JdbcTemplate.class)),
            objectMapper,
            mock(AdminAlertService.class),
            new NotificationViewService(
                objectMapper,
                mock(MemberRepository.class),
                new AccessControlService(),
                new ApplicationClock("Africa/Nairobi")
            ),
            deliveryService,
            mock(LoanNotificationFormatter.class)
        );

        scheduler.publish();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxRepository).save(event);
        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(deliveryService, never()).deliver(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(NotificationDeliveryService.DeliveryContent.class)
        );
    }

    @Test
    void staffReviewDuplicateIsSkippedEvenWhenPayloadMetadataDiffers() {
        ObjectMapper objectMapper = new ObjectMapper();
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
        UUID loanId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        OutboxEvent first = staffReviewEvent(loanId, recipientId, UUID.randomUUID());
        OutboxEvent duplicate = staffReviewEvent(loanId, recipientId, UUID.randomUUID());
        LoanNotificationFormatter formatter = mock(LoanNotificationFormatter.class);
        when(outboxRepository.findNextPublishBatch(anyInt())).thenReturn(List.of(first, duplicate));
        when(outboxRepository.saveAll(List.of(first, duplicate))).thenReturn(List.of(first, duplicate));
        when(notificationRepository.existsDeliveredStaffReviewDuplicate(
            recipientId,
            "LOAN_READY_FOR_MANAGER",
            loanId.toString(),
            "MANAGER"
        )).thenReturn(false, true);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(formatter.format(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));

        OutboxPublisherScheduler scheduler = new OutboxPublisherScheduler(
            new OutboxPublishService(outboxRepository),
            new NotificationPublishService(notificationRepository, mock(JdbcTemplate.class)),
            objectMapper,
            mock(AdminAlertService.class),
            new NotificationViewService(
                objectMapper,
                mock(MemberRepository.class),
                new AccessControlService(),
                new ApplicationClock("Africa/Nairobi")
            ),
            deliveryService,
            formatter
        );

        scheduler.publish();

        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(duplicate.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(deliveryService, times(1)).deliver(
            eq("SACCO-1"),
            eq("AR704"),
            any(),
            eq(recipientId),
            eq("LOAN_READY_FOR_MANAGER"),
            any(NotificationDeliveryService.DeliveryContent.class),
            eq("LOAN"),
            eq(loanId)
        );
        verify(notificationRepository, times(2)).existsDeliveredStaffReviewDuplicate(
            recipientId,
            "LOAN_READY_FOR_MANAGER",
            loanId.toString(),
            "MANAGER"
        );
    }

    @Test
    void managerReviewBranchManagerEmailIsSentOnceForDuplicateReadyForManagerEvents() {
        ObjectMapper objectMapper = new ObjectMapper();
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        LoanApplicationRepository loanRepository = mock(LoanApplicationRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        NotificationEmailService emailService = mock(NotificationEmailService.class);
        SmsGateway smsGateway = mock(SmsGateway.class);
        SmsUnitTransactionService unitService = mock(SmsUnitTransactionService.class);
        SmsUsageAlertService alertService = mock(SmsUsageAlertService.class);
        StationOtpSettingsService stationOtpSettingsService = mock(StationOtpSettingsService.class);
        UUID loanId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        OutboxEvent first = staffReviewEvent(loanId, managerId, UUID.randomUUID());
        OutboxEvent duplicate = staffReviewEvent(loanId, managerId, UUID.randomUUID());
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .applicationNumber(101901L)
            .saccoId("SACCO-1")
            .stationId("AR704")
            .applicantMemberId(applicantId)
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .amount(new BigDecimal("200000.00"))
            .status(LoanStatus.READY_FOR_MANAGER)
            .build();
        Member applicant = Member.builder()
            .id(applicantId)
            .fullName("AMANI CHARLES TEMU")
            .memberNo("001")
            .build();
        Member manager = Member.builder()
            .id(managerId)
            .email("manager@example.com")
            .build();
        when(outboxRepository.findNextPublishBatch(anyInt())).thenReturn(List.of(first, duplicate));
        when(outboxRepository.saveAll(List.of(first, duplicate))).thenReturn(List.of(first, duplicate));
        when(notificationRepository.existsDeliveredStaffReviewDuplicate(
            managerId,
            "LOAN_READY_FOR_MANAGER",
            loanId.toString(),
            "MANAGER"
        )).thenReturn(false, true);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(applicant));
        when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(stationOtpSettingsService.channel("SACCO-1", "AR704")).thenReturn(OtpDeliveryChannel.EMAIL);

        LoanNotificationFormatter formatter = new LoanNotificationFormatter(loanRepository, memberRepository);
        ReflectionTestUtils.setField(formatter, "baseUrl", "https://sacco.example");
        NotificationDeliveryService deliveryService = new NotificationDeliveryService(
            memberRepository,
            emailService,
            smsGateway,
            unitService,
            alertService,
            stationOtpSettingsService
        );
        OutboxPublisherScheduler scheduler = new OutboxPublisherScheduler(
            new OutboxPublishService(outboxRepository),
            new NotificationPublishService(notificationRepository, mock(JdbcTemplate.class)),
            objectMapper,
            mock(AdminAlertService.class),
            new NotificationViewService(
                objectMapper,
                memberRepository,
                new AccessControlService(),
                new ApplicationClock("Africa/Nairobi")
            ),
            deliveryService,
            formatter
        );

        scheduler.publish();

        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(duplicate.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(notificationRepository, times(2)).existsDeliveredStaffReviewDuplicate(
            managerId,
            "LOAN_READY_FOR_MANAGER",
            loanId.toString(),
            "MANAGER"
        );
        verify(emailService, times(1)).sendNotificationEmail(
            eq(managerId),
            eq("Loan application review required"),
            argThat(text -> text != null
                && text.contains("Loan Application Review Required By Branch Manager")
                && text.contains("Loan Application ID: 101901")
                && text.contains("Applicant: AMANI CHARLES TEMU (001)")
                && text.contains("Review Stage: Branch Manager")),
            argThat(html -> html != null
                && html.contains("Loan Application Review Required By Branch Manager")
                && html.contains("View Loan Details"))
        );
        verify(unitService, never()).reserve(any(), any(), any(), any());
        verify(smsGateway, never()).send(any(), any());
    }

    private OutboxEvent staffReviewEvent(UUID loanId, UUID recipientId, UUID actorId) {
        return OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("LOAN")
            .aggregateId(loanId)
            .eventType("LOAN_READY_FOR_MANAGER")
            .payload("""
                {
                  "recipientId":"%s",
                  "actorId":"%s",
                  "eventType":"LOAN_READY_FOR_MANAGER",
                  "saccoId":"SACCO-1",
                  "stationId":"AR704",
                  "details":{
                    "loanId":"%s",
                    "reviewStage":"MANAGER",
                    "reviewerMemberId":"%s"
                  }
                }
                """.formatted(recipientId, actorId, loanId, recipientId))
            .status(OutboxStatus.NEW)
            .createdAt(OffsetDateTime.now())
            .build();
    }
}
