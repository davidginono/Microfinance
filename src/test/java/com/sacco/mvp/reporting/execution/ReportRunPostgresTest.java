package com.sacco.mvp.reporting.execution;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.reporting.*;
import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.*;
import com.sacco.mvp.reporting.execution.repository.ReportRunRepository;
import com.sacco.mvp.reporting.execution.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_REPORT_H_DATABASE_URL",matches="(?:(?:jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_h_(test|release_test|release_final_test)|jdbc:postgresql://127\\.0\\.0\\.1:55439/microfinance_accounting_h_release_combined_test_20261004)|jdbc:postgresql://127\\.0\\.0\\.1:55439/microfinance_accounting_h_release_integrity_test_20261004)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportRunPostgresTest {
 private JdbcTemplate jdbc,outside;
 private PlatformTransactionManager manager;
 private TransactionTemplate tx;
 private ReportRunRepository repository;
 private ReportRunService runs;
 private OperationalReportService reports;
 private OperationalReportTemplateService templates;
 private ReportRunGenerator generator;
 private ReportWorkerControl control;
 private MemberDirectoryService members;
 private UserClaimService claims;
 private AuditService audit;
 private ApplicationClock clock;
 private AppUserPrincipal maker,checker,otherBranch,foreign;
 private String institution;
 private UUID template;
 private OffsetDateTime now;
 private Set<UserClaim> allowed;
 @BeforeAll void migrate(){
  var dataSource=new DriverManagerDataSource(System.getenv("MICROFINANCE_REPORT_H_DATABASE_URL"),"microfinance_test","");
  Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(dataSource);outside=new JdbcTemplate(new DriverManagerDataSource(System.getenv("MICROFINANCE_REPORT_H_DATABASE_URL"),"microfinance_test",""));manager=new DataSourceTransactionManager(dataSource);tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
 }
 @BeforeEach void fixture(){
  now=OffsetDateTime.now();clock=mock(ApplicationClock.class);when(clock.now()).thenAnswer(invocation->OffsetDateTime.now());when(clock.today()).thenReturn(now.toLocalDate());
  members=mock(MemberDirectoryService.class);claims=mock(UserClaimService.class);var institutions=mock(SaccoRegistryService.class);audit=mock(AuditService.class);
  allowed=Set.of(UserClaim.REPORT_TEMPLATE_DESIGN,UserClaim.REPORT_TEMPLATE_PUBLISH,UserClaim.REPORT_TEMPLATE_SHARE,UserClaim.REPORT_RUN,UserClaim.REPORT_EXPORT,UserClaim.REPORT_RUN_APPROVE,UserClaim.REPORT_JOBS_REVIEW,UserClaim.LOAN_REPORTS_VIEW,UserClaim.LOAN_REPAYMENTS_VIEW);
  institution="RH-"+UUID.randomUUID();institution(institution);institution("FOREIGN-"+institution);
  for(String tenant:List.of(institution,"FOREIGN-"+institution)){when(institutions.findActiveSacco(tenant)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(tenant).active(true).build()));for(String branch:List.of("B1","B2"))when(institutions.findStation(tenant,branch)).thenReturn(Optional.of(SaccoStation.builder().saccoId(tenant).stationId(branch).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));}
  maker=actor(institution,"B1");checker=actor(institution,"B1");otherBranch=actor(institution,"B2");foreign=actor("FOREIGN-"+institution,"B1");
  reports=spy(new OperationalReportService(new OperationalReportRepository(new NamedParameterJdbcTemplate(jdbc)),new AccessControlService(),clock,members,claims,institutions,mock(SaccoLogoStorageService.class)));
  var mapper=JsonMapper.builder().findAndAddModules().build();repository=new ReportRunRepository(jdbc,mapper);templates=new OperationalReportTemplateService(new OperationalReportTemplateRepository(jdbc,mapper),reports,new AccessControlService(),clock,audit);
  runs=proxy(new ReportRunService(repository,reports,templates,new AccessControlService(),clock,audit,new ReportResultCodec(mapper)));
  template=tx.execute(status->templates.save(OperationalReportDefinition.standard(OperationalReportDefinition.Dataset.DISBURSEMENTS,"en"),null,true,maker));tx.executeWithoutResult(status->templates.publish(template,checker));
  control=proxy(new ReportWorkerControl(repository,clock,reports));var messages=new StaticMessageSource();messages.setUseCodeAsDefaultMessage(true);
  generator=proxy(new ReportRunGenerator(repository,reports,templates,new OperationalReportExportService(messages),control,new ReportResultCodec(mapper),members,clock,audit));
 }
 @AfterEach void releaseSyntheticJobs(){jdbc.update("UPDATE report_runs SET status='CANCELLED',cancel_requested=true,lease_until=NULL WHERE sacco_id IN(?,?) AND status IN('QUEUED','RUNNING','FAILED')",institution,"FOREIGN-"+institution);}
 @Test void scopedDuplicateRequestReturnsSameRunButChangedPayloadIsRejected(){
  var request=request(UUID.randomUUID());UUID id=tx.execute(status->runs.request(maker,request));UUID duplicate=tx.execute(status->runs.request(maker,request));assertThat(duplicate).isEqualTo(id);
  var changed=new Request(request.requestKey(),template,request.from(),request.through(),request.recordedCutoff(),Set.of(OperationalReportExportService.Format.XLSX),null,null);
  assertThatThrownBy(()->tx.execute(status->runs.request(maker,changed))).hasMessage("report.run.error.payload");
  assertThatThrownBy(()->runs.get(id,otherBranch)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->runs.get(id,foreign)).isInstanceOf(AccessDeniedException.class);
 }
 @Test void completedEmptyReportRetainsTypedResultArtifactAndIndependentApproval(){
  UUID id=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));Run run=control.claim().orElseThrow();assertThat(run.id()).isEqualTo(id);generator.generate(run);
  assertThat(runs.get(id,maker).status()).isEqualTo("READY");assertThat(runs.get(id,maker).snapshot()).isNotBlank();var artifact=runs.artifacts(id,maker).getFirst();byte[] before=tx.execute(status->runs.download(id,artifact.id(),maker)).bytes();
  assertThatThrownBy(()->tx.executeWithoutResult(status->runs.approve(id,"Synthetic review",maker))).hasMessage("report.run.error.checker");tx.executeWithoutResult(status->runs.approve(id,"Synthetic independent review",checker));
  assertThat(runs.get(id,maker).status()).isEqualTo("APPROVED");assertThat(tx.execute(status->runs.download(id,artifact.id(),maker)).bytes()).isEqualTo(before);
  assertThatThrownBy(()->jdbc.update("UPDATE report_artifacts SET payload='changed'::bytea WHERE id=?",artifact.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.update("DELETE FROM report_result_pages WHERE run_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE report_runs SET from_date=from_date-1 WHERE id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void revokedPermissionsProtectFrozenDownloadsAndQueuedExecution(){
  UUID id=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));generator.generate(control.claim().orElseThrow());UUID artifact=runs.artifacts(id,maker).getFirst().id();
  when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.REPORT_RUN,UserClaim.LOAN_REPORTS_VIEW));
  assertThatThrownBy(()->tx.execute(status->runs.download(id,artifact,maker))).isInstanceOf(AccessDeniedException.class);
  when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(allowed);UUID queued=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));Run job=control.claim().orElseThrow();
  when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.REPORT_RUN,UserClaim.LOAN_REPORTS_VIEW));
  assertThatThrownBy(()->generator.generate(job)).isInstanceOf(AccessDeniedException.class);control.fail(job,"report.run.error.permission");assertThat(runs.get(queued,maker).status()).isEqualTo("FAILED");assertThat(repository.pages(queued,0,25)).isEmpty();
 }
 @Test void auditFailureRollsBackAllPagesArtifactsAndReadyStateThenRetryCanRecover(){
  UUID id=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));Run job=control.claim().orElseThrow();
  doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).log(eq("REPORT_RUN"),eq(id),eq("GENERATE"),any(),any(),any());
  assertThatThrownBy(()->generator.generate(job)).hasMessage("Synthetic audit failure");assertThat(repository.pages(id,0,25)).isEmpty();assertThat(repository.artifacts(id)).isEmpty();assertThat(runs.get(id,maker).status()).isEqualTo("RUNNING");
  control.fail(job,"report.run.error.failed");reset(audit);tx.executeWithoutResult(status->runs.retry(id,maker));generator.generate(control.claim().orElseThrow());assertThat(runs.get(id,maker).attempts()).isEqualTo(2);
 }
 @Test void cancellationPreventsAClaimOrRollsBackRunningGeneration(){
  UUID id=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));tx.executeWithoutResult(status->runs.cancel(id,maker));assertThat(runs.get(id,maker).status()).isEqualTo("CANCELLED");
  UUID second=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));Run job=control.claim().orElseThrow();tx.executeWithoutResult(status->runs.cancel(second,maker));assertThatThrownBy(()->generator.generate(job)).hasMessage("report.run.error.cancelled");control.fail(job,"report.run.error.cancelled");assertThat(runs.get(second,maker).status()).isEqualTo("CANCELLED");
 }
 @Test void quotaAndWorkerFairnessKeepOneInstitutionFromTakingAllSlots(){
  UUID one=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));tx.execute(status->runs.request(maker,request(UUID.randomUUID())));
  assertThatThrownBy(()->tx.execute(status->runs.request(maker,request(UUID.randomUUID())))).hasMessage("report.run.error.quota");
  tx.execute(status->runs.request(checker,request(UUID.randomUUID())));Run first=control.claim().orElseThrow();assertThat(first.id()).isEqualTo(one);assertThat(control.claim()).isEmpty();
  var foreignChecker=actor(foreign.getSaccoId(),"B1");UUID foreignVersion=tx.execute(status->templates.save(OperationalReportDefinition.standard(OperationalReportDefinition.Dataset.DISBURSEMENTS,"en"),null,true,foreign));tx.executeWithoutResult(status->templates.publish(foreignVersion,foreignChecker));var base=request(UUID.randomUUID());
  tx.execute(status->runs.request(foreign,new Request(base.requestKey(),foreignVersion,base.from(),base.through(),base.recordedCutoff(),base.formats(),null,null)));Run next=control.claim().orElseThrow();assertThat(next.institution()).isEqualTo(foreign.getSaccoId());generator.generate(next);generator.generate(first);assertThat(control.claim()).isPresent();
 }
 @Test void approvedRestatementKeepsPriorArtifactsAndRejectsCrossBranchParent(){
  UUID id=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));generator.generate(control.claim().orElseThrow());tx.executeWithoutResult(status->runs.approve(id,"Synthetic reviewed coverage",checker));UUID originalArtifact=runs.artifacts(id,maker).getFirst().id();byte[] original=tx.execute(status->runs.download(id,originalArtifact,maker)).bytes();
  var base=request(UUID.randomUUID());var restatement=new Request(base.requestKey(),template,base.from(),base.through(),base.recordedCutoff(),base.formats(),id,"Synthetic late correction");UUID next=tx.execute(status->runs.request(maker,restatement));generator.generate(control.claim().orElseThrow());tx.executeWithoutResult(status->runs.approve(next,"Synthetic restatement review",checker));assertThat(runs.get(next,maker).restates()).isEqualTo(id);assertThat(tx.execute(status->runs.download(id,originalArtifact,maker)).bytes()).isEqualTo(original);assertThatThrownBy(()->tx.execute(status->runs.request(otherBranch,restatement))).isInstanceOf(AccessDeniedException.class);
 }
 @Test void foreignTemplateMappingAndChangedRequestMetadataFailAtDatabaseBoundary(){
  UUID id=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));
  assertThatThrownBy(()->jdbc.update("UPDATE report_runs SET sacco_id=? WHERE id=?",foreign.getSaccoId(),id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.update("INSERT INTO report_runs(id,sacco_id,station_id,requested_by,request_key,request_hash,template_version_id,template_version,dataset_version,metric_version,dataset,definition,from_date,through_date,recorded_cutoff,formats,status,requested_at) SELECT ?::uuid,?::varchar,station_id,requested_by,?::uuid,request_hash,template_version_id,template_version,dataset_version,metric_version,dataset,definition,from_date,through_date,recorded_cutoff,formats,status,requested_at FROM report_runs WHERE id=?",UUID.randomUUID(),foreign.getSaccoId(),UUID.randomUUID(),id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining("foreign key");
 }
 @Test void committedBackdatedSourceBetweenPagesCannotChangeFrozenSnapshotOrTotals(){
  seedLoans(jdbc,1001);UUID id=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));AtomicBoolean inserted=new AtomicBoolean();
  doAnswer(invocation->{var result=invocation.callRealMethod();if((Integer)invocation.getArgument(5)==0&&inserted.compareAndSet(false,true))seedLoans(outside,1);return result;}).when(reports).execute(any(),any(),any(),any(),any(),anyInt(),anyInt());
  generator.generate(control.claim().orElseThrow());var frozen=runs.frozen(id,maker,40);assertThat(frozen.rowsInScope()).isEqualTo(1001);assertThat(frozen.rows()).hasSize(1);assertThat(frozen.totals().get("PRINCIPAL")).isEqualByComparingTo("1001010.01");
  var current=tx.execute(status->reports.execute(OperationalReportDefinition.standard(OperationalReportDefinition.Dataset.DISBURSEMENTS,"en"),maker,now.toLocalDate().minusDays(30),now.toLocalDate(),now,0,25));assertThat(current.rowsInScope()).isEqualTo(1002);assertThat(runs.frozen(id,maker,0).rowsInScope()).isEqualTo(1001);
 }
 @Test void concurrentIdempotentRequestsCreateOneJob(){
  var request=request(UUID.randomUUID());try(var pool=Executors.newFixedThreadPool(2)){List<Future<UUID>> jobs=new ArrayList<>();for(int n=0;n<2;n++)jobs.add(pool.submit(()->tx.execute(status->runs.request(maker,request))));UUID one=jobs.get(0).get(20,TimeUnit.SECONDS);assertThat(jobs.get(1).get(20,TimeUnit.SECONDS)).isEqualTo(one);assertThat(repository.active(institution,maker.getMemberId())).isEqualTo(1);}catch(Exception ex){throw new AssertionError(ex);}
 }
 @Test void largestSupportedCsvAndWorkbookCaptureAreBoundedAndExact(){
  seedLoans(jdbc,20000);var base=request(UUID.randomUUID());UUID id=tx.execute(status->runs.request(maker,new Request(base.requestKey(),template,base.from(),base.through(),base.recordedCutoff(),Set.of(OperationalReportExportService.Format.CSV,OperationalReportExportService.Format.XLSX),null,null)));
  var pools=java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream().filter(p->p.getType()==java.lang.management.MemoryType.HEAP).toList();pools.forEach(java.lang.management.MemoryPoolMXBean::resetPeakUsage);long start=System.nanoTime();generator.generate(control.claim().orElseThrow());long elapsed=(System.nanoTime()-start)/1_000_000;long peaks=pools.stream().mapToLong(p->p.getPeakUsage().getUsed()).sum();
  assertThat(runs.get(id,maker).rows()).isEqualTo(20000);assertThat(repository.pages(id,0,25)).hasSize(20);assertThat(runs.frozen(id,maker,799).rows()).hasSize(25);assertThat(runs.frozen(id,maker,0).totals().get("PRINCIPAL")).isEqualByComparingTo("20000200.00");assertThat(runs.artifacts(id,maker)).hasSize(2);for(var artifact:runs.artifacts(id,maker))assertThat(artifact.bytes()).isBetween(1L,32L*1024*1024);System.out.println("H bounded CSV/XLSX generation: rows=20000 elapsedMs="+elapsed+" aggregateHeapPoolPeaksBytes="+peaks+" configuredMaxHeapBytes="+Runtime.getRuntime().maxMemory()+"; synthetic single job, not an HTTP throughput claim");
 }
 @Test void cancellationCommittedDuringPaginationRollsBackCapturedRows()throws Exception{
  seedLoans(jdbc,1001);UUID id=tx.execute(status->runs.request(maker,request(UUID.randomUUID())));Run job=control.claim().orElseThrow();AtomicBoolean cancelled=new AtomicBoolean();
  doAnswer(invocation->{var result=invocation.callRealMethod();if((Integer)invocation.getArgument(5)==0&&cancelled.compareAndSet(false,true))try(var pool=Executors.newSingleThreadExecutor()){pool.submit(()->runs.cancel(id,maker)).get(30,TimeUnit.SECONDS);}return result;}).when(reports).execute(any(),any(),any(),any(),any(),anyInt(),anyInt());
  assertThatThrownBy(()->generator.generate(job)).hasMessage("report.run.error.cancelled");control.fail(job,"report.run.error.cancelled");assertThat(runs.get(id,maker).status()).isEqualTo("CANCELLED");assertThat(repository.pages(id,0,25)).isEmpty();assertThat(repository.artifacts(id)).isEmpty();
 }
 private Request request(UUID key){return new Request(key,template,now.toLocalDate().minusDays(30),now.toLocalDate(),now,Set.of(OperationalReportExportService.Format.CSV),null,null);}
 private void institution(String id){jdbc.update("INSERT INTO registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) VALUES(?,'Synthetic report execution institution',true,now(),now())",id);}
 private void seedLoans(JdbcTemplate connection,int count){
  long base=Math.abs(UUID.randomUUID().getLeastSignificantBits()%1000000000000000L);
  connection.update("WITH source AS(SELECT gen_random_uuid() id,?::bigint+g number FROM generate_series(1,?) g),loans AS(INSERT INTO loan_applications(id,application_number,loan_id,sacco_id,station_id,applicant_member_id,amount,tenor_months,status,loan_type,form_data,policy_snapshot,required_guarantors,created_at,updated_at,version,disbursement_date) SELECT id,number,number::text,?,'B1',?,1000.01,1,'DISBURSED','CUSTOMIZED_LOAN','{}','{}',0,?,?,0,? FROM source RETURNING id,loan_id) INSERT INTO loan_ledgers(loan_application_id,sacco_id,station_id,loan_id,applicant_member_id,disbursement_date,principal,created_at) SELECT id,?,'B1',loan_id,?,?,1000.01,? FROM loans",base,count,institution,maker.getMemberId(),now.minusDays(1),now.minusDays(1),now.toLocalDate().minusDays(1),institution,maker.getMemberId(),now.toLocalDate().minusDays(1),now.minusDays(1));
 }
 private AppUserPrincipal actor(String tenant,String branch){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) VALUES(?,?,?,?,?,'ACTIVE','ACCOUNTANT',now(),false,'synthetic')",id,tenant,branch,id.toString(),"Synthetic officer");Member member=Member.builder().id(id).saccoId(tenant).stationId(branch).memberNo(id.toString()).fullName("Synthetic officer").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).memberAccount(false).build();when(members.find(id)).thenReturn(Optional.of(member));when(claims.effectiveClaims(eq(id),anyCollection(),anyBoolean())).thenReturn(allowed);return new AppUserPrincipal(member,allowed,true);}
 @SuppressWarnings("unchecked") private <T>T proxy(T target){ProxyFactory factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();}
}
