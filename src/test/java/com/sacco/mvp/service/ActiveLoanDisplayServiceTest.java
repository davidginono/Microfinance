package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ActiveLoanDisplayServiceTest {
    @Mock private LoanPresentationService loanPresentationService;
    @Mock private LoanProductDisplayService loanProductDisplayService;

    private ActiveLoanDisplayService service;

    @BeforeEach
    void setUp() {
        service = new ActiveLoanDisplayService(
            loanPresentationService,
            loanProductDisplayService,
            JsonMapper.builder().findAndAddModules().build()
        );
        lenient().when(loanProductDisplayService.namesForSacco("IAA"))
            .thenReturn(Map.of(LoanType.EDUCATION_LOAN, "Education Loan"));
        lenient().when(loanProductDisplayService.displayName(any(LoanApplication.class), anyMap()))
            .thenReturn("Education Loan");
        lenient().when(loanPresentationService.formatMoneyDisplay(nullable(BigDecimal.class)))
            .thenAnswer(invocation -> money(invocation.getArgument(0)));
        lenient().when(loanPresentationService.countdownLabel(nullable(LocalDate.class))).thenReturn("");
        lenient().when(loanPresentationService.activeLoanOutstandingBalance(any(LoanApplication.class)))
            .thenAnswer(invocation -> {
                LoanApplication loan = invocation.getArgument(0);
                return loan.getAmount() == null ? BigDecimal.ZERO : loan.getAmount();
            });
        lenient().when(loanPresentationService.activeLoanPaidAmount(any(LoanApplication.class))).thenReturn(BigDecimal.ZERO);
        lenient().when(loanPresentationService.activeLoanOutstandingPrincipal(any(LoanApplication.class)))
            .thenAnswer(invocation -> {
                LoanApplication loan = invocation.getArgument(0);
                return loan.getAmount() == null ? BigDecimal.ZERO : loan.getAmount();
            });
        lenient().when(loanPresentationService.activeLoanOutstandingInterest(any(LoanApplication.class))).thenReturn(BigDecimal.ZERO);
        lenient().when(loanPresentationService.activeLoanTotalPrincipalPaid(any(LoanApplication.class))).thenReturn(BigDecimal.ZERO);
        lenient().when(loanPresentationService.activeLoanTotalInterestPaid(any(LoanApplication.class))).thenReturn(BigDecimal.ZERO);
        lenient().when(loanPresentationService.activeLoanLastPaymentDateLabel(any(LoanApplication.class))).thenReturn("-");
    }

    @Test
    void memberDashboardRowsUseLocalLoansOnly() {
        LoanApplication first = activeLoan(UUID.randomUUID(), "1001", "1000000.00");
        LoanApplication second = activeLoan(UUID.randomUUID(), "2002", "150000.00");

        ActiveLoanDisplayService.ActiveLoanDisplay display =
            service.memberDashboardRows(member("MEM001", "ST01"), "IAA", List.of(first, second), Set.of());

        assertThat(display.status()).isEqualTo("LOCAL_ONLY");
        assertThat(display.count()).isEqualTo(2);
        assertThat(display.totalExposure()).isEqualTo("TSh 1150000");
        assertThat(display.rows()).extracting(row -> row.get("loanId"))
            .containsExactly("1001", "2002");
        assertThat(display.rows().getFirst().get("currentBalance")).isEqualTo("TSh 1000000");
        assertThat(display.rows().getFirst().get("scheduleAvailable")).isEqualTo(true);
    }

    @Test
    void staffRowsExcludeCurrentReviewApplicationByUuid() {
        LoanApplication currentApplication = activeLoan(UUID.randomUUID(), "3003", "400000.00");
        LoanApplication sameUuid = activeLoan(currentApplication.getId(), "3003", "400000.00");
        LoanApplication existingLoan = activeLoan(UUID.randomUUID(), "1001", "500000.00");

        ActiveLoanDisplayService.ActiveLoanDisplay display = service.staffReviewRows(
            member("MEM001", "ST01"),
            "IAA",
            currentApplication,
            List.of(sameUuid, existingLoan)
        );

        assertThat(display.rows()).extracting(row -> row.get("loanId"))
            .containsExactly("1001");
        assertThat(display.rows()).noneMatch(row -> "3003".equals(row.get("loanId")));
        assertThat(display.rows().getFirst().get("scheduleAvailable")).isEqualTo(true);
        assertThat(display.rows().getFirst().get("scheduleLoanId")).isEqualTo(existingLoan.getId().toString());
    }

    private LoanApplication activeLoan(UUID id, String loanId, String amount) {
        return LoanApplication.builder()
            .id(id)
            .applicationNumber(704L)
            .loanId(loanId)
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal(amount))
            .status(LoanStatus.DISBURSED)
            .saccoId("IAA")
            .stationId("ST01")
            .disbursementDate(LocalDate.of(2026, 7, 13))
            .finalDueDate(LocalDate.of(2027, 7, 28))
            .createdAt(OffsetDateTime.parse("2026-07-13T10:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-07-13T10:00:00Z"))
            .build();
    }

    private Member member(String memberNo, String stationId) {
        return Member.builder()
            .memberNo(memberNo)
            .stationId(stationId)
            .build();
    }

    private String money(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        return "TSh " + safe.stripTrailingZeros().toPlainString();
    }
}
