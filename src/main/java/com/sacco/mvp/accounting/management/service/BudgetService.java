package com.sacco.mvp.accounting.management.service;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.management.repository.BudgetRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class BudgetService {
    private final BudgetRepository budgets;
    private final GeneralLedgerService guard;
    private final ApplicationClock clock;
    private final AuditService audit;
    public void authorize(AppUserPrincipal a,boolean create){guard.requireActor(a,create?"ACCOUNTING_ACCOUNTS_CREATE":"ACCOUNTING_ACCOUNTS_VIEW");}
    @Transactional(readOnly=true) public BudgetPage list(AppUserPrincipal a,int year,int page,String sort,String direction){authorize(a,false);check(year>=1900 && year<=2200 && page>=0 && page<=10000,"parameters");var rows=budgets.list(a,year,page,sort,direction);return new BudgetPage(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public Budget view(AppUserPrincipal a,UUID id){authorize(a,false);return budgets.find(a,id).orElseThrow(()->error("budget"));}
    @Transactional(readOnly=true) public List<Choice> accounts(AppUserPrincipal a,String search,int page){authorize(a,false);check(search!=null && search.length()<=100 && page>=0 && page<=10000,"parameters");return budgets.accounts(a,search,page,false);}
    @Transactional(readOnly=true) public Choice selected(AppUserPrincipal a,UUID id){authorize(a,false);return id==null?null:budgets.account(a,id,false).orElseThrow(()->error("account"));}
    @Transactional public UUID save(AppUserPrincipal a,BudgetForm f){
        authorize(a,true);validate(f,clock.today());
        if(f.getReplacesId()!=null)guard.requireActor(a,"ACCOUNTING_ACCOUNTS_UPDATE");
        String payload=f.getAccountId()+"/"+f.getYear()+"/"+f.getDate()+"/"+f.getReplacesId()+"/"+f.getAmounts().stream().map(v->v.setScale(2).toPlainString()).toList();
        String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        // Lock one account/year only. Posted money and other budgets are independent.
        budgets.lockRequest(a,f.getRequestKey());budgets.lock(a,f.getAccountId(),f.getYear());
        var retry=budgets.retry(a,f.getRequestKey(),hash);if(retry.isPresent())return retry.get();
        budgets.account(a,f.getAccountId(),false).orElseThrow(()->error("account"));
        var previous=budgets.latest(a,f.getAccountId(),f.getYear());
        check(previous.isEmpty()?f.getReplacesId()==null:previous.get().id().equals(f.getReplacesId()),"revision");
        int revision=previous.map(b->b.revision()+1).orElse(1);UUID id=UUID.randomUUID();
        budgets.insert(a,id,f,revision,hash,clock.now());
        audit.logEvent("ACCOUNT_BUDGET",id,"BUDGET_SAVED",a.getMemberId(),AuditEventStatus.SUCCESS,"Budget saved","ACCOUNT_BUDGET",id.toString(),a.getSaccoId(),a.getStationId(),Map.of("year",f.getYear(),"revision",revision,"accountId",f.getAccountId()));
        return id;
    }
    public static void validate(BudgetForm f,java.time.LocalDate today){
        check(f!=null && f.getRequestKey()!=null && f.getAccountId()!=null && f.getYear()!=null && f.getYear()>=1900 && f.getYear()<=2200 && f.getDate()!=null && !f.getDate().isAfter(today),"parameters");
        check(f.getAmounts()!=null && f.getAmounts().size()==12,"months");
        for(BigDecimal n:f.getAmounts())check(n!=null && n.signum()>=0 && n.scale()<=2 && n.precision()-n.scale()<=16,"months");
        check(f.getAmounts().stream().reduce(BigDecimal.ZERO,BigDecimal::add).signum()>0,"months");
    }
    private static void check(boolean valid,String key){if(!valid)throw error(key);}
    private static IllegalArgumentException error(String key){return new IllegalArgumentException("management.error."+key);}
}
