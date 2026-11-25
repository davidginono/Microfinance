package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface LoanLedgerRepository extends JpaRepository<LoanLedger, UUID> {
    @Query(value="""
        select max(event_date) from (select q.effective_date event_date from loan_adjustment_request q
        join loan_adjustment_decision d on d.request_id=q.id
        join gl_journal j on j.id=d.journal_id and j.state='POSTED'
        where q.loan_id=:id and q.sacco_id=:institution and q.station_id=:branch
        union all select payment_date from loan_repayment_transactions where loan_application_id=:id
        and sacco_id=:institution and station_id=:branch and kind in ('SETTLEMENT','SETTLEMENT_UNDO')) movements
        """,nativeQuery=true)
    java.time.LocalDate latestAdjustmentDate(@Param("id") UUID id,@Param("institution") String institution,@Param("branch") String branch);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanLedger l where l.loanApplicationId = :id and l.saccoId = :institution and l.stationId = :branch")
    Optional<LoanLedger> lockScoped(@Param("id") UUID id, @Param("institution") String institution, @Param("branch") String branch);
}
