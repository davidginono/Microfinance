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
    List<AdminIncident> findBySaccoIdOrderByCreatedAtDesc(String saccoId);

    List<AdminIncident> findTop250ByOrderByCreatedAtDesc();

    List<AdminIncident> findTop250BySaccoIdOrderByCreatedAtDesc(String saccoId);

    @Query("""
        select i
        from AdminIncident i
        where (cast(:saccoId as string) is null or i.saccoId = :saccoId)
          and (cast(:status as string) is null or i.status = :status)
          and (cast(:severity as string) is null or i.severity = :severity)
        order by i.createdAt desc
        """)
    List<AdminIncident> findRecentForReview(@Param("saccoId") String saccoId,
                                            @Param("status") IncidentStatus status,
                                            @Param("severity") IncidentSeverity severity,
                                            Pageable pageable);

    List<AdminIncident> findByReportedByMemberIdOrderByCreatedAtDesc(UUID reportedByMemberId);

    @Query("""
        select i
        from AdminIncident i
        where i.reportedByMemberId = :reporterId
          and i.category = 'SUPPORT_MESSAGE'
          and i.source = :source
          and (cast(:sentFrom as timestamp) is null or i.createdAt >= :sentFrom)
          and (cast(:sentToExclusive as timestamp) is null or i.createdAt < :sentToExclusive)
        order by i.createdAt desc
        """)
    Page<AdminIncident> findSupportArchivePage(@Param("reporterId") UUID reporterId,
                                               @Param("source") String source,
                                               @Param("sentFrom") OffsetDateTime sentFrom,
                                               @Param("sentToExclusive") OffsetDateTime sentToExclusive,
                                               Pageable pageable);

    List<AdminIncident> findBySaccoIdAndStatusOrderByCreatedAtDesc(String saccoId, IncidentStatus status);

    List<AdminIncident> findBySaccoIdAndSeverityOrderByCreatedAtDesc(String saccoId, IncidentSeverity severity);

    List<AdminIncident> findTop50BySaccoIdAndCreatedAtAfterOrderByCreatedAtDesc(String saccoId, OffsetDateTime createdAt);
}

