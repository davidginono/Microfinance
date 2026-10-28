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
    private void own(AppUserPrincipal a,UserClaim... claims){if(a==null||a.isStaffSession())throw new AccessDeniedException("Named person session required");var required=EnumSet.of(UserClaim.GROUP_COVERAGE_OWN_VIEW);Collections.addAll(required,claims);clients.authorize(a,required.toArray(UserClaim[]::new));}
    private void view(AppUserPrincipal a,boolean own){if(own)own(a);else staff(a,null);}
    public Set<String> actions(AppUserPrincipal a,boolean own){
        view(a,own);var result=new HashSet<String>();
        for(var action:own?List.of("RESPOND","EXPORT"):List.of("CREATE","UPDATE","CONFIGURE","LINK","REVERSE","EXPORT"))try{
            if(own)own(a,UserClaim.valueOf("GROUP_COVERAGE_OWN_"+action));else staff(a,action);
            result.add(action);
        }catch(AccessDeniedException ignored){}
        return Set.copyOf(result);
    }
    @Transactional(readOnly=true) public Source sourceForCall(AppUserPrincipal a,UUID cycle,UUID source,String kind){staff(a,"CREATE");return cases.source(a,cycle,source,kind,clock.today(),false);}
    @Transactional(readOnly=true) public Page<PaymentSource> paymentChoices(AppUserPrincipal a,UUID id,int page){
        staff(a,"LINK");page(page);var c=cases.detail(a,id,clock.today(),false);return page(cases.paymentChoices(a,c,page),page);
    }
    @Transactional(readOnly=true) public PaymentSource selectedPayment(AppUserPrincipal a,UUID id,UUID posting,UUID contribution){
        staff(a,"LINK");var c=cases.detail(a,id,clock.today(),false);var p=cases.paymentSource(a,c,posting,contribution).orElseThrow(()->new IllegalArgumentException("coverage.error.payment"));
        check(p.payerId()==null||p.payerId().equals(c.client()),"payer");
        check(!p.date().isBefore(c.callDate())&&!p.recordedAt().isBefore(c.recordedAt())&&!cases.linked(p),"payment");return p;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30) public Detail document(AppUserPrincipal a,UUID id,boolean own){
        view(a,own);if(own)own(a,UserClaim.GROUP_COVERAGE_OWN_EXPORT);else staff(a,"EXPORT");
        var c=cases.detail(a,id,clock.today(),own);var events=cases.events(c,0,501);var payments=cases.payments(c,0,501);
        check(events.size()<=500&&payments.size()<=500,"documentRange");
        if(own)payments=payments.stream().map(p->new Payment(p.id(),p.posting(),p.receipt(),p.reference(),p.date(),p.payer(),p.amount(),p.principal(),p.interest(),p.charges(),p.net(),p.reversedBy(),p.correctedBy(),"",p.linkedAt(),null,"",p.correctedAt())).toList();
        return new Detail(c,new Page<>(events,0,false),new Page<>(payments,0,false),!own,cases.frozenPolicy(c),cases.noticeFacts(c,own),clock.today());
    }
    @Transactional(readOnly=true) public Trace trace(AppUserPrincipal a,UUID cycle,UUID client,LocalDate from,LocalDate through,int page,boolean own,boolean export){
        try{view(a,own);if(export){if(own)own(a,UserClaim.GROUP_COVERAGE_OWN_EXPORT);else staff(a,"EXPORT");}}catch(AccessDeniedException denied){return null;}
        page(page);if(cycle==null||through==null||through.isAfter(clock.today())||through.getYear()<1||from!=null&&(from.getYear()<1||from.isAfter(through)))throw new IllegalArgumentException("group.error.query");
        var rows=cases.trace(a,cycle,own?a.getMemberId():client,clock.today(),from,through,export?0:page*25,export?501:26);
        check(!export||rows.size()<=500,"documentRange");return new Trace(export?rows:rows.stream().limit(25).toList(),page,!export&&rows.size()>25,own);
    }
    @Transactional(readOnly=true) public void authorize(AppUserPrincipal a,String action){staff(a,action);}
    @Transactional(readOnly=true) public Optional<Policy> policy(AppUserPrincipal a,UUID cycle){staff(a,null);return cases.policy(a,cases.product(a,cycle));}
    @Transactional public UUID configure(AppUserPrincipal a,UUID cycle,PolicyForm f){
        staff(a,"CONFIGURE");check(f!=null&&f.getRequestKey()!=null&&f.isVerified(),"policy");range(f.getNoticeDays(),14,365);range(f.getCaseDays(),1,365);range(f.getPromiseDays(),1,365);text(f.getReference());
        var product=cases.product(a,cycle);String hash=payload(product,f);cases.requestLock(a,f.getRequestKey());cases.policyLock(a,product);var retry=cases.retry(a,"lending_group_coverage_policy",f.getRequestKey(),hash);if(retry.isPresent())return retry.get();var prior=cases.policy(a,product).orElse(null);check(Objects.equals(f.getExpectedPreviousId(),prior==null?null:prior.id()),"changed");UUID id=cases.recordPolicy(a,product,f,prior,hash,clock.now());audit(a,id,"COVERAGE_POLICY_RECORDED",Map.of("product",product,"reference",f.getReference().strip(),"enabled",f.isEnabled()));return id;
    }
    @Transactional(readOnly=true) public Page<Source> sources(AppUserPrincipal a,UUID cycle,int page){staff(a,null);page(page);cases.product(a,cycle);return page(cases.sources(a,cycle,clock.today(),page),page);}
    @Transactional(readOnly=true) public Context context(AppUserPrincipal a,UUID cycle){staff(a,null);return cases.context(a,cycle);}
    @Transactional(readOnly=true) public CallPreparation prepare(AppUserPrincipal a,UUID cycle,String selected){staff(a,"CREATE");var context=cases.context(a,cycle);Source source=null;if(selected!=null&&!selected.isBlank()){var key=selected.split(":",-1);check(key.length==2&&Set.of("RECIPROCAL","PERSONAL","JOINT","VOLUNTARY").contains(key[0]),"query");source=cases.source(a,cycle,uuid(key[1]),key[0],clock.today(),false);}BigDecimal max=source==null||source.arrears()==null?null:source.arrears().subtract(cases.reserved(source,clock.today())).min(source.cap().subtract(cases.received(source.loan(),source.id(),source.kind()))).max(BigDecimal.ZERO);return new CallPreparation(context,source,cases.policy(a,cases.product(a,cycle)).orElse(null),max);}
    @Transactional(readOnly=true) public Page<Source> sourceChoices(AppUserPrincipal a,UUID cycle,String search,int page){staff(a,"CREATE");page(page);search(search);cases.product(a,cycle);return page(cases.sourceChoices(a,cycle,clock.today(),search.strip(),page),page);}
    @Transactional(readOnly=true) public Page<PaymentSource> paymentChoices(AppUserPrincipal a,UUID id,String search,int page){staff(a,"LINK");page(page);search(search);var c=cases.detail(a,id,clock.today(),false);return page(cases.paymentChoices(a,c,search.strip(),page),page);}
    @Transactional(readOnly=true) public PaymentSource paymentSource(AppUserPrincipal a,UUID id,UUID posting,UUID contribution){staff(a,"LINK");var c=cases.detail(a,id,clock.today(),false);return cases.paymentSource(a,c,posting,contribution).orElseThrow(()->new IllegalArgumentException("coverage.error.payment"));}
    @Transactional(readOnly=true) public Payment correctionSource(AppUserPrincipal a,UUID id,UUID payment){staff(a,"REVERSE");return cases.payment(cases.detail(a,id,clock.today(),false),payment);}
    @Transactional(readOnly=true) public CaseContext caseContext(AppUserPrincipal a,UUID id,boolean own,String action){if(own){if(action!=null)own(a,UserClaim.GROUP_COVERAGE_OWN_RESPOND);else own(a);}else staff(a,action);var c=cases.detail(a,id,clock.today(),own);return new CaseContext(c,cases.frozenPolicy(c),cases.noticeFacts(c,own),clock.today());}
    @Transactional(readOnly=true) public Page<Case> list(AppUserPrincipal a,UUID cycle,int page,boolean own){return list(a,cycle,"newest",page,own);}
    @Transactional(readOnly=true) public Page<Case> list(AppUserPrincipal a,UUID cycle,String sort,int page,boolean own){view(a,own);page(page);sort(sort,GroupCoverageRepository.CASE_SORT);if(!own)cases.product(a,cycle);return page(cases.cases(a,cycle,clock.today(),sort,page,own),page);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30) public Detail detail(AppUserPrincipal a,UUID id,int eventPage,int paymentPage,boolean own){return detail(a,id,eventPage,paymentPage,"newest","newest",own);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=30) public Detail detail(AppUserPrincipal a,UUID id,int eventPage,int paymentPage,String eventSort,String paymentSort,boolean own){view(a,own);page(eventPage);page(paymentPage);sort(eventSort,GroupCoverageRepository.EVENT_SORT);sort(paymentSort,GroupCoverageRepository.PAYMENT_SORT);var c=cases.detail(a,id,clock.today(),own);var payments=cases.payments(c,paymentSort,paymentPage);if(own)payments=payments.stream().map(p->new Payment(p.id(),p.posting(),p.receipt(),p.reference(),p.date(),p.payer(),p.amount(),p.principal(),p.interest(),p.charges(),p.net(),p.reversedBy(),p.correctedBy(),"",p.linkedAt(),null,"",p.correctedAt())).toList();return new Detail(c,page(cases.events(c,eventSort,eventPage),eventPage),page(payments,paymentPage),!own,cases.frozenPolicy(c),cases.noticeFacts(c,own),clock.today());}
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
        staff(a,"LINK");check(f!=null&&f.getRequestKey()!=null&&f.getPostingId()!=null&&f.isPayerVerified(),"payment");text(f.getPayerEvidence());String hash=payload(id,f);var c=locked(a,id,false);var retry=cases.paymentRetry(c,f.getRequestKey(),hash,a.getMemberId());if(retry.isPresent())return retry.get();var p=cases.paymentSource(a,c,f.getPostingId(),f.getContributionId()).orElseThrow(()->new IllegalArgumentException("coverage.error.payment"));check(p.payerId()==null||p.payerId().equals(c.client()),"payer");text(p.payer());check(!p.date().isBefore(c.callDate())&&!p.recordedAt().isBefore(c.recordedAt()),"payment");check(!cases.linked(p),"linked");check(p.amount().compareTo(c.cap().subtract(cases.received(c.loan(),c.source(),c.sourceKind())))<=0,"cap");var link=cases.link(a,c,p,f,hash,clock.now());audit(a,id,"COVERAGE_RECEIPT_LINKED",Map.of("link",link,"posting",p.posting(),"amount",p.amount()));return link;
    }
    @Transactional public UUID correct(AppUserPrincipal a,UUID id,UUID payment,UUID key,String reason){staff(a,"REVERSE");check(payment!=null&&key!=null,"request");text(reason);var c=locked(a,id,false);var context=cases.correction(c,payment).orElseThrow(()->new AccessDeniedException("Coverage link unavailable"));
        if(context.correction()!=null){if(!key.equals(context.requestKey())||!reason.strip().equals(context.reason())||!a.getMemberId().equals(context.correctionActor()))throw new IllegalArgumentException("group.error.retry");return context.correction();}
        check(!a.getMemberId().equals(context.actor()),"independent");var correction=cases.recordCorrection(a,payment,key,reason.strip(),clock.now());audit(a,id,"COVERAGE_LINK_CORRECTED",Map.of("link",payment,"correction",correction,"reason",reason.strip()));return correction;}
    private String payload(Object... values){return hash(json.writeValueAsString(Arrays.asList(values)).getBytes(StandardCharsets.UTF_8));}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void audit(AppUserPrincipal a,UUID id,String action,Map<String,?> values){audit.logEvent("GROUP_LENDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"GROUP_COVERAGE",id.toString(),a.getSaccoId(),a.getStationId(),new LinkedHashMap<>(values));}
    private static void text(String value){check(value!=null&&!value.isBlank()&&value.length()<=500&&value.chars().noneMatch(Character::isISOControl),"form");}
    private static void money(BigDecimal value){check(value!=null&&value.signum()>0&&value.scale()<=2&&value.precision()-value.scale()<=16,"amount");}
    private static void date(LocalDate value){check(value!=null&&value.getYear()>=1&&value.getYear()<=9999,"date");}
    private static void range(Integer value,int min,int max){check(value!=null&&value>=min&&value<=max,"policy");}
    private static UUID uuid(String text){try{return UUID.fromString(text);}catch(RuntimeException e){throw new IllegalArgumentException("coverage.error.query");}}
    private static void search(String value){check(value!=null&&value.length()<=100&&value.chars().noneMatch(Character::isISOControl),"query");}
    private static void sort(String value,Map<String,String> columns){check(com.sacco.mvp.accounting.dto.RegisterSort.valid(value,columns.keySet()),"query");try{com.sacco.mvp.accounting.dto.RegisterSort.order(value,columns,"id");}catch(IllegalArgumentException e){throw new IllegalArgumentException("coverage.error.query");}}
    private static void page(int page){check(page>=0&&page<=10000,"query");}
    private static <T> Page<T> page(List<T> rows,int page){return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    private static void check(boolean condition,String key){if(!condition)throw new IllegalArgumentException("coverage.error."+key);}
}
