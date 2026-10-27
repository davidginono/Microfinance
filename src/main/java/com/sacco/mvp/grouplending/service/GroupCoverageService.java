package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCoverageDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page;
import com.sacco.mvp.grouplending.repository.GroupCoverageRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

/** Operational recovery evidence only; owning receipt services remain the sole cash-posting path. */
@Service @RequiredArgsConstructor
public class GroupCoverageService {
    private final GroupCoverageRepository cases;
    private final GeneralLedgerService access;
    private final GroupConsentService clients;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final ReportExportLimiter exports;
    private final java.util.concurrent.Semaphore uploads=new java.util.concurrent.Semaphore(2);
    private void staff(AppUserPrincipal a,String action){access.requireActor(a,"GROUP_LENDING_VIEW");access.requireActor(a,"LOAN_RECORDING_VIEW");access.requireActor(a,"GROUP_COVERAGE_VIEW");if(action!=null)access.requireActor(a,"GROUP_COVERAGE_"+action);}
    private void own(AppUserPrincipal a,UserClaim... claims){if(a==null||a.isStaffSession())throw new AccessDeniedException("Named person session required");clients.authorize(a,UserClaim.GROUP_COVERAGE_OWN_VIEW);clients.authorize(a,claims);}
    private void view(AppUserPrincipal a,boolean own){if(own)own(a);else staff(a,null);}
    @Transactional(readOnly=true) public Optional<Policy> policy(AppUserPrincipal a,UUID cycle){staff(a,null);return cases.policy(a,cases.product(a,cycle));}
    @Transactional public UUID configure(AppUserPrincipal a,UUID cycle,PolicyForm f){
        staff(a,"CONFIGURE");check(f!=null&&f.getRequestKey()!=null&&f.isVerified(),"policy");range(f.getNoticeDays(),14,365);range(f.getCaseDays(),1,365);range(f.getPromiseDays(),1,365);text(f.getReference());
        var product=cases.product(a,cycle);String hash=payload(product,f);cases.requestLock(a,f.getRequestKey());cases.policyLock(a,product);var retry=cases.retry(a,"lending_group_coverage_policy",f.getRequestKey(),hash);if(retry.isPresent())return retry.get();var prior=cases.policy(a,product).orElse(null);check(Objects.equals(f.getExpectedPreviousId(),prior==null?null:prior.id()),"changed");UUID id=cases.recordPolicy(a,product,f,prior,hash,clock.now());audit(a,id,"COVERAGE_POLICY_RECORDED",Map.of("product",product,"reference",f.getReference().strip(),"enabled",f.isEnabled()));return id;
    }
    @Transactional(readOnly=true) public Page<Source> sources(AppUserPrincipal a,UUID cycle,int page){staff(a,null);page(page);cases.product(a,cycle);return page(cases.sources(a,cycle,clock.today(),page),page);}
    @Transactional(readOnly=true) public Page<Case> list(AppUserPrincipal a,UUID cycle,int page,boolean own){view(a,own);page(page);if(!own)cases.product(a,cycle);return page(cases.cases(a,cycle,clock.today(),page,own),page);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30) public Detail detail(AppUserPrincipal a,UUID id,int eventPage,int paymentPage,boolean own){view(a,own);page(eventPage);page(paymentPage);var c=cases.detail(a,id,clock.today(),own);var payments=cases.payments(c,paymentPage);if(own)payments=payments.stream().map(p->new Payment(p.id(),p.posting(),p.receipt(),p.reference(),p.date(),p.payer(),p.amount(),p.principal(),p.interest(),p.charges(),p.net(),p.reversedBy(),p.correctedBy(),"",p.linkedAt())).toList();return new Detail(c,page(cases.events(c,eventPage),eventPage),page(payments,paymentPage),!own);}
    @Transactional(readOnly=true) public byte[] notice(AppUserPrincipal a,UUID id,boolean own){view(a,own);if(own)own(a,UserClaim.GROUP_COVERAGE_OWN_EXPORT);else staff(a,"EXPORT");var c=cases.detail(a,id,clock.today(),own);return exports.run(()->cases.notice(c));}
    @Transactional(propagation=Propagation.NOT_SUPPORTED) public UUID call(AppUserPrincipal a,UUID cycle,CallForm f,MultipartFile file){
        staff(a,"CREATE");check(f!=null&&f.getRequestKey()!=null&&f.getSourceId()!=null&&f.getPolicyId()!=null&&Set.of("RECIPROCAL","PERSONAL","JOINT","VOLUNTARY").contains(Objects.toString(f.getSourceKind(),"")),"request");money(f.getAmount());check(f.isNoticesVerified()&&Set.of("SW","BOTH").contains(Objects.toString(f.getNoticeLanguage(),"")),"notice");text(f.getBorrowerNoticeEvidence());text(f.getPersonNoticeEvidence());date(f.getBorrowerNoticeDate());date(f.getPersonNoticeDate());check(file!=null&&!file.isEmpty()&&file.getSize()<=GroupOrganizationDocumentService.MAX_BYTES,"documentPdf");check(uploads.tryAcquire(),"documentBusy");
        try{byte[] bytes;try{bytes=file.getBytes();}catch(IOException e){throw new IllegalArgumentException("coverage.error.documentPdf",e);}check(bytes.length>=5&&bytes.length<=GroupOrganizationDocumentService.MAX_BYTES&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"),"documentPdf");int pages=GroupAgreementDocumentValidation.pages(bytes);
            String name=Objects.toString(file.getOriginalFilename(),"served-notices.pdf").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).strip();check(!name.isBlank()&&name.length()<=200&&name.chars().noneMatch(Character::isISOControl),"documentPdf");String filename=name,checksum=hash(bytes),payload=payload(cycle,f,filename,checksum);
            return new TransactionTemplate(transactions).execute(tx->{staff(a,"CREATE");cases.requestLock(a,f.getRequestKey());var retry=cases.retry(a,"lending_group_coverage_case",f.getRequestKey(),payload);if(retry.isPresent())return retry.get();var today=clock.today();var s=cases.source(a,cycle,f.getSourceId(),f.getSourceKind(),today,true);cases.policyLock(a,s.product());var p=cases.policy(a,s.product()).orElseThrow(()->new IllegalArgumentException("coverage.error.policy"));check(p.enabled()&&p.id().equals(f.getPolicyId()),"policy");check(s.known()&&s.arrears()!=null&&s.oldestDue()!=null&&Set.of("DISBURSED","PAR","DEFAULTED","WRITTEN_OFF").contains(s.status()),"ledger");check(f.getAmount().compareTo(s.arrears().subtract(cases.reserved(s,today)))<=0,"shortfall");check(f.getAmount().compareTo(s.cap().subtract(cases.received(s.loan(),s.id(),s.kind())))<=0,"cap");for(var served:List.of(f.getBorrowerNoticeDate(),f.getPersonNoticeDate()))check(!served.isBefore(s.oldestDue())&&!served.isAfter(today.minusDays(p.noticeDays())),"notice");var id=cases.record(a,s,p,f,payload,filename,checksum,pages,bytes,today,clock.now());audit(a,id,"COVERAGE_CASE_RECORDED",Map.of("loan",s.loan(),"client",s.client(),"kind",s.kind(),"amount",f.getAmount(),"policy",p.id(),"noticeSha256",checksum));return id;});
        }finally{uploads.release();}
    }
    @Transactional public UUID respond(AppUserPrincipal a,UUID id,ResponseForm f){own(a,UserClaim.GROUP_COVERAGE_OWN_RESPOND);check(f!=null&&f.isConfirmed()&&Set.of("ACKNOWLEDGED","PROMISED","DISPUTED").contains(Objects.toString(f.getKind(),"")),"response");return event(a,id,f,true);}
    @Transactional public UUID close(AppUserPrincipal a,UUID id,ResponseForm f){staff(a,"UPDATE");check(f!=null&&f.isConfirmed()&&Set.of("CANCELLED","CLOSED").contains(Objects.toString(f.getKind(),"")),"response");return event(a,id,f,false);}
    private UUID event(AppUserPrincipal a,UUID id,ResponseForm f,boolean own){
        check(f.getRequestKey()!=null&&f.getExpectedSequence()>=0,"request");if(f.getKind().equals("PROMISED")){money(f.getAmount());date(f.getPromiseOn());}else check(f.getAmount()==null&&f.getPromiseOn()==null,"response");if(!f.getKind().equals("ACKNOWLEDGED"))text(f.getReason());else check(f.getReason()!=null&&f.getReason().length()<=500&&f.getReason().chars().noneMatch(Character::isISOControl),"form");String hash=payload(id,f);var c=locked(a,id,own);var retry=cases.eventRetry(c,f.getRequestKey(),hash,a.getMemberId());if(retry.isPresent())return retry.get();check(c.sequence()==f.getExpectedSequence()&&!Set.of("CANCELLED","CLOSED").contains(c.state()),"changed");if(own)check(!clock.today().isAfter(c.expiresOn()),"expired");if(f.getKind().equals("PROMISED")){var p=cases.frozenPolicy(c);check(c.unpaid()!=null&&f.getAmount().compareTo(c.unpaid())<=0,"shortfall");check(!f.getPromiseOn().isBefore(clock.today())&&!f.getPromiseOn().isAfter(clock.today().plusDays(p.promiseDays()))&&!f.getPromiseOn().isAfter(c.expiresOn()),"promise");}var event=cases.event(a,c,f,hash,clock.now());audit(a,id,"COVERAGE_"+f.getKind(),Map.of("event",event,"sequence",c.sequence()+1));return event;
    }
    private Case locked(AppUserPrincipal a,UUID id,boolean own){var c=cases.detail(a,id,clock.today(),own);cases.lock(a,c);return cases.detail(a,id,clock.today(),own);}
    @Transactional public UUID link(AppUserPrincipal a,UUID id,LinkForm f){
        staff(a,"LINK");check(f!=null&&f.getRequestKey()!=null&&f.getPostingId()!=null&&f.isPayerVerified(),"payment");text(f.getPayerEvidence());String hash=payload(id,f);var c=locked(a,id,false);var retry=cases.paymentRetry(c,f.getRequestKey(),hash,a.getMemberId());if(retry.isPresent())return retry.get();var p=cases.paymentSource(a,c,f.getPostingId(),f.getContributionId());check(p.payerId()==null||p.payerId().equals(c.client()),"payer");text(p.payer());check(!p.date().isBefore(c.callDate())&&!p.recordedAt().isBefore(c.recordedAt()),"payment");check(!cases.linked(p),"linked");check(p.amount().compareTo(c.cap().subtract(cases.received(c.loan(),c.source(),c.sourceKind())))<=0,"cap");var link=cases.link(a,c,p,f,hash,clock.now());audit(a,id,"COVERAGE_RECEIPT_LINKED",Map.of("link",link,"posting",p.posting(),"amount",p.amount()));return link;
    }
    @Transactional public UUID correct(AppUserPrincipal a,UUID id,UUID payment,UUID key,String reason){staff(a,"REVERSE");check(payment!=null&&key!=null,"request");text(reason);var c=locked(a,id,false);var correction=cases.correct(a,c,payment,key,reason.strip(),clock.now());audit(a,id,"COVERAGE_LINK_CORRECTED",Map.of("link",payment,"correction",correction,"reason",reason.strip()));return correction;}
    private String payload(Object... values){return hash(json.writeValueAsString(Arrays.asList(values)).getBytes(StandardCharsets.UTF_8));}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void audit(AppUserPrincipal a,UUID id,String action,Map<String,?> values){audit.logEvent("GROUP_LENDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"GROUP_COVERAGE",id.toString(),a.getSaccoId(),a.getStationId(),new LinkedHashMap<>(values));}
    private static void text(String value){check(value!=null&&!value.isBlank()&&value.length()<=500&&value.chars().noneMatch(Character::isISOControl),"form");}
    private static void money(BigDecimal value){check(value!=null&&value.signum()>0&&value.scale()<=2&&value.precision()-value.scale()<=16,"amount");}
    private static void date(LocalDate value){check(value!=null&&value.getYear()>=1&&value.getYear()<=9999,"date");}
    private static void range(Integer value,int min,int max){check(value!=null&&value>=min&&value<=max,"policy");}
    private static void page(int page){check(page>=0&&page<=10000,"query");}
    private static <T> Page<T> page(List<T> rows,int page){return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    private static void check(boolean condition,String key){if(!condition)throw new IllegalArgumentException("coverage.error."+key);}
}
