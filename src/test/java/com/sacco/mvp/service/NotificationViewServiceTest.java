package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.NotificationStatus;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationViewServiceTest {

    @Test
    void loanStatusEventsHaveReadableFallbackText() {
        NotificationViewService service = service();
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

    @Test
    void obsoleteSyncNotificationsAreRemovedButValidPaidNotificationRemains() {
        NotificationViewService service = service();
        Notification syncPaid = notification("PAID", """
            {"details":{"source":"SYNC","loanId":"LN-1"}}
            """);
        Notification syncDefaulted = notification("DEFAULTED", """
            {"details":{"source":"sync","loanId":"LN-2"}}
            """);
        Notification validPaid = notification("PAID", """
            {"details":{"source":"MANAGER","loanId":"LN-3"}}
            """);

        List<NotificationViewService.NotificationView> views =
            service.toViews(List.of(syncPaid, syncDefaulted, validPaid));

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.getType()).isEqualTo("PAID");
            assertThat(view.getMessage()).isEqualTo("Your manager marked this disbursed loan as fully paid.");
            assertThat(view.getMessage()).doesNotContainIgnoringCase("sync");
        });
    }

    private NotificationViewService service() {
        return new NotificationViewService(
            new ObjectMapper(),
            mock(MemberRepository.class),
            new AccessControlService()
        );
    }

    private Notification notification(String type, String payload) {
        return Notification.builder()
            .id(UUID.randomUUID())
            .recipientMemberId(UUID.randomUUID())
            .type(type)
            .payload(payload)
            .status(NotificationStatus.SENT)
            .createdAt(OffsetDateTime.now())
            .build();
    }
}
