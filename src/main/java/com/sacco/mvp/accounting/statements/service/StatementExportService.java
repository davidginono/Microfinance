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
    public byte[] export(Snapshot snapshot,Document doc,String format,Locale locale){
        return limiter.run(()->{try{return format.equals("PDF")?pdf(snapshot,doc,locale):excel(snapshot,doc,locale);}catch(IOException e){throw new IllegalStateException("Statement document could not be generated",e);}});
    }
    private List<String> context(Snapshot s,Document document,Locale locale){
        if(document.style().equals("equity"))return List.of(label("fs.equity.forPeriod",locale)+"       "+presentation.equityPeriod(s,locale),s.institutionName()+" | "+(s.parameters().institutionWide()?label("fs.institutionScope",locale):s.branchName())+" | TZS");
        var result=new ArrayList<String>();result.add(s.institutionName()+" | "+(s.parameters().institutionWide()?label("fs.institutionScope",locale):s.branchName()));
        result.add(document.asAt()?label("fs.asAt",locale)+": "+com.sacco.mvp.service.ApplicationTimestamps.date(s.parameters().through())+" | TZS":label("fs.period",locale)+": "+com.sacco.mvp.service.ApplicationTimestamps.date(s.parameters().from())+" — "+com.sacco.mvp.service.ApplicationTimestamps.date(s.parameters().through())+" | TZS");
        result.add(label("fs.previous",locale)+": "+com.sacco.mvp.service.ApplicationTimestamps.date(s.parameters().previousFrom())+" — "+com.sacco.mvp.service.ApplicationTimestamps.date(s.parameters().previousThrough()));
        result.add(label("fs.generated",locale)+": "+clock.zoned(s.generatedAt()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm z",locale)));return result;
    }
    private byte[] pdf(Snapshot s,Document doc,Locale locale)throws IOException{
        try(var pdf=new PDDocument();var fontStream=new ClassPathResource("report-fonts/NotoSans-Regular.ttf").getInputStream();var bytes=new ByteArrayOutputStream()){
            var font=PDType0Font.load(pdf,fontStream);
                var style=switch(doc.style()){case "trial"->AccountingPdfDocument.Style.TRIAL;case "equity"->AccountingPdfDocument.Style.EQUITY;default->AccountingPdfDocument.Style.STATEMENT;};
                try(var page=new AccountingPdfDocument(pdf,font,doc.columns().size()>5||doc.style().equals("equity"),(doc.style().equals("equity")?doc.title():doc.title().toUpperCase(locale)),context(s,doc,locale),null,style)){
                    float[] weights=new float[doc.columns().size()];boolean[] numeric=new boolean[weights.length];for(int i=0;i<weights.length;i++){weights[i]=doc.columns().get(i).weight();numeric[i]=doc.columns().get(i).numeric();}
                    page.columns(doc.columns().stream().map(Column::label).toList(),weights,numeric);
                    if(doc.rows().isEmpty())page.text(label("fs.noRows",locale));
                    for(var row:doc.rows())if(row.heading())page.section(row.cells().stream().map(Cell::text).filter(t->!t.isBlank()).findFirst().orElse(""));else page.row(row.cells().stream().map(Cell::text).toList(),row.total());
                    page.endTable();if(doc.style().equals("equity"))page.text(context(s,doc,locale).get(1));page.text(label("fs.outputNotice",locale));
                }
            pdf.save(bytes);return bytes.toByteArray();
        }
    }
    private byte[] excel(Snapshot s,Document doc,Locale locale)throws IOException{
        try(var book=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()){
                String name=doc.title().replaceAll("[\\\\/?*\\[\\]:]"," ");if(name.length()>28)name=name.substring(0,28);
                var sheet=book.createSheet(name);boolean equity=doc.style().equals("equity");int count=doc.columns().size(),rowNo=0;
                var normal=book.createCellStyle();var font=book.createFont();font.setFontName(switch(doc.style()){case "trial"->"Times New Roman";case "equity"->"Courier New";default->"Arial";});font.setFontHeightInPoints((short)10);normal.setFont(font);normal.setWrapText(true);normal.setVerticalAlignment(VerticalAlignment.TOP);
                var money=book.createCellStyle();money.cloneStyleFrom(normal);money.setDataFormat(book.createDataFormat().getFormat(equity?"#,##0.00;(#,##0.00);\"\"":"#,##0.00;(#,##0.00)"));money.setAlignment(HorizontalAlignment.RIGHT);
                var bold=book.createCellStyle();bold.cloneStyleFrom(normal);var boldFont=book.createFont();boldFont.setFontName(font.getFontName());boldFont.setFontHeightInPoints((short)10);boldFont.setBold(true);bold.setFont(boldFont);
                var total=book.createCellStyle();total.cloneStyleFrom(money);total.setFont(boldFont);total.setBorderBottom(BorderStyle.THIN);if(equity){var totalFont=book.createFont();totalFont.setFontName(font.getFontName());totalFont.setFontHeightInPoints((short)10);totalFont.setBold(true);totalFont.setUnderline(Font.U_SINGLE);total.setFont(totalFont);}
                var title=book.createCellStyle();title.cloneStyleFrom(bold);title.setAlignment(HorizontalAlignment.CENTER);var titleFont=book.createFont();titleFont.setFontName(doc.style().equals("trial")?"Times New Roman":"Arial");titleFont.setFontHeightInPoints((short)15);titleFont.setBold(doc.style().equals("equity"));if(equity)titleFont.setColor(IndexedColors.RED.getIndex());title.setFont(titleFont);
                if(equity){
                    normal.setWrapText(false);bold.setWrapText(false);
                    rowNo=text(sheet,rowNo,doc.title(),title,Math.max(2,(count+1)/2));sheet.getRow(0).setHeightInPoints(28);
                    rowNo=text(sheet,rowNo,context(s,doc,locale).getFirst(),bold,1);
                    sheet.createRow(rowNo++).setHeightInPoints(28);
                }else{rowNo=text(sheet,rowNo,doc.title().toUpperCase(locale),title,count);for(String line:context(s,doc,locale))rowNo=text(sheet,rowNo,line,normal,count);}
                rowNo++;
                if(equity){
                    var tableNormal=book.createCellStyle();tableNormal.cloneStyleFrom(normal);tableNormal.setWrapText(true);normal=tableNormal;
                    var tableBold=book.createCellStyle();tableBold.cloneStyleFrom(bold);tableBold.setWrapText(true);bold=tableBold;
                }
                if(equity)for(var style:List.of(normal,money,bold,total)){
                    style.setBorderTop(BorderStyle.THIN);style.setBorderBottom(BorderStyle.THIN);style.setBorderLeft(BorderStyle.THIN);style.setBorderRight(BorderStyle.THIN);
                    style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
                }
                int headerRow=rowNo;var header=sheet.createRow(rowNo++);int headerLines=1;for(int i=0;i<count;i++){var cell=header.createCell(i);cell.setCellValue(doc.columns().get(i).label());cell.setCellStyle(bold);sheet.setColumnWidth(i,Math.min(255*256,(int)(doc.columns().get(i).weight()*170*256)));float charactersPerLine=Math.max(10f,doc.columns().get(i).weight()*160);headerLines=Math.max(headerLines,doc.columns().get(i).label().lines().mapToInt(line->Math.max(1,(int)Math.ceil(line.length()/charactersPerLine))).sum());}header.setHeightInPoints(Math.min(300,headerLines*14+(equity?32:8)));
                for(var r:doc.rows()){
                    var row=sheet.createRow(rowNo++);int longest=1;
                    for(int i=0;i<r.cells().size();i++){var value=r.cells().get(i);var cell=row.createCell(i);if(value.amount()!=null&&value.amount().precision()<=15){cell.setCellValue(value.amount().doubleValue());cell.setCellStyle(r.total()?total:money);}else{cell.setCellValue(value.text());cell.setCellStyle(r.total()||r.heading()||equity&&i==0?bold:normal);}longest=Math.max(longest,(int)Math.ceil(value.text().length()/Math.max(10f,doc.columns().get(i).weight()*160)));}
                    row.setHeightInPoints(Math.min(300,Math.max(16,longest*13)));if(r.heading())row.setHeightInPoints(Math.max(27,row.getHeightInPoints()));
                }
                rowNo++;
                if(equity){
                    var footer=book.createCellStyle();footer.setFont(font);footer.setWrapText(false);
                    rowNo=text(sheet,rowNo,context(s,doc,locale).get(1),footer,1);text(sheet,rowNo,label("fs.outputNotice",locale),footer,1);
                }else text(sheet,rowNo,label("fs.outputNotice",locale),normal,count);
                sheet.createFreezePane(equity?1:2,headerRow+1);sheet.setRepeatingRows(new CellRangeAddress(headerRow,headerRow,-1,-1));sheet.setDisplayGridlines(false);
                sheet.getPrintSetup().setLandscape(count>5||doc.style().equals("equity"));sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);sheet.setFitToPage(true);sheet.setAutobreaks(true);book.setPrintArea(book.getSheetIndex(sheet),0,count-1,0,sheet.getLastRowNum());sheet.getFooter().setRight("Page &P / &N");
            book.write(bytes);return bytes.toByteArray();
        }
    }
    private int text(Sheet sheet,int number,String value,CellStyle style,int columns){var row=sheet.createRow(number);row.setHeightInPoints(Math.max(17,(float)Math.ceil(value.length()/135.0)*14));var cell=row.createCell(0);cell.setCellValue(value);cell.setCellStyle(style);if(columns>1)sheet.addMergedRegion(new CellRangeAddress(number,number,0,columns-1));return number+1;}
    private String label(String value,Locale locale){return presentation.label(value,locale);}
}
