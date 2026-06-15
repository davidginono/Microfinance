package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
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
