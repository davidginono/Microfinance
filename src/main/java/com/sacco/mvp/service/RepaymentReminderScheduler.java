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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    private final LoanApplicationRepository loanApplicationRepository;
    private final NotificationRepository notificationRepository;
    private final RepaymentScheduleService repaymentScheduleService;
    private final NotificationDeliveryService notificationDeliveryService;
    private final NotificationViewService notificationViewService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void sendRepaymentReminders() {
        List<LoanApplication> loans = loanApplicationRepository.findByStatusAndFinalDueDateIsNotNull(LoanStatus.DISBURSED);
        for (LoanApplication loan : loans) {
            long daysLeft = repaymentScheduleService.daysLeft(loan.getFinalDueDate());
            if (!REMINDER_DAYS.contains(daysLeft)) {
                continue;
            }
            if (alreadySentToday(loan, daysLeft)) {
                continue;
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
                notificationDeliveryService.deliver(
                    loan.getSaccoId(),
                    loan.getStationId(),
                    notification.getId(),
                    loan.getApplicantMemberId(),
                    "REPAYMENT_REMINDER",
                    view.getSubject(),
                    view.getMessage()
                );
            } catch (Exception ex) {
                log.warn("Unable to create repayment reminder for loan {}: {}", loan.getId(), ex.getMessage());
            }
        }
    }

    private boolean alreadySentToday(LoanApplication loan, long daysLeft) {
        LocalDate today = LocalDate.now();
        return notificationRepository.findTop100ByRecipientMemberIdAndTypeOrderByCreatedAtDesc(
                loan.getApplicantMemberId(), "REPAYMENT_REMINDER")
            .stream()
            .anyMatch(item -> item.getCreatedAt() != null
                && item.getCreatedAt().toLocalDate().isEqual(today)
                && item.getPayload() != null
                && item.getPayload().contains(loan.getId().toString())
                && item.getPayload().contains("\"daysLeft\":" + daysLeft));
    }
}
