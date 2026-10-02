package com.sacco.mvp.reporting.execution.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.sacco.mvp.service.ReportExportLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.*;

@Component
@ConditionalOnProperty(name="app.reports.worker-enabled",havingValue="true",matchIfMissing=true)
public class ReportRunWorker {
 private static final Logger log=LoggerFactory.getLogger(ReportRunWorker.class);
 private final ReportWorkerControl control;
 private final ReportRunGenerator generator;
 private final ReportExportLimiter limiter;
 private final ThreadPoolExecutor executor;
 private final MeterRegistry metrics;
 public ReportRunWorker(ReportWorkerControl control,ReportRunGenerator generator,ReportExportLimiter limiter,
  @Qualifier("reportGenerationExecutor")ThreadPoolExecutor executor,MeterRegistry metrics){
  this.control=control;this.generator=generator;this.limiter=limiter;this.executor=executor;this.metrics=metrics;
  metrics.gauge("accounting.report.executor.active",executor,ThreadPoolExecutor::getActiveCount);
 }
 @Scheduled(fixedDelayString="${app.reports.worker-delay-ms:2000}")
 public void tick(){
  if(executor.getActiveCount()>=executor.getMaximumPoolSize()||!limiter.tryAcquire())return;
  try{var candidate=control.claim();if(candidate.isEmpty()){limiter.release();return;}var run=candidate.get();
   metrics.counter("accounting.report.queue.claimed").increment();try{executor.execute(()->{
    long started=System.nanoTime();String outcome="success";
    try{generator.generate(run);}catch(RuntimeException ex){outcome="failure";String key=ex.getMessage()!=null&&ex.getMessage().startsWith("report.run.error.")?ex.getMessage():"report.run.error.failed";control.fail(run,key);log.warn("Report generation failed: run={}, failure={}",run.id(),key);}
    finally{try{metrics.timer("accounting.report.generation","outcome",outcome).record(System.nanoTime()-started,TimeUnit.NANOSECONDS);}finally{limiter.release();}}
   });}
   catch(RejectedExecutionException ex){control.fail(run,"report.run.error.busy");limiter.release();}
  }catch(RuntimeException ex){limiter.release();throw ex;}
 }
}
