package com.sacco.mvp.service;

import tools.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.StoredUpload;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoanAttachmentServiceTest {

    @Test
    void storesMetadataAndBinaryContentInStorage() {
        StoredUploadStorageService storage = mock(StoredUploadStorageService.class);
        LoanAttachmentService service = new LoanAttachmentService(
            JsonMapper.builder().findAndAddModules().build(), mock(AdminAlertService.class), storage);
        UUID loanId = UUID.randomUUID();

        String json = service.store(loanId, List.of(
            new MockMultipartFile("attachments", "statement.pdf", "application/pdf", "%PDF-1.4\n%%EOF".getBytes())
        ), null);

        assertThat(json).contains("statement.pdf").contains("APPLICATION_ATTACHMENT");
        verify(storage).store(any(UUID.class), eq(StoredUploadStorageService.OWNER_LOAN_APPLICATION),
            eq(loanId.toString()), eq(LoanAttachmentService.CATEGORY_APPLICATION_ATTACHMENT),
            eq("statement.pdf"), eq("application/pdf"), any(byte[].class));
    }

    @Test
    void rejectsAttachmentsThatCannotBeAppendedToPrintableExports() {
        LoanAttachmentService service = new LoanAttachmentService(
            JsonMapper.builder().findAndAddModules().build(), mock(AdminAlertService.class), mock(StoredUploadStorageService.class));

        assertThatThrownBy(() -> service.store(UUID.randomUUID(), List.of(
            new MockMultipartFile("attachments", "statement.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{1, 2, 3})
        ), null)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PDF, PNG, or JPEG");
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

    @Test
    void removesOnlyRequestedApplicationAttachmentMetadataAndStorage() {
        StoredUploadStorageService storage = mock(StoredUploadStorageService.class);
        LoanAttachmentService service = new LoanAttachmentService(
            JsonMapper.builder().findAndAddModules().build(), mock(AdminAlertService.class), storage);
        UUID loanId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        UUID remainingId = UUID.randomUUID();
        String json = """
            [
              {"id":"%s","originalName":"idp.pdf","attachmentCategory":"APPLICATION_ATTACHMENT"},
              {"id":"%s","originalName":"salary.pdf","attachmentCategory":"APPLICATION_ATTACHMENT"}
            ]
            """.formatted(attachmentId, remainingId);

        LoanAttachmentService.AttachmentRemoval removal = service.removeApplicationAttachment(loanId, attachmentId.toString(), json);

        assertThat(removal.attachmentsJson()).doesNotContain(attachmentId.toString(), "idp.pdf");
        assertThat(removal.attachmentsJson()).contains(remainingId.toString(), "salary.pdf");
        assertThat(removal.originalName()).isEqualTo("idp.pdf");
        verify(storage).delete(attachmentId, StoredUploadStorageService.OWNER_LOAN_APPLICATION, loanId.toString());
    }

    @Test
    void refusesToRemoveDisbursementProofThroughApplicantAttachmentRemoval() {
        StoredUploadStorageService storage = mock(StoredUploadStorageService.class);
        LoanAttachmentService service = new LoanAttachmentService(
            JsonMapper.builder().findAndAddModules().build(), mock(AdminAlertService.class), storage);
        UUID loanId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        String json = "[{\"id\":\"" + attachmentId + "\",\"originalName\":\"proof.pdf\",\"attachmentCategory\":\"DISBURSEMENT_PROOF\"}]";

        assertThatThrownBy(() -> service.removeApplicationAttachment(loanId, attachmentId.toString(), json))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Attachment not found");

        verify(storage, never()).delete(any(), any(), any());
    }
}
