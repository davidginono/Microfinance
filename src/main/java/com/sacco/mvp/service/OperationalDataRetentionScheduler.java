package com.sacco.mvp.service;

import com.sacco.mvp.repository.AuditLogRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.retention.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class OperationalDataRetentionScheduler {
    private final AuditLogRepository auditLogRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Value("${app.retention.audit-log-days:70}")
    private long auditLogRetentionDays;

    @Value("${app.retention.outbox-days:70}")
    private long outboxRetentionDays;

    @Scheduled(cron = "${app.retention.cleanup-cron:0 30 2 * * *}")
    @Transactional
    public void purgeExpiredOperationalData() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime auditCutoff = now.minusDays(Math.max(auditLogRetentionDays, 1));
        OffsetDateTime outboxCutoff = now.minusDays(Math.max(outboxRetentionDays, 1));

        long deletedAuditLogs = auditLogRepository.deleteByCreatedAtBefore(auditCutoff);
        long deletedOutboxEvents = outboxEventRepository.deleteByCreatedAtBefore(outboxCutoff);

        if (deletedAuditLogs > 0 || deletedOutboxEvents > 0) {
            log.info(
                "Operational data retention cleanup removed {} audit logs older than {} and {} outbox events older than {}",
                deletedAuditLogs,
                auditCutoff,
                deletedOutboxEvents,
                outboxCutoff
            );
        } else {
            log.debug("Operational data retention cleanup found no expired audit or outbox rows to remove.");
        }
    }
}
