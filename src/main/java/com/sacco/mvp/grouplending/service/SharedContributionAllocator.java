package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.ReceiptContribution;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.AllocationPreview;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos.*;
import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.Balance;
import java.math.BigDecimal;
import java.util.*;

/** Matches actual contributors to the signed pro-rata cash-component allocations, without pricing new debts. */
public final class SharedContributionAllocator {
    private SharedContributionAllocator() {}
    public static List<ReceiptContribution> allocate(Form form,Beneficiary loan,Map<UUID,Payer> payers,AllocationPreview cash,List<Balance> balances){
        var principal=GroupSharedLoanService.split(cash.principal(),balances.stream().map(Balance::remainingPrincipal).toList());
        var interest=GroupSharedLoanService.split(cash.interest(),balances.stream().map(Balance::remainingInterest).toList());
        var charges=GroupSharedLoanService.split(cash.charges(),balances.stream().map(Balance::remainingCharges).toList());
        var result=new ArrayList<ReceiptContribution>();int beneficiary=0,source=0;
        for(var row:form.getRows()){
            source++;var payer=payers.get(row.getPayerId());var available=row.getAmount();
            if(!loan.loanId().equals(row.getLoanId())||payer==null)throw new IllegalArgumentException("group.error.attribution");
            while(available.signum()>0){
                while(beneficiary<balances.size()&&principal.get(beneficiary).add(interest.get(beneficiary)).add(charges.get(beneficiary)).signum()==0)beneficiary++;
                if(beneficiary==balances.size())throw new IllegalArgumentException("group.error.attribution");
                var person=balances.get(beneficiary);var i=available.min(interest.get(beneficiary)).setScale(2,java.math.RoundingMode.UNNECESSARY);var c=available.subtract(i).min(charges.get(beneficiary)).setScale(2,java.math.RoundingMode.UNNECESSARY);var p=available.subtract(i).subtract(c).min(principal.get(beneficiary)).setScale(2,java.math.RoundingMode.UNNECESSARY);var amount=p.add(i).add(c);
                principal.set(beneficiary,principal.get(beneficiary).subtract(p));interest.set(beneficiary,interest.get(beneficiary).subtract(i));charges.set(beneficiary,charges.get(beneficiary).subtract(c));available=available.subtract(amount);
                int line=result.size()+1;result.add(new ReceiptContribution(GroupContributionAllocator.key(form.getRequestKey(),"CONTRIBUTION/"+line),line,loan.loanId(),loan.loanNumber(),payer.id(),payer.number(),payer.name(),person.client(),person.number(),person.name(),row.getDescription().strip(),amount,p,i,c,null,source));
            }
        }
        if(principal.stream().anyMatch(v->v.signum()!=0)||interest.stream().anyMatch(v->v.signum()!=0)||charges.stream().anyMatch(v->v.signum()!=0)||result.isEmpty()||result.size()>99)throw new IllegalArgumentException("group.error.attribution");
        return List.copyOf(result);
    }
}
