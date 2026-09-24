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
              and (cast(:actorId as text) is null or cast(al.actor_member_id as text) ilike concat('%', cast(:actorId as text), '%'))
            order by al.created_at desc
            """,
        countQuery = """
            select count(*)
            from audit_log al
            where al.created_at >= coalesce(:dateFrom, al.created_at)
              and al.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and (cast(:actorId as text) is null or cast(al.actor_member_id as text) ilike concat('%', cast(:actorId as text), '%'))
            """,
        nativeQuery = true
    )
    Page<AuditLog> searchEventLogView(@Param("dateFrom") OffsetDateTime dateFrom,
                                      @Param("dateTo") OffsetDateTime dateTo,
                                      @Param("actorId") String actorId,
                                      Pageable pageable);

    @Query(
        value = """
            select distinct al.*
            from audit_log al
            left join members actor_member on actor_member.id = al.actor_member_id
            left join members entity_member on entity_member.id = al.entity_id
            left join loan_applications entity_loan on entity_loan.id = al.entity_id
            left join members loan_applicant on loan_applicant.id = entity_loan.applicant_member_id
            where al.created_at >= coalesce(:dateFrom, al.created_at)
              and al.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and (cast(:actorId as text) is null or cast(al.actor_member_id as text) ilike concat('%', cast(:actorId as text), '%'))
              and (
                cast(:saccoId as text) is null
                or al.sacco_id = cast(:saccoId as text)
                or actor_member.sacco_id = cast(:saccoId as text)
                or entity_member.sacco_id = cast(:saccoId as text)
                or entity_loan.sacco_id = cast(:saccoId as text)
                or loan_applicant.sacco_id = cast(:saccoId as text)
                or cast(al.before_state as text) ilike concat('%', cast(:saccoId as text), '%')
                or cast(al.after_state as text) ilike concat('%', cast(:saccoId as text), '%')
              )
              and (
                cast(:stationId as text) is null
                or (
                  cast(:saccoId as text) is not null
                  and (
                    al.station_id = cast(:stationId as text)
                    or (actor_member.sacco_id = cast(:saccoId as text) and actor_member.station_id = cast(:stationId as text))
                    or (entity_member.sacco_id = cast(:saccoId as text) and entity_member.station_id = cast(:stationId as text))
                    or (entity_loan.sacco_id = cast(:saccoId as text) and entity_loan.station_id = cast(:stationId as text))
                    or (loan_applicant.sacco_id = cast(:saccoId as text) and loan_applicant.station_id = cast(:stationId as text))
                    or (
                      (
                        cast(al.before_state as text) ilike concat('%', cast(:saccoId as text), '%')
                        or cast(al.after_state as text) ilike concat('%', cast(:saccoId as text), '%')
                      )
                      and (
                        cast(al.before_state as text) ilike concat('%', cast(:stationId as text), '%')
                        or cast(al.after_state as text) ilike concat('%', cast(:stationId as text), '%')
                      )
                    )
                  )
                )
              )
            order by al.created_at desc
            """,
        countQuery = """
            select count(distinct al.id)
            from audit_log al
            left join members actor_member on actor_member.id = al.actor_member_id
            left join members entity_member on entity_member.id = al.entity_id
            left join loan_applications entity_loan on entity_loan.id = al.entity_id
            left join members loan_applicant on loan_applicant.id = entity_loan.applicant_member_id
            where al.created_at >= coalesce(:dateFrom, al.created_at)
              and al.created_at < coalesce(:dateTo, cast('9999-12-31 23:59:59+00' as timestamptz))
              and (cast(:actorId as text) is null or cast(al.actor_member_id as text) ilike concat('%', cast(:actorId as text), '%'))
              and (
                cast(:saccoId as text) is null
                or al.sacco_id = cast(:saccoId as text)
                or actor_member.sacco_id = cast(:saccoId as text)
                or entity_member.sacco_id = cast(:saccoId as text)
                or entity_loan.sacco_id = cast(:saccoId as text)
                or loan_applicant.sacco_id = cast(:saccoId as text)
                or cast(al.before_state as text) ilike concat('%', cast(:saccoId as text), '%')
                or cast(al.after_state as text) ilike concat('%', cast(:saccoId as text), '%')
              )
              and (
                cast(:stationId as text) is null
                or (
                  cast(:saccoId as text) is not null
                  and (
                    al.station_id = cast(:stationId as text)
                    or (actor_member.sacco_id = cast(:saccoId as text) and actor_member.station_id = cast(:stationId as text))
                    or (entity_member.sacco_id = cast(:saccoId as text) and entity_member.station_id = cast(:stationId as text))
                    or (entity_loan.sacco_id = cast(:saccoId as text) and entity_loan.station_id = cast(:stationId as text))
                    or (loan_applicant.sacco_id = cast(:saccoId as text) and loan_applicant.station_id = cast(:stationId as text))
                    or (
                      (
                        cast(al.before_state as text) ilike concat('%', cast(:saccoId as text), '%')
                        or cast(al.after_state as text) ilike concat('%', cast(:saccoId as text), '%')
                      )
                      and (
                        cast(al.before_state as text) ilike concat('%', cast(:stationId as text), '%')
                        or cast(al.after_state as text) ilike concat('%', cast(:stationId as text), '%')
                      )
                    )
                  )
                )
              )
            """,
        nativeQuery = true
    )
    Page<AuditLog> searchEventLogViewScoped(@Param("dateFrom") OffsetDateTime dateFrom,
                                            @Param("dateTo") OffsetDateTime dateTo,
                                            @Param("actorId") String actorId,
                                            @Param("saccoId") String saccoId,
                                            @Param("stationId") String stationId,
                                            Pageable pageable);
}
