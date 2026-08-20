package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.ForesightLoanPaymentSummary;
import com.sacco.mvp.integration.foresight.UpstreamAvailabilityException;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanPaymentSummarySyncServiceTest {
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ForesightDirectoryService foresightDirectoryService;

    private ObjectMapper objectMapper;
    private LoanPaymentSummarySyncService service;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new LoanPaymentSummarySyncService(
            loanApplicationRepository,
            memberRepository,
            foresightDirectoryService,
            objectMapper
        );
    }

    @Test
    void successUpdatesFinancialSnapshotWithForesightPaymentSummary() throws Exception {
        UUID loanApplicationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        LoanApplication loan = activeLoan(loanApplicationId, memberId, "1001", "ST01");
        loan.setFinancialSnapshot("""
            {"interestAmount":25000.00,"principalPlusInterest":525000.00}
            """);
        Member member = member(memberId, "MEM001", "ST01");

        when(loanApplicationRepository.findById(loanApplicationId)).thenReturn(Optional.of(loan));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(foresightDirectoryService.fetchLoanPaymentSummary("MEM001", "ST01", "1001"))
            .thenReturn(List.of(summary("1001", "445000.00", "425000.00", "20000.00")));

        var result = service.refreshLoanPaymentSummary(loanApplicationId);

        assertThat(result.status()).isEqualTo(LoanPaymentSummarySyncService.RefreshStatus.UPDATED);
        Map<String, Object> snapshot = objectMapper.readValue(loan.getFinancialSnapshot(), new TypeReference<>() {});
        assertThat(snapshot.get("interestAmount")).isNotNull();
        assertSnapshotAmount(snapshot, "foresightTotalOutstanding", "445000.00");
        assertSnapshotAmount(snapshot, "foresightOutstandingPrincipal", "425000.00");
        assertSnapshotAmount(snapshot, "foresightOutstandingInterest", "20000.00");
        assertSnapshotAmount(snapshot, "foresightTotalPrincipalPaid", "75000.00");
        assertSnapshotAmount(snapshot, "foresightTotalInterestPaid", "5000.00");
        assertThat(snapshot.get("foresightLastPaymentDate")).isEqualTo("2024-04-10");
        assertThat(snapshot.get("foresightPaymentSummaryFetchedAt")).isNotNull();
        verify(loanApplicationRepository).save(loan);
    }

    @Test
    void emptyForesightResponsePreservesPreviousSnapshot() {
        UUID loanApplicationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        LoanApplication loan = activeLoan(loanApplicationId, memberId, "1001", "ST01");
        String originalSnapshot = "{\"interestAmount\":25000.00,\"principalPlusInterest\":525000.00}";
        loan.setFinancialSnapshot(originalSnapshot);

        when(loanApplicationRepository.findById(loanApplicationId)).thenReturn(Optional.of(loan));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(memberId, "MEM001", "ST01")));
        when(foresightDirectoryService.fetchLoanPaymentSummary("MEM001", "ST01", "1001"))
            .thenReturn(List.of());

        var result = service.refreshLoanPaymentSummary(loanApplicationId);

        assertThat(result.status()).isEqualTo(LoanPaymentSummarySyncService.RefreshStatus.NO_DATA);
        assertThat(loan.getFinancialSnapshot()).isEqualTo(originalSnapshot);
        verify(loanApplicationRepository, never()).save(loan);
    }

    @Test
    void communicationErrorPreservesPreviousSnapshot() {
        UUID loanApplicationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        LoanApplication loan = activeLoan(loanApplicationId, memberId, "1001", "ST01");
        String originalSnapshot = "{\"interestAmount\":25000.00,\"principalPlusInterest\":525000.00}";
        loan.setFinancialSnapshot(originalSnapshot);

        when(loanApplicationRepository.findById(loanApplicationId)).thenReturn(Optional.of(loan));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(memberId, "MEM001", "ST01")));
        when(foresightDirectoryService.fetchLoanPaymentSummary("MEM001", "ST01", "1001"))
            .thenThrow(new UpstreamAvailabilityException("down", null));

        var result = service.refreshLoanPaymentSummary(loanApplicationId);

        assertThat(result.status()).isEqualTo(LoanPaymentSummarySyncService.RefreshStatus.ERROR);
        assertThat(result.message()).isEqualTo(LoanPaymentSummarySyncService.COMMUNICATION_ERROR_MESSAGE);
        assertThat(loan.getFinancialSnapshot()).isEqualTo(originalSnapshot);
        verify(loanApplicationRepository, never()).save(loan);
    }

    @Test
    void missingMemberStationOrLoanIdIsSkippedSafely() {
        UUID loanApplicationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        LoanApplication missingLoanId = activeLoan(loanApplicationId, memberId, " ", "ST01");

        when(loanApplicationRepository.findById(loanApplicationId)).thenReturn(Optional.of(missingLoanId));

        var missingLoanResult = service.refreshLoanPaymentSummary(loanApplicationId);

        assertThat(missingLoanResult.status()).isEqualTo(LoanPaymentSummarySyncService.RefreshStatus.SKIPPED);
        verifyNoInteractions(foresightDirectoryService);
        verify(loanApplicationRepository, never()).save(missingLoanId);

        UUID missingMemberLoanId = UUID.randomUUID();
        LoanApplication missingMemberLoan = activeLoan(missingMemberLoanId, memberId, "1001", "ST01");
        when(loanApplicationRepository.findById(missingMemberLoanId)).thenReturn(Optional.of(missingMemberLoan));
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        var missingMemberResult = service.refreshLoanPaymentSummary(missingMemberLoanId);

        assertThat(missingMemberResult.status()).isEqualTo(LoanPaymentSummarySyncService.RefreshStatus.SKIPPED);
        verify(loanApplicationRepository, never()).save(missingMemberLoan);

        UUID missingStationLoanId = UUID.randomUUID();
        UUID missingStationMemberId = UUID.randomUUID();
        LoanApplication missingStationLoan = activeLoan(missingStationLoanId, missingStationMemberId, "1002", " ");
        when(loanApplicationRepository.findById(missingStationLoanId)).thenReturn(Optional.of(missingStationLoan));
        when(memberRepository.findById(missingStationMemberId))
            .thenReturn(Optional.of(member(missingStationMemberId, "MEM002", " ")));

        var missingStationResult = service.refreshLoanPaymentSummary(missingStationLoanId);

        assertThat(missingStationResult.status()).isEqualTo(LoanPaymentSummarySyncService.RefreshStatus.SKIPPED);
        verify(loanApplicationRepository, never()).save(missingStationLoan);
    }

    private LoanApplication activeLoan(UUID id, UUID memberId, String loanId, String stationId) {
        return LoanApplication.builder()
            .id(id)
            .applicantMemberId(memberId)
            .saccoId("IAA")
            .stationId(stationId)
            .loanId(loanId)
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal("500000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2026-07-08T10:00:00Z"))
            .updatedAt(OffsetDateTime.parse("2026-07-08T10:00:00Z"))
            .build();
    }

    private Member member(UUID memberId, String memberNo, String stationId) {
        return Member.builder()
            .id(memberId)
            .memberNo(memberNo)
            .stationId(stationId)
            .build();
    }

    private ForesightLoanPaymentSummary summary(String loanId,
                                                String totalOutstanding,
                                                String outstandingPrincipal,
                                                String outstandingInterest) {
        return new ForesightLoanPaymentSummary(
            loanId,
            "Personal Loan",
            new BigDecimal("500000.00"),
            new BigDecimal("525000.00"),
            new BigDecimal("15.5"),
            LocalDate.of(2024, 3, 15),
            LocalDate.of(2024, 4, 10),
            new BigDecimal("500000.00"),
            new BigDecimal("25000.00"),
            new BigDecimal("75000.00"),
            new BigDecimal("5000.00"),
            new BigDecimal(outstandingPrincipal),
            new BigDecimal(outstandingInterest),
            new BigDecimal(totalOutstanding)
        );
    }

    private void assertSnapshotAmount(Map<String, Object> snapshot, String key, String expected) {
        assertThat(new BigDecimal(String.valueOf(snapshot.get(key)))).isEqualByComparingTo(expected);
    }
}
