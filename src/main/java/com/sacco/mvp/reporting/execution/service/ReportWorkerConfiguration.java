package com.sacco.mvp.reporting.execution.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.util.concurrent.*;

@Configuration(proxyBeanMethods=false)
public class ReportWorkerConfiguration {
 @Bean(name="reportGenerationExecutor",destroyMethod="shutdown")
 public ThreadPoolExecutor reportGenerationExecutor(@Value("${app.reports.worker-threads:1}")int threads){
  if(threads<1||threads>4)throw new IllegalArgumentException("Report workers must be between one and four");
  return new ThreadPoolExecutor(threads,threads,0,TimeUnit.MILLISECONDS,new SynchronousQueue<>(),
   Thread.ofPlatform().daemon(true).name("report-generation-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
 }
}
