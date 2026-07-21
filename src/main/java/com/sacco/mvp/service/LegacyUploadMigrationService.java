package com.sacco.mvp.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.StoredUpload;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyUploadMigrationService {
    private static final String MIGRATION_KEY = "filesystem-uploads-to-postgresql-v1";

    private final StoredUploadStorageService storedUploadStorageService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.upload-migration.loan-root:loan-uploads/applications}")
    private String loanRoot;

    @Value("${app.upload-migration.logo-root:branding/sacco-logos}")
    private String logoRoot;

    public MigrationSummary migrate() {
        if (isComplete()) {
            log.info("Legacy upload migration is already complete; no files were processed.");
            return new MigrationSummary(0, 0, 0, true);
        }

        MutableSummary summary = new MutableSummary();
        migrateLoanAttachments(Paths.get(loanRoot), summary);
        migrateLogos(Paths.get(logoRoot), summary);
        if (summary.failed == 0) {
            jdbcTemplate.update(
                "insert into stored_upload_migrations (migration_key, completed_at, imported_count, failed_count) values (?, ?, ?, ?)",
                MIGRATION_KEY,
                Timestamp.from(OffsetDateTime.now().toInstant()),
                summary.imported,
                summary.failed
            );
        }
        MigrationSummary result = new MigrationSummary(summary.imported, summary.failed, summary.retained, false);
        log.info("Legacy upload migration finished: imported={}, failed={}, retained={}",
            result.imported(), result.failed(), result.retained());
        return result;
    }

    private void migrateLoanAttachments(Path root, MutableSummary summary) {
        if (!Files.isDirectory(root)) {
            return;
        }
        Map<UUID, LoanApplication> loans = new LinkedHashMap<>();
        loanApplicationRepository.findAll().forEach(loan -> loans.put(loan.getId(), loan));
        try (Stream<Path> folders = Files.list(root)) {
            for (Path folder : folders.filter(Files::isDirectory).toList()) {
                UUID loanId;
                try {
                    loanId = UUID.fromString(folder.getFileName().toString());
                } catch (IllegalArgumentException ex) {
                    retainFailure(folder, summary, "Invalid loan folder name");
                    continue;
                }
                LoanApplication loan = loans.get(loanId);
                Map<String, Map<String, Object>> metadata = attachmentMetadataByStoredName(
                    loan == null ? null : loan.getAttachmentsJson());
                migrateFolderFiles(folder, file -> {
                    Map<String, Object> item = metadata.getOrDefault(file.getFileName().toString(), Collections.emptyMap());
                    UUID uploadId = parseUuid(item.get("id"), file);
                    String category = stringValue(item.get("attachmentCategory"), LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT);
                    String originalName = stringValue(item.get("originalName"), file.getFileName().toString());
                    String contentType = stringValue(item.get("contentType"), probeContentType(file));
                    migrateFile(file, uploadId, StoredUploadStorageService.OWNER_LOAN_APPLICATION,
                        loanId.toString(), category, originalName, contentType, summary);
                }, summary);
            }
        } catch (Exception ex) {
            retainFailure(root, summary, ex.getMessage());
        }
    }

    private void migrateLogos(Path root, MutableSummary summary) {
        if (!Files.isDirectory(root)) {
            return;
        }
        Map<String, String> saccoIdsByFolder = new LinkedHashMap<>();
        for (RegisteredSacco sacco : registeredSaccoRepository.findAll()) {
            saccoIdsByFolder.put(safeFolderName(sacco.getSaccoId()), sacco.getSaccoId());
        }
        try (Stream<Path> folders = Files.list(root)) {
            for (Path folder : folders.filter(Files::isDirectory).toList()) {
                String saccoId = saccoIdsByFolder.getOrDefault(folder.getFileName().toString(), folder.getFileName().toString());
                migrateFolderFiles(folder, file -> migrateFile(
                    file,
                    UUID.nameUUIDFromBytes(("SACCO_LOGO:" + saccoId).getBytes(StandardCharsets.UTF_8)),
                    StoredUploadStorageService.OWNER_SACCO,
                    saccoId,
                    StoredUploadStorageService.CATEGORY_SACCO_LOGO,
                    file.getFileName().toString(),
                    probeContentType(file),
                    summary
                ), summary);
            }
        } catch (Exception ex) {
            retainFailure(root, summary, ex.getMessage());
        }
    }

    private void migrateFolderFiles(Path folder, FileMigration action, MutableSummary summary) {
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                action.migrate(file);
            }
        } catch (Exception ex) {
            retainFailure(folder, summary, ex.getMessage());
        }
        try {
            Files.deleteIfExists(folder);
        } catch (Exception ignored) {
            // A retained file keeps the folder in place for the next run.
        }
    }

    private void migrateFile(Path file,
                             UUID uploadId,
                             String ownerType,
                             String ownerId,
                             String category,
                             String originalName,
                             String contentType,
                             MutableSummary summary) {
        try {
            byte[] source = Files.readAllBytes(file);
            StoredUpload stored;
            try {
                stored = storedUploadStorageService.load(uploadId, ownerType, ownerId);
            } catch (IllegalArgumentException ex) {
                stored = storedUploadStorageService.store(
                    uploadId, ownerType, ownerId, category, originalName, contentType, source);
            }
            if (!storedUploadStorageService.verify(stored, source)) {
                retainFailure(file, summary, "Stored size or SHA-256 mismatch");
                return;
            }
            Files.delete(file);
            summary.imported++;
        } catch (Exception ex) {
            retainFailure(file, summary, ex.getMessage());
        }
    }

    private Map<String, Map<String, Object>> attachmentMetadataByStoredName(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            List<Map<String, Object>> items = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Map<String, Object>> result = new LinkedHashMap<>();
            for (Map<String, Object> item : items) {
                Object storedName = item.get("storedName");
                if (storedName != null) {
                    result.put(String.valueOf(storedName), item);
                }
            }
            return result;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private boolean isComplete() {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from stored_upload_migrations where migration_key = ?",
            Integer.class,
            MIGRATION_KEY
        );
        return count != null && count > 0;
    }

    private UUID parseUuid(Object value, Path file) {
        if (value != null) {
            try {
                return UUID.fromString(String.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // Use the legacy stored filename when possible.
            }
        }
        String name = file.getFileName().toString();
        int extensionIndex = name.indexOf('.');
        try {
            return UUID.fromString(extensionIndex < 0 ? name : name.substring(0, extensionIndex));
        } catch (IllegalArgumentException ex) {
            return UUID.randomUUID();
        }
    }

    private String probeContentType(Path file) {
        try {
            String type = Files.probeContentType(file);
            return type == null || type.isBlank() ? "application/octet-stream" : type;
        } catch (Exception ex) {
            return "application/octet-stream";
        }
    }

    private String stringValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String safeFolderName(String saccoId) {
        return saccoId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void retainFailure(Path path, MutableSummary summary, String reason) {
        summary.failed++;
        summary.retained++;
        log.error("Legacy upload retained at {}: {}", path, reason == null ? "migration failure" : reason);
    }

    @FunctionalInterface
    private interface FileMigration {
        void migrate(Path file);
    }

    private static final class MutableSummary {
        private long imported;
        private long failed;
        private long retained;
    }

    public record MigrationSummary(long imported, long failed, long retained, boolean alreadyComplete) {
    }
}
