package com.sacco.mvp.web;

import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.IncidentSeverity;
import com.sacco.mvp.domain.IncidentStatus;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.AdminService;
import com.sacco.mvp.service.DatabaseUtilizationService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.SaccoRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN') and @userClaims.has(principal, 'ACCESS_ADMIN_SETTINGS')")
public class AdminController {
    private final AdminService adminService;
    private final AdminScopeService adminScopeService;
    private final SaccoRegistryService saccoRegistryService;
    private final DatabaseUtilizationService databaseUtilizationService;
    private final NotificationInboxService notificationInboxService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("dashboard", adminService.dashboard(adminScopeService.currentSaccoId(principal), principal.getMemberId()));
        return "admin/dashboard";
    }

    @GetMapping(value = "/dashboard/database-utilization", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public DatabaseUtilizationService.DatabaseUtilizationPayload databaseUtilization() {
        return databaseUtilizationService.snapshot();
    }

    @GetMapping("/messages")
    public String messages(@AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false) UUID highlight,
                           Model model) {
        model.addAttribute("messages", adminService.adminMessages(principal.getMemberId()));
        model.addAttribute("members", adminService.activeMembers(adminScopeService.currentSaccoId(principal)));
        model.addAttribute("highlightNotificationId", highlight);
        return "admin/messages";
    }

    @GetMapping("/messages/{id}/open")
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

    @PostMapping("/messages/mark-all-read")
    public String markAllMessagesRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                      RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        if (updated > 0) {
            ra.addFlashAttribute("message", "All admin messages have been marked as read.");
        } else {
            ra.addFlashAttribute("message", "There were no unread admin messages.");
        }
        return "redirect:/admin/messages";
    }

    @GetMapping("/incidents")
    public String incidents(@AuthenticationPrincipal AppUserPrincipal principal,
                            @RequestParam(required = false) IncidentStatus status,
                            @RequestParam(required = false) IncidentSeverity severity,
                            Model model) {
        model.addAttribute("incidents", adminService.incidents(adminScopeService.currentSaccoId(principal), status, severity));
        model.addAttribute("incidentStatuses", IncidentStatus.values());
        model.addAttribute("incidentSeverities", IncidentSeverity.values());
        model.addAttribute("selectedStatus", status == null ? "" : status.name());
        model.addAttribute("selectedSeverity", severity == null ? "" : severity.name());
        return "admin/incidents";
    }

    @GetMapping("/incidents/{id}")
    public String incidentDetail(@PathVariable UUID id,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 Model model) {
        model.addAttribute("incident", adminService.incident(adminScopeService.currentSaccoId(principal), id));
        model.addAttribute("incidentStatuses", IncidentStatus.values());
        model.addAttribute("incidentSeverities", IncidentSeverity.values());
        return "admin/incident-detail";
    }

    @PostMapping("/incidents/{id}")
    public String updateIncident(@PathVariable UUID id,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam IncidentSeverity severity,
                                 @RequestParam IncidentStatus status,
                                 @RequestParam(required = false) String resolutionNote,
                                 RedirectAttributes ra) {
        adminService.updateIncident(adminScopeService.currentSaccoId(principal), principal.getMemberId(), id, severity, status, resolutionNote);
        ra.addFlashAttribute("message", "Incident updated.");
        return "redirect:/admin/incidents/" + id;
    }

    @PostMapping("/messages/reply")
    public String reply(@AuthenticationPrincipal AppUserPrincipal principal,
                        @RequestParam UUID memberId,
                        @RequestParam String subject,
                        @RequestParam String message,
                        RedirectAttributes ra) {
        adminService.replyToMember(adminScopeService.currentSaccoId(principal), principal.getMemberId(), memberId, subject, message);
        ra.addFlashAttribute("message", "Reply sent to member notifications.");
        return "redirect:/admin/messages";
    }

    @PostMapping("/messages/broadcast")
    public String broadcast(@AuthenticationPrincipal AppUserPrincipal principal,
                            @RequestParam String subject,
                            @RequestParam String message,
                            RedirectAttributes ra) {
        adminService.broadcast(adminScopeService.currentSaccoId(principal), principal.getMemberId(), subject, message);
        ra.addFlashAttribute("message", "Broadcast sent to active SACCO members.");
        return "redirect:/admin/messages";
    }

    @GetMapping("/users")
    public String users(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        java.util.List<AdminService.UserAccessView> users = adminService.users(adminScopeService.currentSaccoId(principal));
        model.addAttribute("users", users);
        model.addAttribute("staffPositions", Position.staffAssignableRoles());
        model.addAttribute("statuses", MemberStatus.values());
        return "admin/users";
    }

    @PostMapping("/users")
    public String createUser(@AuthenticationPrincipal AppUserPrincipal principal,
                             @RequestParam String memberNo,
                             @RequestParam String fullName,
                             @RequestParam String email,
                             @RequestParam(required = false) String phone,
                             @RequestParam(name = "positions", required = false) java.util.List<Position> positions,
                             RedirectAttributes ra) {
        adminService.createUser(adminScopeService.currentSaccoId(principal), principal.getMemberId(), memberNo, fullName, email, phone, positions);
        ra.addFlashAttribute("message", "User created.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable UUID id,
                             @AuthenticationPrincipal AppUserPrincipal principal,
                             @RequestParam(name = "positions", required = false) java.util.List<Position> positions,
                             @RequestParam MemberStatus status,
                             RedirectAttributes ra) {
        adminService.updateUser(adminScopeService.currentSaccoId(principal), principal.getMemberId(), id, positions, status);
        ra.addFlashAttribute("message", "User updated.");
        return "redirect:/admin/users";
    }

    @GetMapping({"/loan-products", "/settings-controls"})
    public String loanProducts(@AuthenticationPrincipal AppUserPrincipal principal,
                               @RequestParam(required = false, defaultValue = "loan") String section,
                               Model model) {
        String saccoId = adminScopeService.currentSaccoId(principal);
        model.addAttribute("products", adminService.loanProducts(saccoId));
        model.addAttribute("settings", adminService.settings(saccoId));
        model.addAttribute("settingsSection", "board".equalsIgnoreCase(section) ? "board" : "loan");
        return "admin/settings-controls";
    }

    @PostMapping({"/loan-products/{id}", "/settings-controls/{id}"})
    public String updateLoanProduct(@PathVariable UUID id,
                                    @AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam Integer guarantorsRequired,
                                    @RequestParam BigDecimal maxLoanSavingsPercent,
                                    @RequestParam BigDecimal insurancePercent,
                                    @RequestParam BigDecimal interestPercent,
                                    @RequestParam Integer maxRepaymentMonths,
                                    @RequestParam(defaultValue = "false") boolean active,
                                    RedirectAttributes ra) {
        BigDecimal maxLoanSavingsRatio = maxLoanSavingsPercent
            .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal insuranceRate = insurancePercent
            .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal interestRate = interestPercent
            .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        adminService.updateLoanProduct(adminScopeService.currentSaccoId(principal), principal.getMemberId(), id,
            guarantorsRequired, maxLoanSavingsRatio, insuranceRate, interestRate, maxRepaymentMonths, active);
        ra.addFlashAttribute("message", "Loan product updated.");
        return "redirect:/admin/settings-controls?section=loan";
    }

    @PostMapping("/settings-controls/review-rules")
    public String updateReviewRules(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam Integer boardQuorum,
                                    RedirectAttributes ra) {
        adminService.updateBoardReviewRequirement(
            adminScopeService.currentSaccoId(principal),
            principal.getMemberId(),
            boardQuorum
        );
        ra.addFlashAttribute("message", "Board review requirement updated.");
        return "redirect:/admin/settings-controls?section=board";
    }

    @GetMapping("/outbox")
    @PreAuthorize("hasRole('ADMIN') and @userClaims.has(principal, 'ACCESS_OUTBOX_MONITOR')")
    public String outbox(Model model) {
        model.addAttribute("events", adminService.outboxEvents());
        model.addAttribute("outboxStatuses", OutboxStatus.values());
        return "admin/outbox";
    }

    @PostMapping("/outbox/{id}/retry")
    @PreAuthorize("hasRole('ADMIN') and @userClaims.has(principal, 'ACCESS_OUTBOX_MONITOR')")
    public String retryOutbox(@PathVariable UUID id,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              RedirectAttributes ra) {
        adminService.retryOutbox(principal.getMemberId(), id);
        ra.addFlashAttribute("message", "Outbox event moved back to NEW for retry.");
        return "redirect:/admin/outbox";
    }

    @GetMapping("/events")
    public String events(Model model) {
        model.addAttribute("entries", adminService.eventEntries());
        return "admin/events";
    }

    @GetMapping("/saccos")
    public String saccos(Model model) {
        model.addAttribute("registeredSaccos", saccoRegistryService.listRegisteredSaccos());
        return "admin/saccos";
    }

    @PostMapping("/saccos")
    public String registerSacco(@RequestParam String saccoId,
                                @RequestParam String saccoName,
                                @RequestParam String stationIds,
                                RedirectAttributes ra) {
        saccoRegistryService.registerSacco(saccoId, saccoName, stationIds);
        ra.addFlashAttribute("message", "SACCO details saved.");
        return "redirect:/admin/saccos";
    }

    @PostMapping("/saccos/{saccoId}")
    public String updateSacco(@PathVariable String saccoId,
                              @RequestParam String saccoName,
                              @RequestParam String stationIds,
                              RedirectAttributes ra) {
        saccoRegistryService.updateSacco(saccoId, saccoName, stationIds);
        ra.addFlashAttribute("message", "SACCO registry updated.");
        return "redirect:/admin/saccos";
    }

    @PostMapping("/scope")
    public String updateScope(@RequestParam String saccoId,
                              @RequestParam String stationId,
                              RedirectAttributes ra) {
        adminScopeService.updateScope(saccoId, stationId);
        ra.addFlashAttribute("message", "You are now working under the selected SACCO and station.");
        return "redirect:/admin/dashboard";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/admin/dashboard";
    }
}
