package com.sacco.mvp.repository;

import com.sacco.mvp.domain.AdminIncident;
import com.sacco.mvp.domain.IncidentSeverity;
import com.sacco.mvp.domain.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminIncidentRepository extends JpaRepository<AdminIncident, UUID> {
    List<AdminIncident> findBySaccoIdOrderByCreatedAtDesc(String saccoId);

    List<AdminIncident> findBySaccoIdAndStatusOrderByCreatedAtDesc(String saccoId, IncidentStatus status);

    List<AdminIncident> findBySaccoIdAndSeverityOrderByCreatedAtDesc(String saccoId, IncidentSeverity severity);
}

