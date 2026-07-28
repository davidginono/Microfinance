package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.NotificationStatus;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationViewServiceTest {

    @Test
    void loanStatusEventsHaveReadableFallbackText() {
        NotificationViewService service = new NotificationViewService(
            new ObjectMapper(),
            mock(MemberRepository.class),
            new AccessControlService()
        );
        Notification notification = Notification.builder()
            .id(UUID.randomUUID())
            .recipientMemberId(UUID.randomUUID())
            .type("LOAN_STATUS_AWAITING_ACCOUNTANT")
            .payload("""
                {"recipientId":"00000000-0000-0000-0000-000000000001","details":{"status":"AWAITING_ACCOUNTANT"}}
                """)
            .status(NotificationStatus.SENT)
            .createdAt(OffsetDateTime.now())
            .build();

        NotificationViewService.NotificationView view = service.toView(notification);

        assertThat(view.getSubject()).isEqualTo("Loan Status Updated");
        assertThat(view.getMessage()).isEqualTo("Your loan application status changed to Awaiting Accountant.");
    }
}
