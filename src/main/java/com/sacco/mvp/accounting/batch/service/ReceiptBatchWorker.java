package com.sacco.mvp.accounting.batch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="app.receipt-batches.worker-enabled",havingValue="true",matchIfMissing=true)
public class ReceiptBatchWorker {
    private static final Logger log=LoggerFactory.getLogger(ReceiptBatchWorker.class);
    private final ReceiptBatchWorkerService worker;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    @Bean public static ThreadPoolTaskScheduler receiptBatchScheduler(){var s=new ThreadPoolTaskScheduler();s.setPoolSize(1);s.setThreadNamePrefix("receipt-batch-");s.setWaitForTasksToCompleteOnShutdown(true);s.setAwaitTerminationSeconds(35);return s;}
    @Scheduled(fixedDelayString="${app.receipt-batches.worker-delay-ms:250}",scheduler="receiptBatchScheduler")
    public void tick(){
        var candidate=worker.claim();if(candidate.isEmpty())return;var batch=candidate.get();
        long started=System.nanoTime();String outcome="success";
        try{worker.step(batch);}catch(RuntimeException failure){outcome="failure";worker.failure(batch,failure);log.warn("Receipt batch step failed: batch={}, code={}",batch.id(),ReceiptBatchWorkerService.safeError(failure));}
        finally{metrics.counter("accounting.receipt.batch.steps","phase",batch.state(),"outcome",outcome).increment();metrics.timer("accounting.receipt.batch.step.duration","phase",batch.state(),"outcome",outcome).record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS);}
    }
}
