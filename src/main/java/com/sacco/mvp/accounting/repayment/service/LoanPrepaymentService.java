package com.sacco.mvp.accounting.repayment.service;

import com.sacco.mvp.accounting.repayment.repository.LoanPrepaymentRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanPrepaymentService {
    private final LoanPrepaymentRepository repository;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final com.sacco.mvp.accounting.service.VoucherService vouchers;
    private final com.sacco.mvp.grouplending.service.GroupSharedLoanService shared;
    public Optional<LoanPrepaymentRepository.Terms> terms(UUID loan){return repository.terms(loan);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void bind(LoanLedger ledger,Map<String,Object> snapshot) {
        var terms=LoanPrepaymentTerms.read(snapshot.get("prepaymentTerms"));
        if(terms!=null)repository.bind(ledger,terms,InterestMethod.valueOf(snapshot.get("interestMethod").toString()),"CONTRACT",null,null,clock.now());
    }
    public Map<UUID,BigDecimal> earned(UUID loan,LocalDate date){return repository.earned(loan,date);}
    public LocalDate latestWaiverDate(UUID loan){return repository.latestDate(loan);}
    public BigDecimal charges(LoanLedger l){return repository.charges(l);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void adopt(LoanLedger ledger,AppUserPrincipal actor,InterestMethod method,String reason){
        requireActor(actor,ledger);
        require(ledger.getNextSequence()==1 && terms(ledger.getLoanApplicationId()).isEmpty(),"state");
        require(reason!=null && !reason.isBlank() && reason.strip().length()<=500,"reason");
        repository.bind(ledger,LoanPrepaymentTerms.defaults(),method,"EXPLICIT_ADOPTION",actor.getMemberId(),reason.strip(),clock.now());
        audit.logEvent("LOAN_REPAYMENT",ledger.getLoanApplicationId(),"PREPAYMENT_TERMS_ADOPTED",actor.getMemberId(),AuditEventStatus.SUCCESS,reason.strip(),"LOAN",ledger.getLoanId(),ledger.getSaccoId(),ledger.getStationId(),Map.of("terms",LoanPrepaymentTerms.defaults().snapshot()));
    }
    public void checkReversal(UUID loan,long sequence){require(!repository.laterWaiver(loan,sequence),"latest");}
    public List<LoanPrepaymentRepository.Waiver> history(LoanLedger l,int page){require(page>=0 && page<=10000,"input");return repository.history(l,page);}

    /** Returns per-period rebates; contractual interest remains unchanged. */
    public Map<UUID,BigDecimal> reductions(LoanLedger ledger,List<LoanLedgerInstallment> rows,LocalDate date,
                 Map<UUID,BigDecimal> prepaid,Map<UUID,BigDecimal> earned,boolean full) {
        var found=terms(ledger.getLoanApplicationId());if(found.isEmpty())return Map.of();var policy=found.get();
        if(!full && policy.rules().partialInterest()!=LoanPrepaymentTerms.PartialInterest.RECALCULATE)return Map.of();
        var result=new LinkedHashMap<UUID,BigDecimal>();LocalDate start=ledger.getDisbursementDate();BigDecimal basis=ledger.getPrincipal();
        BigDecimal totalAdvance=prepaid.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add),remainingAdvance=totalAdvance;
        for(var row:rows){
            if(row.getDueDate().isAfter(date)){
                var unpaid=row.getInterest().subtract(row.getInterestPaid()).subtract(row.getInterestExcluded()).subtract(row.getInterestWaived());
                var earnedUnpaid=earned.getOrDefault(row.getId(),BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP).subtract(row.getInterestPaid()).max(BigDecimal.ZERO);
                var unearned=unpaid.subtract(earnedUnpaid).max(BigDecimal.ZERO);
                BigDecimal reduction=unearned;
                if(!full){
                    BigDecimal reductionPrincipal=policy.method()==InterestMethod.FLAT_RATE?totalAdvance:remainingAdvance;
                    var unused=BigDecimal.ONE.subtract(LoanAmortizationCalculator.periodFraction(start,row.getDueDate(),date,policy.rules().dayBasis()));
                    reduction=LoanAmortizationCalculator.prepaidInterestReduction(row.getInterest(),reductionPrincipal,policy.method()==InterestMethod.FLAT_RATE?ledger.getPrincipal():basis,unused,unearned);
                }
                if(reduction.signum()>0)result.put(row.getId(),reduction);
            }
            start=row.getDueDate();basis=basis.subtract(row.getPrincipal());remainingAdvance=remainingAdvance.subtract(prepaid.getOrDefault(row.getId(),BigDecimal.ZERO));
        }
        return result;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public UUID waive(LoanLedger l,List<LoanLedgerInstallment> rows,AppUserPrincipal actor,UUID key,LocalDate date,BigDecimal amount,String reason,UUID reverses) {
        requireActor(actor,l);
        require(key!=null && date!=null && !date.isAfter(clock.today()) && !date.isBefore(l.getDisbursementDate()),"input");
        require(reason!=null && !reason.isBlank() && reason.strip().length()<=500,"reason");reason=reason.strip();
        require(amount!=null && amount.signum()>0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");
        shared.authorizeWaiver(actor,l.getLoanApplicationId(),reverses);
        var retry=repository.request(l,key);if(retry.isPresent()){
            var r=retry.get();require(r.loanId().equals(l.getLoanApplicationId()) && r.actor().equals(actor.getMemberId()) && r.date().equals(date) && r.amount().compareTo(amount)==0 && r.reason().equals(reason) && Objects.equals(r.reverses(),reverses),"retry");return r.id();
        }
        vouchers.requireInterestWaiverPeriod(actor,date);
        var last=latestWaiverDate(l.getLoanApplicationId());require(last==null || !date.isBefore(last),"date");require(l.getLastPaymentDate()==null || !date.isBefore(l.getLastPaymentDate()),"date");
        var parts=new LinkedHashMap<UUID,BigDecimal>();
        if(reverses==null){
            var earned=earned(l.getLoanApplicationId(),date);BigDecimal left=amount;
            for(var row:rows){var due=earned.getOrDefault(row.getId(),BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP).subtract(row.getInterestPaid()).max(BigDecimal.ZERO);var take=left.min(due);if(take.signum()>0){parts.put(row.getId(),take);left=left.subtract(take);}}
            require(left.signum()==0,"amount");
        }else{
            var original=repository.waiver(l,reverses).orElseThrow(()->new IllegalArgumentException("prepayment.error.unavailable"));
            require(original.reverses()==null && original.reversedBy()==null && !original.actor().equals(actor.getMemberId()) && original.amount().compareTo(amount)==0 && !date.isBefore(original.date()),"checker");
            require(!repository.laterPayment(l.getLoanApplicationId(),original.sequence()) && !repository.laterWaiver(l.getLoanApplicationId(),original.sequence()),"latest");parts.putAll(repository.parts(reverses));
        }
        for(var row:rows)if(parts.containsKey(row.getId()))row.setInterestWaived(reverses==null?row.getInterestWaived().add(parts.get(row.getId())):row.getInterestWaived().subtract(parts.get(row.getId())));
        UUID id=UUID.randomUUID();repository.waiver(l,id,key,date,amount,actor.getMemberId(),reason,reverses,clock.now(),parts);shared.waiver(actor,l.getLoanApplicationId(),id,amount,reverses);l.setNextSequence(l.getNextSequence()+1);
        audit.logEvent("LOAN_REPAYMENT",id,reverses==null?"INTEREST_WAIVED":"INTEREST_WAIVER_REVERSED",actor.getMemberId(),AuditEventStatus.SUCCESS,reason,"LOAN",l.getLoanId(),l.getSaccoId(),l.getStationId(),Map.of("loan",l.getLoanApplicationId(),"amount",amount,"date",date,"reverses",Objects.toString(reverses,"")));
        return id;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void configure(LoanProductSetting product,AppUserPrincipal actor,LoanPrepaymentTerms terms,String reason){
        if(actor==null || !actor.isStaffSession() || !actor.getClaims().contains(UserClaim.LOAN_PRODUCTS_CONFIGURE.name()) || !Objects.equals(product.getSaccoId(),actor.getSaccoId()))throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        require(reason!=null && !reason.isBlank() && reason.strip().length()<=500,"reason");
        var before=LoanPrepaymentTerms.product(product);product.setPrepaymentSchedule(terms.schedule());product.setPartialPrepaymentInterest(terms.partialInterest());product.setSettlementDayBasis(terms.dayBasis());
        audit.logEvent("ACCOUNTING",product.getId(),"LOAN_PREPAYMENT_TERMS_CONFIGURED",actor.getMemberId(),AuditEventStatus.SUCCESS,reason.strip(),"LOAN_PRODUCT",product.getDisplayCode(),product.getSaccoId(),null,Map.of("before",before.snapshot(),"after",terms.snapshot()));
    }
    private static void require(boolean condition,String key){if(!condition)throw new IllegalArgumentException("prepayment.error."+key);}
    private static void requireActor(AppUserPrincipal actor,LoanLedger ledger){
        if(actor==null || !actor.isStaffSession() || !actor.getClaims().contains(UserClaim.LOAN_INTEREST_WAIVE.name()) || !Objects.equals(actor.getSaccoId(),ledger.getSaccoId()) || !Objects.equals(actor.getStationId(),ledger.getStationId()))throw new org.springframework.security.access.AccessDeniedException("Forbidden");
    }
}
