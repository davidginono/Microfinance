package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.repository.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService;
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
 JdbcTemplate jdbc; VoucherService service; TransactionTemplate tx; AuditService audit; UserClaimService claims; MemberDirectoryService directory;
 SaccoRegistryService institutions; AccountingPolicyService policies; String institution; AppUserPrincipal actor; UUID cash,capital,expense,control;
 final LocalDate day=LocalDate.of(2026,10,1); final OffsetDateTime now=OffsetDateTime.parse("2026-10-08T10:00:00+03:00");
 @BeforeAll void start(){
  var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_VOUCHER_TEST_URL"),"microfinance_test","");
  Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);
  var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
  audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);policies=mock(AccountingPolicyService.class);
  var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(now.toLocalDate());when(clock.now()).thenReturn(now);
  var ledger=new GeneralLedgerService(new GeneralLedgerRepository(jdbc),policies,new AccessControlService(),audit,clock,claims,directory,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class));
  var raw=new VoucherService(new VoucherRepository(jdbc),ledger,clock,audit,JsonMapper.builder().findAndAddModules().build());
  var proxy=new ProxyFactory(raw);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(VoucherService)proxy.getProxy();
 }
 @BeforeEach void fixture(){
  reset(audit,claims,directory,institutions,policies);institution="V-"+UUID.randomUUID();
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
  verifyNoInteractions(policies);
 }
 @Test void paymentAndJournalDirectionsAndSameAccountSplits(){
  var payment=service.post(actor,Type.PAYMENT,form(Type.PAYMENT));assertThat(payment.transactions()).allSatisfy(t->assertThat(t.credit().id()).isEqualTo(cash));
  var journal=service.post(actor,Type.JOURNAL,form(Type.JOURNAL));assertThat(journal.transactions()).allSatisfy(t->{assertThat(t.debit().id()).isEqualTo(expense);assertThat(t.credit().id()).isEqualTo(capital);});
  assertThat(service.list(actor,Type.RECEIPT,new Filter("",null,null,"","newest",0)).rows()).isEmpty();
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
   new GeneralLedgerService(new GeneralLedgerRepository(jdbc),policies,new AccessControlService(),audit,configClock,claims,directory,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class)),audit,configClock);
  var activityForm=new com.sacco.mvp.accounting.dto.AccountingLibraryDtos.CodeForm();activityForm.setCode("OPS");activityForm.setName("Operations");
  UUID activity=tx.execute(s->library.createActivity(actor,activityForm));var transactionForm=new com.sacco.mvp.accounting.dto.AccountingLibraryDtos.CodeForm();transactionForm.setActivityId(activity);transactionForm.setCode("RECEIVE");transactionForm.setName("Operating income");
  UUID transaction=tx.execute(s->library.createTransaction(actor,transactionForm));
  var template=new com.sacco.mvp.accounting.dto.AccountingLibraryDtos.TemplateForm();var rule=new com.sacco.mvp.accounting.dto.AccountingLibraryDtos.RuleForm();rule.setComponent("TOTAL");rule.setDebitCode("CASH");rule.setCreditCode("CAPITAL");template.setRules(List.of(rule));
  tx.executeWithoutResult(s->library.saveTemplate(actor,transaction,template));
  UUID revision=library.template(actor,transaction).requestKey();var f=form(Type.RECEIPT);
  for(var row:f.getRows()){row.setTransactionId(transaction);row.setTemplateKey(revision);row.setCreditAccountId(control);}
  var v=service.post(actor,Type.RECEIPT,f);assertThat(v.transactions()).allSatisfy(t->{assertThat(t.credit().id()).isEqualTo(capital);assertThat(t.transactionName()).contains("Operating income");});
  template.setRequestKey(UUID.randomUUID());template.setExpectedRequestKey(revision);rule.setCreditCode("EXPENSE");tx.executeWithoutResult(s->library.saveTemplate(actor,transaction,template));
  assertThat(service.post(actor,Type.RECEIPT,f).id()).isEqualTo(v.id());
  f.setRequestKey(UUID.randomUUID());f.setReference("New reference");assertThatThrownBy(()->service.post(actor,Type.RECEIPT,f)).hasMessage("voucher.error.staleTemplate");
 }
 Form form(Type type){var f=new Form();f.setEffectiveDate(day);f.setParty("Synthetic payer");f.setReference("REF-"+UUID.randomUUID());if(type!=Type.JOURNAL)f.setMoneyAccountId(cash);var rows=new ArrayList<RowForm>();for(String amount:List.of("10.01","20.02")){var r=new RowForm();r.setDescription("Synthetic transaction "+amount);r.setAmount(new BigDecimal(amount));r.setDebitAccountId(expense);r.setCreditAccountId(capital);rows.add(r);}f.setRows(rows);return f;}
 UUID account(String code,String type,String balance,String kind,String purpose){UUID id=UUID.randomUUID();jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,maker_id,created_at) values(?,?,?,?,?,?,?,?,?,?)",id,institution,code,"Synthetic "+code,type,balance,kind,purpose,actor.getMemberId(),now);return id;}
 AppUserPrincipal operator(String branch){UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','ACCOUNTANT',now(),false,'test-only')",id,institution,branch,id.toString(),"Synthetic accountant");
  var member=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).fullName("Synthetic accountant").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).build();when(directory.find(id)).thenReturn(Optional.of(member));return new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),true);
 }
}
