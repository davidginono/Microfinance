package com.sacco.mvp.accounting.approval.service;

import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Document;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Approval;
import com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.domain.AuditEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.sacco.mvp.accounting.approval.service.VoucherApprovalGate.require;

@Service @RequiredArgsConstructor
public class VoucherDocumentService {
    private final VoucherWorkflowRepository repository;
    private final VoucherWorkflowService workflows;
    private final ApplicationClock clock;
    private final AuditService audit;
    @Transactional(readOnly=true)
    public List<Document> list(AppUserPrincipal a,UUID id) {workflows.authorize(a,"VOUCHER_DOCUMENT_VIEW");workflows.details(a,id);return repository.documents(id);}
    @Transactional(readOnly=true)
    public Document get(AppUserPrincipal a,UUID workflow,UUID id) {
        workflows.authorize(a,"VOUCHER_DOCUMENT_VIEW");workflows.details(a,workflow);
        return repository.document(workflow,id).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Supporting document unavailable"));
    }
    @Transactional
    public void add(AppUserPrincipal a,UUID workflow,String filename,String type,byte[] bytes) {
        workflows.authorize(a,"VOUCHER_DOCUMENT_CREATE");var d=workflows.details(a,workflow);require(d.actions().contains("EDIT"),"transition");
        var w=repository.find(a,workflow,true).orElseThrow();require(!Set.of("POSTED","POSTING").contains(w.posting()) && w.approval()!=Approval.INITIATED,"transition");
        require(type!=null && bytes!=null && bytes.length>0 && bytes.length<=5242880 && Set.of("application/pdf","image/png","image/jpeg").contains(type),"document");
        boolean signature=type.equals("application/pdf")?bytes.length>5 && new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"):
            type.equals("image/png")?bytes.length>8 && bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71:bytes.length>3 && bytes[0]==(byte)255 && bytes[1]==(byte)216;
        require(signature && repository.documents(workflow).size()<10,"document");
        String name=filename==null?"document":filename.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[^A-Za-z0-9._ -]","_");require(!name.isBlank() && name.length()<=160,"document");
        repository.document(workflow,a,UUID.randomUUID(),name,type,bytes,clock.now());repository.invalidate(workflow);
        repository.update(w,w.approval().approved()?Approval.RESUBMIT_FOR_APPROVAL:w.approval(),"NOT_VALIDATED","NOT_POSTED",w.total(),w.assessmentAmount(),w.itemCount(),0,0,List.of(),false,null,clock.now());
        repository.history(a,workflow,"DOCUMENT_ADDED","",clock.now());
        audit.logEvent("ACCOUNTING",workflow,"VOUCHER_DOCUMENT_ADDED",a.getMemberId(),AuditEventStatus.SUCCESS,"Supporting document added","VOUCHER_WORKFLOW",w.number(),a.getSaccoId(),a.getStationId(),Map.of());
    }
}
