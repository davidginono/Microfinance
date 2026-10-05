package com.sacco.mvp.reporting.operational;

import com.sacco.mvp.reporting.operational.dto.ReportDefinition;
import com.sacco.mvp.reporting.operational.dto.ReportDefinition.*;
import com.sacco.mvp.reporting.operational.repository.OperationalReportRepository;
import com.sacco.mvp.reporting.operational.service.OperationalReportCatalog;
import com.sacco.mvp.reporting.operational.service.OperationalReportService;
import com.sacco.mvp.reporting.operational.service.ReportDefinitionValidator;
import com.sacco.mvp.reporting.operational.repository.OperationalTemplateRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.context.annotation.*;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit, disposable loopback database only; does not read the operational application's credentials. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_TEST_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_f_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationalReportPostgresTest {
    private JdbcTemplate jdbc; private OperationalReportRepository reports;
    private final OperationalReportCatalog catalog=new OperationalReportCatalog();
    private final LocalDate today=LocalDate.of(2026,10,4);
    private final OffsetDateTime cutoff=OffsetDateTime.parse("2026-10-04T12:00:00+03:00");
    private String institution; private UUID actor;
    private AnnotationConfigApplicationContext context;
    @BeforeAll void start() {
        context=new AnnotationConfigApplicationContext(Config.class);
        var ds=context.getBean(DataSource.class);
        jdbc=new JdbcTemplate(ds);reports=new OperationalReportRepository(new NamedParameterJdbcTemplate(ds));
    }
    @AfterAll void close() { if(context!=null) context.close(); }
    @BeforeEach void fixture() {
        institution="F-"+UUID.randomUUID();actor=UUID.randomUUID();
        jdbc.update("INSERT INTO registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) VALUES(?,?,true,now(),now())",institution,"F Test");
        jdbc.update("INSERT INTO members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) VALUES(?,?,'B1',?,'Report Test','ACTIVE','MANAGER',now(),true,'not-a-password')",actor,institution,actor.toString());
    }
    @Test void fullFilteredCollectionsTotalsRespectBranchReversalsAndCutoff() {
        UUID loan=loan("B1","100001",true);UUID foreign=loan("B2","100002",true);
        UUID payment=payment(loan,"B1","PAYMENT",new BigDecimal("120.00"),new BigDecimal("100.00"),null,cutoff.minusDays(2));
        payment(loan,"B1","REVERSAL",new BigDecimal("120.00"),new BigDecimal("100.00"),payment,cutoff.minusDays(1));
        payment(loan,"B1","PAYMENT",new BigDecimal("30.00"),new BigDecimal("20.00"),null,cutoff.minusHours(1));
        payment(loan,"B1","PAYMENT",new BigDecimal("9.00"),new BigDecimal("8.00"),null,cutoff.plusHours(1));
        payment(foreign,"B2","PAYMENT",new BigDecimal("999.00"),new BigDecimal("900.00"),null,cutoff.minusHours(1));
        var result=reports.query(institution,"B1",catalog.system(Dataset.COLLECTIONS,today),cutoff,0,1);
        assertThat(result.count()).isEqualTo(3);assertThat(result.rows()).hasSize(1);
        assertThat(result.totals().get("amount").value()).isEqualByComparingTo("30.00");
        assertThat(result.totals().get("principal").value()).isEqualByComparingTo("20.00");
        assertThat(result.coverage().trackedLoans()).isEqualTo(1);
        assertThat(reports.query("OTHER","B1",catalog.system(Dataset.COLLECTIONS,today),cutoff,0,25).rows()).isEmpty();
    }
    @Test void asOfPortfolioIncludesOldLoansAndUnknownsAndIgnoresMutatingPaidCounters() {
        UUID tracked=loan("B1","200001",true);loan("B1","200002",false);loan("B2","200003",true);
        payment(tracked,"B1","PAYMENT",new BigDecimal("30.00"),new BigDecimal("20.00"),null,cutoff.minusHours(1));
        payment(tracked,"B1","PAYMENT",new BigDecimal("12.00"),new BigDecimal("10.00"),null,cutoff.plusHours(1));
        jdbc.update("UPDATE loan_ledgers SET principal_paid=999,interest_paid=99 WHERE loan_application_id=?",tracked);
        var result=reports.query(institution,"B1",catalog.system(Dataset.LOAN_PORTFOLIO,today),cutoff,0,1);
        assertThat(result.count()).isEqualTo(2);assertThat(result.rows()).hasSize(1);
        assertThat(result.totals().get("outstanding_principal").value()).isEqualByComparingTo("980.00");
        assertThat(result.totals().get("outstanding_principal").unavailableRows()).isEqualTo(1);
        assertThat(result.totals().get("due_interest").value()).isEqualByComparingTo("40.00");
        assertThat(result.coverage().untrackedLoans()).isEqualTo(1);
        var later=reports.query(institution,"B1",catalog.system(Dataset.LOAN_PORTFOLIO,today),cutoff.plusHours(2),0,25);
        assertThat(later.totals().get("outstanding_principal").value()).isEqualByComparingTo("970.00");
    }
    @Test void allUnavailableIsNullInsteadOfInventedZeroAndNoRowsStillHasExplicitCounts() {
        loan("B1","300001",false);
        var result=reports.query(institution,"B1",catalog.system(Dataset.LOAN_PORTFOLIO,today),cutoff,0,25);
        assertThat(result.rows().getFirst().get("principal")).isNull();
        assertThat(result.totals().get("principal").value()).isNull();
        assertThat(result.totals().get("principal").unavailableRows()).isEqualTo(1);
        var empty=reports.query(institution,"B2",catalog.system(Dataset.LOAN_PORTFOLIO,today),cutoff,0,25);
        assertThat(empty.count()).isZero();assertThat(empty.totals().get("principal").value()).isNull();
    }
    @Test void publicationHistoryCannotBeEditedAndVisibilityChangeDoesNotChangeDefinition() {
        UUID id=UUID.randomUUID();String json="{\"schemaVersion\":1}";
        jdbc.update("INSERT INTO operational_report_templates(id,sacco_id,family_id,report_version,name,definition_json,state,visibility,dataset,creator_id,created_at,publisher_id,published_at) VALUES(?,?,?,1,'Test',?,'PUBLISHED','PRIVATE','COLLECTIONS',?,?,?,?)",id,institution,id,json,actor,cutoff,actor,cutoff);
        assertThatThrownBy(() -> jdbc.update("UPDATE operational_report_templates SET definition_json='{}' WHERE id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM operational_report_templates WHERE id=?",id)).isInstanceOf(DataAccessException.class);
        jdbc.update("UPDATE operational_report_templates SET visibility='INSTITUTION',version=version+1 WHERE id=?",id);
        reports.event(id,institution,actor,"SHARED",json,cutoff);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM operational_report_template_events WHERE template_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT definition_json FROM operational_report_templates WHERE id=?",String.class,id)).isEqualTo(json);
    }
    @Test void persistedTemplateLifecycleKeepsPrivateScopeAndImmutablePublishedVersion() {
        var service=context.getBean(OperationalReportService.class);
        var principal=principal(actor);var other=principal(UUID.randomUUID());
        var draft=service.saveDraft(principal,null,null,"Collections",catalog.system(Dataset.COLLECTIONS,today));
        assertThat(draft.visibility()).isEqualTo("PRIVATE");
        assertThat(service.templates(principal,0).getTotalElements()).isEqualTo(1);
        assertThat(service.templates(other,0).getTotalElements()).isZero();
        assertThatThrownBy(() -> service.definition(other,draft.id())).isInstanceOf(AccessDeniedException.class);
        var published=service.publish(principal,draft.id(),draft.lockVersion());
        assertThat(published.state()).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> service.saveDraft(principal,published.id(),published.lockVersion(),"Changed",published.definition())).hasMessage("opreport.error.immutable");
        service.share(principal,published.id(),published.lockVersion(),true);
        assertThat(service.templates(other,0).getTotalElements()).isEqualTo(1);
        assertThat(service.run(other,published.id(),0,25).rowCount()).isZero();
        var clone=service.cloneVersion(principal,published.id());
        assertThat(clone.reportVersion()).isEqualTo(2);assertThat(clone.state()).isEqualTo("DRAFT");assertThat(clone.visibility()).isEqualTo("PRIVATE");
        assertThat(service.definition(principal,published.id()).definition()).isEqualTo(published.definition());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operational_report_template_events WHERE template_id=?",Integer.class,published.id())).isEqualTo(3);
    }
    private AppUserPrincipal principal(UUID id) {
        var p=mock(AppUserPrincipal.class);when(p.getMemberId()).thenReturn(id);when(p.getSaccoId()).thenReturn(institution);when(p.getStationId()).thenReturn("B1");when(p.isStaffSession()).thenReturn(true);
        when(p.getClaims()).thenReturn(Set.of("REPORT_TEMPLATES_VIEW","REPORT_TEMPLATES_CREATE","REPORT_TEMPLATES_UPDATE","REPORT_TEMPLATES_PUBLISH","REPORT_TEMPLATES_SHARE","REPORTS_RUN","LOAN_REPAYMENTS_VIEW"));return p;
    }
    private UUID loan(String branch,String number,boolean tracked) {
        UUID id=UUID.randomUUID();long applicationNumber=Math.abs(UUID.randomUUID().getMostSignificantBits());
        jdbc.update("INSERT INTO loan_applications(id,application_number,loan_id,sacco_id,station_id,applicant_member_id,loan_type,amount,tenor_months,status,form_data,policy_snapshot,required_guarantors,disbursement_date,created_at,updated_at,version) VALUES(?,?,?,?,?,?,'DEVELOPMENT_LOAN',1000,12,'DISBURSED','{}','{}',0,'2025-01-01',?,?,0)",id,applicationNumber,number,institution,branch,actor,cutoff.minusYears(2),cutoff.minusYears(2));
        if(tracked) {
            jdbc.update("INSERT INTO loan_ledgers(loan_application_id,sacco_id,station_id,loan_id,applicant_member_id,disbursement_date,principal,created_at) VALUES(?,?,?,?,?,'2025-01-01',1000,?)",id,institution,branch,number,actor,cutoff.minusYears(1));
            jdbc.update("INSERT INTO loan_ledger_installments(id,loan_application_id,installment_number,due_date,principal,interest) VALUES(?,?,1,'2026-10-01',1000,50)",UUID.randomUUID(),id);
        }
        return id;
    }
    private UUID payment(UUID loan,String branch,String kind,BigDecimal amount,BigDecimal principal,UUID reversal,OffsetDateTime posted) {
        UUID id=UUID.randomUUID();Long sequence=jdbc.queryForObject("SELECT COALESCE(MAX(sequence),0)+1 FROM loan_repayment_transactions WHERE loan_application_id=?",Long.class,loan);
        jdbc.update("INSERT INTO loan_repayment_transactions(id,loan_application_id,sacco_id,station_id,sequence,receipt_reference,request_key,kind,channel,channel_reference,payment_date,amount,principal_amount,interest_amount,actor_member_id,reverses_transaction_id,reason,loan_status_before,posted_at) VALUES(?,?,?,?,?,?,?,?,'CASH',?,'2026-10-01',?,?,?,?,?,'Test reversal','DISBURSED',?)",id,loan,institution,branch,sequence,id.toString(),UUID.randomUUID(),kind,id.toString(),amount,principal,amount.subtract(principal),actor,reversal,posted);
        return id;
    }
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=OperationalTemplateRepository.class)
    @Import({OperationalReportService.class,OperationalReportRepository.class,OperationalReportCatalog.class,ReportDefinitionValidator.class,AccessControlService.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(System.getenv("MICROFINANCE_TEST_DATABASE_URL"),"microfinance_test",""); }
        @Bean(initMethod="migrate") Flyway flyway(DataSource dataSource) { return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load(); }
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);factory.setPackagesToScan("com.sacco.mvp.reporting.operational.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean NamedParameterJdbcTemplate namedJdbc(DataSource ds) { return new NamedParameterJdbcTemplate(ds); }
        @Bean ApplicationClock clock() { return new ApplicationClock("Africa/Nairobi"); }
        @Bean ObjectMapper mapper() { return JsonMapper.builder().build(); }
        @Bean MessageSource messageSource() { var source=new ResourceBundleMessageSource();source.setBasename("messages");source.setDefaultEncoding("UTF-8");source.setUseCodeAsDefaultMessage(true);return source; }
    }
}
