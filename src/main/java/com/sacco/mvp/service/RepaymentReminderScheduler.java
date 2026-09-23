package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.NotificationStatus;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RepaymentReminderScheduler {
    private static final List<Long> REMINDER_DAYS = List.of(30L, 14L, 7L, 3L, 1L, 0L);
    private static final int REMINDER_BATCH_SIZE = 200;

    private final LoanApplicationRepository loanApplicationRepository;
    private final NotificationRepository notificationRepository;
    private final RepaymentScheduleService repaymentScheduleService;
    private final NotificationDeliveryService notificationDeliveryService;
    private final NotificationViewService notificationViewService;
    private final ObjectMapper objectMapper;

    private final SchedulerLockService schedulerLockService;

    // Deliberately not @Transactional: delivery performs SMS/e-mail calls, so a wrapping
    // transaction would pin a pooled connection for the whole run. Each reminder is
    // independently idempotent via alreadySentToday.
    @Scheduled(fixedDelay = 3600000)
    public void sendRepaymentReminders() {
        schedulerLockService.runExclusive(SchedulerLockService.REPAYMENT_REMINDERS, this::sendRepaymentRemindersLocked);
    }

    private void sendRepaymentRemindersLocked() {
        LocalDate today = LocalDate.now();
        List<LocalDate> dueDates = REMINDER_DAYS.stream().map(today::plusDays).toList();
        Pageable page = PageRequest.of(0, REMINDER_BATCH_SIZE);
        while (true) {
            Page<LoanApplication> batch = loanApplicationRepository.findDueForReminder(
                LoanStatus.DISBURSED, dueDates, page);
            for (LoanApplication loan : batch.getContent()) {
                sendReminder(loan);
            }
            if (!batch.hasNext()) {
                return;
            }
            page = batch.nextPageable();
        }
    }

    private void sendReminder(LoanApplication loan) {
        long daysLeft = repaymentScheduleService.daysLeft(loan.getFinalDueDate());
        if (!REMINDER_DAYS.contains(daysLeft)) {
            return;
        }
        if (alreadySentToday(loan, daysLeft)) {
            return;
        }
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("loanId", loan.getId().toString());
            details.put("daysLeft", daysLeft);
            details.put("finalDueDate", loan.getFinalDueDate().toString());
            details.put("installmentAmount", loan.getInstallmentAmount());
            String payload = objectMapper.writeValueAsString(Map.of(
                "subject", "Repayment reminder",
                "message", daysLeft == 0
                    ? "Your loan repayment reaches its final due date today."
                    : "Your loan repayment final due date is in " + daysLeft + " day(s).",
                "source", "Repayment Scheduler",
                "details", details
            ));
            Notification notification = notificationRepository.save(Notification.builder()
                .id(UUID.randomUUID())
                .recipientMemberId(loan.getApplicantMemberId())
                .type("REPAYMENT_REMINDER")
                .payload(payload)
                .status(NotificationStatus.SENT)
                .createdAt(OffsetDateTime.now())
                .sentAt(OffsetDateTime.now())
                .build());
            NotificationViewService.NotificationView view = notificationViewService.toView(notification);
            notificationDeliveryService.deliverForLoan(
                loan.getSaccoId(),
                loan.getStationId(),
                notification.getId(),
                loan.getApplicantMemberId(),
                "REPAYMENT_REMINDER",
                NotificationDeliveryService.DeliveryContent.plain(view.getSubject(), view.getMessage()),
                loan.getId(),
                loan.getApplicantMemberId()
            );
        } catch (Exception ex) {
            log.warn("Unable to create repayment reminder for loan {}: {}", loan.getId(), ex.getMessage());
        }
    }

    private boolean alreadySentToday(LoanApplication loan, long daysLeft) {
        return notificationRepository.existsRepaymentReminder(
            loan.getApplicantMemberId(),
            loan.getId().toString(),
            Long.toString(daysLeft),
            LocalDate.now().atStartOfDay().atOffset(OffsetDateTime.now().getOffset())
        );
    }
}
