package com.sacco.mvp.web;

import com.sacco.mvp.domain.Position;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.NotificationViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class HeaderNotificationController {
    private final NotificationInboxService notificationInboxService;

    @GetMapping("/header/notifications")
    public HeaderNotificationPayload notifications(@AuthenticationPrincipal AppUserPrincipal principal) {
        boolean platformAdmin = principal.hasRole(Position.ADMIN);
        List<NotificationViewService.HeaderNotificationView> views = platformAdmin
            ? notificationInboxService.unreadIncidentHeaderViews(principal.getMemberId())
            : notificationInboxService.unreadHeaderViews(principal.getMemberId(), principal.getGrantedPositions());
        long count = platformAdmin
            ? views.size()
            : notificationInboxService.unreadCount(principal.getMemberId(), principal.getGrantedPositions());
        String targetUrl = notificationTargetUrl(principal);
        List<HeaderNotificationItem> items = views.stream()
            .map(view -> new HeaderNotificationItem(
                view.id(),
                view.subject(),
                view.message(),
                view.source(),
                view.createdAtLabel(),
                notificationHref(principal, targetUrl, view)
            ))
            .toList();
        return new HeaderNotificationPayload(count, targetUrl, panelSubtitle(principal), panelEmptyState(principal), items);
    }

    private String notificationHref(AppUserPrincipal principal,
                                    String targetUrl,
                                    NotificationViewService.HeaderNotificationView view) {
        if (principal.getPosition() != null && principal.getPosition().isAdminRole() && view.incidentId() != null) {
            return "/admin/notifications/" + view.id() + "/open";
        }
        return targetUrl + (targetUrl.contains("?") ? "&" : "?")
            + "highlight=" + view.id() + "#notification-" + view.id();
    }

    private String notificationTargetUrl(AppUserPrincipal principal) {
        if (principal.hasRole(Position.ADMIN)) {
            return "/admin/incidents";
        }
        if (principal.hasRole(Position.MINOR_ADMIN)) {
            return "/admin/support/replies";
        }
        return switch (principal.getPosition()) {
            case ADMIN, MINOR_ADMIN -> "/admin/dashboard";
            case MANAGER -> "/manager/notifications";
            case ACCOUNTANT -> "/accountant/notifications";
            case DISBURSEMENT_OFFICER -> "/disbursement/notifications";
            case BOARD -> "/board/notifications";
            case LOAN_OFFICER -> "/loan-officer/notifications";
            case MEMBER -> "/app/notifications";
        };
    }

    private String panelSubtitle(AppUserPrincipal principal) {
        if (principal.hasRole(Position.ADMIN)) {
            return "Latest SACCO support incidents requiring platform attention";
        }
        if (principal.getPosition() != null && principal.getPosition().isAdminRole()) {
            return "Latest member support incidents requiring admin attention";
        }
        return "Latest updates from the loan workflow";
    }

    private String panelEmptyState(AppUserPrincipal principal) {
        if (principal.hasRole(Position.ADMIN)) {
            return "No SACCO support incidents yet.";
        }
        if (principal.getPosition() != null && principal.getPosition().isAdminRole()) {
            return "No member support incidents yet.";
        }
        return "No notifications yet.";
    }

    public record HeaderNotificationPayload(
        long count,
        String targetUrl,
        String subtitle,
        String emptyState,
        List<HeaderNotificationItem> notifications
    ) {
    }

    public record HeaderNotificationItem(
        UUID id,
        String subject,
        String message,
        String source,
        String createdAtLabel,
        String href
    ) {
    }
}
