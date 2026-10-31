package com.sacco.mvp.clients.onboarding.service;

import com.sacco.mvp.clients.onboarding.dto.LoanIntakeDtos.*;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.Client;
import com.sacco.mvp.clients.onboarding.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.domain.AuditEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanIntakeService {
    private final OnboardingService onboarding;
    private final OnboardingRepository requests;
    private final LoanIntakeRepository records;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;

    public Client authorize(AppUserPrincipal a,UUID client,boolean write) {
        if(a!=null&&!a.isStaffSession()) {
            check(client.equals(a.getMemberId()),"owner");onboarding.ownStatus(a);
        } else onboarding.authorize(a,write?"UPDATE":"VIEW");
        return requests.client(a,client,clock.today());
    }
    @Transactional(readOnly=true) public Optional<Request> latest(AppUserPrincipal a,UUID client) {authorize(a,client,false);return records.latest(a,client);}
    @Transactional(readOnly=true) public Form form(AppUserPrincipal a,UUID client) {
        authorize(a,client,true);var f=new Form();f.setExpectedSequence(records.sequence(a,client));records.latest(a,client).ifPresent(r->f.setDetails(r.details()));return f;
    }
    @Transactional(readOnly=true) public List<Product> products(AppUserPrincipal a,UUID client,String search,int page) {
        authorize(a,client,false);check(search!=null&&search.length()<=100&&page>=0&&page<=10000,"query");return records.products(a,search,page);
    }
    @Transactional(readOnly=true) public Optional<Product> product(AppUserPrincipal a,UUID client,UUID id) {
        authorize(a,client,false);return id==null?Optional.empty():records.product(a,id);
    }
    @Transactional(timeout=20) public UUID save(AppUserPrincipal a,UUID client,Form f) {
        authorize(a,client,true);check(f.getRequestKey()!=null&&f.getExpectedSequence()>=0&&f.getDetails()!=null,"request");
        check(Set.of("DRAFT","SUBMITTED").contains(f.getAction()),"action");
        String hash=digest(json.writeValueAsString(List.of(client,f)));
        requests.lock("CLIENT_LOAN_REQUEST/"+a.getSaccoId()+"/"+a.getStationId()+"/"+f.getRequestKey());
        var retry=requests.retry(a,"client_onboarding_loan_request",f.getRequestKey(),hash);if(retry.isPresent())return retry.get();
        requests.lock("CLIENT_LOAN_INTAKE/"+client);
        check(records.sequence(a,client)==f.getExpectedSequence(),"changed");
        var d=json.readValue(json.writeValueAsString(f.getDetails()),Details.class);boolean complete=f.getAction().equals("SUBMITTED");
        text(d.getPurpose(),1000,complete);text(d.getBusiness(),1000,complete);
        money(d.getPrincipal(),true,complete);money(d.getIncome(),false,complete);money(d.getExpenses(),false,complete);money(d.getOtherDebt(),false,complete);
        check(d.getTenureValue()==null?!complete:d.getTenureValue()>0&&d.getTenureValue()<=12000,"loanTerms");
        Product product=d.getProductId()==null?null:records.product(a,d.getProductId()).orElseThrow(()->invalid("loanProduct"));
        check(!complete||product!=null,"loanProduct");
        d.setTenureUnit(product==null?"":product.unit());
        if(complete) {
            check((product.minimum()==null||d.getPrincipal().compareTo(product.minimum())>=0)&&(product.maximum()==null||d.getPrincipal().compareTo(product.maximum())<=0),"loanLimits");
            check(d.getTenureValue()>=product.minTenure()&&d.getTenureValue()<=product.maxTenure(),"loanLimits");
        }
        UUID id=UUID.randomUUID();records.save(a,client,id,f,d,hash,clock.now());
        audit.logEvent("CLIENT_LOAN_REQUEST",id,"CLIENT_LOAN_REQUEST_"+f.getAction(),a.getMemberId(),AuditEventStatus.SUCCESS,"Loan request saved","CLIENT",client.toString(),a.getSaccoId(),a.getStationId(),Map.of("sequence",f.getExpectedSequence()+1));
        return id;
    }
    @Transactional(readOnly=true) public Request ready(AppUserPrincipal a,UUID client) {
        check(authorize(a,client,false).state().equals("VERIFY"),"required");
        var r=records.latest(a,client).orElseThrow(()->invalid("loanRequest"));check(r.mode().equals("SUBMITTED"),"loanRequest");
        var p=records.product(a,r.details().getProductId()).orElseThrow(()->invalid("loanProduct"));
        check(p.unit().equals(r.details().getTenureUnit()),"loanTermsChanged");return r;
    }
    @Transactional(readOnly=true) public Map<String,String> ownApplicationValues(AppUserPrincipal a) {
        var d=ready(a,a.getMemberId()).details();var p=records.product(a,d.getProductId()).orElseThrow();var values=new LinkedHashMap<String,String>();
        values.put("amount",d.getPrincipal().toPlainString());values.put("tenureValue",d.getTenureValue().toString());values.put("tenureUnit",p.unit());
        values.put("purpose",d.getPurpose());values.put("business",d.getBusiness());values.put("monthlyIncome",d.getIncome().toPlainString());
        values.put("monthlyExpenses",d.getExpenses().toPlainString());values.put("otherDebtRepayments",d.getOtherDebt().toPlainString());return values;
    }
    @Transactional(readOnly=true) public com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm recordingForm(AppUserPrincipal a,UUID client) {
        var d=ready(a,client).details();var product=records.product(a,d.getProductId()).orElseThrow();
        var f=new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm();f.setClientId(client);f.setProductId(d.getProductId());
        f.setApplicationDate(clock.today());f.setPrincipal(d.getPrincipal());f.setTenureUnit(product.unit());f.setTenureValue(d.getTenureValue());
        f.setPurpose(d.getPurpose());f.setBusiness(d.getBusiness());f.setIncome(d.getIncome());f.setExpenses(d.getExpenses());f.setOtherDebt(d.getOtherDebt());return f;
    }
    private static void text(String v,int max,boolean required) {check(v!=null&&v.length()<=max&&(!required||!v.isBlank())&&v.codePoints().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'),"loanDetails");}
    private static void money(BigDecimal v,boolean positive,boolean required) {check(v==null?!required:v.signum()>=(positive?1:0)&&v.scale()<=2&&v.compareTo(new BigDecimal("9999999999999999.99"))<=0,"loanMoney");}
    private static String digest(String value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void check(boolean value,String key){if(!value)throw invalid(key);}
    private static IllegalArgumentException invalid(String key){return new IllegalArgumentException("kyc.error."+key);}
}
