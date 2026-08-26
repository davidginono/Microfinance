package com.sacco.mvp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class SaccoLogoStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final List<String> LOGO_FILE_NAMES = List.of("logo.png", "logo.jpeg", "logo.jpg");
    private static final String IMAGES_DIRECTORY = "images";

    private final Path saccoFilesRoot;
    private final PlatformBrandingSettingsService platformBrandingSettingsService;

    public SaccoLogoStorageService(@Value("${app.saccos.files-root:registered-saccos}") String saccoFilesRoot,
                                   PlatformBrandingSettingsService platformBrandingSettingsService) {
        this.saccoFilesRoot = Paths.get(saccoFilesRoot).toAbsolutePath().normalize();
        this.platformBrandingSettingsService = platformBrandingSettingsService;
    }

    public void ensureSaccoDirectories(String saccoId) {
        createDirectories(imagesDirectory(requireSaccoId(saccoId)));
    }

    public void store(String saccoId, MultipartFile logoFile) {
        String normalizedSaccoId = requireSaccoId(saccoId);
        if (logoFile == null || logoFile.isEmpty()) {
            return;
        }

        ensureSaccoDirectories(normalizedSaccoId);
        LogoUploadPolicy policy = platformBrandingSettingsService.logoUploadPolicy();
        String extension = resolveAllowedExtension(logoFile);
        validateSize(logoFile, policy);
        byte[] bytes = readBytes(logoFile);
        validateImageDimensions(bytes, policy);
        Path imagesDirectory = imagesDirectory(normalizedSaccoId);
        Path target = imagesDirectory.resolve("logo." + extension);
        writeLogo(target, bytes);
        deleteStaleLogoFiles(imagesDirectory, target);
    }

    public boolean hasLogo(String saccoId) {
        return saccoId != null && !saccoId.isBlank() && findLogoPath(saccoId.trim()).isPresent();
    }

    public void delete(String saccoId) {
        deleteLogoFiles(imagesDirectory(requireSaccoId(saccoId)));
    }

    public void deleteSaccoFiles(String saccoId) {
        Path saccoDirectory = saccoDirectory(requireSaccoId(saccoId));
        if (!saccoDirectory.startsWith(saccoFilesRoot)) {
            throw new IllegalStateException("SACCO logo storage path is invalid.");
        }
        if (!Files.exists(saccoDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(saccoDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to remove SACCO logo storage.", ex);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect SACCO logo storage.", ex);
        }
    }

    public String publicLogoUrl(String saccoId, OffsetDateTime updatedAt) {
        if (saccoId == null || saccoId.isBlank() || !hasLogo(saccoId)) {
            return null;
        }
        StringBuilder url = new StringBuilder("/branding/saccos/")
            .append(UriUtils.encodePathSegment(saccoId, StandardCharsets.UTF_8))
            .append("/logo");
        if (updatedAt != null) {
            url.append("?v=").append(updatedAt.toInstant().toEpochMilli());
        }
        return url.toString();
    }

    public LogoResource load(String saccoId) {
        Path logoPath = findLogoPath(requireSaccoId(saccoId))
            .orElseThrow(() -> new IllegalArgumentException("Stored logo not found"));
        try {
            return new LogoResource(Files.readAllBytes(logoPath), mediaTypeFor(logoPath));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Stored logo not found", ex);
        }
    }

    private String resolveAllowedExtension(MultipartFile logoFile) {
        String originalName = StringUtils.cleanPath(logoFile.getOriginalFilename() == null ? "logo" : logoFile.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalName);
        String normalized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(normalized)) {
            throw new IllegalStateException("Upload the SACCO logo as a PNG or JPEG image.");
        }
        return "jpg".equals(normalized) ? "jpeg" : normalized;
    }

    private void validateSize(MultipartFile logoFile, LogoUploadPolicy policy) {
        if (logoFile.getSize() > policy.maxFileSizeBytes()) {
            throw new IllegalStateException("SACCO logo image must be " + policy.maxFileSizeLabel() + " or smaller.");
        }
    }

    private byte[] readBytes(MultipartFile logoFile) {
        try {
            return logoFile.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read the SACCO logo image.", ex);
        }
    }

    private void validateImageDimensions(byte[] bytes, LogoUploadPolicy policy) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalStateException("Upload a valid PNG or JPEG image for the SACCO logo.");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width < policy.minWidthPx()
                || height < policy.minHeightPx()
                || width > policy.maxWidthPx()
                || height > policy.maxHeightPx()) {
                throw new IllegalStateException("SACCO logo image must be between " + policy.dimensionsLabel() + ".");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect the SACCO logo image.", ex);
        }
    }

    private String requireSaccoId(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            throw new IllegalStateException("SACCO ID is required for logo storage.");
        }
        return saccoId.trim();
    }

    private Path imagesDirectory(String saccoId) {
        return saccoDirectory(saccoId).resolve(IMAGES_DIRECTORY).normalize();
    }

    private Path saccoDirectory(String saccoId) {
        return saccoFilesRoot.resolve(safePathSegment(saccoId)).normalize();
    }

    private String safePathSegment(String saccoId) {
        String safe = saccoId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) {
            throw new IllegalStateException("SACCO ID is required for logo storage.");
        }
        return safe;
    }

    private Optional<Path> findLogoPath(String saccoId) {
        Path imagesDirectory = imagesDirectory(saccoId);
        return LOGO_FILE_NAMES.stream()
            .map(imagesDirectory::resolve)
            .filter(Files::isRegularFile)
            .findFirst();
    }

    private MediaType mediaTypeFor(Path path) {
        String extension = StringUtils.getFilenameExtension(path.getFileName().toString());
        return "png".equalsIgnoreCase(extension) ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    }

    private void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare SACCO logo storage.", ex);
        }
    }

    private void writeLogo(Path target, byte[] bytes) {
        createDirectories(target.getParent());
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(target.getParent(), "logo-", ".tmp");
            Files.write(tempFile, bytes);
            try {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save the SACCO logo image.", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Temporary write files are best-effort cleanup only.
                }
            }
        }
    }

    private void deleteLogoFiles(Path imagesDirectory) {
        if (!Files.isDirectory(imagesDirectory)) {
            return;
        }
        for (String fileName : LOGO_FILE_NAMES) {
            try {
                Files.deleteIfExists(imagesDirectory.resolve(fileName));
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to remove the SACCO logo image.", ex);
            }
        }
    }

    private void deleteStaleLogoFiles(Path imagesDirectory, Path retainedLogo) {
        if (!Files.isDirectory(imagesDirectory)) {
            return;
        }
        try (Stream<Path> files = Files.list(imagesDirectory)) {
            files.filter(Files::isRegularFile)
                .filter(path -> LOGO_FILE_NAMES.contains(path.getFileName().toString()))
                .filter(path -> !path.normalize().equals(retainedLogo.normalize()))
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new IllegalStateException("Failed to remove the old SACCO logo image.", ex);
                    }
                });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect SACCO logo storage.", ex);
        }
    }

    public record LogoResource(byte[] content, MediaType contentType) {
    }
}
