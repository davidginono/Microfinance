package com.sacco.mvp.accounting.statements.service;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.service.StatementPresentation.*;
import com.sacco.mvp.accounting.statements.service.StatementPresentation.Cell;
import com.sacco.mvp.accounting.reports.AccountingPdfDocument;
import com.sacco.mvp.service.ReportExportLimiter;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import java.io.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class StatementExportService {
    private final StatementPresentation presentation;
    private final ReportExportLimiter limiter;
    private final ApplicationClock clock;
    public byte[] export(Snapshot snapshot,List<Document> documents,String format,Locale locale){
        return limiter.run(()->{try{return format.equals("PDF")?pdf(snapshot,documents,locale):excel(snapshot,documents,locale);}catch(IOException e){throw new IllegalStateException("Statement document could not be generated",e);}});
    }
    private List<String> context(Snapshot s,Document document,Locale locale){
        var result=new ArrayList<String>();result.add(s.institutionName()+" | "+(s.parameters().institutionWide()?label("fs.institutionScope",locale):s.branchName()));
        result.add(document.title().equals(label("fs.title.POSITION",locale))?label("fs.asAt",locale)+": "+s.parameters().through()+" | TZS":label("fs.period",locale)+": "+s.parameters().from()+" — "+s.parameters().through()+" | TZS");
        result.add(label("fs.previous",locale)+": "+s.parameters().previousFrom()+" — "+s.parameters().previousThrough());
        result.add(label("fs.generated",locale)+": "+clock.zoned(s.generatedAt()).format(java.time.format.DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm z",locale))+" | "+label("fs.provisional",locale));return result;
    }
    private byte[] pdf(Snapshot s,List<Document> documents,Locale locale)throws IOException{
        try(var pdf=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var bytes=new ByteArrayOutputStream()){
            var font=PDType0Font.load(pdf,fontStream);
            for(var doc:documents){
                var style=switch(doc.style()){case "trial"->AccountingPdfDocument.Style.TRIAL;case "equity"->AccountingPdfDocument.Style.EQUITY;default->AccountingPdfDocument.Style.STATEMENT;};
                try(var page=new AccountingPdfDocument(pdf,font,doc.columns().size()>5||doc.style().equals("equity"),doc.title().toUpperCase(locale),context(s,doc,locale),null,style)){
                    float[] weights=new float[doc.columns().size()];boolean[] numeric=new boolean[weights.length];for(int i=0;i<weights.length;i++){weights[i]=doc.columns().get(i).weight();numeric[i]=doc.columns().get(i).numeric();}
                    page.columns(doc.columns().stream().map(Column::label).toList(),weights,numeric);
                    if(doc.rows().isEmpty())page.text(label("fs.noRows",locale));
                    for(var row:doc.rows())if(row.heading())page.section(row.cells().stream().map(Cell::text).filter(t->!t.isBlank()).findFirst().orElse(""));else page.row(row.cells().stream().map(Cell::text).toList(),row.total());
                    page.endTable();page.text(label("fs.coverage",locale));
                    for(var check:s.checks())if(!check.key().equals("coverage"))page.text(label("fs.check."+check.key(),locale)+(check.difference()==null?"":" : "+check.difference().toPlainString()));
                    page.text(label("fs.snapshot",locale)+": "+s.id());
                }
            }
            pdf.save(bytes);return bytes.toByteArray();
        }
    }
    private byte[] excel(Snapshot s,List<Document> documents,Locale locale)throws IOException{
        try(var book=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()){
            for(var doc:documents){
                String name=doc.title().replaceAll("[\\\\/?*\\[\\]:]"," ");if(name.length()>28)name=name.substring(0,28);String base=name;int suffix=1;while(book.getSheet(name)!=null)name=base+suffix++;
                var sheet=book.createSheet(name);int count=doc.columns().size(),rowNo=0;
                var normal=book.createCellStyle();var font=book.createFont();font.setFontName(switch(doc.style()){case "trial"->"Times New Roman";case "equity"->"Courier New";default->"Arial";});font.setFontHeightInPoints((short)10);normal.setFont(font);normal.setWrapText(true);normal.setVerticalAlignment(VerticalAlignment.TOP);
                var money=book.createCellStyle();money.cloneStyleFrom(normal);money.setDataFormat(book.createDataFormat().getFormat("#,##0.00;(#,##0.00)"));money.setAlignment(HorizontalAlignment.RIGHT);
                var bold=book.createCellStyle();bold.cloneStyleFrom(normal);var boldFont=book.createFont();boldFont.setFontName(font.getFontName());boldFont.setFontHeightInPoints((short)10);boldFont.setBold(true);bold.setFont(boldFont);
                var total=book.createCellStyle();total.cloneStyleFrom(money);total.setFont(boldFont);total.setBorderBottom(BorderStyle.THIN);
                var title=book.createCellStyle();title.cloneStyleFrom(bold);title.setAlignment(HorizontalAlignment.CENTER);var titleFont=book.createFont();titleFont.setFontName(doc.style().equals("trial")?"Times New Roman":"Arial");titleFont.setFontHeightInPoints((short)15);titleFont.setBold(doc.style().equals("equity"));if(doc.style().equals("equity"))titleFont.setColor(IndexedColors.RED.getIndex());title.setFont(titleFont);
                rowNo=text(sheet,rowNo,doc.title().toUpperCase(locale),title,count);for(String line:context(s,doc,locale))rowNo=text(sheet,rowNo,line,normal,count);rowNo++;
                int headerRow=rowNo;var header=sheet.createRow(rowNo++);int headerLines=1;for(int i=0;i<count;i++){var cell=header.createCell(i);cell.setCellValue(doc.columns().get(i).label());cell.setCellStyle(bold);sheet.setColumnWidth(i,Math.min(255*256,(int)(doc.columns().get(i).weight()*170*256)));headerLines=Math.max(headerLines,(int)Math.ceil(doc.columns().get(i).label().length()/Math.max(10f,doc.columns().get(i).weight()*160)));}header.setHeightInPoints(Math.min(300,headerLines*14+8));
                for(var r:doc.rows()){
                    var row=sheet.createRow(rowNo++);int longest=1;
                    for(int i=0;i<r.cells().size();i++){var value=r.cells().get(i);var cell=row.createCell(i);if(value.amount()!=null&&value.amount().precision()<=15){cell.setCellValue(value.amount().doubleValue());cell.setCellStyle(r.total()?total:money);}else{cell.setCellValue(value.text());cell.setCellStyle(r.total()||r.heading()?bold:normal);}longest=Math.max(longest,(int)Math.ceil(value.text().length()/Math.max(10f,doc.columns().get(i).weight()*160)));}
                    row.setHeightInPoints(Math.min(300,Math.max(16,longest*13)));if(r.heading())row.setHeightInPoints(Math.max(27,row.getHeightInPoints()));
                }
                rowNo++;rowNo=text(sheet,rowNo,label("fs.coverage",locale),normal,count);
                for(var check:s.checks())if(!check.key().equals("coverage"))rowNo=text(sheet,rowNo,label("fs.check."+check.key(),locale)+(check.difference()==null?"":" : "+check.difference().toPlainString()),normal,count);
                text(sheet,rowNo,label("fs.snapshot",locale)+": "+s.id(),normal,count);
                sheet.createFreezePane(2,headerRow+1);sheet.setRepeatingRows(new CellRangeAddress(headerRow,headerRow,-1,-1));sheet.setDisplayGridlines(false);
                sheet.getPrintSetup().setLandscape(count>5||doc.style().equals("equity"));sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);sheet.setFitToPage(true);sheet.setAutobreaks(true);book.setPrintArea(book.getSheetIndex(sheet),0,count-1,0,sheet.getLastRowNum());sheet.getFooter().setRight("Page &P / &N");
            }
            book.write(bytes);return bytes.toByteArray();
        }
    }
    private int text(Sheet sheet,int number,String value,CellStyle style,int columns){var row=sheet.createRow(number);row.setHeightInPoints(Math.max(17,(float)Math.ceil(value.length()/135.0)*14));var cell=row.createCell(0);cell.setCellValue(value);cell.setCellStyle(style);if(columns>1)sheet.addMergedRegion(new CellRangeAddress(number,number,0,columns-1));return number+1;}
    private String label(String value,Locale locale){return presentation.label(value,locale);}
}
