package com.sacco.mvp.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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

    /**
     * Legacy events remain publishable for in-app/email delivery, but their
     * missing station scope intentionally prevents SMS unit consumption.
     */
    public void enqueue(String aggregateType, UUID aggregateId, String eventType, UUID recipientId,
                        Map<String, Object> details) {
        enqueue(aggregateType, aggregateId, eventType, recipientId, null, null, null, details);
    }

    public void enqueue(String aggregateType, UUID aggregateId, String eventType, UUID recipientId,
                        String saccoId, String stationId,
                        Map<String, Object> details) {
        enqueue(aggregateType, aggregateId, eventType, recipientId, null, saccoId, stationId, details);
    }

    public void enqueue(String aggregateType, UUID aggregateId, String eventType, UUID recipientId,
                        UUID actorId, String saccoId, String stationId,
                        Map<String, Object> details) {
        if (isDuplicate(aggregateType, aggregateId, eventType, recipientId)) {
            log.info("Skipping duplicate outbox event: aggregateType={}, aggregateId={}, eventType={}, recipientId={}",
                aggregateType, aggregateId, eventType, recipientId);
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientId", recipientId);
        payload.put("actorId", actorId);
        payload.put("eventType", eventType);
        payload.put("saccoId", saccoId);
        payload.put("stationId", stationId);
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
        } catch (JacksonException e) {
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

    private boolean isDuplicate(String aggregateType, UUID aggregateId, String eventType, UUID recipientId) {
        if (aggregateType == null || aggregateId == null || eventType == null || recipientId == null) {
            return false;
        }
        try {
            return outboxEventRepository.existsActiveDuplicate(aggregateType, aggregateId, eventType, recipientId);
        } catch (Exception ex) {
            log.warn("Outbox duplicate check failed for {} on {}: {}", eventType, aggregateId, ex.getMessage());
            return false;
        }
    }
}
