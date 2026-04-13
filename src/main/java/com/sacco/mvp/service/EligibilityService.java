package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SavingsAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EligibilityService {
    private final SavingsAccountRepository savingsAccountRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final ObjectMapper objectMapper;

    public EligibilityResult check(String saccoId, UUID memberId, LoanType loanType, BigDecimal amount) {
        BigDecimal savings = savingsAccountRepository.findByMemberId(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Savings account missing"))
            .getAvailableBalance();

        BigDecimal ratio = resolveRatio(saccoId, loanType);
        BigDecimal maxAllowed = savings.multiply(ratio).setScale(2, RoundingMode.DOWN);
        boolean eligible = amount.compareTo(maxAllowed) <= 0;
        return new EligibilityResult(eligible, ratio, savings, maxAllowed);
    }

    private BigDecimal resolveRatio(String saccoId, LoanType loanType) {
        LoanProductSetting product = loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, loanType)
            .orElseThrow(() -> new IllegalArgumentException("Loan product not found"));
        if (product.getMaxLoanSavingsRatio() != null) {
            return product.getMaxLoanSavingsRatio();
        }
        return saccoSettingsRepository.findById(saccoId)
            .orElseThrow(() -> new IllegalArgumentException("SACCO settings missing"))
            .getMaxLoanSavingsRatio();
    }

    public String policySnapshotJson(EligibilityResult result, int guarantorsRequired) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ratio", result.ratio());
        snapshot.put("savings", result.savings());
        snapshot.put("maxAllowed", result.maxAllowed());
        snapshot.put("guarantorsRequired", guarantorsRequired);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to save policy snapshot", e);
        }
    }

    public record EligibilityResult(boolean eligible, BigDecimal ratio, BigDecimal savings, BigDecimal maxAllowed) {
    }
}

