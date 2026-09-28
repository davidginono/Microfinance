package com.sacco.mvp.service;

import tools.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanApplication;
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
import org.apache.pdfbox.rendering.PDFRenderer;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanPresentationServiceTest {

    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private com.sacco.mvp.repository.MemberRepository memberRepository;
    @Mock private com.sacco.mvp.repository.LoanApplicationRepository loanApplicationRepository;
    @Mock private com.sacco.mvp.repository.GuarantorRequestRepository guarantorRequestRepository;
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
        messageSource.addMessage("loan.status.AWAITING_CREDIT_COMMITTEE", Locale.ENGLISH, "On Review By Credit Committee");
        messageSource.addMessage("loan.status.CREDIT_COMMITTEE_REJECTED", Locale.ENGLISH, "Credit Committee Rejected");
        messageSource.addMessage("loan.status.AWAITING_ACCOUNTANT", Locale.ENGLISH, "On Review By Accountant");
        messageSource.addMessage("loan.status.READY_FOR_DISBURSEMENT", Locale.ENGLISH, "Approved For Disbursement");
        loanPresentationService = new LoanPresentationService(
            JsonMapper.builder().findAndAddModules().build(),
            managerReviewRepository,
            boardReviewRepository,
            memberRepository,
            loanApplicationRepository,
            guarantorRequestRepository,
            loanAttachmentService,
            loanProductWorkflowService,
            messageSource
        );
    }

    @Test
    void previousApprovedReviewsShowsOnlyApprovedEarlierStages() {
        UUID loanId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID boardMemberId = UUID.randomUUID();
        UUID pendingLoanOfficerId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-1")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .status(LoanStatus.AWAITING_ACCOUNTANT)
            .build();
        org.mockito.Mockito.when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            workflow(ApprovalWorkflowStage.MANAGER, ApprovalWorkflowStage.LOAN_OFFICER,
                ApprovalWorkflowStage.BOARD, ApprovalWorkflowStage.ACCOUNTANT,
                ApprovalWorkflowStage.DISBURSEMENT_OFFICER)
        );
        org.mockito.Mockito.when(managerReviewRepository.findByLoanApplicationIdOrderByCreatedAtAsc(loanId)).thenReturn(List.of(
            ManagerReview.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(loanId)
                .managerMemberId(managerId)
                .reviewStage(ApprovalWorkflowStage.MANAGER)
                .decision(ManagerDecision.ACCEPT)
                .reasons("Manager checks passed.")
                .createdAt(OffsetDateTime.parse("2026-06-10T09:00:00+03:00"))
                .build()
        ));
        org.mockito.Mockito.when(boardReviewRepository.findByLoanApplicationId(loanId)).thenReturn(List.of(
            BoardReview.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(loanId)
                .boardMemberId(pendingLoanOfficerId)
                .reviewStage(ApprovalWorkflowStage.LOAN_OFFICER)
                .decision(BoardDecision.PENDING)
                .createdAt(OffsetDateTime.parse("2026-06-10T10:00:00+03:00"))
                .build(),
            BoardReview.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(loanId)
                .boardMemberId(boardMemberId)
                .reviewStage(ApprovalWorkflowStage.BOARD)
                .decision(BoardDecision.APPROVED)
                .comment("Board approved.")
                .createdAt(OffsetDateTime.parse("2026-06-11T10:00:00+03:00"))
                .decidedAt(OffsetDateTime.parse("2026-06-11T11:00:00+03:00"))
                .build()
        ));
        org.mockito.Mockito.when(memberRepository.findAllById(org.mockito.ArgumentMatchers.anyIterable())).thenReturn(List.of(
            Member.builder().id(managerId).fullName("Branch Manager").memberNo("M-001").build(),
            Member.builder().id(boardMemberId).fullName("Board Member").memberNo("B-001").build()
        ));

        List<LoanPresentationService.ApprovedReviewSummary> rows =
            loanPresentationService.previousApprovedReviews(app, ApprovalWorkflowStage.ACCOUNTANT);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(LoanPresentationService.ApprovedReviewSummary::getStageLabel)
            .containsExactly("Branch Manager", "Board Member");
        assertThat(rows).extracting(LoanPresentationService.ApprovedReviewSummary::getReviewerName)
            .containsExactly("Branch Manager", "Board Member");
    }

    @Test
    void previousApprovedReviewsIsEmptyAtFirstWorkflowStage() {
        LoanApplication app = LoanApplication.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .status(LoanStatus.READY_FOR_MANAGER)
            .build();
        org.mockito.Mockito.when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            workflow(ApprovalWorkflowStage.MANAGER, ApprovalWorkflowStage.BOARD,
                ApprovalWorkflowStage.DISBURSEMENT_OFFICER)
        );

        assertThat(loanPresentationService.previousApprovedReviews(app, ApprovalWorkflowStage.MANAGER)).isEmpty();
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
            .containsEntry("Insurance Fee (1.5%)", "TSh 2,250")
            .containsEntry("Loan Processing Fee (2.5%)", "TSh 3,750")
            .containsEntry("Loan Amount (TZS)", "TSh 125,000")
            .containsEntry("Loan Amount + Interest (TZS)", "TSh 137,000");
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
            .containsEntry("Loan Amount", "TSh 120,000")
            .containsEntry("Annual Interest Rate", "12.00%")
            .containsEntry("Loan Period in Years", "1 year")
            .containsEntry("Number of Payments", 12)
            .containsEntry("Estimated Installment", "TSh 11,200")
            .containsEntry("Total Interest", "TSh 14,400")
            .containsEntry("Total Principal", "TSh 120,000")
            .containsEntry("Total Amount", "TSh 134,400")
            .containsEntry("Estimated Total Repayment", "TSh 134,400");
    }

    @Test
    void parseRepaymentSummaryAddsScheduleTotals() {
        String scheduleJson = """
            {
              "installmentAmount": 30000.00,
              "schedule": [
                {"amount": 30000.00, "principalComponent": 24000.00, "interestComponent": 6000.00},
                {"amount": 30000.00, "principalComponent": 25000.00, "interestComponent": 5000.00},
                {"amount": 30000.00, "principalComponent": 26000.00, "interestComponent": 4000.00}
              ]
            }
            """;

        Map<String, Object> summary = loanPresentationService.parseRepaymentSummary(scheduleJson);

        assertThat(summary)
            .containsEntry("Total Interest", "TSh 15,000")
            .containsEntry("Total Principal", "TSh 75,000")
            .containsEntry("Total Amount", "TSh 90,000");
    }

    @Test
    void reviewRepaymentSummaryUsesCalculatorTotalsWhenStoredScheduleIsStale() {
        LoanApplication app = LoanApplication.builder()
            .amount(new BigDecimal("700000.00"))
            .repaymentScheduleJson("""
                {
                  "installmentAmount": 122500.00,
                  "schedule": [
                    {"amount": 122500.00, "principalComponent": 116666.67, "interestComponent": 5833.33},
                    {"amount": 122500.00, "principalComponent": 116666.67, "interestComponent": 5833.33},
                    {"amount": 122500.00, "principalComponent": 116666.67, "interestComponent": 5833.33},
                    {"amount": 122500.00, "principalComponent": 116666.67, "interestComponent": 5833.33},
                    {"amount": 122500.00, "principalComponent": 116666.67, "interestComponent": 5833.33},
                    {"amount": 122500.00, "principalComponent": 116666.65, "interestComponent": 5833.35}
                  ]
                }
                """)
            .financialSnapshot("""
                {
                  "interestMethod": "FLAT_RATE",
                  "interestRate": 0.10,
                  "interestAmount": 70000.00,
                  "principalPlusInterest": 770000.00
                }
                """)
            .build();

        Map<String, Object> summary = loanPresentationService.reviewRepaymentSummary(app);

        assertThat(summary)
            .containsEntry("Total Interest", "TSh 70,000")
            .containsEntry("Total Principal", "TSh 700,000")
            .containsEntry("Total Amount", "TSh 770,000");
    }

    @Test
    void activeLoanOutstandingBalanceIncludesContractualInterest() {
        LoanApplication app = LoanApplication.builder()
            .amount(new BigDecimal("700000.00"))
            .status(LoanStatus.DISBURSED)
            .financialSnapshot("""
                {
                  "interestAmount": 70000.00,
                  "principalPlusInterest": 770000.00
                }
                """)
            .build();

        assertThat(loanPresentationService.activeLoanOutstandingBalance(app))
            .isEqualByComparingTo("770000.00");
    }

    @Test
    void activeLoanOutstandingBalancePrefersLocalPaymentSummaryValues() {
        LoanApplication app = LoanApplication.builder()
            .amount(new BigDecimal("500000.00"))
            .status(LoanStatus.DISBURSED)
            .financialSnapshot("""
                {
                  "interestAmount": 25000.00,
                  "principalPlusInterest": 525000.00,
                  "paymentSummaryTotalOutstanding": 445000.00,
                  "paymentSummaryOutstandingPrincipal": 425000.00,
                  "paymentSummaryOutstandingInterest": 20000.00,
                  "paymentSummaryTotalPrincipalPaid": 75000.00,
                  "paymentSummaryTotalInterestPaid": 5000.00,
                  "paymentSummaryLastPaymentDate": "2024-04-10"
                }
                """)
            .build();

        assertThat(loanPresentationService.activeLoanOutstandingBalance(app))
            .isEqualByComparingTo("445000.00");
        assertThat(loanPresentationService.activeLoanOutstandingPrincipal(app))
            .isEqualByComparingTo("425000.00");
        assertThat(loanPresentationService.activeLoanOutstandingInterest(app))
            .isEqualByComparingTo("20000.00");
        assertThat(loanPresentationService.activeLoanPaidAmount(app))
            .isEqualByComparingTo("80000.00");
        assertThat(loanPresentationService.activeLoanLastPaymentDateLabel(app))
            .isEqualTo("2024-04-10");
    }

    @Test
    void activeLoanOutstandingBalanceRecalculatesTotalForReleasedPrincipal() {
        LoanApplication app = LoanApplication.builder()
            .amount(new BigDecimal("700000.00"))
            .status(LoanStatus.DISBURSED)
            .financialSnapshot("""
                {
                  "principalAmount": 800000.00,
                  "interestAmount": 70000.00,
                  "principalPlusInterest": 870000.00
                }
                """)
            .build();

        assertThat(loanPresentationService.activeLoanOutstandingBalance(app))
            .isEqualByComparingTo("770000.00");
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
            .containsEntry("beginningBalance", "TSh 134,400")
            .containsEntry("payment", "TSh 44,800")
            .containsEntry("loanAmount", "TSh 40,000")
            .containsEntry("interest", "TSh 4,800")
            .containsEntry("endingBalance", "TSh 89,600");
        assertThat(rows.getFirst().get("scheduledBreakdown").toString())
            .contains("Loan Amount: TSh 40,000")
            .contains("Interest: TSh 4,800");
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
    void buildProgressItemsMarksRejectedStageAndClosesFutureStages() {
        LoanApplication app = LoanApplication.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .status(LoanStatus.CREDIT_COMMITTEE_REJECTED)
            .requiredGuarantors(0)
            .build();

        when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            workflow(
                ApprovalWorkflowStage.MANAGER,
                ApprovalWorkflowStage.CREDIT_COMMITTEE,
                ApprovalWorkflowStage.ACCOUNTANT,
                ApprovalWorkflowStage.DISBURSEMENT_OFFICER
            )
        );

        List<Map<String, Object>> items = loanPresentationService.buildProgressItems(app);

        assertThat(items).extracting(item -> item.get("label")).containsExactly(
            "Draft",
            "On Review By Manager",
            "Credit Committee Rejected",
            "On Review By Accountant",
            "Approved For Disbursement"
        );
        assertThat(items).extracting(item -> item.get("state")).containsExactly(
            "completed",
            "completed",
            "rejected",
            "closed",
            "closed"
        );
        assertThat(items.get(2))
            .containsEntry("current", true)
            .containsEntry("active", false);
        assertThat(items.get(3))
            .containsEntry("current", false)
            .containsEntry("active", false);
    }

    @Test
    void calculatedRepaymentRowsUseSnapshotBreakdownWhenStoredScheduleComponentsAreStale() {
        LoanApplication app = LoanApplication.builder()
            .amount(new BigDecimal("700000.00"))
            .tenorMonths(6)
            .financialSnapshot("""
                {
                  "interestMethod": "FLAT_RATE",
                  "interestRate": 0.10,
                  "interestAmount": 66000.00,
                  "monthlyRepaymentAmount": 127666.67
                }
                """)
            .repaymentScheduleJson("""
                {
                  "schedule": [
                    {
                      "installmentNumber": 1,
                      "dueDate": "2026-08-31",
                      "amount": 122500.00,
                      "principalComponent": 116666.67,
                      "interestComponent": 5833.33
                    },
                    {
                      "installmentNumber": 2,
                      "dueDate": "2026-09-30",
                      "amount": 122500.00,
                      "principalComponent": 116666.67,
                      "interestComponent": 5833.33
                    },
                    {
                      "installmentNumber": 3,
                      "dueDate": "2026-10-31",
                      "amount": 122500.00,
                      "principalComponent": 116666.67,
                      "interestComponent": 5833.33
                    },
                    {
                      "installmentNumber": 4,
                      "dueDate": "2026-11-30",
                      "amount": 122500.00,
                      "principalComponent": 116666.67,
                      "interestComponent": 5833.33
                    },
                    {
                      "installmentNumber": 5,
                      "dueDate": "2026-12-31",
                      "amount": 122500.00,
                      "principalComponent": 116666.67,
                      "interestComponent": 5833.33
                    },
                    {
                      "installmentNumber": 6,
                      "dueDate": "2027-01-31",
                      "amount": 122500.00,
                      "principalComponent": 116666.65,
                      "interestComponent": 5833.35
                    }
                  ]
                }
                """)
            .build();

        List<Map<String, Object>> rows = loanPresentationService.calculatedRepaymentRows(app);

        assertThat(rows).hasSize(6);
        assertThat(rows.getFirst())
            .containsEntry("amount", "TSh 127,666.67")
            .containsEntry("loanAmount", "TSh 116,666.67")
            .containsEntry("interest", "TSh 11,000");
        assertThat(rows.getFirst().get("scheduledBreakdown").toString())
            .contains("Loan Amount: TSh 116,666.67")
            .contains("Interest: TSh 11,000");
    }

    @Test
    void parseRepaymentRowsUsesStoredScheduleWithoutPaymentRecordFields() {
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

        List<Map<String, Object>> rows = loanPresentationService.parseRepaymentRows(scheduleJson);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
            .containsEntry("installmentNumber", "1")
            .containsEntry("payment", "TSh 30,000")
            .containsEntry("loanAmount", "TSh 30,000")
            .containsEntry("scheduledBreakdown", "Principal: TSh 30,000")
            .doesNotContainKey("outstandingBalance")
            .doesNotContainKey("principalPaid")
            .doesNotContainKey("paymentDate")
            .doesNotContainKey("paymentStatus");
    }

    @Test
    void parseRepaymentRowsAlignsStoredBeginningAndEndingBalances() {
        String scheduleJson = """
            {
              "schedule": [
                {
                  "installmentNumber": 1,
                  "dueDate": "2026-09-13",
                  "amount": 64166.67,
                  "principalComponent": 58333.33,
                  "interestComponent": 5833.33,
                  "outstandingBalance": 705833.33
                },
                {
                  "installmentNumber": 2,
                  "dueDate": "2026-10-13",
                  "amount": 64166.66,
                  "principalComponent": 58333.33,
                  "interestComponent": 5833.33,
                  "outstandingBalance": 641666.67
                }
              ]
            }
            """;

        List<Map<String, Object>> rows = loanPresentationService.parseRepaymentRows(scheduleJson);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst())
            .containsEntry("beginningBalance", "TSh 770,000")
            .containsEntry("endingBalance", "TSh 705,833.33");
        assertThat(rows.get(1))
            .containsEntry("beginningBalance", "TSh 705,833.33")
            .containsEntry("endingBalance", "TSh 641,666.67");
    }

    @Test
    void printablePdfIncludesCalculatedRepaymentScheduleWhenStoredScheduleIsMissing() throws IOException {
        LoanApplication app = LoanApplication.builder()
            .id(UUID.randomUUID())
            .applicationNumber(501L)
            .saccoId("SACCO-1")
            .stationId("AR704")
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal("2000000.00"))
            .tenorMonths(12)
            .requiredGuarantors(1)
            .financialSnapshot("""
                {
                  "interestRate": 0.10,
                  "interestMethod": "FLAT_RATE",
                  "interestAmount": 200000.00,
                  "monthlyRepaymentAmount": 183333.33,
                  "principalPlusInterest": 2200000.00
                }
                """)
            .applicantSignatureText("D. Applicant")
            .applicantSignatureVerifiedAt(OffsetDateTime.parse("2026-06-30T04:57:00+03:00"))
            .status(LoanStatus.READY_FOR_MANAGER)
            .build();
        Member applicant = Member.builder()
            .id(UUID.randomUUID())
            .fullName("David Wankyo")
            .memberNo("1145")
            .signatureText("D. Applicant")
            .build();

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of("Loan Purpose", "SCHOOL FEES"),
            loanPresentationService.parseFinancialFields(app),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                .contains("Loan Calculation Details")
                .contains("Repayment Schedule")
                .contains("No.")
                .contains("Beginning")
                .contains("Balance")
                .contains("Amount to Pay")
                .contains("Monthly Repayment Amount (TZS)")
                .contains("Month 1")
                .contains("TSh 2,000,000")
                .doesNotContain("Financial Details")
                .doesNotContain("Repayment Timetable")
                .doesNotContain("No repayment schedule available.");
        }
    }

    @Test
    void printablePdfAppendsStoredApplicationAttachmentContents() throws IOException {
        UUID loanId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        String attachmentsJson = "[{\"id\":\"" + attachmentId + "\"}]";
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .applicationNumber(502L)
            .saccoId("SACCO-1")
            .stationId("AR704")
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal("500000.00"))
            .tenorMonths(6)
            .requiredGuarantors(0)
            .financialSnapshot("{}")
            .attachmentsJson(attachmentsJson)
            .applicantSignatureText("D. Applicant")
            .applicantSignatureVerifiedAt(OffsetDateTime.parse("2026-06-30T04:57:00+03:00"))
            .status(LoanStatus.READY_FOR_MANAGER)
            .build();
        Member applicant = Member.builder()
            .id(UUID.randomUUID())
            .fullName("David Wankyo")
            .memberNo("1145")
            .signatureText("D. Applicant")
            .build();
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("id", attachmentId.toString());
        attachment.put("originalName", "salary-slip.png");
        attachment.put("attachmentCategory", LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT);
        attachment.put("size", 68);
        when(loanAttachmentService.parse(attachmentsJson)).thenReturn(List.of(attachment));
        when(loanAttachmentService.load(loanId, attachmentId.toString(), attachmentsJson))
            .thenReturn(new LoanAttachmentService.AttachmentResource(tinyPng(), "salary-slip.png", "image/png"));

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of("Loan Purpose", "SCHOOL FEES"),
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                .contains("Application Attachments")
                .contains("Attachment Contents")
                .contains("salary-slip.png");
        }
    }

    @Test
    void printablePdfRepeatsApplicationReferenceOnEveryGeneratedPage() throws IOException {
        LoanApplication app = basicPrintableApplication();
        Member applicant = basicApplicant();
        Map<String, Object> formFields = new LinkedHashMap<>();
        for (int index = 1; index <= 90; index++) {
            formFields.put("Supporting Detail " + index, "Recorded value for application reference footer check " + index);
        }

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            formFields,
            loanPresentationService.parseFinancialFields(app),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                assertThat(countOccurrences(stripper.getText(document), "Application Reference: 503"))
                    .isGreaterThanOrEqualTo(2);
            }
        }
    }

    @Test
    void printablePdfOmitsRequestedFacilityFromApplicationSummary() throws IOException {
        LoanApplication app = basicPrintableApplication();
        Member applicant = basicApplicant();

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of("Loan Purpose", "SCHOOL FEES"),
            loanPresentationService.parseFinancialFields(app),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                .contains("Application Summary")
                .contains("Requested Amount")
                .contains("Current State")
                .doesNotContain("Requested Facility")
                .doesNotContain("Education Loan");
        }
    }

    @Test
    void printablePdfIncludesGeneratedAndCalculatedSchedulesForDisbursedLoan() throws IOException {
        LoanApplication app = LoanApplication.builder()
            .id(UUID.randomUUID())
            .applicationNumber(502L)
            .saccoId("SACCO-1")
            .stationId("AR704")
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal("2000000.00"))
            .tenorMonths(2)
            .requiredGuarantors(1)
            .financialSnapshot("""
                {
                  "interestRate": 0.10,
                  "interestMethod": "FLAT_RATE",
                  "interestAmount": 33333.34,
                  "monthlyRepaymentAmount": 1016666.67,
                  "principalPlusInterest": 2033333.34,
                  "applicationFee": 15000.00,
                  "insuranceRate": 0.015,
                  "insuranceFee": 30000.00,
                  "processingFeeRate": 0.015,
                  "processingFee": 30000.00,
                  "totalDeductions": 75000.00
                }
                """)
            .repaymentScheduleJson("""
                {
                  "schedule": [
                    {
                      "installmentNumber": 1,
                      "dueDate": "2026-07-31",
                      "amount": 1016666.67,
                      "principalComponent": 1000000.00,
                      "interestComponent": 16666.67
                    }
                  ]
                }
                """)
            .applicantSignatureText("D. Applicant")
            .applicantSignatureVerifiedAt(OffsetDateTime.parse("2026-06-30T04:57:00+03:00"))
            .status(LoanStatus.DISBURSED)
            .build();
        Member applicant = Member.builder()
            .id(UUID.randomUUID())
            .fullName("David Wankyo")
            .memberNo("1145")
            .signatureText("D. Applicant")
            .build();

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of("Loan Purpose", "SCHOOL FEES"),
            loanPresentationService.parseFinancialFields(app),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                .contains("Loan Calculation Details")
                .contains("Application Fee (TZS)")
                .contains("Total Fees (TZS)")
                .contains("Repayment Schedule")
                .doesNotContain("Record of Payments")
                .doesNotContain("Financial Details")
                .doesNotContain("Repayment Timetable");
        }
    }

    @Test
    void printablePdfDrawsSaccoLogoWhenLogoBytesAreSupplied() throws IOException {
        LoanApplication app = basicPrintableApplication();
        Member applicant = basicApplicant();

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            null,
            sampleLogoPng(),
            Map.of("Loan Purpose", "SCHOOL FEES"),
            loanPresentationService.parseFinancialFields(app),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            int imageCount = 0;
            for (org.apache.pdfbox.cos.COSName name : document.getPage(0).getResources().getXObjectNames()) {
                if (document.getPage(0).getResources().getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                    imageCount++;
                }
            }
            assertThat(imageCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void printablePdfAlignsSaccoTitleWithLogo() throws IOException {
        LoanApplication app = basicPrintableApplication();
        Member applicant = basicApplicant();

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            null,
            sampleLogoPng(),
            Map.of("Loan Purpose", "SCHOOL FEES"),
            loanPresentationService.parseFinancialFields(app),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            java.awt.image.BufferedImage pageImage = new PDFRenderer(document).renderImageWithDPI(0, 72);
            Bounds logoBounds = detectHeaderLogoBounds(pageImage);
            Bounds titleBounds = detectHeaderTitleBounds(pageImage, logoBounds);

            assertThat(Math.abs(titleBounds.centerY() - logoBounds.centerY())).isLessThanOrEqualTo(2.5f);
            assertThat(titleBounds.left).isGreaterThan(logoBounds.right);
            assertThat(titleBounds.left - logoBounds.right).isBetween(6f, 16f);
        }
    }

    @Test
    void printablePdfDrawsDefaultWatermarkWhenNoSaccoLogoIsSupplied() throws IOException {
        LoanApplication app = basicPrintableApplication();
        Member applicant = basicApplicant();

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            null,
            null,
            Map.of("Loan Purpose", "SCHOOL FEES"),
            loanPresentationService.parseFinancialFields(app),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            int imageCount = 0;
            for (org.apache.pdfbox.cos.COSName name : document.getPage(0).getResources().getXObjectNames()) {
                if (document.getPage(0).getResources().getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                    imageCount++;
                }
            }
            assertThat(imageCount).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void printablePdfStillRendersWhenSaccoLogoBytesAreInvalid() throws IOException {
        LoanApplication app = basicPrintableApplication();
        Member applicant = basicApplicant();

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            null,
            "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            Map.of("Loan Purpose", "SCHOOL FEES"),
            loanPresentationService.parseFinancialFields(app),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            List.of(),
            Map.of(),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document))
                .contains("Loan Application")
                .contains("IAA SACCOS LTD");
        }
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
        BoardReview loanOfficerReview = BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .boardMemberId(reviewerId)
            .reviewStage(ApprovalWorkflowStage.LOAN_OFFICER)
            .decision(BoardDecision.APPROVED)
            .comment("Eligibility checks completed.")
            .boardSignatureText("S. Loan Officer")
            .boardSignatureVerifiedAt(OffsetDateTime.parse("2026-06-13T10:24:00+03:00"))
            .decidedAt(OffsetDateTime.parse("2026-06-13T10:24:00+03:00"))
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
            List.<ManagerReview>of(),
            Map.of(),
            List.of(loanOfficerReview),
            Map.of(reviewerId, loanOfficer),
            null,
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                .contains("Loan Application")
                .containsSubsequence("Loan Officer", "Accountant", "Pending")
                .containsSubsequence("Applicant Declaration and Signature", "Loan Committee")
                .containsSubsequence("Applicant", "Sample Applicant", "Member Number", "MEM-001")
                .contains("Membership status: Active")
                .contains("Station: MAIN-CAMPUS")
                .contains("Repayment period: 12 months")
                .contains("Sample Loan Officer")
                .contains("S. Loan Officer")
                .contains("Review Stage")
                .contains("Review Note")
                .contains("Verified At")
                .contains("Amount to Pay")
                .contains("TSh 24,000")
                .contains("TSh 6,000")
                .contains("TSh 5,000")
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
            List.<ManagerReview>of(),
            Map.of(),
            List.of(loanOfficerReview),
            Map.of(reviewerId, loanOfficer),
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
                .contains("LOAN OFFICER REVIEW RECORDED")
                .doesNotContain("Signature copy")
                .doesNotContain("Signed staff record copy")
                .doesNotContain("S. Loan Officer")
                .doesNotContain("Review Stage")
                .doesNotContain("Physical signature")
                .doesNotContain("Signed: 2026-06-13 10:24");
        }

        loanOfficer.setSignatureRegisteredAt(null);
        loanOfficerReview.setBoardSignatureVerifiedAt(null);
        byte[] unverifiedStaffSignaturePdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            Map.of("Member Information", "Recorded"),
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.<ManagerReview>of(),
            Map.of(),
            List.of(loanOfficerReview),
            Map.of(reviewerId, loanOfficer),
            null,
            true
        );
        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(unverifiedStaffSignaturePdf)) {
            assertThat(new PDFTextStripper().getText(document))
                .contains("Review Stage")
                .contains("S. Loan Officer");
        }
    }

    @Test
    void printablePdfMarksFutureReviewStagesClosedAfterRejection() throws IOException {
        UUID loanId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID committeeId = UUID.randomUUID();
        LoanApplication app = basicPrintableApplication();
        app.setId(loanId);
        app.setRequiredGuarantors(0);
        app.setStatus(LoanStatus.CREDIT_COMMITTEE_REJECTED);
        Member applicant = basicApplicant();
        Member manager = Member.builder()
            .id(managerId)
            .fullName("Branch Manager")
            .memberNo("BM-001")
            .signatureText("Branch M")
            .build();
        Member committeeMember = Member.builder()
            .id(committeeId)
            .fullName("Credit Member")
            .memberNo("CC-001")
            .signatureText("Credit M")
            .build();
        ManagerReview managerReview = ManagerReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .managerMemberId(managerId)
            .reviewStage(ApprovalWorkflowStage.MANAGER)
            .decision(ManagerDecision.ACCEPT)
            .reasons("Application details confirmed")
            .managerSignatureText("Branch M")
            .managerSignatureVerifiedAt(OffsetDateTime.parse("2026-07-21T05:38:00+03:00"))
            .createdAt(OffsetDateTime.parse("2026-07-21T05:38:00+03:00"))
            .build();
        BoardReview committeeReview = BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .boardMemberId(committeeId)
            .reviewStage(ApprovalWorkflowStage.CREDIT_COMMITTEE)
            .decision(BoardDecision.REJECTED)
            .comment("TOO HIGH")
            .boardSignatureText("Credit M")
            .boardSignatureVerifiedAt(OffsetDateTime.parse("2026-07-29T07:05:00+03:00"))
            .decidedAt(OffsetDateTime.parse("2026-07-29T07:05:00+03:00"))
            .createdAt(OffsetDateTime.parse("2026-07-29T07:05:00+03:00"))
            .build();
        when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(
            workflow(
                ApprovalWorkflowStage.MANAGER,
                ApprovalWorkflowStage.CREDIT_COMMITTEE,
                ApprovalWorkflowStage.ACCOUNTANT,
                ApprovalWorkflowStage.DISBURSEMENT_OFFICER
            )
        );

        byte[] pdf = loanPresentationService.buildPrintablePdf(
            app,
            "IAA SACCOS LTD",
            applicant,
            null,
            null,
            Map.of(),
            Map.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(managerReview),
            Map.of(managerId, manager),
            List.of(committeeReview),
            Map.of(committeeId, committeeMember),
            "TOO HIGH",
            true
        );

        try (org.apache.pdfbox.pdmodel.PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                .containsSubsequence("Branch Manager", "Approved")
                .containsSubsequence("Credit Committee", "Rejected")
                .containsSubsequence("Accountant", "Closed")
                .doesNotContain("Accountant\n-\nPending");
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
    void printablePdfShowsSavedGuarantorSignatureInsteadOfApprovalMethod() throws IOException {
        UUID loanId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .applicationNumber(421L)
            .loanId("LN-00421")
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
        Member guarantor = Member.builder()
            .id(guarantorId)
            .fullName("Guarantor Person")
            .memberNo("GUA-009")
            .signatureText("G. Saved Signature")
            .build();
        GuarantorRequest guarantorRequest = GuarantorRequest.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .guarantorMemberId(guarantorId)
            .status(GuarantorRequestStatus.APPROVED)
            .guarantorSignatureText("Approved by guarantor OTP")
            .guarantorSignatureVerifiedAt(OffsetDateTime.parse("2026-06-13T10:00:00+03:00"))
            .build();
        when(memberRepository.findAllById(org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(guarantor));

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
                .contains("G. Saved Signature")
                .doesNotContain("Approved by guarantor OTP");
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

    private LoanApplication basicPrintableApplication() {
        return LoanApplication.builder()
            .id(UUID.randomUUID())
            .applicationNumber(503L)
            .saccoId("SACCO-1")
            .stationId("AR704")
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal("2000000.00"))
            .tenorMonths(2)
            .requiredGuarantors(1)
            .financialSnapshot("""
                {
                  "interestRate": 0.10,
                  "interestMethod": "FLAT_RATE",
                  "interestAmount": 33333.34,
                  "monthlyRepaymentAmount": 1016666.67,
                  "principalPlusInterest": 2033333.34
                }
                """)
            .applicantSignatureText("D. Applicant")
            .applicantSignatureVerifiedAt(OffsetDateTime.parse("2026-06-30T04:57:00+03:00"))
            .status(LoanStatus.READY_FOR_MANAGER)
            .build();
    }

    private LoanApplication repaymentApplication(LocalDate finalDueDate, LoanStatus status) {
        return LoanApplication.builder()
            .id(UUID.randomUUID())
            .applicationNumber(1001L)
            .saccoId("SACCO-1")
            .stationId("AR704")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("120000.00"))
            .tenorMonths(12)
            .finalDueDate(finalDueDate)
            .status(status)
            .repaymentScheduleJson("""
                {
                  "schedule": [
                    {"installmentNumber": 1, "dueDate": "2026-01-31", "amount": 10000.00, "principalComponent": 9000.00, "interestComponent": 1000.00},
                    {"installmentNumber": 2, "dueDate": "2026-02-28", "amount": 10000.00, "principalComponent": 9000.00, "interestComponent": 1000.00},
                    {"installmentNumber": 3, "dueDate": "2026-03-31", "amount": 10000.00, "principalComponent": 9000.00, "interestComponent": 1000.00},
                    {"installmentNumber": 4, "dueDate": "2026-04-30", "amount": 10000.00, "principalComponent": 9000.00, "interestComponent": 1000.00},
                    {"installmentNumber": 5, "dueDate": "2026-05-31", "amount": 10000.00, "principalComponent": 9000.00, "interestComponent": 1000.00}
                  ]
                }
                """)
            .build();
    }

    private Member basicApplicant() {
        return Member.builder()
            .id(UUID.randomUUID())
            .fullName("David Wankyo")
            .memberNo("1145")
            .signatureText("D. Applicant")
            .build();
    }

    private byte[] sampleLogoPng() throws IOException {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(80, 80, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, 80, 80);
            graphics.setColor(new java.awt.Color(41, 52, 127));
            graphics.fillOval(8, 8, 64, 64);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(34, 18, 12, 44);
        } finally {
            graphics.dispose();
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] tinyPng() throws IOException {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(24, 24, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, 24, 24);
            graphics.setColor(new java.awt.Color(22, 101, 52));
            graphics.fillRect(4, 4, 16, 16);
        } finally {
            graphics.dispose();
        }
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private Bounds detectHeaderLogoBounds(java.awt.image.BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        int headerWidth = Math.min(image.getWidth(), 130);
        int headerHeight = Math.min(image.getHeight(), 95);
        for (int y = 0; y < headerHeight; y++) {
            for (int x = 0; x < headerWidth; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                if (red < 80 && green < 95 && blue > 95 && blue > red + 25 && blue > green + 20) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertThat(maxX).as("header logo pixels were rendered").isGreaterThanOrEqualTo(0);
        return new Bounds(minX, minY, maxX + 1f, maxY + 1f);
    }

    private Bounds detectHeaderTitleBounds(java.awt.image.BufferedImage image, Bounds logoBounds) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        int startX = Math.min(image.getWidth(), (int) Math.ceil(logoBounds.right) + 4);
        int endX = Math.min(image.getWidth(), 280);
        int startY = Math.max(0, (int) Math.floor(logoBounds.top) - 24);
        int endY = Math.min(image.getHeight(), (int) Math.ceil(logoBounds.bottom) + 24);
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                if (red < 145 && green < 155 && blue < 170) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        assertThat(maxX).as("header title pixels were rendered").isGreaterThanOrEqualTo(0);
        return new Bounds(minX, minY, maxX + 1f, maxY + 1f);
    }

    private static final class Bounds {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private float centerY() {
            return top + ((bottom - top) / 2f);
        }
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private LoanProductWorkflowService.WorkflowDefinition workflow(ApprovalWorkflowStage... stages) {
        return new LoanProductWorkflowService.WorkflowDefinition(
            List.of(stages),
            stages[0],
            true,
            1,
            true,
            2,
            true,
            3,
            false,
            4,
            0,
            0,
            true,
            5,
            true
        );
    }
}
