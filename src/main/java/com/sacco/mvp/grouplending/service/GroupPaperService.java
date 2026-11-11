package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.grouplending.dto.GroupPaperDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page;
import com.sacco.mvp.grouplending.repository.GroupPaperRepository;
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
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupPaperService {
    private final GroupPaperRepository papers;
    private final GeneralLedgerService access;
    private final PlatformTransactionManager transactions;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ObjectMapper json;
    private final java.util.concurrent.Semaphore uploads=new java.util.concurrent.Semaphore(2);
    public void authorize(AppUserPrincipal a,boolean write){access.requireActor(a,"GROUP_LENDING_VIEW");access.requireActor(a,"LOAN_RECORDING_VIEW");if(write){access.requireActor(a,"GROUP_LENDING_UPDATE");access.requireActor(a,"LOAN_RECORDING_CREATE");}}
    @Transactional(readOnly=true) public List<PaperTarget> targets(AppUserPrincipal a,UUID cycle){authorize(a,false);papers.cycle(a,cycle,false);return papers.targets(a,cycle);}
    @Transactional(readOnly=true) public Page<Paper> history(AppUserPrincipal a,UUID cycle,int page){authorize(a,false);papers.cycle(a,cycle,false);check(page>=0&&page<=10000,"query");var rows=papers.papers(a,cycle,page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional public PaperProposal preview(AppUserPrincipal a,UUID cycle,PaperForm f){authorize(a,true);papers.cycle(a,cycle,false);validate(f);return proposal(a,cycle,f);}
    private PaperProposal proposal(AppUserPrincipal a,UUID cycle,PaperForm f){return papers.proposal(a,cycle,target(a,cycle,f),f,destination(f),signed(f));}
    private PaperTarget target(AppUserPrincipal a,UUID cycle,PaperForm f){return papers.targets(a,cycle).stream().filter(t->t.key().equals(f.getTarget())).findFirst().orElseThrow(()->new IllegalArgumentException("group.error.request"));}
    private OffsetDateTime signed(PaperForm f){return f.getSignedAt().atZone(ZoneId.of("Africa/Dar_es_Salaam")).toOffsetDateTime();}
    private String destination(PaperForm f){return !f.getTarget().startsWith("SHARED_PAYOUT/")?"{}":json.writeValueAsString(Map.of("available",true,"destinationType",f.getDestinationType(),"destinationTypeLabel",f.getDestinationType(),"provider",f.getProvider().strip(),"accountHolderName",f.getAccountHolderName().strip(),"accountIdentifier",f.getAccountIdentifier().strip(),"sourceLabel","SIGNED_PAPER"));}
    private void validate(PaperForm f){check(f!=null&&f.getRequestKey()!=null&&f.getSignedAt()!=null,"request");text(f.getTarget(),200);check(!signed(f).isAfter(clock.now())&&f.getSignedAt().getYear()>=1901,"request");text(f.getReference(),500);check(Set.of("EN","SW").contains(Objects.toString(f.getLanguage(),"")),"disclosureLanguage");
        if(f.getTarget().startsWith("SHARED_PAYOUT/")){check(Set.of("BANK_ACCOUNT","MOBILE_MONEY","OTHER").contains(f.getDestinationType()),"sharedDestination");text(f.getProvider(),120);text(f.getAccountHolderName(),160);text(f.getAccountIdentifier(),100);}}
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public UUID record(AppUserPrincipal a,UUID cycle,PaperForm f,MultipartFile file){
        authorize(a,true);validate(f);check(f.isChecked(),"paperChecked");check(file!=null&&!file.isEmpty()&&file.getSize()<=10*1024*1024,"documentPdf");check(uploads.tryAcquire(),"documentBusy");
        try{byte[] bytes;try(var in=file.getInputStream()){bytes=in.readNBytes(10*1024*1024+1);}check(bytes.length>=5&&bytes.length<=10*1024*1024&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"),"documentPdf");GroupAgreementDocumentValidation.pages(bytes);
            String name=Objects.toString(file.getOriginalFilename(),"signed-paper.pdf").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).strip();text(name,200);final String filename=name;
            String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            var tx=new TransactionTemplate(transactions);tx.setTimeout(30);return tx.execute(status->{authorize(a,true);papers.cycle(a,cycle,true);
                var retry=papers.retry(a,cycle,f,destination(f),filename,checksum,signed(f));if(retry.isPresent())return retry.get();
                var t=target(a,cycle,f);var proposal=proposal(a,cycle,f);check(proposal.hash().equals(f.getExpectedHash()),"changed");
                papers.record(a,cycle,t,f,proposal,signed(f),clock.now(),filename,checksum,bytes);audit.logEvent("GROUP_LENDING",cycle,"SIGNED_PAPER_RECORDED",a.getMemberId(),AuditEventStatus.SUCCESS,"Signed paper recorded by staff","GROUP_CYCLE",cycle.toString(),a.getSaccoId(),a.getStationId(),Map.of("paper",f.getRequestKey(),"kind",t.kind(),"client",t.client(),"sha256",checksum,"reference",f.getReference().strip()));return f.getRequestKey();});
        }catch(IOException e){throw new IllegalArgumentException("group.error.documentPdf",e);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}finally{uploads.release();}
    }
    @Transactional public byte[] original(AppUserPrincipal a,UUID cycle,UUID id){authorize(a,false);access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");var bytes=papers.body(a,cycle,id);audit.logEvent("GROUP_LENDING",cycle,"SIGNED_PAPER_EXPORTED",a.getMemberId(),AuditEventStatus.SUCCESS,"Signed paper exported","GROUP_CYCLE",cycle.toString(),a.getSaccoId(),a.getStationId(),Map.of("paper",id));return bytes;}
    @Transactional public Optional<byte[]> guaranteePaper(AppUserPrincipal a,UUID cycle,UUID offer){authorize(a,false);access.requireActor(a,"LOAN_DOCUMENTS_VIEW");access.requireActor(a,"LOAN_DOCUMENTS_EXPORT");return papers.guaranteePaper(a,cycle,offer).map(id->original(a,cycle,id));}
    private static void text(String s,int max){check(s!=null&&!s.isBlank()&&s.length()<=max&&s.codePoints().noneMatch(Character::isISOControl),"request");}
    private static void check(boolean valid,String key){if(!valid)throw new IllegalArgumentException("group.error."+key);}
}
