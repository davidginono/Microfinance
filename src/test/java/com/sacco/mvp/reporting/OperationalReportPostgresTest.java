package com.sacco.mvp.reporting;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.sacco.mvp.reporting.OperationalReportDefinition.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_REPORT_F_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_reporting_f_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationalReportPostgresTest {
    private static final LocalDate FROM=LocalDate.of(2026,9,1),THROUGH=LocalDate.of(2026,10,2);
    private static final OffsetDateTime CUTOFF=OffsetDateTime.parse("2026-10-02T10:00:00+03:00");
    private JdbcTemplate jdbc;private TransactionTemplate tx;private OperationalReportService reports;private OperationalReportTemplateService templates;
    private MemberDirectoryService directory;private UserClaimService claims;private SaccoRegistryService institutions;
    private SaccoLogoStorageService logos;
    private AppUserPrincipal maker,checker;private String institution;private UUID loan;
    @BeforeAll void migrate(){
        var source=new DriverManagerDataSource(System.getenv("MICROFINANCE_REPORT_F_DATABASE_URL"),"microfinance_test","");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(source);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @BeforeEach void fixture(){
        directory=mock(MemberDirectoryService.class);claims=mock(UserClaimService.class);institutions=mock(SaccoRegistryService.class);
        var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(THROUGH);when(clock.now()).thenReturn(CUTOFF.plusMinutes(5));
        logos=mock(SaccoLogoStorageService.class);
        reports=new OperationalReportService(new OperationalReportRepository(new NamedParameterJdbcTemplate(jdbc)),new AccessControlService(),clock,directory,claims,institutions,logos);
        templates=new OperationalReportTemplateService(new OperationalReportTemplateRepository(jdbc,JsonMapper.builder().findAndAddModules().build()),reports,new AccessControlService(),clock,mock(AuditService.class));
        institution="RF-"+UUID.randomUUID();jdbc.update("INSERT INTO registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) VALUES(?,'Synthetic reporting institution',true,now(),now())",institution);
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
        when(institutions.findStation(institution,"B1")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B1").active(true).build()));
        maker=operator(institution,"B1");checker=operator(institution,"B1");loan=openLoan(institution,"B1",maker.getMemberId(),true);
        payment(loan,institution,"B1",maker.getMemberId(),"PAYMENT","100.01","90.00",CUTOFF.minusMinutes(2),null);
        // A later reversal is excluded at the prior recording cutoff, despite its earlier effective date.
        UUID original=jdbc.queryForObject("SELECT id FROM loan_repayment_transactions WHERE loan_application_id=?",UUID.class,loan);
        payment(loan,institution,"B1",checker.getMemberId(),"REVERSAL","100.01","90.00",CUTOFF.plusMinutes(1),original);
        openLoan(institution,"B1",maker.getMemberId(),false);
        openLoan(institution,"B2",maker.getMemberId(),true);
    }
    @Test void totalsCoverTheWholeFilteredScopeWhileRowsRemainBounded(){
        UUID second=openLoan(institution,"B1",maker.getMemberId(),true);
        payment(second,institution,"B1",maker.getMemberId(),"PAYMENT","25.00","20.00",CUTOFF.minusMinutes(1),null);
        var r=run(standard(Dataset.COLLECTIONS,"en"),CUTOFF,1);
        assertThat(r.rows()).hasSize(1);assertThat(r.rowsInScope()).isEqualTo(2);assertThat(r.truncated()).isTrue();
        assertThat(r.totals().get("AMOUNT")).isEqualByComparingTo("125.01");assertThat(r.untrackedLoans()).isEqualTo(1);
    }
    @Test void immutableReversalsAndCutoffsAgreeWithPortfolioPrincipal(){
        assertThat(run(standard(Dataset.LOAN_PORTFOLIO,"en"),CUTOFF,25).totals().get("OUTSTANDING_PRINCIPAL")).isEqualByComparingTo("910.00");
        var after=run(standard(Dataset.COLLECTIONS,"en"),CUTOFF.plusMinutes(2),25);
        assertThat(after.rows()).hasSize(2);assertThat(after.totals().get("AMOUNT")).isZero();
        assertThat(after.rows().stream().map(r->(BigDecimal)r.get("AMOUNT"))).anyMatch(a->a.signum()<0);
        assertThat(run(standard(Dataset.LOAN_PORTFOLIO,"en"),CUTOFF.plusMinutes(2),25).totals().get("OUTSTANDING_PRINCIPAL")).isEqualByComparingTo("1000.00");
    }
    @Test void allowedGroupingAndParameterizedHostileFiltersNeverBroadenScope(){
        var d=standard(Dataset.COLLECTIONS,"en");
        var grouped=new OperationalReportDefinition(1,d.dataset(),d.title(),"","en",true,List.of(new Column(Field.CHANNEL,"",120,true),new Column(Field.AMOUNT,"",120,true)),List.of(),List.of(new Sort(Field.CHANNEL,false)),List.of(Field.CHANNEL),List.of(Field.AMOUNT));
        assertThat(run(grouped,CUTOFF,25).rows()).hasSize(1);
        var hostile=new OperationalReportDefinition(1,d.dataset(),d.title(),"","en",true,d.columns(),List.of(new Filter(Field.LOAN_ID,Operator.EQ,"' OR 1=1 --")),d.sorts(),List.of(),d.totals());
        assertThat(run(hostile,CUTOFF,25).rows()).isEmpty();
    }
    @Test void revokedClaimAndChangedOrInactiveBranchFailBeforeQueries(){
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.LOAN_REPAYMENTS_VIEW));
        assertThatThrownBy(()->run(standard(Dataset.COLLECTIONS,"en"),CUTOFF,25)).isInstanceOf(AccessDeniedException.class);
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(allClaims());
        when(institutions.findStation(institution,"B1")).thenReturn(Optional.of(SaccoStation.builder().active(false).build()));
        assertThatThrownBy(()->run(standard(Dataset.COLLECTIONS,"en"),CUTOFF,25)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void publicationRequiresIndependentCheckerAndPublishedDefinitionsStayImmutable(){
        UUID id=tx.execute(status->templates.save(standard(Dataset.COLLECTIONS,"en"),null,true,maker));
        assertThatThrownBy(()->tx.executeWithoutResult(status->templates.publish(id,maker))).hasMessage("report.error.checker");
        tx.executeWithoutResult(status->templates.publish(id,checker));
        var published=templates.get(id,maker,true);assertThat(published.state()).isEqualTo("PUBLISHED");assertThat(published.checkedBy()).isEqualTo(checker.getMemberId());
        assertThatThrownBy(()->jdbc.update("UPDATE operational_report_template_versions SET definition='{}' WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        UUID next=tx.execute(status->templates.save(standard(Dataset.COLLECTIONS,"sw"),published.templateId(),true,maker));
        assertThat(templates.get(next,maker,false).version()).isEqualTo(2);assertThat(templates.get(id,maker,true).definition().language()).isEqualTo("en");
        tx.executeWithoutResult(status->templates.retire(id,checker));assertThatThrownBy(()->templates.get(id,maker,true)).hasMessage("report.error.unpublished");
    }
    @Test void retentionGuardsRecognizeInstitutionCreatorMakerAndChecker(){
        assertThat(templates.hasInstitutionHistory(institution)).isFalse();assertThat(templates.hasMemberHistory(maker.getMemberId())).isFalse();
        UUID id=tx.execute(status->templates.save(standard(Dataset.COLLECTIONS,"en"),null,true,maker));
        assertThat(templates.hasInstitutionHistory(institution)).isTrue();assertThat(templates.hasInstitutionHistory("FOREIGN")).isFalse();
        assertThat(templates.hasMemberHistory(maker.getMemberId())).isTrue();assertThat(templates.hasMemberHistory(checker.getMemberId())).isFalse();
        tx.executeWithoutResult(status->templates.publish(id,checker));assertThat(templates.hasMemberHistory(checker.getMemberId())).isTrue();
        assertThat(templates.hasMemberHistory(UUID.randomUUID())).isFalse();
    }
    @Test void foreignInstitutionCannotReadOrPublishGuessedTemplate(){
        UUID id=tx.execute(status->templates.save(standard(Dataset.COLLECTIONS,"en"),null,true,maker));
        var foreign=operator("FOREIGN-"+UUID.randomUUID(),"B1");
        when(institutions.findActiveSacco(foreign.getSaccoId())).thenReturn(Optional.of(RegisteredSacco.builder().active(true).build()));
        when(institutions.findStation(foreign.getSaccoId(),"B1")).thenReturn(Optional.of(SaccoStation.builder().active(true).build()));
        assertThatThrownBy(()->templates.get(id,foreign,false)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->tx.executeWithoutResult(status->templates.publish(id,foreign))).isInstanceOf(AccessDeniedException.class);
    }
    @Test void selectedLogoIsOwnedByTrustedInstitutionAndItsExactBytesAreFrozen(){
        byte[] bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jrwoAAAAASUVORK5CYII=");
        when(logos.hasLogo(institution)).thenReturn(true);when(logos.load(institution)).thenReturn(new SaccoLogoStorageService.LogoResource(bytes,org.springframework.http.MediaType.IMAGE_PNG));
        var d=standard(Dataset.DISBURSEMENTS,"en");
        var branded=new OperationalReportDefinition(1,d.dataset(),d.title(),"","en",false,d.columns(),d.filters(),d.sorts(),d.groups(),d.totals(),true);
        var result=run(branded,CUTOFF,25);assertThat(result.branding().logo()).containsExactly(bytes);assertThat(result.branding().sha256()).hasSize(64);
        bytes[0]=0;assertThat(result.branding().logo()[0]).isNotZero();
        verify(logos).load(institution);verify(logos,never()).load("FOREIGN");
        when(institutions.findStation(institution,"B1")).thenReturn(Optional.empty());
        clearInvocations(logos);assertThatThrownBy(()->run(branded,CUTOFF,25)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(logos);
    }
    private OperationalReportService.Result run(OperationalReportDefinition d,OffsetDateTime cutoff,int size){return tx.execute(status->reports.execute(d,maker,FROM,THROUGH,cutoff,0,size));}
    private Set<UserClaim> allClaims(){return Set.of(UserClaim.REPORT_TEMPLATE_DESIGN,UserClaim.REPORT_TEMPLATE_PUBLISH,UserClaim.REPORT_TEMPLATE_SHARE,UserClaim.REPORT_RUN,UserClaim.REPORT_EXPORT,UserClaim.LOAN_REPAYMENTS_VIEW,UserClaim.LOAN_REPORTS_VIEW);}
    private AppUserPrincipal operator(String institution,String branch){
        jdbc.update("INSERT INTO registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) VALUES(?,'Synthetic reporting institution',true,now(),now()) ON CONFLICT DO NOTHING",institution);
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) VALUES(?,?,?,?,?,'ACTIVE','MANAGER',now(),true,'synthetic')",id,institution,branch,id.toString(),"Synthetic report officer");
        Member m=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).fullName("Synthetic report officer").position(Position.MANAGER).memberAccount(true).status(MemberStatus.ACTIVE).build();
        when(directory.find(id)).thenReturn(Optional.of(m));when(claims.effectiveClaims(eq(id),anyCollection(),anyBoolean())).thenReturn(allClaims());return new AppUserPrincipal(m,allClaims(),true);
    }
    private UUID openLoan(String institution,String branch,UUID applicant,boolean ledger){
        UUID id=UUID.randomUUID();long number=Math.abs(id.getLeastSignificantBits())%100000000000000000L;
        jdbc.update("INSERT INTO loan_applications(id,application_number,loan_id,sacco_id,station_id,applicant_member_id,amount,tenor_months,status,loan_type,form_data,policy_snapshot,required_guarantors,created_at,updated_at,version,disbursement_date) VALUES(?,?,?,?,?,?,1000,1,'DISBURSED','CUSTOMIZED_LOAN','{}','{}',0,?,?,0,?)",id,number,Long.toString(number),institution,branch,applicant,CUTOFF.minusMonths(1),CUTOFF.minusMonths(1),FROM);
        if(ledger)jdbc.update("INSERT INTO loan_ledgers(loan_application_id,sacco_id,station_id,loan_id,applicant_member_id,disbursement_date,principal,created_at) VALUES(?,?,?,?,?,?,1000,?)",id,institution,branch,Long.toString(number),applicant,FROM,CUTOFF.minusMonths(1));return id;
    }
    private void payment(UUID loan,String institution,String branch,UUID actor,String kind,String amount,String principal,OffsetDateTime posted,UUID original){
        UUID id=UUID.randomUUID();long sequence=kind.equals("PAYMENT")?1:2;BigDecimal total=new BigDecimal(amount),p=new BigDecimal(principal);
        jdbc.update("INSERT INTO loan_repayment_transactions(id,loan_application_id,sacco_id,station_id,sequence,receipt_reference,request_key,kind,channel,channel_reference,payment_date,amount,principal_amount,interest_amount,actor_member_id,reverses_transaction_id,reason,loan_status_before,posted_at) VALUES(?,?,?,?,?,?,?,?,'CASH',?,?, ?,?,?,?,?,?,'DISBURSED',?)",id,loan,institution,branch,sequence,id.toString(),UUID.randomUUID(),kind,id.toString(),THROUGH,total,p,total.subtract(p),actor,original,kind.equals("REVERSAL")?"Synthetic correction":null,posted);
    }
}
