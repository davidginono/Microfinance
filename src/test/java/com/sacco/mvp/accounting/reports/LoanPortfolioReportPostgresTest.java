package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.sacco.mvp.accounting.reports.LoanPortfolioReportService.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_E_DATABASE_URL",matches="(?:(?:jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_e_test_portfolio_20261003_v[12]|jdbc:postgresql://127\\.0\\.0\\.1:55439/microfinance_accounting_h_release_combined_test_20261004)|jdbc:postgresql://127\\.0\\.0\\.1:55439/microfinance_accounting_h_release_integrity_test_20261004)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoanPortfolioReportPostgresTest {
    static final LocalDate DAY=LocalDate.of(2026,10,3);
    JdbcTemplate jdbc;TransactionTemplate tx;LoanPortfolioReportRepository repository;LoanPortfolioReportService service;
    MemberDirectoryService members;UserClaimService claims;SaccoRegistryService institutions;ApplicationClock clock;
    String institution;AppUserPrincipal actor;Scope branch,wide;Parameters p;int number;
    @BeforeAll void start(){var ds=new SingleConnectionDataSource(System.getenv("MICROFINANCE_ACCOUNTING_E_DATABASE_URL"),"microfinance_test","",true);var connectionProperties=new Properties();connectionProperties.setProperty("sslmode","disable");ds.setConnectionProperties(connectionProperties);Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        members=mock(MemberDirectoryService.class);claims=mock(UserClaimService.class);institutions=mock(SaccoRegistryService.class);clock=mock(ApplicationClock.class);
        repository=new LoanPortfolioReportRepository(new NamedParameterJdbcTemplate(jdbc));var proxy=new ProxyFactory(new LoanPortfolioReportService(members,claims,institutions,clock,repository,new ReportExportLimiter(1)));proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));service=(LoanPortfolioReportService)proxy.getProxy();}
    @AfterAll void close(){if(jdbc!=null)((SingleConnectionDataSource)jdbc.getDataSource()).destroy();}
    @BeforeEach void fixture(){reset(members,claims,institutions,clock);institution="PORTFOLIO-"+UUID.randomUUID();number=jdbc.queryForObject("select coalesce(max(application_number),999)+1 from loan_applications",Integer.class);
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",institution,"Synthetic portfolio tests");
        for(String b:List.of("B1","B2")){jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at,access_status) values(?,?,?,true,now(),now(),'ACTIVE')",UUID.randomUUID(),institution,b);when(institutions.findStation(institution,b)).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId(b).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));}
        UUID staff=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,'B1',?,'Synthetic staff','ACTIVE','MANAGER',now(),false,'test-only')",staff,institution,staff.toString());var m=Member.builder().id(staff).saccoId(institution).stationId("B1").memberNo(staff.toString()).position(Position.MANAGER).status(MemberStatus.ACTIVE).memberAccount(false).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build();actor=new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class));when(members.find(staff)).thenReturn(Optional.of(m));when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));when(clock.today()).thenReturn(DAY);when(clock.now()).thenAnswer(i->OffsetDateTime.now());branch=new Scope(institution,"B1",false);wide=new Scope(institution,"B1",true);p=new Parameters(DAY.minusDays(100),DAY,OffsetDateTime.now().plusSeconds(1),0);}
    record Loan(UUID id,UUID installment,BigDecimal principal,BigDecimal interest,LocalDate due) { }
    Loan loan(String b,String principal,String interest,int dpd,boolean origin){return loan(b,principal,interest,dpd,origin,new BigDecimal(principal));}
    Loan loan(String b,String principal,String interest,int dpd,boolean origin,BigDecimal duePrincipal){UUID id=UUID.randomUUID(),installment=UUID.randomUUID();BigDecimal amount=new BigDecimal(principal),i=new BigDecimal(interest);LocalDate disbursed=DAY.minusDays(Math.max(100,dpd+1)),due=DAY.minusDays(dpd);int n=number++;
        var schedule=new ArrayList<Map<String,Object>>();schedule.add(Map.of("dueDate",due.toString(),"principalComponent",duePrincipal,"interestComponent",i,"amount",duePrincipal.add(i)));if(duePrincipal.compareTo(amount)<0)schedule.add(Map.of("dueDate",DAY.plusDays(30).toString(),"principalComponent",amount.subtract(duePrincipal),"interestComponent",BigDecimal.ZERO,"amount",amount.subtract(duePrincipal)));var json=JsonMapper.builder().build().writeValueAsString(Map.of("schedule",schedule));
        tx.executeWithoutResult(s->{jdbc.update("insert into loan_applications(id,amount,applicant_member_id,created_at,updated_at,disbursement_date,form_data,loan_type,policy_snapshot,repayment_schedule_json,required_guarantors,sacco_id,station_id,status,tenor_months,version,loan_id,application_number) values(?,?,?,now(),now(),?,'{}','DEVELOPMENT_LOAN','{}',?::jsonb,0,?,?,'DISBURSED',1,0,?,?)",id,amount,actor.getMemberId(),disbursed,json,institution,b,Integer.toString(n),n);jdbc.update("insert into loan_ledgers(loan_application_id,sacco_id,station_id,loan_id,applicant_member_id,disbursement_date,principal,created_at) values(?,?,?,?,?,?,?,now())",id,institution,b,Integer.toString(n),actor.getMemberId(),disbursed,amount);jdbc.update("insert into loan_ledger_installments(id,loan_application_id,installment_number,due_date,principal,interest) values(?,?,1,?,?,?)",installment,id,due,duePrincipal,i);if(duePrincipal.compareTo(amount)<0)jdbc.update("insert into loan_ledger_installments(id,loan_application_id,installment_number,due_date,principal,interest) values(?,?,2,?,?,0)",UUID.randomUUID(),id,DAY.plusDays(30),amount.subtract(duePrincipal));if(origin){UUID voucher=UUID.randomUUID();journal(id,null,voucher,"LOAN_PRINCIPAL",amount,BigDecimal.ZERO,disbursed);journal(id,null,voucher,"DISBURSEMENT_CLEARING",BigDecimal.ZERO,amount,disbursed);}});return new Loan(id,installment,amount,i,due);}
    void journal(UUID loan,UUID transaction,UUID voucher,String code,BigDecimal debit,BigDecimal credit,LocalDate date){jdbc.update("insert into loan_journal_entries(id,loan_application_id,transaction_id,voucher_id,account_code,debit,credit,effective_date,posted_at) values(?,?,?,?,?,?,?,?,now())",UUID.randomUUID(),loan,transaction,voucher,code,debit,credit,date);}
    UUID payment(Loan loan,String b,String principal,String interest,LocalDate date,UUID reverses,boolean allocations){return payment(loan,b,principal,interest,date,reverses,allocations,"0");}
    UUID payment(Loan loan,String b,String principal,String interest,LocalDate date,UUID reverses,boolean allocations,String excluded){UUID id=UUID.randomUUID();BigDecimal a=new BigDecimal(principal),i=new BigDecimal(interest);boolean reverse=reverses!=null;
        tx.executeWithoutResult(s->{jdbc.update("insert into loan_repayment_transactions(id,loan_application_id,sacco_id,station_id,sequence,receipt_reference,request_key,kind,channel,channel_reference,payment_date,amount,principal_amount,interest_amount,actor_member_id,reverses_transaction_id,reason,loan_status_before,posted_at,future_interest_excluded) values(?,?,?,?,?,?,?,?, 'CASH',?,?,?,?,?,?,?,?,'DISBURSED',now(),?)",id,loan.id(),institution,b,reverse?2:1,id.toString(),UUID.randomUUID(),reverse?"REVERSAL":"PAYMENT",id.toString(),date,a.add(i),a,i,actor.getMemberId(),reverses,reverse?"Synthetic reversal":null,new BigDecimal(excluded));if(allocations)jdbc.update("insert into loan_repayment_allocations(id,transaction_id,installment_id,principal_amount,interest_amount,interest_excluded) values(?,?,?,?,?,?)",UUID.randomUUID(),id,loan.installment(),a,i,new BigDecimal(excluded));if(new BigDecimal(excluded).signum()>0){var sign=reverse?BigDecimal.ONE.negate():BigDecimal.ONE;jdbc.update("update loan_ledgers set principal_paid=principal_paid+?,interest_paid=interest_paid+? where loan_application_id=?",a.multiply(sign),i.multiply(sign),loan.id());jdbc.update("update loan_ledger_installments set principal_paid=principal_paid+?,interest_paid=interest_paid+?,interest_excluded=interest_excluded+? where id=?",a.multiply(sign),i.multiply(sign),new BigDecimal(excluded).multiply(sign),loan.installment());}journal(loan.id(),id,id,"CASH_CLEARING",reverse?BigDecimal.ZERO:a.add(i),reverse?a.add(i):BigDecimal.ZERO,date);if(a.signum()>0)journal(loan.id(),id,id,"LOAN_PRINCIPAL",reverse?a:BigDecimal.ZERO,reverse?BigDecimal.ZERO:a,date);if(i.signum()>0)journal(loan.id(),id,id,"INTEREST_COLLECTIONS_CLEARING",reverse?i:BigDecimal.ZERO,reverse?BigDecimal.ZERO:i,date);});return id;}
    Parameters cutoff(){return new Parameters(p.from(),p.through(),OffsetDateTime.now(),0);}
    com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters reportParameters(LocalDate from,LocalDate through,OffsetDateTime cutoff,int page){
        return new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(from,through,cutoff,null,null,null,"",page);
    }
    ManagementReportRepository management(){return new ManagementReportRepository(new NamedParameterJdbcTemplate(jdbc));}
    @Test void productSelectorFiltersEveryLoanReportByProductIdWithinTheBranch(){
        UUID selected=UUID.randomUUID(), other=UUID.randomUUID();
        jdbc.update("""
            insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,
                guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,created_at,updated_at)
            values(?,?,'DEVELOPMENT_LOAN',true,'ACTIVE','01','Business','{}',0,0.24,'FLAT_RATE','MONTHLY',1,12,now(),now()),
                  (?,?,'DEVELOPMENT_LOAN',true,'ACTIVE','02','Business Plus','{}',0,0.24,'FLAT_RATE','MONTHLY',1,12,now(),now())
            """,selected,institution,other,institution);
        var matching=loan("B1","100.00","5.00",0,true);
        var similar=loan("B1","200.00","5.00",0,true);
        var differentBranch=loan("B2","300.00","5.00",0,true);
        jdbc.update("update loan_applications set loan_product_setting_id=? where id in (?,?)",selected,matching.id(),differentBranch.id());
        jdbc.update("update loan_applications set loan_product_setting_id=? where id=?",other,similar.id());
        payment(matching,"B1","10.00","0.00",DAY,null,true);
        payment(similar,"B1","20.00","0.00",DAY,null,true);
        payment(differentBranch,"B2","30.00","0.00",DAY,null,true);
        String loanNumber=jdbc.queryForObject("select loan_id from loan_applications where id=?",String.class,matching.id());
        var filter=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(
            DAY.minusDays(100),DAY,OffsetDateTime.now(),null,null,null,"",0,selected);
        var reports=management();
        for(var kind:com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.values()){
            if(!com.sacco.mvp.accounting.management.dto.ManagementReports.spreadsheetSupported(kind))continue;
            var preview=reports.read(actor,kind,filter,false);
            assertThat(preview.rows()).extracting(row->row.get("loanId"))
                .as("selected product in %s",kind).containsExactly(loanNumber);
            if(kind==com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DISBURSED_LOANS_AUDIT){
                assertThat(preview.totals().get("netDisbursed")).isNull();
                assertThat(preview.totals()).doesNotContainKey("principalInstallment");
                assertThat(preview.rows().getFirst().get("principalInstallment")).isEqualTo(new BigDecimal("100.00"));
            }
            assertThat(reports.read(actor,kind,filter,true).rows()).extracting(row->row.get("loanId"))
                .as("selected product in %s export",kind).containsExactly(loanNumber);
        }
    }
    @Test void futureInterestExclusionsAreNotCashAndReversalRestoresExpectedAmounts(){
        var loan=loan("B1","100.00","5.00",-30,true);
        var payment=payment(loan,"B1","100.00","0.00",DAY,null,true,"5.00");
        var expected=com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.EXPECTED_REPAYMENTS;
        var p=reportParameters(DAY,DAY.plusDays(30),OffsetDateTime.now(),0);
        assertThat(management().read(actor,expected,p,false).count()).isZero();
        var transactions=management().read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.LOAN_REPAYMENT_TRANSACTIONS,p,false);
        assertThat(transactions.totals().get("payment")).isEqualByComparingTo("100.00");
        assertThat(transactions.totals().get("interestExcluded")).isEqualByComparingTo("5.00");
        payment(loan,"B1","100.00","0.00",DAY,payment,true,"5.00");
        assertThat(management().read(actor,expected,reportParameters(DAY,DAY.plusDays(30),OffsetDateTime.now(),0),false).totals().get("expectedTotal")).isEqualByComparingTo("105.00");
    }
    @Test void excludedHistoryCannotClaimZeroExpectedRepayments(){
        loan("B1","100.00","5.00",0,false);
        var result=management().read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.EXPECTED_REPAYMENTS,reportParameters(DAY,DAY,OffsetDateTime.now(),0),false);
        assertThat(result.count()).isZero();assertThat(result.excluded()).isEqualTo(1);
        assertThat(result.totals().get("expectedTotal")).isNull();
    }
    @Test void expectedRepaymentsUseUnpaidFutureTermsAndRecordedCutoffs(){
        var loan=loan("B1","100.00","5.00",0,true,new BigDecimal("40.00"));
        UUID product=UUID.randomUUID();
        jdbc.update("""
            insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,
                guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,created_at,updated_at)
            values(?,?,'DEVELOPMENT_LOAN',true,'ACTIVE','01','Synthetic loan','{}',0,0.24,'FLAT_RATE','MONTHLY',1,12,now(),now())
            """,product,institution);
        jdbc.update("update loan_applications set loan_product_setting_id=? where id=? and sacco_id=?",product,loan.id(),institution);
        loan("B2","900.00","9.00",0,true);
        var before=OffsetDateTime.now();
        payment(loan,"B1","10.00","5.00",DAY,null,true);
        var kind=com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.EXPECTED_REPAYMENTS;
        var now=management().read(actor,kind,reportParameters(DAY,DAY.plusDays(30),OffsetDateTime.now(),0),false);
        assertThat(now.count()).isEqualTo(2);assertThat(now.excluded()).isZero();
        assertThat(now.totals()).containsOnlyKeys("principalDue","interestDue","expectedTotal");
        assertThat(now.totals().get("principalDue")).isEqualByComparingTo("90.00");
        assertThat(now.totals().get("interestDue")).isZero();
        var old=management().read(actor,kind,reportParameters(DAY,DAY.plusDays(30),before,0),false);
        assertThat(old.totals().get("expectedTotal")).isEqualByComparingTo("105.00");
        assertThat(now.rows()).allSatisfy(row->{
            assertThat((BigDecimal)row.get("principalBalance")).isEqualByComparingTo("90.00");
            assertThat(row).containsEntry("productCode","01");
        });
    }
    @Test void repaymentTransactionsRetainSignedCorrectionsAndBalancesBeforeDateFilter(){
        var loan=loan("B1","100.00","5.00",0,true);
        var first=payment(loan,"B1","20.00","5.00",DAY.minusDays(1),null,true);
        payment(loan,"B1","20.00","5.00",DAY,first,true);
        var kind=com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.LOAN_REPAYMENT_TRANSACTIONS;
        var result=management().read(actor,kind,reportParameters(DAY,DAY,OffsetDateTime.now(),0),false);
        assertThat(result.rows()).singleElement().satisfies(row->{
            assertThat(row.get("entryKind")).isEqualTo("REVERSAL");assertThat(row.get("reversesReference")).isEqualTo(first.toString());
            assertThat((BigDecimal)row.get("openingPrincipal")).isEqualByComparingTo("80.00");
            assertThat((BigDecimal)row.get("closingPrincipal")).isEqualByComparingTo("100.00");
        });
        assertThat(result.totals().get("payment")).isEqualByComparingTo("-25.00");
        assertThat(result.totals()).doesNotContainKeys("openingPrincipal","closingPrincipal");
    }
    @Test void disbursementAuditUsesMonthGroupsAndPreservesUnknownSources(){
        loan("B1","100.00","5.00",0,true);loan("B1","200.00","5.00",0,false);loan("B2","900.00","9.00",0,true);
        var result=management().read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DISBURSED_LOANS_AUDIT,reportParameters(DAY.minusDays(100),DAY,OffsetDateTime.now(),0),false);
        assertThat(result.count()).isEqualTo(2);assertThat(result.excluded()).isEqualTo(1);
        assertThat(result.totals().get("disbursed")).isEqualByComparingTo("100.00");
        assertThat(result.groups()).singleElement().satisfies(g->assertThat(g.name()).startsWith("2026-06 | "));
        assertThat(result.rows()).allSatisfy(row->{
            assertThat(row.get("disbursementAccount")).isNull();
            assertThat(row).containsEntry("institutionCode",institution).containsEntry("branchCode","B1");
        });
    }
    @Test void expectedPreviewIsBoundedAndUnverifiedSchedulesNeverBecomeReceivables(){
        for(int n=0;n<28;n++)loan("B1","0.01","0.00",0,true);
        loan("B1","99.00","1.00",0,false);
        var kind=com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.EXPECTED_REPAYMENTS;
        var first=management().read(actor,kind,reportParameters(DAY,DAY,OffsetDateTime.now(),0),false);
        assertThat(first.rows()).hasSize(26);assertThat(first.count()).isEqualTo(28);assertThat(first.excluded()).isEqualTo(1);
        assertThat(first.totals().get("expectedTotal")).isEqualByComparingTo("0.28");
        var second=management().read(actor,kind,reportParameters(DAY,DAY,OffsetDateTime.now(),1),false);
        assertThat(second.rows()).hasSize(3);assertThat(second.totals()).isEqualTo(first.totals());
    }
    @Test void referenceRiskBandsUseWholeExposureAtEveryBoundaryAndRoundEachLoan(){
        var expected=new java.util.LinkedHashMap<UUID,Integer>();int[] days={30,31,90,91,180,181,365,366},bands={0,1,1,2,2,3,3,4};
        for(int n=0;n<days.length;n++)expected.put(loan("B1","10.01","1.00",days[n],true).id(),bands[n]);
        var reports=new ManagementReportRepository(new NamedParameterJdbcTemplate(jdbc));
        var parameters=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(p.from(),p.through(),OffsetDateTime.now(),null,null,null,"",0);
        var result=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.PORTFOLIO_RISK,parameters,false);
        assertThat(result.excluded()).isZero();assertThat(result.count()).isEqualTo(8);assertThat(result.totals().get("principalBalance")).isEqualByComparingTo("80.08");assertThat(result.totals().get("referenceProvision")).isEqualByComparingTo("28.03");
        expected.forEach((id,band)->{var row=result.rows().stream().filter(r->r.get("rowOrder").toString().endsWith(id.toString())).findFirst().orElseThrow();for(int b=0;b<5;b++)assertThat((BigDecimal)row.get("band"+b)).isEqualByComparingTo(b==band?"10.01":"0.00");});
    }
    @Test void referenceReportsKeepUnverifiedLoanBalancesAndTotalsUnavailable(){
        loan("B1","100.00","1.00",31,false);
        var reports=new ManagementReportRepository(new NamedParameterJdbcTemplate(jdbc));
        var parameters=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(p.from(),p.through(),OffsetDateTime.now(),null,null,null,"",0);
        var result=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.PORTFOLIO_ANALYSIS,parameters,false);
        assertThat(result.excluded()).isEqualTo(1);assertThat(result.rows()).singleElement().satisfies(r->assertThat(r.get("principalBalance")).isNull());assertThat(result.totals().get("principalBalance")).isNull();
    }
    @Test void principalWeightedParAndInterestOnlyArrearsUseWholeExposure(){var late=loan("B1","1000.01","1.00",31,true,new BigDecimal("100.01"));loan("B2","9000.00","0.00",0,true);payment(late,"B1","100.01","0.00",DAY,null,true);var summary=repository.summary(wide,cutoff());assertThat(summary.covered()).isEqualTo(2);assertThat(summary.principal()).isEqualByComparingTo("9900.00");assertThat(summary.par30()).isEqualByComparingTo("900.00");assertThat(summary.overduePrincipal()).isZero();assertThat(summary.overdueInterest()).isEqualByComparingTo("1.00");assertThat(repository.rows(branch,cutoff())).singleElement().satisfies(r->assertThat(r.oldest()).isEqualTo(DAY.minusDays(31)));}
    @Test void latePrincipalUsesEntireBalanceAndNeverAveragesBranches(){loan("B1","1000.01","1.00",31,true);loan("B2","9000.00","0.00",0,true);var s=repository.summary(wide,cutoff());assertThat(s.par30()).isEqualByComparingTo("1000.01");assertThat(LoanRiskMetrics.percentage(s.par30(),s.principal())).isEqualByComparingTo("10.000090");assertThat(repository.branches(wide,cutoff())).hasSize(2);assertThat(repository.rows(branch,cutoff())).hasSize(1);assertThatThrownBy(()->repository.rows(wide,cutoff())).isInstanceOf(IllegalArgumentException.class);}
    @Test void signedReversalAndRecordedCutoffIgnoreMutablePaidAndStatusProjections(){var l=loan("B1","100.01","5.00",31,true);var payment=payment(l,"B1","25.01","5.00",DAY.minusDays(1),null,true);var old=cutoff();jdbc.update("update loan_ledgers set principal_paid=principal,interest_paid=500 where loan_application_id=?",l.id());jdbc.update("update loan_ledger_installments set principal_paid=principal,interest_paid=interest where id=?",l.installment());jdbc.update("update loan_applications set status='PAID' where id=?",l.id());payment(l,"B1","25.01","5.00",DAY,payment,true);assertThat(repository.summary(branch,old).principal()).isEqualByComparingTo("75.00");var now=repository.summary(branch,cutoff());assertThat(now.principal()).isEqualByComparingTo("100.01");assertThat(now.collectedPrincipal()).isZero();assertThat(now.collectedInterest()).isZero();assertThat(now.overdueInterest()).isEqualByComparingTo("5.00");}
    @Test void missingOriginOrAllocationIsExcludedAndUnreviewedOpeningBlocksHeadline(){loan("B1","100.00","1.00",31,false);var l=loan("B1","100.00","1.00",31,true);payment(l,"B1","1.00","0.00",DAY,null,false);loan("B1","100.00","1.00",0,true);var result=service.report(actor,cutoff(),false);assertThat(result.summary().excluded()).isEqualTo(2);assertThat(result.summary().covered()).isEqualTo(1);assertThat(result.ratios().par1()).isNull();assertThat(result.rows().stream().filter(r->!r.complete())).allSatisfy(r->assertThat(r.principal()).isNull());}
    @Test void sourceBranchesClaimsAndCurrentMembershipAreRechecked(){loan("B2","500.00","1.00",91,true);assertThat(service.report(actor,cutoff(),false).rows()).isEmpty();var global=service.report(actor,cutoff(),true);assertThat(global.rows()).isEmpty();assertThat(global.summary().principal()).isEqualByComparingTo("500.00");when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.LOAN_REPORTS_VIEW));assertThatThrownBy(()->service.report(actor,cutoff(),true)).isInstanceOf(AccessDeniedException.class);when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW));assertThatThrownBy(()->service.report(actor,cutoff(),false)).isInstanceOf(AccessDeniedException.class);}
    @Test void pageRowsRemainBoundedWhileTotalsCoverAllSources(){for(int i=0;i<31;i++)loan("B1","0.01","0.00",0,true);var first=service.report(actor,cutoff(),false);assertThat(first.rows()).hasSize(25);assertThat(first.hasNext()).isTrue();assertThat(first.summary().principal()).isEqualByComparingTo("0.31");var second=service.report(actor,new Parameters(p.from(),p.through(),OffsetDateTime.now(),1),false);assertThat(second.rows()).hasSize(6);assertThat(second.hasNext()).isFalse();assertThat(second.summary().principal()).isEqualByComparingTo("0.31");}
    @Test void unverifiedLegacyLoanWithoutLedgerIsMissingRatherThanZeroExposure(){var l=loan("B1","100.00","1.00",31,true);int n=number++;jdbc.update("insert into loan_applications(id,amount,applicant_member_id,created_at,updated_at,disbursement_date,form_data,loan_type,policy_snapshot,repayment_schedule_json,required_guarantors,sacco_id,station_id,status,tenor_months,version,loan_id,application_number) select ?,amount,applicant_member_id,created_at,updated_at,disbursement_date,form_data,loan_type,policy_snapshot,repayment_schedule_json,required_guarantors,sacco_id,station_id,status,tenor_months,version,?,? from loan_applications where id=?",UUID.randomUUID(),Integer.toString(n),n,l.id());var result=service.report(actor,cutoff(),false);assertThat(result.summary().covered()).isEqualTo(1);assertThat(result.summary().excluded()).isEqualTo(1);assertThat(result.ratios().par30()).isNull();}
    @Test void extraOrMalformedContractTermsAreUnavailableRatherThanCleanRisk(){var l=loan("B1","100.00","1.00",31,true);jdbc.update("insert into loan_ledger_installments(id,loan_application_id,installment_number,due_date,principal,interest) values(?,?,2,?,0,1)",UUID.randomUUID(),l.id(),DAY.minusDays(20));var bad=loan("B1","100.00","1.00",0,true);jdbc.update("update loan_applications set repayment_schedule_json='{\"schedule\":[{\"dueDate\":\"2026-10-03\",\"principalComponent\":\"bad\",\"interestComponent\":0}]}' where id=?",bad.id());assertThat(repository.summary(branch,cutoff()).excluded()).isEqualTo(2);}
    @Test void managementAndOrdinaryReferenceBandsHaveDistinctFiveSixAndThirtyBoundaries(){loan("B1","10.00","1.00",5,true);loan("B1","20.00","1.00",6,true);loan("B1","40.00","1.00",30,true);loan("B1","80.00","1.00",31,true);assertThat(repository.buckets(branch,cutoff(),false)).anySatisfy(b->{assertThat(b.band()).isEqualTo(1);assertThat(b.principal()).isEqualByComparingTo("70.00");});assertThat(repository.buckets(branch,cutoff(),true)).anySatisfy(b->{assertThat(b.band()).isEqualTo(0);assertThat(b.principal()).isEqualByComparingTo("10.00");});assertThat(repository.summary(branch,cutoff()).par30()).isEqualByComparingTo("80.00");}
    @Test void spreadsheetExportsAllBranchRowsWithTheSameCutoffAndFreshClaims(){
        for(int n=0;n<31;n++)loan("B1","0.01","0.00",31,true);
        loan("B2","9000.00","0.00",0,true);
        var parameters=new Parameters(p.from(),p.through(),OffsetDateTime.now(),2);
        var exported=service.export(actor,parameters,false);
        assertThat(exported.rows()).hasSize(31);assertThat(exported.hasNext()).isFalse();
        assertThat(exported.summary().principal()).isEqualByComparingTo("0.31");
        assertThat(exported.parameters().recordedCutoff()).isEqualTo(parameters.recordedCutoff());
        var institution=service.export(actor,parameters,true);assertThat(institution.rows()).isEmpty();
        assertThat(institution.summary().principal()).isEqualByComparingTo("9000.31");
        assertThat(institution.branches()).hasSize(2);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.LOAN_REPORTS_VIEW));
        assertThatThrownBy(()->service.export(actor,parameters,false)).isInstanceOf(AccessDeniedException.class);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of(UserClaim.FINANCIAL_REPORTS_VIEW,UserClaim.LOAN_REPORTS_VIEW,UserClaim.FINANCIAL_REPORTS_EXPORT));
        assertThat(service.export(actor,parameters,false).rows()).hasSize(31);
        assertThatThrownBy(()->service.export(actor,parameters,true)).isInstanceOf(AccessDeniedException.class);
    }

}
