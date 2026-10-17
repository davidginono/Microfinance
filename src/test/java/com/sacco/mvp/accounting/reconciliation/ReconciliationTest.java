package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.dto.ReconciliationReport.*;
import com.sacco.mvp.accounting.reconciliation.repository.ReconciliationRepository;
import com.sacco.mvp.accounting.reconciliation.service.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReconciliationTest {
    final ReconciliationRepository repository=mock(ReconciliationRepository.class);
    final GeneralLedgerService guard=mock(GeneralLedgerService.class);
    final ApplicationClock clock=new ApplicationClock("Africa/Nairobi");
    final ReconciliationService service=new ReconciliationService(repository,guard,clock,new ReportExportLimiter(1));
    final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    Parameters p(){return new Parameters(LocalDate.of(2026,10,1),LocalDate.of(2026,10,8),OffsetDateTime.parse("2026-10-08T12:00:00+03:00"),"PRINCIPAL",0);}
    @Test void onlyReadPermissionReturnsToTheAccessMatrixWithoutDefaultGrants() {
        assertThat(UserClaim.ACCOUNTING_RECONCILIATION_VIEW.isAvailable()).isTrue();
        assertThat(UserClaim.ACCOUNTING_RECONCILIATION_CREATE.isAvailable()).isFalse();assertThat(UserClaim.ACCOUNTING_RECONCILIATION_APPROVE.isAvailable()).isFalse();
        assertThat(UserClaim.defaultClaims(Arrays.asList(Position.values()),true)).doesNotContain(UserClaim.ACCOUNTING_RECONCILIATION_VIEW);
    }
    @Test void invalidDatesComponentAndPageNeverReachFinancialQueries() {
        for(var invalid:List.of(new Parameters(p().through(),p().from(),p().cutoff(),"PRINCIPAL",0),
            new Parameters(p().from(),p().through(),p().cutoff(),"OTHER",0),new Parameters(p().from(),p().through(),p().cutoff(),"PRINCIPAL",-1),
            new Parameters(p().from(),p().through(),clock.now().plusMinutes(1),"PRINCIPAL",0)))
            assertThatThrownBy(()->service.report(actor,invalid,false)).hasMessage("reconciliation.error.parameters");
        verifyNoInteractions(repository);
    }
    @Test void exportNeverSilentlyTruncatesAndRequiresCurrentClaims() {
        when(repository.groups(actor,p())).thenReturn(List.of(new Group("PRINCIPAL","MOVEMENT",5001,List.of())));
        assertThatThrownBy(()->service.report(actor,p(),true)).hasMessage("reconciliation.error.large");
        verify(guard).requireActor(actor,"ACCOUNTING_RECONCILIATION_VIEW");verify(guard).requireActor(actor,"FINANCIAL_REPORTS_EXPORT");verify(repository,never()).rows(any(),any(),anyBoolean());
    }
    @Test void pdfRetainsTemplateColumnsExactVarianceAndBothLanguages() throws Exception {
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        var pdf=new ReconciliationPdf(messages,new ReportExportLimiter(1));var zero=new BigDecimal("0.00");var six=new BigDecimal("6.00");
        var row=new Row("PRINCIPAL","MOVEMENT","CLIENT-2","LOAN-2","120001","DOC-2",p().through(),"PAYMENT","SL_ONLY",zero,six,zero,zero,zero,six,"synthetic");
        var result=new Result(p(),"Synthetic institution","Branch 1",List.of(row),List.of(new Group("PRINCIPAL","MOVEMENT",1,row.getAmounts())),1,false);
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))) {
            var bytes=pdf.build(result,locale);
            try(var document=org.apache.pdfbox.Loader.loadPDF(bytes)) {
                var text=new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+"," ");
                assertThat(text).contains("DOC-2","CLIENT-2","6.00",messages.getMessage("reconciliation.column.creditVariance",null,locale));
                assertThat(text).doesNotContain("Details","SHARES");
                String output=System.getProperty("reconciliation.visualOutput");
                if(output!=null) {
                    var dir=java.nio.file.Path.of(output);java.nio.file.Files.createDirectories(dir);java.nio.file.Files.write(dir.resolve("reconciliation-"+locale.getLanguage()+".pdf"),bytes);
                    javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0,96),"png",dir.resolve("reconciliation-"+locale.getLanguage()+".png").toFile());
                }
            }
        }
        assertThatThrownBy(()->pdf.build(new Result(p(),"I","B",List.of(),result.groups(),1,false),Locale.ENGLISH)).hasMessage("reconciliation.error.large");
    }
    @Test void multiPagePdfKeepsAllExceptionsAndRepeatsColumnHeadings() throws Exception {
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        var rows=new ArrayList<Row>();var zero=new BigDecimal("0.00");
        for(int n=0;n<120;n++)rows.add(new Row("PRINCIPAL","MOVEMENT","CLIENT","LOAN","120001","DOCUMENT-"+n,p().through(),"PAYMENT","SL_ONLY",zero,BigDecimal.ONE,zero,zero,zero,BigDecimal.ONE,"row-"+n));
        var result=new Result(p(),"Institution","Branch",rows,List.of(new Group("PRINCIPAL","MOVEMENT",120,List.of(zero,new BigDecimal("120"),zero,zero,zero,new BigDecimal("120")))),120,false);
        try(var document=org.apache.pdfbox.Loader.loadPDF(new ReconciliationPdf(messages,new ReportExportLimiter(1)).build(result,Locale.ENGLISH))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);assertThat(text).contains("DOCUMENT-0","DOCUMENT-119");
            assertThat(text.split("SL Debits",-1).length-1).isGreaterThan(1);
        }
    }
}
