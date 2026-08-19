package com.sacco.mvp.repository;

import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>, JpaSpecificationExecutor<OutboxEvent> {
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Query(value = """
        select *
        from outbox_events
        where status = 'NEW'
        order by created_at asc
        limit :limit
        for update skip locked
        """, nativeQuery = true)
    List<OutboxEvent> findNextPublishBatch(@Param("limit") int limit);

    @Modifying
    @Query("""
        update OutboxEvent e
        set e.status = com.sacco.mvp.domain.OutboxStatus.NEW, e.publishedAt = null
        where e.status = com.sacco.mvp.domain.OutboxStatus.PROCESSING
          and e.publishedAt < :staleBefore
        """)
    int reclaimStaleProcessing(@Param("staleBefore") OffsetDateTime staleBefore);

    List<OutboxEvent> findTop10ByStatusOrderByCreatedAtDesc(OutboxStatus status);

    @Query(
        value = """
            select *
            from outbox_events
            where status = 'FAILED'
              and payload ->> 'saccoId' = :saccoId
              and (cast(:stationId as text) is null or payload ->> 'stationId' = :stationId)
            order by created_at desc
            limit 10
            """,
        nativeQuery = true
    )
    List<OutboxEvent> findRecentFailedForScope(@Param("saccoId") String saccoId,
                                               @Param("stationId") String stationId);

    interface StatusCountRow {
        String getStatus();
        long getTotal();
    }

    @Query(
        value = """
            select status as status, count(*) as total
            from outbox_events
            where payload ->> 'saccoId' = :saccoId
              and (cast(:stationId as text) is null or payload ->> 'stationId' = :stationId)
            group by status
            """,
        nativeQuery = true
    )
    List<StatusCountRow> countGroupedByStatusForScope(@Param("saccoId") String saccoId,
                                                      @Param("stationId") String stationId);

    List<OutboxEvent> findTop100ByOrderByCreatedAtDesc();

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtDesc(OutboxStatus status);

    @Query(
        value = """
            select exists (
                select 1
                from outbox_events oe
                where oe.aggregate_type = :aggregateType
                  and oe.aggregate_id = :aggregateId
                  and oe.event_type = :eventType
                  and oe.status in ('NEW', 'PUBLISHED')
                  and oe.payload ->> 'recipientId' = cast(:recipientId as text)
            )
            """,
        nativeQuery = true
    )
    boolean existsActiveDuplicate(@Param("aggregateType") String aggregateType,
                                  @Param("aggregateId") UUID aggregateId,
                                  @Param("eventType") String eventType,
                                  @Param("recipientId") UUID recipientId);

    Page<OutboxEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<OutboxEvent> findByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);

    long deleteByCreatedAtBefore(OffsetDateTime cutoff);

    @Query(
        value = """
            select *
            from outbox_events oe
            where cast(oe.status as text) = coalesce(cast(:status as text), cast(oe.status as text))
              and oe.created_at >= coalesce(:dateFrom, oe.created_at)
              and oe.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and cast(oe.aggregate_id as text) ilike coalesce(concat('%', cast(:loanApplicationId as text), '%'), '%')
            order by oe.created_at desc
            """,
        countQuery = """
            select count(*)
            from outbox_events oe
            where cast(oe.status as text) = coalesce(cast(:status as text), cast(oe.status as text))
              and oe.created_at >= coalesce(:dateFrom, oe.created_at)
              and oe.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and cast(oe.aggregate_id as text) ilike coalesce(concat('%', cast(:loanApplicationId as text), '%'), '%')
            """,
        nativeQuery = true
    )
    Page<OutboxEvent> searchMonitorView(@Param("status") String status,
                                        @Param("dateFrom") OffsetDateTime dateFrom,
                                        @Param("dateTo") OffsetDateTime dateTo,
                                        @Param("loanApplicationId") String loanApplicationId,
                                        Pageable pageable);

    @Query(
        value = """
            select distinct oe.*
            from outbox_events oe
            left join loan_applications la on la.id = oe.aggregate_id
            left join members applicant on applicant.id = la.applicant_member_id
            left join members entity_member on entity_member.id = oe.aggregate_id
            where cast(oe.status as text) = coalesce(cast(:status as text), cast(oe.status as text))
              and oe.created_at >= coalesce(:dateFrom, oe.created_at)
              and oe.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and cast(oe.aggregate_id as text) ilike coalesce(concat('%', cast(:loanApplicationId as text), '%'), '%')
              and (
                cast(:saccoId as text) is null
                or la.sacco_id = cast(:saccoId as text)
                or applicant.sacco_id = cast(:saccoId as text)
                or entity_member.sacco_id = cast(:saccoId as text)
                or cast(oe.payload as text) ilike concat('%', cast(:saccoId as text), '%')
              )
              and (
                cast(:stationId as text) is null
                or (
                  cast(:saccoId as text) is not null
                  and (
                    (la.sacco_id = cast(:saccoId as text) and la.station_id = cast(:stationId as text))
                    or (applicant.sacco_id = cast(:saccoId as text) and applicant.station_id = cast(:stationId as text))
                    or (entity_member.sacco_id = cast(:saccoId as text) and entity_member.station_id = cast(:stationId as text))
                    or (
                      cast(oe.payload as text) ilike concat('%', cast(:saccoId as text), '%')
                      and cast(oe.payload as text) ilike concat('%', cast(:stationId as text), '%')
                    )
                  )
                )
              )
            order by oe.created_at desc
            """,
        countQuery = """
            select count(distinct oe.id)
            from outbox_events oe
            left join loan_applications la on la.id = oe.aggregate_id
            left join members applicant on applicant.id = la.applicant_member_id
            left join members entity_member on entity_member.id = oe.aggregate_id
            where cast(oe.status as text) = coalesce(cast(:status as text), cast(oe.status as text))
              and oe.created_at >= coalesce(:dateFrom, oe.created_at)
              and oe.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and cast(oe.aggregate_id as text) ilike coalesce(concat('%', cast(:loanApplicationId as text), '%'), '%')
              and (
                cast(:saccoId as text) is null
                or la.sacco_id = cast(:saccoId as text)
                or applicant.sacco_id = cast(:saccoId as text)
                or entity_member.sacco_id = cast(:saccoId as text)
                or cast(oe.payload as text) ilike concat('%', cast(:saccoId as text), '%')
              )
              and (
                cast(:stationId as text) is null
                or (
                  cast(:saccoId as text) is not null
                  and (
                    (la.sacco_id = cast(:saccoId as text) and la.station_id = cast(:stationId as text))
                    or (applicant.sacco_id = cast(:saccoId as text) and applicant.station_id = cast(:stationId as text))
                    or (entity_member.sacco_id = cast(:saccoId as text) and entity_member.station_id = cast(:stationId as text))
                    or (
                      cast(oe.payload as text) ilike concat('%', cast(:saccoId as text), '%')
                      and cast(oe.payload as text) ilike concat('%', cast(:stationId as text), '%')
                    )
                  )
                )
              )
            """,
        nativeQuery = true
    )
    Page<OutboxEvent> searchMonitorViewScoped(@Param("status") String status,
                                              @Param("dateFrom") OffsetDateTime dateFrom,
                                              @Param("dateTo") OffsetDateTime dateTo,
                                              @Param("loanApplicationId") String loanApplicationId,
                                              @Param("saccoId") String saccoId,
                                              @Param("stationId") String stationId,
                                              Pageable pageable);

    long countByStatus(OutboxStatus status);
}
