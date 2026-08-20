package com.sacco.mvp.service;

import com.sacco.mvp.domain.StoredUpload;
import com.sacco.mvp.repository.StoredUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoredUploadStorageService {
    public static final String OWNER_LOAN_APPLICATION = "LOAN_APPLICATION";
    public static final String OWNER_MEMBER = "MEMBER";
    public static final String CATEGORY_MEMBER_PROFILE_PHOTO = "MEMBER_PROFILE_PHOTO";
    public static final String STORAGE_BACKEND_DATABASE = "DATABASE";
    public static final String STORAGE_BACKEND_LOCAL_FILE = "LOCAL_FILE";

    private final StoredUploadRepository repository;

    @Value("${app.uploads.files-root:stored-uploads}")
    private String uploadsRoot;

    @Transactional
    public StoredUpload store(UUID id,
                              String ownerType,
                              String ownerId,
                              String category,
                              String originalName,
                              String contentType,
                              byte[] content) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID uploadId = id == null ? UUID.randomUUID() : id;
        String storageKey = storageKey(uploadId, ownerType, ownerId, category, originalName);
        writeLocalFile(storageKey, content);
        StoredUpload upload = StoredUpload.builder()
            .id(uploadId)
            .ownerType(ownerType)
            .ownerId(ownerId)
            .category(category)
            .originalName(originalName)
            .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
            .sizeBytes(content.length)
            .sha256Checksum(sha256(content))
            .storageBackend(STORAGE_BACKEND_LOCAL_FILE)
            .storageKey(storageKey)
            .content(null)
            .createdAt(now)
            .updatedAt(now)
            .build();
        StoredUpload saved;
        try {
            saved = repository.save(upload);
        } catch (RuntimeException ex) {
            deleteLocalFile(storageKey);
            throw ex;
        }
        deleteLocalFileAfterRollback(storageKey);
        return saved;
    }

    @Transactional(readOnly = true)
    public StoredUpload load(UUID id, String ownerType, String ownerId) {
        return withContent(repository.findByIdAndOwnerTypeAndOwnerId(id, ownerType, ownerId)
            .orElseThrow(() -> new IllegalArgumentException("Stored upload not found")));
    }

    @Transactional(readOnly = true)
    public StoredUpload loadLatest(String ownerType, String ownerId, String category) {
        return withContent(repository.findFirstByOwnerTypeAndOwnerIdAndCategoryOrderByUpdatedAtDesc(ownerType, ownerId, category)
            .orElseThrow(() -> new IllegalArgumentException("Stored upload not found")));
    }

    @Transactional(readOnly = true)
    public boolean exists(String ownerType, String ownerId, String category) {
        return repository.existsByOwnerTypeAndOwnerIdAndCategory(ownerType, ownerId, category);
    }

    @Transactional
    public void delete(UUID id, String ownerType, String ownerId) {
        repository.findByIdAndOwnerTypeAndOwnerId(id, ownerType, ownerId).ifPresent(upload -> {
            deleteLocalUploadsAfterCommit(List.of(upload));
            repository.delete(upload);
        });
    }

    @Transactional
    public void deleteOwner(String ownerType, String ownerId) {
        deleteLocalUploadsAfterCommit(repository.findByOwnerTypeAndOwnerId(ownerType, ownerId));
        repository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    @Transactional
    public void deleteCategory(String ownerType, String ownerId, String category) {
        deleteLocalUploadsAfterCommit(repository.findByOwnerTypeAndOwnerIdAndCategory(ownerType, ownerId, category));
        repository.deleteByOwnerTypeAndOwnerIdAndCategory(ownerType, ownerId, category);
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
        byte[] storedContent = content(upload);
        return upload != null
            && upload.getSizeBytes() == expectedContent.length
            && upload.getSha256Checksum().equals(sha256(expectedContent))
            && storedContent.length == expectedContent.length
            && upload.getSha256Checksum().equals(sha256(storedContent));
    }

    @Transactional
    public StoredUpload migrateDatabaseContentToLocalFile(StoredUpload upload) {
        if (upload == null || upload.getContent() == null || upload.getContent().length == 0) {
            return upload;
        }
        String storageKey = storageKey(upload.getId(), upload.getOwnerType(), upload.getOwnerId(),
            upload.getCategory(), upload.getOriginalName());
        writeLocalFile(storageKey, upload.getContent());
        upload.setStorageBackend(STORAGE_BACKEND_LOCAL_FILE);
        upload.setStorageKey(storageKey);
        upload.setContent(null);
        upload.setUpdatedAt(OffsetDateTime.now());
        try {
            StoredUpload saved = repository.save(upload);
            deleteLocalFileAfterRollback(storageKey);
            return saved;
        } catch (RuntimeException ex) {
            deleteLocalFile(storageKey);
            throw ex;
        }
    }

    public String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private StoredUpload withContent(StoredUpload upload) {
        return upload.toBuilder().content(content(upload)).build();
    }

    private byte[] content(StoredUpload upload) {
        if (upload == null) {
            throw new IllegalArgumentException("Stored upload not found");
        }
        if (upload.getStorageKey() == null || upload.getStorageKey().isBlank()) {
            byte[] databaseContent = upload.getContent();
            if (databaseContent == null) {
                throw new IllegalArgumentException("Stored upload content not found");
            }
            return databaseContent;
        }
        Path path = resolveStorageKey(upload.getStorageKey());
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Stored upload content not found", ex);
        }
    }

    private String storageKey(UUID id, String ownerType, String ownerId, String category, String originalName) {
        String extension = StringUtils.getFilenameExtension(originalName == null ? "" : originalName);
        String fileName = id.toString();
        if (extension != null && !extension.isBlank()) {
            fileName += "." + safeSegment(extension.toLowerCase(Locale.ROOT));
        }
        return String.join("/",
            safeSegment(ownerType),
            safeSegment(ownerId),
            safeSegment(category),
            fileName
        );
    }

    private String safeSegment(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
            throw new IllegalStateException("Upload storage path is invalid.");
        }
        return safe;
    }

    private void writeLocalFile(String storageKey, byte[] content) {
        Path target = resolveStorageKey(storageKey);
        Path tempFile = null;
        try {
            Files.createDirectories(target.getParent());
            tempFile = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            Files.write(tempFile, content);
            try {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save upload content.", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Temporary upload files are best-effort cleanup only.
                }
            }
        }
    }

    public void deleteLocalFilesAfterCommit(List<String> storageKeys) {
        deleteStorageKeysAfterCommit(storageKeys);
    }

    private void deleteLocalUploadsAfterCommit(List<StoredUpload> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            return;
        }
        List<String> storageKeys = uploads.stream()
            .map(StoredUpload::getStorageKey)
            .filter(key -> key != null && !key.isBlank())
            .toList();
        deleteStorageKeysAfterCommit(storageKeys);
    }

    private void deleteStorageKeysAfterCommit(List<String> storageKeys) {
        if (storageKeys == null) {
            return;
        }
        if (storageKeys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storageKeys.forEach(this::deleteLocalFile);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageKeys.forEach(StoredUploadStorageService.this::deleteLocalFile);
            }
        });
    }

    private void deleteLocalFileAfterRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteLocalFile(storageKey);
                }
            }
        });
    }

    private void deleteLocalFile(String storageKey) {
        try {
            Files.deleteIfExists(resolveStorageKey(storageKey));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to remove upload content.", ex);
        }
    }

    private Path resolveStorageKey(String storageKey) {
        Path root = Paths.get(uploadsRoot == null || uploadsRoot.isBlank() ? "stored-uploads" : uploadsRoot)
            .toAbsolutePath()
            .normalize();
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalStateException("Upload storage path is invalid.");
        }
        return path;
    }
}
