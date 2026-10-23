package com.sacco.mvp.accounting.approval.service;

import com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.Objects;

/** Durable ready rows survive a restart. Failures remain visible until an authorized retry. */
@Service @RequiredArgsConstructor
public class VoucherAutomaticPostingService {
    private final VoucherWorkflowRepository repository;
    private final VoucherWorkflowPostingService posting;
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private final ApplicationClock clock;
    private final PlatformTransactionManager transactions;

    @Scheduled(fixedDelayString="${app.vouchers.auto-post-delay-ms:5000}")
    public void pending() {for(var id:repository.automaticCandidates())attempt(id);}

    public void attempt(UUID id) {
        var tx=new TransactionTemplate(transactions);
        var request=tx.execute(status->repository.automaticRequest(id).orElse(null));if(request==null)return;
        try {
            var actor=tx.execute(status->{
                var member=members.find(request.owner()).orElseThrow(()->new IllegalArgumentException("approval.error.assignment"));
                VoucherApprovalGate.require(Objects.equals(member.getSaccoId(),request.institution()) && Objects.equals(member.getStationId(),request.branch()),"assignment");
                return new AppUserPrincipal(member,claims.effectiveClaims(member.getId(),member.getActiveStaffRolesResolved(),member.isMemberAccess()),true);
            });
            posting.postAutomatically(actor,id,request.version());
        }catch(RuntimeException failure){tx.executeWithoutResult(status->repository.automaticFailed(id,request.version(),VoucherWorkflowService.safeError(failure),clock.now()));}
    }
}
