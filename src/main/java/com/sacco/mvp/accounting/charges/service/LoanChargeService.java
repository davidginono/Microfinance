package com.sacco.mvp.accounting.charges.service;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.charges.repository.LoanChargeRepository;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm;
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
    @Transactional(readOnly=true) public List<com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice> accounts(AppUserPrincipal a,String kind,String search,int page){configure(a);require(search!=null && search.length()<=100 && page>=0 && page<=10000,"input");return repository.accounts(a.getSaccoId(),kind,search,page);}
    @Transactional(readOnly=true) public com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice productChoice(AppUserPrincipal a,UUID product){configure(a);return repository.productChoice(a.getSaccoId(),product);}
    @Transactional(readOnly=true) public com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice selectedAccount(AppUserPrincipal a,UUID id,String kind){configure(a);return repository.selectedAccount(a.getSaccoId(),id,kind);}
    @Transactional public UUID savePolicy(AppUserPrincipal a,PolicyForm f){
        configure(a);require(f!=null && f.getRequestKey()!=null && f.getProductId()!=null && f.getEffectiveFrom()!=null && f.getManagementTiming()!=null && f.getInsuranceTiming()!=null,"input");
        require(f.getManagementTiming()!=Timing.FINANCED && f.getInsuranceTiming()!=Timing.FINANCED,"financed");text(f.getPolicyReference(),f.isEnabled());text(f.getAgreementReference(),f.isEnabled());text(f.getInsurer(),false);text(f.getInsurerVerification(),false);
        require(repository.product(a.getSaccoId(),f.getProductId()),"policy");repository.policyLock(a.getSaccoId(),f.getProductId());String hash=hash(f);var retry=repository.policyRetry(a,f.getRequestKey(),hash);if(retry.isPresent())return retry.get();
        var prior=repository.policy(a.getSaccoId(),f.getProductId(),LocalDate.of(9999,12,31));require(Objects.equals(prior.map(Policy::id).orElse(null),f.getExpectedVersion()),"stale");require(prior.isEmpty() || f.getEffectiveFrom().isAfter(prior.get().effectiveFrom()),"date");
        if(f.isEnabled()){repository.account(a.getSaccoId(),f.getReceivableAccountId(),"RECEIVABLE");if(f.getManagementAccountId()!=null)repository.account(a.getSaccoId(),f.getManagementAccountId(),"MANAGEMENT");if(f.getInsuranceAccountId()!=null){repository.account(a.getSaccoId(),f.getInsuranceAccountId(),"INSURANCE");text(f.getInsurer(),true);text(f.getInsurerVerification(),true);}}
        if(f.isAllowCustom())require(f.getCustomManagementLimit()!=null && amount(f.getCustomManagementLimit()).signum()>0,"custom");
        var id=repository.insertPolicy(a,f,hash,clock.now());event(a,id,"LOAN_CHARGE_POLICY_RECORDED");return id;
    }
    @Transactional(readOnly=true) public Plan quote(AppUserPrincipal a,RecordForm f,LoanProductSetting product,boolean strict){
        return quote(a,f,product,strict,false);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Plan recordingQuote(AppUserPrincipal a,RecordForm f,LoanProductSetting product,boolean individual){
        return quote(a,f,product,true,individual);
    }
    private Plan quote(AppUserPrincipal a,RecordForm f,LoanProductSetting product,boolean strict,boolean allowPending){
        boolean manual="CUSTOM".equals(f.getLoanMode());
        if(manual)accounting.requireActor(a,"LOAN_CHARGES_CUSTOM");
        var application=amount(manual?f.getCustomApplicationCharge():product.getApplicationFee());
        var processingInput=manual?enteredAmount(f.getPrincipal(),f.getCustomProcessingBasis(),f.getCustomProcessingPercent(),f.getCustomProcessingCharge()):null;
        var insuranceInput=manual?enteredAmount(f.getPrincipal(),f.getCustomInsuranceBasis(),f.getCustomInsurancePercent(),f.getInsurancePremium()):null;
        var processing=manual?amount(processingInput):percent(f.getPrincipal(),product.getProcessingFeeRate());
        var insuranceLimit=percent(f.getPrincipal(),product.getInsuranceRate());
        var insurance=manual?amount(insuranceInput):insuranceLimit;
        var policy=repository.policy(a.getSaccoId(),product.getId(),f.getApplicationDate()==null?clock.today():f.getApplicationDate()).filter(Policy::enabled).orElse(null);
        boolean custom=manual && application.add(processing).signum()>0;
        String failure=manual && (f.getCustomApplicationCharge()==null || processingInput==null || insuranceInput==null)?"charges.error.enterFees":"";
        if(manual && insurance.compareTo(insuranceLimit)>0)failure="charges.error.insuranceLimit";
        if(custom){require(policy!=null && policy.enabled() && policy.allowCustom(),"custom");require(application.add(processing).compareTo(policy.customManagementLimit())<=0,"custom");}
        if(f.getTopUpSourceLoanId()!=null && application.add(processing).signum()>0)failure="charges.error.topUpFee";
        if(application.add(processing).add(insurance).signum()>0 && (policy==null || !policy.enabled()))failure="charges.error.policy";
        var terms=new ArrayList<Term>();
        if(policy!=null && policy.enabled()){
            if(application.add(processing).signum()>0 && policy.managementAccountId()==null)failure="charges.error.account";
            if(insurance.signum()>0 && (policy.insuranceAccountId()==null || policy.insurer().isBlank() || policy.insurerVerification().isBlank()))failure="charges.error.insurance";
        }
        String entryEvidence="Loan charge entry / "+f.getRequestKey();
        String managementEvidence=f.getEvidence()==null || f.getEvidence().isBlank()?entryEvidence:f.getEvidence();
        String insuranceEvidence=f.getInsuranceEvidence()==null || f.getInsuranceEvidence().isBlank()?entryEvidence:f.getInsuranceEvidence();
        boolean processingRate=!manual || "PERCENTAGE".equals(f.getCustomProcessingBasis()),insuranceRate=!manual || "PERCENTAGE".equals(f.getCustomInsuranceBasis());
        if(application.signum()>0)terms.add(term("APPLICATION",manual?"CUSTOM_AMOUNT":"FIXED",application,application,policy,false,custom?managementEvidence:""));
        if(processing.signum()>0)terms.add(term("PROCESSING",processingRate?"PRINCIPAL_RATE":"CUSTOM_AMOUNT",processingRate?(manual?f.getCustomProcessingPercent().movePointLeft(2):product.getProcessingFeeRate()):processing,processing,policy,false,custom?managementEvidence:""));
        if(insurance.signum()>0)terms.add(term("INSURANCE",insuranceRate?"PRINCIPAL_RATE":"CUSTOM_AMOUNT",insuranceRate?(manual?f.getCustomInsurancePercent().movePointLeft(2):product.getInsuranceRate()):insurance,insurance,policy,true,insuranceEvidence));
        if(f.getTopUpSourceLoanId()!=null && application.add(processing).signum()>0)failure="charges.error.topUpFee";
        if(terms.stream().filter(t->t.timing()==Timing.DEDUCT).map(Term::amount).reduce(ZERO,BigDecimal::add).compareTo(f.getPrincipal())>=0)failure="charges.error.amount";
        var plan=new Plan(policy==null?null:policy.id(),policy==null?"":policy.policyReference(),policy==null?"":policy.agreementReference(),List.copyOf(terms),failure.isEmpty(),failure);
        if(allowPending && canRecordWithPendingCharges(f,plan))return plan;
        if(strict && policy!=null && insurance.signum()>0)require(!repository.insuranceUsed(a.getSaccoId(),policy.insurer(),insuranceEvidence),"insuranceDuplicate");
        if(strict){
            require(failure.isEmpty(),failure.isEmpty()?"policy":failure.substring("charges.error.".length()));
            for(var t:terms){
                text(t.evidence(),custom || t.kind().equals("INSURANCE"));
                try{
                    repository.account(a.getSaccoId(),t.receivableAccountId(),"RECEIVABLE");
                    repository.account(a.getSaccoId(),t.liabilityAccountId(),t.kind().equals("INSURANCE")?"INSURANCE":"MANAGEMENT");
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
        for(String kind:List.of("APPLICATION","PROCESSING","INSURANCE")){
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
            var account=insurance?policy.insuranceAccountId():policy.managementAccountId();
            repository.account(actor.getSaccoId(),policy.receivableAccountId(),"RECEIVABLE");
            repository.account(actor.getSaccoId(),account,insurance?"INSURANCE":"MANAGEMENT");
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
    private Term term(String kind,String basis,BigDecimal value,BigDecimal total,Policy p,boolean insurance,String evidence){return new Term(UUID.randomUUID(),kind,basis,value,total,p==null?Timing.SEPARATE:insurance?p.insuranceTiming():p.managementTiming(),p==null?null:p.receivableAccountId(),p==null?null:insurance?p.insuranceAccountId():p.managementAccountId(),evidence,insurance && p!=null?p.insurer():"",insurance && p!=null?p.insurerVerification():"");}
    @Transactional(propagation=Propagation.MANDATORY) public void freeze(AppUserPrincipal a,UUID loan,Plan plan){repository.freeze(a,loan,plan,clock.now());}
    private LoanChargeRepository.Loan readable(AppUserPrincipal a,UUID loan,boolean lock){if(a==null||!a.isStaffSession())throw new org.springframework.security.access.AccessDeniedException("Loan unavailable");return repository.loan(a,loan,lock);}
    @Transactional(readOnly=true) public Plan quotedTerms(AppUserPrincipal actor,UUID loan){
        readable(actor,loan,false);return displayPlan(actor,loan);
    }
    private Plan displayPlan(AppUserPrincipal actor,UUID loan){
        return repository.pendingPlan(actor,loan).map(snapshot->json.readValue(snapshot,Plan.class)).orElseGet(()->repository.frozen(loan));
    }
    @Transactional(readOnly=true) public Plan frozen(AppUserPrincipal a,UUID loan){readable(a,loan,false);return repository.frozen(loan);}
    @Transactional(readOnly=true) public Balance balance(AppUserPrincipal a,UUID loan,LocalDate date){readable(a,loan,false);return repository.balance(loan,date);}
    @Transactional(readOnly=true) public Summary summary(AppUserPrincipal a,UUID loan,int page){var identity=readable(a,loan,false);require(page>=0 && page<=10000,"input");var history=repository.history(loan,page);var assessment=repository.fundingAssessments(loan).stream().findFirst().orElse(null);return new Summary(displayPlan(a,loan),repository.balance(loan,clock.today()),history.stream().limit(25).toList(),page,history.size()>25,assessment==null?null:assessment.journalId(),assessment==null?null:assessment.actorId(),identity.number(),identity.institution(),identity.branch());}
    @Transactional public UUID assess(AppUserPrincipal a,UUID loan,AssessmentForm f){accounting.requireActor(a,"LOAN_RECORDING_POST");return assessment(a,loan,f);}
    private UUID assessment(AppUserPrincipal a,UUID loan,AssessmentForm f){
        require(f!=null && f.getRequestKey()!=null && f.getDate()!=null && !f.getDate().isAfter(clock.today()),"date");text(f.getEvidence(),true);text(f.getReason(),false);
        var l=readable(a,loan,true);String hash=hash(List.of(loan,f));var retry=repository.commandRetry(a,f.getRequestKey(),hash);if(retry.isPresent())return retry.get();require(Set.of("READY_FOR_DISBURSEMENT","DISBURSED").contains(l.status()),"state");require(!f.getDate().isBefore(l.applicationDate()),"date");
        require(repository.pendingPlan(a,loan).isEmpty(),"fundingSetup");
        var plan=repository.frozen(loan);require(!plan.terms().isEmpty() && !repository.assessed(loan),"assessed");dated(loan,f.getDate());
        UUID journal=UUID.randomUUID(),period=periods.period(a.getSaccoId(),f.getDate(),a.getMemberId(),clock.now());var now=clock.now();var id=repository.command(a,loan,f.getRequestKey(),hash,f.getDate(),"ASSESS",f.getEvidence(),f.getReason(),null,journal,now);repository.journal(a,journal,id,f.getRequestKey(),hash,f.getDate(),period,null,f.getEvidence(),f.getReason(),now);
        for(var t:plan.terms()){repository.account(a.getSaccoId(),t.receivableAccountId(),"RECEIVABLE");repository.account(a.getSaccoId(),t.liabilityAccountId(),t.kind().equals("INSURANCE")?"INSURANCE":"MANAGEMENT");repository.event(a,loan,t,"ASSESS",t.amount(),f.getDate(),journal,null,id,null,f.getEvidence(),f.getReason(),now);repository.line(a,journal,t.receivableAccountId(),t.amount(),ZERO);repository.line(a,journal,t.liabilityAccountId(),ZERO,t.amount());}
        repository.post(a,journal,now);event(a,id,"LOAN_CHARGES_ASSESSED");return id;
    }
    @Transactional(propagation=Propagation.MANDATORY) public void assessAtFunding(AppUserPrincipal a,UUID loan,LocalDate date,String evidence){accounting.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");var p=repository.frozen(loan);if(p.terms().isEmpty() || repository.assessed(loan))return;var f=new AssessmentForm();f.setRequestKey(UUID.nameUUIDFromBytes(("CHARGE_ASSESS/"+loan+"/"+repository.sequence(loan)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));f.setDate(date);f.setEvidence(evidence);f.setReason("");assessment(a,loan,f);}
    private void dated(UUID loan,LocalDate date){var b=repository.balance(loan,LocalDate.MAX);require(b.lastDate()==null || !date.isBefore(b.lastDate()),"date");}
    @Transactional(propagation=Propagation.MANDATORY) public List<Object[]> collect(AppUserPrincipal a,UUID loan,String kind,BigDecimal total,LocalDate date,UUID journal,UUID posting,String evidence,String reason){
        if(total.signum()==0)return List.of();var l=readable(a,loan,true);dated(loan,date);require(Set.of("COLLECT","DEDUCT","SETTLE").contains(kind),"input");if(kind.equals("COLLECT"))require(Set.of("DISBURSED","PAR","DEFAULTED").contains(l.status()),"state");var remaining=total;var lines=new ArrayList<Object[]>();
        for(var t:repository.frozen(loan).terms()){if(kind.equals("DEDUCT") && t.timing()!=Timing.DEDUCT)continue;var due=repository.outstanding(t.id());var paid=remaining.min(due);if(paid.signum()<=0)continue;repository.account(a.getSaccoId(),t.receivableAccountId(),"RECEIVABLE");repository.event(a,loan,t,kind,paid,date,journal,posting,null,null,evidence,reason,clock.now());lines.add(new Object[]{t.receivableAccountId(),ZERO,paid});remaining=remaining.subtract(paid);}
        require(remaining.signum()==0,"amount");return lines;
    }
    @Transactional(propagation=Propagation.MANDATORY) public void undoCollections(AppUserPrincipal a,UUID originalJournal,UUID reversalJournal,UUID posting,LocalDate date,String evidence,String reason){
        for(var e:repository.journalEvents(originalJournal)){if(!Set.of("COLLECT","DEDUCT","SETTLE").contains(e.kind()))continue;readable(a,e.loanId(),true);dated(e.loanId(),date);require(!e.actorId().equals(a.getMemberId()),"independent");var t=repository.frozen(e.loanId()).terms().stream().filter(v->v.id().equals(e.termId())).findFirst().orElseThrow();repository.event(a,e.loanId(),t,e.kind()+"_UNDO",e.amount(),date,reversalJournal,posting,null,e.id(),evidence,reason,clock.now());}
    }
    @Transactional(readOnly=true) public boolean hasCollections(UUID journal){return !repository.journalEvents(journal).isEmpty();}
    @Transactional(propagation=Propagation.MANDATORY) public void beforeCorrection(AppUserPrincipal a,UUID loan,UUID original,LocalDate date){readable(a,loan,true);if(repository.frozen(loan).terms().isEmpty())return;require(!repository.laterPayment(loan,original,date),"dependent");dated(loan,date);}
    @Transactional(propagation=Propagation.MANDATORY) public void checkFundingCorrection(AppUserPrincipal a,UUID loan){readable(a,loan,true);require(!repository.fundingDependencies(loan),"dependent");}
    @Transactional(propagation=Propagation.MANDATORY) public List<Object[]> correctFunding(AppUserPrincipal a,UUID loan,UUID original,UUID journal,UUID key,LocalDate date,String evidence,String reason){checkFundingCorrection(a,loan);undoCollections(a,original,journal,repository.fundingPosting(loan),date,evidence,reason);reverseAssessmentsForFunding(a,loan,key,date,evidence,reason);return repository.reversalLines(original);}
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
