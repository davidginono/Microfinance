package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final AdminAlertService adminAlertService;

    public void enqueue(String aggregateType, UUID aggregateId, String eventType, UUID recipientId,
                        Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientId", recipientId);
        payload.put("eventType", eventType);
        payload.put("details", details);

        try {
            outboxEventRepository.save(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(objectMapper.writeValueAsString(payload))
                .status(OutboxStatus.NEW)
                .createdAt(OffsetDateTime.now())
                .build());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to write outbox payload", e);
        } catch (Exception e) {
            log.warn("Outbox enqueue skipped for {} due to persistence issue: {}", eventType, e.getMessage());
            adminAlertService.alertAllAdmins(
                "Outbox",
                "Outbox enqueue skipped",
                "An outbox event could not be saved for event type " + eventType + ".",
                Map.of(
                    "eventType", eventType,
                    "aggregateType", aggregateType,
                    "aggregateId", aggregateId == null ? "" : aggregateId.toString(),
                    "error", e.getMessage() == null ? "Unknown persistence error" : e.getMessage()
                )
            );
        }
    }
}
