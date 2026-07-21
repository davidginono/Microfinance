package com.sacco.mvp.service;

import tools.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.StoredUpload;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyUploadMigrationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void importsVerifiesAndDeletesLegacyFile() throws Exception {
        UUID loanId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Path loanRoot = tempDir.resolve("loans");
        Path file = Files.createDirectories(loanRoot.resolve(loanId.toString())).resolve(attachmentId + ".pdf");
        byte[] content = new byte[]{1, 2, 3};
        Files.write(file, content);

        StoredUploadStorageService storage = mock(StoredUploadStorageService.class);
        when(storage.load(any(), anyString(), anyString())).thenThrow(new IllegalArgumentException("missing"));
        StoredUpload stored = StoredUpload.builder().sizeBytes(content.length).sha256Checksum("checksum").build();
        when(storage.store(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(stored);
        when(storage.verify(stored, content)).thenReturn(true);
        LoanApplicationRepository loans = mock(LoanApplicationRepository.class);
        when(loans.findAll()).thenReturn(List.of(LoanApplication.builder()
            .id(loanId)
            .attachmentsJson("[{\"id\":\"" + attachmentId + "\",\"storedName\":\"" + attachmentId + ".pdf\",\"originalName\":\"proof.pdf\",\"contentType\":\"application/pdf\"}]")
            .build()));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any())).thenReturn(0);
        LegacyUploadMigrationService service = service(storage, loans, jdbc, loanRoot);

        LegacyUploadMigrationService.MigrationSummary summary = service.migrate();

        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        assertThat(file).doesNotExist();
        verify(jdbc).update(anyString(), any(), any(), any(), any());
    }

    @Test
    void retainsFileWhenStoredChecksumDoesNotVerify() throws Exception {
        UUID loanId = UUID.randomUUID();
        Path loanRoot = tempDir.resolve("loans");
        Path file = Files.createDirectories(loanRoot.resolve(loanId.toString())).resolve(UUID.randomUUID() + ".pdf");
        Files.write(file, new byte[]{7});
        StoredUploadStorageService storage = mock(StoredUploadStorageService.class);
        when(storage.load(any(), anyString(), anyString())).thenThrow(new IllegalArgumentException("missing"));
        when(storage.store(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(StoredUpload.builder().build());
        when(storage.verify(any(), any())).thenReturn(false);
        LoanApplicationRepository loans = mock(LoanApplicationRepository.class);
        when(loans.findAll()).thenReturn(List.of(LoanApplication.builder().id(loanId).build()));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any())).thenReturn(0);

        LegacyUploadMigrationService.MigrationSummary summary = service(storage, loans, jdbc, loanRoot).migrate();

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(file).exists();
    }

    @Test
    void isNoOpAfterSuccessfulMigrationMarker() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(Class.class), any())).thenReturn(1);
        LegacyUploadMigrationService service = service(
            mock(StoredUploadStorageService.class), mock(LoanApplicationRepository.class), jdbc, tempDir.resolve("loans"));

        assertThat(service.migrate().alreadyComplete()).isTrue();
    }

    private LegacyUploadMigrationService service(StoredUploadStorageService storage,
                                                 LoanApplicationRepository loans,
                                                 JdbcTemplate jdbc,
                                                 Path loanRoot) {
        LegacyUploadMigrationService service = new LegacyUploadMigrationService(
            storage, loans, mock(RegisteredSaccoRepository.class),
            JsonMapper.builder().findAndAddModules().build(), jdbc);
        ReflectionTestUtils.setField(service, "loanRoot", loanRoot.toString());
        ReflectionTestUtils.setField(service, "logoRoot", tempDir.resolve("logos").toString());
        return service;
    }
}
