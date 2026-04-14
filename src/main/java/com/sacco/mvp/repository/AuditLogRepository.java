package com.sacco.mvp.repository;

import com.sacco.mvp.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    java.util.List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long deleteByCreatedAtBefore(OffsetDateTime cutoff);

    @Query(
        value = """
            select *
            from audit_log al
            where al.created_at >= coalesce(:dateFrom, al.created_at)
              and al.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and cast(al.actor_member_id as text) ilike coalesce(concat('%', cast(:actorId as text), '%'), '%')
            order by al.created_at desc
            """,
        countQuery = """
            select count(*)
            from audit_log al
            where al.created_at >= coalesce(:dateFrom, al.created_at)
              and al.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and cast(al.actor_member_id as text) ilike coalesce(concat('%', cast(:actorId as text), '%'), '%')
            """,
        nativeQuery = true
    )
    Page<AuditLog> searchEventLogView(@Param("dateFrom") OffsetDateTime dateFrom,
                                      @Param("dateTo") OffsetDateTime dateTo,
                                      @Param("actorId") String actorId,
                                      Pageable pageable);
}
