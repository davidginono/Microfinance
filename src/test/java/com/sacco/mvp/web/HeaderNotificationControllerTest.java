package com.sacco.mvp.web;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.security.AppUserPrincipal;
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
        HeaderNotificationController controller = new HeaderNotificationController(inbox);
        AppUserPrincipal principal = principal(Position.MEMBER, true);
        UUID notificationId = UUID.randomUUID();
        NotificationViewService.HeaderNotificationView view = new NotificationViewService.HeaderNotificationView(
            notificationId, "Approved", "Your loan was approved", "Workflow", null, "2026-06-12 12:00"
        );
        when(inbox.unreadHeaderViews(principal.getMemberId(), principal.getGrantedPositions())).thenReturn(List.of(view));
        when(inbox.unreadCount(principal.getMemberId(), principal.getGrantedPositions())).thenReturn(4L);

        HeaderNotificationController.HeaderNotificationPayload payload = controller.notifications(principal);

        assertThat(payload.count()).isEqualTo(4L);
        assertThat(payload.targetUrl()).isEqualTo("/app/notifications");
        assertThat(payload.notifications()).singleElement()
            .satisfies(item -> assertThat(item.href()).isEqualTo(
                "/app/notifications?highlight=" + notificationId + "#notification-" + notificationId));
    }

    private AppUserPrincipal principal(Position position, boolean memberAccess) {
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("ST01")
            .memberNo("MEM-1")
            .fullName("Member")
            .memberAccount(memberAccess)
            .position(position)
            .status(MemberStatus.ACTIVE)
            .passwordHash("hash")
            .build();
        return new AppUserPrincipal(member, Collections.emptySet());
    }
}
