package com.sacco.mvp.service;

import com.sacco.mvp.domain.StoredUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberProfileImageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final long MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L;
    private static final int TARGET_WIDTH = 600;
    private static final int TARGET_HEIGHT = 600;
    private static final int MIN_WIDTH = 240;
    private static final int MIN_HEIGHT = 240;
    private static final int MAX_WIDTH = 3000;
    private static final int MAX_HEIGHT = 3000;
    private static final long MAX_PIXELS = 9_000_000L;

    private final StoredUploadStorageService storedUploadStorageService;

    public void store(UUID memberId, MultipartFile imageFile) {
        if (memberId == null) {
            throw new IllegalStateException("Member account is required before uploading a profile image.");
        }
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalStateException("Choose a profile photo to upload.");
        }

        resolveAllowedExtension(imageFile);
        if (imageFile.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalStateException("Profile image must be 2 MB or smaller.");
        }

        byte[] normalized = normalizeToProfileJpeg(readBytes(imageFile));
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

    private byte[] normalizeToProfileJpeg(byte[] source) {
        try {
            ImageDimensions dimensions = inspectDimensions(source);
            validateDimensions(dimensions.width(), dimensions.height());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw new IllegalStateException("Upload a valid PNG or JPEG profile image.");
            }

            BufferedImage resized = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, TARGET_WIDTH, TARGET_HEIGHT);
                double scale = Math.max((double) TARGET_WIDTH / image.getWidth(), (double) TARGET_HEIGHT / image.getHeight());
                int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
                int x = (TARGET_WIDTH - drawWidth) / 2;
                int y = (TARGET_HEIGHT - drawHeight) / 2;
                graphics.drawImage(image, x, y, drawWidth, drawHeight, null);
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

    private ImageDimensions inspectDimensions(byte[] source) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) {
                throw new IllegalStateException("Upload a valid PNG or JPEG profile image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalStateException("Upload a valid PNG or JPEG profile image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private void validateDimensions(int width, int height) {
        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            throw new IllegalStateException("Profile image must be at least 240x240 pixels.");
        }
        if (width > MAX_WIDTH || height > MAX_HEIGHT || (long) width * (long) height > MAX_PIXELS) {
            throw new IllegalStateException("Profile image dimensions are too large. Upload an image up to 3000x3000 pixels.");
        }
    }

    private record ImageDimensions(int width, int height) {
    }

    public record ProfileImageResource(byte[] content, MediaType contentType) {
    }
}
