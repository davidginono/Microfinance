package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.accounting.statements.StatementMappingChangeListener;
import com.sacco.mvp.reporting.execution.repository.AccountingReleaseRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class AccountingMappingReleaseInvalidation implements StatementMappingChangeListener {
 private final AccountingReleaseRepository repository;
 private final ApplicationClock clock;
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public void mappingChanged(AppUserPrincipal actor,UUID template,UUID mapping,String reason){repository.invalidateMapping(actor.getSaccoId(),template,mapping,reason,actor.getMemberId(),reason,clock.now());}
}
