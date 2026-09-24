package com.sacco.mvp.repository;

import com.sacco.mvp.domain.AppUsageEvent;
import com.sacco.mvp.domain.AppUsageEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AppUsageEventRepository extends JpaRepository<AppUsageEvent, UUID> {
    interface CountRow {
        String getLabel();
        long getTotal();
    }

    interface TopPageRow {
        String getLabel();
        String getSaccoId();
        long getTotal();
    }

    @Query(
        value = """
            select count(*)
            from app_usage_events
            where event_type = cast(:eventType as text)
              and occurred_at >= :from
              and occurred_at < :to
              and (cast(:saccoId as text) is null or sacco_id = cast(:saccoId as text))
              and (cast(:stationId as text) is null or station_id = cast(:stationId as text))
            """,
        nativeQuery = true
    )
    long countScoped(@Param("eventType") String eventType,
                     @Param("from") OffsetDateTime from,
                     @Param("to") OffsetDateTime to,
                     @Param("saccoId") String saccoId,
                     @Param("stationId") String stationId);

    default long countScoped(AppUsageEventType eventType,
                             OffsetDateTime from,
                             OffsetDateTime to,
                             String saccoId,
                             String stationId) {
        return countScoped(eventType.name(), from, to, saccoId, stationId);
    }

    @Query(
        value = """
            select to_char(occurred_at at time zone cast(:zoneId as text), cast(:pattern as text)) as label,
                   count(*) as total
            from app_usage_events
            where event_type = cast(:eventType as text)
              and occurred_at >= :from
              and occurred_at < :to
              and (cast(:saccoId as text) is null or sacco_id = cast(:saccoId as text))
              and (cast(:stationId as text) is null or station_id = cast(:stationId as text))
            group by label
            order by min(occurred_at)
            """,
        nativeQuery = true
    )
    List<CountRow> countEventsByBucket(@Param("eventType") String eventType,
                                       @Param("from") OffsetDateTime from,
                                       @Param("to") OffsetDateTime to,
                                       @Param("saccoId") String saccoId,
                                       @Param("stationId") String stationId,
                                       @Param("pattern") String pattern,
                                       @Param("zoneId") String zoneId);

    @Query(
        value = """
            select coalesce(nullif(page_path, ''), '/') as label,
                   coalesce(nullif(sacco_id, ''), 'Platform') as saccoId,
                   count(*) as total
            from app_usage_events
            where event_type = 'PAGE_VIEW'
              and occurred_at >= :from
              and occurred_at < :to
              and (cast(:saccoId as text) is null or sacco_id = cast(:saccoId as text))
              and (cast(:stationId as text) is null or station_id = cast(:stationId as text))
            group by label, saccoId
            order by total desc, label asc, saccoId asc
            limit 8
            """,
        nativeQuery = true
    )
    List<TopPageRow> topPages(@Param("from") OffsetDateTime from,
                              @Param("to") OffsetDateTime to,
                              @Param("saccoId") String saccoId,
                              @Param("stationId") String stationId);

    @Query(
        value = """
            select coalesce(nullif(device_type, ''), 'Other') as label,
                   count(*) as total
            from app_usage_events
            where event_type = 'PAGE_VIEW'
              and occurred_at >= :from
              and occurred_at < :to
              and (cast(:saccoId as text) is null or sacco_id = cast(:saccoId as text))
              and (cast(:stationId as text) is null or station_id = cast(:stationId as text))
            group by label
            order by total desc, label asc
            """,
        nativeQuery = true
    )
    List<CountRow> deviceBreakdown(@Param("from") OffsetDateTime from,
                                   @Param("to") OffsetDateTime to,
                                   @Param("saccoId") String saccoId,
                                   @Param("stationId") String stationId);

    @Query(
        value = """
            select coalesce(nullif(browser_family, ''), 'Other') as label,
                   count(*) as total
            from app_usage_events
            where event_type = 'PAGE_VIEW'
              and occurred_at >= :from
              and occurred_at < :to
              and (cast(:saccoId as text) is null or sacco_id = cast(:saccoId as text))
              and (cast(:stationId as text) is null or station_id = cast(:stationId as text))
            group by label
            order by total desc, label asc
            """,
        nativeQuery = true
    )
    List<CountRow> browserBreakdown(@Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to,
                                    @Param("saccoId") String saccoId,
                                    @Param("stationId") String stationId);

    @Modifying
    long deleteByOccurredAtBefore(OffsetDateTime cutoff);
}
