package com.sacco.mvp.service;

import tools.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.StoredUpload;
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
        StoredUploadStorageService storage = mock(StoredUploadStorageService.class);
        LoanAttachmentService service = new LoanAttachmentService(
            JsonMapper.builder().findAndAddModules().build(), mock(AdminAlertService.class), storage);
        UUID loanId = UUID.randomUUID();

        String json = service.store(loanId, List.of(
            new MockMultipartFile("attachments", "statement.pdf", "application/pdf", new byte[]{1, 2, 3})
        ), null);

        assertThat(json).contains("statement.pdf").contains("APPLICATION_ATTACHMENT");
        verify(storage).store(any(UUID.class), eq(StoredUploadStorageService.OWNER_LOAN_APPLICATION),
            eq(loanId.toString()), eq(LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT),
            eq("statement.pdf"), eq("application/pdf"), any(byte[].class));
    }

    @Test
    void loadsBinaryContentUsingAuthorizedMetadataId() {
        StoredUploadStorageService storage = mock(StoredUploadStorageService.class);
        UUID loanId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        when(storage.load(attachmentId, StoredUploadStorageService.OWNER_LOAN_APPLICATION, loanId.toString()))
            .thenReturn(StoredUpload.builder().content(new byte[]{9, 8}).contentType("application/pdf").build());
        LoanAttachmentService service = new LoanAttachmentService(
            JsonMapper.builder().findAndAddModules().build(), mock(AdminAlertService.class), storage);
        String json = "[{\"id\":\"" + attachmentId + "\",\"originalName\":\"proof.pdf\",\"storedName\":\"proof.pdf\"}]";

        LoanAttachmentService.AttachmentResource resource = service.load(loanId, attachmentId.toString(), json);

        assertThat(resource.getContent()).containsExactly(9, 8);
        assertThat(resource.getOriginalName()).isEqualTo("proof.pdf");
    }
}
