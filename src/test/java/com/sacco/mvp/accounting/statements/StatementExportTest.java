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
}
