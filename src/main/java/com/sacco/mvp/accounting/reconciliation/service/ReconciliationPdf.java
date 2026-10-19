package com.sacco.mvp.accounting.reconciliation.service;

import com.sacco.mvp.accounting.reconciliation.dto.ReconciliationReport.*;
import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReconciliationPdf {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;
    private String msg(String key,Locale locale) { return messages.getMessage("reconciliation."+key,null,locale); }
    public byte[] build(Result r,Locale locale) {
        if(r.count()!=r.rows().size() || r.count()>5000)throw new IllegalArgumentException("reconciliation.error.large");
        return limiter.run(()->render(r,locale));
    }
    private byte[] render(Result r,Locale locale) {
        try(var document=new PDDocument();var out=new ByteArrayOutputStream();var input=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream()) {
            var font=PDType0Font.load(document,input);
            var context=List.of(r.institution()+" | "+r.branch(),com.sacco.mvp.service.ApplicationTimestamps.display(r.parameters().from())+" — "+com.sacco.mvp.service.ApplicationTimestamps.display(r.parameters().through())+" | TZS",
                msg("cutoff",locale)+": "+com.sacco.mvp.service.ApplicationTimestamps.display(r.parameters().cutoff()),msg("exceptionsOnly",locale));
            try(var page=new AccountingPdfDocument(document,font,true,msg("title",locale),context,null,AccountingPdfDocument.Style.REFERENCE)) {
                var labels=List.of("client","account","reference","date","transaction","description","slDebit","slCredit","glDebit","glCredit","debitVariance","creditVariance")
                    .stream().map(k->msg("column."+k,locale)).toList();
                float[] widths={.065f,.075f,.12f,.075f,.085f,.16f,.07f,.07f,.07f,.07f,.07f,.07f};
                // Reference stationery uses standalone financial alignment, independent of workspace tables.
                float sum=0;for(float width:widths)sum+=width;for(int n=0;n<widths.length;n++)widths[n]/=sum;
                boolean[] numeric={false,false,false,false,false,false,true,true,true,true,true,true};
                for(var group:r.groups()) {
                    page.endTable();page.section(msg("activity",locale)+": "+msg("component."+group.component(),locale)+" | "+msg("section."+group.section(),locale));
                    page.columns(labels,widths,numeric);
                    for(var row:r.rows())if(row.getGroup().equals(group.getKey())) {
                        var cells=new ArrayList<>(List.of(row.client(),row.loan().isBlank()?row.account():row.loan()+" / "+row.account(),row.reference(),
                            row.date()==null?"":com.sacco.mvp.service.ApplicationTimestamps.date(row.date()),msg("event."+row.activity(),locale),msg("issue."+row.issue(),locale)));
                        row.getAmounts().forEach(n->cells.add(money(n,locale)));page.row(cells,false);
                    }
                    var total=new ArrayList<>(List.of(msg("total",locale),"","","","",""));group.totals().forEach(n->total.add(money(n,locale)));page.row(total,true);
                }
                page.endTable();if(r.count()==0)page.text(msg("empty",locale));
                page.section(msg("basis",locale));page.text(msg("coverage",locale));page.text(msg("interestBasis",locale));page.text(msg("liveBasis",locale));
            }
            document.save(out);return out.toByteArray();
        } catch(IOException e) { throw new IllegalStateException("Reconciliation PDF failed",e); }
    }
    private String money(BigDecimal n,Locale locale) {
        if(n==null)return "—";
        var f=NumberFormat.getNumberInstance(locale);f.setMinimumFractionDigits(2);f.setMaximumFractionDigits(2);return f.format(n);
    }
}
