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
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.LoanPaymentTransaction;
import com.sacco.mvp.integration.memberportal.LoanPaymentSummaryDto;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanPaymentTransactionRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private static final DateTimeFormatter HUMAN_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanPaymentTransactionRepository loanPaymentTransactionRepository;
    private final MemberRepository memberRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final ObjectMapper objectMapper;
    private final LoanAnalyticsService loanAnalyticsService;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;

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

    public AnalyticsExportReport memberAnalyticsExportReport(AppUserPrincipal principal,
                                                             LocalDate fromDate,
                                                             LocalDate toDate,
                                                             com.sacco.mvp.domain.LoanType loanType) {
        DateRange range = resolveReportRange(fromDate, toDate);
        DateRange previous = previousRange(range.fromDate(), range.toDate());
        Member member = memberRepository.findById(principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        List<LoanApplication> loans = loanApplicationRepository.findMemberLoansForAnalytics(
                principal.getMemberId(), startOfDay(range.fromDate()), dayAfter(range.toDate()), loanType, null)
            .stream()
            .sorted(Comparator.comparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        Map<UUID, PaymentTotals> paymentTotalsByLoanId = paymentTotalsByLoanId(loans, range);
        LoanAnalyticsService.MemberLoanAnalytics analytics =
            loanAnalyticsService.forMember(principal.getMemberId(), range.fromDate(), range.toDate(), loanType, null);
        LoanAnalyticsService.MemberLoanAnalytics previousAnalytics =
            loanAnalyticsService.forMember(principal.getMemberId(), previous.fromDate(), previous.toDate(), loanType, null);
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries =
            loanAnalyticsService.statusTrendForMember(principal.getMemberId(), range.fromDate(), range.toDate(), loanType, null);
        ExportContext context = exportContext(principal, range, loanType);
        return new AnalyticsExportReport(
            ReportKind.MEMBER,
            context.saccoName(),
            "MEMBER LOAN REPORT",
            context.stationId(),
            context.branchName(),
            range.fromDate(),
            range.toDate(),
            LocalDate.now(),
            principal.getFullName(),
            exporterRoleLabel(principal),
            loanProductLabel(loanType),
            member,
            analytics,
            previousAnalytics,
            metricDeltas(analytics, previousAnalytics),
            memberPortfolio(analytics),
            productRowsFromLoans(loans, paymentTotalsByLoanId),
            productFinancialRowsFromLoans(loans, paymentTotalsByLoanId),
            trendSeries,
            recentActivityRows(loans),
            activeLoanRowsFromLoans(loans, paymentTotalsByLoanId),
            financialSummary(loans, analytics),
            observations(analytics, previousAnalytics),
            "This report summarizes the loan performance and status of the member within the selected period."
        );
    }

    public List<ActiveLoanDetailRow> memberActiveLoanDetails(UUID memberId,
                                                             LocalDate fromDate,
                                                             LocalDate toDate,
                                                             com.sacco.mvp.domain.LoanType loanType) {
        DateRange range = resolveReportRange(fromDate, toDate);
        List<LoanApplication> loans = loanApplicationRepository.findMemberLoansForAnalytics(
                memberId, startOfDay(range.fromDate()), dayAfter(range.toDate()), loanType, null)
            .stream()
            .sorted(Comparator.comparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        return activeLoanRowsFromLoans(loans, paymentTotalsByLoanId(loans, range));
    }

    public AnalyticsExportReport staffAnalyticsExportReport(AppUserPrincipal principal,
                                                            LocalDate fromDate,
                                                            LocalDate toDate,
                                                            com.sacco.mvp.domain.LoanType loanType,
                                                            String viewAs) {
        if ("member".equalsIgnoreCase(viewAs) && principal.isMemberAccess()) {
            return memberAnalyticsExportReport(principal, fromDate, toDate, loanType);
        }
        DateRange range = resolveReportRange(fromDate, toDate);
        DateRange previous = previousRange(range.fromDate(), range.toDate());
        boolean stationWideStaffView = principal.hasRole(Position.MANAGER);
        LoanAnalyticsService.MemberLoanAnalytics analytics = stationWideStaffView
            ? loanAnalyticsService.forStation(principal.getSaccoId(), principal.getStationId(), range.fromDate(), range.toDate(), loanType, null)
            : loanAnalyticsService.forStaff(principal, range.fromDate(), range.toDate(), loanType, null);
        LoanAnalyticsService.MemberLoanAnalytics previousAnalytics = stationWideStaffView
            ? loanAnalyticsService.forStation(principal.getSaccoId(), principal.getStationId(), previous.fromDate(), previous.toDate(), loanType, null)
            : loanAnalyticsService.forStaff(principal, previous.fromDate(), previous.toDate(), loanType, null);
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries = stationWideStaffView
            ? loanAnalyticsService.statusTrendForStation(principal.getSaccoId(), principal.getStationId(), range.fromDate(), range.toDate(), loanType, null)
            : loanAnalyticsService.statusTrendForStaff(principal, range.fromDate(), range.toDate(), loanType, null);
        LoanAnalyticsService.StaffPortfolioSummary portfolio = stationWideStaffView
            ? loanAnalyticsService.stationPortfolio(principal.getSaccoId(), principal.getStationId(), range.fromDate(), range.toDate(), loanType, null)
            : loanAnalyticsService.staffPortfolio(principal, range.fromDate(), range.toDate(), loanType, null);
        List<LoanApplication> stationLoans = stationWideStaffView
            ? loanApplicationRepository.findScopeLoansForAnalytics(
                principal.getSaccoId(), principal.getStationId(), startOfDay(range.fromDate()), dayAfter(range.toDate()), loanType, null)
            : loanAnalyticsService.loansForStaffAnalytics(principal, range.fromDate(), range.toDate(), loanType, null);
        Map<UUID, PaymentTotals> paymentTotalsByLoanId = paymentTotalsByLoanId(stationLoans, range);
        List<ProductPerformanceRow> productRows = productRowsFromLoans(stationLoans, paymentTotalsByLoanId);
        List<ProductFinancialBreakdownRow> productFinancialRows = productFinancialRowsFromLoans(stationLoans, paymentTotalsByLoanId);
        ExportContext context = exportContext(principal, range, loanType);
        return new AnalyticsExportReport(
            stationWideStaffView ? ReportKind.STATION : ReportKind.STAFF,
            context.saccoName(),
            stationWideStaffView ? "STATION LOAN STATUS REPORT" : "STAFF LOAN ANALYTICS REPORT",
            context.stationId(),
            context.branchName(),
            range.fromDate(),
            range.toDate(),
            LocalDate.now(),
            principal.getFullName(),
            exporterRoleLabel(principal),
            loanProductLabel(loanType),
            null,
            analytics,
            previousAnalytics,
            metricDeltas(analytics, previousAnalytics),
            portfolio,
            productRows,
            productFinancialRows,
            trendSeries,
            stationWideStaffView ? recentActivityRows(stationLoans) : List.of(),
            List.of(),
            stationWideStaffView ? financialSummary(stationLoans, analytics) : financialSummary(List.of(), analytics),
            observations(analytics, previousAnalytics),
            stationWideStaffView
                ? "This report summarizes station loan performance and status within the selected period."
                : "This report summarizes loans handled by the staff member within the selected period."
        );
    }

    public byte[] buildMemberAnalyticsPdf(AnalyticsExportReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            new FormalPdf(document, report, PDRectangle.A4).renderMemberTwoPage();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate member analytics PDF.", ex);
        }
    }

    public byte[] buildStationAnalyticsPdf(AnalyticsExportReport report) {
        if (report.kind() == ReportKind.MEMBER) {
            return buildMemberAnalyticsPdf(report);
        }
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            new FormalPdf(document, report, PDRectangle.A4).renderStationTwoPage();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate station analytics PDF.", ex);
        }
    }

    public byte[] buildMemberAnalyticsExcel(AnalyticsExportReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            XSSFSheet summary = workbook.createSheet("Summary");
            writeMemberSummarySheet(summary, report, styles);
            writeStatusSheet(workbook.createSheet("Status Analysis"), report, styles);
            writeProductSheet(workbook.createSheet("Product Performance"), report, styles);
            writeTrendSheet(workbook.createSheet("Trends"), report, styles);
            writeFinancialSheet(workbook.createSheet("Financial Summary"), report, styles);
            writeActivitySheet(workbook.createSheet("Activity Log"), report, styles);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate member analytics Excel report.", ex);
        }
    }

    public byte[] buildStationAnalyticsExcel(AnalyticsExportReport report) {
        if (report.kind() == ReportKind.MEMBER) {
            return buildMemberAnalyticsExcel(report);
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            writeStationSummarySheet(workbook.createSheet("Summary"), report, styles);
            writeStatusSheet(workbook.createSheet("Loan Status Analysis"), report, styles);
            writeProductSheet(workbook.createSheet("Product Performance"), report, styles);
            writeTrendSheet(workbook.createSheet("Trends"), report, styles);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate staff analytics Excel report.", ex);
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

    private DateRange resolveReportRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate effectiveTo = toDate == null ? LocalDate.now() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.minusYears(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            return new DateRange(effectiveTo, effectiveFrom);
        }
        return new DateRange(effectiveFrom, effectiveTo);
    }

    private DateRange previousRange(LocalDate fromDate, LocalDate toDate) {
        long days = Math.max(java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate), 0);
        LocalDate previousTo = fromDate.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days);
        return new DateRange(previousFrom, previousTo);
    }

    private OffsetDateTime startOfDay(LocalDate value) {
        return value == null ? null : value.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime dayAfter(LocalDate value) {
        return value == null ? null : value.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private ExportContext exportContext(AppUserPrincipal principal, DateRange range, com.sacco.mvp.domain.LoanType loanType) {
        String saccoName = registeredSaccoRepository.findById(principal.getSaccoId())
            .map(RegisteredSacco::getSaccoName)
            .filter(name -> !name.isBlank())
            .orElse(principal.getSaccoId());
        SaccoStation station = principal.getStationId() == null || principal.getStationId().isBlank()
            ? null
            : saccoStationRepository.findBySaccoIdAndStationId(principal.getSaccoId(), principal.getStationId()).orElse(null);
        String stationId = station == null || station.getStationId() == null || station.getStationId().isBlank()
            ? valueOrDash(principal.getStationId())
            : station.getStationId();
        String branchName = station == null || station.getAddressLocation() == null || station.getAddressLocation().isBlank()
            ? stationId
            : station.getAddressLocation();
        return new ExportContext(saccoName, stationId, branchName, range.fromDate(), range.toDate(), loanProductLabel(loanType));
    }

    private String exporterRoleLabel(AppUserPrincipal principal) {
        if (principal == null || principal.getPosition() == null) {
            return "-";
        }
        return switch (principal.getPosition()) {
            case MEMBER -> "Member";
            case MINOR_ADMIN -> "Minor Admin";
            case MANAGER -> "Manager";
            case ACCOUNTANT -> "Accountant";
            case DISBURSEMENT_OFFICER -> "Disbursement Officer";
            case BOARD -> "Board";
            case LOAN_OFFICER -> "Loan Officer";
            case ADMIN -> "Admin";
        };
    }

    private String loanProductLabel(com.sacco.mvp.domain.LoanType loanType) {
        return loanType == null ? "All Products" : loanType.getDisplayLabel();
    }

    private Map<String, LoanAnalyticsService.MetricDelta> metricDeltas(LoanAnalyticsService.MemberLoanAnalytics analytics,
                                                                       LoanAnalyticsService.MemberLoanAnalytics previousAnalytics) {
        return loanAnalyticsService.metricDeltas(analytics, previousAnalytics)
            .stream()
            .collect(Collectors.toMap(LoanAnalyticsService.MetricDelta::key, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private LoanAnalyticsService.StaffPortfolioSummary memberPortfolio(LoanAnalyticsService.MemberLoanAnalytics analytics) {
        BigDecimal defaultedRate = analytics.disbursedLoans() == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(analytics.defaultedLoans())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(analytics.disbursedLoans()), 2, RoundingMode.HALF_UP);
        String riskLevel = defaultedRate.compareTo(BigDecimal.valueOf(10)) >= 0
            ? "High"
            : defaultedRate.compareTo(BigDecimal.valueOf(5)) >= 0 ? "Moderate" : "Low";
        return new LoanAnalyticsService.StaffPortfolioSummary(
            analytics.appliedLoans(),
            analytics.disbursedLoans(),
            analytics.rejectedLoans(),
            analytics.disbursedLoans(),
            analytics.defaultedLoans(),
            defaultedRate,
            riskLevel
        );
    }

    private Map<UUID, PaymentTotals> paymentTotalsByLoanId(List<LoanApplication> loans, DateRange range) {
        List<UUID> loanIds = loans.stream()
            .map(LoanApplication::getId)
            .filter(Objects::nonNull)
            .toList();
        if (loanIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PaymentTotals> totalsByLoanId = new LinkedHashMap<>();
        loanPaymentTransactionRepository
            .findByLoanApplicationIdInAndReceiptDateBetweenOrderByReceiptDateAsc(loanIds, range.fromDate(), range.toDate())
            .forEach(transaction -> totalsByLoanId.merge(
                transaction.getLoanApplicationId(),
                new PaymentTotals(
                    transaction.getPrincipalPaid() == null ? BigDecimal.ZERO : transaction.getPrincipalPaid(),
                    transaction.getInterestPaid() == null ? BigDecimal.ZERO : transaction.getInterestPaid(),
                    transaction.getTotalPaid() == null ? BigDecimal.ZERO : transaction.getTotalPaid()
                ),
                PaymentTotals::plus
            ));
        return totalsByLoanId;
    }

    private List<ProductPerformanceRow> productRowsFromLoans(List<LoanApplication> loans, Map<UUID, PaymentTotals> paymentTotalsByLoanId) {
        Map<com.sacco.mvp.domain.LoanType, List<LoanApplication>> byType = new EnumMap<>(com.sacco.mvp.domain.LoanType.class);
        for (com.sacco.mvp.domain.LoanType type : reportableLoanTypes()) {
            byType.put(type, List.of());
        }
        Map<com.sacco.mvp.domain.LoanType, List<LoanApplication>> grouped = loans.stream()
            .filter(loan -> loan.getLoanType() != null)
            .filter(loan -> loan.getLoanType() != com.sacco.mvp.domain.LoanType.CUSTOMIZED_LOAN)
            .collect(Collectors.groupingBy(LoanApplication::getLoanType, () -> new EnumMap<>(com.sacco.mvp.domain.LoanType.class), Collectors.toList()));
        grouped.forEach(byType::put);
        return byType.entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> entry.getKey().getDisplayOrder()))
            .map(entry -> {
                List<LoanApplication> values = entry.getValue();
                long applied = values.stream().filter(loan -> loan.getStatus() != LoanStatus.DRAFT).count();
                long active = values.stream().filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus())).count();
                long disbursed = values.stream().filter(loan -> DISBURSED_STATUSES.contains(loan.getStatus())).count();
                long paid = values.stream().filter(loan -> loan.getStatus() == LoanStatus.PAID).count();
                long defaulted = values.stream().filter(loan -> loan.getStatus() == LoanStatus.DEFAULTED).count();
                long rejected = values.stream().filter(loan -> REJECTED_STATUSES.contains(loan.getStatus())).count();
                BigDecimal interestPaid = values.stream()
                    .map(LoanApplication::getId)
                    .map(id -> paymentTotalsByLoanId.getOrDefault(id, PaymentTotals.ZERO).interestPaid())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return new ProductPerformanceRow(entry.getKey().getDisplayLabel(), applied, active, disbursed, paid, defaulted, rejected, interestPaid);
            })
            .toList();
    }

    private List<ProductPerformanceRow> productRowsFromPerformance(List<LoanAnalyticsService.LoanProductPerformance> performance) {
        return performance.stream()
            .map(item -> {
                long active = Math.max(0, item.totalLoans() - item.paidLoans() - item.rejectedLoans());
                long disbursed = Math.max(0, item.totalLoans() - item.rejectedLoans());
                return new ProductPerformanceRow(item.label(), item.totalLoans(), active, disbursed, item.paidLoans(), item.defaultedLoans(), item.rejectedLoans(), BigDecimal.ZERO);
            })
            .toList();
    }

    private List<ProductFinancialBreakdownRow> productFinancialRowsFromLoans(List<LoanApplication> loans, Map<UUID, PaymentTotals> paymentTotalsByLoanId) {
        Map<com.sacco.mvp.domain.LoanType, List<LoanApplication>> byType = new EnumMap<>(com.sacco.mvp.domain.LoanType.class);
        for (com.sacco.mvp.domain.LoanType type : reportableLoanTypes()) {
            byType.put(type, List.of());
        }
        Map<com.sacco.mvp.domain.LoanType, List<LoanApplication>> grouped = loans.stream()
            .filter(loan -> loan.getLoanType() != null)
            .filter(loan -> loan.getLoanType() != com.sacco.mvp.domain.LoanType.CUSTOMIZED_LOAN)
            .collect(Collectors.groupingBy(LoanApplication::getLoanType, () -> new EnumMap<>(com.sacco.mvp.domain.LoanType.class), Collectors.toList()));
        grouped.forEach(byType::put);
        return byType.entrySet().stream()
            .sorted(Comparator.comparingInt(entry -> entry.getKey().getDisplayOrder()))
            .map(entry -> {
                BigDecimal interestPaid = BigDecimal.ZERO;
                BigDecimal interestUnpaid = BigDecimal.ZERO;
                BigDecimal loanAmountPaid = BigDecimal.ZERO;
                BigDecimal loanAmountUnpaid = BigDecimal.ZERO;
                for (LoanApplication loan : entry.getValue()) {
                    if (!DISBURSED_STATUSES.contains(loan.getStatus())) {
                        continue;
                    }
                    LoanPaymentSummaryDto summary = loanPaymentSummary(loan);
                    PaymentTotals totals = paymentTotalsByLoanId.getOrDefault(loan.getId(), PaymentTotals.ZERO);
                    interestPaid = interestPaid.add(totals.interestPaid() == null ? BigDecimal.ZERO : totals.interestPaid());
                    interestUnpaid = interestUnpaid.add(interestUnpaidAmount(loan, summary, totals));
                    loanAmountPaid = loanAmountPaid.add(totals.principalPaid() == null ? BigDecimal.ZERO : totals.principalPaid());
                    loanAmountUnpaid = loanAmountUnpaid.add(outstandingPrincipalAmount(loan, summary, totals));
                }
                return new ProductFinancialBreakdownRow(entry.getKey().getDisplayLabel(), interestPaid, interestUnpaid, loanAmountPaid, loanAmountUnpaid);
            })
            .toList();
    }

    private List<com.sacco.mvp.domain.LoanType> reportableLoanTypes() {
        return java.util.Arrays.stream(com.sacco.mvp.domain.LoanType.values())
            .filter(type -> type != com.sacco.mvp.domain.LoanType.CUSTOMIZED_LOAN)
            .toList();
    }

    private List<ActiveLoanDetailRow> activeLoanRowsFromLoans(List<LoanApplication> loans, Map<UUID, PaymentTotals> paymentTotalsByLoanId) {
        return loans.stream()
            .filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus()))
            .sorted(Comparator.comparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(loan -> {
                LoanPaymentSummaryDto summary = loanPaymentSummary(loan);
                PaymentTotals totals = paymentTotalsByLoanId.getOrDefault(loan.getId(), PaymentTotals.ZERO);
                BigDecimal outstanding = summary == null || summary.totalOutstanding() == null
                    ? loan.getAmount()
                    : summary.totalOutstanding();
                BigDecimal requiredInterest = requiredInterestAmount(loan, summary);
                BigDecimal remainingInterest = summary == null || summary.outstandingInterest() == null
                    ? BigDecimal.ZERO
                    : summary.outstandingInterest();
                return new ActiveLoanDetailRow(
                    loan.getLoanId() == null || loan.getLoanId().isBlank() ? "-" : loan.getLoanId(),
                    loan.getLoanType() == null ? "-" : loan.getLoanType().getDisplayLabel(),
                    moneyPlain(loan.getAmount()),
                    moneyPlain(outstanding),
                    moneyPlain(requiredInterest),
                    moneyPlain(totals.principalPaid()),
                    moneyPlain(totals.interestPaid()),
                    moneyPlain(remainingInterest)
                );
            })
            .toList();
    }

    private BigDecimal requiredInterestAmount(LoanApplication loan, LoanPaymentSummaryDto summary) {
        if (summary != null && summary.interestAmount() != null) {
            return summary.interestAmount();
        }
        if (loan == null || loan.getFinancialSnapshot() == null || loan.getFinancialSnapshot().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(loan.getFinancialSnapshot(), new TypeReference<>() {});
            return readBigDecimal(raw.get("interestAmount"));
        } catch (IOException ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal outstandingBalanceAmount(LoanApplication loan, LoanPaymentSummaryDto summary) {
        if (summary != null && summary.totalOutstanding() != null) {
            return summary.totalOutstanding();
        }
        if (loan != null && loan.getStatus() == LoanStatus.PAID) {
            return BigDecimal.ZERO;
        }
        return loan == null || loan.getAmount() == null ? BigDecimal.ZERO : loan.getAmount();
    }

    private BigDecimal outstandingPrincipalAmount(LoanApplication loan, LoanPaymentSummaryDto summary, PaymentTotals totals) {
        if (loan != null && loan.getStatus() == LoanStatus.PAID) {
            return BigDecimal.ZERO;
        }
        if (summary != null && summary.outstandingPrincipal() != null) {
            return summary.outstandingPrincipal();
        }
        BigDecimal principal = summary != null && summary.principalAmount() != null
            ? summary.principalAmount()
            : loan == null || loan.getAmount() == null ? BigDecimal.ZERO : loan.getAmount();
        BigDecimal paid = summary != null && summary.totalPrincipalPaid() != null
            ? summary.totalPrincipalPaid()
            : totals == null || totals.principalPaid() == null ? BigDecimal.ZERO : totals.principalPaid();
        return principal.subtract(paid).max(BigDecimal.ZERO);
    }

    private BigDecimal interestPaidAmount(LoanPaymentSummaryDto summary, PaymentTotals totals) {
        if (summary != null && summary.totalInterestPaid() != null) {
            return summary.totalInterestPaid();
        }
        return totals == null || totals.interestPaid() == null ? BigDecimal.ZERO : totals.interestPaid();
    }

    private BigDecimal interestUnpaidAmount(LoanApplication loan, LoanPaymentSummaryDto summary, PaymentTotals totals) {
        if (loan != null && loan.getStatus() == LoanStatus.PAID) {
            return BigDecimal.ZERO;
        }
        if (summary != null && summary.outstandingInterest() != null) {
            return summary.outstandingInterest();
        }
        BigDecimal required = requiredInterestAmount(loan, summary);
        return required.subtract(interestPaidAmount(summary, totals)).max(BigDecimal.ZERO);
    }

    private LoanPaymentSummaryDto loanPaymentSummary(LoanApplication loan) {
        if (loan == null || loan.getLoanPaymentSummaryJson() == null || loan.getLoanPaymentSummaryJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(loan.getLoanPaymentSummaryJson(), LoanPaymentSummaryDto.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private FinancialSummary financialSummary(List<LoanApplication> loans, LoanAnalyticsService.MemberLoanAnalytics analytics) {
        BigDecimal disbursed = loans.stream()
            .filter(loan -> DISBURSED_STATUSES.contains(loan.getStatus()))
            .map(LoanApplication::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal repaid = loans.stream()
            .filter(loan -> loan.getStatus() == LoanStatus.PAID)
            .map(LoanApplication::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal defaulted = loans.stream()
            .filter(loan -> loan.getStatus() == LoanStatus.DEFAULTED)
            .map(LoanApplication::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal active = analytics.activeLoanAmount() == null ? BigDecimal.ZERO : analytics.activeLoanAmount();
        return new FinancialSummary(active, disbursed, repaid, active, defaulted);
    }

    private List<ActivityRow> recentActivityRows(List<LoanApplication> loans) {
        return loans.stream()
            .sorted(Comparator.comparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(6)
            .map(loan -> new ActivityRow(
                loan.getCreatedAt() == null ? "-" : HUMAN_DATE_FORMATTER.format(loan.getCreatedAt().toLocalDate()),
                activityLabel(loan),
                loan.getLoanType() == null ? "-" : loan.getLoanType().getDisplayLabel(),
                moneyPlain(loan.getAmount()),
                humanizeLoanStatusForReport(loan.getStatus())
            ))
            .toList();
    }

    private String activityLabel(LoanApplication loan) {
        if (loan.getStatus() == LoanStatus.PAID) {
            return "Loan Paid";
        }
        if (DISBURSED_STATUSES.contains(loan.getStatus())) {
            return "Loan Disbursed";
        }
        if (REJECTED_STATUSES.contains(loan.getStatus())) {
            return "Application Rejected";
        }
        if (loan.getStatus() == LoanStatus.FORFEITED) {
            return "Application Forfeited";
        }
        return "Application Submitted";
    }

    private List<String> observations(LoanAnalyticsService.MemberLoanAnalytics analytics,
                                      LoanAnalyticsService.MemberLoanAnalytics previousAnalytics) {
        Map<String, LoanAnalyticsService.MetricDelta> deltas = metricDeltas(analytics, previousAnalytics);
        List<String> rows = new ArrayList<>();
        rows.add("Total loan applications changed by " + percentLabel(deltas.get("applied")) + ".");
        rows.add(analytics.defaultedLoans() == 0
            ? "No defaults recorded during the reporting period."
            : analytics.defaultedLoans() + " defaulted loan(s) recorded during the reporting period.");
        BigDecimal approvalRate = analytics.appliedLoans() == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(analytics.disbursedLoans())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(analytics.appliedLoans()), 0, RoundingMode.HALF_UP);
        rows.add("Approval rate remains at " + approvalRate + "%.");
        rows.add("Portfolio risk level remains " + memberPortfolio(analytics).riskLevel() + ".");
        return rows;
    }

    private boolean fitsMemberOnePage(AnalyticsExportReport report) {
        return report.activityRows().size() <= 4 && report.productRows().size() <= 5 && report.trendSeries().size() <= 7;
    }

    private List<String[]> statusRows(AnalyticsExportReport report) {
        LoanAnalyticsService.MemberLoanAnalytics analytics = report.analytics();
        long total = Math.max(analytics.appliedLoans(), 1);
        return List.of(
            statusRow("Applied", analytics.appliedLoans(), total),
            statusRow("Active", analytics.activeLoans(), total),
            statusRow("Disbursed", analytics.disbursedLoans(), total),
            statusRow("Paid", analytics.paidLoans(), total),
            statusRow("Defaulted", analytics.defaultedLoans(), total),
            statusRow("Forfeited", analytics.forfeitedLoans(), total),
            statusRow("Rejected", analytics.rejectedLoans(), total)
        );
    }

    private String[] statusRow(String label, long count, long total) {
        long percent = total == 0 ? 0 : Math.round((count * 100d) / total);
        return new String[]{label, String.valueOf(count), percent + "%"};
    }

    private String percentLabel(LoanAnalyticsService.MetricDelta delta) {
        BigDecimal value = delta == null ? BigDecimal.ZERO : delta.percent();
        return (value.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + value.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private String periodLabel(AnalyticsExportReport report) {
        return HUMAN_DATE_FORMATTER.format(report.fromDate()) + " - " + HUMAN_DATE_FORMATTER.format(report.toDate());
    }

    private String humanDate(LocalDate date) {
        return date == null ? "-" : HUMAN_DATE_FORMATTER.format(date);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String moneyPlain(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(Locale.US));
        return format.format(safe);
    }

    private String totalRiskLevel(AnalyticsExportReport report) {
        return report.portfolioSummary() == null ? "Low" : report.portfolioSummary().riskLevel();
    }

    private void writeMemberSummarySheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "MEMBER LOAN REPORT", styles);
        row = writeKeyValueBlock(sheet, row, "MEMBER INFORMATION", List.of(
            new String[]{"Member Name", report.member() == null ? "-" : report.member().getFullName()},
            new String[]{"Member Number", report.member() == null ? "-" : report.member().getMemberNo()},
            new String[]{"Station", report.stationId() + " - " + report.branchName()}
        ), styles);
        row = writeKeyValueBlock(sheet, row + 1, "REPORT DETAILS", List.of(
            new String[]{"Reporting Period", periodLabel(report)},
            new String[]{"Generated On", humanDate(report.generatedOn())},
            new String[]{"Generated By", report.generatedBy()},
            new String[]{"Loan Product", report.loanProductLabel()}
        ), styles);
        row = writeMetricSummary(sheet, row + 1, report, styles);
        row = writeTable(sheet, row + 1, "LOAN STATUS BREAKDOWN",
            new String[]{"Status", "Count", "% of Total"},
            statusRowsWithoutDelta(report), styles);
        row = writeProductTable(sheet, row + 1, report, styles);
        row = writeActiveLoanTable(sheet, row + 1, report, styles);
        row = writeFinancialTable(sheet, row + 1, report, styles);
        writeSignOff(sheet, row + 1, report, styles);
        autosize(sheet, 8);
    }

    private void writeStationSummarySheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), report.title(), styles);
        row = writeReportDetails(sheet, row, report, styles);
        row = writeKeyValueBlock(sheet, row, "SUMMARY", List.of(
            new String[]{"Applications", String.valueOf(report.analytics().appliedLoans())},
            new String[]{"Approved", String.valueOf(report.analytics().disbursedLoans())},
            new String[]{"Disbursed", String.valueOf(report.analytics().disbursedLoans())},
            new String[]{"Active", String.valueOf(report.analytics().activeLoans())},
            new String[]{"Paid", String.valueOf(report.analytics().paidLoans())},
            new String[]{"Rejected", String.valueOf(report.analytics().rejectedLoans())},
            new String[]{"Defaulted", String.valueOf(report.analytics().defaultedLoans())},
            new String[]{"Total Paid Interest Accumulated", moneyPlain(totalProductInterest(report))},
            new String[]{"Risk Level", totalRiskLevel(report)}
        ), styles);
        row = writeTable(sheet, row + 1, "LOAN STATUS ANALYSIS",
            new String[]{"Status", "Count", "% of Total"},
            statusRowsWithoutDelta(report), styles);
        row = writeProductTable(sheet, row + 1, report, styles);
        row = writeProductFinancialBreakdownTable(sheet, row + 1, report, styles);
        row = writeTable(sheet, row + 1, "KEY OBSERVATIONS",
            new String[]{"Observation"},
            report.observations().stream().map(value -> new String[]{value}).toList(), styles);
        writeSignOff(sheet, row + 1, report, styles);
        autosize(sheet, 8);
    }

    private void writeStatusSheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "LOAN STATUS ANALYSIS", styles);
        row = writeReportDetails(sheet, row, report, styles);
        writeTable(sheet, row, "STATUS BREAKDOWN",
            new String[]{"Status", "Count", "% of Total"},
            statusRowsWithoutDelta(report), styles);
        autosize(sheet, 8);
    }

    private void writeProductSheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "PRODUCT PERFORMANCE", styles);
        row = writeReportDetails(sheet, row, report, styles);
        row = writeProductTable(sheet, row, report, styles);
        if (report.kind() != ReportKind.MEMBER) {
            writeProductFinancialBreakdownTable(sheet, row + 1, report, styles);
        }
        autosize(sheet, 8);
    }

    private void writeTrendSheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "TRENDS", styles);
        row = writeReportDetails(sheet, row, report, styles);
        List<String[]> rows = trendTableRows(report);
        int tableStart = row;
        writeTable(sheet, tableStart, "LOAN TREND OVER TIME",
            new String[]{"Period", "Applied", "Active", "Disbursed", "Paid", "Defaulted", "Forfeited", "Rejected"},
            rows, styles);
        makeTrendValuesNumeric(sheet, tableStart, rows.size());
        addTrendChart(sheet, tableStart, rows.size());
        autosize(sheet, 8);
    }

    private void writeFinancialSheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "FINANCIAL SUMMARY", styles);
        row = writeReportDetails(sheet, row, report, styles);
        writeFinancialTable(sheet, row, report, styles);
        autosize(sheet, 5);
    }

    private void writeActivitySheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "ACTIVITY LOG", styles);
        row = writeReportDetails(sheet, row, report, styles);
        writeTable(sheet, row, "RECENT LOAN ACTIVITY",
            new String[]{"Date", "Activity", "Loan Product", "Amount (TZS)", "Status"},
            report.activityRows().stream()
                .map(item -> new String[]{item.date(), item.activity(), item.loanProduct(), item.amount(), item.status()})
                .toList(), styles);
        autosize(sheet, 8);
    }

    private int titleRows(XSSFSheet sheet, String saccoName, String title, ExcelStyles styles) {
        Row row0 = sheet.createRow(0);
        Cell c0 = row0.createCell(0);
        c0.setCellValue(saccoName);
        c0.setCellStyle(styles.title);
        Row row1 = sheet.createRow(1);
        Cell c1 = row1.createCell(0);
        c1.setCellValue(title);
        c1.setCellStyle(styles.subtitle);
        return 2;
    }

    private int writeReportDetails(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        return writeKeyValueBlock(sheet, startRow, "REPORT DETAILS", List.of(
            new String[]{"Reporting Period", periodLabel(report)},
            new String[]{"Generated On", humanDate(report.generatedOn())},
            new String[]{"Name", report.generatedBy()},
            new String[]{"Role", report.generatedByRole()},
            new String[]{"Loan Product", report.loanProductLabel()}
        ), styles);
    }

    private int writeKeyValueBlock(XSSFSheet sheet, int startRow, String title, List<String[]> rows, ExcelStyles styles) {
        Row titleRow = sheet.createRow(startRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.section);
        for (String[] data : rows) {
            Row row = sheet.createRow(startRow++);
            writeCell(row, 0, data[0], styles.header);
            writeCell(row, 1, data.length > 1 ? data[1] : "", styles.body);
        }
        return startRow;
    }

    private int writeMetricSummary(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        return writeKeyValueBlock(sheet, startRow, "LOAN STATUS SUMMARY", List.of(
            new String[]{"Applied Loans", String.valueOf(report.analytics().appliedLoans())},
            new String[]{"Active Loans", String.valueOf(report.analytics().activeLoans())},
            new String[]{"Disbursed Loans", String.valueOf(report.analytics().disbursedLoans())},
            new String[]{"Paid Loans", String.valueOf(report.analytics().paidLoans())},
            new String[]{"Defaulted Loans", String.valueOf(report.analytics().defaultedLoans())},
            new String[]{"Rejected Loans", String.valueOf(report.analytics().rejectedLoans())},
            new String[]{"Forfeited Loan Applications", String.valueOf(report.analytics().forfeitedLoans())}
        ), styles);
    }

    private int writeProductTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        List<String[]> rows = new ArrayList<>();
        long applied = 0;
        long active = 0;
        long disbursed = 0;
        long paid = 0;
        long defaulted = 0;
        long rejected = 0;
        for (ProductPerformanceRow item : report.productRows()) {
            rows.add(new String[]{
                item.label(), String.valueOf(item.applied()), String.valueOf(item.active()),
                String.valueOf(item.disbursed()), String.valueOf(item.paid()),
                String.valueOf(item.defaulted()), String.valueOf(item.rejected()),
                moneyPlain(item.interestPaid())
            });
            applied += item.applied();
            active += item.active();
            disbursed += item.disbursed();
            paid += item.paid();
            defaulted += item.defaulted();
            rejected += item.rejected();
        }
        BigDecimal interestPaid = BigDecimal.ZERO;
        for (ProductPerformanceRow item : report.productRows()) {
            interestPaid = interestPaid.add(item.interestPaid() == null ? BigDecimal.ZERO : item.interestPaid());
        }
        rows.add(new String[]{"TOTAL", String.valueOf(applied), String.valueOf(active), String.valueOf(disbursed), String.valueOf(paid), String.valueOf(defaulted), String.valueOf(rejected), moneyPlain(interestPaid)});
        return writeTable(sheet, startRow, "LOAN PRODUCT PERFORMANCE",
            new String[]{"Loan Product", "Applied", "Active", "Disbursed", "Paid", "Defaulted", "Rejected", report.kind() == ReportKind.STATION ? "Total Paid Interest Accumulated" : "Total Interest Paid"},
            rows, styles);
    }

    private int writeActiveLoanTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        return writeTable(sheet, startRow, "ACTIVE LOAN DETAILS",
            new String[]{"Loan ID", "Loan Product", "Loan Amount", "Outstanding Balance", "Required Interest Amount", "Paid Loan Amount", "Interest Paid", "Interest Not Yet Paid"},
            report.activeLoanRows().stream()
                .map(row -> new String[]{row.loanId(), row.loanProduct(), row.loanAmount(), row.outstandingBalance(), row.requiredInterestAmount(), row.paidLoanAmount(), row.interestPaid(), row.interestNotYetPaid()})
                .toList(), styles);
    }

    private int writeProductFinancialBreakdownTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        return writeTable(sheet, startRow, "LOAN PRODUCT FINANCIAL BREAKDOWN",
            new String[]{"Loan Product", "Total Interest Paid", "Total Interest Unpaid Yet", "Total Loan Amount Paid", "Total Loan Amount Unpaid Yet"},
            productFinancialRowsForExcel(report), styles);
    }

    private int writeFinancialTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        FinancialSummary financial = report.financialSummary();
        return writeKeyValueBlock(sheet, startRow, "LOAN FINANCIAL SUMMARY", List.of(
            new String[]{"Current Active Loan Amount", moneyPlain(financial.activeLoanAmount())},
            new String[]{"Total Disbursed Amount", moneyPlain(financial.totalDisbursedAmount())},
            new String[]{"Total Repaid Amount", moneyPlain(financial.totalRepaidAmount())},
            new String[]{"Outstanding Balance", moneyPlain(financial.outstandingBalance())},
            new String[]{"Defaulted Amount", moneyPlain(financial.defaultedAmount())}
        ), styles);
    }

    private BigDecimal totalProductInterest(AnalyticsExportReport report) {
        return report.productRows().stream()
            .map(ProductPerformanceRow::interestPaid)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<String[]> productFinancialRowsForExcel(AnalyticsExportReport report) {
        List<String[]> rows = report.productFinancialRows().stream()
            .map(row -> new String[]{
                row.loanProduct(),
                moneyPlain(row.totalInterestPaid()),
                moneyPlain(row.totalInterestUnpaid()),
                moneyPlain(row.totalLoanAmountPaid()),
                moneyPlain(row.totalLoanAmountUnpaid())
            })
            .collect(Collectors.toCollection(ArrayList::new));
        BigDecimal interestUnpaid = BigDecimal.ZERO;
        BigDecimal interestPaid = BigDecimal.ZERO;
        BigDecimal loanAmountPaid = BigDecimal.ZERO;
        BigDecimal loanAmountUnpaid = BigDecimal.ZERO;
        for (ProductFinancialBreakdownRow row : report.productFinancialRows()) {
            interestPaid = interestPaid.add(row.totalInterestPaid() == null ? BigDecimal.ZERO : row.totalInterestPaid());
            interestUnpaid = interestUnpaid.add(row.totalInterestUnpaid() == null ? BigDecimal.ZERO : row.totalInterestUnpaid());
            loanAmountPaid = loanAmountPaid.add(row.totalLoanAmountPaid() == null ? BigDecimal.ZERO : row.totalLoanAmountPaid());
            loanAmountUnpaid = loanAmountUnpaid.add(row.totalLoanAmountUnpaid() == null ? BigDecimal.ZERO : row.totalLoanAmountUnpaid());
        }
        rows.add(new String[]{"TOTAL", moneyPlain(interestPaid), moneyPlain(interestUnpaid), moneyPlain(loanAmountPaid), moneyPlain(loanAmountUnpaid)});
        return rows;
    }

    private void writeSignOff(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        writeKeyValueBlock(sheet, startRow, "SIGN-OFF", List.of(
            new String[]{"Prepared By", report.generatedBy()},
            new String[]{"Prepared Date", humanDate(report.generatedOn())},
            new String[]{"Reviewed By", "____________________________"},
            new String[]{"Approved By", "____________________________"}
        ), styles);
    }

    private int writeTable(XSSFSheet sheet, int startRow, String title, String[] headers, List<String[]> rows, ExcelStyles styles) {
        Row titleRow = sheet.createRow(startRow++);
        writeCell(titleRow, 0, title, styles.section);
        Row headerRow = sheet.createRow(startRow++);
        for (int i = 0; i < headers.length; i++) {
            writeCell(headerRow, i, headers[i], styles.header);
        }
        if (rows.isEmpty()) {
            Row empty = sheet.createRow(startRow++);
            writeCell(empty, 0, "No records for the selected period.", styles.body);
            return startRow;
        }
        for (String[] rowValues : rows) {
            Row row = sheet.createRow(startRow++);
            for (int i = 0; i < rowValues.length; i++) {
                writeCell(row, i, rowValues[i], styles.body);
            }
        }
        return startRow;
    }

    private void writeCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void autosize(XSSFSheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private List<String[]> trendTableRows(AnalyticsExportReport report) {
        Map<String, Map<String, Long>> byPeriod = new LinkedHashMap<>();
        for (LoanAnalyticsService.MetricTrendSeries series : report.trendSeries()) {
            for (Map<String, Object> point : series.dataPoints()) {
                String period = trendPeriodLabel(point.get("x"));
                byPeriod.computeIfAbsent(period, key -> new LinkedHashMap<>()).put(series.name(), numberValue(point.get("y")));
            }
        }
        return byPeriod.entrySet().stream()
            .map(entry -> {
                Map<String, Long> values = entry.getValue();
                return new String[]{
                    entry.getKey(),
                    String.valueOf(values.getOrDefault("Applied", 0L)),
                    String.valueOf(values.getOrDefault("Active", 0L)),
                    String.valueOf(values.getOrDefault("Disbursed", 0L)),
                    String.valueOf(values.getOrDefault("Paid", 0L)),
                    String.valueOf(values.getOrDefault("Defaulted", 0L)),
                    String.valueOf(values.getOrDefault("Forfeited", 0L)),
                    String.valueOf(values.getOrDefault("Rejected", 0L))
                };
            })
            .toList();
    }

    private void makeTrendValuesNumeric(XSSFSheet sheet, int tableStartRow, int dataRowCount) {
        int dataStart = tableStartRow + 2;
        int dataEnd = dataStart + dataRowCount - 1;
        for (int rowIndex = dataStart; rowIndex <= dataEnd; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int column = 1; column <= 7; column++) {
                Cell cell = row.getCell(column);
                if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                    String value = cell.getStringCellValue();
                    if (value != null && value.matches("-?\\d+(\\.\\d+)?")) {
                        CellStyle style = cell.getCellStyle();
                        cell.setCellValue(Double.parseDouble(value));
                        cell.setCellStyle(style);
                    }
                }
            }
        }
    }

    private void addTrendChart(XSSFSheet sheet, int tableStartRow, int dataRowCount) {
        if (dataRowCount <= 0) {
            return;
        }
        int dataStart = tableStartRow + 2;
        int dataEnd = dataStart + dataRowCount - 1;
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, dataEnd + 3, 8, dataEnd + 21);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Loan Trend Over Time");
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.TOP);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("Period");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Loans");
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String> periods = XDDFDataSourcesFactory.fromStringCellRange(
            sheet, new CellRangeAddress(dataStart, dataEnd, 0, 0));
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        addTrendSeries(data, periods, sheet, dataStart, dataEnd, 1, "Applied");
        addTrendSeries(data, periods, sheet, dataStart, dataEnd, 3, "Disbursed");
        addTrendSeries(data, periods, sheet, dataStart, dataEnd, 4, "Paid");
        addTrendSeries(data, periods, sheet, dataStart, dataEnd, 5, "Defaulted");
        chart.plot(data);
    }

    private void addTrendSeries(XDDFChartData data,
                                XDDFDataSource<String> periods,
                                XSSFSheet sheet,
                                int dataStart,
                                int dataEnd,
                                int column,
                                String title) {
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, new CellRangeAddress(dataStart, dataEnd, column, column));
        XDDFChartData.Series series = data.addSeries(periods, values);
        series.setTitle(title, null);
        if (series instanceof XDDFLineChartData.Series lineSeries) {
            lineSeries.setSmooth(false);
        }
    }

    private List<String[]> statusRowsWithoutDelta(AnalyticsExportReport report) {
        return statusRows(report);
    }

    private String trendPeriodLabel(Object value) {
        if (value instanceof Number number) {
            return YearMonth.from(java.time.Instant.ofEpochMilli(number.longValue()).atZone(ZoneOffset.UTC)).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH));
        }
        return String.valueOf(value);
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
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
            case FINAL_APPROVED -> "Final Approved and Disbursed";
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
        }

        Map<UUID, Member> memberMap = memberRepository.findAllById(memberIds).stream()
            .collect(Collectors.toMap(Member::getId, item -> item));

        return loans.stream()
            .map(loan -> buildMemberLoanDetail(
                member,
                loan,
                guarantorsByLoan.getOrDefault(loan.getId(), List.of()),
                boardReviewsByLoan.getOrDefault(loan.getId(), List.of()),
                memberMap
            ))
            .toList();
    }

    private MemberLoanDetail buildMemberLoanDetail(Member member,
                                                   LoanApplication loan,
                                                   List<GuarantorRequest> guarantorRequests,
                                                   List<BoardReview> boardReviews,
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
            formatApprovalSummary(boardReviews, memberMap),
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

    private String formatApprovalSummary(List<BoardReview> boardReviews,
                                         Map<UUID, Member> memberMap) {
        if (boardReviews != null && !boardReviews.isEmpty()) {
            return boardReviews.stream()
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
        }
        return "No board committee decision details available";
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
            case FINAL_APPROVED -> "Final Approved and Disbursed";
            case DEFAULTED -> "Defaulted / Not Paid";
            case PAID -> "Paid";
        };
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

    private static String sanitizePdfLineText(String text) {
        String sanitized = sanitizePdfText(text).replaceAll("\\p{Cntrl}+", " ").trim();
        return sanitized.isEmpty() ? "-" : sanitized;
    }

    public enum ReportKind {
        MEMBER,
        STATION,
        STAFF
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {}

    private record ExportContext(
        String saccoName,
        String stationId,
        String branchName,
        LocalDate fromDate,
        LocalDate toDate,
        String loanProductLabel
    ) {}

    private record PaymentTotals(
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal totalPaid
    ) {
        private static final PaymentTotals ZERO = new PaymentTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        private PaymentTotals plus(PaymentTotals other) {
            if (other == null) {
                return this;
            }
            return new PaymentTotals(
                principalPaid.add(other.principalPaid == null ? BigDecimal.ZERO : other.principalPaid),
                interestPaid.add(other.interestPaid == null ? BigDecimal.ZERO : other.interestPaid),
                totalPaid.add(other.totalPaid == null ? BigDecimal.ZERO : other.totalPaid)
            );
        }
    }

    public record ProductPerformanceRow(
        String label,
        long applied,
        long active,
        long disbursed,
        long paid,
        long defaulted,
        long rejected,
        BigDecimal interestPaid
    ) {
        public String getInterestPaidLabel() {
            return formatMoneyPlainStatic(interestPaid);
        }
    }

    public record ProductFinancialBreakdownRow(
        String loanProduct,
        BigDecimal totalInterestPaid,
        BigDecimal totalInterestUnpaid,
        BigDecimal totalLoanAmountPaid,
        BigDecimal totalLoanAmountUnpaid
    ) {
        public String getLoanProduct() {
            return loanProduct;
        }

        public String getTotalInterestPaidLabel() {
            return formatMoneyPlainStatic(totalInterestPaid);
        }

        public String getTotalInterestUnpaidLabel() {
            return formatMoneyPlainStatic(totalInterestUnpaid);
        }

        public String getTotalLoanAmountPaidLabel() {
            return formatMoneyPlainStatic(totalLoanAmountPaid);
        }

        public String getTotalLoanAmountUnpaidLabel() {
            return formatMoneyPlainStatic(totalLoanAmountUnpaid);
        }
    }

    public record ActiveLoanDetailRow(
        String loanId,
        String loanProduct,
        String loanAmount,
        String outstandingBalance,
        String requiredInterestAmount,
        String paidLoanAmount,
        String interestPaid,
        String interestNotYetPaid
    ) {
        public String getLoanId() {
            return loanId;
        }

        public String getLoanProduct() {
            return loanProduct;
        }

        public String getLoanAmount() {
            return loanAmount;
        }

        public String getOutstandingBalance() {
            return outstandingBalance;
        }

        public String getRequiredInterestAmount() {
            return requiredInterestAmount;
        }

        public String getPaidLoanAmount() {
            return paidLoanAmount;
        }

        public String getInterestPaid() {
            return interestPaid;
        }

        public String getInterestNotYetPaid() {
            return interestNotYetPaid;
        }
    }

    public record FinancialSummary(
        BigDecimal activeLoanAmount,
        BigDecimal totalDisbursedAmount,
        BigDecimal totalRepaidAmount,
        BigDecimal outstandingBalance,
        BigDecimal defaultedAmount
    ) {}

    public record ActivityRow(
        String date,
        String activity,
        String loanProduct,
        String amount,
        String status
    ) {}

    public record AnalyticsExportReport(
        ReportKind kind,
        String saccoName,
        String title,
        String stationId,
        String branchName,
        LocalDate fromDate,
        LocalDate toDate,
        LocalDate generatedOn,
        String generatedBy,
        String generatedByRole,
        String loanProductLabel,
        Member member,
        LoanAnalyticsService.MemberLoanAnalytics analytics,
        LoanAnalyticsService.MemberLoanAnalytics previousAnalytics,
        Map<String, LoanAnalyticsService.MetricDelta> deltaByKey,
        LoanAnalyticsService.StaffPortfolioSummary portfolioSummary,
        List<ProductPerformanceRow> productRows,
        List<ProductFinancialBreakdownRow> productFinancialRows,
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries,
        List<ActivityRow> activityRows,
        List<ActiveLoanDetailRow> activeLoanRows,
        FinancialSummary financialSummary,
        List<String> observations,
        String remarks
    ) {}

    private static final class ExcelStyles {
        private final CellStyle title;
        private final CellStyle subtitle;
        private final CellStyle section;
        private final CellStyle header;
        private final CellStyle body;

        private ExcelStyles(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title = workbook.createCellStyle();
            title.setFont(titleFont);

            Font subtitleFont = workbook.createFont();
            subtitleFont.setBold(true);
            subtitleFont.setFontHeightInPoints((short) 11);
            subtitle = workbook.createCellStyle();
            subtitle.setFont(subtitleFont);

            Font sectionFont = workbook.createFont();
            sectionFont.setBold(true);
            section = bordered(workbook);
            section.setFont(sectionFont);
            section.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            header = bordered(workbook);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            header.setFont(headerFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);

            body = bordered(workbook);
            body.setVerticalAlignment(VerticalAlignment.CENTER);
        }

        private static CellStyle bordered(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setWrapText(true);
            return style;
        }
    }

    private final class FormalPdf {
        private static final float MARGIN = 28f;
        private static final float SMALL = 7.2f;
        private static final float BODY = 8.2f;
        private static final float HEADER = 10f;
        private static final float TITLE = 16f;

        private final PDDocument document;
        private final AnalyticsExportReport report;
        private final PDRectangle size;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private FormalPdf(PDDocument document, AnalyticsExportReport report, PDRectangle size) {
            this.document = document;
            this.report = report;
            this.size = size;
        }

        private void renderMemberOnePage() throws IOException {
            open();
            float width = page.getMediaBox().getWidth();
            write(report.saccoName(), MARGIN + 52, y, bold, TITLE);
            write("LOAN APPLICATION PORTAL", MARGIN + 52, y - 16, bold, HEADER);
            right(report.title(), width - MARGIN, y, bold, TITLE);
            right("Loan Reports and Analytics", width - MARGIN, y - 18, regular, HEADER + 1);
            line(MARGIN, y - 30, width - MARGIN, y - 30);
            y -= 48;

            float third = (width - (MARGIN * 2)) / 3f;
            drawInfoPanel(MARGIN, y, third - 8, "MEMBER INFORMATION", List.of(
                new String[]{"Member Name", report.member().getFullName()},
                new String[]{"Member Number", report.member().getMemberNo()},
                new String[]{"Station", report.stationId() + " - " + report.branchName()}
            ));
            drawInfoPanel(MARGIN + third, y, third - 8, "REPORT DETAILS", List.of(
                new String[]{"Reporting Period", periodLabel(report)},
                new String[]{"Generated On", humanDate(report.generatedOn())},
                new String[]{"Generated By", report.generatedBy()}
            ));
            drawInfoPanel(MARGIN + (third * 2), y, third - 8, "REPORT SUMMARY", List.of(
                new String[]{"Loan Product", report.loanProductLabel()},
                new String[]{"Report Type", "Member Loan Summary"}
            ));
            y -= 88;

            section("1. LOAN STATUS SUMMARY");
            metricBoxes(MARGIN, y, width - (MARGIN * 2));
            y -= 78;

            float leftWidth = (width - (MARGIN * 2) - 18) * 0.44f;
            float rightWidth = (width - (MARGIN * 2) - 18) - leftWidth;
            float tableTop = y;
            sectionAt(MARGIN, tableTop, "2. LOAN STATUS BREAKDOWN");
            table(MARGIN, tableTop - 16, new float[]{leftWidth * .45f, leftWidth * .22f, leftWidth * .33f},
                new String[]{"Status", "Count", "% of Total"},
                statusRows(report), SMALL, 15f);
            sectionAt(MARGIN + leftWidth + 18, tableTop, "3. LOAN PRODUCT PERFORMANCE");
            table(MARGIN + leftWidth + 18, tableTop - 16,
                new float[]{rightWidth * .34f, rightWidth * .13f, rightWidth * .13f, rightWidth * .15f, rightWidth * .12f, rightWidth * .13f},
                new String[]{"Loan Product", "Applied", "Active", "Disbursed", "Paid", "Rejected"},
                report.productRows().stream()
                    .map(row -> new String[]{row.label(), String.valueOf(row.applied()), String.valueOf(row.active()), String.valueOf(row.disbursed()), String.valueOf(row.paid()), String.valueOf(row.rejected())})
                    .toList(), SMALL, 15f);
            y -= 136;

            section("4. RISK / ELIGIBILITY SUMMARY");
            table(MARGIN, y, new float[]{145, 90, 145, 90, 90, 90},
                new String[]{"Current Active Loan Amount", "Defaulted Loans", "Forfeited Loan Applications", "Total Guaranteed Amount", "Can Apply", "Can Guarantee"},
                List.of(new String[]{moneyPlain(report.financialSummary().activeLoanAmount()), String.valueOf(report.analytics().defaultedLoans()), String.valueOf(report.analytics().forfeitedLoans()), "0.00", "Yes", "Yes"},
                    new String[]{"Eligibility Reason", report.remarks(), "", "", "", ""}), SMALL, 16f);
            y -= 60;

            section("5. LOAN TREND OVER TIME");
            drawTrendChart(MARGIN, y, width - (MARGIN * 2), 105);
            y -= 125;

            float half = (width - (MARGIN * 2) - 18) / 2f;
            sectionAt(MARGIN, y, "6. LOAN FINANCIAL SUMMARY");
            table(MARGIN, y - 16, new float[]{half * .62f, half * .38f}, new String[]{"Item", "Amount (TZS)"}, financialRows(), SMALL, 15f);
            sectionAt(MARGIN + half + 18, y, "7. RECENT LOAN ACTIVITY");
            table(MARGIN + half + 18, y - 16, new float[]{58, 94, 95, 70, half - 317}, new String[]{"Date", "Activity", "Loan Product", "Amount", "Status"}, activityRows(), SMALL, 15f);
            y -= 96;
            sectionAt(MARGIN, y, "8. REMARKS");
            box(MARGIN, y - 64, half, 58);
            writeWrapped(report.remarks(), MARGIN + 8, y - 20, half - 16, SMALL);
            sectionAt(MARGIN + half + 18, y, "9. SIGN-OFF");
            signOff(MARGIN + half + 18, y - 18, half);
            close(1, 1, "Member Loan Report");
        }

        private void renderMemberTwoPage() throws IOException {
            open();
            memberHeader(false);
            section("1. MEMBER INFORMATION");
            table(MARGIN, y, new float[]{170, 250}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Member Name", report.member().getFullName()},
                new String[]{"Member Number", report.member().getMemberNo()},
                new String[]{"Station", report.stationId() + " - " + report.branchName()}
            ), BODY, 16f);
            y -= 80;
            section("2. REPORT DETAILS");
            table(MARGIN, y, new float[]{170, 250}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Reporting Period", periodLabel(report)},
                new String[]{"Generated On", humanDate(report.generatedOn())},
                new String[]{"Generated By", report.generatedBy()},
                new String[]{"Report Type", "Member Loan Summary"},
                new String[]{"Loan Product", report.loanProductLabel()}
            ), BODY, 16f);
            y -= 112;
            section("3. LOAN STATUS SUMMARY");
            table(MARGIN, y, new float[]{220, 120}, new String[]{"Metric", "Count"}, metricRows().stream()
                .map(row -> new String[]{row[0], row[1]})
                .toList(), BODY, 17f);
            y -= 170;
            section("5. LOAN PRODUCT PERFORMANCE");
            table(MARGIN, y, new float[]{120, 50, 50, 60, 45, 55, 55, 88},
                new String[]{"Loan Product", "Applied", "Active", "Disbursed", "Paid", "Defaulted", "Rejected", "Total Interest Paid"},
                productRowsForPdf(false), SMALL, 16f);
            close(1, 2, "Member Loan Report");

            open();
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y, regular, SMALL);
            y -= 34;
            section("6. RISK / ELIGIBILITY SUMMARY");
            table(MARGIN, y, new float[]{118, 72, 128, 112, 45, 50},
                new String[]{"Current Active Loan Amount", "Defaulted Loans", "Forfeited Loan Applications", "Total Guaranteed Amount", "Can Apply", "Can Guarantee"},
                java.util.Collections.singletonList(new String[]{
                    moneyPlain(report.financialSummary().activeLoanAmount()),
                    String.valueOf(report.analytics().defaultedLoans()),
                    String.valueOf(report.analytics().forfeitedLoans()),
                    "0.00",
                    "Yes",
                    "Yes"
                }), SMALL, 24f);
            y -= 72;
            section("7. LOAN TREND OVER TIME");
            drawTrendChart(MARGIN, y, page.getMediaBox().getWidth() - (MARGIN * 2), 150);
            y -= 175;
            section("8. ACTIVE LOAN DETAILS");
            table(MARGIN, y, new float[]{46, 88, 65, 78, 78, 64, 58, 58},
                new String[]{"Loan ID", "Loan Product", "Loan Amount", "Outstanding Balance", "Required Interest", "Paid Loan Amount", "Interest Paid", "Remaining Interest"},
                activeLoanRowsForPdf(), SMALL, 22f);
            y -= 112;
            float half = (page.getMediaBox().getWidth() - (MARGIN * 2)) / 2f;
            box(MARGIN, y - 118, half - 8, 110);
            sectionAt(MARGIN + 10, y - 20, "10. REMARKS");
            writeWrapped(report.remarks(), MARGIN + 10, y - 38, half - 26, SMALL);
            box(MARGIN + half + 8, y - 118, half - 8, 110);
            sectionAt(MARGIN + half + 18, y - 20, "11. SIGN-OFF");
            signOff(MARGIN + half + 18, y - 38, half - 18);
            close(2, 2, "Member Loan Report");
        }

        private void renderStationTwoPage() throws IOException {
            open();
            center(report.saccoName(), page.getMediaBox().getWidth() / 2, y, bold, TITLE);
            center(report.title(), page.getMediaBox().getWidth() / 2, y - 18, bold, HEADER + 2);
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y - 8, regular, SMALL);
            line(MARGIN, y - 34, page.getMediaBox().getWidth() - MARGIN, y - 34);
            y -= 58;
            stationSection("1. MEMBER INFORMATION");
            y = table(MARGIN, y, new float[]{120, 235}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Station", report.stationId()},
                new String[]{"Name", report.generatedBy()},
                new String[]{"Role", report.generatedByRole()}
            ), BODY, 17f);
            y -= 20;
            stationSection("2. REPORT DETAILS");
            y = table(MARGIN, y, new float[]{120, 235}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Reporting Period", periodLabel(report)},
                new String[]{"Generated On", humanDate(report.generatedOn())},
                new String[]{"Name", report.generatedBy()},
                new String[]{"Role", report.generatedByRole()},
                new String[]{"Report Type", report.kind() == ReportKind.STAFF ? "Staff Loan Analytics" : "Station Loan Summary"},
                new String[]{"Loan Product", report.loanProductLabel()}
            ), BODY, 17f);
            y -= 20;
            stationSection("3. LOAN STATUS SUMMARY");
            y = table(MARGIN, y, new float[]{190, 75, 120}, new String[]{"Metric", "Count", "% of Total Applicants"}, statusRows(report).stream()
                .map(row -> new String[]{row[0], row[1], row[2]})
                .toList(), SMALL, 14f);
            y -= 20;
            stationSection("4. LOAN PRODUCT PERFORMANCE");
            y = table(MARGIN, y, new float[]{146, 54, 48, 52, 38, 48, 45, 84},
                new String[]{"Loan Product", "Applications", "Approved", "Disbursed", "Paid", "Defaulted", "Rejected", "Total Paid Interest Accumulated"},
                productRowsForPdf(true), SMALL - 0.4f, 22f);
            close(1, 2, "Station Loan Status Report");

            open();
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y, regular, SMALL);
            y -= 30;
            stationSection("5. PORTFOLIO SUMMARY");
            y = table(MARGIN, y, new float[]{260, 120}, new String[]{"Indicator", "Value"}, portfolioRows(), BODY, 14f);
            y -= 20;
            stationSection("6. TREND ANALYSIS");
            y = table(MARGIN, y, new float[]{70, 75, 75, 70, 70}, new String[]{"Year", "Applied", "Disbursed", "Paid", "Defaulted"}, yearlyTrendRows(), BODY, 14f);
            y -= 18;
            drawTrendChart(MARGIN, y, page.getMediaBox().getWidth() - (MARGIN * 2), 108);
            y -= 128;
            stationSection("7. LOAN PRODUCT FINANCIAL BREAKDOWN");
            y = table(MARGIN, y, new float[]{140, 85, 100, 95, 95},
                new String[]{"Loan Product", "Total Interest Paid", "Total Interest Unpaid Yet", "Total Loan Amount Paid", "Total Loan Amount Unpaid Yet"},
                productFinancialRowsForPdf(), SMALL - 0.5f, 22f);
            y -= 20;
            float half = (page.getMediaBox().getWidth() - (MARGIN * 2)) / 2f;
            box(MARGIN, y - 120, half - 8, 112);
            sectionAt(MARGIN + 10, y - 22, "8. REMARKS");
            writeWrapped(report.remarks(), MARGIN + 10, y - 42, half - 26, SMALL);
            box(MARGIN + half + 8, y - 120, half - 8, 112);
            sectionAt(MARGIN + half + 18, y - 22, "9. SIGN-OFF");
            signOff(MARGIN + half + 18, y - 42, half - 18);
            close(2, 2, "Station Loan Status Report");
        }

        private void memberHeader(boolean compact) throws IOException {
            write(report.saccoName(), MARGIN, y, bold, TITLE);
            write("MEMBER LOAN REPORT", MARGIN, y - 18, bold, HEADER + 2);
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y - 8, regular, SMALL);
            line(MARGIN, y - 34, page.getMediaBox().getWidth() - MARGIN, y - 34);
            y -= compact ? 44 : 58;
        }

        private List<String[]> metricRows() {
            return List.of(
                new String[]{"Applied Loans", String.valueOf(report.analytics().appliedLoans())},
                new String[]{"Active Loans", String.valueOf(report.analytics().activeLoans())},
                new String[]{"Disbursed Loans", String.valueOf(report.analytics().disbursedLoans())},
                new String[]{"Paid Loans", String.valueOf(report.analytics().paidLoans())},
                new String[]{"Defaulted Loans", String.valueOf(report.analytics().defaultedLoans())},
                new String[]{"Rejected Loans", String.valueOf(report.analytics().rejectedLoans())},
                new String[]{"Forfeited Loan Applications", String.valueOf(report.analytics().forfeitedLoans())}
            );
        }

        private List<String[]> executiveRows() {
            return List.of(
                new String[]{"Loan Applications Received", String.valueOf(report.analytics().appliedLoans())},
                new String[]{"Loans Approved", String.valueOf(report.analytics().disbursedLoans())},
                new String[]{"Loans Disbursed", String.valueOf(report.analytics().disbursedLoans())},
                new String[]{"Active Loans", String.valueOf(report.analytics().activeLoans())},
                new String[]{"Paid Loans", String.valueOf(report.analytics().paidLoans())},
                new String[]{"Defaulted Loans", String.valueOf(report.analytics().defaultedLoans())},
                new String[]{"Rejected Loans", String.valueOf(report.analytics().rejectedLoans())},
                new String[]{"Forfeited Applications", String.valueOf(report.analytics().forfeitedLoans())},
                new String[]{"Portfolio Risk Level", totalRiskLevel(report)}
            );
        }

        private List<String[]> portfolioRows() {
            LoanAnalyticsService.StaffPortfolioSummary portfolio = report.portfolioSummary();
            return List.of(
                new String[]{"Loans Handled", String.valueOf(portfolio.handledLoans())},
                new String[]{"Loans Approved", String.valueOf(portfolio.approvedLoans())},
                new String[]{"Loans Disbursed", String.valueOf(portfolio.disbursedLoans())},
                new String[]{"Loans Rejected", String.valueOf(portfolio.rejectedLoans())},
                new String[]{"Defaulted After Approval", String.valueOf(portfolio.defaultedAfterApproval())},
                new String[]{"Default Rate", portfolio.defaultedAfterApprovalRate() + "%"},
                new String[]{"Portfolio Risk Rating", portfolio.riskLevel()}
            );
        }

        private List<String[]> financialRows() {
            FinancialSummary financial = report.financialSummary();
            return List.of(
                new String[]{"Current Active Loan Amount", moneyPlain(financial.activeLoanAmount())},
                new String[]{"Total Disbursed Amount", moneyPlain(financial.totalDisbursedAmount())},
                new String[]{"Total Repaid Amount", moneyPlain(financial.totalRepaidAmount())},
                new String[]{"Outstanding Balance", moneyPlain(financial.outstandingBalance())},
                new String[]{"Defaulted Amount", moneyPlain(financial.defaultedAmount())}
            );
        }

        private List<String[]> activityRows() {
            return report.activityRows().stream()
                .map(row -> new String[]{row.date(), row.activity(), row.loanProduct(), row.amount(), row.status()})
                .toList();
        }

        private List<String[]> productRowsForPdf(boolean stationReport) {
            List<String[]> rows = new ArrayList<>();
            long applied = 0;
            long active = 0;
            long disbursed = 0;
            long paid = 0;
            long defaulted = 0;
            long rejected = 0;
            BigDecimal interest = BigDecimal.ZERO;
            for (ProductPerformanceRow row : report.productRows()) {
                rows.add(new String[]{
                    row.label(),
                    String.valueOf(row.applied()),
                    String.valueOf(row.active()),
                    String.valueOf(row.disbursed()),
                    String.valueOf(row.paid()),
                    String.valueOf(row.defaulted()),
                    String.valueOf(row.rejected()),
                    moneyPlain(row.interestPaid())
                });
                applied += row.applied();
                active += row.active();
                disbursed += row.disbursed();
                paid += row.paid();
                defaulted += row.defaulted();
                rejected += row.rejected();
                interest = interest.add(row.interestPaid() == null ? BigDecimal.ZERO : row.interestPaid());
            }
            rows.add(new String[]{
                "TOTAL",
                String.valueOf(applied),
                stationReport ? String.valueOf(active) : String.valueOf(active),
                String.valueOf(disbursed),
                String.valueOf(paid),
                String.valueOf(defaulted),
                String.valueOf(rejected),
                moneyPlain(interest)
            });
            return rows;
        }

        private List<String[]> activeLoanRowsForPdf() {
            return report.activeLoanRows().stream()
                .map(row -> new String[]{
                    row.loanId(),
                    row.loanProduct(),
                    row.loanAmount(),
                    row.outstandingBalance(),
                    row.requiredInterestAmount(),
                    row.paidLoanAmount(),
                    row.interestPaid(),
                    row.interestNotYetPaid()
                })
                .toList();
        }

        private List<String[]> productFinancialRowsForPdf() {
            List<String[]> rows = productFinancialRowsForExcel(report);
            return rows.size() > 7 ? rows.subList(0, 7) : rows;
        }

        private List<String[]> yearlyTrendRows() {
            Map<String, long[]> years = new LinkedHashMap<>();
            for (String[] row : trendTableRows(report)) {
                String year = row[0].length() >= 4 ? row[0].substring(row[0].length() - 4) : row[0];
                long[] values = years.computeIfAbsent(year, key -> new long[4]);
                values[0] += Long.parseLong(row[1]);
                values[1] += Long.parseLong(row[3]);
                values[2] += Long.parseLong(row[4]);
                values[3] += Long.parseLong(row[5]);
            }
            return years.entrySet().stream()
                .map(entry -> new String[]{entry.getKey(), String.valueOf(entry.getValue()[0]), String.valueOf(entry.getValue()[1]), String.valueOf(entry.getValue()[2]), String.valueOf(entry.getValue()[3])})
                .toList();
        }

        private void open() throws IOException {
            page = new PDPage(size);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void close(int pageNumber, int pageCount, String footerTitle) throws IOException {
            line(MARGIN, 36, page.getMediaBox().getWidth() - MARGIN, 36);
            write(report.saccoName() + "  |  " + footerTitle, MARGIN, 22, regular, SMALL);
            right("Page " + pageNumber + " of " + pageCount, page.getMediaBox().getWidth() - MARGIN, 22, regular, SMALL);
            stream.close();
        }

        private void section(String title) throws IOException {
            write(title, MARGIN, y, bold, HEADER);
            y -= 17;
        }

        private void stationSection(String title) throws IOException {
            write(title, MARGIN, y, bold, HEADER);
            y -= 12;
        }

        private void sectionAt(float x, float yPos, String title) throws IOException {
            write(title, x, yPos, bold, HEADER);
        }

        private void metricBoxes(float x, float yPos, float width) throws IOException {
            List<String[]> rows = metricRows();
            float gap = 12f;
            float boxWidth = (width - (gap * (rows.size() - 1))) / rows.size();
            for (int i = 0; i < rows.size(); i++) {
                float boxX = x + (i * (boxWidth + gap));
                box(boxX, yPos - 52, boxWidth, 48);
                center(rows.get(i)[0].toUpperCase(Locale.ENGLISH), boxX + boxWidth / 2, yPos - 14, bold, SMALL);
                center(rows.get(i)[1], boxX + boxWidth / 2, yPos - 31, bold, TITLE);
            }
        }

        private void drawInfoPanel(float x, float top, float width, String title, List<String[]> rows) throws IOException {
            box(x, top - 70, width, 66);
            write(title, x + 8, top - 16, bold, BODY);
            float rowY = top - 34;
            for (String[] row : rows) {
                write(row[0] + " :", x + 8, rowY, bold, SMALL);
                write(row[1], x + 86, rowY, regular, SMALL);
                rowY -= 16;
            }
        }

        private float table(float x, float top, float[] widths, String[] headers, List<String[]> rows, float fontSize, float rowHeight) throws IOException {
            float tableWidth = 0;
            for (float width : widths) {
                tableWidth += width;
            }
            drawRow(x, top, widths, headers, bold, fontSize, rowHeight);
            float rowY = top - rowHeight;
            List<String[]> safeRows = rows.isEmpty() ? java.util.Collections.singletonList(new String[]{"No records for the selected period."}) : rows;
            for (String[] row : safeRows) {
                drawRow(x, rowY, widths, row, regular, fontSize, rowHeight);
                rowY -= rowHeight;
            }
            return rowY;
        }

        private void drawRow(float x, float top, float[] widths, String[] values, PDType1Font font, float fontSize, float rowHeight) throws IOException {
            float cursor = x;
            for (int i = 0; i < widths.length; i++) {
                box(cursor, top - rowHeight, widths[i], rowHeight);
                String value = i < values.length ? sanitizePdfLineText(values[i]) : "";
                writeCellText(value, cursor + 4, top, widths[i] - 8, rowHeight, font, fontSize);
                cursor += widths[i];
            }
        }

        private void writeCellText(String text, float x, float top, float maxWidth, float rowHeight, PDType1Font font, float size) throws IOException {
            String safe = sanitizePdfLineText(text);
            if (font.getStringWidth(safe) / 1000f * size <= maxWidth || !safe.contains(" ")) {
                writeClipped(safe, x, top - rowHeight + 5, maxWidth, font, size);
                return;
            }
            List<String> lines = wrapText(safe, font, size, maxWidth);
            if (lines.size() <= 1) {
                writeClipped(safe, x, top - rowHeight + 5, maxWidth, font, size);
                return;
            }
            float firstY = top - 7;
            int count = Math.min(2, lines.size());
            for (int line = 0; line < count; line++) {
                writeClipped(lines.get(line), x, firstY - (line * (size + 2)), maxWidth, font, Math.max(6f, size - 0.4f));
            }
        }

        private void drawTrendChart(float x, float top, float width, float height) throws IOException {
            box(x, top - height, width, height);
            float chartX = x + 28;
            float chartY = top - height + 34;
            float chartWidth = width - 52;
            float chartHeight = height - 64;
            line(chartX, chartY, chartX + chartWidth, chartY);
            line(chartX, chartY, chartX, chartY + chartHeight);
            long max = 1;
            for (LoanAnalyticsService.MetricTrendSeries series : report.trendSeries()) {
                for (Map<String, Object> point : series.dataPoints()) {
                    max = Math.max(max, numberValue(point.get("y")));
                }
            }
            write("0", chartX - 12, chartY - 2, regular, SMALL);
            write(String.valueOf(max), chartX - 12, chartY + chartHeight - 2, regular, SMALL);
            List<Map<String, Object>> labelPoints = report.trendSeries().stream()
                .filter(series -> !series.dataPoints().isEmpty())
                .findFirst()
                .map(LoanAnalyticsService.MetricTrendSeries::dataPoints)
                .orElse(List.of());
            for (int i = 0; i < labelPoints.size(); i++) {
                if (labelPoints.size() > 6 && i != 0 && i != labelPoints.size() - 1 && i % 2 != 0) {
                    continue;
                }
                float labelX = chartX + chartWidth * i / Math.max(labelPoints.size() - 1, 1);
                center(shortTrendLabel(labelPoints.get(i).get("x")), labelX, chartY - 14, regular, SMALL);
            }
            int seriesIndex = 0;
            int visibleSeries = Math.min(4, report.trendSeries().size());
            float legendStep = visibleSeries <= 1 ? 0 : Math.max(42f, (width - 72f) / visibleSeries);
            for (LoanAnalyticsService.MetricTrendSeries series : report.trendSeries()) {
                if (series.dataPoints().isEmpty() || seriesIndex > 3) {
                    continue;
                }
                Color color = seriesColor(series, seriesIndex);
                stream.setStrokingColor(color);
                stream.setLineWidth(seriesIndex == 0 ? 1.1f : 0.7f);
                List<Map<String, Object>> points = series.dataPoints();
                for (int i = 1; i < points.size(); i++) {
                    float x1 = chartX + chartWidth * (i - 1) / Math.max(points.size() - 1, 1);
                    float y1 = chartY + chartHeight * numberValue(points.get(i - 1).get("y")) / max;
                    float x2 = chartX + chartWidth * i / Math.max(points.size() - 1, 1);
                    float y2 = chartY + chartHeight * numberValue(points.get(i).get("y")) / max;
                    line(x1, y1, x2, y2);
                }
                float legendX = x + 42 + (seriesIndex * legendStep);
                line(legendX - 14, top - 12, legendX - 4, top - 12);
                stream.setNonStrokingColor(color);
                write(series.name(), legendX, top - 14, regular, SMALL);
                stream.setNonStrokingColor(Color.BLACK);
                seriesIndex++;
            }
            stream.setStrokingColor(Color.BLACK);
        }

        private Color seriesColor(LoanAnalyticsService.MetricTrendSeries series, int fallbackIndex) {
            String color = series.color();
            if (color != null && color.matches("#[0-9a-fA-F]{6}")) {
                return Color.decode(color);
            }
            return switch (fallbackIndex) {
                case 0 -> new Color(37, 99, 235);
                case 1 -> new Color(5, 150, 105);
                case 2 -> new Color(124, 58, 237);
                case 3 -> new Color(220, 38, 38);
                default -> Color.DARK_GRAY;
            };
        }

        private String shortTrendLabel(Object value) {
            String label = trendPeriodLabel(value);
            return label != null && label.length() >= 4 ? label.substring(label.length() - 4) : valueOrDash(label);
        }

        private void signOff(float x, float top, float width) throws IOException {
            write("Prepared By:", x, top, regular, BODY);
            line(x + 70, top - 2, x + width - 20, top - 2);
            write(report.generatedBy(), x + 74, top + 2, regular, SMALL);
            write("Date: " + humanDate(report.generatedOn()), x + width - 110, top - 18, regular, SMALL);
            write("Reviewed By:", x, top - 32, regular, BODY);
            line(x + 70, top - 34, x + width - 20, top - 34);
            write("Approved By:", x, top - 58, regular, BODY);
            line(x + 70, top - 60, x + width - 20, top - 60);
        }

        private void box(float x, float bottom, float width, float height) throws IOException {
            stream.addRect(x, bottom, width, height);
            stream.stroke();
        }

        private void line(float x1, float y1, float x2, float y2) throws IOException {
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        }

        private void write(String text, float x, float yPos, PDType1Font font, float size) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, yPos);
            stream.showText(sanitizePdfLineText(text));
            stream.endText();
        }

        private void center(String text, float x, float yPos, PDType1Font font, float size) throws IOException {
            String safe = sanitizePdfLineText(text);
            float width = font.getStringWidth(safe) / 1000f * size;
            write(safe, x - width / 2f, yPos, font, size);
        }

        private void right(String text, float x, float yPos, PDType1Font font, float size) throws IOException {
            String safe = sanitizePdfLineText(text);
            float width = font.getStringWidth(safe) / 1000f * size;
            write(safe, x - width, yPos, font, size);
        }

        private void writeClipped(String text, float x, float yPos, float maxWidth, PDType1Font font, float size) throws IOException {
            String safe = sanitizePdfLineText(text);
            while (!safe.isEmpty() && font.getStringWidth(safe) / 1000f * size > maxWidth) {
                safe = safe.substring(0, safe.length() - 1);
            }
            write(safe, x, yPos, font, size);
        }

        private void writeWrapped(String text, float x, float yPos, float maxWidth, float size) throws IOException {
            List<String> lines = wrapText(sanitizePdfLineText(text), regular, size, maxWidth);
            float cursor = yPos;
            for (String line : lines.stream().limit(3).toList()) {
                write(line, x, cursor, regular, size);
                cursor -= size + 4;
            }
        }

        private List<String> wrapText(String text, PDType1Font font, float fontSize, float width) throws IOException {
            if (text == null || text.isBlank()) {
                return List.of("");
            }
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (font.getStringWidth(candidate) / 1000f * fontSize > width && !current.isEmpty()) {
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

    private static String formatMoneyPlainStatic(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(Locale.US));
        return format.format(safe);
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
            stream.showText(sanitizePdfLineText(text));
            stream.endText();
        }

        private void writeRightAligned(String text,
                                       float rightX,
                                       float baselineY,
                                       PDType1Font font,
                                       float fontSize,
                                       Color color) throws IOException {
            float width = stringWidth(sanitizePdfLineText(text), font, fontSize);
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
