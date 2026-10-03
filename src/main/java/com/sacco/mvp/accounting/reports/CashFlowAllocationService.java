package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.*;
import static com.sacco.mvp.accounting.reports.CashFlowAllocation.*;

@Service @RequiredArgsConstructor
public class CashFlowAllocationService {
    private final CashFlowAllocationRepository repository;
    private final LedgerReportService reports;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final List<CashFlowApprovalListener> approvalListeners;
    public void authorizeCreate(AppUserPrincipal actor){reports.authorizeAllocation(actor,UserClaim.ACCOUNTING_CASH_FLOW_CREATE);}
    @Transactional(timeout=20) public UUID draft(AppUserPrincipal actor,UUID journal,UUID request,List<Split> splits,String evidence,String noncashEvidence) {
        var scope=reports.authorizeAllocation(actor,UserClaim.ACCOUNTING_CASH_FLOW_CREATE);text(evidence,true);text(noncashEvidence,false);require(journal!=null && request!=null,"source");
        repository.lockSource(scope.institution(),scope.branch(),journal);Source source=repository.source(scope.institution(),scope.branch(),journal);validate(source,splits,noncashEvidence);
        splits=splits.stream().map(s->new Split(s.moneyLineId(),s.counterpartAccountId(),s.activity(),s.signedAmount().setScale(2,java.math.RoundingMode.UNNECESSARY))).sorted(Comparator.comparing(Split::moneyLineId).thenComparing(Split::counterpartAccountId).thenComparing(Split::activity)).toList();
        String sourceHash=hash(mapper.writeValueAsString(source)),definitionHash=hash(mapper.writeValueAsString(splits));var prior=repository.retry(scope.institution(),scope.branch(),request);
        if(prior.isPresent()){var p=prior.get();require(p.maker().equals(actor.getMemberId()) && p.journalId().equals(journal) && p.sourceChecksum().equals(sourceHash) && p.definitionChecksum().equals(definitionHash) && p.evidence().equals(evidence) && Objects.equals(p.noncashEvidence(),noncashEvidence),"changedRetry");return p.id();}
        UUID id=repository.save(scope.institution(),scope.branch(),request,actor.getMemberId(),clock.now(),evidence,noncashEvidence,source,splits,sourceHash,definitionHash);event(actor,id,"DRAFTED");return id;
    }
    @Transactional(timeout=20) public void approve(AppUserPrincipal actor,UUID id,String evidence) {
        var scope=reports.authorizeAllocation(actor,UserClaim.ACCOUNTING_CASH_FLOW_APPROVE);text(evidence,true);writableTransaction();
        var candidate=repository.version(scope.institution(),scope.branch(),id,false);
        repository.lockReviewSource(scope.institution(),scope.branch(),candidate.journalId());var v=repository.version(scope.institution(),scope.branch(),id,true);
        require(!v.maker().equals(actor.getMemberId()) && !v.approved(),"checker");verify(v);Source source=repository.source(scope.institution(),scope.branch(),v.journalId());require(hash(mapper.writeValueAsString(source)).equals(v.sourceChecksum()),"source");
        require(v.version()>repository.latestApprovedVersion(scope.institution(),scope.branch(),v.journalId()),"superseded");
        var now=clock.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);repository.approve(scope.institution(),scope.branch(),id,actor.getMemberId(),evidence,now);
        approvalListeners.forEach(listener->listener.approved(scope.institution(),v.journalId(),id,v.version(),actor.getMemberId(),now));event(actor,id,"APPROVED");
    }
    @Transactional(readOnly=true) public Source source(AppUserPrincipal actor,UUID journal){var scope=reports.authorizeAllocation(actor,null);return repository.source(scope.institution(),scope.branch(),journal);}
    @Transactional(readOnly=true) public List<Version> versions(AppUserPrincipal actor,int page){var scope=reports.authorizeAllocation(actor,null);require(page>=0 && page<=10000,"size");return repository.list(scope.institution(),scope.branch(),page);}
    @Transactional(readOnly=true) public List<CashFlowAllocationRepository.JournalChoice> journalChoices(AppUserPrincipal actor,int page){var scope=reports.authorizeAllocation(actor,null);require(page>=0 && page<=10000,"size");return repository.journalChoices(scope.institution(),scope.branch(),page);}
    /** Caller freezes these actual versions in its own close snapshot. Missing review is explicit coverage. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=20)
    public Coverage reviewedAllocations(AppUserPrincipal actor,List<UUID> journals,OffsetDateTime cutoff) {
        return reviewed(reports.authorizeAllocation(actor,null),journals,cutoff);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Coverage reviewedForClosing(AppUserPrincipal actor,List<UUID> journals,OffsetDateTime cutoff) {
        var scope=reports.authorizeClosingProof(actor,actor==null?null:actor.getSaccoId(),actor==null?null:actor.getStationId(),false);writableTransaction();
        repository.lockProofSources(scope.institution(),scope.branch(),journals);var proof=reviewed(scope,journals,cutoff);
        require(currentIdentifiers(scope,journals,proof),"superseded");return canonical(proof);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public boolean currentForInstitutionClosingProof(AppUserPrincipal actor,String institution,String branch,java.time.LocalDate from,java.time.LocalDate through,OffsetDateTime recordedCutoff,Coverage frozen) {
        var scope=reports.authorizeClosingProof(actor,institution,branch,true);
        writableTransaction();
        if(from==null || through==null || through.isBefore(from) || through.isAfter(from.plusDays(366)) || through.isAfter(clock.today()) || recordedCutoff==null || recordedCutoff.isAfter(clock.now()) || frozen==null)return false;
        repository.lockProofPeriodRange(institution,from,through);
        var now=clock.now();var ids=repository.qualifying(institution,branch,from,through,now);if(ids.size()>1000)return false;
        repository.lockProofSources(institution,branch,ids);
        var current=reviewed(scope,ids,clock.now());return currentIdentifiers(scope,ids,current) && canonical(current).equals(canonical(frozen));
    }
    private Coverage reviewed(LedgerReportService.Scope scope,List<UUID> journals,OffsetDateTime cutoff) {
        require(journals!=null && journals.size()<=1000 && journals.stream().allMatch(Objects::nonNull) && cutoff!=null && !cutoff.isAfter(clock.now()),"size");
        var ids=journals.stream().distinct().sorted().toList();if(!repository.allSourcesAvailable(scope.institution(),scope.branch(),cutoff,ids))throw new org.springframework.security.access.AccessDeniedException("Posted cash flow sources outside cutoff or scope");
        var versions=repository.approved(scope.institution(),scope.branch(),cutoff,ids);versions.forEach(this::verify);
        var actual=repository.sources(scope.institution(),scope.branch(),cutoff,versions.stream().map(Version::journalId).toList());
        versions.forEach(v->require(hash(mapper.writeValueAsString(actual.get(v.journalId()))).equals(v.sourceChecksum()),"source"));
        var covered=new HashSet<UUID>();versions.forEach(v->covered.add(v.journalId()));
        return new Coverage(versions,ids.stream().filter(id->!covered.contains(id)).toList());
    }
    private static Coverage canonical(Coverage proof) {
        require(proof.versions().size()<=1000 && proof.missingJournalIds().size()<=1000,"size");
        long count=proof.versions().stream().mapToLong(v->(long)v.source().lines().size()+v.splits().size()).sum();require(count<=10000,"size");
        var versions=proof.versions().stream().map(v->new Version(v.id(),v.journalId(),v.version(),v.maker(),utc(v.madeAt()),v.evidence(),v.noncashEvidence(),v.sourceChecksum(),v.definitionChecksum(),
            new Source(v.source().journalId(),v.source().policyId(),v.source().policyVersion(),v.source().sourceReference(),v.source().lines().stream().map(l->new SourceLine(l.id(),l.accountId(),l.code(),l.type(),l.purpose(),l.signedAmount().setScale(2))).sorted(Comparator.comparing(l->l.id().toString())).toList()),
            v.splits().stream().map(s->new Split(s.moneyLineId(),s.counterpartAccountId(),s.activity(),s.signedAmount().setScale(2))).sorted(Comparator.comparing(s->s.moneyLineId()+"/"+s.counterpartAccountId()+"/"+s.activity())).toList(),v.checker(),utc(v.reviewedAt()),v.reviewEvidence())).sorted(Comparator.comparing(v->v.journalId().toString())).toList();
        return new Coverage(versions,proof.missingJournalIds().stream().sorted(Comparator.comparing(UUID::toString)).toList());
    }
    private static OffsetDateTime utc(OffsetDateTime time){return time==null?null:time.withOffsetSameInstant(java.time.ZoneOffset.UTC);}
    private boolean currentIdentifiers(LedgerReportService.Scope scope,List<UUID> journals,Coverage proof){var expected=new HashMap<UUID,UUID>();proof.versions().forEach(v->expected.put(v.journalId(),v.id()));return repository.currentApprovedIdentifiers(scope.institution(),scope.branch(),journals).equals(expected);}
    private static void writableTransaction(){var isolation=org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive() || org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly() || isolation!=null && isolation!=java.sql.Connection.TRANSACTION_READ_COMMITTED)throw new IllegalStateException("Cash-flow approval/proof requires a writable READ_COMMITTED owning transaction");}
    @Transactional(readOnly=true) public boolean hasInstitutionHistory(String id){return id!=null && repository.hasInstitutionHistory(id);}
    @Transactional(readOnly=true) public boolean hasMemberHistory(UUID id){return id!=null && repository.hasMemberHistory(id);}
    private void verify(Version v){require(hash(mapper.writeValueAsString(v.source())).equals(v.sourceChecksum()) && hash(mapper.writeValueAsString(v.splits())).equals(v.definitionChecksum()),"checksum");validate(v.source(),v.splits(),v.noncashEvidence());}
    private static void text(String text,boolean mandatory){require((!mandatory && text==null) || text!=null && (!mandatory || !text.isBlank()) && text.length()<=1000 && text.codePoints().noneMatch(c->Character.isISOControl(c) && c!='\n' && c!='\t'),"evidence");}
    private void event(AppUserPrincipal actor,UUID id,String action){audit.log("ACCOUNTING_CASH_FLOW",id,action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId()));}
    private static String hash(String json){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
