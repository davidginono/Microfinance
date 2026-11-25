package com.sacco.mvp.accounting.charges.service;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.charges.repository.LoanChargeRepository;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ChargeField;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ChargeQuote;
import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.sacco.mvp.accounting.charges.service.LoanChargeCalculator.*;

@Service @RequiredArgsConstructor
public class LoanChargeService {
    private final LoanChargeRepository repository;
    private final GeneralLedgerService accounting;
    private final VoucherRepository periods;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    private void require(boolean condition,String key){if(!condition)throw new IllegalArgumentException("charges.error."+key);}
    private void text(String value,boolean required){require(value!=null && value.length()<=500 && (!required || !value.isBlank()) && value.chars().noneMatch(c->c<32),"evidence");}
    private void configure(AppUserPrincipal a){accounting.requireActor(a,"LOAN_PRODUCTS_CONFIGURE");accounting.requireActor(a,"ACCOUNTING_ACCOUNTS_UPDATE");}
    @Transactional(readOnly=true) public Optional<Policy> policy(AppUserPrincipal a,UUID product){configure(a);require(repository.product(a.getSaccoId(),product),"policy");return repository.policy(a.getSaccoId(),product,LocalDate.of(9999,12,31));}
    @Transactional(readOnly=true) public AccountSummary accountSummary(AppUserPrincipal actor,UUID product) {
        accounting.requireActor(actor,"LOAN_PRODUCTS_VIEW");require(repository.product(actor.getSaccoId(),product),"policy");
        var current=repository.policy(actor.getSaccoId(),product,clock.today()).orElse(null);
        return new AccountSummary(current,repository.generatedAccounts(actor.getSaccoId(),product),currentMappings(actor.getSaccoId(),current));
    }
    @Transactional(readOnly=true)
    public void requireApprovedProductCharges(String institution,UUID product,boolean application,boolean processing,boolean insurance) {
        Policy policy=repository.policy(institution,product,clock.today()).filter(Policy::enabled)
            .orElseThrow(()->new IllegalArgumentException("charges.error.policy"));
        repository.account(institution,policy.receivableAccountId(),"RECEIVABLE");
        if(application)repository.account(institution,policy.account("APPLICATION"),policy.accountKind("APPLICATION"));
        if(processing)repository.account(institution,policy.account("PROCESSING"),policy.accountKind("PROCESSING"));
        if(insurance){
            require(policy.insuranceAccountId()!=null && !policy.insurer().isBlank(),"insurance");
            repository.account(institution,policy.insuranceAccountId(),"INSURANCE");
        }
    }

