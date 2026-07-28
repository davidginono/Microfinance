package com.sacco.mvp.web;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.NotificationViewService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeaderNotificationControllerTest {

    @Test
    void memberPayloadPreservesNotificationTargetAndCount() {
        NotificationInboxService inbox = mock(NotificationInboxService.class);
        HeaderNotificationController controller = new HeaderNotificationController(inbox, new AccessControlService());
        AppUserPrincipal principal = principal(Position.MEMBER, true);
        UUID notificationId = UUID.randomUUID();
        NotificationViewService.HeaderNotificationView view = new NotificationViewService.HeaderNotificationView(
            notificationId, "Approved", "Your loan was approved", "Workflow", null, "2026-06-12 12:00"
        );
        when(inbox.unreadHeaderViews(principal)).thenReturn(List.of(view));
        when(inbox.unreadCount(principal)).thenReturn(4L);

        HeaderNotificationController.HeaderNotificationPayload payload = controller.notifications(principal);

        assertThat(payload.count()).isEqualTo(4L);
        assertThat(payload.targetUrl()).isEqualTo("/app/notifications");
        assertThat(payload.notifications()).singleElement()
            .satisfies(item -> assertThat(item.href()).isEqualTo(
                "/app/notifications?highlight=" + notificationId + "#notification-" + notificationId));
    }

    @Test
    void minorAdminPayloadTargetsAdminNotificationInbox() {
        NotificationInboxService inbox = mock(NotificationInboxService.class);
        HeaderNotificationController controller = new HeaderNotificationController(inbox, new AccessControlService());
        AppUserPrincipal principal = principal(Position.MINOR_ADMIN, false);
        UUID notificationId = UUID.randomUUID();
        NotificationViewService.HeaderNotificationView view = new NotificationViewService.HeaderNotificationView(
            notificationId, "Station SMS units depleted", "SMS notifications are blocked.", "SMS Usage Control", null, "2026-06-18 15:00"
        );
        when(inbox.unreadHeaderViews(principal)).thenReturn(List.of(view));
        when(inbox.unreadCount(principal)).thenReturn(1L);

        HeaderNotificationController.HeaderNotificationPayload payload = controller.notifications(principal);

        assertThat(payload.count()).isEqualTo(1L);
        assertThat(payload.targetUrl()).isEqualTo("/admin/notifications");
        assertThat(payload.notifications()).singleElement()
            .satisfies(item -> assertThat(item.href()).isEqualTo("/admin/notifications/" + notificationId + "/open"));
    }

    private AppUserPrincipal principal(Position position, boolean memberAccess) {
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("ST01")
            .memberNo("MEM-1")
            .staffNo(position != null && position.isStaffRole() ? "STAFF-1" : null)
            .fullName("Member")
            .memberAccount(memberAccess)
            .staffAccessStatus(position != null && position.isStaffRole() ? StaffAccessStatus.ACTIVE : StaffAccessStatus.NONE)
            .position(position)
            .staffRoles(position != null && position.isStaffRole() ? new java.util.LinkedHashSet<>(List.of(position)) : new java.util.LinkedHashSet<>())
            .status(MemberStatus.ACTIVE)
            .passwordHash("hash")
            .build();
        return new AppUserPrincipal(member, memberAccess
            ? java.util.Set.of(UserClaim.NOTIFICATIONS_VIEW)
            : java.util.Set.of(UserClaim.NOTIFICATIONS_VIEW), !memberAccess);
    }
}
