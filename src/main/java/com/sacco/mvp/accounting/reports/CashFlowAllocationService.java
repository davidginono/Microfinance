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
    public void authorizeCreate(AppUserPrincipal actor){reports.authorizeAllocation(actor,UserClaim.ACCOUNTING_CASH_FLOW_CREATE);}
    @Transactional(timeout=20) public UUID draft(AppUserPrincipal actor,UUID journal,UUID request,List<Split> splits,String evidence,String noncashEvidence) {
        var scope=reports.authorizeAllocation(actor,UserClaim.ACCOUNTING_CASH_FLOW_CREATE);text(evidence,true);text(noncashEvidence,false);require(journal!=null && request!=null,"source");
        repository.lockSource(scope.institution(),scope.branch(),journal);Source source=repository.source(scope.institution(),scope.branch(),journal);validate(source,splits,noncashEvidence);
        splits=splits.stream().sorted(Comparator.comparing(Split::moneyLineId).thenComparing(Split::counterpartAccountId).thenComparing(Split::activity)).toList();
        String sourceHash=hash(mapper.writeValueAsString(source)),definitionHash=hash(mapper.writeValueAsString(splits));var prior=repository.retry(scope.institution(),scope.branch(),request);
        if(prior.isPresent()){var p=prior.get();require(p.maker().equals(actor.getMemberId()) && p.journalId().equals(journal) && p.sourceChecksum().equals(sourceHash) && p.definitionChecksum().equals(definitionHash) && p.evidence().equals(evidence) && Objects.equals(p.noncashEvidence(),noncashEvidence),"changedRetry");return p.id();}
        UUID id=repository.save(scope.institution(),scope.branch(),request,actor.getMemberId(),clock.now(),evidence,noncashEvidence,source,splits,sourceHash,definitionHash);event(actor,id,"DRAFTED");return id;
    }
    @Transactional(timeout=20) public void approve(AppUserPrincipal actor,UUID id,String evidence) {
        var scope=reports.authorizeAllocation(actor,UserClaim.ACCOUNTING_CASH_FLOW_APPROVE);text(evidence,true);var v=repository.version(scope.institution(),scope.branch(),id,true);
        require(!v.maker().equals(actor.getMemberId()) && !v.approved(),"checker");verify(v);Source source=repository.source(scope.institution(),scope.branch(),v.journalId());require(hash(mapper.writeValueAsString(source)).equals(v.sourceChecksum()),"source");
        repository.approve(scope.institution(),scope.branch(),id,actor.getMemberId(),evidence,clock.now());event(actor,id,"APPROVED");
    }
    @Transactional(readOnly=true) public Source source(AppUserPrincipal actor,UUID journal){var scope=reports.authorizeAllocation(actor,null);return repository.source(scope.institution(),scope.branch(),journal);}
    @Transactional(readOnly=true) public List<Version> versions(AppUserPrincipal actor,int page){var scope=reports.authorizeAllocation(actor,null);require(page>=0 && page<=10000,"size");return repository.list(scope.institution(),scope.branch(),page);}
    @Transactional(readOnly=true) public List<CashFlowAllocationRepository.JournalChoice> journalChoices(AppUserPrincipal actor,int page){var scope=reports.authorizeAllocation(actor,null);require(page>=0 && page<=10000,"size");return repository.journalChoices(scope.institution(),scope.branch(),page);}
    /** Caller freezes these actual versions in its own close snapshot. Missing review is explicit coverage. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=20)
    public Coverage reviewedAllocations(AppUserPrincipal actor,List<UUID> journals,OffsetDateTime cutoff) {
        var scope=reports.authorizeAllocation(actor,null);require(journals!=null && journals.size()<=1000 && journals.stream().allMatch(Objects::nonNull) && cutoff!=null && !cutoff.isAfter(clock.now()),"size");
        var ids=journals.stream().distinct().sorted().toList();if(!repository.allSourcesAvailable(scope.institution(),scope.branch(),cutoff,ids))throw new org.springframework.security.access.AccessDeniedException("Posted cash flow sources outside cutoff or scope");
        var versions=repository.approved(scope.institution(),scope.branch(),cutoff,ids);versions.forEach(this::verify);var covered=new HashSet<UUID>();versions.forEach(v->covered.add(v.journalId()));
        return new Coverage(versions,ids.stream().filter(id->!covered.contains(id)).toList());
    }
    @Transactional(readOnly=true) public boolean hasInstitutionHistory(String id){return id!=null && repository.hasInstitutionHistory(id);}
    @Transactional(readOnly=true) public boolean hasMemberHistory(UUID id){return id!=null && repository.hasMemberHistory(id);}
    private void verify(Version v){require(hash(mapper.writeValueAsString(v.source())).equals(v.sourceChecksum()) && hash(mapper.writeValueAsString(v.splits())).equals(v.definitionChecksum()),"checksum");validate(v.source(),v.splits(),v.noncashEvidence());}
    private static void text(String text,boolean mandatory){require((!mandatory && text==null) || text!=null && (!mandatory || !text.isBlank()) && text.length()<=1000 && text.codePoints().noneMatch(c->Character.isISOControl(c) && c!='\n' && c!='\t'),"evidence");}
    private void event(AppUserPrincipal actor,UUID id,String action){audit.log("ACCOUNTING_CASH_FLOW",id,action,actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId()));}
    private static String hash(String json){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
