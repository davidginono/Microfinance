package com.sacco.mvp.accounting.repayment.service;

import com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.*;
import com.sacco.mvp.accounting.repayment.repository.RepaymentPolicyRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class RepaymentPolicyService {
    private final RepaymentPolicyRepository repository;
    private final GeneralLedgerService accounting;
    private final ApplicationClock clock;
    private final AuditService audit;
    @Transactional(readOnly=true)
    public Optional<Policy> latest(AppUserPrincipal a){accounting.requireActor(a,"ACCOUNTING_ACCOUNTS_VIEW");return repository.latest(a.getSaccoId());}
    @Transactional(readOnly=true)
    public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> accounts(AppUserPrincipal a,String search,int page){accounting.requireActor(a,"ACCOUNTING_ACCOUNTS_VIEW");if(search==null || search.length()>100 || page<0 || page>10000)throw new IllegalArgumentException("batch.error.input");return repository.accounts(a.getSaccoId(),search,page);}
    // The caller owns repayment authorization. A shared lock prevents settings changing during posting.
    @Transactional
    public Policy effective(AppUserPrincipal a,LocalDate date,boolean required){
        if(date==null)throw new IllegalArgumentException("batch.error.date");repository.lock(a.getSaccoId(),false);if(required && !repository.standards(a.getSaccoId()))throw new IllegalArgumentException("batch.error.policy");
        var policy=repository.effective(a.getSaccoId(),date).orElseGet(()->{if(required)throw new IllegalArgumentException("batch.error.policy");return new Policy(null,0,LocalDate.MIN,AllocationOrder.INTEREST_FIRST,null,"","",null);});
        if(policy.allocationOrder()!=AllocationOrder.INTEREST_FIRST)throw new IllegalArgumentException("batch.error.policyOrder");return policy;
    }
    @Transactional
    public UUID save(AppUserPrincipal a,PolicyForm f){
        accounting.requireActor(a,"ACCOUNTING_ACCOUNTS_UPDATE");
        if(f==null || f.requestKey()==null || f.effectiveFrom()==null || f.allocationOrder()==null || f.interestAccountId()==null || f.reason()==null || f.reason().isBlank() || f.reason().length()>500 || f.reason().chars().anyMatch(c->c<32))throw new IllegalArgumentException("batch.error.policyInput");
        if(f.allocationOrder()!=AllocationOrder.INTEREST_FIRST)throw new IllegalArgumentException("batch.error.policyOrder");
        repository.lock(a.getSaccoId(),true);var retry=repository.retry(a.getSaccoId(),a.getMemberId(),f);if(retry.isPresent())return retry.get();var prior=repository.latest(a.getSaccoId());
        if(!Objects.equals(prior.map(Policy::id).orElse(null),f.expectedVersion()))throw new IllegalArgumentException("batch.error.stale");
        LocalDate last=repository.lastPayment(a.getSaccoId());
        if((prior.isPresent() && !f.effectiveFrom().isAfter(prior.get().effectiveFrom())) || (last!=null && !f.effectiveFrom().isAfter(last) && (prior.isPresent() || f.allocationOrder()!=AllocationOrder.INTEREST_FIRST)))throw new IllegalArgumentException("batch.error.policyDate");
        if(!repository.account(a.getSaccoId(),f.interestAccountId()))throw new IllegalArgumentException("recording.error.account");
        UUID id=repository.insert(a.getSaccoId(),a.getMemberId(),f,prior.map(p->p.version()+1).orElse(1),clock.now());
        audit.logEvent("ACCOUNTING",id,"REPAYMENT_POLICY_CONFIGURED",a.getMemberId(),AuditEventStatus.SUCCESS,"Repayment settings","REPAYMENT_POLICY",id.toString(),a.getSaccoId(),a.getStationId(),Map.of("allocationOrder",f.allocationOrder().name(),"effectiveFrom",f.effectiveFrom().toString()));return id;
    }
}
