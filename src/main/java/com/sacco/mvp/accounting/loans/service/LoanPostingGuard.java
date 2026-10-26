package com.sacco.mvp.accounting.loans.service;

import com.sacco.mvp.accounting.loans.repository.LoanPostingAuthorizationRepository;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanRepaymentLedgerService.PaymentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

/** Preserve loan posting ownership when the retired accounting workflows are unavailable. */
@Service @RequiredArgsConstructor
public class LoanPostingGuard {
    private final LoanPostingAuthorizationRepository sources;
    public boolean recordedLoan(UUID loan) {return sources.recordedLoan(loan);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockRecordedLoan(UUID loan,AppUserPrincipal actor){if(sources.recordedLoan(loan)&&!sources.lockRecorded(loan,actor))throw new org.springframework.security.access.AccessDeniedException("Loan unavailable");}
    @Transactional(propagation=Propagation.MANDATORY)
    public void disbursement(LoanApplication loan, UUID officer) {requireUnowned(loan.getId());}
    @Transactional(propagation=Propagation.MANDATORY)
    public void repayment(UUID loan, AppUserPrincipal actor, PaymentCommand command) {
        if (sources.recordedLoan(loan)) {
            if (!sources.recordedCommand(loan, actor, command.requestKey(), "PAYMENT", command.reference(), command.amount(), command.paymentDate()))
                throw new IllegalArgumentException("recording.error.directPath");
        } else requireUnowned(loan);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void reversal(UUID loan, AppUserPrincipal actor, UUID requestKey) {reversal(loan, actor, requestKey, null, null);}
    @Transactional(propagation=Propagation.MANDATORY)
    public void reversal(UUID loan, AppUserPrincipal actor, UUID requestKey, LocalDate effectiveDate, UUID originalTransaction) {
        if (sources.recordedLoan(loan)) {
            if (!sources.recordedReversal(loan, actor, requestKey, effectiveDate, originalTransaction))
                throw new IllegalArgumentException("recording.error.directPath");
        } else requireUnowned(loan);
    }
    private void requireUnowned(UUID loan) {
        if (sources.retainedSourceOwnsLoan(loan)) throw new IllegalArgumentException("recording.error.retiredSource");
    }
}
