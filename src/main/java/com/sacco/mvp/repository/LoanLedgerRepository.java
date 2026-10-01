package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface LoanLedgerRepository extends JpaRepository<LoanLedger, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanLedger l where l.loanApplicationId = :id and l.saccoId = :institution and l.stationId = :branch")
    Optional<LoanLedger> lockScoped(@Param("id") UUID id, @Param("institution") String institution, @Param("branch") String branch);

    @Query("select l from LoanLedger l where l.saccoId = :institution and l.stationId = :branch and (:number is null or l.loanId = :number)")
    Page<LoanLedger> listScoped(@Param("institution") String institution, @Param("branch") String branch,
                               @Param("number") String number, Pageable pageable);
}
