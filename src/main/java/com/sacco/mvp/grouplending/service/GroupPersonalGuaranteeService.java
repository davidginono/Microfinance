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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final GroupConsentService clients;
    private final LoanQualificationPolicyService qualification;
    private final LoanRecordingService loans;
    private final ApplicationClock clock;
    private final StationOtpSettingsService otp;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final ReportExportLimiter limiter;
    private final java.util.concurrent.Semaphore uploads=new java.util.concurrent.Semaphore(2);
    private static final SecureRandom RANDOM=new SecureRandom();
    private void staff(AppUserPrincipal a,boolean write){access.requireActor(a,"GROUP_LENDING_"+(write?"UPDATE":"VIEW"));access.requireActor(a,write?"LOAN_RECORDING_CREATE":"LOAN_RECORDING_VIEW");}
    private Member client(AppUserPrincipal a,UserClaim... claims){if(a==null||a.isStaffSession())throw new AccessDeniedException("Named guarantor session required");return clients.authorize(a,claims);}
    @Transactional(readOnly=true) public Workspace workspace(AppUserPrincipal a,UUID cycle,int page,Form form){staff(a,form!=null);page(page);var s=offers.source(a,cycle,false);var f=form==null?new Form():form;var rows=offers.staff(a,cycle,clock.today(),page);return new Workspace(s,new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25),f,f.getClientId()==null?null:offers.person(a,f.getClientId()).orElse(null),offers.ready(s.loan()));}
    @Transactional(readOnly=true) public List<Choice> choices(AppUserPrincipal a,UUID cycle,String search,int page){staff(a,true);page(page);check(search!=null&&search.length()<=100,"query");offers.source(a,cycle,false);return offers.choices(a,cycle,search,page);}
    @Transactional(readOnly=true) public Page<Offer> list(AppUserPrincipal a,int page){client(a,UserClaim.GUARANTOR_REQUESTS_VIEW);page(page);var rows=offers.client(a,clock.today(),page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
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
    @Transactional(readOnly=true) public Packet packet(AppUserPrincipal a,UUID id){var m=client(a,UserClaim.GUARANTOR_REQUESTS_VIEW);var o=offers.offer(a,id,clock.today(),true);var s=offers.source(a,o.cycle(),false);return new Packet(o,s,json.readValue(offers.documentMetadata(a,id),com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm.class),offers.opened(a,id),otp.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId()),m.getSignatureText()!=null&&!m.getSignatureText().isBlank(),open(o,s));}
    private static boolean open(Offer o,Source s){return o.state().equals("PENDING")&&s.status().equals("AWAITING_GUARANTORS")&&s.active();}
    private record Locked(Offer offer,Source source) {}
    private Locked locked(AppUserPrincipal a,UUID id,String hash){var prior=offers.offer(a,id,clock.today(),true);var s=offers.source(a,prior.cycle(),true);var o=offers.offer(a,id,clock.today(),true);check(o.hash().equals(hash),"changed");return new Locked(o,s);}
    private Offer writable(AppUserPrincipal a,UUID id,String hash,UserClaim action){client(a,UserClaim.GUARANTOR_REQUESTS_VIEW,action);var l=locked(a,id,hash);check(open(l.offer(),l.source()),"personalGuaranteesClosed");return l.offer();}
    @Transactional(readOnly=true) public Packet staffPacket(AppUserPrincipal a,UUID cycle,UUID id){staff(a,false);var o=offers.offer(a,id,clock.today(),false);check(o.cycle().equals(cycle),"request");return new Packet(o,offers.source(a,cycle,false),json.readValue(offers.documentMetadata(a,id),com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm.class),false,false,false,false);}
    @Transactional public byte[] clientDocument(AppUserPrincipal a,UUID id){client(a,UserClaim.GUARANTOR_REQUESTS_VIEW,UserClaim.LOAN_DOCUMENTS_VIEW,UserClaim.LOAN_DOCUMENTS_EXPORT);var o=offers.offer(a,id,clock.today(),true);offers.source(a,o.cycle(),true);return limiter.run(()->{var bytes=offers.body(a,id);offers.opened(a,o,clock.now());event(a,id,"PERSONAL_GUARANTEE_DOCUMENT_OPENED",Map.of("sha256",o.sha256()));return bytes;});}
    @Transactional(readOnly=true) public byte[] staffDocument(AppUserPrincipal a,UUID cycle,UUID id){staff(a,false);access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");var o=offers.offer(a,id,clock.today(),false);check(o.cycle().equals(cycle),"request");return limiter.run(()->offers.body(a,id));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30) public Evidence evidence(AppUserPrincipal a,UUID cycle,UUID id,boolean own){
        if(own)client(a,UserClaim.GUARANTOR_REQUESTS_VIEW,UserClaim.LOAN_DOCUMENTS_VIEW,UserClaim.LOAN_DOCUMENTS_EXPORT);else {staff(a,false);access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");}
        var o=offers.offer(a,id,clock.today(),own);check((own||o.cycle().equals(cycle))&&o.state().equals("ACCEPTED"),"personalGuaranteesClosed");var s=offers.source(a,o.cycle(),false);return new Evidence(new Packet(o,s,json.readValue(offers.documentMetadata(a,id),com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm.class),true,false,true,false),offers.body(a,id));
    }
    @Transactional public void cancel(AppUserPrincipal a,UUID cycle,UUID id,String reason){staff(a,true);text(reason,500);var s=offers.source(a,cycle,true);var o=offers.offer(a,id,clock.today(),false);check(o.cycle().equals(cycle),"request");if(o.state().equals("CANCELLED")&&o.reason().equals(reason.strip()))return;check(s.status().equals("AWAITING_GUARANTORS")&&Set.of("PENDING","EXPIRED").contains(o.state()),"personalGuaranteesClosed");offers.decide(a,o,"CANCELLED",reason.strip(),null,null,clock.now());event(a,id,"PERSONAL_GUARANTEE_CANCELLED",Map.of("reason",reason.strip()));}
    @Transactional public void startReview(AppUserPrincipal a,UUID cycle){staff(a,true);access.requireActor(a,"GROUP_LENDING_SUBMIT");var s=offers.source(a,cycle,true);loans.startGuaranteedGroupReview(a,s.loan());}
    @Transactional public void refuse(AppUserPrincipal a,UUID id,String hash,String reason){text(reason,500);var o=writable(a,id,hash,UserClaim.GUARANTOR_REQUESTS_REJECT);offers.decide(a,o,"REFUSED",reason.strip(),null,null,clock.now());event(a,id,"PERSONAL_GUARANTEE_REFUSED",Map.of("reason",reason.strip()));}
    /** Invalid OTP attempts commit while returning a stable recoverable error. */
    @Transactional public String accept(AppUserPrincipal a,UUID id,String hash,boolean accepted,String code){
        if(!accepted)throw new AccessDeniedException("Personal acceptance required");var m=client(a,UserClaim.GUARANTOR_REQUESTS_VIEW,UserClaim.GUARANTOR_REQUESTS_APPROVE);var l=locked(a,id,hash);var o=l.offer();if(o.state().equals("ACCEPTED"))return null;check(open(o,l.source()),"personalGuaranteesClosed");
        if(!offers.opened(a,id))return "group.error.documentOpen";if(m.getSignatureText()==null||m.getSignatureText().isBlank())return "group.error.signature";
        qualification.lockGuarantorCapacity(a.getSaccoId(),a.getStationId(),a.getMemberId());capacity(a,a.getMemberId(),o.capacity(),o.cap(),false);UUID challenge=null;var at=clock.now();
        if(otp.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId())){var all=offers.challenges(o);if(all.isEmpty())return "group.error.otpRequired";var q=all.getFirst();if(q.delivered()==null||q.consumed()!=null||!q.expires().isAfter(at)||q.attempts()>=5)return "group.error.otpExpired";offers.attempted(q.id());if(code==null||!code.matches("[0-9]{6}")||!passwords.matches(code,q.hash()))return "group.error.otpInvalid";challenge=q.id();offers.consumed(challenge,at);}
        offers.decide(a,o,"ACCEPTED","",m.getSignatureText(),challenge,at);event(a,id,"PERSONAL_GUARANTEE_ACCEPTED",Map.of("loan",o.loan(),"cap",o.cap()));return null;
    }
    @Transactional public Delivery prepareCode(AppUserPrincipal a,UUID id,String hash){var m=client(a,UserClaim.GUARANTOR_REQUESTS_VIEW,UserClaim.GUARANTOR_REQUESTS_APPROVE);var o=writable(a,id,hash,UserClaim.GUARANTOR_REQUESTS_APPROVE);check(offers.opened(a,id),"documentOpen");check(m.getSignatureText()!=null&&!m.getSignatureText().isBlank(),"signature");check(otp.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId()),"otpDisabled");var at=clock.now();var recent=offers.challenges(o);check(recent.isEmpty()||!recent.getFirst().issued().isAfter(at.minusSeconds(60))&&!(recent.size()==4&&recent.getLast().issued().isAfter(at.minusMinutes(10))),"otpWait");String code=Integer.toString(RANDOM.nextInt(900000)+100000);UUID idCode=UUID.randomUUID();offers.challenge(a,o,idCode,passwords.encode(code),at,at.plusMinutes(10));return new Delivery(idCode,id,o.loan(),a.getMemberId(),a.getSaccoId(),a.getStationId(),m.getEmail(),m.getPhone(),code,10);}
    @Transactional public void codeDelivered(AppUserPrincipal a,UUID offer,UUID id){client(a,UserClaim.GUARANTOR_REQUESTS_VIEW,UserClaim.GUARANTOR_REQUESTS_APPROVE);var o=offers.offer(a,offer,clock.today(),true);writable(a,offer,o.hash(),UserClaim.GUARANTOR_REQUESTS_APPROVE);var q=offers.challenges(o).stream().findFirst().filter(x->x.id().equals(id)&&x.expires().isAfter(clock.now())&&x.consumed()==null).orElseThrow(()->new IllegalArgumentException("group.error.otpExpired"));offers.delivered(q.id(),clock.now());}
    private static String hash(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void event(AppUserPrincipal a,UUID id,String action,Map<String,?> values){audit.logEvent("GROUP_LENDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"PERSONAL_GUARANTEE",id.toString(),a.getSaccoId(),a.getStationId(),new LinkedHashMap<>(values));}
    private static void text(String s,int max){check(s!=null&&!s.isBlank()&&s.length()<=max&&s.chars().noneMatch(Character::isISOControl),"form");}
    private static void page(int p){check(p>=0&&p<=10000,"query");}
    private static void check(boolean condition,String key){if(!condition)throw new IllegalArgumentException("group.error."+key);}
}
