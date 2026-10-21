package com.sacco.mvp.accounting.payroll;

import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.accounting.payroll.service.*;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PayrollSpreadsheetTest {
    final ResourceBundleMessageSource messages=new ResourceBundleMessageSource();
    PayrollSpreadsheetTest(){messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");}
    PayrollSpreadsheetService exporter(){return new PayrollSpreadsheetService(messages,new ReportExportLimiter(1));}
    Batch batch(UUID id,String state,BigDecimal total,long employees){return new Batch(id,"Frozen Employer",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31),false,OffsetDateTime.parse("2026-10-19T12:00:00+03:00"),"SECRET_ACTOR","Frozen Institution","Frozen Branch","SECRET_HASH",null,state,total,employees,1);}
    Line line(UUID client,String employee,String name,String amount,String issue){return new Line(UUID.randomUUID(),UUID.randomUUID(),client,employee,name,"SECRET_CLIENT","SECRET_LOAN",new BigDecimal(amount),BigDecimal.ZERO,new BigDecimal(amount),issue);}
    String text(Sheet sheet){var result=new StringBuilder();var formatter=new DataFormatter(Locale.ENGLISH);for(var row:sheet)for(var cell:row)result.append(formatter.formatCellValue(cell)).append('\n');return result.toString();}

    @Test void bilingualFrozenScheduleGroupsLoansPreservesTextAndOmitsExcludedAndPrivateData()throws Exception {
        var id=UUID.randomUUID();var client=UUID.randomUUID();var lines=List.of(line(client,"000007","=HYPERLINK(\"bad\")","100.01",""),line(client,"000007","=HYPERLINK(\"bad\")","20.02",""),line(UUID.randomUUID(),"EXCLUDED","Excluded Employee","0.00","expired"));
        for(var language:List.of("en","sw")) {
            var locale=Locale.forLanguageTag(language);var b=batch(id,"FROZEN",new BigDecimal("120.03"),1);
            var bytes=exporter().render(b,lines,locale);
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertThat(book.getNumberOfSheets()).isEqualTo(1);assertThat(book.getExternalLinksTable()).isEmpty();
                var sheet=book.getSheetAt(0);var header=sheet.getPaneInformation().getHorizontalSplitPosition()-1;
                var row=sheet.getRow(header+1);assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);assertThat(row.getCell(0).getStringCellValue()).isEqualTo("000007");
                assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.STRING);assertThat(row.getCell(1).getStringCellValue()).startsWith("=HYPERLINK");
                assertThat(row.getCell(2).getStringCellValue()).isEqualTo(PayrollPdfService.group(id,lines).getFirst().reference());
                assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(120.03);
                assertThat(row.getCell(3).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
                assertThat(sheet.getLastRowNum()).isEqualTo(header+2);assertThat(sheet.getRow(header+2).getCell(3).getNumericCellValue()).isEqualTo(120.03);
                assertThat(text(sheet)).contains(b.reference(),"Frozen Employer","Frozen Institution","Frozen Branch","2026-10","31/10/2026",messages.getMessage("payroll.requestOnly",null,locale),messages.getMessage("payroll.snapshotNotice",null,locale));
                assertThat(text(sheet)).doesNotContain("SECRET_","EXCLUDED","Excluded Employee");
                assertThat(sheet.getRepeatingRows().getFirstRow()).isEqualTo(header);assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A"+(header+1)+":D"+(header+2));
                for(var r:sheet)for(var cell:r)assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
            }
        }
    }
    @Test void preservesLargeDecimalAmountsAndMarksCancelledHistoricalCopies()throws Exception {
        var id=UUID.randomUUID();var amount=new BigDecimal("9999999999999999.99");
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter().render(batch(id,"CANCELLED",amount,1),List.of(line(UUID.randomUUID(),"+00001","Employee",amount.toPlainString(),"")),Locale.ENGLISH)))) {
            var sheet=book.getSheetAt(0);int first=sheet.getPaneInformation().getHorizontalSplitPosition();
            assertThat(sheet.getRow(first).getCell(3).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(first).getCell(3).getStringCellValue()).isEqualTo(amount.toPlainString());
            assertThat(sheet.getRow(first+1).getCell(3).getStringCellValue()).isEqualTo(amount.toPlainString());
            assertThat(text(sheet)).contains(messages.getMessage("payroll.cancelledNotice",null,Locale.ENGLISH));
        }
    }
    @Test void includesAllThousandInstructionsAndRejectsOversizedExports()throws Exception {
        var id=UUID.randomUUID();var lines=new ArrayList<Line>();for(int n=0;n<1000;n++)lines.add(line(UUID.randomUUID(),"000"+n,"Employee "+n,"0.01",""));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter().render(batch(id,"SENT",new BigDecimal("10.00"),1000),lines,Locale.ENGLISH)))) {
            var sheet=book.getSheetAt(0);int first=sheet.getPaneInformation().getHorizontalSplitPosition();
            assertThat(sheet.getRow(first+999).getCell(0).getStringCellValue()).isEqualTo("000999");assertThat(sheet.getLastRowNum()).isEqualTo(first+1000);
        }
        lines.add(lines.getFirst());assertThatThrownBy(()->exporter().render(batch(id,"FROZEN",BigDecimal.TEN,1000),lines,Locale.ENGLISH)).hasMessage("payroll.error.large");
    }
}
