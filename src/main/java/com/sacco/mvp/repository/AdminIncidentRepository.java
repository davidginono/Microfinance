package com.sacco.mvp.repository;

import com.sacco.mvp.domain.AdminIncident;
import com.sacco.mvp.domain.IncidentSeverity;
import com.sacco.mvp.domain.IncidentStatus;
import org.springframework.data.domain.Pageable;
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
        where (:saccoId is null or i.saccoId = :saccoId)
          and (:status is null or i.status = :status)
          and (:severity is null or i.severity = :severity)
        order by i.createdAt desc
        """)
    List<AdminIncident> findRecentForReview(@Param("saccoId") String saccoId,
                                            @Param("status") IncidentStatus status,
                                            @Param("severity") IncidentSeverity severity,
                                            Pageable pageable);

    List<AdminIncident> findByReportedByMemberIdOrderByCreatedAtDesc(UUID reportedByMemberId);

    List<AdminIncident> findBySaccoIdAndStatusOrderByCreatedAtDesc(String saccoId, IncidentStatus status);

    List<AdminIncident> findBySaccoIdAndSeverityOrderByCreatedAtDesc(String saccoId, IncidentSeverity severity);

    List<AdminIncident> findTop50BySaccoIdAndCreatedAtAfterOrderByCreatedAtDesc(String saccoId, OffsetDateTime createdAt);
}

