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
public class StoredUploadService {
    public static final String OWNER_LOAN_APPLICATION = "LOAN_APPLICATION";
    public static final String OWNER_SACCO = "SACCO";
    public static final String CATEGORY_SACCO_LOGO = "SACCO_LOGO";

    private final StoredUploadRepository storedUploadRepository;

    @Transactional
    public StoredUploadResource store(UUID id,
                                      String ownerType,
                                      String ownerId,
                                      String category,
                                      String originalName,
                                      String contentType,
                                      byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Upload content is required.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        StoredUpload upload = storedUploadRepository.findById(id).orElseGet(StoredUpload::new);
        upload.setId(id);
        upload.setOwnerType(ownerType);
        upload.setOwnerId(ownerId);
        upload.setCategory(category);
        upload.setOriginalName(originalName);
        upload.setContentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType);
        upload.setSizeBytes(content.length);
        upload.setSha256(checksum(content));
        upload.setContent(content);
        upload.setCreatedAt(upload.getCreatedAt() == null ? now : upload.getCreatedAt());
        upload.setUpdatedAt(now);
        StoredUpload saved = storedUploadRepository.save(upload);
        return resource(saved);
    }

    @Transactional
    public StoredUploadResource replaceSingle(String ownerType,
                                              String ownerId,
                                              String category,
                                              String originalName,
                                              String contentType,
                                              byte[] content) {
        storedUploadRepository.deleteByOwnerTypeAndOwnerIdAndCategory(ownerType, ownerId, category);
        return store(UUID.randomUUID(), ownerType, ownerId, category, originalName, contentType, content);
    }

    @Transactional(readOnly = true)
    public StoredUploadResource load(UUID id, String ownerType, String ownerId) {
        return storedUploadRepository.findByIdAndOwnerTypeAndOwnerId(id, ownerType, ownerId)
            .map(this::resource)
            .orElseThrow(() -> new IllegalArgumentException("Upload not found"));
    }

    @Transactional(readOnly = true)
    public StoredUploadResource loadSingle(String ownerType, String ownerId, String category) {
        return storedUploadRepository.findFirstByOwnerTypeAndOwnerIdAndCategoryOrderByUpdatedAtDesc(ownerType, ownerId, category)
            .map(this::resource)
            .orElseThrow(() -> new IllegalArgumentException("Upload not found"));
    }

    @Transactional(readOnly = true)
    public boolean exists(String ownerType, String ownerId, String category) {
        return storedUploadRepository.existsByOwnerTypeAndOwnerIdAndCategory(ownerType, ownerId, category);
    }

    @Transactional
    public void deleteOwner(String ownerType, String ownerId) {
        storedUploadRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    public boolean isVerified(StoredUploadResource resource, byte[] expectedContent) {
        return resource != null
            && expectedContent != null
            && resource.sizeBytes() == expectedContent.length
            && resource.sha256().equals(checksum(expectedContent));
    }

    public String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private StoredUploadResource resource(StoredUpload upload) {
        return new StoredUploadResource(
            upload.getId(),
            upload.getOriginalName(),
            upload.getContentType(),
            upload.getSizeBytes(),
            upload.getSha256(),
            upload.getContent()
        );
    }

    public record StoredUploadResource(
        UUID id,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        byte[] content
    ) {
    }
}
