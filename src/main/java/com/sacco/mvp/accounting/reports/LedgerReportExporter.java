package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Only source-owned posted-book DTOs enter this exporter; these are explicitly draft reports. */
@Service
@RequiredArgsConstructor
public class LedgerReportExporter {
    private final LedgerReportService reports;
    private final ReportExportLimiter limiter;
    private final MessageSource messages;
    public enum Format { CSV, XLSX, PDF }
    public record Output(byte[] bytes,String contentType,String filename) { }

    public Output trial(AppUserPrincipal actor,LedgerReportService.Parameters parameters,boolean wide,Format format,Locale locale) {
        if(format!=Format.PDF)throw new IllegalArgumentException("financial.export.format");
        return limiter.run(()->{
            var source=reports.exportTrialBalance(actor,parameters,wide);
            try{return new Output(trialPdf(source,locale),"application/pdf","trial-balance.pdf");}
            catch(IOException failure){throw new IllegalStateException("Posted-book export failed",failure);}
        });
    }
    public Output activity(AppUserPrincipal actor,UUID account,LedgerReportService.Parameters parameters,boolean wide,Format format,Locale locale) {
        if(format!=Format.PDF)throw new IllegalArgumentException("financial.export.format");
        return limiter.run(()->{
            var source=reports.exportAccountActivity(actor,account,parameters,wide);
            try{return new Output(activityPdf(source,locale),"application/pdf","account-statement.pdf");}
            catch(IOException failure){throw new IllegalStateException("Account statement export failed",failure);}
        });
    }
    /** The caller must supply the authorized, bounded statement-line trial DTO. */
    public byte[] renderTrial(LedgerReportService.TrialBalance source,Locale locale){
        try{return trialPdf(source,locale);}catch(IOException failure){throw new IllegalStateException("Trial balance export failed",failure);}
    }
    private byte[] activityPdf(LedgerReportService.AccountActivityExport exported,Locale locale)throws IOException {
        var source=exported.activity();
        try(var document=new PDDocument();var input=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()){
            var font=PDType0Font.load(document,input);var p=source.parameters();
            var summary=List.of(source.scope().institution()+" / "+source.scope().branch(),source.code()+"  "+source.name(),
                text("financial.report.from",locale)+": "+p.from()+"  /  "+p.through()+"  |  TZS",
                text("financial.report.opening",locale)+": "+printed(source.opening(),locale)+"    "+text("financial.report.closing",locale)+": "+printed(source.closing(),locale));
            try(var page=new AccountingPdfDocument(document,font,false,text("statement.reference.accountTitle",locale),summary,null,AccountingPdfDocument.Style.ACCOUNT)){
                page.columns(labels(locale,"financial.report.effective","financial.report.reference","financial.report.source","financial.report.debit","financial.report.credit","statement.reference.running"),
                    new float[]{.14f,.2f,.24f,.14f,.14f,.14f},new boolean[]{false,false,false,true,true,true});
                BigDecimal running=source.opening();
                for(var row:source.movements()){
                    if(running!=null)running=running.add(row.debit()).subtract(row.credit());
                    page.row(List.of(row.effectiveDate().format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-uuuu",locale)).toUpperCase(locale),Objects.toString(row.sourceReference(),""),text("accounting.source."+row.sourceType(),locale),printed(row.debit(),locale),printed(row.credit(),locale),printed(running,locale)),false);
                }
                page.appendix(text("financial.print.reportContext",locale));
                for(var pair:context(source.scope(),p,source.coverage(),locale))page.text(pair.getFirst()+": "+pair.get(1));
                page.text(text("accounting.policy.version",locale)+": "+exported.policyVersion()+" / "+exported.policyId());
                for(var row:source.movements())page.text(row.journalId()+" | "+row.recordedAt()+" | "+row.postedAt()+" | "+Objects.toString(row.evidenceReference(),"")+" | "+Objects.toString(row.reversalOf(),""));
            }
            document.save(out);return out.toByteArray();
        }
    }
    private ArrayList<List<Object>> context(LedgerReportService.Scope scope,LedgerReportService.Parameters p,LedgerReportService.Coverage c,Locale locale) {
        var result=new ArrayList<List<Object>>();
        result.add(pair(text("financial.export.status",locale),text("financial.report.draft",locale)));
        result.add(pair(text("financial.export.basis",locale),text("financial.report.basis",locale)));
        result.add(pair(text("financial.report.currency",locale),"TZS"));
        result.add(pair(text("financial.export.institutionId",locale),scope.institution()));
        result.add(pair(text("financial.report.scope",locale),text(scope.institutionWide()?"financial.report.institution":"financial.report.branch",locale)));
        result.add(pair(text("financial.export.branchId",locale),scope.institutionWide()?text("financial.report.institution",locale):scope.branch()));
        result.add(pair(text("financial.report.from",locale),p.from().toString()));result.add(pair(text("financial.report.through",locale),p.through().toString()));
        result.add(pair(text("financial.report.recordedThrough",locale),p.recordedCutoff().toString()));
        result.add(pair(text("financial.report.missingOpenings",locale),c.missingOpenings()));
        result.add(pair(text("financial.report.unbridged",locale),c.unbridgedVouchers()));result.add(pair(text("financial.report.uncovered",locale),c.uncoveredLoans()));
        return result;
    }
    private String text(String key,Locale locale){return messages.getMessage(key,null,locale);}
    private byte[] trialPdf(LedgerReportService.TrialBalance source,Locale locale)throws IOException {
        try(var document=new PDDocument();var input=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()){
            var font=PDType0Font.load(document,input);var p=source.parameters();var scope=source.scope();
            var summary=List.of(scope.institution()+" / "+(scope.institutionWide()?text("financial.report.institution",locale):scope.branch()),
                text("financial.report.from",locale)+": "+p.from()+"  |  "+text("financial.report.through",locale)+": "+p.through()+"  |  TZS",
                text("financial.report.draft",locale)+"  |  "+text("financial.report.recordedThrough",locale)+": "+p.recordedCutoff());
            try(var page=new AccountingPdfDocument(document,font,true,text("financial.report.trial",locale).toUpperCase(locale),summary,null,AccountingPdfDocument.Style.TRIAL)){
                page.columns(labels(locale,"accounting.code","accounting.name","financial.report.opening","financial.report.debit","financial.report.credit","financial.print.netChange","financial.report.closing"),new float[]{.09f,.23f,.136f,.136f,.136f,.136f,.136f},new boolean[]{false,false,true,true,true,true,true});
                for(var a:source.accounts())page.row(List.of(a.code(),a.name(),printed(a.opening(),locale),printed(a.debit(),locale),printed(a.credit(),locale),printed(a.debit().subtract(a.credit()),locale),printed(a.closing(),locale)),false);
                var t=source.totals();page.row(List.of(text("financial.print.total",locale),"",printed(difference(t.openingDebit(),t.openingCredit()),locale),printed(t.movementDebit(),locale),printed(t.movementCredit(),locale),printed(t.movementDebit().subtract(t.movementCredit()),locale),printed(difference(t.closingDebit(),t.closingCredit()),locale)),true);
                page.appendix(text("financial.print.reportContext",locale));
                page.text(text("financial.report.balanceSign",locale));page.text(text("statement.reference.trialBasis",locale));page.text(text("statement.reference.accountNames",locale));
                for(var pair:context(scope,p,source.coverage(),locale))page.text(pair.getFirst()+": "+pair.get(1));
                page.text(text("accounting.policy.version",locale)+": "+source.policyVersion()+" / "+source.policyId());
                // Preserve the gross debit/credit totals, not just their net value.
                page.text(text("financial.export.openingDebit",locale)+": "+printed(t.openingDebit(),locale)+" / "+text("financial.export.openingCredit",locale)+": "+printed(t.openingCredit(),locale));
                page.text(text("financial.export.closingDebit",locale)+": "+printed(t.closingDebit(),locale)+" / "+text("financial.export.closingCredit",locale)+": "+printed(t.closingCredit(),locale));
            }document.save(out);return out.toByteArray();
        }
    }
    private static BigDecimal difference(BigDecimal debit,BigDecimal credit){return debit==null||credit==null?null:debit.subtract(credit);}
    private String printed(BigDecimal value,Locale locale){
        if(value==null)return text("financial.report.unknown",locale);
        var format=new java.text.DecimalFormat("#,##0.00;(#,##0.00)",java.text.DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        format.setRoundingMode(java.math.RoundingMode.UNNECESSARY);return format.format(value);
    }
    private List<String> labels(Locale locale,String... keys){return Arrays.stream(keys).map(k->text(k,locale)).toList();}
    private static List<Object> pair(Object key,Object value){return List.of(key,value);}
}
