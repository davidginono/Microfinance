package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.grouplending.dto.GroupCycleStatementDtos.*;
import com.sacco.mvp.grouplending.dto.GroupOrganizationAccountDtos.Account;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupOrganizationAccountService {
    private final GroupOrganizationAccountRepository accounts;
    private final GroupCycleStatementRepository statements;
    private final GroupConsentService clients;
    private final ApplicationClock clock;
    private final ReportExportLimiter limiter;
    private void authorize(AppUserPrincipal a,boolean export){
        if(a==null||a.isStaffSession())throw new AccessDeniedException("Named client session required");
        if(export)clients.authorize(a,UserClaim.MEMBER_LOANS_VIEW,UserClaim.LOAN_DOCUMENTS_VIEW,UserClaim.LOAN_DOCUMENTS_EXPORT);
        else clients.authorize(a,UserClaim.MEMBER_LOANS_VIEW);
    }
    @Transactional(readOnly=true) public Page<Account> list(AppUserPrincipal a,int page){authorize(a,false);if(page<0||page>10000)throw new IllegalArgumentException("group.error.query");var rows=accounts.list(a,clock.today(),page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public boolean canExport(AppUserPrincipal a){try{authorize(a,true);return true;}catch(AccessDeniedException ignored){return false;}}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30)
    public Statement statement(AppUserPrincipal a,UUID cycle,LocalDate from,LocalDate through,int page,boolean export){
        authorize(a,export);var today=clock.today();var end=through==null?today:through;
        if(cycle==null||page<0||page>10000||end.isAfter(today)||end.getYear()<1||from!=null&&(from.getYear()<1||from.isAfter(end)))throw new IllegalArgumentException("group.error.query");
        if(!accounts.permitted(a,cycle,today))throw new AccessDeniedException("Organization account unavailable");
        return limiter.run(()->{
            var context=statements.context(a,cycle);var loans=statements.loans(a,cycle,today);
            if(!context.model().equals("ORGANIZATION")||loans.size()!=1)throw new AccessDeniedException("Organization account unavailable");
            var movements=statements.movements(a,cycle,from,end,export?5001:26,export?0:page*25);
            if(export&&movements.size()>5000)throw new IllegalArgumentException("group.error.statementRange");
            return new Statement(context,today,clock.now(),from,end,GroupCycleStatementService.totals(loans),loans,List.of(),export?movements:movements.stream().limit(25).toList(),List.of(),0,false,0,false,page,!export&&movements.size()>25,0,false,false,false);
        });
    }
}
