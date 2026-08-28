package com.sacco.mvp.web;

import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.IncidentStatus;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanProductStatus;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.OtpSelectionPolicy;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.AdminService;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.ArchiveDateRange;
import com.sacco.mvp.service.AppUsageAnalyticsService;
import com.sacco.mvp.service.DatabaseUtilizationService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.LoanProductRequiredAttachmentService;
import com.sacco.mvp.service.PlatformAdminService;
import com.sacco.mvp.service.PlatformEmailSettingsService;
import com.sacco.mvp.service.PlatformBrandingSettingsService;
import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.PlatformSmsGatewaySettingsService;
import com.sacco.mvp.service.PlatformSupportContactSettingsService;
import com.sacco.mvp.service.SaccoDataDeletionService;
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
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("@access.canAccessAdminArea(principal)")
public class AdminController {
    private static final List<String> LOAN_PRODUCT_BOOLEAN_FIELDS = List.of(
        "savingsLimitCheckRequired",
        "allowApplicationWithActiveLoan",
        "freshFinancialDataRequired",
        "managerReviewRequired",
        "loanOfficerReviewRequired",
        "chairpersonReviewRequired",
        "boardReviewRequired",
        "committeeReviewRequired",
        "accountantReviewRequired",
        "disbursementOfficerRequired",
        "disbursementProofRequired",
        "applicantAttachmentRequired",
        "guarantorMinSavingsCheckRequired"
    );

    private final AdminService adminService;
    private final ApplicationClock applicationClock;
    private final AdminScopeService adminScopeService;
    private final SaccoRegistryService saccoRegistryService;
    private final PlatformAdminService platformAdminService;
    private final DatabaseUtilizationService databaseUtilizationService;
    private final AppUsageAnalyticsService appUsageAnalyticsService;
    private final NotificationInboxService notificationInboxService;
    private final LoanProductRequiredAttachmentService requiredAttachmentService;
    private final SmsUsageManagementService smsUsageManagementService;
    private final StationOtpSettingsService stationOtpSettingsService;
    private final SaccoDataDeletionService saccoDataDeletionService;
    private final PlatformBrandingSettingsService platformBrandingSettingsService;
    private final PlatformSessionSettingsService platformSessionSettingsService;
    private final PlatformSupportContactSettingsService platformSupportContactSettingsService;
    private final PlatformEmailSettingsService platformEmailSettingsService;
    private final PlatformSmsGatewaySettingsService platformSmsGatewaySettingsService;

    @GetMapping("/scope/select")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_VIEW')")
    public String selectScope(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam(required = false) String next,
                              Model model) {
        if (principal != null && principal.isWorkspaceAdminScope() && !principal.isPlatformIdentity()) {
            return "redirect:" + normalizeAdminNextPath(next);
        }
        model.addAttribute("scopeSelection", adminScopeService.currentScope(principal));
        model.addAttribute("nextAdminPath", normalizeAdminNextPath(next));
        return "admin/scope-select";
    }

