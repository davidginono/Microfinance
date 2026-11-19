package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.dto.VoucherDtos;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementLayoutModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementSetupRepository;
import com.sacco.mvp.accounting.statements.service.*;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_STATEMENTS_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_statement_workspace_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementEquityPostgresTest {
    final StatementWorkspacePostgresTest fixture=new StatementWorkspacePostgresTest();
    StatementSetupRepository repository;StatementLayoutService layouts;EquityMovementService movements;
    @BeforeAll void database(){fixture.migrate();}
    @BeforeEach void prepare(){
        fixture.fixture();repository=new StatementSetupRepository(fixture.jdbc);
        layouts=fixture.proxy(new StatementLayoutService(repository,fixture.guard,JsonMapper.builder().findAndAddModules().build(),mock(AuditService.class)));
        movements=fixture.proxy(new EquityMovementService(repository,fixture.guard,mock(AuditService.class),JsonMapper.builder().findAndAddModules().build()));
    }
    GroupForm configuration(){
        var group=new GroupForm();group.setRequestKey(UUID.randomUUID());group.setReason("Verified reporting layout");group.setName("Owner capital");group.setNameSw("Mtaji wa mmiliki");group.setAccounts(List.of(fixture.capital));layouts.saveGroup(fixture.actor,group);
        var note=new NoteForm();note.setRequestKey(UUID.randomUUID());note.setVersion(1);note.setReason("Capital disclosure");note.setTitle("Capital");note.setBasis("BALANCE");note.setIncludeProfit(true);note.setAccounts(List.of(fixture.capital));layouts.saveNote(fixture.actor,note);
        var text=new NoteForm();text.setRequestKey(UUID.randomUUID());text.setVersion(2);text.setReason("Basis disclosure");text.setNumber(2);text.setTitle("Basis of preparation");text.setText("Synthetic disclosure, not an institution policy.");layouts.saveNote(fixture.actor,text);return group;
    }
    @Test void configChangesNeverRewriteOldSnapshotsOrPostedJournals(){
        UUID old=fixture.preview();var snapshot=fixture.statements.get(fixture.actor,old).snapshot();var count=fixture.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,fixture.institution);
        var first=configuration();layouts.saveGroup(fixture.actor,first);
        assertThat(layouts.overview(fixture.actor).version()).isEqualTo(3);var newer=fixture.statements.get(fixture.actor,fixture.preview()).snapshot();
        assertThat(newer.setup().version()).isEqualTo(3);assertThat(newer.equityColumns()).contains("component:0");assertThat(newer.setup().notes()).hasSize(2);assertThat(fixture.statements.get(fixture.actor,old).snapshot()).isEqualTo(snapshot);assertThat(fixture.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,fixture.institution)).isEqualTo(count);
        assertThatThrownBy(()->fixture.jdbc.update("update statement_setup_version set reason='overwrite' where sacco_id=?",fixture.institution)).hasMessageContaining("immutable");
    }
    @Test void classificationIsScopedAppendOnlyAndFrozenPerReport(){
        var journal=movements.journals(fixture.actor,new Parameters(fixture.from,fixture.through,false),0).getFirst();UUID old=fixture.preview();movements.classify(fixture.actor,journal.id(),0,Category.CONTRIBUTION,"Owner funding evidence");movements.classify(fixture.actor,journal.id(),0,Category.CONTRIBUTION,"Owner funding evidence");
        assertThat(fixture.statements.get(fixture.actor,old).snapshot().equity()).extracting(EquityRow::label).contains("fs.movement.UNCLASSIFIED");
        var current=fixture.statements.get(fixture.actor,fixture.preview()).snapshot();assertThat(current.equity()).extracting(EquityRow::label).contains("fs.movement.CONTRIBUTION").doesNotContain("fs.movement.UNCLASSIFIED");assertThat(current.equity().getLast().total()).isEqualByComparingTo("1416.67");
        assertThatThrownBy(()->fixture.jdbc.update("delete from statement_equity_classification where sacco_id=?",fixture.institution)).hasMessageContaining("immutable");
        var foreign=new AppUserPrincipal(Member.builder().id(fixture.actor.getMemberId()).saccoId(fixture.institution).stationId("B2").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class));
        assertThat(movements.journals(foreign,new Parameters(fixture.from,fixture.through,false),0)).isEmpty();assertThatThrownBy(()->movements.classify(foreign,journal.id(),0,Category.CONTRIBUTION,"Outside branch")).isInstanceOf(AccessDeniedException.class);
    }
    @Test void closingJournalPreservesProfitAndDoesNotCreateMoney(){
        var voucher=fixture.post(fixture.income,fixture.capital,"416.67","Transfer earned profit");movements.classify(fixture.actor,voucher.journalId(),0,Category.EARNINGS_TRANSFER,"Approved closing transfer");
        var s=fixture.statements.get(fixture.actor,fixture.preview()).snapshot();assertThat(s.income().getLast().current()).isEqualByComparingTo("416.67");assertThat(s.equity().get(1).total()).isEqualByComparingTo("416.67");assertThat(s.equity().getLast().total()).isEqualByComparingTo("1416.67");assertThat(s.checks()).extracting(Check::key).doesNotContain("equity");
    }
    @Test void namedTransfersKeepTheirPostedLabelsAfterRenameAndReversal(){
        var reserve=fixture.account("311002","Operations reserve","EQUITY","OTHER");
        var capitalGroup=layouts.group(fixture.actor,-1,null).form();
        capitalGroup.setName("Retained earnings");
        capitalGroup.setAccounts(List.of(fixture.capital));
        capitalGroup.setRetained(true);
        capitalGroup.setReason("Set up retained profit");
        assertThat(layouts.saveGroup(fixture.actor,capitalGroup)).isTrue();
        var reserveGroup=layouts.group(fixture.actor,-1,null).form();
        reserveGroup.setName("Operations reserve");
        reserveGroup.setAccounts(List.of(reserve));
        reserveGroup.setReason("Set up operations reserve");
        assertThat(layouts.saveGroup(fixture.actor,reserveGroup)).isTrue();

        var rowForm=layouts.movementRow(fixture.actor,-1,null);
        rowForm.setName("Profit set aside for operations");
        rowForm.setNameSw("Faida iliyotengwa kwa shughuli");
        rowForm.setCategory(Category.TRANSFER);
        rowForm.setReason("Approved reserve labels");
        assertThat(layouts.saveMovementRow(fixture.actor,rowForm)).isTrue();
        assertThat(layouts.saveMovementRow(fixture.actor,rowForm)).isTrue();
        var other=layouts.movementRow(fixture.actor,-1,null);
        other.setName("Profit set aside for training");
        other.setCategory(Category.TRANSFER);
        other.setReason("Distinguish training allocations");
        assertThat(layouts.saveMovementRow(fixture.actor,other)).isTrue();
        var options=movements.options(fixture.actor,null,true);
        assertThat(options).extracting(option->option.row().name()).containsExactly(rowForm.getName(),other.getName());
        var initial=options.getFirst();
        var posting=transfer(reserve,initial.value(),"75.00");
        assertThat(fixture.vouchers.preview(fixture.actor,VoucherDtos.Type.JOURNAL,posting).equityClassification().row()).isEqualTo(initial.row());
        var voucher=fixture.vouchers.post(fixture.actor,VoucherDtos.Type.JOURNAL,posting);
        assertThat(fixture.vouchers.post(fixture.actor,VoucherDtos.Type.JOURNAL,posting).id()).isEqualTo(voucher.id());
        UUID oldRun=fixture.preview();
        var captured=fixture.statements.get(fixture.actor,oldRun).snapshot();
        assertThat(captured.equity()).filteredOn(r->r.customRow()!=null).singleElement().satisfies(r->{
            assertThat(r.customRow()).isEqualTo(initial.row());
            assertThat(r.values().get("component:0")).isEqualByComparingTo("-75.00");
            assertThat(r.values().get("component:1")).isEqualByComparingTo("75.00");
            assertThat(r.total()).isZero();
        });
        assertThat(captured.checks()).extracting(Check::key).doesNotContain("equity");

        var edit=layouts.movementRow(fixture.actor,0,4);
        edit.setName("Profit set aside for branch operations");
        edit.setNameSw("");
        edit.setReason("Clearer name for new journals");
        assertThat(layouts.saveMovementRow(fixture.actor,edit)).isTrue();
        var renamed=movements.options(fixture.actor,null,true).getFirst();
        assertThat(renamed.row().id()).isEqualTo(initial.row().id());
        assertThat(renamed.value()).isNotEqualTo(initial.value());
        fixture.vouchers.post(fixture.actor,VoucherDtos.Type.JOURNAL,transfer(reserve,renamed.value(),"25.00"));
        fixture.vouchers.reverse(fixture.actor,VoucherDtos.Type.JOURNAL,voucher.id(),UUID.randomUUID(),voucher.effectiveDate(),"Reverse the initial transfer");
        var latest=fixture.statements.get(fixture.actor,fixture.preview()).snapshot();
        var named=latest.equity().stream().filter(r->r.customRow()!=null).toList();
        assertThat(named).extracting(r->r.customRow().name()).containsExactlyInAnyOrder(initial.row().name(),renamed.row().name());
        assertThat(named.stream().filter(r->r.customRow().equals(initial.row())).findFirst().orElseThrow().values().get("component:1")).isZero();
        assertThat(named.stream().filter(r->r.customRow().equals(renamed.row())).findFirst().orElseThrow().values().get("component:1")).isEqualByComparingTo("25.00");
        assertThat(fixture.statements.get(fixture.actor,oldRun).snapshot()).isEqualTo(captured);
        var messages=new org.springframework.context.support.ReloadableResourceBundleMessageSource();
        messages.setBasename("classpath:messages");
        messages.setDefaultEncoding("UTF-8");
        var document=new StatementPresentation(messages).document(captured,Kind.EQUITY,captured.balances(),Locale.forLanguageTag("sw"));
        assertThat(document.rows().toString()).contains(initial.row().nameSw());
    }

    VoucherDtos.Form transfer(UUID reserve,String choice,String amount){
        var form=new VoucherDtos.Form();
        form.setEffectiveDate(fixture.from.plusDays(9));
        form.setReference(UUID.randomUUID().toString());
        form.setEquity(true);
        form.setEquityChoice(choice);
        form.setEquityReason("Approved management allocation");
        var row=form.getRows().getFirst();
        row.setDescription("Move retained profit to the reserve");
        row.setDebitAccountId(fixture.capital);
        row.setCreditAccountId(reserve);
        row.setAmount(new BigDecimal(amount));
        return form;
    }
    @Test void conflictingRetryAndConcurrentFocusedEditsCannotLoseHistory()throws Exception{
        var first=configuration();first.setReason("Changed payload");assertThatThrownBy(()->layouts.saveGroup(fixture.actor,first)).hasMessage("fs.error.retry");
        var one=layouts.group(fixture.actor,0,3).form();one.setName("Capital one");one.setReason("First edit");var two=layouts.group(fixture.actor,0,3).form();two.setName("Capital two");two.setReason("Second edit");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var tasks=List.<java.util.concurrent.Callable<Boolean>>of(()->attempt(one),()->attempt(two));var result=pool.invokeAll(tasks);assertThat(List.of(result.get(0).get(),result.get(1).get())).containsExactlyInAnyOrder(true,false);}
        assertThat(layouts.overview(fixture.actor).version()).isEqualTo(4);
    }
    @Test void historicalPayloadWithoutNewFieldsStillReads(){
        UUID id=fixture.preview();String payload=fixture.jdbc.queryForObject("select payload from statement_run where id=?",String.class,id);var mapper=JsonMapper.builder().findAndAddModules().build();var tree=(tools.jackson.databind.node.ObjectNode)mapper.readTree(payload);tree.put("version",2);tree.remove("setup");tree.remove("previousEquity");tree.remove("equityMovements");
        var old=mapper.treeToValue(tree,Snapshot.class);assertThat(old.version()).isEqualTo(2);assertThat(old.setup()).isNull();assertThat(old.previousEquity()).isNull();assertThat(old.equityMovements()).isNull();assertThat(old.balances()).isNotEmpty();
    }
    @Test void missingConfiguredAccountFailsBeforeAnyFiguresAreSaved(){var mapper=JsonMapper.builder().findAndAddModules().build();var setup=new Setup(1,List.of(new Component("Missing account","",List.of(UUID.randomUUID()),false)),List.of(),List.of());repository.save(fixture.actor,setup,mapper.writeValueAsString(setup),UUID.randomUUID(),"test-only","Missing account fixture");assertThatThrownBy(()->fixture.preview()).hasMessage("fs.error.setupAccounts");}
    @Test void explicitNoteRemovalDoesNotRestoreLegacyNotesOrChangeAnEarlierCapture(){
        configuration();UUID old=fixture.preview();var original=fixture.statements.get(fixture.actor,old).snapshot();
        var last=layouts.removal(fixture.actor,false,1,3).form();last.setReason("Remove narrative fixture");layouts.remove(fixture.actor,false,last);
        var first=layouts.removal(fixture.actor,false,0,4).form();first.setReason("Remove financial fixture");layouts.remove(fixture.actor,false,first);
        var snapshot=fixture.statements.get(fixture.actor,fixture.preview()).snapshot();assertThat(snapshot.balances()).allSatisfy(b->assertThat(b.mapping().note()).isZero());var messages=new org.springframework.context.support.ResourceBundleMessageSource();messages.setBasename("messages");assertThat(new StatementPresentation(messages).document(snapshot,Kind.NOTES,snapshot.balances(),Locale.ENGLISH).rows()).isEmpty();assertThat(fixture.statements.get(fixture.actor,old).snapshot()).isEqualTo(original);
    }
    boolean attempt(GroupForm form){try{layouts.saveGroup(fixture.actor,form);return true;}catch(IllegalArgumentException error){assertThat(error).hasMessage("fs.error.setupStale");return false;}}
}
