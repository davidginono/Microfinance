package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.reports.*;
import com.sacco.mvp.service.ReportExportLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StatementDrilldownControllerTest {
    private final StatementDesignerService statements=mock(StatementDesignerService.class);
    private final LedgerReportService ledger=mock(LedgerReportService.class);
    private final LedgerReportExporter exporter=mock(LedgerReportExporter.class);
    private final UUID id=UUID.randomUUID(),account=UUID.randomUUID();
    private final LocalDate from=LocalDate.of(2026,1,1),through=LocalDate.of(2026,1,31);
    private final OffsetDateTime cutoff=OffsetDateTime.parse("2026-02-01T00:00:00Z");
    private final StatementDrilldownController controller=new StatementDrilldownController(statements,ledger,exporter,new ReportExportLimiter(1));
    private StatementDesignerService.LineTrial detail(boolean wide){
        var result=mock(StatementDesignerService.Result.class);when(result.from()).thenReturn(from);when(result.through()).thenReturn(through);when(result.recordedCutoff()).thenReturn(cutoff);
        var row=new StatementDefinition.Row("ASSETS","Assets","Mali",StatementDefinition.RowKind.ACCOUNT_GROUP,StatementDefinition.Section.ASSETS,StatementDefinition.Unit.TZS,List.of(account),StatementDefinition.Sign.DEBIT_POSITIVE,null,null,null,true,false,false,null);
        var trial=new LedgerReportService.TrialBalance(new LedgerReportService.Scope("I1","B1",wide),new LedgerReportService.Parameters(from,through,cutoff,1),UUID.randomUUID(),1,List.of(),null,null,false);
        return new StatementDesignerService.LineTrial(result,row,trial);
    }
    @Test void draftQueryBindsDatesAndCutoffAndKeepsContextOnPageTwo()throws Exception{
        var detail=detail(false);when(statements.lineTrial(null,"versions",id,"ASSETS",from,through,null,null,cutoff,1,false)).thenReturn(detail);
        MockMvcBuilders.standaloneSetup(controller).setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver()).build().perform(get("/reports/statements/versions/"+id+"/lines/ASSETS/trial-balance")
            .param("from",from.toString()).param("through",through.toString()).param("cutoff",cutoff.toString()).param("page","1"))
            .andExpect(status().isOk()).andExpect(view().name("reports/statement-trial")).andExpect(model().attribute("detailQuery","?from=2026-01-01&through=2026-01-31&cutoff=2026-02-01T00:00:00Z"));
    }
    @Test void accountMustBelongToLineAndInstitutionSummaryCannotOpenBranchActivity()throws Exception{
        var wide=detail(true);var branch=detail(false);when(statements.lineTrial(isNull(),eq("results"),eq(id),eq("ASSETS"),isNull(),isNull(),isNull(),isNull(),isNull(),eq(0),eq(false))).thenReturn(wide);
        var query=new StatementDrilldownController.Query(null,null,null,null,null,0);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->controller.account(null,"results",id,"ASSETS",account,query,new org.springframework.ui.ExtendedModelMap())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(statements.lineTrial(null,"results",id,"ASSETS",null,null,null,null,null,0,false)).thenReturn(branch);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->controller.account(null,"results",id,"ASSETS",UUID.randomUUID(),query,new org.springframework.ui.ExtendedModelMap())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(ledger,exporter);
    }
    @Test void pdfAndPrintUseFullAuthorizedLineAndOnlyPdf()throws Exception{
        var detail=detail(false);when(statements.lineTrial(null,"results",id,"ASSETS",null,null,null,null,null,0,true)).thenReturn(detail);
        byte[] bytes="%PDF-synthetic".getBytes(java.nio.charset.StandardCharsets.US_ASCII);when(exporter.renderTrial(detail.trial(),Locale.ENGLISH)).thenReturn(bytes);
        var query=new StatementDrilldownController.Query(null,null,null,null,null,0);
        var download=controller.trialPdf(null,"results",id,"ASSETS",query,false,Locale.ENGLISH);
        var print=controller.trialPdf(null,"results",id,"ASSETS",query,true,Locale.ENGLISH);
        org.assertj.core.api.Assertions.assertThat(print.getBody()).isEqualTo(download.getBody());
        org.assertj.core.api.Assertions.assertThat(print.getHeaders().getFirst("Content-Disposition")).startsWith("inline");
        org.assertj.core.api.Assertions.assertThat(download.getHeaders().getFirst("Content-Type")).isEqualTo("application/pdf");
    }
}
