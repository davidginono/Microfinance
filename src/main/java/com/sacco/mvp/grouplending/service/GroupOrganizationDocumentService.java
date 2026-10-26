package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.CycleForm;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.*;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
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
public class GroupOrganizationDocumentService {
    public static final int MAX_BYTES=5*1024*1024;
    // Bound concurrent in-memory PDF parsing per application instance; reject instead of queueing uploads.
    private final java.util.concurrent.Semaphore uploadSlots=new java.util.concurrent.Semaphore(2);
    private final GroupOrganizationDocumentRepository documents;
    private final GroupCycleRepository cycles;
    private final GroupOrganizationService organizations;
    private final GeneralLedgerService access;
    private final SaccoRegistryService institutions;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;

    /** File reads and PDF parsing finish before the short draft/document transaction starts. */
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public UUID record(AppUserPrincipal actor,UUID cycle,CycleForm draft,DocumentForm form,MultipartFile file){
        authorize(actor);cycles.cycle(actor,cycle,false);validate(form);check(file!=null && !file.isEmpty() && file.getSize()<=MAX_BYTES,"documentPdf");
        check(uploadSlots.tryAcquire(),"documentBusy");try{
        byte[] bytes;try{bytes=file.getBytes();}catch(IOException e){throw new IllegalArgumentException("group.error.documentPdf",e);}
        check(bytes.length>=5 && bytes.length<=MAX_BYTES&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"),"documentPdf");int pages=pages(bytes);
        String name=Objects.toString(file.getOriginalFilename(),"agreement.pdf").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).strip();
        check(!name.isBlank() && name.length()<=200 && name.chars().noneMatch(Character::isISOControl),"documentPdf");
        final String filename=name;String checksum=hash(bytes),payload=hash(json.writeValueAsString(List.of(cycle,draft,form,filename,checksum)).getBytes(StandardCharsets.UTF_8));
        return new TransactionTemplate(transactions).execute(tx->{
            authorize(actor);var c=cycles.cycle(actor,cycle,true);var retry=documents.retry(actor,cycle,form.getDocumentRequestKey(),payload);if(retry.isPresent())return retry.get();
            var prior=documents.latest(actor,cycle).orElse(null);check(Objects.equals(form.getExpectedDocumentId(),prior==null?null:prior.id()),"changed");
            String institution=institutions.findActiveSacco(actor.getSaccoId()).map(s->s.getSaccoName()).filter(s->s!=null&&!s.isBlank()).orElseThrow(()->new IllegalArgumentException("group.error.documentInstitution"));
            organizations.save(actor,cycle,draft);
            var id=documents.record(actor,cycle,form,prior,institution,filename,payload,checksum,pages,bytes,clock.now());
            audit.logEvent("GROUP_LENDING",cycle,"ORGANIZATION_DOCUMENT_RECORDED",actor.getMemberId(),AuditEventStatus.SUCCESS,"Institution agreement document recorded","GROUP_CYCLE",cycle.toString(),actor.getSaccoId(),actor.getStationId(),Map.of("document",id,"sha256",checksum,"pages",pages));return id;
        });
        }finally{uploadSlots.release();}
    }
    private void authorize(AppUserPrincipal a){organizations.authorize(a,"UPDATE");access.requireActor(a,"LOAN_RECORDING_CREATE");}
    @Transactional public byte[] original(AppUserPrincipal a,UUID cycle,UUID document){organizations.authorize(a,"VIEW");access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");cycles.cycle(a,cycle,false);var bytes=documents.body(a,cycle,document);audit.logEvent("GROUP_LENDING",cycle,"ORGANIZATION_TERMS_EXPORTED",a.getMemberId(),AuditEventStatus.SUCCESS,"Retained institution agreement PDF exported","GROUP_CYCLE",cycle.toString(),a.getSaccoId(),a.getStationId(),Map.of("document",document));return bytes;}
    private static void validate(DocumentForm f){
        check(f!=null&&f.getDocumentRequestKey()!=null&&f.isDocumentChecked(),"documentChecked");
        check(Set.of("EN","SW","BOTH").contains(Objects.toString(f.getDocumentLanguage(),"")),"documentFields");
        f.setDocumentReference(text(f.getDocumentReference(),500,true));f.setInstitutionAddress(text(f.getInstitutionAddress(),500,true));
        f.setContactPhone(text(f.getContactPhone(),80,false));f.setComplaintPhone(text(f.getComplaintPhone(),80,false));
        f.setContactEmail(email(f.getContactEmail()));f.setComplaintEmail(email(f.getComplaintEmail()));f.setComplaintAddress(text(f.getComplaintAddress(),500,false));
        check(!f.getContactPhone().isEmpty()||!f.getContactEmail().isEmpty(),"documentContacts");check(!f.getComplaintPhone().isEmpty()||!f.getComplaintEmail().isEmpty(),"documentContacts");
    }
    private static String email(String s){s=text(s,160,false);check(s.isEmpty()||s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"documentContacts");return s;}
    private static String text(String s,int max,boolean required){check(s!=null&&s.length()<=max&&s.chars().noneMatch(Character::isISOControl)&&(!required||!s.isBlank()),"documentFields");return s.strip();}
    private static int pages(byte[] bytes){
        try(var pdf=Loader.loadPDF(bytes)){
            check(!pdf.isEncrypted()&&pdf.getNumberOfPages()>=1&&pdf.getNumberOfPages()<=50,"documentPdf");
            // Retain exact approved bytes. Reject active forms/actions instead of silently rewriting them.
            var pending=new ArrayDeque<COSBase>();pending.add(pdf.getDocumentCatalog().getCOSObject());var seen=Collections.newSetFromMap(new IdentityHashMap<COSBase,Boolean>());
            Set<String> forbidden=Set.of("JS","AA","OpenAction","EmbeddedFiles","AcroForm","XFA","RichMediaContent");int count=0;
            while(!pending.isEmpty()){
                var node=pending.removeLast();if(!seen.add(node))continue;check(++count<=100000,"documentPdf");
                if(node instanceof COSObject indirect){if(indirect.getObject()!=null)pending.add(indirect.getObject());}
                else if(node instanceof COSArray array){for(var child:array)if(child!=null)pending.add(child);}
                else if(node instanceof COSDictionary dictionary){
                    for(var entry:dictionary.entrySet()){check(!forbidden.contains(entry.getKey().getName()),"documentPdf");if(entry.getValue()!=null)pending.add(entry.getValue());}
                    check(!Set.of("JavaScript","Launch","GoToR","SubmitForm","ImportData","Rendition","Sound","Movie").contains(dictionary.getNameAsString(COSName.S,"")),"documentPdf");
                }
            }
            return pdf.getNumberOfPages();
        }catch(IOException|IllegalStateException e){throw new IllegalArgumentException("group.error.documentPdf",e);}
    }
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void check(boolean b,String key){if(!b)throw new IllegalArgumentException("group.error."+key);}
}
