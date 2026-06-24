package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanPaymentTransaction;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LoanPresentationServiceTest {

    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private LoanAttachmentService loanAttachmentService;
    @Mock private LoanProductWorkflowService loanProductWorkflowService;

    private LoanPresentationService loanPresentationService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("loan.status.DRAFT", Locale.ENGLISH, "Draft");
        messageSource.addMessage("loan.status.AWAITING_GUARANTORS", Locale.ENGLISH, "Awaiting Guarantors");
        messageSource.addMessage("loan.status.ALL_GUARANTORS_APPROVED", Locale.ENGLISH, "All Guarantors Approved");
        messageSource.addMessage("loan.status.READY_FOR_MANAGER", Locale.ENGLISH, "On Review By Manager");
        messageSource.addMessage("loan.status.AWAITING_LOAN_OFFICER", Locale.ENGLISH, "On Review By Loan Officer");
        messageSource.addMessage("loan.status.AWAITING_BOARD", Locale.ENGLISH, "On Review By Board");
        messageSource.addMessage("loan.status.AWAITING_ACCOUNTANT", Locale.ENGLISH, "On Review By Accountant");
        messageSource.addMessage("loan.status.READY_FOR_DISBURSEMENT", Locale.ENGLISH, "Approved For Disbursement");
        loanPresentationService = new LoanPresentationService(
            JsonMapper.builder().findAndAddModules().build(),
            managerReviewRepository,
            boardReviewRepository,
            loanAttachmentService,
            loanProductWorkflowService,
            messageSource
        );
    }

    @Test
    void parseApplicationAttachmentsHidesLegacyFeeReceiptsAndDisbursementProofs() {
        String attachmentsJson = "[{}]";
        org.mockito.Mockito.when(loanAttachmentService.parse(attachmentsJson)).thenReturn(List.of(
            new LinkedHashMap<>(Map.of(
                "id", "application-file",
                "originalName", "application.pdf",
                "attachmentCategory", "APPLICATION_ATTACHMENT",
                "size", 1200
            )),
            new LinkedHashMap<>(Map.of(
                "id", "legacy-fee-file",
                "originalName", "fee-receipt.pdf",
                "attachmentCategory", "FEE_INSURANCE_RECEIPT",
                "size", 800
            )),
            new LinkedHashMap<>(Map.of(
                "id", "disbursement-file",
                "originalName", "disbursement.pdf",
                "attachmentCategory", "DISBURSEMENT_PROOF",
                "size", 900
            ))
        ));

        List<Map<String, Object>> attachments = loanPresentationService.parseApplicationAttachments(attachmentsJson);

        assertThat(attachments).hasSize(1);
        assertThat(attachments.getFirst()).containsEntry("id", "application-file");
    }

    @Test
    void parseFinancialFieldsUsesReleasedPrincipalFromLoanRecord() {
        LoanApplication app = LoanApplication.builder()
            .id(UUID.randomUUID())
            .amount(new BigDecimal("125000.00"))
            .financialSnapshot("""
                {
                  "applicationFee": 15000.00,
                  "insuranceFee": 2250.00,
                  "insuranceRate": 0.015,
                  "processingFee": 3750.00,
                  "processingFeeRate": 0.025,
                  "principalAmount": 150000.00,
                  "interestAmount": 12000.00,
                  "principalPlusInterest": 162000.00,
                  "monthlyRepaymentAmount": 27000.00
                }
                """)
            .build();

        Map<String, Object> fields = loanPresentationService.parseFinancialFields(app);

        assertThat(fields)
            .containsEntry("Insurance Fee (1.5%)", "TSh 2,250.00")
            .containsEntry("Loan Processing Fee (2.5%)", "TSh 3,750.00")
            .containsEntry("Loan Amount (TZS)", "TSh 125,000.00")
            .containsEntry("Loan Amount + Interest (TZS)", "TSh 137,000.00");
    }

    @Test
    void reviewRepaymentSummaryUsesLoadedLoanDetailsBeforeDisbursement() {
        LoanApplication app = LoanApplication.builder()
            .amount(new BigDecimal("120000.00"))
            .tenorMonths(12)
            .financialSnapshot("""
                {
                  "interestMethod": "FLAT_RATE",
                  "interestRate": 0.12,
                  "interestAmount": 14400.00,
                  "principalPlusInterest": 134400.00,
                  "monthlyRepaymentAmount": 11200.00
                }
                """)
            .build();

        Map<String, Object> summary = loanPresentationService.reviewRepaymentSummary(app);

        assertThat(loanPresentationService.isEstimatedReviewRepaymentSummary(app)).isTrue();
        assertThat(summary)
            .containsEntry("Loan Amount", "TSh 120,000.00")
            .containsEntry("Annual Interest Rate", "12.00%")
            .containsEntry("Loan Period in Years", "1 year")
            .containsEntry("Number of Payments", 12)
            .containsEntry("Estimated Installment", "TSh 11,200.00")
            .containsEntry("Estimated Total Repayment", "TSh 134,400.00");
    }

    @Test
    void reviewRepaymentRowsBuildEstimatedScheduleBeforeDisbursement() {
        LoanApplication app = LoanApplication.builder()
            .amount(new BigDecimal("120000.00"))
            .tenorMonths(3)
            .financialSnapshot("""
                {
                  "interestMethod": "FLAT_RATE",
                  "interestRate": 0.12,
                  "interestAmount": 14400.00,
                  "principalPlusInterest": 134400.00,
                  "monthlyRepaymentAmount": 44800.00
                }
                """)
            .build();

        List<Map<String, Object>> rows = loanPresentationService.reviewRepaymentRows(app);

        assertThat(rows).hasSize(3);
        assertThat(rows.getFirst())
            .containsEntry("installment", "Installment 1")
            .containsEntry("pmtNo", "1")
            .containsEntry("month", "Month 1")
            .containsEntry("beginningBalance", "TSh 120,000.00")
            .containsEntry("payment", "TSh 44,800.00")
            .containsEntry("loanAmount", "TSh 40,000.00")
            .containsEntry("interest", "TSh 4,800.00")
            .containsEntry("endingBalance", "TSh 80,000.00");
        assertThat(rows.getFirst().get("scheduledBreakdown").toString())
            .contains("Loan Amount: TSh 40,000.00")
            .contains("Interest: TSh 4,800.00");
    }

    @Test
    void buildProgressItemsUsesConfiguredWorkflowPriorityOrder() {
        LoanApplication app = LoanApplication.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .status(LoanStatus.AWAITING_LOAN_OFFICER)
            .requiredGuarantors(2)
            .build();

        org.mockito.Mockito.when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            new LoanProductWorkflowService.WorkflowDefinition(
                List.of(
                    ApprovalWorkflowStage.LOAN_OFFICER,
                    ApprovalWorkflowStage.MANAGER,
                    ApprovalWorkflowStage.BOARD,
                    ApprovalWorkflowStage.ACCOUNTANT,
                    ApprovalWorkflowStage.DISBURSEMENT_OFFICER
                ),
                ApprovalWorkflowStage.LOAN_OFFICER,
                true,
                2,
                true,
                1,
                true,
                3,
                false,
                1,
                1,
                1,
                true,
                4,
                true
            )
        );

        List<Map<String, Object>> items = loanPresentationService.buildProgressItems(app);

        assertThat(items).extracting(item -> item.get("label")).containsExactly(
            "Draft",
            "Awaiting Guarantors",
            "All Guarantors Approved",
            "On Review By Loan Officer",
            "On Review By Manager",
            "On Review By Board",
            "On Review By Accountant",
            "Approved For Disbursement"
        );
        assertThat(items.get(3)).containsEntry("current", true);
    }

    @Test
    void buildProgressItemsMovesLoanOfficerApprovalToNextConfiguredStage() {
        LoanApplication app = LoanApplication.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .status(LoanStatus.LOAN_OFFICER_APPROVED)
            .requiredGuarantors(0)
            .build();

        org.mockito.Mockito.when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            new LoanProductWorkflowService.WorkflowDefinition(
                List.of(
                    ApprovalWorkflowStage.LOAN_OFFICER,
                    ApprovalWorkflowStage.MANAGER,
                    ApprovalWorkflowStage.DISBURSEMENT_OFFICER
                ),
                ApprovalWorkflowStage.LOAN_OFFICER,
                true,
                2,
                true,
                1,
                false,
                3,
                0,
                0,
                false,
                4
            )
        );

        List<Map<String, Object>> items = loanPresentationService.buildProgressItems(app);

        assertThat(items).extracting(item -> item.get("label")).containsExactly(
            "Draft",
            "On Review By Loan Officer",
            "On Review By Manager",
            "Approved For Disbursement"
        );
        assertThat(items.get(2)).containsEntry("current", true);
    }

    @Test
    void parseRepaymentRowsUsesSyncedPrincipalPaymentsForOutstandingBalance() {
        String scheduleJson = """
            {
              "schedule": [
                {
                  "installmentNumber": 1,
                  "dueDate": "2026-05-30",
                  "amount": 30000.00,
                  "principalComponent": 24000.00,
                  "interestComponent": 6000.00,
                  "outstandingBalance": 270000.00,
                  "status": "UPCOMING"
                },
                {
                  "installmentNumber": 2,
                  "dueDate": "2026-06-30",
                  "amount": 30000.00,
                  "principalComponent": 25000.00,
                  "interestComponent": 5000.00,
                  "outstandingBalance": 240000.00,
                  "status": "UPCOMING"
                },
                {
                  "installmentNumber": 3,
                  "dueDate": "2026-07-30",
                  "amount": 30000.00,
                  "principalComponent": 26000.00,
                  "interestComponent": 4000.00,
                  "outstandingBalance": 210000.00,
                  "status": "UPCOMING"
                }
              ]
            }
            """;

        List<LoanPaymentTransaction> transactions = List.of(
            LoanPaymentTransaction.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(UUID.randomUUID())
                .saccoId("SACCO-1")
                .externalLoanId("1001")
                .receiptDate(LocalDate.of(2026, 5, 15))
                .principalPaid(new BigDecimal("12000.00"))
                .interestPaid(new BigDecimal("3000.00"))
                .totalPaid(new BigDecimal("15000.00"))
                .fetchedAt(OffsetDateTime.now())
                .build(),
            LoanPaymentTransaction.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(UUID.randomUUID())
                .saccoId("SACCO-1")
                .externalLoanId("1001")
                .receiptDate(LocalDate.of(2026, 7, 10))
                .principalPaid(new BigDecimal("18000.00"))
                .interestPaid(new BigDecimal("2000.00"))
                .totalPaid(new BigDecimal("20000.00"))
                .fetchedAt(OffsetDateTime.now())
                .build()
        );

        LoanPresentationService.LoanPaymentSummaryView paymentSummary = new LoanPresentationService.LoanPaymentSummaryView(
            true,
            "Personal Loan",
            LocalDate.of(2026, 7, 10),
            "2026-07-10",
            new BigDecimal("60000.00"),
            "TSh 60,000.00",
            "TSh 60,000.00",
            "TSh 0.00",
            "TSh 30,000.00",
            "TSh 5,000.00"
        );

        List<Map<String, Object>> rows = loanPresentationService.parseRepaymentRows(scheduleJson, transactions, paymentSummary);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0))
            .containsEntry("installmentNumber", "1")
            .containsEntry("pmtNo", "1")
            .containsEntry("payment", "TSh 30,000.00")
            .containsEntry("loanAmount", "TSh 24,000.00")
            .containsEntry("interest", "TSh 6,000.00")
            .containsEntry("scheduledBreakdown", "Principal: TSh 24,000.00\nInterest: TSh 6,000.00")
            .containsEntry("outstandingBalance", "")
            .containsEntry("principalPaid", "TSh 12,000.00")
            .containsEntry("interestPaid", "TSh 3,000.00")
            .containsEntry("totalPaid", "TSh 15,000.00")
            .containsEntry("paymentDate", "2026-05-15");
        assertThat(rows.get(1))
            .containsEntry("scheduledBreakdown", "Principal: TSh 25,000.00\nInterest: TSh 5,000.00")
            .containsEntry("outstandingBalance", "")
            .containsEntry("principalPaid", "-");
        assertThat(rows.get(2))
            .containsEntry("scheduledBreakdown", "Principal: TSh 26,000.00\nInterest: TSh 4,000.00")
            .containsEntry("outstandingBalance", "TSh 60,000.00")
            .containsEntry("principalPaid", "TSh 18,000.00")
            .containsEntry("paymentDate", "2026-07-10");
        assertThat(rows.get(0)).doesNotContainKey("status");
    }

    @Test
    void parseRepaymentRowsLeavesOutstandingBalanceEmptyWhenNoTransactionsExist() {
        String scheduleJson = """
            {
              "schedule": [
                {
                  "installmentNumber": 1,
                  "dueDate": "2026-05-30",
                  "amount": 30000.00,
                  "principalComponent": 30000.00
                }
              ]
            }
            """;

        List<Map<String, Object>> rows = loanPresentationService.parseRepaymentRows(
            scheduleJson,
            List.of(),
            LoanPresentationService.LoanPaymentSummaryView.empty()
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
            .containsEntry("outstandingBalance", "")
            .containsEntry("principalPaid", "-")
            .containsEntry("paymentDate", "-");
    }

    @Test
    void printablePdfUsesConfiguredReviewRolesInWorkflowOrder() throws IOException {
        UUID loanId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .applicationNumber(418L)
            .loanId("LN-00418")
            .saccoId("SACCO-1")
            .stationId("MAIN-CAMPUS")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .amount(new BigDecimal("500000.00"))
            .tenorMonths(12)
            .requiredGuarantors(2)
            .repaymentFrequency(RepaymentFrequency.MONTHLY)
            .installmentAmount(new BigDecimal("50000.00"))
            .firstRepaymentDate(LocalDate.of(2026, 7, 15))
            .submittedAt(OffsetDateTime.parse("2026-06-12T09:00:00+03:00"))
            .repaymentScheduleJson("""
                {
                  "schedule": [
                    {
                      "installmentNumber": 1,
                      "dueDate": "2026-07-31",
                      "amount": 30000.00,
                      "principalComponent": 24000.00,
                      "interestComponent": 6000.00
                    },
                    {
                      "installmentNumber": 2,
                      "dueDate": "2026-08-31",
                      "amount": 30000.00,
                      "principalComponent": 25000.00,
                      "interestComponent": 5000.00
                    }
                  ]
                }
                """)
            .applicantSignatureText("S. Applicant")
            .applicantSignatureVerifiedAt(OffsetDateTime.parse("2026-06-12T09:05:00+03:00"))
            .status(LoanStatus.AWAITING_ACCOUNTANT)
            .build();
        Member applicant = Member.builder()
            .id(UUID.randomUUID())
            .fullName("Sample Applicant")
            .memberNo("MEM-001")
            .phone("0712345678")
            .status(MemberStatus.ACTIVE)
            .signatureText("S. Applicant")
            .build();
        Member loanOfficer = Member.builder()
            .id(reviewerId)
            .fullName("Sample Loan Officer")
            .memberNo("STAFF-014")
            .signatureText("S. Loan Officer")
            .signatureRegisteredAt(OffsetDateTime.parse("2026-06-01T08:00:00+03:00"))
            .build();
        ManagerReview loanOfficerReview = ManagerReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .managerMemberId(reviewerId)
            .reviewStage(ApprovalWorkflowStage.LOAN_OFFICER)
            .decision(ManagerDecision.ACCEPT)
            .reasons("Eligibility checks completed.")
            .createdAt(OffsetDateTime.parse("2026-06-13T10:24:00+03:00"))
            .build();

        org.mockito.Mockito.when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            new LoanProductWorkflowService.WorkflowDefinition(
                List.of(ApprovalWorkflowStage.LOAN_OFFICER, ApprovalWorkflowStage.ACCOUNTANT, ApprovalWorkflowStage.DISBURSEMENT_OFFICER),
                ApprovalWorkflowStage.LOAN_OFFICER,
                false,
                2,
                true,
                1,
                false,
                3,
                0,
                0,
                true,
                4
            )
        );

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of("Member Information", "Recorded"),
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(loanOfficerReview),
            Map.of(reviewerId, loanOfficer),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                .contains("Loan Application")
                .containsSubsequence("Loan Officer", "Accountant", "Pending")
                .containsSubsequence("Applicant Declaration and Signature", "Official Staff Review and Sign-off")
                .containsSubsequence("Applicant", "Sample Applicant", "Member Number", "MEM-001")
                .contains("Membership status: Active")
                .contains("Station: MAIN-CAMPUS")
                .contains("Repayment period: 12 months")
                .contains("Sample Loan Officer")
                .contains("S. Loan Officer")
                .contains("Review Stage")
                .contains("Review Note")
                .contains("Verified At")
                .contains("Monthly Amount")
                .contains("Principal: TSh 24,000.00")
                .contains("Interest: TSh 6,000.00")
                .contains("Interest: TSh 5,000.00")
                .doesNotContain("Loan Application Review Copy")
                .doesNotContain("Signed staff record copy")
                .doesNotContain("Key details not repeated in the summary")
                .doesNotContain("Sample Loan Officer (STAFF-014)")
                .doesNotContain("Sample Applicant (MEM-001)")
                .doesNotContain("Workflow Position")
                .doesNotContain("Sequential configured approval order")
                .doesNotContain("Current stage")
                .doesNotContain("LOAN OFFICER REVIEW COMPLETED")
                .doesNotContain("Branch Manager")
                .doesNotContain("Board Committee")
                .doesNotContain("Disbursement Officer");
        }

        byte[] physicalSignaturePdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of("Member Information", "Recorded"),
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(loanOfficerReview),
            Map.of(reviewerId, loanOfficer),
            List.of(),
            Map.of(),
            null,
            false
        );
        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(physicalSignaturePdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                .contains("Loan Application")
                .contains("Signature")
                .contains("Sample Loan Officer")
                .contains("S. Applicant")
                .contains("LOAN OFFICER REVIEW COMPLETED")
                .doesNotContain("Signature copy")
                .doesNotContain("Signed staff record copy")
                .doesNotContain("S. Loan Officer")
                .doesNotContain("Review Stage")
                .doesNotContain("Physical signature")
                .doesNotContain("Signed: 2026-06-13 10:24");
        }

        loanOfficer.setSignatureRegisteredAt(null);
        byte[] unverifiedStaffSignaturePdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of("Member Information", "Recorded"),
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(loanOfficerReview),
            Map.of(reviewerId, loanOfficer),
            List.of(),
            Map.of(),
            null,
            true
        );
        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(unverifiedStaffSignaturePdf)) {
            assertThat(new PDFTextStripper().getText(document))
                .contains("Review Stage")
                .doesNotContain("S. Loan Officer");
        }
    }

    @Test
    void printablePdfIncludesGuarantorMemberNumberColumn() throws IOException {
        UUID loanId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .applicationNumber(420L)
            .loanId("LN-00420")
            .saccoId("SACCO-1")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .amount(new BigDecimal("500000.00"))
            .applicantSignatureText("A. Applicant")
            .applicantSignatureVerifiedAt(OffsetDateTime.parse("2026-06-12T09:05:00+03:00"))
            .status(LoanStatus.AWAITING_BOARD)
            .build();
        Member applicant = Member.builder()
            .id(UUID.randomUUID())
            .fullName("Sample Applicant")
            .memberNo("MEM-001")
            .build();
        GuarantorRequest guarantorRequest = GuarantorRequest.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .guarantorMemberId(guarantorId)
            .status(GuarantorRequestStatus.APPROVED)
            .guarantorSignatureText("G. Signature")
            .guarantorSignatureVerifiedAt(OffsetDateTime.parse("2026-06-13T10:00:00+03:00"))
            .build();

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of(),
            Map.of(),
            List.of(guarantorRequest),
            Map.of(guarantorId, "Guarantor Person"),
            Map.of(guarantorId, "GUA-009"),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document))
                .contains("Member Number")
                .containsSubsequence("Guarantor Person", "GUA-009", "Approved");
        }
    }

    @Test
    void printablePdfFlattensControlCharactersInBoardSignOffText() throws IOException {
        UUID loanId = UUID.randomUUID();
        UUID boardMemberId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .applicationNumber(419L)
            .loanId("LN-00419")
            .saccoId("SACCO-1")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .amount(new BigDecimal("500000.00"))
            .status(LoanStatus.AWAITING_BOARD)
            .build();
        Member applicant = Member.builder()
            .id(UUID.randomUUID())
            .fullName("Sample Applicant")
            .memberNo("MEM-001")
            .build();
        Member boardMember = Member.builder()
            .id(boardMemberId)
            .fullName("Board Reviewer")
            .memberNo("STAFF-020")
            .build();
        BoardReview boardReview = BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .boardMemberId(boardMemberId)
            .reviewStage(ApprovalWorkflowStage.BOARD)
            .decision(BoardDecision.APPROVED)
            .comment("Approved after review.\nAll requirements met.")
            .boardSignatureText("B. Reviewer\nBoard Member")
            .boardSignatureVerifiedAt(OffsetDateTime.parse("2026-06-14T10:24:00+03:00"))
            .decidedAt(OffsetDateTime.parse("2026-06-14T10:24:00+03:00"))
            .createdAt(OffsetDateTime.parse("2026-06-14T10:00:00+03:00"))
            .build();

        org.mockito.Mockito.when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            new LoanProductWorkflowService.WorkflowDefinition(
                List.of(ApprovalWorkflowStage.BOARD, ApprovalWorkflowStage.DISBURSEMENT_OFFICER),
                ApprovalWorkflowStage.BOARD,
                false,
                2,
                false,
                1,
                true,
                3,
                false,
                1,
                1,
                1,
                false,
                4,
                true
            )
        );

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(boardReview),
            Map.of(boardMemberId, boardMember),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document))
                .containsSubsequence("B. Reviewer", "Board Member")
                .doesNotContain("Verified:");
        }

        boardReview.setBoardSignatureVerifiedAt(null);
        byte[] unverifiedBoardSignaturePdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(boardReview),
            Map.of(boardMemberId, boardMember),
            null,
            true
        );
        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(unverifiedBoardSignaturePdf)) {
            assertThat(new PDFTextStripper().getText(document))
                .contains("Review Stage")
                .doesNotContain("B. Reviewer Board Member");
        }
    }
}
