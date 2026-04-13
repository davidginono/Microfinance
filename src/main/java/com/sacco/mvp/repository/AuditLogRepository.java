package com.sacco.mvp.repository;

import com.sacco.mvp.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    java.util.List<AuditLog> findTop100ByOrderByCreatedAtDesc();
}
