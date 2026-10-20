package com.sacco.mvp.accounting.repayment.service;

import com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.*;
import com.sacco.mvp.accounting.repayment.repository.RepaymentPolicyRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service @RequiredArgsConstructor
public class RepaymentPolicyService {
    private final RepaymentPolicyRepository repository;
    // The caller owns repayment authorization. Allocation is built in; legacy evidence stays readable.
    @Transactional(readOnly=true)
    public Policy effective(AppUserPrincipal actor,LocalDate date,boolean requireStandards){
        if(date==null)throw new IllegalArgumentException("batch.error.date");
        if(requireStandards && !repository.standards(actor.getSaccoId()))throw new IllegalArgumentException("batch.error.policy");
        var policy=repository.effective(actor.getSaccoId(),date).orElseGet(()->new Policy(null,0,LocalDate.MIN,AllocationOrder.INTEREST_FIRST,null,"","",null));
        if(policy.allocationOrder()!=AllocationOrder.INTEREST_FIRST)throw new IllegalArgumentException("batch.error.policyOrder");
        return policy;
    }
}
