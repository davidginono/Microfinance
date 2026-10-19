package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.AccountingLibraryDtos.*;
import com.sacco.mvp.accounting.dto.VoucherDtos.Type;
import com.sacco.mvp.accounting.reconciliation.dto.ReconciliationReport.*;
import com.sacco.mvp.accounting.reconciliation.repository.ReconciliationRepository;
import com.sacco.mvp.accounting.reconciliation.service.ReconciliationService;
import com.sacco.mvp.accounting.repository.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_VOUCHER_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_(vouchers|reconciliation_activity_[a-f0-9]{8})_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReconciliationActivityPostgresTest {
    final VoucherPostgresTest f=new VoucherPostgresTest();
    ReconciliationService reports;ReconciliationRepository repository;AccountingCodeLibraryService library;
    @BeforeAll void start(){f.start();}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){
        f.fixture();var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(f.now.toLocalDate());when(clock.now()).thenAnswer(i->OffsetDateTime.now());
        var guard=new GeneralLedgerService(new GeneralLedgerRepository(f.jdbc),new AccessControlService(),f.audit,clock,f.claims,f.directory,f.institutions);
        repository=new ReconciliationRepository(new NamedParameterJdbcTemplate(f.jdbc));
        reports=new ReconciliationService(repository,guard,clock,new ReportExportLimiter(1));
        library=new AccountingCodeLibraryService(new AccountingCodeLibraryRepository(f.jdbc),guard,f.audit,clock);
    }
    record Link(UUID activity,UUID transaction,UUID template) {}
    UUID activity(String code,String name){var form=new CodeForm();form.setCode(code);form.setName(name);form.setNameSw("Shughuli "+name);return f.tx.execute(s->library.createActivity(f.actor,form));}
    Link configured(String code){
        UUID activity=activity(code,"Configured "+code);var transaction=new CodeForm();transaction.setActivityId(activity);transaction.setCode(code+".POST");transaction.setName("Transaction "+code);transaction.setSourceEvent("MANUAL_JOURNAL");
        UUID id=f.tx.execute(s->library.createTransaction(f.actor,transaction));var template=new TemplateForm();var rule=new RuleForm();rule.setComponent("TOTAL");rule.setDebitCode("EXPENSE");rule.setCreditCode("CAPITAL");template.setRules(List.of(rule));
        f.tx.executeWithoutResult(s->library.saveTemplate(f.actor,id,template));return new Link(activity,id,library.template(f.actor,id).requestKey());
    }
    com.sacco.mvp.accounting.dto.VoucherDtos.Voucher post(Link first,Link second){
        var form=f.form(Type.JOURNAL);var links=Arrays.asList(first,second);
        for(int n=0;n<form.getRows().size();n++){if(links.get(n)!=null){form.getRows().get(n).setTransactionId(links.get(n).transaction());form.getRows().get(n).setTemplateKey(links.get(n).template());}}
        return f.service.post(f.actor,Type.JOURNAL,form);
    }
    Parameters parameters(UUID activity){return new Parameters(f.day,f.now.toLocalDate(),OffsetDateTime.now(),"ALL",0,activity);}
    Result report(UUID activity){return reports.report(f.actor,parameters(activity),false);}
    Result manual(){var p=parameters(null);return reports.report(f.actor,new Parameters(p.from(),p.through(),p.cutoff(),p.component(),0,null,true),false);}
    @Test void manualVouchersRemainVisibleInAllAndCanBeSelectedWithoutIncludingConfiguredTransactions(){
        var a=configured("MANUAL.A");var configured=post(a,a);var manual=post(null,null);
        assertThat(report(null).rows()).extracting(Row::reference).containsOnly(configured.number(),manual.number());
        assertThat(manual().rows()).hasSize(2).allSatisfy(row->{assertThat(row.reference()).isEqualTo(manual.number());assertThat(row.slDebit()).isNull();assertThat(row.debitVariance()).isNull();});
        assertThat(manual().rows().stream().map(Row::glDebit).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(manual.total());
        assertThat(report(a.activity()).rows()).extracting(Row::reference).containsOnly(configured.number());
        var reversed=f.service.reverse(f.actor,Type.JOURNAL,manual.id(),UUID.randomUUID(),f.day.plusDays(1),"Synthetic manual correction");
        assertThat(manual().rows()).extracting(Row::reference).containsOnly(manual.number(),reversed.number());
    }
    @Test void manualAndConfiguredRowsSharingOneJournalAccountDoNotIncludeEachOthersAmounts(){
        var a=configured("MANUAL.MIX");var voucher=post(a,null);
        for(var result:List.of(manual(),report(a.activity()))){
            assertThat(result.rows()).hasSize(2).allSatisfy(row->{assertThat(row.issue()).isEqualTo("ACTIVITY_LINK_UNAVAILABLE");assertThat(row.getAmounts()).containsOnlyNulls();});
        }
        assertThat(report(null).rows()).hasSize(2).allSatisfy(row->assertThat(row.reference()).isEqualTo(voucher.number()));
    }
    @Test void manualFilterIncludesOrdinaryReceiptPaymentAndJournalVouchers(){
        var numbers=new ArrayList<String>();
        for(var type:Type.values())numbers.add(f.service.post(f.actor,type,f.form(type)).number());
        assertThat(manual().rows()).hasSize(6).allSatisfy(row->{assertThat(row.slDebit()).isNull();assertThat(row.debitVariance()).isNull();});
        assertThat(manual().rows()).extracting(Row::reference).containsOnlyElementsOf(numbers);
        assertThat(report(null).rows()).extracting(Row::reference).containsOnlyElementsOf(numbers);
    }
    @Test void configuredActivitiesSharingAccountsDoNotIncludeEachOthersTransactions(){
        var a=configured("ACT.A");var b=configured("ACT.B");var unused=configured("ACT.UNUSED");var first=post(a,a);var second=post(b,b);
        var selected=report(a.activity());assertThat(selected.selectedActivity().id()).isEqualTo(a.activity());assertThat(selected.rows()).hasSize(2);
        assertThat(selected.rows()).allSatisfy(row->{assertThat(row.reference()).isEqualTo(first.number());assertThat(row.identity()).isEqualTo(first.journalId().toString());assertThat(row.issue()).isEqualTo("UNSUPPORTED_ACTIVITY");assertThat(row.slDebit()).isNull();assertThat(row.debitVariance()).isNull();});
        assertThat(selected.rows().stream().map(Row::glDebit).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(first.total());
        assertThat(report(b.activity()).rows()).allSatisfy(row->assertThat(row.reference()).isEqualTo(second.number()));
        assertThat(report(unused.activity()).count()).isZero();
        var exported=reports.report(f.actor,parameters(a.activity()),true);assertThat(exported.rows()).extracting(Row::reference).containsOnly(first.number());assertThat(exported.count()).isEqualTo(selected.count());
        var reversal=f.service.reverse(f.actor,Type.JOURNAL,first.id(),UUID.randomUUID(),f.day.plusDays(1),"Synthetic correction");
        assertThat(report(a.activity()).rows()).extracting(Row::reference).containsOnly(first.number(),reversal.number());
    }
    @Test void mixedActivityRowsOnOneSharedGlAccountAreUnavailableRatherThanLeakingOtherActivityAmounts(){
        var a=configured("MIX.A");var b=configured("MIX.B");post(a,b);
        for(UUID id:List.of(a.activity(),b.activity())){
            var result=report(id);assertThat(result.count()).isEqualTo(2);
            assertThat(result.rows()).allSatisfy(row->{assertThat(row.issue()).isEqualTo("ACTIVITY_LINK_UNAVAILABLE");assertThat(row.getAmounts()).containsOnlyNulls();});
        }
    }
    @Test void catalogSearchIsInstitutionScopedPagedAndRetainsInactiveHistoricalActivities(){
        var ids=new ArrayList<UUID>();for(int n=0;n<28;n++)ids.add(activity("LOOKUP."+n,String.format("Lookup %02d",n)));
        f.jdbc.update("update gl_activity set active=false where id=?",ids.getFirst());
        assertThat(reports.activities(f.actor,"Lookup",0)).hasSize(26);assertThat(reports.activities(f.actor,"Lookup",1)).hasSize(3);
        assertThat(reports.activities(f.actor,"Lookup 00",0)).singleElement().satisfies(a->assertThat(a.id()).isEqualTo(ids.getFirst()));
        var foreign=mock(com.sacco.mvp.security.AppUserPrincipal.class);when(foreign.getSaccoId()).thenReturn("OTHER-INSTITUTION");
        assertThat(repository.activities(foreign,"lookup",0)).isEmpty();assertThat(repository.activity(foreign,ids.getFirst())).isEmpty();
    }
    @Test void unmatchedGlAmountsAreNotAttributedToAnActivity(){
        var a=configured("DAMAGED.A");post(a,a);
        f.tx.executeWithoutResult(status->{
            f.jdbc.execute("create temporary table gl_journal_line on commit drop as select * from public.gl_journal_line where false");
            f.jdbc.update("insert into gl_journal_line select * from public.gl_journal_line where sacco_id=?",f.institution);
            f.jdbc.execute("set local search_path to pg_temp,public");
            f.jdbc.update("update gl_journal_line set debit=debit+7 where account_id=?",f.expense);
            assertThat(report(a.activity()).rows()).filteredOn(row->row.account().equals("EXPENSE")).singleElement().satisfies(row->{
                assertThat(row.issue()).isEqualTo("ACTIVITY_LINK_UNAVAILABLE");assertThat(row.getAmounts()).containsOnlyNulls();
            });
        });
    }
}
