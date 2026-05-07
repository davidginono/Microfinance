package com.sacco.mvp.web;

import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanAttachmentService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class LoanDocumentController {
    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final LoanPresentationService loanPresentationService;
    private final LoanAttachmentService loanAttachmentService;
    private final LoanReportService loanReportService;

    @GetMapping("/documents/loan-applications/{loanId}/print")
    @PreAuthorize("@authz.canViewLoan(#loanId, principal)")
    public ResponseEntity<byte[]> downloadPrintable(@PathVariable UUID loanId) {
        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        if (app.getFinancialSnapshot() == null || app.getFinancialSnapshot().isBlank()) {
            throw new IllegalStateException("Load SACCO financial details before printing");
        }
        long approvedGuarantors = guarantorRequestRepository.findByLoanApplicationId(loanId).stream()
            .filter(request -> request.getStatus() == GuarantorRequestStatus.APPROVED)
            .count();
        if (app.getRequiredGuarantors() != null && app.getRequiredGuarantors() > 0 && approvedGuarantors < app.getRequiredGuarantors()) {
            throw new IllegalStateException("Printing is available after all guarantors approve");
        }
        if (app.getStatus() == LoanStatus.DRAFT || app.getStatus() == LoanStatus.AWAITING_GUARANTORS) {
            throw new IllegalStateException("Printing is available once the application is on review by manager");
        }

        Member applicant = memberRepository.findById(app.getApplicantMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Applicant not found"));
        List<GuarantorRequest> guarantorRequests = guarantorRequestRepository.findByLoanApplicationId(loanId);
        List<BoardReview> boardReviews = boardReviewRepository.findByLoanApplicationId(loanId);
        Map<UUID, String> guarantorNames = new LinkedHashMap<>();
        for (Member member : memberRepository.findAllById(guarantorRequests.stream().map(GuarantorRequest::getGuarantorMemberId).toList())) {
            guarantorNames.put(member.getId(), member.getFullName());
        }
        Map<UUID, Member> boardMembers = new LinkedHashMap<>();
        for (Member member : memberRepository.findAllById(boardReviews.stream().map(BoardReview::getBoardMemberId).toList())) {
            boardMembers.put(member.getId(), member);
        }

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            resolvePrintableSaccoName(app.getSaccoId()),
            applicant,
            loanPresentationService.parseFormFields(app.getFormData()),
            loanPresentationService.parseFinancialFields(app.getFinancialSnapshot()),
            guarantorRequests,
            guarantorNames,
            boardReviews,
            boardMembers,
            loanPresentationService.latestManagerReason(loanId)
        );

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=loan-application-" + loanId.toString().substring(0, 8) + ".pdf")
            .body(pdf);
    }

    private String resolvePrintableSaccoName(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            return "SACCO";
        }
        return registeredSaccoRepository.findById(saccoId)
            .filter(registeredSacco -> registeredSacco.getSaccoName() != null && !registeredSacco.getSaccoName().isBlank())
            .map(registeredSacco -> registeredSacco.getSaccoName().trim())
            .or(() -> saccoSettingsRepository.findById(saccoId)
                .map(settings -> settings.getExternalSaccoName())
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim))
            .orElse(saccoId);
    }

    @GetMapping("/documents/loan-applications/{loanId}/attachments/{attachmentId}")
    @PreAuthorize("@authz.canViewLoan(#loanId, principal)")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable UUID loanId,
                                                     @PathVariable String attachmentId,
                                                     @RequestParam(name = "inline", defaultValue = "false") boolean inline) throws IOException {
        LoanApplication app = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        LoanAttachmentService.AttachmentResource resource = loanAttachmentService.load(loanId, attachmentId, app.getAttachmentsJson());
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(resource.getContentType());
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment") + "; filename=\"" + resource.getOriginalName() + "\"")
            .body(Files.readAllBytes(resource.getPath()));
    }

    @GetMapping("/documents/reports/member-loans.pdf")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public ResponseEntity<byte[]> downloadMemberLoanReport(@AuthenticationPrincipal AppUserPrincipal principal) {
        LoanReportService.MemberLoanReport report = loanReportService.memberReport(principal.getMemberId());
        byte[] pdf = loanReportService.buildMemberPdf(report);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=member-loan-report.pdf")
            .body(pdf);
    }

    @GetMapping("/documents/reports/member-loans.csv")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public ResponseEntity<byte[]> downloadMemberLoanReportCsv(@AuthenticationPrincipal AppUserPrincipal principal) {
        LoanReportService.MemberLoanReport report = loanReportService.memberReport(principal.getMemberId());
        byte[] csv = loanReportService.buildMemberCsv(report);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=member-loan-report.csv")
            .body(csv);
    }

    @GetMapping("/documents/reports/member-loans.xlsx")
    @PreAuthorize("hasRole('MEMBER') and @userClaims.has(principal, 'APPLY_LOANS')")
    public ResponseEntity<byte[]> downloadMemberLoanReportExcel(@AuthenticationPrincipal AppUserPrincipal principal) {
        LoanReportService.MemberLoanReport report = loanReportService.memberReport(principal.getMemberId());
        byte[] workbook = loanReportService.buildMemberExcel(report);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=member-loan-report.xlsx")
            .body(workbook);
    }

    @GetMapping("/documents/reports/manager-loans.pdf")
    @PreAuthorize("hasRole('MANAGER') and @userClaims.has(principal, 'REVIEW_MANAGER_QUEUE')")
    public ResponseEntity<byte[]> downloadManagerLoanReport(@AuthenticationPrincipal AppUserPrincipal principal,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                            @RequestParam(required = false) String decisionFilter) {
        LoanReportService.ManagerWorkflowReport report = loanReportService.managerWorkflowReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), fromDate, toDate, decisionFilter);
        byte[] pdf = loanReportService.buildManagerWorkflowPdf(report);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=manager-reviewed-loans-" + report.fromDate() + "-to-" + report.toDate() + ".pdf")
            .body(pdf);
    }

    @GetMapping("/documents/reports/accountant-loans.pdf")
    @PreAuthorize("hasRole('ACCOUNTANT') and @userClaims.has(principal, 'REVIEW_ACCOUNTANT_QUEUE')")
    public ResponseEntity<byte[]> downloadAccountantLoanReport(@AuthenticationPrincipal AppUserPrincipal principal,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                                               @RequestParam(required = false) String decisionFilter) {
        LoanReportService.AccountantLoanReport report = loanReportService.accountantReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), fromDate, toDate, decisionFilter);
        byte[] pdf = loanReportService.buildAccountantPdf(report);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=accountant-reviewed-loans-" + report.fromDate() + "-to-" + report.toDate() + ".pdf")
            .body(pdf);
    }

    @GetMapping("/documents/reports/disbursement-loans.pdf")
    @PreAuthorize("hasRole('DISBURSEMENT_OFFICER') and @userClaims.has(principal, 'ACCESS_DISBURSEMENT_QUEUE')")
    public ResponseEntity<byte[]> downloadDisbursementLoanReport(@AuthenticationPrincipal AppUserPrincipal principal,
                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        LoanReportService.DisbursementLoanReport report = loanReportService.disbursementReport(
            principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), fromDate, toDate);
        byte[] pdf = loanReportService.buildDisbursementPdf(report);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=disbursement-loans-" + report.fromDate() + "-to-" + report.toDate() + ".pdf")
            .body(pdf);
    }

}
