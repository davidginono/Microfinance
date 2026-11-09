package com.sacco.mvp.accounting.reports;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Typed, bounded report output. Text is never interpreted as an Excel formula. */
public final class AccountingReportWorkbook implements AutoCloseable {
    private final XSSFWorkbook book=new XSSFWorkbook();
    private final CellStyle text,heading,money,date,integer,total,totalMoney;
    private boolean exactText;
    public AccountingReportWorkbook(String family) {
        text=style(family,false);heading=style(family,true);money=style(family,false);date=style(family,false);integer=style(family,false);
        total=style(family,true);totalMoney=style(family,true);
        money.setDataFormat(book.createDataFormat().getFormat("#,##0.00;[Red](#,##0.00)"));
        totalMoney.setDataFormat(money.getDataFormat());date.setDataFormat(book.createDataFormat().getFormat("dd/mm/yyyy"));
        integer.setDataFormat(book.createDataFormat().getFormat("0"));
        for(var s:List.of(heading,total,totalMoney))s.setBorderBottom(BorderStyle.THIN);
    }
    private CellStyle style(String family,boolean bold){var s=book.createCellStyle();var font=book.createFont();font.setFontName(family);font.setFontHeightInPoints((short)10);font.setBold(bold);s.setFont(font);s.setVerticalAlignment(VerticalAlignment.TOP);s.setWrapText(true);return s;}
    public Sheet sheet(String name,int columns,boolean landscape,short paper) {
        var s=book.createSheet(org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(name));s.setDisplayGridlines(false);s.setFitToPage(true);
        s.getPrintSetup().setLandscape(landscape);s.getPrintSetup().setPaperSize(paper);s.getPrintSetup().setFitWidth((short)1);s.getPrintSetup().setFitHeight((short)0);
        s.getFooter().setCenter("&P / &N");for(int i=0;i<columns;i++)s.setColumnWidth(i,20*256);return s;
    }
    public void line(Sheet s,String value,int columns,boolean bold){int n=s.getPhysicalNumberOfRows()==0?0:s.getLastRowNum()+1;var row=s.createRow(n);var c=row.createCell(0);c.setCellValue(value);c.setCellStyle(bold?heading:text);if(columns>1)s.addMergedRegion(new CellRangeAddress(n,n,0,columns-1));int capacity=Math.max(40,columns*18);row.setHeightInPoints(15*Math.max(1,(value.length()+capacity-1)/capacity));}
    public void headings(String family,boolean shaded){var font=book.createFont();font.setFontName(family);font.setFontHeightInPoints((short)10);font.setBold(true);heading.setFont(font);if(shaded){heading.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);}}
    public void title(Sheet s,String value,int columns){title(s,value,columns,"Arial",14,true,false);}
    public void title(Sheet s,String value,int columns,String family,int points,boolean bold,boolean underline){line(s,value,columns,true);var style=book.createCellStyle();style.cloneStyleFrom(text);var font=book.createFont();font.setFontName(family);font.setFontHeightInPoints((short)points);font.setBold(bold);if(underline)font.setUnderline(Font.U_SINGLE);style.setFont(font);style.setAlignment(HorizontalAlignment.CENTER);s.getRow(s.getLastRowNum()).getCell(0).setCellStyle(style);s.getRow(s.getLastRowNum()).setHeightInPoints(24);}
    public int headers(Sheet s,List<String> labels){int n=s.getPhysicalNumberOfRows()==0?0:s.getLastRowNum()+1;var r=s.createRow(n);for(int i=0;i<labels.size();i++){var c=r.createCell(i);c.setCellValue(labels.get(i));c.setCellStyle(heading);}r.setHeightInPoints(60);return n;}
    public void row(Sheet s,List<?> values,Set<Integer> monetary,boolean bold){int n=s.getLastRowNum()+1;var row=s.createRow(n);int lines=1;for(int i=0;i<values.size();i++){Object v=values.get(i);var c=row.createCell(i);c.setCellStyle(bold?(monetary.contains(i)?totalMoney:total):monetary.contains(i)?money:text);
        if(v instanceof BigDecimal amount){double d=amount.doubleValue();if(Double.isFinite(d) && amount.stripTrailingZeros().precision()<=15 && BigDecimal.valueOf(d).compareTo(amount)==0)c.setCellValue(d);else{c.setCellValue(amount.toPlainString());exactText=true;}}
        else if(v instanceof Number num){c.setCellValue(num.doubleValue());if(!monetary.contains(i)&&!bold)c.setCellStyle(integer);}
        else if(v instanceof LocalDate day){c.setCellValue(day);c.setCellStyle(date);}
        else if(v!=null){String str=v.toString();c.setCellValue(str);int width=Math.max(8,s.getColumnWidth(i)/256);lines=Math.max(lines,Arrays.stream(str.split("\n",-1)).mapToInt(part->Math.max(1,(part.length()+width-1)/width)).sum());}
    }row.setHeightInPoints(Math.min(409,15*lines));}
    public void filter(Sheet s,int header,int columns){s.createFreezePane(0,header+1);s.setRepeatingRows(new CellRangeAddress(header,header,-1,-1));if(s.getLastRowNum()>header)s.setAutoFilter(new CellRangeAddress(header,s.getLastRowNum(),0,columns-1));}
    public boolean hasExactText(){return exactText;}
    public byte[] bytes(){try(var out=new ByteArrayOutputStream()){book.write(out);return out.toByteArray();}catch(IOException e){throw new IllegalStateException("Spreadsheet generation failed",e);}}
    @Override public void close(){try{book.close();}catch(IOException e){throw new IllegalStateException("Spreadsheet close failed",e);}}
}
