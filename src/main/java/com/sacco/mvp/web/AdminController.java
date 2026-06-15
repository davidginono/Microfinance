package com.sacco.mvp.web;

import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.IncidentSeverity;
import com.sacco.mvp.domain.IncidentStatus;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanProductStatus;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.AdminService;
import com.sacco.mvp.service.DatabaseUtilizationService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.PlatformAdminService;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.service.SmsUsageManagementService;
import com.sacco.mvp.service.StationOtpSettingsService;
import com.sacco.mvp.web.form.MinorAdminRegistrationForm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMIN','MINOR_ADMIN') and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
public class AdminController {
    private final AdminService adminService;
    private final AdminScopeService adminScopeService;
    private final SaccoRegistryService saccoRegistryService;
    private final PlatformAdminService platformAdminService;
    private final DatabaseUtilizationService databaseUtilizationService;
    private final NotificationInboxService notificationInboxService;
    private final SmsUsageManagementService smsUsageManagementService;
    private final StationOtpSettingsService stationOtpSettingsService;

    @GetMapping("/scope/select")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String selectScope(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam(required = false) String next,
                              Model model) {
        if (principal != null && principal.hasRole(Position.MINOR_ADMIN) && !principal.hasRole(Position.ADMIN)) {
            return "redirect:" + normalizeAdminNextPath(next);
        }
        model.addAttribute("scopeSelection", adminScopeService.currentScope(principal));
        model.addAttribute("nextAdminPath", normalizeAdminNextPath(next));
        return "admin/scope-select";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        if (principal != null && principal.hasRole(Position.ADMIN)) {
            model.addAttribute("platformDashboard", platformAdminService.dashboard());
            return "admin/platform-dashboard";
        }
        model.addAttribute("dashboard", adminService.dashboard(
            adminScopeService.currentSaccoId(principal),
            adminScopeService.currentStationId(principal),
            principal.getMemberId()
        ));
        return "admin/dashboard";
    }

