package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static com.sacco.mvp.accounting.reports.LedgerReportService.*;
import static com.sacco.mvp.accounting.reports.LoanPortfolioReportRepository.*;

@Service
@RequiredArgsConstructor
public class LoanPortfolioReportService {
    private final LedgerReportService access;
    private final LedgerReportRepository books;
    private final LoanPortfolioReportRepository sources;
    public record Ratios(BigDecimal par1,BigDecimal par30,BigDecimal par90) { }
    public record Report(Scope scope,Parameters parameters,Summary summary,Ratios ratios,Coverage bookCoverage,
            List<SourceRow> rows,List<Bucket> ageing,List<Bucket> classificationReference,List<BranchPerformance> branches,
            boolean hasNext,String definitionsVersion,String regulatoryReference) { }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=20)
    public Report report(AppUserPrincipal actor,Parameters p,boolean institutionWide) {
        var scope=access.authorizeManagement(actor,institutionWide);access.validateDates(p);
        var summary=sources.summary(scope,p);var coverage=books.coverage(scope,p);
        boolean known=summary.excluded()==0 && coverage.complete();
        var ratios=new Ratios(known?LoanRiskMetrics.percentage(summary.par1(),summary.principal()):null,
            known?LoanRiskMetrics.percentage(summary.par30(),summary.principal()):null,
            known?LoanRiskMetrics.percentage(summary.par90(),summary.principal()):null);
        var rows=institutionWide?List.<SourceRow>of():sources.rows(scope,p);var branches=sources.branches(scope,p);
        if(branches.size()>1000)throw new IllegalArgumentException("financial.portfolio.error.branches");
        return new Report(scope,p,summary,ratios,coverage,rows.stream().limit(25).toList(),sources.buckets(scope,p,false),
            sources.buckets(scope,p,true),List.copyOf(branches),rows.size()>25,LoanRiskMetrics.VERSION,LoanRiskMetrics.REGULATORY_REFERENCE);
    }
}
