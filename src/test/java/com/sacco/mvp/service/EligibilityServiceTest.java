package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {
    @Mock private LoanProductSettingRepository loanProductSettingRepository;
    @Mock private LoanAnalyticsService loanAnalyticsService;

    private EligibilityService eligibilityService;

    @BeforeEach
    void setUp() {
        eligibilityService = new EligibilityService(
            loanProductSettingRepository,
            loanAnalyticsService,
            JsonMapper.builder().findAndAddModules().build()
        );
    }

    @Test
    void checksRepaymentAgainstDisposableIncome() {
        String saccoId = "CIRCLE-1001";
        UUID memberId = UUID.randomUUID();
        LoanProductSetting product = product(saccoId)
            .maxRepaymentToDisposableIncomeRatio(new BigDecimal("0.4000"))
            .build();
        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, LoanType.EDUCATION_LOAN))
            .thenReturn(Optional.of(product));
        when(loanAnalyticsService.activeLoanAmount(memberId, saccoId, null)).thenReturn(new BigDecimal("250000.00"));

        EligibilityService.EligibilityResult ok = eligibilityService.check(
            saccoId,
            memberId,
            LoanType.EDUCATION_LOAN,
            new BigDecimal("500000.00"),
            Map.of(
                "monthlyIncome", new BigDecimal("1000000.00"),
                "monthlyExpenses", new BigDecimal("300000.00"),
                "otherDebtRepayments", new BigDecimal("100000.00")
            ),
            Map.of("monthlyRepaymentAmount", new BigDecimal("200000.00"))
        );
        EligibilityService.EligibilityResult notOk = eligibilityService.check(
            saccoId,
            memberId,
            LoanType.EDUCATION_LOAN,
            new BigDecimal("500000.00"),
            Map.of(
                "monthlyIncome", new BigDecimal("1000000.00"),
                "monthlyExpenses", new BigDecimal("300000.00"),
                "otherDebtRepayments", new BigDecimal("100000.00")
            ),
            Map.of("monthlyRepaymentAmount", new BigDecimal("260000.00"))
        );

        assertThat(ok.eligible()).isTrue();
        assertThat(ok.savings()).isEqualByComparingTo("600000.00");
        assertThat(ok.maxAllowed()).isEqualByComparingTo("240000.00");
        assertThat(ok.activeExposure()).isEqualByComparingTo("250000.00");
        assertThat(notOk.eligible()).isFalse();
        assertThat(notOk.reason()).contains("affordable limit");
    }

    @Test
    void enforcesCollateralRequirementWhenConfigured() {
        String saccoId = "CIRCLE-1001";
        UUID memberId = UUID.randomUUID();
        LoanProductSetting product = product(saccoId)
            .collateralRequired(true)
            .minCollateralCoverageRatio(new BigDecimal("1.2500"))
            .build();
        when(loanAnalyticsService.activeLoanAmount(memberId, saccoId, null)).thenReturn(BigDecimal.ZERO);

        EligibilityService.EligibilityResult notOk = eligibilityService.check(
            saccoId,
            memberId,
            product,
            new BigDecimal("500000.00"),
            Map.of(
                "monthlyIncome", new BigDecimal("1000000.00"),
                "monthlyExpenses", new BigDecimal("200000.00"),
                "otherDebtRepayments", BigDecimal.ZERO,
                "collateralDescription", "Motorcycle",
                "collateralEstimatedValue", new BigDecimal("500000.00")
            ),
            Map.of("monthlyRepaymentAmount", new BigDecimal("100000.00"))
        );

        assertThat(notOk.eligible()).isFalse();
        assertThat(notOk.reason()).contains("Collateral value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-100", "invalid", "0.001"})
    void missingOrInvalidRepaymentCannotPass(String repayment) {
        var result = assess(cashFlow(), Map.of("monthlyRepaymentAmount", repayment));
        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).contains("positive repayment");
    }

    @Test
    void missingRepaymentCannotPass() {
        assertThat(assess(cashFlow(), Map.of()).eligible()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"monthlyIncome", "monthlyExpenses", "otherDebtRepayments"})
    void missingCashFlowMustBeDeclaredExplicitly(String field) {
        var form = new java.util.LinkedHashMap<String, Object>(cashFlow());
        form.remove(field);
        assertThat(assess(form, Map.of("monthlyRepaymentAmount", "100.00")).eligible()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-100.00", "invalid", "-0.001"})
    void negativeOrMalformedExpensesCannotIncreaseAffordability(String expenses) {
        var form = new java.util.LinkedHashMap<String, Object>(cashFlow());
        form.put("monthlyExpenses", expenses);
        form.put("expenses", "0.00");
        assertThat(assess(form, Map.of("monthlyRepaymentAmount", "100.00")).eligible()).isFalse();
    }

    @Test
    void weeklyRepaymentIsComparedOnMonthlyBasis() {
        var result = assess(cashFlow(), Map.of("periodicRepaymentAmount", "100000.00", "repaymentFrequency", "WEEKLY"));
        assertThat(result.repaymentAmount()).isEqualByComparingTo("433333.34");
        assertThat(result.eligible()).isFalse();
    }

    @Test
    void periodicRepaymentWithUnknownFrequencyCannotPass() {
        assertThat(assess(cashFlow(), Map.of("periodicRepaymentAmount", "100.00", "repaymentFrequency", "FORTNIGHTLY"))
            .eligible()).isFalse();
    }

    @Test
    void snapshotMarksDeclarationsUnverifiedAndProtectsAssessmentFacts() {
        var assessment = assess(cashFlow(), Map.of("monthlyRepaymentAmount", "100.00"));
        String snapshot = eligibilityService.policySnapshotJson(assessment, 1,
            Map.of("eligible", false, "monthlyIncome", BigDecimal.ZERO, "reviewPath", "MANAGER"));
        assertThat(snapshot).contains("DECLARED_NOT_VERIFIED", "MONTHLY_EQUIVALENT_MAX_INSTALLMENT", "MANAGER");
        assertThat(snapshot).contains("\"eligible\":true", "\"monthlyIncome\":1000000.00");
    }

    private EligibilityService.EligibilityResult assess(Map<String, ?> form, Map<String, ?> snapshot) {
        return eligibilityService.check("CIRCLE-1001", UUID.randomUUID(), product("CIRCLE-1001").build(),
            new BigDecimal("500000.00"), form, snapshot);
    }

    private Map<String, Object> cashFlow() {
        return Map.of("monthlyIncome", "1000000.00", "monthlyExpenses", "300000.00", "otherDebtRepayments", "100000.00");
    }

    private LoanProductSetting.LoanProductSettingBuilder product(String saccoId) {
        return LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(LoanType.EDUCATION_LOAN)
            .guarantorsRequired(3)
            .active(true)
            .formSchema("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now());
    }
}
