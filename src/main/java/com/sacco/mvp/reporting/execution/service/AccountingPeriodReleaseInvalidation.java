package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.accounting.reconciliation.PeriodReopenListener;
import com.sacco.mvp.reporting.execution.repository.AccountingReleaseRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

/** D has authorized institution reopening. Invalidate every affected branch atomically, returning no records. */
@Component @RequiredArgsConstructor
public class AccountingPeriodReleaseInvalidation implements PeriodReopenListener {
 private final AccountingReleaseRepository repository;
 private final ApplicationClock clock;
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public void periodReopened(AppUserPrincipal actor,UUID period,String reason){repository.invalidatePeriod(actor.getSaccoId(),period,actor.getMemberId(),reason,clock.now());}
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public void periodReopened(AppUserPrincipal actor,UUID period,java.time.LocalDate affectedFrom,String reason){repository.invalidateFrom(actor.getSaccoId(),period,affectedFrom,actor.getMemberId(),reason,clock.now());}
}
