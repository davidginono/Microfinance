package com.sacco.mvp.service;

import com.sacco.mvp.domain.StoredUpload;
import com.sacco.mvp.repository.StoredUploadRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoredUploadServiceTest {
    @Test
    void storesAndVerifiesBinaryContent() {
        StoredUploadRepository repository = mock(StoredUploadRepository.class);
        StoredUploadService service = new StoredUploadService(repository);
        UUID id = UUID.randomUUID();
        byte[] content = "receipt-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(repository.findById(id)).thenReturn(Optional.empty());
        when(repository.save(any(StoredUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoredUploadService.StoredUploadResource resource = service.store(
            id, StoredUploadService.OWNER_LOAN_APPLICATION, "loan-1", "RECEIPT",
            "receipt.pdf", "application/pdf", content
        );

        assertThat(service.isVerified(resource, content)).isTrue();
        assertThat(resource.content()).isEqualTo(content);
        verify(repository).save(any(StoredUpload.class));
    }
}
