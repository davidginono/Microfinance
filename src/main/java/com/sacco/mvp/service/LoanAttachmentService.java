package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanAttachmentService {
    private final ObjectMapper objectMapper;
    private final AdminAlertService adminAlertService;
    private final Path rootPath = Paths.get("loan-uploads", "applications");

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            adminAlertService.alertAllAdmins(
                "Attachment Storage",
                "Attachment storage initialization failed",
                "The attachment storage directory could not be initialized.",
                Map.of("path", rootPath.toString(), "error", e.getMessage() == null ? "Initialization error" : e.getMessage())
            );
            throw new IllegalStateException("Failed to initialize attachment storage", e);
        }
    }

    public String store(UUID loanId, List<MultipartFile> files, String existingJson) {
        List<Map<String, Object>> attachments = parse(existingJson);
        if (files == null || files.isEmpty()) {
            return writeJson(attachments);
        }

        Path loanFolder = rootPath.resolve(loanId.toString());
        try {
            Files.createDirectories(loanFolder);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String attachmentId = UUID.randomUUID().toString();
                String cleanedName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
                String extension = StringUtils.getFilenameExtension(cleanedName);
                String storedName = extension == null || extension.isBlank()
                    ? attachmentId
                    : attachmentId + "." + extension;
                Path target = loanFolder.resolve(storedName);
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", attachmentId);
                item.put("originalName", cleanedName);
                item.put("storedName", storedName);
                item.put("contentType", file.getContentType() == null ? "application/octet-stream" : file.getContentType());
                item.put("size", file.getSize());
                item.put("uploadedAt", OffsetDateTime.now().toString());
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

        String storedName = String.valueOf(match.get("storedName"));
        Path filePath = rootPath.resolve(loanId.toString()).resolve(storedName);
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("Attachment file is missing");
        }
        return new AttachmentResource(
            filePath,
            String.valueOf(match.get("originalName")),
            String.valueOf(match.getOrDefault("contentType", "application/octet-stream"))
        );
    }

    public void deleteAll(UUID loanId) {
        Path loanFolder = rootPath.resolve(loanId.toString());
        if (!Files.exists(loanFolder)) {
            return;
        }
        try (var stream = Files.walk(loanFolder)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    adminAlertService.alertAllAdmins(
                        "Attachment Storage",
                        "Attachment cleanup failed",
                        "Attachment files could not be deleted during cleanup.",
                        Map.of("loanId", loanId.toString(), "path", path.toString(), "error", e.getMessage() == null ? "Delete error" : e.getMessage())
                    );
                    throw new IllegalStateException("Failed to delete attachment files", e);
                }
            });
        } catch (IOException e) {
            adminAlertService.alertAllAdmins(
                "Attachment Storage",
                "Attachment directory cleanup failed",
                "The application attachment directory could not be removed.",
                Map.of("loanId", loanId.toString(), "error", e.getMessage() == null ? "Directory delete error" : e.getMessage())
            );
            throw new IllegalStateException("Failed to remove attachment directory", e);
        }
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
        private final Path path;
        private final String originalName;
        private final String contentType;

        public AttachmentResource(Path path, String originalName, String contentType) {
            this.path = path;
            this.originalName = originalName;
            this.contentType = contentType;
        }
    }
}
