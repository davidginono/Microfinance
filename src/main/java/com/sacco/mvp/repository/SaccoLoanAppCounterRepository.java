package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SaccoLoanAppCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface SaccoLoanAppCounterRepository extends JpaRepository<SaccoLoanAppCounter, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SaccoLoanAppCounter> findBySaccoId(String saccoId);
}
