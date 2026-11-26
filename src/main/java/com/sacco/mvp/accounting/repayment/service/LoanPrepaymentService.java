package com.sacco.mvp.accounting.repayment.service;

import com.sacco.mvp.accounting.repayment.repository.LoanPrepaymentRepository;
import com.sacco.mvp.accounting.repayment.repository.LoanPrepaymentRepository.MonthlyTerms;
import com.sacco.mvp.accounting.repayment.repository.LoanPrepaymentRepository.WaiverPart;
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
    @org.springframework.beans.factory.annotation.Value("${app.monthly-loan-interest.enabled:false}")
    private boolean monthlyInterestEnabled;

    public boolean monthlyInterestEnabled() { return monthlyInterestEnabled; }
    public Optional<LoanPrepaymentRepository.Terms> terms(UUID loan){return repository.terms(loan);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void bind(LoanLedger ledger,Map<String,Object> snapshot) {
        var terms=LoanPrepaymentTerms.read(snapshot.get("prepaymentTerms"));
        if(terms!=null) {
            repository.bind(ledger,terms,InterestMethod.valueOf(snapshot.get("interestMethod").toString()),"CONTRACT",null,null,clock.now());
            if (snapshot.get("prepaymentTerms") instanceof Map<?,?> frozen) {
                if (frozen.get("accrualMode") instanceof String mode) {
                    repository.bindAccrual(ledger,InterestAccrualMode.valueOf(mode),String.valueOf(snapshot.get("tenureUnit")));
                }
                if ("MONTHLY".equals(snapshot.get("repaymentFrequency")) && frozen.get("monthlyInterest") instanceof String payoff) {
                    repository.bindMonthly(ledger,new MonthlyTerms(ledger.getDisbursementDate(),1,EarlyPayoffInterest.valueOf(payoff),
                        null,null,null,null),clock.now());
                }
            }
        }
    }
    public Map<String,String> contractTerms(LoanProductSetting product, LoanTenure tenure) {
        var terms=LoanPrepaymentTerms.defaults();
        var snapshot=monthlyInterestEnabled && product.getResolvedRepaymentFrequency()==RepaymentFrequency.MONTHLY && !"DAYS".equals(tenure.unit())
            ? terms.monthlySnapshot(product.getEarlyPayoffInterest()) : terms.snapshot();
        if (!monthlyInterestEnabled || product.getInterestAccrualMode()==null) return snapshot;
        var configured=new LinkedHashMap<>(snapshot);
        configured.put("accrualMode",product.getInterestAccrualMode().name());
        return Map.copyOf(configured);
    }
    public Map<UUID,BigDecimal> earned(UUID loan,LocalDate date){return repository.earned(loan,date);}
    public Map<UUID,BigDecimal> collectible(UUID loan,LocalDate date) {
        return repository.interest(loan,date,true);
    }
    public Map<LocalDate,Map<UUID,BigDecimal>> accrualFractions(UUID loan,Collection<LocalDate> dates,boolean collectible) {
        return repository.accrualFractions(loan,dates,collectible);
    }
    public Optional<MonthlyTerms> monthlyTerms(LoanLedger ledger) {
        return repository.monthlyTerms(ledger,LocalDate.of(9999,12,31),null);
    }
    public Optional<MonthlyTerms> monthlyAmendment(LoanLedger ledger,UUID key) {
        return repository.monthlyTerms(ledger,LocalDate.of(9999,12,31),key);
    }
    public boolean monthlyActive(LoanLedger ledger,LocalDate date) {
        return repository.monthlyTerms(ledger,date,null).isPresent();
    }
    public int firstMonthlyInstallment(LoanLedger ledger) {
        return repository.firstMonthlyInstallment(ledger);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void amendMonthly(LoanLedger ledger,AppUserPrincipal actor,UUID key,LocalDate date,int firstPeriod,
                             EarlyPayoffInterest payoff,String agreement,String approval) {
        require(monthlyInterestEnabled && actor.getClaims().contains(UserClaim.LOAN_INTEREST_TERMS_UPDATE.name())
            && actor.isStaffSession() && Objects.equals(ledger.getSaccoId(),actor.getSaccoId())
            && Objects.equals(ledger.getStationId(),actor.getStationId()),"unavailable");
        require(date.isAfter(clock.today()) && firstPeriod>1 && agreement.length()<=160 && !approval.isBlank()
            && approval.length()<=160,"input");
        repository.bindMonthly(ledger,new MonthlyTerms(date,firstPeriod,payoff,actor.getMemberId(),key,agreement,approval),clock.now());
        audit.logEvent("LOAN_REPAYMENT",ledger.getLoanApplicationId(),"LOAN_INTEREST_TERMS_AMENDED",actor.getMemberId(),
            AuditEventStatus.SUCCESS,"Approved amendment","LOAN",ledger.getLoanId(),ledger.getSaccoId(),ledger.getStationId(),
            Map.of("effectiveDate",date,"firstInstalment",firstPeriod,"payoffInterest",payoff.name(),"agreement",agreement,"approval",approval));
    }

    public Optional<MonthlyInterestCalculator.Payoff> monthlyPayoff(LoanLedger ledger,
                List<LoanLedgerInstallment> rows, LocalDate date, BigDecimal outstandingCharges) {
        return monthlyPayoffPlan(ledger,rows,date,outstandingCharges).map(PayoffPlan::totals);
    }

    public Optional<PayoffPlan> monthlyPayoffPlan(LoanLedger ledger,List<LoanLedgerInstallment> rows,LocalDate date,
                                                 BigDecimal outstandingCharges) {
        var terms = repository.monthlyTerms(ledger,date,null);
        if (terms.isEmpty()) return Optional.empty();
        var rule = terms.get().payoffInterest();
        var elapsedInterest = rule == EarlyPayoffInterest.ELAPSED_DAYS
            ? repository.elapsedInterest(ledger,date) : Map.<UUID,BigDecimal>of();
        var instalments = new LinkedHashMap<UUID,MonthlyInterestCalculator.Instalment>();
        rows.forEach(row -> instalments.put(row.getId(),new MonthlyInterestCalculator.Instalment(row.getDueDate(),
            row.getInterest().subtract(row.getInterestExcluded()).subtract(row.getInterestWaived()),
            row.getInterestPaid(),elapsedInterest.getOrDefault(row.getId(),BigDecimal.ZERO))));
        var totals=MonthlyInterestCalculator.payoff(date,rule,ledger.getPrincipal().subtract(ledger.getPrincipalPaid()),
            outstandingCharges,List.copyOf(instalments.values()));
        var parts = new LinkedHashMap<UUID,MonthlyInterestCalculator.InterestPart>();
        instalments.forEach((id,row) -> parts.put(id,MonthlyInterestCalculator.payoffInterest(date,rule,row)));
        return Optional.of(new PayoffPlan(totals,parts));
    }
    public record PayoffPlan(MonthlyInterestCalculator.Payoff totals,Map<UUID,MonthlyInterestCalculator.InterestPart> parts) {}
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
        return reductions(ledger,rows,date,prepaid,earned,Map.of(),full);
    }
    public Map<UUID,BigDecimal> reductions(LoanLedger ledger,List<LoanLedgerInstallment> rows,LocalDate date,
                 Map<UUID,BigDecimal> prepaid,Map<UUID,BigDecimal> earned,Map<UUID,BigDecimal> configured,boolean full) {
        var found=terms(ledger.getLoanApplicationId());if(found.isEmpty())return Map.of();var policy=found.get();
        if(!full && policy.rules().partialInterest()!=LoanPrepaymentTerms.PartialInterest.RECALCULATE)return Map.of();
        int firstMonthly = firstMonthlyInstallment(ledger);
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
                    BigDecimal unused = configured.containsKey(row.getId()) ? BigDecimal.ONE.subtract(configured.get(row.getId()))
                        : row.getInstallmentNumber() >= firstMonthly
                        ? (date.isBefore(MonthlyInterestCalculator.openingDate(ledger.getDisbursementDate(),
                            row.getInstallmentNumber()==1 ? null : start)) ? BigDecimal.ONE : BigDecimal.ZERO)
                        : BigDecimal.ONE.subtract(LoanAmortizationCalculator.periodFraction(start,row.getDueDate(),date,policy.rules().dayBasis()));
                    reduction=LoanAmortizationCalculator.prepaidInterestReduction(row.getInterest(),reductionPrincipal,policy.method()==InterestMethod.FLAT_RATE?ledger.getPrincipal():basis,unused,unearned);
                }
                if(reduction.signum()>0)result.put(row.getId(),reduction);
            }
            start=row.getDueDate();basis=basis.subtract(row.getPrincipal());remainingAdvance=remainingAdvance.subtract(prepaid.getOrDefault(row.getId(),BigDecimal.ZERO));
        }
        return result;
    }
    public BigDecimal unpaidEarned(LoanLedgerInstallment row,Map<UUID,BigDecimal> earned) {
        return earned.getOrDefault(row.getId(),BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP)
            .subtract(row.getInterestPaid()).max(BigDecimal.ZERO);
    }

    public BigDecimal unpaidScheduled(LoanLedgerInstallment row) {
        return row.getInterest().subtract(row.getInterestPaid()).subtract(row.getInterestExcluded()).subtract(row.getInterestWaived());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public UUID waive(LoanLedger l,List<LoanLedgerInstallment> rows,AppUserPrincipal actor,UUID key,LocalDate date,BigDecimal amount,String reason,UUID reverses,Long sequence,Map<UUID,BigDecimal> selections) {
        requireActor(actor,l);
        require(key!=null && date!=null && !date.isAfter(clock.today()) && !date.isBefore(l.getDisbursementDate()),"input");
        require(reason!=null && !reason.isBlank() && reason.strip().length()<=500,"reason");reason=reason.strip();
        var original=reverses==null?null:repository.waiver(l,reverses)
            .orElseThrow(()->new IllegalArgumentException("prepayment.error.unavailable"));
        if(original!=null && amount==null)amount=original.amount();
        require(amount!=null && amount.signum()>0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");
        shared.authorizeWaiver(actor,l.getLoanApplicationId(),reverses);
        var retry=repository.request(l,key);if(retry.isPresent()){
            var r=retry.get();require(r.loanId().equals(l.getLoanApplicationId()) && r.actor().equals(actor.getMemberId()) && r.date().equals(date) && r.amount().compareTo(amount)==0 && r.reason().equals(reason) && Objects.equals(r.reverses(),reverses),"retry");
            if(selections!=null){
                var saved = repository.parts(r.id());
                require(saved.size()==selections.size() && saved.entrySet().stream()
                    .allMatch(entry -> entry.getValue().amount().equals(selections.get(entry.getKey()))),"retry");
            }
            return r.id();
        }
        if(sequence!=null)require(l.getNextSequence()==sequence,"conflict");
        vouchers.requireInterestWaiverPeriod(actor,date);
        var last=latestWaiverDate(l.getLoanApplicationId());require(last==null || !date.isBefore(last),"date");require(l.getLastPaymentDate()==null || !date.isBefore(l.getLastPaymentDate()),"date");
        var parts=new LinkedHashMap<UUID,WaiverPart>();
        if(reverses==null){
            var earned=earned(l.getLoanApplicationId(),date);
            BigDecimal left=amount;
            for(var row:rows){
                var accrued=unpaidEarned(row,earned);
                var take=selections==null?left.min(accrued):selections.getOrDefault(row.getId(),BigDecimal.ZERO);
                require(take.compareTo(unpaidScheduled(row))<=0,"amount");
                if(take.signum()>0){
                    parts.put(row.getId(),new WaiverPart(take,take.subtract(accrued).max(BigDecimal.ZERO)));
                    left=left.subtract(take);
                }
            }
            require(left.signum()==0,"amount");
        }else{
            require(original.reverses()==null && original.reversedBy()==null && !original.actor().equals(actor.getMemberId()) && original.amount().compareTo(amount)==0 && !date.isBefore(original.date()),"checker");
            require(!repository.laterPayment(l.getLoanApplicationId(),original.sequence()) && !repository.laterWaiver(l.getLoanApplicationId(),original.sequence()),"latest");parts.putAll(repository.parts(reverses));
        }
        for(var row:rows){
            var part=parts.get(row.getId());
            if(part!=null){
                var change=reverses==null?part.amount():part.amount().negate();
                row.setInterestWaived(row.getInterestWaived().add(change));
            }
        }
        var futureAmount=parts.values().stream().map(WaiverPart::futureAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        UUID id=UUID.randomUUID();repository.waiver(l,id,key,date,amount,futureAmount,actor.getMemberId(),reason,reverses,clock.now(),parts);shared.waiver(actor,l.getLoanApplicationId(),id,amount,reverses);l.setNextSequence(l.getNextSequence()+1);
        audit.logEvent("LOAN_REPAYMENT",id,reverses==null?"INTEREST_WAIVED":"INTEREST_WAIVER_REVERSED",actor.getMemberId(),AuditEventStatus.SUCCESS,reason,"LOAN",l.getLoanId(),l.getSaccoId(),l.getStationId(),Map.of("loan",l.getLoanApplicationId(),"amount",amount,"futureAmount",futureAmount,"date",date,"reverses",Objects.toString(reverses,"")));
        return id;
    }
    private static void require(boolean condition,String key){if(!condition)throw new IllegalArgumentException("prepayment.error."+key);}
    private static void requireActor(AppUserPrincipal actor,LoanLedger ledger){
        if(actor==null || !actor.isStaffSession() || !actor.getClaims().contains(UserClaim.LOAN_INTEREST_WAIVE.name()) || !Objects.equals(actor.getSaccoId(),ledger.getSaccoId()) || !Objects.equals(actor.getStationId(),ledger.getStationId()))throw new org.springframework.security.access.AccessDeniedException("Forbidden");
    }
}
