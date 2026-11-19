package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementModels.Parameters;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementSetupRepository;
import com.sacco.mvp.accounting.statements.service.EquityMovementService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AuditService;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EquityMovementServiceTest {
    final StatementSetupRepository repository=mock(StatementSetupRepository.class);
    final GeneralLedgerService access=mock(GeneralLedgerService.class);
    final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    final EquityMovementService service=new EquityMovementService(repository,access,mock(AuditService.class),tools.jackson.databind.json.JsonMapper.builder().build());
    final UUID capital=UUID.randomUUID(),bank=UUID.randomUUID(),income=UUID.randomUUID(),journal=UUID.randomUUID();
    @BeforeEach void fixture(){when(actor.getSaccoId()).thenReturn("I");when(actor.getStationId()).thenReturn("B");when(actor.getMemberId()).thenReturn(UUID.randomUUID());when(repository.accounts("I")).thenReturn(List.of(new AccountChoice(capital,"311001","Capital","EQUITY"),new AccountChoice(bank,"111001","Bank","ASSET"),new AccountChoice(income,"411001","Income","INCOME")));}
    Movement line(UUID account,String debit,String credit){return new Movement(account,Category.UNCLASSIFIED,false,new BigDecimal(debit),new BigDecimal(credit));}
    @Test void stillNeedsExplicitConfigurationClaim(){doThrow(new AccessDeniedException("Denied")).when(access).requireActor(actor,"FINANCIAL_REPORTS_CONFIGURE");assertThatThrownBy(()->service.classify(actor,journal,0,Category.CONTRIBUTION,"Owner evidence")).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(repository);}
    @Test void unknownOrForeignJournalFailsClosed(){assertThatThrownBy(()->service.classify(actor,journal,0,Category.CONTRIBUTION,"Owner evidence")).isInstanceOf(AccessDeniedException.class);verify(repository,never()).classify(any(),any(),anyInt(),any(),any());}
    @Test void reserveTransferCannotContainCash(){when(repository.journal(actor,journal)).thenReturn(List.of(line(capital,"0","10"),line(bank,"10","0")));assertThatThrownBy(()->service.classify(actor,journal,0,Category.TRANSFER,"Wrong type")).hasMessage("fs.error.movement");}
    @Test void contributionsCannotHideIncomeAndEarningsTransfersCannotContainCash(){when(repository.journal(actor,journal)).thenReturn(List.of(line(capital,"0","10"),line(income,"10","0")));assertThatThrownBy(()->service.classify(actor,journal,0,Category.CONTRIBUTION,"Wrong type")).hasMessage("fs.error.movement");when(repository.journal(actor,journal)).thenReturn(List.of(line(capital,"0","10"),line(bank,"10","0")));assertThatThrownBy(()->service.classify(actor,journal,0,Category.EARNINGS_TRANSFER,"Wrong type")).hasMessage("fs.error.movement");}
    @Test void classificationRetriesAndScopeArePreserved(){when(repository.journal(actor,journal)).thenReturn(List.of(line(capital,"0","10"),line(bank,"10","0")));when(repository.classificationVersion(actor,journal)).thenReturn(1);when(repository.sameClassification(actor,journal,Category.CONTRIBUTION,"Owner evidence")).thenReturn(true);service.classify(actor,journal,0,Category.CONTRIBUTION,"Owner evidence");verify(repository,never()).classify(any(),any(),anyInt(),any(),any());verify(repository).journal(actor,journal);verify(repository).lock("I/B/"+journal);}
    @Test void datesAndPageRemainBounded(){var from=LocalDate.of(2026,1,1);assertThatThrownBy(()->service.journals(actor,new Parameters(from,from.plusDays(367),false),0)).hasMessage("fs.error.dates");assertThatThrownBy(()->service.journals(actor,new Parameters(from,from,false),10001)).hasMessage("fs.error.dates");}
}
