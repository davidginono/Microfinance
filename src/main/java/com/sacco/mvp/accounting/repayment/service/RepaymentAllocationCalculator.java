package com.sacco.mvp.accounting.repayment.service;

import com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.*;
import java.math.BigDecimal;

/** Component totals only; the ledger retains ownership of dated instalment allocations. */
public final class RepaymentAllocationCalculator {
    private RepaymentAllocationCalculator() {}
    public static Split allocate(BigDecimal amount,BigDecimal principalDue,BigDecimal interestDue,BigDecimal outstanding,AllocationOrder order){
        if(order==null || amount==null || amount.signum()<=0 || principalDue==null || interestDue==null || outstanding==null
            || principalDue.signum()<0 || interestDue.signum()<0 || amount.compareTo(principalDue.add(interestDue))>0)
            throw new IllegalArgumentException("repayment.error.advance");
        BigDecimal principal,interest;
        if(order==AllocationOrder.PRINCIPAL_FIRST){principal=amount.min(principalDue);interest=amount.subtract(principal);}
        else {interest=amount.min(interestDue);principal=amount.subtract(interest);}
        return new Split(principal,interest,outstanding.subtract(principal));
    }
}
