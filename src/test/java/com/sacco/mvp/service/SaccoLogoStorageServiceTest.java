package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaccoLogoStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesValidPngLogo() throws IOException {
        SaccoLogoStorageService service = new SaccoLogoStorageService(tempDir.resolve("logos"));

        service.store("SACCO-ARUSHA-001", new MockMultipartFile(
            "logoFile",
            "logo.png",
            "image/png",
            pngBytes(128, 128)
        ));

        assertTrue(service.hasLogo("SACCO-ARUSHA-001"));
        assertEquals("image/png", service.load("SACCO-ARUSHA-001").contentType().toString());
    }

    @Test
    void rejectsLogoOutsideAllowedResolutionRange() throws IOException {
        SaccoLogoStorageService service = new SaccoLogoStorageService(tempDir.resolve("logos"));

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
