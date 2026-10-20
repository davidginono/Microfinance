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
    @Query(value = """
        select a.* from station_sms_accounts a left join registered_saccos i on i.sacco_id=a.sacco_id
        where (cast(:institution as text) is null or a.sacco_id=:institution)
          and (cast(:branch as text) is null or a.station_id=:branch)
          and (cast(:status as text) is null or a.status=:status)
        order by a.sacco_id,a.station_id,a.id
        """, nativeQuery = true, queryRewriter = com.sacco.mvp.support.ResourceTableSort.SmsAccounts.class)
    org.springframework.data.domain.Page<StationSmsAccount> findWorkspaceAccounts(@Param("institution") String institution,
        @Param("branch") String branch,@Param("status") String status,org.springframework.data.domain.Pageable page);

    Optional<StationSmsAccount> findBySaccoIdAndStationId(String saccoId, String stationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StationSmsAccount a where a.saccoId = :saccoId and a.stationId = :stationId")
    Optional<StationSmsAccount> findForUpdate(@Param("saccoId") String saccoId, @Param("stationId") String stationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StationSmsAccount a where a.id = :id")
    Optional<StationSmsAccount> findByIdForUpdate(@Param("id") UUID id);

    List<StationSmsAccount> findAllByOrderBySaccoIdAscStationIdAsc();

}
