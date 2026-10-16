package com.sacco.mvp.accounting.management.service;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

@Service @RequiredArgsConstructor
public class ManagementReportPdf {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;
    public String message(String key,Locale locale){return messages.getMessage("management."+key,null,locale);}
    public byte[] build(Result r,Locale locale){
        if(r.rows().size()!=r.count() || r.count()>5000)throw new IllegalArgumentException("management.error.large");
        return limiter.run(()->render(r,locale));
    }
    private byte[] render(Result r,Locale locale){
        try(var document=new PDDocument();var out=new ByteArrayOutputStream();var input=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream()){
            var font=PDType0Font.load(document,input);
            boolean asAt=r.kind()==Kind.PORTFOLIO_RISK || r.kind()==Kind.PORTFOLIO_ANALYSIS;
            var context=new ArrayList<>(List.of(r.institution()+" | "+r.branch(),message(asAt?"asAt":"period",locale)+": "+(asAt?"":r.parameters().from()+" — ")+r.parameters().through()+" | TZS",
                message("cutoff",locale)+": "+r.parameters().cutoff(),message("excluded",locale)+": "+r.excluded()));
            if(r.kind()==Kind.CASHBOOK || r.kind()==Kind.DAILY_COLLECTIONS)context.add(message("accounts",locale)+": "+(r.parameters().startAccount()==null && r.parameters().endAccount()==null?message("allAccounts",locale):r.accountRange()));
            if(!r.parameters().client().isBlank())context.add(message("clientSearch",locale)+": "+r.parameters().client());
            if(!r.parameters().product().isBlank())context.add(message("productSearch",locale)+": "+r.parameters().product());
            try(var page=new AccountingPdfDocument(document,font,true,message("title."+r.kind(),locale),context,null,AccountingPdfDocument.Style.REFERENCE)){
                float total=(float)r.columns().stream().mapToDouble(Column::width).sum();float[] widths=new float[r.columns().size()];boolean[] numeric=new boolean[widths.length];
                for(int n=0;n<widths.length;n++){widths[n]=r.columns().get(n).width()/total;numeric[n]=r.columns().get(n).money();}
                var labels=r.columns().stream().map(c->message("field."+c.key(),locale)).toList();
                String day=null;
                for(var group:r.groups()){
                    if(r.kind()==Kind.DAILY_COLLECTIONS && day!=null && !group.name().startsWith(day))page.row(totalRow(r,r.getDailyTotals().get(day),message("dayTotal",locale),locale),true);
                    String activity=group.name().contains(" | ")?group.name().substring(group.name().indexOf(" | ")+3):"";
                    String groupTitle=Set.of("@loan","@general").contains(activity)?group.name().substring(0,group.name().indexOf(" | ")+3)+message(activity.equals("@loan")?"loanRepayments":"generalReceipts",locale):group.name();
                    page.endTable();page.section(groupTitle);page.columns(labels,widths,numeric);
                    for(var row:r.rows())if(Objects.equals(row.get("groupName"),group.name()))page.row(r.columns().stream().map(c->"transaction".equals(c.key()) && Set.of("PRINCIPAL","INTEREST").contains(Objects.toString(row.get("component"),""))?message("component."+row.get("component"),locale)+" — "+value(row.get(c.key()),locale):value(row.get(c.key()),locale)).toList(),false);
                    page.row(totalRow(r,group.totals(),message("groupTotal",locale),locale),true);
                    day=group.name().substring(0,Math.min(10,group.name().length()));
                }
                if(r.kind()==Kind.DAILY_COLLECTIONS && day!=null)page.row(totalRow(r,r.getDailyTotals().get(day),message("dayTotal",locale),locale),true);
                if(r.groups().isEmpty()){page.columns(labels,widths,numeric);page.text(message("empty",locale));}
                page.row(totalRow(r,r.totals(),message("total",locale),locale),true);
                page.endTable();page.section(message("basis",locale));page.text(message("coverage."+r.kind(),locale));
                if(r.kind()==Kind.PORTFOLIO_RISK)page.text(message("riskBasis",locale));
                if(r.kind()==Kind.LOAN_PORTFOLIO || r.kind()==Kind.PORTFOLIO_ANALYSIS)page.text(message("interestBasis",locale));
                if(r.excluded()>0){
                    if(r.kind()!=Kind.DAILY_COLLECTIONS && r.kind()!=Kind.CASHBOOK)page.text(message("exclusionBasis",locale));
                    page.text(message("missingNext",locale));
                }
                page.text(message("fullTotals",locale));
            }
            document.save(out);return out.toByteArray();
        }catch(IOException failure){throw new IllegalStateException("PDF generation failed",failure);}
    }
    private List<String> totalRow(Result r,Map<String,BigDecimal> totals,String title,Locale locale){var values=new ArrayList<String>();for(int n=0;n<r.columns().size();n++){var c=r.columns().get(n);values.add(n==0?title:c.money()?value(totals.get(c.key()),locale):"");}return values;}
    private String value(Object value,Locale locale){if(value==null)return "—";if(value instanceof BigDecimal n){var format=NumberFormat.getNumberInstance(locale);format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(2);return format.format(n);}return value.toString();}
}
