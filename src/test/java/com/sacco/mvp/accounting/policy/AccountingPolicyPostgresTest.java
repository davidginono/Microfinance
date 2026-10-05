package com.sacco.mvp.accounting.policy;

import com.sacco.mvp.accounting.policy.dto.*;
import com.sacco.mvp.accounting.policy.model.*;
import com.sacco.mvp.accounting.policy.repository.AccountingPolicyRepository;
import com.sacco.mvp.accounting.policy.service.AccountingPolicyService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_A_DATABASE_URL",
    matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_a_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingPolicyPostgresTest {
    private static final LocalDate TODAY = LocalDate.of(2026,10,4);
    private AnnotationConfigApplicationContext context;
    private AccountingPolicyService policies;
    private JdbcTemplate jdbc;
    private AppUserPrincipal maker, checker;
    private String institution;

    @BeforeAll void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        policies = context.getBean(AccountingPolicyService.class);
        jdbc = context.getBean(JdbcTemplate.class);
    }
    @AfterAll void stop() { if (context != null) context.close(); }
    @BeforeEach void fixture() {
        institution="A-"+UUID.randomUUID();
        jdbc.update("INSERT INTO registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) VALUES (?, 'Synthetic Institution',true,now(),now())",institution);
        maker=operator(institution,"B1",Position.ACCOUNTANT,true,EnumSet.of(UserClaim.ACCOUNTING_POLICY_VIEW,UserClaim.ACCOUNTING_POLICY_CREATE,UserClaim.ACCOUNTING_POLICY_APPROVE,UserClaim.ACCOUNTING_POLICY_REJECT));
        checker=operator(institution,"B2",Position.ACCOUNTANT,true,EnumSet.of(UserClaim.ACCOUNTING_POLICY_VIEW,UserClaim.ACCOUNTING_POLICY_APPROVE,UserClaim.ACCOUNTING_POLICY_REJECT));
    }

    @Test void noPolicyFailsClosedAndHistoricalFirstCutoverIsAllowed() {
        assertThatThrownBy(()->policies.requireLocalPolicy(institution,TODAY)).hasMessage("policy.error.unavailable");
        var p=policies.create(maker,UUID.randomUUID(),content(GlAuthority.LOCAL,LocalDate.of(2026,9,1),true));
        assertThatThrownBy(()->policies.requireApproved(institution,TODAY)).hasMessage("policy.error.unavailable");
        var approved=policies.approve(checker,p.id(),p.contentHash(),"Signed accountant minute A","Approved synthetic test decisions");
        assertThat(approved.state()).isEqualTo("APPROVED");
        assertThat(policies.requireApprovedLocal(institution,p.id(),1,TODAY).id()).isEqualTo(p.id());
        assertThatThrownBy(()->policies.requireApproved(institution,LocalDate.of(2026,8,31))).hasMessage("policy.error.unavailable");
        assertThatThrownBy(()->policies.requirePostingRule(institution,TODAY,AccountingEvent.EARLY_SETTLEMENT)).hasMessage("policy.error.disabled");
        assertThat(policies.requirePostingRule(institution,TODAY,AccountingEvent.ORDINARY_DISBURSEMENT).accountCodes())
            .containsEntry(AccountRole.LOAN_PRINCIPAL,"1200");
    }

    @Test void makerCannotApproveOrRejectAndMissingEvidenceCannotApprove() {
        var p=policies.create(maker,UUID.randomUUID(),content(GlAuthority.LOCAL,TODAY,true));
        assertThatThrownBy(()->policies.approve(maker,p.id(),p.contentHash(),"A","A")).hasMessage("policy.error.independent");
        assertThatThrownBy(()->policies.reject(maker,p.id(),p.contentHash(),"A","A")).hasMessage("policy.error.independent");
        assertThatThrownBy(()->policies.approve(checker,p.id(),p.contentHash(),"","Decision")).hasMessage("policy.error.incomplete");
        assertThat(policies.view(maker,p.id()).state()).isEqualTo("DRAFT");
    }

    @Test void missingDecisionsAndAccountMappingsRemainBlocked() {
        var incomplete=content(GlAuthority.LOCAL,TODAY,false);
        var p=policies.create(maker,UUID.randomUUID(),incomplete);
        assertThatThrownBy(()->policies.approve(checker,p.id(),p.contentHash(),"A","A")).hasMessage("policy.error.incomplete");
        var full=content(GlAuthority.LOCAL,TODAY,true);
        var rules=new EnumMap<AccountingEvent,PostingRule>(full.postingRules());
        rules.put(AccountingEvent.ORDINARY_DISBURSEMENT,new PostingRule(true,Map.of(),"Verified disbursement","A"));
        var p2=policies.create(maker,UUID.randomUUID(),new PolicyContent(full.authority(),full.effectiveFrom(),full.openingDate(),full.authorityEvidence(),full.decisions(),rules));
        assertThatThrownBy(()->policies.approve(checker,p2.id(),p2.contentHash(),"A","A")).hasMessage("policy.error.mapping");
    }

    @Test void exactCreateRetryAndDecisionRetryDoNotDuplicateAudit() {
        UUID key=UUID.randomUUID(); var c=content(GlAuthority.LOCAL,TODAY,true);
        var p=policies.create(maker,key,c);
        assertThat(policies.create(maker,key,c).id()).isEqualTo(p.id());
        Map<PolicyDecision,String> reorderedDecisions=new LinkedHashMap<>();
        Arrays.stream(PolicyDecision.values()).sorted(Comparator.reverseOrder()).forEach(d->reorderedDecisions.put(d,c.decisions().get(d)));
        Map<AccountingEvent,PostingRule> reorderedRules=new LinkedHashMap<>();
        Arrays.stream(AccountingEvent.values()).sorted(Comparator.reverseOrder()).forEach(e->{
            var source=c.postingRules().get(e); Map<AccountRole,String> reorderedCodes=new LinkedHashMap<>();
            source.accountCodes().keySet().stream().sorted(Comparator.reverseOrder()).forEach(r->reorderedCodes.put(r,source.accountCodes().get(r)));
            reorderedRules.put(e,new PostingRule(source.enabled(),reorderedCodes,source.treatment(),source.evidenceReference()));
        });
        var reordered=new PolicyContent(c.authority(),c.effectiveFrom(),c.openingDate(),c.authorityEvidence(),reorderedDecisions,reorderedRules);
        assertThat(policies.create(maker,key,reordered).id()).isEqualTo(p.id());
        assertThatThrownBy(()->policies.create(maker,key,content(GlAuthority.EXTERNAL,TODAY,true))).hasMessage("policy.error.conflict");
        var approved=policies.approve(checker,p.id(),p.contentHash(),"A","Reviewed");
        assertThat(policies.approve(checker,p.id(),p.contentHash(),"A","Reviewed")).isEqualTo(approved);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_policy_audit WHERE policy_id=?",Integer.class,p.id())).isEqualTo(2);
        assertThatThrownBy(()->policies.reject(checker,p.id(),p.contentHash(),"A","Reviewed")).hasMessage("policy.error.conflict");
    }

    @Test void foreignInstitutionClientPlatformAndMissingBranchCannotReadOrMutate() {
        var p=policies.create(maker,UUID.randomUUID(),content(GlAuthority.LOCAL,TODAY,true));
        var wrong=operator("wrong","B1",Position.ACCOUNTANT,true,EnumSet.of(UserClaim.ACCOUNTING_POLICY_VIEW,UserClaim.ACCOUNTING_POLICY_APPROVE));
        assertThatThrownBy(()->policies.view(wrong,p.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->policies.approve(wrong,p.id(),p.contentHash(),"A","A")).isInstanceOf(AccessDeniedException.class);
        for (var actor:List.of(operator(institution,"B1",Position.MEMBER,false,EnumSet.of(UserClaim.ACCOUNTING_POLICY_VIEW)),
            operator(institution,"B1",Position.ADMIN,true,EnumSet.of(UserClaim.ACCOUNTING_POLICY_VIEW)),
            operator(institution,null,Position.ACCOUNTANT,true,EnumSet.of(UserClaim.ACCOUNTING_POLICY_VIEW)),
            operator(institution,"B1",Position.ACCOUNTANT,true,EnumSet.noneOf(UserClaim.class)))) {
            assertThatThrownBy(()->policies.list(actor,0)).isInstanceOf(AccessDeniedException.class);
        }
        // Policy metadata is explicitly institution-wide governance, with no borrower registry access.
        assertThat(policies.view(checker,p.id()).originatingBranch()).isEqualTo("B1");
    }

    @Test void staleVersionAndRevisionsCannotRetrospectivelyChangeAuthority() {
        var p=policies.create(maker,UUID.randomUUID(),content(GlAuthority.LOCAL,TODAY,true));
        assertThatThrownBy(()->policies.approve(checker,p.id(),"0".repeat(64),"A","A")).hasMessage("policy.error.conflict");
        policies.approve(checker,p.id(),p.contentHash(),"A","A");
        var backdated=policies.create(maker,UUID.randomUUID(),content(GlAuthority.LOCAL,TODAY,true));
        assertThatThrownBy(()->policies.approve(checker,backdated.id(),backdated.contentHash(),"A","A")).hasMessage("policy.error.future");
        LocalDate future=LocalDate.of(2030,1,1);
        var switcher=policies.create(maker,UUID.randomUUID(),content(GlAuthority.EXTERNAL,future,true));
        assertThatThrownBy(()->policies.approve(checker,switcher.id(),switcher.contentHash(),"A","A")).hasMessage("policy.error.authority");
        var revision=policies.create(maker,UUID.randomUUID(),content(GlAuthority.LOCAL,future,true));
        policies.approve(checker,revision.id(),revision.contentHash(),"A","A");
        assertThat(policies.requireLocalPolicy(institution,TODAY).id()).isEqualTo(p.id());
        assertThat(policies.requireLocalPolicy(institution,future).id()).isEqualTo(revision.id());
        assertThatThrownBy(()->policies.requireApprovedLocal(institution,p.id(),1,future)).hasMessage("policy.error.conflict");
    }

    @Test void externalBooksCannotProduceAnOfficialLocalPolicy() {
        var p=policies.create(maker,UUID.randomUUID(),content(GlAuthority.EXTERNAL,TODAY,true));
        policies.approve(checker,p.id(),p.contentHash(),"External accounting authorization","Explicit external books decision");
        assertThat(policies.requireApproved(institution,TODAY).authority()).isEqualTo(GlAuthority.EXTERNAL);
        assertThatThrownBy(()->policies.requireLocalPolicy(institution,TODAY)).hasMessage("policy.error.external");
    }

    @Test void databaseRejectsContentEditsDeletesSameMakerAndAuditEdits() {
        var p=policies.create(maker,UUID.randomUUID(),content(GlAuthority.LOCAL,TODAY,true));
        assertThatThrownBy(()->jdbc.update("UPDATE accounting_policy SET content_json='{}' WHERE id=?",p.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM accounting_policy WHERE id=?",p.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE accounting_policy SET state='APPROVED',checked_by=created_by,checked_at=now(),review_evidence='A',review_reason='A' WHERE id=?",p.id())).isInstanceOf(DataAccessException.class);
        policies.approve(checker,p.id(),p.contentHash(),"A","A");
        assertThatThrownBy(()->jdbc.update("UPDATE accounting_policy SET review_reason='changed' WHERE id=?",p.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM accounting_policy_audit WHERE policy_id=?",p.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE accounting_policy_audit SET reason=reason WHERE policy_id=?",p.id())).isInstanceOf(DataAccessException.class);
    }

    @Test void reviewedEndOfDateOpeningBeforeFirstPolicyIsNarrowlyAllowed() {
        var c=content(GlAuthority.LOCAL,TODAY,true);
        var rules=new EnumMap<AccountingEvent,PostingRule>(c.postingRules());
        rules.put(AccountingEvent.OPENING_BALANCE,new PostingRule(true,Map.of(),"Verified end-of-date opening, source movement starts following day","Synthetic signed opening A"));
        var opening=new PolicyContent(c.authority(),c.effectiveFrom(),c.openingDate(),c.authorityEvidence(),c.decisions(),rules);
        var p=policies.create(maker,UUID.randomUUID(),opening);
        policies.approve(checker,p.id(),p.contentHash(),"A","A");
        assertThat(policies.requireOpeningPolicy(institution,p.id(),p.version(),c.openingDate()).id()).isEqualTo(p.id());
        assertThat(policies.requireOpeningPolicy(institution,c.openingDate()).id()).isEqualTo(p.id());
        assertThat(policies.approvedOpeningDate(institution,TODAY)).isEqualTo(c.openingDate());
        assertThatThrownBy(()->policies.requireLocalPolicy(institution,c.openingDate())).hasMessage("policy.error.unavailable");
        assertThatThrownBy(()->policies.requireOpeningPolicy(institution,p.id(),p.version(),c.openingDate().minusDays(1))).hasMessage("policy.error.openingDate");
        assertThatThrownBy(()->policies.requireOpeningPolicy(institution,UUID.randomUUID(),p.version(),c.openingDate())).hasMessage("policy.error.conflict");
        var changedDate=new PolicyContent(c.authority(),LocalDate.of(2030,1,1),c.openingDate().plusDays(1),c.authorityEvidence(),c.decisions(),rules);
        var revision=policies.create(maker,UUID.randomUUID(),changedDate);
        assertThatThrownBy(()->policies.approve(checker,revision.id(),revision.contentHash(),"A","A")).hasMessage("policy.error.openingDate");
    }

    @Test void concurrentSameKeyAndApprovalCommitExactlyOnce() throws Exception {
        UUID key=UUID.randomUUID(); var c=content(GlAuthority.LOCAL,TODAY,true);
        var ids=parallel(()->policies.create(maker,key,c).id());
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        var p=policies.view(maker,ids.get(0));
        var states=parallel(()->policies.approve(checker,p.id(),p.contentHash(),"A","A").state());
        assertThat(states).containsExactly("APPROVED","APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_policy WHERE sacco_id=?",Integer.class,institution)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounting_policy_audit WHERE policy_id=?",Integer.class,p.id())).isEqualTo(2);
    }

    @Test void listingIsBoundedAndPolicyCollectionsAreImmutable() {
        for(int i=0;i<27;i++) policies.create(maker,UUID.randomUUID(),content(GlAuthority.LOCAL,TODAY,false));
        var first=policies.list(maker,0); var second=policies.list(maker,1);
        assertThat(first.content()).hasSize(25); assertThat(first.hasNext()).isTrue();
        assertThat(second.content()).hasSize(2); assertThat(second.hasNext()).isFalse();
        assertThatThrownBy(()->first.content().clear()).isInstanceOf(UnsupportedOperationException.class);
        var content=policies.view(maker,first.content().get(0).id()).content();
        assertThatThrownBy(()->content.decisions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private <T> List<T> parallel(Callable<T> operation) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<T> synchronizedOperation=()->{barrier.await(10,TimeUnit.SECONDS);return operation.call();};
            var first=pool.submit(synchronizedOperation); var second=pool.submit(synchronizedOperation);
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
    }

    private PolicyContent content(GlAuthority authority,LocalDate date,boolean complete) {
        EnumMap<PolicyDecision,String> decisions=new EnumMap<>(PolicyDecision.class);
        EnumMap<AccountingEvent,PostingRule> rules=new EnumMap<>(AccountingEvent.class);
        if(complete) {
            for(var decision:PolicyDecision.values()) decisions.put(decision,"Synthetic approved decision "+decision+"; signed evidence A");
            for(var event:AccountingEvent.values()) rules.put(event,new PostingRule(false,Map.of(),"Disabled until reviewed capability exists","Synthetic evidence A"));
            rules.put(AccountingEvent.ORDINARY_DISBURSEMENT,new PostingRule(true,Map.of(AccountRole.CASH_BANK,"1000",AccountRole.LOAN_PRINCIPAL,"1200"),"Verified ordinary disbursement with linked reversal","Synthetic evidence A"));
        }
        return new PolicyContent(authority,date,LocalDate.of(2026,9,1),"Synthetic authority evidence A",decisions,rules);
    }

    private AppUserPrincipal operator(String sacco,String branch,Position position,boolean staff,Set<UserClaim> claims) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) VALUES (?, 'Synthetic Institution',true,now(),now()) ON CONFLICT DO NOTHING",sacco);
        jdbc.update("INSERT INTO members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) VALUES (?,?,?,?, 'Synthetic Operator','ACTIVE',?,now(),true,'not-a-password')",id,sacco,branch,id.toString(),position.name());
        return new AppUserPrincipal(Member.builder().id(id).saccoId(sacco).stationId(branch).memberNo(id.toString())
            .fullName("Synthetic Operator").memberAccount(true).position(position).status(MemberStatus.ACTIVE).build(),claims,staff);
    }

    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Config {
        @Bean DataSource source() {
            var source=new DriverManagerDataSource(System.getenv("MICROFINANCE_ACCOUNTING_A_DATABASE_URL"),"microfinance_test","");
            Properties limits=new Properties();limits.setProperty("connectTimeout","5");limits.setProperty("loginTimeout","10");limits.setProperty("socketTimeout","15");
            source.setConnectionProperties(limits);
            return source;
        }
        @Bean(initMethod="migrate") Flyway flyway(DataSource source) { return Flyway.configure().dataSource(source).locations("classpath:db/migration").load(); }
        @Bean @DependsOn("flyway") JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager tx(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean AccountingPolicyRepository repository(JdbcTemplate jdbc) { return new AccountingPolicyRepository(jdbc); }
        @Bean ObjectMapper mapper() { return JsonMapper.builder().findAndAddModules().build(); }
        @Bean AccessControlService access() { return new AccessControlService(); }
        @Bean ApplicationClock clock() { var c=mock(ApplicationClock.class); when(c.today()).thenReturn(TODAY);when(c.now()).thenReturn(OffsetDateTime.parse("2026-10-04T12:00:00+03:00"));return c; }
        @Bean AccountingPolicyService service(AccountingPolicyRepository repository,ObjectMapper mapper,AccessControlService access,ApplicationClock clock) { return new AccountingPolicyService(repository,mapper,access,clock); }
    }
}
