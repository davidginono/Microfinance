package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.management.dto.ManagementReports;
import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.management.service.ManagementReportExcel;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LoanReportExcelTest {
    static ResourceBundleMessageSource messages(){var m=new ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");m.setFallbackToSystemLocale(false);return m;}
    Result result(Kind kind,int count){
        var rows=new ArrayList<Map<String,Object>>();var totals=new LinkedHashMap<String,BigDecimal>();
        for(int n=0;n<count;n++){
            var row=new LinkedHashMap<String,Object>();row.put("groupName","2026-10 | Business loans");row.put("product","Business loans");row.put("month","2026-10");row.put("disbursementAccount","Bank");
            row.put("clientNumber","00007");row.put("name","=HYPERLINK(\"bad\")");row.put("loanId","000031");row.put("startDate",ManagementReportTest.DAY);row.put("disbursed",new BigDecimal("1000.01"));row.put("principalBalance",new BigDecimal("800.00"));row.put("reference","RC-"+n);row.put("entryKind",n==0?"PAYMENT":"REVERSAL");row.put("reversesReference",n==0?null:"RC-0");
            for(var col:ManagementReports.columns(kind))if(col.money()){row.put(col.key(),n==0?new BigDecimal("100.01"):new BigDecimal("-0.01"));if(ManagementReports.totalFields(kind).contains(col.key()))totals.merge(col.key(),(BigDecimal)row.get(col.key()),BigDecimal::add);}else if(col.key().toLowerCase().contains("date")||col.key().equals("maturity"))row.put(col.key(),ManagementReportTest.DAY);else if(!row.containsKey(col.key()))row.put(col.key(),col.key().equals("frequency")?"MONTHLY":"REF-"+n);
            rows.add(row);
        }
        return new Result(kind,ManagementReportTest.parameters(2),"Synthetic institution","Branch B1",ManagementReports.columns(kind),rows,List.of(new Group("2026-10 | Business loans",count,totals)),totals,count,1,false,"","Client 00007");
    }
    @Test void sixLoanReportsExportAllRowsWithReferenceFieldOrderAndTypedSortableDataInBothLanguages()throws Exception{
        var m=messages();var export=new ManagementReportExcel(m,new ReportExportLimiter(1));
        for(var kind:Kind.values())if(ManagementReportExcel.supports(kind))for(var language:List.of("en","sw")){
            var l=Locale.forLanguageTag(language);var source=result(kind,31);byte[] bytes=export.build(source,l);
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
                assertThat(book.getNumberOfSheets()).isEqualTo(2);var report=book.getSheetAt(0);var data=book.getSheetAt(1);int header=data.getPaneInformation().getHorizontalSplitPosition()-1;
                assertThat(data.getLastRowNum()-header).isEqualTo(31);assertThat(data.getCTWorksheet().getAutoFilter().getRef()).endsWith(Integer.toString(data.getLastRowNum()+1));
                for(int i=0;i<source.columns().size();i++)assertThat(data.getRow(header).getCell(i).getStringCellValue()).isEqualTo(m.getMessage("management.field."+source.columns().get(i).key(),null,l));
                int name=column(data,header,m.getMessage("management.field.name",null,l));assertThat(data.getRow(header+1).getCell(name).getCellType()).isEqualTo(CellType.STRING);assertThat(data.getRow(header+1).getCell(name).getStringCellValue()).startsWith("=HYPERLINK");
                int client=column(data,header,m.getMessage("management.field.clientNumber",null,l));assertThat(data.getRow(header+1).getCell(client).getStringCellValue()).isEqualTo("00007");
                int money=source.columns().indexOf(source.columns().stream().filter(Column::money).findFirst().orElseThrow());assertThat(data.getRow(header+1).getCell(money).getNumericCellValue()).isEqualTo(100.01);assertThat(data.getRow(header+2).getCell(money).getNumericCellValue()).isEqualTo(-.01);
                for(var row:data)for(var cell:row)assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
                if(kind==Kind.LOAN_REPAYMENT_TRANSACTIONS){assertThat(DateUtil.isCellDateFormatted(data.getRow(header+1).getCell(0))).isTrue();int correction=column(data,header,m.getMessage("management.field.reversesReference",null,l));assertThat(data.getRow(header+2).getCell(correction).getStringCellValue()).isEqualTo("RC-0");}
                var expected=source.totals();var formatter=new DataFormatter();for(var row:report)if(formatter.formatCellValue(row.getCell(0)).equals(m.getMessage("management.total",null,l)))for(int i=1;i<source.columns().size();i++){var key=source.columns().get(i).key();if(source.getTotalFields().contains(key))assertThat(row.getCell(i).getNumericCellValue()).isEqualTo(expected.get(key).doubleValue());else assertThat(row.getCell(i).getCellType()).isEqualTo(CellType.BLANK);}
                assertThat(report.getPrintSetup().getLandscape()).isEqualTo(kind!=Kind.LOAN_REPAYMENT_TRANSACTIONS);
                assertThat(book.getFontAt(data.getRow(header+1).getCell(money).getCellStyle().getFontIndex()).getFontName()).isEqualTo("Courier New");
            }
            if(Boolean.getBoolean("microfinance.management.samples")){var folder=java.nio.file.Path.of("target/management-reference-previews");java.nio.file.Files.createDirectories(folder);java.nio.file.Files.write(folder.resolve(kind+"-"+language+".xlsx"),bytes);}
        }
    }
    @Test void unknownAndLargeAmountsRemainUnknownAndExactAndPartialExportsAreRejected()throws Exception{
        var source=result(Kind.EXPECTED_REPAYMENTS,1);source.rows().getFirst().put("principalDue",null);source.rows().getFirst().put("expectedTotal",new BigDecimal("99999999999999.99"));
        var export=new ManagementReportExcel(messages(),new ReportExportLimiter(1));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(export.build(source,Locale.ENGLISH)))){var data=book.getSheetAt(1);int header=data.getPaneInformation().getHorizontalSplitPosition()-1;assertThat(data.getRow(header+1).getCell(6).getCellType()).isEqualTo(CellType.BLANK);assertThat(data.getRow(header+1).getCell(8).getStringCellValue()).isEqualTo("99999999999999.99");}
        var partial=new Result(source.kind(),source.parameters(),source.institution(),source.branch(),source.columns(),source.rows(),source.groups(),source.totals(),2,1,true,"","");assertThatThrownBy(()->export.build(partial,Locale.ENGLISH)).hasMessage("management.error.large");
        var oversized=new Result(source.kind(),source.parameters(),source.institution(),source.branch(),source.columns(),Collections.nCopies(5001,source.rows().getFirst()),source.groups(),source.totals(),5001,0,false,"","");assertThatThrownBy(()->export.build(oversized,Locale.ENGLISH)).hasMessage("management.error.large");
        for(var kind:List.of(Kind.CASHBOOK,Kind.DAILY_COLLECTIONS))assertThatThrownBy(()->export.build(result(kind,1),Locale.ENGLISH)).hasMessage("management.error.parameters");
    }
    @Test void portfolioWorkbookKeepsUnknownRatiosAndInstitutionTotalsWithoutLoanDetails()throws Exception{
        var source=result(Kind.PORTFOLIO_RISK,1);var s=new LoanPortfolioReportRepository.Summary(1,1,new BigDecimal("100.01"),BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO);
        var export=new LoanPortfolioReportExcel(messages(),new ReportExportLimiter(1));
        for(boolean wide:List.of(false,true))for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
            var r=new LoanPortfolioReportService.Report(new LoanPortfolioReportService.Scope("I1","B1",wide),new LoanPortfolioReportService.Parameters(source.parameters().from(),source.parameters().through(),source.parameters().cutoff(),0),s,new LoanPortfolioReportService.Ratios(null,null,null),new LoanPortfolioReportService.Coverage(1,0,1),wide?List.of():List.of(new LoanPortfolioReportRepository.SourceRow(UUID.randomUUID(),"000031",null,null,null,ManagementReportTest.DAY,false)),List.of(),List.of(),List.of(new LoanPortfolioReportRepository.BranchPerformance("B1",s)),false,"TEST","REFERENCE",Map.of());
            byte[] bytes=export.build(r,locale);try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(book.getNumberOfSheets()).isEqualTo(2);var report=book.getSheetAt(0);var data=book.getSheetAt(1);int header=data.getPaneInformation().getHorizontalSplitPosition()-1;assertThat(data.getRow(header+1).getCell(0).getStringCellValue()).isEqualTo(wide?"B1":"000031");var formatter=new DataFormatter();for(var row:report)if(formatter.formatCellValue(row.getCell(0)).equals(messages().getMessage("financial.portfolio.par30",null,locale)))assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.BLANK);}
        }
    }
    static int column(Sheet s,int header,String label){for(var cell:s.getRow(header))if(cell.getStringCellValue().equals(label))return cell.getColumnIndex();throw new AssertionError(label);}
}
