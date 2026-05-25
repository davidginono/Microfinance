package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.integration.foresight.ForesightAccountSummary;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.MemberRepository;
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
    private final MemberRepository memberRepository;
    private final ForesightDirectoryService foresightDirectoryService;
    private final ObjectMapper objectMapper;

    public EligibilityResult check(String saccoId, UUID memberId, LoanType loanType, BigDecimal amount) {
        BigDecimal savings = resolveSavings(memberId);

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

    public BigDecimal resolveSavings(UUID memberId) {
        BigDecimal localSavings = savingsAccountRepository.findByMemberId(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Savings account missing"))
            .getAvailableBalance();

        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null
            || member.getMemberNo() == null
            || member.getMemberNo().isBlank()
            || member.getStationId() == null
            || member.getStationId().isBlank()) {
            return localSavings;
        }

        try {
            ForesightAccountSummary summary = foresightDirectoryService.fetchAccountSummary(
                member.getMemberNo(),
                member.getStationId()
            );
            if (summary != null && summary.savingsBalance() != null) {
                return summary.savingsBalance();
            }
        } catch (IllegalStateException ignored) {
            return localSavings;
        }

        return localSavings;
    }

    public String policySnapshotJson(EligibilityResult result, int guarantorsRequired, Map<String, Object> extraData) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ratio", result.ratio());
        snapshot.put("savings", result.savings());
        snapshot.put("maxAllowed", result.maxAllowed());
        snapshot.put("guarantorsRequired", guarantorsRequired);
        if (extraData != null && !extraData.isEmpty()) {
            snapshot.putAll(extraData);
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to save policy snapshot", e);
        }
    }

    public record EligibilityResult(boolean eligible, BigDecimal ratio, BigDecimal savings, BigDecimal maxAllowed) {
    }
}

