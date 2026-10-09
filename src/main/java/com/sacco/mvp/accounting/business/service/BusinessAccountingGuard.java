package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.accounting.business.repository.BusinessAccountingRepository;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanRepaymentLedgerService.PaymentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
import java.time.LocalDate;

/** The database-backed posting ticket is created only by the independently authorized source workflow. */
@Service @RequiredArgsConstructor
public class BusinessAccountingGuard {
    private final BusinessAccountingRepository sources;

    public boolean recordedLoan(UUID loan) { return sources.recordedLoan(loan); }

    @Transactional(propagation=Propagation.MANDATORY)
    public void disbursement(LoanApplication loan,UUID officer) {
        if(sources.activated(loan.getSaccoId()) && !sources.trustedLoanCommand(loan.getSaccoId(),loan.getStationId(),loan.getId(),officer,
            "LOAN_DISBURSEMENT",null,loan.getDisbursementReference(),loan.getAmount(),loan.getDisbursementDate()))
            throw new IllegalArgumentException("finance.business.error.reviewedSource");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void repayment(UUID loan,AppUserPrincipal actor,PaymentCommand command) {
        if(sources.recordedLoan(loan)) {
            if(!sources.recordedCommand(loan,actor,command.requestKey(),"PAYMENT",command.reference(),command.amount(),command.paymentDate()))
                throw new IllegalArgumentException("recording.error.directPath");
            return;
        }
        if(sources.activated(actor.getSaccoId()) && !sources.trustedLoanCommand(actor.getSaccoId(),actor.getStationId(),loan,actor.getMemberId(),
            "LOAN_REPAYMENT",command.requestKey(),command.reference(),command.amount(),command.paymentDate()))
            throw new IllegalArgumentException("finance.business.error.reviewedSource");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void reversal(UUID loan,AppUserPrincipal actor,UUID requestKey) {
        if(sources.recordedLoan(loan)) {
            if(!sources.recordedReversal(loan,actor,requestKey,null,null))throw new IllegalArgumentException("recording.error.directPath");
            return;
        }
        if(sources.activated(actor.getSaccoId()) && !sources.byRequest(actor.getSaccoId(),actor.getStationId(),requestKey)
            .filter(d->"POSTING".equals(d.state()) && "LOAN_REPAYMENT_REVERSAL".equals(d.command().kind().name()) && loan.equals(d.command().loanId()) && actor.getMemberId().equals(d.checkerId())).isPresent())
            throw new IllegalArgumentException("finance.business.error.reviewedSource");
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void reversal(UUID loan,AppUserPrincipal actor,UUID requestKey,LocalDate effectiveDate,UUID originalTransaction) {
        if(sources.recordedLoan(loan)) {
            if(!sources.recordedReversal(loan,actor,requestKey,effectiveDate,originalTransaction))throw new IllegalArgumentException("recording.error.directPath");
            return;
        }
        if(sources.activated(actor.getSaccoId()) && !sources.trustedReversal(actor.getSaccoId(),actor.getStationId(),loan,
            actor.getMemberId(),requestKey,effectiveDate,originalTransaction))
            throw new IllegalArgumentException("finance.business.error.reviewedSource");
    }
}
