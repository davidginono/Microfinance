package com.sacco.mvp.service;

import com.sacco.mvp.domain.SaccoLoanAppCounter;
import com.sacco.mvp.repository.SaccoLoanAppCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Issues the next per-SACCO sequential loan application number.
 * <p>
 * Allocates per-SACCO numbers in small blocks. The database row is still the
 * source of truth, but it is locked once per block instead of once per draft.
 * This preserves uniqueness under concurrency while avoiding a hot-row lock on
 * every loan application creation.
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
    private final PlatformTransactionManager transactionManager;
    private final ConcurrentMap<String, NumberBlock> blocks = new ConcurrentHashMap<>();

    @Value("${app.application-numbers.block-size:100}")
    private int configuredBlockSize;

    public long nextFor(String saccoId) {
        String key = saccoId == null ? "" : saccoId.trim();
        NumberBlock block = blocks.computeIfAbsent(key, ignored -> new NumberBlock(1, 0));
        synchronized (block) {
            if (!block.hasNext()) {
                NumberBlock allocated = allocateBlock(key);
                block.reset(allocated.next, allocated.max);
            }
            return block.next();
        }
    }

    private NumberBlock allocateBlock(String saccoId) {
        int blockSize = Math.max(1, configuredBlockSize);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            SaccoLoanAppCounter counter = counterRepository.findBySaccoId(saccoId)
                .orElseGet(() -> SaccoLoanAppCounter.builder()
                    .saccoId(saccoId)
                    .lastNumber(STARTING_NUMBER)
                    .build());
            long first = counter.getLastNumber() + 1;
            long max = counter.getLastNumber() + blockSize;
            counter.setLastNumber(max);
            counterRepository.save(counter);
            return new NumberBlock(first, max);
        });
    }

    private static final class NumberBlock {
        private long next;
        private long max;

        private NumberBlock(long next, long max) {
            this.next = next;
            this.max = max;
        }

        private boolean hasNext() {
            return next <= max;
        }

        private long next() {
            return next++;
        }

        private void reset(long next, long max) {
            this.next = next;
            this.max = max;
        }
    }
}
