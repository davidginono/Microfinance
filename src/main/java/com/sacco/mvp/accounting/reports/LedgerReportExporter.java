package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
    private record Table(String title,List<List<Object>> context,List<String> headers,List<List<Object>> rows) { }

    public Output trial(AppUserPrincipal actor,LedgerReportService.Parameters parameters,boolean wide,Format format,Locale locale) {
        return limiter.run(()->write(trialTable(reports.exportTrialBalance(actor,parameters,wide),locale),format));
    }
    public Output activity(AppUserPrincipal actor,UUID account,LedgerReportService.Parameters parameters,boolean wide,Format format,Locale locale) {
        return limiter.run(()->write(activityTable(reports.exportAccountActivity(actor,account,parameters,wide),locale),format));
    }
    private Table trialTable(LedgerReportService.TrialBalance source,Locale locale) {
        var context=context(source.scope(),source.parameters(),source.coverage(),locale);
        context.add(pair(text("accounting.policy.version",locale),source.policyVersion()));
        context.add(pair(text("financial.export.policyId",locale),source.policyId().toString()));
        var t=source.totals();
        context.add(pair(text("financial.export.openingDebit",locale),available(t.openingDebit(),locale)));
        context.add(pair(text("financial.export.openingCredit",locale),available(t.openingCredit(),locale)));
        context.add(pair(text("financial.export.movementDebit",locale),t.movementDebit()));
        context.add(pair(text("financial.export.movementCredit",locale),t.movementCredit()));
        context.add(pair(text("financial.export.closingDebit",locale),available(t.closingDebit(),locale)));
        context.add(pair(text("financial.export.closingCredit",locale),available(t.closingCredit(),locale)));
        var rows=new ArrayList<List<Object>>();
        for(var a:source.accounts())rows.add(List.of(a.code(),a.name(),available(a.opening(),locale),a.debit(),a.credit(),available(a.closing(),locale)));
        return new Table(text("financial.report.trial",locale),context,labels(locale,"accounting.code","accounting.name","financial.report.opening","financial.report.debit","financial.report.credit","financial.report.closing"),rows);
    }
    private Table activityTable(LedgerReportService.AccountActivityExport exported,Locale locale) {
        var source=exported.activity();
        var context=context(source.scope(),source.parameters(),source.coverage(),locale);
        context.add(pair(text("accounting.policy.version",locale),exported.policyVersion()));
        context.add(pair(text("financial.export.policyId",locale),exported.policyId().toString()));
        context.add(pair(text("accounting.code",locale),source.code()));context.add(pair(text("accounting.name",locale),source.name()));
        context.add(pair(text("financial.report.opening",locale),available(source.opening(),locale)));
        context.add(pair(text("financial.report.closing",locale),available(source.closing(),locale)));
        var rows=new ArrayList<List<Object>>();
        for(var a:source.movements())rows.add(List.of(a.journalId().toString(),a.effectiveDate().toString(),a.recordedAt().toString(),a.postedAt().toString(),
            text("accounting.source."+a.sourceType(),locale),safe(a.sourceReference()),safe(a.evidenceReference()),a.reversalOf()==null?"":a.reversalOf().toString(),a.debit(),a.credit()));
        return new Table(text("financial.report.activity",locale),context,labels(locale,"financial.export.journalId","financial.report.effective","financial.export.recordedAt","financial.export.postedAt","financial.report.source","financial.report.reference","financial.report.evidence","financial.export.reversalOf","financial.report.debit","financial.report.credit"),rows);
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
    private List<String> labels(Locale locale,String... keys){return Arrays.stream(keys).map(k->text(k,locale)).toList();}
    private Object available(BigDecimal amount,Locale locale){return amount==null?text("financial.report.unknown",locale):amount;}
    private static List<Object> pair(Object key,Object value){return List.of(key,value);}
    private static String safe(String value){return value==null?"":value;}
    private Output write(Table table,Format format) {
        if(format!=Format.PDF)throw new IllegalArgumentException("financial.export.format");
        try{return switch(format){
            case CSV->new Output(csv(table),"text/csv;charset=UTF-8","posted-ledger.csv");
            case XLSX->new Output(xlsx(table),"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","posted-ledger.xlsx");
            case PDF->new Output(pdf(table),"application/pdf","posted-ledger.pdf");};
        }catch(IOException e){throw new IllegalStateException("Posted-book export failed",e);}
    }
    private static byte[] csv(Table table) {
        var result=new StringBuilder("\uFEFF");appendCsv(result,List.of(table.title()));
        table.context().forEach(row->appendCsv(result,row));appendCsv(result,table.headers());table.rows().forEach(row->appendCsv(result,row));
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void appendCsv(StringBuilder out,List<?> row) {
        for(int i=0;i<row.size();i++){if(i>0)out.append(',');Object value=row.get(i);String s=value instanceof BigDecimal b?b.toPlainString():String.valueOf(value);
            if(value instanceof String){String trimmed=s.stripLeading();if(!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0)s="'"+s;}
            out.append('"').append(s.replace("\"","\"\"")).append('"');}out.append("\r\n");
    }
    private static byte[] xlsx(Table table)throws IOException {
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet(org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(table.title()));var amount=workbook.createCellStyle();amount.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            var header=workbook.createCellStyle();var font=workbook.createFont();font.setBold(true);header.setFont(font);
            int index=0;excelRow(sheet.createRow(index++),List.of(table.title()),amount,header);
            for(var row:table.context())excelRow(sheet.createRow(index++),row,amount,null);
            int headerIndex=index;excelRow(sheet.createRow(index++),table.headers(),amount,header);
            for(var row:table.rows())excelRow(sheet.createRow(index++),row,amount,null);
            sheet.createFreezePane(0,headerIndex+1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(headerIndex,index-1,0,table.headers().size()-1));
            for(int col=0;col<table.headers().size();col++)sheet.setColumnWidth(col,(col==1?40:24)*256);
            workbook.write(out);return out.toByteArray();
        }
    }
    private static void excelRow(Row row,List<?> values,CellStyle amount,CellStyle header) {
        for(int i=0;i<values.size();i++){var cell=row.createCell(i);Object value=values.get(i);
            if(value instanceof BigDecimal decimal && decimal.precision()<=15){cell.setCellValue(decimal.doubleValue());cell.setCellStyle(amount);}
            else if(value instanceof Number number && !(value instanceof BigDecimal)){cell.setCellValue(number.doubleValue());}
            else cell.setCellValue(value instanceof BigDecimal decimal?decimal.toPlainString():String.valueOf(value));
            if(header!=null)cell.setCellStyle(header);
        }
    }
    private static byte[] pdf(Table table)throws IOException {
        try(var document=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var out=new ByteArrayOutputStream()) {
            var font=PDType0Font.load(document,fontStream);
            // Column bands repeat the source identifier so no field is clipped or silently omitted.
            for(int start=1;start<table.headers().size();start+=4) {
                var columns=new ArrayList<Integer>();columns.add(0);for(int i=start;i<Math.min(start+4,table.headers().size());i++)columns.add(i);
                var pdfRows=new ArrayList<List<Object>>();pdfRows.add(List.of(table.title()));pdfRows.addAll(table.context());
                pdfRows.add(columns.stream().map(i->(Object)table.headers().get(i)).toList());
                for(var row:table.rows())pdfRows.add(columns.stream().map(row::get).toList());
                float y=0;PDPageContentStream stream=null;int pageNumber=0;
                try {
                    for(var row:pdfRows) {
                        float width=770f/row.size();var wrapped=new ArrayList<List<String>>();int lines=1;
                        for(Object value:row){String text=value instanceof BigDecimal b?b.toPlainString():String.valueOf(value);var pieces=wrap(text,font,9,width-12);wrapped.add(pieces);lines=Math.max(lines,pieces.size());}
                        for(int line=0;line<lines;line++) {
                        if(stream==null || y<45) {
                            if(stream!=null)stream.close();var page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));document.addPage(page);
                            stream=new PDPageContentStream(document,page);y=page.getMediaBox().getHeight()-36;pageNumber++;
                            draw(stream,font,9,36,y,table.title()+" • "+pageNumber);y-=20;
                            if(pageNumber>1){String scope=table.context().get(0).get(1)+" • "+table.context().get(3).get(1)+" / "+table.context().get(5).get(1)+" • "+table.context().get(6).get(1)+" / "+table.context().get(7).get(1)+" • "+table.context().get(8).get(1);for(String part:wrap(scope,font,8,770)){draw(stream,font,8,36,y,part);y-=12;}y-=4;
                                int headerLines=1;for(int c=0;c<columns.size();c++){var parts=wrap(table.headers().get(columns.get(c)),font,8,770f/columns.size()-12);headerLines=Math.max(headerLines,parts.size());for(int h=0;h<parts.size();h++)draw(stream,font,8,36+c*(770f/columns.size()),y-h*12,parts.get(h));}y-=headerLines*12+8;}
                            if(line>0){draw(stream,font,8,36,y,table.headers().getFirst()+": "+row.getFirst());y-=16;}
                        }
                        for(int col=0;col<wrapped.size();col++)if(line<wrapped.get(col).size())draw(stream,font,9,36+col*width,y,wrapped.get(col).get(line));
                        y-=13;
                        }
                        y-=9;
                    }
                }finally{if(stream!=null)stream.close();}
            }
            document.save(out);return out.toByteArray();
        }
    }
    private static void draw(PDPageContentStream stream,PDType0Font font,float size,float x,float y,String text)throws IOException {
        stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(text);stream.endText();
    }
    private static List<String> wrap(String source,PDType0Font font,float size,float width)throws IOException {
        var result=new ArrayList<String>();var line=new StringBuilder();
        for(int cp:source.codePoints().toArray()){String character=new String(Character.toChars(cp));if(Character.isISOControl(cp)){if(cp=='\n'){result.add(line.toString());line.setLength(0);}continue;}
            if(line.length()>0 && font.getStringWidth(line.toString()+character)*size/1000>width){result.add(line.toString());line.setLength(0);}line.append(character);}
        result.add(line.toString());return result;
    }
}
