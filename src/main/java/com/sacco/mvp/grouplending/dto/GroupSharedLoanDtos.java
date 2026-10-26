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
        BigDecimal paidPrincipal,BigDecimal paidInterest,BigDecimal paidCharges,BigDecimal excludedInterest,boolean fundingReversed) {
        public BigDecimal remainingPrincipal(){return fundingReversed?new BigDecimal("0.00"):principal.subtract(paidPrincipal);}
        public BigDecimal remainingInterest(){return fundingReversed?new BigDecimal("0.00"):interest.subtract(paidInterest).subtract(excludedInterest);}
        public BigDecimal remainingCharges(){return fundingReversed?new BigDecimal("0.00"):charges.subtract(paidCharges);}
    }
    public record Movement(UUID posting,UUID client,String payer,BigDecimal principal,BigDecimal interest,BigDecimal charges,BigDecimal excluded,UUID reverses) {}
}
