package com.sacco.mvp.grouplending.dto;

import com.sacco.mvp.service.PaymentDetailsService.PaymentDetailsView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class GroupSharedLoanDtos {
    private GroupSharedLoanDtos() {}
    public record Payout(UUID client,String number,String name,BigDecimal amount,PaymentDetailsView destination) {}
    public record Funding(UUID cycle,String hash,BigDecimal principal,BigDecimal net,LocalDate firstPaymentDate,boolean ready,List<Payout> payouts) {}
    public record Balance(UUID client,String number,String name,BigDecimal principal,BigDecimal interest,BigDecimal charges,
        BigDecimal paidPrincipal,BigDecimal paidInterest,BigDecimal paidCharges,BigDecimal excludedInterest,BigDecimal waivedInterest,BigDecimal writtenOff,boolean fundingReversed,boolean known) {
        public Balance(UUID client,String number,String name,BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal paidPrincipal,BigDecimal paidInterest,BigDecimal paidCharges,BigDecimal excludedInterest,BigDecimal waivedInterest,boolean fundingReversed,boolean known){this(client,number,name,principal,interest,charges,paidPrincipal,paidInterest,paidCharges,excludedInterest,waivedInterest,new BigDecimal("0.00"),fundingReversed,known);}
        public Balance(UUID client,String number,String name,BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal paidPrincipal,BigDecimal paidInterest,BigDecimal paidCharges,BigDecimal excludedInterest,boolean fundingReversed){this(client,number,name,principal,interest,charges,paidPrincipal,paidInterest,paidCharges,excludedInterest,new BigDecimal("0.00"),fundingReversed,true);}
        public BigDecimal remainingPrincipal(){return fundingReversed?new BigDecimal("0.00"):principal.subtract(paidPrincipal);}
        public BigDecimal remainingInterest(){return fundingReversed?new BigDecimal("0.00"):interest.subtract(paidInterest).subtract(excludedInterest).subtract(waivedInterest);}
        public BigDecimal remainingCharges(){return fundingReversed?new BigDecimal("0.00"):charges.subtract(paidCharges);}
    }
    public record AccountingAllocation(UUID request,UUID client,BigDecimal amount,UUID reverses) {}
    public record AccountingBenefit(UUID cycle,UUID client,String number,String name,BigDecimal amount) {}
    public record WaiverAllocation(UUID waiver,UUID client,BigDecimal amount,UUID reverses) {}
    public record Movement(UUID posting,UUID client,String payer,BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal excluded,UUID reverses) {}
}
