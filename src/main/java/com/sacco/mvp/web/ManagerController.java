package com.sacco.mvp.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.ManagerService;
import com.sacco.mvp.service.ReversalRequestService;
import com.sacco.mvp.service.ExternalAccountStatusService;
import lombok.RequiredArgsConstructor;
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
        model.addAttribute("dashboardDisbursementYear", LocalDate.now().getYear());
        model.addAttribute("dashboardActiveDisbursedLoans", dashboard.activeDisbursedLoans());
        model.addAttribute("dashboardOnReviewByManagerLoans", dashboard.onReviewByManagerLoans());
        model.addAttribute("dashboardPaidLoans", dashboard.paidLoans());
        model.addAttribute("dashboardStatusRows", dashboard.statusBreakdown().stream()
            .map(entry -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("statusLabel", switch (entry.status()) {
                    case READY_FOR_MANAGER -> "ON REVIEW BY MANAGER";
                    case AWAITING_BOARD -> "ON REVIEW BY BOARD";
                    case FINAL_APPROVED -> "DISBURSED LOAN";
                    case PAID -> "PAID";
                    case MANAGER_REJECTED -> "MANAGER REJECTED";
                    case BOARD_REJECTED -> "BOARD REJECTED";
                    case FINAL_REJECTED -> "FINAL REJECTED";
                    case ALL_GUARANTORS_APPROVED -> "ALL GUARANTORS APPROVED";
                    case AWAITING_GUARANTORS -> "AWAITING GUARANTORS";
                    case BOARD_APPROVED -> "REVIEWED";
                    case MANAGER_ACCEPTED -> "MANAGER ACCEPTED";
                    case DRAFT -> "DRAFT";
                    default -> entry.status().name();
                });
                row.put("count", String.valueOf(entry.count()));
                return row;
            })
            .toList());
        model.addAttribute("dashboardDisbursementRows", dashboard.recentDisbursements().stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("applicantName", applicantNames.getOrDefault(loan.getApplicantMemberId(), "-"));
                row.put("loanTypeLabel", switch (loan.getLoanType()) {
                    case LOAN_ADVANCE -> "Loan Advance (Mkopo wa Chapchap)";
                    case EDUCATION_LOAN -> "Education Loan (Mkopo wa Elimu)";
                    case EMERGENCY_LOAN -> "Emergency Loan (Mkopo wa Dharura)";
                    case DEVELOPMENT_LOAN -> "Development Loan (Mkopo wa Biashara)";
                });
                row.put("statusLabel", loan.getStatus() == LoanStatus.PAID ? "PAID" : "DISBURSED LOAN");
                row.put("shortId", loan.getId().toString().substring(0, 8));
                row.put("amount", loan.getAmount() == null ? "-" : loan.getAmount().toPlainString());
                row.put("disbursementDate", loan.getDisbursementDate() == null ? "-" : loan.getDisbursementDate().toString());
                return row;
            })
            .toList());
        model.addAttribute("dashboardRecentDisbursementDays", 30);
        return "manager/dashboard";
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
        model.addAttribute("repaymentSummary", loanPresentationService.parseRepaymentSummary(app.getRepaymentScheduleJson()));
        model.addAttribute("repaymentRows", loanPresentationService.parseRepaymentRows(app.getRepaymentScheduleJson()));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("managerReason", managerReason);
        model.addAttribute("guarantorRequests", guarantorRequests);
        model.addAttribute("guarantorNames", guarantorNames);
        model.addAttribute("pendingManagerStageWithdrawal", reversalRequestService.pendingManagerStageWithdrawal(id));
        model.addAttribute("activeApplicantLoans", activeApplicantLoans.stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", loan.getId().toString());
                row.put("shortId", shortLoanId(loan.getId()));
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
        model.addAttribute("paidReversalWindowOpen",
            app.getStatus() == LoanStatus.PAID
                && app.getPaidAt() != null
                && app.getPaidAt().plusHours(24).isAfter(OffsetDateTime.now()));
        addReviewDisplayAttributes(model, app, applicantExternalAccountStatus, managerReason);
        return "manager/detail";
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
                           @RequestParam(required = false) String disbursementReference,
                           @RequestParam(required = false) String disbursementNotes,
                           RedirectAttributes ra) {
        managerService.finalizeDecision(id, decision, principal.getMemberId(),
            disbursementDate, firstRepaymentDate, null, null,
            disbursementReference, disbursementNotes);
        ra.addFlashAttribute("message", "Application finalized");
        return "redirect:/manager/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/mark-paid")
    public String markPaid(@PathVariable UUID id,
                           @AuthenticationPrincipal AppUserPrincipal principal,
                           RedirectAttributes ra) {
        managerService.markPaid(id, principal.getMemberId(), true);
        ra.addFlashAttribute("message", "Loan marked as paid.");
        return "redirect:/manager/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/mark-active")
    public String markActive(@PathVariable UUID id,
                             @AuthenticationPrincipal AppUserPrincipal principal,
                             RedirectAttributes ra) {
        managerService.markPaid(id, principal.getMemberId(), false);
        ra.addFlashAttribute("message", "Loan moved back to disbursed status.");
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
                row.put("shortId", loan.getId().toString().substring(0, 8));
                row.put("applicantName", applicantNames.getOrDefault(loan.getApplicantMemberId(), "-"));
                row.put("loanTypeLabel", switch (loan.getLoanType()) {
                    case LOAN_ADVANCE -> "Loan Advance (Mkopo wa Chapchap)";
                    case EDUCATION_LOAN -> "Education Loan (Mkopo wa Elimu)";
                    case EMERGENCY_LOAN -> "Emergency Loan (Mkopo wa Dharura)";
                    case DEVELOPMENT_LOAN -> "Development Loan (Mkopo wa Biashara)";
                });
                row.put("amount", loan.getAmount() == null ? "-" : loan.getAmount().toPlainString());
                row.put("disbursed", loan.getDisbursementDate() == null ? "-" : loan.getDisbursementDate().toString());
                row.put("finalDueDate", loan.getFinalDueDate() == null ? "-" : loan.getFinalDueDate().toString());
                row.put("statusLabel", switch (loan.getStatus()) {
                    case FINAL_APPROVED -> "DISBURSED LOAN";
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
        return switch (loanType) {
            case LOAN_ADVANCE -> "Loan Advance (Mkopo wa Chapchap)";
            case EDUCATION_LOAN -> "Education Loan (Mkopo wa Elimu)";
            case EMERGENCY_LOAN -> "Emergency Loan (Mkopo wa Dharura)";
            case DEVELOPMENT_LOAN -> "Development Loan (Mkopo wa Biashara)";
        };
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

    private void addReviewDisplayAttributes(Model model,
                                            com.sacco.mvp.domain.LoanApplication app,
                                            ExternalAccountStatusService.ExternalAccountStatusView accountStatus,
                                            String managerReason) {
        boolean showReviewSidebar = !accountStatus.isAvailable()
            || (managerReason != null && !managerReason.isBlank())
            || app.getStatus() == LoanStatus.AWAITING_BOARD
            || app.getDisbursementDate() != null;

        model.addAttribute("loanIdShort", app.getId() == null ? "" : app.getId().toString().substring(0, 8));
        model.addAttribute("showReviewSidebar", showReviewSidebar);
        model.addAttribute("managerReviewLayoutClass", showReviewSidebar ? "xl:grid-cols-[1.45fr_0.55fr]" : "");
        model.addAttribute("applicantDetailsGridClass", showReviewSidebar ? "sm:grid-cols-2" : "sm:grid-cols-2 xl:grid-cols-3");
        model.addAttribute("progressStep", switch (app.getStatus()) {
            case DRAFT -> 1;
            case SUBMITTED, AWAITING_GUARANTORS, ALL_GUARANTORS_APPROVED -> 2;
            case READY_FOR_MANAGER, MANAGER_REJECTED -> 3;
            case MANAGER_ACCEPTED, AWAITING_BOARD, BOARD_REJECTED, BOARD_APPROVED -> 4;
            case FINAL_REJECTED, FINAL_APPROVED, PAID -> 5;
        });
        model.addAttribute("managerStatusBadgeClass", switch (app.getStatus()) {
            case READY_FOR_MANAGER -> "bg-amber-50 text-amber-700";
            case AWAITING_BOARD, MANAGER_ACCEPTED -> "bg-blue-50 text-blue-700";
            case BOARD_APPROVED, FINAL_APPROVED, PAID -> "bg-emerald-50 text-emerald-700";
            case MANAGER_REJECTED, BOARD_REJECTED, FINAL_REJECTED -> "bg-rose-50 text-rose-700";
            default -> "bg-slate-100 text-slate-700";
        });
    }
}
