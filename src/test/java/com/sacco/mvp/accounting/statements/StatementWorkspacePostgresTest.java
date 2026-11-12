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
        var audit=mock(AuditService.class);var json=JsonMapper.builder().findAndAddModules().build();statements=proxy(new StatementService(new StatementRepository(jdbc),new com.sacco.mvp.accounting.statements.repository.StatementSetupRepository(jdbc),guard,json,clock,audit,manager,new ReportExportLimiter(4)));vouchers=proxy(new VoucherService(new VoucherRepository(jdbc),guard,clock,audit,json,new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(new com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository(jdbc,json),guard)));
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
    UUID preview(){return statements.generate(actor,new Parameters(from,through,false),UUID.randomUUID());}
    @Test void figuresReconcileAndFreezeMembershipAcrossLateBackdatedPostings(){UUID id=preview();var initial=statements.get(actor,id).snapshot();assertThat(initial.position().getLast().current()).isEqualByComparingTo("1416.67");assertThat(initial.income().getLast().current()).isEqualByComparingTo("416.67");assertThat(initial.equity().getLast().total()).isEqualByComparingTo("1416.67");assertThat(initial.checks()).extracting(Check::key).contains("coverage","equityMovements","notesSetup");
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
        var selected=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,OffsetDateTime.now(),bank,bank,client,"",0);
        assertThat(reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.CASHBOOK,selected,false).rows())
            .singleElement().satisfies(row->assertThat(row.get("clientNumber")).isEqualTo(clientNumber));
        jdbc.update("update members set member_no=?,station_id='B2' where id=?","CHANGED-"+client,client);
        for(UUID account:List.of(bank,capital))assertThat(statements.activity(actor,run,account,0)).filteredOn(a->a.journalId().equals(posted.journalId())).singleElement().satisfies(a->assertThat(a.accountNumber()).isEqualTo(clientNumber));
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
    @Test void ledgerLayoutRetainsEarlierReferencesButRegeneratesAccountNumberExports(){
        UUID id=preview();var repository=new StatementRepository(jdbc);byte[] old="Reference column".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbc.update("insert into statement_run_artifact(run_id,kind,format,language,bytes,checksum) values(?,'LEDGER_V2','PDF','en',?,?)",id,old,StatementService.sha(old));
        assertThat(repository.artifact(id,Kind.LEDGER,"PDF","en")).isEmpty();byte[] current="Account number column".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        repository.artifact(id,Kind.LEDGER,"PDF","en",current,StatementService.sha(current));assertThat(repository.artifact(id,Kind.LEDGER,"PDF","en").orElseThrow().bytes()).isEqualTo(current);
        assertThat(jdbc.queryForObject("select bytes from statement_run_artifact where run_id=? and kind='LEDGER_V2'",byte[].class,id)).isEqualTo(old);
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
    @Test void cashBankChoicesAndRangesUseTheSameChartFamilyForBothReports() {
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
        var choices=reports.accounts(actor,"",0,List.of());
        assertThat(choices).extracting(com.sacco.mvp.accounting.management.dto.ManagementReports.Choice::id).containsExactly(bank,middle,end,olderBank);
        assertThat(choices.getFirst().label()).isEqualTo("111001 — Bank");
        assertThat(reports.accounts(actor,"Mobile",0,List.of())).extracting(com.sacco.mvp.accounting.management.dto.ManagementReports.Choice::id).containsExactly(end);
        assertThat(reports.accounts(actor,"",0,List.of(olderBank))).extracting(com.sacco.mvp.accounting.management.dto.ManagementReports.Choice::id).containsExactly(olderBank);
        assertThat(reports.accounts(actor,"",0,List.of(other))).isEmpty();
        var cutoff=OffsetDateTime.now();
        var range=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,middle,end,null,"",0);
        assertThat(reports.read(actor,daily,range,false).count()).isEqualTo(2);
        assertThat(reports.read(actor,cashbook,range,true).count()).isEqualTo(2);
        var oneSided=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,middle,null,null,"",0);
        assertThat(reports.read(actor,daily,oneSided,false).count()).isEqualTo(2);
        assertThat(reports.read(actor,cashbook,oneSided,false).count()).isEqualTo(3);
        var upperOnly=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,null,middle,null,"",0);
        assertThat(reports.read(actor,daily,upperOnly,false).count()).isEqualTo(2);
        var unfiltered=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,null,null,null,"",0);
        assertThat(reports.read(actor,daily,unfiltered,false).count()).isEqualTo(4);
        assertThat(reports.read(actor,cashbook,unfiltered,false).count()).isEqualTo(8);
        var existingBankOnly=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,olderBank,olderBank,null,"",0);
        assertThat(reports.read(actor,cashbook,existingBankOnly,false).totals().get("moneyIn")).isEqualByComparingTo("25.00");
        var invalid=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,other,null,null,"",0);
        assertThat(reports.read(actor,daily,invalid,false).count()).isZero();
        var foreign=new AppUserPrincipal(Member.builder().id(actor.getMemberId()).saccoId("OTHER").stationId("B1").position(Position.ACCOUNTANT).status(com.sacco.mvp.domain.MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of());
        assertThat(reports.accounts(foreign,"",0,List.of())).isEmpty();
        UUID client=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,'B2',?,'Other branch client','ACTIVE','MEMBER',now(),true,'test-only')",client,institution,"CL-"+client);
        assertThat(reports.clients(actor,"",0,client)).isEmpty();
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
