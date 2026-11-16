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
    public void replaceForProduct(UUID productId, com.sacco.mvp.service.dto.LoanProductDocumentsForm form) {
        if(productId==null || form==null)throw new IllegalArgumentException("productDocuments.invalid");
        var names=form.getRequiredAttachmentName();var sizes=form.getRequiredAttachmentMaxSizeMb();var ids=form.getRequiredAttachmentId();
        if(names==null || sizes==null || ids==null || names.size()>10 || sizes.size()!=names.size() || (!ids.isEmpty() && ids.size()!=names.size()))throw new IllegalArgumentException("productDocuments.invalid");
        var selected=ids.stream().filter(Objects::nonNull).filter(value->!value.isBlank()).map(this::documentId).toList();
        if(new HashSet<>(selected).size()!=selected.size())throw new IllegalArgumentException("productDocuments.invalid");
        var existing=selected.isEmpty()?List.<LoanProductRequiredAttachment>of():repository.findByLoanProductSettingIdAndIdIn(productId,selected);
        var retained=new HashSet<UUID>();var replacements=new ArrayList<LoanProductRequiredAttachment>();var uniqueNames=new HashSet<String>();
        var now=OffsetDateTime.now();
        for(int index=0;index<names.size();index++){
            String name=normalizeName(names.get(index));var size=sizes.get(index);
            if(name==null || size==null || size.signum()<=0 || size.compareTo(new BigDecimal("25.00"))>0 || !uniqueNames.add(name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("productDocuments.invalid");
            LoanProductRequiredAttachment previous=null;
            String value=ids.isEmpty()?"":Objects.toString(ids.get(index),"");
            if(!value.isBlank()){
                UUID id=documentId(value);
                previous=existing.stream().filter(item->item.getId().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("productDocuments.invalid"));
            }
            var item=previous!=null && previous.getAttachmentName().equals(name)?previous:LoanProductRequiredAttachment.builder().id(UUID.randomUUID()).loanProductSettingId(productId).createdAt(now).build();
            item.setAttachmentName(name);item.setMaxSizeMb(normalizeMaxSize(size));item.setDisplayOrder(index+1);item.setActive(true);item.setUpdatedAt(now);retained.add(item.getId());replacements.add(item);
        }
        if(retained.isEmpty())repository.deactivateAll(productId,now);else repository.deactivateOthers(productId,retained,now);
        if(!replacements.isEmpty())repository.saveAll(replacements);
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

    private UUID documentId(String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("productDocuments.invalid", e); }
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

}
