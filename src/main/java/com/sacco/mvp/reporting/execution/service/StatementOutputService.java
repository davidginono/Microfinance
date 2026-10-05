package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.accounting.statements.StatementDesignerService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.reporting.*;
import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.*;
import com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.*;
import com.sacco.mvp.reporting.execution.repository.StatementOutputRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class StatementOutputService {
 private final StatementOutputRepository repository;
 private final StatementDesignerService statements;
 private final StatementTypedExporter exporter;
 private final OperationalReportService actors;
 private final AccessControlService access;
 private final SaccoLogoStorageService logos;
 private final ReportExportLimiter limiter;
 private final ApplicationClock clock;
 private final AuditService audit;
 private final ObjectMapper mapper;
 @Transactional(timeout=30)
 public UUID capture(AppUserPrincipal actor,UUID resultId,Layout layout){
  actor=statements.authorizeExport(actor);layout.validate();var result=statements.verifiedResult(actor,resultId);String source=statements.verifiedResultDigest(actor,resultId);
  if(!"FINAL".equals(result.status())||result.mappingReviewer()==null||!Objects.equals(actor.getStationId(),result.branch()))invalid("source");
  if(result.dimension()==com.sacco.mvp.accounting.statements.RegulatoryFormatCatalog.Scope.INSTITUTION){if(result.institutionPeriodId()==null||result.institutionSourceChecksum()==null||result.currentBranchSources().isEmpty())invalid("source");}
  else if(result.closeReviewId()==null||result.closeChecksum()==null)invalid("source");
  OperationalReportService.Branding branding=null;
  if(layout.institutionLogo()){if(!logos.hasLogo(actor.getSaccoId()))invalid("logo");var logo=logos.load(actor.getSaccoId());byte[] bytes=logo.content();String type=logo.contentType().toString();if(bytes.length<1||bytes.length>5_000_000||!Set.of("image/png","image/jpeg").contains(type))invalid("logo");branding=new OperationalReportService.Branding(bytes,type,ReportRunService.sha256(bytes));}
  String checksum=layoutChecksum(layout,exporter.fontChecksum(),branding);
  repository.lock(actor.getSaccoId(),actor.getStationId(),resultId);
  var existing=repository.duplicate(actor.getSaccoId(),actor.getStationId(),resultId,checksum);if(existing.isPresent())return existing.get();
  UUID id=UUID.randomUUID();Output output=new Output(id,actor.getSaccoId(),actor.getStationId(),resultId,source,result,layout,checksum,exporter.fontChecksum(),branding,actor.getMemberId(),clock.now(),null,null,null);
  if(!limiter.tryAcquire())invalid("busy");
  try{
   repository.create(output);
   for(var format:OperationalReportExportService.Format.values()){statements.authorizeExport(actor);byte[] bytes=exporter.export(result,layout,branding,format);if(bytes.length>32*1024*1024)invalid("size");repository.artifact(id,format,bytes,ReportRunService.sha256(bytes));}
   statements.authorizeExport(actor);event(actor,id,"CAPTURED");return id;
  }finally{limiter.release();}
 }
 @Transactional(readOnly=true)
 public Output get(AppUserPrincipal actor,UUID id){return get(actor,id,false);}
 @Transactional(readOnly=true)
 public List<Summary> list(AppUserPrincipal actor,int page){actor=authorizeView(actor);if(page<0||page>10000)invalid("page");return repository.list(actor.getSaccoId(),actor.getStationId(),page,access.has(actor,UserClaim.FINANCIAL_REPORTS_VIEW)&&access.has(actor,UserClaim.FINANCIAL_REPORTS_INSTITUTION));}
 @Transactional(readOnly=true)
 public List<Artifact> artifacts(AppUserPrincipal actor,UUID id){get(actor,id,false);return repository.artifacts(id);}
 @Transactional(readOnly=true)
 public List<Artifact> verifiedArtifacts(AppUserPrincipal actor,UUID id){get(actor,id,false);var artifacts=repository.artifacts(id);for(var artifact:artifacts){var bytes=repository.download(id,artifact.id()).orElseThrow();if(!ReportRunService.sha256(bytes.bytes()).equals(bytes.checksum()))throw new IllegalStateException("Statement artifact checksum failed");}return artifacts;}
 @Transactional
 public void review(AppUserPrincipal actor,UUID id,String evidence){actor=statements.authorizeExport(actor);if(!access.has(actor,UserClaim.REPORT_RUN_APPROVE))throw new AccessDeniedException("Independent output review required");OperationalReportDefinition.safeText(evidence,1000,false);var output=get(actor,id,true);if(output.generatedBy().equals(actor.getMemberId())||output.reviewer()!=null)invalid("checker");repository.review(id,actor.getMemberId(),evidence,clock.now());event(actor,id,"REVIEWED");}
 @Transactional
 public Download download(AppUserPrincipal actor,UUID id,UUID artifact){actor=statements.authorizeExport(actor);get(actor,id,false);var data=repository.download(id,artifact).orElseThrow(()->new AccessDeniedException("Statement artifact unavailable"));if(!ReportRunService.sha256(data.bytes()).equals(data.checksum()))throw new IllegalStateException("Statement artifact checksum failed");event(actor,id,"DOWNLOADED");return data;}
 private Output get(AppUserPrincipal actor,UUID id,boolean lock){actor=authorizeView(actor);var output=repository.get(id,actor.getSaccoId(),actor.getStationId(),lock).orElseThrow(()->new AccessDeniedException("Statement output unavailable in branch"));var retained=statements.verifiedResult(actor,output.resultId());if(!statements.verifiedResultDigest(actor,output.resultId()).equals(output.sourceChecksum())||retained.dimension()!=output.result().dimension())throw new IllegalStateException("Statement source checksum or scope failed");if(!layoutChecksum(output.layout(),output.fontChecksum(),output.branding()).equals(output.layoutChecksum()))throw new IllegalStateException("Statement layout checksum failed");if(output.branding()!=null&&!ReportRunService.sha256(output.branding().logo()).equals(output.branding().sha256()))throw new IllegalStateException("Statement logo checksum failed");return output;}
 private AppUserPrincipal authorizeView(AppUserPrincipal actor){actor=actors.currentActor(actor);if(!access.has(actor,UserClaim.STATEMENT_VIEW))throw new AccessDeniedException("Statement view required");return actor;}
 private String layoutChecksum(Layout layout,String font,OperationalReportService.Branding logo){return ReportRunService.sha256((mapper.writeValueAsString(layout)+"|"+font+"|"+(logo==null?"":logo.sha256())).getBytes(StandardCharsets.UTF_8));}
 private void event(AppUserPrincipal actor,UUID id,String action){audit.log("STATEMENT_OUTPUT",id,action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId()));}
 private static void invalid(String key){throw new IllegalArgumentException("accounting.release.error."+key);}
}
