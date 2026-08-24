package com.sacco.mvp.web;

import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanAttachmentService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanReportService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.MemberProfileImageService;
import com.sacco.mvp.service.SaccoLogoStorageService;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
public class LoanDocumentController {
    private static final Pattern UNSAFE_HEADER_FILENAME_CHARACTERS = Pattern.compile("[\\r\\n\\u0000-\\u001F\\u007F]");

    private final MemberDirectoryService memberDirectoryService;
    private final SaccoRegistryService saccoRegistryService;
    private final LoanPresentationService loanPresentationService;
    private final LoanAttachmentService loanAttachmentService;
    private final LoanReportService loanReportService;
    private final MemberProfileImageService memberProfileImageService;
    private final SaccoLogoStorageService saccoLogoStorageService;
    private final AuditService auditService;
    private final AccessControlService access;

    @GetMapping("/documents/loan-applications/{loanId}/print")
    @PreAuthorize("@authz.canViewLoan(#loanId, principal) and @access.has(principal, 'LOAN_DOCUMENTS_EXPORT')")
    public ResponseEntity<byte[]> downloadPrintable(@PathVariable UUID loanId,
                                                    @AuthenticationPrincipal AppUserPrincipal principal,
                                                    @RequestParam(name = "signatureMode", defaultValue = "signed") String signatureMode) {
        boolean includeRecordedSignatures = !"unsigned".equalsIgnoreCase(signatureMode);
        LoanApplication app = loanPresentationService.findLoan(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        if (app.getFinancialSnapshot() == null || app.getFinancialSnapshot().isBlank()) {
            throw new IllegalStateException("Load SACCO financial details before printing");
        }
        long approvedGuarantors = loanPresentationService.guarantorRequests(loanId).stream()
            .filter(request -> request.getStatus() == GuarantorRequestStatus.APPROVED)
            .count();
        if (app.getRequiredGuarantors() != null && app.getRequiredGuarantors() > 0 && approvedGuarantors < app.getRequiredGuarantors()) {
            throw new IllegalStateException("Printing is available after all guarantors approve");
        }
        if (app.getStatus() == LoanStatus.DRAFT || app.getStatus() == LoanStatus.AWAITING_GUARANTORS) {
            throw new IllegalStateException("Printing is available once the application is on review by manager");
        }

        Member applicant = memberDirectoryService.find(app.getApplicantMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Applicant not found"));
        boolean hasApplicantSignature = (app.getApplicantSignatureText() != null && !app.getApplicantSignatureText().isBlank())
            || (applicant.getSignatureText() != null && !applicant.getSignatureText().isBlank());
        if (app.getApplicantSignatureVerifiedAt() == null || !hasApplicantSignature) {
            throw new IllegalStateException("Printing is available after the applicant signature is verified");
        }
        List<GuarantorRequest> guarantorRequests = loanPresentationService.guarantorRequests(loanId);
        List<BoardReview> boardReviews = loanPresentationService.boardReviewsForLoan(loanId);
        List<ManagerReview> staffReviews = loanPresentationService.staffReviewsForLoan(loanId);
        Map<UUID, String> guarantorNames = new LinkedHashMap<>();
        Map<UUID, String> guarantorMemberNumbers = new LinkedHashMap<>();
        for (Member member : memberDirectoryService.findAll(guarantorRequests.stream()
            .map(GuarantorRequest::getGuarantorMemberId)
            .filter(java.util.Objects::nonNull)
            .toList())) {
            guarantorNames.put(member.getId(), member.getFullName());
            guarantorMemberNumbers.put(member.getId(), member.getMemberNo());
        }
        Map<UUID, Member> staffReviewers = memberDirectoryService.membersById(
            staffReviews.stream().map(ManagerReview::getManagerMemberId).toList());
        Map<UUID, Member> boardMembers = memberDirectoryService.membersById(
            boardReviews.stream().map(BoardReview::getBoardMemberId).toList());

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            resolvePrintableSaccoName(app.getSaccoId()),
            applicant,
            applicantProfileImage(applicant.getId()),
            saccoLogoImage(app.getSaccoId()),
            loanPresentationService.parseFormFields(app.getFormData()),
            loanPresentationService.parseFinancialFields(app),
            guarantorRequests,
            guarantorNames,
            guarantorMemberNumbers,
            staffReviews,
            staffReviewers,
            boardReviews,
            boardMembers,
            loanPresentationService.latestManagerReason(loanId),
            includeRecordedSignatures
        );

        String modeLabel = includeRecordedSignatures ? "signed" : "physical-signature";
        auditLoanDocument(app, principal, "LOAN_APPLICATION_DOCUMENT_EXPORTED", "Loan application document exported");
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=loan-application-" + loanId.toString().substring(0, 8) + "-" + modeLabel + ".pdf")
            .body(pdf);
    }

    private byte[] applicantProfileImage(UUID applicantId) {
        try {
            return memberProfileImageService.load(applicantId).content();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private byte[] saccoLogoImage(String saccoId) {
        try {
            return saccoLogoStorageService.load(saccoId).content();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return null;
        }
    }

    private String resolvePrintableSaccoName(String saccoId) {
        return saccoRegistryService.documentSaccoName(saccoId);
    }

    @GetMapping("/documents/loan-applications/{loanId}/attachments/{attachmentId}")
    @PreAuthorize("@authz.canViewLoan(#loanId, principal)")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable UUID loanId,
                                                     @PathVariable String attachmentId,
                                                     @AuthenticationPrincipal AppUserPrincipal principal,
                                                     @RequestParam(name = "inline", defaultValue = "false") boolean inline) throws IOException {
        LoanApplication app = loanPresentationService.findLoan(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        LoanAttachmentService.AttachmentResource resource = loanAttachmentService.load(loanId, attachmentId, app.getAttachmentsJson());
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(resource.getContentType());
        } catch (Exception ignored) {
        }

        auditLoanDocument(app, principal, "ATTACHMENT_DOWNLOADED", "Attachment downloaded");
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, attachmentContentDisposition(resource.getOriginalName(), inline))
            .body(resource.getContent());
    }

    static String attachmentContentDisposition(String fileName, boolean inline) {
        String safeFileName = fileName == null || fileName.isBlank() ? "attachment" : fileName;
        safeFileName = UNSAFE_HEADER_FILENAME_CHARACTERS.matcher(safeFileName).replaceAll("_");
        ContentDisposition.Builder builder = inline ? ContentDisposition.inline() : ContentDisposition.attachment();
        return builder.filename(safeFileName, StandardCharsets.UTF_8).build().toString();
    }

    @GetMapping("/documents/loan-applications/{loanId}/attachments/{attachmentId}/view")
    @PreAuthorize("@authz.canViewLoan(#loanId, principal)")
    public String viewAttachment(@PathVariable UUID loanId,
                                 @PathVariable String attachmentId,
                                 Model model) {
        LoanApplication app = loanPresentationService.findLoan(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        LoanAttachmentService.AttachmentResource resource = loanAttachmentService.load(loanId, attachmentId, app.getAttachmentsJson());
        String contentType = resource.getContentType() == null || resource.getContentType().isBlank()
            ? "application/octet-stream"
            : resource.getContentType();

        model.addAttribute("previewFileName", resource.getOriginalName());
        model.addAttribute("previewInlineUrl",
            "/documents/loan-applications/" + loanId + "/attachments/" + attachmentId + "?inline=true");
        model.addAttribute("previewDownloadUrl",
            "/documents/loan-applications/" + loanId + "/attachments/" + attachmentId);
        model.addAttribute("previewIsImage", contentType.startsWith("image/"));
        model.addAttribute("previewIsPdf", MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType));
        return "documents/attachment-view";
    }

    @GetMapping("/documents/reports/member-loans.pdf")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'LOAN_REPORTS_EXPORT')")
    public ResponseEntity<byte[]> downloadMemberLoanReport(@AuthenticationPrincipal AppUserPrincipal principal,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                           @RequestParam(required = false) UUID loanProductId,
                                                           @RequestParam(required = false) LoanType loanType) {
        LoanReportService.AnalyticsExportReport report = loanReportService.memberAnalyticsExportReport(principal, fromDate, toDate, loanType, loanProductId);
        byte[] pdf = loanReportService.buildMemberAnalyticsPdf(report);
        auditReportExport(principal, "member-loan-report", "PDF", report.fromDate(), report.toDate());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=member-loan-report-" + report.fromDate() + "-to-" + report.toDate() + ".pdf")
            .body(pdf);
    }

