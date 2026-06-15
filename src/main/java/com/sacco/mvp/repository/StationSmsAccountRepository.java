package com.sacco.mvp.repository;

import com.sacco.mvp.domain.StationSmsAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StationSmsAccountRepository extends JpaRepository<StationSmsAccount, UUID>,
    JpaSpecificationExecutor<StationSmsAccount> {
    Optional<StationSmsAccount> findBySaccoIdAndStationId(String saccoId, String stationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StationSmsAccount a where a.saccoId = :saccoId and a.stationId = :stationId")
    Optional<StationSmsAccount> findForUpdate(@Param("saccoId") String saccoId, @Param("stationId") String stationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StationSmsAccount a where a.id = :id")
    Optional<StationSmsAccount> findByIdForUpdate(@Param("id") UUID id);

    List<StationSmsAccount> findAllByOrderBySaccoIdAscStationIdAsc();

}
