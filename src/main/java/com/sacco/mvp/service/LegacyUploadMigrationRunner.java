package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.repository.LoanApplicationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.uploads.legacy-migration-enabled", havingValue = "true")
@Slf4j
public class LegacyUploadMigrationRunner implements ApplicationRunner {
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanAttachmentService loanAttachmentService;
    private final StoredUploadService storedUploadService;

    private final Path loanRoot;
    private final Path logoRoot;

    public LegacyUploadMigrationRunner(LoanApplicationRepository loanApplicationRepository,
                                       LoanAttachmentService loanAttachmentService,
                                       StoredUploadService storedUploadService) {
        this(
            loanApplicationRepository,
            loanAttachmentService,
            storedUploadService,
            Paths.get("loan-uploads", "applications"),
            Paths.get("branding", "sacco-logos")
        );
    }

    LegacyUploadMigrationRunner(LoanApplicationRepository loanApplicationRepository,
                                LoanAttachmentService loanAttachmentService,
                                StoredUploadService storedUploadService,
                                Path loanRoot,
                                Path logoRoot) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.loanAttachmentService = loanAttachmentService;
        this.storedUploadService = storedUploadService;
        this.loanRoot = loanRoot;
        this.logoRoot = logoRoot;
    }

    @Override
    public void run(ApplicationArguments args) {
        MigrationSummary summary = new MigrationSummary();
        migrateLoanAttachments(summary);
        migrateSaccoLogos(summary);
        log.info(
            "Legacy upload migration finished: imported={}, deleted={}, skipped={}, failed={}",
            summary.imported, summary.deleted, summary.skipped, summary.failed
        );
        if (summary.failed > 0) {
            throw new IllegalStateException("Legacy upload migration completed with " + summary.failed + " failed file(s).");
        }
    }

    private void migrateLoanAttachments(MigrationSummary summary) {
        if (!Files.isDirectory(loanRoot)) {
            return;
        }
        for (Path loanFolder : directories(loanRoot)) {
            UUID loanId;
            try {
                loanId = UUID.fromString(loanFolder.getFileName().toString());
            } catch (IllegalArgumentException ex) {
                summary.skipped++;
                log.warn("Skipping legacy upload directory with invalid loan ID: {}", loanFolder);
                continue;
            }
            LoanApplication loan = loanApplicationRepository.findById(loanId).orElse(null);
            if (loan == null) {
                summary.skipped++;
                log.warn("Skipping legacy uploads because loan application was not found: {}", loanId);
                continue;
            }
            Map<String, Map<String, Object>> metadataByStoredName = loanAttachmentService.parse(loan.getAttachmentsJson()).stream()
                .filter(item -> item.get("storedName") != null)
                .collect(java.util.stream.Collectors.toMap(
                    item -> String.valueOf(item.get("storedName")),
                    item -> item,
                    (left, right) -> left
                ));
            for (Path file : files(loanFolder)) {
                Map<String, Object> metadata = metadataByStoredName.get(file.getFileName().toString());
                if (metadata == null || metadata.get("id") == null) {
                    summary.skipped++;
                    log.warn("Keeping unrecognized legacy loan upload: {}", file);
                    continue;
                }
                try {
                    byte[] content = Files.readAllBytes(file);
                    UUID uploadId = UUID.fromString(String.valueOf(metadata.get("id")));
                    String category = String.valueOf(metadata.getOrDefault(
                        "attachmentCategory", LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT));
                    String originalName = String.valueOf(metadata.getOrDefault("originalName", file.getFileName().toString()));
                    String contentType = String.valueOf(metadata.getOrDefault("contentType", "application/octet-stream"));
                    StoredUploadService.StoredUploadResource resource = storedUploadService.store(
                        uploadId,
                        StoredUploadService.OWNER_LOAN_APPLICATION,
                        loanId.toString(),
                        category,
                        originalName,
                        contentType,
                        content
                    );
                    verifyAndDelete(file, resource, content, summary);
                } catch (Exception ex) {
                    summary.failed++;
                    log.error("Failed to migrate legacy loan upload {}", file, ex);
                }
            }
            deleteIfEmpty(loanFolder);
        }
        deleteIfEmpty(loanRoot);
    }

    private void migrateSaccoLogos(MigrationSummary summary) {
        if (!Files.isDirectory(logoRoot)) {
            return;
        }
        for (Path saccoFolder : directories(logoRoot)) {
            String saccoId = saccoFolder.getFileName().toString();
            for (Path file : files(saccoFolder)) {
                try {
                    byte[] content = Files.readAllBytes(file);
                    StoredUploadService.StoredUploadResource resource = storedUploadService.replaceSingle(
                        StoredUploadService.OWNER_SACCO,
                        saccoId,
                        StoredUploadService.CATEGORY_SACCO_LOGO,
                        file.getFileName().toString(),
                        logoContentType(file),
                        content
                    );
                    verifyAndDelete(file, resource, content, summary);
                } catch (Exception ex) {
                    summary.failed++;
                    log.error("Failed to migrate legacy SACCO logo {}", file, ex);
                }
            }
            deleteIfEmpty(saccoFolder);
        }
        deleteIfEmpty(logoRoot);
    }

    private void verifyAndDelete(Path file,
                                 StoredUploadService.StoredUploadResource resource,
                                 byte[] content,
                                 MigrationSummary summary) throws IOException {
        if (!storedUploadService.isVerified(resource, content)) {
            throw new IllegalStateException("Database upload verification failed.");
        }
        summary.imported++;
        Files.delete(file);
        summary.deleted++;
    }

    private List<Path> directories(Path root) {
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory).sorted().toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list legacy upload directories under " + root, ex);
        }
    }

    private List<Path> files(Path root) {
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to list legacy upload files under " + root, ex);
        }
    }

    private void deleteIfEmpty(Path directory) {
        try (var stream = Files.list(directory)) {
            if (stream.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        } catch (IOException ex) {
            log.warn("Unable to remove empty legacy upload directory {}", directory, ex);
        }
    }

    private String logoContentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private static final class MigrationSummary {
        private int imported;
        private int deleted;
        private int skipped;
        private int failed;
    }
}
