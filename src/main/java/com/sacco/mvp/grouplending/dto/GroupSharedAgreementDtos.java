package com.sacco.mvp.grouplending.dto;

import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.Document;
import com.sacco.mvp.service.PaymentDetailsService.PaymentDetailsView;
import java.time.OffsetDateTime;
import java.util.*;

public final class GroupSharedAgreementDtos {
    private GroupSharedAgreementDtos() {}
    public record Confirmation(OffsetDateTime at,boolean otpRequired) {}
    public record Row(UUID cycle,UUID loan,String number,String group,String clientName,String clientNumber,String hash,String state,String loanStatus,
        Document document,SharedObligation obligation,PaymentDetailsView destination,boolean consented,boolean opened,Confirmation confirmation) {
        public boolean ready(){return document!=null && consented && destination.available() && destination.destinationType()!=null
            && destination.provider()!=null&&!destination.provider().isBlank() && destination.accountHolderName()!=null&&!destination.accountHolderName().isBlank()
            && destination.accountIdentifier()!=null&&!destination.accountIdentifier().isBlank() && "SUBMITTED".equals(state)
            && !Set.of("WITHDRAWN","REJECTED","MANAGER_REJECTED","BOARD_REJECTED","LOAN_OFFICER_REJECTED","CHAIRPERSON_REJECTED","CREDIT_COMMITTEE_REJECTED","ACCOUNTANT_REJECTED","DISBURSEMENT_REVERSED","DISBURSED","PAR","DEFAULTED","WRITTEN_OFF","PAID").contains(loanStatus);}
    }
    public record Packet(Row row,Terms terms,boolean signatureReady,boolean otpRequired) {
        public boolean canConfirm(){return row.ready()&&row.opened()&&signatureReady&&row.confirmation()==null;}
    }
    public record PublicShare(String name,String number,java.math.BigDecimal principal) {}
    public record Export(Packet packet,List<PublicShare> parties,List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow> schedule,byte[] original) {}
    public record Challenge(UUID id,String hash,String packetHash,OffsetDateTime issued,OffsetDateTime expires,OffsetDateTime delivered,OffsetDateTime consumed,int attempts) {}
}
