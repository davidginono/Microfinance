package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Detail;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.Balance;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupSharedStatementService {
    private final GroupCycleService cycles;
    private final GroupSharedLoanService shared;
    private final LoanRecordingService loans;
    private final GeneralLedgerService access;
    public record Statement(UUID cycle,Detail parent,List<Balance> members,boolean funded) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Statement statement(AppUserPrincipal a,UUID cycle,boolean export){
        if(export){access.requireActor(a,"GROUP_LENDING_EXPORT");loans.authorize(a,"EXPORT");}
        var facility=cycles.sharedFacility(a,cycle).orElseThrow(()->new AccessDeniedException("Shared facility unavailable"));
        var parent=loans.detail(a,facility.loanId());var rows=shared.balances(a,facility.loanId());
        boolean funded=Set.of("DISBURSED","PAR","DEFAULTED","WRITTEN_OFF","PAID","DISBURSEMENT_REVERSED").contains(parent.loan().status());
        return new Statement(cycle,parent,rows,funded);
    }
}
