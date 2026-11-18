package com.sacco.mvp.accounting.loans.service;

import com.sacco.mvp.accounting.loans.dto.LoanCancellationDtos.*;
import com.sacco.mvp.accounting.loans.repository.*;
import com.sacco.mvp.accounting.adjustments.dto.LoanAdjustmentDtos;
import com.sacco.mvp.accounting.adjustments.service.LoanAdjustmentService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanCancellationService {
    private final LoanCancellationRepository cancellations;
    private final LoanRecordingRepository records;
    private final LoanAdjustmentService adjustments;
    private final GeneralLedgerService access;
    private final ApplicationClock clock;
    private final AuditService audit;

    @Transactional(readOnly=true)
    public View view(AppUserPrincipal actor,UUID id) {
        access.requireActor(actor,"LOAN_RECORDING_VIEW");
        return cancellations.view(actor,id);
    }

    @Transactional
    public Result cancel(AppUserPrincipal actor,UUID id,Form form) {
        access.requireActor(actor,"LOAN_RECORDING_VIEW");access.requireActor(actor,"LOAN_RECORDING_CANCEL");
        check(form!=null && form.getRequestKey()!=null && form.isConfirmed(),"confirmation");
        check(form.getState()!=null && form.getState().length()<=64 && form.getTerms()!=null && form.getTerms().length()==32,"changed");
        check(form.getReason()!=null && !form.getReason().isBlank() && form.getReason().length()<=500,"reason");
        // Adjustment idempotency acquires its request-key lock before its source locks.
        // Reusing this namespace avoids a reversed key/source lock order on retries.
        cancellations.lockRequest(actor,form.getRequestKey());
        cancellations.lock(actor,id);
        var loan=cancellations.view(actor,id);
        if(loan.funded()) {
            adjustments.authorize(actor,"VIEW");adjustments.authorize(actor,"CREATE");
            check(form.getEffectiveDate()!=null,"date");
            check(form.getEvidence()!=null && !form.getEvidence().isBlank() && form.getEvidence().length()<=500,"evidence");
        }
        if(cancellations.withdrawalRetry(actor,id,form))return new Result(id,null);
        if(cancellations.adjustmentRetry(actor,form.getRequestKey())) {
            // The owning adjustment validates its frozen payload. A completed
            // correction may already have changed the loan's balance projection.
            return new Result(id,adjustments.request(actor,adjustment(id,form)));
        }
        check(loan.status().equals(form.getState()) && loan.terms().equals(form.getTerms()),"changed");
        if(loan.blockedReason()!=null)throw new IllegalArgumentException(loan.blockedReason());
        if(loan.funded())return new Result(id,adjustments.request(actor,adjustment(id,form)));
        check(form.getEffectiveDate()==null && (form.getEvidence()==null || form.getEvidence().isBlank()),"changed");
        var source=records.groupLoan(actor,id,false);
        records.withdrawGroup(actor,source,form.getRequestKey(),form.getReason(),clock.now());
        audit.logEvent("ACCOUNTING_LOAN_ADJUSTMENT",id,"LOAN_CANCELLED",actor.getMemberId(),AuditEventStatus.SUCCESS,
            "Loan cancelled","LOAN",id.toString(),actor.getSaccoId(),actor.getStationId(),
            Map.of("request",form.getRequestKey(),"previousStatus",source.status(),"terms",source.termsHash(),"reason",form.getReason().strip()));
        return new Result(id,null);
    }

    private static LoanAdjustmentDtos.Form adjustment(UUID id,Form source) {
        var form=new LoanAdjustmentDtos.Form();form.setRequestKey(source.getRequestKey());form.setLoanId(id);
        form.setKind(LoanAdjustmentDtos.Kind.DISBURSEMENT_REVERSAL);form.setDate(source.getEffectiveDate());
        form.setReason(source.getReason());form.setEvidence(source.getEvidence());return form;
    }
    private static void check(boolean condition,String key) {
        if(!condition)throw new IllegalArgumentException("cancellation.error."+key);
    }
}
