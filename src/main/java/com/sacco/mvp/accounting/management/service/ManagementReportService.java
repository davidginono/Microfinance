package com.sacco.mvp.accounting.management.service;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.management.repository.BudgetRepository;
import com.sacco.mvp.accounting.reports.ManagementReportRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ManagementReportService {
    private final ManagementReportRepository reports;
    private final BudgetRepository accounts;
    private final GeneralLedgerService guard;
    private final ApplicationClock clock;
    private final ReportExportLimiter limiter;
    public void authorize(AppUserPrincipal a,Kind kind,boolean export){
        guard.requireActor(a,"FINANCIAL_REPORTS_VIEW");
        guard.requireActor(a,switch(kind){case DAILY_COLLECTIONS->"ACCOUNTING_BUSINESS_VIEW";case CASHBOOK->"ACCOUNTING_JOURNALS_VIEW";default->"LOAN_REPORTS_VIEW";});
        if(export)guard.requireActor(a,"FINANCIAL_REPORTS_EXPORT");
    }
    @Transactional(readOnly=true) public List<Choice> accounts(AppUserPrincipal a,Kind kind,String search,int page){authorize(a,kind,false);check(search!=null && search.length()<=100 && page>=0 && page<=10000,"parameters");return accounts.accounts(a,search,page,true);}
    @Transactional(readOnly=true) public Choice selected(AppUserPrincipal a,Kind kind,UUID id){authorize(a,kind,false);return id==null?null:accounts.account(a,id,true).orElseThrow(()->error("account"));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30)
    public Result report(AppUserPrincipal a,Kind kind,Parameters p,boolean export){
        authorize(a,kind,export);validate(p,clock.today(),clock.now());
        return limiter.run(()->{
            String start=p.startAccount()==null?"":accounts.account(a,p.startAccount(),true).orElseThrow(()->error("account")).code();
            String end=p.endAccount()==null?"":accounts.account(a,p.endAccount(),true).orElseThrow(()->error("account")).code();
            check(start.isEmpty() || end.isEmpty() || start.compareTo(end)<=0,"range");
            var data=reports.read(a,kind,p,start,end,export);
            check(data.groups().size()<=1000 && (!export || data.rows().size()<=5000),"large");
            var identity=reports.identity(a);
            return new Result(kind,p,identity[0],identity[1],com.sacco.mvp.accounting.management.dto.ManagementReports.columns(kind),
                export?data.rows():data.rows().stream().limit(25).toList(),data.groups(),data.totals(),data.count(),data.excluded(),!export && data.rows().size()>25,start+" — "+end);
        });
    }
    public static void validate(Parameters p,java.time.LocalDate today,java.time.OffsetDateTime now){
        check(p!=null && p.from()!=null && p.through()!=null && p.cutoff()!=null,"parameters");
        check(p.from().getYear()>=1900 && !p.through().isBefore(p.from()) && !p.through().isAfter(p.from().plusDays(366))
            && !p.through().isAfter(today) && !p.cutoff().isAfter(now) && p.page()>=0 && p.page()<=10000,"parameters");
        check(p.client()!=null && p.client().length()<=100 && p.product()!=null && p.product().length()<=100
            && p.client().chars().noneMatch(Character::isISOControl) && p.product().chars().noneMatch(Character::isISOControl),"parameters");
    }
    private static void check(boolean valid,String key){if(!valid)throw error(key);}
    private static IllegalArgumentException error(String key){return new IllegalArgumentException("management.error."+key);}
}
