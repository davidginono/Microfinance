package com.sacco.mvp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.uploads.database-to-file-migration-enabled", havingValue = "true")
public class DatabaseUploadFileMigrationRunner implements ApplicationRunner {
    private final DatabaseUploadFileMigrationService migrationService;

    @Override
    public void run(ApplicationArguments args) {
        migrationService.migrate();
    }
}
