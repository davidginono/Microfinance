package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.grouplending.dto.GroupOrganizationAgreementDtos.ExportData;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.security.*;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupOrganizationAgreementExportService {
    private final GroupOrganizationAgreementService signing;
    private final GroupOrganizationAgreementRepository agreements;
    private final GroupOrganizationDocumentRepository documents;
    private final GroupOrganizationAgreementPdf pdf;
    private final GeneralLedgerService staffAccess;
    private final AuthzService authz;
    private final PlatformTransactionManager transactions;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final ReportExportLimiter limiter;
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public byte[] client(AppUserPrincipal actor,UUID cycle,Locale locale){
        return limiter.run(()->{var data=signing.exportData(actor,cycle);byte[] result=pdf.render(data,locale);
            signing.documentPrepared(actor,cycle,data.row().snapshot().document().id(),data.row().hash());return result;});
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Optional<byte[]> staffLoan(AppUserPrincipal actor,UUID loan,Locale locale){return staffLoan(actor,loan,locale,true);}
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Optional<byte[]> staffLoan(AppUserPrincipal actor,UUID loan,Locale locale,boolean includeSignatures){
        if(actor==null||!actor.isStaffSession())return Optional.empty();
        var read=new TransactionTemplate(transactions);read.setReadOnly(true);read.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var permit=new java.util.concurrent.atomic.AtomicBoolean();
        try{var data=read.execute(tx->{
            staffAccess.requireActor(actor,"LOAN_DOCUMENTS_EXPORT");
            if(!authz.canViewLoan(loan,actor))throw new AccessDeniedException("Loan document unavailable");
            var row=agreements.staffLoan(actor,loan);if(row.isEmpty())return Optional.<ExportData>empty();
            staffAccess.requireActor(actor,"LOAN_DOCUMENTS_VIEW");
            if(!limiter.tryAcquire())throw ReportExportLimiter.busy();permit.set(true);
            var r=row.get();if(r.snapshot().document()==null)throw new IllegalArgumentException("group.error.documentRequired");
            return Optional.of(new ExportData(r,documents.body(actor,r.cycle(),r.snapshot().document().id()),documents.signatures(actor,r.cycle())));
        });
        if(data.isEmpty())return Optional.empty();byte[] result=pdf.render(data.get(),locale,includeSignatures);
        new TransactionTemplate(transactions).executeWithoutResult(tx->{staffScope(actor,loan);audit.logEvent("GROUP_LENDING",data.get().row().cycle(),"ORGANIZATION_AGREEMENT_EXPORTED",actor.getMemberId(),AuditEventStatus.SUCCESS,"Full frozen organization agreement exported","GROUP_CYCLE",data.get().row().cycle().toString(),actor.getSaccoId(),actor.getStationId(),Map.of("loan",loan,"packet",data.get().row().hash(),"at",clock.now(),"includeSignatures",includeSignatures));});
        return Optional.of(result);
        }finally{if(permit.get())limiter.release();}
    }
    private void staffScope(AppUserPrincipal a,UUID loan){staffAccess.requireActor(a,"LOAN_DOCUMENTS_VIEW");staffAccess.requireActor(a,"LOAN_DOCUMENTS_EXPORT");if(!authz.canViewLoan(loan,a))throw new AccessDeniedException("Loan document unavailable");}
}
