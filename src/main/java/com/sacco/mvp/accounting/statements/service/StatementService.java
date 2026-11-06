package com.sacco.mvp.accounting.statements.service;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import static com.sacco.mvp.accounting.statements.dto.StatementModels.*;

@Service @RequiredArgsConstructor
public class StatementService {
    private final StatementRepository repository;
    private final GeneralLedgerService access;
    private final ObjectMapper mapper;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final ReportExportLimiter limiter;
    public void authorize(AppUserPrincipal a,boolean wide){access.requireActor(a,"FINANCIAL_REPORTS_VIEW");if(wide)access.requireActor(a,"FINANCIAL_REPORTS_INSTITUTION");}
    @Transactional(isolation=Isolation.READ_COMMITTED,timeout=40)
    public UUID generate(AppUserPrincipal a,Parameters p,UUID key){
        return limiter.run(()->request(a,p,key));
    }
    private UUID request(AppUserPrincipal a,Parameters p,UUID key){
        require(p!=null&&p.from()!=null&&p.through()!=null&&key!=null,"dates");authorize(a,p.institutionWide());
        require(!p.through().isBefore(p.from())&&!p.through().isAfter(clock.today())&&!p.through().isAfter(p.from().plusDays(366))&&p.from().getYear()>=1901,"dates");
        repository.lockRequest(a,key);var duplicate=repository.duplicate(a,key);
        if(duplicate.isPresent()){var old=duplicate.get();require(old.from().equals(p.from())&&old.through().equals(p.through())&&old.wide()==p.institutionWide(),"retry");return old.id();}
        // Take the coherent snapshot only after the retry lock is acquired. An RR snapshot taken
        // before waiting could miss the preceding request's commit and defeat concurrent retries.
        var capture=new TransactionTemplate(transactionManager);capture.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);capture.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);capture.setTimeout(30);
        return capture.execute(status->capture(a,p,key));
    }
    private UUID capture(AppUserPrincipal a,Parameters p,UUID key){
        UUID id=UUID.randomUUID();var now=clock.now();repository.create(id,a,p,now,key);
        require(repository.capture(id,a,p,now)<=100000,"limit");var balances=repository.balances(id,a.getSaccoId(),p);require(balances.size()<=2000,"limit");
        var cash=repository.cash(id,p);require(cash.size()<=20000,"limit");var calculated=StatementCalculator.calculate(balances,cash,p);var identity=repository.identity(a);
        var snapshot=new Snapshot(2,id,p,now,a.getSaccoId(),a.getStationId(),identity[0],identity[1],balances,calculated.position(),calculated.income(),calculated.cash(),calculated.equityColumns(),calculated.equity(),calculated.checks());
        String payload=mapper.writeValueAsString(snapshot);require(payload.length()<=8*1024*1024,"limit");repository.payload(id,payload,sha(payload.getBytes(StandardCharsets.UTF_8)));
        log(a,id,"PREVIEW");return id;
    }
    @Transactional(readOnly=true)
    public Run get(AppUserPrincipal a,UUID id){
        authorize(a,false);var stored=repository.find(id,a).orElseThrow(()->new AccessDeniedException("Statement unavailable"));authorize(a,stored.wide());
        if(stored.payload()==null||!sha(stored.payload().getBytes(StandardCharsets.UTF_8)).equals(stored.checksum()))throw new IllegalStateException("Statement integrity check failed");
        var snapshot=mapper.readValue(stored.payload(),Snapshot.class);require((snapshot.version()==1||snapshot.version()==2)&&snapshot.id().equals(id),"snapshot");
        return new Run(id,stored.from(),stored.through(),stored.generated(),stored.wide(),stored.saved(),snapshot);
    }
    @Transactional(readOnly=true)
    public List<StatementRepository.Stored> history(AppUserPrincipal a,int page,String sort){authorize(a,false);page(page);require(Set.of("newest","oldest","from","from_desc","through","through_desc","status","status_desc").contains(sort),"filter");boolean wide=false;try{access.requireActor(a,"FINANCIAL_REPORTS_INSTITUTION");wide=true;}catch(AccessDeniedException unavailable){/* Hide institution runs when the current grant is absent or revoked. */}return repository.history(a,page,sort,wide);}
    @Transactional public void save(AppUserPrincipal a,UUID id){var run=get(a,id);repository.save(id,a,clock.now());log(a,id,"SAVE_SNAPSHOT");}
    public List<Balance> selected(Snapshot s,Kind kind,String line){
        if(line==null||line.isBlank())return s.balances();
        List<Row> rows=switch(kind){case POSITION->s.position();case INCOME->s.income();case CASH_FLOW->s.cashFlow();default->throw new IllegalArgumentException("fs.error.selection");};
        var row=rows.stream().filter(r->r.key().equals(line)&&!r.accounts().isEmpty()).findFirst().orElseThrow(()->new IllegalArgumentException("fs.error.selection"));
        var ids=new HashSet<>(row.accounts());return s.balances().stream().filter(b->ids.contains(b.id())).toList();
    }
    @Transactional(readOnly=true) public List<Activity> activity(AppUserPrincipal a,UUID id,UUID account,int page){var run=get(a,id);branchActivity(run);page(page);require(run.snapshot().balances().stream().anyMatch(b->b.id().equals(account)),"selection");return repository.activity(id,account,run.snapshot().parameters(),page,31);}
    @Transactional(readOnly=true) public List<Activity> activityExport(AppUserPrincipal a,UUID id,UUID account){var run=get(a,id);branchActivity(run);exportAccess(a,run);require(run.snapshot().balances().stream().anyMatch(b->b.id().equals(account)),"selection");var rows=repository.activity(id,account,run.snapshot().parameters(),0,5001);require(rows.size()<=5000,"limit");return rows;}
    private static void branchActivity(Run run){if(run.institutionWide())throw new AccessDeniedException("Institution aggregation does not authorize cross-branch transaction access");}
    public void exportAccess(AppUserPrincipal a,Run run){authorize(a,run.institutionWide());access.requireActor(a,"FINANCIAL_REPORTS_EXPORT");}
    @Transactional(readOnly=true) public Optional<byte[]> artifact(AppUserPrincipal a,UUID id,Kind kind,String format,String language){var run=get(a,id);exportAccess(a,run);return repository.artifact(id,kind,format,language).map(StatementService::verified);}
    @Transactional public byte[] retain(AppUserPrincipal a,UUID id,Kind kind,String format,String language,byte[] bytes){var run=get(a,id);exportAccess(a,run);require(bytes.length<=32*1024*1024,"limit");repository.artifact(id,kind,format,language,bytes,sha(bytes));log(a,id,"EXPORT_"+format);return verified(repository.artifact(id,kind,format,language).orElseThrow());}
    private static byte[] verified(StatementRepository.Artifact artifact){if(!sha(artifact.bytes()).equals(artifact.checksum()))throw new IllegalStateException("Statement artifact integrity check failed");return artifact.bytes();}
    public static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void log(AppUserPrincipal a,UUID id,String action){audit.log("STATEMENT",id,action,a.getMemberId(),null,Map.of("saccoId",a.getSaccoId(),"stationId",a.getStationId()));}
    private static void page(int page){require(page>=0&&page<=10000,"filter");}
    private static void require(boolean condition,String key){if(!condition)throw new IllegalArgumentException("fs.error."+key);}
}
