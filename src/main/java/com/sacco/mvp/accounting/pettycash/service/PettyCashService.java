package com.sacco.mvp.accounting.pettycash.service;

import com.sacco.mvp.accounting.pettycash.dto.PettyCashDtos.*;
import com.sacco.mvp.accounting.pettycash.repository.PettyCashRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PettyCashService {
    private final PettyCashRepository funds;
    private final GeneralLedgerService ledger;
    private final ApplicationClock clock;
    private final AuditService audit;
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Workspace workspace(AppUserPrincipal actor,Query q){
        ledger.requireActor(actor,"ACCOUNTING_BUSINESS_VIEW");validate(q);
        var now=clock.now();var today=clock.today();var f=funds.fund(actor).orElse(null);
        if(f==null)return new Workspace(null,q,null,List.of(),false,now,today);
        var rows=funds.rows(actor,f,q,now);
        return new Workspace(f,q,funds.balances(actor,f,q,now,today),rows.stream().limit(q.size()).toList(),rows.size()>q.size(),now,today);
    }
    @Transactional(readOnly=true)
    public List<Pending> pending(AppUserPrincipal actor,int page,int size,String sort,String search){
        ledger.requireActor(actor,"ACCOUNTING_BUSINESS_VIEW");ledger.requireActor(actor,"VOUCHER_WORKSPACE_VIEW");
        if(search==null || search.length()>100 || page<0 || page>10000 || !Set.of(10,30,50).contains(size) || !Set.of("number_asc","number_desc","state_asc","state_desc").contains(sort))throw invalid("filter");
        return funds.fund(actor).map(f->funds.pending(actor,f,page,size,sort,search)).orElse(List.of());
    }
    @Transactional(readOnly=true)
    public Optional<Fund> setup(AppUserPrincipal actor){ledger.requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");return funds.fund(actor);}
    @Transactional(readOnly=true)
    public String selectedAccount(AppUserPrincipal actor,UUID id){ledger.requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");return funds.choice(actor,id).map(Choice::name).orElse("");}
    @Transactional(readOnly=true)
    public Fund expenseFund(AppUserPrincipal actor){
        ledger.requireActor(actor,"ACCOUNTING_BUSINESS_CREATE");return funds.fund(actor).orElseThrow(()->new com.sacco.mvp.accounting.dto.VoucherDtos.Invalid("","pettySetup"));
    }
    @Transactional(readOnly=true)
    public List<Choice> choices(AppUserPrincipal actor,String search,int page){
        ledger.requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");ledger.requireActor(actor,"ACCOUNTING_ACCOUNTS_UPDATE");
        if(search==null || search.length()>100 || page<0 || page>10000)throw invalid("filter");return funds.choices(actor,search,page);
    }
    @Transactional
    public void configure(AppUserPrincipal actor,Setup setup){
        ledger.requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");ledger.requireActor(actor,"ACCOUNTING_ACCOUNTS_UPDATE");
        if(setup.accountId()==null || setup.evidence()==null || setup.evidence().isBlank() || setup.evidence().length()>500)throw invalid("evidence");
        var existing=funds.fund(actor);if(existing.isPresent()){
            if(existing.get().accountId().equals(setup.accountId()) && existing.get().evidence().equals(setup.evidence().strip()))return;
            throw invalid("fixed");
        }
        try{funds.configure(actor,new Setup(setup.accountId(),setup.evidence().strip()),clock.today(),clock.now());}
        catch(DataAccessException e){String message=e.getMostSpecificCause().getMessage();
            if(message!=null && message.contains("petty.error.coverage"))throw invalid("coverage");
            if(message!=null && message.contains("petty.error.account"))throw invalid("account");
            throw invalid("fixed");
        }
        audit.logEvent("ACCOUNTING",setup.accountId(),"PETTY_CASH_CONFIGURED",actor.getMemberId(),AuditEventStatus.SUCCESS,
            "Petty cash configured","ACCOUNT",setup.accountId().toString(),actor.getSaccoId(),actor.getStationId(),Map.of("account",setup.accountId()));
    }
    private void validate(Query q){
        if(q.from()==null || q.through()==null || q.from().getYear()<1 || q.through().isBefore(q.from()) || q.through().isAfter(clock.today())
            || !Set.of("expenses","movements").contains(q.view()) || q.search()==null || q.search().length()>100
            || q.page()<0 || q.page()>10000 || !Set.of(10,30,50).contains(q.size())
            || !q.sort().matches("(date|number|party|account|description|paid|in|out|balance|state)_(asc|desc)"))throw invalid("filter");
    }
    private static IllegalArgumentException invalid(String code){return new IllegalArgumentException("petty.error."+code);}
}
