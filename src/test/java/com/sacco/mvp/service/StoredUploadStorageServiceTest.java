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

class StoredUploadStorageServiceTest {

    @Test
    void storesContentWithSizeAndSha256() {
        StoredUploadRepository repository = mock(StoredUploadRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        StoredUploadStorageService service = new StoredUploadStorageService(repository);

        StoredUpload upload = service.store(
            UUID.randomUUID(), "LOAN_APPLICATION", UUID.randomUUID().toString(),
            "APPLICATION_ATTACHMENT", "proof.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThat(upload.getSizeBytes()).isEqualTo(3);
        assertThat(upload.getSha256Checksum()).hasSize(64);
        assertThat(service.verify(upload, new byte[]{1, 2, 3})).isTrue();
    }

    @Test
    void loadsOnlyForTheRequestedOwner() {
        StoredUploadRepository repository = mock(StoredUploadRepository.class);
        StoredUploadStorageService service = new StoredUploadStorageService(repository);
        UUID id = UUID.randomUUID();
        StoredUpload upload = StoredUpload.builder().id(id).content(new byte[]{4}).build();
        when(repository.findByIdAndOwnerTypeAndOwnerId(id, "LOAN_APPLICATION", "loan-1"))
            .thenReturn(Optional.of(upload));

        assertThat(service.load(id, "LOAN_APPLICATION", "loan-1").getContent()).containsExactly(4);
        verify(repository).findByIdAndOwnerTypeAndOwnerId(id, "LOAN_APPLICATION", "loan-1");
    }
}
