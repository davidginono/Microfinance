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
        loanProductSettingRepository.save(LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .loanType(loanType)
            .guarantorsRequired(guarantorsRequired)
            .maxLoanSavingsRatio(DEFAULT_RATIO)
            .insuranceRate(DEFAULT_INSURANCE_RATE)
            .interestRate(DEFAULT_INTEREST_RATE)
            .maxRepaymentMonths(maxRepaymentMonths)
            .formSchema(defaultLoanFormSchema())
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build());
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
