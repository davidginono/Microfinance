package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanPortfolioRiskStatusServiceTest {
    private static final String SACCO_ID = "SACCO-01";
    private static final String STATION_ID = "ST01";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private SaccoStationPolicyRepository saccoStationPolicyRepository;
    @Mock private RepaymentScheduleService repaymentScheduleService;
    @Mock private OutboxService outboxService;
    @Mock private AuditService auditService;
    @Mock private ApplicationClock applicationClock;

    private LoanPortfolioRiskStatusService service;

    @BeforeEach
    void setUp() {
        service = new LoanPortfolioRiskStatusService(
            loanApplicationRepository,
            saccoSettingsRepository,
            saccoStationPolicyRepository,
            repaymentScheduleService,
            outboxService,
            auditService,
            applicationClock,
            new ObjectMapper()
        );
        when(applicationClock.today()).thenReturn(TODAY);
        lenient().when(applicationClock.now()).thenReturn(OffsetDateTime.parse("2026-08-24T13:00:00+03:00"));
    }

    @Test
    void disbursedLoanMovesToParAfterFirstUnpaidDueDate() {
        LoanApplication loan = loan(LoanStatus.DISBURSED, outstandingSnapshot("100.00", "0.00", "0.00"));
        givenSettings(30);
        givenSchedule("2026-08-01", "100.00");
        givenCandidatePage(loan);
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanPortfolioRiskStatusService.PortfolioRiskStatusResult result = service.reevaluatePortfolioRiskStatuses(50);

        assertThat(result.par()).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAR);
        verify(outboxService).enqueue(any(), any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void parLoanMovesToDefaultedAfterConfiguredPortfolioAtRiskDays() {
        LoanApplication loan = loan(LoanStatus.PAR, outstandingSnapshot("100.00", "0.00", "0.00"));
        givenSettings(30);
        givenSchedule("2026-07-01", "100.00");
        givenCandidatePage(loan);
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanPortfolioRiskStatusService.PortfolioRiskStatusResult result = service.reevaluatePortfolioRiskStatuses(50);

        assertThat(result.defaulted()).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DEFAULTED);
    }

    @Test
    void parLoanReturnsToDisbursedWhenTheFirstUnpaidInstallmentIsNotOverdue() {
        LoanApplication loan = loan(LoanStatus.PAR, outstandingSnapshot("100.00", "100.00", "0.00"));
        givenSettings(30);
        givenSchedule(
            Map.of("dueDate", "2026-08-01", "amount", "100.00"),
            Map.of("dueDate", "2026-09-01", "amount", "100.00")
        );
        givenCandidatePage(loan);
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanPortfolioRiskStatusService.PortfolioRiskStatusResult result = service.reevaluatePortfolioRiskStatuses(50);

        assertThat(result.reverted()).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DISBURSED);
    }

    @Test
    void loanWithZeroOutstandingMovesToPaid() {
        LoanApplication loan = loan(LoanStatus.DISBURSED, outstandingSnapshot("0.00", "100.00", "0.00"));
        givenSettings(30);
        givenCandidatePage(loan);
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanPortfolioRiskStatusService.PortfolioRiskStatusResult result = service.reevaluatePortfolioRiskStatuses(50);

        assertThat(result.paid()).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
        assertThat(loan.getPaidAt()).isEqualTo(OffsetDateTime.parse("2026-08-24T13:00:00+03:00"));
    }

    @Test
    void defaultedLoanWithZeroOutstandingMovesToPaid() {
        LoanApplication loan = loan(LoanStatus.DEFAULTED, outstandingSnapshot("0.00", "100.00", "0.00"));
        givenSettings(30);
        givenCandidatePage(loan);
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanPortfolioRiskStatusService.PortfolioRiskStatusResult result = service.reevaluatePortfolioRiskStatuses(50);

        assertThat(result.paid()).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
        assertThat(loan.getPaidAt()).isEqualTo(OffsetDateTime.parse("2026-08-24T13:00:00+03:00"));
        verify(loanApplicationRepository, atLeastOnce()).findByStatusInAndLoanIdIsNotNull(
            argThat(statuses -> statuses.contains(LoanStatus.DEFAULTED)),
            any()
        );
    }

    @Test
    void defaultedLoanWithPositiveOutstandingRemainsDefaulted() {
        LoanApplication loan = loan(LoanStatus.DEFAULTED, outstandingSnapshot("100.00", "0.00", "0.00"));
        givenSettings(30);
        givenCandidatePage(loan);

        LoanPortfolioRiskStatusService.PortfolioRiskStatusResult result = service.reevaluatePortfolioRiskStatuses(50);

        assertThat(result.changed()).isZero();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DEFAULTED);
        verify(loanApplicationRepository, never()).save(any());
        verify(repaymentScheduleService, never()).parseRows(any());
    }

    @Test
    void statusEvaluationRestartsFromFirstPageAfterMutatingCandidateRows() {
        LoanApplication first = loan(LoanStatus.PAR, outstandingSnapshot("100.00", "0.00", "0.00"));
        LoanApplication shifted = loan(LoanStatus.PAR, outstandingSnapshot("100.00", "0.00", "0.00"));
        givenSettings(30);
        givenSchedule("2026-07-01", "100.00");
        when(loanApplicationRepository.findByStatusInAndLoanIdIsNotNull(any(), any()))
            .thenReturn(
                new PageImpl<>(List.of(first), PageRequest.of(0, 1), 2),
                new PageImpl<>(List.of(shifted), PageRequest.of(0, 1), 1),
                new PageImpl<>(List.of(), PageRequest.of(0, 1), 0)
            );
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LoanPortfolioRiskStatusService.PortfolioRiskStatusResult result = service.reevaluatePortfolioRiskStatuses(1);

        assertThat(result.defaulted()).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(LoanStatus.DEFAULTED);
        assertThat(shifted.getStatus()).isEqualTo(LoanStatus.DEFAULTED);
    }

    @Test
    void loanWithoutSyncedOutstandingBalanceIsSkipped() {
        LoanApplication loan = loan(LoanStatus.DISBURSED, "{}");
        givenCandidatePage(loan);

        LoanPortfolioRiskStatusService.PortfolioRiskStatusResult result = service.reevaluatePortfolioRiskStatuses(50);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        verify(loanApplicationRepository, never()).save(any());
    }

    private void givenCandidatePage(LoanApplication loan) {
        when(loanApplicationRepository.findByStatusInAndLoanIdIsNotNull(any(), any()))
            .thenReturn(
                new PageImpl<>(List.of(loan), PageRequest.of(0, 50), 1),
                new PageImpl<>(List.of(), PageRequest.of(0, 50), 0)
            );
    }

    private void givenSettings(int portfolioAtRiskDays) {
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .portfolioAtRiskDays(portfolioAtRiskDays)
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.empty());
    }

    private void givenSchedule(String dueDate, String amount) {
        givenSchedule(Map.of("dueDate", dueDate, "amount", amount));
    }

    @SafeVarargs
    private final void givenSchedule(Map<String, Object>... rows) {
        when(repaymentScheduleService.parseRows(any())).thenReturn(List.of(rows));
    }

    private LoanApplication loan(LoanStatus status, String financialSnapshot) {
        return LoanApplication.builder()
            .id(UUID.randomUUID())
            .applicationNumber(101L)
            .loanId("LN-101")
            .saccoId(SACCO_ID)
            .stationId(STATION_ID)
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("1000.00"))
            .tenorMonths(2)
            .status(status)
            .financialSnapshot(financialSnapshot)
            .finalDueDate(LocalDate.of(2026, 9, 1))
            .repaymentScheduleJson("{}")
            .createdAt(OffsetDateTime.parse("2026-01-01T00:00:00+03:00"))
            .updatedAt(OffsetDateTime.parse("2026-01-01T00:00:00+03:00"))
            .build();
    }

    private String outstandingSnapshot(String outstanding, String principalPaid, String interestPaid) {
        return """
            {
              "foresightTotalOutstanding": "%s",
              "foresightTotalPrincipalPaid": "%s",
              "foresightTotalInterestPaid": "%s"
            }
            """.formatted(outstanding, principalPaid, interestPaid);
    }
}
