package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementLayoutModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementSetupRepository;
import com.sacco.mvp.accounting.statements.service.StatementLayoutService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AuditService;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatementLayoutServiceTest {
    final StatementSetupRepository repository=mock(StatementSetupRepository.class);
    final GeneralLedgerService access=mock(GeneralLedgerService.class);
    final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    final AuditService audit=mock(AuditService.class);
    final tools.jackson.databind.ObjectMapper mapper=JsonMapper.builder().findAndAddModules().build();
    final StatementLayoutService service=new StatementLayoutService(repository,access,mapper,audit);
    final UUID capital=UUID.randomUUID(),reserve=UUID.randomUUID(),bank=UUID.randomUUID(),income=UUID.randomUUID();
    @BeforeEach void fixture(){
        when(actor.getSaccoId()).thenReturn("I");when(actor.getStationId()).thenReturn("B");when(actor.getMemberId()).thenReturn(UUID.randomUUID());
        when(repository.latest("I")).thenReturn(Optional.empty());when(repository.request(eq("I"),any())).thenReturn(Optional.empty());
        when(repository.accounts("I")).thenReturn(List.of(new AccountChoice(capital,"311001","Capital","EQUITY"),new AccountChoice(reserve,"321001","Reserve","EQUITY"),new AccountChoice(bank,"111001","Bank","ASSET"),new AccountChoice(income,"411001","Income","INCOME")));
    }
    GroupForm group(){var f=new GroupForm();f.setRequestKey(UUID.randomUUID());f.setReason("Owner capital mapping");f.setName("Capital");f.setAccounts(List.of(capital));return f;}
    NoteForm note(){var f=new NoteForm();f.setRequestKey(UUID.randomUUID());f.setReason("Cash disclosure");f.setTitle("Cash");f.setBasis("BALANCE");f.setAccounts(List.of(bank));return f;}
    void stored(Setup setup){when(repository.latest("I")).thenReturn(Optional.of(new StoredSetup(setup.version(),mapper.writeValueAsString(setup))));}
    Component capitalGroup(){return new Component("Capital","Mtaji",List.of(capital),false);}

    @Test void movementOnlySetupKeepsChartNotesAndOlderLayoutsReadWithTheirOriginalMeaning(){
        var older=mapper.readValue("""
            {"version":2,"components":[],"notes":[],"categories":[]}
            """,Setup.class);
        assertThat(older.movementRows()).isEmpty();
        assertThat(older.hasLayoutNotes()).isTrue();
        var form=service.movementRow(actor,-1,null);
        form.setName("Profit set aside");
        form.setCategory(Category.UNCLASSIFIED);
        form.setReason("Approved reporting label");
        assertThatThrownBy(()->service.saveMovementRow(actor,form)).hasMessage("fs.error.layoutMovementCategory");
        form.setCategory(Category.TRANSFER);
        assertThat(service.saveMovementRow(actor,form)).isTrue();
        var recorded=org.mockito.ArgumentCaptor.forClass(Setup.class);
        verify(repository).save(eq(actor),recorded.capture(),anyString(),eq(form.getRequestKey()),anyString(),eq(form.getReason()));
        assertThat(recorded.getValue().hasLayoutNotes()).isFalse();
        stored(recorded.getValue());
        var duplicate=service.movementRow(actor,-1,null);
        duplicate.setName("profit set aside");
        duplicate.setCategory(Category.TRANSFER);
        duplicate.setReason("Same label");
        assertThatThrownBy(()->service.saveMovementRow(actor,duplicate)).hasMessage("fs.error.layoutMovementName");
    }

    @Test void explicitClaimIsCheckedBeforePersistenceForReadsAndWrites(){
        doThrow(new AccessDeniedException("Denied")).when(access).requireActor(actor,"FINANCIAL_REPORTS_CONFIGURE");
        assertThatThrownBy(()->service.saveGroup(actor,group())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.overview(actor)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(repository);
    }
    @Test void rejectsUnknownNonEquityAndDuplicateGroupAccounts(){
        var f=group();f.setAccounts(List.of(UUID.randomUUID()));assertThatThrownBy(()->service.saveGroup(actor,f)).hasMessage("fs.error.componentAccounts");
        f.setAccounts(List.of(bank));assertThatThrownBy(()->service.saveGroup(actor,f)).hasMessage("fs.error.componentAccounts");
        f.setAccounts(List.of(capital,capital));assertThatThrownBy(()->service.saveGroup(actor,f)).hasMessage("fs.error.setupAccounts");
        verify(repository,never()).save(any(),any(),any(),any(),any(),any());
    }
    @Test void focusedGroupEditPreservesNotesLabelsAndOrderWithoutChangingTheOriginal(){
        var notes=List.of(new Note(1,"Cash","","BALANCE","","",List.of(bank)));
        var labels=List.of(new CategoryLabel(Category.CONTRIBUTION,"Owner funding",""));
        var original=new Setup(4,List.of(capitalGroup(),new Component("Reserve","",List.of(reserve),false)),notes,labels);stored(original);
        var f=service.group(actor,1,4).form();f.setPosition(1);f.setName("General reserve");f.setReason("Column order");assertThat(service.saveGroup(actor,f)).isTrue();
        verify(repository).save(eq(actor),argThat(s->s.version()==5&&s.components().getFirst().name().equals("General reserve")&&s.components().getLast().name().equals("Capital")&&s.notes().equals(notes)&&s.categories().equals(labels)),any(),eq(f.getRequestKey()),any(),eq("Column order"));
        assertThat(original.components().getFirst().name()).isEqualTo("Capital");assertThat(original.version()).isEqualTo(4);
    }
    @Test void groupNamesMembershipAndUntransferredProfitCannotOverlap(){
        stored(new Setup(1,List.of(new Component("Capital","",List.of(capital),true)),List.of(),List.of()));
        var f=group();f.setVersion(1);f.setPosition(2);f.setAccounts(List.of(reserve));assertThatThrownBy(()->service.saveGroup(actor,f)).hasMessage("fs.error.layoutGroupName");
        f.setName("Reserve");f.setAccounts(List.of(capital));assertThatThrownBy(()->service.saveGroup(actor,f)).hasMessage("fs.error.componentAccounts");
        f.setAccounts(List.of(reserve));f.setRetained(true);assertThatThrownBy(()->service.saveGroup(actor,f)).hasMessage("fs.error.layoutRetained");
    }
    @Test void noteEditKeepsGroupMappingsAndSortsByItsVisibleNumber(){
        var original=new Setup(3,List.of(capitalGroup()),List.of(new Note(1,"Policy","","NARRATIVE","Recorded policy","",List.of())),List.of());stored(original);
        var f=note();f.setVersion(3);f.setNumber(2);service.saveNote(actor,f);
        verify(repository).save(eq(actor),argThat(s->s.version()==4&&s.components().equals(original.components())&&s.notes().size()==2&&s.notes().getFirst().number()==1&&s.notes().getLast().accounts().equals(List.of(bank))),any(),any(),any(),any());
    }
    @Test void narrativeNotesNeedTextAndCannotQuietlyDiscardSubmittedAccounts(){
        var f=note();f.setBasis("NARRATIVE");assertThatThrownBy(()->service.saveNote(actor,f)).hasMessage("fs.error.noteAccounts");
        f.setAccounts(List.of());assertThatThrownBy(()->service.saveNote(actor,f)).hasMessage("fs.error.layoutNoteText");
        f.setText("Approved basis of preparation");assertThat(service.saveNote(actor,f)).isTrue();
    }
    @Test void financialNotesRequireOneAccountTypeAndNoSharedAccounts(){
        var f=note();f.setAccounts(List.of(bank,income));assertThatThrownBy(()->service.saveNote(actor,f)).hasMessage("fs.error.noteAccounts");
        f.setAccounts(List.of(UUID.randomUUID()));assertThatThrownBy(()->service.saveNote(actor,f)).hasMessage("fs.error.noteAccounts");
        stored(new Setup(1,List.of(),List.of(new Note(1,"Cash","","BALANCE","","",List.of(bank))),List.of()));f.setVersion(1);f.setNumber(2);f.setAccounts(List.of(bank));assertThatThrownBy(()->service.saveNote(actor,f)).hasMessage("fs.error.noteAccounts");
    }
    @Test void untransferredProfitBelongsToOneEquityBalanceNote(){
        var f=note();f.setIncludeProfit(true);assertThatThrownBy(()->service.saveNote(actor,f)).hasMessage("fs.error.noteProfit");
        f.setAccounts(List.of(capital));f.setBasis("MOVEMENT");assertThatThrownBy(()->service.saveNote(actor,f)).hasMessage("fs.error.noteProfit");
        f.setBasis("BALANCE");stored(new Setup(1,List.of(),List.of(new Note(1,"Profit","","BALANCE","","",List.of(),true)),List.of()));f.setVersion(1);f.setNumber(2);assertThatThrownBy(()->service.saveNote(actor,f)).hasMessage("fs.error.noteProfit");
    }
    @Test void noteNumbersAreUniqueAndStaleEditsCannotTargetAReorderedRecord(){
        stored(new Setup(3,List.of(capitalGroup()),List.of(new Note(1,"Policy","","NARRATIVE","Policy text","",List.of())),List.of()));
        var f=note();f.setVersion(3);assertThatThrownBy(()->service.saveNote(actor,f)).hasMessage("fs.error.layoutNoteNumber");
        assertThatThrownBy(()->service.group(actor,0,2)).hasMessage("fs.error.setupStale");assertThatThrownBy(()->service.group(actor,0,null)).hasMessage("fs.error.setupStale");
        var g=group();g.setVersion(2);assertThatThrownBy(()->service.saveGroup(actor,g)).hasMessage("fs.error.setupStale");verify(repository,never()).save(any(),any(),any(),any(),any(),any());
    }
    @Test void exactRetriesAreIdempotentAndChangedPayloadOrActorIsRejected(){
        var f=group();service.saveGroup(actor,f);var hash=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).save(eq(actor),any(),any(),eq(f.getRequestKey()),hash.capture(),any());when(repository.request("I",f.getRequestKey())).thenReturn(Optional.of(hash.getValue()));
        stored(new Setup(8,List.of(capitalGroup()),List.of(),List.of()));assertThat(service.saveGroup(actor,f)).isTrue();
        f.setReason("Changed payload");assertThatThrownBy(()->service.saveGroup(actor,f)).hasMessage("fs.error.retry");f.setReason("Owner capital mapping");when(actor.getMemberId()).thenReturn(UUID.randomUUID());assertThatThrownBy(()->service.saveGroup(actor,f)).hasMessage("fs.error.retry");
        verify(repository,times(1)).save(any(),any(),any(),any(),any(),any());
    }
    @Test void unchangedEditsDoNotCreateFakeVersionsOrRequireAChangeReason(){
        stored(new Setup(1,List.of(capitalGroup()),List.of(),List.of()));var form=service.group(actor,0,1).form();assertThat(service.saveGroup(actor,form)).isFalse();verify(repository,never()).save(any(),any(),any(),any(),any(),any());verifyNoInteractions(audit);
    }
    @Test void choosingTheSameAccountsInAnotherOrderIsStillUnchanged(){
        stored(new Setup(1,List.of(new Component("Equity","",List.of(reserve,capital),false)),List.of(),List.of()));var form=service.group(actor,0,1).form();form.setAccounts(List.of(capital,reserve));assertThat(service.saveGroup(actor,form)).isFalse();verify(repository,never()).save(any(),any(),any(),any(),any(),any());
    }
    @Test void removalAppendsMetadataAndCannotDeleteAStaleOrDifferentGroup(){
        var original=new Setup(2,List.of(capitalGroup()),List.of(new Note(1,"Cash","","BALANCE","","",List.of(bank))),List.of());stored(original);
        var removal=service.removal(actor,true,0,2).form();removal.setReason("Use the account defaults");service.remove(actor,true,removal);
        verify(repository).save(eq(actor),argThat(s->s.version()==3&&s.components().isEmpty()&&s.notes().equals(original.notes())),any(),any(),any(),any());assertThat(original.components()).hasSize(1);
        removal.setVersion(1);assertThatThrownBy(()->service.remove(actor,true,removal)).hasMessage("fs.error.setupStale");
    }
    @Test void missingConfiguredAccountsAndLargeChoicesFailClosed(){
        stored(new Setup(1,List.of(new Component("Missing","",List.of(UUID.randomUUID()),false)),List.of(),List.of()));assertThatThrownBy(()->service.group(actor,0,1)).hasMessage("fs.error.setupAccounts");
        when(repository.accounts("I")).thenReturn(Collections.nCopies(2001,new AccountChoice(capital,"311001","Capital","EQUITY")));assertThatThrownBy(()->service.accounts(actor,false)).hasMessage("fs.error.limit");
    }
    @Test void fullLayoutsDoNotOfferAnImpossibleNewItem(){
        stored(new Setup(1,Collections.nCopies(9,capitalGroup()),List.of(),List.of()));assertThatThrownBy(()->service.group(actor,-1,null)).hasMessage("fs.error.limit");
        stored(new Setup(1,List.of(),Collections.nCopies(30,new Note(1,"Policy","","NARRATIVE","Policy text","",List.of())),List.of()));assertThatThrownBy(()->service.note(actor,-1,null)).hasMessage("fs.error.limit");
    }
}
