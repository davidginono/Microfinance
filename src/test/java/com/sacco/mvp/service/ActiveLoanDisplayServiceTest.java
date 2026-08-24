package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.integration.foresight.ForesightAccountSummary;
import com.sacco.mvp.integration.foresight.ForesightActiveLoan;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.UpstreamAvailabilityException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveLoanDisplayServiceTest {
    @Mock private ForesightDirectoryService foresightDirectoryService;
    @Mock private LoanPresentationService loanPresentationService;
    @Mock private LoanProductDisplayService loanProductDisplayService;

    private ActiveLoanDisplayService service;

    @BeforeEach
    void setUp() {
        service = new ActiveLoanDisplayService(
            foresightDirectoryService,
            loanPresentationService,
            loanProductDisplayService,
            JsonMapper.builder().findAndAddModules().build()
        );
        lenient().when(loanProductDisplayService.namesForSacco("IAA"))
            .thenReturn(Map.of(LoanType.EDUCATION_LOAN, "Education Loan"));
        lenient().when(loanProductDisplayService.displayName(any(LoanApplication.class), anyMap()))
            .thenAnswer(invocation -> {
                LoanApplication loan = invocation.getArgument(0);
                return loan.getLoanType() == null ? "-" : loan.getLoanType().getDisplayLabel();
            });
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
    void memberDashboardRowsMergeLocalAndForesightLoansByLoanId() {
        LoanApplication localLoan = activeLoan(UUID.randomUUID(), "1001", "1000000.00");
        Member member = member("MEM001", "ST01");
        when(foresightDirectoryService.fetchActiveLoans("MEM001", "ST01")).thenReturn(List.of(
            activeLoan("1001", "Education Loan", "1000000.00"),
            activeLoan("2002", "Emergency Loan", "150000.00")
        ));
        when(foresightDirectoryService.fetchAccountSummary("MEM001", "ST01")).thenReturn(summary(List.of(
            outstanding("1001", "700000.00", "70000.00"),
            outstanding("2002", "150000.00", "15000.00")
        )));

        ActiveLoanDisplayService.ActiveLoanDisplay display =
            service.memberDashboardRows(member, "IAA", List.of(localLoan), Set.of());

        assertThat(display.status()).isEqualTo("AVAILABLE");
        assertThat(display.count()).isEqualTo(2);
        assertThat(display.totalExposure()).isEqualTo("TSh 935000");
        assertThat(display.rows()).extracting(row -> row.get("loanId"))
            .containsExactly("1001", "2002");
        assertThat(display.rows().get(0).get("currentBalance")).isEqualTo("TSh 770000");
        assertThat(display.rows().get(0).get("scheduleAvailable")).isEqualTo(true);
        assertThat(display.rows().get(1).get("currentBalance")).isEqualTo("TSh 165000");
        assertThat(display.rows().get(1).get("paidAmount")).isEqualTo("-");
        assertThat(display.rows().get(1).get("scheduleAvailable")).isEqualTo(true);
        assertThat(display.rows().get(1).get("scheduleDataUrl"))
            .isEqualTo("/app/active-loans/2002/repayment-schedule");
    }

    @Test
    void staffRowsExcludeCurrentReviewApplicationByUuidAndLoanId() {
        LoanApplication currentApplication = activeLoan(UUID.randomUUID(), "3003", "400000.00");
        LoanApplication sameUuid = activeLoan(currentApplication.getId(), "3003", "400000.00");
        LoanApplication existingLoan = activeLoan(UUID.randomUUID(), "1001", "500000.00");
        Member member = member("MEM001", "ST01");
        when(foresightDirectoryService.fetchActiveLoans("MEM001", "ST01")).thenReturn(List.of(
            activeLoan("3003", "Current Review Loan", "400000.00"),
            activeLoan("2002", "Emergency Loan", "150000.00")
        ));
        when(foresightDirectoryService.fetchAccountSummary("MEM001", "ST01")).thenReturn(summary(List.of(
            outstanding("1001", "400000.00", "40000.00"),
            outstanding("2002", "150000.00", "15000.00"),
            outstanding("3003", "400000.00", "40000.00")
        )));

        ActiveLoanDisplayService.ActiveLoanDisplay display = service.staffReviewRows(
            member,
            "IAA",
            currentApplication,
            List.of(sameUuid, existingLoan)
        );

        assertThat(display.rows()).extracting(row -> row.get("loanId"))
            .containsExactly("1001", "2002");
        assertThat(display.rows()).noneMatch(row -> "3003".equals(row.get("loanId")));
        assertThat(display.rows().get(1).get("scheduleAvailable")).isEqualTo(true);
        assertThat(display.rows().get(1).get("scheduleLoanId")).isEqualTo("2002");
        assertThat(display.rows().get(1).get("installmentAmount")).isEqualTo("-");
    }

    @Test
    void unavailableForesightFallsBackToLocalRows() {
        LoanApplication localLoan = activeLoan(UUID.randomUUID(), "1001", "1000000.00");
        Member member = member("MEM001", "ST01");
        when(foresightDirectoryService.fetchActiveLoans("MEM001", "ST01"))
            .thenThrow(new UpstreamAvailabilityException("down", null));
        when(loanPresentationService.activeLoanOutstandingBalance(localLoan))
            .thenReturn(new BigDecimal("1100000.00"));

        ActiveLoanDisplayService.ActiveLoanDisplay display =
            service.memberDashboardRows(member, "IAA", List.of(localLoan), Set.of());

        assertThat(display.status()).isEqualTo("UNAVAILABLE");
        assertThat(display.count()).isEqualTo(1);
        assertThat(display.rows().getFirst().get("loanId")).isEqualTo("1001");
        assertThat(display.rows().getFirst().get("currentBalance")).isEqualTo("TSh 1100000");
        assertThat(display.totalExposure()).isEqualTo("TSh 1100000");
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

    private ForesightActiveLoan activeLoan(String loanId, String description, String amount) {
        return new ForesightActiveLoan(
            loanId,
            LocalDate.of(2026, 7, 13),
            description,
            new BigDecimal(amount),
            new BigDecimal(amount),
            new BigDecimal("10.0"),
            BigDecimal.ZERO
        );
    }

    private ForesightAccountSummary summary(List<ForesightAccountSummary.ForesightOutstandingLoan> loans) {
        return new ForesightAccountSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, loans);
    }

    private ForesightAccountSummary.ForesightOutstandingLoan outstanding(String loanId,
                                                                         String principal,
                                                                         String interest) {
        return new ForesightAccountSummary.ForesightOutstandingLoan(
            loanId,
            "Loan " + loanId,
            null,
            null,
            new BigDecimal(principal),
            new BigDecimal(interest)
        );
    }

    private String money(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        return "TSh " + safe.stripTrailingZeros().toPlainString();
    }
}
