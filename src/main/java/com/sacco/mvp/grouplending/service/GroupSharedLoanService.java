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
        var balances=shared.balances(a,loan);check(balances.stream().allMatch(Balance::known),"sharedWaiverHistory");var principal=split(posting.principal(),balances.stream().map(Balance::remainingPrincipal).toList());
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
    @Transactional(readOnly=true) public List<com.sacco.mvp.accounting.dto.VoucherDtos.LoanReceiptRow> receiptRows(AppUserPrincipal a,List<com.sacco.mvp.accounting.dto.VoucherDtos.LoanReceiptRow> rows,boolean preview,String payer){
        check(rows!=null&&!rows.isEmpty()&&rows.size()<=50,"request");var ids=rows.stream().map(com.sacco.mvp.accounting.dto.VoucherDtos.LoanReceiptRow::loanId).distinct().toList();var sharedIds=shared.sharedLoans(a,ids);if(sharedIds.isEmpty())return rows;
        access.requireActor(a,"GROUP_LENDING_VIEW");access.requireActor(a,"LOAN_RECORDING_VIEW");if(preview){access.requireActor(a,"GROUP_LENDING_POST");access.requireActor(a,"LOAN_RECORDING_POST");}
        var balances=preview?shared.balances(a,new ArrayList<>(sharedIds)):Map.<UUID,List<Balance>>of();
        var evidence=preview?Map.<UUID,List<com.sacco.mvp.accounting.dto.VoucherDtos.SharedReceiptBenefit>>of():shared.receiptBenefits(a,rows.stream().filter(r->sharedIds.contains(r.loanId())).map(com.sacco.mvp.accounting.dto.VoucherDtos.LoanReceiptRow::postingId).toList());
        var result=new ArrayList<com.sacco.mvp.accounting.dto.VoucherDtos.LoanReceiptRow>();
        for(var row:rows){if(!sharedIds.contains(row.loanId())){result.add(row);continue;}List<com.sacco.mvp.accounting.dto.VoucherDtos.SharedReceiptBenefit> benefits;
            if(preview){var people=balances.getOrDefault(row.loanId(),List.of());check(!people.isEmpty(),"sharedAttribution");check(people.stream().allMatch(Balance::known),"sharedWaiverHistory");var principal=split(row.principal(),people.stream().map(Balance::remainingPrincipal).toList());var interest=split(row.interest(),people.stream().map(Balance::remainingInterest).toList());var charges=split(row.charges(),people.stream().map(Balance::remainingCharges).toList());benefits=new ArrayList<>();
                for(int i=0;i<people.size();i++){var person=people.get(i);benefits.add(new com.sacco.mvp.accounting.dto.VoucherDtos.SharedReceiptBenefit(person.client(),person.number(),person.name(),payer,principal.get(i).setScale(2),interest.get(i).setScale(2),charges.get(i).setScale(2),null));}
            }else benefits=evidence.getOrDefault(row.postingId(),List.of());
            check(!benefits.isEmpty()&&benefits.stream().map(com.sacco.mvp.accounting.dto.VoucherDtos.SharedReceiptBenefit::principal).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(row.principal())==0&&benefits.stream().map(com.sacco.mvp.accounting.dto.VoucherDtos.SharedReceiptBenefit::interest).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(row.interest())==0&&benefits.stream().map(com.sacco.mvp.accounting.dto.VoucherDtos.SharedReceiptBenefit::charges).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(row.charges())==0,"sharedAttribution");
            result.add(row.withSharedBenefits(benefits.stream().filter(b->b.amount().signum()>0||(b.excludedInterest()!=null&&b.excludedInterest().signum()>0)).toList()));
        }return List.copyOf(result);
    }
    public void authorizeWaiver(AppUserPrincipal a,UUID loan,UUID reverses){if(shared.shared(a,loan)){access.requireActor(a,"GROUP_LENDING_VIEW");access.requireActor(a,reverses==null?"GROUP_LENDING_POST":"GROUP_LENDING_REVERSE");}}
    @Transactional(propagation=Propagation.MANDATORY) public void waiver(AppUserPrincipal a,UUID loan,UUID source,BigDecimal amount,UUID reverses){
        if(!shared.shared(a,loan))return;authorizeWaiver(a,loan,reverses);check(shared.waiversComplete(a,loan,source),"sharedWaiverHistory");
        List<WaiverAllocation> rows;if(reverses!=null){var original=shared.waivers(a,reverses);check(!original.isEmpty(),"sharedWaiverHistory");rows=original.stream().map(r->new WaiverAllocation(source,r.client(),r.amount(),reverses)).toList();}
        else{var people=shared.balances(a,loan);check(!people.isEmpty(),"sharedAttribution");var amounts=split(amount,people.stream().map(Balance::remainingInterest).toList());var allocated=new ArrayList<WaiverAllocation>();for(int i=0;i<people.size();i++)allocated.add(new WaiverAllocation(source,people.get(i).client(),amounts.get(i),null));rows=allocated;}
        shared.waivers(a,loan,rows);audit.logEvent("GROUP_LENDING",source,"SHARED_INTEREST_ATTRIBUTED",a.getMemberId(),AuditEventStatus.SUCCESS,"Non-cash earned-interest allocation","LOAN",loan.toString(),a.getSaccoId(),a.getStationId(),Map.of("loan",loan,"waiver",source,"amount",amount,"reverses",Objects.toString(reverses,"")));
    }
    // Cumulative cent allocation conserves the component exactly and never exceeds a remaining share.
    static List<BigDecimal> split(BigDecimal amount,List<BigDecimal> remaining){
        var total=remaining.stream().reduce(BigDecimal.ZERO,BigDecimal::add);check(amount!=null&&amount.signum()>=0&&remaining.stream().allMatch(x->x.signum()>=0)&&amount.compareTo(total)<=0,"sharedAttribution");
        var result=new ArrayList<BigDecimal>();BigDecimal cumulative=BigDecimal.ZERO,used=BigDecimal.ZERO;
        for(var value:remaining){cumulative=cumulative.add(value);var next=total.signum()==0?BigDecimal.ZERO:amount.multiply(cumulative).divide(total,2,RoundingMode.DOWN);result.add(next.subtract(used));used=next;}return result;
    }
    private static void check(boolean valid,String key){if(!valid)throw new IllegalArgumentException("recording.error."+key);}
}
