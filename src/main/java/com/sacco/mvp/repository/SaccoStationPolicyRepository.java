package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SaccoStationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaccoStationPolicyRepository extends JpaRepository<SaccoStationPolicy, UUID> {
    Optional<SaccoStationPolicy> findBySaccoIdAndStationId(String saccoId, String stationId);

    List<SaccoStationPolicy> findBySaccoIdOrderByStationIdAsc(String saccoId);
}
