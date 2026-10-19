package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.repository.*;
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
 @AfterAll void stop(){if(dataSource!=null)dataSource.close();}
 @BeforeEach void fixture(){
  reset(audit,claims,directory,institutions);institution="V-"+UUID.randomUUID();
  jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?, 'Synthetic voucher institution',true,now(),now())",institution);
  when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
  when(institutions.findStation(eq(institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder().saccoId(institution).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
  when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
  actor=operator("B1");cash=account("CASH","ASSET","DEBIT","POSTING","CASH");capital=account("CAPITAL","EQUITY","CREDIT","POSTING","CAPITAL");expense=account("EXPENSE","EXPENSE","DEBIT","POSTING","OTHER");control=account("LOAN","ASSET","DEBIT","CONTROL","LOAN_PRINCIPAL");
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
  UUID revision=library.template(actor,transaction).requestKey();var f=form(Type.RECEIPT);
  for(var row:f.getRows()){row.setTransactionId(transaction);row.setTemplateKey(revision);row.setCreditAccountId(control);}
  var reviewed=service.reviewAccounts(actor,Type.RECEIPT,f);assertThat(reviewed.get("rows[0].debitAccountId").id()).isEqualTo(cash);assertThat(reviewed.get("rows[0].creditAccountId").id()).isEqualTo(capital);
  var v=service.post(actor,Type.RECEIPT,f);assertThat(v.transactions()).allSatisfy(t->{assertThat(t.credit().id()).isEqualTo(capital);assertThat(t.transactionName()).contains("Operating income").hasSizeGreaterThan(160);});
  template.setRequestKey(UUID.randomUUID());template.setExpectedRequestKey(revision);rule.setCreditCode("EXPENSE");tx.executeWithoutResult(s->library.saveTemplate(actor,transaction,template));
  assertThat(service.post(actor,Type.RECEIPT,f).id()).isEqualTo(v.id());
  f.setRequestKey(UUID.randomUUID());f.setReference("New reference");assertThatThrownBy(()->service.post(actor,Type.RECEIPT,f)).hasMessage("voucher.error.staleTemplate");
  template.setExpectedRequestKey(library.template(actor,transaction).requestKey());template.setRequestKey(UUID.randomUUID());rule.setComponent("INTEREST");
  tx.executeWithoutResult(s->library.saveTemplate(actor,transaction,template));
  assertThat(lookup.mappings(institution,"interest",0)).singleElement().satisfies(m->assertThat(m.component()).isEqualTo("INTEREST"));
 }

 @Test void configuredIndividualApprovalBlocksBypassAndSupportsIndependentApprovalAndPosting(){
  configure(true,true,false,false);var f=form(Type.JOURNAL);f.setParty("");UUID client=client("B1","CL-APPROVED");f.setClientId(client);
  assertThatThrownBy(()->service.post(actor,Type.JOURNAL,f)).hasMessage("approval.error.required");
  UUID id=workflows.capture(actor,Type.JOURNAL,f);assertThat(workflows.details(actor,id).items().getFirst().form().getClientId()).isEqualTo(client);validateWorkflow(id);
  command(actor,id,"INITIATE","");assertThat(workflows.details(actor,id).workflow().approval().name()).isEqualTo("INITIATED");
  assertThatThrownBy(()->command(actor,id,"APPROVE","")).hasMessage("approval.error.assignment");
  assertThatThrownBy(()->service.post(actor,Type.JOURNAL,f)).hasMessage("approval.error.required");
  var reviewer=operator("B1");command(reviewer,id,"APPROVE","Reviewed");
  workflowPosting.post(actor,id,workflows.details(actor,id).workflow().version());
  var d=workflows.details(actor,id);assertThat(d.workflow().posting()).isEqualTo("POSTED");assertThat(d.items()).allSatisfy(i->assertThat(i.voucherId()).isNotNull());
  workflowPosting.post(actor,id,0);assertThat(service.post(actor,Type.JOURNAL,f).id()).isEqualTo(d.items().getFirst().voucherId());assertThat(service.view(actor,Type.JOURNAL,d.items().getFirst().voucherId()).clientNumber()).isEqualTo(clientNumber("CL-APPROVED"));
  assertThatThrownBy(()->workflows.save(actor,id,d.items().getFirst().id(),d.workflow().version(),0,f)).hasMessage("approval.error.stale");
 }
 @Test void withdrawHoldRejectAndMaterialCorrectionsRequireResubmission(){
  configure(true,true,false,false);UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));validateWorkflow(id);
  command(actor,id,"HOLD","Check reference");assertThat(workflows.details(actor,id).workflow().approval().name()).isEqualTo("HELD_FROM_APPROVAL");
  command(actor,id,"RESUBMIT","");command(actor,id,"WITHDRAW","");command(actor,id,"RESUBMIT","");
  var reviewer=operator("B1");command(reviewer,id,"REJECT","Wrong account");command(actor,id,"RESUBMIT","");command(reviewer,id,"APPROVE","");
  var d=workflows.details(actor,id);var item=d.items().getFirst();item.form().getRows().getFirst().setAmount(new BigDecimal("11.00"));
  workflows.save(actor,id,item.id(),d.workflow().version(),item.version(),item.form());
  assertThat(workflows.details(actor,id).workflow().approval().name()).isEqualTo("RESUBMIT_FOR_APPROVAL");
  assertThatThrownBy(()->workflowPosting.post(actor,id,workflows.details(actor,id).workflow().version())).hasMessage("approval.error.validate");
  validateWorkflow(id);command(actor,id,"RESUBMIT","");command(reviewer,id,"APPROVE","");
 }
 @Test void batchPostingIsAtomicWhenLaterVoucherFailsAndCanRetryAfterCorrection(){
  configure(false,true,true,false);UUID id=batch(2);var before=workflows.details(actor,id);var later=before.items().get(1);later.form().setEffectiveDate(day.plusDays(1));workflows.save(actor,id,later.id(),before.workflow().version(),later.version(),later.form());
  validateWorkflow(id);command(actor,id,"INITIATE","");
  jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'OPEN',?,?)",UUID.randomUUID(),institution,day,day,actor.getMemberId(),now);
  jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'CLOSED',?,?)",UUID.randomUUID(),institution,day.plusDays(1),day.plusDays(1),actor.getMemberId(),now);
  var version=workflows.details(actor,id).workflow().version();assertThatThrownBy(()->workflowPosting.post(actor,id,version)).hasMessage("voucher.error.period");
  assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isZero();
  assertThat(workflows.details(actor,id).items()).allSatisfy(i->assertThat(i.voucherId()).isNull());
  workflowPosting.failed(actor,id,version,new IllegalArgumentException("voucher.error.period"));assertThat(workflows.details(actor,id).workflow().posting()).isEqualTo("FAILED");
  var d=workflows.details(actor,id);var item=d.items().get(1);item.form().setEffectiveDate(day);workflows.save(actor,id,item.id(),d.workflow().version(),item.version(),item.form());
  validateWorkflow(id);command(actor,id,"RESUBMIT","");workflowPosting.post(actor,id,workflows.details(actor,id).workflow().version());
  assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isEqualTo(2);
 }
 @Test void forceApprovalRequiresEnabledOptionCurrentClaimsAndReason(){
  configure(true,true,false,false);UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));validateWorkflow(id);
  assertThatThrownBy(()->command(actor,id,"FORCE","Urgent")).hasMessage("approval.error.force");
  configure(true,true,false,true);validateWorkflow(id);
  assertThatThrownBy(()->command(actor,id,"FORCE","")).hasMessage("approval.error.comments");
  var allowed=EnumSet.allOf(UserClaim.class);allowed.remove(UserClaim.VOUCHER_APPROVAL_FORCE);when(claims.effectiveClaims(eq(actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(allowed);
  assertThatThrownBy(()->command(actor,id,"FORCE","Urgent")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  when(claims.effectiveClaims(eq(actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));command(actor,id,"FORCE","Documented exception");
  assertThat(workflows.details(actor,id).workflow().approval().name()).isEqualTo("MANUALLY_APPROVED");
 }
 @Test void sequentialStagesRequireDistinctApproversAndPolicyChangesInvalidatePosting(){
  var f=ruleForm(true,true,false,false);f.getRules().getFirst().setSecondClaim("VOUCHER_APPROVAL_ESCALATED_APPROVE");workflows.configure(actor,f);
  UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));validateWorkflow(id);command(actor,id,"INITIATE","");
  var reviewer=operator("B1");command(reviewer,id,"APPROVE","");assertThatThrownBy(()->command(reviewer,id,"APPROVE","")).hasMessage("approval.error.independent");
  var senior=operator("B1");var reviewerOnly=new AppUserPrincipal(directory.find(senior.getMemberId()).orElseThrow(),EnumSet.of(UserClaim.VOUCHER_WORKSPACE_VIEW,UserClaim.VOUCHER_APPROVAL_ESCALATED_APPROVE,UserClaim.ACCOUNTING_JOURNALS_VIEW),true);
  assertThat(workflows.selectedAccounts(reviewerOnly,id,workflows.details(reviewerOnly,id).items().getFirst().form())).containsKeys("rows[0].debitAccountId","rows[0].creditAccountId");command(reviewerOnly,id,"APPROVE","");
  configure(true,true,false,false);
  assertThatThrownBy(()->workflowPosting.post(actor,id,workflows.details(actor,id).workflow().version())).hasMessage("approval.error.configurationChanged");
 }
 @Test void validationRecordsFieldWarningsAndRejectsCrossBranchOrRevokedWorkspaceAccess(){
  UUID id=workflows.capture(actor,Type.PAYMENT,form(Type.PAYMENT));var d=workflows.details(actor,id);var i=d.items().getFirst();i.form().getRows().getFirst().setAmount(null);i.form().setParty("");workflows.save(actor,id,i.id(),d.workflow().version(),i.version(),i.form());validateWorkflow(id);
  var invalid=workflows.details(actor,id);assertThat(invalid.workflow().validation()).isEqualTo("ERROR");assertThat(invalid.items().getFirst().issues()).extracting(com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Issue::field).contains("party","rows[0].amount");
  assertThatThrownBy(()->workflows.details(operator("B2"),id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  when(claims.effectiveClaims(eq(actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(()->workflows.details(actor,id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
 @Test void mixedRegisterUsesScopedTypesDatesCountsAndRealSorts(){
  UUID journal=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));workflows.capture(actor,Type.PAYMENT,form(Type.PAYMENT));workflows.capture(actor,Type.RECEIPT,form(Type.RECEIPT));
  var result=workflows.register(actor,null,"","","date_asc",0,0,10);assertThat(result.rows()).hasSize(3);assertThat(result.counts().get("ALL")).isEqualTo(3L);assertThat(result.counts().get("recent_created")).isEqualTo(3L);
  assertThat(workflows.transactionDates(actor,result.rows()).get(journal)).isEqualTo(day.toString());
  assertThat(workflows.register(actor,null,"","RECENT_CREATED","owner_desc",7,0,10).rows()).hasSize(3);
  var restricted=EnumSet.allOf(UserClaim.class);restricted.remove(UserClaim.ACCOUNTING_BUSINESS_VIEW);var journalOnly=new AppUserPrincipal(directory.find(actor.getMemberId()).orElseThrow(),restricted,true);
  assertThat(workflows.register(journalOnly,null,"","","type_asc",0,0,10).rows()).extracting(com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Workflow::type).containsExactly(Type.JOURNAL);
  assertThatThrownBy(()->workflows.register(journalOnly,Type.PAYMENT,"","","date_desc",0,0,10)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  configure(true,true,false,false);validateWorkflow(journal);command(actor,journal,"INITIATE","");var reviewer=operator("B1");var workflow=workflows.details(actor,journal).workflow();
  assertThat(workflows.reviewActions(reviewer,List.of(workflow)).get(journal)).contains("APPROVE","REJECT");assertThat(workflows.reviewActions(actor,List.of(workflow)).get(journal)).isEmpty();
  assertThat(workflows.register(operator("B2"),null,"","","date_desc",0,0,10).rows()).isEmpty();
 }
 @Test void ruleValidationDoesNotActivateOrSaveAPolicy(){
  var f=ruleForm(true,true,false,false);workflows.validateConfiguration(actor,f);assertThat(workflows.configuration(actor,Type.JOURNAL).version()).isZero();
  var overlap=new com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.RuleRow();overlap.setName("Overlap");f.getRules().add(overlap);
  assertThatThrownBy(()->workflows.validateConfiguration(actor,f)).hasMessage("approval.error.overlap");assertThat(workflows.configuration(actor,Type.JOURNAL).individuals()).isFalse();
 }
 @Test void validationRetainsEngineErrorsWithoutRollingBackOtherVoucherResults(){
  UUID id=batch(2);var d=workflows.details(actor,id);var item=d.items().get(1);item.form().getRows().getFirst().setCreditAccountId(control);
  workflows.save(actor,id,item.id(),d.workflow().version(),item.version(),item.form());validateWorkflow(id);
  var result=workflows.details(actor,id);assertThat(result.workflow().validation()).isEqualTo("ERROR");
  assertThat(result.items().getFirst().planHash()).isNotNull();assertThat(result.items().get(1).issues()).extracting(com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Issue::code).contains("voucher.error.account");
 }
 @Test void postingAndFailureRecordingCannotBypassVoucherTypeViewClaims(){
  configure(true,true,true,false);UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));validateWorkflow(id);command(actor,id,"INITIATE","");
  int version=workflows.details(actor,id).workflow().version();var restricted=EnumSet.allOf(UserClaim.class);restricted.remove(UserClaim.ACCOUNTING_JOURNALS_VIEW);
  var withoutTypeView=new AppUserPrincipal(directory.find(actor.getMemberId()).orElseThrow(),restricted,true);
  assertThatThrownBy(()->workflowPosting.post(withoutTypeView,id,version)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  assertThatThrownBy(()->workflowPosting.failed(withoutTypeView,id,version,new IllegalArgumentException("voucher.error.account"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  assertThat(workflows.details(actor,id).workflow().posting()).isEqualTo("NOT_POSTED");
 }
 @Test void policyRevisionsAndApprovalContextRemainImmutableAfterConfigurationChanges(){
  configure(true,true,true,false);UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));validateWorkflow(id);command(actor,id,"INITIATE","");
  var original=jdbc.queryForObject("select definition::text from voucher_approval_configuration_history where sacco_id=? and version=1",String.class,institution);
  configure(true,true,false,false);
  assertThat(jdbc.queryForObject("select definition::text from voucher_approval_configuration_history where sacco_id=? and version=1",String.class,institution)).isEqualTo(original);
  assertThat(jdbc.queryForObject("select (context->>'configurationVersion')::integer from voucher_workflow_history where workflow_id=? and action='INITIATE'",Integer.class,id)).isEqualTo(1);
  assertThatThrownBy(()->jdbc.update("delete from voucher_approval_configuration_history where sacco_id=?",institution)).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void documentEvidenceIsScopedAndInvalidatesAnApproval(){
  configure(true,true,true,false);UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));validateWorkflow(id);command(actor,id,"INITIATE","");
  documents.add(actor,id,"proof.pdf","application/pdf","%PDF-1.7 synthetic".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  assertThat(workflows.details(actor,id).workflow().approval().name()).isEqualTo("RESUBMIT_FOR_APPROVAL");var doc=documents.list(actor,id).getFirst();assertThat(doc.content()).isNull();
  assertThat(documents.get(actor,id,doc.id()).content()).isNotEmpty();assertThatThrownBy(()->documents.get(operator("B2"),id,doc.id())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  assertThatThrownBy(()->documents.add(actor,id,"x.pdf","application/pdf","not a pdf".getBytes())).hasMessage("approval.error.document");
 }
 @Test void concurrentWorkflowPostingCommitsOneBatchAndRulesRejectOverlaps()throws Exception{
  configure(false,true,true,false);UUID id=batch(2);validateWorkflow(id);command(actor,id,"INITIATE","");int version=workflows.details(actor,id).workflow().version();
  var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->workflowPosting.post(actor,id,version));var b=pool.submit(()->workflowPosting.post(actor,id,version));a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isEqualTo(2);
  var f=ruleForm(true,true,false,false);var row=new com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.RuleRow();row.setName("Overlapping");f.getRules().add(row);assertThatThrownBy(()->workflows.configure(actor,f)).hasMessage("approval.error.overlap");
 }
 // Existing batch records are fixture data, not a production creation API.
 UUID batch(int count){return tx.execute(status->{var workflow=workflowRepository.create(actor,UUID.randomUUID(),Type.JOURNAL,"BATCH","Synthetic existing batch",now);for(int n=0;n<count;n++){var f=form(Type.JOURNAL);workflowRepository.save(workflow,actor,f.getRequestKey(),n+1,f,true);}jdbc.update("update voucher_workflow set item_count=?,validation='NOT_VALIDATED' where id=?",count,workflow.id());return workflow.id();});}
 @Test void correctingAnApprovalCannotAppendAnUnknownVoucher(){
  UUID id=workflows.capture(actor,Type.JOURNAL,form(Type.JOURNAL));var before=workflows.details(actor,id);var extra=form(Type.JOURNAL);
  assertThatThrownBy(()->workflows.save(actor,id,extra.getRequestKey(),before.workflow().version(),0,extra)).hasMessage("approval.error.unavailable");
  var after=workflows.details(actor,id);assertThat(after.items()).hasSize(1);assertThat(after.workflow().version()).isEqualTo(before.workflow().version());
 }
 void validateWorkflow(UUID id){workflows.validate(actor,id,workflows.details(actor,id).workflow().version());}
 void command(AppUserPrincipal user,UUID id,String action,String comments){workflows.action(user,id,workflows.details(user,id).workflow().version(),action,comments);}
 void configure(boolean individual,boolean batch,boolean automatic,boolean force){workflows.configure(actor,ruleForm(individual,batch,automatic,force));}
 com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.RuleForm ruleForm(boolean individual,boolean batch,boolean automatic,boolean force){var f=new com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.RuleForm();f.setVersion(workflows.configuration(actor,Type.JOURNAL).version());f.setIndividuals(individual);f.setBatches(batch);f.setForceAllowed(force);f.getRules().getFirst().setName("Default approval rule");f.getRules().getFirst().setAutomatic(automatic);return f;}
 @SuppressWarnings("unchecked") static <T>T transactional(T raw,org.springframework.transaction.PlatformTransactionManager manager){var proxy=new ProxyFactory(raw);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)proxy.getProxy();}
 Form form(Type type){var f=new Form();f.setEffectiveDate(day);f.setParty("Synthetic payer");f.setReference("REF-"+UUID.randomUUID());if(type!=Type.JOURNAL)f.setMoneyAccountId(cash);var rows=new ArrayList<RowForm>();for(String amount:List.of("10.01","20.02")){var r=new RowForm();r.setDescription("Synthetic transaction "+amount);r.setAmount(new BigDecimal(amount));r.setDebitAccountId(expense);r.setCreditAccountId(capital);rows.add(r);}f.setRows(rows);return f;}
 String clientNumber(String base){return base+"-"+actor.getMemberId().toString().substring(0,8);}
 UUID client(String branch,String number){UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','MEMBER',now(),true,'test-only')",id,institution,branch,clientNumber(number),"Synthetic client "+number);return id;}
 UUID account(String code,String type,String balance,String kind,String purpose){UUID id=UUID.randomUUID();jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,maker_id,created_at) values(?,?,?,?,?,?,?,?,?,?)",id,institution,code,"Synthetic "+code,type,balance,kind,purpose,actor.getMemberId(),now);return id;}
 AppUserPrincipal operator(String branch){jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at) values(?,?,?,true,now(),now()) on conflict(sacco_id,station_id) do nothing",UUID.randomUUID(),institution,branch);UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','ACCOUNTANT',now(),false,'test-only')",id,institution,branch,id.toString(),"Synthetic accountant");
  var member=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).fullName("Synthetic accountant").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).build();when(directory.find(id)).thenReturn(Optional.of(member));return new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),true);
 }
}
