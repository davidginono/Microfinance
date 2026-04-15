package com.sacco.mvp.web;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.EligibilityService;
import com.sacco.mvp.service.FinancialDetailsService;
import com.sacco.mvp.service.FormSchemaService;
import com.sacco.mvp.service.AdminService;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.ExternalAccountStatusService;
import com.sacco.mvp.service.LoanWorkflowService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.ReversalRequestService;
import com.sacco.mvp.integration.foresight.ForesightAccountSummary;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.service.dto.FormModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/app")
public class AppController {
    private static final long REVERSAL_WINDOW_HOURS = 24L;
    private static final String DISMISSED_ACTIVE_LOAN_CHARTS_KEY = "dismissedActiveLoanCharts";
    private final LoanWorkflowService loanWorkflowService;
    private final FormSchemaService formSchemaService;
    private final EligibilityService eligibilityService;
    private final FinancialDetailsService financialDetailsService;
    private final LoanPresentationService loanPresentationService;
    private final LoanReportService loanReportService;
    private final AdminService adminService;
    private final EmailOtpService emailOtpService;
    private final ExternalAccountStatusService externalAccountStatusService;
    private final NotificationInboxService notificationInboxService;
    private final ReversalRequestService reversalRequestService;
    private final MemberRepository memberRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ForesightDirectoryService foresightDirectoryService;
    private final ObjectMapper objectMapper;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<GuarantorRequest> pendingGuarantees = activeGuarantorRequests(
            loanWorkflowService.myGuarantorRequests(principal.getMemberId()));
        List<LoanApplication> allApplications = loanWorkflowService.myApplications(principal.getMemberId());
        List<LoanApplication> currentApplications = currentApplications(allApplications);
        List<LoanApplication> archivedApplications = archivedApplications(allApplications);
        Map<LoanStatus, Long> statusCounts = currentApplications.stream()
            .filter(app -> isStatusChartIncluded(app.getStatus()))
            .collect(Collectors.groupingBy(LoanApplication::getStatus, LinkedHashMap::new, Collectors.counting()));
        List<LoanApplication> activeLoans = activeRepaymentLoans(allApplications);
        long openApplications = currentApplications.stream()
            .filter(this::isPendingApplication)
            .count();
        long rejectedLoans = archivedApplications.stream()
            .filter(app -> isRejectedStatus(app.getStatus()))
            .count();
        long pendingGuaranteeApprovals = pendingGuarantees.stream()
            .filter(req -> req.getStatus() == GuarantorRequestStatus.PENDING)
            .count();
        long loansAwaitingDecision = currentApplications.stream()
            .filter(this::isAwaitingDecisionStage)
            .count();
        Set<UUID> dismissedActiveLoanChartIds = dismissedActiveLoanChartIds(principal.getMemberId());
        List<Map<String, Object>> activeLoanChartRows = buildActiveLoanChartRows(activeLoans, dismissedActiveLoanChartIds);

