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
            var sheet=book.getSheetAt(0);
            var data=java.util.stream.IntStream.rangeClosed(0,sheet.getLastRowNum()).mapToObj(sheet::getRow)
                .filter(row->row!=null && row.getCell(0)!=null && row.getCell(0).getCellType()==org.apache.poi.ss.usermodel.CellType.STRING
                    && row.getCell(0).getStringCellValue().startsWith("=HYPERLINK")).findFirst().orElseThrow();
            assertThat(data.getCell(2).getStringCellValue()).isEqualTo("9999999999999999.99");
            assertThat(data.getCell(3).getNumericCellValue()).isEqualTo(-123.45);
            assertThat(data.getCell(0).getCellType().name()).isEqualTo("STRING");
            assertThat(data.getCell(1).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026,10,1));
        }
    }
    @Test void pdfContainsCanonicalUnitsCoverageAndPageNumbers()throws Exception{
        try(var pdf=Loader.loadPDF(exporter.export(result(false),OperationalReportExportService.Format.PDF))){
            String text=new PDFTextStripper().getText(pdf);assertThat(text).contains("TZS","9999999999999999.99","-123.45","1 / ","Montréal");
            assertThat(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImage(0).getWidth()).isGreaterThan(500);
            if(System.getenv("MICROFINANCE_REPORT_PREVIEW_IMAGE")!=null)javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,120),"png",new java.io.File(System.getenv("MICROFINANCE_REPORT_PREVIEW_IMAGE")));
        }
    }
    @Test void aPartialPageCanNeverBeExportedAsACompleteReport(){assertThatThrownBy(()->exporter.export(result(true),OperationalReportExportService.Format.CSV)).hasMessage("report.error.largeExport");}
    @Test void frozenOwnedLogoIsEmbeddedWithoutAnyRemoteFetch()throws Exception{
        var image=new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();graphics.setColor(java.awt.Color.GREEN);graphics.fillRect(0,0,16,16);graphics.dispose();
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        var r=result(false);var branded=new OperationalReportService.Result(r.definition(),r.institution(),r.branch(),r.from(),r.through(),r.recordedCutoff(),r.rowsInScope(),r.untrackedLoans(),r.rows(),r.totals(),r.page(),r.pageSize(),r.truncated(),r.coverageKey(),new OperationalReportService.Branding(bytes.toByteArray(),"image/png","synthetic-test-hash"));
        try(var pdf=Loader.loadPDF(exporter.export(branded,OperationalReportExportService.Format.PDF))){assertThat(pdf.getPage(0).getResources().getXObjectNames()).isNotEmpty();}
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(branded,OperationalReportExportService.Format.XLSX)))){assertThat(book.getAllPictures()).hasSize(1);assertThat(book.getAllPictures().getFirst().getData()).containsExactly(bytes.toByteArray());}
    }
    @Test void kiswahiliLabelsAndLongCustomAliasesRetainMoneyMeaningAcrossFormats()throws Exception{
        var r=result(false);var original=r.definition();
        var columns=new java.util.ArrayList<>(original.columns());columns.set(2,new Column(Field.PRINCIPAL,"Mtaji uliotolewa kwa biashara ya mteja Montréal",180,true));
        var d=new OperationalReportDefinition(1,original.dataset(),original.title(),original.footer(),"sw",false,columns,original.filters(),original.sorts(),original.groups(),original.totals());d.validate();
        var localized=new OperationalReportService.Result(d,r.institution(),r.branch(),r.from(),r.through(),r.recordedCutoff(),r.rowsInScope(),r.untrackedLoans(),r.rows(),r.totals(),r.page(),r.pageSize(),r.truncated(),r.coverageKey());
        assertThat(new String(exporter.export(localized,OperationalReportExportService.Format.CSV),java.nio.charset.StandardCharsets.UTF_8)).contains("Makusanyo na marejesho","Namba ya mkopo","TZS");
        try(var pdf=Loader.loadPDF(exporter.export(localized,OperationalReportExportService.Format.PDF))){assertThat(new PDFTextStripper().getText(pdf)).contains("Makusanyo na marejesho","Namba ya mkopo","TZS","Montréal");}
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(localized,OperationalReportExportService.Format.XLSX)))){
            var sheet=book.getSheetAt(0);
            assertThat(java.util.stream.IntStream.rangeClosed(0,sheet.getLastRowNum()).mapToObj(sheet::getRow)
                .filter(row->row!=null && row.getCell(2)!=null && row.getCell(2).getCellType()==org.apache.poi.ss.usermodel.CellType.STRING)
                .map(row->row.getCell(2).getStringCellValue()).toList()).anySatisfy(label->assertThat(label).contains("TZS","Mtaji uliotolewa"));
        }
    }
    private OperationalReportService.Result result(boolean truncated){
        var d=standard(Dataset.COLLECTIONS,"en");
        var columns=List.of(new Column(Field.LOAN_ID,"",130,true),new Column(Field.EFFECTIVE_DATE,"",130,true),new Column(Field.PRINCIPAL,"",180,true),new Column(Field.AMOUNT,"",130,true));
        d=new OperationalReportDefinition(1,d.dataset(),"Ripoti ya Montréal – Mkoa","Approved source records only","en",true,columns,List.of(),List.of(),List.of(),List.of(Field.PRINCIPAL,Field.AMOUNT));
        d.validate();var row=Map.<String,Object>of("LOAN_ID","=HYPERLINK(\"test\")","EFFECTIVE_DATE",LocalDate.of(2026,10,1),"PRINCIPAL",new BigDecimal("9999999999999999.99"),"AMOUNT",new BigDecimal("-123.45"));
        return new OperationalReportService.Result(d,"I1","B1",LocalDate.of(2026,10,1),LocalDate.of(2026,10,2),OffsetDateTime.parse("2026-10-02T10:00:00+03:00"),truncated?30:1,3,List.of(row),Map.of("AMOUNT",new BigDecimal("-123.45")),0,25,truncated,"report.coverage.COLLECTIONS");
    }
}
