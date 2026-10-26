package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.*;
import com.sacco.mvp.grouplending.repository.GroupSharedLoanRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupSharedLoanService {
    private final GroupSharedLoanRepository shared;
    private final GeneralLedgerService access;
    private final MemberDirectoryService members;
    private final ApplicationClock clock;
    private final AuditService audit;
    @Transactional(readOnly=true) public Optional<Funding> funding(AppUserPrincipal a,UUID loan){access.requireActor(a,"DISBURSEMENT_QUEUE_VIEW");return shared.funding(a,loan);}
    @Transactional(propagation=Propagation.MANDATORY) public void lock(AppUserPrincipal a,UUID loan){access.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");shared.lock(a,loan);}
    @Transactional(propagation=Propagation.MANDATORY) public boolean execute(AppUserPrincipal a,PostForm form,UUID posting,UUID journal,LocalDate first,BigDecimal deductions){
        if(!shared.shared(a,form.getLoanId()))return false;
        access.requireActor(a,"GROUP_LENDING_EXECUTE");var f=shared.funding(a,form.getLoanId()).orElseThrow();
        check(f.ready()&&Boolean.TRUE.equals(form.getSharedAccepted())&&Objects.equals(f.hash(),form.getSharedPacketHash()),"sharedFunding");
        check(Objects.equals(first,f.firstPaymentDate())&&f.principal().compareTo(form.getAmount())==0&&f.net().compareTo(form.getAmount().subtract(deductions))==0,"sharedTerms");
        var refs=form.getSharedPayoutReferences();check(Boolean.TRUE.equals(form.getSharedPayoutsConfirmed())&&refs!=null&&refs.size()==f.payouts().size(),"sharedTransfers");
        var unique=new HashSet<String>();for(var reference:refs){check(reference!=null&&!reference.isBlank()&&reference.length()<=100&&reference.chars().noneMatch(Character::isISOControl)&&unique.add(reference.strip()),"sharedTransfers");}
        var actor=members.find(a.getMemberId()).orElseThrow();check(actor.getSignatureText()!=null&&!actor.getSignatureText().isBlank(),"organizationSignature");
        shared.execute(a,f,form.getLoanId(),posting,journal,refs,clock.now());
        audit.logEvent("GROUP_LENDING",f.cycle(),"SHARED_LENDER_SIGNED",a.getMemberId(),AuditEventStatus.SUCCESS,"Complete shared funding","GROUP_CYCLE",f.cycle().toString(),a.getSaccoId(),a.getStationId(),Map.of("loan",form.getLoanId(),"posting",posting,"packet",f.hash()));return true;
    }
    @Transactional(readOnly=true) public List<Balance> balances(AppUserPrincipal a,UUID loan){access.requireActor(a,"GROUP_LENDING_VIEW");access.requireActor(a,"LOAN_RECORDING_VIEW");return shared.balances(a,loan);}
    @Transactional(propagation=Propagation.MANDATORY) public void payment(AppUserPrincipal a,UUID loan,Posting posting,String payer){
        if(!shared.shared(a,loan))return;access.requireActor(a,"GROUP_LENDING_POST");
        check(payer!=null&&!payer.isBlank()&&payer.length()<=160&&payer.chars().noneMatch(Character::isISOControl),"sharedPayer");
        var balances=shared.balances(a,loan);var principal=split(posting.principal(),balances.stream().map(Balance::remainingPrincipal).toList());
        var interest=split(posting.interest(),balances.stream().map(Balance::remainingInterest).toList());
        var charges=split(posting.charges(),balances.stream().map(Balance::remainingCharges).toList());
        var afterInterest=new ArrayList<BigDecimal>();for(int i=0;i<balances.size();i++)afterInterest.add(balances.get(i).remainingInterest().subtract(interest.get(i)));
        var excluded=split(shared.excluded(posting.id()),afterInterest);var rows=new ArrayList<Movement>();
        for(int i=0;i<balances.size();i++)rows.add(new Movement(posting.id(),balances.get(i).client(),payer.strip(),principal.get(i),interest.get(i),charges.get(i),excluded.get(i),null));
        shared.movements(a,loan,rows);
    }
    @Transactional(propagation=Propagation.MANDATORY) public void reverse(AppUserPrincipal a,UUID loan,UUID original,UUID posting){
        if(!shared.shared(a,loan))return;access.requireActor(a,"GROUP_LENDING_REVERSE");var rows=shared.movements(a,original);check(!rows.isEmpty(),"sharedAttribution");
        shared.movements(a,loan,rows.stream().map(r->new Movement(posting,r.client(),r.payer(),r.principal(),r.interest(),r.charges(),r.excluded(),original)).toList());
    }
    // Cumulative cent allocation conserves the component exactly and never exceeds a remaining share.
    static List<BigDecimal> split(BigDecimal amount,List<BigDecimal> remaining){
        var total=remaining.stream().reduce(BigDecimal.ZERO,BigDecimal::add);check(amount!=null&&amount.signum()>=0&&remaining.stream().allMatch(x->x.signum()>=0)&&amount.compareTo(total)<=0,"sharedAttribution");
        var result=new ArrayList<BigDecimal>();BigDecimal cumulative=BigDecimal.ZERO,used=BigDecimal.ZERO;
        for(var value:remaining){cumulative=cumulative.add(value);var next=total.signum()==0?BigDecimal.ZERO:amount.multiply(cumulative).divide(total,2,RoundingMode.DOWN);result.add(next.subtract(used));used=next;}return result;
    }
    private static void check(boolean valid,String key){if(!valid)throw new IllegalArgumentException("recording.error."+key);}
}