        model.addAttribute("myApplications", currentApplications);
        model.addAttribute("managerReasons", loanPresentationService.latestManagerReasons(allApplications));
        model.addAttribute("pendingGuarantees", pendingGuarantees);
        model.addAttribute("totalApplications", openApplications);
        model.addAttribute("currentApplicationCount", currentApplications.size());
        model.addAttribute("activeLoanCount", activeLoans.size());
        model.addAttribute("activeLoanChartCount", activeLoanChartRows.size());
        model.addAttribute("rejectedLoanCount", rejectedLoans);
        model.addAttribute("pendingGuaranteeApprovals", pendingGuaranteeApprovals);
        model.addAttribute("loansAwaitingDecision", loansAwaitingDecision);
        model.addAttribute("statusChartRows", buildStatusChartRows(statusCounts));
        model.addAttribute("activeLoanChartRows", activeLoanChartRows);
        model.addAttribute("archivedApplicationCount", archivedApplications.size());
        addGuaranteeContext(pendingGuarantees, model);
        return "app/dashboard";
    }

    @PostMapping("/dashboard/active-loans/{loanId}/seen")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String markExpiredActiveLoanChartSeen(@AuthenticationPrincipal AppUserPrincipal principal,
                                                 @PathVariable UUID loanId,
                                                 RedirectAttributes ra) {
        LoanApplication app = loanApplicationRepository.findByIdAndApplicantMemberId(loanId, principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        if (app.getStatus() != LoanStatus.FINAL_APPROVED) {
            ra.addFlashAttribute("error", "Only active disbursed loans can be removed from the repayment timeline.");
            return "redirect:/app/dashboard";
        }
        if (!hasRepaymentTimeframeEnded(app)) {
            ra.addFlashAttribute("error", "This repayment timeline is still active.");
            return "redirect:/app/dashboard";
        }
        dismissActiveLoanChart(principal.getMemberId(), loanId);
        return "redirect:/app/dashboard";
    }

    @GetMapping("/loan-products")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String products(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("products", loanWorkflowService.listProducts(principal.getSaccoId()));
        loanWorkflowService.findApplicationInProgress(principal.getMemberId()).ifPresent(app -> {
            model.addAttribute("applicationLockApp", app);
            model.addAttribute("applicationLockStatusLabel", dashboardStatusLabel(app.getStatus()));
        });
        return "app/loan-products";
    }

    @GetMapping("/loan-applications")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String listMyApps(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<LoanApplication> allApplications = loanWorkflowService.myApplications(principal.getMemberId());
        List<LoanApplication> apps = currentApplications(allApplications);
        model.addAttribute("apps", apps);
        model.addAttribute("managerReasons", loanPresentationService.latestManagerReasons(allApplications));
        model.addAttribute("archiveCount", archivedApplications(allApplications).size());
        return "app/loan-applications";
    }

    @GetMapping("/archives")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String archives(@AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false, defaultValue = "loans") String section,
                           @RequestParam(required = false) String loanArchiveQuery,
                           @RequestParam(required = false, defaultValue = "ALL") String loanArchiveFilter,
                           @RequestParam(required = false) String guarantorArchiveQuery,
                           @RequestParam(required = false, defaultValue = "ALL") String guarantorArchiveFilter,
                           Model model) {
        List<LoanApplication> allApplications = loanWorkflowService.myApplications(principal.getMemberId());
        List<LoanApplication> allArchives = archivedApplications(allApplications);
        List<GuarantorRequest> allGuarantorArchives = archivedGuarantorRequests(
            loanWorkflowService.myGuarantorRequests(principal.getMemberId()));
        List<LoanApplication> archives = filterLoanArchives(allArchives, loanArchiveQuery, loanArchiveFilter);
        List<GuarantorRequest> guarantorArchives = filterGuarantorArchives(
            allGuarantorArchives, guarantorArchiveQuery, guarantorArchiveFilter);
        model.addAttribute("archives", archives);
        model.addAttribute("guarantorArchives", guarantorArchives);
        model.addAttribute("archiveSection", normalizeArchiveSection(section));
        model.addAttribute("loanArchiveQuery", safeArchiveQuery(loanArchiveQuery));
        model.addAttribute("loanArchiveFilter", safeArchiveFilter(loanArchiveFilter));
        model.addAttribute("guarantorArchiveQuery", safeArchiveQuery(guarantorArchiveQuery));
        model.addAttribute("guarantorArchiveFilter", safeArchiveFilter(guarantorArchiveFilter));
        model.addAttribute("totalArchivedRecordCount", allArchives.size() + allGuarantorArchives.size());
        model.addAttribute("totalLoanArchiveCount", allArchives.size());
        model.addAttribute("totalGuarantorArchiveCount", allGuarantorArchives.size());
        model.addAttribute("disbursedArchiveCount", allArchives.stream()
            .filter(app -> app.getStatus() == LoanStatus.FINAL_APPROVED)
            .count());
        model.addAttribute("managerReasons", loanPresentationService.latestManagerReasons(allApplications));
        addGuaranteeActionContext(allGuarantorArchives, model);
        return "app/archives";
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String reports(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LoanReportService.MemberLoanReport report = loanReportService.memberReport(principal.getMemberId());
        model.addAttribute("reportOwnerName", report.member().getFullName());
        model.addAttribute("reportDisbursedCount", report.summary().disbursedCount());
        model.addAttribute("reportDisbursedAmountLabel", report.summary().getDisbursedAmountLabel());
        model.addAttribute("reportPaidCount", report.summary().paidCount());
        model.addAttribute("reportPaidAmountLabel", report.summary().getPaidAmountLabel());
        model.addAttribute("reportOngoingCount", report.summary().ongoingCount());
        model.addAttribute("reportRows", report.loans().stream()
            .map(loan -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("shortId", loan.getId().toString().substring(0, 8));
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
                row.put("paidAt", loan.getPaidAt() == null ? "-" : loan.getPaidAt().toString().replace('T', ' ').substring(0, 16));
                return row;
            })
            .toList());
        return "app/reports";
    }

    private List<Map<String, Object>> buildActiveLoanChartRows(List<LoanApplication> activeLoans,
                                                               Set<UUID> dismissedChartIds) {
        LocalDate today = LocalDate.now();
        return activeLoans.stream()
            .sorted(Comparator.comparing(LoanApplication::getFinalDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .filter(app -> !hasRepaymentTimeframeEnded(app) || !dismissedChartIds.contains(app.getId()))
            .map(app -> {
                Map<String, Object> row = new LinkedHashMap<>();
                long daysLeft = app.getFinalDueDate() == null ? 0 : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, app.getFinalDueDate()));
                long totalDays = 0L;
                if (app.getDisbursementDate() != null && app.getFinalDueDate() != null) {
                    totalDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(app.getDisbursementDate(), app.getFinalDueDate()));
                } else if (app.getFirstRepaymentDate() != null && app.getFinalDueDate() != null) {
                    totalDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(app.getFirstRepaymentDate(), app.getFinalDueDate()));
                }
                long elapsedDays = Math.max(0, totalDays - daysLeft);
                long remainingPercent = totalDays <= 0
                    ? (daysLeft > 0 ? 100 : 0)
                    : Math.max(0, Math.min(100, Math.round((daysLeft * 100.0d) / totalDays)));

                row.put("fullId", app.getId());
                row.put("loanId", app.getId().toString().substring(0, 8));
                row.put("amountLabel", loanPresentationService.formatMoneyDisplay(app.getAmount()));
                row.put("daysLeft", daysLeft);
                row.put("elapsedDays", elapsedDays);
                row.put("totalDays", totalDays);
                row.put("remainingPercent", remainingPercent);
                row.put("finalDueDate", app.getFinalDueDate());
                row.put("countdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
                row.put("canDismiss", hasRepaymentTimeframeEnded(app));
                return row;
            })
            .toList();
    }

    private List<Map<String, Object>> buildStatusChartRows(Map<LoanStatus, Long> statusCounts) {
        List<LoanStatus> statusOrder = List.of(
            LoanStatus.DRAFT,
            LoanStatus.AWAITING_GUARANTORS,
            LoanStatus.ALL_GUARANTORS_APPROVED,
            LoanStatus.READY_FOR_MANAGER,
            LoanStatus.AWAITING_BOARD,
            LoanStatus.FINAL_APPROVED
        );
        long maxCount = statusOrder.stream()
            .map(status -> statusCounts.getOrDefault(status, 0L))
            .max(Long::compareTo)
            .orElse(1L);
        long safeMax = Math.max(1L, maxCount);

        return statusOrder.stream()
            .map(status -> {
                long count = statusCounts.getOrDefault(status, 0L);
                long heightPercent = count <= 0 ? 6L : Math.max(18L, Math.round((count * 100.0d) / safeMax));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", status.name());
                row.put("label", dashboardStatusLabel(status));
                row.put("count", count);
                row.put("heightPercent", heightPercent);
                row.put("color", dashboardStatusColor(status));
                return row;
            })
            .toList();
    }

    @GetMapping("/loan-applications/new")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String newApp(@AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam LoanType loanType,
                         @RequestParam(required = false) UUID topUpLoanId,
                         RedirectAttributes ra,
                         Model model) {
        Optional<LoanApplication> blockingApplication = loanWorkflowService.findApplicationInProgress(principal.getMemberId());
        if (blockingApplication.isPresent()) {
            LoanApplication app = blockingApplication.get();
            ra.addFlashAttribute(
                "error",
                "You already have loan application " + app.getId().toString().substring(0, 8)
                    + " on review (" + dashboardStatusLabel(app.getStatus())
                    + "). Continue it until it is disbursed before applying again."
            );
            return "redirect:/app/loan-applications/" + app.getId();
        }
        Map<String, String> formValues = new LinkedHashMap<>();
        if (topUpLoanId != null) {
            formValues.put("topUpLoanId", topUpLoanId.toString());
        }
        return prepareLoanNewModel(principal, loanType, formValues, Collections.emptyList(), null, model);
    }

    @GetMapping("/loan-applications/{id}/edit")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String editDraft(@PathVariable UUID id,
                            @AuthenticationPrincipal AppUserPrincipal principal,
                            Model model,
                            RedirectAttributes ra) {
        LoanApplication app = loanWorkflowService.getMine(id, principal.getMemberId());
        if (app.getStatus() != LoanStatus.DRAFT) {
            ra.addFlashAttribute("error", "Only draft applications can be edited.");
            return "redirect:/app/loan-applications/" + id;
        }

        Map<String, String> formValues = new LinkedHashMap<>();
        formValues.put("applicationId", app.getId().toString());
        formValues.put("amount", app.getAmount() == null ? "" : app.getAmount().toPlainString());
        formValues.put("tenorMonths", app.getTenorMonths() == null ? "" : String.valueOf(app.getTenorMonths()));
        formValues.put("financialSnapshotJson", app.getFinancialSnapshot());
        if (app.getTopUpSourceLoanId() != null) {
            formValues.put("topUpLoanId", app.getTopUpSourceLoanId().toString());
        }
        formValues.putAll(parseJsonAsStringMap(app.getFormData()));

        return prepareLoanNewModel(
            principal,
            app.getLoanType(),
            formValues,
            parseUuidList(app.getSelectedGuarantors()),
            app.getRequiredGuarantors(),
            model
        );
    }

    @PostMapping("/loan-applications")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public String createDraft(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam LoanType loanType,
                              @RequestParam BigDecimal amount,
                              @RequestParam Integer tenorMonths,
                              @RequestParam(required = false) UUID applicationId,
                              @RequestParam(required = false) String action,
                              @RequestParam(required = false) String applicantSignatureOtpCode,
                              @RequestParam(required = false) List<UUID> guarantorIds,
                              @RequestParam(required = false) String financialSnapshotJson,
                              @RequestParam(required = false) UUID topUpLoanId,
                              @RequestParam Map<String, String> params,
                              @RequestParam(required = false, name = "attachments") List<MultipartFile> attachments,
                              RedirectAttributes ra,
                              Model model) {
        Map<String, String> submittedValues = new LinkedHashMap<>(params);
        Map<String, String> formPayload = new LinkedHashMap<>(params);
        formPayload.remove("loanType");
        formPayload.remove("amount");
        formPayload.remove("tenorMonths");
        formPayload.remove("applicationId");
        formPayload.remove("action");
        formPayload.remove("guarantorIds");
        formPayload.remove("financialSnapshotJson");
        formPayload.remove("topUpLoanId");
        formPayload.remove("_csrf");

        try {
            if ("SEND_TO_GUARANTORS".equalsIgnoreCase(action)) {
                if (applicationId == null) {
                    throw new IllegalStateException("Save the application as a draft before submitting it.");
                }
                UUID applicantSignatureOtpTokenId = null;
                if (requiresApplicantOtpBeforeImmediateSubmission(principal.getSaccoId(), loanType)) {
                    applicantSignatureOtpTokenId = validateApplicantSignatureOtp(principal.getMemberId(), applicantSignatureOtpCode);
                }
                LoanApplication submitted = loanWorkflowService.saveAndSubmit(
                    principal.getSaccoId(),
                    principal.getMemberId(),
                    loanType,
                    amount,
                    tenorMonths,
                    formPayload,
                    applicationId,
                    guarantorIds,
                    financialSnapshotJson,
                    topUpLoanId,
                    attachments
                );
                if (submitted.getStatus() == LoanStatus.AWAITING_GUARANTORS) {
                    ra.addFlashAttribute("message", "Application sent to guarantors successfully. Current status: " + submitted.getStatus());
                } else if (submitted.getRequiredGuarantors() <= 0) {
                    Member signingMember = requireMemberWithSavedSignature(principal.getMemberId());
                    loanWorkflowService.recordApplicantSignature(
                        submitted.getId(),
                        principal.getMemberId(),
                        signingMember.getSignatureText(),
                        OffsetDateTime.now()
                    );
                    if (applicantSignatureOtpTokenId != null) {
                        emailOtpService.consumeOtpById(applicantSignatureOtpTokenId);
                    }
                    ra.addFlashAttribute("message", "Application submitted and sent for manager review. Current status: " + submitted.getStatus());
                } else {
                    ra.addFlashAttribute("message", "Application updated successfully. Current status: " + submitted.getStatus());
                }
                return "redirect:/app/loan-applications";
            }

            LoanApplication app = loanWorkflowService.saveDraft(principal.getSaccoId(), principal.getMemberId(), loanType,
                amount, tenorMonths, formPayload, applicationId, guarantorIds, financialSnapshotJson, topUpLoanId, attachments);

            ra.addFlashAttribute("message", "Draft saved successfully. You can continue editing.");
            return "redirect:/app/loan-applications/" + app.getId() + "/edit";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("error", humanizeLoanFormError(ex.getMessage(), principal, loanType));
            Integer requiredGuarantorsOverride = resolveDraftRequiredGuarantors(principal.getMemberId(), applicationId);
            return prepareLoanNewModel(
                principal,
                loanType,
                submittedValues,
                guarantorIds,
                requiredGuarantorsOverride,
                model
            );
        }
    }

    @GetMapping("/loan-applications/{id}")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String viewMine(@PathVariable UUID id, Model model) {
        LoanApplication app = loanApplicationRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        Member applicant = memberRepository.findById(app.getApplicantMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Applicant member not found"));
        List<GuarantorRequest> guarantorRequests = guarantorRequestRepository.findByLoanApplicationId(id);
        Set<UUID> guarantorIds = new HashSet<>();
        for (GuarantorRequest req : guarantorRequests) {
            guarantorIds.add(req.getGuarantorMemberId());
        }
        Map<UUID, String> guarantorNames = new HashMap<>();
        if (!guarantorIds.isEmpty()) {
            for (Member member : memberRepository.findAllById(guarantorIds)) {
                guarantorNames.put(member.getId(), member.getFullName());
            }
        }
        model.addAttribute("app", app);
        addMemberLoanViewDisplayAttributes(model, app);
        model.addAttribute("applicantExternalAccountStatus", externalAccountStatusService.resolve(applicant));
        model.addAttribute("topUpSourceLoan",
            app.getTopUpSourceLoanId() == null ? null
                : loanApplicationRepository.findByIdAndApplicantMemberId(app.getTopUpSourceLoanId(), app.getApplicantMemberId()).orElse(null));
        model.addAttribute("canRequestTopUp",
            app.getStatus() == LoanStatus.FINAL_APPROVED && (app.getFinalDueDate() == null || !app.getFinalDueDate().isBefore(java.time.LocalDate.now())));
        model.addAttribute("formFields", loanPresentationService.parseFormFields(app.getFormData()));
        model.addAttribute("guarantorRequests", guarantorRequests);
        model.addAttribute("guarantorNames", guarantorNames);
        Map<UUID, ReversalRequest> pendingGuarantorUndoRequests = new HashMap<>();
        for (GuarantorRequest guarantorRequest : guarantorRequests) {
            ReversalRequest pendingUndo = reversalRequestService.pendingGuarantorUndo(guarantorRequest.getId());
            if (pendingUndo != null) {
                pendingGuarantorUndoRequests.put(guarantorRequest.getId(), pendingUndo);
            }
        }
        model.addAttribute("pendingGuarantorUndoRequests", pendingGuarantorUndoRequests);
        model.addAttribute("pendingManagerStageWithdrawal", reversalRequestService.pendingManagerStageWithdrawal(id));
        OffsetDateTime memberReversalReferenceAt = app.getStatus() == LoanStatus.READY_FOR_MANAGER
            ? app.getUpdatedAt()
            : app.getSubmittedAt();
        model.addAttribute("memberReversalWindowOpen", isWithinReversalWindow(memberReversalReferenceAt));
        model.addAttribute("draftSelectedGuarantors",
            memberRepository.findAllById(parseUuidList(app.getSelectedGuarantors())).stream()
                .map(member -> member.getMemberNo() + " - " + member.getFullName())
                .toList());
        model.addAttribute("managerReason",
            app.getStatus() == LoanStatus.MANAGER_REJECTED ? loanPresentationService.latestManagerReason(id) : "");
        model.addAttribute("financialFields", loanPresentationService.parseFinancialFields(app.getFinancialSnapshot()));
        model.addAttribute("attachments", loanPresentationService.parseAttachments(app.getAttachmentsJson()));
        model.addAttribute("repaymentSummary", loanPresentationService.parseRepaymentSummary(app.getRepaymentScheduleJson()));
        model.addAttribute("repaymentRows", loanPresentationService.parseRepaymentRows(app.getRepaymentScheduleJson()));
        model.addAttribute("repaymentCountdown", loanPresentationService.countdownLabel(app.getFinalDueDate()));
        model.addAttribute("repaymentDaysLeft",
            app.getFinalDueDate() == null ? null : java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), app.getFinalDueDate()));
        model.addAttribute("repaymentWeeksLeft",
            app.getFinalDueDate() == null ? null : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), app.getFinalDueDate()) / 7));
        model.addAttribute("repaymentMonthsLeft",
            app.getFinalDueDate() == null ? null : Math.max(0, java.time.temporal.ChronoUnit.MONTHS.between(java.time.LocalDate.now().withDayOfMonth(1), app.getFinalDueDate().withDayOfMonth(1))));
        model.addAttribute("savedSignatureText", resolveSavedSignatureText(app.getApplicantMemberId()));
        model.addAttribute("canPrint",
            app.getFinancialSnapshot() != null
                && !app.getFinancialSnapshot().isBlank()
                && app.getStatus() != LoanStatus.DRAFT
                && app.getStatus() != LoanStatus.AWAITING_GUARANTORS
                && app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED
                && guarantorRequests.stream().filter(req -> req.getStatus() == GuarantorRequestStatus.APPROVED).count() >= app.getRequiredGuarantors());
        model.addAttribute("statusTimeline", List.of(
            LoanStatus.DRAFT, LoanStatus.AWAITING_GUARANTORS, LoanStatus.ALL_GUARANTORS_APPROVED, LoanStatus.READY_FOR_MANAGER,
            LoanStatus.MANAGER_ACCEPTED, LoanStatus.AWAITING_BOARD, LoanStatus.BOARD_APPROVED, LoanStatus.FINAL_APPROVED, LoanStatus.PAID
        ));
        return "app/loan-view";
    }

    private void addMemberLoanViewDisplayAttributes(Model model, LoanApplication app) {
        int progressStep = memberLoanProgressStep(app.getStatus());
        int progressPercent = memberLoanProgressPercent(app.getStatus());

        model.addAttribute("loanIdShort", app.getId() == null ? "" : app.getId().toString().substring(0, 8));
        model.addAttribute("loanProgressStep", progressStep);
        model.addAttribute("loanProgressPercent", progressPercent);
        model.addAttribute("loanStatusBadgeClass", memberLoanStatusBadgeClass(app.getStatus()));
        model.addAttribute("loanStep1CardClass", progressStep >= 1 ? "border-cyan-200 bg-cyan-50" : "border-slate-200 bg-white");
        model.addAttribute("loanStep1DotClass", progressStep >= 1 ? "bg-cyan-600 text-white" : "bg-slate-200 text-slate-500");
        model.addAttribute("loanStep1TextClass", progressStep >= 1 ? "text-slate-900" : "text-slate-500");
        model.addAttribute("loanStep2CardClass", progressStep >= 2 ? "border-cyan-200 bg-cyan-50" : "border-slate-200 bg-white");
        model.addAttribute("loanStep2DotClass", progressStep >= 2 ? "bg-cyan-600 text-white" : "bg-slate-200 text-slate-500");
        model.addAttribute("loanStep2TextClass", progressStep >= 2 ? "text-slate-900" : "text-slate-500");
        model.addAttribute("loanStep3CardClass", progressStep >= 3 ? "border-cyan-200 bg-cyan-50" : "border-slate-200 bg-white");
        model.addAttribute("loanStep3DotClass", progressStep >= 3 ? "bg-cyan-600 text-white" : "bg-slate-200 text-slate-500");
        model.addAttribute("loanStep3TextClass", progressStep >= 3 ? "text-slate-900" : "text-slate-500");
        model.addAttribute("loanStep4CardClass", progressStep >= 4 ? "border-cyan-200 bg-cyan-50" : "border-slate-200 bg-white");
        model.addAttribute("loanStep4DotClass", progressStep >= 4 ? "bg-cyan-600 text-white" : "bg-slate-200 text-slate-500");
        model.addAttribute("loanStep4TextClass", progressStep >= 4 ? "text-slate-900" : "text-slate-500");
        model.addAttribute("loanStep5CardClass", progressStep >= 5 ? "border-cyan-200 bg-cyan-50" : "border-slate-200 bg-white");
        model.addAttribute("loanStep5DotClass", progressStep >= 5 ? "bg-cyan-600 text-white" : "bg-slate-200 text-slate-500");
        model.addAttribute("loanStep5TextClass", progressStep >= 5 ? "text-slate-900" : "text-slate-500");
    }

    private int memberLoanProgressStep(LoanStatus status) {
        if (status == null) {
            return 1;
        }
        return switch (status) {
            case DRAFT -> 1;
            case SUBMITTED, AWAITING_GUARANTORS, ALL_GUARANTORS_APPROVED -> 2;
            case READY_FOR_MANAGER, MANAGER_REJECTED, MANAGER_ACCEPTED -> 3;
            case AWAITING_BOARD, BOARD_REJECTED, BOARD_APPROVED -> 4;
            case FINAL_REJECTED, FINAL_APPROVED, PAID -> 5;
        };
    }

    private int memberLoanProgressPercent(LoanStatus status) {
        if (status == null) {
            return 8;
        }
        return switch (status) {
            case DRAFT -> 10;
            case SUBMITTED, AWAITING_GUARANTORS, ALL_GUARANTORS_APPROVED -> 35;
            case READY_FOR_MANAGER, MANAGER_REJECTED, MANAGER_ACCEPTED -> 58;
            case AWAITING_BOARD, BOARD_REJECTED, BOARD_APPROVED -> 80;
            case FINAL_REJECTED, FINAL_APPROVED, PAID -> 100;
        };
    }

    private String memberLoanStatusBadgeClass(LoanStatus status) {
        if (status == null) {
            return "bg-slate-100 text-slate-700";
        }
        return switch (status) {
            case DRAFT -> "bg-slate-100 text-slate-700";
            case SUBMITTED, AWAITING_GUARANTORS, ALL_GUARANTORS_APPROVED -> "bg-cyan-50 text-cyan-700";
            case READY_FOR_MANAGER -> "bg-amber-50 text-amber-700";
            case MANAGER_REJECTED, BOARD_REJECTED -> "bg-rose-50 text-rose-700";
            case AWAITING_BOARD -> "bg-blue-50 text-blue-700";
            case MANAGER_ACCEPTED, BOARD_APPROVED, FINAL_APPROVED, PAID -> "bg-emerald-50 text-emerald-700";
            case FINAL_REJECTED -> "bg-rose-50 text-rose-700";
        };
    }

    @PostMapping("/loan-applications/{id}/submit")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String submit(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal principal,
                         @RequestParam(required = false) String applicantSignatureOtpCode,
                         RedirectAttributes ra) {
        try {
            LoanApplication current = loanWorkflowService.getMine(id, principal.getMemberId());
            UUID applicantSignatureOtpTokenId = validateApplicantSignatureOtpIfRequired(current, principal.getMemberId(), applicantSignatureOtpCode);
            LoanApplication app = loanWorkflowService.submit(id, principal.getMemberId());
            if (app.getStatus() == LoanStatus.AWAITING_GUARANTORS) {
                ra.addFlashAttribute("message", "Application sent to guarantors successfully.");
                return "redirect:/app/loan-applications/" + id;
            }
            if (app.getStatus() == LoanStatus.READY_FOR_MANAGER) {
                Member signingMember = requireMemberWithSavedSignature(principal.getMemberId());
                loanWorkflowService.recordApplicantSignature(
                    app.getId(),
                    principal.getMemberId(),
                    signingMember.getSignatureText(),
                    OffsetDateTime.now()
                );
                if (applicantSignatureOtpTokenId != null) {
                    emailOtpService.consumeOtpById(applicantSignatureOtpTokenId);
                }
                ra.addFlashAttribute("message", "Application submitted and sent for manager review.");
                return "redirect:/app/loan-applications/" + id;
            }
            ra.addFlashAttribute("message", "Application status updated successfully.");
            return "redirect:/app/loan-applications/" + id;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/loan-applications/" + id;
        }
    }

    @PostMapping("/loan-applications/{id}/cancel")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String cancelSubmission(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            LoanApplication app = loanWorkflowService.getMine(id, principal.getMemberId());
            if (app.getStatus() == LoanStatus.READY_FOR_MANAGER) {
                reversalRequestService.requestManagerStageWithdrawal(id, principal.getMemberId());
                ra.addFlashAttribute("message", "Removal request sent to the manager for approval.");
                return "redirect:/app/loan-applications/" + id;
            }
            loanWorkflowService.cancelSubmission(id, principal.getMemberId());
            ra.addFlashAttribute("message", "Application moved back to draft.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + id;
    }

    @GetMapping("/loan-applications/{id}/guarantors")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String guarantorSelection(@PathVariable UUID id,
                                     @AuthenticationPrincipal AppUserPrincipal principal,
                                     @RequestParam(defaultValue = "") String q,
                                     @RequestParam(defaultValue = "0") int page,
                                     RedirectAttributes ra) {
        loanWorkflowService.getMine(id, principal.getMemberId());
        ra.addFlashAttribute("message", "Guarantors are selected directly from the application form before submission.");
        return "redirect:/app/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/guarantors")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String saveGuarantors(@PathVariable UUID id,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false) List<UUID> guarantorIds,
                                 RedirectAttributes ra) {
        loanWorkflowService.selectGuarantors(id, principal.getMemberId(), guarantorIds);
        ra.addFlashAttribute("message", "Guarantors assigned successfully");
        return "redirect:/app/loan-applications/" + id;
    }

    @PostMapping("/loan-applications/{id}/delete")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#id, principal)")
    public String deleteApplication(@PathVariable UUID id,
                                    @AuthenticationPrincipal AppUserPrincipal principal,
                                    RedirectAttributes ra) {
        try {
            loanWorkflowService.deleteApplication(id, principal.getMemberId());
            ra.addFlashAttribute("message", "Application removed successfully.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/loan-applications/" + id;
        }
        return "redirect:/app/loan-applications";
    }

    @GetMapping("/guarantors/search")
    @ResponseBody
    public List<Map<String, String>> searchGuarantors(@AuthenticationPrincipal AppUserPrincipal principal,
                                                      @RequestParam(defaultValue = "") String q) {
        String query = q == null ? "" : q.trim().toUpperCase(Locale.ROOT);
        boolean sixDigits = query.matches("\\d{6}");
        boolean fullMemberNo = query.matches("[A-Z0-9]{6,20}") && query.chars().anyMatch(Character::isDigit);
        if (!sixDigits && !fullMemberNo) {
            return Collections.emptyList();
        }
        return loanWorkflowService.searchGuarantors(principal.getSaccoId(), principal.getMemberId(), query, 0, 6)
            .getContent()
            .stream()
            .map(member -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("id", member.getId().toString());
                row.put("memberNo", member.getMemberNo());
                row.put("fullName", member.getFullName());
                return row;
            })
            .toList();
    }

    @PostMapping("/loan-applications/financial-preview")
    @ResponseBody
    public Map<String, Object> financialPreview(@AuthenticationPrincipal AppUserPrincipal principal,
                                                @RequestParam LoanType loanType,
                                                @RequestParam BigDecimal amount,
                                                @RequestParam Integer tenorMonths,
                                                @RequestParam(required = false) UUID topUpLoanId) {
        Map<String, Object> snapshot = financialDetailsService.generateSnapshot(
            principal.getSaccoId(), principal.getMemberId(), loanType, amount, tenorMonths, topUpLoanId);
        EligibilityService.EligibilityResult eligibility = eligibilityService.check(
            principal.getSaccoId(), principal.getMemberId(), loanType, amount);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("snapshotJson", financialDetailsService.toJson(snapshot));
        response.put("fields", loanPresentationService.parseFinancialFields(financialDetailsService.toJson(snapshot)));
        response.put("message", "Loan details loaded");
        Map<String, Object> eligibilityMap = new LinkedHashMap<>();
        eligibilityMap.put("eligible", eligibility.eligible());
        eligibilityMap.put("savingsLabel", formatTzs(eligibility.savings()));
        eligibilityMap.put("maxAllowedLabel", formatTzs(eligibility.maxAllowed()));
        eligibilityMap.put("ratioPercentLabel",
            eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
        response.put("eligibility", eligibilityMap);
        return response;
    }

    @GetMapping("/loan-applications/external-eligibility-summary")
    @ResponseBody
    public Map<String, Object> externalEligibilitySummary(@AuthenticationPrincipal AppUserPrincipal principal,
                                                          @RequestParam LoanType loanType) {
        Member member = memberRepository.findById(principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Logged-in member was not found."));
        if (member.getStationId() == null || member.getStationId().isBlank()) {
            throw new IllegalStateException("Station ID is not configured for this member.");
        }
        SaccoSettings sacco = saccoSettingsRepository.findById(principal.getSaccoId())
            .orElseThrow(() -> new IllegalArgumentException("SACCO settings missing"));

        ForesightAccountSummary summary = foresightDirectoryService.fetchAccountSummary(principal.getUsername(), member.getStationId());
        BigDecimal savings = summary == null || summary.savingsBalance() == null
            ? BigDecimal.ZERO
            : summary.savingsBalance();

        BigDecimal ratio = loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(principal.getSaccoId(), loanType)
            .map(LoanProductSetting::getMaxLoanSavingsRatio)
            .filter(Objects::nonNull)
            .orElseGet(sacco::getMaxLoanSavingsRatio);
        if (ratio == null) {
            ratio = BigDecimal.ZERO;
        }

        BigDecimal maxAllowed = savings.multiply(ratio).setScale(2, RoundingMode.DOWN);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("savingsLabel", formatTzs(savings));
        response.put("ratioPercentLabel", ratio.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
        response.put("maxAllowedLabel", formatTzs(maxAllowed));
        response.put("exampleAmountLabel", formatTzs(exampleAmount(maxAllowed)));
        response.put("memberNumber", principal.getUsername());
        response.put("stationId", member.getStationId());
        return response;
    }

    @GetMapping("/guarantee-requests")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS')")
    public String myGuarantorRequests(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        List<GuarantorRequest> requests = activeGuarantorRequests(
            loanWorkflowService.myGuarantorRequests(principal.getMemberId()));
        model.addAttribute("requests", requests);
        addGuaranteeActionContext(requests, model);
        model.addAttribute("guarantorSavedSignatureText", resolveSavedSignatureText(principal.getMemberId()));
        return "app/guarantee-requests";
    }

    @PostMapping("/guarantee-requests/{requestId}/approve")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS') and @authz.isGuarantorAssignee(#requestId, principal)")
    public String approveRequest(@PathVariable UUID requestId,
                                 @AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false, defaultValue = "false") boolean guarantorDeclarationAccepted,
                                 @RequestParam(required = false) String guarantorSignatureOtpCode,
                                 RedirectAttributes ra) {
        if (!guarantorDeclarationAccepted) {
            ra.addFlashAttribute("error", "Confirm the guarantor declaration before approving the request.");
            return "redirect:/app/guarantee-requests";
        }
        try {
            Member guarantor = requireMemberWithSavedSignature(
                principal.getMemberId(),
                "Add an email address to your member profile before requesting a guarantor OTP.",
                "Register your signature first before approving guarantor requests."
            );
            UUID otpTokenId = emailOtpService.validateOtp(
                guarantor.getEmail(), EmailOtpPurpose.GUARANTOR_SIGNATURE, guarantorSignatureOtpCode);
            loanWorkflowService.approveGuarantorRequest(
                requestId,
                principal.getMemberId(),
                guarantor.getSignatureText(),
                OffsetDateTime.now()
            );
            emailOtpService.consumeOtpById(otpTokenId);
            ra.addFlashAttribute("message", "Guarantee request approved");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/guarantee-requests";
    }

    @PostMapping("/guarantee-requests/request-signature-otp")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestGuarantorSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal) {
        try {
            Member member = requireMemberWithSavedSignature(
                principal.getMemberId(),
                "Add an email address to your member profile before requesting a guarantor OTP.",
                "Register your signature first before approving guarantor requests."
            );
            emailOtpService.issueOtp(
                member.getEmail(),
                EmailOtpPurpose.GUARANTOR_SIGNATURE,
                member.getId(),
                "Your SACCO MVP guarantor confirmation code",
                "Use this OTP code to confirm your guarantor signature and approve the request."
            );
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "We sent a guarantor confirmation code to " + member.getEmail() + "."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/request-signature-otp")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestApplicantSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                            @RequestParam UUID applicationId) {
        try {
            LoanApplication application = loanWorkflowService.getMine(applicationId, principal.getMemberId());
            assertApplicantSignatureOtpAllowed(application);
            Member member = requireMemberWithSavedSignature(principal.getMemberId());
            emailOtpService.issueOtp(
                member.getEmail(),
                EmailOtpPurpose.APPLICANT_SIGNATURE,
                member.getId(),
                "Your SACCO MVP submission code",
                "Use this OTP code to confirm your signature and submit your loan application."
            );
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "We sent a submission code to " + member.getEmail() + "."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/loan-applications/verify-signature-otp")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyApplicantSignatureOtp(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                           @RequestParam UUID applicationId,
                                                                           @RequestParam String otpCode) {
        try {
            LoanApplication application = loanWorkflowService.getMine(applicationId, principal.getMemberId());
            assertApplicantSignatureOtpAllowed(application);
            validateApplicantSignatureOtp(principal.getMemberId(), otpCode);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Verified"
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/guarantee-requests/{requestId}/reject")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS') and @authz.isGuarantorAssignee(#requestId, principal)")
    public String rejectRequest(@PathVariable UUID requestId,
                                @AuthenticationPrincipal AppUserPrincipal principal,
                                RedirectAttributes ra) {
        loanWorkflowService.rejectGuarantorRequest(requestId, principal.getMemberId(), "Declined by guarantor");
        ra.addFlashAttribute("message", "Guarantee request rejected");
        return "redirect:/app/guarantee-requests";
    }

    @PostMapping("/guarantee-requests/{requestId}/undo")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPROVE_GUARANTOR_REQUESTS') and @authz.isGuarantorAssignee(#requestId, principal)")
    public String undoGuarantorDecision(@PathVariable UUID requestId,
                                        @AuthenticationPrincipal AppUserPrincipal principal,
                                        RedirectAttributes ra) {
        try {
            reversalRequestService.requestGuarantorUndo(requestId, principal.getMemberId());
            ra.addFlashAttribute("message", "Removal request sent to the applicant for approval.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/guarantee-requests";
    }

    @PostMapping("/loan-applications/{loanId}/guarantor-reversal-requests/{requestId}/approve")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#loanId, principal)")
    public String approveGuarantorUndoRequest(@PathVariable UUID loanId,
                                              @PathVariable UUID requestId,
                                              @AuthenticationPrincipal AppUserPrincipal principal,
                                              RedirectAttributes ra) {
        try {
            reversalRequestService.decideGuarantorUndo(requestId, principal.getMemberId(), true);
            ra.addFlashAttribute("message", "Guarantor removed from this application.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + loanId;
    }

    @PostMapping("/loan-applications/{loanId}/guarantor-reversal-requests/{requestId}/reject")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS') and @authz.isLoanOwner(#loanId, principal)")
    public String rejectGuarantorUndoRequest(@PathVariable UUID loanId,
                                             @PathVariable UUID requestId,
                                             @AuthenticationPrincipal AppUserPrincipal principal,
                                             RedirectAttributes ra) {
        try {
            reversalRequestService.decideGuarantorUndo(requestId, principal.getMemberId(), false);
            ra.addFlashAttribute("message", "Guarantor removal request declined.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/app/loan-applications/" + loanId;
    }

    @GetMapping("/notifications")
    public String notifications(@AuthenticationPrincipal AppUserPrincipal principal,
                                @RequestParam(required = false) UUID highlight,
                                Model model) {
        model.addAttribute("notifications", notificationInboxService.allViews(principal.getMemberId(), principal.getGrantedPositions()));
        model.addAttribute("highlightNotificationId", highlight);
        return "app/notifications";
    }

    @GetMapping("/notifications/{id}/open")
    public String openNotification(@PathVariable UUID id,
                                   @AuthenticationPrincipal AppUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            return "redirect:" + notificationInboxService.openForMember(
                id, principal.getMemberId(), principal.getGrantedPositions(), principal.getPosition(), "/app/notifications");
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/app/notifications";
        }
    }

    @GetMapping("/support")
    public String support() {
        return "app/support";
    }

    @PostMapping("/notifications/mark-all-read")
    public String markAllNotificationsRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                           RedirectAttributes ra) {
        int updated = notificationInboxService.markAllAsRead(principal.getMemberId());
        if (updated > 0) {
            ra.addFlashAttribute("message", "All notifications have been marked as read.");
        } else {
            ra.addFlashAttribute("message", "There were no unread notifications.");
        }
        return "redirect:/app/notifications";
    }

    @PostMapping("/support")
    public String sendSupport(@AuthenticationPrincipal AppUserPrincipal principal,
                              @RequestParam String subject,
                              @RequestParam String message,
                              RedirectAttributes ra) {
        adminService.submitSupport(principal.getSaccoId(), principal.getMemberId(), subject, message);
        ra.addFlashAttribute("message", "Your message has been sent to the system administrator.");
        return "redirect:/app/support";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handleError(RuntimeException ex, RedirectAttributes ra) {
        ra.addFlashAttribute("error", ex.getMessage());
        return "redirect:/app/dashboard";
    }

    private String prepareLoanNewModel(AppUserPrincipal principal,
                                       LoanType loanType,
                                       Map<String, String> formValues,
                                       List<UUID> guarantorIds,
                                       Integer requiredGuarantorsOverride,
                                       Model model) {
        LoanProductSetting schema = formSchemaService.getSchema(principal.getSaccoId(), loanType);
        FormModel form = formSchemaService.toFormModel(loanType, schema.getFormSchema());
        int requiredGuarantors = requiredGuarantorsOverride == null
            ? schema.getGuarantorsRequired()
            : Math.max(requiredGuarantorsOverride, 0);
        EligibilityService.EligibilityResult eligibility = eligibilityService.check(
            principal.getSaccoId(), principal.getMemberId(), loanType, BigDecimal.ZERO);

        model.addAttribute("formModel", form);
        model.addAttribute("loanType", loanType);
        model.addAttribute("requiredGuarantors", requiredGuarantors);
        model.addAttribute("formValues", formValues == null ? Collections.emptyMap() : formValues);
        model.addAttribute("selectedGuarantorLookup", toLookupMap(guarantorIds));
        model.addAttribute("selectedGuarantorItems", selectedGuarantorItems(guarantorIds));
        model.addAttribute("savingsLabel", formatTzs(eligibility.savings()));
        model.addAttribute("maxAllowedLabel", formatTzs(eligibility.maxAllowed()));
        model.addAttribute("ratioPercentLabel",
            eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
        model.addAttribute("exampleAmountLabel", formatTzs(exampleAmount(eligibility.maxAllowed())));
        model.addAttribute("exampleAmountRaw", exampleAmount(eligibility.maxAllowed()).toPlainString());
        model.addAttribute("financialSnapshotDisplay",
            loanPresentationService.parseFinancialFields(formValues == null ? null : formValues.get("financialSnapshotJson")));
        model.addAttribute("savedSignatureText", resolveSavedSignatureText(principal.getMemberId()));
        model.addAttribute("topUpLoanId", formValues == null ? null : formValues.get("topUpLoanId"));
        model.addAttribute("topUpSourceLoan", resolveTopUpSourceLoan(principal.getMemberId(), formValues == null ? null : formValues.get("topUpLoanId")));
        return "app/loan-new";
    }

    private Integer resolveDraftRequiredGuarantors(UUID memberId, UUID applicationId) {
        if (applicationId == null) {
            return null;
        }
        return loanApplicationRepository.findById(applicationId)
            .filter(app -> app.getApplicantMemberId().equals(memberId))
            .map(LoanApplication::getRequiredGuarantors)
            .orElse(null);
    }

    private Map<UUID, Boolean> toLookupMap(List<UUID> values) {
        Map<UUID, Boolean> lookup = new HashMap<>();
        if (values == null) {
            return lookup;
        }
        for (UUID value : values) {
            lookup.put(value, true);
        }
        return lookup;
    }

    private BigDecimal exampleAmount(BigDecimal maxAllowed) {
        if (maxAllowed == null || maxAllowed.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.DOWN);
        }
        BigDecimal half = maxAllowed.multiply(new BigDecimal("0.5")).setScale(2, RoundingMode.DOWN);
        return half.compareTo(BigDecimal.ZERO) > 0 ? half : maxAllowed.setScale(2, RoundingMode.DOWN);
    }

    private String formatTzs(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.DOWN);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return "TSh " + format.format(safeAmount);
    }

    private String humanizeLoanFormError(String message, AppUserPrincipal principal, LoanType loanType) {
        if (message != null && message.contains("Amount exceeds eligibility cap")) {
            EligibilityService.EligibilityResult eligibility = eligibilityService.check(
                principal.getSaccoId(), principal.getMemberId(), loanType, BigDecimal.ZERO);
            return "Amount exceeds your eligibility. Maximum allowed now is " + formatTzs(eligibility.maxAllowed())
                + " (" + eligibility.ratio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()
                + "% of savings " + formatTzs(eligibility.savings()) + ").";
        }
        if (message != null && message.contains("Load SACCO financial details")) {
            return "Load the loan details first so the application can include the official deductions section.";
        }
        return message == null ? "Unable to process loan form." : message;
    }

    private Member requireMemberWithSavedSignature(UUID memberId) {
        return requireMemberWithSavedSignature(
            memberId,
            "Add an email address to your member profile before requesting a submission OTP.",
            "Register your signature first before submitting a loan application."
        );
    }

    private Member requireMemberWithSavedSignature(UUID memberId,
                                                   String missingEmailMessage,
                                                   String missingSignatureMessage) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member account not found."));
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            throw new IllegalStateException(missingEmailMessage);
        }
        if (member.getSignatureText() == null || member.getSignatureText().isBlank()) {
            throw new IllegalStateException(missingSignatureMessage);
        }
        return member;
    }

    private UUID validateApplicantSignatureOtpIfRequired(LoanApplication app, UUID memberId, String otpCode) {
        if (app == null) {
            return null;
        }
        boolean requiresOtp = app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED
            || (app.getStatus() == LoanStatus.DRAFT && (app.getRequiredGuarantors() == null || app.getRequiredGuarantors() <= 0));
        if (!requiresOtp) {
            return null;
        }
        return validateApplicantSignatureOtp(memberId, otpCode);
    }

    private UUID validateApplicantSignatureOtp(UUID memberId, String otpCode) {
        Member signingMember = requireMemberWithSavedSignature(memberId);
        return emailOtpService.validateOtp(
            signingMember.getEmail(),
            EmailOtpPurpose.APPLICANT_SIGNATURE,
            otpCode
        );
    }

    private boolean requiresApplicantOtpBeforeImmediateSubmission(String saccoId, LoanType loanType) {
        return loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, loanType)
            .map(LoanProductSetting::getGuarantorsRequired)
            .map(required -> required == null || required <= 0)
            .orElse(false);
    }

    private void assertApplicantSignatureOtpAllowed(LoanApplication application) {
        if (application.getRequiredGuarantors() > 0 && application.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
            throw new IllegalStateException("Request the OTP after all guarantors have approved this application.");
        }
        if (application.getRequiredGuarantors() <= 0 && application.getStatus() != LoanStatus.DRAFT) {
            throw new IllegalStateException("This application is no longer waiting for applicant OTP confirmation.");
        }
    }

    private boolean canRequestApplicantOtp(LoanApplication app) {
        return app != null && (
            (app.getRequiredGuarantors() > 0 && app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED)
                || (app.getRequiredGuarantors() <= 0 && app.getStatus() == LoanStatus.DRAFT)
        );
    }

    private String resolveSavedSignatureText(UUID memberId) {
        return memberRepository.findById(memberId)
            .map(Member::getSignatureText)
            .filter(text -> text != null && !text.isBlank())
            .orElse("");
    }

    private List<Map<String, String>> selectedGuarantorItems(List<UUID> guarantorIds) {
        if (guarantorIds == null || guarantorIds.isEmpty()) {
            return Collections.emptyList();
        }
        return memberRepository.findAllById(guarantorIds).stream()
            .sorted(Comparator.comparing(Member::getFullName))
            .map(member -> {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("id", member.getId().toString());
                item.put("memberNo", member.getMemberNo());
                item.put("fullName", member.getFullName());
                return item;
            })
            .collect(Collectors.toList());
    }

    private Map<String, String> parseJsonAsStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                values.put(entry.getKey(), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
            return values;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private List<UUID> parseUuidList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private void addGuaranteeContext(List<GuarantorRequest> requests, Model model) {
        Map<UUID, LoanApplication> applicationById = new HashMap<>();
        Set<UUID> applicationIds = new HashSet<>();
        for (GuarantorRequest request : requests) {
            applicationIds.add(request.getLoanApplicationId());
        }
        if (!applicationIds.isEmpty()) {
            for (LoanApplication application : loanApplicationRepository.findAllById(applicationIds)) {
                applicationById.put(application.getId(), application);
            }
        }

        Set<UUID> applicantIds = new HashSet<>();
        for (LoanApplication application : applicationById.values()) {
            applicantIds.add(application.getApplicantMemberId());
        }

        Map<UUID, String> applicantNames = new HashMap<>();
        if (!applicantIds.isEmpty()) {
            for (Member member : memberRepository.findAllById(applicantIds)) {
                applicantNames.put(member.getId(), member.getFullName());
            }
        }

        Map<UUID, String> guaranteeNames = new HashMap<>();
        Map<UUID, LoanType> guaranteeLoanTypes = new HashMap<>();
        Map<UUID, BigDecimal> guaranteeLoanAmounts = new HashMap<>();
        for (Map.Entry<UUID, LoanApplication> entry : applicationById.entrySet()) {
            LoanApplication application = entry.getValue();
            guaranteeNames.put(entry.getKey(),
                applicantNames.getOrDefault(application.getApplicantMemberId(), "Unknown Member"));
            guaranteeLoanTypes.put(entry.getKey(), application.getLoanType());
            guaranteeLoanAmounts.put(entry.getKey(), application.getAmount());
        }

        model.addAttribute("guaranteeNames", guaranteeNames);
        model.addAttribute("guaranteeLoanTypes", guaranteeLoanTypes);
        model.addAttribute("guaranteeLoanAmounts", guaranteeLoanAmounts);
    }

    private void addGuaranteeActionContext(List<GuarantorRequest> requests, Model model) {
        addGuaranteeContext(requests, model);
        Map<UUID, LoanApplication> applicationById = new HashMap<>();
        Set<UUID> applicationIds = requests.stream()
            .map(GuarantorRequest::getLoanApplicationId)
            .collect(Collectors.toSet());
        if (!applicationIds.isEmpty()) {
            for (LoanApplication application : loanApplicationRepository.findAllById(applicationIds)) {
                applicationById.put(application.getId(), application);
            }
        }
        Map<UUID, ReversalRequest> pendingRemovalRequests = new HashMap<>();
        Map<UUID, Boolean> removalAllowed = new HashMap<>();
        for (GuarantorRequest request : requests) {
            ReversalRequest pendingUndo = reversalRequestService.pendingGuarantorUndo(request.getId());
            if (pendingUndo != null) {
                pendingRemovalRequests.put(request.getId(), pendingUndo);
            }
            LoanApplication application = applicationById.get(request.getLoanApplicationId());
            boolean stageOpen = application != null
                && (application.getStatus() == LoanStatus.AWAITING_GUARANTORS
                    || application.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED);
            removalAllowed.put(request.getId(), stageOpen && isWithinReversalWindow(request.getDecidedAt()));
        }
        model.addAttribute("guaranteePendingRemovalRequests", pendingRemovalRequests);
        model.addAttribute("guaranteeRemovalAllowed", removalAllowed);
    }

    private LoanApplication resolveTopUpSourceLoan(UUID memberId, String topUpLoanId) {
        if (topUpLoanId == null || topUpLoanId.isBlank()) {
            return null;
        }
        try {
            UUID loanId = UUID.fromString(topUpLoanId);
            return loanApplicationRepository.findByIdAndApplicantMemberId(loanId, memberId).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<LoanApplication> currentApplications(List<LoanApplication> apps) {
        return apps.stream()
            .filter(app -> !isArchived(app))
            .toList();
    }

    private List<LoanApplication> archivedApplications(List<LoanApplication> apps) {
        return apps.stream()
            .filter(this::isArchived)
            .toList();
    }

    private List<LoanApplication> filterLoanArchives(List<LoanApplication> archives,
                                                     String loanArchiveQuery,
                                                     String loanArchiveFilter) {
        String normalizedQuery = safeArchiveQuery(loanArchiveQuery).toLowerCase(Locale.ROOT);
        String normalizedFilter = safeArchiveFilter(loanArchiveFilter);
        return archives.stream()
            .filter(app -> normalizedQuery.isBlank()
                || app.getId().toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || app.getId().toString().substring(0, 8).toLowerCase(Locale.ROOT).contains(normalizedQuery))
            .filter(app -> switch (normalizedFilter) {
                case "DISBURSED" -> app.getStatus() == LoanStatus.FINAL_APPROVED;
                case "PAID" -> app.getStatus() == LoanStatus.PAID;
                case "REJECTED" -> isRejectedStatus(app.getStatus());
                default -> true;
            })
            .toList();
    }

    private List<GuarantorRequest> filterGuarantorArchives(List<GuarantorRequest> archives,
                                                           String guarantorArchiveQuery,
                                                           String guarantorArchiveFilter) {
        String normalizedQuery = safeArchiveQuery(guarantorArchiveQuery).toLowerCase(Locale.ROOT);
        String normalizedFilter = safeArchiveFilter(guarantorArchiveFilter);
        return archives.stream()
            .filter(request -> normalizedQuery.isBlank()
                || request.getLoanApplicationId().toString().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || request.getLoanApplicationId().toString().substring(0, 8).toLowerCase(Locale.ROOT).contains(normalizedQuery))
            .filter(request -> {
                if ("ALL".equals(normalizedFilter)) {
                    return true;
                }
                try {
                    return request.getStatus() == GuarantorRequestStatus.valueOf(normalizedFilter);
                } catch (IllegalArgumentException ex) {
                    return true;
                }
            })
            .toList();
    }

    private List<LoanApplication> activeRepaymentLoans(List<LoanApplication> apps) {
        return apps.stream()
            .filter(app -> app.getStatus() == LoanStatus.FINAL_APPROVED)
            .toList();
    }

    private Set<UUID> dismissedActiveLoanChartIds(UUID memberId) {
        if (memberId == null) {
            return Collections.emptySet();
        }
        return userSettingsRepository.findById(memberId)
            .map(UserSettings::getNotificationPrefs)
            .map(this::parsePrefs)
            .map(prefs -> parseUuidSet(prefs.get(DISMISSED_ACTIVE_LOAN_CHARTS_KEY)))
            .orElse(Collections.emptySet());
    }

    private void dismissActiveLoanChart(UUID memberId, UUID loanId) {
        OffsetDateTime now = OffsetDateTime.now();
        UserSettings settings = userSettingsRepository.findById(memberId)
            .orElseGet(() -> UserSettings.builder()
                .memberId(memberId)
                .language("en")
                .notificationPrefs("{}")
                .createdAt(now)
                .updatedAt(now)
                .build());
        Map<String, Object> prefs = parsePrefs(settings.getNotificationPrefs());
        LinkedHashSet<String> dismissedCharts = parseUuidSet(prefs.get(DISMISSED_ACTIVE_LOAN_CHARTS_KEY)).stream()
            .map(UUID::toString)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        dismissedCharts.add(loanId.toString());
        prefs.put(DISMISSED_ACTIVE_LOAN_CHARTS_KEY, new ArrayList<>(dismissedCharts));
        settings.setNotificationPrefs(writePrefs(prefs));
        if (settings.getCreatedAt() == null) {
            settings.setCreatedAt(now);
        }
        settings.setUpdatedAt(now);
        userSettingsRepository.save(settings);
    }

    private List<GuarantorRequest> activeGuarantorRequests(List<GuarantorRequest> requests) {
        return requests.stream()
            .filter(request -> request.getStatus() == GuarantorRequestStatus.PENDING
                || (request.getStatus() == GuarantorRequestStatus.APPROVED && isWithinReversalWindow(request.getDecidedAt())))
            .toList();
    }

    private List<GuarantorRequest> archivedGuarantorRequests(List<GuarantorRequest> requests) {
        return requests.stream()
            .filter(request -> request.getStatus() == GuarantorRequestStatus.REJECTED
                || request.getStatus() == GuarantorRequestStatus.EXPIRED
                || (request.getStatus() == GuarantorRequestStatus.APPROVED && !isWithinReversalWindow(request.getDecidedAt())))
            .toList();
    }

    private boolean isPendingApplication(LoanApplication app) {
        return !isRejectedStatus(app.getStatus())
            && app.getStatus() != LoanStatus.FINAL_APPROVED
            && app.getStatus() != LoanStatus.PAID;
    }

    private boolean isAwaitingDecisionStage(LoanApplication app) {
        return app.getStatus() == LoanStatus.AWAITING_GUARANTORS
            || app.getStatus() == LoanStatus.ALL_GUARANTORS_APPROVED
            || app.getStatus() == LoanStatus.READY_FOR_MANAGER
            || app.getStatus() == LoanStatus.AWAITING_BOARD;
    }

    private boolean isArchived(LoanApplication app) {
        return isRejectedStatus(app.getStatus())
            || app.getStatus() == LoanStatus.FINAL_APPROVED
            || app.getStatus() == LoanStatus.PAID;
    }

    private boolean isWithinReversalWindow(OffsetDateTime referenceAt) {
        return referenceAt != null && referenceAt.plusHours(REVERSAL_WINDOW_HOURS).isAfter(OffsetDateTime.now());
    }

    private boolean hasRepaymentTimeframeEnded(LoanApplication app) {
        return app != null && app.getFinalDueDate() != null && !app.getFinalDueDate().isAfter(LocalDate.now());
    }

    private String normalizeArchiveSection(String section) {
        return "guarantors".equalsIgnoreCase(section) ? "guarantors" : "loans";
    }

    private String safeArchiveQuery(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeArchiveFilter(String value) {
        return value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Set<UUID> parseUuidSet(Object rawValue) {
        if (!(rawValue instanceof List<?> values)) {
            return Collections.emptySet();
        }
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            try {
                result.add(UUID.fromString(String.valueOf(value)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    private Map<String, Object> parsePrefs(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String writePrefs(Map<String, Object> prefs) {
        try {
            return objectMapper.writeValueAsString(prefs == null ? Collections.emptyMap() : prefs);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private boolean isRejectedStatus(LoanStatus status) {
        return status == LoanStatus.MANAGER_REJECTED
            || status == LoanStatus.BOARD_REJECTED
            || status == LoanStatus.FINAL_REJECTED;
    }

    private boolean isStatusChartIncluded(LoanStatus status) {
        return status == LoanStatus.DRAFT
            || status == LoanStatus.AWAITING_GUARANTORS
            || status == LoanStatus.ALL_GUARANTORS_APPROVED
            || status == LoanStatus.READY_FOR_MANAGER
            || status == LoanStatus.AWAITING_BOARD
            || status == LoanStatus.FINAL_APPROVED;
    }

    private String dashboardStatusLabel(LoanStatus status) {
        if (status == LoanStatus.DRAFT) {
            return "Draft";
        }
        if (status == LoanStatus.AWAITING_GUARANTORS) {
            return "Awaiting Guarantors";
        }
        if (status == LoanStatus.ALL_GUARANTORS_APPROVED) {
            return "All Guarantors Approved";
        }
        if (status == LoanStatus.READY_FOR_MANAGER) {
            return "On Review By Manager";
        }
        if (status == LoanStatus.AWAITING_BOARD) {
            return "On Review By Board";
        }
        if (status == LoanStatus.FINAL_APPROVED) {
            return "Disbursed Loan";
        }
        if (status == LoanStatus.PAID) {
            return "Paid";
        }
        return status.name().replace('_', ' ');
    }

    private String dashboardStatusColor(LoanStatus status) {
        if (status == LoanStatus.DRAFT) {
            return "#60A5FA";
        }
        if (status == LoanStatus.AWAITING_GUARANTORS) {
            return "#F59E0B";
        }
        if (status == LoanStatus.ALL_GUARANTORS_APPROVED) {
            return "#0F766E";
        }
        if (status == LoanStatus.READY_FOR_MANAGER) {
            return "#14B8A6";
        }
        if (status == LoanStatus.AWAITING_BOARD) {
            return "#6366F1";
        }
        if (status == LoanStatus.FINAL_APPROVED) {
            return "#22C55E";
        }
        if (status == LoanStatus.PAID) {
            return "#0F766E";
        }
        return "#94A3B8";
    }
}
