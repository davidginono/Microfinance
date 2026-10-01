package com.sacco.mvp.service;

import tools.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepaymentScheduleServiceTest {

    @Mock private LoanProductSettingRepository loanProductSettingRepository;

    private RepaymentScheduleService repaymentScheduleService;

    @BeforeEach
    void setUp() {
        repaymentScheduleService = new RepaymentScheduleService(
            JsonMapper.builder().findAndAddModules().build(),
            loanProductSettingRepository
        );
    }

    @Test
    void buildScheduleUsesStoredReducingBalanceTermsAfterProductChanges() {
        LoanApplication app = baseApplication("""
            {"interestMethod":"REDUCING_BALANCE","interestRate":0.1200}
            """);
        LoanProductSetting currentProduct = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .loanType(LoanType.EDUCATION_LOAN)
            .interestMethod(InterestMethod.FLAT_RATE)
            .interestRate(new BigDecimal("0.0800"))
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-1", LoanType.EDUCATION_LOAN))
            .thenReturn(Optional.of(currentProduct));

        RepaymentScheduleService.ScheduleResult result = repaymentScheduleService.buildSchedule(
            app,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 6, 1),
            RepaymentFrequency.MONTHLY,
            null,
            null,
            null
        );

        Map<String, Object> summary = repaymentScheduleService.parseSummary(result.scheduleJson());
        assertThat(new BigDecimal(String.valueOf(summary.get("disbursedPrincipal"))).compareTo(new BigDecimal("120000.00"))).isZero();
        assertThat(summary.get("interestMethod")).isEqualTo("REDUCING_BALANCE");
        assertThat(new BigDecimal(String.valueOf(summary.get("interestRate"))).compareTo(new BigDecimal("0.1200"))).isZero();
        assertThat(repaymentScheduleService.parseRows(result.scheduleJson()).getFirst().get("interestComponent"))
            .isNotEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void buildScheduleUsesStoredFlatRateTermsAfterProductChanges() {
        LoanApplication app = baseApplication("""
            {"interestMethod":"FLAT_RATE","interestRate":0.1000}
            """);
        LoanProductSetting currentProduct = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .loanType(LoanType.EDUCATION_LOAN)
            .interestMethod(InterestMethod.REDUCING_BALANCE)
            .interestRate(new BigDecimal("0.1500"))
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-1", LoanType.EDUCATION_LOAN))
            .thenReturn(Optional.of(currentProduct));

        RepaymentScheduleService.ScheduleResult result = repaymentScheduleService.buildSchedule(
            app,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 6, 1),
            RepaymentFrequency.MONTHLY,
            null,
            null,
            null
        );

        Map<String, Object> summary = repaymentScheduleService.parseSummary(result.scheduleJson());
        assertThat(summary.get("interestMethod")).isEqualTo("FLAT_RATE");
        assertThat(new BigDecimal(String.valueOf(summary.get("interestRate"))).compareTo(new BigDecimal("0.1000"))).isZero();
        assertThat(new BigDecimal(String.valueOf(
            repaymentScheduleService.parseRows(result.scheduleJson()).getFirst().get("interestComponent")
        )).compareTo(new BigDecimal("0.00"))).isPositive();
    }

    @Test
    void flatRateScheduleKeepsSnapshotInterestWhenInstallmentAmountIsOverridden() {
        LoanApplication app = baseApplication("""
            {"interestMethod":"FLAT_RATE","interestRate":0.1000,"interestAmount":66000.00}
            """);
        app.setAmount(new BigDecimal("700000.00"));
        app.setTenorMonths(6);
        LoanProductSetting currentProduct = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .loanType(LoanType.EDUCATION_LOAN)
            .interestMethod(InterestMethod.FLAT_RATE)
            .interestRate(new BigDecimal("0.1000"))
            .active(true)
            .build();

        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-1", LoanType.EDUCATION_LOAN))
            .thenReturn(Optional.of(currentProduct));

        RepaymentScheduleService.ScheduleResult result = repaymentScheduleService.buildSchedule(
            app,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 6, 1),
            RepaymentFrequency.MONTHLY,
            new BigDecimal("122500.00"),
            null,
            null
        );

        Map<String, Object> firstRow = repaymentScheduleService.parseRows(result.scheduleJson()).getFirst();
        assertThat(new BigDecimal(String.valueOf(firstRow.get("amount"))).compareTo(new BigDecimal("122500.00"))).isZero();
        assertThat(new BigDecimal(String.valueOf(firstRow.get("interestComponent"))).compareTo(new BigDecimal("11000.00"))).isZero();
        assertThat(new BigDecimal(String.valueOf(firstRow.get("principalComponent"))).compareTo(new BigDecimal("111500.00"))).isZero();
        assertThat(new BigDecimal(String.valueOf(firstRow.get("outstandingBalance"))).compareTo(new BigDecimal("643500.00"))).isZero();
    }

    @Test
    void oneMonthLoanAdvanceUsesStoredConfiguredRate() {
        LoanApplication app = baseApplication("""
            {"interestMethod":"FLAT_RATE","interestRate":0.0750}
            """);
        app.setLoanType(LoanType.LOAN_ADVANCE);
        app.setTenorMonths(1);
        LoanProductSetting currentProduct = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .loanType(LoanType.LOAN_ADVANCE)
            .interestMethod(InterestMethod.REDUCING_BALANCE)
            .interestRate(new BigDecimal("0.1500"))
            .active(true)
            .build();

        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue("SACCO-1", LoanType.LOAN_ADVANCE))
            .thenReturn(Optional.of(currentProduct));

        RepaymentScheduleService.ScheduleResult result = repaymentScheduleService.buildSchedule(
            app,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 6, 1),
            RepaymentFrequency.MONTHLY,
            null,
            null,
            null
        );

        Map<String, Object> summary = repaymentScheduleService.parseSummary(result.scheduleJson());
        assertThat(summary.get("interestMethod")).isEqualTo("FLAT_RATE");
        assertThat(new BigDecimal(String.valueOf(summary.get("interestRate"))).compareTo(new BigDecimal("0.0750"))).isZero();
        assertThat(new BigDecimal(String.valueOf(
            repaymentScheduleService.parseRows(result.scheduleJson()).getFirst().get("interestComponent")
        )).compareTo(BigDecimal.ZERO)).isPositive();
    }

    @Test
    void monthlyDatesStayAnchoredToOriginalRepaymentDay() {
        var app = baseApplication("{\"interestMethod\":\"REDUCING_BALANCE\",\"interestRate\":0}");
        app.setTenorMonths(3);
        var result = repaymentScheduleService.buildSchedule(app, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
            RepaymentFrequency.MONTHLY, null, null, null);
        assertThat(repaymentScheduleService.parseRows(result.scheduleJson())).extracting(row -> row.get("dueDate"))
            .containsExactly("2026-01-31", "2026-02-28", "2026-03-31");
    }

    @Test
    void flatFallbackUsesAnnualRateAndTenure() {
        var app = baseApplication("{\"interestMethod\":\"FLAT_RATE\",\"interestRate\":0.12}");
        app.setTenorMonths(6);
        var result = repaymentScheduleService.buildSchedule(app, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
            RepaymentFrequency.MONTHLY, null, null, null);
        assertThat(repaymentScheduleService.parseRows(result.scheduleJson()).stream()
            .map(row -> new BigDecimal(String.valueOf(row.get("interestComponent"))))
            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("7200.00");
    }

    @Test
    void assessedTermsCannotBeChangedAtDisbursement() {
        var app = baseApplication("""
            {"calculationVersion":"DECIMAL_PERIODIC_V1","repaymentFrequency":"MONTHLY",
             "interestMethod":"REDUCING_BALANCE","interestRate":0,"principalAmount":120000,
             "periodicRepaymentAmount":10000,"interestAmount":0}
            """);
        assertThatThrownBy(() -> repaymentScheduleService.buildSchedule(app, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 8),
            RepaymentFrequency.WEEKLY, null, null, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reassessment");
        assertThatThrownBy(() -> repaymentScheduleService.buildSchedule(app, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
            RepaymentFrequency.MONTHLY, new BigDecimal("9000.00"), null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reassessment");
    }

    @Test
    void invalidDatesAndOversizedOverridesFailBeforeProducingSchedule() {
        var app = baseApplication("{\"interestMethod\":\"REDUCING_BALANCE\",\"interestRate\":0.12}");
        assertThatThrownBy(() -> repaymentScheduleService.buildSchedule(app, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1),
            RepaymentFrequency.MONTHLY, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repaymentScheduleService.buildSchedule(app, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
            RepaymentFrequency.MONTHLY, new BigDecimal("120000.00"), null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oldWeeklyQuoteKeepsLegacyCountUntilExplicitReassessment() {
        var app = baseApplication("{\"interestMethod\":\"REDUCING_BALANCE\",\"interestRate\":0.12}");
        var result = repaymentScheduleService.buildSchedule(app, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 8),
            RepaymentFrequency.WEEKLY, null, null, null);
        assertThat(result.installments()).isEqualTo(48);
        assertThat(repaymentScheduleService.parseSummary(result.scheduleJson()).get("calculationVersion"))
            .isEqualTo("LEGACY_PERIOD_COUNT_DECIMAL");
    }

    private LoanApplication baseApplication(String financialSnapshot) {
        return LoanApplication.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal("120000.00"))
            .tenorMonths(12)
            .status(LoanStatus.AWAITING_ACCOUNTANT)
            .financialSnapshot(financialSnapshot)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
