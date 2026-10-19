package com.sacco.mvp.accounting.payroll.service;

import com.sacco.mvp.accounting.payroll.dto.PayrollFeedbackDtos.Input;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Strict input contract: identifiers and ISO dates are text; formulas are never evaluated. */
@Component
public class PayrollFeedbackWorkbook {
    public static final int MAX_BYTES=2*1024*1024;
    public static final List<String> HEADERS=List.of("Batch reference","Employee number","Deduction reference","Status","Deducted TZS","Deduction date","Employer reference");
    public static class RowError extends IllegalArgumentException {
        private final int line;
        public RowError(String key,int line){super(key);this.line=line;}
        public int line(){return line;}
    }
    public List<Input> read(byte[] content) {
        check(content!=null && content.length>0 && content.length<=MAX_BYTES,"file");
        try {
            // Bound decompression before POI constructs any workbook objects.
            try(var zip=new ZipInputStream(new ByteArrayInputStream(content))) {
                int entries=0,total=0;var buffer=new byte[8192];java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    String name=entry.getName().toLowerCase(Locale.ROOT);
                    check(++entries<=128 && !name.contains("vbaproject") && !name.contains("externallinks/"),"file");
                    int size=0,n;while((n=zip.read(buffer))!=-1){size+=n;total+=n;check(size<=4*1024*1024 && total<=16*1024*1024,"file");}
                }
                check(entries>0,"file");
            }
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(content))) {
                check(book.getWorkbookType()==org.apache.poi.xssf.usermodel.XSSFWorkbookType.XLSX && book.getNumberOfSheets()==1 && book.getExternalLinksTable().isEmpty(),"file");
                var sheet=book.getSheetAt(0);check(sheet.getLastRowNum()>=1 && sheet.getLastRowNum()<=1000,"rows");
                var header=sheet.getRow(0);check(header!=null && header.getLastCellNum()==7,"headers");
                for(int c=0;c<7;c++)check(HEADERS.get(c).equals(text(header.getCell(c))),"headers");
                var rows=new ArrayList<Input>();var employees=new HashSet<String>();var references=new HashSet<String>();
                for(int n=1;n<=sheet.getLastRowNum();n++) {
                    try {
                        var row=sheet.getRow(n);check(row!=null && row.getLastCellNum()<=7,"row");
                        String batch=text(row.getCell(0)),employee=text(row.getCell(1)),reference=text(row.getCell(2)),status=text(row.getCell(3));
                        check(batch.matches("PAY-[A-Fa-f0-9-]{36}") && employee.matches("[A-Za-z0-9/._ -]{1,50}") && reference.matches("[A-Fa-f0-9-]{36}"),"row");
                        reference=UUID.fromString(reference).toString().toUpperCase(Locale.ROOT);
                        check(employees.add(employee) && references.add(reference),"duplicateRow");
                        check(Set.of("SUCCESS","FAILED").contains(status),"row");
                        var amount=money(row.getCell(4));String date=text(row.getCell(5));LocalDate day=date.isEmpty()?null:LocalDate.parse(date);
                        check(date.isEmpty() || date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),"row");
                        String evidence=text(row.getCell(6));check(!evidence.isBlank() && evidence.length()<=250,"row");
                        check(status.equals("SUCCESS")?amount.signum()>0 && day!=null:amount.signum()==0,"row");
                        rows.add(new Input(n+1,batch.toUpperCase(Locale.ROOT),employee,reference,status,amount,day,evidence));
                    }catch(IllegalArgumentException ex){throw new RowError("payroll.feedback.error.row",n+1);}
                }
                return List.copyOf(rows);
            }
        }catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("payroll.feedback.error.file",ex);}
    }
    private static String text(Cell c) {
        if(c==null || c.getCellType()==CellType.BLANK)return "";
        check(c.getCellType()==CellType.STRING,"row");String value=c.getStringCellValue().strip();
        check(value.length()<=250 && value.chars().noneMatch(ch->ch<32),"row");return value;
    }
    private static BigDecimal money(Cell c) {
        check(c!=null,"row");BigDecimal value;
        if(c.getCellType()==CellType.NUMERIC){check(!DateUtil.isCellDateFormatted(c),"row");value=BigDecimal.valueOf(c.getNumericCellValue());}
        else {String s=text(c);check(s.matches("[0-9]{1,16}(\\.[0-9]{1,2})?"),"row");value=new BigDecimal(s);}
        check(value.signum()>=0 && value.stripTrailingZeros().scale()<=2 && value.precision()-value.scale()<=16,"row");return value.setScale(2);
    }
    private static void check(boolean condition,String key){if(!condition)throw new IllegalArgumentException("payroll.feedback.error."+key);}
}
