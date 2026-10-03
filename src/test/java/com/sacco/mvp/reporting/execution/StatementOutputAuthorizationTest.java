package com.sacco.mvp.reporting.execution;

import com.sacco.mvp.accounting.statements.StatementDesignerService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.reporting.*;
import com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.*;
import com.sacco.mvp.reporting.execution.repository.StatementOutputRepository;
import com.sacco.mvp.reporting.execution.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatementOutputAuthorizationTest {
 private final StatementOutputRepository repository=mock(StatementOutputRepository.class);
 private final StatementDesignerService statements=mock(StatementDesignerService.class);
 private final OperationalReportService actors=mock(OperationalReportService.class);
 private final AccessControlService access=mock(AccessControlService.class);
 private final AppUserPrincipal actor=mock(AppUserPrincipal.class);
 private final UUID id=UUID.randomUUID();
 private final StatementOutputService service=new StatementOutputService(repository,statements,mock(StatementTypedExporter.class),actors,access,mock(SaccoLogoStorageService.class),mock(ReportExportLimiter.class),mock(ApplicationClock.class),mock(AuditService.class),JsonMapper.builder().findAndAddModules().build());
 private void scope(){when(actors.currentActor(actor)).thenReturn(actor);when(actor.getSaccoId()).thenReturn("SYNTHETIC");when(actor.getStationId()).thenReturn("B1");when(access.has(actor,UserClaim.STATEMENT_VIEW)).thenReturn(true);when(statements.authorizeExport(actor)).thenReturn(actor);when(access.has(actor,UserClaim.REPORT_RUN_APPROVE)).thenReturn(true);}
 private Output output(){var result=StatementTypedExporterTest.result();return new Output(id,"SYNTHETIC","B1",result.id(),"a".repeat(64),result,new Layout(1,"en",false,false),"b".repeat(64),"c".repeat(64),null,UUID.randomUUID(),result.generatedAt(),null,null,null);}
 @Test void currentSourceAuthorizationIsRequiredBeforeArtifactReadsOrReview(){
  scope();var output=output();when(repository.get(id,"SYNTHETIC","B1",false)).thenReturn(Optional.of(output));when(repository.get(id,"SYNTHETIC","B1",true)).thenReturn(Optional.of(output));when(statements.verifiedResult(actor,output.resultId())).thenThrow(new AccessDeniedException("Institution statement permission revoked"));
  assertThatThrownBy(()->service.get(actor,id)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->service.artifacts(actor,id)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->service.download(actor,id,UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->service.review(actor,id,"Independent output review")).isInstanceOf(AccessDeniedException.class);
  verify(repository,never()).artifacts(any());verify(repository,never()).download(any(),any());verify(repository,never()).review(any(),any(),any(),any());
 }
 @Test void changedFrozenSourceDigestCannotReachPayload(){
  scope();var output=output();when(repository.get(id,"SYNTHETIC","B1",false)).thenReturn(Optional.of(output));when(statements.verifiedResult(actor,output.resultId())).thenReturn(output.result());when(statements.verifiedResultDigest(actor,output.resultId())).thenReturn("d".repeat(64));
  assertThatThrownBy(()->service.download(actor,id,UUID.randomUUID())).isInstanceOf(IllegalStateException.class).hasMessageContaining("source checksum or scope");verify(repository,never()).download(any(),any());
 }
 @Test void listPassesCompleteInstitutionPermissionToFilterBeforePagination(){
  scope();service.list(actor,2);verify(repository).list("SYNTHETIC","B1",2,false);
  when(access.has(actor,UserClaim.FINANCIAL_REPORTS_INSTITUTION)).thenReturn(true);service.list(actor,3);verify(repository).list("SYNTHETIC","B1",3,false);
  when(access.has(actor,UserClaim.FINANCIAL_REPORTS_VIEW)).thenReturn(true);service.list(actor,4);verify(repository).list("SYNTHETIC","B1",4,true);
 }
}
