package com.sacco.mvp.service;

import com.sacco.mvp.domain.StoredUpload;
import com.sacco.mvp.repository.StoredUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseUploadFileMigrationService {
    private final StoredUploadRepository repository;
    private final StoredUploadStorageService storageService;

    @Value("${app.uploads.database-to-file-migration-batch-size:100}")
    private int batchSize;

    public MigrationSummary migrate() {
        int imported = 0;
        int failed = 0;
        while (true) {
            List<StoredUpload> uploads = repository.findDatabaseBackedUploads(
                PageRequest.of(0, Math.max(1, Math.min(batchSize, 500)))
            );
            if (uploads.isEmpty()) {
                break;
            }
            for (StoredUpload upload : uploads) {
                try {
                    storageService.migrateDatabaseContentToLocalFile(upload);
                    imported++;
                } catch (Exception ex) {
                    failed++;
                    log.error("Stored upload {} could not be migrated to file storage: {}",
                        upload == null ? null : upload.getId(), ex.getMessage());
                }
            }
            if (failed > 0) {
                break;
            }
        }
        log.info("Database upload file migration finished: imported={}, failed={}", imported, failed);
        return new MigrationSummary(imported, failed);
    }

    public record MigrationSummary(int imported, int failed) {
    }
}
