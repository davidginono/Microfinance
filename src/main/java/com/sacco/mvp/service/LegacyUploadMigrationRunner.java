package com.sacco.mvp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.upload-migration.enabled", havingValue = "true")
public class LegacyUploadMigrationRunner implements ApplicationRunner {
    private final LegacyUploadMigrationService migrationService;

    @Override
    public void run(ApplicationArguments args) {
        migrationService.migrate();
    }
}
