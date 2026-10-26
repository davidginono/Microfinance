package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.PostForm;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.grouplending.dto.GroupOrganizationFundingDtos.Context;
import com.sacco.mvp.grouplending.repository.GroupOrganizationFundingRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupOrganizationFundingService {
    private final GroupOrganizationFundingRepository funding;
    private final GeneralLedgerService access;
    private final MemberDirectoryService members;
    private final ApplicationClock clock;
    private final AuditService audit;
    @Transactional(readOnly=true) public Optional<Context> context(AppUserPrincipal a,UUID loan){
        access.requireActor(a,"DISBURSEMENT_QUEUE_VIEW");return funding.context(a,loan,clock.today());
    }
    @Transactional(propagation=Propagation.MANDATORY) public void lock(AppUserPrincipal a,UUID loan){
        access.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");funding.lock(a,loan);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void execute(AppUserPrincipal a,PostForm f,UUID posting,UUID journal,LocalDate first,BigDecimal deductions){
        // Missing agreement acceptance retains the existing safe failure for earlier clients.
        check(Boolean.TRUE.equals(f.getOrganizationAccepted()) && f.getOrganizationPacketHash()!=null,"organizationFundingPending");
        access.requireActor(a,"GROUP_LENDING_EXECUTE");
        var current=members.find(a.getMemberId()).orElseThrow();check(current.getSignatureText()!=null&&!current.getSignatureText().isBlank(),"organizationSignature");
        var c=funding.context(a,f.getLoanId(),clock.today()).orElseThrow(()->new IllegalArgumentException("recording.error.organizationFundingPending"));
        check(c.ready() && Objects.equals(c.hash(),f.getOrganizationPacketHash()),"organizationFundingPending");
        check(Objects.equals(first,c.firstPaymentDate()) && c.principal().compareTo(f.getAmount())==0
            && c.net().compareTo(f.getAmount().subtract(deductions))==0,"organizationTerms");
        funding.execute(a,f.getLoanId(),posting,journal,c.hash(),clock.now());
        audit.logEvent("GROUP_LENDING",c.cycle(),"ORGANIZATION_LENDER_SIGNED",a.getMemberId(),AuditEventStatus.SUCCESS,
            "Lender executed the frozen organization agreement with its funding","GROUP_CYCLE",c.cycle().toString(),a.getSaccoId(),a.getStationId(),Map.of("loan",f.getLoanId(),"packet",c.hash(),"posting",posting));
    }
    private static void check(boolean value,String key){if(!value)throw new IllegalArgumentException("recording.error."+key);}
}
