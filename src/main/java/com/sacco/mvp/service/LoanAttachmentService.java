package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.StoredUpload;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanAttachmentService {
    public static final String CATEGORY_APPLICATION_ATTACHMENT = "APPLICATION_ATTACHMENT";
    public static final String CATEGORY_DISBURSEMENT_PROOF = "DISBURSEMENT_PROOF";

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
        String storedName = extension == null || extension.isBlank()
            ? attachmentId
            : attachmentId + "." + extension;
        storedUploadStorageService.store(
            UUID.fromString(attachmentId),
            StoredUploadStorageService.OWNER_LOAN_APPLICATION,
            loanId.toString(),
            attachmentCategory,
            cleanedName,
            file.getContentType(),
            file.getBytes()
        );

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", attachmentId);
        item.put("originalName", cleanedName);
        item.put("storedName", storedName);
        item.put("contentType", file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        item.put("size", file.getSize());
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
        } catch (JsonProcessingException e) {
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
