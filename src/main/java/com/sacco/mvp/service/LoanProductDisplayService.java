package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanProductDisplayService {
    private final LoanProductSettingRepository loanProductSettingRepository;

    public List<LoanProductSetting> activeProducts(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            return List.of();
        }
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId);
    }

    public Optional<LoanProductSetting> findActiveProduct(UUID loanProductId, String saccoId) {
        return loanProductId == null || saccoId == null || saccoId.isBlank()
            ? Optional.empty()
            : loanProductSettingRepository.findByIdAndSaccoIdAndActiveTrue(loanProductId, saccoId);
    }

    public Optional<LoanProductSetting> findProduct(UUID loanProductId, String saccoId) {
        return loanProductId == null || saccoId == null || saccoId.isBlank()
            ? Optional.empty()
            : loanProductSettingRepository.findByIdAndSaccoId(loanProductId, saccoId);
    }

    public Map<LoanType, String> namesForSacco(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            return Map.of();
        }
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId).stream()
            .filter(LoanProductSetting::isAvailableForApplications)
            .sorted(java.util.Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .collect(Collectors.toMap(
                LoanProductSetting::getLoanType,
                LoanProductSetting::getDisplayName,
                (first, ignored) -> first,
                LinkedHashMap::new
            ));
    }

    public Map<UUID, String> namesById(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            return Map.of();
        }
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId).stream()
            .filter(LoanProductSetting::isAvailableForApplications)
            .sorted(java.util.Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .collect(Collectors.toMap(
                LoanProductSetting::getId,
                LoanProductSetting::getDisplayName,
                (first, ignored) -> first,
                LinkedHashMap::new
            ));
    }

    public String displayName(LoanApplication app) {
        if (app == null) {
            return "-";
        }
        String productName = displayNameByProductId(app);
        if (productName != null) {
            return productName;
        }
        return displayName(app.getLoanType(), namesForSacco(app.getSaccoId()));
    }

    public String displayName(LoanApplication app, Map<LoanType, String> productNames) {
        if (app == null) {
            return "-";
        }
        String productName = displayNameByProductId(app);
        if (productName != null) {
            return productName;
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

    private String displayNameByProductId(LoanApplication app) {
        UUID productId = app.getLoanProductSettingId();
        if (productId == null || app.getSaccoId() == null || app.getSaccoId().isBlank()) {
            return null;
        }
        return loanProductSettingRepository.findByIdAndSaccoIdAndActiveTrue(productId, app.getSaccoId())
            .filter(LoanProductSetting::isAvailableForApplications)
            .map(LoanProductSetting::getDisplayName)
            .filter(name -> !name.isBlank())
            .orElse(null);
    }
}
