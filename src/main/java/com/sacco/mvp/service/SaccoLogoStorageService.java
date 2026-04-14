package com.sacco.mvp.service;

import jakarta.annotation.PostConstruct;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class SaccoLogoStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final long MAX_FILE_SIZE_BYTES = 1_048_576L;
    private static final int MIN_WIDTH = 64;
    private static final int MIN_HEIGHT = 64;
    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;

    private final Path rootPath;

    public SaccoLogoStorageService() {
        this(Paths.get("branding", "sacco-logos"));
    }

    SaccoLogoStorageService(Path rootPath) {
        this.rootPath = rootPath;
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(rootPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize SACCO logo storage.", ex);
        }
    }

    public void store(String saccoId, MultipartFile logoFile) {
        if (logoFile == null || logoFile.isEmpty()) {
            return;
        }

        String extension = resolveAllowedExtension(logoFile);
        validateSize(logoFile);
        byte[] bytes = readBytes(logoFile);
        validateImageDimensions(bytes);

        Path saccoFolder = rootPath.resolve(safeFolderName(saccoId));
        Path target = saccoFolder.resolve("logo." + extension);
        try {
            Files.createDirectories(saccoFolder);
            deleteExistingFiles(saccoFolder);
            Files.copy(new ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store the SACCO logo image.", ex);
        }
    }

    public boolean hasLogo(String saccoId) {
        return resolveLogoPath(saccoId).isPresent();
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
        Path logoPath = resolveLogoPath(saccoId)
            .orElseThrow(() -> new IllegalArgumentException("SACCO logo not found."));
        try {
            return new LogoResource(Files.readAllBytes(logoPath), contentTypeFor(logoPath));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load the SACCO logo image.", ex);
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

    private void validateSize(MultipartFile logoFile) {
        if (logoFile.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalStateException("SACCO logo image must be 1 MB or smaller.");
        }
    }

    private byte[] readBytes(MultipartFile logoFile) {
        try {
            return logoFile.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read the SACCO logo image.", ex);
        }
    }

    private void validateImageDimensions(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalStateException("Upload a valid PNG or JPEG image for the SACCO logo.");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width < MIN_WIDTH || height < MIN_HEIGHT || width > MAX_WIDTH || height > MAX_HEIGHT) {
                throw new IllegalStateException("SACCO logo image must be between 64x64 and 1024x1024 pixels.");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect the SACCO logo image.", ex);
        }
    }

    private void deleteExistingFiles(Path saccoFolder) throws IOException {
        if (!Files.exists(saccoFolder)) {
            return;
        }
        try (Stream<Path> files = Files.list(saccoFolder)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    private Optional<Path> resolveLogoPath(String saccoId) {
        Path saccoFolder = rootPath.resolve(safeFolderName(saccoId));
        if (!Files.isDirectory(saccoFolder)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(saccoFolder)) {
            return files
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String extension = StringUtils.getFilenameExtension(path.getFileName().toString());
                    return extension != null && ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
                })
                .findFirst();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read the SACCO logo storage.", ex);
        }
    }

    private String safeFolderName(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            throw new IllegalStateException("SACCO ID is required for logo storage.");
        }
        return saccoId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private MediaType contentTypeFor(Path logoPath) {
        String extension = StringUtils.getFilenameExtension(logoPath.getFileName().toString());
        if ("png".equalsIgnoreCase(extension)) {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.IMAGE_JPEG;
    }

    public record LogoResource(byte[] content, MediaType contentType) {
    }
}
