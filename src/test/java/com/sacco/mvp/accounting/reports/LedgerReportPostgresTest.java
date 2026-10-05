package com.sacco.mvp.accounting.reports;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.policy.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService.*;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_B_DATABASE_URL",matches="(?:(?:jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_b_test|jdbc:postgresql://127\\.0\\.0\\.1:55439/microfinance_accounting_h_release_combined_test_20261004)|jdbc:postgresql://127\\.0\\.0\\.1:55439/microfinance_accounting_h_release_integrity_test_20261004)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LedgerReportPostgresTest {
    private JdbcTemplate jdbc;
    private LedgerReportRepository reports;
    private TransactionTemplate tx;
    private GeneralLedgerService service;
    private AccountingPolicyService policies;
    private AuditService audit;
    private UserClaimService claims;
    private MemberDirectoryService directory;
    private SaccoRegistryService institutions;
    private String institution;
    private AppUserPrincipal maker,checker,reverseMaker,reverseChecker;
    private UUID cash,capital,principal,period,policy;
    private static final LocalDate DAY=LocalDate.of(2026,10,1);
    private static final OffsetDateTime NOW=OffsetDateTime.now();

    @BeforeAll void start() {
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_ACCOUNTING_B_DATABASE_URL"),"microfinance_test","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);reports=new LedgerReportRepository(new NamedParameterJdbcTemplate(jdbc));var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        policies=mock(AccountingPolicyService.class);audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);
        ApplicationClock clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(DAY.plusDays(1));when(clock.now()).thenAnswer(i->OffsetDateTime.now());
        var raw=new GeneralLedgerService(new GeneralLedgerRepository(jdbc),policies,new AccessControlService(),audit,clock,claims,directory,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class));
        var factory=new ProxyFactory(raw);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        service=(GeneralLedgerService)factory.getProxy();
    }
    @BeforeEach void fixture() {
        reset(policies,audit,claims,directory,institutions);
        institution="GL-"+UUID.randomUUID();jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",institution,"Synthetic GL test");
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
        when(institutions.findStation(eq(institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder()
            .saccoId(institution).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at,access_status,otp_requirement_mode,user_otp_selection_policy) values(?,?,'B1',true,now(),now(),'ACTIVE','APPROVAL_ONLY','NONE'),(?,?,'B2',true,now(),now(),'ACTIVE','APPROVAL_ONLY','NONE')",UUID.randomUUID(),institution,UUID.randomUUID(),institution);
        maker=operator("B1");checker=operator("B1");reverseMaker=operator("B1");reverseChecker=operator("B1");
        cash=service.createAccount(maker,new AccountCommand("CASH","Verified test cash","ASSET","DEBIT","POSTING","CASH",null));
        capital=service.createAccount(maker,new AccountCommand("CAPITAL","Verified owner capital","EQUITY","CREDIT","POSTING","CAPITAL",null));
        principal=service.createAccount(maker,new AccountCommand("PRINCIPAL","Loan principal control","ASSET","DEBIT","CONTROL","LOAN_PRINCIPAL",null));
        policy=UUID.randomUUID();var decisions=new EnumMap<PolicyDecision,String>(PolicyDecision.class);for(var k:PolicyDecision.values())decisions.put(k,"Synthetic approved evidence only");
        var matrix=new EnumMap<PostingEvent,PostingRule>(PostingEvent.class);for(var k:PostingEvent.values())matrix.put(k,new PostingRule(PostingPermission.ALLOWED,"Synthetic treatment"));
        var mapping=Map.of("LOAN_PRINCIPAL",principal,"DISBURSEMENT_CLEARING",cash,"REPAYMENT_CLEARING",cash,"OWNER_CAPITAL",capital);
        var mapper=JsonMapper.builder().findAndAddModules().build();
        jdbc.update("insert into accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) values(?,?,1,?,?,'LOCAL_GL',?,?,?,'Synthetic reviewer evidence',?,?,?)",policy,institution,DAY,DAY,mapper.writeValueAsString(decisions),mapper.writeValueAsString(matrix),mapper.writeValueAsString(mapping),maker.getMemberId(),UUID.randomUUID(),NOW);
        jdbc.update("insert into accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) values(?,?,1,?,?,'APPROVED','Synthetic independent review','Test evidence only',?)",policy,institution,DAY,checker.getMemberId(),NOW);
        var snapshot=new PolicySnapshot(policy,institution,1,DAY,DAY,AuthoritativeLedger.LOCAL_GL,decisions,matrix,mapping,"Synthetic evidence",maker.getMemberId(),Decision.APPROVED,checker.getMemberId(),"Synthetic approval","Test",NOW);
        when(policies.requireApprovedLocalPolicy(eq(institution),any())).thenReturn(snapshot);
        period=service.createPeriod(maker,DAY,DAY.plusMonths(1));
    }
    private LedgerReportService.Parameters p(OffsetDateTime cutoff) {
        return new LedgerReportService.Parameters(DAY,DAY.plusDays(1),cutoff,0);
    }
    private LedgerReportService.Scope scope(boolean wide) {return new LedgerReportService.Scope(institution,"B1",wide);}
    private Journal post(String reference,String amount) {
        var j=service.draftManual(maker,command(reference,amount));service.approve(checker,j.id(),"Synthetic independent review");return service.post(checker,j.id(),false);
    }
    @Test void unknownOpeningIsUnknownAndInactiveHistoricalBranchStillNeedsCoverage() {
        var params=p(OffsetDateTime.now());var coverage=reports.coverage(scope(false),params);
        assertThat(coverage.missingOpenings()).isEqualTo(1);
        assertThat(reports.balances(scope(false),params,coverage.complete())).allSatisfy(a->{assertThat(a.opening()).isNull();assertThat(a.closing()).isNull();});
        opening();jdbc.update("update sacco_stations set active=false where sacco_id=? and station_id='B2'",institution);
        assertThat(reports.coverage(scope(true),p(OffsetDateTime.now())).missingOpenings()).isEqualTo(1);
    }
    @Test void cutoverCashIsOpeningAndBackdatedLaterPostingDoesNotAlterRetainedCutoff() {
        var opening=service.importOpening(maker,command("OPENING-REPORT","1000.01"));service.approve(checker,opening.id(),"Independently reviewed source reconciliation");service.post(checker,opening.id(),true);
        var posted=post("PERIOD-MOVEMENT","1.01");var cutoff=OffsetDateTime.now();
        post("LATER-BACKDATED","2.00");
        var historical=reports.balance(scope(false),p(cutoff),cash,true).orElseThrow();
        assertThat(historical.opening()).isEqualByComparingTo("1000.01");assertThat(historical.debit()).isEqualByComparingTo("1.01");assertThat(historical.closing()).isEqualByComparingTo("1001.02");
        assertThat(reports.activity(scope(false),p(cutoff),cash)).singleElement().satisfies(a->{assertThat(a.journalId()).isEqualTo(posted.id());assertThat(a.evidenceReference()).isEqualTo("Synthetic source evidence");});
        assertThat(reports.balance(scope(false),p(OffsetDateTime.now()),cash,true).orElseThrow().closing()).isEqualByComparingTo("1003.02");
        var totals=reports.totals(scope(false),p(cutoff),true);assertThat(totals.openingDebit()).isEqualByComparingTo(totals.openingCredit());assertThat(totals.movementDebit()).isEqualByComparingTo("1.01");assertThat(totals.movementDebit()).isEqualByComparingTo(totals.movementCredit());assertThat(totals.closingDebit()).isEqualByComparingTo(totals.closingCredit());
    }
    @Test void linkedReversalRemainsVisibleAndNetsToOpeningWithoutDeletingMovement() {
        opening();var j=post("REVERSED-MOVEMENT","1.01");var r=service.reverse(reverseMaker,j.id(),UUID.randomUUID(),DAY.plusDays(1),"Verified correction","Synthetic correcting evidence");service.approve(reverseChecker,r.id(),"Independent correcting review");service.post(reverseChecker,r.id(),false);
        var params=p(OffsetDateTime.now());var balance=reports.balance(scope(false),params,cash,true).orElseThrow();
        assertThat(balance.debit()).isEqualByComparingTo("1.01");assertThat(balance.credit()).isEqualByComparingTo("1.01");assertThat(balance.closing()).isEqualByComparingTo("100.00");
        assertThat(reports.activity(scope(false),params,cash)).hasSize(2).anySatisfy(a->{assertThat(a.reversalOf()).isEqualTo(j.id());assertThat(a.sourceType()).isEqualTo("REVERSAL");});
    }
    @Test void branchAmountsAndForeignAccountsCannotLeakIntoReportScope() {
        opening();post("OWN-MOVEMENT","4.00");var b2maker=operator("B2");var b2checker=operator("B2");
        var j=service.importOpening(b2maker,command("OPENING-B2","200.00"));service.approve(b2checker,j.id(),"Independent B2 review");service.post(b2checker,j.id(),true);
        var params=p(OffsetDateTime.now());assertThat(reports.balance(scope(false),params,cash,true).orElseThrow().closing()).isEqualByComparingTo("104.00");assertThat(reports.balance(scope(true),params,cash,true).orElseThrow().closing()).isEqualByComparingTo("304.00");
        var foreign=new LedgerReportService.Scope("UNRELATED-"+UUID.randomUUID(),"B1",false);
        assertThat(reports.balance(foreign,params,cash,true)).isEmpty();assertThat(reports.activity(foreign,params,cash)).isEmpty();
    }
    private Journal opening() {var j=service.importOpening(maker,command("OPENING-"+UUID.randomUUID(),"100.00"));service.approve(checker,j.id(),"Independent verified source reconciliation");return service.post(checker,j.id(),true);}
    private JournalCommand command(String reference,String amount) {var a=new BigDecimal(amount);return new JournalCommand(UUID.randomUUID(),reference,reference.startsWith("OPEN") || reference.equals("SCOPE") || reference.equals("RACE") || reference.equals("ROLLBACK")?DAY:DAY.plusDays(1),"Synthetic source evidence",null,List.of(new Line(cash,a,BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,a)));}
    private AppUserPrincipal operator(String branch) {
        UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','MANAGER',now(),false,'test-only')",id,institution,branch,id.toString(),"Synthetic accounting staff");
        var m=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).fullName("Synthetic staff").position(Position.MANAGER).status(MemberStatus.ACTIVE).build();
        when(directory.find(id)).thenReturn(Optional.of(m));return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),true);
    }
}
