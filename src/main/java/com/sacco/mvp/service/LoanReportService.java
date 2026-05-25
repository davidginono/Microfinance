package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanReportService {
    private static final List<LoanStatus> DISBURSED_STATUSES = List.of(
        LoanStatus.FINAL_APPROVED,
        LoanStatus.DEFAULTED,
        LoanStatus.PAID
    );
    private static final List<LoanStatus> REJECTED_STATUSES = List.of(
        LoanStatus.MANAGER_REJECTED,
        LoanStatus.LOAN_OFFICER_REJECTED,
        LoanStatus.BOARD_REJECTED,
        LoanStatus.ACCOUNTANT_REJECTED,
        LoanStatus.FINAL_REJECTED
    );
    private static final List<LoanStatus> ACTIVE_STATUSES = List.of(
        LoanStatus.FINAL_APPROVED,
        LoanStatus.DEFAULTED
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final ObjectMapper objectMapper;

    public MemberLoanReport memberReport(UUID memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        List<LoanApplication> loans = loanApplicationRepository.findByApplicantMemberIdOrderByCreatedAtDesc(memberId).stream()
            .sorted(Comparator.comparing(this::memberReportSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        List<LoanApplication> summaryLoans = loans.stream()
            .filter(loan -> DISBURSED_STATUSES.contains(loan.getStatus()))
            .toList();
        return new MemberLoanReport(member, summarize(summaryLoans), summarizeMemberAnalytics(loans), loans, buildMemberLoanDetails(member, loans));
    }

    public ManagerLoanReport managerReport(String saccoId, Integer year, boolean returnedOnly) {
        int effectiveYear = year == null ? LocalDate.now().getYear() : year;
        LocalDate fromDate = LocalDate.of(effectiveYear, 1, 1);
        LocalDate toDate = LocalDate.of(effectiveYear, 12, 31);
        List<LoanApplication> loans = loanApplicationRepository.findBySaccoIdAndStatusInOrderByCreatedAtAsc(
                saccoId, DISBURSED_STATUSES).stream()
            .filter(loan -> loan.getDisbursementDate() != null)
            .filter(loan -> fromDate == null || !loan.getDisbursementDate().isBefore(fromDate))
            .filter(loan -> toDate == null || !loan.getDisbursementDate().isAfter(toDate))
            .filter(loan -> !returnedOnly || loan.getStatus() == LoanStatus.PAID)
            .sorted(Comparator.comparing(LoanApplication::getDisbursementDate, Comparator.reverseOrder())
                .thenComparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        Set<UUID> applicantIds = loans.stream().map(LoanApplication::getApplicantMemberId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, Member> applicantMap = new LinkedHashMap<>();
        if (!applicantIds.isEmpty()) {
            for (Member member : memberRepository.findAllById(applicantIds)) {
                applicantMap.put(member.getId(), member);
            }
        }
        return new ManagerLoanReport(saccoId, effectiveYear, returnedOnly, summarize(loans), loans, applicantMap);
    }

    public List<Integer> managerReportYears(String saccoId) {
        List<Integer> years = loanApplicationRepository.findBySaccoIdAndStatusInOrderByCreatedAtAsc(
                saccoId, DISBURSED_STATUSES).stream()
            .map(LoanApplication::getDisbursementDate)
            .filter(java.util.Objects::nonNull)
            .map(LocalDate::getYear)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int currentYear = LocalDate.now().getYear();
        if (!years.contains(currentYear)) {
            years.add(0, currentYear);
        }
        return years;
    }

    public AccountantLoanReport accountantReport(UUID accountantId,
                                                 String saccoId,
                                                 String stationId,
                                                 LocalDate fromDate,
                                                 LocalDate toDate,
                                                 String decisionFilter) {
        LocalDate effectiveTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("From date cannot be after to date.");
        }

        OffsetDateTime fromAt = effectiveFrom.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime toAt = effectiveTo.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset()).minusNanos(1);
        ManagerDecision expectedDecision = resolveAccountantDecisionFilter(decisionFilter);
        String effectiveFilter = expectedDecision == null
            ? "ALL"
            : (expectedDecision == ManagerDecision.ACCEPT ? "APPROVED" : "REJECTED");

        List<ManagerReview> reviews = managerReviewRepository
            .findByManagerMemberIdAndReviewStageAndCreatedAtBetweenOrderByCreatedAtDesc(
                accountantId, ApprovalWorkflowStage.ACCOUNTANT, fromAt, toAt).stream()
            .filter(review -> expectedDecision == null || review.getDecision() == expectedDecision)
            .toList();

        Map<UUID, LoanApplication> loanMap = loanApplicationRepository.findAllById(
                reviews.stream().map(ManagerReview::getLoanApplicationId).collect(Collectors.toSet()))
            .stream()
            .filter(loan -> loan.getSaccoId().equals(saccoId))
            .filter(loan -> matchesApplicantStation(loan, stationId))
            .collect(Collectors.toMap(LoanApplication::getId, loan -> loan, (left, right) -> left, LinkedHashMap::new));

        List<AccountantReviewEntry> entries = reviews.stream()
            .map(review -> {
                LoanApplication loan = loanMap.get(review.getLoanApplicationId());
                return loan == null ? null : new AccountantReviewEntry(review, loan);
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing((AccountantReviewEntry entry) -> entry.review().getCreatedAt()).reversed())
            .toList();

        Map<UUID, Member> applicantMap = memberRepository.findAllById(
                entries.stream().map(entry -> entry.loan().getApplicantMemberId()).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member, (left, right) -> left, LinkedHashMap::new));

        long approvedCount = entries.stream().filter(entry -> entry.review().getDecision() == ManagerDecision.ACCEPT).count();
        long rejectedCount = entries.stream().filter(entry -> entry.review().getDecision() == ManagerDecision.REJECT).count();
        BigDecimal totalAmount = entries.stream()
            .map(entry -> entry.loan().getAmount())
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AccountantLoanReport(
            accountantId,
            saccoId,
            effectiveFrom,
            effectiveTo,
            effectiveFilter,
            new AccountantReviewSummary(entries.size(), approvedCount, rejectedCount, totalAmount),
            entries,
            applicantMap
        );
    }

    public ManagerWorkflowReport managerWorkflowReport(UUID managerId,
                                                       String saccoId,
                                                       String stationId,
                                                       LocalDate fromDate,
                                                       LocalDate toDate,
                                                       String decisionFilter) {
        LocalDate effectiveTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("From date cannot be after to date.");
        }

        OffsetDateTime fromAt = effectiveFrom.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime toAt = effectiveTo.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset()).minusNanos(1);
        ManagerDecision expectedDecision = resolveAccountantDecisionFilter(decisionFilter);
        String effectiveFilter = expectedDecision == null
            ? "ALL"
            : (expectedDecision == ManagerDecision.ACCEPT ? "APPROVED" : "REJECTED");

        List<ManagerReview> reviews = managerReviewRepository
            .findByManagerMemberIdAndReviewStageAndCreatedAtBetweenOrderByCreatedAtDesc(
                managerId, ApprovalWorkflowStage.MANAGER, fromAt, toAt).stream()
            .filter(review -> expectedDecision == null || review.getDecision() == expectedDecision)
            .toList();

        Map<UUID, LoanApplication> loanMap = loanApplicationRepository.findAllById(
                reviews.stream().map(ManagerReview::getLoanApplicationId).collect(Collectors.toSet()))
            .stream()
            .filter(loan -> loan.getSaccoId().equals(saccoId))
            .filter(loan -> matchesApplicantStation(loan, stationId))
            .collect(Collectors.toMap(LoanApplication::getId, loan -> loan, (left, right) -> left, LinkedHashMap::new));

        List<ManagerWorkflowEntry> entries = reviews.stream()
            .map(review -> {
                LoanApplication loan = loanMap.get(review.getLoanApplicationId());
                return loan == null ? null : new ManagerWorkflowEntry(review, loan);
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing((ManagerWorkflowEntry entry) -> entry.review().getCreatedAt()).reversed())
            .toList();

        Map<UUID, Member> applicantMap = memberRepository.findAllById(
                entries.stream().map(entry -> entry.loan().getApplicantMemberId()).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member, (left, right) -> left, LinkedHashMap::new));

        long approvedCount = entries.stream().filter(entry -> entry.review().getDecision() == ManagerDecision.ACCEPT).count();
        long rejectedCount = entries.stream().filter(entry -> entry.review().getDecision() == ManagerDecision.REJECT).count();
        BigDecimal totalAmount = entries.stream()
            .map(entry -> entry.loan().getAmount())
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ManagerWorkflowReport(
            managerId,
            saccoId,
            effectiveFrom,
            effectiveTo,
            effectiveFilter,
            new ManagerWorkflowSummary(entries.size(), approvedCount, rejectedCount, totalAmount),
            entries,
            applicantMap
        );
    }

    public DisbursementLoanReport disbursementReport(UUID disbursementOfficerId,
                                                     String saccoId,
                                                     String stationId,
                                                     LocalDate fromDate,
                                                     LocalDate toDate) {
        LocalDate effectiveTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("From date cannot be after to date.");
        }

        OffsetDateTime fromAt = effectiveFrom.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime toAt = effectiveTo.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset()).minusNanos(1);
        List<ManagerReview> reviews = managerReviewRepository
            .findByManagerMemberIdAndReviewStageAndCreatedAtBetweenOrderByCreatedAtDesc(
                disbursementOfficerId, ApprovalWorkflowStage.DISBURSEMENT_OFFICER, fromAt, toAt).stream()
            .filter(review -> review.getDecision() == ManagerDecision.ACCEPT)
            .toList();

        Map<UUID, LoanApplication> loanMap = loanApplicationRepository.findAllById(
                reviews.stream().map(ManagerReview::getLoanApplicationId).collect(Collectors.toSet()))
            .stream()
            .filter(loan -> loan.getSaccoId().equals(saccoId))
            .filter(loan -> matchesApplicantStation(loan, stationId))
            .collect(Collectors.toMap(LoanApplication::getId, loan -> loan, (left, right) -> left, LinkedHashMap::new));

        List<DisbursementReviewEntry> entries = reviews.stream()
            .map(review -> {
                LoanApplication loan = loanMap.get(review.getLoanApplicationId());
                return loan == null ? null : new DisbursementReviewEntry(review, loan);
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing((DisbursementReviewEntry entry) -> entry.review().getCreatedAt()).reversed())
            .toList();

        Map<UUID, Member> applicantMap = memberRepository.findAllById(
                entries.stream().map(entry -> entry.loan().getApplicantMemberId()).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member, (left, right) -> left, LinkedHashMap::new));

        long paidCount = entries.stream().filter(entry -> entry.loan().getStatus() == LoanStatus.PAID).count();
        long defaultedCount = entries.stream().filter(entry -> entry.loan().getStatus() == LoanStatus.DEFAULTED).count();
        BigDecimal totalAmount = entries.stream()
            .map(entry -> entry.loan().getAmount())
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DisbursementLoanReport(
            disbursementOfficerId,
            saccoId,
            effectiveFrom,
            effectiveTo,
            new DisbursementReviewSummary(entries.size(), paidCount, defaultedCount, totalAmount),
            entries,
            applicantMap
        );
    }

    public byte[] buildMemberPdf(MemberLoanReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MemberPdfRenderer renderer = new MemberPdfRenderer(document, report);
            renderer.render();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate PDF report.", ex);
        }
    }

    public byte[] buildMemberCsv(MemberLoanReport report) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv,
            "Loan Application ID",
            "Loan ID",
            "Member Details",
            "Approved Product",
            "Approved Amount",
            "Tenure",
            "Estimated Fee / Insurance Deductions",
            "Application Fee",
            "Insurance Fee",
            "Total Deductions",
            "Guarantor Details",
            "Approval Decision Summary",
            "Disbursement Date",
            "Final Due Date",
            "Status",
            "Prepared By",
            "Prepared Date"
        );
        for (MemberLoanDetail detail : report.details()) {
            appendCsvRow(csv,
                detail.loanApplicationIdLabel(),
                detail.loanIdLabel(),
                detail.memberDetailsLabel(),
                detail.approvedProductLabel(),
                detail.approvedAmountLabel(),
                detail.tenureLabel(),
                detail.deductionSummaryLabel(),
                detail.applicationFeeLabel(),
                detail.insuranceFeeLabel(),
                detail.totalDeductionsLabel(),
                detail.guarantorDetailsLabel(),
                detail.approvalDecisionSummaryLabel(),
                detail.disbursementDateLabel(),
                detail.finalDueDateLabel(),
                detail.statusLabel(),
                detail.preparedByLabel(),
                detail.preparedDateLabel()
            );
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] buildMemberExcel(MemberLoanReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Member Loan Report");
            int rowIndex = 0;
            Row header = sheet.createRow(rowIndex++);
            String[] columns = {
                "Loan Application ID",
                "Loan ID",
                "Member Details",
                "Approved Product",
                "Approved Amount",
                "Tenure",
                "Estimated Fee / Insurance Deductions",
                "Application Fee",
                "Insurance Fee",
                "Total Deductions",
                "Guarantor Details",
                "Approval Decision Summary",
                "Disbursement Date",
                "Final Due Date",
                "Status",
                "Prepared By",
                "Prepared Date"
            };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }
            for (MemberLoanDetail detail : report.details()) {
                Row row = sheet.createRow(rowIndex++);
                int column = 0;
                row.createCell(column++).setCellValue(detail.loanApplicationIdLabel());
                row.createCell(column++).setCellValue(detail.loanIdLabel());
                row.createCell(column++).setCellValue(detail.memberDetailsLabel());
                row.createCell(column++).setCellValue(detail.approvedProductLabel());
                row.createCell(column++).setCellValue(detail.approvedAmountLabel());
                row.createCell(column++).setCellValue(detail.tenureLabel());
                row.createCell(column++).setCellValue(detail.deductionSummaryLabel());
                row.createCell(column++).setCellValue(detail.applicationFeeLabel());
                row.createCell(column++).setCellValue(detail.insuranceFeeLabel());
                row.createCell(column++).setCellValue(detail.totalDeductionsLabel());
                row.createCell(column++).setCellValue(detail.guarantorDetailsLabel());
                row.createCell(column++).setCellValue(detail.approvalDecisionSummaryLabel());
                row.createCell(column++).setCellValue(detail.disbursementDateLabel());
                row.createCell(column++).setCellValue(detail.finalDueDateLabel());
                row.createCell(column++).setCellValue(detail.statusLabel());
                row.createCell(column++).setCellValue(detail.preparedByLabel());
                row.createCell(column).setCellValue(detail.preparedDateLabel());
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate Excel report.", ex);
        }
    }

    public byte[] buildManagerPdf(ManagerLoanReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Manager Disbursed Loans Report");
        lines.add("SACCO: " + report.saccoId());
        lines.add("Generated: " + DATE_FORMATTER.format(LocalDate.now()));
        lines.add("Year: " + report.year());
        lines.add("Returned Only: " + (report.returnedOnly() ? "Yes" : "No"));
        lines.add("");
        lines.add("Summary");
        lines.add("Disbursed loans in report: " + report.summary().disbursedCount());
        lines.add("Returned / paid: " + report.summary().paidCount());
        lines.add("Ongoing loans: " + report.summary().ongoingCount());
        lines.add("Disbursed amount: " + formatMoney(report.summary().disbursedAmount()));
        lines.add("Returned amount: " + formatMoney(report.summary().paidAmount()));
        lines.add("");
        lines.add("Loan List");
        for (LoanApplication loan : report.loans()) {
            Member applicant = report.applicantMap().get(loan.getApplicantMemberId());
            lines.add(shortLoanRow(loan, applicant));
        }
        return renderPdf(lines);
    }

    public byte[] buildAccountantPdf(AccountantLoanReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Accountant Review Report");
        lines.add("SACCO: " + report.saccoId());
        lines.add("Generated: " + DATE_FORMATTER.format(LocalDate.now()));
        lines.add("Period: " + DATE_FORMATTER.format(report.fromDate()) + " to " + DATE_FORMATTER.format(report.toDate()));
        lines.add("Decision Filter: " + accountantDecisionFilterLabel(report.decisionFilter()));
        lines.add("");
        lines.add("Summary");
        lines.add("Reviewed loans: " + report.summary().reviewedCount());
        lines.add("Ready for disbursement: " + report.summary().approvedCount());
        lines.add("Rejected: " + report.summary().rejectedCount());
        lines.add("Reviewed amount: " + formatMoney(report.summary().reviewedAmount()));
        lines.add("");
        lines.add("Loan List");
        for (AccountantReviewEntry entry : report.entries()) {
            Member applicant = report.applicantMap().get(entry.loan().getApplicantMemberId());
            lines.add(accountantLoanRow(entry, applicant));
        }
        if (report.entries().isEmpty()) {
            lines.add("No accountant-reviewed loans matched the selected period.");
        }
        return renderPdf(lines);
    }

    public byte[] buildManagerWorkflowPdf(ManagerWorkflowReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Manager Review Report");
        lines.add("SACCO: " + report.saccoId());
        lines.add("Generated: " + DATE_FORMATTER.format(LocalDate.now()));
        lines.add("Period: " + DATE_FORMATTER.format(report.fromDate()) + " to " + DATE_FORMATTER.format(report.toDate()));
        lines.add("Decision Filter: " + managerDecisionFilterLabel(report.decisionFilter()));
        lines.add("");
        lines.add("Summary");
        lines.add("Reviewed loans: " + report.summary().reviewedCount());
        lines.add("Approved: " + report.summary().approvedCount());
        lines.add("Rejected: " + report.summary().rejectedCount());
        lines.add("Reviewed amount: " + formatMoney(report.summary().reviewedAmount()));
        lines.add("");
        lines.add("Loan List");
        for (ManagerWorkflowEntry entry : report.entries()) {
            Member applicant = report.applicantMap().get(entry.loan().getApplicantMemberId());
            lines.add(managerWorkflowRow(entry, applicant));
        }
        if (report.entries().isEmpty()) {
            lines.add("No manager-reviewed loans matched the selected period.");
        }
        return renderPdf(lines);
    }

    public byte[] buildDisbursementPdf(DisbursementLoanReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Disbursement Officer Report");
        lines.add("SACCO: " + report.saccoId());
        lines.add("Generated: " + DATE_FORMATTER.format(LocalDate.now()));
        lines.add("Period: " + DATE_FORMATTER.format(report.fromDate()) + " to " + DATE_FORMATTER.format(report.toDate()));
        lines.add("");
        lines.add("Summary");
        lines.add("Disbursed loans: " + report.summary().disbursedCount());
        lines.add("Paid loans: " + report.summary().paidCount());
        lines.add("Defaulted loans: " + report.summary().defaultedCount());
        lines.add("Disbursed amount: " + formatMoney(report.summary().disbursedAmount()));
        lines.add("");
        lines.add("Loan List");
        for (DisbursementReviewEntry entry : report.entries()) {
            Member applicant = report.applicantMap().get(entry.loan().getApplicantMemberId());
            lines.add(disbursementLoanRow(entry, applicant));
        }
        if (report.entries().isEmpty()) {
            lines.add("No disbursed loans matched the selected period.");
        }
        return renderPdf(lines);
    }

    private LoanSummary summarize(Collection<LoanApplication> loans) {
        long paidCount = loans.stream().filter(loan -> loan.getStatus() == LoanStatus.PAID).count();
        long ongoingCount = loans.stream()
            .filter(loan -> loan.getStatus() == LoanStatus.FINAL_APPROVED || loan.getStatus() == LoanStatus.DEFAULTED)
            .count();
        BigDecimal disbursedAmount = loans.stream().map(LoanApplication::getAmount).filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = loans.stream()
            .filter(loan -> loan.getStatus() == LoanStatus.PAID)
            .map(LoanApplication::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LoanSummary(loans.size(), paidCount, ongoingCount, disbursedAmount, paidAmount);
    }

    private MemberLoanAnalyticsSummary summarizeMemberAnalytics(Collection<LoanApplication> loans) {
        Collection<LoanApplication> safeLoans = loans == null ? List.of() : loans;
        long appliedCount = safeLoans.stream().filter(loan -> loan.getStatus() != LoanStatus.DRAFT).count();
        long activeCount = safeLoans.stream().filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus())).count();
        long disbursedCount = safeLoans.stream().filter(loan -> DISBURSED_STATUSES.contains(loan.getStatus())).count();
        long paidCount = safeLoans.stream().filter(loan -> loan.getStatus() == LoanStatus.PAID).count();
        long defaultedCount = safeLoans.stream().filter(loan -> loan.getStatus() == LoanStatus.DEFAULTED).count();
        long forfeitedCount = safeLoans.stream().filter(loan -> loan.getStatus() == LoanStatus.FORFEITED).count();
        long rejectedCount = safeLoans.stream().filter(loan -> REJECTED_STATUSES.contains(loan.getStatus())).count();
        BigDecimal activeAmount = safeLoans.stream()
            .filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus()))
            .map(LoanApplication::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MemberLoanAnalyticsSummary(
            appliedCount,
            activeCount,
            disbursedCount,
            paidCount,
            defaultedCount,
            forfeitedCount,
            rejectedCount,
            activeAmount
        );
    }

    private byte[] renderPdf(List<String> lines) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFCursor cursor = new PDFCursor(document);
            cursor.openPage();
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                cursor.writeWrapped(line);
            }
            cursor.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate PDF report.", ex);
        }
    }

    private String shortLoanRow(LoanApplication loan, Member applicant) {
        StringBuilder line = new StringBuilder();
        line.append(shortId(loan.getId())).append(" | ");
        if (applicant != null) {
            line.append(applicant.getFullName()).append(" (").append(applicant.getMemberNo()).append(") | ");
        }
        line.append(loan.getLoanType() == null ? "-" : humanizeLoanType(loan.getLoanType().name())).append(" | ");
        line.append(formatMoney(loan.getAmount())).append(" | ");
        line.append("Disbursed ").append(formatDate(loan.getDisbursementDate())).append(" | ");
        line.append("Due ").append(formatDate(loan.getFinalDueDate())).append(" | ");
        line.append(switch (loan.getStatus()) {
            case PAID -> "PAID on " + formatTimestamp(loan.getPaidAt());
            case DEFAULTED -> "DEFAULTED";
            default -> "ONGOING";
        });
        return line.toString();
    }

    private String accountantLoanRow(AccountantReviewEntry entry, Member applicant) {
        LoanApplication loan = entry.loan();
        ManagerReview review = entry.review();
        StringBuilder line = new StringBuilder();
        line.append(loan.getApplicationNumber() == null ? shortId(loan.getId()) : loan.getApplicationNumber()).append(" | ");
        if (applicant != null) {
            line.append(applicant.getFullName()).append(" (").append(applicant.getMemberNo()).append(") | ");
        }
        line.append(loan.getLoanType() == null ? "-" : humanizeLoanType(loan.getLoanType().name())).append(" | ");
        line.append(formatMoney(loan.getAmount())).append(" | ");
        line.append(review.getDecision() == ManagerDecision.ACCEPT ? "READY FOR DISBURSEMENT" : "REJECTED").append(" | ");
        line.append("Reviewed ").append(formatTimestamp(review.getCreatedAt()));
        return line.toString();
    }

    private String managerWorkflowRow(ManagerWorkflowEntry entry, Member applicant) {
        LoanApplication loan = entry.loan();
        ManagerReview review = entry.review();
        StringBuilder line = new StringBuilder();
        line.append(loan.getApplicationNumber() == null ? shortId(loan.getId()) : loan.getApplicationNumber()).append(" | ");
        if (applicant != null) {
            line.append(applicant.getFullName()).append(" (").append(applicant.getMemberNo()).append(") | ");
        }
        line.append(loan.getLoanType() == null ? "-" : humanizeLoanType(loan.getLoanType().name())).append(" | ");
        line.append(formatMoney(loan.getAmount())).append(" | ");
        line.append(review.getDecision() == ManagerDecision.ACCEPT ? "APPROVED" : "REJECTED").append(" | ");
        line.append("Reviewed ").append(formatTimestamp(review.getCreatedAt())).append(" | ");
        line.append("Current status ").append(humanizeLoanStatusForReport(loan.getStatus()));
        return line.toString();
    }

    private String disbursementLoanRow(DisbursementReviewEntry entry, Member applicant) {
        LoanApplication loan = entry.loan();
        StringBuilder line = new StringBuilder();
        line.append(loan.getLoanId() == null || loan.getLoanId().isBlank() ? shortId(loan.getId()) : loan.getLoanId()).append(" | ");
        line.append(loan.getApplicationNumber() == null ? shortId(loan.getId()) : loan.getApplicationNumber()).append(" | ");
        if (applicant != null) {
            line.append(applicant.getFullName()).append(" (").append(applicant.getMemberNo()).append(") | ");
        }
        line.append(loan.getLoanType() == null ? "-" : humanizeLoanType(loan.getLoanType().name())).append(" | ");
        line.append(formatMoney(loan.getAmount())).append(" | ");
        line.append("Disbursed ").append(formatTimestamp(entry.review().getCreatedAt())).append(" | ");
        line.append("Current status ").append(humanizeLoanStatusForReport(loan.getStatus()));
        return line.toString();
    }

    private String humanizeLoanStatusForReport(LoanStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case READY_FOR_MANAGER -> "On Review By Manager";
            case AWAITING_LOAN_OFFICER -> "On Review By Loan Officer";
            case AWAITING_BOARD -> "On Review By Board";
            case AWAITING_ACCOUNTANT -> "On Review By Accountant";
            case READY_FOR_DISBURSEMENT -> "Ready for Disbursement";
            case MANAGER_REJECTED -> "Manager Rejected";
            case LOAN_OFFICER_REJECTED -> "Loan Officer Rejected";
            case BOARD_REJECTED -> "Board Rejected";
            case ACCOUNTANT_REJECTED -> "Accountant Rejected";
            case FORFEITED -> "Forfeited";
            case FINAL_REJECTED -> "Final Rejected";
            case FINAL_APPROVED -> "Disbursed Loan";
            case DEFAULTED -> "Defaulted";
            case PAID -> "Paid";
            case MANAGER_ACCEPTED -> "Manager Approved";
            case LOAN_OFFICER_APPROVED -> "Loan Officer Approved";
            case BOARD_APPROVED -> "Board Approved";
            case ACCOUNTANT_APPROVED -> "Accountant Approved";
            case AWAITING_GUARANTORS -> "Awaiting Guarantors";
            case ALL_GUARANTORS_APPROVED -> "All Guarantors Approved";
            case SUBMITTED -> "Submitted";
            case DRAFT -> "Draft";
        };
    }

    private ManagerDecision resolveAccountantDecisionFilter(String decisionFilter) {
        if (decisionFilter == null || decisionFilter.isBlank()) {
            return null;
        }
        return switch (decisionFilter.trim().toUpperCase(Locale.ENGLISH)) {
            case "APPROVED", "ACCEPT", "ACCEPTED" -> ManagerDecision.ACCEPT;
            case "REJECTED", "REJECT" -> ManagerDecision.REJECT;
            default -> null;
        };
    }

    private String accountantDecisionFilterLabel(String decisionFilter) {
        if (decisionFilter == null || decisionFilter.isBlank() || "ALL".equalsIgnoreCase(decisionFilter)) {
            return "All decisions";
        }
        return "APPROVED".equalsIgnoreCase(decisionFilter)
            ? "Ready for disbursement"
            : "Rejected";
    }

    private String managerDecisionFilterLabel(String decisionFilter) {
        if (decisionFilter == null || decisionFilter.isBlank() || "ALL".equalsIgnoreCase(decisionFilter)) {
            return "All decisions";
        }
        return "APPROVED".equalsIgnoreCase(decisionFilter)
            ? "Approved"
            : "Rejected";
    }

    private boolean matchesApplicantStation(LoanApplication loan, String stationId) {
        if (loan == null || stationId == null || stationId.isBlank()) {
            return true;
        }
        return loan.getStationId() != null
            && !loan.getStationId().isBlank()
            && stationId.equalsIgnoreCase(loan.getStationId().trim());
    }

    private LocalDate memberReportSortDate(LoanApplication loan) {
        if (loan == null) {
            return null;
        }
        if (loan.getDisbursementDate() != null) {
            return loan.getDisbursementDate();
        }
        return loan.getCreatedAt() == null ? null : loan.getCreatedAt().toLocalDate();
    }

    private List<MemberLoanDetail> buildMemberLoanDetails(Member member, List<LoanApplication> loans) {
        if (loans.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<GuarantorRequest>> guarantorsByLoan = new LinkedHashMap<>();
        Map<UUID, List<BoardReview>> boardReviewsByLoan = new LinkedHashMap<>();
        Map<UUID, ManagerReview> latestManagerReviewByLoan = new LinkedHashMap<>();
        Set<UUID> memberIds = new java.util.LinkedHashSet<>();

        for (LoanApplication loan : loans) {
            List<GuarantorRequest> requests = guarantorRequestRepository.findByLoanApplicationId(loan.getId());
            guarantorsByLoan.put(loan.getId(), requests);
            requests.stream()
                .map(GuarantorRequest::getGuarantorMemberId)
                .forEach(memberIds::add);

            List<BoardReview> boardReviews = boardReviewRepository.findByLoanApplicationId(loan.getId());
            boardReviewsByLoan.put(loan.getId(), boardReviews);
            boardReviews.stream()
                .map(BoardReview::getBoardMemberId)
                .forEach(memberIds::add);

            managerReviewRepository.findFirstByLoanApplicationIdOrderByCreatedAtDesc(loan.getId())
                .ifPresent(review -> {
                    latestManagerReviewByLoan.put(loan.getId(), review);
                    memberIds.add(review.getManagerMemberId());
                });
        }

        Map<UUID, Member> memberMap = memberRepository.findAllById(memberIds).stream()
            .collect(Collectors.toMap(Member::getId, item -> item));

        return loans.stream()
            .map(loan -> buildMemberLoanDetail(
                member,
                loan,
                guarantorsByLoan.getOrDefault(loan.getId(), List.of()),
                boardReviewsByLoan.getOrDefault(loan.getId(), List.of()),
                latestManagerReviewByLoan.get(loan.getId()),
                memberMap
            ))
            .toList();
    }

    private MemberLoanDetail buildMemberLoanDetail(Member member,
                                                   LoanApplication loan,
                                                   List<GuarantorRequest> guarantorRequests,
                                                   List<BoardReview> boardReviews,
                                                   ManagerReview managerReview,
                                                   Map<UUID, Member> memberMap) {
        Map<String, Object> financialSnapshot = parseJsonMap(loan.getFinancialSnapshot());
        String applicationFeeLabel = formatMoney(readBigDecimal(financialSnapshot.get("applicationFee")));
        String insuranceFeeLabel = formatMoney(readBigDecimal(financialSnapshot.get("insuranceFee")));
        String totalDeductionsLabel = formatMoney(resolveTotalDeductions(financialSnapshot));
        String deductionSummary = "Application Fee: " + applicationFeeLabel
            + ", Insurance Fee: " + insuranceFeeLabel
            + ", Total Deductions: " + totalDeductionsLabel;
        return new MemberLoanDetail(
            loan.getId(),
            loan.getApplicationNumber() == null ? shortId(loan.getId()) : loan.getApplicationNumber().toString(),
            loan.getLoanId() == null || loan.getLoanId().isBlank() ? "-" : loan.getLoanId(),
            formatMemberDetails(member),
            loan.getLoanType().getDisplayLabel(),
            formatMoney(loan.getAmount()),
            loan.getTenorMonths() == null ? "-" : loan.getTenorMonths() + " month(s)",
            applicationFeeLabel,
            insuranceFeeLabel,
            totalDeductionsLabel,
            deductionSummary,
            formatGuarantorDetails(guarantorRequests, memberMap),
            formatApprovalSummary(managerReview, boardReviews, memberMap),
            formatDate(loan.getDisbursementDate()),
            formatDate(loan.getFinalDueDate()),
            formatMemberLoanStatus(loan),
            member.getFullName(),
            DATE_FORMATTER.format(LocalDate.now())
        );
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal resolveTotalDeductions(Map<String, Object> financialSnapshot) {
        BigDecimal explicit = readBigDecimal(financialSnapshot.get("totalDeductions"));
        if (explicit.compareTo(BigDecimal.ZERO) > 0) {
            return explicit;
        }
        return readBigDecimal(financialSnapshot.get("applicationFee"))
            .add(readBigDecimal(financialSnapshot.get("insuranceFee")))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMemberDetails(Member member) {
        List<String> parts = new ArrayList<>();
        parts.add(member.getFullName() + " (" + member.getMemberNo() + ")");
        if (member.getEmail() != null && !member.getEmail().isBlank()) {
            parts.add(member.getEmail());
        }
        if (member.getPhone() != null && !member.getPhone().isBlank()) {
            parts.add(member.getPhone());
        }
        return String.join(" | ", parts);
    }

    private String formatGuarantorDetails(List<GuarantorRequest> guarantorRequests, Map<UUID, Member> memberMap) {
        if (guarantorRequests == null || guarantorRequests.isEmpty()) {
            return "No guarantor details available";
        }
        return guarantorRequests.stream()
            .map(request -> {
                Member guarantor = memberMap.get(request.getGuarantorMemberId());
                String guarantorLabel = guarantor == null
                    ? shortId(request.getGuarantorMemberId())
                    : guarantor.getFullName() + " (" + guarantor.getMemberNo() + ")";
                return guarantorLabel + " - " + humanizeGuarantorStatus(request.getStatus());
            })
            .collect(Collectors.joining("; "));
    }

    private String humanizeGuarantorStatus(GuarantorRequestStatus status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case EXPIRED -> "Expired";
            case PENDING -> "Pending";
        };
    }

    private String formatApprovalSummary(ManagerReview managerReview,
                                         List<BoardReview> boardReviews,
                                         Map<UUID, Member> memberMap) {
        List<String> parts = new ArrayList<>();
        if (managerReview != null) {
            Member manager = memberMap.get(managerReview.getManagerMemberId());
            String managerName = manager == null ? "Manager" : manager.getFullName();
            String summary = managerName + ": " + (managerReview.getDecision() == ManagerDecision.ACCEPT ? "Accepted" : "Rejected");
            if (managerReview.getReasons() != null && !managerReview.getReasons().isBlank()) {
                summary += " (" + managerReview.getReasons().trim() + ")";
            }
            parts.add(summary);
        }
        if (boardReviews != null && !boardReviews.isEmpty()) {
            String boardSummary = boardReviews.stream()
                .map(review -> {
                    Member boardMember = memberMap.get(review.getBoardMemberId());
                    String boardName = boardMember == null ? "Board Member" : boardMember.getFullName();
                    String status = review.getDecision() == null ? "Pending" : switch (review.getDecision()) {
                        case APPROVED -> "Approved";
                        case REJECTED -> "Rejected";
                        case PENDING -> "Pending";
                    };
                    String note = review.getComment() == null || review.getComment().isBlank()
                        ? ""
                        : " (" + review.getComment().trim() + ")";
                    return boardName + ": " + status + note;
                })
                .collect(Collectors.joining("; "));
            parts.add("Committee: " + boardSummary);
        }
        if (parts.isEmpty()) {
            return "No approval decision details available";
        }
        return String.join(" | ", parts);
    }

    private String formatMemberLoanStatus(LoanApplication loan) {
        if (loan == null || loan.getStatus() == null) {
            return "Unknown";
        }
        return switch (loan.getStatus()) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
            case AWAITING_GUARANTORS -> "Awaiting Guarantors";
            case ALL_GUARANTORS_APPROVED -> "All Guarantors Approved";
            case READY_FOR_MANAGER -> "On Review By Manager";
            case MANAGER_REJECTED -> "Manager Rejected";
            case MANAGER_ACCEPTED -> "Ready for Disbursement";
            case AWAITING_LOAN_OFFICER -> "On Review By Loan Officer";
            case LOAN_OFFICER_REJECTED -> "Loan Officer Rejected";
            case LOAN_OFFICER_APPROVED -> "Loan Officer Approved";
            case AWAITING_BOARD -> "On Review By Board";
            case BOARD_REJECTED -> "Board Rejected";
            case BOARD_APPROVED -> "Reviewed";
            case AWAITING_ACCOUNTANT -> "On Review By Accountant";
            case ACCOUNTANT_REJECTED -> "Accountant Rejected";
            case ACCOUNTANT_APPROVED -> "Accountant Approved";
            case READY_FOR_DISBURSEMENT -> "Ready for Disbursement";
            case FORFEITED -> "Forfeited";
            case FINAL_REJECTED -> "Final Rejected";
            case FINAL_APPROVED -> "Disbursed Loan";
            case DEFAULTED -> "Defaulted / Not Paid";
            case PAID -> "Paid";
        };
    }

    private void appendCsvRow(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            String value = values[i] == null ? "" : values[i];
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append('\n');
    }

    private String formatMoney(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(safe);
    }

    private String shortId(UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }

    private String humanizeLoanType(String text) {
        return text == null ? "" : text.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : DATE_FORMATTER.format(date);
    }

    private String formatTimestamp(OffsetDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static String sanitizePdfText(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text.replace('\r', ' ').trim();
    }

    public record LoanSummary(
        long disbursedCount,
        long paidCount,
        long ongoingCount,
        BigDecimal disbursedAmount,
        BigDecimal paidAmount
    ) {
        public String getDisbursedAmountLabel() {
            return formatMoneyStatic(disbursedAmount);
        }

        public String getPaidAmountLabel() {
            return formatMoneyStatic(paidAmount);
        }
    }

    public record MemberLoanReport(
        Member member,
        LoanSummary summary,
        MemberLoanAnalyticsSummary analytics,
        List<LoanApplication> loans,
        List<MemberLoanDetail> details
    ) {}

    public record MemberLoanAnalyticsSummary(
        long appliedCount,
        long activeCount,
        long disbursedCount,
        long paidCount,
        long defaultedCount,
        long forfeitedCount,
        long rejectedCount,
        BigDecimal activeAmount
    ) {
        public String getActiveAmountLabel() {
            return formatMoneyStatic(activeAmount);
        }
    }

    public record MemberLoanDetail(
        UUID loanId,
        String loanApplicationIdLabel,
        String loanIdLabel,
        String memberDetailsLabel,
        String approvedProductLabel,
        String approvedAmountLabel,
        String tenureLabel,
        String applicationFeeLabel,
        String insuranceFeeLabel,
        String totalDeductionsLabel,
        String deductionSummaryLabel,
        String guarantorDetailsLabel,
        String approvalDecisionSummaryLabel,
        String disbursementDateLabel,
        String finalDueDateLabel,
        String statusLabel,
        String preparedByLabel,
        String preparedDateLabel
    ) {}

    public record ManagerLoanReport(
        String saccoId,
        int year,
        boolean returnedOnly,
        LoanSummary summary,
        List<LoanApplication> loans,
        Map<UUID, Member> applicantMap
    ) {}

    public record AccountantLoanReport(
        UUID accountantId,
        String saccoId,
        LocalDate fromDate,
        LocalDate toDate,
        String decisionFilter,
        AccountantReviewSummary summary,
        List<AccountantReviewEntry> entries,
        Map<UUID, Member> applicantMap
    ) {}

    public record AccountantReviewSummary(
        long reviewedCount,
        long approvedCount,
        long rejectedCount,
        BigDecimal reviewedAmount
    ) {
        public String getReviewedAmountLabel() {
            return formatMoneyStatic(reviewedAmount);
        }
    }

    public record AccountantReviewEntry(
        ManagerReview review,
        LoanApplication loan
    ) {}

    public record ManagerWorkflowReport(
        UUID managerId,
        String saccoId,
        LocalDate fromDate,
        LocalDate toDate,
        String decisionFilter,
        ManagerWorkflowSummary summary,
        List<ManagerWorkflowEntry> entries,
        Map<UUID, Member> applicantMap
    ) {}

    public record ManagerWorkflowSummary(
        long reviewedCount,
        long approvedCount,
        long rejectedCount,
        BigDecimal reviewedAmount
    ) {
        public String getReviewedAmountLabel() {
            return formatMoneyStatic(reviewedAmount);
        }
    }

    public record ManagerWorkflowEntry(
        ManagerReview review,
        LoanApplication loan
    ) {}

    public record DisbursementLoanReport(
        UUID disbursementOfficerId,
        String saccoId,
        LocalDate fromDate,
        LocalDate toDate,
        DisbursementReviewSummary summary,
        List<DisbursementReviewEntry> entries,
        Map<UUID, Member> applicantMap
    ) {}

    public record DisbursementReviewSummary(
        long disbursedCount,
        long paidCount,
        long defaultedCount,
        BigDecimal disbursedAmount
    ) {
        public String getDisbursedAmountLabel() {
            return formatMoneyStatic(disbursedAmount);
        }
    }

    public record DisbursementReviewEntry(
        ManagerReview review,
        LoanApplication loan
    ) {}

    private static String formatMoneyStatic(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(safe);
    }

    private static final class PDFCursor {
        private static final float MARGIN = 48f;
        private static final float FONT_SIZE = 11f;
        private static final float LEADING = 16f;

        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private PDFCursor(PDDocument document) {
            this.document = document;
        }

        private void openPage() throws IOException {
            close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void writeWrapped(String text) throws IOException {
            List<String> wrapped = wrap(text == null ? "" : text, text != null && !text.isBlank() && !text.startsWith(" ") && !text.contains("|") && !text.endsWith(":") ? regular : regular);
            if (text != null && (text.equals("Member Loan Report") || text.equals("Manager Disbursed Loans Report") || text.equals("Summary") || text.equals("Loan List"))) {
                wrapped = wrap(text, bold);
                writeLines(wrapped, bold);
                y -= 2f;
                return;
            }
            writeLines(wrapped, regular);
        }

        private void writeLines(List<String> lines, PDType1Font font) throws IOException {
            for (String line : lines) {
                if (y <= MARGIN) {
                    openPage();
                }
                stream.beginText();
                stream.setFont(font, FONT_SIZE);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(line);
                stream.endText();
                y -= LEADING;
            }
        }

        private List<String> wrap(String text, PDType1Font font) throws IOException {
            List<String> lines = new ArrayList<>();
            if (text.isBlank()) {
                lines.add("");
                return lines;
            }
            float maxWidth = PDRectangle.A4.getWidth() - (MARGIN * 2);
            String[] words = text.split("\\s+");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                float width = font.getStringWidth(candidate) / 1000f * FONT_SIZE;
                if (width > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }

    private static final class MemberPdfRenderer {
        private static final float MARGIN = 38f;
        private static final float TOP_MARGIN = 40f;
        private static final float BOTTOM_MARGIN = 34f;
        private static final float FOOTER_GAP = 22f;
        private static final float TITLE_SIZE = 18f;
        private static final float META_SIZE = 9f;
        private static final float SECTION_SIZE = 10.5f;
        private static final float BODY_SIZE = 8.2f;
        private static final float SMALL_SIZE = 7.1f;
        private static final float LINE_GAP = 3f;
        private static final float CELL_PADDING_X = 6f;
        private static final float CELL_PADDING_Y = 5f;
        private static final Color TEXT_COLOR = new Color(41, 55, 71);
        private static final Color MUTED_COLOR = new Color(87, 106, 126);
        private static final Color BORDER_COLOR = new Color(225, 232, 238);
        private static final Color HEADER_FILL = new Color(246, 248, 251);
        private static final Color RULE_COLOR = new Color(60, 79, 97);

        private final PDDocument document;
        private final MemberLoanReport report;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private MemberPdfRenderer(PDDocument document, MemberLoanReport report) {
            this.document = document;
            this.report = report;
        }

        private void render() throws IOException {
            startNewPage();
            drawHeader();
            drawSummaryTable();
            drawAnalyticsTable();
            drawDetailedLoansSection();
            drawApprovalSection();
            closePage();
        }

        private void startNewPage() throws IOException {
            if (stream != null) {
                drawFooter();
                stream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - TOP_MARGIN;
        }

        private void closePage() throws IOException {
            if (stream != null) {
                drawFooter();
                stream.close();
                stream = null;
            }
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight < (BOTTOM_MARGIN + FOOTER_GAP)) {
                startNewPage();
            }
        }

        private void drawHeader() throws IOException {
            ensureSpace(56f);
            writeText(sanitizePdfText("Member Loan Report"), MARGIN, y, bold, TITLE_SIZE, TEXT_COLOR);
            writeRightAligned(
                "Generated: " + DATE_FORMATTER.format(LocalDate.now()),
                page.getMediaBox().getWidth() - MARGIN,
                y + 1f,
                regular,
                META_SIZE,
                MUTED_COLOR
            );
            y -= 19f;
            String memberLabel = sanitizePdfText(report.member().getFullName()).toUpperCase(Locale.ROOT)
                + " (" + sanitizePdfText(report.member().getMemberNo()) + ")";
            writeText(memberLabel, MARGIN, y, bold, META_SIZE + 0.5f, MUTED_COLOR);
            y -= 16f;
            drawRule();
            y -= 14f;
        }

        private void drawSummaryTable() throws IOException {
            List<String[]> rows = List.of(
                new String[]{"Disbursed Loans", String.valueOf(report.summary().disbursedCount())},
                new String[]{"Returned / Paid", String.valueOf(report.summary().paidCount())},
                new String[]{"Ongoing Loans", String.valueOf(report.summary().ongoingCount())},
                new String[]{"Total Disbursed Amount", sanitizePdfText(report.summary().getDisbursedAmountLabel())},
                new String[]{"Total Returned Amount", sanitizePdfText(report.summary().getPaidAmountLabel())}
            );
            float width = contentWidth();
            drawTable(
                new String[]{"Metric", "Value"},
                new float[]{width * 0.58f, width * 0.42f},
                rows,
                BODY_SIZE,
                BODY_SIZE,
                14f
            );
            y -= 16f;
        }

        private void drawAnalyticsTable() throws IOException {
            MemberLoanAnalyticsSummary analytics = report.analytics();
            if (analytics == null) {
                return;
            }
            drawSectionHeading("Loan Status Analytics");
            List<String[]> rows = List.of(
                new String[]{"Applied Loans", String.valueOf(analytics.appliedCount())},
                new String[]{"Active Loans", String.valueOf(analytics.activeCount())},
                new String[]{"Disbursed Loans", String.valueOf(analytics.disbursedCount())},
                new String[]{"Paid Loans", String.valueOf(analytics.paidCount())},
                new String[]{"Defaulted Loans", String.valueOf(analytics.defaultedCount())},
                new String[]{"Forfeited Loan Applications", String.valueOf(analytics.forfeitedCount())},
                new String[]{"Rejected Loans", String.valueOf(analytics.rejectedCount())},
                new String[]{"Active Loan Amount", sanitizePdfText(analytics.getActiveAmountLabel())}
            );
            float width = contentWidth();
            drawTable(
                new String[]{"Metric", "Value"},
                new float[]{width * 0.58f, width * 0.42f},
                rows,
                BODY_SIZE,
                BODY_SIZE,
                14f
            );
            y -= 6f;
        }

        private void drawDetailedLoansSection() throws IOException {
            drawSectionHeading("Detailed Loan Applications");
            drawParagraph("This layout uses structured tables for better clarity and PDF readability.", regular, META_SIZE, MUTED_COLOR);
            y -= 6f;

            List<String[]> rows = new ArrayList<>();
            List<MemberLoanDetail> details = report.details() == null ? List.of() : report.details();
            if (details.isEmpty()) {
                rows.add(new String[]{"-", "No records", "-", "-", "-", "-", "-", "-", "-"});
            } else {
                int index = 1;
                for (MemberLoanDetail detail : details) {
                    rows.add(new String[]{
                        String.valueOf(index++),
                        sanitizePdfText(detail.loanApplicationIdLabel()),
                        sanitizePdfText(detail.loanIdLabel()),
                        sanitizePdfText(detail.approvedAmountLabel()),
                        sanitizePdfText(detail.tenureLabel()),
                        "App: " + sanitizePdfText(detail.applicationFeeLabel()) + "\nIns: " + sanitizePdfText(detail.insuranceFeeLabel()),
                        sanitizePdfText(detail.totalDeductionsLabel()),
                        sanitizePdfText(detail.statusLabel()),
                        sanitizePdfText(detail.disbursementDateLabel()) + " / " + sanitizePdfText(detail.finalDueDateLabel())
                    });
                }
            }

            drawTable(
                new String[]{"Loan #", "Application ID", "Loan ID", "Amount", "Tenure", "Fees", "Deductions", "Status", "Disbursed / Due"},
                new float[]{28f, 54f, 42f, 56f, 46f, 66f, 64f, 48f, contentWidth() - 404f},
                rows,
                SMALL_SIZE,
                SMALL_SIZE,
                13f
            );
            y -= 16f;
        }

        private void drawApprovalSection() throws IOException {
            drawSectionHeading("Approval & Member Details");
            List<MemberLoanDetail> details = report.details() == null ? List.of() : report.details();
            if (details.isEmpty()) {
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Field", "No loan applications found for this member yet."});
                float width = contentWidth();
                drawTable(
                    new String[]{"Field", "Details"},
                    new float[]{width * 0.32f, width * 0.68f},
                    rows,
                    BODY_SIZE,
                    BODY_SIZE,
                    14f
                );
                return;
            }

            int index = 1;
            for (MemberLoanDetail detail : details) {
                drawLoanDetailTitle(detail, index++);
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Member Contact", stackText(detail.memberDetailsLabel(), " | ")});
                rows.add(new String[]{"Guarantor", stackText(detail.guarantorDetailsLabel(), "; ")});
                rows.add(new String[]{"Committee Decision", stackText(detail.approvalDecisionSummaryLabel(), " | ", "; ")});
                rows.add(new String[]{"Prepared By", sanitizePdfText(detail.preparedByLabel()) + " / " + sanitizePdfText(detail.preparedDateLabel())});

                float width = contentWidth();
                drawTable(
                    new String[]{"Field", "Details"},
                    new float[]{width * 0.32f, width * 0.68f},
                    rows,
                    BODY_SIZE,
                    BODY_SIZE,
                    12f
                );
            }
        }

        private void drawLoanDetailTitle(MemberLoanDetail detail, int index) throws IOException {
            ensureSpace(18f);
            String loanId = "-".equals(detail.loanIdLabel())
                ? ""
                : " | Loan ID " + sanitizePdfText(detail.loanIdLabel());
            String title = "Loan " + index
                + ": " + sanitizePdfText(detail.approvedProductLabel())
                + " | Application " + sanitizePdfText(detail.loanApplicationIdLabel())
                + loanId;
            writeText(title, MARGIN, y, bold, BODY_SIZE + 0.6f, TEXT_COLOR);
            y -= 12f;
        }

        private void drawSectionHeading(String text) throws IOException {
            ensureSpace(18f);
            writeText(sanitizePdfText(text), MARGIN, y, bold, SECTION_SIZE, TEXT_COLOR);
            y -= 16f;
        }

        private void drawParagraph(String text, PDType1Font font, float fontSize, Color color) throws IOException {
            float width = contentWidth();
            List<String> lines = wrapText(text, font, fontSize, width);
            for (String line : lines) {
                ensureSpace(fontSize + LINE_GAP + 2f);
                writeText(line, MARGIN, y, font, fontSize, color);
                y -= fontSize + LINE_GAP;
            }
        }

        private void drawTable(String[] headers,
                               float[] widths,
                               List<String[]> rows,
                               float headerFontSize,
                               float bodyFontSize,
                               float gapAfter) throws IOException {
            drawRow(headers, widths, bold, headerFontSize, true);
            for (String[] row : rows) {
                if (row == null) {
                    continue;
                }
                float rowHeight = measureRowHeight(row, widths, regular, bodyFontSize);
                if (y - rowHeight < (BOTTOM_MARGIN + FOOTER_GAP)) {
                    startNewPage();
                    drawRow(headers, widths, bold, headerFontSize, true);
                }
                drawRow(row, widths, regular, bodyFontSize, false);
            }
            y -= gapAfter;
        }

        private void drawRow(String[] cells,
                             float[] widths,
                             PDType1Font font,
                             float fontSize,
                             boolean header) throws IOException {
            float rowHeight = measureRowHeight(cells, widths, font, fontSize);
            ensureSpace(rowHeight);

            float x = MARGIN;
            float lineHeight = fontSize + 2f;
            List<List<String>> wrappedCells = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) {
                wrappedCells.add(wrapText(cells[i], font, fontSize, widths[i] - (CELL_PADDING_X * 2f)));
            }

            for (int i = 0; i < cells.length; i++) {
                stream.setNonStrokingColor(header ? HEADER_FILL : Color.WHITE);
                stream.addRect(x, y - rowHeight, widths[i], rowHeight);
                stream.fill();

                stream.setStrokingColor(BORDER_COLOR);
                stream.addRect(x, y - rowHeight, widths[i], rowHeight);
                stream.stroke();

                float textY = y - CELL_PADDING_Y - fontSize;
                for (String line : wrappedCells.get(i)) {
                    writeText(line, x + CELL_PADDING_X, textY, font, fontSize, TEXT_COLOR);
                    textY -= lineHeight;
                }
                x += widths[i];
            }
            y -= rowHeight;
        }

        private float measureRowHeight(String[] cells,
                                       float[] widths,
                                       PDType1Font font,
                                       float fontSize) throws IOException {
            float lineHeight = fontSize + 2f;
            int maxLines = 1;
            for (int i = 0; i < cells.length; i++) {
                List<String> lines = wrapText(cells[i], font, fontSize, widths[i] - (CELL_PADDING_X * 2f));
                maxLines = Math.max(maxLines, lines.size());
            }
            return (CELL_PADDING_Y * 2f) + (maxLines * lineHeight);
        }

        private List<String> wrapText(String text,
                                      PDType1Font font,
                                      float fontSize,
                                      float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            String safeText = sanitizePdfText(text);
            for (String paragraph : safeText.split("\\n", -1)) {
                if (paragraph.isBlank()) {
                    lines.add("-");
                    continue;
                }
                List<String> paragraphLines = wrapParagraph(paragraph, font, fontSize, maxWidth);
                lines.addAll(paragraphLines);
            }
            return lines.isEmpty() ? List.of("-") : lines;
        }

        private List<String> wrapParagraph(String text,
                                           PDType1Font font,
                                           float fontSize,
                                           float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String token : text.split("\\s+")) {
                List<String> pieces = splitLongToken(token, font, fontSize, maxWidth);
                for (String piece : pieces) {
                    String candidate = current.isEmpty() ? piece : current + " " + piece;
                    if (stringWidth(candidate, font, fontSize) > maxWidth && !current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder(piece);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("-") : lines;
        }

        private List<String> splitLongToken(String token,
                                            PDType1Font font,
                                            float fontSize,
                                            float maxWidth) throws IOException {
            if (stringWidth(token, font, fontSize) <= maxWidth) {
                return List.of(token);
            }
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (char ch : token.toCharArray()) {
                String candidate = current.toString() + ch;
                if (stringWidth(candidate, font, fontSize) > maxWidth && !current.isEmpty()) {
                    parts.add(current.toString());
                    current = new StringBuilder(String.valueOf(ch));
                } else {
                    current.append(ch);
                }
            }
            if (!current.isEmpty()) {
                parts.add(current.toString());
            }
            return parts;
        }

        private float stringWidth(String text, PDType1Font font, float fontSize) throws IOException {
            return font.getStringWidth(text) / 1000f * fontSize;
        }

        private void drawRule() throws IOException {
            stream.setStrokingColor(RULE_COLOR);
            stream.moveTo(MARGIN, y);
            stream.lineTo(page.getMediaBox().getWidth() - MARGIN, y);
            stream.stroke();
        }

        private void drawFooter() throws IOException {
            float footerY = BOTTOM_MARGIN + 10f;
            stream.setStrokingColor(BORDER_COLOR);
            stream.moveTo(MARGIN, footerY + 10f);
            stream.lineTo(page.getMediaBox().getWidth() - MARGIN, footerY + 10f);
            stream.stroke();
            String preparedBy = sanitizePdfText(report.member().getFullName());
            String preparedDate = DATE_FORMATTER.format(LocalDate.now());
            writeText("Prepared by: " + preparedBy, MARGIN, footerY, regular, 7.4f, MUTED_COLOR);
            writeRightAligned("Date: " + preparedDate, page.getMediaBox().getWidth() - MARGIN, footerY, regular, 7.4f, MUTED_COLOR);
        }

        private void writeText(String text,
                               float x,
                               float baselineY,
                               PDType1Font font,
                               float fontSize,
                               Color color) throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(color);
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(x, baselineY);
            stream.showText(sanitizePdfText(text));
            stream.endText();
        }

        private void writeRightAligned(String text,
                                       float rightX,
                                       float baselineY,
                                       PDType1Font font,
                                       float fontSize,
                                       Color color) throws IOException {
            float width = stringWidth(sanitizePdfText(text), font, fontSize);
            writeText(text, rightX - width, baselineY, font, fontSize, color);
        }

        private float contentWidth() {
            return page.getMediaBox().getWidth() - (MARGIN * 2f);
        }

        private static String stackText(String text, String... delimiters) {
            String stacked = sanitizePdfText(text);
            for (String delimiter : delimiters) {
                stacked = stacked.replace(delimiter, "\n");
            }
            return stacked;
        }
    }
}
