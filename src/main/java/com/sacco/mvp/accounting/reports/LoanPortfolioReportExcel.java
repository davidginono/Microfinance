package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Report;
import com.sacco.mvp.accounting.reports.LoanPortfolioReportRepository.*;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class LoanPortfolioReportExcel {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;
    public byte[] build(Report r,Locale locale){
        if(r.hasNext() || r.rows().size()>5000 || r.branches().size()>1000 || r.scope().institutionWide() && !r.rows().isEmpty())throw new IllegalArgumentException("financial.portfolio.error.exportLarge");
        return limiter.run(()->render(r,locale));
    }
    private String label(String key,Locale locale){return messages.getMessage(key,null,locale);}
    private String p(String key,Locale locale){return label("financial.portfolio."+key,locale);}
    private byte[] render(Report r,Locale l){
        try(var b=new AccountingReportWorkbook("Arial")){
            var s=b.sheet(label("management.sheet.report",l),3,false,PrintSetup.A4_PAPERSIZE);context(b,s,r,3,l);
            s.setColumnWidth(0,65*256);s.setColumnWidth(1,24*256);s.setColumnWidth(2,24*256);
            b.headers(s,List.of(p("measure",l),"TZS",p("percentage",l)+" (%)"));
            var t=r.summary();
            var amounts=new LinkedHashMap<String,Object>();amounts.put("principal",t.principal());amounts.put("overduePrincipal",t.overduePrincipal());amounts.put("overdueInterest",t.overdueInterest());amounts.put("disbursed",t.disbursed());amounts.put("collectedPrincipal",t.collectedPrincipal());amounts.put("collectedInterest",t.collectedInterest());
            amounts.put("fees",null);amounts.put("recovery",null);amounts.put("allowance",r.adjustments().get("allowance"));amounts.put("writeOff",r.adjustments().get("writeOff"));
            amounts.forEach((k,v)->b.row(s,Arrays.asList(p(k,l),v,null),Set.of(1),false));
            b.row(s,Arrays.asList(p("par1",l),t.par1(),r.ratios().par1()),Set.of(1),false);
            b.row(s,Arrays.asList(p("par30",l),t.par30(),r.ratios().par30()),Set.of(1),false);
            b.row(s,Arrays.asList(p("par90",l),t.par90(),r.ratios().par90()),Set.of(1),false);
            b.line(s,p("parDefinition",l),3,false);
            b.line(s,p("coverage",l),3,true);
            b.row(s,List.of(p("covered",l),t.covered()),Set.of(),false);b.row(s,List.of(p("excluded",l),t.excluded()),Set.of(),false);
            b.row(s,List.of(label("financial.report.missingOpenings",l),r.bookCoverage().missingOpenings()),Set.of(),false);
            b.row(s,List.of(label("financial.report.unbridged",l),r.bookCoverage().unbridgedVouchers()),Set.of(),false);
            b.row(s,List.of(label("financial.report.uncovered",l),r.bookCoverage().uncoveredLoans()),Set.of(),false);
            b.line(s,t.excluded()>0 || !r.bookCoverage().complete()?p("coverageLimit",l):p("coveredScope",l),3,false);
            buckets(b,s,r.ageing(),false,l);buckets(b,s,r.classificationReference(),true,l);
            b.line(s,p("regulatoryNote",l),3,false);b.line(s,p("basis",l),3,false);b.line(s,p("advanced",l),3,false);
            b.line(s,label("management.fullTotals",l),3,false);b.line(s,p("definitions",l)+": "+r.definitionsVersion()+" / "+r.regulatoryReference(),3,false);
            s.createFreezePane(0,6);
            if(r.scope().institutionWide()){
                var data=b.sheet(p("branches",l),6,true,PrintSetup.A3_PAPERSIZE);context(b,data,r,6,l);
                int header=b.headers(data,List.of(label("financial.report.branch",l),p("covered",l),p("excluded",l),p("principal",l),p("par30",l),p("disbursed",l)));
                for(var branch:r.branches())b.row(data,List.of(branch.branch(),branch.summary().covered(),branch.summary().excluded(),branch.summary().principal(),branch.summary().par30(),branch.summary().disbursed()),Set.of(3,4,5),false);
                b.filter(data,header,6);
            }else{
                var data=b.sheet(p("loans",l),6,true,PrintSetup.A3_PAPERSIZE);context(b,data,r,6,l);
                int header=b.headers(data,List.of(p("loan",l),p("principal",l),p("overduePrincipal",l),p("overdueInterest",l),p("oldest",l),p("coverage",l)));
                for(var row:r.rows())b.row(data,Arrays.asList(row.number(),row.principal(),row.overduePrincipal(),row.overdueInterest(),row.oldest(),p(row.complete()?"verified":"unverified",l)),Set.of(1,2,3),false);
                b.filter(data,header,6);
            }
            if(b.hasExactText())b.line(s,label("management.exactAmounts",l),3,false);
            return b.bytes();
        }
    }
    private void context(AccountingReportWorkbook b,Sheet s,Report r,int n,Locale l){
        b.title(s,p("results",l),n);b.line(s,r.scope().institution()+" | "+(r.scope().institutionWide()?label("financial.report.institution",l):r.scope().branch())+" | TZS",n,false);
        b.line(s,p("period",l)+": "+r.parameters().from()+" — "+r.parameters().through(),n,false);b.line(s,label("financial.report.recordedThrough",l)+": "+r.parameters().recordedCutoff(),n,false);
        b.line(s,label("management.sheet.unknown",l),n,false);
    }
    private void buckets(AccountingReportWorkbook b,Sheet s,List<Bucket> buckets,boolean reference,Locale l){
        b.line(s,p(reference?"classification":"ageing",l),3,true);b.headers(s,List.of(p("band",l),p("covered",l),p("principal",l)+" (TZS)"));
        for(int band=0;band<5;band++){int current=band;var bucket=buckets.stream().filter(v->v.band()==current).findFirst().orElse(new Bucket(band,0,java.math.BigDecimal.ZERO));b.row(s,List.of(p((reference?"reg.":"age.")+band,l),bucket.loans(),bucket.principal()),Set.of(2),false);}
    }
}
