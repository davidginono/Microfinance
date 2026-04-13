package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SaccoStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaccoStationRepository extends JpaRepository<SaccoStation, UUID> {
    List<SaccoStation> findBySaccoIdAndActiveTrueOrderByStationIdAsc(String saccoId);

    Optional<SaccoStation> findBySaccoIdAndStationId(String saccoId, String stationId);
}
