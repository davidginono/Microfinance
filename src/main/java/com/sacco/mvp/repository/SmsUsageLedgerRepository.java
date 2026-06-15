package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SmsUsageLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SmsUsageLedgerRepository extends JpaRepository<SmsUsageLedger, UUID> {
    Optional<SmsUsageLedger> findByIdAndAccountId(UUID id, UUID accountId);

    Page<SmsUsageLedger> findBySaccoIdAndStationIdOrderByCreatedAtDesc(String saccoId, String stationId, Pageable pageable);

    Page<SmsUsageLedger> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
