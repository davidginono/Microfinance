package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaccoLogoStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsSaccoImagesDirectoryWhenNoLogoIsProvided() {
        SaccoLogoStorageService service = service();

        service.store("SACCO-ARUSHA-001", null);

        assertThat(tempDir.resolve("SACCO-ARUSHA-001").resolve("images")).isDirectory();
        assertThat(service.hasLogo("SACCO-ARUSHA-001")).isFalse();
    }

    @Test
    void storesAndLoadsValidPngLogoFromSaccoImagesDirectory() throws IOException {
        SaccoLogoStorageService service = service();
        byte[] content = pngBytes(128, 128);

        service.store("SACCO-ARUSHA-001", new MockMultipartFile(
            "logoFile", "logo.png", "image/png", content));

        Path logoPath = tempDir.resolve("SACCO-ARUSHA-001").resolve("images").resolve("logo.png");
        assertThat(logoPath).exists();
        assertThat(Files.readAllBytes(logoPath)).containsExactly(content);
        assertThat(service.hasLogo("SACCO-ARUSHA-001")).isTrue();
        assertEquals("image/png", service.load("SACCO-ARUSHA-001").contentType().toString());
        assertThat(service.load("SACCO-ARUSHA-001").content()).containsExactly(content);
    }

    @Test
    void replacesOldLogoExtensionInsideSaccoImagesDirectory() throws IOException {
        SaccoLogoStorageService service = service();

        service.store("SACCO-ARUSHA-001", new MockMultipartFile(
            "logoFile", "logo.jpg", "image/jpeg", jpgBytes(128, 128)));
        service.store("SACCO-ARUSHA-001", new MockMultipartFile(
            "logoFile", "logo.png", "image/png", pngBytes(128, 128)));

        Path images = tempDir.resolve("SACCO-ARUSHA-001").resolve("images");
        assertThat(images.resolve("logo.png")).exists();
        assertThat(images.resolve("logo.jpeg")).doesNotExist();
        assertThat(images.resolve("logo.jpg")).doesNotExist();
    }

    @Test
    void deletesLogoFromSaccoImagesDirectory() throws IOException {
        SaccoLogoStorageService service = service();
        service.store("SACCO-ARUSHA-001", new MockMultipartFile(
            "logoFile", "logo.png", "image/png", pngBytes(128, 128)));

        service.delete(" SACCO-ARUSHA-001 ");

        assertThat(tempDir.resolve("SACCO-ARUSHA-001").resolve("images").resolve("logo.png")).doesNotExist();
        assertThat(tempDir.resolve("SACCO-ARUSHA-001").resolve("images")).isDirectory();
    }

    @Test
    void rejectsLogoOutsideAllowedResolutionRange() throws IOException {
        SaccoLogoStorageService service = service();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.store(
            "SACCO-ARUSHA-001",
            new MockMultipartFile("logoFile", "logo.png", "image/png", pngBytes(48, 48))
        ));

        assertEquals("SACCO logo image must be between 64x64 and 1024x1024 pixels.", ex.getMessage());
    }

    @Test
    void rejectsLogoUsingConfiguredResolutionRange() throws IOException {
        PlatformBrandingSettingsService settings = mock(PlatformBrandingSettingsService.class);
        when(settings.logoUploadPolicy()).thenReturn(new LogoUploadPolicy(128, 128, 256, 256, 1024));
        SaccoLogoStorageService service = new SaccoLogoStorageService(tempDir.toString(), settings);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.store(
            "SACCO-ARUSHA-001",
            new MockMultipartFile("logoFile", "logo.png", "image/png", pngBytes(96, 96))
        ));

        assertEquals("SACCO logo image must be between 128x128 and 256x256 pixels.", ex.getMessage());
    }

    private SaccoLogoStorageService service() {
        PlatformBrandingSettingsService settings = mock(PlatformBrandingSettingsService.class);
        when(settings.logoUploadPolicy()).thenReturn(new LogoUploadPolicy(64, 64, 1024, 1024, 1024));
        return new SaccoLogoStorageService(tempDir.toString(), settings);
    }

    private byte[] pngBytes(int width, int height) throws IOException {
        return imageBytes(width, height, "png", BufferedImage.TYPE_INT_ARGB);
    }

    private byte[] jpgBytes(int width, int height) throws IOException {
        return imageBytes(width, height, "jpg", BufferedImage.TYPE_INT_RGB);
    }

    private byte[] imageBytes(int width, int height, String format, int type) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
