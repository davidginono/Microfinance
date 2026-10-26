package com.sacco.mvp.grouplending.service;

import java.math.*;
import java.util.*;

/** Allocates an already calculated parent component; it never prices a member loan. */
public final class GroupSharedAllocator {
    private GroupSharedAllocator() {}
    public static List<BigDecimal> split(BigDecimal amount,List<BigDecimal> principal){
        if(amount==null || amount.signum()<0 || amount.scale()>2 || principal==null || principal.isEmpty() || principal.size()>50
            || principal.stream().anyMatch(p->p==null || p.signum()<0 || p.scale()>2))throw new IllegalArgumentException("group.error.amount");
        BigDecimal total=principal.stream().reduce(BigDecimal.ZERO,BigDecimal::add),weight=BigDecimal.ZERO,previous=new BigDecimal("0.00");
        if(total.signum()==0){if(amount.signum()!=0)throw new IllegalArgumentException("group.error.amount");return Collections.nCopies(principal.size(),previous);}
        var result=new ArrayList<BigDecimal>(principal.size());
        // Differences of cumulative rounded shares conserve every cent, including zero/tiny components.
        for(var p:principal){weight=weight.add(p);var cumulative=amount.multiply(weight).divide(total,2,RoundingMode.DOWN);result.add(cumulative.subtract(previous));previous=cumulative;}
        return List.copyOf(result);
    }
}
