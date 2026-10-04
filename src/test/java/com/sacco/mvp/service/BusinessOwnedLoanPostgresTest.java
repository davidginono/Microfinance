package com.sacco.mvp.service;
import com.sacco.mvp.accounting.business.service.*;
import com.sacco.mvp.repository.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.*;
import com.sacco.mvp.accounting.business.repository.BusinessAccountingRepository;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.policy.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService.*;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService;
import com.sacco.mvp.reporting.execution.repository.AccountingReleaseRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import com.zaxxer.hikari.HikariDataSource;
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

@EnabledIfEnvironmentVariable(named="MICROFINANCE_TEST_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_c_test_owned_sources_20261003(?:_v2)?")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessOwnedLoanPostgresTest {
    private static final LocalDate OPENING=LocalDate.of(2026,10,1), DAY=OPENING.plusDays(1);
    private JdbcTemplate jdbc;
    private HikariDataSource ds;
    private TransactionTemplate tx;
    private BusinessAccountingService service;
    private BusinessAccountingRepository repository;
    private GeneralLedgerService books;
    private AccountingPolicyService policies;
    private AuditService audit;
    private MemberDirectoryService members;
    private UserClaimService claims;
    private SaccoRegistryService institutions;
    private ApplicationClock clock;
    private String institution;
    private AppUserPrincipal maker,checker,third,fourth;
    private Map<String,UUID> mappings;
    private PolicySnapshot policy;
    private AccountingReleaseGateService releaseGate;
    private PlatformTransactionManager transactionManager;
    private AnnotationConfigApplicationContext context;
    private LoanApplicationRepository loans;
    private LoanRepaymentLedgerService repayments;
    private GeneralLedgerRepository bookRepository;


    @BeforeAll void start(){
        context=new AnnotationConfigApplicationContext(OwnedConfig.class);
        jdbc=new JdbcTemplate(context.getBean(javax.sql.DataSource.class));
        transactionManager=context.getBean(PlatformTransactionManager.class);tx=new TransactionTemplate(transactionManager);
        policies=mock(AccountingPolicyService.class);audit=context.getBean(AuditService.class);members=mock(MemberDirectoryService.class);claims=mock(UserClaimService.class);institutions=mock(SaccoRegistryService.class);clock=context.getBean(ApplicationClock.class);
        var mapper=context.getBean(tools.jackson.databind.ObjectMapper.class);bookRepository=new GeneralLedgerRepository(jdbc);
        releaseGate=mock(AccountingReleaseGateService.class);
        books=proxy(new GeneralLedgerService(bookRepository,policies,new AccessControlService(),audit,clock,claims,members,institutions,releaseGate),transactionManager);
        repository=context.getBean(BusinessAccountingRepository.class);repayments=context.getBean(LoanRepaymentLedgerService.class);loans=context.getBean(LoanApplicationRepository.class);
        releaseGate=mock(AccountingReleaseGateService.class);
        var bridge=proxy(new BusinessOperationalBridge(repository,bookRepository,context.getBean(LoanJournalEntryRepository.class)),transactionManager);
        service=proxy(new BusinessAccountingService(repository,books,policies,repayments,context.getBean(ManagerService.class),new AccessControlService(),members,claims,clock,audit,institutions,releaseGate,bridge),transactionManager);
    }
    @AfterAll void stop(){if(context!=null)context.close();}
    @BeforeEach void fixture(){
        reset(policies,audit,members,claims,institutions,releaseGate);institution="C-"+UUID.randomUUID();
        when(clock.today()).thenReturn(DAY.plusMonths(1));when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-02T12:00:00+03:00"));
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",institution,"Synthetic business accounting institution");
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
        when(institutions.findStation(institution,"B1")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B1").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        maker=operator("B1");checker=operator("B1");third=operator("B1");fourth=operator("B1");
        mappings=new LinkedHashMap<>();
        for(String key:List.of("LOAN_PRINCIPAL","INTEREST_INCOME","CASH","BANK","MOBILE_MONEY","OWNER_CAPITAL","OWNER_DISTRIBUTIONS","SUPPLIER_PAYABLE","OPERATING_EXPENSE","FUNDING_PRINCIPAL","FUNDING_INTEREST_EXPENSE","FIXED_ASSET","DEPRECIATION_EXPENSE","ACCUMULATED_DEPRECIATION","DISPOSAL_GAIN","DISPOSAL_LOSS","PREPAYMENT_ASSET","ACCRUED_LIABILITY","TAX_EXPENSE","TAX_PAYABLE","UNAPPLIED_FUNDS","INTERNAL_DUE_FROM","INTERNAL_DUE_TO")){
            String type=key.contains("EXPENSE")||key.contains("LOSS")?"EXPENSE":Set.of("SUPPLIER_PAYABLE","FUNDING_PRINCIPAL","ACCRUED_LIABILITY","TAX_PAYABLE","UNAPPLIED_FUNDS","INTERNAL_DUE_TO").contains(key)?"LIABILITY":key.startsWith("OWNER")?"EQUITY":key.contains("GAIN")||key.equals("INTEREST_INCOME")?"INCOME":"ASSET";
            mappings.put(key,books.createAccount(maker,new AccountCommand(key,"Synthetic "+key,type,Set.of("LIABILITY","EQUITY","INCOME").contains(type)?"CREDIT":"DEBIT",key.equals("LOAN_PRINCIPAL")?"CONTROL":"POSTING",key.equals("LOAN_PRINCIPAL")?"LOAN_PRINCIPAL":"OTHER",null)));
        }
        UUID id=UUID.randomUUID();var decisions=new EnumMap<PolicyDecision,String>(PolicyDecision.class);for(var v:PolicyDecision.values())decisions.put(v,"Synthetic independently approved evidence");decisions.put(PolicyDecision.INTEREST_RECOGNITION,"CASH_DUE_INTEREST_V1");
        var matrix=new EnumMap<PostingEvent,PostingRule>(PostingEvent.class);for(var v:PostingEvent.values())matrix.put(v,new PostingRule(PostingPermission.ALLOWED,"Synthetic verified treatment"));
        var mapper=JsonMapper.builder().findAndAddModules().build();
        jdbc.update("insert into accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) values(?,?,1,?,?,'LOCAL_GL',?,?,?,'Synthetic policy evidence',?,?,now())",id,institution,OPENING,OPENING,mapper.writeValueAsString(decisions),mapper.writeValueAsString(matrix),mapper.writeValueAsString(mappings),maker.getMemberId(),UUID.randomUUID());
        jdbc.update("insert into accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) values(?,?,1,?,?,'APPROVED','Synthetic checker evidence','Synthetic only',now())",id,institution,OPENING,checker.getMemberId());
        policy=new PolicySnapshot(id,institution,1,OPENING,OPENING,AuthoritativeLedger.LOCAL_GL,decisions,matrix,mappings,"Synthetic policy evidence",maker.getMemberId(),Decision.APPROVED,checker.getMemberId(),"Synthetic checker evidence","Synthetic only",clock.now());
        when(policies.requireApprovedLocalPolicy(eq(institution),any())).thenReturn(policy);books.createPeriod(maker,OPENING,OPENING.plusMonths(2));
        var opening=books.importOpening(maker,new JournalCommand(UUID.randomUUID(),"SYNTHETIC-OPENING",OPENING,"Synthetic reconciled cash/capital",null,List.of(new Line(mappings.get("CASH"),new BigDecimal("10000.00"),BigDecimal.ZERO),new Line(mappings.get("OWNER_CAPITAL"),BigDecimal.ZERO,new BigDecimal("10000.00")))));
        books.approve(checker,opening.id(),"Synthetic independent opening reconciliation");books.post(checker,opening.id(),true);
    }

    @Test void actualDisbursementRepaymentAndCorrectionShareOneOwnedGlAndVoucher() {
        var loan=readyLoan();var origin=post(maker,checker,loan,Kind.LOAN_DISBURSEMENT,"200.00",DAY,null);
        assertThat(loans.findById(loan.getId()).orElseThrow().getStatus()).isEqualTo(LoanStatus.DISBURSED);
        assertThat(jdbc.queryForObject("select count(*) from gl_operational_bridge where journal_id=?",Long.class,origin.journalId())).isEqualTo(1);
        var payment=post(maker,checker,loan,Kind.LOAN_REPAYMENT,"5.01",DAY.plusMonths(1),null);
        assertThat(payment.interest()).isEqualByComparingTo("2.00");assertThat(payment.principal()).isEqualByComparingTo("3.01");
        assertThat(loanBalance(loan.getId())).isEqualByComparingTo("196.99");
        var correction=post(third,fourth,loan,Kind.LOAN_REPAYMENT_REVERSAL,"5.01",DAY.plusMonths(1),payment.id());
        assertThat(loanBalance(loan.getId())).isEqualByComparingTo("200.00");
        assertThat(jdbc.queryForObject("select count(*) from gl_operational_bridge b join accounting_business_document d on d.journal_id=b.journal_id where d.loan_id=?",Long.class,loan.getId())).isEqualTo(3);
        assertThat(service.approveAndPost(fourth,correction.id(),"Synthetic owning transaction review",true).id()).isEqualTo(correction.id());
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Long.class,loan.getId())).isEqualTo(2);
        assertThat(controlBalance()).isEqualByComparingTo("200.00");
        assertThat(bookRepository.unbridged(institution,"B1")).isZero();
    }
    @Test void failureAfterActualVoucherAttachmentRollsBackLoanGlAndReceiptTogether() {
        var loan=readyLoan();post(maker,checker,loan,Kind.LOAN_DISBURSEMENT,"200.00",DAY,null);
        var draft=source(maker,loan,Kind.LOAN_REPAYMENT,"5.01",DAY.plusMonths(1),null);service.submit(maker,draft.id());
        doThrow(new IllegalStateException("Synthetic final source audit failure")).when(audit).logEvent(anyString(),eq(draft.id()),eq("SOURCE_POSTED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
        assertThatThrownBy(()->service.approveAndPost(checker,draft.id(),"Synthetic owning transaction review",true)).isInstanceOf(IllegalStateException.class);
        assertThat(loanBalance(loan.getId())).isEqualByComparingTo("200.00");assertThat(controlBalance()).isEqualByComparingTo("200.00");
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Long.class,loan.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from gl_operational_bridge where journal_id=?",Long.class,draft.journalId())).isZero();
        assertThat(service.view(maker,draft.id()).state()).isEqualTo("SUBMITTED");
        assertThat(books.journal(maker,service.view(maker,draft.id()).journalId()).state()).isEqualTo("DRAFT");
    }
    @Test void unavailableReleaseCannotMutateActualDisbursement() {
        var loan=readyLoan();var draft=source(maker,loan,Kind.LOAN_DISBURSEMENT,"200.00",DAY,null);service.submit(maker,draft.id());
        doThrow(new IllegalArgumentException("accounting.release.error.restricted")).when(releaseGate).requireLiveRelease(any(),any(),anyInt());
        assertThatThrownBy(()->service.approveAndPost(checker,draft.id(),"Synthetic owning transaction review",true)).hasMessage("accounting.release.error.restricted");
        assertThat(loans.findById(loan.getId()).orElseThrow().getStatus()).isEqualTo(LoanStatus.READY_FOR_DISBURSEMENT);
        assertThat(jdbc.queryForObject("select count(*) from loan_ledgers where loan_application_id=?",Long.class,loan.getId())).isZero();
    }
    private LoanApplication readyLoan(){var now=clock.now();var product=LoanProductSetting.builder().id(UUID.randomUUID()).saccoId(institution).loanType(LoanType.DEVELOPMENT_LOAN).productCode("OWNED-LOAN").productName("Synthetic reviewed ordinary lending").guarantorsRequired(0).interestRate(new BigDecimal("0.1200")).interestMethod(InterestMethod.REDUCING_BALANCE).repaymentFrequency(RepaymentFrequency.MONTHLY).disbursementProofRequired(false).applicantAttachmentRequired(false).affordabilityCheckRequired(true).maxRepaymentToDisposableIncomeRatio(new BigDecimal("0.4000")).collateralRequired(false).minCollateralCoverageRatio(new BigDecimal("0.0000")).savingsLimitCheckRequired(false).formSchema("{}").active(true).productStatus(LoanProductStatus.ACTIVE).createdAt(now).updatedAt(now).build();tx.executeWithoutResult(t->context.getBean(LoanProductSettingRepository.class).saveAndFlush(product));var loan=LoanApplication.builder().id(UUID.randomUUID()).applicationNumber(Math.abs(UUID.randomUUID().getLeastSignificantBits())).saccoId(institution).stationId("B1").applicantMemberId(maker.getMemberId()).loanType(LoanType.DEVELOPMENT_LOAN).loanProductSettingId(product.getId()).amount(new BigDecimal("200.00")).tenorMonths(1).status(LoanStatus.READY_FOR_DISBURSEMENT).formData("{}").policySnapshot("{}").financialSnapshot(reviewedQuote()).requiredGuarantors(0).createdAt(now).updatedAt(now).build();tx.executeWithoutResult(s->loans.saveAndFlush(loan));return loan;}
    private String reviewedQuote(){var principal=new BigDecimal("200.00");var rate=new BigDecimal("0.1200");int payments=LoanAmortizationCalculator.numberOfPayments(1,RepaymentFrequency.MONTHLY);var calculation=LoanAmortizationCalculator.calculate(principal,payments,rate,InterestMethod.REDUCING_BALANCE,RepaymentFrequency.MONTHLY,null,LoanAmortizationCalculator.flatInterest(principal,rate,1));return context.getBean(tools.jackson.databind.ObjectMapper.class).writeValueAsString(Map.of("calculationVersion",LoanAmortizationCalculator.VERSION,"repaymentFrequency",RepaymentFrequency.MONTHLY.name(),"tenorMonths",1,"numberOfPayments",payments,"maximumInstallmentAmount",calculation.maximumInstallment(),"periodicRepaymentAmount",calculation.installment(),"interestAmount",calculation.totalInterest(),"principalAmount",principal,"interestRate",rate,"interestMethod",InterestMethod.REDUCING_BALANCE.name()));}
    private Document source(AppUserPrincipal actor,LoanApplication loan,Kind kind,String amount,LocalDate date,UUID related){return service.create(actor,new Command(UUID.randomUUID(),kind,date,new BigDecimal(amount),loan.getId(),related,null,"Synthetic actual owning source","Synthetic verified payment evidence","OWN-"+UUID.randomUUID(),"CASH",null,kind==Kind.LOAN_DISBURSEMENT?"123456789":null,kind==Kind.LOAN_DISBURSEMENT?DAY.plusMonths(1):null,kind==Kind.LOAN_DISBURSEMENT?RepaymentFrequency.MONTHLY:null,kind==Kind.LOAN_DISBURSEMENT?new BigDecimal("202.00"):null));}
    private Document post(AppUserPrincipal maker,AppUserPrincipal checker,LoanApplication loan,Kind kind,String amount,LocalDate date,UUID related){var d=source(maker,loan,kind,amount,date,related);service.submit(maker,d.id());return service.approveAndPost(checker,d.id(),"Synthetic owning transaction review",true);}
    private BigDecimal loanBalance(UUID loan){return jdbc.queryForObject("select principal-principal_paid from loan_ledgers where loan_application_id=?",BigDecimal.class,loan);}
    private BigDecimal controlBalance(){return jdbc.queryForObject("select coalesce(sum(l.debit-l.credit),0) from gl_journal_line l join gl_journal j on j.id=l.journal_id where j.sacco_id=? and j.state='POSTED' and l.account_id=?",BigDecimal.class,institution,mappings.get("LOAN_PRINCIPAL"));}
    private AppUserPrincipal operator(String branch){UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,?,?,?,'ACTIVE','MANAGER',now(),false,'synthetic')",id,institution,branch,id.toString(),"Synthetic staff");var m=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).fullName("Synthetic staff").status(MemberStatus.ACTIVE).position(Position.MANAGER).build();when(members.find(id)).thenReturn(Optional.of(m));return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),true);}
    @SuppressWarnings("unchecked") private <T>T proxy(T target,PlatformTransactionManager manager){var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();}
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackages="com.sacco.mvp.repository")
    static class OwnedConfig extends LoanRepaymentLedgerPostgresTest.TestConfig {
        @Bean BusinessAccountingRepository sources(javax.sql.DataSource ds,tools.jackson.databind.ObjectMapper mapper){return new BusinessAccountingRepository(new JdbcTemplate(ds),mapper);}
        @Bean BusinessAccountingGuard guard(){return new BusinessAccountingGuard(sources(dataSource(),mapper()));}
        @Override @Bean LoanRepaymentLedgerService ledger(LoanLedgerRepository ledgers,LoanLedgerInstallmentRepository installments,LoanRepaymentTransactionRepository payments,LoanRepaymentAllocationRepository allocations,LoanJournalEntryRepository journal,LoanApplicationRepository loans,tools.jackson.databind.ObjectMapper mapper,ApplicationClock clock,AccessControlService access,AuditService audit){return new LoanRepaymentLedgerService(ledgers,installments,payments,allocations,journal,loans,mapper,clock,access,audit,guard());}
        @Bean ManagerService manager(LoanApplicationRepository loans,ManagerReviewRepository reviews,BoardReviewRepository board,GuarantorRequestRepository guarantees,MemberRepository members,SaccoSettingsRepository settings,LoanProductSettingRepository products,LoanRepaymentLedgerService ledger){var roles=mock(RoleDirectoryService.class);when(roles.hasActiveClaimInSacco(any(),anyString(),any())).thenReturn(true);return new ManagerService(loans,reviews,board,guarantees,members,settings,products,mock(OutboxService.class),roles,mock(LoanAttachmentService.class),mock(WorkflowRoutingService.class),audit(),mapper(),new RepaymentScheduleService(mapper(),products),ledger,guard());}
    }
}