    private Map<String,String> currentMappings(String institution,Policy p) {
        var currentMappings=new LinkedHashMap<String,String>();
        if(p!=null){
            var selected=new LinkedHashMap<String,UUID>();
            selected.put("APPLICATION",p.account("APPLICATION"));selected.put("PROCESSING",p.account("PROCESSING"));
            selected.put("RECEIVABLE",p.receivableAccountId());selected.put("INSURANCE",p.insuranceAccountId());
            selected.put("MANAGEMENT",p.feeRecoveryAccountId());
            var labels=repository.accountLabels(institution,selected.values().stream().filter(Objects::nonNull).distinct().toList());
            selected.forEach((kind,id)->{if(id!=null && labels.containsKey(id))currentMappings.put(kind,labels.get(id));});
        }
        return Map.copyOf(currentMappings);
    }
    @Transactional
    public UUID savePolicy(AppUserPrincipal a,PolicyForm f){
        configure(a);require(f!=null && f.getRequestKey()!=null && f.getProductId()!=null && f.getEffectiveFrom()!=null && f.getManagementTiming()!=null && f.getInsuranceTiming()!=null,"input");
        require(f.getManagementTiming()!=Timing.FINANCED && f.getInsuranceTiming()!=Timing.FINANCED,"financed");text(f.getPolicyReference(),false);text(f.getAgreementReference(),false);text(f.getInsurer(),false);text(f.getInsurerVerification(),false);
        require(repository.product(a.getSaccoId(),f.getProductId()),"policy");repository.policyLock(a.getSaccoId(),f.getProductId());String hash=hash(f);var retry=repository.policyRetry(a,f.getRequestKey(),hash);if(retry.isPresent())return retry.get();
        var command=new PolicyForm();org.springframework.beans.BeanUtils.copyProperties(f,command);f=command;
        var product=repository.productChoice(a.getSaccoId(),f.getProductId());
        accounting.provisionLoanProductFeeAccounts(a.getSaccoId(),a.getMemberId(),f.getProductId(),product.label());
        for(var generated:repository.generatedAccounts(a.getSaccoId(),f.getProductId())){
            if(!f.isEnabled() && !Set.of("APPROVED","BASELINE").contains(generated.state())){
                if(Objects.equals(f.getApplicationAccountId(),generated.accountId()))f.setApplicationAccountId(null);
                if(Objects.equals(f.getProcessingAccountId(),generated.accountId()))f.setProcessingAccountId(null);
                continue;
            }
            if(generated.component().equals("APPLICATION") && f.getApplicationAccountId()==null)f.setApplicationAccountId(generated.accountId());
            if(generated.component().equals("PROCESSING") && f.getProcessingAccountId()==null)f.setProcessingAccountId(generated.accountId());
        }
        f.setManagementAccountId(null);
        if(f.getFeeRecoveryAccountId()!=null){
            require(f.isEnabled() && f.getManagementTiming()==Timing.SEPARATE,"recovery");
            text(f.getPolicyReference(),true);text(f.getAgreementReference(),true);
            repository.account(a.getSaccoId(),f.getFeeRecoveryAccountId(),"MANAGEMENT");
        }
        var prior=repository.policy(a.getSaccoId(),f.getProductId(),LocalDate.of(9999,12,31));require(Objects.equals(prior.map(Policy::id).orElse(null),f.getExpectedVersion()),"stale");require(prior.isEmpty() || !f.getEffectiveFrom().isBefore(prior.get().effectiveFrom()),"date");
        if(f.isEnabled() || f.getReceivableAccountId()!=null)repository.account(a.getSaccoId(),f.getReceivableAccountId(),"RECEIVABLE");
        if(f.isEnabled() || f.getApplicationAccountId()!=null)repository.account(a.getSaccoId(),f.getApplicationAccountId(),"APPLICATION");
        if(f.isEnabled() || f.getProcessingAccountId()!=null)repository.account(a.getSaccoId(),f.getProcessingAccountId(),"PROCESSING");
        if(f.getInsuranceAccountId()!=null){repository.account(a.getSaccoId(),f.getInsuranceAccountId(),"INSURANCE");if(f.isEnabled())text(f.getInsurer(),true);}
        if(f.isAllowCustom())require(f.getCustomManagementLimit()!=null && amount(f.getCustomManagementLimit()).signum()>0,"custom");
        var id=repository.insertPolicy(a,f,hash,clock.now());event(a,id,"LOAN_CHARGE_POLICY_RECORDED");return id;
    }
    @Transactional(readOnly=true) public Plan quote(AppUserPrincipal a,RecordForm f,LoanProductSetting product,boolean strict){
        return quote(a,f,product,strict,false,true);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Plan recordingQuote(AppUserPrincipal a,RecordForm f,LoanProductSetting product,boolean individual){
        return quote(a,f,product,true,individual,true);
    }
    @Transactional(readOnly=true)
    public Plan validateRecordingQuote(AppUserPrincipal a,RecordForm f,LoanProductSetting product,boolean individual){
        return quote(a,f,product,true,individual,false);
    }
    private Plan quote(AppUserPrincipal a,RecordForm f,LoanProductSetting product,boolean strict,boolean allowPending,boolean lockAccounts){
        boolean manual="CUSTOM".equals(f.getLoanMode());
        boolean topUp=f.getTopUpSourceLoanId()!=null;
        if(manual)accounting.requireActor(a,"LOAN_CHARGES_CUSTOM");
        var application=topUp?ZERO:amount(manual?f.getCustomApplicationCharge():product.getApplicationFee());
        var processingInput=manual && !topUp?enteredAmount(f.getPrincipal(),f.getCustomProcessingBasis(),f.getCustomProcessingPercent(),f.getCustomProcessingCharge()):null;
        var insuranceInput=manual?enteredAmount(f.getPrincipal(),f.getCustomInsuranceBasis(),f.getCustomInsurancePercent(),f.getInsurancePremium()):null;
        var processing=topUp?ZERO:manual?amount(processingInput):percent(f.getPrincipal(),product.getProcessingFeeRate());
        var insuranceLimit=percent(f.getPrincipal(),product.getInsuranceRate());
        var insurance=manual?amount(insuranceInput):insuranceLimit;
        var policy=repository.policy(a.getSaccoId(),product.getId(),f.getApplicationDate()==null?clock.today():f.getApplicationDate()).filter(Policy::enabled).orElse(null);
        boolean custom=manual && application.add(processing).signum()>0;
        String failure=manual && (insuranceInput==null || !topUp && (f.getCustomApplicationCharge()==null || processingInput==null))?"charges.error.enterFees":"";
        if(manual && insurance.compareTo(insuranceLimit)>0)failure="charges.error.insuranceLimit";
        if(custom){require(policy!=null && policy.enabled() && policy.allowCustom(),"custom");require(application.add(processing).compareTo(policy.customManagementLimit())<=0,"custom");}
        if(application.add(processing).add(insurance).signum()>0 && (policy==null || !policy.enabled()))failure="charges.error.policy";
        var terms=new ArrayList<Term>();
        if(policy!=null && policy.enabled()){
            if(application.signum()>0 && policy.account("APPLICATION")==null || processing.signum()>0 && policy.account("PROCESSING")==null)failure="charges.error.account";
            if(insurance.signum()>0 && (policy.insuranceAccountId()==null || policy.insurer().isBlank()))failure="charges.error.insurance";
        }
        String entryEvidence="Loan charge entry / "+f.getRequestKey();
        String managementEvidence=f.getEvidence()==null || f.getEvidence().isBlank()?entryEvidence:f.getEvidence();
        String insuranceEvidence=f.getInsuranceEvidence()==null || f.getInsuranceEvidence().isBlank()?entryEvidence:f.getInsuranceEvidence();
        boolean processingRate=!manual || "PERCENTAGE".equals(f.getCustomProcessingBasis()),insuranceRate=!manual || "PERCENTAGE".equals(f.getCustomInsuranceBasis());
        if(application.signum()>0)terms.add(term("APPLICATION",manual?"CUSTOM_AMOUNT":"FIXED",application,application,policy,false,custom?managementEvidence:""));
        if(processing.signum()>0)terms.add(term("PROCESSING",processingRate?"PRINCIPAL_RATE":"CUSTOM_AMOUNT",processingRate?(manual?f.getCustomProcessingPercent().movePointLeft(2):product.getProcessingFeeRate()):processing,processing,policy,false,custom?managementEvidence:""));
        if(insurance.signum()>0)terms.add(term("INSURANCE",insuranceRate?"PRINCIPAL_RATE":"CUSTOM_AMOUNT",insuranceRate?(manual?f.getCustomInsurancePercent().movePointLeft(2):product.getInsuranceRate()):insurance,insurance,policy,true,insuranceEvidence));
        if(terms.stream().filter(t->t.timing()==Timing.DEDUCT).map(Term::amount).reduce(ZERO,BigDecimal::add).compareTo(f.getPrincipal())>=0)failure="charges.error.amount";
        var plan=new Plan(policy==null?null:policy.id(),policy==null?"":policy.policyReference(),policy==null?"":policy.agreementReference(),List.copyOf(terms),failure.isEmpty(),failure);
        if(allowPending && canRecordWithPendingCharges(f,plan))return plan;
        if(strict && policy!=null && insurance.signum()>0)require(!repository.insuranceUsed(a.getSaccoId(),policy.insurer(),insuranceEvidence),"insuranceDuplicate");
        if(strict){
            require(failure.isEmpty(),failure.isEmpty()?"policy":failure.substring("charges.error.".length()));
            for(var t:terms){
                text(t.evidence(),custom || t.kind().equals("INSURANCE"));
                try{
                    repository.account(a.getSaccoId(),t.receivableAccountId(),"RECEIVABLE",lockAccounts);
                    repository.account(a.getSaccoId(),t.liabilityAccountId(),policy.accountKind(t.kind()),lockAccounts);
                }catch(IllegalArgumentException error){
                    var pending=new Plan(plan.policyId(),plan.policyReference(),plan.agreementReference(),plan.terms(),false,error.getMessage());
                    if(allowPending && canRecordWithPendingCharges(f,pending))return pending;
                    throw error;
                }
            }
        }
        return plan;
    }
    private BigDecimal enteredAmount(BigDecimal principal,String basis,BigDecimal percentage,BigDecimal entered) {
        require(basis==null || Set.of("AMOUNT","PERCENTAGE").contains(basis),"input");
        if("PERCENTAGE".equals(basis))return percentage==null?null:percent(principal,percentage.movePointLeft(2).stripTrailingZeros());
        return entered==null?null:amount(entered);
    }
    @Transactional(readOnly=true) public ChargeQuote quoteView(AppUserPrincipal a,RecordForm f,LoanProductSetting product){
        var plan=quote(a,f,product,false);boolean manual="CUSTOM".equals(f.getLoanMode());
        var fields=new ArrayList<ChargeField>();
        for(String kind:f.getTopUpSourceLoanId()!=null?List.of("INSURANCE"):List.of("APPLICATION","PROCESSING","INSURANCE")){
            var term=plan.terms().stream().filter(t->t.kind().equals(kind)).findFirst().orElse(null);var total=term==null?ZERO:term.amount();
            BigDecimal rate=null;
            if(!kind.equals("APPLICATION")){
                boolean processing=kind.equals("PROCESSING");
                if(!manual)rate=Objects.requireNonNullElse(processing?product.getProcessingFeeRate():product.getInsuranceRate(),BigDecimal.ZERO).movePointRight(2);
                else if("PERCENTAGE".equals(processing?f.getCustomProcessingBasis():f.getCustomInsuranceBasis()))rate=processing?f.getCustomProcessingPercent():f.getCustomInsurancePercent();
                else rate=percentage(f.getPrincipal(),total);
            }
            fields.add(new ChargeField(kind,rate==null?"":rate.stripTrailingZeros().toPlainString(),total.toPlainString()));
        }
        return new ChargeQuote(plan,List.copyOf(fields));
    }
    public boolean canRecordWithPendingCharges(RecordForm form,Plan plan){
        return "CONFIGURED".equals(form.getLoanMode()) && form.getTopUpSourceLoanId()==null
            && Set.of("charges.error.policy","charges.error.account","charges.error.insurance").contains(plan.error());
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public Plan resolvePending(AppUserPrincipal actor,UUID productId,Plan pending,LocalDate date){
        var policy=repository.policy(actor.getSaccoId(),productId,date).filter(Policy::enabled)
            .orElseThrow(()->new IllegalArgumentException("charges.error.fundingSetup"));
        var terms=new ArrayList<Term>();
        for(var term:pending.terms()){
            boolean insurance=term.kind().equals("INSURANCE");
            var timing=insurance?policy.insuranceTiming():policy.managementTiming();
            require(term.timing()==timing,"fundingTiming");
            var account=policy.account(term.kind());
            repository.account(actor.getSaccoId(),policy.receivableAccountId(),"RECEIVABLE");
            repository.account(actor.getSaccoId(),account,policy.accountKind(term.kind()));
            if(insurance){
                text(term.evidence(),true);
                require(!repository.insuranceUsed(actor.getSaccoId(),policy.insurer(),term.evidence()),"insuranceDuplicate");
            }
            terms.add(new Term(term.id(),term.kind(),term.basis(),term.basisValue(),term.amount(),timing,
                policy.receivableAccountId(),account,term.evidence(),insurance?policy.insurer():"",insurance?policy.insurerVerification():""));
        }
        return new Plan(policy.id(),policy.policyReference(),policy.agreementReference(),List.copyOf(terms),true,"");
    }
    public Optional<Plan> pending(LoanApplication loan){return pending(loan.getFinancialSnapshot());}
    private Optional<Plan> pending(String financial){
        if(financial==null || financial.isBlank())return Optional.empty();
        var snapshot=json.readTree(financial).path("recordedCharges");
        return snapshot.path("supported").isBoolean() && !snapshot.path("supported").asBoolean()
            ?Optional.of(json.treeToValue(snapshot,Plan.class)):Optional.empty();
    }
    private Term term(String kind,String basis,BigDecimal value,BigDecimal total,Policy p,boolean insurance,String evidence){return new Term(UUID.randomUUID(),kind,basis,value,total,p==null?Timing.SEPARATE:insurance?p.insuranceTiming():p.managementTiming(),p==null?null:p.receivableAccountId(),p==null?null:p.account(kind),evidence,insurance && p!=null?p.insurer():"",insurance && p!=null?p.insurerVerification():"");}
    @Transactional(propagation=Propagation.MANDATORY) public void clearUnpostedForEdit(AppUserPrincipal a,UUID loan){accounting.requireActor(a,"LOAN_RECORDING_CREATE");readable(a,loan,true);repository.clearUnpostedForEdit(loan);}
    @Transactional(propagation=Propagation.MANDATORY) public void freeze(AppUserPrincipal a,UUID loan,Plan plan){repository.freeze(a,loan,plan,clock.now());}
    private LoanChargeRepository.Loan readable(AppUserPrincipal a,UUID loan,boolean lock){if(a==null||!a.isStaffSession())throw new org.springframework.security.access.AccessDeniedException("Loan unavailable");return repository.loan(a,loan,lock);}
    @Transactional(readOnly=true) public Plan quotedTerms(AppUserPrincipal actor,UUID loan){
        readable(actor,loan,false);return displayPlan(actor,loan);
    }
    private Plan displayPlan(AppUserPrincipal actor,UUID loan){
        return repository.pendingPlan(actor,loan).map(snapshot->json.readValue(snapshot,Plan.class)).orElseGet(()->repository.frozen(loan));
    }
    @Transactional(readOnly=true) public Plan frozen(AppUserPrincipal a,UUID loan){readable(a,loan,false);return repository.frozen(loan);}
    @Transactional(readOnly=true)
    public List<FeeBalance> collectionBalances(AppUserPrincipal actor,UUID loan,LocalDate date){
        readable(actor,loan,false);
        dated(loan,date);
        var balances=repository.feeBalances(loan);
        balances.stream().filter(b->b.outstanding().signum()>0)
            .forEach(b->require(repository.selectedAccount(actor.getSaccoId(),b.accountId(),"RECEIVABLE")!=null,"account"));
        return balances;
    }
    @Transactional(readOnly=true)
    public Optional<UUID> fundingRecoveryAccount(AppUserPrincipal a,UUID loan,LocalDate date){
        readable(a,loan,false);
        return repository.contractPolicy(loan).map(Policy::feeRecoveryAccountId)
            .or(()->repository.pendingRecoveryAccount(a,loan,date==null?clock.today():date));
    }

    public void requireNoUnrecoveredFees(AppUserPrincipal actor,UUID loan){
        readable(actor,loan,false);
        require(repository.recoveryBalances(loan).stream().noneMatch(b->b.outstanding().signum()>0),"recoveryClose");
    }

    @Transactional(readOnly=true)
    public List<RecoveryBalance> recoveryBalances(AppUserPrincipal a,UUID loan,LocalDate date){
        readable(a,loan,false);
        var balances=repository.recoveryBalances(loan);
        require(balances.stream().noneMatch(b->date.isBefore(b.startsOn())),"date");
        return balances;
    }

    @Transactional(readOnly=true)
    public Map<UUID,BigDecimal> recoveredAmounts(AppUserPrincipal a,List<UUID> postingIds){
        accounting.requireActor(a,"LOAN_RECORDING_VIEW");
        require(postingIds.size()<=50,"input");
        return repository.recoveredAmounts(a,postingIds);
    }

    public List<RecoveryPayment> allocateRecovery(AppUserPrincipal a,List<RecoveryBalance> balances,BigDecimal principal){
        var payments=new ArrayList<RecoveryPayment>();
        var remaining=principal;
        for(var balance:balances){
            var paid=remaining.min(balance.outstanding()).setScale(2);
            if(paid.signum()<=0)continue;
            require(repository.selectedAccount(a.getSaccoId(),balance.holdingAccountId(),"MANAGEMENT")!=null,"account");
            require(repository.selectedAccount(a.getSaccoId(),balance.accountId(),balance.kind())!=null ||
                repository.selectedAccount(a.getSaccoId(),balance.accountId(),"MANAGEMENT")!=null,"account");
            payments.add(new RecoveryPayment(balance.termId(),balance.accountId(),balance.holdingAccountId(),paid));
            remaining=remaining.subtract(paid);
        }
        return List.copyOf(payments);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void linkFundingFeeReceipt(UUID funding,UUID receipt){
        repository.linkFundingFeeReceipt(funding,receipt);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void recordFundingRecovery(AppUserPrincipal a,UUID journal,Plan plan,UUID holding){
        repository.account(a.getSaccoId(),holding,"MANAGEMENT");
        for(var term:plan.terms()){
            if(term.kind().equals("INSURANCE"))continue;
            repository.account(a.getSaccoId(),term.liabilityAccountId(),term.kind());
            repository.recoveryEvent(a,term.id(),journal,"FUND",term.amount(),holding,term.liabilityAccountId(),null);
        }
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void recordRecoveryPayments(AppUserPrincipal a,UUID journal,List<RecoveryPayment> payments){
        for(var payment:payments){
            repository.account(a.getSaccoId(),payment.holdingAccountId(),"MANAGEMENT");
            repository.recoveryEvent(a,payment.termId(),journal,"RECOVER",payment.amount(),
                payment.holdingAccountId(),payment.accountId(),null);
        }
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void reverseRecovery(AppUserPrincipal a,UUID original,UUID journal,String expected,String correction){
        for(var event:repository.recoveryEvents(original)){
            require(event.kind().equals(expected),"dependent");
            repository.recoveryEvent(a,event.termId(),journal,correction,event.amount(),event.holdingAccountId(),event.accountId(),event.id());
        }
    }

    @Transactional(readOnly=true)
    public Optional<RecoveryReview> recoveryReview(AppUserPrincipal a,UUID loan){
        accounting.requireActor(a,"LOAN_RECORDING_VIEW");
        if(!a.getClaims().contains("LOAN_FEE_RECOVERY_APPROVE"))accounting.requireActor(a,"LOAN_FEE_RECOVERY_SUBMIT");
        else accounting.requireActor(a,"LOAN_FEE_RECOVERY_APPROVE");
        readable(a,loan,false);
        return repository.recoveryReview(a,loan,null,false);
    }

    @Transactional(readOnly=true)
    public List<Choice> recoveryAccountChoices(AppUserPrincipal a,String search,int page){
        accounting.requireActor(a,"LOAN_FEE_RECOVERY_SUBMIT");
        require(search!=null && search.length()<=100 && page>=0 && page<=10000,"input");
        return repository.accounts(a.getSaccoId(),"MANAGEMENT",search,page*25);
    }

    @Transactional(readOnly=true)
    public com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice recoveryHolding(AppUserPrincipal actor,UUID account){
        accounting.requireActor(actor,"LOAN_FEE_RECOVERY_SUBMIT");
        return repository.selectedAccount(actor.getSaccoId(),account,"MANAGEMENT");
    }

    @Transactional
    public UUID submitRecoveryReview(AppUserPrincipal a,UUID loan,RecoveryReviewForm form){
        accounting.requireActor(a,"LOAN_RECORDING_VIEW");
        accounting.requireActor(a,"LOAN_FEE_RECOVERY_SUBMIT");
        require(form.getRequestKey()!=null,"input");text(form.getEvidence(),true);
        var amounts=new LinkedHashMap<String,BigDecimal>();
        var application=amount(form.getApplication());var processing=amount(form.getProcessing());
        if(application.signum()>0)amounts.put("APPLICATION",application);
        if(processing.signum()>0)amounts.put("PROCESSING",processing);
        var identity=readable(a,loan,true);
        var existing=repository.recoveryReview(a,loan,form.getRequestKey(),true);
        if(existing.isPresent()){
            var review=existing.get();
            require(review.makerId().equals(a.getMemberId()) && review.holdingAccountId().equals(form.getHoldingAccountId())
                && review.amounts().equals(amounts) && review.evidence().equals(form.getEvidence().strip()),"retry");
            return review.id();
        }
        var snapshot=repository.recoverySnapshot(loan);
        validateRecoveryReview(a,identity,form.getHoldingAccountId(),amounts,snapshot);
        var id=UUID.randomUUID();
        repository.submitRecoveryReview(a,id,loan,form,snapshot,json.writeValueAsString(amounts),clock.now());
        event(a,id,"LOAN_FEE_RECOVERY_REVIEW_SUBMITTED");
        return id;
    }

    @Transactional
    public UUID approveRecoveryReview(AppUserPrincipal a,UUID loan,UUID id,LocalDate date,String reason){
        accounting.requireActor(a,"LOAN_RECORDING_VIEW");
        accounting.requireActor(a,"LOAN_FEE_RECOVERY_APPROVE");
        require(id!=null && date!=null && !date.isAfter(clock.today()),"date");text(reason,true);
        var identity=readable(a,loan,true);
        var review=repository.recoveryReview(a,loan,id,false).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Fee review unavailable"));
        require(!review.makerId().equals(a.getMemberId()),"independent");
        require(!review.approved() && repository.recoveryReview(a,loan,null,false).filter(latest->latest.id().equals(id)).isPresent(),"stale");
        var snapshot=repository.recoverySnapshot(loan);
        require(snapshot.sequence()==review.postingSnapshot() && snapshot.principal().compareTo(review.principalSnapshot())==0,"stale");
        require(snapshot.latestDate()!=null && !date.isBefore(snapshot.latestDate()),"date");dated(loan,date);
        var terms=validateRecoveryReview(a,identity,review.holdingAccountId(),review.amounts(),snapshot);
        var now=clock.now();
        var journal=UUID.randomUUID();
        var period=periods.period(a.getSaccoId(),date,a.getMemberId(),now);
        repository.approveRecoveryReview(a,review,journal,period,date,reason.strip(),hash(List.of(review,date,reason.strip())),now);
        for(var term:terms)repository.recoveryEvent(a,term.id(),journal,"CUTOVER",review.amounts().get(term.kind()),review.holdingAccountId(),term.liabilityAccountId(),null);
        repository.post(a,journal,now);event(a,id,"LOAN_FEE_RECOVERY_REVIEW_APPROVED");
        return journal;
    }

    private List<Term> validateRecoveryReview(AppUserPrincipal a,LoanChargeRepository.Loan loan,UUID holding,Map<String,BigDecimal> amounts,RecoverySnapshot snapshot){
        require(loan.client()!=null && loan.source()==null && Set.of("DISBURSED","PAR","DEFAULTED").contains(loan.status()),"state");
        require(!amounts.isEmpty() && snapshot.principal().signum()>0 &&
            amounts.values().stream().reduce(ZERO,BigDecimal::add).compareTo(snapshot.principal())<=0,"recovery");
        require(repository.recoveryBalances(loan.id()).isEmpty(),"stale");
        repository.account(a.getSaccoId(),holding,"MANAGEMENT");
        var deductions=repository.withheldAmounts(loan.id());
        var terms=repository.frozen(loan.id()).terms().stream().filter(t->amounts.containsKey(t.kind())).toList();
        require(terms.size()==amounts.size(),"recovery");
        for(var term:terms){
            require(!holding.equals(term.liabilityAccountId()) && amounts.get(term.kind()).compareTo(deductions.getOrDefault(term.id(),ZERO))<=0,"recovery");
            var policy=repository.contractPolicy(loan.id()).orElseThrow(()->new IllegalArgumentException("charges.error.policy"));
            repository.account(a.getSaccoId(),term.liabilityAccountId(),policy.accountKind(term.kind()));
        }
        return terms;
    }

    @Transactional(readOnly=true) public Balance balance(AppUserPrincipal a,UUID loan,LocalDate date){readable(a,loan,false);return repository.balance(loan,date);}
    @Transactional(readOnly=true) public Summary summary(AppUserPrincipal a,UUID loan){var identity=readable(a,loan,false);var assessment=repository.fundingAssessments(loan).stream().findFirst().orElse(null);return new Summary(displayPlan(a,loan),repository.balance(loan,clock.today()),assessment==null?null:assessment.journalId(),assessment==null?null:assessment.actorId(),identity.number(),identity.institution(),identity.branch());}
    @Transactional public UUID assess(AppUserPrincipal a,UUID loan,AssessmentForm f){accounting.requireActor(a,"LOAN_RECORDING_POST");return assessment(a,loan,f);}
    private UUID assessment(AppUserPrincipal a,UUID loan,AssessmentForm f){
        require(f!=null && f.getRequestKey()!=null && f.getDate()!=null && !f.getDate().isAfter(clock.today()),"date");text(f.getEvidence(),true);text(f.getReason(),false);
        var l=readable(a,loan,true);String hash=hash(List.of(loan,f));var retry=repository.commandRetry(a,f.getRequestKey(),hash);if(retry.isPresent())return retry.get();require(Set.of("READY_FOR_DISBURSEMENT","DISBURSED").contains(l.status()),"state");require(!f.getDate().isBefore(l.applicationDate()),"date");
        require(repository.pendingPlan(a,loan).isEmpty(),"fundingSetup");
        var plan=repository.frozen(loan);require(!plan.terms().isEmpty() && !repository.assessed(loan),"assessed");dated(loan,f.getDate());
        UUID journal=UUID.randomUUID(),period=periods.period(a.getSaccoId(),f.getDate(),a.getMemberId(),clock.now());var now=clock.now();var id=repository.command(a,loan,f.getRequestKey(),hash,f.getDate(),"ASSESS",f.getEvidence(),f.getReason(),null,journal,now);repository.journal(a,journal,id,f.getRequestKey(),hash,f.getDate(),period,null,f.getEvidence(),f.getReason(),now);
        var policy=repository.contractPolicy(loan).orElseThrow(()->new IllegalArgumentException("charges.error.policy"));
        for(var t:plan.terms()){repository.account(a.getSaccoId(),t.receivableAccountId(),"RECEIVABLE");repository.account(a.getSaccoId(),t.liabilityAccountId(),policy.accountKind(t.kind()));repository.event(a,loan,t,"ASSESS",t.amount(),f.getDate(),journal,null,id,null,f.getEvidence(),f.getReason(),now);repository.line(a,journal,t.receivableAccountId(),t.amount(),ZERO);repository.line(a,journal,t.liabilityAccountId(),ZERO,t.amount());}
        repository.post(a,journal,now);event(a,id,"LOAN_CHARGES_ASSESSED");return id;
    }
    @Transactional(propagation=Propagation.MANDATORY) public void assessAtFunding(AppUserPrincipal a,UUID loan,LocalDate date,String evidence){accounting.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");var p=repository.frozen(loan);if(p.terms().isEmpty() || repository.assessed(loan))return;var f=new AssessmentForm();f.setRequestKey(UUID.nameUUIDFromBytes(("CHARGE_ASSESS/"+loan+"/"+repository.sequence(loan)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));f.setDate(date);f.setEvidence(evidence);f.setReason("");assessment(a,loan,f);}
    private void dated(UUID loan,LocalDate date){var b=repository.balance(loan,LocalDate.MAX);require(b.lastDate()==null || !date.isBefore(b.lastDate()),"date");}
    /** A funding election collects an existing fee; it never rewrites its frozen amount or timing. */
    public BigDecimal fundingDeductions(Plan plan,List<UUID> selected){
        require(selected!=null && selected.size()<=3 && new HashSet<>(selected).size()==selected.size(),"selection");
        require(plan.terms().stream().filter(t->t.timing()==Timing.SEPARATE).map(Term::id).toList().containsAll(selected),"selection");
        return plan.terms().stream().filter(t->t.timing()==Timing.DEDUCT || selected.contains(t.id())).map(Term::amount).reduce(ZERO,BigDecimal::add);
    }
    @Transactional(propagation=Propagation.MANDATORY) public List<Object[]> collect(AppUserPrincipal a,UUID loan,String kind,BigDecimal total,LocalDate date,UUID journal,UUID posting,String evidence,String reason){
        return collect(a,loan,kind,total,date,journal,posting,evidence,reason,List.of());
    }
    @Transactional(propagation=Propagation.MANDATORY) public List<Object[]> collect(AppUserPrincipal a,UUID loan,String kind,BigDecimal total,LocalDate date,UUID journal,UUID posting,String evidence,String reason,List<UUID> selected){
        require(selected!=null,"selection");
        if(total.signum()==0 && selected.isEmpty())return List.of();
        var l=readable(a,loan,true);
        var plan=repository.frozen(loan);
        if(kind.equals("DEDUCT"))require(total.compareTo(fundingDeductions(plan,selected))==0,"amount");
        else require(selected.isEmpty(),"selection");
        dated(loan,date);require(Set.of("COLLECT","DEDUCT","SETTLE").contains(kind),"input");if(kind.equals("COLLECT"))require(Set.of("DISBURSED","PAR","DEFAULTED").contains(l.status()),"state");var remaining=total;var lines=new ArrayList<Object[]>();
        for(var t:plan.terms()){if(kind.equals("DEDUCT") && t.timing()!=Timing.DEDUCT && !selected.contains(t.id()))continue;var due=repository.outstanding(t.id());var paid=remaining.min(due);if(paid.signum()<=0)continue;repository.account(a.getSaccoId(),t.receivableAccountId(),"RECEIVABLE");repository.event(a,loan,t,kind,paid,date,journal,posting,null,null,evidence,reason,clock.now());lines.add(new Object[]{t.receivableAccountId(),ZERO,paid});remaining=remaining.subtract(paid);}
        require(remaining.signum()==0,"amount");return lines;
    }
    @Transactional(propagation=Propagation.MANDATORY) public void undoCollections(AppUserPrincipal a,UUID originalJournal,UUID reversalJournal,UUID posting,LocalDate date,String evidence,String reason){
        for(var e:repository.journalEvents(originalJournal)){if(!Set.of("COLLECT","DEDUCT","SETTLE").contains(e.kind()))continue;readable(a,e.loanId(),true);dated(e.loanId(),date);require(!e.actorId().equals(a.getMemberId()),"independent");var t=repository.frozen(e.loanId()).terms().stream().filter(v->v.id().equals(e.termId())).findFirst().orElseThrow();repository.event(a,e.loanId(),t,e.kind()+"_UNDO",e.amount(),date,reversalJournal,posting,null,e.id(),evidence,reason,clock.now());}
    }
    @Transactional(readOnly=true) public boolean hasCollections(UUID journal){return !repository.journalEvents(journal).isEmpty();}
    @Transactional(propagation=Propagation.MANDATORY) public void beforeCorrection(AppUserPrincipal a,UUID loan,UUID original,LocalDate date){readable(a,loan,true);if(repository.frozen(loan).terms().isEmpty())return;require(!repository.includedInRecoveryReview(loan,original),"dependent");require(!repository.laterPayment(loan,original,date),"dependent");dated(loan,date);}
    @Transactional(propagation=Propagation.MANDATORY) public void checkFundingCorrection(AppUserPrincipal a,UUID loan){readable(a,loan,true);require(!repository.fundingDependencies(loan),"dependent");
        require(repository.recoveryReview(a,loan,null,false).stream().noneMatch(RecoveryReview::approved),"recoveryClose");}
    @Transactional(propagation=Propagation.MANDATORY) public List<Object[]> correctFunding(AppUserPrincipal a,UUID loan,UUID original,UUID journal,UUID key,LocalDate date,String evidence,String reason){checkFundingCorrection(a,loan);undoCollections(a,original,journal,repository.fundingPosting(loan),date,evidence,reason);reverseAssessmentsForFunding(a,loan,key,date,evidence,reason);reverseRecovery(a,original,journal,"FUND","UNDO_FUND");return repository.reversalLines(original);}
    @Transactional public void reverseAssessment(AppUserPrincipal a,UUID loan,UUID journal,UUID key,LocalDate date,String evidence,String reason){accounting.requireActor(a,"LOAN_RECORDING_REVERSE");undoAssessment(a,loan,journal,key,date,evidence,reason,true);}
    @Transactional(propagation=Propagation.MANDATORY) public void reverseAssessmentsForFunding(AppUserPrincipal a,UUID loan,UUID key,LocalDate date,String evidence,String reason){
        readable(a,loan,true);require(repository.balance(loan,LocalDate.MAX).collected().signum()==0,"dependent");
        for(var journal:repository.fundingAssessments(loan).stream().map(Movement::journalId).distinct().toList())undoAssessment(a,loan,journal,UUID.nameUUIDFromBytes((key+"/"+journal).getBytes(java.nio.charset.StandardCharsets.UTF_8)),date,evidence,reason,false);
    }
    private void undoAssessment(AppUserPrincipal a,UUID loan,UUID original,UUID key,LocalDate date,String evidence,String reason,boolean readyOnly){
        require(key!=null && date!=null && !date.isAfter(clock.today()),"date");text(evidence,true);text(reason,true);var source=readable(a,loan,true);String hash=hash(List.of(loan,original,key,date,evidence,reason));var retry=repository.commandRetry(a,key,hash);if(retry.isPresent())return;
        if(readyOnly)require(source.status().equals("READY_FOR_DISBURSEMENT"),"state");dated(loan,date);var command=repository.assessmentCommand(original);require(loan.equals(command.get("loan_id")) && !a.getMemberId().equals(command.get("actor_id")),"independent");require(repository.balance(loan,LocalDate.MAX).collected().signum()==0,"dependent");
        UUID journal=UUID.randomUUID(),period=periods.period(a.getSaccoId(),date,a.getMemberId(),clock.now());var now=clock.now();var id=repository.command(a,loan,key,hash,date,"ASSESS_UNDO",evidence,reason,(UUID)command.get("id"),journal,now);repository.journal(a,journal,id,key,hash,date,period,original,evidence,reason,now);
        for(var e:repository.journalEvents(original)){var term=repository.frozen(loan).terms().stream().filter(t->t.id().equals(e.termId())).findFirst().orElseThrow();repository.event(a,loan,term,"ASSESS_UNDO",e.amount(),date,journal,null,id,e.id(),evidence,reason,now);}
        for(var line:repository.reversalLines(original))repository.line(a,journal,(UUID)line[0],(BigDecimal)line[1],(BigDecimal)line[2]);repository.post(a,journal,now);event(a,id,"LOAN_CHARGE_ASSESSMENT_REVERSED");
    }
    private String hash(Object value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private void event(AppUserPrincipal a,UUID id,String type){audit.logEvent("ACCOUNTING_LOAN_CHARGES",id,type,a.getMemberId(),AuditEventStatus.SUCCESS,"Loan charges","LOAN_CHARGES",id.toString(),a.getSaccoId(),a.getStationId(),Map.of());}
}
