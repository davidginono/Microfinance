package com.sacco.mvp.service;

import tools.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialDetailsServiceTest {

    @Mock private LoanProductSettingRepository loanProductSettingRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;

    private FinancialDetailsService financialDetailsService;

    @BeforeEach
    void setUp() {
        financialDetailsService = new FinancialDetailsService(
            loanProductSettingRepository,
            saccoSettingsRepository,
            loanApplicationRepository,
            JsonMapper.builder().findAndAddModules().build()
        );
    }

    @Test
    void generateSnapshotUsesConfiguredApplicationFee() {
        String saccoId = "SACCO-1";
        UUID memberId = UUID.randomUUID();

        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.EDUCATION_LOAN)
            .minimumAmount(BigDecimal.ZERO)
            .maximumAmount(new BigDecimal("5000000.00"))
            .guarantorsRequired(2)
            .maxLoanSavingsRatio(new BigDecimal("0.5000"))
            .insuranceRate(new BigDecimal("0.0150"))
            .interestRate(new BigDecimal("0.1000"))
            .interestMethod(InterestMethod.FLAT_RATE)
            .minRepaymentMonths(1)
            .maxRepaymentMonths(12)
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, LoanType.EDUCATION_LOAN))
            .thenReturn(Optional.of(product));
        when(saccoSettingsRepository.findById(saccoId)).thenReturn(Optional.of(
            SaccoSettings.builder()
                .saccoId(saccoId)
                .applicationFee(new BigDecimal("25000.00"))
                .build()
        ));

        Map<String, Object> snapshot = financialDetailsService.generateSnapshot(
            saccoId,
            memberId,
            LoanType.EDUCATION_LOAN,
            new BigDecimal("100000.00"),
            1,
            null
        );

        assertThat(snapshot.get("applicationFee")).isEqualTo(new BigDecimal("25000.00"));
        assertThat(snapshot.get("totalDeductions")).isEqualTo(new BigDecimal("26500.00"));
    }

    @Test
    void oneMonthLoanAdvanceUsesConfiguredInterestTerms() {
        String saccoId = "SACCO-1";
        UUID memberId = UUID.randomUUID();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.LOAN_ADVANCE)
            .minimumAmount(BigDecimal.ZERO)
            .maximumAmount(new BigDecimal("5000000.00"))
            .guarantorsRequired(0)
            .insuranceRate(BigDecimal.ZERO)
            .interestRate(new BigDecimal("0.0750"))
            .interestMethod(InterestMethod.FLAT_RATE)
            .minRepaymentMonths(1)
            .maxRepaymentMonths(3)
            .active(true)
            .build();

        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, LoanType.LOAN_ADVANCE))
            .thenReturn(Optional.of(product));
        when(saccoSettingsRepository.findById(saccoId)).thenReturn(Optional.of(
            SaccoSettings.builder().saccoId(saccoId).applicationFee(BigDecimal.ZERO).build()
        ));

        Map<String, Object> snapshot = financialDetailsService.generateSnapshot(
            saccoId, memberId, LoanType.LOAN_ADVANCE, new BigDecimal("100000.00"), 1, null
        );

        assertThat(snapshot.get("interestRate")).isEqualTo(new BigDecimal("0.0750"));
        assertThat(snapshot.get("interestMethod")).isEqualTo("FLAT_RATE");
        assertThat(snapshot.get("interestAmount")).isEqualTo(new BigDecimal("625.00"));
    }

    @Test
    void generateSnapshotUsesProductApplicationAndProcessingFees() {
        String saccoId = "SACCO-1";
        UUID memberId = UUID.randomUUID();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .minimumAmount(BigDecimal.ZERO)
            .maximumAmount(new BigDecimal("5000000.00"))
            .guarantorsRequired(1)
            .applicationFee(new BigDecimal("18000.00"))
            .insuranceRate(new BigDecimal("0.0100"))
            .processingFeeRate(new BigDecimal("0.0200"))
            .interestRate(BigDecimal.ZERO)
            .interestMethod(InterestMethod.FLAT_RATE)
            .minRepaymentMonths(1)
            .maxRepaymentMonths(12)
            .active(true)
            .build();

        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, LoanType.DEVELOPMENT_LOAN))
            .thenReturn(Optional.of(product));
        when(saccoSettingsRepository.findById(saccoId)).thenReturn(Optional.of(
            SaccoSettings.builder().saccoId(saccoId).applicationFee(new BigDecimal("25000.00")).build()
        ));

        Map<String, Object> snapshot = financialDetailsService.generateSnapshot(
            saccoId, memberId, LoanType.DEVELOPMENT_LOAN, new BigDecimal("100000.00"), 1, null
        );

        assertThat(snapshot.get("applicationFee")).isEqualTo(new BigDecimal("18000.00"));
        assertThat(snapshot.get("processingFee")).isEqualTo(new BigDecimal("2000.00"));
        assertThat(snapshot.get("totalDeductions")).isEqualTo(new BigDecimal("21000.00"));
    }

    @Test
    void weeklyQuoteAndDatedScheduleHaveIdenticalTotals() {
        var product = LoanProductSetting.builder().saccoId("SACCO-1").loanType(LoanType.DEVELOPMENT_LOAN)
            .interestMethod(InterestMethod.REDUCING_BALANCE).interestRate(new BigDecimal("0.12"))
            .repaymentFrequency(RepaymentFrequency.WEEKLY).insuranceRate(BigDecimal.ZERO).applicationFee(BigDecimal.ZERO)
            .minRepaymentMonths(1).maxRepaymentMonths(12).build();
        var snapshot = financialDetailsService.generateSnapshot("SACCO-1", UUID.randomUUID(), product,
            new BigDecimal("120000.00"), 12, null);
        var app = LoanApplication.builder().saccoId("SACCO-1").loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("120000.00")).tenorMonths(12)
            .financialSnapshot(financialDetailsService.toJson(snapshot)).build();
        var schedules = new RepaymentScheduleService(JsonMapper.builder().findAndAddModules().build(), loanProductSettingRepository);
        var schedule = schedules.buildSchedule(app, java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 8),
            RepaymentFrequency.WEEKLY, null, null, null);
        assertThat(snapshot.get("numberOfPayments")).isEqualTo(52);
        assertThat(schedule.installments()).isEqualTo(52);
        assertThat(schedules.parseRows(schedule.scheduleJson()).stream()
            .map(row -> new BigDecimal(String.valueOf(row.get("amount"))))
            .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo((BigDecimal) snapshot.get("principalPlusInterest"));
        assertThat(snapshot.get("monthlyRepaymentAmount"))
            .isEqualTo(LoanAmortizationCalculator.monthlyEquivalent((BigDecimal) snapshot.get("maximumInstallmentAmount"), RepaymentFrequency.WEEKLY));
    }

    @Test
    void zeroInterestQuoteReconcilesRatherThanMultiplyingRoundedInstallment() {
        var product = LoanProductSetting.builder().saccoId("SACCO-1").loanType(LoanType.DEVELOPMENT_LOAN)
            .interestMethod(InterestMethod.REDUCING_BALANCE).interestRate(BigDecimal.ZERO).insuranceRate(BigDecimal.ZERO)
            .applicationFee(BigDecimal.ZERO).minRepaymentMonths(1).build();
        var snapshot = financialDetailsService.generateSnapshot("SACCO-1", UUID.randomUUID(), product, new BigDecimal("100.00"), 3, null);
        assertThat(snapshot.get("principalPlusInterest")).isEqualTo(new BigDecimal("100.00"));
        assertThat(snapshot.get("monthlyRepaymentAmount")).isEqualTo(new BigDecimal("33.34"));
        assertThat(snapshot.get("periodicRepaymentAmount")).isEqualTo(new BigDecimal("33.33"));
    }

    @Test
    void quoteRejectsMissingTenureAndFractionalCents() {
        var product = LoanProductSetting.builder().saccoId("SACCO-1").build();
        assertThatThrownBy(() -> financialDetailsService.generateSnapshot("SACCO-1", UUID.randomUUID(), product,
            new BigDecimal("100.00"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> financialDetailsService.generateSnapshot("SACCO-1", UUID.randomUUID(), product,
            new BigDecimal("100.001"), 3, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void topUpSnapshotRecalculatesInterestOnConsolidatedPrincipal() {
        String saccoId = "SACCO-1";
        UUID memberId = UUID.randomUUID();
        UUID sourceLoanId = UUID.randomUUID();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .minimumAmount(BigDecimal.ZERO)
            .maximumAmount(new BigDecimal("5000000.00"))
            .guarantorsRequired(1)
            .insuranceRate(BigDecimal.ZERO)
            .processingFeeRate(BigDecimal.ZERO)
            .interestRate(new BigDecimal("0.1000"))
            .interestMethod(InterestMethod.FLAT_RATE)
            .minRepaymentMonths(1)
            .maxRepaymentMonths(12)
            .active(true)
            .build();
        LoanApplication sourceLoan = LoanApplication.builder()
            .id(sourceLoanId)
            .applicantMemberId(memberId)
            .amount(new BigDecimal("700000.00"))
            .status(LoanStatus.DISBURSED)
            .financialSnapshot("""
                {
                  "interestAmount": 70000.00,
                  "principalPlusInterest": 770000.00
                }
                """)
            .build();

        when(saccoSettingsRepository.findById(saccoId)).thenReturn(Optional.of(
            SaccoSettings.builder().saccoId(saccoId).applicationFee(BigDecimal.ZERO).build()
        ));
        when(loanApplicationRepository.findById(sourceLoanId)).thenReturn(Optional.of(sourceLoan));

        Map<String, Object> snapshot = financialDetailsService.generateSnapshot(
            saccoId, memberId, product, new BigDecimal("100000.00"), 1, sourceLoanId
        );

        assertThat(snapshot.get("loanBalance")).isEqualTo(new BigDecimal("770000.00"));
        assertThat(snapshot.get("topUpRequestedAmount")).isEqualTo(new BigDecimal("100000.00"));
        assertThat(snapshot.get("topUpSettlementAmount")).isEqualTo(new BigDecimal("770000.00"));
        assertThat(snapshot.get("principalAmount")).isEqualTo(new BigDecimal("870000.00"));
        assertThat(snapshot.get("interestAmount")).isEqualTo(new BigDecimal("7250.00"));
        assertThat(snapshot.get("principalPlusInterest")).isEqualTo(new BigDecimal("877250.00"));
        assertThat(snapshot.get("monthlyRepaymentAmount")).isEqualTo(new BigDecimal("877250.00"));
    }
}
