package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanPaymentTransaction;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.integration.memberportal.LoanPaymentSummaryClient;
import com.sacco.mvp.integration.memberportal.LoanPaymentSummaryDto;
import com.sacco.mvp.integration.memberportal.LoanPaymentLookupException;
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
    @Mock private AuditService auditService;

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
            JsonMapper.builder().findAndAddModules().build(),
            auditService
        );
    }

    @Test
    void syncPersistsPaymentHistoryAndCalculatesBalancesBackwardFromLatestSummary() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .loanId("3100")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .build();
        Member applicant = Member.builder()
            .id(memberId)
            .memberNo("MBR-3100")
            .stationId("ST-1")
            .build();
        List<LoanPaymentTransactionDto> fetched = List.of(
            new LoanPaymentTransactionDto(3100L, LocalDate.of(2025, 4, 10), money("5000"), money("500"), money("5500")),
            new LoanPaymentTransactionDto(3100L, LocalDate.of(2025, 3, 15), money("3000"), money("800"), money("3800")),
            new LoanPaymentTransactionDto(3100L, LocalDate.of(2025, 2, 20), money("2000"), money("200"), money("2200"))
        );
        LoanPaymentSummaryDto summary = new LoanPaymentSummaryDto(
            3100L, "Loan", money("100000"), money("110000"), money("10"),
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 10), money("100000"), money("10000"),
            money("60000"), money("9000"), money("39000"), money("1000"), money("40000"));

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-3100", "ST-1", "3100")).thenReturn(fetched);
        when(summaryClient.fetchSummary("MBR-3100", "ST-1", "3100")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncAllAndRefreshSummary(loan);

        assertThat(inserted).isEqualTo(3);
        verify(transactionRepository, atLeastOnce()).saveAll(argThat(records -> {
            List<LoanPaymentTransaction> saved = new java.util.ArrayList<>();
            records.forEach(saved::add);
            if (saved.size() != 3 || saved.stream().anyMatch(item -> item.getOutstandingBalance() == null)) {
                return false;
            }
            saved.sort(java.util.Comparator.comparing(LoanPaymentTransaction::getReceiptDate).reversed());
            return saved.get(0).getOutstandingBalance().compareTo(money("40000")) == 0
                && saved.get(1).getOutstandingBalance().compareTo(money("45500")) == 0
                && saved.get(1).getOutstandingPrincipal().compareTo(money("44000")) == 0
                && saved.get(1).getOutstandingInterest().compareTo(money("1500")) == 0
                && saved.get(2).getOutstandingBalance().compareTo(money("49300")) == 0;
        }));
    }

    @Test
    void syncMarksLoanPaidWhenFinalInstallmentMonthSummaryIsCleared() {
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
        when(summaryClient.fetchSummary("MBR-001", "ST-1", "1001")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncAllAndRefreshSummary(loan);

        assertThat(inserted).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
        assertThat(loan.getPaidAt()).isNotNull();
        assertThat(loan.getLoanPaymentSummaryJson()).contains("Personal Loan");
        verify(transactionRepository, atLeastOnce()).saveAll(argThat(saved -> {
            List<LoanPaymentTransaction> rows = new java.util.ArrayList<>();
            saved.forEach(rows::add);
            return rows.stream().anyMatch(row -> row.getLoanApplicationId().equals(loanApplicationId)
                && row.getExternalLoanId().equals("1001")
                && row.getReceiptDate().equals(LocalDate.of(2026, 4, 10)));
        }));
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
    void syncStoresSummaryButKeepsLoanActiveBeforeFinalInstallmentMonth() {
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
        when(summaryClient.fetchSummary("MBR-002", "ST-1", "1002")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncAllAndRefreshSummary(loan);

        assertThat(inserted).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        assertThat(loan.getPaidAt()).isNull();
        assertThat(loan.getLoanPaymentSummaryJson()).contains("Development Loan");
        verify(loanApplicationRepository, atLeastOnce()).save(loan);
        verify(outboxService, never()).enqueue(any(), any(), eq("PAID"), any(), any(), any(), any());
    }

    @Test
    void syncMarksLoanDefaultedWhenPastFinalDueDateAndOutstandingRemains() {
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
        when(summaryClient.fetchSummary("MBR-003", "ST-1", "1003")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncAllAndRefreshSummary(loan);

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
    void syncMarksDefaultedLoanPaidEvenWithoutNewTransactions() {
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

        int inserted = syncService.syncAllAndRefreshSummary(loan);

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

    @Test
    void syncAllAndRefreshSummaryStoresLatePostTenureTransaction() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate finalDueDate = LocalDate.of(2024, 1, 31);
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .applicationNumber(306L)
            .loanId("2001")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DEFAULTED)
            .finalDueDate(finalDueDate)
            .createdAt(now.minusYears(2))
            .updatedAt(now.minusDays(2))
            .build();
        Member applicant = Member.builder()
            .id(memberId)
            .saccoId("SACCO-1")
            .memberNo("MBR-006")
            .stationId("ST-1")
            .fullName("Late Paying Member")
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .createdAt(now)
            .build();
        LoanPaymentTransactionDto transaction = new LoanPaymentTransactionDto(
            2001L,
            finalDueDate.plusMonths(3),
            money("35000.00"),
            money("1500.00"),
            money("36500.00")
        );
        LoanPaymentSummaryDto summary = new LoanPaymentSummaryDto(
            2001L,
            "Late Recovery Loan",
            money("250000.00"),
            money("270000.00"),
            money("12.0"),
            finalDueDate.minusMonths(7),
            transaction.receiptDate(),
            money("250000.00"),
            money("20000.00"),
            money("240000.00"),
            money("19000.00"),
            money("10000.00"),
            money("1000.00"),
            money("11000.00")
        );

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-006", "ST-1", "2001")).thenReturn(List.of(transaction));
        when(summaryClient.fetchSummary("MBR-006", "ST-1", "2001")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncAllAndRefreshSummary(loan);

        assertThat(inserted).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DEFAULTED);
        assertThat(loan.getLoanPaymentSummaryJson()).contains("Late Recovery Loan");
        verify(transactionRepository, atLeastOnce()).saveAll(argThat(saved -> {
            List<LoanPaymentTransaction> rows = new java.util.ArrayList<>();
            saved.forEach(rows::add);
            return rows.stream().anyMatch(row -> row.getLoanApplicationId().equals(loanApplicationId)
                && row.getReceiptDate().equals(finalDueDate.plusMonths(3)));
        }));
        verify(loanApplicationRepository).save(loan);
        verify(outboxService, never()).enqueue(any(), any(), eq("PAID"), any(), any(), any(), any());
    }

    @Test
    void syncAllAndRefreshSummaryMarksDefaultedLoanPaidWhenOutstandingIsZero() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .applicationNumber(307L)
            .loanId("2002")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DEFAULTED)
            .finalDueDate(now.toLocalDate().minusMonths(4))
            .createdAt(now.minusMonths(10))
            .updatedAt(now.minusDays(3))
            .build();
        Member applicant = Member.builder()
            .id(memberId)
            .saccoId("SACCO-1")
            .memberNo("MBR-007")
            .stationId("ST-1")
            .fullName("Recovered Member")
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .createdAt(now)
            .build();
        LoanPaymentSummaryDto summary = new LoanPaymentSummaryDto(
            2002L,
            "Recovered Late Loan",
            money("180000.00"),
            money("198000.00"),
            money("12.0"),
            now.toLocalDate().minusMonths(9),
            now.toLocalDate(),
            money("180000.00"),
            money("18000.00"),
            money("180000.00"),
            money("18000.00"),
            money("0.00"),
            money("0.00"),
            money("0.00")
        );

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-007", "ST-1", "2002")).thenReturn(List.of());
        when(summaryClient.fetchSummary("MBR-007", "ST-1", "2002")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncAllAndRefreshSummary(loan);

        assertThat(inserted).isZero();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
        assertThat(loan.getPaidAt()).isNotNull();
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

    @Test
    void syncAllAndRefreshSummaryKeepsDefaultedLoanWhenOutstandingRemains() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .applicationNumber(308L)
            .loanId("2003")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DEFAULTED)
            .finalDueDate(now.toLocalDate().minusMonths(3))
            .createdAt(now.minusMonths(8))
            .updatedAt(now.minusDays(1))
            .build();
        Member applicant = Member.builder()
            .id(memberId)
            .saccoId("SACCO-1")
            .memberNo("MBR-008")
            .stationId("ST-1")
            .fullName("Still Outstanding Member")
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .createdAt(now)
            .build();
        LoanPaymentSummaryDto summary = new LoanPaymentSummaryDto(
            2003L,
            "Still Defaulted Loan",
            money("120000.00"),
            money("132000.00"),
            money("12.0"),
            now.toLocalDate().minusMonths(7),
            now.toLocalDate(),
            money("120000.00"),
            money("12000.00"),
            money("100000.00"),
            money("10000.00"),
            money("20000.00"),
            money("2000.00"),
            money("22000.00")
        );

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-008", "ST-1", "2003")).thenReturn(List.of());
        when(summaryClient.fetchSummary("MBR-008", "ST-1", "2003")).thenReturn(Optional.of(summary));

        int inserted = syncService.syncAllAndRefreshSummary(loan);

        assertThat(inserted).isZero();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DEFAULTED);
        assertThat(loan.getPaidAt()).isNull();
        assertThat(loan.getLoanPaymentSummaryJson()).contains("Still Defaulted Loan");
        verify(loanApplicationRepository).save(loan);
        verify(outboxService, never()).enqueue(any(), any(), eq("PAID"), any(), any());
    }

    @Test
    void syncPreservesExactDuplicateOccurrencesAndDoesNotInsertThemAgain() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .loanId("4100")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .build();
        Member applicant = Member.builder().id(memberId).memberNo("MBR-4100").stationId("ST-1").build();
        LoanPaymentTransactionDto duplicate = new LoanPaymentTransactionDto(
            4100L, LocalDate.of(2026, 7, 8), money("10000"), money("1000"), money("11000"));

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-4100", "ST-1", "4100"))
            .thenReturn(List.of(duplicate, duplicate));
        when(summaryClient.fetchSummary("MBR-4100", "ST-1", "4100")).thenReturn(Optional.empty());

        int firstInserted = syncService.syncAllAndRefreshSummary(loan);

        org.mockito.ArgumentCaptor<Iterable<LoanPaymentTransaction>> captor =
            org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(transactionRepository).saveAll(captor.capture());
        List<LoanPaymentTransaction> firstRows = new java.util.ArrayList<>();
        captor.getValue().forEach(firstRows::add);
        assertThat(firstInserted).isEqualTo(2);
        assertThat(firstRows).extracting(LoanPaymentTransaction::getDuplicateOccurrence)
            .containsExactly(0, 1);
        assertThat(firstRows).extracting(LoanPaymentTransaction::getProviderOrder)
            .containsExactly(0, 1);

        when(transactionRepository.findByLoanApplicationIdOrderByReceiptDateAscProviderOrderDesc(loanApplicationId))
            .thenReturn(firstRows);
        int secondInserted = syncService.syncAllAndRefreshSummary(loan);

        assertThat(secondInserted).isZero();
    }

    @Test
    void syncKeepsFetchedPaymentsWhenSummaryLookupFails() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .loanId("4200")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .build();
        Member applicant = Member.builder().id(memberId).memberNo("MBR-4200").stationId("ST-1").build();
        LoanPaymentTransactionDto payment = new LoanPaymentTransactionDto(
            4200L, LocalDate.of(2026, 7, 9), money("9000"), money("1000"), money("10000"));

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionClient.fetchTransactions("MBR-4200", "ST-1", "4200")).thenReturn(List.of(payment));
        when(summaryClient.fetchSummary("MBR-4200", "ST-1", "4200"))
            .thenThrow(new LoanPaymentLookupException("summary unavailable", new RuntimeException("offline")));

        int inserted = syncService.syncAllAndRefreshSummary(loan);

        assertThat(inserted).isEqualTo(1);
        verify(transactionRepository).saveAll(argThat(saved -> {
            List<LoanPaymentTransaction> rows = new java.util.ArrayList<>();
            saved.forEach(rows::add);
            return rows.size() == 1
                && rows.getFirst().getOutstandingBalance() == null
                && rows.getFirst().getOutstandingPrincipal() == null
                && rows.getFirst().getOutstandingInterest() == null;
        }));
    }

    @Test
    void syncRemovesStoredPaymentsMissingFromSuccessfulHistoryResponse() {
        UUID memberId = UUID.randomUUID();
        UUID loanApplicationId = UUID.randomUUID();
        LoanApplication loan = LoanApplication.builder()
            .id(loanApplicationId)
            .loanId("4300")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .status(LoanStatus.DISBURSED)
            .build();
        Member applicant = Member.builder().id(memberId).memberNo("MBR-4300").stationId("ST-1").build();
        LoanPaymentTransaction stale = LoanPaymentTransaction.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanApplicationId)
            .saccoId("SACCO-1")
            .externalLoanId("4300")
            .receiptDate(LocalDate.of(2026, 6, 1))
            .principalPaid(money("9000"))
            .interestPaid(money("1000"))
            .totalPaid(money("10000"))
            .fetchedAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(applicant));
        when(transactionRepository.findByLoanApplicationIdOrderByReceiptDateAscProviderOrderDesc(loanApplicationId))
            .thenReturn(List.of(stale));
        when(transactionClient.fetchTransactions("MBR-4300", "ST-1", "4300")).thenReturn(List.of());
        when(summaryClient.fetchSummary("MBR-4300", "ST-1", "4300")).thenReturn(Optional.empty());

        int changes = syncService.syncAllAndRefreshSummary(loan);

        assertThat(changes).isEqualTo(1);
        verify(transactionRepository).deleteAllInBatch(List.of(stale));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
