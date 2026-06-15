package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyUploadMigrationRunnerTest {
    @TempDir Path tempDir;

    @Test
    void verifiedLegacyFileIsDeletedAndRerunIsNoOp() throws Exception {
        UUID loanId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Path loanRoot = tempDir.resolve("loan-uploads");
        Path logoRoot = tempDir.resolve("logos");
        Path loanFolder = Files.createDirectories(loanRoot.resolve(loanId.toString()));
        Path legacyFile = loanFolder.resolve(attachmentId + ".pdf");
        byte[] content = "legacy receipt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(legacyFile, content);

        LoanApplicationRepository loans = mock(LoanApplicationRepository.class);
        LoanAttachmentService attachments = mock(LoanAttachmentService.class);
        StoredUploadService uploads = mock(StoredUploadService.class);
        LoanApplication loan = LoanApplication.builder().id(loanId).attachmentsJson("[{}]").build();
        when(loans.findById(loanId)).thenReturn(Optional.of(loan));
        when(attachments.parse(loan.getAttachmentsJson())).thenReturn(List.of(Map.of(
            "id", attachmentId.toString(),
            "storedName", legacyFile.getFileName().toString(),
            "originalName", "receipt.pdf",
            "contentType", "application/pdf",
            "attachmentCategory", LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF
        )));
        StoredUploadService.StoredUploadResource resource = new StoredUploadService.StoredUploadResource(
            attachmentId, "receipt.pdf", "application/pdf", content.length, "hash", content
        );
        when(uploads.store(
            eq(attachmentId), eq(StoredUploadService.OWNER_LOAN_APPLICATION), eq(loanId.toString()),
            eq(LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF), eq("receipt.pdf"), eq("application/pdf"), eq(content)
        )).thenReturn(resource);
        when(uploads.isVerified(resource, content)).thenReturn(true);

        LegacyUploadMigrationRunner runner = new LegacyUploadMigrationRunner(loans, attachments, uploads, loanRoot, logoRoot);
        runner.run(mock(org.springframework.boot.ApplicationArguments.class));
        runner.run(mock(org.springframework.boot.ApplicationArguments.class));

        assertThat(legacyFile).doesNotExist();
        verify(uploads).store(any(), any(), any(), any(), any(), any(), any());
    }
}
