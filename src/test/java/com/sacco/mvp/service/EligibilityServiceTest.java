package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
                "collateralDescription", "Motorcycle",
                "collateralEstimatedValue", new BigDecimal("500000.00")
            ),
            Map.of("monthlyRepaymentAmount", new BigDecimal("100000.00"))
        );

        assertThat(notOk.eligible()).isFalse();
        assertThat(notOk.reason()).contains("Collateral value");
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
