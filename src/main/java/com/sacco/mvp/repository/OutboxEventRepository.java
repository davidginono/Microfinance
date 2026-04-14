package com.sacco.mvp.repository;

import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>, JpaSpecificationExecutor<OutboxEvent> {
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findTop100ByOrderByCreatedAtDesc();

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtDesc(OutboxStatus status);

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
              and cast(oe.aggregate_id as text) ilike coalesce(concat('%', cast(:loanId as text), '%'), '%')
            order by oe.created_at desc
            """,
        countQuery = """
            select count(*)
            from outbox_events oe
            where cast(oe.status as text) = coalesce(cast(:status as text), cast(oe.status as text))
              and oe.created_at >= coalesce(:dateFrom, oe.created_at)
              and oe.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and cast(oe.aggregate_id as text) ilike coalesce(concat('%', cast(:loanId as text), '%'), '%')
            """,
        nativeQuery = true
    )
    Page<OutboxEvent> searchMonitorView(@Param("status") String status,
                                        @Param("dateFrom") OffsetDateTime dateFrom,
                                        @Param("dateTo") OffsetDateTime dateTo,
                                        @Param("loanId") String loanId,
                                        Pageable pageable);

    long countByStatus(OutboxStatus status);
}
