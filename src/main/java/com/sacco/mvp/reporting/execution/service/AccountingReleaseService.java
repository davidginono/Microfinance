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
 @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=30)
 public UUID propose(AppUserPrincipal actor,Proposal proposal){
  actor=authorize(actor,UserClaim.ACCOUNTING_RELEASE_REQUEST);if(proposal==null||proposal.requestKey()==null||proposal.statementOutput()==null||proposal.firstSample()==null||proposal.secondSample()==null||proposal.firstSample().equals(proposal.secondSample()))invalid("source");OperationalReportDefinition.safeText(proposal.evidence(),1000,false);
  var receipt=receipt(actor,proposal.statementOutput(),proposal.firstSample(),proposal.secondSample());
  repository.requestLock(actor.getSaccoId(),actor.getStationId());
  var duplicate=repository.duplicate(actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),proposal.requestKey());
  if(duplicate.isPresent()){var existing=scoped(actor,duplicate.get(),false);if(!existing.statementOutput().equals(proposal.statementOutput())||!existing.firstSample().equals(proposal.firstSample())||!existing.secondSample().equals(proposal.secondSample())||!existing.evidence().equals(proposal.evidence())||!existing.checksum().equals(receipt.checksum()))invalid("retry");return existing.id();}
  UUID id=UUID.randomUUID();repository.create(new Release(id,actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),clock.now(),receipt.policy(),receipt.policyVersion(),receipt.period(),receipt.mapping(),receipt.template(),proposal.statementOutput(),proposal.firstSample(),proposal.secondSample(),receipt.json(),receipt.checksum(),proposal.evidence(),List.of(),false),proposal.requestKey());event(actor,id,"PROPOSED");return id;
 }
 @Transactional(readOnly=true) public List<Release> list(AppUserPrincipal actor,int page){actor=authorize(actor,UserClaim.ACCOUNTING_RELEASE_VIEW);if(page<0||page>10000)invalid("page");return repository.list(actor.getSaccoId(),actor.getStationId(),page);}
 @Transactional(readOnly=true) public Release get(AppUserPrincipal actor,UUID id){actor=authorize(actor,UserClaim.ACCOUNTING_RELEASE_VIEW);return scoped(actor,id,false);}
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
 private record Receipt(UUID policy,int policyVersion,UUID period,UUID mapping,UUID template,String json,String checksum) { }
 private Receipt receipt(AppUserPrincipal actor,UUID outputId,UUID firstId,UUID secondId){
  var output=outputs.get(actor,outputId);if(output.reviewer()==null||output.reviewer().equals(output.generatedBy()))invalid("outputReview");
  var result=statements.verifiedResult(actor,output.resultId());if(!statements.verifiedResultDigest(actor,result.id()).equals(output.sourceChecksum()))invalid("source");
  var close=closing.finalizedSnapshotForPublication(actor,result.closeReviewId());if(!close.checksum().equals(result.closeChecksum())||!close.asOf().equals(result.through())||!close.recordedCutoff().isEqual(result.recordedCutoff()))invalid("source");
  if(result.comparisonCloseId()!=null){var comparison=closing.finalizedSnapshotForPublication(actor,result.comparisonCloseId());if(!comparison.checksum().equals(result.comparisonCloseChecksum()))invalid("source");}
  var mapping=statements.verifiedVersionForPublication(actor,result.versionId());if(mapping.version()!=result.mappingVersion()||!mapping.reviewer().equals(result.mappingReviewer()))invalid("source");
  var policy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),result.through());var livePolicy=policies.requireApprovedLocalPolicy(actor.getSaccoId(),clock.today());if(!policy.id().equals(result.policyId())||policy.version()!=result.policyVersion()||!livePolicy.id().equals(policy.id()))invalid("policy");
  var opening=closing.reviewedOpeningEvidence(actor);if(opening.maker().equals(opening.reviewer()))invalid("source");
  Map<?,?> snapshot=mapper.readValue(close.snapshot(),Map.class);if(!(snapshot.get("reviewedOpening") instanceof Map<?,?> frozen)||!opening.id().toString().equals(Objects.toString(frozen.get("id")))||!opening.payloadChecksum().equals(frozen.get("payloadChecksum")))invalid("source");
  var differences=new ArrayList<Object>();if(snapshot.get("retainedDifferences") instanceof List<?> retained)differences.addAll(retained);if(snapshot.get("certificates") instanceof List<?> certificates)for(var value:certificates){if(!(value instanceof Map<?,?>))invalid("source");var certificate=(Map<?,?>)value;String amount=Objects.toString(certificate.get("difference"),"");if(amount.isBlank())invalid("source");if(new java.math.BigDecimal(amount).signum()!=0){if(certificate.get("checker_id")==null||Objects.toString(certificate.get("evidence"),"").isBlank())invalid("source");differences.add(certificate);}}
  var first=runs.get(firstId,actor);var second=runs.get(secondId,actor);checkSample(first,result.from(),result.through(),result.recordedCutoff());checkSample(second,result.from(),result.through(),result.recordedCutoff());String firstLayout=sampleLayout(first),secondLayout=sampleLayout(second);if(firstLayout.equals(secondLayout))invalid("layouts");
  var artifacts=outputs.verifiedArtifacts(actor,outputId);if(artifacts.size()!=3)invalid("source");
  var evidence=new LinkedHashMap<String,Object>();evidence.put("schemaVersion",1);evidence.put("institution",actor.getSaccoId());evidence.put("branch",actor.getStationId());evidence.put("policy",policy);evidence.put("opening",opening);evidence.put("closeId",close.id());evidence.put("closeVersion",close.version());evidence.put("closeChecksum",close.checksum());evidence.put("period",close.period());evidence.put("reviewedDifferences",differences);evidence.put("comparisonClose",result.comparisonCloseId());evidence.put("comparisonChecksum",result.comparisonCloseChecksum());evidence.put("mapping",mapping);evidence.put("statementResult",result.id());evidence.put("statementSourceChecksum",output.sourceChecksum());evidence.put("statementOutput",output.id());evidence.put("statementLayoutChecksum",output.layoutChecksum());evidence.put("statementFontChecksum",output.fontChecksum());evidence.put("statementOutputReviewer",output.reviewer());evidence.put("statementOutputReviewEvidence",output.reviewEvidence());evidence.put("statementArtifacts",artifacts);evidence.put("mandatoryDisclosures",result.disclosures());evidence.put("firstSample",sample(first,firstLayout));evidence.put("secondSample",sample(second,secondLayout));String json=mapper.writeValueAsString(evidence);if(json.getBytes(StandardCharsets.UTF_8).length>64000)invalid("size");return new Receipt(policy.id(),policy.version(),close.period(),mapping.id(),mapping.templateId(),json,ReportRunService.sha256(json.getBytes(StandardCharsets.UTF_8)));
 }
 private void checkSample(Run sample,java.time.LocalDate from,java.time.LocalDate through,java.time.OffsetDateTime cutoff){if(!sample.status().equals("APPROVED")||sample.approvedBy()==null||sample.approvedBy().equals(sample.requester())||sample.untracked()!=0||!sample.from().equals(from)||!sample.through().equals(through)||!sample.cutoff().isEqual(cutoff))invalid("samples");}
 private String sampleLayout(Run run){var d=run.definition();return ReportRunService.sha256(mapper.writeValueAsString(List.of(d.landscape(),d.columns(),d.groups(),d.totals(),d.institutionLogo())).getBytes(StandardCharsets.UTF_8));}
 private Map<String,Object> sample(Run run,String layout){return Map.of("run",run.id(),"templateVersionId",run.templateVersion(),"templateVersion",run.version(),"checksum",run.checksum(),"layoutChecksum",layout,"coverage",run.coverage(),"untracked",run.untracked(),"reviewer",run.approvedBy(),"reviewEvidence",run.approvalEvidence());}
 private AppUserPrincipal authorize(AppUserPrincipal actor,UserClaim permission){actor=actors.currentActor(actor);if(!access.has(actor,permission))throw new AccessDeniedException("Explicit institution release permission required");return actor;}
 private Release scoped(AppUserPrincipal actor,UUID id,boolean lock){var release=repository.get(id,actor.getSaccoId(),actor.getStationId(),lock).orElseThrow(()->new AccessDeniedException("Institution release unavailable in branch"));if(!ReportRunService.sha256(release.dependencyJson().getBytes(StandardCharsets.UTF_8)).equals(release.checksum()))throw new IllegalStateException("Release evidence checksum failed");return release;}
 private void event(AppUserPrincipal actor,UUID id,String action){audit.log("ACCOUNTING_RELEASE",id,action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId()));}
 private static void invalid(String key){throw new IllegalArgumentException("accounting.release.error."+key);}
}
