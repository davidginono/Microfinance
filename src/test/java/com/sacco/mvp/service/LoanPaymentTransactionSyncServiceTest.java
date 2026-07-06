package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.integration.memberportal.LoanPaymentSummaryClient;
import com.sacco.mvp.integration.memberportal.LoanPaymentSummaryDto;
import com.sacco.mvp.integration.memberportal.LoanPaymentTransactionClient;
import com.sacco.mvp.integration.memberportal.LoanPaymentTransactionDto;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanPaymentTransactionRepository;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanPaymentTransactionSyncServiceTest {

    @Mock private LoanPaymentTransactionClient transactionClient;
    @Mock private LoanPaymentSummaryClient summaryClient;
    @Mock private LoanPaymentTransactionRepository transactionRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private OutboxService outboxService;

    private LoanPaymentTransactionSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new LoanPaymentTransactionSyncService(
            transactionClient,
            summaryClient,
            transactionRepository,
            memberRepository,
            loanApplicationRepository,
            outboxService,
            JsonMapper.builder().findAndAddModules().build()
        );
    }

    @Test
    void syncMonthMarksLoanPaidWhenFinalInstallmentMonthSummaryIsCleared() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .applicationNumber(77L)
            .loanId("1001")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .finalDueDate(LocalDate.of(2026, 4, 30))
            .createdAt(now)
            .updatedAt(now)
            .build();
        Member applicant = Member.builder()
            .id(memberId)
            .saccoId("SACCO-1")
            .memberNo("MBR-001")
            .stationId("ST-1")
            .fullName("Jane Member")
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .createdAt(now)
            .build();
        LoanPaymentTransactionDto transaction = new LoanPaymentTransactionDto(
            1001L,
            LocalDate.of(2026, 4, 10),
            money("75000.00"),
            money("5000.00"),
            money("80000.00")
        );
        LoanPaymentSummaryDto summary = new LoanPaymentSummaryDto(
            1001L,
            "Personal Loan",
            money("500000.00"),
            money("525000.00"),
            money("15.5"),
            LocalDate.of(2024, 3, 15),
            LocalDate.of(2026, 4, 10),
            money("500000.00"),
            money("25000.00"),
            money("500000.00"),
            money("25000.00"),
            money("0.00"),
            money("0.00"),
            money("0.00")
        );

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-001", "ST-1", "1001")).thenReturn(List.of(transaction));
        when(transactionRepository.existsByLoanApplicationIdAndReceiptDateAndPrincipalPaidAndInterestPaidAndTotalPaid(
            loanApplicationId,
            transaction.receiptDate(),
            transaction.principalPaid(),
            transaction.interestPaid(),
            transaction.totalPaid())).thenReturn(false);
        when(summaryClient.fetchSummary("MBR-001", "ST-1", "1001")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncMonth(loan, YearMonth.of(2026, 4));

        assertThat(inserted).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
        assertThat(loan.getPaidAt()).isNotNull();
        assertThat(loan.getLoanPaymentSummaryJson()).contains("Personal Loan");
        verify(transactionRepository).save(argThat(saved ->
            saved.getLoanApplicationId().equals(loanApplicationId)
                && saved.getExternalLoanId().equals("1001")
                && saved.getReceiptDate().equals(LocalDate.of(2026, 4, 10))));
        verify(loanApplicationRepository, atLeastOnce()).save(loan);
        verify(outboxService).enqueue(
            eq("LOAN"),
            eq(loanApplicationId),
            eq("PAID"),
            eq(memberId),
            eq("SACCO-1"),
            eq("ST-1"),
            argThat((Map<String, Object> details) -> "SYNC".equals(details.get("source"))));
    }

    @Test
    void syncMonthStoresSummaryButKeepsLoanActiveBeforeFinalInstallmentMonth() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .applicationNumber(91L)
            .loanId("1002")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .finalDueDate(now.toLocalDate().plusMonths(2))
            .createdAt(now)
            .updatedAt(now)
            .build();
        Member applicant = Member.builder()
            .id(memberId)
            .saccoId("SACCO-1")
            .memberNo("MBR-002")
            .stationId("ST-1")
            .fullName("John Member")
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .createdAt(now)
            .build();
        LoanPaymentTransactionDto transaction = new LoanPaymentTransactionDto(
            1002L,
            LocalDate.of(2026, 4, 18),
            money("50000.00"),
            money("2500.00"),
            money("52500.00")
        );
        LoanPaymentSummaryDto summary = new LoanPaymentSummaryDto(
            1002L,
            "Development Loan",
            money("600000.00"),
            money("630000.00"),
            money("15.5"),
            LocalDate.of(2024, 3, 15),
            LocalDate.of(2026, 4, 18),
            money("600000.00"),
            money("30000.00"),
            money("150000.00"),
            money("7500.00"),
            money("450000.00"),
            money("22500.00"),
            money("472500.00")
        );

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-002", "ST-1", "1002")).thenReturn(List.of(transaction));
        when(transactionRepository.existsByLoanApplicationIdAndReceiptDateAndPrincipalPaidAndInterestPaidAndTotalPaid(
            loanApplicationId,
            transaction.receiptDate(),
            transaction.principalPaid(),
            transaction.interestPaid(),
            transaction.totalPaid())).thenReturn(false);
        when(summaryClient.fetchSummary("MBR-002", "ST-1", "1002")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncMonth(loan, YearMonth.of(2026, 4));

        assertThat(inserted).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        assertThat(loan.getPaidAt()).isNull();
        assertThat(loan.getLoanPaymentSummaryJson()).contains("Development Loan");
        verify(loanApplicationRepository, atLeastOnce()).save(loan);
        verify(outboxService, never()).enqueue(any(), any(), eq("PAID"), any(), any());
    }

    @Test
    void syncMonthMarksLoanDefaultedWhenPastFinalDueDateAndOutstandingRemains() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate pastDueDate = now.toLocalDate().minusDays(10);
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .applicationNumber(105L)
            .loanId("1003")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .finalDueDate(pastDueDate)
            .createdAt(now.minusMonths(7))
            .updatedAt(now.minusDays(1))
            .build();
        Member applicant = Member.builder()
            .id(memberId)
            .saccoId("SACCO-1")
            .memberNo("MBR-003")
            .stationId("ST-1")
            .fullName("Late Member")
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .createdAt(now)
            .build();
        LoanPaymentTransactionDto transaction = new LoanPaymentTransactionDto(
            1003L,
            now.toLocalDate().minusDays(3),
            money("25000.00"),
            money("1000.00"),
            money("26000.00")
        );
        LoanPaymentSummaryDto summary = new LoanPaymentSummaryDto(
            1003L,
            "Emergency Loan",
            money("300000.00"),
            money("315000.00"),
            money("15.5"),
            pastDueDate.minusMonths(5),
            now.toLocalDate().minusDays(3),
            money("300000.00"),
            money("15000.00"),
            money("240000.00"),
            money("12000.00"),
            money("60000.00"),
            money("3000.00"),
            money("63000.00")
        );

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-003", "ST-1", "1003")).thenReturn(List.of(transaction));
        when(transactionRepository.existsByLoanApplicationIdAndReceiptDateAndPrincipalPaidAndInterestPaidAndTotalPaid(
            loanApplicationId,
            transaction.receiptDate(),
            transaction.principalPaid(),
            transaction.interestPaid(),
            transaction.totalPaid())).thenReturn(false);
        when(summaryClient.fetchSummary("MBR-003", "ST-1", "1003")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncMonth(loan, YearMonth.from(transaction.receiptDate()));

        assertThat(inserted).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DEFAULTED);
        assertThat(loan.getPaidAt()).isNull();
        verify(outboxService).enqueue(
            eq("LOAN"),
            eq(loanApplicationId),
            eq("DEFAULTED"),
            eq(memberId),
            eq("SACCO-1"),
            eq("ST-1"),
            argThat((Map<String, Object> details) -> "SYNC".equals(details.get("source"))));
    }

    @Test
    void syncRecentAndRefreshSummaryMarksDefaultedLoanPaidEvenWithoutNewTransactions() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .applicationNumber(205L)
            .loanId("1005")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DEFAULTED)
            .finalDueDate(now.toLocalDate().minusDays(20))
            .createdAt(now.minusMonths(8))
            .updatedAt(now.minusDays(2))
            .build();
        Member applicant = Member.builder()
            .id(memberId)
            .saccoId("SACCO-1")
            .memberNo("MBR-005")
            .stationId("ST-1")
            .fullName("Recovered Member")
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .createdAt(now)
            .build();
        LoanPaymentSummaryDto summary = new LoanPaymentSummaryDto(
            1005L,
            "Recovered Loan",
            money("200000.00"),
            money("220000.00"),
            money("12.0"),
            now.toLocalDate().minusMonths(7),
            now.toLocalDate(),
            money("200000.00"),
            money("20000.00"),
            money("200000.00"),
            money("20000.00"),
            money("0.00"),
            money("0.00"),
            money("0.00")
        );

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-005", "ST-1", "1005")).thenReturn(List.of());
        when(summaryClient.fetchSummary("MBR-005", "ST-1", "1005")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncRecentAndRefreshSummary(loan, 24);

        assertThat(inserted).isZero();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
        assertThat(loan.getPaidAt()).isNotNull();
        assertThat(loan.getLoanPaymentSummaryJson()).contains("Recovered Loan");
        verify(loanApplicationRepository).save(loan);
        verify(outboxService).enqueue(
            eq("LOAN"),
            eq(loanApplicationId),
            eq("PAID"),
            eq(memberId),
            eq("SACCO-1"),
            eq("ST-1"),
            argThat((Map<String, Object> details) -> "SYNC".equals(details.get("source"))));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
