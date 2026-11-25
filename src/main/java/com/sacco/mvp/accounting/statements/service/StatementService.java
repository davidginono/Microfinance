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
    private final com.sacco.mvp.accounting.statements.repository.StatementSetupRepository setupRepository;
    private final GeneralLedgerService access;
    private final ObjectMapper mapper;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final PlatformTransactionManager transactionManager;
    private final ReportExportLimiter limiter;
    public void authorize(AppUserPrincipal a,boolean wide){access.requireActor(a,"FINANCIAL_REPORTS_VIEW");if(wide)access.requireActor(a,"FINANCIAL_REPORTS_INSTITUTION");}
    @Transactional(isolation=Isolation.READ_COMMITTED,timeout=40)
    public UUID generate(AppUserPrincipal a,Parameters p,UUID key){
        return limiter.run(()->request(a,p,key,null));
    }
    @Transactional(readOnly=true)
    public List<LedgerAccount> ledgerAccounts(AppUserPrincipal actor,String search,int page,UUID selected){
        authorize(actor,false);page(page);
        require(search!=null&&search.length()<=100,"filter");
        return repository.ledgerAccounts(actor.getSaccoId(),search.strip(),page*25,selected);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED,timeout=40)
    public UUID generateLedger(AppUserPrincipal actor,LocalDate from,LocalDate through,UUID account,UUID key){
        require(account!=null,"selection");
        return limiter.run(()->request(actor,new Parameters(from,through,false),key,account));
    }
    private UUID request(AppUserPrincipal a,Parameters p,UUID key,UUID account){
        require(p!=null&&p.from()!=null&&p.through()!=null&&key!=null,"dates");authorize(a,p.institutionWide());
        require(!p.through().isBefore(p.from())&&!p.through().isAfter(clock.today())&&!p.through().isAfter(p.from().plusDays(366))&&p.from().getYear()>=1901,"dates");
        if(account!=null)require(!repository.ledgerAccounts(a.getSaccoId(),"",0,account).isEmpty(),"selection");
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
        var cash=repository.cash(id,p,balances);require(cash.size()<=20000,"limit");var setup=setupRepository.latest(a.getSaccoId()).map(v->mapper.readValue(v.payload(),com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Setup.class)).orElse(com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Setup.empty());
        var accountIds=new HashSet<UUID>();balances.forEach(b->accountIds.add(b.id()));
        require(setup.components().stream().allMatch(c->accountIds.containsAll(c.accounts()))&&setup.notes().stream().allMatch(n->accountIds.containsAll(n.accounts())),"setupAccounts");
        var movements=setupRepository.movements(id,p);require(movements.size()<=32000,"limit");
        balances=notes(balances,setup);
        var calculated=StatementCalculator.calculate(EquityStatementCalculator.incomeWithoutClosings(balances,movements),cash,p,false);var identity=repository.identity(a);
        var equity=EquityStatementCalculator.calculate(balances,movements,setup);var checks=new ArrayList<>(calculated.checks());checks.removeIf(c->c.key().equals("equity"));checks.addAll(equity.checks());
        if(setup.notes().isEmpty())checks.add(new Check("notesSetup",null));
        else {var familyNotes=new HashMap<UUID,Set<Integer>>();for(var b:balances)if(b.mapping()!=null&&b.mapping().groupId()!=null)familyNotes.computeIfAbsent(b.mapping().groupId(),k->new HashSet<>()).add(b.mapping().note());if(familyNotes.values().stream().anyMatch(n->n.size()>1&&n.stream().anyMatch(i->i>0)))checks.add(new Check("notesLinks",null));}

        var snapshot=new Snapshot(5,id,p,now,a.getSaccoId(),a.getStationId(),identity[0],identity[1],balances,profitNote(calculated.position(),setup),calculated.income(),calculated.cash(),equity.columns(),equity.current(),List.copyOf(checks),setup,equity.previous(),List.copyOf(movements));
        String payload=mapper.writeValueAsString(snapshot);require(payload.length()<=8*1024*1024,"limit");repository.payload(id,payload,sha(payload.getBytes(StandardCharsets.UTF_8)));
        log(a,id,"PREVIEW");return id;
    }
    private static List<Row> profitNote(List<Row> rows,com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Setup setup){
        int note=setup.notes().stream().filter(n->n.includeProfit()).mapToInt(n->n.number()).findFirst().orElse(0);
        if(note==0)return rows;return rows.stream().map(r->r.key().equals("earnings")?new Row(r.key(),r.code(),r.label(),note,r.section(),r.accounts(),r.current(),r.previous(),r.total()):r).toList();
    }
    private static List<Balance> notes(List<Balance> balances,com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Setup setup){
        if(!setup.hasLayoutNotes())return balances;var byAccount=new HashMap<UUID,Integer>();for(var n:setup.notes())for(UUID id:n.accounts())byAccount.put(id,n.number());
        return balances.stream().map(b->{var m=b.mapping();if(m==null)return b;var mapping=new Mapping(m.accountId(),m.line(),m.section(),m.cashSection(),m.equityComponent(),byAccount.getOrDefault(b.id(),0),"",m.groupId(),m.groupCode());return new Balance(b.id(),b.code(),b.name(),b.type(),b.purpose(),mapping,b.opening(),b.debit(),b.credit(),b.closing(),b.previousOpening(),b.previousDebit(),b.previousCredit(),b.previousClosing());}).toList();
    }
    @Transactional(readOnly=true)
    public Run get(AppUserPrincipal a,UUID id){
        authorize(a,false);var stored=repository.find(id,a).orElseThrow(()->new AccessDeniedException("Statement unavailable"));authorize(a,stored.wide());
        return verifiedRun(id,stored);
    }

    @Transactional(readOnly=true)
    public List<StatementRepository.Stored> branchSaved(com.sacco.mvp.branches.service.BranchService.ReadScope scope,int page) {
        scope.require(com.sacco.mvp.domain.UserClaim.FINANCIAL_REPORTS_VIEW);
        page(page);
        return repository.branchSaved(scope.institution(),scope.branch().id(),page);
    }

    @Transactional(readOnly=true)
    public Run branchSaved(com.sacco.mvp.branches.service.BranchService.ReadScope scope,UUID id) {
        scope.require(com.sacco.mvp.domain.UserClaim.FINANCIAL_REPORTS_VIEW);
        var stored=repository.branchSaved(scope.institution(),scope.branch().id(),id)
            .orElseThrow(()->new AccessDeniedException("Statement unavailable"));
        return verifiedRun(id,stored);
    }

    private Run verifiedRun(UUID id,StatementRepository.Stored stored) {
        if(stored.payload()==null||!sha(stored.payload().getBytes(StandardCharsets.UTF_8)).equals(stored.checksum()))throw new IllegalStateException("Statement integrity check failed");
        var snapshot=mapper.readValue(stored.payload(),Snapshot.class);require((snapshot.version()>=1&&snapshot.version()<=5)&&snapshot.id().equals(id),"snapshot");
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
