package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.NotificationViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class HeaderNotificationController {
    private final NotificationInboxService notificationInboxService;
    private final AccessControlService access;

    @GetMapping("/header/notifications")
    @PreAuthorize("@access.has(principal, 'NOTIFICATIONS_VIEW')")
    public HeaderNotificationPayload notifications(@AuthenticationPrincipal AppUserPrincipal principal) {
        boolean platformAdmin = principal.isPlatformIdentity();
        List<NotificationViewService.HeaderNotificationView> views = platformAdmin
            ? notificationInboxService.unreadIncidentHeaderViews(principal.getMemberId())
            : notificationInboxService.unreadHeaderViews(principal);
        long count = platformAdmin
            ? views.size()
            : notificationInboxService.unreadCount(principal);
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
        if (principal.isPlatformIdentity() || principal.isWorkspaceAdminScope()) {
            return "/admin/notifications/" + view.id() + "/open";
        }
        return targetUrl + (targetUrl.contains("?") ? "&" : "?")
            + "highlight=" + view.id() + "#notification-" + view.id();
    }

    private String notificationTargetUrl(AppUserPrincipal principal) {
        if (principal.isPlatformIdentity()) {
            return "/admin/incidents";
        }
        if (principal.isWorkspaceAdminScope()) {
            return "/admin/notifications";
        }
        if (access.canAccessManagerArea(principal)) {
            return "/manager/notifications";
        }
        if (access.canAccessAccountantArea(principal)) {
            return "/accountant/notifications";
        }
        if (access.canAccessDisbursementArea(principal)) {
            return "/disbursement/notifications";
        }
        if (access.canAccessChairpersonArea(principal)) {
            return "/chairperson/notifications";
        }
        if (access.canAccessCreditCommitteeArea(principal)) {
            return "/credit-committee/notifications";
        }
        if (access.canAccessBoardArea(principal)) {
            return "/board/notifications";
        }
        if (access.canAccessLoanOfficerArea(principal)) {
            return "/loan-officer/notifications";
        }
        return "/app/notifications";
    }

    private String panelSubtitle(AppUserPrincipal principal) {
        if (principal.isPlatformIdentity()) {
            return "Latest SACCO support incidents requiring platform attention";
        }
        if (principal.isWorkspaceAdminScope()) {
            return "Latest member support incidents requiring admin attention";
        }
        return "Latest updates from the loan workflow";
    }

    private String panelEmptyState(AppUserPrincipal principal) {
        if (principal.isPlatformIdentity()) {
            return "No SACCO support incidents yet.";
        }
        if (principal.isWorkspaceAdminScope()) {
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
