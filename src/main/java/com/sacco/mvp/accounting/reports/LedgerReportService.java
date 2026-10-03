package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/** Posted-book read boundary; final statements additionally require reviewed closing/mapping evidence. */
@Service
@RequiredArgsConstructor
public class LedgerReportService {
    private final LedgerReportRepository repository;
    private final AccountingPolicyService policies;
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private final SaccoRegistryService institutions;
    private final ApplicationClock clock;

    public record Scope(String institution, String branch, boolean institutionWide) {}
    public record Parameters(LocalDate from, LocalDate through, OffsetDateTime recordedCutoff, int page) {}
    public record AccountBalance(UUID id, String code, String name, String type, String purpose,
            BigDecimal opening, BigDecimal debit, BigDecimal credit, BigDecimal closing) {}
    public record Totals(BigDecimal openingDebit, BigDecimal openingCredit, BigDecimal movementDebit,
            BigDecimal movementCredit, BigDecimal closingDebit, BigDecimal closingCredit) {}
    public record Coverage(long missingOpenings, long unbridgedVouchers, long uncoveredLoans) {
        public boolean complete() { return missingOpenings==0 && unbridgedVouchers==0 && uncoveredLoans==0; }
    }
    public record TrialBalance(Scope scope, Parameters parameters, UUID policyId, int policyVersion,
            List<AccountBalance> accounts, Totals totals, Coverage coverage, boolean hasNext) {}
    public record Activity(UUID journalId, LocalDate effectiveDate, OffsetDateTime recordedAt,
            OffsetDateTime postedAt, String sourceType, String sourceReference, String evidenceReference,
            UUID reversalOf, BigDecimal debit, BigDecimal credit) {}
    public record AccountActivity(Scope scope, Parameters parameters, UUID accountId, String code,
            String name, BigDecimal opening, List<Activity> movements, BigDecimal closing,
            Coverage coverage, boolean hasNext) {}

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=20)
    public TrialBalance trialBalance(AppUserPrincipal actor, Parameters parameters, boolean institutionWide) {
        Scope scope=authorize(actor,institutionWide);
        validate(parameters);
        var policy=policies.requireApprovedLocalPolicy(scope.institution(),parameters.through());
        require(!parameters.from().isBefore(policy.openingDate()),"openingDate");
        require(policy.approvedAt()!=null && !policy.approvedAt().isAfter(parameters.recordedCutoff()),"policyCutoff");
        var coverage=repository.coverage(scope,parameters);
        var accounts=repository.balances(scope,parameters,coverage.complete());
        return new TrialBalance(scope,parameters,policy.id(),policy.version(),accounts.stream().limit(25).toList(),
            repository.totals(scope,parameters,coverage.complete()),coverage,accounts.size()>25);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=20)
    public AccountActivity accountActivity(AppUserPrincipal actor, UUID accountId, Parameters parameters, boolean institutionWide) {
        Scope scope=authorize(actor,institutionWide);validate(parameters);require(accountId!=null,"account");
        var policy=policies.requireApprovedLocalPolicy(scope.institution(),parameters.through());
        require(!parameters.from().isBefore(policy.openingDate()),"openingDate");
        require(policy.approvedAt()!=null && !policy.approvedAt().isAfter(parameters.recordedCutoff()),"policyCutoff");
        var coverage=repository.coverage(scope,parameters);
        var account=repository.balance(scope,parameters,accountId,coverage.complete())
            .orElseThrow(()->new AccessDeniedException("Account outside reporting scope"));
        var rows=repository.activity(scope,parameters,accountId);
        return new AccountActivity(scope,parameters,accountId,account.code(),account.name(),account.opening(),
            rows.stream().limit(25).toList(),account.closing(),coverage,rows.size()>25);
    }

    Scope authorize(AppUserPrincipal actor, boolean institutionWide) {
        return authorize(actor,institutionWide,null);
    }
    Scope authorizeAllocation(AppUserPrincipal actor,UserClaim allocationClaim) {
        if(allocationClaim!=null && allocationClaim!=UserClaim.ACCOUNTING_CASH_FLOW_CREATE && allocationClaim!=UserClaim.ACCOUNTING_CASH_FLOW_APPROVE)
            throw new AccessDeniedException("Cash flow action required");
        return authorize(actor,false,allocationClaim);
    }
    private Scope authorize(AppUserPrincipal actor, boolean institutionWide, UserClaim allocationClaim) {
        if(actor==null || !actor.isStaffSession() || actor.isPlatformIdentity() || actor.getMemberId()==null
            || actor.getSaccoId()==null || actor.getSaccoId().isBlank() || actor.getStationId()==null || actor.getStationId().isBlank())
            throw new AccessDeniedException("Financial report staff scope required");
        var current=members.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Staff unavailable"));
        if(current.getStatus()!=MemberStatus.ACTIVE || !current.isStaffAccessActive()
            || current.getActiveStaffRolesResolved().contains(Position.ADMIN)
            || !Objects.equals(current.getSaccoId(),actor.getSaccoId()) || !Objects.equals(current.getStationId(),actor.getStationId())
            || institutions.findActiveSacco(actor.getSaccoId()).isEmpty()
            || institutions.findStation(actor.getSaccoId(),actor.getStationId()).filter(SaccoStation::isActive)
                .filter(s->s.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty())
            throw new AccessDeniedException("Financial report workspace inactive");
        var effective=claims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),current.isMemberAccess());
        if(!effective.contains(UserClaim.FINANCIAL_REPORTS_VIEW)
            || (institutionWide && !effective.contains(UserClaim.FINANCIAL_REPORTS_INSTITUTION))
            || (allocationClaim!=null && !effective.contains(allocationClaim)))
            throw new AccessDeniedException("Financial report permission required");
        return new Scope(actor.getSaccoId(),actor.getStationId(),institutionWide);
    }
    private void validate(Parameters p) {
        require(p!=null && p.from()!=null && p.through()!=null && p.recordedCutoff()!=null,"dates");
        require(!p.through().isBefore(p.from()) && !p.through().isAfter(clock.today())
            && !p.through().isAfter(p.from().plusDays(365)) && !p.recordedCutoff().isAfter(clock.now())
            && p.page()>=0 && p.page()<=10000,"dates");
    }
    private static void require(boolean condition,String key) {if(!condition)throw new IllegalArgumentException("financial.report.error."+key);}
}
