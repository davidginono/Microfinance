package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.reporting.execution.repository.AccountingReleaseRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;

/** Leaf dependency for posting services: no accounting, closing, or statement service dependency. */
@Service @RequiredArgsConstructor
public class AccountingReleaseGateService {
 private final AccountingReleaseRepository repository;
 /** Owning posting services must already authorize the current actor and resolve its approved policy. */
 @Transactional(propagation=Propagation.MANDATORY)
 public void requireLiveRelease(AppUserPrincipal actor,UUID policy,int version){
  Integer isolation=TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();if(isolation!=null&&isolation!=TransactionDefinition.ISOLATION_READ_COMMITTED)throw new IllegalStateException("Live release requires a fresh READ_COMMITTED posting transaction");
  if(actor==null||!actor.isStaffSession()||actor.isPlatformIdentity()||actor.getSaccoId()==null||actor.getStationId()==null||policy==null||version<1)throw new IllegalArgumentException("accounting.release.error.restricted");
  UUID release=repository.lockLiveCandidate(actor.getSaccoId(),actor.getStationId(),policy,version).orElseThrow(()->new IllegalArgumentException("accounting.release.error.restricted"));
  if(!repository.liveId(release))throw new IllegalArgumentException("accounting.release.error.restricted");
 }
 @Transactional(readOnly=true) public boolean hasInstitutionHistory(String institution){return repository.hasInstitutionHistory(institution);}
 @Transactional(readOnly=true) public boolean hasMemberHistory(UUID member){return repository.hasMemberHistory(member);}
}
