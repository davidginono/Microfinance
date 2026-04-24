package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaccoConfigurationService {
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.3333");
    private static final BigDecimal DEFAULT_INSURANCE_RATE = new BigDecimal("0.0150");
    private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("0.1000");

    private final LoanProductSettingRepository loanProductSettingRepository;

    @Transactional
    public void ensureDefaultLoanProducts(String saccoId) {
        if (saccoId == null || saccoId.isBlank() || loanProductSettingRepository.existsBySaccoId(saccoId)) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        seedDefaultProduct(saccoId, LoanType.LOAN_ADVANCE, 0, 3, now);
        seedDefaultProduct(saccoId, LoanType.EDUCATION_LOAN, 3, 12, now);
        seedDefaultProduct(saccoId, LoanType.EMERGENCY_LOAN, 3, 12, now);
        seedDefaultProduct(saccoId, LoanType.DEVELOPMENT_LOAN, 3, 12, now);
    }

    private void seedDefaultProduct(String saccoId,
                                    LoanType loanType,
                                    int guarantorsRequired,
                                    int maxRepaymentMonths,
                                    OffsetDateTime now) {
        loanProductSettingRepository.save(newLoanProduct(
            saccoId,
            loanType,
            null,
            guarantorsRequired,
            DEFAULT_RATIO,
            DEFAULT_INSURANCE_RATE,
            DEFAULT_INTEREST_RATE,
            maxRepaymentMonths,
            true,
            now
        ));
    }

    @Transactional
    public LoanProductSetting createLoanProduct(String saccoId,
                                                LoanType loanType,
                                                String productName,
                                                Integer guarantorsRequired,
                                                BigDecimal maxLoanSavingsRatio,
                                                BigDecimal insuranceRate,
                                                BigDecimal interestRate,
                                                Integer maxRepaymentMonths,
                                                boolean active) {
        if (loanProductSettingRepository.existsBySaccoIdAndLoanType(saccoId, loanType)) {
            throw new IllegalStateException("That loan product already exists for this SACCO.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return loanProductSettingRepository.save(newLoanProduct(
            saccoId,
            loanType,
            productName,
            guarantorsRequired,
            maxLoanSavingsRatio == null ? DEFAULT_RATIO : maxLoanSavingsRatio,
            insuranceRate == null ? DEFAULT_INSURANCE_RATE : insuranceRate,
            interestRate == null ? DEFAULT_INTEREST_RATE : interestRate,
            maxRepaymentMonths,
            active,
            now
        ));
    }

    private LoanProductSetting newLoanProduct(String saccoId,
                                              LoanType loanType,
                                              String productName,
                                              Integer guarantorsRequired,
                                              BigDecimal maxLoanSavingsRatio,
                                              BigDecimal insuranceRate,
                                              BigDecimal interestRate,
                                              Integer maxRepaymentMonths,
                                              boolean active,
                                              OffsetDateTime now) {
        return LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(loanType)
            .productName(productName)
            .guarantorsRequired(guarantorsRequired)
            .maxLoanSavingsRatio(maxLoanSavingsRatio)
            .insuranceRate(insuranceRate)
            .interestRate(interestRate)
            .maxRepaymentMonths(maxRepaymentMonths)
            .formSchema(defaultLoanFormSchema())
            .active(active)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private String defaultLoanFormSchema() {
        return """
            {
              "type": "object",
              "properties": {}
            }
            """;
    }
}