    @GetMapping("/sms-usage")
    @PreAuthorize("hasAnyRole('ADMIN','MINOR_ADMIN')")
    public String smsUsage(@AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false) String saccoId,
                           @RequestParam(required = false) String stationId,
                           @RequestParam(required = false) SmsUnitStatus status,
                           @RequestParam(required = false) UUID accountId,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "0") int historyPage,
                           Model model) {
        boolean superAdmin = principal != null && principal.hasRole(Position.ADMIN);
        int safePage = Math.max(0, page);
        int safeHistoryPage = Math.max(0, historyPage);
        model.addAttribute("superAdmin", superAdmin);
        model.addAttribute("smsStatuses", SmsUnitStatus.values());
        model.addAttribute("smsSettings", smsUsageManagementService.settings());

        if (superAdmin) {
            var registeredSaccos = saccoRegistryService.listRegisteredSaccos();
            String selectedSaccoId = normalizeTextParam(saccoId);
            String selectedStationId = normalizeTextParam(stationId);
            String requestedSaccoId = selectedSaccoId;
            var selectedSacco = registeredSaccos.stream()
                .filter(sacco -> sacco.getSaccoId().equals(requestedSaccoId))
                .findFirst()
                .orElse(null);
            if (selectedSacco == null) {
                selectedSaccoId = "";
                selectedStationId = "";
            } else if (!selectedSacco.getStationIds().contains(selectedStationId)) {
                selectedStationId = "";
            }
            var accounts = smsUsageManagementService.accounts(
                selectedSaccoId.isBlank() ? null : selectedSaccoId,
                selectedStationId.isBlank() ? null : selectedStationId,
                status,
                PageRequest.of(safePage, 25)
            );
            var selectedAccount = accountId == null ? null : smsUsageManagementService.account(accountId);
            model.addAttribute("accounts", accounts);
            model.addAttribute("selectedAccount", selectedAccount);
            model.addAttribute("selectedOtpDeliveryChannel", selectedAccount == null ? null
                : stationOtpSettingsService.channel(selectedAccount.getSaccoId(), selectedAccount.getStationId()));
            model.addAttribute("usageHistory", selectedAccount == null
                ? Page.empty(PageRequest.of(safeHistoryPage, 25))
                : smsUsageManagementService.history(selectedAccount.getId(), PageRequest.of(safeHistoryPage, 25)));
            model.addAttribute("registeredSaccos", registeredSaccos);
            model.addAttribute("selectedSaccoId", selectedSaccoId);
            model.addAttribute("selectedStationId", selectedStationId);
            model.addAttribute("selectedSmsStatus", status == null ? "" : status.name());
        } else {
            String scopedSaccoId = adminScopeService.currentSaccoId(principal);
            String scopedStationId = adminScopeService.currentStationId(principal);
            var selectedAccount = smsUsageManagementService.account(scopedSaccoId, scopedStationId);
            model.addAttribute("selectedAccount", selectedAccount);
            model.addAttribute("selectedOtpDeliveryChannel", stationOtpSettingsService.channel(scopedSaccoId, scopedStationId));
            model.addAttribute("usageHistory", smsUsageManagementService.history(
                scopedSaccoId,
                scopedStationId,
                PageRequest.of(safeHistoryPage, 25)
            ));
        }
        return "admin/sms-usage";
    }

    @PostMapping("/sms-usage/allocations")
    @PreAuthorize("@authz.platformAdminIdentity(principal)")
    public String allocateSmsUnits(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @RequestParam String saccoId,
                                   @RequestParam String stationId,
                                   @RequestParam long units,
                                   @RequestParam String note,
                                   RedirectAttributes ra) {
        try {
            smsUsageManagementService.allocate(saccoId, stationId, units, principal.getMemberId(), note);
            ra.addFlashAttribute("message", units + " SMS unit(s) added to " + saccoId + " / " + stationId + ".");
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/sms-usage?saccoId=" + UriUtils.encode(saccoId, StandardCharsets.UTF_8)
            + "&stationId=" + UriUtils.encode(stationId, StandardCharsets.UTF_8);
    }

    @PostMapping("/sms-usage/thresholds")
    @PreAuthorize("@authz.platformAdminIdentity(principal)")
    public String updateSmsThresholds(@AuthenticationPrincipal AppUserPrincipal principal,
                                      @RequestParam int lowPercent,
                                      @RequestParam int criticalPercent,
                                      RedirectAttributes ra) {
        try {
            smsUsageManagementService.updateThresholds(lowPercent, criticalPercent, principal.getMemberId());
            ra.addFlashAttribute("message", "SMS usage warning thresholds updated.");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/sms-usage";
    }

    @GetMapping(value = "/dashboard/database-utilization", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    @ResponseBody
    public DatabaseUtilizationService.DatabaseUtilizationPayload databaseUtilization(@AuthenticationPrincipal AppUserPrincipal principal) {
        return databaseUtilizationService.snapshot();
    }

    @GetMapping("/messages")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String messages(@AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false) IncidentStatus status,
                           @RequestParam(required = false) IncidentSeverity severity,
                           @RequestParam(required = false) UUID highlight,
                           Model model) {
        String stationId = adminScopeService.currentStationId(principal);
        java.util.List<com.sacco.mvp.service.NotificationViewService.NotificationView> allMessages =
            adminService.adminMessages(principal.getMemberId());
        Map<UUID, com.sacco.mvp.domain.AdminIncident> messageIncidents = adminService.adminMessageIncidentMap(allMessages, stationId);
        java.util.List<com.sacco.mvp.service.NotificationViewService.NotificationView> filteredMessages = allMessages.stream()
            .filter(item -> {
                com.sacco.mvp.domain.AdminIncident incident = item.getIncidentId() == null ? null : messageIncidents.get(item.getIncidentId());
                if (incident == null) {
                    return false;
                }
                return (status == null || incident.getStatus() == status)
                    && (severity == null || incident.getSeverity() == severity);
            })
            .toList();
        model.addAttribute("messages", filteredMessages);
        model.addAttribute("messageIncidents", messageIncidents);
        model.addAttribute("members", adminService.activeMembers(adminScopeService.currentSaccoId(principal), stationId));
        model.addAttribute("highlightNotificationId", highlight);
        model.addAttribute("incidentStatuses", IncidentStatus.values());
        model.addAttribute("incidentSeverities", IncidentSeverity.values());
        model.addAttribute("selectedStatus", status == null ? "" : status.name());
        model.addAttribute("selectedSeverity", severity == null ? "" : severity.name());
        return "admin/messages";
    }

    @GetMapping("/messages/{id}/open")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String openMessage(@PathVariable UUID id,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(), principal.getPosition(), "/admin/messages");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/messages";
        }
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("hasAnyRole('ADMIN','MINOR_ADMIN')")
    public String openAdminNotification(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUserPrincipal principal,
                                        RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(), principal.getPosition(),
                principal.hasRole(Position.ADMIN) ? "/admin/incidents" : "/admin/support/replies");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return principal != null && principal.hasRole(Position.ADMIN)
                ? "redirect:/admin/incidents"
                : "redirect:/admin/support/replies";
        }
    }

    @PostMapping("/messages/mark-all-read")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String markAllMessagesRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                      RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        if (updated > 0) {
            ra.addFlashAttribute("message", "All admin messages have been marked as read.");
        } else {
            ra.addFlashAttribute("message", "There were no unread admin messages.");
        }
        return "redirect:/admin/incidents";
    }

    @PostMapping("/notifications/mark-all-read")
    @PreAuthorize("hasAnyRole('ADMIN','MINOR_ADMIN')")
    public String markAllAdminNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                                RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        ra.addFlashAttribute("message", updated > 0
            ? "All notifications have been marked as read."
            : "There were no unread notifications.");
        return principal != null && principal.hasRole(Position.ADMIN)
            ? "redirect:/admin/incidents"
            : "redirect:/admin/support/replies";
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAnyRole('ADMIN','MINOR_ADMIN')")
    public String incidents(@AuthenticationPrincipal AppUserPrincipal principal,
                            @RequestParam(required = false) IncidentStatus status,
                            @RequestParam(required = false) IncidentSeverity severity,
                            @RequestParam(required = false) String saccoId,
                            @RequestParam(required = false) String stationId,
                            Model model) {
        boolean superAdmin = principal != null && principal.hasRole(Position.ADMIN);
        String scopedSaccoId = superAdmin ? normalizeTextParam(saccoId) : adminScopeService.currentSaccoId(principal);
        String scopedStationId = superAdmin ? normalizeTextParam(stationId) : adminScopeService.currentStationId(principal);
        model.addAttribute("incidents", superAdmin
            ? adminService.platformSupportIncidents(
                scopedSaccoId.isBlank() ? null : scopedSaccoId,
                scopedStationId.isBlank() ? null : scopedStationId,
                status,
                severity
            )
            : adminService.incidents(
                scopedSaccoId.isBlank() ? null : scopedSaccoId,
                scopedStationId.isBlank() ? null : scopedStationId,
                status,
                severity
            ));
        addAdminScopeFilters(model, scopedSaccoId, scopedStationId, superAdmin);
        if (superAdmin) {
            model.addAttribute("minorAdmins", adminService.minorAdmins());
        }
        model.addAttribute("incidentStatuses", IncidentStatus.values());
        model.addAttribute("incidentSeverities", IncidentSeverity.values());
        model.addAttribute("selectedStatus", status == null ? "" : status.name());
        model.addAttribute("selectedSeverity", severity == null ? "" : severity.name());
        return "admin/incidents";
    }

    @GetMapping("/incidents/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MINOR_ADMIN')")
    public String incidentDetail(@PathVariable UUID id,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false) String saccoId,
                                 @RequestParam(required = false) String stationId,
                                 Model model) {
        boolean superAdmin = principal != null && principal.hasRole(Position.ADMIN);
        com.sacco.mvp.domain.AdminIncident incident = superAdmin
            ? adminService.platformSupportIncident(
                normalizeTextParam(saccoId).isBlank() ? null : normalizeTextParam(saccoId),
                normalizeTextParam(stationId).isBlank() ? null : normalizeTextParam(stationId),
                id
            )
            : adminService.incident(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                id
            );
        if (superAdmin) {
            adminService.markPlatformSupportIncidentRead(id, principal.getMemberId());
        } else {
            adminService.markMemberSupportIncidentRead(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                id,
                principal.getMemberId()
            );
        }
        model.addAttribute("incident", incident);
        model.addAttribute("selectedSaccoId", superAdmin ? normalizeTextParam(saccoId) : "");
        model.addAttribute("selectedStationId", superAdmin ? normalizeTextParam(stationId) : "");
        model.addAttribute("reporterMembers", superAdmin ? java.util.List.of() : java.util.List.of());
        model.addAttribute("incidentStatuses", IncidentStatus.values());
        model.addAttribute("incidentSeverities", IncidentSeverity.values());
        return "admin/incident-detail";
    }

    @PostMapping("/incidents/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MINOR_ADMIN') and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateIncident(@PathVariable UUID id,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam IncidentSeverity severity,
                                 @RequestParam IncidentStatus status,
                                 @RequestParam(required = false) String resolutionNote,
                                 RedirectAttributes ra) {
        boolean superAdmin = principal != null && principal.hasRole(Position.ADMIN);
        if (superAdmin) {
            adminService.platformSupportIncident(null, null, id);
        }
        adminService.updateIncident(
            superAdmin ? null : adminScopeService.currentSaccoId(principal),
            superAdmin ? null : adminScopeService.currentStationId(principal),
            principal.getMemberId(),
            id,
            severity,
            status,
            resolutionNote
        );
        ra.addFlashAttribute("message", "Incident updated.");
        return "redirect:/admin/incidents/" + id;
    }

    @PostMapping("/incidents/{id}/reply")
    @PreAuthorize("hasAnyRole('ADMIN','MINOR_ADMIN') and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String replyToSupportReporter(@PathVariable UUID id,
                                         @AuthenticationPrincipal AppUserPrincipal principal,
                                         @RequestParam String subject,
                                         @RequestParam String message,
                                         RedirectAttributes ra) {
        boolean superAdmin = principal != null && principal.hasRole(Position.ADMIN);
        if (superAdmin) {
            adminService.replyToPlatformSupportReporter(id, principal.getMemberId(), subject, message);
            ra.addFlashAttribute("message", "Reply sent to the SACCO admin.");
        } else {
            adminService.replyToMemberSupportReporter(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                id,
                principal.getMemberId(),
                subject,
                message
            );
            ra.addFlashAttribute("message", "Reply sent to the member.");
        }
        return "redirect:/admin/incidents/" + id;
    }

    @PostMapping("/incidents/broadcast-minor-admins")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String broadcastToMinorAdmins(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @RequestParam String subject,
                                         @RequestParam String message,
                                         RedirectAttributes ra) {
        adminService.broadcastToMinorAdmins(principal.getMemberId(), subject, message);
        ra.addFlashAttribute("message", "Broadcast sent to minor admins.");
        return "redirect:/admin/incidents";
    }

    @PostMapping("/incidents/reply-minor-admin")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String replyToMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam UUID memberId,
                                    @RequestParam String subject,
                                    @RequestParam String message,
                                    RedirectAttributes ra) {
        adminService.replyToMinorAdmin(principal.getMemberId(), memberId, subject, message);
        ra.addFlashAttribute("message", "Reply sent to the minor admin.");
        return "redirect:/admin/incidents";
    }

    @GetMapping("/support")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String adminSupport() {
        return "admin/support";
    }

    @GetMapping("/support/archive")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String adminSupportArchive(@AuthenticationPrincipal AppUserPrincipal principal,
                                      Model model) {
        model.addAttribute("supportArchive", adminService.platformSupportArchive(principal.getMemberId()));
        return "admin/support-archive";
    }

    @GetMapping("/support/replies")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String adminSupportReplies(@AuthenticationPrincipal AppUserPrincipal principal,
                                      @RequestParam(required = false) UUID highlight,
                                      Model model) {
        model.addAttribute("replies", notificationInboxService.allViewsByType(principal.getMemberId(), "ADMIN_REPLY"));
        model.addAttribute("broadcasts", notificationInboxService.allViewsByType(principal.getMemberId(), "ADMIN_BROADCAST"));
        model.addAttribute("highlightNotificationId", highlight);
        return "admin/support-replies";
    }

    @GetMapping("/support/replies/{id}/open")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String openAdminSupportReply(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUserPrincipal principal,
                                        RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(), principal.getPosition(), "/admin/support/replies");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/support/replies";
        }
    }

    @PostMapping("/support/replies/mark-all-read")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String markAllAdminSupportRepliesRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                                 RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsReadByTypes(
            principal.getMemberId(),
            List.of("ADMIN_REPLY", "ADMIN_BROADCAST")
        );
        ra.addFlashAttribute("message", updated > 0
            ? "All support replies have been marked as read."
            : "There were no unread support replies.");
        return "redirect:/admin/support/replies";
    }

    @PostMapping("/support")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String sendAdminSupport(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @RequestParam String subject,
                                   @RequestParam String message,
                                   RedirectAttributes ra) {
        adminService.submitPlatformSupport(
            adminScopeService.currentSaccoId(principal),
            adminScopeService.currentStationId(principal),
            principal.getMemberId(),
            subject,
            message
        );
        ra.addFlashAttribute("message", "Your message has been sent to the platform admin.");
        return "redirect:/admin/support";
    }

    @PostMapping("/messages/reply")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String reply(@AuthenticationPrincipal AppUserPrincipal principal,
                        @RequestParam UUID memberId,
                        @RequestParam String subject,
                        @RequestParam String message,
                        RedirectAttributes ra) {
        adminService.replyToMember(
            adminScopeService.currentSaccoId(principal),
            adminScopeService.currentStationId(principal),
            principal.getMemberId(),
            memberId,
            subject,
            message
        );
        ra.addFlashAttribute("message", "Reply sent to member notifications.");
        return "redirect:/admin/messages";
    }

    @PostMapping("/messages/broadcast")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String broadcast(@AuthenticationPrincipal AppUserPrincipal principal,
                            @RequestParam String subject,
                            @RequestParam String message,
                            RedirectAttributes ra) {
        adminService.broadcast(
            adminScopeService.currentSaccoId(principal),
            adminScopeService.currentStationId(principal),
            principal.getMemberId(),
            subject,
            message
        );
        ra.addFlashAttribute("message", "Broadcast sent to active SACCO members.");
        return "redirect:/admin/messages";
    }

    @GetMapping("/users")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String users(@AuthenticationPrincipal AppUserPrincipal principal,
                        @RequestParam(required = false) String query,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "25") int size,
                        Model model) {
        Page<AdminService.UserAccessView> usersPage = adminService.usersPage(
            adminScopeService.currentSaccoId(principal),
            adminScopeService.currentStationId(principal),
            query,
            page,
            size
        );
        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("usersPage", usersPage);
        model.addAttribute("selectedUserQuery", query == null ? "" : query.trim());
        model.addAttribute("selectedPageSize", usersPage.getSize());
        model.addAttribute("usersPaginationQuery", buildUsersPaginationQuery(query, usersPage.getSize()));
        model.addAttribute("staffPositions", principal != null && principal.hasRole(Position.ADMIN)
            ? Position.staffAssignableRoles()
            : Position.staffAssignableRoles().stream().filter(position -> position != Position.ADMIN).toList());
        model.addAttribute("availableClaims", UserClaim.values());
        model.addAttribute("statuses", MemberStatus.values());
        return "admin/users";
    }

    @PostMapping("/users")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String createUser(@AuthenticationPrincipal AppUserPrincipal principal,
                             @RequestParam String memberNo,
                             @RequestParam String fullName,
                             @RequestParam String email,
                             @RequestParam(required = false) String phone,
                             @RequestParam(name = "positions", required = false) java.util.List<Position> positions,
                             RedirectAttributes ra) {
        adminService.createUser(
            adminScopeService.currentSaccoId(principal),
            adminScopeService.currentStationId(principal),
            principal.getMemberId(),
            principal.getGrantedPositions(),
            memberNo,
            fullName,
            email,
            phone,
            positions
        );
        ra.addFlashAttribute("message", "User created.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateUser(@PathVariable UUID id,
                             @AuthenticationPrincipal AppUserPrincipal principal,
                             @RequestParam(name = "positions", required = false) java.util.List<Position> positions,
                             @RequestParam(name = "claims", required = false) java.util.List<UserClaim> claims,
                             @RequestParam MemberStatus status,
                             RedirectAttributes ra) {
        adminService.updateUser(
            adminScopeService.currentSaccoId(principal),
            adminScopeService.currentStationId(principal),
            principal.getMemberId(),
            principal.getGrantedPositions(),
            id,
            positions,
            status,
            claims
        );
        ra.addFlashAttribute("message", "User updated.");
        return "redirect:/admin/users";
    }

    @GetMapping({"/loan-products", "/settings-controls"})
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String loanProducts(@AuthenticationPrincipal AppUserPrincipal principal,
                               @RequestParam(required = false, defaultValue = "loan") String section,
                               @RequestParam(required = false) String modal,
                               Model model) {
        String saccoId = adminScopeService.currentSaccoId(principal);
        model.addAttribute("products", adminService.loanProducts(saccoId));
        model.addAttribute("productVersionsByProductId", adminService.loanProductVersions(saccoId));
        model.addAttribute("loanProductsVersions", adminService.loanProductsVersionHistory(saccoId));
        model.addAttribute("customizedProductExists", adminService.customizedLoanProductExists(saccoId));
        var settings = adminService.settings(saccoId);
        var stationPolicy = adminService.stationQualificationPolicy(saccoId, adminScopeService.currentStationId(principal)).orElse(null);
        model.addAttribute("settings", settings);
        model.addAttribute(
            "stationOtpDeliveryChannel",
            stationOtpSettingsService.channel(saccoId, adminScopeService.currentStationId(principal))
        );
        model.addAttribute("otpDeliveryChannels", OtpDeliveryChannel.values());
        model.addAttribute("currentStationPolicy", stationPolicy);
        model.addAttribute("policyStationId", adminScopeService.currentStationId(principal));
        model.addAttribute("policyApplicantMaxDefaultedLoans", stationPolicy == null ? settings.getApplicantMaxDefaultedLoans() : stationPolicy.getApplicantMaxDefaultedLoans());
        model.addAttribute("policyApplicantMaxForfeitedLoans", stationPolicy == null ? settings.getApplicantMaxForfeitedLoans() : stationPolicy.getApplicantMaxForfeitedLoans());
        model.addAttribute("policyApplicantForfeitedLookbackDays", stationPolicy == null ? settings.getApplicantForfeitedLookbackDays() : stationPolicy.getApplicantForfeitedLookbackDays());
        Integer applicantForfeitedWaitDays = stationPolicy == null ? settings.getApplicantForfeitedWaitDays() : stationPolicy.getApplicantForfeitedWaitDays();
        Integer applicantForfeitedLookbackDays = stationPolicy == null ? settings.getApplicantForfeitedLookbackDays() : stationPolicy.getApplicantForfeitedLookbackDays();
        model.addAttribute("policyApplicantForfeitedWaitDays", applicantForfeitedWaitDays == null ? applicantForfeitedLookbackDays : applicantForfeitedWaitDays);
        Boolean guarantorWithActiveLoanAllowed = stationPolicy == null ? settings.getGuarantorWithActiveLoanAllowed() : stationPolicy.getGuarantorWithActiveLoanAllowed();
        model.addAttribute("policyGuarantorWithActiveLoanAllowed", guarantorWithActiveLoanAllowed == null || guarantorWithActiveLoanAllowed);
        model.addAttribute("policyGuarantorMaxGuaranteedLoanAmount", stationPolicy == null ? settings.getGuarantorMaxGuaranteedLoanAmount() : stationPolicy.getGuarantorMaxGuaranteedLoanAmount());
        model.addAttribute("policyGuarantorMaxDefaultedLoans", stationPolicy == null ? settings.getGuarantorMaxDefaultedLoans() : stationPolicy.getGuarantorMaxDefaultedLoans());
        model.addAttribute("activeBoardMemberCount", adminService.activeBoardMemberCount(saccoId));
        model.addAttribute("activeLoanOfficerCount", adminService.activeLoanOfficerCount(saccoId));
        model.addAttribute("activeAccountantCount", adminService.activeAccountantCount(saccoId));
        model.addAttribute("activeDisbursementOfficerCount", adminService.activeDisbursementOfficerCount(saccoId));
        model.addAttribute("activeDisbursementClaimHolderCount", adminService.activeDisbursementClaimHolderCount(saccoId));
        model.addAttribute("approvalFlowStageLabels", settings.resolvedApprovalFlow().stream()
            .map(ApprovalWorkflowStage::getDisplayLabel)
            .toList());
        model.addAttribute("settingsSection", normalizeSettingsSection(section));
        model.addAttribute("openProductModalKey", normalizeLoanSettingsModalKey(modal));
        model.addAttribute("suppressToastMessages", normalizeLoanSettingsModalKey(modal) != null);
        return "admin/settings-controls";
    }

    @PostMapping({"/loan-products/{id}", "/settings-controls/{id}"})
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateLoanProduct(@PathVariable UUID id,
                                    @AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam(required = false) String productCode,
                                    @RequestParam(required = false) String productName,
                                    @RequestParam(required = false) String productDescription,
                                    @RequestParam Integer displayOrder,
                                    @RequestParam BigDecimal minimumAmount,
                                    @RequestParam(required = false) BigDecimal maximumAmount,
                                    @RequestParam Integer guarantorsRequired,
                                    @RequestParam BigDecimal maxLoanSavingsPercent,
                                    @RequestParam BigDecimal insurancePercent,
                                    @RequestParam BigDecimal annualInterestPercent,
                                    @RequestParam(defaultValue = "FLAT_RATE") InterestMethod interestMethod,
                                    @RequestParam Integer minRepaymentMonths,
                                    @RequestParam Integer maxRepaymentMonths,
                                    @RequestParam(defaultValue = "false") boolean allowApplicationWithActiveLoan,
                                    @RequestParam(defaultValue = "false") boolean freshFinancialDataRequired,
                                    @RequestParam(defaultValue = "false") boolean managerReviewRequired,
                                    @RequestParam(defaultValue = "false") boolean loanOfficerReviewRequired,
                                    @RequestParam(defaultValue = "MANAGER") ApprovalWorkflowStage workflowStartStage,
                                    @RequestParam(defaultValue = "false") boolean committeeReviewRequired,
                                    @RequestParam(required = false) Integer committeePriority,
                                    @RequestParam(required = false) Integer committeeMinimumVotes,
                                    @RequestParam(required = false) Integer committeeApprovalThreshold,
                                    @RequestParam(defaultValue = "true") boolean accountantReviewRequired,
                                    @RequestParam(required = false) Integer accountantPriority,
                                    @RequestParam(defaultValue = "true") boolean disbursementOfficerRequired,
                                    @RequestParam(defaultValue = "1") Integer managerPriority,
                                    @RequestParam(defaultValue = "2") Integer loanOfficerPriority,
                                    @RequestParam(defaultValue = "false") boolean guarantorMinSavingsCheckRequired,
                                    @RequestParam(required = false) BigDecimal guarantorMinimumSavings,
                                    @RequestParam(defaultValue = "ACTIVE") LoanProductStatus productStatus,
                                    @RequestParam(required = false) String modalKey,
                                    RedirectAttributes ra) {
        String resolvedModalKey = normalizeLoanSettingsModalKey(modalKey) == null ? "product-" + id : normalizeLoanSettingsModalKey(modalKey);
        try {
            BigDecimal maxLoanSavingsRatio = percentToRatio(maxLoanSavingsPercent);
            BigDecimal insuranceRate = percentToRatio(insurancePercent);
            BigDecimal annualRate = percentToRatio(annualInterestPercent);
            ApprovalWorkflowStage resolvedWorkflowStartStage = resolveLoanOfficerWorkflowStartStage(
                managerReviewRequired,
                loanOfficerReviewRequired,
                workflowStartStage,
                managerPriority,
                loanOfficerPriority
            );
            adminService.updateLoanProduct(adminScopeService.currentSaccoId(principal), principal.getMemberId(), id,
                productCode, productName, productDescription, displayOrder, minimumAmount, maximumAmount, guarantorsRequired,
                maxLoanSavingsRatio, insuranceRate, annualRate, interestMethod, minRepaymentMonths, maxRepaymentMonths,
                allowApplicationWithActiveLoan, freshFinancialDataRequired, managerReviewRequired, loanOfficerReviewRequired,
                resolvedWorkflowStartStage, managerPriority, loanOfficerPriority, committeeReviewRequired, committeePriority, committeeMinimumVotes,
                committeeApprovalThreshold, accountantReviewRequired, accountantPriority, disbursementOfficerRequired,
                guarantorMinSavingsCheckRequired, guarantorMinimumSavings, productStatus);
            ra.addFlashAttribute("message", "Loan product updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            attachLoanSettingsValidationFeedback(ra, ex.getMessage());
        }
        return loanSettingsRedirect(resolvedModalKey);
    }

    @PostMapping({"/loan-products/customized-product", "/settings-controls/customized-product"})
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String createCustomizedLoanProduct(@AuthenticationPrincipal AppUserPrincipal principal,
                                              @RequestParam(required = false) String productCode,
                                              @RequestParam(required = false) String productName,
                                              @RequestParam(required = false) String productDescription,
                                              @RequestParam Integer displayOrder,
                                              @RequestParam BigDecimal minimumAmount,
                                              @RequestParam(required = false) BigDecimal maximumAmount,
                                              @RequestParam Integer guarantorsRequired,
                                              @RequestParam BigDecimal maxLoanSavingsPercent,
                                              @RequestParam BigDecimal insurancePercent,
                                              @RequestParam BigDecimal annualInterestPercent,
                                              @RequestParam(defaultValue = "FLAT_RATE") InterestMethod interestMethod,
                                              @RequestParam Integer minRepaymentMonths,
                                              @RequestParam Integer maxRepaymentMonths,
                                              @RequestParam(defaultValue = "false") boolean allowApplicationWithActiveLoan,
                                              @RequestParam(defaultValue = "false") boolean freshFinancialDataRequired,
                                              @RequestParam(defaultValue = "false") boolean managerReviewRequired,
                                              @RequestParam(defaultValue = "false") boolean loanOfficerReviewRequired,
                                              @RequestParam(defaultValue = "MANAGER") ApprovalWorkflowStage workflowStartStage,
                                              @RequestParam(defaultValue = "false") boolean committeeReviewRequired,
                                              @RequestParam(required = false) Integer committeePriority,
                                              @RequestParam(required = false) Integer committeeMinimumVotes,
                                              @RequestParam(required = false) Integer committeeApprovalThreshold,
                                              @RequestParam(defaultValue = "true") boolean accountantReviewRequired,
                                              @RequestParam(required = false) Integer accountantPriority,
                                              @RequestParam(defaultValue = "true") boolean disbursementOfficerRequired,
                                              @RequestParam(defaultValue = "1") Integer managerPriority,
                                              @RequestParam(defaultValue = "2") Integer loanOfficerPriority,
                                              @RequestParam(defaultValue = "false") boolean guarantorMinSavingsCheckRequired,
                                              @RequestParam(required = false) BigDecimal guarantorMinimumSavings,
                                              @RequestParam(defaultValue = "ACTIVE") LoanProductStatus productStatus,
                                              @RequestParam(required = false) String modalKey,
                                              RedirectAttributes ra) {
        String resolvedModalKey = normalizeLoanSettingsModalKey(modalKey) == null ? "create-product" : normalizeLoanSettingsModalKey(modalKey);
        try {
            ApprovalWorkflowStage resolvedWorkflowStartStage = resolveLoanOfficerWorkflowStartStage(
                managerReviewRequired,
                loanOfficerReviewRequired,
                workflowStartStage,
                managerPriority,
                loanOfficerPriority
            );
            adminService.createCustomizedLoanProduct(
                adminScopeService.currentSaccoId(principal),
                principal.getMemberId(),
                productCode,
                productName,
                productDescription,
                displayOrder,
                minimumAmount,
                maximumAmount,
                guarantorsRequired,
                percentToRatio(maxLoanSavingsPercent),
                percentToRatio(insurancePercent),
                percentToRatio(annualInterestPercent),
                interestMethod,
                minRepaymentMonths,
                maxRepaymentMonths,
                allowApplicationWithActiveLoan,
                freshFinancialDataRequired,
                managerReviewRequired,
                loanOfficerReviewRequired,
                resolvedWorkflowStartStage,
                managerPriority,
                loanOfficerPriority,
                committeeReviewRequired,
                committeePriority,
                committeeMinimumVotes,
                committeeApprovalThreshold,
                accountantReviewRequired,
                accountantPriority,
                disbursementOfficerRequired,
                guarantorMinSavingsCheckRequired,
                guarantorMinimumSavings,
                productStatus
            );
            ra.addFlashAttribute("message", "Customized loan product added.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            attachLoanSettingsValidationFeedback(ra, ex.getMessage());
        }
        return loanSettingsRedirect(resolvedModalKey);
    }

    @PostMapping("/settings-controls/loan-rules")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateLoanRules(@AuthenticationPrincipal AppUserPrincipal principal,
                                  @RequestParam BigDecimal applicationFee,
                                  @RequestParam(required = false) String modalKey,
                                  RedirectAttributes ra) {
        String resolvedModalKey = normalizeLoanSettingsModalKey(modalKey) == null ? "application-fee" : normalizeLoanSettingsModalKey(modalKey);
        try {
            adminService.updateLoanApplicationFee(
                adminScopeService.currentSaccoId(principal),
                principal.getMemberId(),
                applicationFee
            );
            ra.addFlashAttribute("message", "Loan settings updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            attachLoanSettingsValidationFeedback(ra, ex.getMessage());
        }
        return loanSettingsRedirect(resolvedModalKey);
    }

    @PostMapping("/settings-controls/language")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateWorkspaceDefaultLanguage(@AuthenticationPrincipal AppUserPrincipal principal,
                                                 @RequestParam String defaultLanguage,
                                                 RedirectAttributes ra) {
        try {
            adminService.updateDefaultLanguage(
                adminScopeService.currentSaccoId(principal),
                principal.getMemberId(),
                defaultLanguage
            );
            ra.addFlashAttribute("message", "Language settings updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings-controls?section=language";
    }

    @PostMapping("/settings-controls/otp-delivery")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateOtpDelivery(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam OtpDeliveryChannel otpDeliveryChannel,
                                    RedirectAttributes ra) {
        try {
            stationOtpSettingsService.update(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                otpDeliveryChannel,
                principal.getMemberId()
            );
            ra.addFlashAttribute("message", "OTP delivery setting updated for this station.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings-controls?section=otp";
    }

    @PostMapping("/settings-controls/qualification-policies")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateQualificationPolicies(@AuthenticationPrincipal AppUserPrincipal principal,
                                              @RequestParam(required = false) Integer applicantMaxDefaultedLoans,
                                              @RequestParam(required = false) Integer applicantMaxForfeitedLoans,
                                              @RequestParam(required = false) Integer applicantForfeitedLookbackDays,
                                              @RequestParam(required = false) Integer applicantForfeitedWaitDays,
                                              @RequestParam(defaultValue = "false") boolean guarantorWithActiveLoanAllowed,
                                              @RequestParam(required = false) BigDecimal guarantorMaxGuaranteedLoanAmount,
                                              @RequestParam(required = false) Integer guarantorMaxDefaultedLoans,
                                              RedirectAttributes ra) {
        try {
            adminService.updateStationQualificationPolicies(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                principal.getMemberId(),
                applicantMaxDefaultedLoans,
                applicantMaxForfeitedLoans,
                applicantForfeitedLookbackDays,
                applicantForfeitedWaitDays,
                guarantorWithActiveLoanAllowed,
                guarantorMaxGuaranteedLoanAmount,
                guarantorMaxDefaultedLoans
            );
            ra.addFlashAttribute("message", "Station qualification policies updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings-controls?section=guarantor";
    }

    @PostMapping("/settings-controls/station-qualification-policies")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateStationQualificationPolicies(@AuthenticationPrincipal AppUserPrincipal principal,
                                                     @RequestParam String stationId,
                                                     @RequestParam(required = false) Integer applicantMaxDefaultedLoans,
                                                     @RequestParam(required = false) Integer applicantMaxForfeitedLoans,
                                                     @RequestParam(required = false) Integer applicantForfeitedLookbackDays,
                                                     @RequestParam(required = false) Integer applicantForfeitedWaitDays,
                                                     @RequestParam(defaultValue = "false") boolean guarantorWithActiveLoanAllowed,
                                                     @RequestParam(required = false) BigDecimal guarantorMaxGuaranteedLoanAmount,
                                                     @RequestParam(required = false) Integer guarantorMaxDefaultedLoans,
                                                     RedirectAttributes ra) {
        try {
            adminService.updateStationQualificationPolicies(
                adminScopeService.currentSaccoId(principal),
                stationId,
                principal.getMemberId(),
                applicantMaxDefaultedLoans,
                applicantMaxForfeitedLoans,
                applicantForfeitedLookbackDays,
                applicantForfeitedWaitDays,
                guarantorWithActiveLoanAllowed,
                guarantorMaxGuaranteedLoanAmount,
                guarantorMaxDefaultedLoans
            );
            ra.addFlashAttribute("message", "Station qualification policies updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings-controls?section=guarantor";
    }

    private ApprovalWorkflowStage resolveLoanOfficerWorkflowStartStage(boolean managerReviewRequired,
                                                                       boolean loanOfficerReviewRequired,
                                                                       ApprovalWorkflowStage workflowStartStage,
                                                                       Integer managerPriority,
                                                                       Integer loanOfficerPriority) {
        int resolvedManagerPriority = managerPriority == null ? 1 : managerPriority;
        int resolvedLoanOfficerPriority = loanOfficerPriority == null ? 2 : loanOfficerPriority;
        if (resolvedManagerPriority < 1 || resolvedManagerPriority > 4) {
            throw new IllegalStateException("Manager priority must be between 1 and 4.");
        }
        if (resolvedLoanOfficerPriority < 1 || resolvedLoanOfficerPriority > 4) {
            throw new IllegalStateException("Loan Officer priority must be between 1 and 4.");
        }
        if (!managerReviewRequired && loanOfficerReviewRequired) {
            return ApprovalWorkflowStage.LOAN_OFFICER;
        }
        if (!loanOfficerReviewRequired) {
            return ApprovalWorkflowStage.MANAGER;
        }
        if (!managerReviewRequired) {
            return ApprovalWorkflowStage.MANAGER;
        }
        if (resolvedManagerPriority == resolvedLoanOfficerPriority) {
            throw new IllegalStateException("Manager and Loan Officer cannot share the same priority slot.");
        }
        return resolvedLoanOfficerPriority < resolvedManagerPriority ? ApprovalWorkflowStage.LOAN_OFFICER : ApprovalWorkflowStage.MANAGER;
    }

    private String loanSettingsRedirect(String modalKey) {
        String normalizedModalKey = normalizeLoanSettingsModalKey(modalKey);
        if (normalizedModalKey == null) {
            return "redirect:/admin/settings-controls?section=loan";
        }
        return "redirect:/admin/settings-controls?section=loan&modal="
            + UriUtils.encode(normalizedModalKey, StandardCharsets.UTF_8);
    }

    private String normalizeSettingsSection(String section) {
        if ("board".equalsIgnoreCase(section)) {
            return "board";
        }
        if ("guarantor".equalsIgnoreCase(section)) {
            return "guarantor";
        }
        if ("language".equalsIgnoreCase(section)) {
            return "language";
        }
        if ("otp".equalsIgnoreCase(section)) {
            return "otp";
        }
        return "loan";
    }

    private String normalizeLoanSettingsModalKey(String modalKey) {
        if (modalKey == null) {
            return null;
        }
        String normalized = modalKey.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void attachLoanSettingsValidationFeedback(RedirectAttributes ra, String message) {
        Map<String, String> fieldErrors = resolveLoanSettingsFieldErrors(message);
        if (fieldErrors.isEmpty()) {
            ra.addFlashAttribute("error", message);
            return;
        }
        ra.addFlashAttribute("error", "Please correct the highlighted fields below.");
        ra.addFlashAttribute("loanSettingsFieldErrors", fieldErrors);
    }

    private Map<String, String> resolveLoanSettingsFieldErrors(String message) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (message == null || message.isBlank()) {
            return fieldErrors;
        }
        switch (message) {
            case "Enter the loan product name." ->
                fieldErrors.put("productName", "Enter a loan product name.");
            case "Loan product name must be 120 characters or fewer." ->
                fieldErrors.put("productName", "Loan product name must be 120 characters or fewer.");
            case "That loan product code already exists in this SACCO." ->
                fieldErrors.put("productName", "Use a more distinct loan product name. This one creates a duplicate product code.");
            case "Loan product description must be 500 characters or fewer." ->
                fieldErrors.put("productDescription", "Keep the description within 500 characters.");
            case "Display order must be at least 1." ->
                fieldErrors.put("displayOrder", "Display order must be 1 or higher.");
            case "Minimum amount cannot be negative." ->
                fieldErrors.put("minimumAmount", "Minimum amount cannot be negative.");
            case "Maximum amount must be greater than zero.",
                 "Maximum amount cannot be lower than the minimum amount." ->
                fieldErrors.put("maximumAmount", message);
            case "Savings ratio must be greater than zero." ->
                fieldErrors.put("maxLoanSavingsPercent", "Loan savings multiple must be greater than zero.");
            case "Guarantors required cannot be negative." ->
                fieldErrors.put("guarantorsRequired", "Guarantors required cannot be negative.");
            case "Guarantors required must be between 0 and 15." ->
                fieldErrors.put("guarantorsRequired", "Guarantors required must be between 0 and 15.");
            case "Minimum guarantor savings cannot be negative." ->
                fieldErrors.put("guarantorMinimumSavings", "Minimum guarantor savings cannot be negative.");
            case "Insurance rate cannot be negative." ->
                fieldErrors.put("insurancePercent", "Insurance percentage cannot be negative.");
            case "Application fee cannot be negative." ->
                fieldErrors.put("applicationFee", "Application fee cannot be negative.");
            case "Annual interest rate cannot be negative." ->
                fieldErrors.put("annualInterestPercent", "Annual interest percentage cannot be negative.");
            case "Workflow start stage must be Manager or Loan Officer." -> {
                fieldErrors.put("workflowStartStage", "Choose whether Manager or Loan Officer starts the review.");
                fieldErrors.put("managerPriority", "Manager and Loan Officer priorities must match the selected start stage.");
                fieldErrors.put("loanOfficerPriority", "Manager and Loan Officer priorities must match the selected start stage.");
            }
            case "Loan Officer must be enabled before it can be selected as the start stage.",
                 "No active loan officers are configured for this SACCO yet." ->
                fieldErrors.put("loanOfficerReviewRequired", "Assign at least one active Loan Officer before using this stage.");
            case "Manager priority must be between 1 and 4." ->
                fieldErrors.put("managerPriority", "Manager priority must be between 1 and 4.");
            case "Loan Officer priority must be between 1 and 4." ->
                fieldErrors.put("loanOfficerPriority", "Loan Officer priority must be between 1 and 4.");
            case "Review priority must be between 1 and 4." -> {
                fieldErrors.put("managerPriority", "Review priority must be between 1 and 4.");
                fieldErrors.put("loanOfficerPriority", "Review priority must be between 1 and 4.");
            }
            case "Manager and Loan Officer cannot share the same priority slot." -> {
                fieldErrors.put("managerPriority", "Choose different priorities for Manager and Loan Officer.");
                fieldErrors.put("loanOfficerPriority", "Choose different priorities for Manager and Loan Officer.");
            }
            case "Stage priority must be between 1 and 4." -> {
                fieldErrors.put("committeePriority", "Stage priority must be between 1 and 4.");
                fieldErrors.put("accountantPriority", "Stage priority must be between 1 and 4.");
            }
            case "Committee and Accountant cannot share the same priority slot." -> {
                fieldErrors.put("committeePriority", "Committee and Accountant cannot share the same priority slot.");
                fieldErrors.put("accountantPriority", "Committee and Accountant cannot share the same priority slot.");
            }
            case "Manager and Committee cannot share the same priority slot." -> {
                fieldErrors.put("managerPriority", "Manager and Committee cannot share the same priority slot.");
                fieldErrors.put("committeePriority", "Manager and Committee cannot share the same priority slot.");
            }
            case "Manager and Accountant cannot share the same priority slot." -> {
                fieldErrors.put("managerPriority", "Manager and Accountant cannot share the same priority slot.");
                fieldErrors.put("accountantPriority", "Manager and Accountant cannot share the same priority slot.");
            }
            case "Loan Officer and Committee cannot share the same priority slot." -> {
                fieldErrors.put("loanOfficerPriority", "Loan Officer and Committee cannot share the same priority slot.");
                fieldErrors.put("committeePriority", "Loan Officer and Committee cannot share the same priority slot.");
            }
            case "Loan Officer and Accountant cannot share the same priority slot." -> {
                fieldErrors.put("loanOfficerPriority", "Loan Officer and Accountant cannot share the same priority slot.");
                fieldErrors.put("accountantPriority", "Loan Officer and Accountant cannot share the same priority slot.");
            }
            case "Minimum repayment period must be at least 1 month." ->
                fieldErrors.put("minRepaymentMonths", "Minimum repayment period must be at least 1 month.");
            case "Maximum repayment period must be at least 1 month.",
                 "Maximum repayment period cannot be lower than the minimum repayment period." ->
                fieldErrors.put("maxRepaymentMonths", message);
            case "Committee minimum votes must be at least 1 when committee review is required.",
                 "Committee minimum votes must be between 1 and 15.",
                 "Committee minimum votes cannot exceed the number of active board members." ->
                fieldErrors.put("committeeMinimumVotes", message);
            case "Committee approval threshold must be at least 1 when committee review is required.",
                 "Committee approval threshold must be between 1 and 15.",
                 "Committee approval threshold cannot exceed the number of active board members." ->
                fieldErrors.put("committeeApprovalThreshold", message);
            case "Committee approval threshold cannot be greater than committee minimum votes." -> {
                fieldErrors.put("committeeMinimumVotes", "Committee approvals needed cannot be greater than committee reviewers assigned.");
                fieldErrors.put("committeeApprovalThreshold", "Committee approvals needed cannot be greater than committee reviewers assigned.");
            }
            case "No active board members are configured for this SACCO yet." ->
                fieldErrors.put("committeeReviewRequired", "Assign at least one active committee reviewer before using this stage.");
            case "Add at least one active Disbursement Officer before requiring that workflow role." ->
                fieldErrors.put("disbursementOfficerRequired", "Assign at least one active Disbursement Officer before requiring this role.");
            case "Grant disbursement queue and release claims to at least one active staff user before removing the Disbursement Officer requirement." ->
                fieldErrors.put("disbursementOfficerRequired", "Grant both disbursement claims to at least one active staff user before removing this role requirement.");
            default -> {
            }
        }
        return fieldErrors;
    }

    @PostMapping("/settings-controls/{id}/versions/{versionId}/rollback")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String rollbackLoanProductVersion(@PathVariable UUID id,
                                             @PathVariable UUID versionId,
                                             @AuthenticationPrincipal AppUserPrincipal principal,
                                             RedirectAttributes ra) {
        adminService.rollbackLoanProductVersion(
            adminScopeService.currentSaccoId(principal),
            principal.getMemberId(),
            id,
            versionId
        );
        ra.addFlashAttribute("message", "Loan product rolled back to the selected saved version.");
        return "redirect:/admin/settings-controls?section=loan";
    }

    @GetMapping("/reports")
    @PreAuthorize("@authz.platformAdminIdentity(principal)")
    public String reports() {
        return "admin/reports";
    }

    @PostMapping("/settings-controls/review-rules")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateReviewRules(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam(defaultValue = "false") boolean loanOfficerReviewRequired,
                                    @RequestParam(defaultValue = "false") boolean boardReviewRequired,
                                    @RequestParam Integer boardQuorum,
                                    RedirectAttributes ra) {
        adminService.updateBoardReviewRequirement(
            adminScopeService.currentSaccoId(principal),
            principal.getMemberId(),
            loanOfficerReviewRequired,
            boardReviewRequired,
            boardQuorum
        );
        ra.addFlashAttribute("message", "Approval flow updated.");
        return "redirect:/admin/settings-controls?section=board";
    }

    @GetMapping("/outbox")
    @PreAuthorize("hasAnyRole('ADMIN','MINOR_ADMIN') and @userClaims.has(principal, 'ACCESS_OUTBOX_MONITOR')")
    public String outbox(@RequestParam(required = false) String dateFrom,
                         @RequestParam(required = false) String dateTo,
                         @RequestParam(required = false) String loanId,
                         @RequestParam(required = false) String saccoId,
                         @RequestParam(required = false) String stationId,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "50") int size,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        boolean superAdmin = principal != null && principal.hasRole(Position.ADMIN);
        String scopedSaccoId = superAdmin ? normalizeTextParam(saccoId) : adminScopeService.currentSaccoId(principal);
        String scopedStationId = superAdmin ? normalizeTextParam(stationId) : adminScopeService.currentStationId(principal);
        Map<String, String> dateErrors = validateDateRangeInputs(dateFrom, dateTo);
        if (!dateErrors.isEmpty()) {
            populateOutboxFilterModel(model, dateFrom, dateTo, loanId, scopedSaccoId, scopedStationId, superAdmin, Page.empty(PageRequest.of(0, normalizePageSize(size))));
            applyDateErrors(model, dateErrors);
            return "admin/outbox";
        }
        try {
            Page<com.sacco.mvp.domain.OutboxEvent> eventsPage = adminService.outboxEvents(
                page,
                size,
                null,
                dateFrom,
                dateTo,
                loanId,
                scopedSaccoId.isBlank() ? null : scopedSaccoId,
                scopedStationId.isBlank() ? null : scopedStationId
            );
            populateOutboxFilterModel(model, dateFrom, dateTo, loanId, scopedSaccoId, scopedStationId, superAdmin, eventsPage);
        } catch (IllegalArgumentException | DataAccessException ex) {
            populateOutboxFilterModel(model, dateFrom, dateTo, loanId, scopedSaccoId, scopedStationId, superAdmin, Page.empty(PageRequest.of(0, normalizePageSize(size))));
            model.addAttribute("error", resolveFilterErrorMessage(ex, "We couldn't apply that outbox filter. Adjust the values and try again."));
        }
        return "admin/outbox";
    }

    @PostMapping("/outbox/{id}/retry")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @userClaims.has(principal, 'ACCESS_OUTBOX_MONITOR')")
    public String retryOutbox(@PathVariable UUID id,
                              @RequestParam(required = false) String dateFrom,
                              @RequestParam(required = false) String dateTo,
                              @RequestParam(required = false) String loanId,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "50") int size,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              RedirectAttributes ra) {
        adminService.retryOutbox(principal.getMemberId(), id);
        ra.addFlashAttribute("message", "Outbox event moved back to NEW for retry.");
        return "redirect:/admin/outbox?page=" + normalizePage(page)
            + buildOutboxPaginationQuery(dateFrom, dateTo, loanId, normalizePageSize(size));
    }

    @GetMapping("/events")
    public String events(@RequestParam(required = false) String dateFrom,
                         @RequestParam(required = false) String dateTo,
                         @RequestParam(required = false) String actorId,
                         @RequestParam(required = false) String saccoId,
                         @RequestParam(required = false) String stationId,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "50") int size,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        boolean superAdmin = principal != null && principal.hasRole(Position.ADMIN);
        String scopedSaccoId = superAdmin ? normalizeTextParam(saccoId) : adminScopeService.currentSaccoId(principal);
        String scopedStationId = superAdmin ? normalizeTextParam(stationId) : adminScopeService.currentStationId(principal);
        Map<String, String> dateErrors = validateDateRangeInputs(dateFrom, dateTo);
        if (!dateErrors.isEmpty()) {
            populateEventsFilterModel(model, dateFrom, dateTo, actorId, scopedSaccoId, scopedStationId, superAdmin, Page.empty(PageRequest.of(0, normalizePageSize(size))));
            applyDateErrors(model, dateErrors);
            return "admin/events";
        }
        try {
            Page<com.sacco.mvp.domain.AuditLog> entriesPage = adminService.eventEntries(
                page,
                size,
                dateFrom,
                dateTo,
                actorId,
                scopedSaccoId.isBlank() ? null : scopedSaccoId,
                scopedStationId.isBlank() ? null : scopedStationId
            );
            populateEventsFilterModel(model, dateFrom, dateTo, actorId, scopedSaccoId, scopedStationId, superAdmin, entriesPage);
        } catch (IllegalArgumentException | DataAccessException ex) {
            populateEventsFilterModel(model, dateFrom, dateTo, actorId, scopedSaccoId, scopedStationId, superAdmin, Page.empty(PageRequest.of(0, normalizePageSize(size))));
            model.addAttribute("error", resolveFilterErrorMessage(ex, "We couldn't apply that event log filter. Adjust the values and try again."));
        }
        return "admin/events";
    }

    @GetMapping("/saccos")
    public String saccos(@AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        boolean superAdmin = principal != null && principal.hasRole(Position.ADMIN);
        if (superAdmin) {
            model.addAttribute("platformDashboard", platformAdminService.dashboard());
            return "admin/platform-saccos";
        }
        java.util.List<SaccoRegistryService.RegisteredSaccoView> registeredSaccos = saccoRegistryService.listRegisteredSaccos().stream()
            .filter(sacco -> sacco.saccoId().equals(adminScopeService.currentSaccoId(principal)))
            .toList();
        model.addAttribute("superAdmin", false);
        model.addAttribute("registeredSaccos", registeredSaccos);
        return "admin/sacco-registry";
    }

    @GetMapping("/saccos/registry")
    @PreAuthorize("@authz.platformAdminIdentity(principal)")
    public String saccoRegistry(Model model) {
        model.addAttribute("registeredSaccos", saccoRegistryService.listRegisteredSaccos());
        model.addAttribute("superAdmin", true);
        return "admin/sacco-registry";
    }

    @GetMapping("/saccos/{saccoId}")
    @PreAuthorize("@authz.platformAdminIdentity(principal)")
    public String saccoDetail(@PathVariable String saccoId,
                              @RequestParam(required = false) String section,
                              @RequestParam(required = false) String stationId,
                              @RequestParam(defaultValue = "0") int smsPage,
                              @RequestParam(defaultValue = "0") int smsHistoryPage,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              Model model) {
        String selectedSection = platformAdminService.normalizeSection(section);
        var saccoDetail = platformAdminService.saccoDetail(saccoId, stationId);
        model.addAttribute("selectedSection", selectedSection);
        model.addAttribute("saccoDetail", saccoDetail);
        if ("sms".equals(selectedSection)) {
            int safeSmsPage = Math.max(0, smsPage);
            int safeSmsHistoryPage = Math.max(0, smsHistoryPage);
            if (saccoDetail.isStationScoped()) {
                var smsAccount = smsUsageManagementService.account(
                    saccoDetail.getSaccoId(),
                    saccoDetail.getSelectedStationId()
                );
                model.addAttribute("smsAccount", smsAccount);
                model.addAttribute("smsHistory", smsUsageManagementService.history(
                    smsAccount.getId(),
                    PageRequest.of(safeSmsHistoryPage, 25)
                ));
            } else {
                model.addAttribute("smsAccounts", smsUsageManagementService.accounts(
                    saccoDetail.getSaccoId(),
                    null,
                    null,
                    PageRequest.of(safeSmsPage, 25)
                ));
            }
        }
        return "admin/sacco-detail";
    }

    @PostMapping("/saccos/{saccoId}/language")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updatePlatformSaccoDefaultLanguage(@PathVariable String saccoId,
                                                     @RequestParam String defaultLanguage,
                                                     @RequestParam(required = false) String section,
                                                     @RequestParam(required = false) String stationId,
                                                     @AuthenticationPrincipal AppUserPrincipal principal,
                                                     RedirectAttributes ra) {
        try {
            adminService.updateDefaultLanguage(saccoId, principal.getMemberId(), defaultLanguage);
            ra.addFlashAttribute("message", "SACCO language settings updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        String normalizedSection = platformAdminService.normalizeSection(section);
        StringBuilder redirect = new StringBuilder("redirect:/admin/saccos/")
            .append(UriUtils.encode(saccoId, StandardCharsets.UTF_8))
            .append("?section=")
            .append(UriUtils.encode(normalizedSection, StandardCharsets.UTF_8));
        if (stationId != null && !stationId.isBlank()) {
            redirect.append("&stationId=").append(UriUtils.encode(stationId.trim(), StandardCharsets.UTF_8));
        }
        return redirect.toString();
    }

    @GetMapping("/saccos/minor-admins")
    @PreAuthorize("@authz.platformAdminIdentity(principal)")
    public String minorAdmins(Model model) {
        model.addAttribute("registeredSaccos", saccoRegistryService.listRegisteredSaccos());
        model.addAttribute("minorAdmins", adminService.minorAdmins());
        model.addAttribute("registrationForm", new MinorAdminRegistrationForm());
        model.addAttribute("superAdmin", true);
        return "admin/minor-admins";
    }

    @PostMapping("/saccos")
    @PreAuthorize("(@authz.platformAdminIdentity(principal) or @authz.workspaceAdminOnly(principal)) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String registerSacco(@RequestParam String saccoId,
                                @RequestParam String saccoName,
                                @RequestParam String stationIds,
                                @RequestParam(name = "logoFile", required = false) MultipartFile logoFile,
                                @AuthenticationPrincipal AppUserPrincipal principal,
                                RedirectAttributes ra) {
        try {
            saccoRegistryService.registerSacco(saccoId, saccoName, stationIds, logoFile);
            ra.addFlashAttribute("message", "SACCO details saved.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/saccos/registry";
        }
        return "redirect:/admin/saccos/registry";
    }

    @PostMapping("/saccos/minor-admins")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String registerMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                     @ModelAttribute MinorAdminRegistrationForm registrationForm,
                                     RedirectAttributes ra) {
        try {
            adminService.registerMinorAdmin(
                principal.getMemberId(),
                registrationForm.getSaccoId(),
                registrationForm.getStationId(),
                registrationForm.getMemberNo(),
                registrationForm.getFullName(),
                registrationForm.getEmail(),
                registrationForm.getPhone()
            );
            ra.addFlashAttribute("message", "Minor Admin invited. A password setup link has been emailed to them.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/minor-admins/{accountId}")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @PathVariable UUID accountId,
                                   @RequestParam String saccoId,
                                   @RequestParam String stationId,
                                   @RequestParam String memberNo,
                                   @RequestParam String fullName,
                                   @RequestParam String email,
                                   @RequestParam(required = false) String phone,
                                   RedirectAttributes ra) {
        try {
            adminService.updateMinorAdmin(
                principal.getMemberId(),
                accountId,
                saccoId,
                stationId,
                memberNo,
                fullName,
                email,
                phone
            );
            ra.addFlashAttribute("message", "Minor Admin details updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/minor-admins/{accountId}/resend-invite")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String resendMinorAdminInvite(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @PathVariable UUID accountId,
                                         RedirectAttributes ra) {
        try {
            adminService.resendMinorAdminInvitation(principal.getMemberId(), accountId);
            ra.addFlashAttribute("message", "A new activation link has been emailed.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/minor-admins/{accountId}/revoke-invite")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String revokeMinorAdminInvite(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @PathVariable UUID accountId,
                                         RedirectAttributes ra) {
        try {
            adminService.revokeMinorAdminInvitation(principal.getMemberId(), accountId);
            ra.addFlashAttribute("message", "Invitation revoked. Account marked inactive.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/minor-admins/{accountId}/deactivate")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String deactivateMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable UUID accountId,
                                       RedirectAttributes ra) {
        try {
            adminService.deactivateMinorAdmin(principal.getMemberId(), accountId);
            ra.addFlashAttribute("message", "Minor Admin deactivated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/minor-admins/{accountId}/reinvite")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String reinviteMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                     @PathVariable UUID accountId,
                                     RedirectAttributes ra) {
        try {
            adminService.reinviteMinorAdmin(principal.getMemberId(), accountId);
            ra.addFlashAttribute("message", "A fresh activation link has been emailed.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/{saccoId}")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String updateSacco(@PathVariable String saccoId,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam String saccoName,
                              @RequestParam String stationIds,
                              @RequestParam(name = "stationAddressIds", required = false) List<String> stationAddressIds,
                              @RequestParam(name = "stationAddressLocations", required = false) List<String> stationAddressLocations,
                              @RequestParam(name = "logoFile", required = false) MultipartFile logoFile,
                              RedirectAttributes ra) {
        try {
            if (principal != null && principal.hasRole(Position.ADMIN)) {
                saccoRegistryService.updateSacco(
                    saccoId,
                    saccoName,
                    stationIds,
                    stationAddressLocationMap(stationAddressIds, stationAddressLocations),
                    logoFile
                );
                ra.addFlashAttribute("message", "SACCO registration updated.");
            } else {
                throw new IllegalStateException("Only Super Admins can manage SACCO stations.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return principal != null && principal.hasRole(Position.ADMIN)
                ? "redirect:/admin/saccos/registry"
                : "redirect:/admin/saccos";
        }
        return principal != null && principal.hasRole(Position.ADMIN)
            ? "redirect:/admin/saccos/registry"
            : "redirect:/admin/saccos";
    }

    @PostMapping("/saccos/{saccoId}/access/suspend")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String suspendStationAccess(@PathVariable String saccoId,
                                       @AuthenticationPrincipal AppUserPrincipal principal,
                                       @RequestParam String stationId,
                                       @RequestParam String reason,
                                       @RequestParam(required = false) LocalDate paymentDueDate,
                                       RedirectAttributes ra) {
        try {
            adminService.suspendStationAccess(saccoId, stationId, principal.getMemberId(), reason, paymentDueDate);
            ra.addFlashAttribute("message", "Station access suspended.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return stationAccessRedirect(saccoId, stationId);
    }

    @PostMapping("/saccos/{saccoId}/access/restore")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String restoreStationAccess(@PathVariable String saccoId,
                                      @AuthenticationPrincipal AppUserPrincipal principal,
                                      @RequestParam String stationId,
                                      RedirectAttributes ra) {
        try {
            adminService.restoreStationAccess(saccoId, stationId, principal.getMemberId());
            ra.addFlashAttribute("message", "Station access restored.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return stationAccessRedirect(saccoId, stationId);
    }

    @PostMapping("/saccos/{saccoId}/stations")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
    public String addStation(@PathVariable String saccoId,
                             @AuthenticationPrincipal AppUserPrincipal principal,
                             @RequestParam String stationId,
                             @RequestParam String addressLocation,
                             RedirectAttributes ra) {
        try {
            saccoRegistryService.addStation(saccoId, stationId, addressLocation);
            ra.addFlashAttribute("message", "Station added.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return principal != null && principal.hasRole(Position.ADMIN)
                ? "redirect:/admin/saccos/registry"
                : "redirect:/admin/saccos";
        }
        return principal != null && principal.hasRole(Position.ADMIN)
            ? "redirect:/admin/saccos/registry"
            : "redirect:/admin/saccos";
    }

    private Map<String, String> stationAddressLocationMap(List<String> stationAddressIds, List<String> stationAddressLocations) {
        Map<String, String> addressLocations = new LinkedHashMap<>();
        if (stationAddressIds == null || stationAddressLocations == null) {
            return addressLocations;
        }
        int count = Math.min(stationAddressIds.size(), stationAddressLocations.size());
        for (int i = 0; i < count; i++) {
            String stationId = normalizeTextParam(stationAddressIds.get(i));
            if (!stationId.isBlank()) {
                addressLocations.put(stationId, stationAddressLocations.get(i));
            }
        }
        return addressLocations;
    }

    private String stationAccessRedirect(String saccoId, String stationId) {
        StringBuilder redirect = new StringBuilder("redirect:/admin/saccos/")
            .append(UriUtils.encode(saccoId, StandardCharsets.UTF_8))
            .append("?section=overview");
        String normalizedStationId = normalizeTextParam(stationId);
        if (!normalizedStationId.isBlank()) {
            redirect.append("&stationId=").append(UriUtils.encode(normalizedStationId, StandardCharsets.UTF_8));
        }
        return redirect.toString();
    }

    @PostMapping("/scope")
    @PreAuthorize("@authz.workspaceAdminOnly(principal)")
    public String updateScope(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam String saccoId,
                              @RequestParam String stationId,
                              @RequestParam(required = false) String next,
                              RedirectAttributes ra) {
        adminScopeService.updateScope(principal, saccoId, stationId);
        ra.addFlashAttribute("message", principal != null && principal.hasRole(Position.ADMIN)
            ? "You are now working under the selected SACCO and station."
            : "You are now working under the selected station.");
        return "redirect:" + normalizeAdminNextPath(next);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex,
                              HttpServletRequest request,
                              RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:" + resolveAdminReturnPath(request);
    }

    private String normalizeDateParam(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private String normalizeTextParam(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return 50;
        }
        return Math.min(size, 100);
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private void populateOutboxFilterModel(Model model,
                                           String dateFrom,
                                           String dateTo,
                                           String loanId,
                                           Page<com.sacco.mvp.domain.OutboxEvent> eventsPage) {
        populateOutboxFilterModel(model, dateFrom, dateTo, loanId, "", "", false, eventsPage);
    }

    private void populateOutboxFilterModel(Model model,
                                           String dateFrom,
                                           String dateTo,
                                           String loanId,
                                           String saccoId,
                                           String stationId,
                                           boolean superAdmin,
                                           Page<com.sacco.mvp.domain.OutboxEvent> eventsPage) {
        model.addAttribute("events", eventsPage.getContent());
        model.addAttribute("eventsPage", eventsPage);
        model.addAttribute("selectedDateFrom", normalizeDateParam(dateFrom));
        model.addAttribute("selectedDateTo", normalizeDateParam(dateTo));
        model.addAttribute("selectedLoanId", normalizeTextParam(loanId));
        addAdminScopeFilters(model, saccoId, stationId, superAdmin);
        model.addAttribute("outboxPaginationQuery", buildOutboxPaginationQuery(dateFrom, dateTo, loanId, saccoId, stationId, eventsPage.getSize()));
        model.addAttribute("selectedPageSize", eventsPage.getSize());
    }

    private void populateEventsFilterModel(Model model,
                                           String dateFrom,
                                           String dateTo,
                                           String actorId,
                                           Page<com.sacco.mvp.domain.AuditLog> entriesPage) {
        populateEventsFilterModel(model, dateFrom, dateTo, actorId, "", "", false, entriesPage);
    }

    private void populateEventsFilterModel(Model model,
                                           String dateFrom,
                                           String dateTo,
                                           String actorId,
                                           String saccoId,
                                           String stationId,
                                           boolean superAdmin,
                                           Page<com.sacco.mvp.domain.AuditLog> entriesPage) {
        model.addAttribute("entries", entriesPage.getContent());
        model.addAttribute("entriesPage", entriesPage);
        model.addAttribute("selectedDateFrom", normalizeDateParam(dateFrom));
        model.addAttribute("selectedDateTo", normalizeDateParam(dateTo));
        model.addAttribute("selectedActorId", normalizeTextParam(actorId));
        addAdminScopeFilters(model, saccoId, stationId, superAdmin);
        model.addAttribute("eventsPaginationQuery", buildEventsPaginationQuery(dateFrom, dateTo, actorId, saccoId, stationId, entriesPage.getSize()));
        model.addAttribute("selectedPageSize", entriesPage.getSize());
    }

    private void addAdminScopeFilters(Model model, String saccoId, String stationId, boolean superAdmin) {
        String selectedSaccoId = normalizeTextParam(saccoId);
        String selectedStationId = normalizeTextParam(stationId);
        java.util.List<SaccoRegistryService.RegisteredSaccoView> saccos = superAdmin
            ? saccoRegistryService.listRegisteredSaccos()
            : java.util.List.of();
        java.util.List<String> stationOptions = saccos.stream()
            .filter(sacco -> selectedSaccoId.equals(sacco.saccoId()))
            .findFirst()
            .map(SaccoRegistryService.RegisteredSaccoView::stationIds)
            .orElse(java.util.List.of());
        model.addAttribute("superAdminScopeFilters", superAdmin);
        model.addAttribute("registeredSaccos", saccos);
        model.addAttribute("selectedSaccoId", selectedSaccoId);
        model.addAttribute("selectedStationId", selectedStationId);
        model.addAttribute("selectedStationOptions", stationOptions);
    }

    private String resolveFilterErrorMessage(RuntimeException ex, String fallback) {
        if (ex instanceof DataAccessException) {
            return fallback;
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }

    private Map<String, String> validateDateRangeInputs(String dateFrom, String dateTo) {
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDate from = parseFilterDate(dateFrom, "dateFromError", "Enter a valid Filter From date.", errors);
        LocalDate to = parseFilterDate(dateTo, "dateToError", "Enter a valid Filter To date.", errors);
        if (from != null && to != null && from.isAfter(to)) {
            errors.put("dateToError", "Filter To cannot be earlier than Filter From.");
        }
        return errors;
    }

    private LocalDate parseFilterDate(String raw, String key, String message, Map<String, String> errors) {
        String normalized = normalizeDateParam(raw);
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ex) {
            errors.put(key, message);
            return null;
        }
    }

    private void applyDateErrors(Model model, Map<String, String> dateErrors) {
        model.addAttribute("dateFromError", dateErrors.get("dateFromError"));
        model.addAttribute("dateToError", dateErrors.get("dateToError"));
    }

    private String buildOutboxPaginationQuery(String dateFrom, String dateTo, String loanId, int size) {
        return buildOutboxPaginationQuery(dateFrom, dateTo, loanId, null, null, size);
    }

    private String buildOutboxPaginationQuery(String dateFrom, String dateTo, String loanId, String saccoId, String stationId, int size) {
        StringBuilder query = new StringBuilder("&size=").append(size);
        if (dateFrom != null && !dateFrom.isBlank()) {
            query.append("&dateFrom=").append(UriUtils.encode(dateFrom.trim(), StandardCharsets.UTF_8));
        }
        if (dateTo != null && !dateTo.isBlank()) {
            query.append("&dateTo=").append(UriUtils.encode(dateTo.trim(), StandardCharsets.UTF_8));
        }
        if (loanId != null && !loanId.isBlank()) {
            query.append("&loanId=").append(UriUtils.encode(loanId.trim(), StandardCharsets.UTF_8));
        }
        if (saccoId != null && !saccoId.isBlank()) {
            query.append("&saccoId=").append(UriUtils.encode(saccoId.trim(), StandardCharsets.UTF_8));
        }
        if (stationId != null && !stationId.isBlank()) {
            query.append("&stationId=").append(UriUtils.encode(stationId.trim(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private String buildEventsPaginationQuery(String dateFrom, String dateTo, String actorId, int size) {
        return buildEventsPaginationQuery(dateFrom, dateTo, actorId, null, null, size);
    }

    private String buildEventsPaginationQuery(String dateFrom, String dateTo, String actorId, String saccoId, String stationId, int size) {
        StringBuilder query = new StringBuilder("&size=").append(size);
        if (dateFrom != null && !dateFrom.isBlank()) {
            query.append("&dateFrom=").append(UriUtils.encode(dateFrom.trim(), StandardCharsets.UTF_8));
        }
        if (dateTo != null && !dateTo.isBlank()) {
            query.append("&dateTo=").append(UriUtils.encode(dateTo.trim(), StandardCharsets.UTF_8));
        }
        if (actorId != null && !actorId.isBlank()) {
            query.append("&actorId=").append(UriUtils.encode(actorId.trim(), StandardCharsets.UTF_8));
        }
        if (saccoId != null && !saccoId.isBlank()) {
            query.append("&saccoId=").append(UriUtils.encode(saccoId.trim(), StandardCharsets.UTF_8));
        }
        if (stationId != null && !stationId.isBlank()) {
            query.append("&stationId=").append(UriUtils.encode(stationId.trim(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private String buildUsersPaginationQuery(String queryText, int size) {
        StringBuilder query = new StringBuilder("&size=").append(size);
        if (queryText != null && !queryText.isBlank()) {
            query.append("&query=").append(UriUtils.encode(queryText.trim(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private BigDecimal percentToRatio(BigDecimal percent) {
        return percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    private String normalizeAdminNextPath(String next) {
        if (next == null || next.isBlank()) {
            return "/admin/dashboard";
        }
        try {
            URI uri = URI.create(next);
            String path = uri.getPath();
            if (path == null || !path.startsWith("/admin") || path.startsWith("/admin/scope")) {
                return "/admin/dashboard";
            }
            String query = uri.getRawQuery();
            return query == null || query.isBlank() ? path : path + "?" + query;
        } catch (IllegalArgumentException ex) {
            return "/admin/dashboard";
        }
    }

    private String resolveAdminReturnPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "/admin/dashboard";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getPath();
            if (path == null || !path.startsWith("/admin")) {
                return "/admin/dashboard";
            }
            String query = uri.getRawQuery();
            return query == null || query.isBlank() ? path : path + "?" + query;
        } catch (IllegalArgumentException ignored) {
            return "/admin/dashboard";
        }
    }
}
