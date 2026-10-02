package com.sacco.mvp.reporting.execution.service;

import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.Run;
import com.sacco.mvp.reporting.execution.repository.ReportRunRepository;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.reporting.OperationalReportService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportWorkerControl {
 private final ReportRunRepository repository;
 private final ApplicationClock clock;
 private final OperationalReportService reports;
 @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED) public Optional<Run> claim(){repository.quotaLock("__REPORT_WORKER_DISPATCH__");repository.recover(clock.now());return repository.claim(clock.now());}
 @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true) public void check(UUID id,UUID token){if(repository.cancelled(id,token))throw new IllegalStateException("report.run.error.cancelled");}
 @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true) public void authorize(Run run,AppUserPrincipal actor){reports.authorize(actor,run.definition().dataset(),UserClaim.REPORT_RUN);reports.authorize(actor,run.definition().dataset(),UserClaim.REPORT_EXPORT);}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void fail(Run run,String reason){repository.fail(run.id(),run.workerToken(),reason,clock.now());}
}