    @GetMapping("/documents/reports/member-loans.xlsx")
    @PreAuthorize("@access.canAccessMemberArea(principal) and @access.has(principal, 'LOAN_REPORTS_EXPORT')")
    public ResponseEntity<byte[]> downloadMemberLoanReportExcel(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                                @RequestParam(required = false) UUID loanProductId,
                                                                @RequestParam(required = false) LoanType loanType) {
        LoanReportService.AnalyticsExportReport report = loanReportService.memberAnalyticsExportReport(principal, fromDate, toDate, loanType, loanProductId);
        byte[] workbook = loanReportService.buildMemberAnalyticsExcel(report);
        auditReportExport(principal, "member-loan-report", "XLSX", report.fromDate(), report.toDate());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=member-loan-report-" + report.fromDate() + "-to-" + report.toDate() + ".xlsx")
            .body(workbook);
    }

    @GetMapping("/documents/reports/staff-loan-analytics.pdf")
    @PreAuthorize("@authz.staffAnalyticsAccess(principal) and @access.has(principal, 'STAFF_ANALYTICS_EXPORT')")
    public ResponseEntity<byte[]> downloadStaffLoanAnalyticsPdf(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                                @RequestParam(required = false) UUID loanProductId,
                                                                @RequestParam(required = false) LoanType loanType,
                                                                @RequestParam(required = false, defaultValue = "staff") String viewAs) {
        boolean stationView = "staff".equalsIgnoreCase(viewAs);
        LocalDate filenameFrom;
        LocalDate filenameTo;
        byte[] pdf;
        String reportSlug;
        if (stationView) {
            LoanReportService.StationAnalyticsExportReport report = loanReportService.stationAnalyticsReport(
                principal.getSaccoId(),
                principal.getStationId(),
                fromDate,
                toDate,
                loanType,
                loanProductId,
                principal.getFullName(),
                roleLabel(principal.getPosition())
            );
            pdf = loanReportService.buildStationAnalyticsPdf(report);
            filenameFrom = report.fromDate();
            filenameTo = report.toDate();
            reportSlug = "station-loan-analytics";
        } else {
            LoanReportService.AnalyticsExportReport report = loanReportService.staffAnalyticsExportReport(
                principal, fromDate, toDate, loanType, loanProductId, "member");
            pdf = loanReportService.buildStationAnalyticsPdf(report);
            filenameFrom = report.fromDate();
            filenameTo = report.toDate();
            reportSlug = "staff-loan-review";
        }
        auditReportExport(principal, reportSlug, "PDF", filenameFrom, filenameTo);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + reportSlug + "-" + filenameFrom + "-to-" + filenameTo + ".pdf")
            .body(pdf);
    }

    @GetMapping("/documents/reports/staff-loan-analytics.xlsx")
    @PreAuthorize("@authz.staffAnalyticsAccess(principal) and @access.has(principal, 'STAFF_ANALYTICS_EXPORT')")
    public ResponseEntity<byte[]> downloadStaffLoanAnalyticsExcel(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                                  @RequestParam(required = false) UUID loanProductId,
                                                                  @RequestParam(required = false) LoanType loanType,
                                                                  @RequestParam(required = false, defaultValue = "staff") String viewAs) {
        boolean stationView = "staff".equalsIgnoreCase(viewAs);
        LocalDate filenameFrom;
        LocalDate filenameTo;
        byte[] workbook;
        String reportSlug;
        if (stationView) {
            LoanReportService.StationAnalyticsExportReport report = loanReportService.stationAnalyticsReport(
                principal.getSaccoId(),
                principal.getStationId(),
                fromDate,
                toDate,
                loanType,
                loanProductId,
                principal.getFullName(),
                roleLabel(principal.getPosition())
            );
            workbook = loanReportService.buildStationAnalyticsExcel(report);
            filenameFrom = report.fromDate();
            filenameTo = report.toDate();
            reportSlug = "station-loan-analytics";
        } else {
            LoanReportService.AnalyticsExportReport report = loanReportService.staffAnalyticsExportReport(
                principal, fromDate, toDate, loanType, loanProductId, "member");
            workbook = loanReportService.buildStationAnalyticsExcel(report);
            filenameFrom = report.fromDate();
            filenameTo = report.toDate();
            reportSlug = "staff-loan-review";
        }
        auditReportExport(principal, reportSlug, "XLSX", filenameFrom, filenameTo);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + reportSlug + "-" + filenameFrom + "-to-" + filenameTo + ".xlsx")
            .body(workbook);
    }

    @GetMapping("/documents/reports/manager-loans.pdf")
    @PreAuthorize("@access.canAccessManagerArea(principal) and @access.has(principal, 'MANAGER_QUEUE_EXPORT')")
    public ResponseEntity<byte[]> downloadManagerLoanReport(@AuthenticationPrincipal AppUserPrincipal principal,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                            @RequestParam(required = false) String decisionFilter) {
        LoanReportService.ManagerWorkflowReport report = loanReportService.managerWorkflowReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), fromDate, toDate, decisionFilter);
        byte[] pdf = loanReportService.buildManagerWorkflowPdf(report);
        auditReportExport(principal, "manager-reviewed-loans", "PDF", report.fromDate(), report.toDate());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=manager-reviewed-loans-" + report.fromDate() + "-to-" + report.toDate() + ".pdf")
            .body(pdf);
    }

    @GetMapping("/documents/reports/board-loans.pdf")
    @PreAuthorize("(@access.canAccessBoardArea(principal) and @access.has(principal, 'BOARD_QUEUE_EXPORT')) or (@access.canAccessChairpersonArea(principal) and @access.has(principal, 'CHAIRPERSON_QUEUE_EXPORT')) or (@access.canAccessCreditCommitteeArea(principal) and @access.has(principal, 'CREDIT_COMMITTEE_QUEUE_EXPORT'))")
    public ResponseEntity<byte[]> downloadBoardLoanReport(@AuthenticationPrincipal AppUserPrincipal principal,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                          @RequestParam(required = false) String decisionFilter) {
        LoanReportService.BoardWorkflowReport report = loanReportService.boardWorkflowReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(),
            boardReportStage(principal),
            fromDate, toDate, decisionFilter);
        byte[] pdf = loanReportService.buildBoardWorkflowPdf(report);
        auditReportExport(principal, "board-reviewed-loans", "PDF", report.fromDate(), report.toDate());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=board-reviewed-loans-" + report.fromDate() + "-to-" + report.toDate() + ".pdf")
            .body(pdf);
    }

    @GetMapping("/documents/reports/loan-officer-loans.pdf")
    @PreAuthorize("@access.canAccessLoanOfficerArea(principal) and @access.has(principal, 'LOAN_OFFICER_QUEUE_EXPORT')")
    public ResponseEntity<byte[]> downloadLoanOfficerLoanReport(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                                @RequestParam(required = false) String decisionFilter) {
        LoanReportService.BoardWorkflowReport report = loanReportService.boardWorkflowReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(),
            ApprovalWorkflowStage.LOAN_OFFICER, fromDate, toDate, decisionFilter);
        byte[] pdf = loanReportService.buildLoanOfficerWorkflowPdf(report);
        auditReportExport(principal, "loan-officer-reviewed-loans", "PDF", report.fromDate(), report.toDate());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=loan-officer-reviewed-loans-" + report.fromDate() + "-to-" + report.toDate() + ".pdf")
            .body(pdf);
    }

    @GetMapping("/documents/reports/accountant-loans.pdf")
    @PreAuthorize("@access.canAccessAccountantArea(principal) and @access.has(principal, 'ACCOUNTANT_QUEUE_EXPORT')")
    public ResponseEntity<byte[]> downloadAccountantLoanReport(@AuthenticationPrincipal AppUserPrincipal principal,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                               @RequestParam(required = false) String decisionFilter) {
        LoanReportService.AccountantLoanReport report = loanReportService.accountantReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), fromDate, toDate, decisionFilter);
        byte[] pdf = loanReportService.buildAccountantPdf(report);
        auditReportExport(principal, "accountant-reviewed-loans", "PDF", report.fromDate(), report.toDate());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=accountant-reviewed-loans-" + report.fromDate() + "-to-" + report.toDate() + ".pdf")
            .body(pdf);
    }

    @GetMapping("/documents/reports/disbursement-loans.pdf")
    @PreAuthorize("@access.canAccessDisbursementArea(principal) and @access.has(principal, 'DISBURSEMENT_QUEUE_EXPORT')")
    public ResponseEntity<byte[]> downloadDisbursementLoanReport(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        LoanReportService.DisbursementLoanReport report = loanReportService.disbursementReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), fromDate, toDate);
        byte[] pdf = loanReportService.buildDisbursementPdf(report);
        auditReportExport(principal, "disbursement-loans", "PDF", report.fromDate(), report.toDate());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=disbursement-loans-" + report.fromDate() + "-to-" + report.toDate() + ".pdf")
            .body(pdf);
    }

    private void auditReportExport(AppUserPrincipal principal,
                                   String reportName,
                                   String format,
                                   LocalDate fromDate,
                                   LocalDate toDate) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", "SUCCESS");
        details.put("saccoId", principal == null ? null : principal.getSaccoId());
        details.put("stationId", principal == null ? null : principal.getStationId());
        details.put("reportName", reportName);
        details.put("format", format);
        details.put("fromDate", fromDate == null ? null : fromDate.toString());
        details.put("toDate", toDate == null ? null : toDate.toString());
        auditService.logEvent(
            "REPORT",
            null,
            "REPORT_EXPORTED",
            principal == null ? null : principal.getMemberId(),
            AuditEventStatus.SUCCESS,
            "Report exported",
            "REPORT",
            "Report " + reportName,
            principal == null ? null : principal.getSaccoId(),
            principal == null ? null : principal.getStationId(),
            details
        );
    }

    private ApprovalWorkflowStage boardReportStage(AppUserPrincipal principal) {
        if (access.canAccessChairpersonArea(principal)) {
            return ApprovalWorkflowStage.CHAIRPERSON;
        }
        if (access.canAccessBoardArea(principal)) {
            return ApprovalWorkflowStage.BOARD;
        }
        return ApprovalWorkflowStage.CREDIT_COMMITTEE;
    }

    private void auditLoanDocument(LoanApplication app,
                                   AppUserPrincipal principal,
                                   String action,
                                   String description) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", "SUCCESS");
        details.put("saccoId", app.getSaccoId());
        details.put("stationId", app.getStationId());
        details.put("applicationNumber", app.getApplicationNumber());
        details.put("loanId", app.getLoanId());
        auditService.logEvent(
            "LOAN_APPLICATION",
            app.getId(),
            action,
            principal == null ? null : principal.getMemberId(),
            AuditEventStatus.SUCCESS,
            description,
            "LOAN_APPLICATION",
            app.getLoanId() == null || app.getLoanId().isBlank()
                ? "Loan Application #" + app.getApplicationNumber()
                : "Loan ID " + app.getLoanId(),
            app.getSaccoId(),
            app.getStationId(),
            details
        );
    }

    private String roleLabel(com.sacco.mvp.domain.Position position) {
        if (position == null) {
            return "Staff";
        }
        String lower = position.name().toLowerCase(java.util.Locale.ENGLISH).replace('_', ' ');
        StringBuilder label = new StringBuilder();
        for (String part : lower.split(" ")) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return label.toString();
    }

    @InitBinder
    void bindReportDates(WebDataBinder binder) {
        binder.registerCustomEditor(LocalDate.class, new StrictAnalyticsLocalDateEditor());
    }

}
