package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.SaccoSettings;
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
}
