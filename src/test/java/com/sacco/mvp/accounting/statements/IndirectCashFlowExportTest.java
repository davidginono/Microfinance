package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.controller.StatementController;
import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndirectCashFlowExportTest {
    final StatementPresentation presentation;
    final ApplicationClock clock=new ApplicationClock("Africa/Dar_es_Salaam");
    final StatementExportService exporter;
    IndirectCashFlowExportTest(){var messages=new ReloadableResourceBundleMessageSource();messages.setBasename("classpath:messages");messages.setDefaultEncoding("UTF-8");presentation=new StatementPresentation(messages);exporter=new StatementExportService(presentation,new ReportExportLimiter(1),clock);}

    @Test void previewPrintPdfAndExcelShareFiguresAndKeepStatementLayout()throws Exception{
        var s=IndirectCashFlowTest.businessSnapshot();var service=mock(StatementService.class);var actor=mock(AppUserPrincipal.class);
        when(service.get(actor,s.id())).thenReturn(new Run(s.id(),s.parameters().from(),s.parameters().through(),s.generatedAt(),false,false,s));
        var controller=new StatementController(service,presentation,exporter,clock);
        Path output=Path.of("target/indirect-cash-check/samples");Files.createDirectories(output);
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
            var preview=new ExtendedModelMap();var print=new ExtendedModelMap();
            controller.preview(actor,s.id(),Kind.CASH_FLOW,preview,locale);
            controller.print(actor,s.id(),Kind.CASH_FLOW,Kind.CASH_FLOW,null,null,print,locale,new MockHttpServletResponse());
            var document=(StatementPresentation.Document)preview.get("document");
            assertThat(print.get("document")).isEqualTo(document);
            assertThat(document.style()).isEqualTo("statement");assertThat(document.columns()).hasSize(5);
            assertThat(document.columns()).extracting(StatementPresentation.Column::weight).containsExactly(.12f,.40f,.08f,.20f,.20f);
            assertThat(document.rows().get(1).cells().get(1).text()).isEqualTo(presentation.label("fs.surplus",locale));
            assertThat(document.rows().get(1).cells().get(3).amount()).isEqualByComparingTo("55");
            assertThat(document.rows().get(1).cells().get(4).amount()).isEqualByComparingTo("20");
            String operatingTotal=presentation.label("fs.cashTotal.OPERATING",locale);
            assertThat(document.rows()).filteredOn(r->r.cells().get(1).text().equals(operatingTotal)).singleElement()
                .satisfies(r->{assertThat(r.total()).isTrue();assertThat(r.cells().get(3).amount()).isEqualByComparingTo("-240");});
            byte[] pdfBytes=exporter.export(s,document,"PDF",locale);
            Files.write(output.resolve("CASH_FLOW-"+locale.getLanguage()+".pdf"),pdfBytes);
            try(var pdf=Loader.loadPDF(pdfBytes)){
                assertThat(pdf.getNumberOfPages()).isEqualTo(1);
                assertThat(pdf.getPage(0).getMediaBox().getHeight()).isGreaterThan(pdf.getPage(0).getMediaBox().getWidth());
                String text=new PDFTextStripper().getText(pdf);
                assertThat(text).contains("55.00","(240.00)","1,140.00").doesNotContain("http://","https://","Details","fs.");
                assertThat(text.replaceAll("\\s+"," ")).contains(operatingTotal);
                javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,110),"png",output.resolve("CASH_FLOW-"+locale.getLanguage()+".png").toFile());
            }
            byte[] excel=exporter.export(s,document,"XLSX",locale);Files.write(output.resolve("CASH_FLOW-"+locale.getLanguage()+".xlsx"),excel);
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(excel))){
                var sheet=book.getSheetAt(0);assertThat(sheet.getPrintSetup().getLandscape()).isFalse();
                assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
                assertThat(sheet.getFooter().getRight()).isEqualTo("Page &P / &N");
                var numericRows=new HashMap<String,org.apache.poi.ss.usermodel.Row>();
                for(var row:sheet){var description=row.getCell(1);if(description!=null&&description.getCellType()==CellType.STRING)numericRows.put(description.getStringCellValue(),row);
                    for(var cell:row){assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);if(cell.getCellType()==CellType.STRING)assertThat(cell.getStringCellValue()).doesNotContain("http://","https://","fs.");}}
                var surplus=numericRows.get(presentation.label("fs.surplus",locale));
                assertThat(surplus.getCell(3).getNumericCellValue()).isEqualTo(55);
                assertThat(surplus.getCell(4).getNumericCellValue()).isEqualTo(20);
                assertThat(book.getFontAt(surplus.getCell(3).getCellStyle().getFontIndex()).getFontName()).isEqualTo("Arial");
                assertThat(numericRows.get(operatingTotal).getCell(3).getNumericCellValue()).isEqualTo(-240);
                assertThat(numericRows.get(presentation.label("fs.cashClosing",locale)).getCell(3).getNumericCellValue()).isEqualTo(1140);
            }
        }
        verify(service,times(2)).exportAccess(eq(actor),any());
    }

    @Test void ambiguousCashDoesNotDisplayAPartialSectionAsACompleteTotal(){
        var base=IndirectCashFlowTest.businessSnapshot();var s=new Snapshot(4,base.id(),base.parameters(),base.generatedAt(),base.institution(),base.branch(),base.institutionName(),base.branchName(),base.balances(),base.position(),base.income(),base.cashFlow(),List.of(),List.of(),List.of(new Check("cashAmbiguous",null)));
        var d=presentation.document(s,Kind.CASH_FLOW,s.balances(),Locale.ENGLISH);
        assertThat(d.rows()).filteredOn(r->r.cells().get(1).text().equals("Net cash from operating activities")).singleElement()
            .satisfies(r->{assertThat(r.cells().get(3).amount()).isNull();assertThat(r.cells().get(3).text()).isEqualTo(presentation.label("fs.unavailable",Locale.ENGLISH));});
    }

    @Test void mixedCashJournalKeepsTheMovementAndTheComparativeSubtotalNumeric() throws Exception {
        var p=StatementCalculatorTest.PERIOD;
        var bank=StatementCalculatorTest.balance("ASSET","BANK","CURRENT_ASSETS","OPERATING","0","100","0");
        var expense=StatementCalculatorTest.balance("EXPENSE","EXPENSE","EXPENSE","OPERATING","0","50","0");
        var capital=StatementCalculatorTest.balance("EQUITY","CAPITAL","EQUITY","FINANCING","0","0","150");
        var id=UUID.randomUUID();
        var accounts=List.of(bank,expense,capital);
        var result=com.sacco.mvp.accounting.statements.service.StatementCalculator.calculate(accounts,List.of(
            StatementCalculatorTest.cash(id,bank,"100","0"),StatementCalculatorTest.cash(id,expense,"50","0"),
            StatementCalculatorTest.cash(id,capital,"0","150")),p,false);
        assertThat(result.checks()).extracting(Check::key).contains("cashAllocation").doesNotContain("cash","previousCash");
        var base=IndirectCashFlowTest.businessSnapshot();
        var s=new Snapshot(5,base.id(),p,base.generatedAt(),base.institution(),base.branch(),base.institutionName(),
            base.branchName(),accounts,result.position(),result.income(),result.cash(),List.of(),List.of(),result.checks());
        var d=presentation.document(s,Kind.CASH_FLOW,accounts,Locale.ENGLISH);
        var amounts=Map.of("Net cash from operating activities (classified)",0,
            "Cash from entries to classify",100,"Net movement in cash and cash equivalents",100);
        amounts.forEach((label,value)->assertThat(d.rows()).filteredOn(r->r.cells().get(1).text().equals(label)).singleElement()
            .satisfies(r->{assertThat(r.cells().get(3).amount()).isEqualByComparingTo(value.toString());
                assertThat(r.cells().get(4).amount()).isEqualByComparingTo("0");}));
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(exporter.export(s,d,"PDF",Locale.ENGLISH))){
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
                .contains("Cash from entries to classify","100.00").doesNotContain("Unavailable");
        }
    }

    @Test void oldSnapshotsKeepTheirDirectMethodRowsAndAmounts(){
        var base=IndirectCashFlowTest.businessSnapshot();var oldRows=List.of(new Row("cash-1","","Interest received",0,"OPERATING",List.of(),new java.math.BigDecimal("80"),new java.math.BigDecimal("30"),false));
        var s=new Snapshot(3,base.id(),base.parameters(),base.generatedAt(),base.institution(),base.branch(),base.institutionName(),base.branchName(),base.balances(),base.position(),base.income(),oldRows,List.of(),List.of(),List.of());
        var d=presentation.document(s,Kind.CASH_FLOW,s.balances(),Locale.ENGLISH);
        assertThat(d.rows()).hasSize(3);assertThat(d.rows().get(1).cells().get(1).text()).isEqualTo("Interest received");
        assertThat(d.rows().getLast().cells().get(1).text()).isEqualTo(presentation.label("fs.total",Locale.ENGLISH));
        assertThat(d.rows().getLast().cells().get(3).amount()).isEqualByComparingTo("80");
    }
}
