package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.service.*;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.sacco.mvp.accounting.statements.StatementCalculatorTest.*;
import static com.sacco.mvp.accounting.statements.StatementCalculatorTest.PERIOD;
import com.sacco.mvp.accounting.statements.controller.StatementController;
import com.sacco.mvp.security.AppUserPrincipal;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import static org.mockito.Mockito.*;

class StatementExportTest {
    final ReloadableResourceBundleMessageSource messages=new ReloadableResourceBundleMessageSource();
    StatementExportTest(){messages.setBasename("classpath:messages");messages.setDefaultEncoding("UTF-8");}
    final StatementPresentation presentation=new StatementPresentation(messages);
    final StatementExportService exporter=new StatementExportService(presentation,new ReportExportLimiter(1),new com.sacco.mvp.service.ApplicationClock("Africa/Nairobi"));
    Snapshot snapshot(){var bank=balance("ASSET","BANK","CURRENT_ASSETS","OPERATING","123.45","0","0");var capital=balance("EQUITY","CAPITAL","EQUITY","FINANCING","-123.45","0","0");var accounts=List.of(bank,capital);var c=StatementCalculator.calculate(accounts,List.of(),PERIOD);return new Snapshot(1,UUID.randomUUID(),PERIOD,OffsetDateTime.parse("2026-01-31T12:00:00+03:00"),"I","B","Synthetic institution","Test branch",accounts,c.position(),c.income(),c.cash(),c.equityColumns(),c.equity(),c.checks());}
    @Test void pdfUsesReferenceTypographyAndNeverExportsDetailsControls()throws Exception{
        var s=snapshot();for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){var doc=presentation.document(s,Kind.POSITION,s.balances(),locale);byte[] bytes=exporter.export(s,List.of(doc),"PDF",locale);try(var pdf=Loader.loadPDF(bytes)){String text=new PDFTextStripper().getText(pdf);assertThat(text).contains("123.45",s.institutionName()).doesNotContain("Details");assertThat(pdf.getPage(0).getMediaBox().getHeight()).isGreaterThan(pdf.getPage(0).getMediaBox().getWidth());}}
    }
    @Test void excelHasNumericAmountsPrintSettingsAndNoDetailsOrFormulas()throws Exception{
        var s=snapshot();var doc=presentation.document(s,Kind.TRIAL,s.balances(),Locale.ENGLISH);try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(s,List.of(doc),"XLSX",Locale.ENGLISH)))){
            var sheet=book.getSheetAt(0);assertThat(sheet.getPrintSetup().getLandscape()).isTrue();assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();boolean found=false;
            for(var row:sheet)for(var cell:row){assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);if(cell.getCellType()==CellType.STRING)assertThat(cell.getStringCellValue()).doesNotContain("Details");if(cell.getCellType()==CellType.NUMERIC&&cell.getNumericCellValue()==123.45)found=true;}
            assertThat(found).isTrue();assertThat(book.getFontAt(0).getFontName()).isNotNull();assertThat(sheet.getRow(7).getLastCellNum()).isEqualTo((short)7);
        }
    }
    @Test void investmentDetailsSelectTheExactMappedAccounts(){var s=snapshot();var line=s.position().stream().filter(r->!r.accounts().isEmpty()).findFirst().orElseThrow();var service=new StatementService(null,null,null,null,null,null,null);assertThat(service.selected(s,Kind.POSITION,line.key())).extracting(Balance::id).containsExactlyElementsOf(line.accounts());assertThatThrownBy(()->service.selected(s,Kind.POSITION,"foreign-line")).hasMessage("fs.error.selection");}
    @Test void completePackageContainsSixPrintableStatementsWithoutDetails()throws Exception{
        var s=snapshot();
        var documents=List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL).stream()
                .map(kind->presentation.document(s,kind,s.balances(),Locale.ENGLISH)).toList();
        try(var pdf=Loader.loadPDF(exporter.export(s,documents,"PDF",Locale.ENGLISH))){
            assertThat(pdf.getNumberOfPages()).isEqualTo(6);
            assertThat(new PDFTextStripper().getText(pdf)).doesNotContain("Details");
        }
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(s,documents,"XLSX",Locale.ENGLISH)))){
            assertThat(book.getNumberOfSheets()).isEqualTo(6);
            for(var sheet:book){
                assertThat(book.getPrintArea(book.getSheetIndex(sheet))).isNotBlank();
                for(var row:sheet)for(var cell:row)if(cell.getCellType()==CellType.STRING)
                    assertThat(cell.getStringCellValue()).doesNotContain("Details");
            }
        }
    }
    @Test void printUsesFullScopedLedgerAndRequiresExportPermission(){
        var s=snapshot();var account=s.balances().getFirst();var actor=mock(AppUserPrincipal.class);
        var service=mock(StatementService.class);var clock=new com.sacco.mvp.service.ApplicationClock("Africa/Nairobi");
        var controller=new StatementController(service,presentation,exporter,clock);
        var run=new Run(s.id(),s.parameters().from(),s.parameters().through(),s.generatedAt(),false,true,s);
        when(service.get(actor,s.id())).thenReturn(run);
        var activity=java.util.stream.IntStream.range(0,35).mapToObj(i->new Activity(UUID.randomUUID(),PERIOD.from(),"JV-"+i,"Investment transaction",n("1"),n("0"),n(Integer.toString(i+1)),null)).toList();
        when(service.activityExport(actor,s.id(),account.id())).thenReturn(activity);
        var model=new ExtendedModelMap();
        assertThat(controller.print(actor,s.id(),Kind.LEDGER,Kind.POSITION,null,account.id(),model,Locale.ENGLISH,new MockHttpServletResponse())).isEqualTo("accounting/statements/print");
        verify(service).exportAccess(actor,run);verify(service).activityExport(actor,s.id(),account.id());
        @SuppressWarnings("unchecked") var documents=(List<StatementPresentation.Document>)model.get("documents");
        assertThat(documents).hasSize(1);assertThat(documents.getFirst().rows()).hasSize(37);
        doThrow(new AccessDeniedException("Export denied")).when(service).exportAccess(actor,run);
        clearInvocations(service);
        assertThatThrownBy(()->controller.print(actor,s.id(),Kind.LEDGER,Kind.POSITION,null,account.id(),new ExtendedModelMap(),Locale.ENGLISH,new MockHttpServletResponse())).isInstanceOf(AccessDeniedException.class);
        verify(service,never()).activityExport(any(),any(),any());
    }
    @Test void ledgerAndPackagePdfTextStaysInsideEveryPage()throws Exception{
        var s=snapshot();var documents=new ArrayList<StatementPresentation.Document>();
        for(var kind:List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL))documents.add(presentation.document(s,kind,s.balances(),Locale.ENGLISH));
        documents.add(presentation.ledger(s,s.balances().getFirst(),List.of(),Locale.ENGLISH));
        try(var pdf=Loader.loadPDF(exporter.export(s,documents,"PDF",Locale.ENGLISH))){
            new PDFTextStripper(){
                float pageWidth;
                @Override protected void startPage(org.apache.pdfbox.pdmodel.PDPage page)throws IOException{pageWidth=page.getCropBox().getWidth();super.startPage(page);}
                @Override protected void processTextPosition(org.apache.pdfbox.text.TextPosition text){
                    assertThat(text.getXDirAdj()).isGreaterThanOrEqualTo(20);
                    assertThat(text.getXDirAdj()+text.getWidthDirAdj()).isLessThanOrEqualTo(pageWidth-20);
                    super.processTextPosition(text);
                }
            }.getText(pdf);
            assertThat(pdf.getPage(6).getMediaBox().getWidth()).isGreaterThan(pdf.getPage(6).getMediaBox().getHeight());
        }
    }
    @Test void ledgerAccountNumbersMatchAcrossPreviewPdfAndExcelInBothLanguages()throws Exception{
        var s=snapshot();var account=s.balances().getFirst();
        var activity=List.of(new Activity(UUID.randomUUID(),PERIOD.from(),"LN-000123","Loan transaction",n("10"),n("0"),n("10"),null),
            new Activity(UUID.randomUUID(),PERIOD.from(),"CL-000045","Client transaction",n("0"),n("2"),n("8"),null),
            new Activity(UUID.randomUUID(),PERIOD.from(),null,"Unlinked transaction",n("1"),n("0"),n("9"),null));
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
            var document=presentation.ledger(s,account,activity,locale);String heading=presentation.label("fs.accountNumber",locale);
            assertThat(document.columns().get(1).label()).isEqualTo(heading);
            assertThat(document.rows().get(1).cells().get(1).text()).isEqualTo("LN-000123");
            assertThat(document.rows().get(2).cells().get(1).text()).isEqualTo("CL-000045");
            assertThat(document.rows().get(3).cells().get(1).text()).isEqualTo(presentation.label("fs.unavailable",locale));
            try(var pdf=Loader.loadPDF(exporter.export(s,List.of(document),"PDF",locale))){
                String text=new PDFTextStripper().getText(pdf).replaceAll("\\s+"," ").toLowerCase(locale);
                assertThat(text).contains(heading.toLowerCase(locale),"ln-000123","cl-000045").doesNotContain(presentation.label("fs.reference",locale).toLowerCase(locale));
            }
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(s,List.of(document),"XLSX",locale)))){
                var numbers=new ArrayList<String>();for(var row:book.getSheetAt(0)){var cell=row.getCell(1);if(cell!=null&&cell.getCellType()==CellType.STRING)numbers.add(cell.getStringCellValue());}
                assertThat(numbers).contains(heading,"LN-000123","CL-000045",presentation.label("fs.unavailable",locale)).doesNotContain(presentation.label("fs.reference",locale));
            }
        }
    }
    @Test void asAtTitlesIncludeReportingDateAndExportsOmitTechnicalChecks()throws Exception{
        var s=snapshot();
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw")))for(var kind:List.of(Kind.POSITION,Kind.TRIAL)){
            var doc=presentation.document(s,kind,s.balances(),locale);
            assertThat(doc.asAt()).isTrue();
            assertThat(doc.title()).endsWith("31/01/2026");
            try(var pdf=Loader.loadPDF(exporter.export(s,List.of(doc),"PDF",locale))){
                assertThat(new PDFTextStripper().getText(pdf)).contains(doc.title().toUpperCase(locale),presentation.label("fs.outputNotice",locale),"31/01/2026","01/01/2025")
                        .doesNotContain(s.id().toString(),presentation.label("fs.check.coverage",locale));
            }
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(s,List.of(doc),"XLSX",locale)))){
                var sheet=book.getSheetAt(0);assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo(doc.title().toUpperCase(locale));
                for(var row:sheet)for(var cell:row)if(cell.getCellType()==CellType.STRING)
                    assertThat(cell.getStringCellValue()).doesNotContain(s.id().toString(),presentation.label("fs.check.coverage",locale));
            }
        }
    }
}
