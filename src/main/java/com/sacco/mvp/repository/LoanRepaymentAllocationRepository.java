package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanRepaymentAllocation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LoanRepaymentAllocationRepository extends JpaRepository<LoanRepaymentAllocation, UUID> {
    List<LoanRepaymentAllocation> findByTransactionId(UUID id, Pageable pageable);
}
