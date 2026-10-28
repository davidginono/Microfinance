package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.repository.*;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.RuleForm;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.RuleRow;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.StepRow;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Approval;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.*;
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

@EnabledIfEnvironmentVariable(named="MICROFINANCE_VOUCHER_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_vouchers_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VoucherPostgresTest {
 HikariDataSource dataSource;
 JdbcTemplate jdbc; VoucherService service; TransactionTemplate tx; AuditService audit; UserClaimService claims; MemberDirectoryService directory;
 SaccoRegistryService institutions; String institution; AppUserPrincipal actor; UUID cash,capital,expense,control;
 com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository workflowRepository;
 com.sacco.mvp.accounting.approval.service.VoucherWorkflowService workflows;
 com.sacco.mvp.accounting.approval.service.VoucherWorkflowPostingService workflowPosting;
 com.sacco.mvp.accounting.approval.service.VoucherDocumentService documents;
 final LocalDate day=LocalDate.of(2026,10,1); final OffsetDateTime now=OffsetDateTime.parse("2026-10-08T10:00:00+03:00");
 @BeforeAll void start(){
  var ds=new HikariDataSource();dataSource=ds;ds.setJdbcUrl(System.getenv("MICROFINANCE_VOUCHER_TEST_URL"));ds.setUsername("microfinance_test");ds.setPassword("");ds.addDataSourceProperty("sslmode","disable");ds.setMinimumIdle(1);ds.setMaximumPoolSize(4);ds.setConnectionTimeout(60000);
  var migration=Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("55").load();
  migration.migrate();jdbc=new JdbcTemplate(ds);
  assertThat(jdbc.queryForObject("select checksum from flyway_schema_history where version='55' and success",Integer.class)).isEqualTo(-1305472888);
  Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
  assertThat(jdbc.queryForObject("select character_maximum_length from information_schema.columns where table_schema=current_schema() and table_name='accounting_voucher_transaction' and column_name='transaction_name'",Integer.class)).isEqualTo(240);
  assertThat(jdbc.queryForObject("select count(*) from pg_trigger where tgname in ('core_voucher_commit','core_journal_commit') and tgdeferrable and tginitdeferred",Integer.class)).isEqualTo(2);
  var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
  audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);
  var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(now.toLocalDate());when(clock.now()).thenReturn(now);
  var ledger=new GeneralLedgerService(new GeneralLedgerRepository(jdbc),new AccessControlService(),audit,clock,claims,directory,institutions);
  var raw=new VoucherService(new VoucherRepository(jdbc),ledger,clock,audit,JsonMapper.builder().findAndAddModules().build(),new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(new com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository(jdbc,JsonMapper.builder().findAndAddModules().build()),ledger));
  var proxy=new ProxyFactory(raw);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(VoucherService)proxy.getProxy();
  workflowRepository=new com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository(jdbc,JsonMapper.builder().findAndAddModules().build());
  var gate=new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(workflowRepository,ledger);
  workflows=transactional(new com.sacco.mvp.accounting.approval.service.VoucherWorkflowService(workflowRepository,gate,service,audit,clock,directory,claims),manager);
  workflowPosting=transactional(new com.sacco.mvp.accounting.approval.service.VoucherWorkflowPostingService(workflowRepository,gate,service,clock,audit),manager);
  documents=transactional(new com.sacco.mvp.accounting.approval.service.VoucherDocumentService(workflowRepository,workflows,clock,audit),manager);

 }
 @Test void statusCardAgeBucketsMatchScopedRowsAndCountsAtBoundaries(){
  java.util.function.Function<OffsetDateTime,UUID> draft=date->tx.execute(status->workflowRepository.create(actor,UUID.randomUUID(),Type.JOURNAL,"SINGLE","Card filter fixture",date).id());
  UUID fresh=draft.apply(now),seven=draft.apply(now.minusDays(7)),eight=draft.apply(now.minusDays(7).minusSeconds(1)),fourteen=draft.apply(now.minusDays(14)),older=draft.apply(now.minusDays(14).minusSeconds(1));
  var foreign=operator("B2");tx.execute(status->workflowRepository.create(foreign,UUID.randomUUID(),Type.JOURNAL,"SINGLE","Other branch",now));
  tx.execute(status->workflowRepository.create(actor,UUID.randomUUID(),Type.RECEIPT,"SINGLE","Other type",now));
  var buckets=Map.of("AGE_0_7",Set.of(fresh,seven),"AGE_8_14",Set.of(eight,fourteen),"AGE_15_PLUS",Set.of(older));
  for(var entry:buckets.entrySet()){
   var result=workflows.register(actor,Type.JOURNAL,"",entry.getKey(),"updated_desc",0,0,30);
   assertThat(result.rows()).extracting(w->w.id()).containsExactlyInAnyOrderElementsOf(entry.getValue());
   assertThat(result.counts().get(entry.getKey())).isEqualTo((long)entry.getValue().size());
  }
  var filtered=workflows.register(actor,Type.JOURNAL,"no match","AGE_0_7","number_asc",7,0,10);assertThat(filtered.rows()).isEmpty();
  assertThat(workflows.register(actor,Type.JOURNAL,"","AGE_15_PLUS","number_asc",7,0,10).rows()).isEmpty();
  var recent=workflows.register(actor,Type.JOURNAL,"","RECENT_CREATED","updated_desc",0,0,30);
  assertThat(recent.rows()).extracting(w->w.id()).containsExactlyInAnyOrder(fresh,seven);
  assertThat(recent.counts().get("RECENT_CREATED")).isEqualTo(2L);
  assertThat(recent.counts().get("RECENT_UPDATED")).isZero();
 }
 @AfterAll void stop(){if(dataSource!=null)dataSource.close();}
 @BeforeEach void fixture(){
  reset(audit,claims,directory,institutions);institution="V-"+UUID.randomUUID();
  jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?, 'Synthetic voucher institution',true,now(),now())",institution);
  when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
  when(institutions.findStation(eq(institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder().saccoId(institution).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
  when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
  when(directory.findAll(anyCollection())).thenAnswer(inv->{Collection<UUID> ids=inv.getArgument(0);return ids.stream().map(id->directory.find(id).orElse(null)).filter(Objects::nonNull).toList();});
  when(claims.effectiveClaims(anyMap())).thenAnswer(inv->{Map<UUID,UserClaimService.ClaimSubject> subjects=inv.getArgument(0);var out=new HashMap<UUID,Set<UserClaim>>();subjects.forEach((id,subject)->out.put(id,claims.effectiveClaims(id,subject.staffRoles(),subject.memberAccess())));return out;});
  actor=operator("B1");cash=account("CASH","ASSET","DEBIT","POSTING","CASH");capital=account("CAPITAL","EQUITY","CREDIT","POSTING","CAPITAL");expense=account("EXPENSE","EXPENSE","DEBIT","POSTING","OTHER");control=account("LOAN","ASSET","DEBIT","POSTING","LOAN_PRINCIPAL");
 }
 @Test void postsMultipleRowsDirectlyWithExactTotalsAndImmutableBalancedJournal(){
  var f=form(Type.RECEIPT);var v=service.post(actor,Type.RECEIPT,f);
  assertThat(v.total()).isEqualByComparingTo("30.03");assertThat(v.transactions()).hasSize(2);
  assertThat(jdbc.queryForMap("select state,checker_id,policy_id,direct_post from gl_journal where id=?",v.journalId())).containsEntry("state","POSTED").containsEntry("checker_id",null).containsEntry("policy_id",null).containsEntry("direct_post",true);
  assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id=?",BigDecimal.class,v.journalId())).isEqualByComparingTo("0");
  assertThat(service.post(actor,Type.RECEIPT,f).id()).isEqualTo(v.id());
  f.getRows().getFirst().setAmount(new BigDecimal("1.00"));assertThatThrownBy(()->service.post(actor,Type.RECEIPT,f)).hasMessage("voucher.error.retry");
  assertThatThrownBy(()->jdbc.update("update accounting_voucher set total=1 where id=?",v.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.update("delete from accounting_voucher_transaction where voucher_id=?",v.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);

 }
 @Test void voucherNumberReservationIsScopedStableAndConsumedByThePostedVoucher(){
  UUID key=UUID.randomUUID();var reserved=service.reserveNumber(actor,Type.JOURNAL,day,key);
  assertThat(service.reserveNumber(actor,Type.JOURNAL,day,key)).isEqualTo(reserved);
  var f=form(Type.JOURNAL);f.setRequestKey(key);var posted=service.post(actor,Type.JOURNAL,f);
  assertThat(posted.number()).isEqualTo(reserved.number());
  assertThat(jdbc.queryForObject("select consumed_at is not null from accounting_voucher_number_reservation where request_key=?",Boolean.class,key)).isTrue();
  assertThatThrownBy(()->service.reserveNumber(actor,Type.JOURNAL,day.minusDays(1),key)).hasMessage("voucher.error.retry");
  assertThatThrownBy(()->service.reserveNumber(operator("B2"),Type.JOURNAL,day,key)).hasMessage("voucher.error.request");
 }
 @Test void journalsWithoutPartyPreviewPostRetryAndReverseWithoutChangingHistory(){
  var f=form(Type.JOURNAL);f.setParty("");
  assertThat(service.preview(actor,Type.JOURNAL,f).total()).isEqualByComparingTo("30.03");
  var posted=service.post(actor,Type.JOURNAL,f);assertThat(posted.party()).isEmpty();
  assertThat(service.post(actor,Type.JOURNAL,f).id()).isEqualTo(posted.id());
  var key=UUID.randomUUID();var reversed=service.reverse(actor,Type.JOURNAL,posted.id(),key,day.plusDays(1),"Synthetic correction");
  assertThat(reversed.party()).isEmpty();assertThat(reversed.reversesId()).isEqualTo(posted.id());
  assertThat(service.reverse(actor,Type.JOURNAL,posted.id(),key,day.plusDays(1),"Synthetic correction").id()).isEqualTo(reversed.id());
  assertThat(service.view(actor,Type.JOURNAL,posted.id()).reversedBy()).isEqualTo(reversed.id());
  assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id in (?,?)",BigDecimal.class,posted.journalId(),reversed.journalId())).isEqualByComparingTo("0");
 }
 @Test void clientSubledgersAreScopedBoundedAndPreservedOnAllVoucherTypesAndReversals(){
  UUID client=client("B1","CL-001"),other=client("B1","CL-002"),foreign=client("B2","CL-FOREIGN");
  jdbc.update("update members set status='INACTIVE' where id=?",other);
  assertThat(service.clients(actor,Type.JOURNAL,"CL-",0)).extracting(Client::id).containsExactly(client);
  assertThat(service.clients(actor,Type.JOURNAL,"accountant",0)).isEmpty();
  for(int n=0;n<27;n++)client("B1",String.format("BOUND-%02d",n));
  assertThat(service.clients(actor,Type.JOURNAL,"BOUND-",0)).hasSize(26);
  assertThat(service.clients(actor,Type.JOURNAL,"BOUND-",1)).hasSize(2);
  assertThatThrownBy(()->service.clients(actor,Type.JOURNAL,"",10001)).isInstanceOf(IllegalArgumentException.class);
  for(UUID invalid:List.of(other,foreign,actor.getMemberId(),UUID.randomUUID())){
   var f=form(Type.JOURNAL);f.setClientId(invalid);
   assertThatThrownBy(()->service.preview(actor,Type.JOURNAL,f)).hasMessage("voucher.error.client");
   assertThatThrownBy(()->service.post(actor,Type.JOURNAL,f)).hasMessage("voucher.error.client");
  }
  for(Type type:Type.values()){
   var f=form(type);f.setClientId(client);var posted=service.post(actor,type,f);
   assertThat(posted.clientId()).isEqualTo(client);assertThat(posted.clientNumber()).isEqualTo(clientNumber("CL-001"));
   assertThat(service.post(actor,type,f).id()).isEqualTo(posted.id());
   f.setClientId(other);assertThatThrownBy(()->service.post(actor,type,f)).hasMessage("voucher.error.retry");
   assertThatThrownBy(()->jdbc.update("update accounting_voucher set client_number='CHANGED' where id=?",posted.id())).hasMessageContaining("append-only");
   jdbc.update("update members set member_no=?,station_id='B2',status='INACTIVE' where id=?",clientNumber("CL-RENAMED"),client);
   var reversal=service.reverse(actor,type,posted.id(),UUID.randomUUID(),day.plusDays(1),"Synthetic correction");
   assertThat(reversal.clientId()).isEqualTo(client);assertThat(reversal.clientNumber()).isEqualTo(clientNumber("CL-001"));
   jdbc.update("update members set member_no=?,station_id='B1',status='ACTIVE' where id=?",clientNumber("CL-001"),client);
  }
 }
 @Test void databaseRejectsForgedClientSnapshotsAndCorrections(){
  UUID client=client("B1","CL-VALID"),foreign=client("B2","CL-OTHER");
  var f=form(Type.JOURNAL);f.setClientId(client);var original=service.post(actor,Type.JOURNAL,f);
  String insert="""
   insert into accounting_voucher(id,sacco_id,station_id,number,type,request_key,payload_hash,effective_date,party,reference,description,evidence,money_account_id,total,transaction_count,journal_id,posted_by,posted_at,reverses_id,client_id,client_number)
   select ?,sacco_id,station_id,?,type,?,payload_hash,effective_date,party,?,description,evidence,money_account_id,total,transaction_count,journal_id,posted_by,posted_at,?, ?, ? from accounting_voucher where id=?
   """;
  for(UUID wrong:List.of(client,foreign))assertThatThrownBy(()->jdbc.update(insert,UUID.randomUUID(),"F-"+UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID().toString(),null,wrong,"CL-FORGED",original.id())).hasMessageContaining("active client");
  assertThatThrownBy(()->jdbc.update(insert,UUID.randomUUID(),"F-"+UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID().toString(),original.id(),foreign,"CL-OTHER",original.id())).hasMessageContaining("preserve the recorded client");
 }
 @Test void paymentAndJournalDirectionsAndSameAccountSplits(){
  var payment=service.post(actor,Type.PAYMENT,form(Type.PAYMENT));assertThat(payment.transactions()).allSatisfy(t->assertThat(t.credit().id()).isEqualTo(cash));
  var journal=service.post(actor,Type.JOURNAL,form(Type.JOURNAL));assertThat(journal.transactions()).allSatisfy(t->{assertThat(t.debit().id()).isEqualTo(expense);assertThat(t.credit().id()).isEqualTo(capital);});
  assertThat(service.list(actor,Type.RECEIPT,new Filter("",null,null,"","newest",0)).rows()).isEmpty();
 }
 @Test void directJournalCannotCommitWithoutItsPostedDocument(){
  var original=service.post(actor,Type.RECEIPT,form(Type.RECEIPT));var draft=UUID.randomUUID();
  assertThatThrownBy(()->tx.executeWithoutResult(s->jdbc.update("""
   insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,
    request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,direct_post)
   select ?,sacco_id,station_id,policy_version,period_id,source_type,?, ?,payload_hash,currency,'DRAFT',
    evidence_reference,reason,effective_date,maker_id,recorded_at,true from gl_journal where id=?
   """,draft,"DRAFT-"+draft,UUID.randomUUID(),original.journalId())))
   .isInstanceOf(org.springframework.transaction.TransactionSystemException.class).hasStackTraceContaining("Core vouchers must commit as posted documents");
  assertThat(jdbc.queryForObject("select count(*) from gl_journal where id=?",Integer.class,draft)).isZero();
 }
 @Test void sameAccountantReversesAllRowsOnceWithLinkedOriginalAndRetry(){
  var original=service.post(actor,Type.RECEIPT,form(Type.RECEIPT));var key=UUID.randomUUID();
  var reversal=service.reverse(actor,Type.RECEIPT,original.id(),key,day.plusDays(1),"Wrong payer");
  assertThat(reversal.reversesId()).isEqualTo(original.id());assertThat(service.view(actor,Type.RECEIPT,original.id()).reversedBy()).isEqualTo(reversal.id());
  assertThat(reversal.transactions()).allSatisfy(t->{assertThat(t.credit().id()).isEqualTo(cash);assertThat(t.debit().id()).isEqualTo(capital);});
  assertThat(service.reverse(actor,Type.RECEIPT,original.id(),key,day.plusDays(1),"Wrong payer").id()).isEqualTo(reversal.id());
  assertThatThrownBy(()->service.reverse(actor,Type.RECEIPT,original.id(),UUID.randomUUID(),day.plusDays(1),"Again")).hasMessage("voucher.error.reversal");
 }
 @Test void scopedAccessRevocationAndInvalidControlAccountsFail(){
  var v=service.post(actor,Type.RECEIPT,form(Type.RECEIPT));
  assertThatThrownBy(()->service.view(operator("B2"),Type.RECEIPT,v.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  assertThatThrownBy(()->service.view(actor,Type.PAYMENT,v.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  var f=form(Type.RECEIPT);f.getRows().getFirst().setCreditAccountId(control);assertThatThrownBy(()->service.post(actor,Type.RECEIPT,f)).hasMessage("voucher.error.account");
  when(claims.effectiveClaims(eq(actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());
  assertThatThrownBy(()->service.export(actor,Type.RECEIPT,v.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
 @Test void auditFailureRollsBackAndClosedPeriodAndDuplicateReferenceFail(){
  var f=form(Type.RECEIPT);
  doThrow(new IllegalStateException("Audit unavailable")).when(audit).logEvent(anyString(),any(),anyString(),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyMap());
  assertThatThrownBy(()->service.post(actor,Type.RECEIPT,f)).hasMessage("Audit unavailable");assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isZero();
  reset(audit);var original=service.post(actor,Type.RECEIPT,f);var same=form(Type.RECEIPT);same.setReference(f.getReference());assertThatThrownBy(()->service.post(actor,Type.RECEIPT,same)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  // A previously stored closed date is fixture data, not a bypass of the legacy close workflow.
  jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'CLOSED',?,?)",UUID.randomUUID(),institution,day.plusDays(1),day.plusDays(1),actor.getMemberId(),now);
  var closed=form(Type.RECEIPT);closed.setEffectiveDate(day.plusDays(1));
  assertThatThrownBy(()->service.post(actor,Type.RECEIPT,closed)).hasMessage("voucher.error.period");
 }
 @Test void concurrentDuplicateRequestsReturnOneVoucher()throws Exception{
  var f=form(Type.JOURNAL);var pool=Executors.newFixedThreadPool(2);
  try{var a=pool.submit(()->service.post(actor,Type.JOURNAL,f));var b=pool.submit(()->service.post(actor,Type.JOURNAL,f));assertThat(a.get(20,TimeUnit.SECONDS).id()).isEqualTo(b.get(20,TimeUnit.SECONDS).id());}finally{pool.shutdownNow();}
  assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isEqualTo(1);
 }
 @Test void registerPaginationFiltersAndSortAreBounded(){
  for(int n=0;n<27;n++)service.post(actor,Type.JOURNAL,form(Type.JOURNAL));
  var first=service.list(actor,Type.JOURNAL,new Filter("",day,day,"POSTED","amount",0));assertThat(first.rows()).hasSize(25);assertThat(first.hasNext()).isTrue();
  assertThat(service.list(actor,Type.JOURNAL,new Filter("",day,day,"POSTED","amount",1)).rows()).hasSize(2);
  assertThat(service.list(actor,Type.JOURNAL,new Filter("no-match",null,null,"","newest",0)).rows()).isEmpty();
 }
 @Test void configuredMappingsAreResolvedOnServerSnapshottedAndStaleRevisionsFail(){
  var configClock=mock(ApplicationClock.class);when(configClock.now()).thenReturn(now);when(configClock.today()).thenReturn(now.toLocalDate());
  var library=new AccountingCodeLibraryService(new AccountingCodeLibraryRepository(jdbc),
   new GeneralLedgerService(new GeneralLedgerRepository(jdbc),new AccessControlService(),audit,configClock,claims,directory,institutions),audit,configClock);
  var activityForm=new com.sacco.mvp.accounting.dto.AccountingLibraryDtos.CodeForm();activityForm.setCode("OPS");activityForm.setName("Operations");
  UUID activity=tx.execute(s->library.createActivity(actor,activityForm));var transactionForm=new com.sacco.mvp.accounting.dto.AccountingLibraryDtos.CodeForm();transactionForm.setActivityId(activity);transactionForm.setCode("RECEIVE");transactionForm.setName("Operating income "+"x".repeat(143));
  UUID transaction=tx.execute(s->library.createTransaction(actor,transactionForm));
  var template=new com.sacco.mvp.accounting.dto.AccountingLibraryDtos.TemplateForm();var rule=new com.sacco.mvp.accounting.dto.AccountingLibraryDtos.RuleForm();rule.setComponent("TOTAL");rule.setDebitCode("CASH");rule.setCreditCode("CAPITAL");template.setRules(List.of(rule));
  tx.executeWithoutResult(s->library.saveTemplate(actor,transaction,template));
  // Searches must match the visible component and both account codes/names, without broadening scope.
  var lookup=new VoucherRepository(jdbc);
  for(String query:List.of("receive","operating income","total","cash","synthetic capital"))
   assertThat(lookup.mappings(institution,query,0)).singleElement().satisfies(m->assertThat(m.transactionId()).isEqualTo(transaction));
  assertThat(lookup.mappings("OTHER-INSTITUTION","total",0)).isEmpty();
  assertThat(lookup.mappings(institution,"no-match",0)).isEmpty();
  assertThat(lookup.mappings(institution,"total",26)).isEmpty();
  assertThat(service.activities(actor,Type.RECEIPT,"operations",0)).extracting(Activity::id).containsExactly(activity);
  assertThat(service.activities(actor,Type.RECEIPT,"no match",0)).isEmpty();
  assertThat(lookup.activities("OTHER-INSTITUTION","",0)).isEmpty();
  assertThat(service.mappings(actor,Type.RECEIPT,activity,"",0)).extracting(Mapping::transactionId).containsExactly(transaction);
  UUID otherActivity=UUID.randomUUID();
  assertThat(service.mappings(actor,Type.RECEIPT,otherActivity,"",0)).isEmpty();
  UUID revision=library.template(actor,transaction).requestKey();var f=form(Type.RECEIPT);f.setActivityId(otherActivity);
  for(var row:f.getRows()){row.setTransactionId(transaction);row.setTemplateKey(revision);row.setCreditAccountId(control);}
  assertThatThrownBy(()->service.preview(actor,Type.RECEIPT,f)).hasMessage("voucher.error.template");
  assertThatThrownBy(()->service.post(actor,Type.RECEIPT,f)).hasMessage("voucher.error.template");
  f.setActivityId(activity);
  var gate=new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(workflowRepository,null);
  var captured=gate.canonical(f,UUID.randomUUID());UUID workflow=workflows.capture(actor,Type.RECEIPT,captured,false);
  var details=workflows.details(actor,workflow);var item=details.items().getFirst();
  var edit=gate.canonical(captured,captured.getRequestKey());edit.setActivityId(null);
  workflows.save(actor,workflow,item.id(),details.workflow().version(),item.version(),edit);
  assertThat(workflows.details(actor,workflow).items().getFirst().form().getActivityId()).isEqualTo(activity);
  assertThat(service.preview(actor,Type.RECEIPT,f).total()).isEqualByComparingTo("30.03");
  var reviewed=service.reviewAccounts(actor,Type.RECEIPT,f);assertThat(reviewed.get("rows[0].debitAccountId").id()).isEqualTo(cash);assertThat(reviewed.get("rows[0].creditAccountId").id()).isEqualTo(capital);
  var v=service.post(actor,Type.RECEIPT,f);assertThat(v.transactions()).allSatisfy(t->{assertThat(t.credit().id()).isEqualTo(capital);assertThat(t.transactionName()).contains("Operating income").hasSizeGreaterThan(160);});
  template.setRequestKey(UUID.randomUUID());template.setExpectedRequestKey(revision);rule.setCreditCode("EXPENSE");tx.executeWithoutResult(s->library.saveTemplate(actor,transaction,template));
  assertThat(service.post(actor,Type.RECEIPT,f).id()).isEqualTo(v.id());
  f.setRequestKey(UUID.randomUUID());f.setReference("New reference");assertThatThrownBy(()->service.post(actor,Type.RECEIPT,f)).hasMessage("voucher.error.staleTemplate");
  template.setExpectedRequestKey(library.template(actor,transaction).requestKey());template.setRequestKey(UUID.randomUUID());rule.setComponent("INTEREST");
  tx.executeWithoutResult(s->library.saveTemplate(actor,transaction,template));
  assertThat(lookup.mappings(institution,"interest",0)).singleElement().satisfies(m->assertThat(m.component()).isEqualTo("INTEREST"));
 }
 @Test void loanProductAccountChoicesAndValidationAcceptPostingAccountsOnly(){
  var accounts=new com.sacco.mvp.accounting.loans.repository.LoanProductAccountRepository(jdbc);
  UUID heading=account("GROUP","ASSET","DEBIT","HEADING","OTHER");
  assertThat(accounts.choices(institution,"",0,null)).extracting(com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice::id).contains(cash,capital,expense).doesNotContain(heading,control);
  for(UUID invalid:List.of(heading,control)){
   assertThat(accounts.choices(institution,"",0,invalid)).isEmpty();
   assertThat(accounts.valid(institution,invalid)).isFalse();
  }
  assertThat(accounts.choices(institution,"",0,cash)).extracting(com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice::id).containsExactly(cash);
  assertThat(accounts.valid(institution,cash)).isTrue();
  assertThat(accounts.choices("FOREIGN","",0,cash)).isEmpty();assertThat(accounts.valid("FOREIGN",cash)).isFalse();
 }

 @Test void completeVoucherRoutesImmediatelyAndOnlyMakerCanPost(){
  var reviewer=approver(Position.MANAGER);configure(rule("SINGLE",false,Position.MANAGER,1));var f=form(Type.JOURNAL);
  assertThatThrownBy(()->service.post(actor,Type.JOURNAL,f)).hasMessage("approval.error.required");
  UUID id=workflows.capture(actor,Type.JOURNAL,f);var w=workflows.details(actor,id).workflow();
  assertThat(w.approval()).isEqualTo(Approval.INITIATED);assertThat(w.validation()).isEqualTo("VALIDATED");
  assertThat(workflows.reviewActions(reviewer,List.of(w)).get(id)).contains("APPROVE");
  assertThat(workflows.reviewActions(actor,List.of(w)).get(id)).isEmpty();
  assertThatThrownBy(()->command(actor,id,"APPROVE")).hasMessage("approval.error.assignment");
  command(reviewer,id,"APPROVE");assertThat(workflows.details(actor,id).workflow().posting()).isEqualTo("NOT_POSTED");
  assertThatThrownBy(()->workflowPosting.post(reviewer,id,version(id))).hasMessage("approval.error.assignment");
  workflowPosting.post(actor,id,version(id));workflowPosting.post(actor,id,0);
  assertThat(workflows.details(actor,id).workflow().posting()).isEqualTo("POSTED");
  assertThat(service.post(actor,Type.JOURNAL,f).id()).isEqualTo(workflows.details(actor,id).items().getFirst().voucherId());
 }
 @Test void automaticPostingUsesMakerPermissionsAndPostsOnce(){
  var reviewer=approver(Position.MANAGER);configure(rule("SINGLE",true,Position.MANAGER,1));UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));
  command(reviewer,id,"APPROVE");automatic().attempt(id);automatic().attempt(id);
  assertThat(workflows.details(actor,id).workflow().posting()).isEqualTo("POSTED");
  assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select posted_by from accounting_voucher where sacco_id=?",UUID.class,institution)).isEqualTo(actor.getMemberId());
 }
 @Test void automaticPostingFailureKeepsApprovalAndAllowsMakerRetry(){
  var reviewer=approver(Position.MANAGER);configure(rule("SINGLE",true,Position.MANAGER,1));UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));command(reviewer,id,"APPROVE");
  var restricted=EnumSet.allOf(UserClaim.class);restricted.remove(UserClaim.VOUCHER_WORKSPACE_POST);
  when(claims.effectiveClaims(eq(actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(restricted);automatic().attempt(id);
  var w=workflows.details(actor,id).workflow();assertThat(w.approval()).isEqualTo(Approval.WORKFLOW_APPROVED);assertThat(w.posting()).isEqualTo("FAILED");
  assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isZero();
  when(claims.effectiveClaims(eq(actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));workflowPosting.post(actor,id,version(id));
  assertThat(workflows.details(actor,id).workflow().posting()).isEqualTo("POSTED");
 }
 @Test void roleQuorumAndLaterStepsRequireDifferentPeople(){
  var first=approver(Position.MANAGER);var second=approver(Position.MANAGER);var last=approver(Position.ACCOUNTANT);
  var r=rule("SINGLE",false,Position.MANAGER,2);var step=new StepRow();step.setRole(Position.ACCOUNTANT);r.getStages().add(step);configure(r);
  UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));command(first,id,"APPROVE");
  assertThat(workflows.details(actor,id).workflow().stage()).isZero();assertThat(workflows.details(actor,id).responses()).containsEntry(0,1L);
  assertThatThrownBy(()->command(first,id,"APPROVE")).hasMessage("approval.error.independent");
  assertThatThrownBy(()->command(last,id,"APPROVE")).hasMessage("approval.error.assignment");command(second,id,"APPROVE");
  assertThat(workflows.details(actor,id).workflow().stage()).isEqualTo(1);command(last,id,"APPROVE");
  assertThat(workflows.details(actor,id).workflow().approval()).isEqualTo(Approval.WORKFLOW_APPROVED);
  assertThat(jdbc.queryForObject("select count(distinct actor_id) from voucher_workflow_history where workflow_id=? and action='APPROVE'",Integer.class,id)).isEqualTo(3);
 }
 @Test void onePersonCannotFillTwoRoleSteps(){
  var reviewer=approver(Position.MANAGER);var member=directory.find(reviewer.getMemberId()).orElseThrow();member.setStaffRoles(Set.of(Position.MANAGER,Position.ACCOUNTANT));
  jdbc.update("insert into member_staff_roles values(?,?)",member.getId(),"ACCOUNTANT");
  var r=rule("SINGLE",false,Position.MANAGER,1);var step=new StepRow();step.setRole(Position.ACCOUNTANT);r.getStages().add(step);
  configure(r);assertThatThrownBy(()->workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL))).hasMessage("approval.error.staff");
  approver(Position.ACCOUNTANT);UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));command(reviewer,id,"APPROVE");
  assertThatThrownBy(()->command(reviewer,id,"APPROVE")).hasMessage("approval.error.independent");
 }
 @Test void rangesAreSeparateForSinglesAndBatchesAndUseExactBoundaries(){
  approver(Position.MANAGER);approver(Position.ACCOUNTANT);
  var low=rule("SINGLE",false,Position.MANAGER,1);low.setMaximum(new BigDecimal("30.03"));
  var high=rule("SINGLE",true,Position.ACCOUNTANT,1);high.setMinimum(new BigDecimal("30.03"));
  var batchRule=rule("BATCH",false,Position.MANAGER,1);configure(low,high,batchRule);
  UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));var w=workflows.details(actor,id).workflow();assertThat(w.autoPost()).isTrue();assertThat(w.stages().getFirst().role()).isEqualTo(Position.ACCOUNTANT);
  UUID batch=batch(2);command(actor,batch,"SEND");w=workflows.details(actor,batch).workflow();assertThat(w.total()).isEqualByComparingTo("60.06");assertThat(w.assessmentAmount()).isEqualByComparingTo("60.06");assertThat(w.autoPost()).isFalse();
 }
 @Test void overlappingRulesAreRejectedButDifferentLevelsCanShareRanges(){
  approver(Position.MANAGER);configure(rule("SINGLE",false,Position.MANAGER,1),rule("BATCH",false,Position.MANAGER,1));
  assertThatThrownBy(()->configure(rule("SINGLE",false,Position.MANAGER,1),rule("SINGLE",true,Position.MANAGER,1))).hasMessage("approval.error.overlap");
 }
 @Test void missingRuleNeverPostsOrCreatesAnApproval(){
  approver(Position.MANAGER);var r=rule("SINGLE",false,Position.MANAGER,1);r.setMinimum(new BigDecimal("100"));configure(r);
  assertThatThrownBy(()->workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL))).hasMessage("approval.error.noRule");
  assertThat(jdbc.queryForObject("select count(*) from voucher_workflow where sacco_id=?",Integer.class,institution)).isZero();
  assertThatThrownBy(()->service.post(actor,Type.JOURNAL,form(Type.JOURNAL))).hasMessage("approval.error.required");
 }
 @Test void savedRulesApplyImmediatelyAndPendingRequestsKeepTheirPath(){
  var manager=approver(Position.MANAGER);var accountant=approver(Position.ACCOUNTANT);configure(rule("SINGLE",false,Position.MANAGER,1));
  UUID old=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));configure(rule("SINGLE",true,Position.ACCOUNTANT,1));UUID next=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));
  assertThat(workflows.details(actor,old).workflow().configVersion()).isEqualTo(1);assertThat(workflows.details(actor,next).workflow().configVersion()).isEqualTo(2);
  command(manager,old,"APPROVE");workflowPosting.post(actor,old,version(old));command(accountant,next,"APPROVE");automatic().attempt(next);
  assertThat(workflows.details(actor,old).workflow().posting()).isEqualTo("POSTED");assertThat(workflows.details(actor,next).workflow().posting()).isEqualTo("POSTED");
  assertThatThrownBy(()->jdbc.update("delete from voucher_approval_configuration_history where sacco_id=?",institution)).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void draftsStayEditableUntilSentAndCorrectionsStartANewRound(){
  var reviewer=approver(Position.MANAGER);configure(rule("SINGLE",false,Position.MANAGER,1));UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL),false);
  assertThat(workflows.details(actor,id).workflow().approval()).isEqualTo(Approval.REQUIRED);command(actor,id,"SEND");
  assertThatThrownBy(()->workflows.save(actor,id,workflows.details(actor,id).items().getFirst().id(),version(id),0,form(Type.JOURNAL))).hasMessage("approval.error.stale");
  command(actor,id,"WITHDRAW");var d=workflows.details(actor,id);var item=d.items().getFirst();item.form().getRows().getFirst().setAmount(new BigDecimal("11"));workflows.save(actor,id,item.id(),version(id),item.version(),item.form());
  command(actor,id,"SEND");command(reviewer,id,"REJECT");command(actor,id,"SEND");command(reviewer,id,"APPROVE");
  assertThat(workflows.details(actor,id).workflow().round()).isEqualTo(3);
  assertThat(jdbc.queryForObject("select context->'vouchers'->0->'form'->'rows'->0->>'amount' from voucher_workflow_history where workflow_id=? and action='INITIATE' and round=1",String.class,id)).isEqualTo("10.01");
 }
 @Test void missingStaffOrRevokedAccessCannotCreateAnUnusableRoute(){
  assertThatThrownBy(()->configure(rule("SINGLE",false,Position.MANAGER,1))).hasMessage("approval.error.staff");
  var reviewer=approver(Position.MANAGER);configure(rule("SINGLE",false,Position.MANAGER,1));
  var limited=EnumSet.allOf(UserClaim.class);limited.remove(UserClaim.VOUCHER_APPROVAL_APPROVE);when(claims.effectiveClaims(eq(reviewer.getMemberId()),anyCollection(),anyBoolean())).thenReturn(limited);
  assertThatThrownBy(()->workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL))).hasMessage("approval.error.staff");
 }
 @Test void wrongRoleBranchAndRevokedClaimsCannotApprove(){
  var reviewer=approver(Position.MANAGER);configure(rule("SINGLE",false,Position.MANAGER,1));UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));
  assertThatThrownBy(()->command(operator("B1"),id,"APPROVE")).hasMessage("approval.error.assignment");
  assertThatThrownBy(()->workflows.details(operator("B2"),id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  var limited=EnumSet.allOf(UserClaim.class);limited.remove(UserClaim.VOUCHER_APPROVAL_APPROVE);when(claims.effectiveClaims(eq(reviewer.getMemberId()),anyCollection(),anyBoolean())).thenReturn(limited);
  assertThatThrownBy(()->command(reviewer,id,"APPROVE")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
 @Test void batchCreationAndSendingUseTheWholeFrozenBatch(){
  var reviewer=approver(Position.MANAGER);configure(rule("BATCH",false,Position.MANAGER,1));UUID key=UUID.randomUUID();UUID id=workflows.batch(actor,Type.JOURNAL,key);
  assertThat(workflows.batch(actor,Type.JOURNAL,key)).isEqualTo(id);var d=workflows.details(actor,id);var first=d.items().getFirst();
  workflows.save(actor,id,first.id(),version(id),first.version(),form(Type.JOURNAL));UUID second=workflows.add(actor,id,version(id));
  workflows.save(actor,id,second,version(id),0,form(Type.JOURNAL));command(actor,id,"SEND");assertThat(workflows.details(actor,id).workflow().total()).isEqualByComparingTo("60.06");
  assertThatThrownBy(()->workflows.add(actor,id,version(id))).hasMessage("approval.error.stale");command(reviewer,id,"APPROVE");workflowPosting.post(actor,id,version(id));
  assertThat(workflows.details(actor,id).items()).hasSize(2).allSatisfy(i->assertThat(i.voucherId()).isNotNull());
 }
 @Test void batchPostingRollsBackAllRowsOnFailure(){
  var reviewer=approver(Position.MANAGER);configure(rule("BATCH",false,Position.MANAGER,1));UUID id=batch(2);var d=workflows.details(actor,id);var second=d.items().get(1);second.form().setEffectiveDate(day.plusDays(1));workflows.save(actor,id,second.id(),version(id),second.version(),second.form());command(actor,id,"SEND");command(reviewer,id,"APPROVE");
  jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'OPEN',?,?)",UUID.randomUUID(),institution,day,day,actor.getMemberId(),now);
  jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'CLOSED',?,?)",UUID.randomUUID(),institution,day.plusDays(1),day.plusDays(1),actor.getMemberId(),now);
  assertThatThrownBy(()->workflowPosting.post(actor,id,version(id))).hasMessage("voucher.error.period");assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isZero();
  assertThat(workflows.details(actor,id).items()).allSatisfy(i->assertThat(i.voucherId()).isNull());
 }
 @Test void concurrentPostingAndApprovalRetriesCannotDuplicateDecisionsOrJournals()throws Exception{
  var reviewer=approver(Position.MANAGER);configure(rule("SINGLE",false,Position.MANAGER,1));UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));int v=version(id);command(reviewer,id,"APPROVE");
  assertThatThrownBy(()->workflows.action(reviewer,id,v,"APPROVE","")).hasMessage("approval.error.stale");final int approvedVersion=version(id);
  var pool=Executors.newFixedThreadPool(2);try{var first=pool.submit(()->workflowPosting.post(actor,id,approvedVersion));var second=pool.submit(()->workflowPosting.post(actor,id,approvedVersion));first.get(30,TimeUnit.SECONDS);second.get(30,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from voucher_workflow_history where workflow_id=? and action='APPROVE'",Integer.class,id)).isEqualTo(1);
 }
 @Test void draftValidationDocumentsAndUnknownItemsPreserveTheirGuards(){
  var f=form(Type.JOURNAL);UUID id=workflows.capture(actor,Type.JOURNAL,f,false);var d=workflows.details(actor,id);var item=d.items().getFirst();item.form().getRows().getFirst().setAmount(null);workflows.save(actor,id,item.id(),version(id),item.version(),item.form());
  workflows.validate(actor,id,version(id));assertThat(workflows.details(actor,id).items().getFirst().issues()).isNotEmpty();
  assertThatThrownBy(()->workflows.save(actor,id,UUID.randomUUID(),version(id),0,f)).hasMessage("approval.error.unavailable");
  documents.add(actor,id,"evidence.pdf","application/pdf","%PDF-1.4 synthetic".getBytes());assertThat(documents.list(actor,id)).hasSize(1);
  assertThatThrownBy(()->documents.get(operator("B2"),id,documents.list(actor,id).getFirst().id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
 @Test void legacyRulesCannotRouteNewRequestsAndExistingHistoryIsUntouched(){
  String definition="{\"type\":\"JOURNAL\",\"version\":1,\"individuals\":true,\"batches\":false,\"forceAllowed\":false,\"rules\":[]}";
  jdbc.update("insert into voucher_approval_configuration values(?,?,'JOURNAL',1,?::jsonb,?,?)",institution,"B1",definition,actor.getMemberId(),now);
  assertThatThrownBy(()->workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL))).hasMessage("approval.error.configuration");
  assertThat(jdbc.queryForObject("select definition->>'version' from voucher_approval_configuration_history where sacco_id=?",String.class,institution)).isEqualTo("1");
 }
 @Test void allOrdinaryVoucherTypesRouteWithTheirOwnRules(){
  var reviewer=approver(Position.MANAGER);
  for(var type:Type.values()){
   var rules=new RuleForm();rules.setType(type);rules.setRules(List.of(rule("SINGLE",false,Position.MANAGER,1)));workflows.configure(actor,rules);
   UUID id=workflows.capture(actor,type,form(type));assertThat(workflows.details(actor,id).workflow().type()).isEqualTo(type);command(reviewer,id,"APPROVE");workflowPosting.post(actor,id,version(id));
   assertThat(workflows.details(actor,id).workflow().posting()).isEqualTo("POSTED");
  }
 }
 @Test void removingDraftBatchItemsRequiresOwnerAndReusesAvailableLines(){
  approver(Position.MANAGER);configure(rule("BATCH",false,Position.MANAGER,1));UUID id=workflows.batch(actor,Type.JOURNAL,UUID.randomUUID());UUID item=workflows.add(actor,id,version(id));
  var other=operator("B1");assertThatThrownBy(()->workflows.remove(other,id,item,version(id),0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  workflows.remove(actor,id,item,version(id),0);assertThat(workflows.details(actor,id).items()).hasSize(1);
  UUID replacement=workflows.add(actor,id,version(id));assertThat(workflows.details(actor,id).items()).hasSize(2);assertThat(workflowRepository.item(id,replacement).orElseThrow().line()).isEqualTo(2);
 }
 @Test void concurrentQuorumApprovalsKeepEveryPersonDistinct()throws Exception{
  var first=approver(Position.MANAGER);var second=approver(Position.MANAGER);configure(rule("SINGLE",false,Position.MANAGER,2));UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));int expected=version(id);
  var pool=Executors.newFixedThreadPool(2);var outcomes=new ArrayList<Boolean>();
  try{var attempts=List.of(first,second).stream().map(person->pool.submit(()->{try{workflows.action(person,id,expected,"APPROVE","");return true;}catch(com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Invalid stale){assertThat(stale).hasMessage("approval.error.stale");return false;}})).toList();for(var attempt:attempts)outcomes.add(attempt.get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
  assertThat(outcomes).containsExactlyInAnyOrder(true,false);var pending=workflows.reviewActions(first,List.of(workflows.details(actor,id).workflow())).get(id).contains("APPROVE")?first:second;command(pending,id,"APPROVE");
  assertThat(workflows.details(actor,id).responses()).containsEntry(0,2L);assertThat(workflows.details(actor,id).workflow().approval()).isEqualTo(Approval.WORKFLOW_APPROVED);
 }
 @Test void sendingAnIncompleteBatchKeepsFieldErrorsWithoutOpeningApproval(){
  approver(Position.MANAGER);configure(rule("BATCH",false,Position.MANAGER,1));UUID id=workflows.batch(actor,Type.JOURNAL,UUID.randomUUID());command(actor,id,"SEND");
  var d=workflows.details(actor,id);assertThat(d.workflow().validation()).isEqualTo("ERROR");assertThat(d.workflow().approval()).isEqualTo(Approval.REQUIRED);assertThat(d.items().getFirst().issues()).isNotEmpty();
 }
 @Test void legacyRuleAndStepSnapshotsStillReadWithoutInventingDecisions(){
  var json=JsonMapper.builder().findAndAddModules().build();
  var configuration=json.readValue("""
   {"type":"JOURNAL","version":1,"individuals":true,"batches":false,"forceAllowed":false,
    "rules":[{"name":"Historical","minimum":0,"maximum":null,"automatic":false,
      "stages":[{"claim":"VOUCHER_APPROVAL_APPROVE","memberId":null}]}]}
   """,com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Configuration.class);
  assertThat(configuration.modelVersion()).isEqualTo(1);assertThat(configuration.rules().getFirst().autoPost()).isFalse();
  assertThat(configuration.rules().getFirst().stages().getFirst().required()).isEqualTo(1);assertThat(configuration.rules().getFirst().stages().getFirst().role()).isNull();
 }
 RuleRow rule(String scope,boolean autoPost,Position role,int count){var r=new RuleRow();r.setScope(scope);r.setAutoPost(autoPost);r.getStages().getFirst().setRole(role);r.getStages().getFirst().setApprovals(count);return r;}
 void configure(RuleRow... rows){var f=new RuleForm();f.setVersion(workflows.configuration(actor,Type.JOURNAL).version());f.setRules(new ArrayList<>(List.of(rows)));workflows.configure(actor,f);}
 AppUserPrincipal approver(Position role){var p=operator("B1");var member=directory.find(p.getMemberId()).orElseThrow();member.setPosition(role);jdbc.update("update members set position=? where id=?",role.name(),member.getId());return new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),true);}
 int version(UUID id){return workflows.details(actor,id).workflow().version();}
 void command(AppUserPrincipal user,UUID id,String action){workflows.action(user,id,workflows.details(user,id).workflow().version(),action,action.equals("REJECT")?"Wrong reference":"");}
 UUID batch(int count){return tx.execute(status->{var w=workflowRepository.create(actor,UUID.randomUUID(),Type.JOURNAL,"BATCH","Synthetic batch",now);for(int n=0;n<count;n++){var f=form(Type.JOURNAL);workflowRepository.save(w,actor,f.getRequestKey(),n+1,f,true);}jdbc.update("update voucher_workflow set item_count=?,validation='NOT_VALIDATED' where id=?",count,w.id());return w.id();});}
 com.sacco.mvp.accounting.approval.service.VoucherAutomaticPostingService automatic(){var c=mock(ApplicationClock.class);when(c.now()).thenReturn(now);return new com.sacco.mvp.accounting.approval.service.VoucherAutomaticPostingService(workflowRepository,workflowPosting,directory,claims,c,tx.getTransactionManager());}
 @SuppressWarnings("unchecked") static <T>T transactional(T raw,org.springframework.transaction.PlatformTransactionManager manager){var proxy=new ProxyFactory(raw);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)proxy.getProxy();}
 Form form(Type type){var f=new Form();f.setEffectiveDate(day);f.setParty("Synthetic payer");f.setReference("REF-"+UUID.randomUUID());if(type!=Type.JOURNAL)f.setMoneyAccountId(cash);var rows=new ArrayList<RowForm>();for(String amount:List.of("10.01","20.02")){var r=new RowForm();r.setDescription("Synthetic transaction "+amount);r.setAmount(new BigDecimal(amount));r.setDebitAccountId(expense);r.setCreditAccountId(capital);rows.add(r);}f.setRows(rows);return f;}
 String clientNumber(String base){return base+"-"+actor.getMemberId().toString().substring(0,8);}
 UUID client(String branch,String number){UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','MEMBER',now(),true,'test-only')",id,institution,branch,clientNumber(number),"Synthetic client "+number);return id;}
 UUID account(String code,String type,String balance,String kind,String purpose){UUID id=UUID.randomUUID();jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,maker_id,created_at) values(?,?,?,?,?,?,?,?,?,?)",id,institution,code,"Synthetic "+code,type,balance,kind,purpose,actor.getMemberId(),now);return id;}
 AppUserPrincipal operator(String branch){jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at) values(?,?,?,true,now(),now()) on conflict(sacco_id,station_id) do nothing",UUID.randomUUID(),institution,branch);UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','ACCOUNTANT',now(),false,'test-only')",id,institution,branch,id.toString(),"Synthetic accountant");
  var member=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).fullName("Synthetic accountant").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).build();when(directory.find(id)).thenReturn(Optional.of(member));return new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),true);
 }
}
