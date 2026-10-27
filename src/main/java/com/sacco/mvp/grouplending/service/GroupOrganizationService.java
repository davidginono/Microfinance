package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.Authority;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupOrganizationService {
    private final GroupCycleRepository cycles;
    private final GroupLendingRepository groups;
    private final GroupAuthorityRepository authorities;
    private final GroupOrganizationRepository organizations;
    private final GroupOrganizationDocumentRepository documents;
    private final GeneralLedgerService access;
    private final LoanRecordingService loans;
    private final LoanQualificationPolicyService qualification;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;

    public record Workspace(Cycle cycle,Policy policy,Authority authority,OrganizationLoan loan,CycleForm form,boolean supported,
        com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.Document document) {
        public Workspace(Cycle c,Policy p,Authority a,OrganizationLoan l,CycleForm f,boolean s){this(c,p,a,l,f,s,null);}
    }
    public void authorize(AppUserPrincipal a,String action){access.requireActor(a,"GROUP_LENDING_"+action);access.requireActor(a,"GROUP_AUTHORITY_VIEW");}
    @Transactional(readOnly=true) public Workspace workspace(AppUserPrincipal a,UUID id,CycleForm submitted){
        authorize(a,"VIEW");var c=cycles.cycle(a,id,false);check(c.model()==Model.ORGANIZATION,"request");var f=submitted==null?c.form():submitted;
        if(submitted==null){f.setExpectedVersion(c.version());if(f.getOrganization()==null){f.setOrganization(new OrganizationForm());f.setParticipants(new ArrayList<>());f.getOrganization().setAuthorityId(authorities.latest(a,c.groupId()).orElse(null));}}
        var org=f.getOrganization();var authority=org==null || org.getAuthorityId()==null?null:authorities.authority(a,c.groupId(),org.getAuthorityId(),clock.today());
        var policy=cycles.policy(a,c.policyId());if(policy.releaseRules()!=null&&f.getDisclosure()==null&&"DRAFT".equals(c.state()))f.setDisclosure(new com.sacco.mvp.grouplending.dto.GroupDisclosureDtos.Declaration());
        return new Workspace(c,policy,authority,organizations.loan(a,id).orElse(null),f,organizations.enabled(a,c.policyId()),documents.latest(a,id).orElse(null));
    }
    @Transactional public void save(AppUserPrincipal a,UUID id,CycleForm f){
        authorize(a,"UPDATE");draft(f);var c=cycles.cycle(a,id,true);check(c.model()==Model.ORGANIZATION && "DRAFT".equals(c.state()) && c.policyId().equals(f.getPolicyId()),"changed");
        cycles.save(c,f,false,clock.now());event(a,id,"ORGANIZATION_DRAFT_SAVED",Map.of("version",f.getExpectedVersion()+1));
    }
    @Transactional(readOnly=true) public void useCurrentAuthority(AppUserPrincipal a,UUID id,CycleForm f){
        authorize(a,"UPDATE");draft(f);var c=cycles.cycle(a,id,false);
        check(c.model()==Model.ORGANIZATION && "DRAFT".equals(c.state()) && c.version()==f.getExpectedVersion() && c.policyId().equals(f.getPolicyId()),"changed");
        f.getOrganization().setAuthorityId(authorities.latest(a,c.groupId()).orElse(null));
    }
    @Transactional public UUID submit(AppUserPrincipal a,UUID id,CycleForm f){
        authorize(a,"SUBMIT");loans.authorize(a,"CREATE");draft(f);var c=cycles.cycle(a,id,true);
        if("SUBMITTED".equals(c.state()) && c.version()==f.getExpectedVersion()+1 && json.writeValueAsString(c.form()).equals(json.writeValueAsString(f)))return organizations.loan(a,id).orElseThrow().loanId();
        check(c.model()==Model.ORGANIZATION && "DRAFT".equals(c.state()) && c.version()==f.getExpectedVersion() && c.policyId().equals(f.getPolicyId()),"changed");
        check(documents.latest(a,id).isPresent(),"documentRequired");
        check(organizations.enabled(a,c.policyId()),"organizationPolicy");cycles.lockProductPolicy(a,c.productId());var p=cycles.policy(a,c.policyId());check(p.enabled() && cycles.current(p,a) && cycles.product(a,c.productId()),"policy");
        var group=groups.group(a,c.groupId(),true);check("ACTIVE".equals(group.status()) && "ORGANIZATION".equals(group.legalForm()),"organizationIdentity");
        var o=f.getOrganization();check(o.getAuthorityId()!=null && o.getRepresentativeId()!=null,"authoritySigners");var h=authorities.authority(a,c.groupId(),o.getAuthorityId(),clock.today());
        check("CURRENT".equals(h.state()) && !organizations.usedAuthority(a,h.id()),"organizationAuthority");
        check(h.signers().size()>=p.minMembers() && h.signers().size()<=p.maxMembers() && h.signers().stream().anyMatch(s->s.clientId().equals(o.getRepresentativeId()) && s.mayApply()),"authoritySigners");
        check(authorities.people(a,h.signers().stream().map(s->s.clientId()).toList()).size()==h.signers().size(),"client");
        check(f.getApplicationDate()!=null && !f.getApplicationDate().isAfter(clock.today()) && !f.getApplicationDate().isBefore(h.recordedAt().atZoneSameInstant(java.time.ZoneId.of("Africa/Dar_es_Salaam")).toLocalDate())
            && !f.getApplicationDate().isBefore(h.validFrom()) && !f.getApplicationDate().isAfter(h.validThrough()) && f.getFirstPaymentDate()!=null && f.getFirstPaymentDate().isAfter(f.getApplicationDate()),"date");
        check(f.getTenureValue()!=null,"terms");var tenure=new LoanTenure(f.getTenureUnit(),f.getTenureValue());
        text(f.getPurpose(),500,true);text(o.getBusiness(),1000,true);text(o.getAssessmentEvidence(),500,true);money(o.getPrincipal(),true);money(o.getIncome(),true);money(o.getExpenses(),false);money(o.getOtherDebt(),false);
        check(o.getPrincipal().compareTo(p.maxPrincipal().min(h.maxPrincipal()))<=0,"principalLimit");var rules=cycles.rules(a,c.productId());
        check(rules.guarantors()>=0 && rules.guarantors()<=50,"guarantees");check(rules.affordabilityRatio().signum()>0 && rules.affordabilityRatio().compareTo(BigDecimal.ONE)<=0,"product");
        if(rules.collateralRequired()){text(o.getCollateral(),500,true);money(o.getCollateralValue(),false);check(o.getCollateralValue().compareTo(o.getPrincipal().multiply(rules.collateralRatio()))>=0,"collateral");}
        UUID party=organizations.party(a,c.groupId());var exposure=organizations.exposure(a,party);check(exposure.missingHistory()==0,"historyMissing");check(exposure.activeCycles()<p.maxActiveCycles(),"activeCycles");
        check(exposure.activeLoans()==0 || (rules.activeLoanAllowed() && exposure.activeLoans()<rules.maximumActiveLoans()),"activeLoans");var branch=qualification.groupPolicy(a.getSaccoId(),a.getStationId());
        check(branch.applicantDefaultLimit()==null || exposure.riskLoans()<branch.applicantDefaultLimit(),"defaultPolicy");
        var command=new RecordForm();command.setGroupDisclosure(GroupDisclosure.snapshot(p,f.getDisclosure()));command.setRequestKey(UUID.nameUUIDFromBytes(("ORGANIZATION_ORIGINATION/"+id).getBytes(StandardCharsets.UTF_8)));command.setProductId(c.productId());command.setPrincipal(o.getPrincipal());
        command.setApplicationDate(f.getApplicationDate());command.setFirstPaymentDate(f.getFirstPaymentDate());command.setTenureUnit(tenure.unit());command.setTenureValue(tenure.value());command.setPurpose(f.getPurpose());command.setBusiness(o.getBusiness());
        command.setIncome(o.getIncome());command.setExpenses(o.getExpenses());command.setOtherDebt(o.getOtherDebt());command.setInformationSource("DOCUMENTED");command.setEvidence(o.getAssessmentEvidence());command.setCollateral(o.getCollateral());command.setGuarantors("");command.setInsurancePremium(o.getInsurancePremium());command.setInsuranceEvidence(o.getInsuranceEvidence());
        var quote=loans.interestQuote(a,command);var maxPayment=quote.schedule().stream().map(com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow::amount).max(BigDecimal::compareTo).orElseThrow();
        var monthly=LoanAmortizationCalculator.monthlyEquivalent(maxPayment,RepaymentFrequency.valueOf(quote.frequency()));var disposable=o.getIncome().subtract(o.getExpenses()).subtract(o.getOtherDebt().max(exposure.monthlyDebt()));
        check(disposable.signum()>0 && monthly.compareTo(disposable.multiply(rules.affordabilityRatio()).setScale(2,RoundingMode.DOWN))<=0,"affordability");
        var payout=new PaymentDetailsService.PaymentDetailsView(true,h.payoutType(),h.payoutType().name(),h.payoutProvider(),h.payoutHolder(),h.payoutIdentifier(),"GROUP_AUTHORITY");
        UUID loan=loans.saveOrganization(a,command,party,json.writeValueAsString(payout));organizations.record(a,c,loan,o,monthly,clock.now());cycles.save(c,f,true,clock.now());
        event(a,id,"ORGANIZATION_LOAN_SUBMITTED",Map.of("loan",loan,"authority",h.id(),"principal",o.getPrincipal()));return loan;
    }
    private static void draft(CycleForm f){check(f!=null && f.getRequestKey()!=null && f.getPolicyId()!=null && f.getExpectedVersion()>=0 && f.getOrganization()!=null && f.getParticipants()!=null && f.getParticipants().isEmpty(),"request");}
    private void event(AppUserPrincipal a,UUID id,String action,Map<String,?> data){audit.logEvent("GROUP_LENDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"GROUP_CYCLE",id.toString(),a.getSaccoId(),a.getStationId(),new LinkedHashMap<>(data));}
    private static void money(BigDecimal n,boolean positive){check(n!=null && (positive?n.signum()>0:n.signum()>=0) && n.scale()<=2 && n.precision()-n.scale()<=16,"amount");}
    private static void text(String s,int max,boolean required){check(s!=null && s.length()<=max && (!required || !s.isBlank()) && s.chars().noneMatch(c->c<32),"form");}
    private static void check(boolean b,String key){if(!b)throw new IllegalArgumentException("group.error."+key);}
}
