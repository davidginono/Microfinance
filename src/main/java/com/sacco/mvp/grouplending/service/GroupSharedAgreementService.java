package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupSharedAgreementDtos.*;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.*;
import com.sacco.mvp.grouplending.dto.GroupConsentDtos.Delivery;
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
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupSharedAgreementService {
    private final GroupSharedAgreementRepository agreements;
    private final GroupCycleRepository cycles;
    private final GroupConsentService people;
    private final GeneralLedgerService access;
    private final SaccoRegistryService institutions;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final StationOtpSettingsService otp;
    private final PasswordEncoder passwords;
    private final org.springframework.core.env.Environment configuration;
    private final GroupSharedAgreementPdf pdf;
    private final java.util.concurrent.Semaphore pdfSlots=new java.util.concurrent.Semaphore(2);
    private static final SecureRandom RANDOM=new SecureRandom();
    private Member client(AppUserPrincipal a,boolean write){if(a==null||a.isStaffSession())throw new AccessDeniedException("Client session required");return write?people.authorize(a,UserClaim.MEMBER_LOANS_VIEW,UserClaim.MEMBER_LOANS_UPDATE):people.authorize(a,UserClaim.MEMBER_LOANS_VIEW);}
    public void staff(AppUserPrincipal a,String action){access.requireActor(a,"GROUP_LENDING_"+action);if("UPDATE".equals(action))access.requireActor(a,"LOAN_RECORDING_CREATE");}
    @Transactional(readOnly=true) public Optional<Document> document(AppUserPrincipal a,UUID cycle){staff(a,"VIEW");cycles.cycle(a,cycle,false);return agreements.document(a,cycle);}
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public UUID record(AppUserPrincipal a,UUID cycle,DocumentForm form,MultipartFile file){
        staff(a,"UPDATE");var source=cycles.cycle(a,cycle,false);check(source.model()==com.sacco.mvp.grouplending.dto.GroupCycleDtos.Model.SHARED,"request");
        GroupAgreementDocumentValidation.validate(form);check(form.getExpectedDocumentId()==null,"changed");check(file!=null&&!file.isEmpty()&&file.getSize()<=GroupOrganizationDocumentService.MAX_BYTES,"documentPdf");check(pdfSlots.tryAcquire(),"documentBusy");
        try{
            byte[] body;try{body=file.getBytes();}catch(IOException e){throw new IllegalArgumentException("group.error.documentPdf",e);}
            check(body.length>=5&&body.length<=GroupOrganizationDocumentService.MAX_BYTES&&new String(body,0,5,StandardCharsets.US_ASCII).equals("%PDF-"),"documentPdf");
            int pages=GroupAgreementDocumentValidation.pages(body);String name=Objects.toString(file.getOriginalFilename(),"agreement.pdf").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).strip();
            check(!name.isBlank()&&name.length()<=200&&name.chars().noneMatch(Character::isISOControl),"documentPdf");final String filename=name;
            String checksum=hash(body),payload=hash(json.writeValueAsBytes(List.of(cycle,form,filename,checksum)));
            return new TransactionTemplate(transactions).execute(tx->{staff(a,"UPDATE");var current=cycles.cycle(a,cycle,true);var retry=agreements.retry(a,cycle,form.getDocumentRequestKey(),payload);if(retry.isPresent())return retry.get();check("SUBMITTED".equals(current.state()),"payoutClosed");
                String institution=institutions.findActiveSacco(a.getSaccoId()).map(s->s.getSaccoName()).filter(s->s!=null&&!s.isBlank()).orElseThrow(()->new IllegalArgumentException("group.error.documentInstitution"));
                UUID id=agreements.record(a,cycle,form,institution,filename,checksum,payload,pages,body,clock.now());event(a,cycle,"SHARED_DOCUMENT_RECORDED",Map.of("document",id,"sha256",checksum));return id;
            });
        }finally{pdfSlots.release();}
    }
    @Transactional(readOnly=true) public Packet packet(AppUserPrincipal a,UUID cycle){var m=client(a,false);return load(a,cycle,m);}
    private Packet load(AppUserPrincipal a,UUID cycle,Member m){return new Packet(agreements.own(a,cycle),cycles.ownTerms(a,cycle),m.getSignatureText()!=null&&!m.getSignatureText().isBlank(),otp.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId()));}
    @Transactional(propagation=Propagation.NOT_SUPPORTED) public byte[] exportClient(AppUserPrincipal a,UUID cycle,Locale locale){
        var packet=packet(a,cycle);check(packet.row().document()!=null,"documentRequired");check(packet.row().destination().available(),"sharedDestination");check(pdfSlots.tryAcquire(),"documentBusy");
        try{
            var data=new Export(packet,agreements.parties(a,cycle),agreements.schedule(a,cycle),agreements.body(a,cycle,packet.row().document().id()));var bytes=pdf.render(data,locale);
            new TransactionTemplate(transactions).executeWithoutResult(tx->{var fresh=packet(a,cycle);agreements.lock(a,cycle);check(Objects.equals(packet.row().hash(),fresh.row().hash()),"changed");agreements.opened(a,cycle,packet.row().hash(),clock.now());event(a,cycle,"SHARED_AGREEMENT_OPENED",Map.of("packet",packet.row().hash(),"document",packet.row().document().id()));});return bytes;
        }finally{pdfSlots.release();}
    }
    @Transactional public byte[] original(AppUserPrincipal a,UUID cycle,UUID document){staff(a,"VIEW");access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");cycles.cycle(a,cycle,false);var bytes=agreements.body(a,cycle,document);event(a,cycle,"SHARED_DOCUMENT_EXPORTED",Map.of("document",document));return bytes;}
    @Transactional public String confirm(AppUserPrincipal a,UUID cycle,String hash,boolean accepted,String code){return confirm(a,cycle,hash,accepted,code,null);}
    @Transactional public String confirm(AppUserPrincipal a,UUID cycle,String hash,boolean accepted,String code,String preferredLanguage){
        var m=client(a,true);if(!accepted)throw new AccessDeniedException("Shared agreement and payout acceptance required");agreements.lock(a,cycle);var packet=load(a,cycle,m);var p=packet.row();check(Objects.equals(p.hash(),hash),"changed");if(p.confirmation()!=null)return null;
        if(!p.ready())return "group.error.payoutClosed";if(!p.opened())return "group.error.sharedReadDocument";if(!packet.signatureReady())return "group.error.signature";
        String language;try{language=GroupDisclosure.language(packet.terms().groupDisclosure(),preferredLanguage,p.document().language());}catch(IllegalArgumentException e){return e.getMessage();}
        var at=clock.now();UUID challenge=null;if(packet.otpRequired()){
            var recent=agreements.challenges(a,cycle);if(recent.isEmpty())return "group.error.otpRequired";var q=recent.getFirst();
            if(!Objects.equals(q.packetHash(),hash))return "group.error.changed";
            if(q.delivered()==null||q.consumed()!=null||!q.expires().isAfter(at)||q.attempts()>=5)return "group.error.otpExpired";
            agreements.attempted(q.id());if(code==null||!code.matches("[0-9]{6}")||!passwords.matches(code,q.hash()))return "group.error.otpInvalid";challenge=q.id();agreements.consumed(challenge,at);
        }
        agreements.confirm(a,p,m.getSignatureText(),challenge,at,language);event(a,cycle,"SHARED_PAYOUT_AUTHORIZED",Map.of("packet",p.hash(),"net",p.obligation().allocation().net(),"document",p.document().id()));return null;
    }
    @Transactional public Delivery prepareCode(AppUserPrincipal a,UUID cycle,String hash){
        var m=client(a,true);agreements.lock(a,cycle);var packet=load(a,cycle,m);var p=packet.row();
        check(Objects.equals(p.hash(),hash),"changed");check(p.ready()&&p.confirmation()==null,"payoutClosed");check(p.opened(),"sharedReadDocument");check(packet.signatureReady(),"signature");check(packet.otpRequired(),"otpDisabled");
        var at=clock.now();var recent=agreements.challenges(a,cycle);check(recent.isEmpty()||(!recent.getFirst().issued().isAfter(at.minusSeconds(60))&&(recent.size()<4||!recent.getLast().issued().isAfter(at.minusMinutes(10)))),"otpWait");
        int ttl=Math.max(1,Math.min(30,configuration.getProperty("app.auth.otp.ttl-minutes",Integer.class,10)));String code=Integer.toString(RANDOM.nextInt(900000)+100000);UUID id=UUID.randomUUID();
        agreements.challenge(a,cycle,hash,id,passwords.encode(code),at,at.plusMinutes(ttl));return new Delivery(id,cycle,a.getMemberId(),p.loan(),m.getEmail(),m.getPhone(),code,a.getSaccoId(),a.getStationId(),ttl);
    }
    @Transactional public void codeDelivered(AppUserPrincipal a,UUID cycle,UUID id){var m=client(a,true);agreements.lock(a,cycle);var p=load(a,cycle,m).row();check(p.ready()&&p.confirmation()==null,"payoutClosed");var q=agreements.challenges(a,cycle).stream().findFirst().filter(x->x.id().equals(id)&&Objects.equals(x.packetHash(),p.hash())&&x.expires().isAfter(clock.now())&&x.consumed()==null).orElseThrow(()->new IllegalArgumentException("group.error.otpExpired"));agreements.delivered(id,clock.now());}
    private void event(AppUserPrincipal a,UUID cycle,String action,Map<String,?> details){audit.logEvent("GROUP_LENDING",cycle,action,a.getMemberId(),AuditEventStatus.SUCCESS,"Shared agreement and payout evidence","GROUP_CYCLE",cycle.toString(),a.getSaccoId(),a.getStationId(),details);}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void check(boolean value,String key){if(!value)throw new IllegalArgumentException("group.error."+key);}
}
