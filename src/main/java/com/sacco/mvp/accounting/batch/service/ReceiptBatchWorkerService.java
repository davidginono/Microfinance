package com.sacco.mvp.accounting.batch.service;

import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.*;
import com.sacco.mvp.accounting.batch.repository.ReceiptBatchRepository;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.LoanReceiptService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReceiptBatchWorkerService {
    private final ReceiptBatchRepository repository;
    private final ReceiptBatchService batches;
    private final LoanReceiptService receipts;
    private final LoanRecordingService loans;
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private final ApplicationClock clock;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Optional<Batch> claim(){return repository.claim(clock.now());}
    @Transactional(propagation=Propagation.REQUIRES_NEW,timeout=30)
    public void step(Batch claimed){
        var locked=repository.workerLock(claimed.id(),claimed.workerToken());if(locked.isEmpty())return;var b=locked.get();
        if(b.cancelRequested()){repository.state(b.id(),"CANCELLED",clock.now());return;}
        boolean posting=b.state().equals("POSTING");var next=repository.next(b.id(),posting);
        if(next.isEmpty()){var summary=repository.summary(b);repository.state(b.id(),posting?(summary.failed()>0?"PARTIAL":"COMPLETED"):(summary.failed()>0?"DRAFT":"READY"),clock.now());return;}
        var item=next.get();var actor=actor(b);batches.authorize(actor,true);
        if(repository.duplicateReference(b.id(),item.id(),item.form().getMoneyAccountId(),item.form().getReference()))throw new IllegalArgumentException("batch.error.duplicate");
        if(posting){
            loans.receiptDefaults(actor,item.form());
            // Lock before comparing the preview. LoanReceiptService reuses these locks in the same transaction.
            loans.lockReceiptLoans(actor,item.form().getRows().stream().map(r->r.getLoanId()).filter(Objects::nonNull).sorted().toList(),item.form().getClientId());
            var current=batches.plan(actor,item,List.of());if(!ReceiptBatchService.samePlan(item.preview(),current))throw new IllegalArgumentException("batch.error.changed");
            var receipt=receipts.post(actor,item.form());repository.posted(b.id(),item.id(),receipt.header().id(),clock.now());
        }else repository.ready(b.id(),item.id(),batches.plan(actor,item,repository.priorPlans(b.id())));
        repository.release(b.id(),b.workerToken(),clock.now());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failure(Batch claimed,RuntimeException failure){
        var locked=repository.workerLock(claimed.id(),claimed.workerToken());if(locked.isEmpty())return;var b=locked.get();var next=repository.next(b.id(),b.state().equals("POSTING"));if(next.isEmpty()){repository.release(b.id(),b.workerToken(),clock.now());return;}var item=next.get();
        if(failure instanceof TransientDataAccessException && repository.attempt(b.id(),item.id())<3){repository.defer(b.id(),b.workerToken(),clock.now());return;}
        String key=failure instanceof AccessDeniedException?"batch.error.permission":failure instanceof DataIntegrityViolationException?"batch.error.duplicate":safeError(failure);
        repository.failed(b.id(),item.id(),key);
        if(failure instanceof AccessDeniedException)repository.state(b.id(),"PARTIAL",clock.now());else repository.release(b.id(),b.workerToken(),clock.now());
    }
    private AppUserPrincipal actor(Batch b){
        var member=members.find(b.owner()).orElseThrow(()->new AccessDeniedException("Batch owner unavailable"));
        if(!Objects.equals(member.getSaccoId(),b.institution()) || !Objects.equals(member.getStationId(),b.branch()))throw new AccessDeniedException("Batch scope changed");
        return new AppUserPrincipal(member,claims.effectiveClaims(member.getId(),member.getActiveStaffRolesResolved(),member.isMemberAccess()),true);
    }
    public static String safeError(RuntimeException e){String key=e.getMessage();return key!=null && key.length()<=120 && key.matches("(?:batch|voucher|recording|repayment|charges)\\.error\\.[A-Za-z0-9]+")?key:"batch.error.failed";}
}
