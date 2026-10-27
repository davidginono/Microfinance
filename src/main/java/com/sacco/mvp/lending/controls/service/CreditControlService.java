package com.sacco.mvp.lending.controls.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.lending.controls.dto.CreditControlDtos.*;
import com.sacco.mvp.lending.controls.dto.CreditControlDtos.Case;
import com.sacco.mvp.lending.controls.repository.CreditControlRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class CreditControlService {
    private final CreditControlRepository records;
    private final GeneralLedgerService access;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    public void authorize(AppUserPrincipal a,String feature,String action){access.requireActor(a,feature+"_VIEW");if(!action.equals("VIEW"))access.requireActor(a,feature+"_"+action);}
    private static void check(boolean valid,String key){if(!valid)throw new IllegalArgumentException("credit.error."+key);}
    private static void text(String value,int max,String key){check(value!=null&&!value.isBlank()&&value.length()<=max&&value.codePoints().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'),key);}
    private static void money(BigDecimal value,boolean positive,String key){check(value!=null&&value.scale()<=2&&value.precision()-value.scale()<=16&&(positive?value.signum()>0:value.signum()>=0),key);}
    private static void query(String search,int page){check(search!=null&&search.length()<=100&&page>=0&&page<=10000,"query");}
    private void date(LocalDate value,String key){check(value!=null&&!value.isAfter(clock.today())&&value.getYear()>=1901,key);}
    private void next(LocalDate value){check(value!=null&&!value.isBefore(clock.today())&&!value.isAfter(clock.today().plusYears(1)),"nextAction");}
    private static <T> Page<T> page(List<T> rows,int page){return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private Optional<UUID> retry(AppUserPrincipal a,String table,UUID key,String hash){check(key!=null,"request");records.lock(table+"/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);return records.retry(a,table,key,hash);}
    private void event(AppUserPrincipal a,UUID id,String action){audit.logEvent("CREDIT_CONTROL",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"CREDIT_CONTROL",id.toString(),a.getSaccoId(),a.getStationId(),Map.of());}
    public LocalDate today(){return clock.today();}
    @Transactional(readOnly=true) public List<Choice> officerChoices(AppUserPrincipal a,String search,int page){authorize(a,"COLLECTIONS","VIEW");query(search,page);return records.officers(a,search,page);}
    @Transactional(readOnly=true) public Choice officerChoice(AppUserPrincipal a,UUID id){authorize(a,"COLLECTIONS","VIEW");return id==null?null:records.officerChoice(a,id).orElse(null);}
    @Transactional(readOnly=true) public List<Choice> loanChoices(AppUserPrincipal a,String feature,String search,int page){check(Set.of("COLLATERAL","COLLECTIONS").contains(feature),"query");authorize(a,feature,"VIEW");query(search,page);return records.loanChoices(a,search,page);}
    @Transactional(readOnly=true) public Policy policy(AppUserPrincipal a){authorize(a,"CREDIT_CONTROLS","VIEW");return records.policy(a).orElse(new Policy(null,0,false,false,"",false,null,null));}
    @Transactional(timeout=15) public UUID configure(AppUserPrincipal a,PolicyForm f){
        authorize(a,"CREDIT_CONTROLS","CONFIGURE");check(f.isVerified(),"verified");text(f.getReference(),500,"reference");
        if(f.isRemindersEnabled()){check(f.getReminderDaysBefore()!=null&&f.getReminderDaysBefore()>=0&&f.getReminderDaysBefore()<=30,"reminderDaysBefore");check(f.getReminderEveryDays()!=null&&f.getReminderEveryDays()>0&&f.getReminderEveryDays()<=30,"reminderEveryDays");}
        else check(f.getReminderDaysBefore()==null&&f.getReminderEveryDays()==null,"fields");
        var hash=hash(f);var old=retry(a,"lending_credit_policy",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        records.lock("CREDIT_POLICY/"+a.getSaccoId()+"/"+a.getStationId());check(records.policy(a).map(Policy::revision).orElse(0)==f.getExpectedRevision(),"changed");
        UUID id=UUID.randomUUID();records.policy(a,id,f,hash,clock.now());event(a,id,"CREDIT_POLICY_RECORDED");return id;
    }
    @Transactional(readOnly=true) public Page<Guarantee> guarantees(AppUserPrincipal a,String search,int page,String sort){authorize(a,"GUARANTOR_CAPACITY","VIEW");query(search,page);return page(records.guarantees(a,search,page,order(sort,Map.of("loan","loan_number","borrower","borrower","guarantor","guarantor","status","g.status","liability","liability"),"loan"),clock.today()),page);}
    @Transactional(readOnly=true) public Guarantee guarantee(AppUserPrincipal a,UUID id){authorize(a,"GUARANTOR_CAPACITY","VIEW");return records.guarantee(a,id,clock.today(),false);}
    @Transactional(readOnly=true) public Page<Assessment> assessments(AppUserPrincipal a,UUID id,int page){guarantee(a,id);query("",page);return page(records.assessments(a,id,page),page);}
    @Transactional(timeout=15) public UUID assess(AppUserPrincipal a,UUID request,AssessmentForm f){
        authorize(a,"GUARANTOR_CAPACITY","CREATE");String hash=hash(List.of(request,f));var old=retry(a,"lending_guarantor_assessment",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        money(f.getIncome(),true,"income");money(f.getExpenses(),false,"expenses");money(f.getOtherDebt(),false,"otherDebt");money(f.getCapacity(),false,"capacity");
        check(f.getIncome().subtract(f.getExpenses()).subtract(f.getOtherDebt()).signum()>0,"cashFlow");date(f.getAssessedOn(),"assessedOn");check(f.getValidThrough()!=null&&!f.getValidThrough().isBefore(clock.today())&&!f.getValidThrough().isBefore(f.getAssessedOn()),"validThrough");
        text(f.getEvidence(),1000,"evidence");text(f.getPolicyReference(),500,"reference");check(f.isVerified(),"verified");
        var g=records.guarantee(a,request,clock.today(),true);check(g.subject()!=null&&!g.subject().equals("CLIENT/"+a.getMemberId()),"independent");check(Set.of("PENDING","APPROVED").contains(g.status()),"state");
        UUID id=UUID.randomUUID();records.assessment(a,id,g,f,hash,clock.now());event(a,id,"GUARANTOR_ASSESSED");return id;
    }
    @Transactional(readOnly=true) public Page<Asset> assets(AppUserPrincipal a,String search,int page,String sort){authorize(a,"COLLATERAL","VIEW");query(search,page);return page(records.assets(a,search,page,order(sort,Map.of("code","a.code","description","a.description","owner","a.owner_name","value","value","pledged","pledged","expires","v.valid_through"),"code")),page);}
    @Transactional(readOnly=true) public Asset asset(AppUserPrincipal a,UUID id){authorize(a,"COLLATERAL","VIEW");return records.asset(a,id,false);}
    @Transactional(readOnly=true) public Page<CollateralEvent> collateralHistory(AppUserPrincipal a,UUID id,int page){asset(a,id);query("",page);return page(records.collateralHistory(a,id,page),page);}
    @Transactional(readOnly=true) public Page<Pledge> pledges(AppUserPrincipal a,UUID id,int page){asset(a,id);query("",page);return page(records.pledges(a,id,page),page);}
    @Transactional(timeout=15) public UUID createAsset(AppUserPrincipal a,AssetForm f){
        authorize(a,"COLLATERAL","CREATE");text(f.getCode(),100,"code");text(f.getDescription(),1000,"description");text(f.getOwner(),255,"owner");text(f.getOwnerEvidence(),1000,"ownerEvidence");check(f.isVerified(),"verified");String hash=hash(f);var old=retry(a,"lending_collateral_asset",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        UUID id=UUID.randomUUID();records.asset(a,id,f,hash,clock.now());event(a,id,"COLLATERAL_REGISTERED");return id;
    }
    @Transactional(timeout=20) public UUID collateral(AppUserPrincipal a,UUID id,CollateralForm f){
        authorize(a,"COLLATERAL",Set.of("RELEASE","RECOVERY").contains(f.getKind())?f.getKind():"UPDATE");check(Set.of("VALUATION","PLEDGE","RELEASE","CUSTODY","RECOVERY").contains(f.getKind()),"kind");date(f.getEffectiveDate(),"effectiveDate");text(f.getEvidence(),1000,"evidence");text(f.getReason(),1000,"reason");check(f.isVerified(),"verified");
        String hash=hash(List.of(id,f));var old=retry(a,"lending_collateral_event",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        // Loan -> asset matches funding's lock order. A valuation never locks a loan.
        Loan loan=f.getLoanId()==null?null:records.loan(a,f.getLoanId(),clock.today(),true);var asset=records.asset(a,id,true);check(asset.sequence()==f.getExpectedSequence(),"changed");
        check(!f.getEffectiveDate().isBefore(records.assetDate(id)),"effectiveDate");
        switch(f.getKind()){
            case "VALUATION"->{check(loan==null,"loan");money(f.getAmount(),true,"amount");money(f.getEncumbrance(),false,"encumbrance");check(f.getEncumbrance().compareTo(f.getAmount())<=0,"encumbrance");check(f.getValidThrough()!=null&&!f.getValidThrough().isBefore(clock.today())&&!f.getValidThrough().isBefore(f.getEffectiveDate()),"validThrough");}
            case "PLEDGE"->{check(loan!=null&&!Set.of("PAID","WITHDRAWN","DISBURSEMENT_REVERSED","REJECTED").contains(loan.status())&&!loan.status().endsWith("REJECTED"),"loan");money(f.getAmount(),true,"amount");check(f.getEncumbrance()==null&&f.getValidThrough()==null,"fields");check(asset.value()!=null&&asset.validThrough()!=null&&!asset.validThrough().isBefore(clock.today()),"valuation");check(!records.pledged(id,f.getLoanId()),"alreadyPledged");check(asset.pledged().add(f.getAmount()).compareTo(asset.value().subtract(asset.encumbrance()))<=0,"pledgeCapacity");}
            case "RELEASE"->{check(loan!=null&&records.pledged(id,f.getLoanId()),"loan");check(Set.of("PAID","WITHDRAWN","DISBURSEMENT_REVERSED","REJECTED").contains(loan.status())||loan.status().endsWith("REJECTED"),"release");check(f.getAmount()==null&&f.getEncumbrance()==null&&f.getValidThrough()==null,"fields");if(loan.status().equals("PAID"))check(loan.tracked()&&loan.principal().signum()==0&&loan.dueInterest().signum()==0&&loan.futureInterest().signum()==0&&loan.charges().signum()==0,"history");check(!records.lastPledgeActor(id,f.getLoanId()).equals(a.getMemberId()),"independent");}
            case "RECOVERY"->{check(loan!=null&&Set.of("PAR","DEFAULTED","WRITTEN_OFF").contains(loan.status())&&records.pledged(id,loan.id()),"loan");check(f.getAmount()==null&&f.getEncumbrance()==null&&f.getValidThrough()==null,"fields");}
            default->check(f.getAmount()==null&&f.getEncumbrance()==null&&f.getValidThrough()==null&&loan==null,"fields");
        }
        UUID event=UUID.randomUUID();records.collateral(a,event,id,f,hash,clock.now());event(a,event,"COLLATERAL_"+f.getKind());return event;
    }
    @Transactional(readOnly=true) public Page<Loan> loans(AppUserPrincipal a,String feature,String search,int page,boolean overdue){check(Set.of("COLLATERAL","COLLECTIONS").contains(feature),"query");authorize(a,feature,"VIEW");query(search,page);return page(records.loans(a,search,page,clock.today(),overdue),page);}
    @Transactional(readOnly=true) public Loan loan(AppUserPrincipal a,String feature,UUID id){check(Set.of("COLLATERAL","COLLECTIONS").contains(feature),"query");authorize(a,feature,"VIEW");return records.loan(a,id,clock.today(),false);}
    @Transactional(readOnly=true) public Page<Choice> officers(AppUserPrincipal a,String search,int page){authorize(a,"COLLECTIONS","VIEW");query(search,page);return page(records.officers(a,search,page),page);}
    @Transactional(readOnly=true) public Page<Case> cases(AppUserPrincipal a,String search,int page,String sort){authorize(a,"COLLECTIONS","VIEW");query(search,page);return page(records.cases(a,search,page,order(sort,Map.of("loan","loan_number","borrower","borrower","state","state","officer","officer.full_name","next","action_on","arrears","arrears","oldest","oldest_due"),"next"),clock.today()),page);}
    @Transactional(readOnly=true) public Case collection(AppUserPrincipal a,UUID id){authorize(a,"COLLECTIONS","VIEW");return records.collection(a,id,clock.today(),false);}
    @Transactional(readOnly=true) public Page<CollectionEvent> collectionHistory(AppUserPrincipal a,UUID id,int page){collection(a,id);query("",page);return page(records.collectionHistory(a,id,page),page);}
    @Transactional(timeout=20) public UUID createCase(AppUserPrincipal a,CaseForm f){
        authorize(a,"COLLECTIONS","CREATE");String hash=hash(f);var old=retry(a,"lending_collection_case",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        text(f.getReason(),1000,"reason");next(f.getNextAction());check(records.officer(a,f.getAssignedTo()),"assignedTo");
        var loan=records.loan(a,f.getLoanId(),clock.today(),true);check(loan.tracked(),"history");check(Set.of("DISBURSED","PAR","DEFAULTED","WRITTEN_OFF").contains(loan.status())&&loan.arrears().signum()>0,"arrears");check(!records.openCase(loan.id()),"openCase");
        UUID id=UUID.randomUUID();records.collection(a,id,f,hash,clock.now());event(a,id,"COLLECTION_OPENED");return id;
    }
    @Transactional(timeout=20) public UUID followUp(AppUserPrincipal a,UUID id,CollectionForm f){
        authorize(a,"COLLECTIONS","UPDATE");String hash=hash(List.of(id,f));var old=retry(a,"lending_collection_event",f.getRequestKey(),hash);if(old.isPresent())return old.get();
        check(Set.of("CONTACT","PROMISE","DISPUTE","RESOLVE","NOTICE","ASSIGN","CLOSE","REOPEN").contains(f.getKind()),"kind");date(f.getEffectiveDate(),"effectiveDate");text(f.getEvidence(),1000,"evidence");if(!f.getKind().equals("CLOSE"))next(f.getNextAction());else check(f.getNextAction()==null,"fields");
        var previous=records.collection(a,id,clock.today(),false);var loan=records.loan(a,previous.loanId(),clock.today(),true);var c=records.collection(a,id,clock.today(),true);check(c.sequence()==f.getExpectedSequence(),"changed");check(loan.tracked(),"history");
        check(!f.getEffectiveDate().isBefore(records.caseDate(id)),"effectiveDate");check(c.state().equals("CLOSE")==f.getKind().equals("REOPEN"),"state");
        if(f.getKind().equals("REOPEN"))check(loan.arrears().signum()>0&&!records.otherOpenCase(loan.id(),id),"openCase");
        if(f.getKind().equals("RESOLVE"))check(c.state().equals("DISPUTE"),"state");
        if(f.getKind().equals("PROMISE")){money(f.getAmount(),true,"amount");next(f.getPromiseOn());check(f.getAmount().compareTo(loan.principal().add(loan.dueInterest()).add(loan.charges()))<=0,"promiseAmount");}else check(f.getAmount()==null&&f.getPromiseOn()==null,"fields");
        if(f.getKind().equals("ASSIGN"))check(records.officer(a,f.getAssignedTo()),"assignedTo");else check(f.getAssignedTo()==null,"fields");
        UUID event=UUID.randomUUID();records.collectionEvent(a,event,id,f,hash,clock.now());event(a,event,"COLLECTION_"+f.getKind());return event;
    }
    private static String order(String sort,Map<String,String> columns,String fallback){
        String value=sort==null||sort.isBlank()?fallback+"_asc":sort;int split=value.lastIndexOf('_');check(split>0,"query");String column=columns.get(value.substring(0,split)),direction=value.substring(split+1);check(column!=null&&Set.of("asc","desc").contains(direction),"query");return column+" "+direction+" nulls last";
    }
}
