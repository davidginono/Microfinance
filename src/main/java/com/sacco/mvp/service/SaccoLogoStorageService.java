package com.sacco.mvp.service;

import com.sacco.mvp.domain.StoredUpload;
import lombok.RequiredArgsConstructor;
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
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SaccoLogoStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final long MAX_FILE_SIZE_BYTES = 1_048_576L;
    private static final int MIN_WIDTH = 64;
    private static final int MIN_HEIGHT = 64;
    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;

    private final StoredUploadStorageService storedUploadStorageService;

    public void store(String saccoId, MultipartFile logoFile) {
        if (logoFile == null || logoFile.isEmpty()) {
            return;
        }

        resolveAllowedExtension(logoFile);
        validateSize(logoFile);
        byte[] bytes = readBytes(logoFile);
        validateImageDimensions(bytes);
        storedUploadStorageService.replaceCategory(
            StoredUploadStorageService.OWNER_SACCO,
            requireSaccoId(saccoId),
            StoredUploadStorageService.CATEGORY_SACCO_LOGO,
            StringUtils.cleanPath(logoFile.getOriginalFilename() == null ? "logo" : logoFile.getOriginalFilename()),
            logoFile.getContentType(),
            bytes
        );
    }

    public boolean hasLogo(String saccoId) {
        return saccoId != null
            && !saccoId.isBlank()
            && storedUploadStorageService.exists(
                StoredUploadStorageService.OWNER_SACCO,
                saccoId.trim(),
                StoredUploadStorageService.CATEGORY_SACCO_LOGO
            );
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
        StoredUpload upload = storedUploadStorageService.loadLatest(
            StoredUploadStorageService.OWNER_SACCO,
            requireSaccoId(saccoId),
            StoredUploadStorageService.CATEGORY_SACCO_LOGO
        );
        return new LogoResource(upload.getContent(), MediaType.parseMediaType(upload.getContentType()));
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

    private String requireSaccoId(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            throw new IllegalStateException("SACCO ID is required for logo storage.");
        }
        return saccoId.trim();
    }

    public record LogoResource(byte[] content, MediaType contentType) {
    }
}
