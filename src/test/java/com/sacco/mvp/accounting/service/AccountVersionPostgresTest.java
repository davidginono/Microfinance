package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.AccountOnboardingForm;
import com.sacco.mvp.accounting.dto.AccountVersionDtos.*;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNT_VERSION_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_account_versions_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountVersionPostgresTest {
    HikariDataSource ds; JdbcTemplate jdbc; GeneralLedgerService ledger; GeneralLedgerRepository books;
    AuditService audit; UserClaimService claims; MemberDirectoryService directory; SaccoRegistryService institutions;
    AppUserPrincipal maker; String institution; UUID family; DataSourceTransactionManager manager;
    final OffsetDateTime now=OffsetDateTime.parse("2026-10-18T10:00:00+03:00");
    @BeforeAll void start() {
        ds=new HikariDataSource();ds.setJdbcUrl(System.getenv("MICROFINANCE_ACCOUNT_VERSION_TEST_URL"));ds.setUsername("microfinance_test");ds.setPassword("");ds.setMaximumPoolSize(4);ds.addDataSourceProperty("sslmode","disable");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);
        var clock=mock(ApplicationClock.class);when(clock.now()).thenReturn(now);when(clock.today()).thenReturn(now.toLocalDate());books=new GeneralLedgerRepository(jdbc);
        var proxy=new ProxyFactory(new GeneralLedgerService(books,new AccessControlService(),audit,clock,claims,directory,institutions));proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));ledger=(GeneralLedgerService)proxy.getProxy();
    }
    @AfterAll void stop(){if(ds!=null)ds.close();}
    @Test void loanPurposesUsePostingAccountsAndNewControlClassificationsAreRejected(){
        var command=form(family,"111099","Loan principal");command.setPurpose("LOAN_PRINCIPAL");command.setKind("CONTROL");
        assertThatThrownBy(()->ledger.onboardAccount(maker,command,false)).hasMessage("accounting.error.accountClassification");
        command.setKind("POSTING");UUID account=ledger.onboardAccount(maker,command,false);
        assertThat(books.account(institution,account).orElseThrow().kind()).isEqualTo("POSTING");
        assertThat(jdbc.queryForObject("select gl_account_requires_loan_source(?)",Boolean.class,account)).isTrue();
    }
    @BeforeEach void setup(){
        reset(audit,claims,directory,institutions);institution="COA-"+UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic account version institution',true,now(),now())",institution);
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
        when(institutions.findStation(eq(institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder().saccoId(institution).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));maker=operator("B1",EnumSet.allOf(UserClaim.class));
        ledger.initializeChart(maker);UUID root=books.accountCode(institution,"100000").orElseThrow().id();
        var group=form(root,"110000","Current assets");UUID subgroup=ledger.onboardAccount(maker,group,true);family=ledger.onboardAccount(maker,form(subgroup,"111000","Cash accounts"),true);clearInvocations(audit);
    }
    AppUserPrincipal operator(String branch,Set<UserClaim> permissions){
        UUID id=UUID.randomUUID();String number="COA-"+id;
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at) values(?,?,?,true,now(),now()) on conflict(sacco_id,station_id) do nothing",UUID.randomUUID(),institution,branch);
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','ACCOUNTANT',now(),false,'test-only')",id,institution,branch,number,"Synthetic "+number);
        var m=Member.builder().id(id).memberNo(number).fullName("Synthetic "+number).saccoId(institution).stationId(branch).position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build();
        when(directory.find(id)).thenReturn(Optional.of(m));return new AppUserPrincipal(m,permissions,true);
    }
    AccountOnboardingForm form(UUID parent,String code,String name){var f=new AccountOnboardingForm();f.setParentId(parent);f.setCode(code);f.setName(name);f.setNormalBalance("DEBIT");f.setReason("Documented account setup");return f;}
    Version version(UUID id){return books.version(institution,id).orElseThrow();}
    PolicyForm policy(boolean create,boolean update,boolean deactivate,boolean reactivate){var f=new PolicyForm();f.setVersion(books.accountPolicy(institution).version());f.setCreateAutomatic(create);f.setUpdateAutomatic(update);f.setDeactivateAutomatic(deactivate);f.setReactivateAutomatic(reactivate);f.setReason("Institution approved configuration");return f;}
    EditForm change(Account a,String name){var f=GeneralLedgerService.metadataForm(a);f.setName(name);f.setReason("Correct account description");return f;}
    LoanProductSetting product(){
        UUID id=UUID.randomUUID();jdbc.update("insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,created_at,updated_at) values(?,?,'DEVELOPMENT_LOAN',true,'ACTIVE',?,'Business growth','{}',0,0.24,'FLAT_RATE','MONTHLY',1,12,now(),now())",id,institution,"PRODUCT-"+id);
        return LoanProductSetting.builder().id(id).saccoId(institution).productName("Business growth").productCode("PRODUCT-"+id).build();
    }
    com.sacco.mvp.accounting.loans.service.LoanProductAccountService productAccounts(){return new com.sacco.mvp.accounting.loans.service.LoanProductAccountService(new com.sacco.mvp.accounting.loans.repository.LoanProductAccountRepository(jdbc),new ApplicationClock("Africa/Nairobi"),audit,ledger);}
    @Test void newProductsGenerateTwoApprovedPostingAccountsWithoutGrantingChartPermissions(){
        var creator=operator("B1",Set.of(UserClaim.LOAN_PRODUCTS_CREATE));
        when(claims.effectiveClaims(eq(creator.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.LOAN_PRODUCTS_CREATE));
        var product=product();var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.executeWithoutResult(t->productAccounts().provision(product,creator.getMemberId()));
        assertThat(product.getPrincipalGlAccountId()).isNotNull();assertThat(product.getInterestGlAccountId()).isNotNull();
        assertThat(books.account(institution,product.getPrincipalGlAccountId()).orElseThrow()).satisfies(a->{assertThat(a.kind()).isEqualTo("POSTING");assertThat(a.type()).isEqualTo("ASSET");assertThat(a.name()).isEqualTo("Business growth - Principal");});
        assertThat(books.account(institution,product.getInterestGlAccountId()).orElseThrow()).satisfies(a->{assertThat(a.kind()).isEqualTo("POSTING");assertThat(a.type()).isEqualTo("INCOME");assertThat(a.name()).isEqualTo("Business growth - Interest");});
        tx.executeWithoutResult(t->productAccounts().provision(product,creator.getMemberId()));
        assertThat(jdbc.queryForObject("select count(*) from gl_account where sacco_id=? and code like 'LP-%'",Integer.class,institution)).isEqualTo(2);
        assertThat(jdbc.queryForMap("select principal_gl_account_id,interest_gl_account_id from loan_product_settings where id=?",product.getId())).containsEntry("principal_gl_account_id",product.getPrincipalGlAccountId()).containsEntry("interest_gl_account_id",product.getInterestGlAccountId());
        assertThat(creator.getClaims()).containsExactly("LOAN_PRODUCTS_CREATE");
        var vouchers=new com.sacco.mvp.accounting.repository.VoucherRepository(jdbc);
        assertThat(vouchers.accounts(institution,"LP-",0)).isEmpty();
        assertThat(vouchers.selectedAccounts(institution,List.of(product.getPrincipalGlAccountId(),product.getInterestGlAccountId()),false)).isEmpty();
    }
    @Test void independentlyApprovedProductAccountsLinkOnlyWhenBothAreEffective(){
        ledger.configureAccountApproval(maker,policy(false,true,true,true));var creator=operator("B1",Set.of(UserClaim.LOAN_PRODUCTS_CREATE));
        when(claims.effectiveClaims(eq(creator.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.LOAN_PRODUCTS_CREATE));
        var product=product();var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.executeWithoutResult(t->productAccounts().provision(product,creator.getMemberId()));
        var ids=jdbc.queryForMap("select principal_version_id,interest_version_id from loan_product_account_provision where product_id=?",product.getId());
        var principal=version((UUID)ids.get("principal_version_id"));var interest=version((UUID)ids.get("interest_version_id"));
        assertThat(principal.state()).isEqualTo("PENDING");assertThat(interest.state()).isEqualTo("PENDING");assertThat(product.getPrincipalGlAccountId()).isNull();
        assertThat(books.account(institution,principal.accountId())).isEmpty();
        assertThatThrownBy(()->ledger.accountVersionAction(creator,principal.id(),principal.revision(),"APPROVE","Self approval")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        ledger.accountVersionAction(maker,principal.id(),principal.revision(),"APPROVE","Principal account reviewed");
        assertThat(jdbc.queryForObject("select principal_gl_account_id is null and interest_gl_account_id is null from loan_product_settings where id=?",Boolean.class,product.getId())).isTrue();
        ledger.accountVersionAction(maker,interest.id(),interest.revision(),"APPROVE","Interest account reviewed");
        assertThat(jdbc.queryForMap("select principal_gl_account_id,interest_gl_account_id from loan_product_settings where id=?",product.getId())).containsEntry("principal_gl_account_id",principal.accountId()).containsEntry("interest_gl_account_id",interest.accountId());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id=? and action='LOAN_PRODUCT_GENERATED_ACCOUNTS_LINKED'",Integer.class,product.getId())).isEqualTo(1);
    }
    @Test void generatedAccountsRollbackWithTheProductCommandAndRejectForeignScope(){
        var product=product();var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        assertThatThrownBy(()->tx.executeWithoutResult(t->ledger.provisionLoanProductAccounts("FOREIGN",maker.getMemberId(),product.getId(),product.getDisplayName()))).hasMessage("accounting.error.account");
        assertThatThrownBy(()->tx.executeWithoutResult(t->{productAccounts().provision(product,maker.getMemberId());throw new IllegalStateException("Rollback whole command");})).hasMessage("Rollback whole command");
        assertThat(jdbc.queryForObject("select count(*) from loan_product_account_provision where product_id=?",Integer.class,product.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from gl_account where sacco_id=? and code like 'LP-%'",Integer.class,institution)).isZero();
    }
    @Test void completeCreationAutomaticallyApprovesOnceAndKeepsSystemEvidence(){
        var f=form(family,"111001","Office cash");UUID id=ledger.onboardVersion(maker,f,false,true);var v=version(id);
        assertThat(v.state()).isEqualTo("APPROVED");assertThat(v.mode()).isEqualTo("AUTOMATIC");assertThat(v.reviewer()).isNull();assertThat(v.policyVersion()).isZero();assertThat(books.account(institution,v.accountId())).isPresent();
        assertThat(ledger.onboardVersion(maker,f,false,true)).isEqualTo(id);
        assertThat(jdbc.queryForObject("select count(*) from gl_account_version_event where version_id=?",Integer.class,id)).isEqualTo(2);
        f.setName("Different payload");assertThatThrownBy(()->ledger.onboardVersion(maker,f,false,true)).hasMessage("accounting.error.versionRetry");
    }
    @Test void incompleteDraftNeverBecomesAnAccountAndCanBeCompletedWithoutSecondApproval(){
        var f=form(family,"111002","");f.setReason("");UUID id=ledger.onboardVersion(maker,f,false,false);var v=version(id);
        assertThat(v.state()).isEqualTo("DRAFT");assertThat(books.account(institution,v.accountId())).isEmpty();
        var edit=change(v.proposed(),"Completed cash account");edit.setRevision(v.revision());ledger.saveAccountVersion(maker,id,edit,true);
        assertThat(version(id).state()).isEqualTo("APPROVED");assertThat(books.account(institution,v.accountId())).isPresent();
    }
    @Test void independentCreationIsUnavailableUntilAnotherInstitutionReviewerApproves(){
        ledger.configureAccountApproval(maker,policy(false,false,false,false));UUID id=ledger.onboardVersion(maker,form(family,"111003","Pending cash"),false,true);var v=version(id);
        assertThat(v.state()).isEqualTo("PENDING");assertThat(books.account(institution,v.accountId())).isEmpty();
        assertThatThrownBy(()->ledger.accountVersionAction(maker,id,v.revision(),"APPROVE","Checked")).hasMessage("accounting.error.versionSelfApproval");
        var reviewer=operator("B2",EnumSet.allOf(UserClaim.class));ledger.accountVersionAction(reviewer,id,v.revision(),"APPROVE","Checked against institution chart");
        assertThat(version(id).reviewer()).isEqualTo(reviewer.getMemberId());assertThat(books.account(institution,v.accountId())).isPresent();
        ledger.accountVersionAction(reviewer,id,v.revision(),"APPROVE","Checked against institution chart");
        assertThat(jdbc.queryForObject("select count(*) from gl_account_version_event where version_id=? and action='APPROVE'",Integer.class,id)).isEqualTo(1);
    }
    @Test void metadataReviewKeepsTheOldLiveVersionAndFreezesSubmittedContent(){
        UUID account=ledger.onboardAccount(maker,form(family,"111004","Old name"),false);var current=ledger.accountCurrent(maker,account);ledger.configureAccountApproval(maker,policy(true,false,true,true));
        UUID id=ledger.proposeAccountVersion(maker,account,current.id(),"UPDATE",UUID.randomUUID(),change(current.proposed(),"New name"),true);
        assertThat(books.account(institution,account).orElseThrow().name()).isEqualTo("Old name");
        assertThatThrownBy(()->jdbc.update("update gl_account_version set snapshot=jsonb_set(snapshot,'{name}','\"Tampered\"') where id=?",id)).hasStackTraceContaining("Submitted account versions are frozen");
        var reviewer=operator("B2",EnumSet.allOf(UserClaim.class));ledger.accountVersionAction(reviewer,id,version(id).revision(),"APPROVE","Verified");
        assertThat(books.account(institution,account).orElseThrow().name()).isEqualTo("New name");assertThat(version(current.id()).proposed().name()).isEqualTo("Old name");
        assertThatThrownBy(()->jdbc.update("update gl_account set name='Bypass' where id=?",account)).hasStackTraceContaining("approved matching version");
    }
    @Test void singleNameEditsPreserveStoredTranslationsAcrossDraftsAndRevisions(){
        var setup=form(family,"111051","Office cash");setup.setNameSw("Fedha za ofisi");UUID account=ledger.onboardAccount(maker,setup,false);var current=ledger.accountCurrent(maker,account);
        var edit=new EditForm();edit.setName("Branch cash");edit.setReason("Correct account name");
        UUID id=ledger.proposeAccountVersion(maker,account,current.id(),"UPDATE",UUID.randomUUID(),edit,false);
        assertThat(version(id).proposed().nameSw()).isEqualTo("Fedha za ofisi");
        edit.setRevision(version(id).revision());edit.setNameSw("Uneditable name");ledger.saveAccountVersion(maker,id,edit,true);
        assertThat(books.account(institution,account).orElseThrow().nameSw()).isEqualTo("Fedha za ofisi");
        current=ledger.accountCurrent(maker,account);edit.setName("Counter cash");
        UUID withdrawn=ledger.proposeAccountVersion(maker,account,current.id(),"UPDATE",UUID.randomUUID(),edit,false);
        ledger.accountVersionAction(maker,withdrawn,version(withdrawn).revision(),"WITHDRAW","Revise wording");
        edit.setNameSw("");edit.setName("Counter cash revised");UUID revised=ledger.reviseAccountVersion(maker,withdrawn,UUID.randomUUID(),edit,true);
        assertThat(version(revised).proposed().nameSw()).isEqualTo("Fedha za ofisi");
        assertThat(books.account(institution,account).orElseThrow().name()).isEqualTo("Counter cash revised");
        assertThat(books.account(institution,account).orElseThrow().nameSw()).isEqualTo("Fedha za ofisi");
    }
    @Test void rejectionAndWithdrawalPreserveEvidenceAndPermitANewNumberedVersion(){
        ledger.configureAccountApproval(maker,policy(false,true,true,true));UUID id=ledger.onboardVersion(maker,form(family,"111005","Rejected cash"),false,true);var reviewer=operator("B2",EnumSet.allOf(UserClaim.class));
        ledger.accountVersionAction(reviewer,id,version(id).revision(),"REJECT","Wrong wording");var old=version(id);
        UUID next=ledger.reviseAccountVersion(maker,id,UUID.randomUUID(),change(old.proposed(),"Correct wording"),true);
        assertThat(version(next).accountId()).isEqualTo(old.accountId());assertThat(version(next).number()).isEqualTo(2);assertThat(version(id).state()).isEqualTo("REJECTED");
        ledger.accountVersionAction(maker,next,version(next).revision(),"WITHDRAW","More changes needed");assertThat(version(next).state()).isEqualTo("WITHDRAWN");
        assertThatThrownBy(()->jdbc.update("delete from gl_account_version where id=?",id)).hasStackTraceContaining("immutable");
        assertThatThrownBy(()->jdbc.update("delete from gl_account_version_event where version_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void policyChangesInvalidatePendingApprovalWithoutApplyingIt(){
        ledger.configureAccountApproval(maker,policy(false,true,true,true));UUID id=ledger.onboardVersion(maker,form(family,"111006","Pending"),false,true);
        ledger.configureAccountApproval(maker,policy(true,true,true,true));var reviewer=operator("B2",EnumSet.allOf(UserClaim.class));
        assertThatThrownBy(()->ledger.accountVersionAction(reviewer,id,version(id).revision(),"APPROVE","Verified")).hasMessage("accounting.error.versionPolicyChanged");
        assertThat(books.account(institution,version(id).accountId())).isEmpty();assertThat(version(id).policyVersion()).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("update gl_account_approval_policy set create_automatic=true where sacco_id=? and version=1",institution)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void lifecycleApprovalsRecheckChildrenAndParentAvailability(){
        UUID account=ledger.onboardAccount(maker,form(family,"111007","Cash"),false);var current=ledger.accountCurrent(maker,account);ledger.configureAccountApproval(maker,policy(true,true,false,false));
        UUID id=ledger.proposeAccountVersion(maker,account,current.id(),"DEACTIVATE",UUID.randomUUID(),change(current.proposed(),current.proposed().name()),true);assertThat(books.account(institution,account).orElseThrow().active()).isTrue();
        var reviewer=operator("B2",EnumSet.allOf(UserClaim.class));ledger.accountVersionAction(reviewer,id,version(id).revision(),"APPROVE","Close account");assertThat(books.account(institution,account).orElseThrow().active()).isFalse();
        ledger.configureAccountApproval(maker,policy(true,true,true,true));ledger.reactivateAccount(maker,account);assertThat(books.account(institution,account).orElseThrow().active()).isTrue();
        assertThatThrownBy(()->ledger.deactivateAccount(maker,family)).hasMessage("accounting.error.activeChildren");
        UUID root=books.accountCode(institution,"100000").orElseThrow().id();assertThatThrownBy(()->ledger.deactivateAccount(maker,root)).hasMessage("accounting.error.mainGroupProtected");
    }
    @Test void staleBasesDraftRevisionsAndParallelOpenVersionsFail(){
        UUID account=ledger.onboardAccount(maker,form(family,"111008","Cash"),false);var current=ledger.accountCurrent(maker,account);var edit=change(current.proposed(),"New cash");
        UUID id=ledger.proposeAccountVersion(maker,account,current.id(),"UPDATE",UUID.randomUUID(),edit,false);
        assertThatThrownBy(()->ledger.saveAccountVersion(maker,id,edit,true)).hasMessage("accounting.error.versionStale");
        assertThatThrownBy(()->ledger.proposeAccountVersion(maker,account,current.id(),"UPDATE",UUID.randomUUID(),edit,true)).hasMessage("accounting.error.versionOpen");
        edit.setRevision(version(id).revision());ledger.saveAccountVersion(maker,id,edit,true);
        assertThatThrownBy(()->ledger.proposeAccountVersion(maker,account,current.id(),"UPDATE",UUID.randomUUID(),edit,true)).hasMessage("accounting.error.versionStale");
    }
    @Test void auditFailureRollsBackApprovalAccountAndVersionEvents(){
        var f=form(family,"111009","Cash");doThrow(new IllegalStateException("Audit unavailable")).when(audit).log(anyString(),any(),anyString(),any(),any(),any());
        assertThatThrownBy(()->ledger.onboardVersion(maker,f,false,true)).hasMessage("Audit unavailable");
        assertThat(books.accountCode(institution,"111009")).isEmpty();assertThat(books.versionRequest(institution,f.getRequestKey())).isEmpty();
    }
    @Test void failedApprovalAuditRestoresPendingStateAndKeepsTheAccountUnavailable(){
        ledger.configureAccountApproval(maker,policy(false,true,true,true));UUID id=ledger.onboardVersion(maker,form(family,"111013","Pending cash"),false,true);var pending=version(id);var reviewer=operator("B2",EnumSet.allOf(UserClaim.class));
        doThrow(new IllegalStateException("Approval audit unavailable")).when(audit).log(eq("ACCOUNT"),eq(pending.accountId()),eq("ACCOUNT_VERSION_APPROVE"),any(),any(),any());
        assertThatThrownBy(()->ledger.accountVersionAction(reviewer,id,pending.revision(),"APPROVE","Verified")).hasMessage("Approval audit unavailable");
        assertThat(version(id).state()).isEqualTo("PENDING");assertThat(books.account(institution,pending.accountId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from gl_account_version_event where version_id=? and action='APPROVE'",Integer.class,id)).isZero();
    }
    @Test void explicitInstitutionReviewDoesNotCrossTenantBoundariesAndIsNotSeeded(){
        UUID id=ledger.onboardVersion(maker,form(family,"111014","Private draft"),false,false);String original=institution;institution="COA-OTHER-"+UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Other synthetic institution',true,now(),now())",institution);
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));when(institutions.findStation(eq(institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder().saccoId(i.getArgument(0)).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        var other=operator("B1",EnumSet.allOf(UserClaim.class));institution=original;
        assertThatThrownBy(()->ledger.accountVersionDetails(other,id,0,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(UserClaim.defaultClaims(List.of(Position.ACCOUNTANT),false)).doesNotContain(UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_VIEW,UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_APPROVE,UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_REJECT,UserClaim.ACCOUNTING_ACCOUNT_RULES_VIEW,UserClaim.ACCOUNTING_ACCOUNT_RULES_CONFIGURE);
    }
    @Test void currentClaimsInstitutionAndBranchOwnershipAreEnforced(){
        UUID id=ledger.onboardVersion(maker,form(family,"111010","Draft"),false,false);
        var reader=operator("B2",Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW,UserClaim.ACCOUNTING_ACCOUNT_APPROVAL_APPROVE));
        assertThatThrownBy(()->ledger.accountVersionDetails(reader,id,0,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(ledger.accountVersionRegister(reader,"","","created","desc",0,30).rows()).isEmpty();
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.ACCOUNTING_ACCOUNTS_VIEW));
        assertThatThrownBy(()->ledger.configureAccountApproval(maker,policy(true,true,true,true))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->ledger.accountVersionDetails(maker,UUID.randomUUID(),0,0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void concurrentCreationRetriesUseOneVersionAndOneLiveAccount()throws Exception{
        var f=form(family,"111011","Concurrent cash");var pool=Executors.newFixedThreadPool(2);
        try{var first=pool.submit(()->ledger.onboardVersion(maker,f,false,true));var second=pool.submit(()->ledger.onboardVersion(maker,f,false,true));assertThat(first.get(30,TimeUnit.SECONDS)).isEqualTo(second.get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
        assertThat(jdbc.queryForObject("select count(*) from gl_account where sacco_id=? and code='111011'",Integer.class,institution)).isEqualTo(1);
    }
    @Test void concurrentReviewDecisionsApplyOnlyOnce()throws Exception{
        ledger.configureAccountApproval(maker,policy(false,true,true,true));UUID id=ledger.onboardVersion(maker,form(family,"111012","Concurrent review"),false,true);int revision=version(id).revision();
        var one=operator("B2",EnumSet.allOf(UserClaim.class));var two=operator("B3",EnumSet.allOf(UserClaim.class));var pool=Executors.newFixedThreadPool(2);
        try{var tasks=List.of(pool.submit(()->{try{ledger.accountVersionAction(one,id,revision,"APPROVE","Checked");return true;}catch(IllegalArgumentException e){return false;}}),pool.submit(()->{try{ledger.accountVersionAction(two,id,revision,"APPROVE","Checked");return true;}catch(IllegalArgumentException e){return false;}}));assertThat(List.of(tasks.get(0).get(30,TimeUnit.SECONDS),tasks.get(1).get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);}finally{pool.shutdownNow();}
    }
    @Test void queuePaginationAndSortAreBoundedAndBadInputRejected(){
        for(int n=1;n<=31;n++)ledger.onboardVersion(maker,form(family,String.format("111%03d",n),"Cash "+n),false,false);
        assertThat(ledger.accountVersionRegister(maker,"DRAFT","","code","asc",0,30).rows()).hasSize(30);assertThat(ledger.accountVersionRegister(maker,"DRAFT","","code","asc",0,30).hasNext()).isTrue();
        assertThat(ledger.accountVersionRegister(maker,"DRAFT","","code","asc",1,30).rows()).hasSize(1);
        assertThatThrownBy(()->ledger.accountVersionRegister(maker,"","","code;drop table gl_account","asc",0,30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ledger.accountVersionRegister(maker,"","","code","asc",0,10000)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void newJournalLinesRetainTheApprovedAccountVersionAcrossRenames(){
        UUID account=ledger.onboardAccount(maker,form(family,"111050","Original cash name"),false);
        UUID capital=ledger.createAccount(maker,new AccountCommand("CAPITAL","Capital","EQUITY","CREDIT","POSTING","CAPITAL",null));
        var clock=mock(ApplicationClock.class);when(clock.now()).thenReturn(now);when(clock.today()).thenReturn(now.toLocalDate());
        var repo=new com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository(jdbc,tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build());
        var raw=new VoucherService(new com.sacco.mvp.accounting.repository.VoucherRepository(jdbc),ledger,clock,audit,tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(),new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(repo,ledger));
        var proxy=new ProxyFactory(raw);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));var vouchers=(VoucherService)proxy.getProxy();
        var f=new com.sacco.mvp.accounting.dto.VoucherDtos.Form();f.setEffectiveDate(now.toLocalDate());f.setParty("Synthetic counterparty");f.setReference("VERSION-"+UUID.randomUUID());f.setDescription("Synthetic journal");f.setEvidence("Verified synthetic test evidence");
        var row=f.getRows().getFirst();row.setDebitAccountId(account);row.setCreditAccountId(capital);row.setAmount(new java.math.BigDecimal("10.00"));row.setDescription("Synthetic entry");
        var posted=vouchers.post(maker,com.sacco.mvp.accounting.dto.VoucherDtos.Type.JOURNAL,f);var old=ledger.accountCurrent(maker,account);
        ledger.proposeAccountVersion(maker,account,old.id(),"UPDATE",UUID.randomUUID(),change(old.proposed(),"New cash name"),true);
        assertThat(jdbc.queryForObject("select v.snapshot->>'name' from gl_journal_line l join gl_account_version v on v.id=l.account_version_id where l.journal_id=? and l.account_id=?",String.class,posted.journalId(),account)).isEqualTo("Original cash name");
        assertThat(vouchers.view(maker,com.sacco.mvp.accounting.dto.VoucherDtos.Type.JOURNAL,posted.id()).transactions().getFirst().debit().name()).isEqualTo("Original cash name");
        assertThat(ledger.accountCurrent(maker,account).proposed().name()).isEqualTo("New cash name");
    }
}
