package com.sacco.mvp.accounting.management.service;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.reports.AccountingReportWorkbook;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class ManagementReportExcel {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;
    public static boolean supports(Kind kind){return com.sacco.mvp.accounting.management.dto.ManagementReports.spreadsheetSupported(kind);}
    public byte[] build(Result r,Locale locale){
        if(!supports(r.kind()))throw new IllegalArgumentException("management.error.parameters");
        if(r.rows().size()!=r.count() || r.count()>5000 || r.groups().size()>1000)throw new IllegalArgumentException("management.error.large");
        return limiter.run(()->render(r,locale));
    }
    private String label(String key,Locale l){return messages.getMessage("management."+key,null,l);}
    private byte[] render(Result r,Locale locale){
        String font="Courier New";
        try(var book=new AccountingReportWorkbook(font)){
            book.headings(r.kind()==Kind.LOAN_REPAYMENT_TRANSACTIONS?"Arial":"Times New Roman",r.kind()==Kind.LOAN_REPAYMENT_TRANSACTIONS);
            int columns=r.columns().size();boolean portrait=r.kind()==Kind.LOAN_REPAYMENT_TRANSACTIONS;
            var sheet=book.sheet(label("sheet.report",locale),columns,!portrait,portrait?PrintSetup.LETTER_PAPERSIZE:r.isReferenceLayout()?PrintSetup.LEGAL_PAPERSIZE:PrintSetup.A3_PAPERSIZE);
            context(book,sheet,r,columns,locale);int contextEnd=sheet.getLastRowNum()+1;
            for(int i=0;i<columns;i++)sheet.setColumnWidth(i,(int)(Math.max(16,Math.min(40,r.columns().get(i).width()*20))*256));
            var monetary=new HashSet<Integer>();for(int i=0;i<columns;i++)if(r.columns().get(i).money())monetary.add(i);
            var grouped=new LinkedHashMap<String,List<Map<String,Object>>>();r.rows().forEach(row->grouped.computeIfAbsent(Objects.toString(row.get("groupName"),""),k->new ArrayList<>()).add(row));
            int firstHeader=-1;String month=null;
            for(var group:r.groups()){
                if(r.kind()==Kind.DISBURSED_LOANS_AUDIT && month!=null && !group.name().startsWith(month))total(book,sheet,r,r.getMonthlyTotals().get(month),label("monthTotal",locale),monetary);
                book.line(sheet,group.name(),columns,true);
                var rows=grouped.getOrDefault(group.name(),List.of());
                if(!rows.isEmpty()){
                    var facts=rows.getFirst();
                    var fields=switch(r.kind()){
                        case LOAN_REPAYMENT_TRANSACTIONS->List.of("clientNumber","name","startDate","loanId","product","disbursed","principalBalance");
                        case DISBURSED_LOANS_AUDIT->List.of("month","product","disbursementAccount");default->List.<String>of();};
                    for(String key:fields)book.line(sheet,field(key,locale)+": "+Objects.toString(facts.get(key),"—"),columns,false);
                }
                int header=book.headers(sheet,r.columns().stream().map(c->field(c.key(),locale)).toList());if(firstHeader<0)firstHeader=header;
                for(var row:rows){var values=new ArrayList<Object>();for(var c:r.columns()){
                    Object value=value(row.get(c.key()),c.key(),locale);
                    if(portrait && c.key().equals("date"))value=(value instanceof java.time.LocalDate d?d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")):Objects.toString(value,"—"))+"\n"+Objects.toString(value(row.get("entryKind"),"entryKind",locale),"")+" "+Objects.toString(row.get("reference"),"")+(row.get("reversesReference")==null?"":"\n"+field("reversesReference",locale)+": "+row.get("reversesReference"));
                    values.add(value);
                }book.row(sheet,values,monetary,false);}
                total(book,sheet,r,group.totals(),label("groupTotal",locale),monetary);month=group.name().substring(0,Math.min(7,group.name().length()));
            }
            if(r.kind()==Kind.DISBURSED_LOANS_AUDIT && month!=null)total(book,sheet,r,r.getMonthlyTotals().get(month),label("monthTotal",locale),monetary);
            if(firstHeader<0){firstHeader=book.headers(sheet,r.columns().stream().map(c->field(c.key(),locale)).toList());book.line(sheet,label("empty",locale),columns,false);}
            total(book,sheet,r,r.totals(),label("total",locale),monetary);
            sheet.createFreezePane(0,contextEnd);
            book.line(sheet,label("coverage."+r.kind(),locale),columns,false);book.line(sheet,label("fullTotals",locale),columns,false);
            if(r.kind()==Kind.PORTFOLIO_RISK)book.line(sheet,label("riskBasis",locale),columns,false);
            if(r.excluded()>0){book.line(sheet,label("exclusionBasis",locale),columns,false);book.line(sheet,label("missingNext",locale),columns,false);}
            // A flat typed sheet retains identifiers and correction evidence when users sort or filter.
            var keys=new ArrayList<>(r.columns().stream().map(Column::key).toList());
            var extra=new ArrayList<>(List.of("groupName","product"));
            if(r.kind()==Kind.LOAN_REPAYMENT_TRANSACTIONS)extra.addAll(List.of("clientNumber","name","loanId","startDate","disbursed","principalBalance","reference","entryKind","reversesReference"));
            if(r.kind()==Kind.DISBURSED_LOANS_AUDIT)extra.addAll(List.of("month","disbursementAccount"));
            for(var key:extra)if(!keys.contains(key))keys.add(key);
            var data=book.sheet(label("sheet.data",locale),keys.size(),true,PrintSetup.A3_PAPERSIZE);context(book,data,r,keys.size(),locale);
            int dataHeader=book.headers(data,keys.stream().map(k->field(k,locale)).toList());
            var dataMoney=new HashSet<Integer>();for(int i=0;i<keys.size();i++)if(monetary.contains(i) || Set.of("disbursed","principalBalance").contains(keys.get(i)))dataMoney.add(i);
            for(var row:r.rows())book.row(data,keys.stream().map(k->value(row.get(k),k,locale)).toList(),dataMoney,false);
            book.filter(data,dataHeader,keys.size());
            if(book.hasExactText()){book.line(sheet,label("exactAmounts",locale),columns,false);book.line(data,label("exactAmounts",locale),keys.size(),false);}
            return book.bytes();
        }
    }
    private void context(AccountingReportWorkbook b,Sheet s,Result r,int n,Locale l){
        b.title(s,label("title."+r.kind(),l).replace('\n',' '),n,r.kind()==Kind.LOAN_REPAYMENT_TRANSACTIONS?"Times New Roman":"Arial",r.kind()==Kind.DISBURSED_LOANS_AUDIT?11:r.kind()==Kind.LOAN_REPAYMENT_TRANSACTIONS?12:14,r.kind()!=Kind.EXPECTED_REPAYMENTS,r.kind()==Kind.DISBURSED_LOANS_AUDIT || r.kind()==Kind.LOAN_REPAYMENT_TRANSACTIONS);b.line(s,r.institution()+" | "+r.branch()+" | TZS",n,false);
        b.line(s,label("period",l)+": "+r.parameters().from()+" — "+r.parameters().through(),n,false);
        b.line(s,label("cutoff",l)+": "+r.parameters().cutoff(),n,false);b.line(s,label("excluded",l)+": "+r.excluded(),n,false);b.line(s,label("sheet.unknown",l),n,false);
        if(!r.clientSelection().isBlank())b.line(s,label("client",l)+": "+r.clientSelection(),n,false);
        if(!r.parameters().product().isBlank())b.line(s,label("productSearch",l)+": "+r.parameters().product(),n,false);
    }
    private String field(String key,Locale l){return label(switch(key){case "product"->"productSearch";case "month","startDate"->key;case "groupName"->"sheet.group";case "entryKind"->"sheet.entry";default->"field."+key;},l);}
    private Object value(Object v,String key,Locale l){if(v==null)return null;if(key.equals("frequency")&&Set.of("MONTHLY","WEEKLY").contains(v.toString()))return messages.getMessage("recording.frequency."+v,null,l);if(key.equals("entryKind"))return label("entry."+v,l);return v;}
    private void total(AccountingReportWorkbook b,Sheet s,Result r,Map<String,BigDecimal> totals,String title,Set<Integer> money){var values=new ArrayList<Object>();for(int i=0;i<r.columns().size();i++){var c=r.columns().get(i);values.add(i==0?title:r.getTotalFields().contains(c.key())?totals.get(c.key()):null);}b.row(s,values,money,true);}
}
