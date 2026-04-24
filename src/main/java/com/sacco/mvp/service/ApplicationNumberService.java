package com.sacco.mvp.service;

import com.sacco.mvp.domain.SaccoLoanAppCounter;
import com.sacco.mvp.repository.SaccoLoanAppCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues the next per-SACCO sequential loan application number.
 * <p>
 * Runs inside the caller's transaction and takes a pessimistic write lock on
 * the SACCO's counter row, which serialises concurrent draft creations for the
 * same SACCO and guarantees contiguous, unique numbers.
 */
@Service
@RequiredArgsConstructor
public class ApplicationNumberService {

    /**
     * Starting point for SACCOs that have never issued a number. The Flyway
     * backfill also begins at 100001 so legacy rows and new rows share a scale.
     */
    private static final long STARTING_NUMBER = 100_000L;

    private final SaccoLoanAppCounterRepository counterRepository;

    @Transactional
    public long nextFor(String saccoId) {
        SaccoLoanAppCounter counter = counterRepository.findBySaccoId(saccoId)
            .orElseGet(() -> SaccoLoanAppCounter.builder()
                .saccoId(saccoId)
                .lastNumber(STARTING_NUMBER)
                .build());
        long next = counter.getLastNumber() + 1;
        counter.setLastNumber(next);
        counterRepository.save(counter);
        return next;
    }
}
