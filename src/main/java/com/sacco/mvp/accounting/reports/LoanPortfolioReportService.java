package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.service.*;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import static com.sacco.mvp.accounting.reports.LoanPortfolioReportRepository.*;

@Service
@RequiredArgsConstructor
public class LoanPortfolioReportService {
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private final SaccoRegistryService institutions;
    private final ApplicationClock clock;
    private final LoanPortfolioReportRepository sources;
    public record Scope(String institution, String branch, boolean institutionWide) {}
    public record Parameters(LocalDate from, LocalDate through, OffsetDateTime recordedCutoff, int page) {}
    public record Coverage(long missingOpenings, long unbridgedVouchers, long uncoveredLoans) {
        public boolean complete() {return missingOpenings == 0 && unbridgedVouchers == 0 && uncoveredLoans == 0;}
    }
    public record Ratios(BigDecimal par1,BigDecimal par30,BigDecimal par90) { }
    public record Report(Scope scope,Parameters parameters,Summary summary,Ratios ratios,Coverage bookCoverage,
            List<SourceRow> rows,List<Bucket> ageing,List<Bucket> classificationReference,List<BranchPerformance> branches,
            boolean hasNext,String definitionsVersion,String regulatoryReference) { }
    public Scope configurationScope(AppUserPrincipal actor,boolean institutionWide) {
        return authorize(actor,institutionWide);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=20)
    public Report report(AppUserPrincipal actor,Parameters p,boolean institutionWide) {
        var scope=authorize(actor,institutionWide);validate(p);
        var summary=sources.summary(scope,p);var coverage=sources.coverage(scope,p);
        boolean known=summary.excluded()==0 && coverage.complete();
        var ratios=new Ratios(known?LoanRiskMetrics.percentage(summary.par1(),summary.principal()):null,
            known?LoanRiskMetrics.percentage(summary.par30(),summary.principal()):null,
            known?LoanRiskMetrics.percentage(summary.par90(),summary.principal()):null);
        var rows=institutionWide?List.<SourceRow>of():sources.rows(scope,p);var branches=sources.branches(scope,p);
        if(branches.size()>1000)throw new IllegalArgumentException("financial.portfolio.error.branches");
        return new Report(scope,p,summary,ratios,coverage,rows.stream().limit(25).toList(),sources.buckets(scope,p,false),
            sources.buckets(scope,p,true),List.copyOf(branches),rows.size()>25,LoanRiskMetrics.VERSION,LoanRiskMetrics.REGULATORY_REFERENCE);
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
    private Scope authorize(AppUserPrincipal actor, boolean wide) {
        var effective = freshClaims(actor);
        if (!effective.contains(UserClaim.FINANCIAL_REPORTS_VIEW) || !effective.contains(UserClaim.LOAN_REPORTS_VIEW)
            || wide && !effective.contains(UserClaim.FINANCIAL_REPORTS_INSTITUTION))
            throw new AccessDeniedException("Loan portfolio permission required");
        return new Scope(actor.getSaccoId(), actor.getStationId(), wide);
    }
    private void validate(Parameters p) {
        require(p!=null && p.from()!=null && p.through()!=null && p.recordedCutoff()!=null,"dates");
        require(!p.through().isBefore(p.from()) && !p.through().isAfter(clock.today())
            && !p.through().isAfter(p.from().plusDays(365)) && !p.recordedCutoff().isAfter(clock.now())
            && p.page()>=0 && p.page()<=10000,"dates");
    }
    private static void require(boolean condition,String key) {if(!condition)throw new IllegalArgumentException("financial.report.error."+key);}
}
