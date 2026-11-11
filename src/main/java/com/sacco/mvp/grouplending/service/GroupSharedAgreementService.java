package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupSharedAgreementDtos.*;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.*;
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
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupSharedAgreementService {
    private final GroupSharedAgreementRepository agreements;
    private final GroupCycleRepository cycles;
    private final GeneralLedgerService access;
    private final SaccoRegistryService institutions;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final PlatformTransactionManager transactions;
    private final java.util.concurrent.Semaphore pdfSlots=new java.util.concurrent.Semaphore(2);

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

    @Transactional public byte[] original(AppUserPrincipal a,UUID cycle,UUID document){staff(a,"VIEW");access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");cycles.cycle(a,cycle,false);var bytes=agreements.body(a,cycle,document);event(a,cycle,"SHARED_DOCUMENT_EXPORTED",Map.of("document",document));return bytes;}

    private void event(AppUserPrincipal a,UUID cycle,String action,Map<String,?> details){audit.logEvent("GROUP_LENDING",cycle,action,a.getMemberId(),AuditEventStatus.SUCCESS,"Shared agreement and payout evidence","GROUP_CYCLE",cycle.toString(),a.getSaccoId(),a.getStationId(),details);}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void check(boolean value,String key){if(!value)throw new IllegalArgumentException("group.error."+key);}
}
