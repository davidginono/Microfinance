package com.sacco.mvp.repository;

import com.sacco.mvp.domain.AdminIncident;
import com.sacco.mvp.domain.IncidentSeverity;
import com.sacco.mvp.domain.IncidentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AdminIncidentRepository extends JpaRepository<AdminIncident, UUID> {
    List<AdminIncident> findTop250ByOrderByCreatedAtDesc();

    List<AdminIncident> findTop250BySaccoIdOrderByCreatedAtDesc(String saccoId);

    @Query(value = """
        select i
        from AdminIncident i
        where (cast(:saccoId as string) is null or i.saccoId = :saccoId)
          and (cast(:status as string) is null or i.status = :status)
          and (cast(:severity as string) is null or i.severity = :severity)
        order by i.createdAt desc
        """, queryRewriter = com.sacco.mvp.support.ResourceTableSort.Incidents.class)
    List<AdminIncident> findRecentForReview(@Param("saccoId") String saccoId,
                                            @Param("status") IncidentStatus status,
                                            @Param("severity") IncidentSeverity severity,
                                            Pageable pageable);

    @Query(value = """
        select i.*
        from admin_incidents i
        where i.reported_by_member_id = :reporterId
          and i.category = 'SUPPORT_MESSAGE'
          and i.source = :source
          and (cast(:sentFrom as timestamptz) is null or i.created_at >= :sentFrom)
          and (cast(:sentToExclusive as timestamptz) is null or i.created_at < :sentToExclusive)
        order by i.created_at desc
        """, nativeQuery = true, queryRewriter = com.sacco.mvp.support.ResourceTableSort.SupportArchives.class)
    Page<AdminIncident> findSupportArchivePage(@Param("reporterId") UUID reporterId,
                                               @Param("source") String source,
                                               @Param("sentFrom") OffsetDateTime sentFrom,
                                               @Param("sentToExclusive") OffsetDateTime sentToExclusive,
                                               Pageable pageable);

    List<AdminIncident> findBySaccoIdAndStatusOrderByCreatedAtDesc(String saccoId, IncidentStatus status);

    List<AdminIncident> findBySaccoIdAndSeverityOrderByCreatedAtDesc(String saccoId, IncidentSeverity severity);

    List<AdminIncident> findTop50BySaccoIdAndCreatedAtAfterOrderByCreatedAtDesc(String saccoId, OffsetDateTime createdAt);
}

