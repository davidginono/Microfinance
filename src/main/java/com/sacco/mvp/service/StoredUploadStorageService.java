package com.sacco.mvp.service;

import com.sacco.mvp.domain.StoredUpload;
import com.sacco.mvp.repository.StoredUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoredUploadStorageService {
    public static final String OWNER_LOAN_APPLICATION = "LOAN_APPLICATION";
    public static final String OWNER_SACCO = "SACCO";
    public static final String CATEGORY_SACCO_LOGO = "SACCO_LOGO";

    private final StoredUploadRepository repository;

    @Transactional
    public StoredUpload store(UUID id,
                              String ownerType,
                              String ownerId,
                              String category,
                              String originalName,
                              String contentType,
                              byte[] content) {
        OffsetDateTime now = OffsetDateTime.now();
        StoredUpload upload = StoredUpload.builder()
            .id(id == null ? UUID.randomUUID() : id)
            .ownerType(ownerType)
            .ownerId(ownerId)
            .category(category)
            .originalName(originalName)
            .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
            .sizeBytes(content.length)
            .sha256Checksum(sha256(content))
            .content(content)
            .createdAt(now)
            .updatedAt(now)
            .build();
        return repository.save(upload);
    }

    @Transactional(readOnly = true)
    public StoredUpload load(UUID id, String ownerType, String ownerId) {
        return repository.findByIdAndOwnerTypeAndOwnerId(id, ownerType, ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Stored upload not found"));
    }

    @Transactional(readOnly = true)
    public StoredUpload loadLatest(String ownerType, String ownerId, String category) {
        return repository.findFirstByOwnerTypeAndOwnerIdAndCategoryOrderByUpdatedAtDesc(ownerType, ownerId, category)
            .orElseThrow(() -> new IllegalArgumentException("Stored upload not found"));
    }

    @Transactional(readOnly = true)
    public boolean exists(String ownerType, String ownerId, String category) {
        return repository.existsByOwnerTypeAndOwnerIdAndCategory(ownerType, ownerId, category);
    }

    @Transactional
    public void deleteOwner(String ownerType, String ownerId) {
        repository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    @Transactional
    public void replaceCategory(String ownerType,
                                String ownerId,
                                String category,
                                String originalName,
                                String contentType,
                                byte[] content) {
        repository.deleteByOwnerTypeAndOwnerIdAndCategory(ownerType, ownerId, category);
        store(null, ownerType, ownerId, category, originalName, contentType, content);
    }

    public boolean verify(StoredUpload upload, byte[] expectedContent) {
        return upload != null
            && upload.getSizeBytes() == expectedContent.length
            && upload.getSha256Checksum().equals(sha256(expectedContent));
    }

    public String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
