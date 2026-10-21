package com.sacco.mvp.accounting.payroll.service;

import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.service.ApplicationTimestamps;
import com.sacco.mvp.service.ReportExportLimiter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Employer-facing requests only, derived exclusively from the frozen schedule. */
@Service
@RequiredArgsConstructor
public class PayrollSpreadsheetService {
    private final MessageSource messages;
    private final ReportExportLimiter limiter;

    public byte[] render(Batch batch,List<Line> lines,Locale locale) {
        if(lines.size()>1000)throw new IllegalArgumentException("payroll.error.large");
        return limiter.run(()->build(batch,lines,locale));
    }
    private byte[] build(Batch b,List<Line> lines,Locale locale) {
        try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=book.createSheet(label("title",locale));sheet.setDisplayGridlines(false);
            var normal=book.createCellStyle();var font=book.createFont();font.setFontName("Arial");font.setFontHeightInPoints((short)11);
            normal.setFont(font);normal.setWrapText(true);normal.setVerticalAlignment(VerticalAlignment.TOP);
            var bold=book.createCellStyle();bold.cloneStyleFrom(normal);var boldFont=book.createFont();boldFont.setFontName("Arial");boldFont.setFontHeightInPoints((short)11);boldFont.setBold(true);bold.setFont(boldFont);
            var money=book.createCellStyle();money.cloneStyleFrom(normal);money.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
            var total=book.createCellStyle();total.cloneStyleFrom(money);total.setFont(boldFont);total.setBorderTop(BorderStyle.THIN);
            int n=0;
            n=text(sheet,n,label("title",locale),bold);
            n=text(sheet,n,b.institution()+" | "+b.branch(),normal);
            n=text(sheet,n,b.reference(),bold);
            n=text(sheet,n,label("employer",locale)+": "+b.employer(),normal);
            n=text(sheet,n,label("month",locale)+": "+b.month().toString().substring(0,7),normal);
            n=text(sheet,n,label("deductionDate",locale)+": "+ApplicationTimestamps.display(b.deductionDate()),normal);
            n=text(sheet,n,label("cutoff",locale)+": "+ApplicationTimestamps.display(b.cutoff()),normal);
            n=text(sheet,n,label("state",locale)+": "+label("state."+b.state(),locale),bold);
            if(b.state().equals("CANCELLED"))n=text(sheet,n,label("cancelledNotice",locale),bold);
            if(b.replacesId()!=null)n=text(sheet,n,label("replaces",locale)+": PAY-"+b.replacesId().toString().toUpperCase(Locale.ROOT),normal);
            n=text(sheet,n,label("requestOnly",locale),normal);
            n=text(sheet,n,label("snapshotNotice",locale),normal);
            int headerRow=n++;var header=sheet.createRow(headerRow);header.setHeightInPoints(32);
            var headings=List.of("employeeNumber","employeeName","deductionReference","requested");
            for(int c=0;c<4;c++){var cell=header.createCell(c);cell.setCellValue(label(headings.get(c),locale));cell.setCellStyle(bold);}
            for(var employee:PayrollPdfService.group(b.id(),lines)) {
                var row=sheet.createRow(n++);row.setHeightInPoints(Math.max(30,15*(float)Math.ceil(employee.clientName().length()/40.0)));
                var values=List.of(employee.employeeNumber(),employee.clientName(),employee.reference());
                for(int c=0;c<3;c++){var cell=row.createCell(c);cell.setCellValue(values.get(c));cell.setCellStyle(normal);}
                amount(row.createCell(3),employee.amount(),money);
            }
            sheet.setAutoFilter(new CellRangeAddress(headerRow,n-1,0,3));
            var footer=sheet.createRow(n);footer.setHeightInPoints(32);
            footer.createCell(0).setCellValue(label("total",locale));footer.getCell(0).setCellStyle(bold);
            footer.createCell(1).setCellValue(label("employees",locale)+": "+b.employees());footer.getCell(1).setCellStyle(bold);
            amount(footer.createCell(3),b.total(),total);
            int[] widths={26,44,40,28};for(int c=0;c<4;c++)sheet.setColumnWidth(c,widths[c]*256);
            sheet.createFreezePane(0,headerRow+1);sheet.setRepeatingRows(new CellRangeAddress(headerRow,headerRow,-1,-1));
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setFitWidth((short)1);sheet.getPrintSetup().setFitHeight((short)0);sheet.setFitToPage(true);sheet.setAutobreaks(true);
            book.setPrintArea(0,0,3,0,n);sheet.getFooter().setRight("&P / &N");
            book.write(output);return output.toByteArray();
        }catch(IOException failure){throw new IllegalStateException("Payroll spreadsheet generation failed",failure);}
    }
    private static int text(Sheet sheet,int n,String value,CellStyle style) {
        var row=sheet.createRow(n);row.setHeightInPoints(Math.max(18,15*(float)Math.ceil(value.length()/120.0)));
        var cell=row.createCell(0);cell.setCellValue(value);cell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(n,n,0,3));return n+1;
    }
    private static void amount(Cell cell,BigDecimal value,CellStyle style) {
        // Excel has 15 significant decimal digits. Preserve larger amounts exactly as text.
        if(value.precision()<=15)cell.setCellValue(value.doubleValue());else cell.setCellValue(value.toPlainString());
        cell.setCellStyle(style);
    }
    private String label(String key,Locale locale){return messages.getMessage("payroll."+key,null,locale);}
}
