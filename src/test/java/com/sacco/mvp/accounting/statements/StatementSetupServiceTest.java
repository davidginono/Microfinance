package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementSetupRepository;
import com.sacco.mvp.accounting.statements.service.StatementSetupService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AuditService;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatementSetupServiceTest {
    StatementSetupRepository repository=mock(StatementSetupRepository.class);
    GeneralLedgerService access=mock(GeneralLedgerService.class);
    AppUserPrincipal actor=mock(AppUserPrincipal.class);
    StatementSetupService service=new StatementSetupService(repository,access,JsonMapper.builder().findAndAddModules().build(),mock(AuditService.class));
    UUID capital=UUID.randomUUID(),bank=UUID.randomUUID();
    @BeforeEach void fixture(){when(actor.getSaccoId()).thenReturn("I");when(actor.getStationId()).thenReturn("B");when(actor.getMemberId()).thenReturn(UUID.randomUUID());when(repository.latest("I")).thenReturn(Optional.empty());when(repository.request(eq("I"),any())).thenReturn(Optional.empty());when(repository.accounts("I")).thenReturn(List.of(new AccountChoice(capital,"311001","Capital","EQUITY"),new AccountChoice(bank,"111001","Bank","ASSET")));}
    Form form(){var form=new Form();form.setRequestKey(UUID.randomUUID());form.setReason("Reporting setup");var c=new ComponentForm();c.setName("Capital");c.setAccounts("311001");form.getComponents().add(c);return form;}
    @Test void needsExplicitClaimBeforeAnyPersistence(){doThrow(new AccessDeniedException("Denied")).when(access).requireActor(actor,"FINANCIAL_REPORTS_CONFIGURE");assertThatThrownBy(()->service.save(actor,form())).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(repository);}
    @Test void rejectsForeignAccountsAndNonEquityComponentAccounts(){var form=form();form.getComponents().getFirst().setAccounts("FOREIGN");assertThatThrownBy(()->service.save(actor,form)).hasMessage("fs.error.setupAccounts");form.getComponents().getFirst().setAccounts("111001");assertThatThrownBy(()->service.save(actor,form)).hasMessage("fs.error.componentAccounts");verify(repository,never()).save(any(),any(),any(),any(),any(),any());}
    @Test void preventsOverlappingComponentsAndDuplicateNotes(){var f=form();var c=new ComponentForm();c.setName("Second capital");c.setAccounts("311001");f.getComponents().add(c);assertThatThrownBy(()->service.save(actor,f)).hasMessage("fs.error.componentAccounts");f.getComponents().removeLast();for(int i=1;i<=2;i++){var note=new NoteForm();note.setNumber(i);note.setTitle("Bank");note.setAccounts("111001");f.getNotes().add(note);}assertThatThrownBy(()->service.save(actor,f)).hasMessage("fs.error.noteAccounts");}
    @Test void rejectsStaleVersionAndWritesOnlyAppendOnlyMetadata(){var f=form();f.setVersion(3);assertThatThrownBy(()->service.save(actor,f)).hasMessage("fs.error.setupStale");f.setVersion(0);service.save(actor,f);verify(repository).save(eq(actor),argThat(s->s.version()==1&&s.components().getFirst().accounts().equals(List.of(capital))),any(),eq(f.getRequestKey()),any(),eq("Reporting setup"));}
    @Test void untransferredProfitNeedsOneEquityBalanceNote(){var f=form();var note=new NoteForm();note.setNumber(1);note.setTitle("Equity");note.setAccounts("311001");note.setIncludeProfit(true);f.getNotes().add(note);var other=new NoteForm();other.setNumber(2);other.setTitle("Profit");other.setIncludeProfit(true);f.getNotes().add(other);assertThatThrownBy(()->service.save(actor,f)).hasMessage("fs.error.noteProfit");f.getNotes().removeLast();note.setAccounts("111001");assertThatThrownBy(()->service.save(actor,f)).hasMessage("fs.error.noteProfit");}
    @Test void unknownJournalFailsClosed(){assertThatThrownBy(()->service.classify(actor,UUID.randomUUID(),0,Category.CONTRIBUTION,"Evidence checked")).isInstanceOf(AccessDeniedException.class);verify(repository,never()).classify(any(),any(),anyInt(),any(),any());}
    @Test void reserveTransfersCannotContainCash(){UUID id=UUID.randomUUID();when(repository.journal(actor,id)).thenReturn(List.of(new Movement(capital,Category.UNCLASSIFIED,false,new java.math.BigDecimal("0"),new java.math.BigDecimal("10")),new Movement(bank,Category.UNCLASSIFIED,false,new java.math.BigDecimal("10"),new java.math.BigDecimal("0"))));assertThatThrownBy(()->service.classify(actor,id,0,Category.TRANSFER,"Wrong type")).hasMessage("fs.error.movement");}
}
