package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.AuditLog;
import com.sacco.mvp.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(String entityType, UUID entityId, String action, UUID actorMemberId, Object beforeState, Object afterState) {
        auditLogRepository.save(AuditLog.builder()
            .id(UUID.randomUUID())
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .actorMemberId(actorMemberId)
            .beforeState(toJson(beforeState))
            .afterState(toJson(afterState))
            .createdAt(OffsetDateTime.now())
            .build());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
