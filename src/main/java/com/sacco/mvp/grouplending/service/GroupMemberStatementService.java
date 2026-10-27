package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.grouplending.dto.GroupMemberStatementDtos.*;
import com.sacco.mvp.grouplending.repository.GroupMemberStatementRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class GroupMemberStatementService {
    private final GroupMemberStatementRepository statements;
    private final GroupConsentService clients;
    private final GeneralLedgerService access;
    private final ApplicationClock clock;
    private final GroupCoverageService coverage;
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Statement client(AppUserPrincipal a,UUID cycle,LocalDate from,LocalDate through,int page,int schedulePage,boolean export){return client(a,cycle,from,through,page,schedulePage,0,export);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Statement client(AppUserPrincipal a,UUID cycle,LocalDate from,LocalDate through,int page,int schedulePage,int coveragePage,boolean export){
        clients.authorize(a,UserClaim.MEMBER_LOANS_VIEW);return load(a,cycle,a.getMemberId(),from,through,page,schedulePage,coveragePage,false,export);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Statement staff(AppUserPrincipal a,UUID cycle,UUID client,LocalDate from,LocalDate through,int page,int schedulePage,boolean export){return staff(a,cycle,client,from,through,page,schedulePage,0,export);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Statement staff(AppUserPrincipal a,UUID cycle,UUID client,LocalDate from,LocalDate through,int page,int schedulePage,int coveragePage,boolean export){
        access.requireActor(a,"GROUP_LENDING_VIEW");access.requireActor(a,"LOAN_RECORDING_VIEW");if(export){access.requireActor(a,"GROUP_LENDING_EXPORT");access.requireActor(a,"LOAN_RECORDING_EXPORT");access.requireActor(a,"FINANCIAL_REPORTS_EXPORT");}
        return load(a,cycle,client,from,through,page,schedulePage,coveragePage,true,export);
    }
    private Statement load(AppUserPrincipal a,UUID cycle,UUID client,LocalDate from,LocalDate through,int page,int schedulePage,int coveragePage,boolean staff,boolean export){
        var today=clock.today();through=through==null?today:through;
        if(coveragePage<0||coveragePage>10000||cycle==null||client==null||page<0||page>10000||schedulePage<0||schedulePage>24||through.isAfter(today)||(from!=null&&(from.isAfter(through)||from.getYear()<1))||through.getYear()<1)throw new IllegalArgumentException("group.error.query");
        var source=statements.source(a,cycle,client,today);var schedule=statements.schedule(a,cycle,client,export?601:26,export?0:schedulePage*25);
        var credits=statements.credits(a,cycle,client,from,through,export?5001:26,export?0:page*25);
        if(export&&(credits.size()>5000||schedule.size()>600))throw new IllegalArgumentException("group.error.statementRange");
        boolean receiptLinks=false;if(staff)try{access.requireActor(a,"ACCOUNTING_BUSINESS_VIEW");receiptLinks=true;}catch(AccessDeniedException ignored){}
        return new Statement(source,today,clock.now(),from,through,export?schedule:schedule.stream().limit(25).toList(),export?credits:credits.stream().limit(25).toList(),page,!export&&credits.size()>25,schedulePage,!export&&schedule.size()>25,staff,receiptLinks,coverage.trace(a,cycle,client,from,through,coveragePage,!staff,export));
    }
}
