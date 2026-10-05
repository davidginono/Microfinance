package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.accounting.reconciliation.ReconciliationService;
import com.sacco.mvp.accounting.statements.StatementDesignerService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.reporting.OperationalReportDefinition;
import com.sacco.mvp.reporting.OperationalReportService;
import com.sacco.mvp.reporting.execution.dto.AccountingReleaseDtos.*;
import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.Run;
import com.sacco.mvp.reporting.execution.repository.AccountingReleaseRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authoritative proposal/decision boundary. The separate posting gate remains a repository-only leaf. */
@Service @RequiredArgsConstructor
public class AccountingReleaseService {
 private final AccountingReleaseRepository repository;
 private final StatementOutputService outputs;
 private final StatementDesignerService statements;
 private final ReconciliationService closing;
 private final AccountingPolicyService policies;
 private final ReportRunService runs;
 private final OperationalReportService actors;
 private final AccessControlService access;
 private final ApplicationClock clock;
 private final AuditService audit;
 private final ObjectMapper mapper;
 private static final tools.jackson.databind.json.JsonMapper SOURCE_JSON=tools.jackson.databind.json.JsonMapper.builder().enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).findAndAddModules().build();
 @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=30)
 public UUID propose(AppUserPrincipal actor,Proposal proposal){
  actor=authorize(actor,UserClaim.ACCOUNTING_RELEASE_REQUEST);if(proposal==null||proposal.requestKey()==null||proposal.statementOutput()==null||proposal.firstSample()==null||proposal.secondSample()==null||proposal.firstSample().equals(proposal.secondSample()))invalid("source");OperationalReportDefinition.safeText(proposal.evidence(),1000,false);
  var receipt=receipt(actor,proposal.statementOutput(),proposal.firstSample(),proposal.secondSample());
  repository.requestLock(actor.getSaccoId(),actor.getStationId());
  var duplicate=repository.duplicate(actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),proposal.requestKey());
  if(duplicate.isPresent()){var existing=scoped(actor,duplicate.get(),false);if(!existing.statementOutput().equals(proposal.statementOutput())||!existing.firstSample().equals(proposal.firstSample())||!existing.secondSample().equals(proposal.secondSample())||!existing.evidence().equals(proposal.evidence())||!existing.checksum().equals(receipt.checksum()))invalid("retry");return existing.id();}
  UUID id=UUID.randomUUID();repository.create(new Release(id,actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),clock.now(),receipt.policy(),receipt.policyVersion(),receipt.period(),receipt.mapping(),receipt.template(),proposal.statementOutput(),proposal.firstSample(),proposal.secondSample(),receipt.json(),receipt.checksum(),proposal.evidence(),List.of(),false,2),proposal.requestKey());repository.createSources(id,actor.getSaccoId(),receipt.sources());event(actor,id,"PROPOSED");return id;
 }
 @Transactional(readOnly=true) public List<ReleaseSummary> list(AppUserPrincipal actor,int page){actor=authorize(actor,UserClaim.ACCOUNTING_RELEASE_VIEW);if(page<0||page>10000)invalid("page");return repository.list(actor.getSaccoId(),actor.getStationId(),page,access.has(actor,UserClaim.FINANCIAL_REPORTS_VIEW)&&access.has(actor,UserClaim.FINANCIAL_REPORTS_INSTITUTION));}
 @Transactional(readOnly=true) public Release get(AppUserPrincipal actor,UUID id){actor=authorize(actor,UserClaim.ACCOUNTING_RELEASE_VIEW);return scoped(actor,id,false);}
 public record Display(Release release,Map<String,Object> dependencies){}
 @Transactional(readOnly=true) public Display display(AppUserPrincipal actor,UUID id){actor=authorize(actor,UserClaim.ACCOUNTING_RELEASE_VIEW);var release=scoped(actor,id,false);var receipt=SOURCE_JSON.readValue(release.dependencyJson(),Map.class);return new Display(release,com.sacco.mvp.reporting.execution.dto.AccountingReleaseDisplay.project(receipt,actor.getStationId(),access.has(actor,UserClaim.ACCOUNTING_CLOSING_VIEW)));}

 @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=30)
 public void decide(AppUserPrincipal actor,UUID id,Stage stage,boolean approved,String evidence){
  if(stage==null)invalid("stage");actor=authorize(actor,switch(stage){case ACCOUNTANT->UserClaim.ACCOUNTING_RELEASE_APPROVE;case COMPLIANCE->UserClaim.ACCOUNTING_COMPLIANCE_RELEASE_APPROVE;case STAFF->UserClaim.ACCOUNTING_RELEASE_ACCEPT;});OperationalReportDefinition.safeText(evidence,1000,false);
  // Resolve period/mapping publication locks before the release row, matching reopen/mapping invalidation order.
  var before=scoped(actor,id,false);var current=receipt(actor,before.statementOutput(),before.firstSample(),before.secondSample());var release=scoped(actor,id,true);
  if(!current.checksum().equals(release.checksum()))invalid("stale");if(!release.getState().equals("PENDING"))invalid("state");
  UUID reviewer=actor.getMemberId();if(release.requester().equals(reviewer)||release.decisions().stream().anyMatch(d->d.reviewer().equals(reviewer)))invalid("checker");
  if(release.decisions().stream().anyMatch(d->d.stage()==stage)||stage==Stage.COMPLIANCE&&release.decisions().stream().noneMatch(d->d.stage()==Stage.ACCOUNTANT&&d.approved())||stage==Stage.STAFF&&release.decisions().size()!=2)invalid("stage");
  repository.decide(id,stage,approved,actor.getMemberId(),evidence,clock.now());event(actor,id,stage.name()+"_"+(approved?"APPROVED":"REJECTED"));
 }
 @Transactional
 public void withdraw(AppUserPrincipal actor,UUID id,String reason){actor=authorize(actor,UserClaim.ACCOUNTING_RELEASE_APPROVE);OperationalReportDefinition.safeText(reason,1000,false);scoped(actor,id,true);repository.withdraw(id,actor.getMemberId(),reason,clock.now());event(actor,id,"WITHDRAWN");}
 private record Receipt(UUID policy,int policyVersion,UUID period,UUID mapping,UUID template,String json,String checksum,List<com.sacco.mvp.reporting.execution.dto.AccountingReleaseSourceProof.Proof> sources) { }
 private Receipt receipt(AppUserPrincipal actor,UUID outputId,UUID firstId,UUID secondId){
  var output=outputs.get(actor,outputId);if(output.reviewer()==null||output.reviewer().equals(output.generatedBy()))invalid("outputReview");
  var result=statements.verifiedResultForPublication(actor,output.resultId());if(!statements.verifiedResultDigest(actor,result.id()).equals(output.sourceChecksum()))invalid("source");
  var current=sourceProof(actor,result,false);var comparison=hasComparison(result)?sourceProof(actor,result,true):null;
  var mapping=statements.verifiedVersionForPublication(actor,result.versionId());if(mapping.version()!=result.mappingVersion()||!mapping.reviewer().equals(result.mappingReviewer()))invalid("source");
  var policy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),result.through());var livePolicy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),clock.today());if(!policy.id().equals(result.policyId())||policy.version()!=result.policyVersion()||!livePolicy.id().equals(policy.id()))invalid("policy");
  var issuer=current.branches().stream().filter(b->b.branch().equals(actor.getStationId())).findFirst().orElseThrow(()->new IllegalArgumentException("accounting.release.error.source"));
  var first=runs.get(firstId,actor);var second=runs.get(secondId,actor);checkSample(first,result.from(),result.through(),issuer.recordedCutoff());checkSample(second,result.from(),result.through(),issuer.recordedCutoff());String firstLayout=sampleLayout(first),secondLayout=sampleLayout(second);if(firstLayout.equals(secondLayout))invalid("layouts");
  var artifacts=outputs.verifiedArtifacts(actor,outputId);if(artifacts.size()!=3)invalid("source");
  var evidence=new LinkedHashMap<String,Object>();evidence.put("schemaVersion",2);evidence.put("institution",actor.getSaccoId());evidence.put("branch",actor.getStationId());evidence.put("dimension",result.dimension().name());evidence.put("policy",policy);evidence.put("currentSource",current);evidence.put("comparisonSource",comparison);evidence.put("mapping",mapping);evidence.put("statementResult",result.id());evidence.put("statementSourceChecksum",output.sourceChecksum());evidence.put("statementOutput",output.id());evidence.put("statementLayoutChecksum",output.layoutChecksum());evidence.put("statementFontChecksum",output.fontChecksum());evidence.put("statementOutputReviewer",output.reviewer());evidence.put("statementOutputReviewEvidence",output.reviewEvidence());evidence.put("statementArtifacts",artifacts);evidence.put("mandatoryDisclosures",result.disclosures());evidence.put("firstSample",sample(first,firstLayout));evidence.put("secondSample",sample(second,secondLayout));String json=mapper.writeValueAsString(evidence);if(json.getBytes(StandardCharsets.UTF_8).length>8*1024*1024)invalid("size");var sources=comparison==null?List.of(current):List.of(current,comparison);return new Receipt(policy.id(),policy.version(),current.period(),mapping.id(),mapping.templateId(),json,ReportRunService.sha256(json.getBytes(StandardCharsets.UTF_8)),sources);
 }
 private boolean hasComparison(StatementDesignerService.Result result){return result.dimension()==com.sacco.mvp.accounting.statements.RegulatoryFormatCatalog.Scope.INSTITUTION?result.comparisonInstitutionPeriodId()!=null:result.comparisonCloseId()!=null;}
 private com.sacco.mvp.reporting.execution.dto.AccountingReleaseSourceProof.Proof sourceProof(AppUserPrincipal actor,StatementDesignerService.Result result,boolean comparison){
  String role=comparison?"COMPARISON":"CURRENT";var from=comparison?result.comparisonFrom():result.from();var through=comparison?result.comparisonThrough():result.through();
  if(result.dimension()==com.sacco.mvp.accounting.statements.RegulatoryFormatCatalog.Scope.INSTITUTION){
   var period=comparison?result.comparisonInstitutionPeriodId():result.institutionPeriodId();var expectedChecksum=comparison?result.comparisonInstitutionSourceChecksum():result.institutionSourceChecksum();var expectedBranches=comparison?result.comparisonBranchSources():result.currentBranchSources();var source=closing.finalizedInstitutionSnapshotForPublication(actor,period);
   var branches=source.branches().stream().map(b->new StatementDesignerService.BranchSource(b.branch(),b.reviewId(),b.version(),b.recordedCutoff(),b.checksum(),b.reviewer())).toList();
   if(!source.checksum().equals(expectedChecksum)||!source.from().equals(from)||!source.through().equals(through)||!branches.equals(expectedBranches))invalid("source");
   return com.sacco.mvp.reporting.execution.dto.AccountingReleaseSourceProof.from(role,"INSTITUTION",source.period(),from,through,source.checksum(),branches,SOURCE_JSON.readValue(source.snapshot(),Map.class));
  }
  var id=comparison?result.comparisonCloseId():result.closeReviewId();var expected=comparison?result.comparisonCloseChecksum():result.closeChecksum();var cutoff=comparison?result.comparisonCutoff():result.recordedCutoff();var source=closing.finalizedSnapshotForPublication(actor,id);
  if(!source.checksum().equals(expected)||!source.asOf().equals(through)||!source.recordedCutoff().isEqual(cutoff))invalid("source");
  var branches=List.of(new StatementDesignerService.BranchSource(result.branch(),source.id(),source.version(),source.recordedCutoff(),source.checksum(),source.reviewer()));
  return com.sacco.mvp.reporting.execution.dto.AccountingReleaseSourceProof.from(role,"BRANCH",source.period(),from,through,source.checksum(),branches,SOURCE_JSON.readValue(source.snapshot(),Map.class));
 }
 private void checkSample(Run sample,java.time.LocalDate from,java.time.LocalDate through,java.time.OffsetDateTime cutoff){if(!sample.status().equals("APPROVED")||sample.approvedBy()==null||sample.approvedBy().equals(sample.requester())||sample.untracked()!=0||!sample.from().equals(from)||!sample.through().equals(through)||!sample.cutoff().isEqual(cutoff))invalid("samples");}
 private String sampleLayout(Run run){var d=run.definition();return ReportRunService.sha256(mapper.writeValueAsString(List.of(d.landscape(),d.columns(),d.groups(),d.totals(),d.institutionLogo())).getBytes(StandardCharsets.UTF_8));}
 private Map<String,Object> sample(Run run,String layout){return Map.of("run",run.id(),"templateVersionId",run.templateVersion(),"templateVersion",run.version(),"checksum",run.checksum(),"layoutChecksum",layout,"coverage",run.coverage(),"untracked",run.untracked(),"reviewer",run.approvedBy(),"reviewEvidence",run.approvalEvidence());}
 private AppUserPrincipal authorize(AppUserPrincipal actor,UserClaim permission){actor=actors.currentActor(actor);if(!access.has(actor,permission))throw new AccessDeniedException("Explicit institution release permission required");return actor;}
 private Release scoped(AppUserPrincipal actor,UUID id,boolean lock){var release=repository.get(id,actor.getSaccoId(),actor.getStationId(),lock).orElseThrow(()->new AccessDeniedException("Institution release unavailable in branch"));if(!ReportRunService.sha256(release.dependencyJson().getBytes(StandardCharsets.UTF_8)).equals(release.checksum()))throw new IllegalStateException("Release evidence checksum failed");var dependencies=SOURCE_JSON.readValue(release.dependencyJson(),Map.class);if("INSTITUTION".equals(dependencies.get("dimension"))&&(!access.has(actor,UserClaim.FINANCIAL_REPORTS_VIEW)||!access.has(actor,UserClaim.FINANCIAL_REPORTS_INSTITUTION)))throw new AccessDeniedException("Explicit institution financial scope required");return release;}
 private void event(AppUserPrincipal actor,UUID id,String action){audit.log("ACCOUNTING_RELEASE",id,action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId()));}
 private static void invalid(String key){throw new IllegalArgumentException("accounting.release.error."+key);}
}
