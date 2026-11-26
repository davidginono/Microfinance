package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.repository.StatementRepository;
import com.sacco.mvp.accounting.statements.service.*;
import com.sacco.mvp.accounting.dto.VoucherDtos;
import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.accounting.service.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_STATEMENTS_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_statement_workspace_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementWorkspacePostgresTest {
    JdbcTemplate jdbc;DataSourceTransactionManager manager;StatementService statements;VoucherService vouchers;GeneralLedgerService guard;ApplicationClock clock;
    AppUserPrincipal actor;String institution,staffNumber;UUID bank,capital,investments,income;
    final LocalDate from=LocalDate.of(2026,1,1),through=LocalDate.of(2026,1,31);
    @BeforeAll void migrate(){var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_STATEMENTS_TEST_URL"),"microfinance_test","");Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);}
    @BeforeEach void fixture(){
        institution="STATEMENT-"+UUID.randomUUID();UUID staff=UUID.randomUUID();staffNumber="FS"+staff.toString().substring(0,8).toUpperCase(Locale.ROOT);
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Statement demonstration institution',true,now(),now())",institution);
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,address_location,created_at,updated_at) values(?,?,'B1',true,'ACTIVE','Main branch',now(),now()),(?,?,'B2',true,'ACTIVE','Other branch',now(),now())",UUID.randomUUID(),institution,UUID.randomUUID(),institution);
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,staff_no,full_name,status,position,staff_access_status,created_at,is_member,password_hash) values(?,?,'B1',?,?,'Statement accountant','ACTIVE','ACCOUNTANT','ACTIVE',now(),false,?)",staff,institution,staffNumber,staffNumber,new BCryptPasswordEncoder().encode("StatementWorkspace-LocalOnly-2026!"));
        jdbc.batchUpdate("insert into member_access_claims(member_id,claim_name) values(?,?) on conflict do nothing",Arrays.stream(UserClaim.values()).filter(UserClaim::isAvailable).map(c->new Object[]{staff,c.name()}).toList());
        actor=new AppUserPrincipal(Member.builder().id(staff).saccoId(institution).stationId("B1").memberNo(staffNumber).staffNo(staffNumber).fullName("Statement accountant").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class));
        guard=mock(GeneralLedgerService.class);clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(LocalDate.of(2026,10,15));when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-15T12:00:00+03:00"));
        var audit=mock(AuditService.class);var json=JsonMapper.builder().findAndAddModules().build();statements=proxy(new StatementService(new StatementRepository(jdbc),new com.sacco.mvp.accounting.statements.repository.StatementSetupRepository(jdbc),guard,json,clock,audit,manager,new ReportExportLimiter(4)));vouchers=proxy(new VoucherService(new VoucherRepository(jdbc),guard,clock,audit,json,new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(new com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository(jdbc,json),guard),new com.sacco.mvp.accounting.statements.service.EquityMovementService(new com.sacco.mvp.accounting.statements.repository.StatementSetupRepository(jdbc),guard,audit,json)));
        UUID asset=group("100000","Assets","ASSET",null),equity=group("300000","Equity","EQUITY",null),revenue=group("500000","Income","INCOME",null);
        UUID current=group("110000","Current assets","ASSET",asset),noncurrent=group("120000","Non-current assets","ASSET",asset);
        jdbc.update("insert into gl_account_reporting_section values(?,?,'CURRENT_ASSETS'),(?,?,'NON_CURRENT_ASSETS')",current,institution,noncurrent,institution);
        group("111000","Bank","ASSET",current);group("121000","Investments","ASSET",noncurrent);
        group("311000","Owner capital","EQUITY",group("310000","Equity groups","EQUITY",equity));
        group("511000","Interest income","INCOME",group("510000","Operating income","INCOME",revenue));
        bank=account("111001","Bank","ASSET","BANK");capital=account("311001","Owner capital","EQUITY","CAPITAL");investments=account("121001","Investments","ASSET","OTHER");income=account("511001","Interest income","INCOME","INCOME");
        map(bank,"Bank","CURRENT_ASSETS","OPERATING",0);map(capital,"Owner capital","EQUITY","FINANCING",1);map(investments,"Investments","NON_CURRENT_ASSETS","INVESTING",3);map(income,"Interest income","INCOME","OPERATING",2);
        post(bank,capital,"1000.00","Capital funding");post(investments,bank,"100.00","Investments");post(bank,income,"416.67","Interest receipt");
    }
    @SuppressWarnings("unchecked") <T>T proxy(T service){var factory=new ProxyFactory(service);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();}
    UUID group(String code,String name,String type,UUID parent) {UUID id=UUID.randomUUID();jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,parent_id,maker_id,created_at) values(?,?,?,?,?,?,'HEADING','OTHER',?,?,now())",id,institution,code,name,type,Set.of("ASSET","EXPENSE").contains(type)?"DEBIT":"CREDIT",parent,actor.getMemberId());return id;}
    UUID account(String code,String name,String type,String purpose){UUID id=UUID.randomUUID();UUID parent=jdbc.queryForObject("select id from gl_account where sacco_id=? and code=?",UUID.class,institution,code.substring(0,3)+"000");jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,parent_id,maker_id,created_at) values(?,?,?,?,?,?,'POSTING',?,?,?,now())",id,institution,code,name,type,Set.of("ASSET","EXPENSE").contains(type)?"DEBIT":"CREDIT",purpose,parent,actor.getMemberId());return id;}
    // Historical mappings remain read-only compatibility for cash-flow/notes; their line and section no longer govern reports.
    void map(UUID id,String line,String section,String cash,int note){jdbc.update("insert into statement_account_mapping(account_id,sacco_id,line_name,section,cash_section,equity_component,note_no,note_text,revision,updated_by,updated_at) values(?,?,?,?,?,'Owner capital',?,'Synthetic legacy note',?,?,now())",id,institution,line,section,cash,note,UUID.randomUUID(),actor.getMemberId());}
    VoucherDtos.Voucher post(UUID debit,UUID credit,String amount,String description){var f=new VoucherDtos.Form();f.setEffectiveDate(LocalDate.of(2026,1,10));f.setParty("Synthetic counterparty");f.setDescription(description);f.setReference(UUID.randomUUID().toString());f.setEvidence("Synthetic test evidence");var row=new VoucherDtos.RowForm();row.setDebitAccountId(debit);row.setCreditAccountId(credit);row.setAmount(new BigDecimal(amount));row.setDescription(description);f.setRows(List.of(row));return vouchers.post(actor,VoucherDtos.Type.JOURNAL,f);}
    @Test void directGeneralLedgerUsesTheScopedPostingAccountAndBranch(){
        var choices=statements.ledgerAccounts(actor,"111",0,null);
        assertThat(choices).containsExactly(new LedgerAccount(bank,"111001 — Bank"));
        UUID heading=jdbc.queryForObject("select parent_id from gl_account where id=?",UUID.class,bank);
        var outside=mock(AppUserPrincipal.class);
        when(outside.getSaccoId()).thenReturn("OTHER-"+institution);
        assertThat(statements.ledgerAccounts(outside,"",0,bank)).isEmpty();
        assertThatThrownBy(()->statements.generateLedger(outside,from,through,bank,UUID.randomUUID())).hasMessage("fs.error.selection");
        assertThatThrownBy(()->statements.generateLedger(actor,from,through,heading,UUID.randomUUID())).hasMessage("fs.error.selection");
        assertThat(jdbc.queryForObject("select count(*) from statement_run where sacco_id=?",Integer.class,institution)).isZero();

        UUID key=UUID.randomUUID(),run=statements.generateLedger(actor,from,through,bank,key);
        assertThat(statements.generateLedger(actor,from,through,bank,key)).isEqualTo(run);
        assertThat(statements.get(actor,run).institutionWide()).isFalse();
        assertThat(statements.activity(actor,run,bank,0)).hasSize(3);
        doThrow(new AccessDeniedException("Revoked")).when(guard).requireActor(actor,"FINANCIAL_REPORTS_VIEW");
        assertThatThrownBy(()->statements.ledgerAccounts(actor,"",0,null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->statements.generateLedger(actor,from,through,bank,key)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void allAccountsIncludeUnusedAccountsAndSearchFrozenActivityBeforePaging(){
        UUID unused=account("111002","Unused bank","ASSET","BANK");
        var voucher=post(bank,capital,"1.50","Voucher search fixture");
        var run=statements.get(actor,statements.generateLedger(actor,from,through,null,UUID.randomUUID()));
        assertThat(statements.ledgerRows(actor,run,null,0,"")).contains(new LedgerRow(unused,null));
        assertThat(statements.ledgerRows(actor,run,null,0,"Unused bank")).containsExactly(new LedgerRow(unused,null));
        var matches=statements.ledgerRows(actor,run,null,0,voucher.number());
        assertThat(matches).extracting(LedgerRow::accountId).containsExactly(bank,capital);
        assertThat(matches).allSatisfy(row->assertThat(row.activity().voucherNumber()).isEqualTo(voucher.number()));
        var reports=new com.sacco.mvp.accounting.reports.ManagementReportRepository(
            new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var params=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(
            from,through,run.snapshot().generatedAt(),bank,bank,null,"",0);
        var kind=com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.CASHBOOK;
        var complete=reports.read(actor,kind,params,false);
        var matching=reports.read(actor,kind,params,voucher.number().toLowerCase(Locale.ROOT));
        assertThat(matching.rows()).singleElement().satisfies(row->assertThat(row.get("reference")).isEqualTo(voucher.number()));
        assertThat(matching.totals()).isEqualTo(complete.totals());
        var after=post(bank,capital,"1.75","Posted after capture");
        assertThat(statements.ledgerRows(actor,run,null,0,after.number())).isEmpty();
    }
    UUID preview(){return statements.generate(actor,new Parameters(from,through,false),UUID.randomUUID());}
    @Test void figuresReconcileAndFreezeMembershipAcrossLateBackdatedPostings(){UUID id=preview();var initial=statements.get(actor,id).snapshot();assertThat(initial.version()).isEqualTo(5);assertThat(initial.cashFlow().getFirst().key()).isEqualTo("cashSurplus");assertThat(initial.cashFlow().getFirst().current()).isEqualByComparingTo("416.67");assertThat(initial.position().getLast().current()).isEqualByComparingTo("1416.67");assertThat(initial.income().getLast().current()).isEqualByComparingTo("416.67");assertThat(initial.equity().getLast().total()).isEqualByComparingTo("1416.67");assertThat(initial.checks()).extracting(Check::key).contains("coverage","equityMovements","notesSetup");
        post(bank,income,"10.00","Later backdated receipt");assertThat(statements.get(actor,id).snapshot()).isEqualTo(initial);assertThat(statements.activity(actor,id,income,0)).hasSize(1);assertThat(statements.get(actor,preview()).snapshot().income().getLast().current()).isEqualByComparingTo("426.67");
    }
    @Test void exactInvestmentTrialAndLedgerUseTheFrozenMembership(){UUID id=preview();var s=statements.get(actor,id).snapshot();var row=s.position().stream().filter(r->r.label().equals("Investments")).findFirst().orElseThrow();assertThat(statements.selected(s,Kind.POSITION,row.key())).extracting(Balance::id).containsExactly(investments);assertThat(statements.activity(actor,id,investments,0)).hasSize(1);}
    @Test void unlinkedVoucherReferencesAreNotUsedAsSubledgerNumbers(){
        var rows=statements.activity(actor,preview(),bank,0);assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row->assertThat(row.accountNumber()).isNull());
    }
    @Test void clientNumberSnapshotAppearsOnEveryOrdinaryVoucherLedgerAndSurvivesClientChanges(){
        UUID client=UUID.randomUUID();String clientNumber="CL-"+client; jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,'B1',?,'Synthetic client','ACTIVE','MEMBER',now(),true,'test-only')",client,institution,clientNumber);
        var f=new VoucherDtos.Form();f.setEffectiveDate(from);f.setParty("Synthetic counterparty");f.setReference("CLIENT-REF");f.setClientId(client);
        var line=new VoucherDtos.RowForm();line.setDescription("Synthetic client transaction");line.setAmount(new BigDecimal("3.00"));line.setDebitAccountId(bank);line.setCreditAccountId(capital);f.setRows(List.of(line));
        var posted=vouchers.post(actor,VoucherDtos.Type.JOURNAL,f);UUID run=preview();
        var reports=new com.sacco.mvp.accounting.reports.ManagementReportRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        assertThat(reports.clients(actor,clientNumber,0,null,false)).extracting(c->c.id()).containsExactly(client);
        var selected=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,OffsetDateTime.now(),bank,bank,client,"",0);
        assertThat(reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.CASHBOOK,selected,false).rows())
            .singleElement().satisfies(row->assertThat(row.get("clientNumber")).isEqualTo(clientNumber));
        jdbc.update("update members set member_no=?,station_id='B2' where id=?","CHANGED-"+client,client);
        for(UUID account:List.of(bank,capital))assertThat(statements.activity(actor,run,account,0)).filteredOn(a->a.journalId().equals(posted.journalId())).singleElement().satisfies(a->{
            assertThat(a.accountNumber()).isEqualTo(clientNumber);
            assertThat(a.voucherNumber()).isEqualTo(posted.number());
        });
    }
    @Test void retriesMappingsSavingAndDatabaseEvidenceProtections(){UUID key=UUID.randomUUID();UUID id=statements.generate(actor,new Parameters(from,through,false),key);assertThat(statements.generate(actor,new Parameters(from,through,false),key)).isEqualTo(id);assertThatThrownBy(()->statements.generate(actor,new Parameters(from.plusDays(1),through,false),key)).hasMessage("fs.error.retry");statements.save(actor,id);assertThat(statements.get(actor,id).saved()).isTrue();
        jdbc.update("update statement_account_mapping set line_name='Ignored override',section='CURRENT_ASSETS' where account_id=?",investments);
        assertThat(statements.get(actor,id).snapshot().position()).extracting(Row::label).contains("Investments");
        assertThat(statements.get(actor,preview()).snapshot().position()).filteredOn(r->r.label().equals("Investments")).singleElement().satisfies(r->assertThat(r.section()).isEqualTo("NON_CURRENT_ASSETS"));
        assertThatThrownBy(()->jdbc.update("update statement_run set payload='{}' where id=?",id)).hasMessageContaining("immutable");assertThatThrownBy(()->jdbc.update("delete from statement_run_journal where run_id=?",id)).hasMessageContaining("immutable");assertThatThrownBy(()->jdbc.update("insert into statement_run_journal select run_id,journal_id from statement_run_journal where run_id=? limit 1",id)).hasMessageContaining("frozen");
    }
    @Test void branchTenantAndRevokedExportChecksApplyToSavedRuns(){UUID id=preview();var foreign=new AppUserPrincipal(Member.builder().id(actor.getMemberId()).saccoId(institution).stationId("B2").position(Position.ACCOUNTANT).status(com.sacco.mvp.domain.MemberStatus.ACTIVE).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class));assertThatThrownBy(()->statements.get(foreign,id)).isInstanceOf(AccessDeniedException.class);doThrow(new AccessDeniedException("revoked")).when(guard).requireActor(actor,"FINANCIAL_REPORTS_EXPORT");assertThatThrownBy(()->statements.artifact(actor,id,Kind.POSITION,"PDF","en")).isInstanceOf(AccessDeniedException.class);}
    @Test void institutionAggregationDoesNotGrantCrossBranchLedgerAccess(){UUID id=statements.generate(actor,new Parameters(from,through,true),UUID.randomUUID());assertThatThrownBy(()->statements.activity(actor,id,bank,0)).isInstanceOf(AccessDeniedException.class);doThrow(new AccessDeniedException("revoked institution grant")).when(guard).requireActor(actor,"FINANCIAL_REPORTS_INSTITUTION");assertThat(statements.history(actor,0,"newest")).extracting(com.sacco.mvp.accounting.statements.repository.StatementRepository.Stored::id).doesNotContain(id);}
    @Test void concurrentIdenticalRequestsReturnOneFrozenSnapshot()throws Exception{UUID key=UUID.randomUUID();try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var tasks=List.<java.util.concurrent.Callable<UUID>>of(()->statements.generate(actor,new Parameters(from,through,false),key),()->statements.generate(actor,new Parameters(from,through,false),key));var results=pool.invokeAll(tasks);assertThat(results.getFirst().get()).isEqualTo(results.getLast().get());}}
    @Test void ledgerArtifactsRetainThePreviousLayoutWhenAddingVoucherNumbers(){
        UUID id=preview();var repository=new StatementRepository(jdbc);byte[] old="Account number column".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbc.update("insert into statement_run_artifact(run_id,kind,format,language,bytes,checksum) values(?,'LEDGER_V5','PDF','en',?,?)",id,old,StatementService.sha(old));
        assertThat(repository.artifact(id,Kind.LEDGER,"PDF","en")).isEmpty();byte[] current="Voucher / receipt number column".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        repository.artifact(id,Kind.LEDGER,"PDF","en",current,StatementService.sha(current));assertThat(repository.artifact(id,Kind.LEDGER,"PDF","en").orElseThrow().bytes()).isEqualTo(current);
        assertThat(jdbc.queryForObject("select bytes from statement_run_artifact where run_id=? and kind='LEDGER_V5'",byte[].class,id)).isEqualTo(old);
    }
    @Test void simplifiedLayoutKeepsEarlierArtifactsAndServesNewDocuments(){
        UUID id=preview();byte[] previous="previous layout".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbc.update("insert into statement_run_artifact(run_id,kind,format,language,bytes,checksum) values(?,'POSITION','PDF','en',?,?)",id,previous,StatementService.sha(previous));
        assertThat(statements.artifact(actor,id,Kind.POSITION,"PDF","en")).isEmpty();
        byte[] current="simplified layout".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(statements.retain(actor,id,Kind.POSITION,"PDF","en",current)).isEqualTo(current);
        assertThat(jdbc.queryForObject("select count(*) from statement_run_artifact where run_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select bytes from statement_run_artifact where run_id=? and kind='POSITION'",byte[].class,id)).isEqualTo(previous);
    }
    @Test void familyTotalsContainExactPostingMembershipAndZeroFamiliesDisappear() {
        UUID second=account("111002","Second bank","ASSET","BANK");post(second,capital,"50.00","Second bank funding");
        var snapshot=statements.get(actor,preview()).snapshot();var row=snapshot.position().stream().filter(r->r.code().equals("111000")).findFirst().orElseThrow();
        assertThat(row.accounts()).containsExactlyInAnyOrder(bank,second);assertThat(row.current()).isEqualByComparingTo("1366.67");
        assertThat(snapshot.position()).noneMatch(r->r.code().equals("111001")||r.code().equals("111002"));
        assertThat(statements.selected(snapshot,Kind.POSITION,row.key())).extracting(Balance::id).containsExactlyInAnyOrder(bank,second);
    }
    @Test void dailyCollectionsIncludesTransportationPaymentsAndNonCashJournals() {
        var daily=com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DAILY_COLLECTIONS;
        var reports=new com.sacco.mvp.accounting.reports.ManagementReportRepository(
            new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        UUID expenseGroup=group("600000","Expenses","EXPENSE",null);
        group("610000","Operating expenses","EXPENSE",expenseGroup);
        UUID transportation=account("610101","Transportation","EXPENSE","OTHER");
        var form=new VoucherDtos.Form();
        form.setEffectiveDate(from);
        form.setMoneyAccountId(bank);
        form.setParty("Synthetic transport provider");
        form.setDescription("Staff costs");
        form.setReference("TRANSPORT-TEST");
        form.setEvidence("Synthetic invoice");
        var row=new VoucherDtos.RowForm();
        row.setDescription("Transportation expense");
        row.setAmount(new BigDecimal("25.00"));
        row.setDebitAccountId(transportation);
        form.setRows(List.of(row));
        var filters=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(
            from,through,OffsetDateTime.now().plusSeconds(1),null,null,null,"",0);
        var baseline=reports.read(actor,daily,filters,false);
        var payment=vouchers.post(actor,VoucherDtos.Type.PAYMENT,form);
        var journal=post(transportation,capital,"10.00","Non-cash adjustment");
        var all=reports.read(actor,daily,filters,false);
        assertThat(all.rows()).filteredOn(line->payment.number().equals(line.get("documentNumber"))).hasSize(2)
            .allSatisfy(line->assertThat(line).containsEntry("transaction","Transportation expense"))
            .anySatisfy(line->assertThat((BigDecimal)line.get("credit")).isEqualByComparingTo("25.00"));
        assertThat(all.rows()).filteredOn(line->journal.number().equals(line.get("documentNumber"))).hasSize(2)
            .allSatisfy(line->assertThat(line).doesNotContainKeys("moneyIn","moneyOut"));
        assertThat(all.totals()).containsOnlyKeys("debit","credit");
        assertThat(all.totals().get("debit")).isEqualByComparingTo(baseline.totals().get("debit").add(new BigDecimal("35.00")));
        assertThat(all.totals().get("credit")).isEqualByComparingTo(baseline.totals().get("credit").add(new BigDecimal("35.00")));
        var accountOnly=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(
            from,through,filters.cutoff(),transportation,transportation,null,"",0);
        assertThat(reports.read(actor,daily,accountOnly,true).rows()).hasSize(2);
    }

    @Test void cashbookClassifiesEachCashPostingAgainstItsOwnVoucherTransaction() throws Exception {
        UUID expenseRoot=group("600000","Expenses","EXPENSE",null);
        group("610000","Operating expenses","EXPENSE",expenseRoot);
        UUID transport=account("610101","Transport","EXPENSE","OTHER");
        UUID revenue=jdbc.queryForObject("select id from gl_account where sacco_id=? and code='510000'",UUID.class,institution);
        group("512000","Other income","INCOME",revenue);
        UUID fees=account("512001","Service fees","INCOME","INCOME");
        UUID liabilities=group("200000","Liabilities","LIABILITY",null);
        UUID current=group("210000","Current liabilities","LIABILITY",liabilities);
        group("211000","Payables","LIABILITY",current);
        UUID creditor=account("211001","Trade creditors","LIABILITY","PAYABLE");
        var form=new VoucherDtos.Form();
        form.setEffectiveDate(from);
        form.setParty("Synthetic counterparty");
        form.setReference("CASHBOOK-SPLIT");
        var partPayment=new VoucherDtos.RowForm();
        partPayment.setDescription("Part payment of transport invoice");
        partPayment.setAmount(new BigDecimal("100.00"));
        partPayment.setEntries(List.of(entry("DEBIT",transport,"100.00"),entry("CREDIT",bank,"40.00"),
            entry("CREDIT",creditor,"60.00")));
        form.setRows(List.of(
            partPayment,
            journalRow(transport,bank,"25.40"),
            journalRow(bank,fees,"16.27"),
            journalRow(bank,creditor,"31.56"),
            journalRow(bank,income,"7.18"),
            journalRow(transport,fees,"10.00"),
            journalRow(creditor,bank,"4.16")));
        var posted=vouchers.post(actor,VoucherDtos.Type.JOURNAL,form);

        var reports=new com.sacco.mvp.accounting.reports.ManagementReportRepository(
            new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var filters=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(
            from,from,OffsetDateTime.now(),bank,bank,null,"",0);
        var kind=com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.CASHBOOK;
        var cashbook=reports.read(actor,kind,filters,false);
        assertThat(cashbook.rows()).hasSize(6);
        String bankCode=jdbc.queryForObject("select code from gl_account where id=?",String.class,bank);
        assertThat(cashbook.rows()).filteredOn(row->new BigDecimal("40.00").equals(row.get("moneyOut")))
            .singleElement().satisfies(row->{
                assertThat(row.get("clientNumber")).isEqualTo(bankCode+", 211001");
                assertThat((BigDecimal)row.get("expenseDr")).isEqualByComparingTo("100.00");
                assertThat((BigDecimal)row.get("otherCreditorsCr")).isEqualByComparingTo("60.00");
            });
        assertThat(cashbook.rows()).filteredOn(row->new BigDecimal("25.40").equals(row.get("moneyOut")))
            .singleElement().satisfies(row->assertThat((BigDecimal)row.get("expenseDr")).isEqualByComparingTo("25.40"));
        assertThat(cashbook.rows()).filteredOn(row->new BigDecimal("16.27").equals(row.get("moneyIn")))
            .singleElement().satisfies(row->{
                assertThat(row.get("clientNumber")).isEqualTo("512001");
                assertThat((BigDecimal)row.get("otherIncomeCr")).isEqualByComparingTo("16.27");
            });
        assertThat(cashbook.rows()).filteredOn(row->new BigDecimal("31.56").equals(row.get("moneyIn")))
            .singleElement().satisfies(row->assertThat((BigDecimal)row.get("otherCreditorsCr")).isEqualByComparingTo("31.56"));
        assertThat(cashbook.totals().get("expenseDr")).isEqualByComparingTo("125.40");
        assertThat(cashbook.totals().get("otherIncomeCr")).isEqualByComparingTo("16.27");
        assertThat(cashbook.totals().get("otherCreditorsCr")).isEqualByComparingTo("91.56");
        assertThat(reports.read(actor,kind,filters,true).totals()).isEqualTo(cashbook.totals());
        assertThat(reports.read(actor,kind,filters,true).rows()).extracting(row->row.get("clientNumber"))
            .containsExactlyElementsOf(cashbook.rows().stream().map(row->row.get("clientNumber")).toList());

        var messages=new org.springframework.context.support.ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        var pdf=new com.sacco.mvp.accounting.management.service.ManagementReportPdf(messages,new ReportExportLimiter(1),clock);
        var result=new com.sacco.mvp.accounting.management.dto.ManagementReports.Result(kind,filters,institution,"Branch",
            com.sacco.mvp.accounting.management.dto.ManagementReports.columns(kind),cashbook.rows(),cashbook.groups(),
            cashbook.totals(),cashbook.count(),cashbook.excluded(),false,bankCode,"");
        try (var document=org.apache.pdfbox.Loader.loadPDF(pdf.build(result,Locale.ENGLISH))) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+"," "))
                .contains("Client No / Account",bankCode,"211001","512001");
        }

        var reversal=vouchers.reverse(actor,VoucherDtos.Type.JOURNAL,posted.id(),UUID.randomUUID(),from,"Correct the journal");
        var after=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,from,OffsetDateTime.now(),bank,bank,null,"",0);
        assertThat(reports.read(actor,kind,after,false).rows())
            .filteredOn(row->reversal.number().equals(row.get("reference")) && new BigDecimal("40.00").equals(row.get("moneyIn")))
            .singleElement().satisfies(row->assertThat(row.get("clientNumber")).isEqualTo("610101"));
    }

    @Test void splitCashReceiptsKeepAmbiguousIncomeUnknownEvenWhenFilteringToOneBank() {
        UUID secondBank=account("111002","Second bank","ASSET","BANK");
        UUID revenue=jdbc.queryForObject("select id from gl_account where sacco_id=? and code='510000'",UUID.class,institution);
        group("512000","Other income","INCOME",revenue);
        UUID fees=account("512001","Service fees","INCOME","INCOME");
        var line=new VoucherDtos.RowForm();
        line.setDescription("Mixed receipt");
        line.setAmount(new BigDecimal("100.00"));
        line.setEntries(List.of(entry("DEBIT",bank,"60.00"),entry("DEBIT",secondBank,"40.00"),
            entry("CREDIT",fees,"30.00"),entry("CREDIT",capital,"70.00")));
        var voucher=new VoucherDtos.Form();
        voucher.setEffectiveDate(from);
        voucher.setParty("Synthetic counterparty");
        voucher.setReference("MIXED-SPLIT");
        voucher.setRows(List.of(line,journalRow(bank,fees,"12.00")));
        vouchers.post(actor,VoucherDtos.Type.JOURNAL,voucher);

        var reports=new com.sacco.mvp.accounting.reports.ManagementReportRepository(
            new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var filters=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(
            from,from,OffsetDateTime.now(),bank,bank,null,"",0);
        var cashbook=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.CASHBOOK,filters,false);
        assertThat(cashbook.rows()).hasSize(2);
        assertThat(cashbook.rows()).filteredOn(row->new BigDecimal("60.00").equals(row.get("moneyIn")))
            .singleElement().satisfies(row->assertThat(row).containsEntry("otherIncomeCr",null));
        assertThat(cashbook.rows()).filteredOn(row->new BigDecimal("12.00").equals(row.get("moneyIn")))
            .singleElement().satisfies(row->assertThat((BigDecimal)row.get("otherIncomeCr")).isEqualByComparingTo("12.00"));
        assertThat(cashbook.totals()).containsEntry("otherIncomeCr",null);
        assertThat(cashbook.groups()).singleElement().satisfies(group->assertThat(group.totals()).containsEntry("otherIncomeCr",null));
        assertThat(cashbook.totals().get("expenseDr")).isZero();
    }

    private VoucherDtos.EntryForm entry(String side,UUID account,String amount) {
        var entry=new VoucherDtos.EntryForm();
        entry.setSide(side);
        entry.setAccountId(account);
        entry.setAmount(new BigDecimal(amount));
        return entry;
    }

    private VoucherDtos.RowForm journalRow(UUID debit,UUID credit,String amount) {
        var line=new VoucherDtos.RowForm();
        line.setDebitAccountId(debit);
        line.setCreditAccountId(credit);
        line.setAmount(new BigDecimal(amount));
        line.setDescription("Cashbook classification");
        return line;
    }

    @Test void dailyCollectionsIncludesAllPostingAccountsAndCashbookUsesTheCashFamily() {
        var reports=new com.sacco.mvp.accounting.reports.ManagementReportRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var cashbook=com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.CASHBOOK;
        var daily=com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DAILY_COLLECTIONS;
        UUID middle=account("111002","Cash", "ASSET","CASH");
        UUID end=account("111003","Mobile money","ASSET","MOBILE_MONEY");
        UUID olderBank=account("111004","Existing bank","ASSET","OTHER");
        post(olderBank,capital,"25.00","Existing bank funding");
        UUID other=account("121002","Legacy bank","ASSET","BANK");
        for(UUID id:List.of(bank,middle,end,other)) {
            var f=new VoucherDtos.Form();f.setEffectiveDate(from);f.setParty("Synthetic payer");
            f.setReference(UUID.randomUUID().toString());f.setMoneyAccountId(id);
            var row=new VoucherDtos.RowForm();row.setDescription("Synthetic collection");row.setAmount(BigDecimal.TEN);
            row.setCreditAccountId(capital);f.setRows(List.of(row));vouchers.post(actor,VoucherDtos.Type.RECEIPT,f);
        }
        var choices=reports.accounts(actor,"",0,List.of(),false);
        assertThat(choices).extracting(com.sacco.mvp.accounting.management.dto.ManagementReports.Choice::id).containsExactly(bank,middle,end,olderBank);
        assertThat(choices.getFirst().label()).isEqualTo("111001 — Bank");
        assertThat(reports.accounts(actor,"Mobile",0,List.of(),false)).extracting(com.sacco.mvp.accounting.management.dto.ManagementReports.Choice::id).containsExactly(end);
        assertThat(reports.accounts(actor,"",0,List.of(olderBank),false)).extracting(com.sacco.mvp.accounting.management.dto.ManagementReports.Choice::id).containsExactly(olderBank);
        assertThat(reports.accounts(actor,"",0,List.of(other),false)).isEmpty();
        var cutoff=OffsetDateTime.now();
        var range=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,middle,end,null,"",0);
        assertThat(reports.read(actor,daily,range,false).count()).isEqualTo(2);
        assertThat(reports.read(actor,cashbook,range,true).count()).isEqualTo(2);
        var oneSided=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,middle,null,null,"",0);
        assertThat(reports.read(actor,daily,oneSided,false).count()).isGreaterThan(3);
        assertThat(reports.read(actor,cashbook,oneSided,false).count()).isEqualTo(3);
        var upperOnly=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,null,middle,null,"",0);
        assertThat(reports.read(actor,daily,upperOnly,false).count()).isEqualTo(5);
        var unfiltered=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,null,null,null,"",0);
        var allPostings=reports.read(actor,daily,unfiltered,false);
        var ledgerCount=jdbc.queryForObject("""
            select count(*) from gl_journal_line l join gl_journal j on j.id=l.journal_id
             where j.sacco_id=? and j.station_id='B1' and j.state='POSTED' and j.effective_date between ? and ?
            """,Long.class,institution,from,through);
        assertThat(allPostings.count()).isEqualTo(ledgerCount);
        assertThat(allPostings.totals().get("debit")).isEqualByComparingTo(allPostings.totals().get("credit"));
        assertThat(reports.read(actor,daily,unfiltered,true).totals()).isEqualTo(allPostings.totals());
        assertThat(reports.accounts(actor,"",0,List.of(other),true)).hasSize(1);
        assertThat(reports.read(actor,cashbook,unfiltered,false).count()).isEqualTo(8);
        var existingBankOnly=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,olderBank,olderBank,null,"",0);
        assertThat(reports.read(actor,cashbook,existingBankOnly,false).totals().get("moneyIn")).isEqualByComparingTo("25.00");
        var invalid=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,other,null,null,"",0);
        assertThat(reports.read(actor,daily,invalid,false).count()).isPositive();
        var foreign=new AppUserPrincipal(Member.builder().id(actor.getMemberId()).saccoId("OTHER").stationId("B1").position(Position.ACCOUNTANT).status(com.sacco.mvp.domain.MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of());
        assertThat(reports.accounts(foreign,"",0,List.of(),false)).isEmpty();
        UUID client=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,'B2',?,'Other branch client','ACTIVE','MEMBER',now(),true,'test-only')",client,institution,"CL-"+client);
        assertThat(reports.clients(actor,"",0,client,false)).isEmpty();
    }
    @Test void legacyIncomeIsFlaggedAndIncludedInIncomeTotals() {
        UUID legacy=UUID.randomUUID();
        jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,maker_id,created_at) values(?,?,'LEGACY-INCOME','Legacy income account','INCOME','CREDIT','POSTING','INCOME',?,now())",legacy,institution,actor.getMemberId());
        post(bank,legacy,"25.00","Legacy income collection");
        var snapshot=statements.get(actor,preview()).snapshot();
        assertThat(snapshot.checks()).extracting(Check::key).contains("mappings");
        assertThat(snapshot.income()).filteredOn(row->row.label().equals("fs.unclassified.income")).singleElement().satisfies(row->{
            assertThat(row.section()).isEqualTo("INCOME");assertThat(row.accounts()).containsExactly(legacy);
        });
        var messages=new org.springframework.context.support.ResourceBundleMessageSource();messages.setBasename("messages");
        var document=new StatementPresentation(messages).document(snapshot,Kind.INCOME,snapshot.balances(),Locale.ENGLISH);
        assertThat(document.rows()).filteredOn(row->row.cells().get(1).text().equals("Total income")).singleElement()
            .satisfies(row->assertThat(row.cells().get(3).amount()).isEqualByComparingTo("441.67"));
        assertThat(snapshot.income().getLast().current()).isEqualByComparingTo("441.67");
    }
    @Test void versionOnePayloadStillOpensWithItsCapturedLineLabelsAndAccounts() {
        var current=statements.get(actor,preview()).snapshot();
        UUID legacyId=UUID.randomUUID();var repository=new StatementRepository(jdbc);
        repository.create(legacyId,actor,current.parameters(),current.generatedAt(),UUID.randomUUID());
        repository.capture(legacyId,actor,current.parameters(),current.generatedAt());
        var mapper=JsonMapper.builder().findAndAddModules().build();
        tools.jackson.databind.node.ObjectNode legacy=mapper.valueToTree(current);
        legacy.put("version",1);legacy.put("id",legacyId.toString());
        legacy.get("balances").forEach(balance->{
            if(balance.path("mapping") instanceof tools.jackson.databind.node.ObjectNode mapping) {
                mapping.remove("groupId");mapping.remove("groupCode");
            }
        });
        ((tools.jackson.databind.node.ObjectNode)legacy.get("position").get(0)).put("label","Original statement line");
        String payload=mapper.writeValueAsString(legacy);
        repository.payload(legacyId,payload,StatementService.sha(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var loaded=statements.get(actor,legacyId).snapshot();
        assertThat(loaded.version()).isEqualTo(1);
        assertThat(loaded.position().getFirst().label()).isEqualTo("Original statement line");
        assertThat(loaded.position().getFirst().accounts()).isEqualTo(current.position().getFirst().accounts());
    }
    @AfterAll void browserFixture()throws Exception{
        if(actor==null)return;var root=java.nio.file.Path.of("target/statement-workspace");java.nio.file.Files.createDirectories(root);UUID id=preview();
        java.nio.file.Files.writeString(root.resolve("fixture.txt"),"staff="+staffNumber+"\ninstitution="+institution+"\nrun="+id+"\n");
        var messages=new org.springframework.context.support.ReloadableResourceBundleMessageSource();messages.setBasename("classpath:messages");messages.setDefaultEncoding("UTF-8");var presentation=new StatementPresentation(messages);var exporter=new StatementExportService(presentation,new ReportExportLimiter(1),new com.sacco.mvp.service.ApplicationClock("Africa/Nairobi"));var snapshot=statements.get(actor,id).snapshot();
        for(var kind:List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL)){
            var document=presentation.document(snapshot,kind,snapshot.balances(),Locale.ENGLISH);byte[] pdf=exporter.export(snapshot,document,"PDF",Locale.ENGLISH);java.nio.file.Files.write(root.resolve(kind+".pdf"),pdf);java.nio.file.Files.write(root.resolve(kind+".xlsx"),exporter.export(snapshot,document,"XLSX",Locale.ENGLISH));
            try(var parsed=org.apache.pdfbox.Loader.loadPDF(pdf)){var image=new org.apache.pdfbox.rendering.PDFRenderer(parsed).renderImageWithDPI(0,110);javax.imageio.ImageIO.write(image,"png",root.resolve(kind+".png").toFile());}
        }
    }
}
