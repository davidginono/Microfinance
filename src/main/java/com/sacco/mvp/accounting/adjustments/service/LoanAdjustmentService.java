package com.sacco.mvp.accounting.adjustments.service;

import com.sacco.mvp.accounting.adjustments.dto.LoanAdjustmentDtos.*;
import com.sacco.mvp.accounting.adjustments.repository.LoanAdjustmentRepository;
import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanAdjustmentService {
    private final LoanAdjustmentRepository adjustments;
    private final GeneralLedgerService accounting;
    private final VoucherRepository periods;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final com.sacco.mvp.accounting.charges.service.LoanChargeService charges;
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    public void authorize(AppUserPrincipal a,String action){accounting.requireActor(a,"LOAN_ADJUSTMENTS_"+action);}
    @Transactional(readOnly=true) public Request view(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return adjustments.find(a,id);}
    @Transactional(readOnly=true) public Context context(AppUserPrincipal a,UUID loan){authorize(a,"VIEW");return adjustments.context(a,loan,false);}
    @Transactional(readOnly=true) public Choice selectedAccount(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return adjustments.selectedAccount(a,id);}
    @Transactional(readOnly=true) public Page list(AppUserPrincipal a,String state,String search,int page,String sort,String direction){authorize(a,"VIEW");query(search,page);check(Set.of("","PENDING","APPROVED","REJECTED").contains(state),"parameters");var rows=adjustments.list(a,state,search,page,sort,direction);return new Page(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public List<Choice> choices(AppUserPrincipal a,String category,String search,int page){authorize(a,"VIEW");query(search,page);return switch(category){case "loans"->adjustments.loans(a,search,page);case "allowance"->adjustments.accounts(a,"ASSET","ALLOWANCE",search,page);case "expense"->adjustments.accounts(a,"EXPENSE","EXPENSE",search,page);default->throw error("parameters");};}
    @Transactional public UUID request(AppUserPrincipal a,Form f){
        authorize(a,"CREATE");validate(f);var hash=hash(f);adjustments.lockKey(a,f.getRequestKey());var retry=adjustments.retry(a,f.getRequestKey(),hash);if(retry.isPresent())return retry.get().id();
        var c=adjustments.context(a,f.getLoanId(),true);check(!adjustments.pending(c.id()),"pending");date(f,c);
        BigDecimal amount,allowanceChange=ZERO,principalChange=ZERO;UUID debit,credit,reverses=null;
        switch(f.getKind()){
            case PROVISION -> {
                check(Set.of("DISBURSED","PAR","DEFAULTED","PAID").contains(c.status()),"state");
                var target=money(f.getTargetAllowance());check(target.signum()>=0 && target.compareTo(c.outstanding())<=0,"target");
                check(adjustments.account(a,f.getAllowanceAccountId(),"ASSET","ALLOWANCE") && adjustments.account(a,f.getExpenseAccountId(),"EXPENSE","EXPENSE"),"accounts");
                check(c.allowance().signum()==0 || Objects.equals(c.allowanceAccount(),f.getAllowanceAccountId()),"allowanceAccount");
                allowanceChange=target.subtract(c.allowance());amount=allowanceChange.abs();check(amount.signum()>0,"unchanged");
                debit=allowanceChange.signum()>0?f.getExpenseAccountId():f.getAllowanceAccountId();credit=allowanceChange.signum()>0?f.getAllowanceAccountId():f.getExpenseAccountId();
            }
            case WRITE_OFF -> {
                active(c);amount=c.outstanding();check(amount.signum()>0 && c.writtenOff().signum()==0,"state");
                check(c.allowance().compareTo(amount)==0 && c.allowanceAccount()!=null,"fullyProvided");
                debit=c.allowanceAccount();credit=c.principalAccount();allowanceChange=amount.negate();principalChange=amount.negate();
            }
            case WRITE_BACK -> {
                check(c.status().equals("WRITTEN_OFF") && c.writtenOff().compareTo(c.outstanding())==0 && c.writtenOff().signum()>0 && c.writeOffJournal()!=null,"state");
                amount=c.writtenOff();debit=c.principalAccount();credit=c.allowanceAccount();allowanceChange=amount;principalChange=amount;reverses=c.writeOffJournal();
            }
            case DISBURSEMENT_REVERSAL -> {
                charges.checkFundingCorrection(a,c.id());
                active(c);check(c.sequence()==1 && c.outstanding().compareTo(c.principal())==0,"repayments");
                check(c.allowance().signum()==0 && c.writtenOff().signum()==0,"allowanceFirst");
                amount=c.principal();debit=c.moneyAccount();credit=c.principalAccount();principalChange=amount.negate();reverses=c.disbursementJournal();
            }
            default -> throw error("parameters");
        }
        UUID id=UUID.randomUUID();adjustments.insert(a,id,f,c,hash,amount,allowanceChange,principalChange,debit,credit,reverses,clock.now());
        event(a,id,"LOAN_ADJUSTMENT_REQUESTED",Map.of("loan",c.id(),"kind",f.getKind().name(),"amount",amount));return id;
    }
    @Transactional public UUID decide(AppUserPrincipal a,UUID id,UUID key,boolean approve,String reason){
        authorize(a,"APPROVE");check(key!=null,"parameters");text(reason);adjustments.lockKey(a,key);var q=adjustments.find(a,id);
        // The loan lock also serializes concurrent decisions and repayment commands.
        var c=adjustments.context(a,q.loanId(),true);q=adjustments.find(a,id);
        if(!q.state().equals("PENDING")){check(adjustments.decisionRetry(a,id,key,approve,reason.strip()),"decided");return id;}
        check(!q.requestedBy().equals(a.getMemberId()),"independent");
        if(approve){
            check(q.sequence()==c.sequence() && Objects.equals(q.lastAdjustment(),c.lastAdjustment()) && q.outstanding().compareTo(c.outstanding())==0 && q.allowance().compareTo(c.allowance())==0 && q.writtenOff().compareTo(c.writtenOff())==0,"changed");
            var f=new Form();f.setDate(q.date());date(f,c);
            if(q.kind()==Kind.DISBURSEMENT_REVERSAL)check(!c.disbursedBy().equals(a.getMemberId()),"independent");
            if(q.kind()==Kind.WRITE_BACK)check(!Objects.equals(c.writeOffPostedBy(),a.getMemberId()),"independent");
            var now=clock.now();UUID period=periods.period(a.getSaccoId(),q.date(),a.getMemberId(),now),journal=UUID.randomUUID();
            adjustments.decide(a,id,key,true,q.date(),reason.strip(),journal,now);
            if(q.kind()==Kind.DISBURSEMENT_REVERSAL && !charges.frozen(a,c.id()).terms().isEmpty()){
                charges.checkFundingCorrection(a,c.id());adjustments.beginPosting(a,q,key,journal,period,now);
                var lines=charges.correctFunding(a,c.id(),q.reversesJournalId(),journal,key,q.date(),q.evidence(),q.reason());adjustments.postingLines(a,q,journal,lines);adjustments.finishPosting(journal,now);
            }else adjustments.post(a,q,key,journal,period,now);
            switch(q.kind()){case WRITE_OFF->adjustments.status(c.id(),"WRITTEN_OFF",clock.now());case WRITE_BACK->adjustments.status(c.id(),"DEFAULTED",clock.now());case DISBURSEMENT_REVERSAL->adjustments.status(c.id(),"DISBURSEMENT_REVERSED",clock.now());default->{}}
        }else adjustments.decide(a,id,key,false,q.date(),reason.strip(),null,clock.now());
        event(a,id,approve?"LOAN_ADJUSTMENT_APPROVED":"LOAN_ADJUSTMENT_REJECTED",Map.of("loan",c.id(),"kind",q.kind().name()));return id;
    }
    public Request export(AppUserPrincipal a,UUID id){authorize(a,"EXPORT");return view(a,id);}
    private void validate(Form f){check(f!=null && f.getRequestKey()!=null && f.getLoanId()!=null && f.getKind()!=null && f.getDate()!=null,"parameters");text(f.getReason());text(f.getEvidence());}
    private void date(Form f,Context c){check(f.getDate()!=null && !f.getDate().isAfter(clock.today()) && !f.getDate().isBefore(c.disbursed()) && (c.lastDate()==null || !f.getDate().isBefore(c.lastDate())),"date");}
    private static void active(Context c){check(Set.of("DISBURSED","PAR","DEFAULTED").contains(c.status()),"state");}
    static BigDecimal money(BigDecimal v){check(v!=null && v.precision()-v.scale()<=16 && v.scale()<=2,"target");return v.setScale(2);}
    private static void text(String v){check(v!=null && !v.isBlank() && v.length()<=500,"reason");}
    private static void query(String s,int p){check(s!=null && s.length()<=100 && p>=0 && p<=10000,"parameters");}
    private static String hash(Form f){String payload=List.of(f.getLoanId(),f.getKind(),f.getDate(),Objects.toString(f.getTargetAllowance(),""),Objects.toString(f.getAllowanceAccountId(),""),Objects.toString(f.getExpenseAccountId(),""),f.getReason().strip(),f.getEvidence().strip()).toString();try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void event(AppUserPrincipal a,UUID id,String type,Map<String,Object> details){audit.logEvent("ACCOUNTING_LOAN_ADJUSTMENT",id,type,a.getMemberId(),AuditEventStatus.SUCCESS,"Loan accounting adjustment","LOAN_ADJUSTMENT",id.toString(),a.getSaccoId(),a.getStationId(),details);}
    static void check(boolean condition,String key){if(!condition)throw error(key);}
    private static IllegalArgumentException error(String key){return new IllegalArgumentException("adjustment.error."+key);}
}
