package com.sacco.mvp.accounting.approval.service;

import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.*;
import com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository;
import com.sacco.mvp.accounting.service.VoucherService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sacco.mvp.accounting.approval.service.VoucherApprovalGate.require;

/** All vouchers in a submitted batch commit together, including the approval envelope and audit. */
@Service @RequiredArgsConstructor
public class VoucherWorkflowPostingService {
    private final VoucherWorkflowRepository repository;
    private final VoucherApprovalGate gate;
    private final VoucherService vouchers;
    private final ApplicationClock clock;
    private final AuditService audit;
    @Transactional(timeout=60)
    public void post(AppUserPrincipal a,java.util.UUID id,int version) {
        post(a,id,version,false);
    }
    @Transactional(timeout=60)
    public void postAutomatically(AppUserPrincipal a,java.util.UUID id,int version) {
        post(a,id,version,true);
    }
    private void post(AppUserPrincipal a,java.util.UUID id,int version,boolean automatic) {
        gate.authorize(a,"VOUCHER_WORKSPACE_VIEW");gate.authorize(a,"VOUCHER_WORKSPACE_POST");repository.policyLock(a,false);
        var w=repository.find(a,id,true).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Voucher workspace unavailable"));
        vouchers.authorize(a,w.type(),"VIEW");vouchers.authorize(a,w.type(),"CREATE");if(w.posting().equals("POSTED"))return;
        require(w.version()==version && w.approval().approved() && w.validation().equals("VALIDATED"),"validate");
        require(w.owner().equals(a.getMemberId()),"assignment");
        require(!automatic || w.autoPost(),"assignment");
        var items=repository.items(id);require(!items.isEmpty() && items.size()==w.itemCount() && items.stream().allMatch(i->i.voucherId()==null),"changed");
        repository.update(w,w.approval(),w.validation(),"POSTING",w.total(),w.assessmentAmount(),w.itemCount(),w.stage(),w.configVersion(),w.stages(),w.forceAllowed(),null,clock.now());
        for(var item:items) {
            var posted=vouchers.post(a,w.type(),item.form());repository.link(id,item.id(),posted.id());
        }
        repository.update(w,w.approval(),w.validation(),"POSTED",w.total(),w.assessmentAmount(),w.itemCount(),w.stage(),w.configVersion(),w.stages(),w.forceAllowed(),null,clock.now());
        repository.history(a,id,"POSTED",automatic?"Automatic":"",clock.now());
        audit.logEvent("ACCOUNTING",id,"VOUCHER_WORKFLOW_POSTED",a.getMemberId(),AuditEventStatus.SUCCESS,"Voucher workflow posted","VOUCHER_WORKFLOW",w.number(),a.getSaccoId(),a.getStationId(),java.util.Map.of("vouchers",items.size(),"automatic",automatic));
    }
    @Transactional
    public void failed(AppUserPrincipal a,java.util.UUID id,int version,RuntimeException failure) {
        gate.authorize(a,"VOUCHER_WORKSPACE_VIEW");gate.authorize(a,"VOUCHER_WORKSPACE_POST");var found=repository.find(a,id,true);
        if(found.isEmpty())return;var w=found.get();
        vouchers.authorize(a,w.type(),"VIEW");vouchers.authorize(a,w.type(),"CREATE");
        require(w.owner().equals(a.getMemberId()),"assignment");
        if(w.version()!=version || w.posting().equals("POSTED") || !w.approval().approved())return;
        repository.update(w,w.approval(),w.validation(),"FAILED",w.total(),w.assessmentAmount(),w.itemCount(),w.stage(),w.configVersion(),w.stages(),w.forceAllowed(),VoucherWorkflowService.safeError(failure),clock.now());
        repository.history(a,id,"POSTING_FAILED","",clock.now());
    }
}
