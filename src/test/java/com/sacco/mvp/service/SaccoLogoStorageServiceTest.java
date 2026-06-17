package com.sacco.mvp.service;

import com.sacco.mvp.domain.StoredUpload;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaccoLogoStorageServiceTest {

    @Test
    void storesValidPngLogoInDatabaseStorage() throws IOException {
        StoredUploadStorageService storage = mock(StoredUploadStorageService.class);
        SaccoLogoStorageService service = new SaccoLogoStorageService(storage);
        byte[] content = pngBytes(128, 128);

        service.store("SACCO-ARUSHA-001", new MockMultipartFile(
            "logoFile", "logo.png", "image/png", content));

        verify(storage).replaceCategory(
            eq(StoredUploadStorageService.OWNER_SACCO),
            eq("SACCO-ARUSHA-001"),
            eq(StoredUploadStorageService.CATEGORY_SACCO_LOGO),
            eq("logo.png"),
            eq("image/png"),
            any(byte[].class)
        );
    }

    @Test
    void loadsLogoBytesFromDatabaseStorage() {
        StoredUploadStorageService storage = mock(StoredUploadStorageService.class);
        when(storage.loadLatest(any(), any(), any())).thenReturn(StoredUpload.builder()
            .content(new byte[]{1, 2, 3})
            .contentType("image/png")
            .build());
        SaccoLogoStorageService service = new SaccoLogoStorageService(storage);

        assertEquals("image/png", service.load("SACCO-ARUSHA-001").contentType().toString());
    }

    @Test
    void rejectsLogoOutsideAllowedResolutionRange() throws IOException {
        SaccoLogoStorageService service = new SaccoLogoStorageService(mock(StoredUploadStorageService.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.store(
            "SACCO-ARUSHA-001",
            new MockMultipartFile("logoFile", "logo.png", "image/png", pngBytes(48, 48))
        ));

        assertEquals("SACCO logo image must be between 64x64 and 1024x1024 pixels.", ex.getMessage());
    }

    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
