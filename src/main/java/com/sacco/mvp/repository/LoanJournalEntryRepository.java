package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanJournalEntry;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LoanJournalEntryRepository extends JpaRepository<LoanJournalEntry, UUID> {
    Page<LoanJournalEntry> findByLoanApplicationIdOrderByPostedAtDescId(UUID id, Pageable pageable);
}
