package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SaccoStation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SaccoStationRepository extends JpaRepository<SaccoStation, UUID> {
    @org.springframework.data.jpa.repository.Query("""
        select b from SaccoStation b where b.saccoId=:institution and (:branch is null or b.stationId=:branch)
        """)
    org.springframework.data.domain.Slice<SaccoStation> branchPage(
        @org.springframework.data.repository.query.Param("institution") String institution,
        @org.springframework.data.repository.query.Param("branch") String branch,
        org.springframework.data.domain.Pageable page);

    List<SaccoStation> findBySaccoIdOrderByStationIdAsc(String saccoId);

    List<SaccoStation> findBySaccoIdAndActiveTrueOrderByStationIdAsc(String saccoId);

    List<SaccoStation> findBySaccoIdInAndActiveTrueOrderBySaccoIdAscStationIdAsc(Collection<String> saccoIds);

    Optional<SaccoStation> findBySaccoIdAndStationId(String saccoId, String stationId);

    Optional<SaccoStation> findBySaccoIdAndStationIdAndActiveTrue(String saccoId, String stationId);
}
