package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoanAttachmentServiceTest {
    @Test
    void storesMetadataAndBinaryContentInDatabaseStorage() {
        StoredUploadService uploads = mock(StoredUploadService.class);
        LoanAttachmentService service = new LoanAttachmentService(
            JsonMapper.builder().findAndAddModules().build(),
            mock(AdminAlertService.class),
            uploads
        );
        UUID loanId = UUID.randomUUID();
        byte[] content = "application attachment".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String metadata = service.store(
            loanId,
            List.of(new MockMultipartFile("attachments", "support.pdf", "application/pdf", content)),
            "[]"
        );

        assertThat(service.parse(metadata).getFirst())
            .containsEntry("originalName", "support.pdf")
            .containsEntry("attachmentCategory", LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT);
        verify(uploads).store(
            any(UUID.class),
            eq(StoredUploadService.OWNER_LOAN_APPLICATION),
            eq(loanId.toString()),
            eq(LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT),
            eq("support.pdf"),
            eq("application/pdf"),
            eq(content)
        );
    }

    @Test
    void loadsBinaryContentUsingAttachmentMetadataId() {
        StoredUploadService uploads = mock(StoredUploadService.class);
        LoanAttachmentService service = new LoanAttachmentService(
            JsonMapper.builder().findAndAddModules().build(),
            mock(AdminAlertService.class),
            uploads
        );
        UUID loanId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        byte[] content = "receipt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(uploads.load(attachmentId, StoredUploadService.OWNER_LOAN_APPLICATION, loanId.toString()))
            .thenReturn(new StoredUploadService.StoredUploadResource(
                attachmentId, "receipt.pdf", "application/pdf", content.length, "hash", content
            ));
        String metadata = "[{\"id\":\"" + attachmentId + "\",\"originalName\":\"receipt.pdf\",\"contentType\":\"application/pdf\"}]";

        LoanAttachmentService.AttachmentResource resource = service.load(loanId, attachmentId.toString(), metadata);

        assertThat(resource.getContent()).isEqualTo(content);
        assertThat(resource.getOriginalName()).isEqualTo("receipt.pdf");
    }
}
