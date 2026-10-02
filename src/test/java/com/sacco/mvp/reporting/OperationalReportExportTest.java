package com.sacco.mvp.reporting;

import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.sacco.mvp.reporting.OperationalReportDefinition.*;

class OperationalReportExportTest {
    private final OperationalReportExportService exporter;
    OperationalReportExportTest(){var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");exporter=new OperationalReportExportService(messages);}
    @Test void csvPreservesSignedCentsAndNeutralizesUserFormulaText(){
        String csv=new String(exporter.export(result(false),OperationalReportExportService.Format.CSV),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("\"-123.45\"").contains("'=HYPERLINK").contains("TZS");
        assertThat(OperationalReportExportService.quote("  +cmd")).isEqualTo("\"'  +cmd\"");
    }
    @Test void workbookKeepsLargeAmountsExactAndUserTextCannotBecomeFormula()throws Exception{
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(result(false),OperationalReportExportService.Format.XLSX)))){
            var sheet=book.getSheetAt(0);assertThat(sheet.getRow(8).getCell(0).getStringCellValue()).startsWith("=HYPERLINK");
            assertThat(sheet.getRow(8).getCell(2).getStringCellValue()).isEqualTo("9999999999999999.99");
            assertThat(sheet.getRow(8).getCell(3).getNumericCellValue()).isEqualTo(-123.45);
            assertThat(sheet.getRow(8).getCell(0).getCellType().name()).isEqualTo("STRING");
        }
    }
    @Test void pdfContainsCanonicalUnitsCoverageAndPageNumbers()throws Exception{
        try(var pdf=Loader.loadPDF(exporter.export(result(false),OperationalReportExportService.Format.PDF))){
            String text=new PDFTextStripper().getText(pdf);assertThat(text).contains("TZS","9999999999999999.99","-123.45","1 / ");
        }
    }
    @Test void aPartialPageCanNeverBeExportedAsACompleteReport(){assertThatThrownBy(()->exporter.export(result(true),OperationalReportExportService.Format.CSV)).hasMessage("report.error.largeExport");}
    private OperationalReportService.Result result(boolean truncated){
        var d=standard(Dataset.COLLECTIONS,"en");
        var columns=List.of(new Column(Field.LOAN_ID,"",130,true),new Column(Field.EFFECTIVE_DATE,"",130,true),new Column(Field.PRINCIPAL,"",180,true),new Column(Field.AMOUNT,"",130,true));
        d=new OperationalReportDefinition(1,d.dataset(),"Sample report","Approved source records only","en",true,columns,List.of(),List.of(),List.of(),List.of(Field.PRINCIPAL,Field.AMOUNT));
        d.validate();var row=Map.<String,Object>of("LOAN_ID","=HYPERLINK(\"test\")","EFFECTIVE_DATE",LocalDate.of(2026,10,1),"PRINCIPAL",new BigDecimal("9999999999999999.99"),"AMOUNT",new BigDecimal("-123.45"));
        return new OperationalReportService.Result(d,"I1","B1",LocalDate.of(2026,10,1),LocalDate.of(2026,10,2),OffsetDateTime.parse("2026-10-02T10:00:00+03:00"),truncated?30:1,3,List.of(row),Map.of("AMOUNT",new BigDecimal("-123.45")),0,25,truncated,"report.coverage.COLLECTIONS");
    }
}
