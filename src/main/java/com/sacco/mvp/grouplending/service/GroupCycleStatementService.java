package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.grouplending.dto.GroupCycleStatementDtos.*;
import com.sacco.mvp.grouplending.repository.GroupCycleStatementRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

@Service @RequiredArgsConstructor
public class GroupCycleStatementService {
    private final GroupCycleStatementRepository statements;
    private final GeneralLedgerService access;
    private final GroupSharedLoanService shared;
    private final ApplicationClock clock;
    private final ReportExportLimiter limiter;
    private final GroupCoverageService coverage;
    public void authorize(AppUserPrincipal a,boolean export){for(var claim:List.of("GROUP_LENDING_VIEW","LOAN_RECORDING_VIEW","LOAN_REPORTS_VIEW","FINANCIAL_REPORTS_VIEW"))access.requireActor(a,claim);if(export)for(var claim:List.of("GROUP_LENDING_EXPORT","LOAN_RECORDING_EXPORT","LOAN_REPORTS_EXPORT","FINANCIAL_REPORTS_EXPORT"))access.requireActor(a,claim);}
    public boolean canExport(AppUserPrincipal a){try{authorize(a,true);return true;}catch(AccessDeniedException ignored){return false;}}
    private boolean allowed(AppUserPrincipal a,String claim){try{access.requireActor(a,claim);return true;}catch(AccessDeniedException ignored){return false;}}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30)
    public Statement statement(AppUserPrincipal a,UUID cycle,LocalDate from,LocalDate through,int loanPage,int page,int contributionPage,int allocationPage,boolean export){return statement(a,cycle,from,through,loanPage,page,contributionPage,allocationPage,0,export);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30)
    public Statement statement(AppUserPrincipal a,UUID cycle,LocalDate from,LocalDate through,int loanPage,int page,int contributionPage,int allocationPage,int coveragePage,boolean export){
        authorize(a,export);var today=clock.today();var end=through==null?today:through;
        if(coveragePage<0||coveragePage>10000||cycle==null||allocationPage<0||allocationPage>10000||loanPage<0||loanPage>10000||page<0||page>10000||contributionPage<0||contributionPage>10000||end.isAfter(today)||end.getYear()<1||from!=null&&(from.getYear()<1||from.isAfter(end)))throw new IllegalArgumentException("group.error.query");
        return limiter.run(()->{
            var context=statements.context(a,cycle);var loans=statements.loans(a,cycle,today);if(loans.size()>50)throw new IllegalArgumentException("group.error.statementRange");
            var allocations=context.model().equals("SHARED")&&!loans.isEmpty()?shared.balances(a,loans.getFirst().id()):List.<com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.Balance>of();
            var movements=statements.movements(a,cycle,from,end,export?5001:26,export?0:page*25);
            var contributions=statements.contributions(a,cycle,from,end,export?5001:26,export?0:contributionPage*25);
            if(export&&(movements.size()>5000||contributions.size()>5000))throw new IllegalArgumentException("group.error.statementRange");
            boolean loanLinks=allowed(a,"LOAN_RECORDING_VIEW");return new Statement(context,today,clock.now(),from,end,totals(loans),export?loans:page(loans,loanPage),export?allocations:page(allocations,allocationPage),export?movements:movements.stream().limit(25).toList(),export?contributions:contributions.stream().limit(25).toList(),loanPage,!export&&loans.size()>(loanPage+1)*25,allocationPage,!export&&allocations.size()>(allocationPage+1)*25,page,!export&&movements.size()>25,contributionPage,!export&&contributions.size()>25,loanLinks,loanLinks&&allowed(a,"ACCOUNTING_BUSINESS_VIEW"),coverage.trace(a,cycle,null,from,end,coveragePage,false,export));
        });
    }
    private static <T> List<T> page(List<T> rows,int page){int start=Math.min(rows.size(),page*25);return List.copyOf(rows.subList(start,Math.min(rows.size(),start+25)));}
    static Totals totals(List<Loan> rows){var funded=rows.stream().filter(Loan::funded).toList();int unknown=(int)funded.stream().filter(l->!l.known()).count();boolean known=unknown==0;
        return new Totals(rows.size(),funded.size(),unknown,sum(rows,Loan::original),known?sum(funded,Loan::original):null,known?sum(funded,Loan::principal):null,known?sum(funded,Loan::dueInterest):null,known?sum(funded,Loan::futureInterest):null,known?sum(funded,Loan::charges):null,known?sum(funded,Loan::arrears):null,known?funded.stream().map(Loan::nextDue).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null):null);
    }
    private static BigDecimal sum(List<Loan> rows,Function<Loan,BigDecimal> amount){return rows.stream().map(amount).reduce(new BigDecimal("0.00"),BigDecimal::add);}
}
