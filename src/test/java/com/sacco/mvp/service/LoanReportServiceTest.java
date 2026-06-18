package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanPaymentTransaction;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanPaymentTransactionRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanReportServiceTest {

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private LoanPaymentTransactionRepository loanPaymentTransactionRepository;

    private LoanReportService loanReportService;

    @BeforeEach
    void setUp() {
        loanReportService = new LoanReportService(
            loanApplicationRepository,
            memberRepository,
            guarantorRequestRepository,
            boardReviewRepository,
            managerReviewRepository,
            loanPaymentTransactionRepository,
            JsonMapper.builder().findAndAddModules().build()
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
        when(guarantorRequestRepository.findByLoanApplicationId(loanId)).thenReturn(List.of());
        when(boardReviewRepository.findByLoanApplicationId(loanId)).thenReturn(List.of(boardReview));
        when(memberRepository.findAllById(any())).thenReturn(List.of(boardMember));

        LoanReportService.MemberLoanReport report = loanReportService.memberReport(applicantId);

        assertThat(report.details().getFirst().approvalDecisionSummaryLabel())
            .isEqualTo("Board Member Name: Approved")
            .doesNotContain("Applicant Name");
        verifyNoInteractions(managerReviewRepository);
    }

    @Test
    void stationAnalyticsReportCountsApplicantsAndFullyPaidInterest() {
        UUID applicantOne = UUID.randomUUID();
        UUID applicantTwo = UUID.randomUUID();
        UUID paidLoanId = UUID.randomUUID();
        UUID activeLoanId = UUID.randomUUID();
        LoanApplication paidLoan = LoanApplication.builder()
            .id(paidLoanId)
            .saccoId("IAA")
            .stationId("AR704")
            .applicantMemberId(applicantOne)
            .loanType(LoanType.LOAN_ADVANCE)
            .amount(new BigDecimal("500000.00"))
            .status(LoanStatus.PAID)
            .createdAt(OffsetDateTime.parse("2026-02-10T08:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-04-10T08:00:00Z"))
            .paidAt(OffsetDateTime.parse("2026-04-10T08:00:00Z"))
            .build();
        LoanApplication activeLoan = LoanApplication.builder()
            .id(activeLoanId)
            .saccoId("IAA")
            .stationId("AR704")
            .applicantMemberId(applicantTwo)
            .loanType(LoanType.LOAN_ADVANCE)
            .amount(new BigDecimal("300000.00"))
            .status(LoanStatus.FINAL_APPROVED)
            .createdAt(OffsetDateTime.parse("2026-03-10T08:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-03-10T08:00:00Z"))
            .build();

        when(loanApplicationRepository.findScopeLoansForAnalytics(
            any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of(paidLoan, activeLoan));
        when(memberRepository.countActiveMemberAccountsForScope("IAA", "AR704")).thenReturn(10L);
        when(loanPaymentTransactionRepository.findByLoanApplicationIdInAndReceiptDateBetweenOrderByReceiptDateAsc(
            any(), any(), any()
        )).thenReturn(List.of(
            LoanPaymentTransaction.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(paidLoanId)
                .receiptDate(LocalDate.of(2026, 4, 10))
                .interestPaid(new BigDecimal("12000.00"))
                .principalPaid(BigDecimal.ZERO)
                .totalPaid(new BigDecimal("12000.00"))
                .build(),
            LoanPaymentTransaction.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(activeLoanId)
                .receiptDate(LocalDate.of(2026, 4, 12))
                .interestPaid(new BigDecimal("3000.00"))
                .principalPaid(BigDecimal.ZERO)
                .totalPaid(new BigDecimal("3000.00"))
                .build()
        ));

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
        assertThat(report.productRows().getFirst().totalPaidInterest()).isEqualByComparingTo("15000.00");
        assertThat(report.productRows().getFirst().fullyPaidLoanInterest()).isEqualByComparingTo("12000.00");
        assertThat(report.yearlyRows().getFirst().totalPaidLoans()).isEqualTo(1);
        assertThat(report.yearlyRows().getFirst().fullyPaidLoanInterest()).isEqualByComparingTo("12000.00");
    }

    @Test
    void stationAnalyticsPdfContainsNewApplicantAndInterestHeaders() throws Exception {
        LoanReportService.StationAnalyticsExportReport report = new LoanReportService.StationAnalyticsExportReport(
            "IAA",
            "AR704",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            LoanType.LOAN_ADVANCE,
            "Alex Jumapili",
            "Manager",
            LocalDate.of(2026, 6, 18),
            List.of(new LoanReportService.StationStatusRow("Applied Loans", 2, 2)),
            new LoanReportService.StationParticipationSummary(10, 2, new BigDecimal("20.00"), new BigDecimal("1.00"), 0),
            List.of(new LoanReportService.StationProductRow("Loan Advance (Mkopo wa Chapchap)", 2, 2, 1, 2, 1, 0, 0, new BigDecimal("15000.00"), new BigDecimal("12000.00"))),
            List.of(new LoanReportService.StationYearlySummaryRow(2026, 2, 2, 0, BigDecimal.ZERO, 1, new BigDecimal("15000.00"), new BigDecimal("12000.00")))
        );

        byte[] pdf = loanReportService.buildStationAnalyticsPdf(report);
        String text;
        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }

        assertThat(text).contains("Number of Applicants");
        assertThat(text).contains("MEMBER / APPLICANT PARTICIPATION SUMMARY");
        assertThat(text).contains("YEARLY LOAN AND INTEREST SUMMARY");
        assertThat(text).contains("Fully Paid Loan Interest");
    }

    @Test
    void stationAnalyticsExcelContainsApplicantAndYearlyInterestHeaders() throws Exception {
        LoanReportService.StationAnalyticsExportReport report = new LoanReportService.StationAnalyticsExportReport(
            "IAA",
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
            List.of(new LoanReportService.StationYearlySummaryRow(2026, 2, 2, 0, BigDecimal.ZERO, 1, new BigDecimal("15000.00"), new BigDecimal("12000.00")))
        );

        byte[] workbookBytes = loanReportService.buildStationAnalyticsExcel(report);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getSheet("Summary")).isNotNull();
            assertThat(workbook.getSheet("Loan Status Analysis")).isNotNull();
            assertThat(workbook.getSheet("Product Performance")).isNotNull();
            assertThat(workbook.getSheet("Trends")).isNotNull();
            assertThat(workbook.getSheet("Loan Status Analysis").getRow(3).getCell(2).getStringCellValue())
                .isEqualTo("Number of Applicants");
            assertThat(workbook.getSheet("Product Performance").getRow(2).getCell(9).getStringCellValue())
                .isEqualTo("Total Interest Paid from Fully Paid Loans");
            assertThat(workbook.getSheet("Trends").getRow(2).getCell(7).getStringCellValue())
                .isEqualTo("Total Interest Paid from Fully Paid Loans");
        }
    }
}
