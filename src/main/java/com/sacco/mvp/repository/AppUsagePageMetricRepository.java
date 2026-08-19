package com.sacco.mvp.repository;

import com.sacco.mvp.domain.AppUsagePageMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AppUsagePageMetricRepository extends JpaRepository<AppUsagePageMetric, UUID> {
    @Modifying
    @Query(
        value = """
            insert into app_usage_page_metrics (
                id,
                bucket_start,
                sacco_id,
                station_id,
                page_path,
                device_type,
                browser_family,
                hit_count,
                last_recorded_at
            )
            values (
                :id,
                :bucketStart,
                cast(:saccoId as text),
                cast(:stationId as text),
                cast(:pagePath as text),
                cast(:deviceType as text),
                cast(:browserFamily as text),
                1,
                :recordedAt
            )
            on conflict on constraint uk_app_usage_page_metrics_bucket_scope_page_device
            do update set
                hit_count = app_usage_page_metrics.hit_count + 1,
                last_recorded_at = excluded.last_recorded_at
            """,
        nativeQuery = true
    )
    int incrementPageView(@Param("id") UUID id,
                          @Param("bucketStart") OffsetDateTime bucketStart,
                          @Param("saccoId") String saccoId,
                          @Param("stationId") String stationId,
                          @Param("pagePath") String pagePath,
                          @Param("deviceType") String deviceType,
                          @Param("browserFamily") String browserFamily,
                          @Param("recordedAt") OffsetDateTime recordedAt);

    @Modifying
    @Query(
        value = """
            insert into app_usage_page_metrics (
                id,
                bucket_start,
                sacco_id,
                station_id,
                page_path,
                device_type,
                browser_family,
                hit_count,
                last_recorded_at
            )
            values (
                :id,
                :bucketStart,
                cast(:saccoId as text),
                cast(:stationId as text),
                cast(:pagePath as text),
                cast(:deviceType as text),
                cast(:browserFamily as text),
                :hitCount,
                :recordedAt
            )
            on conflict on constraint uk_app_usage_page_metrics_bucket_scope_page_device
            do update set
                hit_count = app_usage_page_metrics.hit_count + excluded.hit_count,
                last_recorded_at = excluded.last_recorded_at
            """,
        nativeQuery = true
    )
    int incrementPageViews(@Param("id") UUID id,
                           @Param("bucketStart") OffsetDateTime bucketStart,
                           @Param("saccoId") String saccoId,
                           @Param("stationId") String stationId,
                           @Param("pagePath") String pagePath,
                           @Param("deviceType") String deviceType,
                           @Param("browserFamily") String browserFamily,
                           @Param("hitCount") long hitCount,
                           @Param("recordedAt") OffsetDateTime recordedAt);

    @Query(
        value = """
            select coalesce(sum(hit_count), 0)
            from app_usage_page_metrics
            where bucket_start >= :from
              and bucket_start < :to
              and (cast(:saccoId as text) is null or sacco_id = cast(:saccoId as text))
              and (cast(:stationId as text) is null or station_id = cast(:stationId as text))
            """,
        nativeQuery = true
    )
    long countPageViews(@Param("from") OffsetDateTime from,
                        @Param("to") OffsetDateTime to,
                        @Param("saccoId") String saccoId,
                        @Param("stationId") String stationId);

    @Query(
        value = """
            select page_path as label,
                   sacco_id as saccoId,
                   coalesce(sum(hit_count), 0) as total
            from app_usage_page_metrics
            where bucket_start >= :from
              and bucket_start < :to
              and (cast(:saccoId as text) is null or sacco_id = cast(:saccoId as text))
              and (cast(:stationId as text) is null or station_id = cast(:stationId as text))
            group by page_path, sacco_id
            order by total desc, label asc, saccoId asc
            limit 8
            """,
        nativeQuery = true
    )
    List<AppUsageEventRepository.TopPageRow> topPages(@Param("from") OffsetDateTime from,
                                                      @Param("to") OffsetDateTime to,
                                                      @Param("saccoId") String saccoId,
                                                      @Param("stationId") String stationId);

    @Query(
        value = """
            select device_type as label,
                   coalesce(sum(hit_count), 0) as total
            from app_usage_page_metrics
            where bucket_start >= :from
              and bucket_start < :to
              and (cast(:saccoId as text) is null or sacco_id = cast(:saccoId as text))
              and (cast(:stationId as text) is null or station_id = cast(:stationId as text))
            group by device_type
            order by total desc, label asc
            """,
        nativeQuery = true
    )
    List<AppUsageEventRepository.CountRow> deviceBreakdown(@Param("from") OffsetDateTime from,
                                                           @Param("to") OffsetDateTime to,
                                                           @Param("saccoId") String saccoId,
                                                           @Param("stationId") String stationId);

    @Query(
        value = """
            select browser_family as label,
                   coalesce(sum(hit_count), 0) as total
            from app_usage_page_metrics
            where bucket_start >= :from
              and bucket_start < :to
              and (cast(:saccoId as text) is null or sacco_id = cast(:saccoId as text))
              and (cast(:stationId as text) is null or station_id = cast(:stationId as text))
            group by browser_family
            order by total desc, label asc
            """,
        nativeQuery = true
    )
    List<AppUsageEventRepository.CountRow> browserBreakdown(@Param("from") OffsetDateTime from,
                                                            @Param("to") OffsetDateTime to,
                                                            @Param("saccoId") String saccoId,
                                                            @Param("stationId") String stationId);

    @Modifying
    long deleteByBucketStartBefore(OffsetDateTime cutoff);
}
