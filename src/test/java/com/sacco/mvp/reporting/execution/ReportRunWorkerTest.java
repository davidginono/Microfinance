package com.sacco.mvp.reporting.execution;

import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.Run;
import com.sacco.mvp.reporting.execution.service.*;
import com.sacco.mvp.service.ReportExportLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportRunWorkerTest {
 @Test void generationRunsOutsideSchedulerAndDoesNotTakeASecondJobWhenExecutorIsFull()throws Exception{
  var control=mock(ReportWorkerControl.class);var generator=mock(ReportRunGenerator.class);var limiter=mock(ReportExportLimiter.class);Run run=mock(Run.class);
  when(limiter.tryAcquire()).thenReturn(true);when(control.claim()).thenReturn(Optional.of(run));CountDownLatch started=new CountDownLatch(1),finish=new CountDownLatch(1);
  doAnswer(invocation->{started.countDown();if(!finish.await(10,TimeUnit.SECONDS))throw new AssertionError("Test did not release worker");return null;}).when(generator).generate(run);
  var executor=new ReportWorkerConfiguration().reportGenerationExecutor(1);var metrics=new SimpleMeterRegistry();var worker=new ReportRunWorker(control,generator,limiter,executor,metrics);
  try{worker.tick();assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();assertThat(executor.getActiveCount()).isEqualTo(1);worker.tick();verify(control,times(1)).claim();verify(limiter,never()).release();finish.countDown();executor.shutdown();assertThat(executor.awaitTermination(5,TimeUnit.SECONDS)).isTrue();verify(limiter,times(1)).release();assertThat(metrics.get("accounting.report.generation").tag("outcome","success").timer().count()).isEqualTo(1);}
  finally{finish.countDown();executor.shutdownNow();metrics.close();}
 }
 @Test void failedClaimReturnsItsExportSlot(){
  var control=mock(ReportWorkerControl.class);var limiter=mock(ReportExportLimiter.class);when(limiter.tryAcquire()).thenReturn(true);when(control.claim()).thenThrow(new IllegalStateException("Synthetic unavailable database"));
  var executor=new ReportWorkerConfiguration().reportGenerationExecutor(1);try{var worker=new ReportRunWorker(control,mock(ReportRunGenerator.class),limiter,executor,new SimpleMeterRegistry());assertThatThrownBy(worker::tick).hasMessage("Synthetic unavailable database");verify(limiter).release();}finally{executor.shutdownNow();}
 }
}
