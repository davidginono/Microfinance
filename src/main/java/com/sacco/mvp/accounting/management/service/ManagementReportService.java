package com.sacco.mvp.accounting.management.service;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
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
    private final GeneralLedgerService guard;
    private final ApplicationClock clock;
    private final ReportExportLimiter limiter;
    public void authorize(AppUserPrincipal a,Kind kind,boolean export){
        guard.requireActor(a,"FINANCIAL_REPORTS_VIEW");
        guard.requireActor(a,switch(kind){case DAILY_COLLECTIONS->"ACCOUNTING_BUSINESS_VIEW";case CASHBOOK->"ACCOUNTING_JOURNALS_VIEW";default->"LOAN_REPORTS_VIEW";});
        if(export)guard.requireActor(a,"FINANCIAL_REPORTS_EXPORT");
    }
    @Transactional(readOnly=true) public List<Choice> choices(AppUserPrincipal a,Kind kind,String category,String search,int page) {
        authorize(a,kind,false);check(search!=null && search.length()<=100 && search.chars().noneMatch(Character::isISOControl) && page>=0 && page<=10000,"parameters");
        return switch(category) {
            case "accounts" -> {accountReport(kind);yield reports.accounts(a,search,page,List.of());}
            case "clients" -> reports.clients(a,search,page,null);
            default -> throw error("parameters");
        };
    }
    @Transactional(readOnly=true) public Map<UUID,Choice> selectedRange(AppUserPrincipal a,Kind kind,UUID start,UUID end) {
        authorize(a,kind,false);
        if(start==null && end==null)return Map.of();
        accountReport(kind);
        var ids=new LinkedHashSet<UUID>();
        if(start!=null)ids.add(start);
        if(end!=null)ids.add(end);
        var selected=new HashMap<UUID,Choice>();
        reports.accounts(a,"",0,List.copyOf(ids)).forEach(choice->selected.put(choice.id(),choice));
        check(selected.keySet().equals(ids),"cashBankAccount");
        return selected;
    }
    @Transactional(readOnly=true) public Choice selectedClient(AppUserPrincipal a,Kind kind,UUID id) {
        authorize(a,kind,false);return id==null?null:reports.clients(a,"",0,id).stream().findFirst().orElseThrow(()->error("client"));
    }
    private static void accountReport(Kind kind){check(kind==Kind.CASHBOOK || kind==Kind.DAILY_COLLECTIONS,"parameters");}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30)
    public Result report(AppUserPrincipal a,Kind kind,Parameters p,boolean export){
        authorize(a,kind,export);validate(p,clock.today(),clock.now(),kind);
        return limiter.run(()->{
            var selected=selectedRange(a,kind,p.startAccount(),p.endAccount());
            var start=p.startAccount()==null?null:selected.get(p.startAccount());
            var end=p.endAccount()==null?null:selected.get(p.endAccount());
            check(start==null || end==null || start.code().compareTo(end.code())<=0,"range");
            String accountSelection=start==null?(end==null?"":"≤ "+end.label()):end==null?"≥ "+start.label():start.label()+" → "+end.label();
            var client=selectedClient(a,kind,p.clientId());
            var data=reports.read(a,kind,p,export);
            check(data.groups().size()<=1000 && (!export || data.rows().size()<=5000),"large");
            var identity=reports.identity(a);
            return new Result(kind,p,identity[0],identity[1],com.sacco.mvp.accounting.management.dto.ManagementReports.columns(kind),
                export?data.rows():data.rows().stream().limit(25).toList(),data.groups(),data.totals(),data.count(),data.excluded(),!export && data.rows().size()>25,accountSelection,client==null?"":client.getLabel());
        });
    }
    public static void validate(Parameters p,java.time.LocalDate today,java.time.OffsetDateTime now){
        validate(p,today,now,null);
    }
    public static void validate(Parameters p,java.time.LocalDate today,java.time.OffsetDateTime now,Kind kind){
        check(p!=null && p.from()!=null && p.through()!=null && p.cutoff()!=null,"parameters");
        check(p.from().getYear()>=1900 && !p.through().isBefore(p.from()) && !p.through().isAfter(p.from().plusDays(366))
            && !p.through().isAfter(kind==Kind.EXPECTED_REPAYMENTS?today.plusDays(366):today) && !p.cutoff().isAfter(now) && p.page()>=0 && p.page()<=10000,"parameters");
        check(p.product()!=null && p.product().length()<=100 && p.product().chars().noneMatch(Character::isISOControl),"parameters");
    }
    private static void check(boolean valid,String key){if(!valid)throw error(key);}
    private static IllegalArgumentException error(String key){return new IllegalArgumentException("management.error."+key);}
}
