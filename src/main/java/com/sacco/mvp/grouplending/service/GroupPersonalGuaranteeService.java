package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupPersonalGuaranteeDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupPersonalGuaranteeService {
    private final GroupPersonalGuaranteeRepository offers;
    private final GroupCycleRepository cycles;
    private final GeneralLedgerService access;
    private final LoanQualificationPolicyService qualification;
    private final LoanRecordingService loans;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final ReportExportLimiter limiter;
    private final java.util.concurrent.Semaphore uploads=new java.util.concurrent.Semaphore(2);
    private void staff(AppUserPrincipal a,boolean write){access.requireActor(a,"GROUP_LENDING_"+(write?"UPDATE":"VIEW"));access.requireActor(a,write?"LOAN_RECORDING_CREATE":"LOAN_RECORDING_VIEW");}

    @Transactional(readOnly=true) public Workspace workspace(AppUserPrincipal a,UUID cycle,int page,Form form){staff(a,form!=null);page(page);var s=offers.source(a,cycle,false);var f=form==null?new Form():form;var rows=offers.staff(a,cycle,clock.today(),page);return new Workspace(s,new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25),f,f.getClientId()==null?null:offers.person(a,f.getClientId()).orElse(null),offers.ready(s.loan()));}
    @Transactional(readOnly=true) public List<Choice> choices(AppUserPrincipal a,UUID cycle,String search,int page){staff(a,true);page(page);check(search!=null&&search.length()<=100,"query");offers.source(a,cycle,false);return offers.choices(a,cycle,search,page);}

    private static void validate(Form f){check(f!=null&&f.getRequestKey()!=null&&f.getClientId()!=null,"request");check(f.getCapacity()!=null&&f.getCapacity().signum()>0&&f.getCapacity().scale()<=2&&f.getCapacity().precision()-f.getCapacity().scale()<=16,"amount");text(f.getCapacityEvidence(),500);check(f.getValidThrough()!=null&&f.getValidThrough().getYear()>=1&&f.getValidThrough().getYear()<=9999,"date");GroupAgreementDocumentValidation.validate(f.getDocument());}
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public UUID record(AppUserPrincipal a,UUID cycle,Form f,MultipartFile file){
        staff(a,true);validate(f);check(file!=null&&!file.isEmpty()&&file.getSize()<=GroupOrganizationDocumentService.MAX_BYTES,"documentPdf");check(uploads.tryAcquire(),"documentBusy");
        try{byte[] bytes;try{bytes=file.getBytes();}catch(IOException e){throw new IllegalArgumentException("group.error.documentPdf",e);}check(bytes.length>=5&&bytes.length<=GroupOrganizationDocumentService.MAX_BYTES&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"),"documentPdf");int pages=GroupAgreementDocumentValidation.pages(bytes);
            String name=Objects.toString(file.getOriginalFilename(),"personal-guarantee.pdf").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).strip();check(!name.isBlank()&&name.length()<=200&&name.chars().noneMatch(Character::isISOControl),"documentPdf");
            String filename=name,checksum=hash(bytes),payload=hash(json.writeValueAsString(List.of(cycle,f,filename,checksum)).getBytes(StandardCharsets.UTF_8));
            return new TransactionTemplate(transactions).execute(tx->{staff(a,true);offers.requestLock(a,f.getRequestKey());var s=offers.source(a,cycle,true);var retry=offers.retry(a,f.getRequestKey(),payload);if(retry.isPresent())return retry.get();
                check(s.required()>0&&s.required()<=50&&s.active()&&s.status().equals("AWAITING_GUARANTORS"),"personalGuaranteesClosed");check(!f.getValidThrough().isBefore(clock.today()),"date");qualification.lockGuarantorCapacity(a.getSaccoId(),a.getStationId(),f.getClientId());var p=offers.person(a,f.getClientId()).orElseThrow(()->new IllegalArgumentException("group.error.client"));check(!offers.jointBorrower(cycle,p.id()),"personalGuarantorOwnDebt");check(offers.available(a,s.loan(),p.id()),"personalGuaranteesClosed");capacity(a,p.id(),f.getCapacity(),s.cap(),true);check(s.cap().compareTo(s.maxGuarantee())<=0,"guaranteeCapacity");
                String snapshot=json.writeValueAsString(Map.of("financial",json.readerFor(Map.class).with(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(s.financial()),"borrower",s.borrower(),"loan",s.loan(),"client",p.id(),"cap",s.cap(),"capacity",f.getCapacity(),"validThrough",f.getValidThrough(),"documentSha256",checksum));String packet=hash(snapshot.getBytes(StandardCharsets.UTF_8));
                var id=offers.record(a,s,f,p,payload,packet,json.writeValueAsString(f.getDocument()),snapshot,filename,checksum,pages,bytes,clock.now());event(a,id,"PERSONAL_GUARANTEE_OFFERED",Map.of("loan",s.loan(),"client",p.id(),"cap",s.cap(),"sha256",checksum));return id;});
        }finally{uploads.release();}
    }
    private void capacity(AppUserPrincipal a,UUID client,BigDecimal budget,BigDecimal cap,boolean added){
        check(!offers.offBranch(a,client),"historyMissing");var x=cycles.exposures(a,List.of(client)).get(client);var policy=qualification.groupPolicy(a.getSaccoId(),a.getStationId());check(x.missingHistory()==0,"historyMissing");check(policy.guarantorActiveLoanAllowed()||x.activeLoans()+x.pendingApplications()==0,"activeLoans");check(policy.guarantorDefaultLimit()==null||x.riskLoans()<policy.guarantorDefaultLimit(),"defaultPolicy");check(policy.guaranteeCountLimit()==null||x.guaranteeCount()+(added?1:0)<=policy.guaranteeCountLimit(),"guaranteeCount");check(x.reserved().add(added?cap:BigDecimal.ZERO).compareTo(budget)<=0,"guaranteeCapacity");
    }

    @Transactional(readOnly=true) public Packet staffPacket(AppUserPrincipal a,UUID cycle,UUID id){staff(a,false);var o=offers.offer(a,id,clock.today(),false);check(o.cycle().equals(cycle),"request");return new Packet(o,offers.source(a,cycle,false),json.readValue(offers.documentMetadata(a,id),com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm.class),false,false,false,false);}

    @Transactional(readOnly=true) public byte[] staffDocument(AppUserPrincipal a,UUID cycle,UUID id){staff(a,false);access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");var o=offers.offer(a,id,clock.today(),false);check(o.cycle().equals(cycle),"request");return limiter.run(()->offers.body(a,id));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30) public Evidence evidence(AppUserPrincipal a,UUID cycle,UUID id){
        staff(a,false);access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");
        var o=offers.offer(a,id,clock.today(),false);check(o.cycle().equals(cycle)&&o.state().equals("ACCEPTED"),"personalGuaranteesClosed");var s=offers.source(a,o.cycle(),false);return new Evidence(new Packet(o,s,json.readValue(offers.documentMetadata(a,id),com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm.class),true,false,true,false),offers.body(a,id));
    }
    @Transactional public void cancel(AppUserPrincipal a,UUID cycle,UUID id,String reason){staff(a,true);text(reason,500);var s=offers.source(a,cycle,true);var o=offers.offer(a,id,clock.today(),false);check(o.cycle().equals(cycle),"request");if(o.state().equals("CANCELLED")&&o.reason().equals(reason.strip()))return;check(s.status().equals("AWAITING_GUARANTORS")&&Set.of("PENDING","EXPIRED").contains(o.state()),"personalGuaranteesClosed");offers.decide(a,o,"CANCELLED",reason.strip(),null,null,clock.now());event(a,id,"PERSONAL_GUARANTEE_CANCELLED",Map.of("reason",reason.strip()));}
    @Transactional public void startReview(AppUserPrincipal a,UUID cycle){staff(a,true);access.requireActor(a,"GROUP_LENDING_SUBMIT");var s=offers.source(a,cycle,true);loans.startGuaranteedGroupReview(a,s.loan());}

    /** Invalid OTP attempts commit while returning a stable recoverable error. */

    private static String hash(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void event(AppUserPrincipal a,UUID id,String action,Map<String,?> values){audit.logEvent("GROUP_LENDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"PERSONAL_GUARANTEE",id.toString(),a.getSaccoId(),a.getStationId(),new LinkedHashMap<>(values));}
    private static void text(String s,int max){check(s!=null&&!s.isBlank()&&s.length()<=max&&s.chars().noneMatch(Character::isISOControl),"form");}
    private static void page(int p){check(p>=0&&p<=10000,"query");}
    private static void check(boolean condition,String key){if(!condition)throw new IllegalArgumentException("group.error."+key);}
}
