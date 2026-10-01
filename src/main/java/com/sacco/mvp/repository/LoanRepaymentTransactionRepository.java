package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanRepaymentTransaction;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface LoanRepaymentTransactionRepository extends JpaRepository<LoanRepaymentTransaction, UUID> {
    Optional<LoanRepaymentTransaction> findBySaccoIdAndStationIdAndRequestKey(String institution, String branch, UUID key);
    List<LoanRepaymentTransaction> findByLoanApplicationIdAndReversesTransactionIdIn(UUID loanId, Collection<UUID> ids);
    Page<LoanRepaymentTransaction> findByLoanApplicationIdOrderBySequenceDesc(UUID id, Pageable pageable);
    boolean existsBySaccoIdAndStationIdAndChannelAndChannelReferenceAndKind(String institution, String branch,
        LoanRepaymentTransaction.Channel channel, String reference, LoanRepaymentTransaction.Kind kind);

    @Query("""
        select t from LoanRepaymentTransaction t where t.loanApplicationId = :loanId and t.kind = 'PAYMENT'
        and not exists (select r.id from LoanRepaymentTransaction r where r.reversesTransactionId = t.id)
        order by t.sequence desc
        """)
    List<LoanRepaymentTransaction> latestUnreversed(@Param("loanId") UUID id, Pageable pageable);
}
