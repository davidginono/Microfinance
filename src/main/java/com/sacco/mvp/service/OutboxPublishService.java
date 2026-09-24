package com.sacco.mvp.service;

import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import com.sacco.mvp.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxPublishService {
    static final Duration STALE_CLAIM = Duration.ofMinutes(10);

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public List<OutboxEvent> claimBatch(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        outboxEventRepository.reclaimStaleProcessing(now.minus(STALE_CLAIM));
        List<OutboxEvent> events = outboxEventRepository.findNextPublishBatch(Math.max(1, limit));
        if (events.isEmpty()) {
            return List.of();
        }
        for (OutboxEvent event : events) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setPublishedAt(now);
        }
        return outboxEventRepository.saveAll(events);
    }

    @Transactional
    public void markPublished(OutboxEvent event) {
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(OffsetDateTime.now());
        outboxEventRepository.save(event);
    }

    @Transactional
    public void markFailed(OutboxEvent event) {
        event.setStatus(OutboxStatus.FAILED);
        outboxEventRepository.save(event);
    }
}
