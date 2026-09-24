package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanProductRequiredAttachment;
import com.sacco.mvp.repository.LoanProductRequiredAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanProductRequiredAttachmentService {
    private static final BigDecimal DEFAULT_MAX_SIZE_MB = new BigDecimal("5.00");
    private static final BigDecimal MAX_SIZE_MB = new BigDecimal("100.00");

    private final LoanProductRequiredAttachmentRepository repository;

    public List<LoanProductRequiredAttachment> activeForProduct(UUID productId) {
        if (productId == null) {
            return List.of();
        }
        return repository.findByLoanProductSettingIdAndActiveTrueOrderByDisplayOrderAscCreatedAtAsc(productId);
    }

    public Map<UUID, List<LoanProductRequiredAttachment>> activeByProduct(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<LoanProductRequiredAttachment>> grouped = new LinkedHashMap<>();
        repository.findByLoanProductSettingIdInOrderByDisplayOrderAscCreatedAtAsc(productIds).stream()
            .filter(LoanProductRequiredAttachment::isActive)
            .forEach(item -> grouped.computeIfAbsent(item.getLoanProductSettingId(), ignored -> new ArrayList<>()).add(item));
        return grouped;
    }

    @Transactional
    public void replaceForProduct(UUID productId, List<String> names, List<BigDecimal> maxSizeMbValues) {
        if (productId == null) {
            return;
        }
        repository.deleteByLoanProductSettingId(productId);
        if (names == null || names.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<LoanProductRequiredAttachment> items = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = normalizeName(names.get(i));
            if (name == null) {
                continue;
            }
            items.add(LoanProductRequiredAttachment.builder()
                .id(UUID.randomUUID())
                .loanProductSettingId(productId)
                .attachmentName(name)
                .maxSizeMb(normalizeMaxSize(maxSizeAt(maxSizeMbValues, i)))
                .displayOrder(items.size() + 1)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        }
        if (!items.isEmpty()) {
            repository.saveAll(items);
        }
    }

    @Transactional
    public void createForProduct(UUID productId, String name, BigDecimal maxSizeMb) {
        if (productId == null) {
            throw new IllegalArgumentException("Loan product is required.");
        }
        String normalizedName = normalizeName(name);
        if (normalizedName == null) {
            throw new IllegalArgumentException("Attachment name is required.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        int nextOrder = repository.findByLoanProductSettingIdOrderByDisplayOrderAscCreatedAtAsc(productId).stream()
            .map(LoanProductRequiredAttachment::getDisplayOrder)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(0) + 1;
        repository.save(LoanProductRequiredAttachment.builder()
            .id(UUID.randomUUID())
            .loanProductSettingId(productId)
            .attachmentName(normalizedName)
            .maxSizeMb(normalizeMaxSize(maxSizeMb))
            .displayOrder(nextOrder)
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    @Transactional
    public void deleteForProduct(UUID productId, UUID requirementId) {
        if (productId == null || requirementId == null) {
            throw new IllegalArgumentException("Attachment requirement was not found.");
        }
        LoanProductRequiredAttachment requirement = repository.findById(requirementId)
            .orElseThrow(() -> new IllegalArgumentException("Attachment requirement was not found."));
        if (!productId.equals(requirement.getLoanProductSettingId())) {
            throw new IllegalArgumentException("Attachment requirement was not found for this product.");
        }
        requirement.setActive(false);
        requirement.setUpdatedAt(OffsetDateTime.now());
        repository.save(requirement);
    }

    public BigDecimal normalizeMaxSize(BigDecimal value) {
        if (value == null) {
            return DEFAULT_MAX_SIZE_MB;
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Attachment maximum size must be greater than zero.");
        }
        if (normalized.compareTo(MAX_SIZE_MB) > 0) {
            throw new IllegalStateException("Attachment maximum size cannot be more than 100 MB.");
        }
        return normalized;
    }

    public long maxSizeBytes(LoanProductRequiredAttachment requirement) {
        BigDecimal maxSizeMb = requirement == null ? DEFAULT_MAX_SIZE_MB : normalizeMaxSize(requirement.getMaxSizeMb());
        return maxSizeMb.multiply(BigDecimal.valueOf(1024L * 1024L)).longValue();
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > 120) {
            throw new IllegalStateException("Attachment name must be 120 characters or fewer.");
        }
        return normalized;
    }

    private BigDecimal maxSizeAt(List<BigDecimal> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return DEFAULT_MAX_SIZE_MB;
        }
        return values.get(index);
    }
}
