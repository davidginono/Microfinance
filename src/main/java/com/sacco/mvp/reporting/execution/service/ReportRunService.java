package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.reporting.*;
import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.*;
import com.sacco.mvp.reporting.execution.repository.ReportRunRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportRunService {
 private final ReportRunRepository repository;
 private final OperationalReportService reports;
 private final OperationalReportTemplateService templates;
 private final AccessControlService access;
 private final ApplicationClock clock;
 private final AuditService audit;
 private final ReportResultCodec codec;
 @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
 public UUID request(AppUserPrincipal actor,Request request){
  actor=reports.currentActor(actor);
  if(request==null||request.requestKey()==null||request.templateVersion()==null||request.formats()==null||request.formats().isEmpty()||request.formats().size()>3||request.formats().stream().anyMatch(Objects::isNull))invalid();
  OperationalReportDefinition.validateDates(request.from(),request.through());
  if(request.through().isAfter(clock.today())||request.recordedCutoff()==null||request.recordedCutoff().isAfter(clock.now()))invalid();
  var version=templates.get(request.templateVersion(),actor,true);
  reports.authorize(actor,version.definition().dataset(),UserClaim.REPORT_RUN);
  reports.authorize(actor,version.definition().dataset(),UserClaim.REPORT_EXPORT);
  if(request.restates()!=null){
   var previous=scoped(request.restates(),actor,false);if(!previous.status().equals("APPROVED")||previous.definition().dataset()!=version.definition().dataset())invalid();
   OperationalReportDefinition.safeText(request.reason(),200,false);
  }else if(request.reason()!=null&&!request.reason().isBlank())invalid();
  String hash=sha256((request.templateVersion()+"|"+request.from()+"|"+request.through()+"|"+request.recordedCutoff().toInstant()+"|"+request.formats().stream().map(Enum::name).sorted().toList()+"|"+request.restates()+"|"+request.reason()).getBytes(StandardCharsets.UTF_8));
  repository.quotaLock(actor.getSaccoId());
  var duplicates=repository.duplicate(actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),request.requestKey());
  if(!duplicates.isEmpty()){if(!repository.requestHash(duplicates.getFirst().id()).equals(hash))throw new IllegalArgumentException("report.run.error.payload");return duplicates.getFirst().id();}
  if(repository.active(actor.getSaccoId(),null)>=4||repository.active(actor.getSaccoId(),actor.getMemberId())>=2)throw new IllegalArgumentException("report.run.error.quota");
  UUID id=UUID.randomUUID();repository.create(id,actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),request,hash,version.version(),version.definition(),clock.now());
  log(id,"QUEUE",actor);return id;
 }
 @Transactional(readOnly=true)
 public List<Run> list(AppUserPrincipal actor,int page){actor=reports.currentActor(actor);if(page<0||page>10000)invalid();if(!access.has(actor,UserClaim.REPORT_RUN))denied();return repository.list(actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),access.hasAny(actor,UserClaim.REPORT_JOBS_REVIEW,UserClaim.REPORT_RUN_APPROVE),access.has(actor,UserClaim.LOAN_REPAYMENTS_VIEW),access.has(actor,UserClaim.LOAN_REPORTS_VIEW),page);}
 @Transactional(readOnly=true)
 public Run get(UUID id,AppUserPrincipal actor){return scoped(id,actor,false);}
 @Transactional(readOnly=true)
 public List<Artifact> artifacts(UUID id,AppUserPrincipal actor){scoped(id,actor,false);return repository.artifacts(id);}
 @Transactional(readOnly=true)
 public OperationalReportService.Result frozen(UUID id,AppUserPrincipal actor,int page){
  Run run=scoped(id,actor,false);if(!Set.of("READY","APPROVED").contains(run.status())||page<0||page>800)invalid();
  int rowOffset=Math.multiplyExact(page,25);var pages=repository.pages(id,rowOffset/1000,1);List<Map<String,Object>> rows=List.of();
  if(!pages.isEmpty()){var chunk=pages.getFirst();if(!sha256(chunk.payload().getBytes(StandardCharsets.UTF_8)).equals(chunk.checksum()))throw new IllegalStateException("Report checksum verification failed");var values=codec.rows(chunk.payload(),run.definition());int first=rowOffset%1000;rows=first>=values.size()?List.of():values.subList(first,Math.min(first+25,values.size()));}
  var branding=repository.asset(id).orElse(null);if(branding!=null&&!sha256(branding.logo()).equals(branding.sha256()))throw new IllegalStateException("Report logo checksum verification failed");
  return new OperationalReportService.Result(run.definition(),run.institution(),run.branch(),run.from(),run.through(),run.cutoff(),run.rows(),run.untracked(),List.copyOf(rows),codec.totals(run.totals()),page,25,(long)(page+1)*25<run.rows(),run.coverage(),branding);
 }
 @Transactional
 public Download download(UUID id,UUID artifact,AppUserPrincipal actor){
  var run=scoped(id,actor,false);reports.authorize(actor,run.definition().dataset(),UserClaim.REPORT_EXPORT);
  if(!Set.of("READY","APPROVED").contains(run.status()))invalid();
  var result=repository.artifact(id,artifact).orElseThrow(()->new AccessDeniedException("Report artifact unavailable"));
  if(!sha256(result.bytes()).equals(result.checksum()))throw new IllegalStateException("Report checksum verification failed");
  log(id,"DOWNLOAD",actor);return result;
 }
 @Transactional
 public void cancel(UUID id,AppUserPrincipal actor){var run=scoped(id,actor,true);if(!run.requester().equals(actor.getMemberId())&&!access.has(reports.currentActor(actor),UserClaim.REPORT_JOBS_REVIEW))denied();if(repository.cancel(id)!=1)invalid();log(id,"CANCEL",actor);}
 @Transactional
 public void retry(UUID id,AppUserPrincipal actor){var run=scoped(id,actor,true);reports.authorize(actor,run.definition().dataset(),UserClaim.REPORT_EXPORT);if(!run.requester().equals(actor.getMemberId()))denied();if(repository.retry(id)!=1)throw new IllegalArgumentException("report.run.error.retry");log(id,"RETRY",actor);}
 @Transactional
 public void approve(UUID id,String evidence,AppUserPrincipal actor){
  actor=reports.currentActor(actor);var run=scoped(id,actor,true);reports.authorize(actor,run.definition().dataset(),UserClaim.REPORT_RUN_APPROVE);
  OperationalReportDefinition.safeText(evidence,200,false);
  if(!run.status().equals("READY")||run.requester().equals(actor.getMemberId()))throw new IllegalArgumentException("report.run.error.checker");
  // Approval records acceptance of the operational coverage shown. It never turns it into an audited financial statement.
  repository.approve(id,actor.getMemberId(),evidence,clock.now());log(id,"APPROVE",actor);
 }
 private Run scoped(UUID id,AppUserPrincipal actor,boolean lock){
  actor=reports.currentActor(actor);var matches=repository.scoped(id,actor.getSaccoId(),actor.getStationId(),lock);if(matches.size()!=1)denied();var run=matches.getFirst();
  if(!run.requester().equals(actor.getMemberId())&&!access.hasAny(actor,UserClaim.REPORT_JOBS_REVIEW,UserClaim.REPORT_RUN_APPROVE))denied();
  reports.authorize(actor,run.definition().dataset(),UserClaim.REPORT_RUN);return run;
 }
 @Transactional(readOnly=true) public boolean hasInstitutionHistory(String institution){return repository.hasInstitutionHistory(institution);}
 @Transactional(readOnly=true) public boolean hasMemberHistory(UUID member){return repository.hasMemberHistory(member);}
 private void log(UUID id,String action,AppUserPrincipal actor){audit.log("REPORT_RUN",id,action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId()));}
 public static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
 private static void invalid(){throw new IllegalArgumentException("report.run.error.invalid");}
 private static void denied(){throw new AccessDeniedException("Report run unavailable in the authorized branch");}
}
