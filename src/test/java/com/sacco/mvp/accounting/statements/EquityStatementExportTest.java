package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.service.*;
import com.sacco.mvp.service.ReportExportLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.util.*;
import java.time.OffsetDateTime;
import java.nio.file.*;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.*;
import static com.sacco.mvp.accounting.statements.StatementCalculatorTest.*;
import static com.sacco.mvp.accounting.statements.StatementCalculatorTest.PERIOD;

class EquityStatementExportTest {
    @Test void equityOpeningAndClosingShowNumericZeroInThePreviewAndExports() throws Exception {
        var accounts=List.of(balance("EQUITY","CAPITAL","EQUITY","FINANCING","0","0","0"));
        var base=StatementCalculator.calculate(accounts,List.of(),PERIOD,false);
        var setup=new Setup(1,List.of(),List.of(),List.of());
        var equity=EquityStatementCalculator.calculate(accounts,List.of(),setup);
        var snapshot=new Snapshot(3,UUID.randomUUID(),PERIOD,OffsetDateTime.parse("2026-01-31T12:00:00+03:00"),
            "I","B","Institution","Branch",accounts,base.position(),base.income(),base.cash(),equity.columns(),
            equity.current(),base.checks(),setup,equity.previous(),List.of());
        var messages=new ReloadableResourceBundleMessageSource();
        messages.setBasename("classpath:messages");
        var presentation=new StatementPresentation(messages);
        var export=new StatementExportService(presentation,new ReportExportLimiter(1),new com.sacco.mvp.service.ApplicationClock("Africa/Dar_es_Salaam"));
        var document=presentation.document(snapshot,Kind.EQUITY,accounts,Locale.ENGLISH);
        assertThat(document.rows()).filteredOn(row->row.cells().getFirst().text().startsWith("Balance as at"))
            .hasSize(4).allSatisfy(row->assertThat(row.cells().subList(1,row.cells().size()))
                .allSatisfy(cell->assertThat(cell.text()).isEqualTo("0.00")));
        try(var pdf=Loader.loadPDF(export.export(snapshot,document,"PDF",Locale.ENGLISH))){
            assertThat(new PDFTextStripper().getText(pdf)).contains("Balance as at","0.00");
        }
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(export.export(snapshot,document,"XLSX",Locale.ENGLISH)))){
            var formatter=new org.apache.poi.ss.usermodel.DataFormatter(Locale.ENGLISH);
            for(var row:book.getSheetAt(0)){
                if(row.getCell(0)==null || !formatter.formatCellValue(row.getCell(0)).startsWith("Balance as at"))continue;
                assertThat(formatter.formatCellValue(row.getCell(1))).isEqualTo("0.00");
                assertThat(row.getHeightInPoints()).isGreaterThanOrEqualTo(21);
            }
        }
    }

    @Test void configuredEquityAndNotesUseFrozenBilingualDataAcrossOutputs()throws Exception{
        var capital=balance("EQUITY","CAPITAL","EQUITY","FINANCING","-1000","0","200");var income=balance("INCOME","INCOME","INCOME","OPERATING","0","0","30.45");var accounts=List.of(capital,income);var movements=List.of(new Movement(capital.id(),Category.CONTRIBUTION,false,n("0"),n("200")));
        var setup=new Setup(2,List.of(new Component("Owner capital","Mtaji wa mmiliki",List.of(capital.id()),false)),List.of(new Note(1,"Capital","Mtaji","BALANCE","Verified source disclosure","Maelezo ya chanzo",List.of(capital.id()),true),new Note(2,"Basis of preparation","Msingi wa maandalizi","NARRATIVE","Synthetic policy text, not an approved institution policy.","Maelezo ya mfano pekee.",List.of())),List.of(new CategoryLabel(Category.CONTRIBUTION,"Owner investment","Uwekezaji wa mmiliki")));
        var base=StatementCalculator.calculate(accounts,List.of(),PERIOD,false);var equity=EquityStatementCalculator.calculate(accounts,movements,setup);
        var s=new Snapshot(3,UUID.randomUUID(),PERIOD,OffsetDateTime.parse("2026-01-31T12:00:00+03:00"),"I","B","Synthetic institution","Test branch",accounts,base.position(),base.income(),base.cash(),equity.columns(),equity.current(),base.checks(),setup,equity.previous(),movements);
        var messages=new ReloadableResourceBundleMessageSource();messages.setBasename("classpath:messages");messages.setDefaultEncoding("UTF-8");var presentation=new StatementPresentation(messages);var exporter=new StatementExportService(presentation,new ReportExportLimiter(1),new com.sacco.mvp.service.ApplicationClock("Africa/Dar_es_Salaam"));
        Path output=Path.of("target/equity-statement-check/samples");Files.createDirectories(output);
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw")))for(var kind:List.of(Kind.EQUITY,Kind.NOTES)){
            var document=presentation.document(s,kind,accounts,locale);String language=locale.getLanguage();byte[] pdfBytes=exporter.export(s,document,"PDF",locale);Files.write(output.resolve(kind+"-"+language+".pdf"),pdfBytes);
            try(var pdf=Loader.loadPDF(pdfBytes)){
                String text=new PDFTextStripper().getText(pdf);assertThat(text).doesNotContain("Details");if(kind==Kind.EQUITY){assertThat(text).contains("1,230.45","01/01/2025");assertThat(pdf.getPage(0).getMediaBox().getWidth()).isGreaterThan(pdf.getPage(0).getMediaBox().getHeight());}else assertThat(text).contains(language.equals("sw")?"Msingi wa maandalizi":"Basis of preparation");
                javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,110),"png",output.resolve(kind+"-"+language+".png").toFile());
            }
            byte[] excel=exporter.export(s,document,"XLSX",locale);Files.write(output.resolve(kind+"-"+language+".xlsx"),excel);try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(excel))){boolean found=false;for(var row:workbook.getSheetAt(0))for(var cell:row){assertThat(cell.getCellType()).isNotEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);if(cell.getCellType()==org.apache.poi.ss.usermodel.CellType.NUMERIC&&Math.abs(cell.getNumericCellValue()-1230.45)<0.00001)found=true;}assertThat(found).isTrue();}
        }
    }
}
