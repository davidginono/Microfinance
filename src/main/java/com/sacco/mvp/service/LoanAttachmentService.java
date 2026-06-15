package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final StoredUploadService storedUploadService;

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
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String attachmentId = UUID.randomUUID().toString();
                String cleanedName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
                String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
                storedUploadService.store(
                    UUID.fromString(attachmentId),
                    StoredUploadService.OWNER_LOAN_APPLICATION,
                    loanId.toString(),
                    normalizedCategory,
                    cleanedName,
                    contentType,
                    file.getBytes()
                );

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", attachmentId);
                item.put("originalName", cleanedName);
                item.put("contentType", contentType);
                item.put("size", file.getSize());
                item.put("uploadedAt", OffsetDateTime.now().toString());
                item.put("attachmentCategory", normalizedCategory);
                attachments.add(item);
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

        StoredUploadService.StoredUploadResource resource = storedUploadService.load(
            UUID.fromString(attachmentId),
            StoredUploadService.OWNER_LOAN_APPLICATION,
            loanId.toString()
        );
        return new AttachmentResource(
            resource.content(),
            String.valueOf(match.getOrDefault("originalName", resource.originalName())),
            String.valueOf(match.getOrDefault("contentType", resource.contentType()))
        );
    }

    public void deleteAll(UUID loanId) {
        storedUploadService.deleteOwner(StoredUploadService.OWNER_LOAN_APPLICATION, loanId.toString());
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
}
