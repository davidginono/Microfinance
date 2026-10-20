package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.support.ResourceTableSort;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses only the dedicated disposable loopback fixture, never application.yml credentials. */
@EnabledIfEnvironmentVariable(named="MICROFINANCE_TEST_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_ledger_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResourceTableSortPostgresTest {
    private AnnotationConfigApplicationContext context;
    @BeforeAll void start() { context=new AnnotationConfigApplicationContext(LoanRepaymentLedgerPostgresTest.TestConfig.class); }
    @AfterAll void stop() { if(context!=null)context.close(); }
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }

    private void request(String table,String field) {
        var request=new MockHttpServletRequest();request.setMethod("GET");request.setParameter("tableSort["+table+"]",field);request.setParameter("tableDirection["+table+"]","asc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test void databaseAcceptsEveryWhitelistedJpaAndNativeColumn() throws Exception {
        var manager=context.getBean(EntityManagerFactory.class).createEntityManager();
        var columnsField=ResourceTableSort.class.getDeclaredField("columns");columnsField.setAccessible(true);
        List<Class<?>> repositories=List.<Class<?>>of(LoanApplicationRepository.class,MemberRepository.class,ManagerReviewRepository.class,
            BoardReviewRepository.class,AdminIncidentRepository.class,AuditLogRepository.class,OutboxEventRepository.class,
            NotificationRepository.class,LoanLedgerRepository.class,LoanRepaymentTransactionRepository.class,LoanJournalEntryRepository.class,
            GuarantorRequestRepository.class,LoanProductSettingRepository.class,StationSmsAccountRepository.class,SmsUsageLedgerRepository.class);
        try {
            for(var repository:repositories) for(var method:repository.getMethods()) {
                var annotation=method.getAnnotation(Query.class);
                if(annotation==null || !ResourceTableSort.class.isAssignableFrom(annotation.queryRewriter()))continue;
                var rewriter=(ResourceTableSort)annotation.queryRewriter().getConstructor().newInstance();
                var columns=columnsField.get(rewriter);
                var tablesAccessor=columns.getClass().getDeclaredMethod("tables");tablesAccessor.setAccessible(true);
                var fieldsAccessor=columns.getClass().getDeclaredMethod("fields");fieldsAccessor.setAccessible(true);
                var tables=(Set<?>)tablesAccessor.invoke(columns);var fields=(Map<?,?>)fieldsAccessor.invoke(columns);
                for(var table:tables) for(var field:fields.keySet()) {
                    request(table.toString(),field.toString());
                    String original=annotation.value();
                    if(method.getName().equals("balancesScoped"))original+=" order by l.createdAt desc,l.loanApplicationId";
                    String sql=rewriter.rewrite(original,Sort.unsorted());
                    try {
                        var query=annotation.nativeQuery()?manager.createNativeQuery(sql):manager.createQuery(sql);
                        for(var parameter:method.getParameters()) {
                            var name=parameter.getAnnotation(Param.class);if(name==null)continue;
                            Class<?> type=parameter.getType();Object value;
                            if(Collection.class.isAssignableFrom(type)) {
                                var generic=(ParameterizedType)parameter.getParameterizedType();
                                value=List.of(sample((Class<?>)generic.getActualTypeArguments()[0],annotation.nativeQuery()));
                            } else value=sample(type,annotation.nativeQuery());
                            query.setParameter(name.value(),value);
                        }
                        query.getResultList();
                    } catch(RuntimeException error) {
                        throw new AssertionError(repository.getSimpleName()+"."+method.getName()+" column "+field,error);
                    }
                }
            }
        } finally {manager.close();}
    }

    private Object sample(Class<?> type,boolean nativeQuery) {
        if(type==String.class)return "__SORT_NO_SCOPE__";
        if(type==UUID.class)return UUID.randomUUID();
        if(type==OffsetDateTime.class)return OffsetDateTime.parse("2099-01-01T00:00:00Z");
        if(type==LocalDate.class)return LocalDate.of(2099,1,1);
        if(type==boolean.class || type==Boolean.class)return false;
        if(type==int.class || type==Integer.class)return 1;
        if(type.isEnum())return nativeQuery?((Enum<?>)type.getEnumConstants()[0]).name():type.getEnumConstants()[0];
        throw new AssertionError("Unmapped query parameter "+type);
    }

    private void columns(String table,String names,Runnable query) {
        for(String name:names.split(",")) {
            request(table,name);
            try {query.run();} catch(RuntimeException error) {throw new AssertionError(table+" column "+name,error);}
        }
    }

    @Test void databaseAcceptsEveryAccountingDetailSortBeforeItsExistingLimit() {
        var jdbc=context.getBean(JdbcTemplate.class);UUID id=UUID.randomUUID();
        var member=Member.builder().id(id).memberNo(id.toString()).saccoId("__SORT_NO_SCOPE__").stationId("B1")
            .fullName("Sort fixture").position(Position.ACCOUNTANT).memberAccount(false).status(MemberStatus.ACTIVE).build();
        var actor=new com.sacco.mvp.security.AppUserPrincipal(member,Set.of(),true);
        var gl=new com.sacco.mvp.accounting.repository.GeneralLedgerRepository(jdbc);
        columns("account-version-history","number,state,name,creator",()->gl.accountVersions(actor.getSaccoId(),id,0,id,"B1",true));
        columns("account-version-events","event,actor,date,reason",()->gl.accountVersionEvents(actor.getSaccoId(),id,0));
        var batches=new com.sacco.mvp.accounting.batch.repository.ReceiptBatchRepository(jdbc,context.getBean(tools.jackson.databind.ObjectMapper.class));
        columns("receipt-batches","title,created,status",()->batches.list(actor,"",0));
        var loans=new com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository(jdbc);
        columns("accounting-loans-clients-1","client",()->loans.ledgerClients(actor,"",0));
        columns("accounting-loans-details-1","date,reference,kind,amount,principal,interest,balance",()->loans.loanPosts(actor,id,26,0));
        columns("client-statement-movements","date,loan,product,reference,kind,amount,principal,interest,balance",()->loans.clientPosts(actor,id,26,0));
        var filter=new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Filter("",null,null,"","newest",0,id);
        columns("client-statement-loans","loan,product,status,principal,interest,charges,arrears",()->loans.loans(actor,filter,LocalDate.of(2026,10,20),26,0));
        var terms=new com.sacco.mvp.accounting.repayment.repository.LoanPrepaymentRepository(jdbc);
        var ledger=LoanLedger.builder().loanApplicationId(id).saccoId(actor.getSaccoId()).stationId("B1").build();
        columns("interest-waivers","reference,date,amount,kind,reason",()->terms.history(ledger,0));
        var charges=new com.sacco.mvp.accounting.charges.repository.LoanChargeRepository(jdbc);
        columns("loan-charge-history","date,charge,event,amount,evidence",()->charges.history(id,0));
        var payroll=new com.sacco.mvp.accounting.payroll.repository.PayrollRepository(jdbc);
        columns("payroll-lines","employee,client,loan,principal,interest,requested,issue",()->payroll.lines(actor,id,0,26));
        columns("payroll-history","event,actor,evidence,date",()->payroll.events(actor,id,0));
        var feedback=new com.sacco.mvp.accounting.payroll.repository.PayrollFeedbackRepository(jdbc);
        columns("payroll-results","employee,client,status,requested,deducted,date,employerReference,posting",()->feedback.rows(id,0,26));
        var portfolio=new com.sacco.mvp.accounting.reports.LoanPortfolioReportRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var scope=new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Scope(actor.getSaccoId(),"B1",false);
        var parameters=new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Parameters(LocalDate.of(2026,1,1),LocalDate.of(2026,10,20),OffsetDateTime.now(),0);
        columns("reporting-loan-portfolio-6","loan,principal,overduePrincipal,overdueInterest,oldest,coverage",()->portfolio.rows(scope,parameters));
    }

    @Test void repositorySortsTheEntireScopedQueueBeforePagination() {
        var jdbc=context.getBean(JdbcTemplate.class);
        String institution="SORT-"+UUID.randomUUID().toString().substring(0,8);
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?, 'Sort fixture',true,now(),now())",institution);
        UUID member=UUID.randomUUID();
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,created_at,is_member,password_hash) values(?,?,'B1',?,'Sort borrower','ACTIVE','MEMBER',now(),true,'not-a-login-password')",member,institution,member.toString());
        var repository=context.getBean(LoanApplicationRepository.class);
        var transaction=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        long number=System.currentTimeMillis();
        for(int i=0;i<4;i++) {
            var now=OffsetDateTime.now();
            var loan=LoanApplication.builder().id(UUID.randomUUID()).applicationNumber(number+i).saccoId(institution)
                .stationId(i==3?"B2":"B1").applicantMemberId(member).loanType(LoanType.DEVELOPMENT_LOAN)
                .amount(new BigDecimal(new String[]{"300","100","200","1"}[i])).tenorMonths(2).status(LoanStatus.READY_FOR_MANAGER)
                .formData("{}").policySnapshot("{}").financialSnapshot("{}").requiredGuarantors(0).createdAt(now).updatedAt(now).build();
            transaction.executeWithoutResult(status->repository.saveAndFlush(loan));
        }
        request("manager-queue-1","amount");
        var first=repository.findQueuePageForScope(institution,"B1",List.of(LoanStatus.READY_FOR_MANAGER),PageRequest.of(0,2));
        var second=repository.findQueuePageForScope(institution,"B1",List.of(LoanStatus.READY_FOR_MANAGER),PageRequest.of(1,2));
        assertThat(first.getContent()).extracting(LoanApplicationRepository.QueueLoanRow::getAmount)
            .containsExactly(new BigDecimal("100.00"),new BigDecimal("200.00"));
        assertThat(second.getContent()).extracting(LoanApplicationRepository.QueueLoanRow::getAmount).containsExactly(new BigDecimal("300.00"));
        assertThat(first.getTotalElements()).isEqualTo(3);
        request("admin-users-1","name");
        var users=context.getBean(MemberRepository.class).findUserAccessPageByName(institution,"B1","sort",PageRequest.of(0,25));
        assertThat(users.getContent()).extracting(Member::getFullName).containsExactly("Sort borrower");
    }
}
