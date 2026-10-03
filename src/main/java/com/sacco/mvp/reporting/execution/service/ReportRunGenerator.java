package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.reporting.*;
import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.*;
import com.sacco.mvp.reporting.execution.repository.ReportRunRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Short dispatch transaction is separate from the bounded coherent report snapshot. No money rows are locked. */
@Service
@RequiredArgsConstructor
public class ReportRunGenerator {
 private final ReportRunRepository repository;
 private final OperationalReportService reports;
 private final OperationalReportTemplateService templates;
 private final OperationalReportExportService exports;
 private final ReportWorkerControl control;
 private final ReportResultCodec codec;
 private final MemberDirectoryService members;
 private final ApplicationClock clock;
 private final AuditService audit;
 @Transactional(isolation=Isolation.REPEATABLE_READ,timeout=120)
 public void generate(Run run){
  var member=members.find(run.requester()).orElseThrow(()->new IllegalStateException("report.run.error.permission"));
  AppUserPrincipal actor=new AppUserPrincipal(member,Set.of(),true);
  if(!Objects.equals(member.getSaccoId(),run.institution())||!Objects.equals(member.getStationId(),run.branch()))throw new IllegalStateException("report.run.error.permission");
  control.authorize(run,actor);control.check(run.id(),run.workerToken());templates.get(run.templateVersion(),actor,true);
  long started=System.nanoTime();String snapshot=repository.snapshot();int bytes=0;StringBuilder checksums=new StringBuilder();
  List<Map<String,Object>> frozen=new ArrayList<>();OperationalReportService.Result first=null;
  for(int page=0;page<20;page++){
   control.check(run.id(),run.workerToken());control.authorize(run,actor);
   var result=reports.execute(run.definition(),actor,run.from(),run.through(),run.cutoff(),page,1000);
   if(first==null){first=result;if(result.rowsInScope()>20000)throw new IllegalArgumentException("report.run.error.rows");}
   if(result.rowsInScope()!=first.rowsInScope()||!result.totals().equals(first.totals()))throw new IllegalStateException("report.run.error.snapshot");
   String payload=codec.rows(result.rows());byte[] encoded=payload.getBytes(StandardCharsets.UTF_8);bytes+=encoded.length;
   if(bytes>8*1024*1024||encoded.length>1024*1024)throw new IllegalArgumentException("report.run.error.memory");
   control.check(run.id(),run.workerToken());
   String checksum=ReportRunService.sha256(encoded);
   try{repository.page(run.id(),page,result.rows().size(),payload,checksum);}
   catch(ConcurrencyFailureException ex){
    // A committed cancellation can invalidate the snapshot's FK lock on the run.
    // Read control in its independent transaction; otherwise preserve the retryable failure.
    control.check(run.id(),run.workerToken());throw ex;
   }
   checksums.append(checksum);
   frozen.addAll(result.rows());if(frozen.size()>=first.rowsInScope())break;
   if(System.nanoTime()-started>90_000_000_000L)throw new IllegalArgumentException("report.run.error.timeout");
  }
  if(first==null||frozen.size()!=first.rowsInScope())throw new IllegalStateException("report.run.error.snapshot");
  if(first.branding()!=null)repository.asset(run.id(),first.branding());
  var complete=new OperationalReportService.Result(run.definition(),run.institution(),run.branch(),run.from(),run.through(),run.cutoff(),first.rowsInScope(),first.untrackedLoans(),List.copyOf(frozen),first.totals(),0,Math.max(1,frozen.size()),false,first.coverageKey(),first.branding());
  for(var format:run.formats()){
   control.check(run.id(),run.workerToken());control.authorize(run,actor);
   if(System.nanoTime()-started>90_000_000_000L)throw new IllegalArgumentException("report.run.error.timeout");
   // Each format has a hard finite envelope; oversized runs fail visibly rather than exporting a partial result.
   if(format==OperationalReportExportService.Format.PDF&&frozen.size()>5000)throw new IllegalArgumentException("report.run.error.pdfRows");
   byte[] artifact=exports.export(complete,format);if(artifact.length>32*1024*1024)throw new IllegalArgumentException("report.run.error.memory");
   repository.artifact(run.id(),format,artifact,ReportRunService.sha256(artifact),clock.now());
  }
  if(System.nanoTime()-started>90_000_000_000L)throw new IllegalArgumentException("report.run.error.timeout");
  control.check(run.id(),run.workerToken());control.authorize(run,actor);
  String totals=codec.totals(first.totals());String checksum=ReportRunService.sha256((run.definition()+"|"+run.from()+"|"+run.through()+"|"+run.cutoff()+"|"+snapshot+"|"+first.coverageKey()+"|"+first.untrackedLoans()+"|"+totals+"|"+checksums+"|"+(first.branding()==null?"":first.branding().sha256())).getBytes(StandardCharsets.UTF_8));
  if(repository.ready(run.id(),run.workerToken(),first.rowsInScope(),first.untrackedLoans(),first.coverageKey(),totals,snapshot,checksum,clock.now())!=1)throw new IllegalStateException("report.run.error.cancelled");
  audit.log("REPORT_RUN",run.id(),"GENERATE",run.requester(),null,Map.of("saccoId",run.institution(),"stationId",run.branch(),"rows",first.rowsInScope(),"checksum",checksum));
 }
}
