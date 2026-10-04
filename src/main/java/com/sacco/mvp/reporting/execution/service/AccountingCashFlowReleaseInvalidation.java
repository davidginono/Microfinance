package com.sacco.mvp.reporting.execution.service;
import com.sacco.mvp.accounting.reports.CashFlowApprovalListener;
import com.sacco.mvp.reporting.execution.repository.AccountingReleaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/** E holds the journal source guard; invalidate older current or comparative release provenance atomically. */
@Component @RequiredArgsConstructor
public class AccountingCashFlowReleaseInvalidation implements CashFlowApprovalListener {
 private final AccountingReleaseRepository repository;
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public void approved(String institution,UUID journal,UUID allocationId,int version,UUID reviewer,OffsetDateTime at){repository.invalidateCashFlow(institution,journal,version,reviewer,"Reviewed cash-flow allocation "+allocationId+" version "+version,at);}
}
