package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.repository.GroupCycleRepository;
import com.sacco.mvp.grouplending.repository.GroupLendingRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupCycleService {
    private final GroupCycleRepository cycles;
    private final GroupLendingRepository groups;
    private final GeneralLedgerService access;
    private final LoanRecordingService loans;
    private final LoanQualificationPolicyService qualification;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    public void authorize(AppUserPrincipal a,String action){access.requireActor(a,"GROUP_LENDING_"+action);}
    @Transactional(readOnly=true) public List<Choice> products(AppUserPrincipal a,String search,int page){authorize(a,"CONFIGURE");query(search,page);return cycles.products(a,search,page);}
    @Transactional(readOnly=true) public Optional<Choice> selectedProduct(AppUserPrincipal a,UUID id){authorize(a,"CONFIGURE");return id==null?Optional.empty():cycles.selectedProduct(a,id);}
    @Transactional(readOnly=true) public List<Policy> policyChoices(AppUserPrincipal a,String search,int page){authorize(a,"VIEW");query(search,page);return cycles.policies(a,search,page);}
    @Transactional(readOnly=true) public List<Choice> memberChoices(AppUserPrincipal a,UUID group,String search,int page){prepare(a);query(search,page);groups.group(a,group,false);return cycles.memberChoices(a,group,search,page);}
    @Transactional(readOnly=true) public Map<UUID,String> memberLabels(AppUserPrincipal a,UUID group,CycleForm form){
        prepare(a);check(form!=null && form.getParticipants()!=null && form.getParticipants().size()<=50,"request");groups.group(a,group,false);var ids=form.getParticipants().stream().filter(Objects::nonNull).map(ParticipantForm::getClientId).filter(Objects::nonNull).distinct().toList();return cycles.memberLabels(a,group,ids);
    }
    @Transactional public UUID configure(AppUserPrincipal a,PolicyForm f){
        authorize(a,"CONFIGURE");access.requireActor(a,"LOAN_PRODUCTS_CONFIGURE");
        check(f!=null && f.getRequestKey()!=null && f.getProductId()!=null && f.getModel()!=null,"request");
        check(f.getMinMembers()!=null && f.getMaxMembers()!=null && f.getMinMembers()>=2 && f.getMaxMembers()>=f.getMinMembers() && f.getMaxMembers()<=50,"members");
        money(f.getMaxPrincipal(),true);money(f.getMaxGuarantee(),true);check(f.getMaxActiveCycles()!=null && f.getMaxActiveCycles()>=1 && f.getMaxActiveCycles()<=10,"form");
        text(f.getPolicyReference(),500);text(f.getAgreementReference(),500);
        check(f.getModel()!=Model.INDIVIDUAL || f.isOwnLoanGuarantors(),"ownLoanGuarantors");
        String hash=hash(f);cycles.lock(a,"GROUP_POLICY",f.getRequestKey());var retry=cycles.retry(a,f.getRequestKey(),hash,false);if(retry.isPresent())return retry.get();
        check(cycles.product(a,f.getProductId()),"product");cycles.lock(a,"GROUP_PRODUCT",f.getProductId());
        UUID id=cycles.policy(a,f,hash,clock.now());event(a,id,"GROUP_PRODUCT_RULES_SAVED",Map.of("product",f.getProductId(),"model",f.getModel(),"enabled",f.isEnabled()));return id;
    }
    @Transactional(readOnly=true) public Page<Policy> policies(AppUserPrincipal a,int page){authorize(a,"VIEW");query("",page);var rows=cycles.policies(a,page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public Policy policy(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return cycles.policy(a,id);}
    @Transactional(readOnly=true) public Cycle detail(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return cycles.cycle(a,id,false);}
    @Transactional(readOnly=true) public Page<CycleSummary> list(AppUserPrincipal a,UUID group,int page){authorize(a,"VIEW");query("",page);groups.group(a,group,false);var rows=cycles.cycles(a,group,page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public List<Participant> participants(AppUserPrincipal a,UUID id){authorize(a,"VIEW");cycles.cycle(a,id,false);return cycles.participants(a,id);}
    @Transactional public UUID create(AppUserPrincipal a,UUID group,CycleForm f){
        authorize(a,"CREATE");draft(f);String hash=hash(List.of(group,f));cycles.lock(a,"GROUP_CYCLE",f.getRequestKey());
        var retry=cycles.retry(a,f.getRequestKey(),hash,true);if(retry.isPresent())return retry.get();
        check("ACTIVE".equals(groups.group(a,group,true).status()),"inactive");var policy=cycles.policy(a,f.getPolicyId());check(policy.enabled() && cycles.current(policy,a),"policy");
        UUID id=cycles.cycle(a,group,policy,f,hash,clock.now());event(a,id,"GROUP_CYCLE_CREATED",Map.of("group",group,"policy",policy.id()));return id;
    }
    @Transactional public void save(AppUserPrincipal a,UUID id,CycleForm f){
        authorize(a,"UPDATE");draft(f);var c=cycles.cycle(a,id,true);check("DRAFT".equals(c.state()) && c.policyId().equals(f.getPolicyId()),"changed");
        cycles.save(c,f,false,clock.now());event(a,id,"GROUP_CYCLE_DRAFT_SAVED",Map.of("version",f.getExpectedVersion()+1));
    }
    @Transactional public void submit(AppUserPrincipal a,UUID id,CycleForm f){
        authorize(a,"SUBMIT");loans.authorize(a,"CREATE");draft(f);var c=cycles.cycle(a,id,true);
        if("SUBMITTED".equals(c.state()) && c.version()==f.getExpectedVersion()+1 && hash(c.form()).equals(hash(f)))return;
        check("DRAFT".equals(c.state()) && c.version()==f.getExpectedVersion() && c.policyId().equals(f.getPolicyId()),"changed");
        check(c.model()==Model.INDIVIDUAL,"modelUnavailable");cycles.lock(a,"GROUP_PRODUCT",c.productId());var p=cycles.policy(a,c.policyId());check(p.enabled() && cycles.current(p,a),"policy");
        check("ACTIVE".equals(groups.group(a,c.groupId(),true).status()) && cycles.product(a,c.productId()),"product");
        text(f.getPurpose(),500);check(f.getApplicationDate()!=null && !f.getApplicationDate().isAfter(clock.today()) && f.getFirstPaymentDate()!=null && f.getFirstPaymentDate().isAfter(f.getApplicationDate()),"date");
        check(f.getTenureValue()!=null,"terms");var tenure=new LoanTenure(f.getTenureUnit(),f.getTenureValue());
        var rows=f.getParticipants();check(rows.size()>=p.minMembers() && rows.size()<=p.maxMembers(),"members");
        var ids=rows.stream().map(ParticipantForm::getClientId).toList();check(ids.stream().allMatch(Objects::nonNull) && new HashSet<>(ids).size()==ids.size(),"members");
        cycles.lockClients(a,ids);var memberships=cycles.memberships(a,c.groupId(),ids);check(memberships.size()==ids.size(),"membership");
        var rules=cycles.rules(a,c.productId());check(rows.size()-1>=rules.guarantors(),"guarantees");check(rules.affordabilityRatio().signum()>0 && rules.affordabilityRatio().compareTo(BigDecimal.ONE)<=0,"product");
        var exposures=cycles.exposures(a,ids);var branchPolicy=qualification.groupPolicy(a.getSaccoId(),a.getStationId());
        var commands=new LinkedHashMap<UUID,RecordForm>();var payments=new HashMap<UUID,BigDecimal>();var caps=new HashMap<UUID,BigDecimal>();BigDecimal total=BigDecimal.ZERO;
        for(var row:rows){
            money(row.getPrincipal(),true);money(row.getIncome(),true);money(row.getExpenses(),false);money(row.getOtherDebt(),false);money(row.getGuaranteeCapacity(),false);
            text(row.getAssessmentEvidence(),500);text(row.getCapacityEvidence(),500);
            var exposure=exposures.get(row.getClientId());check(exposure.missingHistory()==0,"historyMissing");check(exposure.activeCycles()<p.maxActiveCycles(),"activeCycles");
            check((branchPolicy.applicantDefaultLimit()==null || exposure.riskLoans()<branchPolicy.applicantDefaultLimit())
                && (branchPolicy.guarantorDefaultLimit()==null || exposure.riskLoans()<branchPolicy.guarantorDefaultLimit()),"defaultPolicy");
            check(branchPolicy.guaranteeCountLimit()==null || exposure.guaranteeCount()+ids.size()-1<=branchPolicy.guaranteeCountLimit(),"guaranteeCount");
            int existing=exposure.activeLoans();check(existing==0 || (rules.activeLoanAllowed() && existing<rules.maximumActiveLoans()),"activeLoans");
            if(rules.collateralRequired()){text(row.getCollateral(),500);money(row.getCollateralValue(),false);check(row.getCollateralValue().compareTo(row.getPrincipal().multiply(rules.collateralRatio()))>=0,"collateral");}
            var command=new RecordForm();command.setRequestKey(key(c.id(),row.getClientId()));command.setClientId(row.getClientId());command.setProductId(c.productId());
            command.setApplicationDate(f.getApplicationDate());command.setPrincipal(row.getPrincipal());command.setTenureUnit(tenure.unit());command.setTenureValue(tenure.value());
            command.setFirstPaymentDate(f.getFirstPaymentDate());
            command.setPurpose(f.getPurpose());command.setIncome(row.getIncome());command.setExpenses(row.getExpenses());command.setOtherDebt(row.getOtherDebt());command.setInformationSource("DOCUMENTED");
            command.setEvidence(row.getAssessmentEvidence());command.setCollateral(Objects.toString(row.getCollateral(),""));command.setGuarantors(p.agreementReference());
            command.setInsurancePremium(row.getInsurancePremium());command.setInsuranceEvidence(row.getInsuranceEvidence());
            var quote=loans.interestQuote(a,command);var charge=loans.chargeQuote(a,command);
            BigDecimal maxPayment=quote.schedule().stream().map(com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow::amount).max(BigDecimal::compareTo).orElseThrow();
            BigDecimal monthly=LoanAmortizationCalculator.monthlyEquivalent(maxPayment,RepaymentFrequency.valueOf(quote.frequency()));
            BigDecimal debt=row.getOtherDebt().max(exposure.monthlyDebt());BigDecimal disposable=row.getIncome().subtract(row.getExpenses()).subtract(debt);
            check(disposable.signum()>0 && monthly.compareTo(disposable.multiply(rules.affordabilityRatio()).setScale(2,RoundingMode.DOWN))<=0,"affordability");
            payments.put(row.getClientId(),monthly);caps.put(row.getClientId(),row.getPrincipal().add(quote.interest()).add(charge.total()));commands.put(row.getClientId(),command);total=total.add(row.getPrincipal());
        }
        check(total.compareTo(p.maxPrincipal())<=0,"principalLimit");BigDecimal totalCaps=caps.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
        for(var row:rows){BigDecimal committed=exposures.get(row.getClientId()).reserved().add(totalCaps.subtract(caps.get(row.getClientId())));
            check(committed.compareTo(row.getGuaranteeCapacity().min(p.maxGuarantee()))<=0,"guaranteeCapacity");}
        var loanIds=new HashMap<UUID,UUID>();
        for(var row:rows){UUID loan=loans.save(a,commands.get(row.getClientId()));loanIds.put(row.getClientId(),loan);cycles.participant(a,c,row,memberships.get(row.getClientId()),loan,payments.get(row.getClientId()));}
        for(UUID borrower:ids)for(UUID guarantor:ids)if(!borrower.equals(guarantor))cycles.guarantee(a,c,guarantor,borrower,loanIds.get(borrower),caps.get(borrower),p.agreementReference(),clock.now());
        cycles.save(c,f,true,clock.now());event(a,id,"GROUP_CYCLE_SUBMITTED",Map.of("participants",ids.size(),"principal",total,"policy",p.id()));
    }
    @Transactional(readOnly=true) public Optional<Termination> termination(AppUserPrincipal a,UUID id){authorize(a,"VIEW");cycles.cycle(a,id,false);return cycles.termination(a,id);}
    @Transactional public void withdraw(AppUserPrincipal a,UUID id,TerminationForm form){
        authorize(a,"SUBMIT");check(form!=null && form.getExpectedVersion()>=0,"request");text(form.getReason(),500);var c=cycles.cycle(a,id,true);
        if("CANCELLED".equals(c.state())){var t=cycles.termination(a,id).orElseThrow();check(t.kind().equals("WITHDRAWN") && t.actorId().equals(a.getMemberId()) && t.reason().equals(form.getReason().strip()) && c.version()==form.getExpectedVersion()+1,"changed");return;}
        check(c.version()==form.getExpectedVersion(),"changed");check(cycles.lockUnfundedLoans(a,id),"fundedCycle");cycles.terminate(a,c,"WITHDRAWN",form.getReason().strip(),clock.now());event(a,id,"GROUP_CYCLE_WITHDRAWN",Map.of("reason",form.getReason().strip()));
    }
    private static void draft(CycleForm f){check(f!=null && f.getRequestKey()!=null && f.getPolicyId()!=null && f.getExpectedVersion()>=0 && f.getParticipants()!=null && f.getParticipants().size()<=50 && f.getParticipants().stream().allMatch(Objects::nonNull),"request");}
    private void prepare(AppUserPrincipal a){if(a!=null && a.getClaims().contains(UserClaim.GROUP_LENDING_CREATE.name()))authorize(a,"CREATE");else authorize(a,"UPDATE");}
    private static UUID key(UUID cycle,UUID client){return UUID.nameUUIDFromBytes(("GROUP_ORIGINATION/"+cycle+"/"+client).getBytes(StandardCharsets.UTF_8));}
    private String hash(Object v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(v).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void event(AppUserPrincipal a,UUID id,String action,Map<String,?> values){audit.logEvent("GROUP_LENDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"GROUP_CYCLE",id.toString(),a.getSaccoId(),a.getStationId(),new LinkedHashMap<>(values));}
    private static void query(String s,int page){check(s!=null && s.length()<=100 && page>=0 && page<=10000,"query");}
    private static void text(String s,int max){check(s!=null && !s.isBlank() && s.length()<=max && s.chars().noneMatch(c->c<32),"form");}
    private static void money(BigDecimal n,boolean positive){check(n!=null && (positive?n.signum()>0:n.signum()>=0) && n.scale()<=2 && n.precision()-n.scale()<=16,"amount");}
    private static void check(boolean b,String key){if(!b)throw new IllegalArgumentException("group.error."+key);}
}
