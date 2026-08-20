package com.sacco.mvp.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.StoredUpload;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanAttachmentService {
    public static final String CATEGORY_APPLICATION_ATTACHMENT = "APPLICATION_ATTACHMENT";
    public static final String CATEGORY_DISBURSEMENT_PROOF = "DISBURSEMENT_PROOF";
    private static final long MAX_ATTACHMENT_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");

    private final ObjectMapper objectMapper;
    private final AdminAlertService adminAlertService;
    private final StoredUploadStorageService storedUploadStorageService;

    public String store(UUID loanId, List<MultipartFile> files, String existingJson) {
        return store(loanId, files, existingJson, CATEGORY_APPLICATION_ATTACHMENT);
    }

    public String store(UUID loanId, List<MultipartFile> files, String existingJson, String attachmentCategory) {
        List<Map<String, Object>> attachments = parse(existingJson);
        if (files == null || files.isEmpty()) {
            return writeJson(attachments);
        }

        String normalizedCategory = normalizeCategory(attachmentCategory);
        try {
            for (MultipartFile file : files) {
                storeOne(loanId, attachments, file, normalizedCategory, null, null);
            }
        } catch (IOException e) {
            adminAlertService.alertAllAdmins(
                "Attachment Storage",
                "Attachment upload failed",
                "One or more loan attachments could not be stored.",
                Map.of("loanId", loanId.toString(), "error", e.getMessage() == null ? "Attachment storage error" : e.getMessage())
            );
            throw new IllegalArgumentException("Failed to store attachments", e);
        }
        return writeJson(attachments);
    }

    public String storeRequired(UUID loanId, List<RequiredAttachmentUpload> uploads, String existingJson) {
        List<Map<String, Object>> attachments = parse(existingJson);
        if (uploads == null || uploads.isEmpty()) {
            return writeJson(attachments);
        }
        try {
            for (RequiredAttachmentUpload upload : uploads) {
                if (upload == null || upload.files() == null) {
                    continue;
                }
                for (MultipartFile file : upload.files()) {
                    storeOne(
                        loanId,
                        attachments,
                        file,
                        CATEGORY_APPLICATION_ATTACHMENT,
                        upload.requiredAttachmentId(),
                        upload.requiredAttachmentName()
                    );
                }
            }
        } catch (IOException e) {
            adminAlertService.alertAllAdmins(
                "Attachment Storage",
                "Attachment upload failed",
                "One or more required loan attachments could not be stored.",
                Map.of("loanId", loanId.toString(), "error", e.getMessage() == null ? "Attachment storage error" : e.getMessage())
            );
            throw new IllegalArgumentException("Failed to store attachments", e);
        }
        return writeJson(attachments);
    }

    private void storeOne(UUID loanId,
                          List<Map<String, Object>> attachments,
                          MultipartFile file,
                          String attachmentCategory,
                          UUID requiredAttachmentId,
                          String requiredAttachmentName) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }
        String attachmentId = UUID.randomUUID().toString();
        String cleanedName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(cleanedName);
        byte[] content = file.getBytes();
        String contentType = resolveAllowedContentType(cleanedName, content);
        String storedName = extension == null || extension.isBlank()
            ? attachmentId
            : attachmentId + "." + extension;
        storedUploadStorageService.store(
            UUID.fromString(attachmentId),
            StoredUploadStorageService.OWNER_LOAN_APPLICATION,
            loanId.toString(),
            attachmentCategory,
            cleanedName,
            contentType,
            content
        );

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", attachmentId);
        item.put("originalName", cleanedName);
        item.put("storedName", storedName);
        item.put("contentType", contentType);
        item.put("size", content.length);
        item.put("uploadedAt", OffsetDateTime.now().toString());
        item.put("attachmentCategory", attachmentCategory);
        if (requiredAttachmentId != null) {
            item.put("requiredAttachmentId", requiredAttachmentId.toString());
        }
        if (requiredAttachmentName != null && !requiredAttachmentName.isBlank()) {
            item.put("requiredAttachmentName", requiredAttachmentName.trim());
        }
        attachments.add(item);
    }

    private String resolveAllowedContentType(String originalName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Upload a valid PDF, PNG, or JPEG attachment.");
        }
        if (content.length > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("Each attachment must be 25 MB or smaller.");
        }
        String extension = StringUtils.getFilenameExtension(originalName == null ? "" : originalName);
        String normalizedExtension = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(normalizedExtension)) {
            throw new IllegalArgumentException("Upload attachments as PDF, PNG, or JPEG files.");
        }
        String detected = detectedContentType(content);
        if (detected == null) {
            throw new IllegalArgumentException("Upload a valid PDF, PNG, or JPEG attachment.");
        }
        if ("application/pdf".equals(detected) && !"pdf".equals(normalizedExtension)) {
            throw new IllegalArgumentException("PDF attachments must use the .pdf file extension.");
        }
        if ("image/png".equals(detected) && !"png".equals(normalizedExtension)) {
            throw new IllegalArgumentException("PNG attachments must use the .png file extension.");
        }
        if ("image/jpeg".equals(detected) && !"jpg".equals(normalizedExtension) && !"jpeg".equals(normalizedExtension)) {
            throw new IllegalArgumentException("JPEG attachments must use the .jpg or .jpeg file extension.");
        }
        return detected;
    }

    private String detectedContentType(byte[] content) {
        if (content.length >= 5
            && content[0] == 0x25
            && content[1] == 0x50
            && content[2] == 0x44
            && content[3] == 0x46
            && content[4] == 0x2D) {
            return "application/pdf";
        }
        if (content.length >= 8
            && (content[0] & 0xFF) == 0x89
            && content[1] == 0x50
            && content[2] == 0x4E
            && content[3] == 0x47
            && content[4] == 0x0D
            && content[5] == 0x0A
            && content[6] == 0x1A
            && content[7] == 0x0A) {
            return "image/png";
        }
        if (content.length >= 3
            && (content[0] & 0xFF) == 0xFF
            && (content[1] & 0xFF) == 0xD8
            && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    private String normalizeCategory(String attachmentCategory) {
        if (attachmentCategory == null || attachmentCategory.isBlank()) {
            return CATEGORY_APPLICATION_ATTACHMENT;
        }
        return attachmentCategory.trim();
    }

    public List<Map<String, Object>> parse(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public AttachmentResource load(UUID loanId, String attachmentId, String attachmentsJson) {
        Map<String, Object> match = parse(attachmentsJson).stream()
            .filter(item -> attachmentId.equals(String.valueOf(item.get("id"))))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));

        StoredUpload upload = storedUploadStorageService.load(
            UUID.fromString(attachmentId),
            StoredUploadStorageService.OWNER_LOAN_APPLICATION,
            loanId.toString()
        );
        return new AttachmentResource(
            upload.getContent(),
            String.valueOf(match.get("originalName")),
            upload.getContentType()
        );
    }

    public void deleteAll(UUID loanId) {
        storedUploadStorageService.deleteOwner(StoredUploadStorageService.OWNER_LOAN_APPLICATION, loanId.toString());
    }

    private String writeJson(List<Map<String, Object>> attachments) {
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to save attachment metadata", e);
        }
    }

    @Getter
    public static class AttachmentResource {
        private final byte[] content;
        private final String originalName;
        private final String contentType;

        public AttachmentResource(byte[] content, String originalName, String contentType) {
            this.content = content;
            this.originalName = originalName;
            this.contentType = contentType;
        }
    }

    public record RequiredAttachmentUpload(UUID requiredAttachmentId,
                                           String requiredAttachmentName,
                                           List<MultipartFile> files) {
    }
}
