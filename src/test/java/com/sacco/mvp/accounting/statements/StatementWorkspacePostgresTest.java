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
        actor=new AppUserPrincipal(Member.builder().id(staff).saccoId(institution).stationId("B1").memberNo(staffNumber).staffNo(staffNumber).fullName("Statement accountant").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);
        guard=mock(GeneralLedgerService.class);clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(LocalDate.of(2026,10,15));when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-15T12:00:00+03:00"));
        var audit=mock(AuditService.class);var json=JsonMapper.builder().findAndAddModules().build();statements=proxy(new StatementService(new StatementRepository(jdbc),guard,json,clock,audit,manager,new ReportExportLimiter(4)));vouchers=proxy(new VoucherService(new VoucherRepository(jdbc),guard,clock,audit,json,new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(new com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository(jdbc,json),guard)));
        bank=account("110101","Bank","ASSET","BANK");capital=account("310101","Owner capital","EQUITY","CAPITAL");investments=account("110301","Investments","ASSET","OTHER");income=account("410101","Interest income","INCOME","INCOME");
        map(bank,"Bank","CURRENT_ASSETS","OPERATING",0);map(capital,"Owner capital","EQUITY","FINANCING",1);map(investments,"Investments","NON_CURRENT_ASSETS","INVESTING",3);map(income,"Interest income","INCOME","OPERATING",2);
        post(bank,capital,"1000.00","Capital funding");post(investments,bank,"100.00","Investments");post(bank,income,"416.67","Interest receipt");
    }
    @SuppressWarnings("unchecked") <T>T proxy(T service){var factory=new ProxyFactory(service);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();}
    UUID account(String code,String name,String type,String purpose){UUID id=UUID.randomUUID();jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,maker_id,created_at) values(?,?,?,?,?,?,'POSTING',?,?,now())",id,institution,code,name,type,Set.of("ASSET","EXPENSE").contains(type)?"DEBIT":"CREDIT",purpose,actor.getMemberId());return id;}
    void map(UUID id,String line,String section,String cash,int note){statements.map(actor,new Mapping(id,line,section,cash,"Owner capital",note,"Verified document reference required for complete opening coverage."),null);}
    VoucherDtos.Voucher post(UUID debit,UUID credit,String amount,String description){var f=new VoucherDtos.Form();f.setEffectiveDate(LocalDate.of(2026,1,10));f.setParty("Synthetic counterparty");f.setDescription(description);f.setReference(UUID.randomUUID().toString());f.setEvidence("Synthetic test evidence");var row=new VoucherDtos.RowForm();row.setDebitAccountId(debit);row.setCreditAccountId(credit);row.setAmount(new BigDecimal(amount));row.setDescription(description);f.setRows(List.of(row));return vouchers.post(actor,VoucherDtos.Type.JOURNAL,f);}
    UUID preview(){return statements.generate(actor,new Parameters(from,through,false),UUID.randomUUID());}
    @Test void figuresReconcileAndFreezeMembershipAcrossLateBackdatedPostings(){UUID id=preview();var initial=statements.get(actor,id).snapshot();assertThat(initial.position().getLast().current()).isEqualByComparingTo("1416.67");assertThat(initial.income().getLast().current()).isEqualByComparingTo("416.67");assertThat(initial.equity().getLast().total()).isEqualByComparingTo("1416.67");assertThat(initial.checks()).extracting(Check::key).containsExactly("coverage");
        post(bank,income,"10.00","Later backdated receipt");assertThat(statements.get(actor,id).snapshot()).isEqualTo(initial);assertThat(statements.activity(actor,id,income,0)).hasSize(1);assertThat(statements.get(actor,preview()).snapshot().income().getLast().current()).isEqualByComparingTo("426.67");
    }
    @Test void exactInvestmentTrialAndLedgerUseTheFrozenMembership(){UUID id=preview();var s=statements.get(actor,id).snapshot();var row=s.position().stream().filter(r->r.label().equals("Investments")).findFirst().orElseThrow();assertThat(statements.selected(s,Kind.POSITION,row.key())).extracting(Balance::id).containsExactly(investments);assertThat(statements.activity(actor,id,investments,0)).hasSize(1);}
    @Test void retriesMappingsSavingAndDatabaseEvidenceProtections(){UUID key=UUID.randomUUID();UUID id=statements.generate(actor,new Parameters(from,through,false),key);assertThat(statements.generate(actor,new Parameters(from,through,false),key)).isEqualTo(id);assertThatThrownBy(()->statements.generate(actor,new Parameters(from.plusDays(1),through,false),key)).hasMessage("fs.error.retry");statements.save(actor,id);assertThat(statements.get(actor,id).saved()).isTrue();
        var rev=statements.revision(actor,investments);statements.map(actor,new Mapping(investments,"New investments label","NON_CURRENT_ASSETS","INVESTING","",3,"Changed note"),rev);assertThat(statements.get(actor,id).snapshot().position()).extracting(Row::label).contains("Investments");assertThatThrownBy(()->statements.map(actor,new Mapping(investments,"Stale label","NON_CURRENT_ASSETS","INVESTING","",3,""),rev)).hasMessage("fs.error.conflict");
        assertThatThrownBy(()->jdbc.update("update statement_run set payload='{}' where id=?",id)).hasMessageContaining("immutable");assertThatThrownBy(()->jdbc.update("delete from statement_run_journal where run_id=?",id)).hasMessageContaining("immutable");assertThatThrownBy(()->jdbc.update("insert into statement_run_journal select run_id,journal_id from statement_run_journal where run_id=? limit 1",id)).hasMessageContaining("frozen");
    }
    @Test void branchTenantAndRevokedExportChecksApplyToSavedRuns(){UUID id=preview();var foreign=new AppUserPrincipal(Member.builder().id(actor.getMemberId()).saccoId(institution).stationId("B2").position(Position.ACCOUNTANT).build(),EnumSet.allOf(UserClaim.class),true);assertThatThrownBy(()->statements.get(foreign,id)).isInstanceOf(AccessDeniedException.class);doThrow(new AccessDeniedException("revoked")).when(guard).requireActor(actor,"FINANCIAL_REPORTS_EXPORT");assertThatThrownBy(()->statements.artifact(actor,id,Kind.POSITION,"PDF","en")).isInstanceOf(AccessDeniedException.class);}
    @Test void institutionAggregationDoesNotGrantCrossBranchLedgerAccess(){UUID id=statements.generate(actor,new Parameters(from,through,true),UUID.randomUUID());assertThatThrownBy(()->statements.activity(actor,id,bank,0)).isInstanceOf(AccessDeniedException.class);doThrow(new AccessDeniedException("revoked institution grant")).when(guard).requireActor(actor,"FINANCIAL_REPORTS_INSTITUTION");assertThat(statements.history(actor,0,"newest")).extracting(com.sacco.mvp.accounting.statements.repository.StatementRepository.Stored::id).doesNotContain(id);}
    @Test void concurrentIdenticalRequestsReturnOneFrozenSnapshot()throws Exception{UUID key=UUID.randomUUID();try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var tasks=List.<java.util.concurrent.Callable<UUID>>of(()->statements.generate(actor,new Parameters(from,through,false),key),()->statements.generate(actor,new Parameters(from,through,false),key));var results=pool.invokeAll(tasks);assertThat(results.getFirst().get()).isEqualTo(results.getLast().get());}}
    @Test void simplifiedLayoutKeepsEarlierArtifactsAndServesNewDocuments(){
        UUID id=preview();byte[] previous="previous layout".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbc.update("insert into statement_run_artifact(run_id,kind,format,language,bytes,checksum) values(?,'POSITION','PDF','en',?,?)",id,previous,StatementService.sha(previous));
        assertThat(statements.artifact(actor,id,Kind.POSITION,"PDF","en")).isEmpty();
        byte[] current="simplified layout".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(statements.retain(actor,id,Kind.POSITION,"PDF","en",current)).isEqualTo(current);
        assertThat(jdbc.queryForObject("select count(*) from statement_run_artifact where run_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select bytes from statement_run_artifact where run_id=? and kind='POSITION'",byte[].class,id)).isEqualTo(previous);
    }
    @AfterAll void browserFixture()throws Exception{
        if(actor==null)return;var root=java.nio.file.Path.of("target/statement-workspace");java.nio.file.Files.createDirectories(root);UUID id=preview();
        java.nio.file.Files.writeString(root.resolve("fixture.txt"),"staff="+staffNumber+"\ninstitution="+institution+"\nrun="+id+"\n");
        var messages=new org.springframework.context.support.ReloadableResourceBundleMessageSource();messages.setBasename("classpath:messages");messages.setDefaultEncoding("UTF-8");var presentation=new StatementPresentation(messages);var exporter=new StatementExportService(presentation,new ReportExportLimiter(1),new com.sacco.mvp.service.ApplicationClock("Africa/Nairobi"));var snapshot=statements.get(actor,id).snapshot();
        for(var kind:List.of(Kind.POSITION,Kind.INCOME,Kind.CASH_FLOW,Kind.EQUITY,Kind.NOTES,Kind.TRIAL)){
            var document=presentation.document(snapshot,kind,snapshot.balances(),Locale.ENGLISH);byte[] pdf=exporter.export(snapshot,List.of(document),"PDF",Locale.ENGLISH);java.nio.file.Files.write(root.resolve(kind+".pdf"),pdf);java.nio.file.Files.write(root.resolve(kind+".xlsx"),exporter.export(snapshot,List.of(document),"XLSX",Locale.ENGLISH));
            try(var parsed=org.apache.pdfbox.Loader.loadPDF(pdf)){var image=new org.apache.pdfbox.rendering.PDFRenderer(parsed).renderImageWithDPI(0,110);javax.imageio.ImageIO.write(image,"png",root.resolve(kind+".png").toFile());}
        }
    }
}
