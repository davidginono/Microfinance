package com.sacco.mvp.grouplending.dto;

import com.sacco.mvp.service.PaymentDetailsService.PaymentDetailsView;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class GroupOrganizationPayoutDtos {
    private GroupOrganizationPayoutDtos() {}
    public record Confirmation(UUID client,String name,String number,OffsetDateTime at,boolean otpRequired) {}
    public record Packet(UUID cycle,UUID loan,String number,String organization,String hash,BigDecimal principal,
        BigDecimal deductions,BigDecimal net,PaymentDetailsView destination,int signedCount,int requiredSignatures,
        String state,String loanStatus,boolean currentAuthority,boolean fullDocument,boolean signatureReady,boolean otpRequired,Confirmation confirmation) {
        public boolean canConfirm(){return confirmation==null && currentAuthority && fullDocument && net!=null && net.signum()>0 && state.equals("SUBMITTED")
            && loanStatus.equals("READY_FOR_DISBURSEMENT") && signedCount>=requiredSignatures;}
    }
}
