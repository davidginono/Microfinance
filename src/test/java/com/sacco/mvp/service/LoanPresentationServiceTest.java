package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.LoanPaymentTransaction;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LoanPresentationServiceTest {

    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private LoanAttachmentService loanAttachmentService;
    @Mock private LoanProductSettingRepository loanProductSettingRepository;

    private LoanPresentationService loanPresentationService;

    @BeforeEach
    void setUp() {
        loanPresentationService = new LoanPresentationService(
            JsonMapper.builder().findAndAddModules().build(),
            managerReviewRepository,
            loanAttachmentService,
            loanProductSettingRepository
        );
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
                  "principalComponent": 30000.00,
                  "outstandingBalance": 270000.00,
                  "status": "UPCOMING"
                },
                {
                  "installmentNumber": 2,
                  "dueDate": "2026-06-30",
                  "amount": 30000.00,
                  "principalComponent": 30000.00,
                  "outstandingBalance": 240000.00,
                  "status": "UPCOMING"
                },
                {
                  "installmentNumber": 3,
                  "dueDate": "2026-07-30",
                  "amount": 30000.00,
                  "principalComponent": 30000.00,
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
            .containsEntry("outstandingBalance", "")
            .containsEntry("principalPaid", "TSh 12,000.00")
            .containsEntry("interestPaid", "TSh 3,000.00")
            .containsEntry("totalPaid", "TSh 15,000.00")
            .containsEntry("paymentDate", "2026-05-15");
        assertThat(rows.get(1))
            .containsEntry("outstandingBalance", "")
            .containsEntry("principalPaid", "-");
        assertThat(rows.get(2))
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
}
