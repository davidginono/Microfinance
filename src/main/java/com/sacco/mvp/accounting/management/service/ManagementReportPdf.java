package com.sacco.mvp.accounting.management.service;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.service.ReportExportLimiter;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import java.io.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class ManagementReportPdf {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;
    private final ApplicationClock clock;
    public String message(String key,Locale locale){return messages.getMessage("management."+key,null,locale);}
    public byte[] build(Result r,Locale locale){
        if(r.rows().size()!=r.count() || r.count()>5000)throw new IllegalArgumentException("management.error.large");
        return limiter.run(()->render(r,locale));
    }
    private byte[] render(Result r,Locale locale){
        try(var document=new PDDocument();var out=new ByteArrayOutputStream();var input=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream()){
            var font=PDType0Font.load(document,input);
            boolean asAt=r.kind()==Kind.PORTFOLIO_RISK || r.kind()==Kind.PORTFOLIO_ANALYSIS;
            var context=new ArrayList<>(List.of(r.institution()+" | "+r.branch(),message(asAt?"asAt":"period",locale)+": "+(asAt?"":com.sacco.mvp.service.ApplicationTimestamps.display(r.parameters().from())+" — ")+com.sacco.mvp.service.ApplicationTimestamps.display(r.parameters().through())+" | TZS",
                message("cutoff",locale)+": "+com.sacco.mvp.service.ApplicationTimestamps.display(r.parameters().cutoff()),message("excluded",locale)+": "+r.excluded()));
            if(r.isReferenceLayout()){
                String period=LoanReportReferenceText.documentPeriod(r.kind(),r.parameters(),locale,key->message(key,locale));
                String printed=LoanReportReferenceText.printed(r.kind(),clock.now(),locale,key->message(key,locale));
                context=new ArrayList<>(List.of(period,context.get(0)+" | TZS",printed));
                context.addAll(LoanReportReferenceText.headerDetails(r,locale,key->message(key,locale)));
            }
            if(r.kind()==Kind.CASHBOOK || r.kind()==Kind.DAILY_COLLECTIONS)context.add(message(r.kind()==Kind.DAILY_COLLECTIONS?"postingAccountRange":"accounts",locale)+": "+(r.parameters().startAccount()==null && r.parameters().endAccount()==null?message(r.kind()==Kind.DAILY_COLLECTIONS?"allPostingAccounts":"allAccounts",locale):r.accountSelection()));
            if(!r.isReferenceLayout()){
                if(!r.clientSelection().isBlank())context.add(message("client",locale)+": "+r.clientSelection());
                if(!r.parameters().product().isBlank())context.add(message("productSearch",locale)+": "+r.parameters().product());
            }
            try(var page=new AccountingPdfDocument(document,font,r.kind()!=Kind.LOAN_REPAYMENT_TRANSACTIONS,message("title."+r.kind(),locale),context,null,LoanReportReferenceText.style(r.kind()))){
                boolean audit=r.kind()==Kind.DISBURSED_LOANS_AUDIT;
                if(r.isReferenceLayout())page.sourcePageNumbers(message(r.kind()==Kind.LOAN_REPAYMENT_TRANSACTIONS?"pdf.page":"pdf.pageOf",locale));
                var columns=r.getDocumentColumns();
                float total=(float)columns.stream().mapToDouble(Column::width).sum();
                int numberColumn=audit?1:0;
                float[] widths=new float[columns.size()+numberColumn];boolean[] numeric=new boolean[widths.length];
                var labels=new ArrayList<String>();
                if(audit)labels.add(message("pdf.recordNumber",locale));
                for(int n=0;n<columns.size();n++){
                    var column=columns.get(n);
                    widths[n+numberColumn]=column.width()/total;
                    numeric[n+numberColumn]=column.money();
                    labels.add(LoanReportReferenceText.documentHeading(r.kind(),column.key(),key->message(key,locale)));
                }
                var groupFacts=r.getGroupFacts();
                var rowsByGroup=new LinkedHashMap<Object,List<Map<String,Object>>>();
                for(var row:r.rows())rowsByGroup.computeIfAbsent(row.get("groupName"),key->new ArrayList<>()).add(row);
                var monthlyTotals=audit?r.getMonthlyTotals():Map.<String,Map<String,BigDecimal>>of();
                var subtotals=r.getSubtotalLayout();
                String day=null,month=null;
                int recordNumber=0;
                var groups=r.groups();
                for(var group:groups){
                    if(audit && month!=null && !group.name().startsWith(month) && subtotals.periods().contains(month))page.row(totalRow(r,monthlyTotals.get(month),message("monthTotal",locale),locale),true);
                    if(r.kind()==Kind.DAILY_COLLECTIONS && day!=null && !group.name().startsWith(day) && subtotals.periods().contains(day))page.row(totalRow(r,r.getDailyTotals().get(day),message("dayTotal",locale),locale),true);
                    String activity=group.name().contains(" | ")?group.name().substring(group.name().indexOf(" | ")+3):"";
                    String groupTitle=Set.of("@loan","@general").contains(activity)?group.name().substring(0,group.name().indexOf(" | ")+3)+message(activity.equals("@loan")?"loanRepayments":"generalReceipts",locale):group.name();
                    page.endTable();
                    if(r.isReferenceLayout()){
                        sourceGroup(page,r,groupFacts.get(group.name()),locale);
                    }else page.section(com.sacco.mvp.service.ApplicationTimestamps.display(groupTitle));
                    page.columns(labels,widths,numeric);
                    for(var row:rowsByGroup.getOrDefault(group.name(),List.of())){
                        var cells=new ArrayList<String>();
                        if(audit)cells.add(Integer.toString(++recordNumber));
                        for(var column:columns)cells.add(cell(r,row,column,locale));
                        page.row(cells,false);
                    }
                    if(subtotals.groups().contains(group.name())){
                        if(audit)page.sourceTotalsGap();
                        page.row(totalRow(r,group.totals(),message("groupTotal",locale),locale),true);
                    }
                    day=group.name().substring(0,Math.min(10,group.name().length()));month=group.name().substring(0,Math.min(7,group.name().length()));
                }
                if(r.kind()==Kind.DAILY_COLLECTIONS && day!=null && subtotals.periods().contains(day))page.row(totalRow(r,r.getDailyTotals().get(day),message("dayTotal",locale),locale),true);
                if(audit && month!=null && subtotals.periods().contains(month))page.row(totalRow(r,monthlyTotals.get(month),message("monthTotal",locale),locale),true);
                if(groups.isEmpty()){
                    if(r.isReferenceLayout())sourceGroup(page,r,r.emptyGroupFacts(),locale);
                    page.columns(labels,widths,numeric);
                    page.text(message(r.getEmptyKey(),locale));
                }
                if(r.count()>0)page.row(totalRow(r,r.totals(),message("total",locale),locale),true);
                page.endTable();
                if(r.isReferenceLayout() && r.excluded()>0)page.text(message("excluded",locale)+": "+r.excluded());
                String noteKey=r.getNoteKey();
                if(!noteKey.isEmpty())page.text(message(noteKey,locale));
            }
            document.save(out);return out.toByteArray();
        }catch(IOException failure){throw new IllegalStateException("PDF generation failed",failure);}
    }
    private void sourceGroup(AccountingPdfDocument page,Result r,Map<String,Object> facts,Locale locale)throws IOException{
        switch(r.kind()){
            case LOAN_REPAYMENT_TRANSACTIONS -> page.sourceGroup(
                List.of(message("field.clientNumber",locale),message("field.name",locale),message("startDate",locale),
                    message("field.loanId",locale),message("pdf.loanType",locale),message("pdf.disbursedLoanAmount",locale),message("pdf.loanBalance",locale)),
                List.of(value(facts.get("clientNumber"),locale),value(facts.get("name"),locale),sourceValue(facts.get("startDate"),locale),
                    value(facts.get("loanId"),locale),value(facts.get("product"),locale),value(facts.get("disbursed"),locale),value(facts.get("principalBalance"),locale)));
            case DISBURSED_LOANS_AUDIT -> page.sourceGroup(
                List.of(message("month",locale),message("productSearch",locale),message("pdf.institutionName",locale),
                    message("pdf.branchName",locale),message("field.disbursementAccount",locale)),
                List.of(value(facts.get("month"),locale),value(facts.get("product"),locale),
                    r.institution(),r.branch(),value(facts.get("disbursementAccount"),locale)));
            case EXPECTED_REPAYMENTS -> page.sourceGroup(
                List.of(message("pdf.loanTypeName",locale)),
                List.of(value(facts.getOrDefault("groupName",facts.get("product")),locale)));
            default -> throw new IllegalArgumentException("Unsupported reference layout");
        }
    }
    private String cell(Result r,Map<String,Object> row,Column c,Locale locale){
        Object amount=row.get(c.key());
        String text=r.isReferenceLayout()?sourceValue(amount,locale):value(amount,locale);
        if(r.kind()==Kind.LOAN_REPAYMENT_TRANSACTIONS && c.key().equals("date")){
            if(row.get("entryKind")!=null)text+="\n"+message("entry."+row.get("entryKind"),locale)+" "+value(row.get("reference"),locale);
            if(row.get("reversesReference")!=null)text+="\n"+message("field.reversesReference",locale)+": "+value(row.get("reversesReference"),locale);
        }else if(c.key().equals("frequency") && Set.of("MONTHLY","WEEKLY").contains(Objects.toString(row.get(c.key()),"")))return messages.getMessage("recording.frequency."+row.get(c.key()),null,locale);
        else if(c.key().equals("transaction") && Set.of("PRINCIPAL","INTEREST","CHARGES").contains(Objects.toString(row.get("component"),"")))return message("component."+row.get("component"),locale)+" — "+text;
        return text;
    }
    private List<String> totalRow(Result r,Map<String,BigDecimal> totals,String title,Locale locale){
        var values=new ArrayList<String>();
        boolean audit=r.kind()==Kind.DISBURSED_LOANS_AUDIT;
        if(audit)values.add(title);
        var columns=r.getDocumentColumns();
        for(int n=0;n<columns.size();n++){
            var column=columns.get(n);
            values.add(n==0&&!audit?title:column.money()&&r.getTotalFields().contains(column.key())&&totals.containsKey(column.key())?value(totals.get(column.key()),locale):"");
        }
        return values;
    }
    private String sourceValue(Object value,Locale locale){
        if(value instanceof java.sql.Date date)value=date.toLocalDate();
        if(value instanceof LocalDate date)return LoanReportReferenceText.date(date,locale);
        return value(value,locale);
    }
    private String value(Object value,Locale locale){if(value==null)return "—";if(value instanceof BigDecimal n){var format=NumberFormat.getNumberInstance(locale);format.setMaximumFractionDigits(2);return format.format(n);}return com.sacco.mvp.service.ApplicationTimestamps.display(value);}
}
