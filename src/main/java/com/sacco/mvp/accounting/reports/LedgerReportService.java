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
    public record AccountActivityExport(AccountActivity activity,UUID policyId,int policyVersion) {}

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=20)
    public TrialBalance trialBalance(AppUserPrincipal actor, Parameters parameters, boolean institutionWide) {
        Scope scope=authorize(actor,institutionWide);
        validate(parameters);
        var policy=policyAt(scope,parameters);
        require(!parameters.from().isBefore(policy.openingDate()),"openingDate");
        var coverage=repository.coverage(scope,parameters);
        var accounts=repository.balances(scope,parameters,coverage.complete());
        return new TrialBalance(scope,parameters,policy.id(),policy.version(),accounts.stream().limit(25).toList(),
            repository.totals(scope,parameters,coverage.complete()),coverage,accounts.size()>25);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=20)
    public AccountActivity accountActivity(AppUserPrincipal actor, UUID accountId, Parameters parameters, boolean institutionWide) {
        Scope scope=authorizeActivity(actor,institutionWide,null);validate(parameters);require(accountId!=null,"account");
        var policy=policyAt(scope,parameters);
        require(!parameters.from().isBefore(policy.openingDate()),"openingDate");
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
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=20)
    public TrialBalance exportTrialBalance(AppUserPrincipal actor,Parameters parameters,boolean institutionWide) {
        Scope scope=authorize(actor,institutionWide,UserClaim.FINANCIAL_REPORTS_EXPORT);validate(parameters);
        var policy=policyAt(scope,parameters);
        require(!parameters.from().isBefore(policy.openingDate()),"openingDate");
        var coverage=repository.coverage(scope,parameters);var accounts=repository.exportBalances(scope,parameters,coverage.complete());
        require(accounts.size()<=2000,"exportLimit");
        return new TrialBalance(scope,parameters,policy.id(),policy.version(),List.copyOf(accounts),
            repository.totals(scope,parameters,coverage.complete()),coverage,false);
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=20)
    public AccountActivityExport exportAccountActivity(AppUserPrincipal actor,UUID accountId,Parameters parameters,boolean institutionWide) {
        Scope scope=authorizeActivity(actor,institutionWide,UserClaim.FINANCIAL_REPORTS_EXPORT);validate(parameters);require(accountId!=null,"account");
        var policy=policyAt(scope,parameters);
        require(!parameters.from().isBefore(policy.openingDate()),"openingDate");
        var coverage=repository.coverage(scope,parameters);var account=repository.balance(scope,parameters,accountId,coverage.complete())
            .orElseThrow(()->new AccessDeniedException("Account outside reporting scope"));
        var rows=repository.exportActivity(scope,parameters,accountId);require(rows.size()<=2000,"exportLimit");
        return new AccountActivityExport(new AccountActivity(scope,parameters,accountId,account.code(),account.name(),account.opening(),List.copyOf(rows),account.closing(),coverage,false),policy.id(),policy.version());
    }
    private LedgerReportRepository.PolicyLineage policyAt(Scope scope,Parameters parameters) {
        var policy=repository.policyAt(scope,parameters).orElseThrow(()->new IllegalArgumentException("financial.report.error.policyCutoff"));
        require("LOCAL_GL".equals(policy.ledger()),"policyCutoff");
        return policy;
    }
    private Scope authorizeActivity(AppUserPrincipal actor,boolean institutionWide,UserClaim action) {
        var scope=authorize(actor,false,action);
        if(institutionWide)throw new AccessDeniedException("Account activity requires the selected branch");
        return scope;
    }
    Scope authorizeAllocation(AppUserPrincipal actor,UserClaim allocationClaim) {
        if(allocationClaim!=null && allocationClaim!=UserClaim.ACCOUNTING_CASH_FLOW_CREATE && allocationClaim!=UserClaim.ACCOUNTING_CASH_FLOW_APPROVE)
            throw new AccessDeniedException("Cash flow action required");
        return authorize(actor,false,allocationClaim);
    }
    private Set<UserClaim> freshClaims(AppUserPrincipal actor) {
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
        return claims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),current.isMemberAccess());
    }
    private Scope authorize(AppUserPrincipal actor, boolean institutionWide, UserClaim allocationClaim) {
        var effective=freshClaims(actor);
        if(!effective.contains(UserClaim.FINANCIAL_REPORTS_VIEW)
            || (institutionWide && !effective.contains(UserClaim.FINANCIAL_REPORTS_INSTITUTION))
            || (allocationClaim!=null && !effective.contains(allocationClaim)))
            throw new AccessDeniedException("Financial report permission required");
        return new Scope(actor.getSaccoId(),actor.getStationId(),institutionWide);
    }
    /** Internal proof scope is separate from financial report registry and export access. */
    Scope authorizeClosingProof(AppUserPrincipal actor,String institution,String branch,boolean global) {
        var effective=freshClaims(actor);
        if(!Objects.equals(institution,actor.getSaccoId()) || branch==null || branch.isBlank() || branch.length()>255
                || (!global && !Objects.equals(branch,actor.getStationId())))throw new AccessDeniedException("Closing proof scope required");
        boolean permitted=global
            ?effective.contains(UserClaim.ACCOUNTING_CLOSING_INSTITUTION) && effective.contains(UserClaim.ACCOUNTING_CLOSING_APPROVE)
                || effective.contains(UserClaim.FINANCIAL_REPORTS_VIEW) && effective.contains(UserClaim.FINANCIAL_REPORTS_INSTITUTION)
            :effective.contains(UserClaim.ACCOUNTING_CLOSING_VIEW) || effective.contains(UserClaim.ACCOUNTING_CLOSING_CREATE)
                || effective.contains(UserClaim.ACCOUNTING_CLOSING_APPROVE) || effective.contains(UserClaim.FINANCIAL_REPORTS_VIEW);
        if(!permitted || institutions.findStation(institution,branch).isEmpty())throw new AccessDeniedException("Closing proof permission required");
        return new Scope(institution,branch,false);
    }
    Scope authorizeManagement(AppUserPrincipal actor,boolean wide){return authorize(actor,wide,UserClaim.LOAN_REPORTS_VIEW);}
    void validateDates(Parameters parameters){validate(parameters);}
    private void validate(Parameters p) {
        require(p!=null && p.from()!=null && p.through()!=null && p.recordedCutoff()!=null,"dates");
        require(!p.through().isBefore(p.from()) && !p.through().isAfter(clock.today())
            && !p.through().isAfter(p.from().plusDays(365)) && !p.recordedCutoff().isAfter(clock.now())
            && p.page()>=0 && p.page()<=10000,"dates");
    }
    private static void require(boolean condition,String key) {if(!condition)throw new IllegalArgumentException("financial.report.error."+key);}
}
