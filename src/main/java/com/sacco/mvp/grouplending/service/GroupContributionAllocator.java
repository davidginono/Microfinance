package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.ReceiptContribution;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.AllocationPreview;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Attributes actual loan cash components in the disclosed contributor order; it never calculates loan interest. */
public final class GroupContributionAllocator {
    private GroupContributionAllocator() {}
    public static List<ReceiptContribution> allocate(Form form,Map<UUID,Beneficiary> borrowers,Map<UUID,Payer> payers,Map<UUID,AllocationPreview> components){
        var remaining=new HashMap<>(components);var result=new ArrayList<ReceiptContribution>();int line=0;
        for(var row:form.getRows()){
            var amounts=remaining.get(row.getLoanId());var borrower=borrowers.get(row.getLoanId());var payer=payers.get(row.getPayerId());
            var interest=row.getAmount().min(amounts.interest());var charge=row.getAmount().subtract(interest).min(amounts.charges());var principal=row.getAmount().subtract(interest).subtract(charge);
            if(principal.compareTo(amounts.principal())>0)throw new IllegalArgumentException("group.error.attribution");
            remaining.put(row.getLoanId(),new AllocationPreview(amounts.principal().subtract(principal),amounts.interest().subtract(interest),amounts.principalBalance(),amounts.charges().subtract(charge)));
            result.add(new ReceiptContribution(key(form.getRequestKey(),"CONTRIBUTION/"+(++line)),line,row.getLoanId(),borrower.loanNumber(),payer.id(),payer.number(),payer.name(),
                borrower.clientId(),borrower.clientNumber(),borrower.name(),row.getDescription().strip(),row.getAmount(),principal,interest,charge,null));
        }
        if(remaining.values().stream().anyMatch(v->v.principal().signum()!=0 || v.interest().signum()!=0 || v.charges().signum()!=0))throw new IllegalArgumentException("group.error.attribution");
        return List.copyOf(result);
    }
    static UUID key(UUID request,String part){return UUID.nameUUIDFromBytes(("GROUP_RECEIPT/"+request+"/"+part).getBytes(StandardCharsets.UTF_8));}
}
