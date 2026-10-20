package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanJournalEntry;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LoanJournalEntryRepository extends JpaRepository<LoanJournalEntry, UUID> {
    @org.springframework.data.jpa.repository.Query(value = "select j from LoanJournalEntry j where j.loanApplicationId=:id order by j.postedAt desc,j.id",
        queryRewriter = com.sacco.mvp.support.ResourceTableSort.LoanJournal.class)
    Page<LoanJournalEntry> findByLoanApplicationIdOrderByPostedAtDescId(@org.springframework.data.repository.query.Param("id") UUID id, Pageable pageable);
}
