package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class WorkflowRoutingService {
    private final OutboxService outboxService;
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void announceRecordedDisbursement(LoanApplication loan,UUID actor) {
        if(loan.getStatus()!=LoanStatus.DISBURSED)throw new IllegalStateException("Disbursement has not been posted");
        outboxService.enqueue("LOAN",loan.getId(),"DISBURSED",loan.getApplicantMemberId(),actor,loan.getSaccoId(),loan.getStationId(),
            Map.of("loanId",loan.getLoanId(),"disbursementDate",String.valueOf(loan.getDisbursementDate()),"disbursementAmount",loan.getAmount()));
    }

}
