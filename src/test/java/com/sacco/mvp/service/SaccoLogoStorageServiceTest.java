package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaccoLogoStorageServiceTest {

    @Test
    void storesValidPngLogoInDatabaseStorage() throws IOException {
        StoredUploadService uploads = mock(StoredUploadService.class);
        byte[] content = pngBytes(128, 128);
        SaccoLogoStorageService service = new SaccoLogoStorageService(uploads);
        when(uploads.exists(
            StoredUploadService.OWNER_SACCO, "SACCO-ARUSHA-001", StoredUploadService.CATEGORY_SACCO_LOGO
        )).thenReturn(true);
        when(uploads.loadSingle(
            StoredUploadService.OWNER_SACCO, "SACCO-ARUSHA-001", StoredUploadService.CATEGORY_SACCO_LOGO
        )).thenReturn(new StoredUploadService.StoredUploadResource(
            UUID.randomUUID(), "logo.png", "image/png", content.length, "hash", content
        ));

        service.store("SACCO-ARUSHA-001", new MockMultipartFile("logoFile", "logo.png", "image/png", content));

        verify(uploads).replaceSingle(
            eq(StoredUploadService.OWNER_SACCO),
            eq("SACCO-ARUSHA-001"),
            eq(StoredUploadService.CATEGORY_SACCO_LOGO),
            eq("logo.png"),
            eq("image/png"),
            any(byte[].class)
        );
        assertTrue(service.hasLogo("SACCO-ARUSHA-001"));
        assertEquals("image/png", service.load("SACCO-ARUSHA-001").contentType().toString());
    }

    @Test
    void rejectsLogoOutsideAllowedResolutionRange() throws IOException {
        SaccoLogoStorageService service = new SaccoLogoStorageService(mock(StoredUploadService.class));

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
