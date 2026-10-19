package com.sacco.mvp.accounting.reconciliation.service;

import com.sacco.mvp.accounting.reconciliation.dto.ReconciliationReport.*;
import com.sacco.mvp.accounting.reconciliation.repository.ReconciliationRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReconciliationService {
    private final ReconciliationRepository reports;
    private final GeneralLedgerService guard;
    private final ApplicationClock clock;
    private final ReportExportLimiter limiter;
    public void authorize(AppUserPrincipal actor, boolean export) {
        guard.requireActor(actor,"FINANCIAL_REPORTS_VIEW");
        guard.requireActor(actor,"LOAN_REPORTS_VIEW");
        guard.requireActor(actor,"ACCOUNTING_RECONCILIATION_VIEW");
        if(export)guard.requireActor(actor,"FINANCIAL_REPORTS_EXPORT");
    }
    @Transactional(readOnly=true)
    public List<Activity> activities(AppUserPrincipal actor,String search,int page) {
        authorize(actor,false);
        if(search==null || search.length()>100 || page<0 || page>10000)throw new IllegalArgumentException("reconciliation.error.parameters");
        return reports.activities(actor,search.strip().toLowerCase(Locale.ROOT),page);
    }
    @Transactional(readOnly=true)
    public Activity activity(AppUserPrincipal actor,UUID id) {
        authorize(actor,false);
        return scopedActivity(actor,id);
    }
    private Activity scopedActivity(AppUserPrincipal actor,UUID id) {
        return reports.activity(actor,id).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Activity outside institution"));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30)
    public Result report(AppUserPrincipal actor, Parameters p, boolean export) {
        authorize(actor,export);
        if(p==null || p.from()==null || p.through()==null || p.cutoff()==null || p.from().getYear()<1900
            || p.through().isBefore(p.from()) || p.through().isAfter(p.from().plusDays(366)) || p.through().isAfter(clock.today())
            || p.cutoff().isAfter(clock.now()) || p.page()<0 || p.page()>10000 || p.unassigned() && p.activityId()!=null
            || p.component()==null || !Set.of("ALL","PRINCIPAL","ALLOWANCE","INTEREST_COLLECTIONS","INTEREST_RECEIVABLE").contains(p.component()))
            throw new IllegalArgumentException("reconciliation.error.parameters");
        var selected=p.activityId()==null?null:scopedActivity(actor,p.activityId());
        return limiter.run(()->{
            var groups=reports.groups(actor,p);
            long count=groups.stream().mapToLong(Group::count).sum();
            if(export && count>5000)throw new IllegalArgumentException("reconciliation.error.large");
            var rows=reports.rows(actor,p,export);
            if(export && rows.size()!=count)throw new IllegalArgumentException("reconciliation.error.large");
            var identity=reports.identity(actor);
            return new Result(p,identity[0],identity[1],export?rows:rows.stream().limit(25).toList(),groups,count,!export && rows.size()>25,selected);
        });
    }
}
