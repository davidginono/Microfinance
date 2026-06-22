package com.sacco.mvp.service;

import com.sacco.mvp.domain.StoredUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberProfileImageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L;
    private static final int TARGET_WIDTH = 600;
    private static final int TARGET_HEIGHT = 750;
    private static final int MIN_WIDTH = 240;
    private static final int MIN_HEIGHT = 300;

    private final StoredUploadStorageService storedUploadStorageService;

    public void store(UUID memberId, MultipartFile imageFile) {
        if (memberId == null) {
            throw new IllegalStateException("Member account is required before uploading a profile image.");
        }
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalStateException("Choose a passport photo to upload.");
        }

        resolveAllowedExtension(imageFile);
        if (imageFile.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalStateException("Profile image must be 2 MB or smaller.");
        }

        byte[] normalized = normalizeToPassportJpeg(readBytes(imageFile));
        storedUploadStorageService.replaceCategory(
            StoredUploadStorageService.OWNER_MEMBER,
            memberId.toString(),
            StoredUploadStorageService.CATEGORY_MEMBER_PROFILE_PHOTO,
            StringUtils.cleanPath(imageFile.getOriginalFilename() == null ? "profile-photo.jpg" : imageFile.getOriginalFilename()),
            MediaType.IMAGE_JPEG_VALUE,
            normalized
        );
    }

    public ProfileImageResource load(UUID memberId) {
        StoredUpload upload = storedUploadStorageService.loadLatest(
            StoredUploadStorageService.OWNER_MEMBER,
            memberId.toString(),
            StoredUploadStorageService.CATEGORY_MEMBER_PROFILE_PHOTO
        );
        return new ProfileImageResource(upload.getContent(), MediaType.parseMediaType(upload.getContentType()));
    }

    private String resolveAllowedExtension(MultipartFile imageFile) {
        String originalName = StringUtils.cleanPath(imageFile.getOriginalFilename() == null ? "profile-photo" : imageFile.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalName);
        String normalized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(normalized)) {
            throw new IllegalStateException("Upload the profile image as a PNG or JPEG file.");
        }
        return "jpg".equals(normalized) ? "jpeg" : normalized;
    }

    private byte[] readBytes(MultipartFile imageFile) {
        try {
            return imageFile.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read the profile image.", ex);
        }
    }

    private byte[] normalizeToPassportJpeg(byte[] source) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw new IllegalStateException("Upload a valid PNG or JPEG profile image.");
            }
            if (image.getWidth() < MIN_WIDTH || image.getHeight() < MIN_HEIGHT) {
                throw new IllegalStateException("Profile image must be at least 240x300 pixels.");
            }

            BufferedImage cropped = cropToAspectRatio(image, TARGET_WIDTH, TARGET_HEIGHT);
            BufferedImage resized = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(cropped, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect the profile image.", ex);
        }
    }

    private BufferedImage cropToAspectRatio(BufferedImage image, int targetWidth, int targetHeight) {
        double targetRatio = (double) targetWidth / (double) targetHeight;
        int cropWidth = image.getWidth();
        int cropHeight = image.getHeight();
        double sourceRatio = (double) cropWidth / (double) cropHeight;
        if (sourceRatio > targetRatio) {
            cropWidth = (int) Math.round(cropHeight * targetRatio);
        } else if (sourceRatio < targetRatio) {
            cropHeight = (int) Math.round(cropWidth / targetRatio);
        }
        int x = Math.max((image.getWidth() - cropWidth) / 2, 0);
        int y = Math.max((image.getHeight() - cropHeight) / 2, 0);
        return image.getSubimage(x, y, cropWidth, cropHeight);
    }

    public record ProfileImageResource(byte[] content, MediaType contentType) {
    }
}
