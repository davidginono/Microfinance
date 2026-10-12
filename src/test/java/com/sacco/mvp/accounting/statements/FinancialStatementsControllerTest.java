package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.reporting.execution.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import java.time.*;
import java.util.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FinancialStatementsControllerTest {
    private final StatementDesignerService statements=mock(StatementDesignerService.class);
    private final StatementOutputService outputs=mock(StatementOutputService.class);
    private final StatementTypedExporter exporter=mock(StatementTypedExporter.class);
    private final ReportExportLimiter limiter=mock(ReportExportLimiter.class);
    private final ApplicationClock clock=mock(ApplicationClock.class);
    private final FinancialStatementsController controller=new FinancialStatementsController(statements,outputs,exporter,limiter,clock);
    @Test void replacesTheBuilderWithKindFilteredBranchResults(){var actor=mock(AppUserPrincipal.class);when(clock.today()).thenReturn(LocalDate.of(2026,10,12));when(statements.versions(actor,Kind.BALANCE_SHEET,0)).thenReturn(List.of());when(statements.results(actor,Kind.BALANCE_SHEET,2)).thenReturn(List.of());var model=new ExtendedModelMap();assertThat(controller.index(actor,Kind.BALANCE_SHEET,null,2,model)).isEqualTo("reports/financial-statements");verify(statements).results(actor,Kind.BALANCE_SHEET,2);assertThat(model.get("selected")).isNull();assertThat(model.get("kind")).isEqualTo(Kind.BALANCE_SHEET);}
    @Test void aSelectedMappingCannotBeUsedForAnotherStatement(){var actor=mock(AppUserPrincipal.class);UUID id=UUID.randomUUID();var definition=StandardStatementTemplates.create(Kind.BALANCE_SHEET,List.of());when(statements.versions(actor,Kind.PROFIT_AND_LOSS,0)).thenReturn(List.of());when(statements.version(actor,id)).thenReturn(new StatementRepository.Version(id,UUID.randomUUID(),1,"APPROVED",UUID.randomUUID(),UUID.randomUUID(),"evidence","review",definition));assertThatThrownBy(()->controller.index(actor,Kind.PROFIT_AND_LOSS,id,0,new ExtendedModelMap())).hasMessage("statement.error.validation");verify(statements,never()).results(any(),any(),anyInt());}
    @Test void exportDenialHappensBeforeAnySourceOrOutputIsRead(){var actor=mock(AppUserPrincipal.class);UUID id=UUID.randomUUID();doThrow(new org.springframework.security.access.AccessDeniedException("Denied")).when(statements).authorizeExport(actor);assertThatThrownBy(()->controller.finalizedPdf(actor,id,false,Locale.ENGLISH)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verify(statements,never()).verifiedResult(any(),any());verifyNoInteractions(outputs,exporter,limiter);}
    @Test void printAndDownloadServeTheSameVerifiedRetainedPdf(){var actor=mock(AppUserPrincipal.class);UUID resultId=UUID.randomUUID(),outputId=UUID.randomUUID(),artifactId=UUID.randomUUID();var source=mock(StatementDesignerService.Result.class);when(source.definition()).thenReturn(StandardStatementTemplates.create(Kind.CHANGES_IN_EQUITY,List.of()));when(statements.verifiedResult(actor,resultId)).thenReturn(source);when(outputs.capture(eq(actor),eq(resultId),any())).thenReturn(outputId);byte[] bytes="%PDF-synthetic".getBytes(java.nio.charset.StandardCharsets.US_ASCII);when(outputs.artifacts(actor,outputId)).thenReturn(List.of(new com.sacco.mvp.reporting.execution.dto.ReportRunDtos.Artifact(artifactId,com.sacco.mvp.reporting.OperationalReportExportService.Format.PDF,"checksum",bytes.length)));when(outputs.download(actor,outputId,artifactId)).thenReturn(new com.sacco.mvp.reporting.execution.dto.ReportRunDtos.Download(com.sacco.mvp.reporting.OperationalReportExportService.Format.PDF,bytes,"checksum"));var download=controller.finalizedPdf(actor,resultId,false,Locale.ENGLISH);var print=controller.finalizedPdf(actor,resultId,true,Locale.ENGLISH);assertThat(print.getBody()).isEqualTo(download.getBody());assertThat(download.getHeaders().getFirst("Content-Disposition")).startsWith("attachment");assertThat(print.getHeaders().getFirst("Content-Disposition")).startsWith("inline");assertThat(print.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");verify(outputs,times(2)).capture(actor,resultId,new com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.Layout(3,"en",true,false));}
    @Test void oldBuilderPathsHaveNoControllerOrMutationHandler()throws Exception{var mvc=MockMvcBuilders.standaloneSetup(controller).build();mvc.perform(get("/reports/builder")).andExpect(status().isNotFound());mvc.perform(post("/reports/builder/templates")).andExpect(status().isNotFound());mvc.perform(post("/reports/builder/preview/export")).andExpect(status().isNotFound());}
}
