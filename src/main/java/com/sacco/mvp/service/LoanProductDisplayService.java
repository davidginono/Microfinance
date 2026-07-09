package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanProductDisplayService {
    private final LoanProductSettingRepository loanProductSettingRepository;

    public Map<LoanType, String> namesForSacco(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            return Map.of();
        }
        return loanProductSettingRepository.findBySaccoIdOrderByLoanTypeAsc(saccoId).stream()
            .collect(Collectors.toMap(
                LoanProductSetting::getLoanType,
                LoanProductSetting::getDisplayName,
                (first, ignored) -> first,
                LinkedHashMap::new
            ));
    }

    public String displayName(LoanApplication app) {
        if (app == null) {
            return "-";
        }
        return displayName(app.getLoanType(), namesForSacco(app.getSaccoId()));
    }

    public String displayName(LoanApplication app, Map<LoanType, String> productNames) {
        if (app == null) {
            return "-";
        }
        return displayName(app.getLoanType(), productNames);
    }

    public String displayName(LoanType loanType, Map<LoanType, String> productNames) {
        if (loanType == null) {
            return "-";
        }
        String configuredName = productNames == null ? null : productNames.get(loanType);
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName;
        }
        return loanType.getDisplayLabel();
    }
}
