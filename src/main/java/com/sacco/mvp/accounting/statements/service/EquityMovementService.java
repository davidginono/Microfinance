package com.sacco.mvp.accounting.statements.service;

import com.sacco.mvp.accounting.statements.dto.StatementModels.Parameters;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementSetupRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import com.sacco.mvp.accounting.dto.VoucherDtos.Type;

@Service @RequiredArgsConstructor
public class EquityMovementService {
    private final StatementSetupRepository repository;
    private final GeneralLedgerService access;
    private final AuditService audit;
    private final ObjectMapper mapper;

    @Transactional(readOnly=true)
    public List<MovementOption> options(AppUserPrincipal actor,MovementSelection selected,boolean editable){
        if(editable)authorize(actor);else access.requireActor(actor,"VOUCHER_WORKSPACE_VIEW");
        var options=new ArrayList<MovementOption>();
        if(editable)repository.latest(actor.getSaccoId()).ifPresent(stored->{
            var setup=mapper.readValue(stored.payload(),Setup.class);
            for(var row:setup.movementRows())options.add(new MovementOption(new MovementSelection(setup.version(),row.id()).value(),row));
        });
        if(selected!=null&&options.stream().noneMatch(option->option.value().equals(selected.value())))resolve(actor,selected).ifPresent(row->{
            options.removeIf(option->option.row().equals(row));
            options.addFirst(new MovementOption(selected.value(),row));
        });
        return List.copyOf(options);
    }

    @Transactional(readOnly=true)
    public Optional<MovementRow> resolve(AppUserPrincipal actor,MovementSelection selection){
        return repository.version(actor.getSaccoId(),selection.version())
            .map(stored->mapper.readValue(stored.payload(),Setup.class))
            .flatMap(setup->setup.movementRows().stream().filter(row->row.id().equals(selection.id())).findFirst());
    }
    @Transactional(readOnly=true) public List<Journal> journals(AppUserPrincipal a,Parameters p,int page){authorize(a);require(p!=null&&p.from()!=null&&p.through()!=null&&!p.through().isBefore(p.from())&&!p.through().isAfter(p.from().plusDays(366))&&page>=0&&page<=10000,"dates");return repository.journals(a,p,page);}
    @Transactional(timeout=15) public void classify(AppUserPrincipal a,UUID id,int version,Category category,String reason){
        classify(a,id,version,new Classification(category,null,null),reason,true);
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void classifyPosted(AppUserPrincipal actor,UUID journal,Classification classification,String reason){
        classify(actor,journal,0,classification,reason,false);
    }
    private void classify(AppUserPrincipal a,UUID id,int version,Classification classification,String reason,boolean manual){
        Category category=classification.category();
        authorize(a);require(id!=null&&category!=null&&version>=0,"setup");require(reason!=null&&reason.length()<=500&&!reason.chars().anyMatch(c->c<32&&c!='\n'&&c!='\r'&&c!='\t'),"setup");reason=reason.strip();require(!reason.isBlank(),"reason");repository.lock(a.getSaccoId()+"/"+a.getStationId()+"/"+id);
        var lines=repository.journal(a,id);if(lines.isEmpty())throw new AccessDeniedException("Journal unavailable");require(lines.size()<=2000,"limit");
        var choices=repository.accounts(a.getSaccoId());require(choices.size()<=2000,"limit");var types=new HashMap<UUID,String>();choices.forEach(c->types.put(c.id(),c.type()));
        validateMovement(category,lines,types);
        int current=repository.classificationVersion(a,id);if(current==version+1&&repository.sameClassification(a,id,category,reason))return;require(current==version,"setupStale");
        if(manual&&current>0)classification=repository.latestClassification(a,id).filter(previous->previous.category()==category).orElse(classification);
        repository.classify(a,id,current+1,classification,reason);
        audit.log("STATEMENT_SETUP",id,"CLASSIFY_EQUITY",a.getMemberId(),null,Map.of("saccoId",a.getSaccoId(),"stationId",a.getStationId(),"reason",reason));
    }
    /** Pure accounting policy shared by voucher preview and posted-journal classification. */
    public static void validateMovement(Category category,List<Movement> lines,Map<UUID,String> types){
        require(category!=null&&!lines.isEmpty()&&lines.size()<=2000&&lines.stream().allMatch(l->types.get(l.accountId())!=null),"movement");
        var equity=lines.stream().filter(l->"EQUITY".equals(types.get(l.accountId()))).toList();require(!equity.isEmpty(),"movement");
        if(Set.of(Category.CONTRIBUTION,Category.DISTRIBUTION,Category.OCI).contains(category))require(lines.stream().noneMatch(l->Set.of("INCOME","EXPENSE").contains(types.getOrDefault(l.accountId(),""))),"movement");
        if(category==Category.TRANSFER)require(lines.size()==equity.size()&&equity.stream().map(Movement::signed).reduce(BigDecimal.ZERO,BigDecimal::add).signum()==0,"movement");
        if(category==Category.EARNINGS_TRANSFER)require(lines.size()>equity.size()&&lines.stream().allMatch(l->Set.of("EQUITY","INCOME","EXPENSE").contains(types.getOrDefault(l.accountId(),""))),"movement");
    }
    /** A linked financial correction inherits its source type; it offers no new classification choice. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void inheritReversal(AppUserPrincipal a,Type type,UUID original,UUID reversal,String reason){
        access.requireActor(a,type==Type.JOURNAL?"ACCOUNTING_JOURNALS_REVERSE":"ACCOUNTING_BUSINESS_REVERSE");
        repository.lock(a.getSaccoId()+"/"+a.getStationId()+"/"+original);
        var inherited=repository.reversalClassification(a,type,original,reversal);if(inherited.isEmpty())return;
        require(repository.classificationVersion(a,reversal)==0,"setupStale");repository.classify(a,reversal,1,inherited.get(),reason.strip());
        audit.log("STATEMENT_SETUP",reversal,"CLASSIFY_EQUITY_REVERSAL",a.getMemberId(),null,Map.of("saccoId",a.getSaccoId(),"stationId",a.getStationId(),"originalJournal",original,"reason",reason.strip()));
    }
    public void authorize(AppUserPrincipal a){access.requireActor(a,"FINANCIAL_REPORTS_VIEW");access.requireActor(a,"FINANCIAL_REPORTS_CONFIGURE");}
    private static void require(boolean valid,String key){if(!valid)throw new IllegalArgumentException("fs.error."+key);}
}