    @GetMapping("/dashboard")
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'ADMIN_DASHBOARD_VIEW')")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        if (principal != null && principal.isPlatformIdentity()) {
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
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'SMS_USAGE_VIEW')")
    public String smsUsage(@AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false) String saccoId,
                           @RequestParam(required = false) String stationId,
                           @RequestParam(required = false) SmsUnitStatus status,
                           @RequestParam(required = false) UUID accountId,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "0") int historyPage,
                           Model model) {
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
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
                : smsUsageManagementService.historyRows(selectedAccount.getId(), PageRequest.of(safeHistoryPage, 25)));
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
            model.addAttribute("usageHistory", smsUsageManagementService.historyRows(
                scopedSaccoId,
                scopedStationId,
                PageRequest.of(safeHistoryPage, 25)
            ));
        }
        return "admin/sms-usage";
    }

    @PostMapping("/sms-usage/allocations")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SMS_USAGE_ADD')")
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
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SMS_USAGE_CONFIGURE')")
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

    @GetMapping("/platform-settings")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'PLATFORM_SETTINGS_VIEW')")
    public String platformSettings(Model model) {
        model.addAttribute("brandingSettings", platformBrandingSettingsService.settings());
        model.addAttribute("logoUploadPolicy", platformBrandingSettingsService.logoUploadPolicy());
        model.addAttribute("platformSessionSettings", platformSessionSettingsService.settings());
        model.addAttribute("supportContactSettings", platformSupportContactSettingsService.settings());
        model.addAttribute("platformEmailSettings", platformEmailSettingsService.settings());
        model.addAttribute("emailDeliveryStatus", platformEmailSettingsService.resolvedConfig());
        model.addAttribute("platformSmsGatewaySettings", platformSmsGatewaySettingsService.settings());
        model.addAttribute("smsGatewayStatus", platformSmsGatewaySettingsService.resolvedConfig());
        model.addAttribute("registeredSaccos", saccoRegistryService.listRegisteredSaccosFresh());
        return "admin/platform-settings";
    }

    @PostMapping("/platform-settings/session-timeout")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'PLATFORM_SETTINGS_UPDATE')")
    public String updateSessionTimeout(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @RequestParam int timeoutMinutes,
                                       RedirectAttributes ra) {
        try {
            platformSessionSettingsService.updateTimeout(timeoutMinutes, principal.getMemberId());
            ra.addFlashAttribute("message", "Session timeout settings updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/platform-settings";
    }

    @PostMapping("/platform-settings/logo-policy")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'PLATFORM_SETTINGS_UPDATE')")
    public String updateLogoPolicy(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @RequestParam int minWidthPx,
                                   @RequestParam int minHeightPx,
                                   @RequestParam int maxWidthPx,
                                   @RequestParam int maxHeightPx,
                                   @RequestParam int maxFileSizeKb,
                                   RedirectAttributes ra) {
        try {
            platformBrandingSettingsService.updateLogoPolicy(
                minWidthPx,
                minHeightPx,
                maxWidthPx,
                maxHeightPx,
                maxFileSizeKb,
                principal.getMemberId()
            );
            ra.addFlashAttribute("message", "Logo upload rules updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/platform-settings";
    }

    @PostMapping("/platform-settings/support-contact")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'PLATFORM_SETTINGS_UPDATE')")
    public String updateSupportContact(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @RequestParam(required = false) String displayName,
                                       @RequestParam(required = false) String displayRole,
                                       @RequestParam(required = false) String phone,
                                       @RequestParam(required = false) String email,
                                       @RequestParam(required = false) String officeHours,
                                       @RequestParam(required = false) String supportNote,
                                       RedirectAttributes ra) {
        try {
            platformSupportContactSettingsService.updateContact(
                displayName,
                displayRole,
                phone,
                email,
                officeHours,
                supportNote,
                principal.getMemberId()
            );
            ra.addFlashAttribute("message", "Platform support contact updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/platform-settings";
    }

    @PostMapping("/platform-settings/email")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'PLATFORM_SETTINGS_UPDATE')")
    public String updateEmailSettings(@AuthenticationPrincipal AppUserPrincipal principal,
                                      @RequestParam(defaultValue = "false") boolean enabled,
                                      @RequestParam(required = false) String host,
                                      @RequestParam int port,
                                      @RequestParam(required = false) String username,
                                      @RequestParam(required = false) String password,
                                      @RequestParam(required = false) String fromAddress,
                                      @RequestParam(required = false) String overrideRecipient,
                                      @RequestParam(defaultValue = "false") boolean sslEnabled,
                                      @RequestParam(defaultValue = "false") boolean starttlsEnabled,
                                      @RequestParam int connectionTimeoutMs,
                                      @RequestParam int readTimeoutMs,
                                      @RequestParam int writeTimeoutMs,
                                      RedirectAttributes ra) {
        try {
            platformEmailSettingsService.updateSettings(
                enabled,
                host,
                port,
                username,
                password,
                fromAddress,
                overrideRecipient,
                sslEnabled,
                starttlsEnabled,
                connectionTimeoutMs,
                readTimeoutMs,
                writeTimeoutMs,
                principal.getMemberId()
            );
            ra.addFlashAttribute("message", "Email delivery settings updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/platform-settings";
    }

    @PostMapping("/platform-settings/email/test")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'PLATFORM_SETTINGS_UPDATE')")
    public String sendTestEmail(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam String testRecipient,
                                RedirectAttributes ra) {
        try {
            platformEmailSettingsService.sendTestEmail(testRecipient, principal.getMemberId());
            ra.addFlashAttribute("message", "Test email sent.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", PlatformEmailSettingsService.describeTestSendFailure(ex));
        }
        return "redirect:/admin/platform-settings";
    }

    @PostMapping("/platform-settings/sms-gateway")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'PLATFORM_SETTINGS_UPDATE')")
    public String updateSmsGatewaySettings(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @RequestParam(defaultValue = "false") boolean enabled,
                                           @RequestParam(required = false) String baseUrl,
                                           @RequestParam(required = false) String sendPath,
                                           @RequestParam(required = false) String clientId,
                                           @RequestParam(required = false) String apiKey,
                                           @RequestParam(required = false) String senderId,
                                           @RequestParam int connectTimeoutSeconds,
                                           @RequestParam int readTimeoutSeconds,
                                           RedirectAttributes ra) {
        try {
            platformSmsGatewaySettingsService.updateSettings(
                enabled,
                baseUrl,
                sendPath,
                clientId,
                apiKey,
                senderId,
                connectTimeoutSeconds,
                readTimeoutSeconds,
                principal.getMemberId()
            );
            ra.addFlashAttribute("message", "SMS gateway settings updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/platform-settings";
    }

    @PostMapping("/platform-settings/sms-gateway/test")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'PLATFORM_SETTINGS_UPDATE')")
    public String sendTestSms(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam String testPhone,
                              RedirectAttributes ra) {
        try {
            platformSmsGatewaySettingsService.sendTestSms(testPhone, principal.getMemberId());
            ra.addFlashAttribute("message", "Test SMS sent.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", PlatformSmsGatewaySettingsService.describeTestSendFailure(ex));
        }
        return "redirect:/admin/platform-settings";
    }

    @PostMapping("/platform-settings/sacco-loan-top-up")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'PLATFORM_SETTINGS_UPDATE')")
    public String updateSaccoLoanTopUp(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @RequestParam String saccoId,
                                       @RequestParam(defaultValue = "false") boolean loanTopUpEnabled,
                                       RedirectAttributes ra) {
        try {
            saccoRegistryService.updateLoanTopUpFeature(saccoId, loanTopUpEnabled, principal.getMemberId());
            ra.addFlashAttribute("message", "Loan top-up access updated for " + saccoId + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/platform-settings";
    }

    @GetMapping(value = "/dashboard/database-utilization", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'ADMIN_DASHBOARD_VIEW')")
    @ResponseBody
    public DatabaseUtilizationService.DatabaseUtilizationPayload databaseUtilization(@AuthenticationPrincipal AppUserPrincipal principal) {
        return databaseUtilizationService.snapshot();
    }

    @GetMapping(value = "/dashboard/usage-activity", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'ADMIN_DASHBOARD_VIEW')")
    @ResponseBody
    public AppUsageAnalyticsService.UsageDashboardPayload usageActivity(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                        @RequestParam(defaultValue = "today") String range) {
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
        return appUsageAnalyticsService.dashboard(
            range,
            superAdmin ? null : adminScopeService.currentSaccoId(principal),
            superAdmin ? null : adminScopeService.currentStationId(principal)
        );
    }

    @GetMapping("/messages")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String messages(@AuthenticationPrincipal AppUserPrincipal principal,
                            @RequestParam(required = false) IncidentStatus status,
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
                return status == null || incident.getStatus() == status;
            })
            .toList();
        model.addAttribute("messages", filteredMessages);
        model.addAttribute("messageIncidents", messageIncidents);
        model.addAttribute("members", adminService.activeMembers(adminScopeService.currentSaccoId(principal), stationId));
        model.addAttribute("highlightNotificationId", highlight);
        model.addAttribute("incidentStatuses", IncidentStatus.values());
        model.addAttribute("selectedStatus", status == null ? "" : status.name());
        return "admin/messages";
    }

    @GetMapping("/messages/{id}/open")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String openMessage(@PathVariable UUID id,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(id, principal, "/admin/messages");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/messages";
        }
    }

    @GetMapping("/notifications")
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'NOTIFICATIONS_VIEW')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        model.addAttribute("notifications", notificationInboxService.allViews(principal));
        model.addAttribute("highlightNotificationId", highlight);
        return "admin/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'NOTIFICATIONS_VIEW')")
    public String openAdminNotification(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUserPrincipal principal,
                                        RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(id, principal, principal.isPlatformIdentity() ? "/admin/incidents" : "/admin/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return principal != null && principal.isPlatformIdentity()
                ? "redirect:/admin/incidents"
                : "redirect:/admin/notifications";
        }
    }

    @PostMapping("/messages/mark-all-read")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'NOTIFICATIONS_UPDATE') and @access.has(principal, 'SUPPORT_VIEW')")
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
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'NOTIFICATIONS_UPDATE')")
    public String markAllAdminNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                                RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        ra.addFlashAttribute("message", updated > 0
            ? "All notifications have been marked as read."
            : "There were no unread notifications.");
        return principal != null && principal.isPlatformIdentity()
            ? "redirect:/admin/incidents"
            : "redirect:/admin/notifications";
    }

    @GetMapping("/incidents")
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String incidents(@AuthenticationPrincipal AppUserPrincipal principal,
                             @RequestParam(required = false) IncidentStatus status,
                             @RequestParam(required = false) String saccoId,
                             @RequestParam(required = false) String stationId,
                            Model model) {
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
        String scopedSaccoId = superAdmin ? normalizeTextParam(saccoId) : adminScopeService.currentSaccoId(principal);
        String scopedStationId = superAdmin ? normalizeTextParam(stationId) : adminScopeService.currentStationId(principal);
        model.addAttribute("incidents", superAdmin
            ? adminService.platformSupportIncidents(
                scopedSaccoId.isBlank() ? null : scopedSaccoId,
                scopedStationId.isBlank() ? null : scopedStationId,
                status,
                null
            )
            : adminService.incidents(
                scopedSaccoId.isBlank() ? null : scopedSaccoId,
                scopedStationId.isBlank() ? null : scopedStationId,
                status,
                null
            ));
        addAdminScopeFilters(model, scopedSaccoId, scopedStationId, superAdmin);
        if (superAdmin) {
            model.addAttribute("minorAdmins", adminService.minorAdmins());
        }
        model.addAttribute("incidentStatuses", IncidentStatus.values());
        model.addAttribute("selectedStatus", status == null ? "" : status.name());
        return "admin/incidents";
    }

    @GetMapping("/incidents/{id}")
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String incidentDetail(@PathVariable UUID id,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false) String saccoId,
                                 @RequestParam(required = false) String stationId,
                                 Model model) {
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
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
        return "admin/incident-detail";
    }

    @PostMapping("/incidents/{id}")
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'SUPPORT_UPDATE')")
    public String updateIncident(@PathVariable UUID id,
                                  @AuthenticationPrincipal AppUserPrincipal principal,
                                  @RequestParam IncidentStatus status,
                                  @RequestParam(required = false) String resolutionNote,
                                  RedirectAttributes ra) {
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
        if (superAdmin) {
            adminService.platformSupportIncident(null, null, id);
        }
        adminService.updateIncident(
            superAdmin ? null : adminScopeService.currentSaccoId(principal),
            superAdmin ? null : adminScopeService.currentStationId(principal),
            principal.getMemberId(),
            id,
            status,
            resolutionNote
        );
        ra.addFlashAttribute("message", "Incident updated.");
        return "redirect:/admin/incidents/" + id;
    }

    @PostMapping("/incidents/{id}/reply")
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'SUPPORT_UPDATE')")
    public String replyToSupportReporter(@PathVariable UUID id,
                                         @AuthenticationPrincipal AppUserPrincipal principal,
                                         @RequestParam String subject,
                                         @RequestParam String message,
                                         RedirectAttributes ra) {
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
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
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SUPPORT_UPDATE')")
    public String broadcastToMinorAdmins(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @RequestParam String subject,
                                         @RequestParam String message,
                                         RedirectAttributes ra) {
        adminService.broadcastToMinorAdmins(principal.getMemberId(), subject, message);
        ra.addFlashAttribute("message", "Broadcast sent to SACCOS admins.");
        return "redirect:/admin/incidents";
    }

    @PostMapping("/incidents/reply-minor-admin")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SUPPORT_UPDATE')")
    public String replyToMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam UUID memberId,
                                    @RequestParam String subject,
                                    @RequestParam String message,
                                    RedirectAttributes ra) {
        adminService.replyToMinorAdmin(principal.getMemberId(), memberId, subject, message);
        ra.addFlashAttribute("message", "Reply sent to the SACCOS admin.");
        return "redirect:/admin/incidents";
    }

    @GetMapping("/support")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String adminSupport() {
        return "admin/support";
    }

    @GetMapping("/support/archive")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String adminSupportArchive(@AuthenticationPrincipal AppUserPrincipal principal,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                      @RequestParam(defaultValue = "0") int page,
                                      Model model) {
        ArchiveDateRange dateRange = ArchiveDateRange.inclusive(fromDate, toDate, applicationClock);
        var archivePage = adminService.platformSupportArchive(
            principal.getMemberId(), dateRange.fromDate(), dateRange.toDate(), page);
        model.addAttribute("supportArchive", archivePage.getContent());
        model.addAttribute("archivePage", archivePage);
        model.addAttribute("fromDate", dateRange.fromDate());
        model.addAttribute("toDate", dateRange.toDate());
        return "admin/support-archive";
    }

    @GetMapping("/support/replies")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String adminSupportReplies(@AuthenticationPrincipal AppUserPrincipal principal,
                                      @RequestParam(required = false) UUID highlight,
                                      Model model) {
        model.addAttribute("replies", notificationInboxService.allViewsByType(principal.getMemberId(), "ADMIN_REPLY"));
        model.addAttribute("broadcasts", notificationInboxService.allViewsByType(principal.getMemberId(), "ADMIN_BROADCAST"));
        model.addAttribute("highlightNotificationId", highlight);
        return "admin/support-replies";
    }

    @GetMapping("/support/replies/{id}/open")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'SUPPORT_VIEW')")
    public String openAdminSupportReply(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUserPrincipal principal,
                                        RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(id, principal, "/admin/support/replies");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/support/replies";
        }
    }

    @PostMapping("/support/replies/mark-all-read")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'NOTIFICATIONS_UPDATE') and @access.has(principal, 'SUPPORT_VIEW')")
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
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'SUPPORT_CREATE')")
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
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'SUPPORT_UPDATE')")
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
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'SUPPORT_UPDATE')")
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
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'USER_ACCESS_VIEW')")
    public String users(@AuthenticationPrincipal AppUserPrincipal principal,
                        @RequestParam(required = false) String query,
                        @RequestParam(required = false, defaultValue = "userId") String searchBy,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "25") int size,
                        Model model) {
        String selectedSearchBy = normalizeUsersSearchBy(searchBy);
        Page<AdminService.UserAccessView> usersPage = adminService.usersPage(
            adminScopeService.currentSaccoId(principal),
            adminScopeService.currentStationId(principal),
            selectedSearchBy,
            query,
            page,
            size
        );
        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("usersPage", usersPage);
        model.addAttribute("selectedUserQuery", query == null ? "" : query.trim());
        model.addAttribute("selectedUserSearchBy", selectedSearchBy);
        model.addAttribute("selectedPageSize", usersPage.getSize());
        model.addAttribute("usersPaginationQuery", buildUsersPaginationQuery(query, selectedSearchBy, usersPage.getSize()));
        model.addAttribute("staffPositions", staffPositionsFor(principal));
        model.addAttribute("canEditUsers", principal != null && principal.getClaims().contains(UserClaim.USER_ACCESS_UPDATE.name()));
        return "admin/users";
    }

    @GetMapping("/users/{id}/edit")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'USER_ACCESS_UPDATE')")
    public String editUser(@PathVariable UUID id,
                           @AuthenticationPrincipal AppUserPrincipal principal,
                           Model model,
                           RedirectAttributes ra) {
        try {
            AdminService.UserAccessView user = adminService.userAccess(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                id
            );
            List<Position> staffPositions = staffPositionsFor(principal);
            model.addAttribute("user", user);
            model.addAttribute("staffPositions", staffPositions);
            model.addAttribute("accessActions", com.sacco.mvp.domain.AccessAction.values());
            model.addAttribute("accessMatrixRows", UserClaim.matrixRows());
            model.addAttribute("statuses", MemberStatus.values());
            model.addAttribute("roleDefaultClaimsJson", roleDefaultClaimsJson(staffPositions));
            model.addAttribute("memberDefaultClaimsJson", claimsJson(UserClaim.defaultClaims(List.of(), true)));
            model.addAttribute("canDeleteUsers", principal != null && principal.getClaims().contains(UserClaim.USER_ACCESS_DELETE.name()));
            return "admin/user-edit";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/users";
        }
    }

    @PostMapping("/users")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'USER_ACCESS_CREATE')")
    public String createUser(@AuthenticationPrincipal AppUserPrincipal principal,
                             @RequestParam String fullName,
                             @RequestParam String email,
                             @RequestParam(required = false) String phone,
                             @RequestParam(name = "positions", required = false) java.util.List<Position> positions,
                             RedirectAttributes ra) {
        try {
            var createdUser = adminService.createUser(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                principal.getMemberId(),
                principal.getGrantedPositions(),
                fullName,
                email,
                phone,
                positions
            );
            String createdUserId = adminService.userIdLabel(createdUser.getId());
            ra.addFlashAttribute("createdStaffUserId", createdUserId);
            ra.addFlashAttribute("createdStaffMemberNumber", createdUser.getStaffNo());
            ra.addFlashAttribute("message", "Staff member created with User ID " + createdUserId + " and Staff Number " + createdUser.getStaffNo() + ". Invite email sent and waiting for activation.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'USER_ACCESS_UPDATE')")
    public String updateUser(@PathVariable UUID id,
                             @AuthenticationPrincipal AppUserPrincipal principal,
                             @RequestParam(name = "positions", required = false) java.util.List<Position> positions,
                             @RequestParam(name = "claims", required = false) java.util.List<UserClaim> claims,
                             @RequestParam MemberStatus status,
                             RedirectAttributes ra) {
        try {
            AdminService.UserUpdateResult result = adminService.updateUser(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                principal.getMemberId(),
                principal.getGrantedPositions(),
                id,
                positions,
                status,
                claims
            );
            if (result.isStaffAccessPending()) {
                ra.addFlashAttribute("message", "User updated. Staff Number " + result.getStaffNo() + " is pending member acknowledgement.");
            } else {
                ra.addFlashAttribute("message", "User updated.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users/" + id + "/edit";
    }

    @PostMapping("/users/{id}/cancel-invite")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'USER_ACCESS_UPDATE')")
    public String cancelStaffInvite(@PathVariable UUID id,
                                    @AuthenticationPrincipal AppUserPrincipal principal,
                                    RedirectAttributes ra) {
        try {
            adminService.cancelStaffInvitation(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                principal.getMemberId(),
                principal.getGrantedPositions(),
                id
            );
            ra.addFlashAttribute("message", "Invitation cancelled. The staff member record can now be deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users/" + id + "/edit";
    }

    @PostMapping("/users/{id}/delete")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'USER_ACCESS_DELETE')")
    public String deleteStaffUser(@PathVariable UUID id,
                                  @AuthenticationPrincipal AppUserPrincipal principal,
                                  @RequestParam String confirmation,
                                  RedirectAttributes ra) {
        try {
            saccoDataDeletionService.deleteInactiveStaffMember(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                principal.getGrantedPositions(),
                id,
                confirmation
            );
            ra.addFlashAttribute("message", "Staff member record deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            ra.addFlashAttribute("openUserModalKey", "delete-user-" + id);
            return "redirect:/admin/users/" + id + "/edit";
        }
        return "redirect:/admin/users";
    }

    @GetMapping({"/loan-products", "/settings-controls"})
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.hasAny(principal, 'WORKSPACE_SETTINGS_VIEW', 'LOAN_PRODUCTS_VIEW', 'APPROVAL_FLOW_VIEW')")
    public String loanProducts(@AuthenticationPrincipal AppUserPrincipal principal,
                               @RequestParam(required = false, defaultValue = "loan") String section,
                               @RequestParam(required = false) String modal,
                               Model model) {
        String saccoId = adminScopeService.currentSaccoId(principal);
        List<LoanProductSetting> products = adminService.loanProducts(saccoId);
        model.addAttribute("products", products);
        model.addAttribute("requiredAttachmentsByProductId", requiredAttachmentService.activeByProduct(
            products.stream().map(LoanProductSetting::getId).toList()));
        model.addAttribute("customizedProductExists", false);
        var settings = adminService.settings(saccoId);
        var stationPolicy = adminService.stationQualificationPolicy(saccoId, adminScopeService.currentStationId(principal)).orElse(null);
        model.addAttribute("settings", settings);
        var stationOtpConfiguration = stationOtpSettingsService.configuration(
            saccoId,
            adminScopeService.currentStationId(principal)
        );
        model.addAttribute("stationOtpDeliveryChannel", stationOtpConfiguration.deliveryChannel());
        model.addAttribute("otpDeliveryChannels", OtpDeliveryChannel.values());
        model.addAttribute("stationOtpSelectionPolicy", stationOtpConfiguration.selectionPolicy());
        model.addAttribute("currentStationPolicy", stationPolicy);
        model.addAttribute("policyStationId", adminScopeService.currentStationId(principal));
        model.addAttribute("policyApplicantMaxDefaultedLoans", stationPolicy == null ? settings.getApplicantMaxDefaultedLoans() : stationPolicy.getApplicantMaxDefaultedLoans());
        Boolean guarantorWithActiveLoanAllowed = stationPolicy == null ? settings.getGuarantorWithActiveLoanAllowed() : stationPolicy.getGuarantorWithActiveLoanAllowed();
        model.addAttribute("policyGuarantorWithActiveLoanAllowed", guarantorWithActiveLoanAllowed == null || guarantorWithActiveLoanAllowed);
        model.addAttribute("policyGuarantorMaxGuaranteedLoanAmount", stationPolicy == null ? settings.getGuarantorMaxGuaranteedLoanAmount() : stationPolicy.getGuarantorMaxGuaranteedLoanAmount());
        model.addAttribute("policyGuarantorMaxDefaultedLoans", stationPolicy == null ? settings.getGuarantorMaxDefaultedLoans() : stationPolicy.getGuarantorMaxDefaultedLoans());
        Integer portfolioAtRiskDays = stationPolicy == null || stationPolicy.getPortfolioAtRiskDays() == null
            ? settings.getPortfolioAtRiskDays()
            : stationPolicy.getPortfolioAtRiskDays();
        model.addAttribute("policyPortfolioAtRiskDays", portfolioAtRiskDays == null ? 30 : portfolioAtRiskDays);
        model.addAttribute("activeBoardMemberCount", adminService.activeBoardMemberCount(saccoId));
        model.addAttribute("activeCreditCommitteeMemberCount", adminService.activeCreditCommitteeMemberCount(saccoId));
        model.addAttribute("activeChairpersonCount", adminService.activeChairpersonCount(saccoId));
        model.addAttribute("boardReviewerOptions", adminService.activeBoardReviewerOptions(saccoId));
        model.addAttribute("creditCommitteeReviewerOptions", adminService.activeCreditCommitteeReviewerOptions(saccoId));
        model.addAttribute("productBoardReviewerIdTokens", adminService.loanProductBoardReviewerIdTokens(saccoId));
        model.addAttribute("productCreditCommitteeReviewerIdTokens", adminService.loanProductCreditCommitteeReviewerIdTokens(saccoId));
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
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'LOAN_PRODUCTS_UPDATE')")
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
                                    @RequestParam(defaultValue = "false") boolean savingsLimitCheckRequired,
                                    @RequestParam BigDecimal applicationFee,
                                    @RequestParam BigDecimal insurancePercent,
                                    @RequestParam BigDecimal processingFeePercent,
                                    @RequestParam BigDecimal annualInterestPercent,
                                    @RequestParam(defaultValue = "FLAT_RATE") InterestMethod interestMethod,
                                    @RequestParam Integer minRepaymentMonths,
                                    @RequestParam Integer maxRepaymentMonths,
                                    @RequestParam(defaultValue = "false") boolean allowApplicationWithActiveLoan,
                                    @RequestParam(defaultValue = "false") boolean freshFinancialDataRequired,
                                    @RequestParam(defaultValue = "false") boolean managerReviewRequired,
                                    @RequestParam(defaultValue = "false") boolean loanOfficerReviewRequired,
                                    @RequestParam(defaultValue = "MANAGER") ApprovalWorkflowStage workflowStartStage,
                                    @RequestParam(defaultValue = "false") boolean chairpersonReviewRequired,
                                    @RequestParam(required = false) Integer chairpersonPriority,
                                    @RequestParam(name = "chairpersonReviewerIds", required = false) java.util.List<UUID> chairpersonReviewerIds,
                                    @RequestParam(defaultValue = "false") boolean boardReviewRequired,
                                    @RequestParam(required = false) Integer boardPriority,
                                    @RequestParam(name = "boardReviewerIds", required = false) java.util.List<UUID> boardReviewerIds,
                                    @RequestParam(defaultValue = "false") boolean committeeReviewRequired,
                                    @RequestParam(required = false) Integer committeePriority,
                                    @RequestParam(required = false) Integer committeeMinimumVotes,
                                    @RequestParam(required = false) Integer committeeApprovalThreshold,
                                    @RequestParam(name = "creditCommitteeReviewerIds", required = false) java.util.List<UUID> creditCommitteeReviewerIds,
                                    @RequestParam(defaultValue = "true") boolean accountantReviewRequired,
                                    @RequestParam(required = false) Integer accountantPriority,
                                    @RequestParam(defaultValue = "true") boolean disbursementOfficerRequired,
                                    @RequestParam(defaultValue = "true") boolean disbursementProofRequired,
                                    @RequestParam(defaultValue = "false") boolean applicantAttachmentRequired,
                                    @RequestParam(defaultValue = "1") Integer managerPriority,
                                    @RequestParam(defaultValue = "2") Integer loanOfficerPriority,
                                    @RequestParam(defaultValue = "false") boolean guarantorMinSavingsCheckRequired,
                                    @RequestParam(required = false) BigDecimal guarantorMinimumSavings,
                                    @RequestParam(defaultValue = "ACTIVE") LoanProductStatus productStatus,
                                    @RequestParam(required = false) String modalKey,
                                    HttpServletRequest request,
                                    RedirectAttributes ra) {
        String resolvedModalKey = normalizeLoanSettingsModalKey(modalKey) == null ? "product-" + id : normalizeLoanSettingsModalKey(modalKey);
        try {
            BigDecimal maxLoanSavingsRatio = percentToRatio(maxLoanSavingsPercent);
            BigDecimal processingFeeRate = percentToRatio(processingFeePercent);
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
                maxLoanSavingsRatio, savingsLimitCheckRequired, applicationFee, insuranceRate, processingFeeRate, annualRate, interestMethod, minRepaymentMonths, maxRepaymentMonths,
                allowApplicationWithActiveLoan, freshFinancialDataRequired, managerReviewRequired, loanOfficerReviewRequired,
                resolvedWorkflowStartStage, managerPriority, loanOfficerPriority, chairpersonReviewRequired, chairpersonPriority, chairpersonReviewerIds,
                boardReviewRequired, boardPriority, boardReviewerIds,
                committeeReviewRequired, committeePriority, committeeMinimumVotes,
                committeeApprovalThreshold, creditCommitteeReviewerIds, accountantReviewRequired, accountantPriority, disbursementOfficerRequired,
                disbursementProofRequired, applicantAttachmentRequired, guarantorMinSavingsCheckRequired, guarantorMinimumSavings, productStatus);
            ra.addFlashAttribute("message", "Loan product updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            attachLoanSettingsValidationFeedback(ra, ex.getMessage());
            attachLoanProductFormDraft(ra, request);
        }
        return "redirect:/admin/settings-controls/loan-products/" + id + "/edit";
    }

    @GetMapping("/settings-controls/loan-products/{id}/edit")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'LOAN_PRODUCTS_VIEW')")
    public String editLoanProduct(@PathVariable UUID id,
                                  @AuthenticationPrincipal AppUserPrincipal principal,
                                  Model model) {
        String saccoId = adminScopeService.currentSaccoId(principal);
        LoanProductSetting product = adminService.loanProduct(saccoId, id);
        var settings = adminService.settings(saccoId);
        model.addAttribute("product", product);
        model.addAttribute("settings", settings);
        model.addAttribute("requiredAttachments", requiredAttachmentService.activeForProduct(id));
        model.addAttribute("requiredAttachmentsByProductId", Map.of(id, requiredAttachmentService.activeForProduct(id)));
        model.addAttribute("activeBoardMemberCount", adminService.activeBoardMemberCount(saccoId));
        model.addAttribute("activeCreditCommitteeMemberCount", adminService.activeCreditCommitteeMemberCount(saccoId));
        model.addAttribute("activeChairpersonCount", adminService.activeChairpersonCount(saccoId));
        model.addAttribute("boardReviewerOptions", adminService.activeBoardReviewerOptions(saccoId));
        model.addAttribute("creditCommitteeReviewerOptions", adminService.activeCreditCommitteeReviewerOptions(saccoId));
        model.addAttribute("productBoardReviewerIdTokens", adminService.loanProductBoardReviewerIdTokens(saccoId));
        model.addAttribute("productCreditCommitteeReviewerIdTokens", adminService.loanProductCreditCommitteeReviewerIdTokens(saccoId));
        model.addAttribute("activeLoanOfficerCount", adminService.activeLoanOfficerCount(saccoId));
        model.addAttribute("activeAccountantCount", adminService.activeAccountantCount(saccoId));
        model.addAttribute("activeDisbursementOfficerCount", adminService.activeDisbursementOfficerCount(saccoId));
        model.addAttribute("activeDisbursementClaimHolderCount", adminService.activeDisbursementClaimHolderCount(saccoId));
        model.addAttribute("suppressToastMessages", true);
        return "admin/loan-product-edit";
    }

    @PostMapping("/settings-controls/loan-products/{id}/required-attachments")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'LOAN_PRODUCTS_CONFIGURE')")
    public String createRequiredAttachment(@PathVariable UUID id,
                                           @AuthenticationPrincipal AppUserPrincipal principal,
                                           @RequestParam(required = false) String attachmentName,
                                           @RequestParam(required = false) BigDecimal maxSizeMb,
                                           RedirectAttributes ra) {
        try {
            String saccoId = adminScopeService.currentSaccoId(principal);
            adminService.loanProduct(saccoId, id);
            adminService.updateApplicantAttachmentRequired(saccoId, principal.getMemberId(), id, true);
            requiredAttachmentService.createForProduct(id, attachmentName, maxSizeMb);
            ra.addFlashAttribute("message", "Required attachment created.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings-controls/loan-products/" + id + "/edit";
    }

    @PostMapping("/settings-controls/loan-products/{id}/required-attachments/{requirementId}/delete")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'LOAN_PRODUCTS_CONFIGURE')")
    public String deleteRequiredAttachment(@PathVariable UUID id,
                                           @PathVariable UUID requirementId,
                                           @AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        try {
            adminService.loanProduct(adminScopeService.currentSaccoId(principal), id);
            requiredAttachmentService.deleteForProduct(id, requirementId);
            ra.addFlashAttribute("message", "Required attachment deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings-controls/loan-products/" + id + "/edit";
    }

    @PostMapping("/settings-controls/loan-products/{id}/delete")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'LOAN_PRODUCTS_DELETE')")
    public String deleteLoanProduct(@PathVariable UUID id,
                                    @AuthenticationPrincipal AppUserPrincipal principal,
                                    RedirectAttributes ra) {
        try {
            adminService.archiveLoanProduct(adminScopeService.currentSaccoId(principal), principal.getMemberId(), id);
            ra.addFlashAttribute("message", "Loan product deleted from active settings. Historical loans remain preserved.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings-controls?section=loan";
    }

    @PostMapping({"/loan-products", "/settings-controls/loan-products"})
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'LOAN_PRODUCTS_CREATE')")
    public String createLoanProduct(@AuthenticationPrincipal AppUserPrincipal principal,
                                              @RequestParam(required = false) String productCode,
                                              @RequestParam(required = false) String productName,
                                              @RequestParam(required = false) String productDescription,
                                              @RequestParam Integer displayOrder,
                                              @RequestParam BigDecimal minimumAmount,
                                              @RequestParam(required = false) BigDecimal maximumAmount,
                                              @RequestParam Integer guarantorsRequired,
                                              @RequestParam BigDecimal maxLoanSavingsPercent,
                                              @RequestParam(defaultValue = "false") boolean savingsLimitCheckRequired,
                                              @RequestParam BigDecimal applicationFee,
                                              @RequestParam BigDecimal insurancePercent,
                                              @RequestParam BigDecimal processingFeePercent,
                                              @RequestParam BigDecimal annualInterestPercent,
                                              @RequestParam(defaultValue = "FLAT_RATE") InterestMethod interestMethod,
                                              @RequestParam Integer minRepaymentMonths,
                                              @RequestParam Integer maxRepaymentMonths,
                                              @RequestParam(defaultValue = "false") boolean allowApplicationWithActiveLoan,
                                              @RequestParam(defaultValue = "false") boolean freshFinancialDataRequired,
                                              @RequestParam(defaultValue = "false") boolean managerReviewRequired,
                                              @RequestParam(defaultValue = "false") boolean loanOfficerReviewRequired,
                                              @RequestParam(defaultValue = "MANAGER") ApprovalWorkflowStage workflowStartStage,
                                              @RequestParam(defaultValue = "false") boolean chairpersonReviewRequired,
                                              @RequestParam(required = false) Integer chairpersonPriority,
                                              @RequestParam(name = "chairpersonReviewerIds", required = false) java.util.List<UUID> chairpersonReviewerIds,
                                              @RequestParam(defaultValue = "false") boolean boardReviewRequired,
                                              @RequestParam(required = false) Integer boardPriority,
                                              @RequestParam(name = "boardReviewerIds", required = false) java.util.List<UUID> boardReviewerIds,
                                              @RequestParam(defaultValue = "false") boolean committeeReviewRequired,
                                              @RequestParam(required = false) Integer committeePriority,
                                              @RequestParam(required = false) Integer committeeMinimumVotes,
                                              @RequestParam(required = false) Integer committeeApprovalThreshold,
                                              @RequestParam(name = "creditCommitteeReviewerIds", required = false) java.util.List<UUID> creditCommitteeReviewerIds,
                                              @RequestParam(defaultValue = "true") boolean accountantReviewRequired,
                                              @RequestParam(required = false) Integer accountantPriority,
                                              @RequestParam(defaultValue = "true") boolean disbursementOfficerRequired,
                                              @RequestParam(defaultValue = "true") boolean disbursementProofRequired,
                                              @RequestParam(defaultValue = "false") boolean applicantAttachmentRequired,
                                              @RequestParam(name = "requiredAttachmentName", required = false) List<String> requiredAttachmentNames,
                                              @RequestParam(name = "requiredAttachmentMaxSizeMb", required = false) List<BigDecimal> requiredAttachmentMaxSizeMb,
                                              @RequestParam(defaultValue = "1") Integer managerPriority,
                                              @RequestParam(defaultValue = "2") Integer loanOfficerPriority,
                                              @RequestParam(defaultValue = "false") boolean guarantorMinSavingsCheckRequired,
                                              @RequestParam(required = false) BigDecimal guarantorMinimumSavings,
                                              @RequestParam(defaultValue = "ACTIVE") LoanProductStatus productStatus,
                                              @RequestParam(required = false) String modalKey,
                                              HttpServletRequest request,
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
            LoanProductSetting product = adminService.createLoanProduct(
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
                savingsLimitCheckRequired,
                applicationFee,
                percentToRatio(insurancePercent),
                percentToRatio(processingFeePercent),
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
                chairpersonReviewRequired,
                chairpersonPriority,
                chairpersonReviewerIds,
                boardReviewRequired,
                boardPriority,
                boardReviewerIds,
                committeeReviewRequired,
                committeePriority,
                committeeMinimumVotes,
                committeeApprovalThreshold,
                creditCommitteeReviewerIds,
                accountantReviewRequired,
                accountantPriority,
                disbursementOfficerRequired,
                disbursementProofRequired,
                applicantAttachmentRequired,
                guarantorMinSavingsCheckRequired,
                guarantorMinimumSavings,
                productStatus
            );
            requiredAttachmentService.replaceForProduct(product.getId(), applicantAttachmentRequired ? requiredAttachmentNames : List.of(), requiredAttachmentMaxSizeMb);
            ra.addFlashAttribute("message", "Loan product created successfully.");
            ra.addFlashAttribute("createdLoanProductId", product.getId());
            return "redirect:/admin/settings-controls?section=loan";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            attachLoanSettingsValidationFeedback(ra, ex.getMessage());
            attachLoanProductFormDraft(ra, request);
        }
        return loanSettingsRedirect(resolvedModalKey);
    }

    @PostMapping("/settings-controls/loan-rules")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_UPDATE')")
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
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_UPDATE')")
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
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_UPDATE')")
    public String updateOtpDelivery(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam OtpDeliveryChannel otpDeliveryChannel,
                                    @RequestParam OtpSelectionPolicy otpSelectionPolicy,
                                    RedirectAttributes ra) {
        try {
            stationOtpSettingsService.update(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                otpDeliveryChannel,
                otpSelectionPolicy,
                principal.getMemberId()
            );
            ra.addFlashAttribute("message", "OTP settings updated for this station.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings-controls?section=otp";
    }

    @PostMapping("/settings-controls/qualification-policies")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_UPDATE')")
    public String updateQualificationPolicies(@AuthenticationPrincipal AppUserPrincipal principal,
                                              @RequestParam(required = false) Integer applicantMaxDefaultedLoans,
                                              @RequestParam(defaultValue = "false") boolean guarantorWithActiveLoanAllowed,
                                              @RequestParam(required = false) BigDecimal guarantorMaxGuaranteedLoanAmount,
                                              @RequestParam(required = false) Integer guarantorMaxDefaultedLoans,
                                              @RequestParam(required = false) Integer portfolioAtRiskDays,
                                              RedirectAttributes ra) {
        try {
            adminService.updateStationQualificationPolicies(
                adminScopeService.currentSaccoId(principal),
                adminScopeService.currentStationId(principal),
                principal.getMemberId(),
                applicantMaxDefaultedLoans,
                guarantorWithActiveLoanAllowed,
                guarantorMaxGuaranteedLoanAmount,
                guarantorMaxDefaultedLoans,
                portfolioAtRiskDays
            );
            ra.addFlashAttribute("message", "Station qualification policies updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings-controls?section=guarantor";
    }

    @PostMapping("/settings-controls/station-qualification-policies")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_UPDATE')")
    public String updateStationQualificationPolicies(@AuthenticationPrincipal AppUserPrincipal principal,
                                                     @RequestParam String stationId,
                                                     @RequestParam(required = false) Integer applicantMaxDefaultedLoans,
                                                     @RequestParam(defaultValue = "false") boolean guarantorWithActiveLoanAllowed,
                                                     @RequestParam(required = false) BigDecimal guarantorMaxGuaranteedLoanAmount,
                                                     @RequestParam(required = false) Integer guarantorMaxDefaultedLoans,
                                                     @RequestParam(required = false) Integer portfolioAtRiskDays,
                                                     RedirectAttributes ra) {
        try {
            adminService.updateStationQualificationPolicies(
                adminScopeService.currentSaccoId(principal),
                stationId,
                principal.getMemberId(),
                applicantMaxDefaultedLoans,
                guarantorWithActiveLoanAllowed,
                guarantorMaxGuaranteedLoanAmount,
                guarantorMaxDefaultedLoans,
                portfolioAtRiskDays
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
        if (resolvedManagerPriority < 1 || resolvedManagerPriority > 6) {
            throw new IllegalStateException("Manager priority must be between 1 and 6.");
        }
        if (resolvedLoanOfficerPriority < 1 || resolvedLoanOfficerPriority > 6) {
            throw new IllegalStateException("Loan Officer priority must be between 1 and 6.");
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
            return workflowStartStage == ApprovalWorkflowStage.LOAN_OFFICER
                ? ApprovalWorkflowStage.LOAN_OFFICER
                : ApprovalWorkflowStage.MANAGER;
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

    private void attachLoanProductFormDraft(RedirectAttributes ra, HttpServletRequest request) {
        if (request == null) {
            return;
        }
        Map<String, List<String>> draft = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (name == null || name.isBlank() || "_csrf".equals(name) || values == null || values.length == 0) {
                return;
            }
            List<String> submittedValues = new ArrayList<>(values.length);
            for (String value : values) {
                submittedValues.add(value == null ? "" : value);
            }
            draft.put(name, submittedValues);
        });
        LOAN_PRODUCT_BOOLEAN_FIELDS.forEach(name ->
            draft.put(name, List.of(Boolean.toString(requestHasTruthyParameter(request, name)))));
        ra.addFlashAttribute("loanProductFormDraft", draft);
    }

    private boolean requestHasTruthyParameter(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "1".equals(value)) {
                return true;
            }
        }
        return false;
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
            case "Enter a loan product description." ->
                fieldErrors.put("productDescription", "Enter a product description.");
            case "Loan product description must be 500 characters or fewer." ->
                fieldErrors.put("productDescription", "Keep the description within 500 characters.");
            case "Display order must be at least 1." ->
                fieldErrors.put("displayOrder", "Display order must be 1 or higher.");
            case "Minimum amount cannot be negative." ->
                fieldErrors.put("minimumAmount", "Minimum amount cannot be negative.");
            case "Maximum amount must be greater than zero.",
                 "Maximum amount cannot be lower than the minimum amount." ->
                fieldErrors.put("maximumAmount", message);
            case "Savings ratio cannot be negative." ->
                fieldErrors.put("maxLoanSavingsPercent", "Loan savings multiple cannot be negative.");
            case "Savings ratio cannot exceed ten times savings." ->
                fieldErrors.put("maxLoanSavingsPercent", "Loan savings multiple cannot be more than 10x savings.");
            case "Attachment maximum size must be greater than zero.",
                 "Attachment maximum size cannot be more than 100 MB." ->
                fieldErrors.put("requiredAttachmentMaxSizeMb", message);
            case "Attachment name must be 120 characters or fewer." ->
                fieldErrors.put("requiredAttachmentName", message);
            case "Guarantors required cannot be negative." ->
                fieldErrors.put("guarantorsRequired", "Guarantors required cannot be negative.");
            case "Guarantors required must be between 0 and 15." ->
                fieldErrors.put("guarantorsRequired", "Guarantors required must be between 0 and 15.");
            case "Minimum guarantor savings cannot be negative." ->
                fieldErrors.put("guarantorMinimumSavings", "Minimum guarantor savings cannot be negative.");
            case "Insurance rate cannot be negative." ->
                fieldErrors.put("insurancePercent", "Insurance percentage cannot be negative.");
            case "Loan processing fee percentage cannot be negative." ->
                fieldErrors.put("processingFeePercent", "Loan processing fee percentage cannot be negative.");
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
            case "Manager priority must be between 1 and 6." ->
                fieldErrors.put("managerPriority", "Manager priority must be between 1 and 6.");
            case "Loan Officer priority must be between 1 and 6." ->
                fieldErrors.put("loanOfficerPriority", "Loan Officer priority must be between 1 and 6.");
            case "Review priority must be between 1 and 6." -> {
                fieldErrors.put("managerPriority", "Review priority must be between 1 and 6.");
                fieldErrors.put("loanOfficerPriority", "Review priority must be between 1 and 6.");
            }
            case "Manager and Loan Officer cannot share the same priority slot." -> {
                fieldErrors.put("managerPriority", "Choose different priorities for Manager and Loan Officer.");
                fieldErrors.put("loanOfficerPriority", "Choose different priorities for Manager and Loan Officer.");
            }
            case "Stage priority must be between 1 and 6." -> {
                fieldErrors.put("boardPriority", "Stage priority must be between 1 and 6.");
                fieldErrors.put("committeePriority", "Stage priority must be between 1 and 6.");
                fieldErrors.put("accountantPriority", "Stage priority must be between 1 and 6.");
            }
            case "Board Member and Credit Committee cannot share the same priority slot." -> {
                fieldErrors.put("boardPriority", message);
                fieldErrors.put("committeePriority", message);
            }
            case "Board Member and Accountant cannot share the same priority slot." -> {
                fieldErrors.put("boardPriority", message);
                fieldErrors.put("accountantPriority", message);
            }
            case "Manager and Board Member cannot share the same priority slot." -> {
                fieldErrors.put("managerPriority", message);
                fieldErrors.put("boardPriority", message);
            }
            case "Loan Officer and Board Member cannot share the same priority slot." -> {
                fieldErrors.put("loanOfficerPriority", message);
                fieldErrors.put("boardPriority", message);
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
            case "Chairperson and Board Member cannot share the same priority slot." -> {
                fieldErrors.put("chairpersonPriority", "Chairperson and Board Member cannot share the same priority slot.");
                fieldErrors.put("boardPriority", "Chairperson and Board Member cannot share the same priority slot.");
            }
            case "Chairperson and Credit Committee cannot share the same priority slot." -> {
                fieldErrors.put("chairpersonPriority", "Chairperson and Credit Committee cannot share the same priority slot.");
                fieldErrors.put("committeePriority", "Chairperson and Credit Committee cannot share the same priority slot.");
            }
            case "Chairperson and Accountant cannot share the same priority slot." -> {
                fieldErrors.put("chairpersonPriority", "Chairperson and Accountant cannot share the same priority slot.");
                fieldErrors.put("accountantPriority", "Chairperson and Accountant cannot share the same priority slot.");
            }
            case "Minimum repayment period must be at least 1 month." ->
                fieldErrors.put("minRepaymentMonths", "Minimum repayment period must be at least 1 month.");
            case "Maximum repayment period must be at least 1 month.",
                 "Maximum repayment period cannot be lower than the minimum repayment period." ->
                fieldErrors.put("maxRepaymentMonths", message);
            case "No active board members are configured for this SACCO yet.",
                 "Assign at least one active board member before using this stage." ->
                fieldErrors.put("boardReviewerIds", "Assign at least one active board member for this product.");
            case "No active chairpersons are configured for this SACCO yet.",
                 "Assign at least one active chairperson before using this stage." ->
                fieldErrors.put("chairpersonReviewRequired", "Assign one active Chairperson before using this stage.");
            case "Only one active Chairperson can be configured for this SACCO." ->
                fieldErrors.put("chairpersonReviewRequired", "Only one active Chairperson can be configured for this SACCO.");
            case "No active credit committee members are configured for this SACCO yet.",
                 "Assign at least one active credit committee member before using this stage." ->
                fieldErrors.put("creditCommitteeReviewerIds", "Assign at least one active credit committee member for this product.");
            case "Selected reviewer is not active in the required role for this SACCO." -> {
                fieldErrors.put("chairpersonReviewerIds", "Only active reviewers in the selected role can be assigned.");
                fieldErrors.put("boardReviewerIds", "Only active reviewers in the selected role can be assigned.");
                fieldErrors.put("creditCommitteeReviewerIds", "Only active reviewers in the selected role can be assigned.");
            }
            case "Reviewers assigned must be between 1 and 15." -> {
                fieldErrors.put("chairpersonReviewerIds", "Reviewers assigned must be between 1 and 15.");
                fieldErrors.put("boardReviewerIds", "Reviewers assigned must be between 1 and 15.");
                fieldErrors.put("creditCommitteeReviewerIds", "Reviewers assigned must be between 1 and 15.");
            }
            case "Committee minimum votes must be at least 1 when committee review is required.",
                 "Committee minimum votes must be between 1 and 15.",
                 "Committee minimum votes cannot exceed the number of active board members.",
                 "Committee approval threshold must be at least 1 when committee review is required.",
                 "Committee approval threshold must be between 1 and 15.",
                 "Committee approval threshold cannot exceed the number of active board members.",
                 "Committee approval threshold cannot be greater than committee minimum votes." ->
                fieldErrors.put("creditCommitteeReviewerIds", "Assign at least one active credit committee member for this product.");
            case "Add at least one active Disbursement/Teller Officer before requiring that workflow role." ->
                fieldErrors.put("disbursementOfficerRequired", "Assign at least one active Disbursement/Teller Officer before requiring this role.");
            case "Grant disbursement queue and release claims to at least one active staff user before removing the Disbursement/Teller Officer requirement." ->
                fieldErrors.put("disbursementOfficerRequired", "Grant both disbursement claims to at least one active staff user before removing this role requirement.");
            default -> {
            }
        }
        return fieldErrors;
    }

    @GetMapping("/reports")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'LOAN_REPORTS_VIEW')")
    public String reports() {
        return "admin/reports";
    }

    @PostMapping("/settings-controls/review-rules")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'APPROVAL_FLOW_CONFIGURE')")
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
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'OUTBOX_VIEW')")
    public String outbox(@RequestParam(required = false) String dateFrom,
                         @RequestParam(required = false) String dateTo,
                         @RequestParam(required = false) String loanApplicationId,
                         @RequestParam(required = false) String loanId,
                         @RequestParam(required = false) String saccoId,
                         @RequestParam(required = false) String stationId,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "50") int size,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
        String scopedSaccoId = superAdmin ? normalizeTextParam(saccoId) : adminScopeService.currentSaccoId(principal);
        String scopedStationId = superAdmin ? normalizeTextParam(stationId) : adminScopeService.currentStationId(principal);
        String selectedLoanApplicationId = firstNonBlank(loanApplicationId, loanId);
        Map<String, String> dateErrors = validateDateRangeInputs(dateFrom, dateTo);
        if (!dateErrors.isEmpty()) {
            populateOutboxFilterModel(model, dateFrom, dateTo, selectedLoanApplicationId, scopedSaccoId, scopedStationId, superAdmin, Page.empty(PageRequest.of(0, normalizePageSize(size))));
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
                selectedLoanApplicationId,
                scopedSaccoId.isBlank() ? null : scopedSaccoId,
                scopedStationId.isBlank() ? null : scopedStationId
            );
            populateOutboxFilterModel(model, dateFrom, dateTo, selectedLoanApplicationId, scopedSaccoId, scopedStationId, superAdmin, eventsPage);
        } catch (IllegalArgumentException | DataAccessException ex) {
            populateOutboxFilterModel(model, dateFrom, dateTo, selectedLoanApplicationId, scopedSaccoId, scopedStationId, superAdmin, Page.empty(PageRequest.of(0, normalizePageSize(size))));
            model.addAttribute("error", resolveFilterErrorMessage(ex, "We couldn't apply that outbox filter. Adjust the values and try again."));
        }
        return "admin/outbox";
    }

    @PostMapping("/outbox/{id}/retry")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'OUTBOX_UPDATE')")
    public String retryOutbox(@PathVariable UUID id,
                              @RequestParam(required = false) String dateFrom,
                              @RequestParam(required = false) String dateTo,
                              @RequestParam(required = false) String loanApplicationId,
                              @RequestParam(required = false) String loanId,
                              @RequestParam(required = false) String saccoId,
                              @RequestParam(required = false) String stationId,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "50") int size,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              RedirectAttributes ra) {
        adminService.retryOutbox(principal.getMemberId(), id);
        ra.addFlashAttribute("message", "Outbox event moved back to NEW for retry.");
        return "redirect:/admin/outbox?page=" + normalizePage(page)
            + buildOutboxPaginationQuery(dateFrom, dateTo, firstNonBlank(loanApplicationId, loanId), saccoId, stationId, normalizePageSize(size));
    }

    @GetMapping("/events")
    @PreAuthorize("@access.canAccessAdminArea(principal) and @access.has(principal, 'ADMIN_DASHBOARD_VIEW')")
    public String events(@RequestParam(required = false) String dateFrom,
                         @RequestParam(required = false) String dateTo,
                         @RequestParam(required = false) String actorId,
                         @RequestParam(required = false) String saccoId,
                         @RequestParam(required = false) String stationId,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "50") int size,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         Model model) {
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
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
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
        if (superAdmin) {
            model.addAttribute("platformDashboard", platformAdminService.dashboard());
            return "admin/platform-saccos";
        }
        java.util.List<SaccoRegistryService.RegisteredSaccoView> registeredSaccos = saccoRegistryService.listRegisteredSaccos().stream()
            .filter(sacco -> sacco.saccoId().equals(adminScopeService.currentSaccoId(principal)))
            .toList();
        model.addAttribute("superAdmin", false);
        model.addAttribute("registeredSaccos", registeredSaccos);
        model.addAttribute("logoUploadPolicy", platformBrandingSettingsService.logoUploadPolicy());
        return "admin/sacco-registry";
    }

    @GetMapping("/saccos/registry")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SACCO_REGISTRY_VIEW')")
    public String saccoRegistry(Model model) {
        model.addAttribute("registeredSaccos", saccoRegistryService.listRegisteredSaccos());
        model.addAttribute("superAdmin", true);
        model.addAttribute("logoUploadPolicy", platformBrandingSettingsService.logoUploadPolicy());
        return "admin/sacco-registry";
    }

    @GetMapping("/saccos/{saccoId}")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SACCO_REGISTRY_VIEW')")
    public String saccoDetail(@PathVariable String saccoId,
                              @RequestParam(required = false) String section,
                              @RequestParam(required = false) String stationId,
                              @RequestParam(required = false) String memberQuery,
                              @RequestParam(defaultValue = "0") int memberPage,
                              @RequestParam(defaultValue = "25") int memberSize,
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
        if ("members".equals(selectedSection)) {
            Page<AdminService.UserAccessView> membersPage = adminService.saccoDetailMembersPage(
                saccoDetail.getSaccoId(),
                saccoDetail.getSelectedStationId(),
                memberQuery,
                memberPage,
                memberSize
            );
            model.addAttribute("saccoMembers", membersPage.getContent());
            model.addAttribute("saccoMembersPage", membersPage);
            model.addAttribute("selectedMemberQuery", normalizeTextParam(memberQuery));
            model.addAttribute("selectedMemberPageSize", membersPage.getSize());
            model.addAttribute("saccoMembersPaginationQuery", buildSaccoMembersPaginationQuery(
                saccoDetail.getSelectedStationId(),
                memberQuery,
                membersPage.getSize()
            ));
        }
        return "admin/sacco-detail";
    }

    @PostMapping("/saccos/{saccoId}/language")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_UPDATE')")
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
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'USER_ACCESS_VIEW')")
    public String minorAdmins(Model model) {
        List<SaccoRegistryService.RegisteredSaccoView> registeredSaccos = saccoRegistryService.listRegisteredSaccosFresh();
        model.addAttribute("registeredSaccos", registeredSaccos);
        model.addAttribute("registeredSaccoNamesById", registeredSaccoNamesById(registeredSaccos));
        model.addAttribute("minorAdmins", adminService.minorAdmins());
        model.addAttribute("registrationForm", new MinorAdminRegistrationForm());
        model.addAttribute("superAdmin", true);
        return "admin/minor-admins";
    }

    @PostMapping("/saccos")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SACCO_REGISTRY_CREATE')")
    public String registerSacco(@RequestParam String saccoName,
                                @RequestParam String stationIds,
                                @RequestParam(name = "logoFile", required = false) MultipartFile logoFile,
                                @AuthenticationPrincipal AppUserPrincipal principal,
                                RedirectAttributes ra) {
        try {
            var sacco = saccoRegistryService.registerSacco(saccoName, stationIds, logoFile);
            ra.addFlashAttribute("message", "SACCO details saved with ID " + sacco.getSaccoId() + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/admin/saccos/registry";
        }
        return "redirect:/admin/saccos/registry";
    }

    @PostMapping("/saccos/minor-admins")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'USER_ACCESS_CREATE')")
    public String registerMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                     @ModelAttribute MinorAdminRegistrationForm registrationForm,
                                     RedirectAttributes ra) {
        try {
            var createdAdmin = adminService.registerMinorAdmin(
                principal.getMemberId(),
                registrationForm.getSaccoId(),
                registrationForm.getStationId(),
                registrationForm.getFullName(),
                registrationForm.getEmail(),
                registrationForm.getPhone()
            );
            ra.addFlashAttribute("createdMinorAdminStaffNumber", createdAdmin.getStaffNo());
            if (createdAdmin.isMemberAccess()) {
                ra.addFlashAttribute("message", "SACCOS Admin access assigned to the existing LMS member account with Staff Number " + createdAdmin.getStaffNo() + ".");
            } else {
                ra.addFlashAttribute("message", "SACCOS Admin invited with Staff Number " + createdAdmin.getStaffNo() + ". A password setup link has been emailed to them.");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/minor-admins/change")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'USER_ACCESS_UPDATE') and @access.has(principal, 'USER_ACCESS_CREATE')")
    public String changeMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @ModelAttribute MinorAdminRegistrationForm registrationForm,
                                   RedirectAttributes ra) {
        try {
            var createdAdmin = adminService.changeMinorAdmin(
                principal.getMemberId(),
                registrationForm.getSaccoId(),
                registrationForm.getStationId(),
                registrationForm.getFullName(),
                registrationForm.getEmail(),
                registrationForm.getPhone()
            );
            ra.addFlashAttribute("createdMinorAdminStaffNumber", createdAdmin.getStaffNo());
            if (createdAdmin.isMemberAccess()) {
                ra.addFlashAttribute("message", "SACCOS Admin changed. Access assigned to the existing LMS member account with Staff Number " + createdAdmin.getStaffNo() + ".");
            } else {
                ra.addFlashAttribute("message", "SACCOS Admin changed. The new admin has been invited with Staff Number " + createdAdmin.getStaffNo() + ".");
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/minor-admins/{accountId}")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'USER_ACCESS_UPDATE')")
    public String updateMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                   @PathVariable UUID accountId,
                                   @RequestParam String saccoId,
                                   @RequestParam String stationId,
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
                fullName,
                email,
                phone
            );
            ra.addFlashAttribute("message", "SACCOS Admin details updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/minor-admins/{accountId}/resend-invite")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'USER_ACCESS_UPDATE')")
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
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'USER_ACCESS_UPDATE')")
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
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'USER_ACCESS_UPDATE')")
    public String deactivateMinorAdmin(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable UUID accountId,
                                       RedirectAttributes ra) {
        try {
            adminService.deactivateMinorAdmin(principal.getMemberId(), accountId);
            ra.addFlashAttribute("message", "SACCOS Admin deactivated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/minor-admins/{accountId}/reinvite")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'USER_ACCESS_UPDATE')")
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

    @PostMapping("/saccos/minor-admins/{accountId}/delete")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'USER_ACCESS_DELETE')")
    public String deleteMinorAdmin(@PathVariable UUID accountId,
                                   @RequestParam String confirmation,
                                   RedirectAttributes ra) {
        try {
            saccoDataDeletionService.deleteRevokedMinorAdmin(accountId, confirmation);
            ra.addFlashAttribute("message", "SACCOS Admin record deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/minor-admins";
    }

    @PostMapping("/saccos/{saccoId}")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SACCO_REGISTRY_UPDATE')")
    public String updateSacco(@PathVariable String saccoId,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam String saccoName,
                              @RequestParam String stationIds,
                              @RequestParam(name = "stationAddressIds", required = false) List<String> stationAddressIds,
                              @RequestParam(name = "stationAddressLocations", required = false) List<String> stationAddressLocations,
                              @RequestParam(name = "logoFile", required = false) MultipartFile logoFile,
                              RedirectAttributes ra) {
        try {
            if (principal != null && principal.isPlatformIdentity()) {
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
            return principal != null && principal.isPlatformIdentity()
                ? "redirect:/admin/saccos/registry"
                : "redirect:/admin/saccos";
        }
        return principal != null && principal.isPlatformIdentity()
            ? "redirect:/admin/saccos/registry"
            : "redirect:/admin/saccos";
    }

    @PostMapping("/saccos/{saccoId}/logo")
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_UPDATE')")
    public String updateWorkspaceSaccoLogo(@PathVariable String saccoId,
                                           @AuthenticationPrincipal AppUserPrincipal principal,
                                           @RequestParam(name = "logoFile", required = false) MultipartFile logoFile,
                                           RedirectAttributes ra) {
        try {
            String currentSaccoId = adminScopeService.currentSaccoId(principal);
            if (currentSaccoId == null || !currentSaccoId.equalsIgnoreCase(saccoId == null ? "" : saccoId.trim())) {
                throw new IllegalStateException("You can only update the logo for your SACCO workspace.");
            }
            saccoRegistryService.updateLogoOnly(currentSaccoId, logoFile, principal.getMemberId());
            ra.addFlashAttribute("message", "SACCO logo updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos";
    }

    @PostMapping("/saccos/{saccoId}/logo/delete")
    @PreAuthorize("(@authz.platformAdminIdentity(principal) or @authz.workspaceAdminOnly(principal)) and @access.has(principal, 'WORKSPACE_SETTINGS_UPDATE')")
    public String deleteWorkspaceSaccoLogo(@PathVariable String saccoId,
                                           @AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        boolean superAdmin = principal != null && principal.isPlatformIdentity();
        try {
            String targetSaccoId = saccoId == null ? "" : saccoId.trim();
            if (!superAdmin) {
                String currentSaccoId = adminScopeService.currentSaccoId(principal);
                if (currentSaccoId == null || !currentSaccoId.equalsIgnoreCase(targetSaccoId)) {
                    throw new IllegalStateException("You can only remove the logo for your SACCO workspace.");
                }
                targetSaccoId = currentSaccoId;
            }
            saccoRegistryService.removeLogoOnly(targetSaccoId, principal == null ? null : principal.getMemberId());
            ra.addFlashAttribute("message", "SACCO logo removed.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return superAdmin ? "redirect:/admin/saccos/registry" : "redirect:/admin/saccos";
    }

    @PostMapping("/saccos/{saccoId}/delete")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SACCO_REGISTRY_DELETE')")
    public String deleteSacco(@PathVariable String saccoId,
                              @RequestParam String confirmation,
                              RedirectAttributes ra) {
        try {
            saccoDataDeletionService.deleteSacco(saccoId, confirmation);
            ra.addFlashAttribute("message", "SACCO and all related data deleted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/saccos/registry";
    }

    @PostMapping("/saccos/{saccoId}/access/suspend")
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SACCO_REGISTRY_UPDATE')")
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
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SACCO_REGISTRY_UPDATE')")
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
    @PreAuthorize("@authz.platformAdminIdentity(principal) and @access.has(principal, 'SACCO_REGISTRY_UPDATE')")
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
            return principal != null && principal.isPlatformIdentity()
                ? "redirect:/admin/saccos/registry"
                : "redirect:/admin/saccos";
        }
        return principal != null && principal.isPlatformIdentity()
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
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'WORKSPACE_SETTINGS_CONFIGURE')")
    public String updateScope(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam String saccoId,
                              @RequestParam String stationId,
                              @RequestParam(required = false) String next,
                              RedirectAttributes ra) {
        adminScopeService.updateScope(principal, saccoId, stationId);
        ra.addFlashAttribute("message", principal != null && principal.isPlatformIdentity()
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

    private String firstNonBlank(String primary, String fallback) {
        String normalizedPrimary = normalizeTextParam(primary);
        return normalizedPrimary.isBlank() ? normalizeTextParam(fallback) : normalizedPrimary;
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
                                           String loanApplicationId,
                                           Page<com.sacco.mvp.domain.OutboxEvent> eventsPage) {
        populateOutboxFilterModel(model, dateFrom, dateTo, loanApplicationId, "", "", false, eventsPage);
    }

    private void populateOutboxFilterModel(Model model,
                                           String dateFrom,
                                           String dateTo,
                                           String loanApplicationId,
                                           String saccoId,
                                           String stationId,
                                           boolean superAdmin,
                                           Page<com.sacco.mvp.domain.OutboxEvent> eventsPage) {
        List<com.sacco.mvp.domain.OutboxEvent> events = eventsPage.getContent();
        model.addAttribute("events", events);
        model.addAttribute("outboxActorPrefixes", adminService.outboxActorPrefixes(events));
        model.addAttribute("outboxActorNames", adminService.outboxActorNames(events));
        model.addAttribute("eventsPage", eventsPage);
        model.addAttribute("selectedDateFrom", normalizeDateParam(dateFrom));
        model.addAttribute("selectedDateTo", normalizeDateParam(dateTo));
        model.addAttribute("selectedLoanApplicationId", normalizeTextParam(loanApplicationId));
        addAdminScopeFilters(model, saccoId, stationId, superAdmin);
        model.addAttribute("outboxPaginationQuery", buildOutboxPaginationQuery(dateFrom, dateTo, loanApplicationId, saccoId, stationId, eventsPage.getSize()));
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
        List<com.sacco.mvp.domain.AuditLog> entries = entriesPage.getContent();
        model.addAttribute("entries", entries);
        model.addAttribute("actorNamesById", adminService.actorNamesForEvents(entries));
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
        model.addAttribute("registeredSaccoNamesById", registeredSaccoNamesById(saccos));
        model.addAttribute("selectedSaccoId", selectedSaccoId);
        model.addAttribute("selectedStationId", selectedStationId);
        model.addAttribute("selectedStationOptions", stationOptions);
    }

    private Map<String, String> registeredSaccoNamesById(List<SaccoRegistryService.RegisteredSaccoView> saccos) {
        Map<String, String> namesById = new LinkedHashMap<>();
        if (saccos == null || saccos.isEmpty()) {
            return namesById;
        }
        for (SaccoRegistryService.RegisteredSaccoView sacco : saccos) {
            if (sacco == null || sacco.saccoId() == null || sacco.saccoName() == null || sacco.saccoName().isBlank()) {
                continue;
            }
            namesById.put(sacco.saccoId(), sacco.saccoName());
        }
        return namesById;
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

    private String buildOutboxPaginationQuery(String dateFrom, String dateTo, String loanApplicationId, int size) {
        return buildOutboxPaginationQuery(dateFrom, dateTo, loanApplicationId, null, null, size);
    }

    private String buildOutboxPaginationQuery(String dateFrom, String dateTo, String loanApplicationId, String saccoId, String stationId, int size) {
        StringBuilder query = new StringBuilder("&size=").append(size);
        if (dateFrom != null && !dateFrom.isBlank()) {
            query.append("&dateFrom=").append(UriUtils.encode(dateFrom.trim(), StandardCharsets.UTF_8));
        }
        if (dateTo != null && !dateTo.isBlank()) {
            query.append("&dateTo=").append(UriUtils.encode(dateTo.trim(), StandardCharsets.UTF_8));
        }
        if (loanApplicationId != null && !loanApplicationId.isBlank()) {
            query.append("&loanApplicationId=").append(UriUtils.encode(loanApplicationId.trim(), StandardCharsets.UTF_8));
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

    private String buildUsersPaginationQuery(String queryText, String searchBy, int size) {
        StringBuilder query = new StringBuilder("&size=").append(size);
        String normalizedSearchBy = normalizeUsersSearchBy(searchBy);
        query.append("&searchBy=").append(UriUtils.encode(normalizedSearchBy, StandardCharsets.UTF_8));
        if (queryText != null && !queryText.isBlank()) {
            query.append("&query=").append(UriUtils.encode(queryText.trim(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private List<Position> staffPositionsFor(AppUserPrincipal principal) {
        return principal != null && principal.isPlatformIdentity()
            ? Position.staffAssignableRoles()
            : Position.staffAssignableRoles().stream().filter(position -> position != Position.ADMIN).toList();
    }

    private String roleDefaultClaimsJson(List<Position> staffPositions) {
        StringBuilder json = new StringBuilder("{");
        boolean firstRole = true;
        for (Position position : staffPositions == null ? List.<Position>of() : staffPositions) {
            if (!firstRole) {
                json.append(',');
            }
            firstRole = false;
            json.append('"').append(position.name()).append('"').append(':')
                .append(claimsJson(UserClaim.defaultClaims(List.of(position), false)));
        }
        return json.append('}').toString();
    }

    private String claimsJson(Collection<UserClaim> claims) {
        StringBuilder json = new StringBuilder("[");
        boolean firstClaim = true;
        for (UserClaim claim : claims == null ? List.<UserClaim>of() : claims) {
            if (!firstClaim) {
                json.append(',');
            }
            firstClaim = false;
            json.append('"').append(claim.name()).append('"');
        }
        return json.append(']').toString();
    }

    private String buildSaccoMembersPaginationQuery(String stationId, String memberQuery, int size) {
        StringBuilder query = new StringBuilder("&section=members&memberSize=").append(size);
        if (stationId != null && !stationId.isBlank()) {
            query.append("&stationId=").append(UriUtils.encode(stationId.trim(), StandardCharsets.UTF_8));
        }
        if (memberQuery != null && !memberQuery.isBlank()) {
            query.append("&memberQuery=").append(UriUtils.encode(memberQuery.trim(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private String normalizeUsersSearchBy(String searchBy) {
        return "name".equalsIgnoreCase(searchBy == null ? "" : searchBy.trim()) ? "name" : "userId";
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
