package com.sacco.mvp.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanPaymentTransactionRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.integration.memberportal.LoanPaymentLookupException;
import com.sacco.mvp.service.LoanPaymentTransactionSyncService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.ManagerService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.ReversalRequestService;
import com.sacco.mvp.service.ExternalAccountStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/manager")
@PreAuthorize("hasRole('MANAGER') and @userClaims.has(principal, 'REVIEW_MANAGER_QUEUE')")
public class ManagerController {
    private static final DateTimeFormatter REPORT_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ManagerService managerService;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final LoanPresentationService loanPresentationService;
    private final LoanReportService loanReportService;
    private final ReversalRequestService reversalRequestService;
    private final ExternalAccountStatusService externalAccountStatusService;
    private final NotificationInboxService notificationInboxService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanPaymentTransactionRepository loanPaymentTransactionRepository;
    private final LoanPaymentTransactionSyncService loanPaymentTransactionSyncService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        ManagerService.ManagerDashboard dashboard = managerService.dashboard(principal.getSaccoId());
        Map<UUID, String> applicantNames = memberRepository.findAllById(
                dashboard.recentDisbursements().stream()
                    .map(com.sacco.mvp.domain.LoanApplication::getApplicantMemberId)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        model.addAttribute("dashboardTotalDisbursedLoans", dashboard.totalDisbursedLoans());
        model.addAttribute("dashboardTrackedApplicationCount", dashboard.totalLoans());
        model.addAttribute("dashboardDisbursementYear", LocalDate.now().getYear());
        model.addAttribute("dashboardActiveDisbursedLoans", dashboard.activeDisbursedLoans());
        model.addAttribute("dashboardOnReviewByManagerLoans", dashboard.onReviewByManagerLoans());
        model.addAttribute("dashboardPaidLoans", dashboard.paidLoans());
        model.addAttribute("dashboardStatusChartRows", buildDashboardStatusChartRows(dashboard.statusBreakdown()));
        model.addAttribute("dashboardDisbursementRows", dashboard.recentDisbursements().stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("applicantName", applicantNames.getOrDefault(loan.getApplicantMemberId(), "-"));
                row.put("loanTypeLabel", loan.getLoanType().getDisplayLabel());
                row.put("statusLabel", switch (loan.getStatus()) {
                    case PAID -> "PAID";
                    case DEFAULTED -> "DEFAULTED";
                    default -> "DISBURSED LOAN";
                });
                row.put("shortId", loan.getApplicationNumber() == null ? "" : loan.getApplicationNumber().toString());
                row.put("loanId", loan.getLoanId() == null ? "" : loan.getLoanId());
                row.put("amount", loan.getAmount() == null ? "-" : loan.getAmount().toPlainString());
                row.put("disbursementDate", loan.getDisbursementDate() == null ? "-" : loan.getDisbursementDate().toString());
                return row;
            })
            .toList());
        model.addAttribute("dashboardRecentDisbursementDays", 30);
        return "manager/dashboard";
    }

    private List<Map<String, Object>> buildDashboardStatusChartRows(List<ManagerService.StatusCount> statusBreakdown) {
        long maxCount = statusBreakdown.stream()
            .map(ManagerService.StatusCount::count)
            .max(Long::compareTo)
            .orElse(1L);
        long safeMax = Math.max(1L, maxCount);

        return statusBreakdown.stream()
            .map(entry -> {
                long count = entry.count();
                long widthPercent = count <= 0 ? 0L : Math.max(8L, Math.round((count * 100.0d) / safeMax));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", entry.status().name());
                row.put("label", dashboardStatusLabel(entry.status()));
                row.put("count", count);
                row.put("widthPercent", widthPercent);
                row.put("color", dashboardStatusColor(entry.status()));
                return row;
            })
            .toList();
    }

    @GetMapping("/loan-applications")
    public String queue(@AuthenticationPrincipal AppUserPrincipal principal,
                        @RequestParam(required = false) LoanStatus status,
                        Model model) {
        LoanStatus effectiveStatus = status == null ? LoanStatus.READY_FOR_MANAGER : status;
        List<com.sacco.mvp.domain.LoanApplication> apps = managerService.queue(principal.getSaccoId(), effectiveStatus);
        Map<UUID, String> applicantNames = memberRepository.findAllById(
                apps.stream().map(com.sacco.mvp.domain.LoanApplication::getApplicantMemberId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        model.addAttribute("apps", apps);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("status", effectiveStatus);
        return "manager/queue";
    }

    @GetMapping("/loan-applications/{id}")
    public String detail(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        var app = managerService.get(id, principal.getSaccoId());
        Member applicant = memberRepository.findById(app.getApplicantMemberId()).orElse(null);
        List<com.sacco.mvp.domain.GuarantorRequest> guarantorRequests = guarantorRequestRepository.findByLoanApplicationId(id);
        Map<UUID, String> guarantorNames = memberRepository.findAllById(
                guarantorRequests.stream().map(com.sacco.mvp.domain.GuarantorRequest::getGuarantorMemberId)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));
        ExternalAccountStatusService.ExternalAccountStatusView applicantExternalAccountStatus =
            externalAccountStatusService.resolve(applicant);
        String managerReason =
            app.getStatus() == LoanStatus.MANAGER_REJECTED ? loanPresentationService.latestManagerReason(id) : "";
        List<com.sacco.mvp.domain.LoanApplication> activeApplicantLoans = managerService.activeApplicantLoans(
            app.getApplicantMemberId(), app.getId(), principal.getSaccoId());

        model.addAttribute("app", app);
        model.addAttribute("applicant", applicant);
        model.addAttribute("applicantExternalAccountStatus", applicantExternalAccountStatus);
        model.addAttribute("formFields", parseJsonObject(app.getFormData()));
        model.addAttribute("financialFields", loanPresentationService.parseFinancialFields(app.getFinancialSnapshot()));
        model.addAttribute("attachments", loanPresentationService.parseAttachments(app.getAttachmentsJson()));
        model.addAttribute("repaymentSummary",
            loanPresentationService.parseRepaymentSummary(app.getRepaymentScheduleJson(), app.getPaidAt()));
        model.addAttribute("repaymentRows", loanPresentationService.parseRepaymentRows(
            app.getRepaymentScheduleJson(),
            loanPaymentTransactionRepository.findByLoanApplicationIdOrderByReceiptDateAsc(app.getId())));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("managerReason", managerReason);
        model.addAttribute("guarantorRequests", guarantorRequests);
        model.addAttribute("guarantorNames", guarantorNames);
        model.addAttribute("pendingManagerStageWithdrawal", reversalRequestService.pendingManagerStageWithdrawal(id));
        model.addAttribute("activeApplicantLoans", activeApplicantLoans.stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("shortId", loan.getApplicationNumber() == null ? "" : loan.getApplicationNumber().toString());
                row.put("loanId", loan.getLoanId() == null ? "" : loan.getLoanId());
                row.put("loanTypeLabel", loanTypeLabel(loan.getLoanType()));
                row.put("amount", formatMoney(loan.getAmount()));
                row.put("disbursedAt", loan.getDisbursementDate() == null ? "-" : loan.getDisbursementDate().toString());
                row.put("finalDueDate", loan.getFinalDueDate() == null ? "-" : loan.getFinalDueDate().toString());
                row.put("installmentAmount", formatMoney(loan.getInstallmentAmount()));
                row.put("repaymentFrequency", loan.getRepaymentFrequency() == null
                    ? "Standard schedule"
                    : humanizeEnum(loan.getRepaymentFrequency().name()));
                row.put("countdown", loanPresentationService.countdownLabel(loan.getFinalDueDate()));
                row.put("isTopUpSource", String.valueOf(
                    app.getTopUpSourceLoanId() != null && app.getTopUpSourceLoanId().equals(loan.getId())));
                return row;
            })
            .toList());
        model.addAttribute("activeApplicantLoanCount", activeApplicantLoans.size());
        model.addAttribute("activeApplicantLoanTotalAmount", formatMoney(
            activeApplicantLoans.stream()
                .map(com.sacco.mvp.domain.LoanApplication::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        model.addAttribute("managerUndoWindowOpen",
            app.getStatus() == LoanStatus.MANAGER_REJECTED
                && app.getUpdatedAt() != null
                && app.getUpdatedAt().plusHours(24).isAfter(OffsetDateTime.now()));
        addReviewDisplayAttributes(model, app, applicantExternalAccountStatus, managerReason);
        return "manager/detail";
    }

    @GetMapping("/loan-applications/{loanId}/guarantors/{guarantorId}/financial-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> guarantorFinancialStatus(@PathVariable UUID loanId,
                                                                        @PathVariable UUID guarantorId,
                                                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        managerService.get(loanId, principal.getSaccoId());
        boolean guarantorAssigned = guarantorRequestRepository.findByLoanApplicationId(loanId).stream()
            .anyMatch(request -> guarantorId.equals(request.getGuarantorMemberId()));
        if (!guarantorAssigned) {
            return ResponseEntity.badRequest().body(Map.of("message", "Guarantor request was not found for this loan."));
        }
        Member guarantor = memberRepository.findById(guarantorId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor not found"));
        ExternalAccountStatusService.ExternalAccountStatusView status = externalAccountStatusService.resolve(guarantor);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", status.isAvailable());
        payload.put("savingsLabel", status.getSavingsLabel());
        payload.put("sharesLabel", status.getSharesLabel());
        payload.put("statusMessage", status.getStatusMessage());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/loan-applications/{id}/decision")
    public String decide(@PathVariable UUID id,
                         @AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam ManagerDecision decision,
                         @RequestParam(required = false) String reasons,
                         RedirectAttributes ra) {
        managerService.decide(id, principal.getMemberId(), decision, reasons);
        if (decision == ManagerDecision.ACCEPT) {
            ra.addFlashAttribute("message", "Manager accepted. Status moved to ON REVIEW BY BOARD.");
        } else {
            ra.addFlashAttribute("message", "Manager rejected application.");
        }
        return "redirect:/manager/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/undo-decision")
    public String undoDecision(@PathVariable UUID id,
                               @AuthenticationPrincipal AppUserPrincipal principal,
                               RedirectAttributes ra) {
        try {
            managerService.undoDecision(id, principal.getMemberId());
            ra.addFlashAttribute("message", "Manager action reversed");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{loanId}/reversal-requests/{requestId}/approve")
    public String approveReversalRequest(@PathVariable UUID loanId,
                                         @PathVariable UUID requestId,
                                         @AuthenticationPrincipal AppUserPrincipal principal,
                                         RedirectAttributes ra) {
        try {
            reversalRequestService.decideManagerStageWithdrawal(requestId, principal.getMemberId(), true);
            ra.addFlashAttribute("message", "Removal request approved. The application was removed from manager review.");
            return "redirect:/manager/loan-applications";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/manager/loan-applications/" + loanId;
        }
    }

    @PostMapping("/loan-applications/{loanId}/reversal-requests/{requestId}/reject")
    public String rejectReversalRequest(@PathVariable UUID loanId,
                                        @PathVariable UUID requestId,
                                        @AuthenticationPrincipal AppUserPrincipal principal,
                                        RedirectAttributes ra) {
        try {
            reversalRequestService.decideManagerStageWithdrawal(requestId, principal.getMemberId(), false);
            ra.addFlashAttribute("message", "Removal request declined.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/loan-applications/" + loanId;
    }

    @PostMapping("/loan-applications/{id}/finalize")
    public String finalize(@PathVariable UUID id,
                           @AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam String decision,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate disbursementDate,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate firstRepaymentDate,
                           @RequestParam(required = false) String loanId,
                           @RequestParam(required = false) String disbursementReference,
                           @RequestParam(required = false) String disbursementNotes,
                           RedirectAttributes ra) {
        try {
            managerService.finalizeDecision(id, decision, principal.getMemberId(),
                disbursementDate, firstRepaymentDate, null, null,
                loanId, disbursementReference, disbursementNotes);
            ra.addFlashAttribute("message", "FINAL_APPROVE".equalsIgnoreCase(decision)
                ? "Loan disbursed successfully."
                : "Application finalized");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/sync-payments")
    public String syncPayments(@PathVariable UUID id,
                               @AuthenticationPrincipal AppUserPrincipal principal,
                               @RequestParam(name = "monthsBack", defaultValue = "12") int monthsBack,
                               RedirectAttributes ra) {
        try {
            int inserted = managerService.syncLoanPayments(id, principal.getMemberId(), monthsBack);
            ra.addFlashAttribute("message",
                inserted == 0
                    ? "Payment transactions refreshed; nothing new."
                    : "Payment transactions refreshed; " + inserted + " new record(s) added.");
        } catch (LoanPaymentLookupException ex) {
            ra.addFlashAttribute("error", "Payment transactions could not be fetched: " + ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/loan-applications/" + id;
    }

    @GetMapping("/reports")
    public String reports(@AuthenticationPrincipal AppUserPrincipal principal,
                          @RequestParam(required = false) Integer year,
                          @RequestParam(defaultValue = "false") boolean returnedOnly,
                          Model model) {
        LoanReportService.ManagerLoanReport report = loanReportService.managerReport(
            principal.getSaccoId(), year, returnedOnly);
        Map<UUID, String> applicantNames = report.applicantMap().values().stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));
        model.addAttribute("report", report);
        model.addAttribute("applicantNames", applicantNames);
        model.addAttribute("reportYears", loanReportService.managerReportYears(principal.getSaccoId()));
        model.addAttribute("yearValue", String.valueOf(report.year()));
        model.addAttribute("returnedOnlyChecked", report.returnedOnly() ? "checked" : "");
        model.addAttribute("returnedOnlyValue", String.valueOf(report.returnedOnly()));
        model.addAttribute("reportDisbursedCount", report.summary().disbursedCount());
        model.addAttribute("reportDisbursedAmountLabel", report.summary().getDisbursedAmountLabel());
        model.addAttribute("reportPaidCount", report.summary().paidCount());
        model.addAttribute("reportPaidAmountLabel", report.summary().getPaidAmountLabel());
        model.addAttribute("reportOngoingCount", report.summary().ongoingCount());
        model.addAttribute("reportRows", report.loans().stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("shortId", loan.getApplicationNumber() == null ? "" : loan.getApplicationNumber().toString());
                row.put("loanId", loan.getLoanId() == null ? "" : loan.getLoanId());
                row.put("applicantName", applicantNames.getOrDefault(loan.getApplicantMemberId(), "-"));
                row.put("loanTypeLabel", loan.getLoanType().getDisplayLabel());
                row.put("amount", loan.getAmount() == null ? "-" : loan.getAmount().toPlainString());
                row.put("disbursed", loan.getDisbursementDate() == null ? "-" : loan.getDisbursementDate().toString());
                row.put("finalDueDate", loan.getFinalDueDate() == null ? "-" : loan.getFinalDueDate().toString());
                row.put("statusLabel", switch (loan.getStatus()) {
                    case FINAL_APPROVED -> "DISBURSED LOAN";
                    case DEFAULTED -> "DEFAULTED";
                    case PAID -> "PAID";
                    default -> loan.getStatus().name();
                });
                row.put("paidAt", loan.getPaidAt() == null ? "-" : loan.getPaidAt().format(REPORT_DATE_TIME));
                return row;
            })
            .toList());
        return "manager/reports";
    }

    @GetMapping("/settings")
    public String settings(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("settings", managerService.getSettings(principal.getSaccoId()));
        return "manager/settings";
    }

    @PostMapping("/settings")
    public String updateSettings(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam Integer requiredGuarantors,
                                 @RequestParam Integer boardSize,
                                 @RequestParam Integer boardQuorum,
                                 @RequestParam String defaultLanguage,
                                 RedirectAttributes ra) {
        SaccoSettings settings = managerService.updateSettings(principal.getSaccoId(), requiredGuarantors,
            boardSize, boardQuorum, defaultLanguage);
        ra.addFlashAttribute("message", "Settings updated to quorum=" + settings.getBoardQuorum());
        return "redirect:/manager/settings";
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('MANAGER')")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        model.addAttribute("notifications", notificationInboxService.allViews(
            principal.getMemberId(), principal.getGrantedPositions()));
        model.addAttribute("highlightNotificationId", highlight);
        return "manager/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    @PreAuthorize("hasRole('MANAGER')")
    public String openNotification(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(),
                principal.getPosition(), "/manager/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/manager/notifications";
        }
    }

    @PostMapping("/notifications/mark-all-read")
    @PreAuthorize("hasRole('MANAGER')")
    public String markAllNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        if (updated > 0) {
            ra.addFlashAttribute("message", "All notifications have been marked as read.");
        } else {
            ra.addFlashAttribute("message", "There were no unread notifications.");
        }
        return "redirect:/manager/notifications";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/manager/loan-applications";
    }

    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank() || shouldHideField(key)) {
                    continue;
                }
                cleaned.put(humanizeFieldLabel(key), entry.getValue());
            }
            return cleaned;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private boolean shouldHideField(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("_csrf")
            || normalized.equals("purpose")
            || normalized.equals("additionalnotes");
    }

    private String humanizeFieldLabel(String key) {
        return switch (key) {
            case "nationalId" -> "National ID";
            case "employerName" -> "Employer Name";
            case "additionalNotes" -> "Additional Notes";
            case "hasExistingLoan" -> "Existing Loan";
            default -> key.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim();
        };
    }

    private String loanTypeLabel(com.sacco.mvp.domain.LoanType loanType) {
        if (loanType == null) {
            return "-";
        }
        return loanType.getDisplayLabel();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        java.text.DecimalFormat format = new java.text.DecimalFormat(
            "#,##0.00", new java.text.DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(amount);
    }

    private String shortLoanId(UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }

    private String humanizeEnum(String value) {
        return value == null ? "-" : value.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String dashboardStatusLabel(LoanStatus status) {
        return switch (status) {
            case SUBMITTED -> "Submitted";
            case READY_FOR_MANAGER -> "On Review By Manager";
            case AWAITING_BOARD -> "On Review By Board";
            case FINAL_APPROVED -> "Disbursed Loan";
            case DEFAULTED -> "Defaulted / Not Paid";
            case PAID -> "Paid";
            case MANAGER_REJECTED -> "Manager Rejected";
            case BOARD_REJECTED -> "Board Rejected";
            case FINAL_REJECTED -> "Final Rejected";
            case ALL_GUARANTORS_APPROVED -> "All Guarantors Approved";
            case AWAITING_GUARANTORS -> "Awaiting Guarantors";
            case BOARD_APPROVED -> "Reviewed";
            case MANAGER_ACCEPTED -> "Manager Accepted";
            case DRAFT -> "Draft";
        };
    }

    private String dashboardStatusColor(LoanStatus status) {
        return switch (status) {
            case DRAFT -> "#60A5FA";
            case SUBMITTED -> "#94A3B8";
            case AWAITING_GUARANTORS -> "#F59E0B";
            case ALL_GUARANTORS_APPROVED -> "#0F766E";
            case READY_FOR_MANAGER -> "#14B8A6";
            case AWAITING_BOARD -> "#6366F1";
            case BOARD_APPROVED -> "#2F348D";
            case FINAL_APPROVED -> "#22C55E";
            case DEFAULTED -> "#DC2626";
            case PAID -> "#16A34A";
            case MANAGER_REJECTED, BOARD_REJECTED, FINAL_REJECTED -> "#F43F5E";
            case MANAGER_ACCEPTED -> "#0EA5E9";
        };
    }

    private void addReviewDisplayAttributes(Model model,
                                            com.sacco.mvp.domain.LoanApplication app,
                                            ExternalAccountStatusService.ExternalAccountStatusView accountStatus,
                                            String managerReason) {
        boolean showReviewSidebar = !accountStatus.isAvailable()
            || (managerReason != null && !managerReason.isBlank())
            || app.getStatus() == LoanStatus.AWAITING_BOARD
            || app.getDisbursementDate() != null;

        model.addAttribute("loanIdShort", app.getApplicationNumber() == null ? "" : app.getApplicationNumber().toString());
        model.addAttribute("disbursedLoanId", app.getLoanId());
        model.addAttribute("showReviewSidebar", showReviewSidebar);
        model.addAttribute("managerReviewLayoutClass", showReviewSidebar ? "xl:grid-cols-[1.45fr_0.55fr]" : "");
        model.addAttribute("applicantDetailsGridClass", showReviewSidebar ? "sm:grid-cols-2" : "sm:grid-cols-2 xl:grid-cols-3");
        model.addAttribute("loanProgressItems", loanPresentationService.buildProgressItems(app));
        model.addAttribute("managerStatusBadgeClass", switch (app.getStatus()) {
            case READY_FOR_MANAGER -> "bg-amber-50 text-amber-700";
            case AWAITING_BOARD, MANAGER_ACCEPTED -> "bg-blue-50 text-blue-700";
            case BOARD_APPROVED, FINAL_APPROVED, PAID -> "bg-emerald-50 text-emerald-700";
            case DEFAULTED -> "bg-rose-50 text-rose-700";
            case MANAGER_REJECTED, BOARD_REJECTED, FINAL_REJECTED -> "bg-rose-50 text-rose-700";
            default -> "bg-slate-100 text-slate-700";
        });
    }
}
