package com.sacco.mvp.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@RequiredArgsConstructor
public class DatabaseUtilizationService {
    private static final int MAX_POINTS = 24;
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final SchedulerLockService schedulerLockService;
    private final Deque<DatabaseUtilizationPoint> history = new ConcurrentLinkedDeque<>();

    @PostConstruct
    public void init() {
        sampleNow();
    }

    @Scheduled(fixedDelay = 15000)
    public void sampleNow() {
        schedulerLockService.runExclusive(SchedulerLockService.DATABASE_UTILIZATION, this::sampleLocked);
    }

    private void sampleLocked() {
        try {
            Double sizeMbRaw = jdbcTemplate.queryForObject(
                "select pg_database_size(current_database()) / 1024.0 / 1024.0",
                Double.class
            );
            Integer activeConnections = jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database()",
                Integer.class
            );
            Integer maxConnections = jdbcTemplate.queryForObject(
                "select setting::int from pg_settings where name = 'max_connections'",
                Integer.class
            );

            double sizeMb = round(sizeMbRaw == null ? 0.0 : sizeMbRaw);
            int active = activeConnections == null ? 0 : activeConnections;
            int max = maxConnections == null || maxConnections <= 0 ? 1 : maxConnections;
            double utilizationPercent = round((active * 100.0) / max);

            history.addLast(new DatabaseUtilizationPoint(
                OffsetDateTime.now(),
                sizeMb,
                active,
                max,
                utilizationPercent
            ));
            while (history.size() > MAX_POINTS) {
                history.pollFirst();
            }
        } catch (Exception ignored) {
            // Keep the dashboard resilient if a metrics query fails.
        }
    }

    public DatabaseUtilizationPayload snapshot() {
        if (history.isEmpty()) {
            sampleNow();
        }

        List<DatabaseUtilizationPointView> points = new ArrayList<>();
        for (DatabaseUtilizationPoint point : history) {
            points.add(new DatabaseUtilizationPointView(
                point.capturedAt().atZoneSameInstant(ZoneId.systemDefault()).format(LABEL_FORMAT),
                point.sizeMb(),
                point.activeConnections(),
                point.maxConnections(),
                point.utilizationPercent()
            ));
        }

        DatabaseUtilizationPoint latest = history.peekLast();
        DatabaseUtilizationSummary summary = latest == null
            ? new DatabaseUtilizationSummary("-", 0.0, 0, 0, 0.0)
            : new DatabaseUtilizationSummary(
                latest.capturedAt().atZoneSameInstant(ZoneId.systemDefault()).format(LABEL_FORMAT),
                latest.sizeMb(),
                latest.activeConnections(),
                latest.maxConnections(),
                latest.utilizationPercent()
            );

        return new DatabaseUtilizationPayload(summary, points);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private record DatabaseUtilizationPoint(
        OffsetDateTime capturedAt,
        double sizeMb,
        int activeConnections,
        int maxConnections,
        double utilizationPercent
    ) {
    }

    public record DatabaseUtilizationPayload(
        DatabaseUtilizationSummary latest,
        List<DatabaseUtilizationPointView> history
    ) {
    }

    public record DatabaseUtilizationSummary(
        String capturedAtLabel,
        double sizeMb,
        int activeConnections,
        int maxConnections,
        double utilizationPercent
    ) {
    }

    public record DatabaseUtilizationPointView(
        String label,
        double sizeMb,
        int activeConnections,
        int maxConnections,
        double utilizationPercent
    ) {
    }
}
