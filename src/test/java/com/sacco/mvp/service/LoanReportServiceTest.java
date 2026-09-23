package com.sacco.mvp.service;

import tools.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanReportServiceTest {

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanProductSettingRepository loanProductSettingRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private LoanAnalyticsService loanAnalyticsService;
    @Mock private RegisteredSaccoRepository registeredSaccoRepository;
    @Mock private SaccoStationRepository saccoStationRepository;
    @Mock private SaccoLogoStorageService saccoLogoStorageService;

    private LoanReportService loanReportService;

    @BeforeEach
    void setUp() {
        loanReportService = new LoanReportService(
            loanApplicationRepository,
            loanProductSettingRepository,
            memberRepository,
            guarantorRequestRepository,
            boardReviewRepository,
            managerReviewRepository,
            JsonMapper.builder().findAndAddModules().build(),
            loanAnalyticsService,
            registeredSaccoRepository,
            saccoStationRepository,
            new ApplicationClock("Africa/Nairobi"),
            saccoLogoStorageService
        );
    }

    @Test
    void memberLoanReportCommitteeDecisionContainsOnlyBoardMemberDecisions() {
        UUID applicantId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID boardMemberId = UUID.randomUUID();
        Member applicant = Member.builder()
            .id(applicantId)
            .fullName("Applicant Name")
            .memberNo("MEM-100")
            .build();
        Member boardMember = Member.builder()
            .id(boardMemberId)
            .fullName("Board Member Name")
            .memberNo("BOARD-10")
            .build();
        LoanApplication loan = LoanApplication.builder()
            .id(loanId)
            .applicantMemberId(applicantId)
            .applicationNumber(10040L)
            .loanId("LN-10040")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .amount(new BigDecimal("500000.00"))
            .status(LoanStatus.AWAITING_BOARD)
            .financialSnapshot("{}")
            .build();
        BoardReview boardReview = BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .boardMemberId(boardMemberId)
            .decision(BoardDecision.APPROVED)
            .build();

        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(applicant));
        when(loanApplicationRepository.findByApplicantMemberIdOrderByCreatedAtDesc(applicantId)).thenReturn(List.of(loan));
        when(guarantorRequestRepository.findByLoanApplicationIdIn(any())).thenReturn(List.of());
        when(boardReviewRepository.findByLoanApplicationIdIn(any())).thenReturn(List.of(boardReview));
        when(memberRepository.findAllById(any())).thenReturn(List.of(boardMember));

        LoanReportService.MemberLoanReport report = loanReportService.memberReport(applicantId);

        assertThat(report.details().getFirst().approvalDecisionSummaryLabel())
            .isEqualTo("Board Member Name: Approved")
            .doesNotContain("Applicant Name");
        verifyNoInteractions(managerReviewRepository);
    }

    @Test
    void memberAnalyticsExcelUsesFormalWorkbookSheets() throws Exception {
        byte[] workbookBytes = loanReportService.buildMemberAnalyticsExcel(exportReport(LoanReportService.ReportKind.MEMBER));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getSheet("Summary")).isNotNull();
            assertThat(workbook.getSheet("Status Analysis")).isNotNull();
            assertThat(workbook.getSheet("Product Performance")).isNotNull();
            assertThat(workbook.getSheet("Trends")).isNotNull();
            assertThat(workbook.getSheet("Financial Summary")).isNotNull();
            assertThat(workbook.getSheet("Activity Log")).isNotNull();
            assertThat(sheetContains(workbook, "Summary", "17 Jun 2025 - 17 Jun 2026")).isTrue();
            assertThat(sheetContains(workbook, "Product Performance", "15,000")).isTrue();
        }
    }

    @Test
    void stationAnalyticsPdfUsesTwoPageFormalTemplate() throws Exception {
        byte[] pdf = loanReportService.buildStationAnalyticsPdf(exportReport(LoanReportService.ReportKind.STATION));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("3. LOAN STATUS SUMMARY");
            assertThat(text).doesNotContain("4. LOAN STATUS OVERVIEW");
            assertThat(text).contains("4. LOAN PRODUCT PERFORMANCE");
            assertThat(text).contains("7. LOAN PRODUCT FINANCIAL BREAKDOWN");
        }
    }

    @Test
    void stationAnalyticsPdfShowsClearZeroActivityYearlyGraph() throws Exception {
        LoanReportService.StationAnalyticsExportReport report = new LoanReportService.StationAnalyticsExportReport(
            "IAA",
            "TAHA SACCOS",
            "AR704",
            LocalDate.of(2025, 6, 19),
            LocalDate.of(2026, 6, 19),
            null,
            "Ben Board One",
            "Manager",
            LocalDate.of(2026, 6, 19),
            List.of(new LoanReportService.StationStatusRow("Applied Loans", 0, 0)),
            new LoanReportService.StationParticipationSummary(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0),
            List.of(),
            List.of(
                new LoanReportService.StationYearlySummaryRow(2025, 0, 0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO),
                new LoanReportService.StationYearlySummaryRow(2026, 0, 0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO)
            ),
            "All Products"
        );

        byte[] pdf = loanReportService.buildStationAnalyticsPdf(report);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("8. YEARLY LOAN TREND GRAPH");
            assertThat(text).contains("No loan activity recorded for the selected years.");
        }
    }

    @Test
    void stationAnalyticsExcelIncludesProductFinancialBreakdown() throws Exception {
        byte[] workbookBytes = loanReportService.buildStationAnalyticsExcel(exportReport(LoanReportService.ReportKind.STATION));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(sheetContains(workbook, "Summary", "LOAN PRODUCT FINANCIAL BREAKDOWN")).isTrue();
            assertThat(sheetContains(workbook, "Summary", "Total Interest Paid")).isTrue();
            assertThat(sheetContains(workbook, "Summary", "Total Interest Unpaid Yet")).isTrue();
            assertThat(sheetContains(workbook, "Summary", "15,000")).isTrue();
            assertThat(sheetContains(workbook, "Summary", "13,000")).isTrue();
            assertThat(sheetContains(workbook, "Product Performance", "772,000")).isTrue();
            assertThat(sheetContains(workbook, "Summary", "Customized Loan Product")).isFalse();
        }
    }

    @Test
    void staffAnalyticsExportsUseReviewTrendAndProductBreakdown() throws Exception {
        LoanReportService.AnalyticsExportReport report = exportReport(LoanReportService.ReportKind.STAFF);

        byte[] pdf = loanReportService.buildStationAnalyticsPdf(report);
        byte[] workbookBytes = loanReportService.buildStationAnalyticsExcel(report);

        try (PDDocument document = Loader.loadPDF(pdf);
             XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(text).contains("STAFF LOAN REVIEW REPORT");
            assertThat(text).contains("REVIEW TREND OVER TIME");
            assertThat(workbook.getSheet("Summary")).isNotNull();
            assertThat(workbook.getSheet("Product Review Breakdown")).isNotNull();
            assertThat(workbook.getSheet("Trends")).isNotNull();
            assertThat(sheetContains(workbook, "Trends", "Reviewed")).isTrue();
            assertThat(sheetContains(workbook, "Trends", "Pending")).isTrue();
        }
    }

    @Test
    void stationStaffAnalyticsFinancialRowsPreferPersistedInstallmentPaymentsForOlderLoans() {
        UUID loanId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Member manager = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("IAA")
            .stationId("AR704")
            .memberNo("MGR-1")
            .fullName("Alex Jumapili")
            .position(com.sacco.mvp.domain.Position.MANAGER)
            .staffRoles(Set.of(com.sacco.mvp.domain.Position.MANAGER))
            .memberAccount(false)
            .passwordHash("secret")
            .build();
        var principal = new com.sacco.mvp.security.AppUserPrincipal(manager, Set.of());
        LoanApplication olderActiveLoan = LoanApplication.builder()
            .id(loanId)
            .saccoId("IAA")
            .stationId("AR704")
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.LOAN_ADVANCE)
            .loanProductSettingId(productId)
            .amount(new BigDecimal("700000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2025-01-10T08:00:00Z"))
            .financialSnapshot("""
                {"interestAmount":70000.00}
                """)
            .build();
        LoanAnalyticsService.MemberLoanAnalytics analytics =
            new LoanAnalyticsService.MemberLoanAnalytics(0, 0, 0, 0, 0, 0, BigDecimal.ZERO);

        when(loanAnalyticsService.forStation(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(analytics);
        when(loanAnalyticsService.statusTrendForStation(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(loanAnalyticsService.stationPortfolio(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new LoanAnalyticsService.StaffPortfolioSummary(0, 0, 0, 0, 0, BigDecimal.ZERO, "Low"));
        when(loanApplicationRepository.findScopeReportRows(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(loanApplicationRepository.findScopeFinancialReportRows(any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(olderActiveLoan)));
        when(loanProductSettingRepository.findBySaccoIdAndActiveTrue("IAA"))
            .thenReturn(List.of(product(productId, LoanType.LOAN_ADVANCE, "Loan Advance (Mkopo wa Chapchap)")));
        when(registeredSaccoRepository.findById("IAA")).thenReturn(Optional.empty());
        when(saccoStationRepository.findBySaccoIdAndStationId("IAA", "AR704")).thenReturn(Optional.empty());

        LoanReportService.AnalyticsExportReport report = loanReportService.staffAnalyticsExportReport(
            principal,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            LoanType.LOAN_ADVANCE,
            "staff"
        );

        LoanReportService.ProductPerformanceRow productRow = report.productRows().stream()
            .filter(row -> row.label().equals("Loan Advance (Mkopo wa Chapchap)"))
            .findFirst()
            .orElseThrow();
        LoanReportService.ProductFinancialBreakdownRow financialRow = report.productFinancialRows().stream()
            .filter(row -> row.loanProduct().equals("Loan Advance (Mkopo wa Chapchap)"))
            .findFirst()
            .orElseThrow();

        assertThat(productRow.applied()).isZero();
        assertThat(productRow.interestPaid()).isEqualByComparingTo("0.00");
        assertThat(financialRow.totalInterestPaid()).isEqualByComparingTo("0.00");
        assertThat(financialRow.totalInterestUnpaid()).isEqualByComparingTo("70000.00");
        assertThat(financialRow.totalLoanAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(financialRow.totalLoanAmountUnpaid()).isEqualByComparingTo("700000.00");
    }

    @Test
    void stationFinancialAnalyticsFallsBackToPaymentSummaryWhenTransactionsAreMissing() {
        UUID loanId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Member manager = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("IAA")
            .stationId("AR704")
            .memberNo("MGR-1")
            .fullName("Alex Jumapili")
            .position(com.sacco.mvp.domain.Position.MANAGER)
            .staffRoles(Set.of(com.sacco.mvp.domain.Position.MANAGER))
            .memberAccount(false)
            .passwordHash("secret")
            .build();
        var principal = new com.sacco.mvp.security.AppUserPrincipal(manager, Set.of());
        LoanApplication activeLoan = LoanApplication.builder()
            .id(loanId)
            .saccoId("IAA")
            .stationId("AR704")
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.EDUCATION_LOAN)
            .loanProductSettingId(productId)
            .amount(new BigDecimal("700000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2026-07-08T10:00:00Z"))
            .financialSnapshot("""
                {"interestAmount":70000.00}
                """)
            .build();
        LoanAnalyticsService.MemberLoanAnalytics analytics =
            new LoanAnalyticsService.MemberLoanAnalytics(0, 0, 0, 0, 0, 0, BigDecimal.ZERO);

        when(loanAnalyticsService.forStation(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(analytics);
        when(loanAnalyticsService.statusTrendForStation(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(loanAnalyticsService.stationPortfolio(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new LoanAnalyticsService.StaffPortfolioSummary(0, 0, 0, 0, 0, BigDecimal.ZERO, "Low"));
        when(loanApplicationRepository.findScopeReportRows(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        when(loanApplicationRepository.findScopeFinancialReportRows(any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(activeLoan)));
        when(loanProductSettingRepository.findBySaccoIdAndActiveTrue("IAA"))
            .thenReturn(List.of(product(productId, LoanType.EDUCATION_LOAN, "Education Loan (Mkopo wa Elimu)")));
        when(registeredSaccoRepository.findById("IAA")).thenReturn(Optional.empty());
        when(saccoStationRepository.findBySaccoIdAndStationId("IAA", "AR704")).thenReturn(Optional.empty());

        LoanReportService.AnalyticsExportReport report = loanReportService.staffAnalyticsExportReport(
            principal,
            LocalDate.of(2025, 7, 13),
            LocalDate.of(2026, 7, 13),
            LoanType.EDUCATION_LOAN,
            "staff"
        );

        LoanReportService.ProductFinancialBreakdownRow financialRow = report.productFinancialRows().stream()
            .filter(row -> row.loanProduct().equals("Education Loan (Mkopo wa Elimu)"))
            .findFirst()
            .orElseThrow();

        assertThat(report.productRows().stream()
            .filter(row -> row.label().equals("Education Loan (Mkopo wa Elimu)"))
            .findFirst()
            .orElseThrow()
            .interestPaid()).isEqualByComparingTo("0.00");
        assertThat(financialRow.totalInterestPaid()).isEqualByComparingTo("0.00");
        assertThat(financialRow.totalInterestUnpaid()).isEqualByComparingTo("70000.00");
        assertThat(financialRow.totalLoanAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(financialRow.totalLoanAmountUnpaid()).isEqualByComparingTo("700000.00");
    }

    @Test
    void memberActiveLoanDetailsUseFilteredTransactionInterestAndOutstandingInterest() {
        UUID memberId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LoanApplication loan = LoanApplication.builder()
            .id(loanId)
            .saccoId("IAA")
            .applicantMemberId(memberId)
            .loanType(LoanType.LOAN_ADVANCE)
            .loanProductSettingId(productId)
            .loanId("LN-1001")
            .amount(new BigDecimal("800000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2026-06-17T10:00:00Z"))
            .financialSnapshot("""
                {"interestAmount":28000.00}
                """)
            .build();

        when(loanApplicationRepository.findMemberReportRowsByStatuses(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(loan)));
        when(loanProductSettingRepository.findBySaccoIdAndActiveTrue("IAA"))
            .thenReturn(List.of(product(productId, LoanType.LOAN_ADVANCE, "Loan Advance (Mkopo wa Chapchap)")));

        List<LoanReportService.ActiveLoanDetailRow> rows = loanReportService.memberActiveLoanDetails(
            memberId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().loanId()).isEqualTo("LN-1001");
        assertThat(rows.getFirst().loanProduct()).isEqualTo("Loan Advance (Mkopo wa Chapchap)");
        assertThat(rows.getFirst().requiredInterestAmount()).isEqualTo("28,000");
        assertThat(rows.getFirst().outstandingBalance()).isEqualTo("828,000");
        assertThat(rows.getFirst().paidLoanAmount()).isEqualTo("0");
        assertThat(rows.getFirst().interestPaid()).isEqualTo("0");
        assertThat(rows.getFirst().interestNotYetPaid()).isEqualTo("28,000");
    }

    @Test
    void memberActiveLoanDetailsPreferSyncedForesightOutstandingBalance() {
        UUID memberId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LoanApplication loan = LoanApplication.builder()
            .id(loanId)
            .saccoId("IAA")
            .applicantMemberId(memberId)
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .loanProductSettingId(productId)
            .loanId("1001")
            .amount(new BigDecimal("500000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2026-07-08T10:00:00Z"))
            .financialSnapshot("""
                {
                  "interestAmount":25000.00,
                  "principalPlusInterest":525000.00,
                  "foresightTotalOutstanding":445000.00,
                  "foresightOutstandingPrincipal":425000.00,
                  "foresightOutstandingInterest":20000.00,
                  "foresightTotalPrincipalPaid":75000.00,
                  "foresightTotalInterestPaid":5000.00
                }
                """)
            .build();

        when(loanApplicationRepository.findMemberReportRowsByStatuses(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(loan)));
        when(loanProductSettingRepository.findBySaccoIdAndActiveTrue("IAA"))
            .thenReturn(List.of(product(productId, LoanType.CUSTOMIZED_LOAN, "Personal Loan")));

        List<LoanReportService.ActiveLoanDetailRow> rows = loanReportService.memberActiveLoanDetails(
            memberId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().outstandingBalance()).isEqualTo("445,000");
        assertThat(rows.getFirst().paidLoanAmount()).isEqualTo("75,000");
        assertThat(rows.getFirst().interestPaid()).isEqualTo("5,000");
        assertThat(rows.getFirst().interestNotYetPaid()).isEqualTo("20,000");
    }

    @Test
    void memberActiveLoanDetailsUseSnapshotAndSummaryWhenTransactionsAreMissing() {
        UUID memberId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LoanApplication loan = LoanApplication.builder()
            .id(loanId)
            .saccoId("IAA")
            .applicantMemberId(memberId)
            .loanType(LoanType.EDUCATION_LOAN)
            .loanProductSettingId(productId)
            .loanId("10706")
            .amount(new BigDecimal("700000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2026-07-08T10:00:00Z"))
            .financialSnapshot("""
                {"interestAmount":70000.00}
                """)
            .build();

        when(loanApplicationRepository.findMemberReportRowsByStatuses(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(loan)));
        when(loanProductSettingRepository.findBySaccoIdAndActiveTrue("IAA"))
            .thenReturn(List.of(product(productId, LoanType.EDUCATION_LOAN, "Education Loan (Mkopo wa Elimu)")));

        List<LoanReportService.ActiveLoanDetailRow> rows = loanReportService.memberActiveLoanDetails(
            memberId, LocalDate.of(2025, 7, 13), LocalDate.of(2026, 7, 13), null);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().loanAmount()).isEqualTo("700,000");
        assertThat(rows.getFirst().outstandingBalance()).isEqualTo("770,000");
        assertThat(rows.getFirst().requiredInterestAmount()).isEqualTo("70,000");
        assertThat(rows.getFirst().paidLoanAmount()).isEqualTo("0");
        assertThat(rows.getFirst().interestPaid()).isEqualTo("0");
        assertThat(rows.getFirst().interestNotYetPaid()).isEqualTo("70,000");
    }

    @Test
    void memberAnalyticsPdfUsesTwoPageFormalTemplate() throws Exception {
        byte[] pdf = loanReportService.buildMemberAnalyticsPdf(exportReport(LoanReportService.ReportKind.MEMBER));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Jun 25").contains("Jun 26");
        }
    }

    @Test
    void stationAnalyticsReportCountsApplicantsAndFullyPaidInterest() {
        UUID applicantOne = UUID.randomUUID();
        UUID applicantTwo = UUID.randomUUID();
        UUID paidLoanId = UUID.randomUUID();
        UUID activeLoanId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LoanApplication paidLoan = LoanApplication.builder()
            .id(paidLoanId)
            .saccoId("IAA")
            .stationId("AR704")
            .applicantMemberId(applicantOne)
            .loanType(LoanType.LOAN_ADVANCE)
            .loanProductSettingId(productId)
            .amount(new BigDecimal("500000.00"))
            .status(LoanStatus.PAID)
            .createdAt(OffsetDateTime.parse("2026-02-10T08:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-04-10T08:00:00Z"))
            .paidAt(OffsetDateTime.parse("2026-04-10T08:00:00Z"))
            .financialSnapshot("""
                {"interestAmount":12000.00}
                """)
            .build();
        LoanApplication activeLoan = LoanApplication.builder()
            .id(activeLoanId)
            .saccoId("IAA")
            .stationId("AR704")
            .applicantMemberId(applicantTwo)
            .loanType(LoanType.LOAN_ADVANCE)
            .loanProductSettingId(productId)
            .amount(new BigDecimal("300000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2026-03-10T08:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-03-10T08:00:00Z"))
            .build();

        when(loanApplicationRepository.findScopeReportRows(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(paidLoan), reportRow(activeLoan)));
        when(loanApplicationRepository.findScopeFinancialReportRows(any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(paidLoan), reportRow(activeLoan)));
        when(memberRepository.countActiveMemberAccountsForScope("IAA", "AR704")).thenReturn(10L);
        when(loanProductSettingRepository.findBySaccoIdAndActiveTrue("IAA"))
            .thenReturn(List.of(product(productId, LoanType.LOAN_ADVANCE, "Loan Advance (Mkopo wa Chapchap)")));

        LoanReportService.StationAnalyticsExportReport report = loanReportService.stationAnalyticsReport(
            "IAA",
            "AR704",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            LoanType.LOAN_ADVANCE,
            "Alex Jumapili",
            "Manager"
        );

        assertThat(report.participation().activeStationMembers()).isEqualTo(10);
        assertThat(report.participation().uniqueApplicants()).isEqualTo(2);
        assertThat(report.statusRows())
            .anySatisfy(row -> {
                assertThat(row.metric()).isEqualTo("Applied Loans");
                assertThat(row.count()).isEqualTo(2);
                assertThat(row.applicantCount()).isEqualTo(2);
        });
        assertThat(report.productRows()).hasSize(1);
        assertThat(report.productRows().getFirst().totalPaidInterest()).isEqualByComparingTo("12000.00");
        assertThat(report.productRows().getFirst().fullyPaidLoanInterest()).isEqualByComparingTo("12000.00");
        assertThat(report.yearlyRows().getFirst().totalPaidLoans()).isEqualTo(1);
        assertThat(report.yearlyRows().getFirst().fullyPaidLoanInterest()).isEqualByComparingTo("12000.00");
    }

    @Test
    void stationAnalyticsReportUsesSummaryInterestWhenTransactionsAreMissing() {
        UUID activeLoanId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LoanApplication activeLoan = LoanApplication.builder()
            .id(activeLoanId)
            .saccoId("TAHA")
            .stationId("AR704")
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.EDUCATION_LOAN)
            .loanProductSettingId(productId)
            .amount(new BigDecimal("700000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2026-07-08T10:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-07-13T10:00:00Z"))
            .financialSnapshot("""
                {"interestAmount":60000.00}
                """)
            .build();

        when(loanApplicationRepository.findScopeReportRows(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(activeLoan)));
        when(loanApplicationRepository.findScopeFinancialReportRows(any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(activeLoan)));
        when(memberRepository.countActiveMemberAccountsForScope("TAHA", "AR704")).thenReturn(3L);
        when(loanProductSettingRepository.findBySaccoIdAndActiveTrue("TAHA"))
            .thenReturn(List.of(product(productId, LoanType.EDUCATION_LOAN, "Education Loan (Mkopo wa Elimu)")));
        when(registeredSaccoRepository.findById("TAHA"))
            .thenReturn(Optional.of(com.sacco.mvp.domain.RegisteredSacco.builder()
                .saccoId("TAHA")
                .saccoName("TAHA SACCOS")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build()));

        LoanReportService.StationAnalyticsExportReport report = loanReportService.stationAnalyticsReport(
            "TAHA",
            "AR704",
            LocalDate.of(2025, 7, 13),
            LocalDate.of(2026, 7, 13),
            LoanType.EDUCATION_LOAN,
            "Daniel Sikukuu",
            "Manager"
        );

        assertThat(report.saccoName()).isEqualTo("TAHA SACCOS");
        assertThat(report.productRows().getFirst().totalPaidInterest()).isEqualByComparingTo("0.00");
        assertThat(report.yearlyRows()).anySatisfy(row -> {
            assertThat(row.year()).isEqualTo(2026);
            assertThat(row.totalPaidInterestAccumulated()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    void stationAnalyticsReportUsesSyncedPaidInterestForAccumulatedInterest() {
        UUID activeLoanId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LoanApplication activeLoan = LoanApplication.builder()
            .id(activeLoanId)
            .saccoId("IAA")
            .stationId("AR704")
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.EDUCATION_LOAN)
            .loanProductSettingId(productId)
            .amount(new BigDecimal("700000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2026-07-08T10:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-07-13T10:00:00Z"))
            .financialSnapshot("""
                {
                  "interestAmount":70000.00,
                  "foresightTotalInterestPaid":5000.00,
                  "foresightLastPaymentDate":"2026-07-12"
                }
                """)
            .build();

        when(loanApplicationRepository.findScopeReportRows(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(activeLoan)));
        when(loanApplicationRepository.findScopeFinancialReportRows(any(), any(), any(), any()))
            .thenReturn(List.of(reportRow(activeLoan)));
        when(memberRepository.countActiveMemberAccountsForScope("IAA", "AR704")).thenReturn(3L);
        when(loanProductSettingRepository.findBySaccoIdAndActiveTrue("IAA"))
            .thenReturn(List.of(product(productId, LoanType.EDUCATION_LOAN, "Education Loan (Mkopo wa Elimu)")));

        LoanReportService.StationAnalyticsExportReport report = loanReportService.stationAnalyticsReport(
            "IAA",
            "AR704",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            LoanType.EDUCATION_LOAN,
            "Daniel Sikukuu",
            "Manager"
        );

        assertThat(report.productRows().getFirst().totalPaidInterest()).isEqualByComparingTo("5000.00");
        assertThat(report.productRows().getFirst().fullyPaidLoanInterest()).isEqualByComparingTo("0.00");
        assertThat(report.yearlyRows()).anySatisfy(row -> {
            assertThat(row.year()).isEqualTo(2026);
            assertThat(row.totalPaidInterestAccumulated()).isEqualByComparingTo("5000.00");
            assertThat(row.fullyPaidLoanInterest()).isEqualByComparingTo("0.00");
        });
    }

    @Test
    void stationAnalyticsExcelContainsApplicantAndYearlyInterestHeaders() throws Exception {
        LoanReportService.StationAnalyticsExportReport report = new LoanReportService.StationAnalyticsExportReport(
            "IAA",
            "TAHA SACCOS",
            "AR704",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            null,
            "Alex Jumapili",
            "Manager",
            LocalDate.of(2026, 6, 18),
            List.of(new LoanReportService.StationStatusRow("Applied Loans", 2, 2)),
            new LoanReportService.StationParticipationSummary(10, 2, new BigDecimal("20.00"), new BigDecimal("1.00"), 0),
            List.of(new LoanReportService.StationProductRow("Loan Advance (Mkopo wa Chapchap)", 2, 2, 1, 2, 1, 0, 0, new BigDecimal("15000.00"), new BigDecimal("12000.00"))),
            List.of(new LoanReportService.StationYearlySummaryRow(2026, 2, 2, 0, BigDecimal.ZERO, 1, new BigDecimal("15000.00"), new BigDecimal("12000.00"))),
            "All Products"
        );

        byte[] workbookBytes = loanReportService.buildStationAnalyticsExcel(report);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getSheet("Summary")).isNotNull();
            assertThat(workbook.getSheet("Loan Status Analysis")).isNotNull();
            assertThat(workbook.getSheet("Product Performance")).isNotNull();
            assertThat(workbook.getSheet("Trends")).isNotNull();
            assertThat(sheetContains(workbook, "Loan Status Analysis", "Number of Applicants")).isTrue();
            assertThat(sheetContains(workbook, "Product Performance", "Total Interest of Fully Paid Loan")).isTrue();
            assertThat(sheetContains(workbook, "Trends", "Total Interest of Fully Paid Loan")).isTrue();
            assertThat(workbook.getSheet("Trends").getDrawingPatriarch()).isNotNull();
        }
    }

    @Test
    void smsUsageExportsContainFiltersHeadersAndFilteredRows() throws Exception {
        UUID applicantId = UUID.randomUUID();
        SmsUsageManagementService.LoanSmsUsageRow row = new SmsUsageManagementService.LoanSmsUsageRow(
            UUID.randomUUID(),
            1001L,
            "LN-1001",
            "On Review By Manager",
            "READY_FOR_MANAGER",
            "IAA",
            "AR704",
            applicantId,
            "Asha Nyerere",
            "MEM-100",
            2,
            4,
            "23 Sep 2026, 10:30"
        );
        SmsUsageManagementService.LoanSmsUsageExportReport report = new SmsUsageManagementService.LoanSmsUsageExportReport(
            "IAA",
            "AR704",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 23),
            "On Review By Manager",
            "Asha Nyerere (MEM-100)",
            "Admin User",
            LocalDate.of(2026, 9, 23),
            List.of(row),
            2,
            4
        );
        when(registeredSaccoRepository.findById("IAA")).thenReturn(Optional.empty());

        byte[] workbookBytes = loanReportService.buildSmsUsageExcel(report);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getSheet("SMS Usage")).isNotNull();
            assertThat(sheetContains(workbook, "SMS Usage", "SMS USAGE BY LOAN APPLICATION")).isTrue();
            assertThat(sheetContains(workbook, "SMS Usage", "REPORT DETAILS")).isTrue();
            assertThat(sheetContains(workbook, "SMS Usage", "Asha Nyerere (MEM-100)")).isTrue();
            assertThat(sheetContains(workbook, "SMS Usage", "SMS Events")).isTrue();
            assertThat(sheetContains(workbook, "SMS Usage", "Units Used")).isTrue();
            assertThat(sheetContains(workbook, "SMS Usage", "Asha Nyerere")).isTrue();
            assertThat(sheetContains(workbook, "SMS Usage", "LN-1001")).isTrue();
        }

        byte[] pdf = loanReportService.buildSmsUsagePdf(report);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                .contains("SMS Usage By Loan Application")
                .contains("Asha Nyerere")
                .contains("LN-1001")
                .contains("SMS Usage Report");
        }
    }

    private LoanReportService.AnalyticsExportReport exportReport(LoanReportService.ReportKind kind) {
        LoanAnalyticsService.MemberLoanAnalytics analytics = new LoanAnalyticsService.MemberLoanAnalytics(
            0, 4, 0, 0, 5, 4, new BigDecimal("772000.00"));
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .fullName("David Wankyo")
            .memberNo("1145")
            .stationId("AR704")
            .build();
        return new LoanReportService.AnalyticsExportReport(
            kind,
            "IAA SACCOS LTD",
            "SACCO-1",
            kind == LoanReportService.ReportKind.MEMBER
                ? "MEMBER LOAN REPORT"
                : kind == LoanReportService.ReportKind.STAFF ? "STAFF LOAN REVIEW REPORT" : "STATION LOAN STATUS REPORT",
            "AR704",
            "Arusha Central Branch",
            LocalDate.of(2025, 6, 17),
            LocalDate.of(2026, 6, 17),
            LocalDate.of(2026, 6, 17),
            "Alex Jumapili",
            kind == LoanReportService.ReportKind.MEMBER ? "Member" : "Manager",
            "All Products",
            kind == LoanReportService.ReportKind.MEMBER ? member : null,
            analytics,
            new LoanAnalyticsService.StaffPortfolioSummary(5, 4, 0, 4, 0, BigDecimal.ZERO, "Low"),
            List.of(new LoanReportService.ProductPerformanceRow("Loan Advance", 5, 5, 4, 0, 0, 0, new BigDecimal("15000.00"), new BigDecimal("12000.00"))),
            List.of(new LoanReportService.ProductFinancialBreakdownRow("Loan Advance", new BigDecimal("15000.00"), new BigDecimal("13000.00"), new BigDecimal("28000.00"), new BigDecimal("772000.00"))),
            kind == LoanReportService.ReportKind.STAFF
                ? new LoanAnalyticsService.StaffReviewAnalytics(
                    5,
                    4,
                    0,
                    1,
                    4,
                    0,
                    List.of(new LoanAnalyticsService.StaffReviewProductPerformance("Loan Advance", 5, 4, 0, 1, new BigDecimal("80.00"))),
                    List.of(
                        new LoanAnalyticsService.MetricTrendSeries("Reviewed", "#111827", List.of(Map.of("x", 1748736000000L, "y", 1L), Map.of("x", 1780272000000L, "y", 5L))),
                        new LoanAnalyticsService.MetricTrendSeries("Approved", "#65a30d", List.of(Map.of("x", 1748736000000L, "y", 1L), Map.of("x", 1780272000000L, "y", 4L))),
                        new LoanAnalyticsService.MetricTrendSeries("Rejected", "#ef4444", List.of(Map.of("x", 1748736000000L, "y", 0L), Map.of("x", 1780272000000L, "y", 0L))),
                        new LoanAnalyticsService.MetricTrendSeries("Pending", "#7e22ce", List.of(Map.of("x", 1748736000000L, "y", 0L), Map.of("x", 1780272000000L, "y", 1L)))
                    )
                )
                : null,
            List.of(new LoanAnalyticsService.MetricTrendSeries("Applied", "#000000", List.of(
                Map.of("x", 1748736000000L, "y", 0L),
                Map.of("x", 1780272000000L, "y", 5L)
            ))),
            List.of(new LoanReportService.ActivityRow("17 Jun 2026", "Application Submitted", "Loan Advance", "772,000", "Applied")),
            List.of(new LoanReportService.ActiveLoanDetailRow("LN-1001", "Loan Advance", "800,000", "772,000", "28,000", "28,000", "15,000", "13,000")),
            new LoanReportService.FinancialSummary(new BigDecimal("772000.00"), new BigDecimal("772000.00"), BigDecimal.ZERO, new BigDecimal("772000.00"), BigDecimal.ZERO),
            List.of("Total loan applications increased by 100%.", "No defaults recorded during the reporting period."),
            "This report summarizes the loan performance and status within the selected period."
        );
    }

    private LoanApplicationRepository.ReportLoanRow reportRow(LoanApplication loan) {
        return new LoanApplicationRepository.ReportLoanRow() {
            @Override
            public UUID getId() {
                return loan.getId();
            }

            @Override
            public Long getApplicationNumber() {
                return loan.getApplicationNumber();
            }

            @Override
            public String getLoanId() {
                return loan.getLoanId();
            }

            @Override
            public LoanStatus getStatus() {
                return loan.getStatus();
            }

            @Override
            public LoanType getLoanType() {
                return loan.getLoanType();
            }

            @Override
            public UUID getLoanProductSettingId() {
                return loan.getLoanProductSettingId();
            }

            @Override
            public OffsetDateTime getCreatedAt() {
                return loan.getCreatedAt();
            }

            @Override
            public OffsetDateTime getUpdatedAt() {
                return loan.getUpdatedAt();
            }

            @Override
            public BigDecimal getAmount() {
                return loan.getAmount();
            }

            @Override
            public String getSaccoId() {
                return loan.getSaccoId();
            }

            @Override
            public String getStationId() {
                return loan.getStationId();
            }

            @Override
            public UUID getApplicantMemberId() {
                return loan.getApplicantMemberId();
            }

            @Override
            public LocalDate getDisbursementDate() {
                return loan.getDisbursementDate();
            }

            @Override
            public String getFinancialSnapshot() {
                return loan.getFinancialSnapshot();
            }
        };
    }

    private LoanProductSetting product(UUID id, LoanType loanType, String name) {
        return LoanProductSetting.builder()
            .id(id)
            .saccoId("IAA")
            .loanType(loanType)
            .productName(name)
            .displayOrder(1)
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private boolean sheetContains(XSSFWorkbook workbook, String sheetName, String expected) {
        var sheet = workbook.getSheet(sheetName);
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                    && expected.equals(cell.getStringCellValue())) {
                    return true;
                }
            }
        }
        return false;
    }
}
