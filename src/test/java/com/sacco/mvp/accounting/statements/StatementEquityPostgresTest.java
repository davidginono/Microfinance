package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementSetupRepository;
import com.sacco.mvp.accounting.statements.service.*;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_STATEMENTS_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_statement_workspace_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementEquityPostgresTest {
    StatementWorkspacePostgresTest fixture=new StatementWorkspacePostgresTest();
    StatementSetupRepository repository;StatementSetupService setup;
    @BeforeAll void database(){fixture.migrate();}
    @BeforeEach void prepare(){fixture.fixture();repository=new StatementSetupRepository(fixture.jdbc);setup=fixture.proxy(new StatementSetupService(repository,fixture.guard,JsonMapper.builder().findAndAddModules().build(),mock(AuditService.class)));}
    Form configuration(){var f=new Form();f.setRequestKey(UUID.randomUUID());f.setReason("Verified reporting setup");var c=new ComponentForm();c.setName("Owner capital");c.setNameSw("Mtaji wa mmiliki");c.setAccounts("311001");f.getComponents().add(c);var n=new NoteForm();n.setNumber(1);n.setTitle("Capital");n.setIncludeProfit(true);n.setAccounts("311001");f.getNotes().add(n);var text=new NoteForm();text.setNumber(2);text.setTitle("Basis of preparation");text.setBasis("NARRATIVE");text.setText("Synthetic disclosure, not an institution policy.");f.getNotes().add(text);return f;}
    @Test void configChangesNeverRewriteOldSnapshotsOrPostedJournals(){
        UUID old=fixture.preview();var snapshot=fixture.statements.get(fixture.actor,old).snapshot();var journalCount=fixture.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,fixture.institution);var f=configuration();setup.save(fixture.actor,f);setup.save(fixture.actor,f);
        assertThat(setup.edit(fixture.actor).getVersion()).isEqualTo(1);var newer=fixture.statements.get(fixture.actor,fixture.preview()).snapshot();assertThat(newer.setup().version()).isEqualTo(1);assertThat(newer.equityColumns()).contains("component:0");assertThat(newer.setup().notes()).hasSize(2);assertThat(fixture.statements.get(fixture.actor,old).snapshot()).isEqualTo(snapshot);assertThat(fixture.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,fixture.institution)).isEqualTo(journalCount);
        assertThatThrownBy(()->fixture.jdbc.update("update statement_setup_version set reason='overwrite' where sacco_id=?",fixture.institution)).hasMessageContaining("immutable");
    }
    @Test void classificationIsScopedAppendOnlyAndFrozenPerReport(){
        var journal=setup.journals(fixture.actor,new Parameters(fixture.from,fixture.through,false),0).getFirst();UUID old=fixture.preview();setup.classify(fixture.actor,journal.id(),0,Category.CONTRIBUTION,"Owner funding evidence");setup.classify(fixture.actor,journal.id(),0,Category.CONTRIBUTION,"Owner funding evidence");
        assertThat(fixture.statements.get(fixture.actor,old).snapshot().equity()).extracting(EquityRow::label).contains("fs.movement.UNCLASSIFIED");
        var current=fixture.statements.get(fixture.actor,fixture.preview()).snapshot();assertThat(current.equity()).extracting(EquityRow::label).contains("fs.movement.CONTRIBUTION").doesNotContain("fs.movement.UNCLASSIFIED");assertThat(current.equity().getLast().total()).isEqualByComparingTo("1416.67");
        assertThatThrownBy(()->fixture.jdbc.update("delete from statement_equity_classification where sacco_id=?",fixture.institution)).hasMessageContaining("immutable");
        var foreign=new AppUserPrincipal(Member.builder().id(fixture.actor.getMemberId()).saccoId(fixture.institution).stationId("B2").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class));
        assertThat(setup.journals(foreign,new Parameters(fixture.from,fixture.through,false),0)).isEmpty();assertThatThrownBy(()->setup.classify(foreign,journal.id(),0,Category.CONTRIBUTION,"Outside branch")).isInstanceOf(AccessDeniedException.class);
    }
    @Test void closingJournalPreservesProfitAndDoesNotCreateMoney(){
        var voucher=fixture.post(fixture.income,fixture.capital,"416.67","Transfer earned profit");setup.classify(fixture.actor,voucher.journalId(),0,Category.EARNINGS_TRANSFER,"Approved closing transfer");
        var s=fixture.statements.get(fixture.actor,fixture.preview()).snapshot();assertThat(s.income().getLast().current()).isEqualByComparingTo("416.67");assertThat(s.equity().get(1).total()).isEqualByComparingTo("416.67");assertThat(s.equity().getLast().total()).isEqualByComparingTo("1416.67");assertThat(s.checks()).extracting(Check::key).doesNotContain("equity");
    }
    @Test void conflictingRetryAndConcurrentSetupSavesCannotLoseHistory()throws Exception{
        var f=configuration();setup.save(fixture.actor,f);f.setReason("Changed payload");assertThatThrownBy(()->setup.save(fixture.actor,f)).hasMessage("fs.error.retry");
        var first=configuration();first.setVersion(1);var second=configuration();second.setVersion(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var tasks=List.<java.util.concurrent.Callable<Boolean>>of(()->attempt(first),()->attempt(second));var result=pool.invokeAll(tasks);assertThat(List.of(result.get(0).get(),result.get(1).get())).containsExactlyInAnyOrder(true,false);}
        assertThat(setup.edit(fixture.actor).getVersion()).isEqualTo(2);
    }
    @Test void historicalPayloadWithoutNewFieldsStillReads(){
        UUID id=fixture.preview();String payload=fixture.jdbc.queryForObject("select payload from statement_run where id=?",String.class,id);var mapper=JsonMapper.builder().findAndAddModules().build();var tree=(tools.jackson.databind.node.ObjectNode)mapper.readTree(payload);tree.put("version",2);tree.remove("setup");tree.remove("previousEquity");tree.remove("equityMovements");
        var old=mapper.treeToValue(tree,Snapshot.class);assertThat(old.version()).isEqualTo(2);assertThat(old.setup()).isNull();assertThat(old.previousEquity()).isNull();assertThat(old.equityMovements()).isNull();assertThat(old.balances()).isNotEmpty();
    }
    @Test void missingConfiguredAccountFailsBeforeAnyFiguresAreSaved(){var mapper=JsonMapper.builder().findAndAddModules().build();var setup=new Setup(1,List.of(new Component("Missing account","",List.of(UUID.randomUUID()),false)),List.of(),List.of());repository.save(fixture.actor,setup,mapper.writeValueAsString(setup),UUID.randomUUID(),"test-only","Missing account fixture");assertThatThrownBy(()->fixture.preview()).hasMessage("fs.error.setupAccounts");}
    @Test void clearingConfiguredNotesDoesNotRestoreLegacyNotes(){var f=configuration();f.getNotes().clear();setup.save(fixture.actor,f);var snapshot=fixture.statements.get(fixture.actor,fixture.preview()).snapshot();assertThat(snapshot.balances()).allSatisfy(b->assertThat(b.mapping().note()).isZero());var messages=new org.springframework.context.support.ResourceBundleMessageSource();messages.setBasename("messages");assertThat(new StatementPresentation(messages).document(snapshot,Kind.NOTES,snapshot.balances(),Locale.ENGLISH).rows()).isEmpty();}
    boolean attempt(Form f){try{setup.save(fixture.actor,f);return true;}catch(IllegalArgumentException e){assertThat(e).hasMessage("fs.error.setupStale");return false;}}
}
