package com.sacco.mvp.repository;

import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findTop100ByOrderByCreatedAtDesc();

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtDesc(OutboxStatus status);

    long countByStatus(OutboxStatus status);
}
