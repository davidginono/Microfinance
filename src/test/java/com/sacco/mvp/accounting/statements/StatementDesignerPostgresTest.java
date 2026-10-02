package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.policy.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService.*;
import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.accounting.reconciliation.*;
import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.Allocation;
import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.StatementCommand;
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
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_ACCOUNTING_G_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_accounting_g_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementDesignerPostgresTest {
    private com.zaxxer.hikari.HikariDataSource dataSource;
    private StatementMappingChangeListener mappingChanges;
    private JdbcTemplate jdbc;private TransactionTemplate tx;private GeneralLedgerService gl;private ReconciliationService close;private StatementDesignerService service;private StatementRepository repository;
    private AccountingPolicyService policies;private AuditService audit;private UserClaimService claims;private MemberDirectoryService directory;private SaccoRegistryService institutions;private ApplicationClock clock;
    private String institution;private AppUserPrincipal maker,checker,third,fourth;private UUID bank,capital,period,policy,format;
    private static final LocalDate DAY=LocalDate.of(2026,10,1),END=LocalDate.of(2026,10,31);private OffsetDateTime now;
    @BeforeAll void start(){
        var config=new com.zaxxer.hikari.HikariConfig();config.setJdbcUrl(System.getenv("MICROFINANCE_ACCOUNTING_G_DATABASE_URL"));config.setUsername("microfinance_test");config.setPassword("");config.setMaximumPoolSize(6);config.setMinimumIdle(0);config.setConnectionTimeout(30000);config.addDataSourceProperty("sslmode","disable");config.addDataSourceProperty("connectTimeout","10");config.addDataSourceProperty("socketTimeout","30");dataSource=new com.zaxxer.hikari.HikariDataSource(config);var ds=dataSource;Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        policies=mock(AccountingPolicyService.class);audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);clock=mock(ApplicationClock.class);
        var access=new AccessControlService();gl=proxy(new GeneralLedgerService(new GeneralLedgerRepository(jdbc),policies,access,audit,clock,claims,directory,institutions),manager);
        close=proxy(new ReconciliationService(new ReconciliationRepository(jdbc),policies,access,directory,claims,audit,clock,Optional.empty(),List.of()),manager);
        var mapper=JsonMapper.builder().findAndAddModules().build();repository=new StatementRepository(jdbc,mapper);var official=new RegulatoryFormatCatalog(repository,mapper,clock);
        mappingChanges=mock(StatementMappingChangeListener.class);service=proxy(new StatementDesignerService(repository,policies,gl,close,official,access,directory,claims,institutions,clock,audit,mapper,List.of(mappingChanges)),manager);
    }
    @AfterAll void stop(){if(dataSource!=null)dataSource.close();}
    private static <T>T proxy(T raw,DataSourceTransactionManager manager){var f=new ProxyFactory(raw);f.setProxyTargetClass(true);f.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)f.getProxy();}
    @BeforeEach void fixture(){
        reset(policies,audit,claims,directory,institutions,clock,mappingChanges);now=OffsetDateTime.parse("2026-11-01T10:00:00+03:00");when(clock.today()).thenReturn(END.plusDays(1));when(clock.now()).thenAnswer(i->now);
        institution="G-"+UUID.randomUUID();jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,?,true,now(),now())",institution,"Synthetic statement designer test");
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));when(institutions.findStation(eq(institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder().saccoId(institution).stationId(i.getArgument(1)).active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,created_at,updated_at,access_status) values(?,?,'B1',true,now(),now(),'ACTIVE')",UUID.randomUUID(),institution);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));maker=operator("B1");checker=operator("B1");third=operator("B1");fourth=operator("B1");
        bank=gl.createAccount(maker,new AccountCommand("BANK","Synthetic verified bank","ASSET","DEBIT","POSTING","BANK",null));capital=gl.createAccount(maker,new AccountCommand("CAPITAL","Synthetic owner capital","EQUITY","CREDIT","POSTING","CAPITAL",null));
        policy=UUID.randomUUID();var decisions=new EnumMap<PolicyDecision,String>(PolicyDecision.class);for(var k:PolicyDecision.values())decisions.put(k,"Synthetic approved evidence only");var matrix=new EnumMap<PostingEvent,PostingRule>(PostingEvent.class);for(var k:PostingEvent.values())matrix.put(k,new PostingRule(PostingPermission.ALLOWED,"Synthetic treatment"));var mappings=Map.of("DISBURSEMENT_CLEARING",bank,"REPAYMENT_CLEARING",bank,"OWNER_CAPITAL",capital);var mapper=JsonMapper.builder().findAndAddModules().build();
        jdbc.update("insert into accounting_policies(id,sacco_id,policy_version,effective_from,opening_date,authoritative_ledger,decisions_json,posting_matrix_json,account_mappings_json,evidence_reference,maker_id,request_key,created_at) values(?,?,1,?,?,'LOCAL_GL',?,?,?,'Synthetic reviewer evidence',?,?,?)",policy,institution,DAY,DAY,mapper.writeValueAsString(decisions),mapper.writeValueAsString(matrix),mapper.writeValueAsString(mappings),maker.getMemberId(),UUID.randomUUID(),now);
        jdbc.update("insert into accounting_policy_approvals(policy_id,sacco_id,policy_version,effective_from,checker_id,decision,evidence_reference,reason,decided_at) values(?,?,1,?,?,'APPROVED','Synthetic independent review','Test evidence only',?)",policy,institution,DAY,checker.getMemberId(),now);
        when(policies.requireApprovedLocalPolicy(eq(institution),any())).thenReturn(new PolicySnapshot(policy,institution,1,DAY,DAY,AuthoritativeLedger.LOCAL_GL,decisions,matrix,mappings,"Synthetic evidence",maker.getMemberId(),Decision.APPROVED,checker.getMemberId(),"Synthetic approval","Test",now));
        period=gl.createPeriod(maker,DAY,END);format=close.proposeFormat(maker,"Synthetic bank CSV","Synthetic approved format evidence");close.approveFormat(checker,format,"Independent synthetic format review");
    }
    private AppUserPrincipal operator(String branch){UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,staff_no,full_name,status,position,created_at,is_member,staff_access_status,password_hash) values(?,?,?,?,?,?,'ACTIVE','MANAGER',now(),false,'ACTIVE','test-only')",id,institution,branch,id.toString(),id.toString(),"Synthetic statement staff");var m=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).staffNo(id.toString()).fullName("Synthetic statement staff").position(Position.MANAGER).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build();when(directory.find(id)).thenReturn(Optional.of(m));return new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),true);}
    private UUID post(String reference,String amount,boolean opening){var a=new BigDecimal(amount);var cmd=new JournalCommand(UUID.randomUUID(),reference,opening?DAY:DAY.plusDays(1),"Synthetic verified source",null,List.of(new Line(bank,a,BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,a)));var journal=opening?gl.importOpening(maker,cmd):gl.draftManual(maker,cmd);gl.approve(checker,journal.id(),"Synthetic independent source review");return gl.post(checker,journal.id(),opening).id();}
    private UUID reviewedClose(String opening){return reviewedClose(opening,null);}
    private UUID reviewedClose(String opening,List<Line> sourceLines){
        post("OPENING",opening,true);UUID journal;
        if(sourceLines==null)journal=post("SOURCE","1.01",false);
        else{var draft=gl.draftManual(maker,new JournalCommand(UUID.randomUUID(),"SOURCE",DAY.plusDays(1),"Synthetic compound source",null,sourceLines));gl.approve(checker,draft.id(),"Independent compound review");journal=gl.post(checker,draft.id(),false).id();}
        BigDecimal ending=new BigDecimal(opening).add(new BigDecimal("1.01"));
        UUID statement=close.importStatement(maker,new StatementCommand(UUID.randomUUID(),bank,format,DAY.plusDays(1),END,new BigDecimal(opening),ending,"synthetic.csv","Synthetic verified statement","date,reference,amount,kind\n2026-10-02,SOURCE,1.01,RECEIPT"));
        UUID line=jdbc.queryForObject("select id from gl_journal_line where journal_id=? and account_id=?",UUID.class,journal,bank);UUID match=close.proposeMatch(maker,"EXACT","Synthetic verified match",List.of(new Allocation(close.rows(maker,statement,0).rows().getFirst().id(),line,new BigDecimal("1.01"))));close.reviewMatch(checker,match,true,"Independent match evidence");
        UUID certificate=close.certify(maker,bank,END,"STATEMENT",ending,"Synthetic verified closing statement");close.reviewCertificate(checker,certificate,"Independent certificate evidence");
        assertThat(close.closeChecks(maker,period)).allMatch(c->c.blockers()==0);UUID review=close.proposeClose(maker,period,"Synthetic branch close",false);close.approveClose(checker,review,"Independent close evidence");close.completeInstitutionClose(checker,period,"All synthetic branches reviewed");return review;
    }
    private StatementDefinition definition(boolean visible){return new StatementDefinition(1,1,Kind.BALANCE_SHEET,"Synthetic financial position","Hali ya fedha ya majaribio",List.of(new Row("ASSETS_ROW","Bank assets","Mali za benki",RowKind.ACCOUNT_GROUP,Section.ASSETS,Unit.TZS,List.of(bank),Sign.DEBIT_POSITIVE,null,null,null,visible,true,false,null),new Row("EQUITY_ROW","Capital","Mtaji",RowKind.ACCOUNT_GROUP,Section.EQUITY,Unit.TZS,List.of(capital),Sign.CREDIT_POSITIVE,null,null,null,visible,false,false,null)),List.of(),false);}
    private UUID approvedVersion(boolean visible){UUID id=service.save(maker,null,definition(visible),"Synthetic mapping evidence");service.approve(checker,id,"Independent mapping and exclusions review");return id;}

    @Test void mappingIndependentApprovalAndDatabaseHistoryAreEnforced(){
        UUID id=service.save(maker,null,definition(true),"Synthetic mapping evidence");assertThatThrownBy(()->service.approve(maker,id,"Own review")).hasMessage("statement.error.checker");service.approve(checker,id,"Independent review");
        assertThatThrownBy(()->jdbc.update("update financial_statement_versions set definition='{}' where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->jdbc.update("delete from financial_statement_versions where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(service.hasInstitutionHistory(institution)).isTrue();assertThat(service.hasMemberHistory(checker.getMemberId())).isTrue();
    }
    @Test void releaseInvalidationFailureRollsBackMappingApproval(){
        UUID id=service.save(maker,null,definition(true),"Synthetic draft");doThrow(new IllegalStateException("Synthetic release invalidation failure")).when(mappingChanges).mappingChanged(any(),any(),any(),eq("MAPPING_APPROVED"));
        assertThatThrownBy(()->service.approve(checker,id,"Independent review")).isInstanceOf(IllegalStateException.class);assertThat(service.version(maker,id).state()).isEqualTo("DRAFT");reset(mappingChanges);service.approve(checker,id,"Independent review");var v=service.version(maker,id);verify(mappingChanges).mappingChanged(argThat(a->a.getMemberId().equals(checker.getMemberId()) && a.getSaccoId().equals(institution)),eq(v.template()),eq(id),eq("MAPPING_APPROVED"));service.retire(checker,id);verify(mappingChanges).mappingChanged(argThat(a->a.getMemberId().equals(checker.getMemberId()) && a.getSaccoId().equals(institution)),eq(v.template()),eq(id),eq("RETIRED"));
    }
    @Test void omittedNewAccountsForeignAccountsAndUnapprovedFinalizationFail(){
        UUID draft=service.save(maker,null,definition(true),"Synthetic draft");gl.createAccount(maker,new AccountCommand("EXTRA","New asset","ASSET","DEBIT","POSTING","OTHER",null));assertThatThrownBy(()->service.approve(checker,draft,"Review")).hasMessage("statement.error.omitted");
        assertThatThrownBy(()->service.finalize(maker,draft,UUID.randomUUID(),null)).hasMessage("statement.error.unapproved");
    }
    @Test void endOfDayOpeningExactCentsAndMandatoryRowsSurviveHiddenLayout(){
        UUID version=approvedVersion(false);UUID review=reviewedClose("1000000000000000.01");UUID id=service.finalize(maker,version,review,null);var result=service.verifiedResult(maker,id);
        assertThat(result.reconciliations().get("CASH_OPENING")).isEqualByComparingTo("1000000000000000.01");assertThat(result.reconciliations().get("CASH_MOVEMENT")).isEqualByComparingTo("1.01");assertThat(result.reconciliations().get("CASH_CLOSING")).isEqualByComparingTo("1000000000000001.02");assertThat(result.reconciliations().get("RECONCILIATION_DIFFERENCE")).isZero();
        assertThat(result.getVisibleRows()).allMatch(r->r.mandatory());assertThat(result.getVisibleRows()).anyMatch(r->r.id().equals("REQUIRED_LOAN_PRINCIPAL"));assertThat(result.mappingReviewer()).isEqualTo(checker.getMemberId());assertThat(result.closeReviewId()).isEqualTo(review);assertThat(service.verifiedResultDigest(maker,id)).hasSize(64);
        assertThat(service.preview(maker,version,DAY,END,null,null,now).reconciliations()).isEqualTo(result.reconciliations());
    }
    @Test void immutableResultRemainsExactAfterApprovedReopeningAndRetirement(){
        UUID version=approvedVersion(true),review=reviewedClose("1000.01"),id=service.finalize(maker,version,review,null);String digest=service.verifiedResultDigest(maker,id);now=now.plusMinutes(1);
        UUID reopening=close.proposeClose(third,period,"Verified synthetic correction",true);close.approveClose(fourth,reopening,"Independent reopening evidence");post("LATE","9.99",false);service.retire(checker,version);
        assertThat(service.verifiedResultDigest(maker,id)).isEqualTo(digest);assertThat(service.verifiedResult(maker,id).reconciliations().get("CASH_CLOSING")).isEqualByComparingTo("1001.02");
        assertThatThrownBy(()->jdbc.update("delete from financial_statement_results where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void approvedMemoPlacementRendersWithoutDoubleCounting(){
        var original=definition(true);var rows=new ArrayList<>(original.rows());rows.add(new Row("BANK_MEMO","Bank disclosure","Maelezo ya benki",RowKind.ACCOUNT_GROUP,Section.NOTES,Unit.TZS,List.of(bank),Sign.DEBIT_POSITIVE,null,"Repeated disclosure only\nNo subtotal effect","Maelezo yanayorudiwa tu\nHakuna athari ya jumla",false,true,true,"Independent memo disclosure evidence"));
        UUID version=service.save(maker,null,new StatementDefinition(1,1,Kind.BALANCE_SHEET,original.titleEn(),original.titleSw(),rows,List.of(),false),"Explicit memo treatment");service.approve(checker,version,"Independent memo placement approval");UUID review=reviewedClose("1000.01"),result=service.finalize(maker,version,review,null);
        assertThat(service.verifiedResult(maker,result).rows().stream().filter(r->r.id().equals("BANK_MEMO")).findFirst().orElseThrow().current()).isEqualByComparingTo("1001.02");assertThat(service.verifiedResult(maker,result).reconciliations().get("ASSETS")).isEqualByComparingTo("1001.02");assertThat(service.verifiedResult(maker,result).reconciliations().get("RECONCILIATION_DIFFERENCE")).isZero();
        var memo=service.verifiedResult(maker,result).getVisibleRows().stream().filter(r->r.id().equals("BANK_MEMO")).findFirst().orElseThrow();assertThat(memo.mandatory()).isTrue();assertThat(memo.collapsed()).isFalse();assertThat(memo.kind()).isEqualTo(RowKind.NOTE);assertThat(memo.noteEn()).contains("Independent memo disclosure evidence");assertThat(service.verifiedResult(maker,result).disclosures()).contains("MEMO_DISCLOSURES_EXCLUDED_FROM_CALCULATIONS");
    }
    @Test void restatementMustLinkThePriorResultAndPreserveBothVersions(){
        UUID version=approvedVersion(true),review=reviewedClose("1000.01"),prior=service.finalize(maker,version,review,null);String originalDigest=service.verifiedResultDigest(maker,prior);now=now.plusMinutes(1);
        UUID reopening=close.proposeClose(third,period,"Verified correction",true);close.approveClose(fourth,reopening,"Independent correction approval");UUID journal=post("LATE","1.00",false);
        UUID statement=close.importStatement(maker,new StatementCommand(UUID.randomUUID(),bank,format,DAY.plusDays(1),END,new BigDecimal("1001.02"),new BigDecimal("1002.02"),"late.csv","Verified correction statement","date,reference,amount,kind\n2026-10-02,LATE,1.00,RECEIPT"));
        UUID line=jdbc.queryForObject("select id from gl_journal_line where journal_id=? and account_id=?",UUID.class,journal,bank);UUID match=close.proposeMatch(maker,"EXACT","Corrected source",List.of(new Allocation(close.rows(maker,statement,0).rows().getFirst().id(),line,BigDecimal.ONE)));close.reviewMatch(checker,match,true,"Independent correction matching");
        UUID certificate=close.certify(maker,bank,END,"STATEMENT",new BigDecimal("1002.02"),"Corrected statement");close.reviewCertificate(checker,certificate,"Independent corrected certificate");now=now.plusMinutes(1);UUID revised=close.proposeClose(maker,period,"Reviewed restatement",false);close.approveClose(checker,revised,"Independent restatement review");close.completeInstitutionClose(checker,period,"Corrected branches reviewed");
        assertThatThrownBy(()->service.finalize(maker,version,revised,null)).hasMessage("statement.error.restatement");UUID result=service.finalize(maker,version,revised,null,prior);
        assertThat(service.verifiedResult(maker,result).priorResultId()).isEqualTo(prior);assertThat(service.verifiedResult(maker,result).reconciliations().get("CASH_CLOSING")).isEqualByComparingTo("1002.02");assertThat(service.verifiedResultDigest(maker,prior)).isEqualTo(originalDigest);
    }
    @Test void cashFlowIncludesActualCashMovementAndExcludesOpeningImports(){
        var row=new Row("FUNDING_MOVEMENT","Owner funding","Ufadhili wa mmiliki",RowKind.ACCOUNT_GROUP,Section.FINANCING,Unit.TZS,List.of(capital),Sign.DEBIT_POSITIVE,null,null,null,true,false,false,null);
        var d=new StatementDefinition(1,1,Kind.CASH_FLOW,"Synthetic cash flow","Mtiririko wa fedha wa majaribio",List.of(row),List.of(),false);UUID version=service.save(maker,null,d,"Synthetic approved cash classification");service.approve(checker,version,"Independent cash classification review");UUID review=reviewedClose("1000.01"),id=service.finalize(maker,version,review,null);
        assertThat(service.verifiedResult(maker,id).rows().stream().filter(r->r.id().equals("FUNDING_MOVEMENT")).findFirst().orElseThrow().current()).isEqualByComparingTo("1.01");
    }
    @Test void comparativeProfitAndEquityUseExactReviewedPeriodMovementsAndRejectNewerComparisonEvidence(){
        UUID income=gl.createAccount(maker,new AccountCommand("INCOME","Synthetic income","INCOME","CREDIT","POSTING","INCOME",null)),expense=gl.createAccount(maker,new AccountCommand("EXPENSE","Synthetic expense","EXPENSE","DEBIT","POSTING","EXPENSE",null));UUID comparison=reviewedClose("1000.01");
        now=OffsetDateTime.parse("2026-12-01T10:00:00+03:00");when(clock.today()).thenReturn(LocalDate.of(2026,12,1));LocalDate from=LocalDate.of(2026,11,1),through=LocalDate.of(2026,11,30);UUID nextPeriod=gl.createPeriod(maker,from,through);
        var movements=new LinkedHashMap<String,List<Line>>();movements.put("INCOME",List.of(new Line(bank,new BigDecimal("10.23"),BigDecimal.ZERO),new Line(income,BigDecimal.ZERO,new BigDecimal("10.23"))));movements.put("EXPENSE",List.of(new Line(expense,new BigDecimal("2.11"),BigDecimal.ZERO),new Line(bank,BigDecimal.ZERO,new BigDecimal("2.11"))));var journals=new HashMap<String,UUID>();int day=1;
        for(var movement:movements.entrySet()){var draft=gl.draftManual(maker,new JournalCommand(UUID.randomUUID(),movement.getKey(),from.plusDays(day++),"Synthetic verified movement",null,movement.getValue()));gl.approve(checker,draft.id(),"Independent movement review");journals.put(movement.getKey(),gl.post(checker,draft.id(),false).id());}
        UUID statement=close.importStatement(maker,new StatementCommand(UUID.randomUUID(),bank,format,from.plusDays(1),through,new BigDecimal("1001.02"),new BigDecimal("1009.14"),"november.csv","Verified second-period statement","date,reference,amount,kind\n2026-11-02,INCOME,10.23,RECEIPT\n2026-11-03,EXPENSE,-2.11,DISBURSEMENT"));
        for(var row:close.rows(maker,statement,0).rows()){UUID line=jdbc.queryForObject("select id from gl_journal_line where journal_id=? and account_id=?",UUID.class,journals.get(row.reference()),bank);UUID match=close.proposeMatch(maker,"EXACT","Verified second-period matching",List.of(new Allocation(row.id(),line,row.amount().abs())));close.reviewMatch(checker,match,true,"Independent matching review");}
        UUID certificate=close.certify(maker,bank,through,"STATEMENT",new BigDecimal("1009.14"),"Verified second-period closing");close.reviewCertificate(checker,certificate,"Independent closing certificate");UUID current=close.proposeClose(maker,nextPeriod,"Second-period review",false);close.approveClose(checker,current,"Independent second-period review");close.completeInstitutionClose(checker,nextPeriod,"All branches reviewed");
        var incomeRow=new Row("INCOME_ROW","Income","Mapato",RowKind.ACCOUNT_GROUP,Section.INCOME,Unit.TZS,List.of(income),Sign.CREDIT_POSITIVE,null,null,null,true,false,false,null);var expenseRow=new Row("EXPENSE_ROW","Expenses","Matumizi",RowKind.ACCOUNT_GROUP,Section.EXPENSES,Unit.TZS,List.of(expense),Sign.DEBIT_POSITIVE,null,null,null,true,false,false,null);var profit=new Row("PERIOD_PROFIT","Period profit","Faida ya kipindi",RowKind.SUBTOTAL,Section.INCOME,Unit.TZS,List.of(),null,new Expression(Operation.DIFFERENCE,List.of("INCOME_ROW","EXPENSE_ROW")),null,null,true,false,false,null);
        UUID pl=service.save(maker,null,new StatementDefinition(1,1,Kind.PROFIT_AND_LOSS,"Reviewed profit","Faida iliyopitiwa",List.of(incomeRow,expenseRow,profit),List.of(),true),"Synthetic comparative mapping");service.approve(checker,pl,"Independent comparative review");var result=service.verifiedResult(maker,service.finalize(maker,pl,current,comparison));var total=result.rows().stream().filter(r->r.id().equals("PERIOD_PROFIT")).findFirst().orElseThrow();assertThat(total.current()).isEqualByComparingTo("8.12");assertThat(total.comparison()).isZero();assertThat(result.reconciliations().get("PROFIT")).isEqualByComparingTo("8.12");assertThat(result.comparisonCutoff()).isBefore(result.recordedCutoff());
        var capitalRow=new Row("CAPITAL_MOVE","Capital movement","Mabadiliko ya mtaji",RowKind.ACCOUNT_GROUP,Section.EQUITY,Unit.TZS,List.of(capital),Sign.CREDIT_POSITIVE,null,null,null,true,false,false,null);var equityIncome=new Row("INCOME_ROW","Income","Mapato",RowKind.ACCOUNT_GROUP,Section.EQUITY,Unit.TZS,List.of(income),Sign.CREDIT_POSITIVE,null,null,null,true,false,false,null);var equityExpense=new Row("EXPENSE_ROW","Expense effect","Athari ya matumizi",RowKind.ACCOUNT_GROUP,Section.EQUITY,Unit.TZS,List.of(expense),Sign.CREDIT_POSITIVE,null,null,null,true,false,false,null);var equityTotal=new Row("EQUITY_CHANGE","Equity change","Mabadiliko ya mtaji",RowKind.SUBTOTAL,Section.EQUITY,Unit.TZS,List.of(),null,new Expression(Operation.SUM,List.of("CAPITAL_MOVE","INCOME_ROW","EXPENSE_ROW")),null,null,true,false,false,null);
        UUID equity=service.save(maker,null,new StatementDefinition(1,1,Kind.CHANGES_IN_EQUITY,"Reviewed equity","Mtaji uliopitiwa",List.of(capitalRow,equityIncome,equityExpense,equityTotal),List.of(),true),"Synthetic equity mapping");service.approve(checker,equity,"Independent equity review");var change=service.verifiedResult(maker,service.finalize(maker,equity,current,comparison));var cell=change.rows().stream().filter(r->r.id().equals("EQUITY_CHANGE")).findFirst().orElseThrow();assertThat(cell.current()).isEqualByComparingTo("8.12");assertThat(cell.comparison()).isEqualByComparingTo("1.01");assertThat(change.reconciliations().get("EQUITY_MOVEMENT")).isEqualByComparingTo(cell.current());
        String retainedDigest=service.verifiedResultDigest(maker,result.id());now=now.plusMinutes(1);UUID reopening=close.proposeClose(third,period,"Verified comparative correction review",true);close.approveClose(fourth,reopening,"Independent comparative reopening");now=now.plusMinutes(1);
        UUID revisedComparison=close.proposeClose(maker,period,"Re-reviewed comparative evidence",false);close.approveClose(checker,revisedComparison,"Independent comparative reclose");close.completeInstitutionClose(checker,period,"All comparative branches reviewed");
        assertThat(close.finalizedSnapshot(maker,revisedComparison).recordedCutoff()).isAfter(result.recordedCutoff());long retainedCount=jdbc.queryForObject("select count(*) from financial_statement_results where sacco_id=?",Long.class,institution);
        assertThatThrownBy(()->service.finalize(maker,pl,current,revisedComparison)).hasMessage("statement.error.comparisonEvidence");assertThat(jdbc.queryForObject("select count(*) from financial_statement_results where sacco_id=?",Long.class,institution)).isEqualTo(retainedCount);assertThat(service.verifiedResultDigest(maker,result.id())).isEqualTo(retainedDigest);
    }
    @Test void netCashEqualityDoesNotProveCompoundJournalClassification(){
        UUID expense=gl.createAccount(maker,new AccountCommand("EXPENSE","Synthetic expense","EXPENSE","DEBIT","POSTING","EXPENSE",null));
        UUID income=gl.createAccount(maker,new AccountCommand("INCOME","Synthetic income","INCOME","CREDIT","POSTING","INCOME",null));
        var row=new Row("FUNDING","Funding","Ufadhili",RowKind.ACCOUNT_GROUP,Section.FINANCING,Unit.TZS,List.of(capital),Sign.DEBIT_POSITIVE,null,null,null,true,false,false,null);
        var d=new StatementDefinition(1,1,Kind.CASH_FLOW,"Compound cash fixture","Majaribio ya fedha",List.of(row),List.of(new Exclusion(expense,"Noncash pair; requires source allocation","Jozi isiyo fedha; inahitaji mgao","Independent synthetic treatment"),new Exclusion(income,"Noncash pair; requires source allocation","Jozi isiyo fedha; inahitaji mgao","Independent synthetic treatment")),false);
        UUID version=service.save(maker,null,d,"Synthetic classifications");service.approve(checker,version,"Independent account classification review");
        UUID review=reviewedClose("1000.01",List.of(new Line(bank,new BigDecimal("1.01"),BigDecimal.ZERO),new Line(capital,BigDecimal.ZERO,new BigDecimal("1.01")),new Line(expense,new BigDecimal("2.00"),BigDecimal.ZERO),new Line(income,BigDecimal.ZERO,new BigDecimal("2.00"))));
        assertThatThrownBy(()->service.finalize(maker,version,review,null)).hasMessage("statement.error.cashAmbiguous");
        assertThat(jdbc.queryForObject("select count(*) from financial_statement_results where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void incompleteOpeningCoverageStaysUnknownInDraft(){
        UUID version=approvedVersion(true);var result=service.preview(maker,version,DAY,END,null,null,now);
        assertThat(result.coverage()).isEqualTo("INCOMPLETE_NOT_AUTHORITATIVE");assertThat(result.reconciliations().values()).allMatch(Objects::isNull);
        assertThat(result.rows().stream().filter(r->r.unit()==Unit.TZS)).allMatch(r->r.current()==null && r.status().equals("UNAVAILABLE"));
    }
    @Test void mappingRetirementWaitsForThePublicationEvidenceTransaction()throws Exception{
        UUID version=approvedVersion(true);var retained=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var publication=pool.submit(()->tx.execute(status->{var mapping=service.verifiedVersionForPublication(maker,version);retained.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}return mapping;}));
            assertThat(retained.await(20,TimeUnit.SECONDS)).isTrue();var retirement=pool.submit(()->service.retire(checker,version));
            try{assertThatThrownBy(()->retirement.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            assertThat(publication.get(20,TimeUnit.SECONDS).definitionChecksum()).hasSize(64);retirement.get(20,TimeUnit.SECONDS);
            assertThatThrownBy(()->tx.execute(status->service.verifiedVersionForPublication(maker,version))).hasMessage("statement.error.unapproved");
        }finally{release.countDown();}
    }
    @Test void publicationHoldsThePeriodGateUntilTheFinalResultCommits()throws Exception{
        UUID version=approvedVersion(true),review=reviewedClose("1000.01");var retained=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var publish=pool.submit(()->tx.execute(status->{UUID id=service.finalize(maker,version,review,null);retained.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic barrier timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}return id;}));
            assertThat(retained.await(20,TimeUnit.SECONDS)).isTrue();now=now.plusMinutes(1);var reopen=pool.submit(()->close.proposeClose(third,period,"Controlled correction after publication",true));
            try{assertThatThrownBy(()->reopen.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            assertThat(publish.get(20,TimeUnit.SECONDS)).isNotNull();assertThat(reopen.get(20,TimeUnit.SECONDS)).isNotNull();
        }finally{release.countDown();}
    }
    @Test void scopeRevocationAndDisabledBranchAreCheckedOnRetainedResults(){
        UUID version=approvedVersion(true),review=reviewedClose("1000.01"),id=service.finalize(maker,version,review,null);var foreignBranch=operator("B2");assertThatThrownBy(()->service.verifiedResult(foreignBranch,id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(()->service.verifiedResult(maker,id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(claims.effectiveClaims(eq(maker.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));when(institutions.findStation(institution,"B1")).thenReturn(Optional.of(SaccoStation.builder().active(false).build()));assertThatThrownBy(()->service.verifiedResult(maker,id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void platformRoleInactiveMemberAndInactiveInstitutionCannotReadRetainedMoney(){
        UUID version=approvedVersion(true),review=reviewedClose("1000.01"),id=service.finalize(maker,version,review,null);Member current=directory.find(maker.getMemberId()).orElseThrow();current.setPosition(Position.ADMIN);assertThatThrownBy(()->service.verifiedResult(maker,id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        current.setPosition(Position.MANAGER);current.setStatus(MemberStatus.INACTIVE);assertThatThrownBy(()->service.verifiedResult(maker,id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);current.setStatus(MemberStatus.ACTIVE);when(institutions.findActiveSacco(institution)).thenReturn(Optional.empty());assertThatThrownBy(()->service.verifiedResult(maker,id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void failedAuditCannotLeaveAnUnreviewedRetainedResult(){
        UUID version=approvedVersion(true),review=reviewedClose("1000.01");doThrow(new IllegalStateException("Synthetic final audit failure")).when(audit).log(eq("FINANCIAL_STATEMENT"),any(),eq("FINALIZED"),any(),any(),any());
        assertThatThrownBy(()->service.finalize(maker,version,review,null)).isInstanceOf(IllegalStateException.class);assertThat(jdbc.queryForObject("select count(*) from financial_statement_results where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void concurrentFinalizationIsScopedAndRetainedOnce()throws Exception{
        UUID version=approvedVersion(true),review=reviewedClose("1000.01");try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->service.finalize(maker,version,review,null));var b=pool.submit(()->service.finalize(third,version,review,null));assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));}assertThat(jdbc.queryForObject("select count(*) from financial_statement_results where sacco_id=?",Integer.class,institution)).isEqualTo(1);
    }
    @Test void protectedRegulatoryFormatAndActualFileRequireIndependentReview(){
        UUID version=approvedVersion(true),review=reviewedClose("1000.01"),result=service.finalize(maker,version,review,null),officialId=UUID.randomUUID();
        var spec=new RegulatoryFormatCatalog.Format(1,"Synthetic authority fixture","SYNTHETIC_"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT),"1","https://www.bot.go.tz/fixture-only","Synthetic test evidence; not an actual return",Kind.BALANCE_SHEET,RegulatoryFormatCatalog.FileFormat.CSV,RegulatoryFormatCatalog.Period.MONTH,30,List.of(new RegulatoryFormatCatalog.Field("ASSETS","Assets","Mali",Unit.TZS,"REQUIRED_ASSETS")),List.of());spec.validate();var mapper=JsonMapper.builder().findAndAddModules().build();String json=mapper.writeValueAsString(spec);repository.installOfficial(officialId,spec.authority(),spec.key(),spec.version(),json,StatementDesignerService.sha(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)),spec.officialReference(),spec.applicabilityEvidence(),now);
        byte[] bytes="Assets,1001.02\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);UUID submission=service.submit(maker,officialId,result,"synthetic.csv",bytes,"Manually verified file against protected fields",null);assertThatThrownBy(()->service.reviewSubmission(maker,submission,"Own review")).hasMessage("statement.error.checker");service.reviewSubmission(checker,submission,"Independent manual file validation");assertThat(service.submissionFile(maker,submission)).isEqualTo(bytes);
        assertThatThrownBy(()->jdbc.update("update regulatory_statement_formats set format_json='{}' where id=?",officialId)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->jdbc.update("update regulatory_statement_submissions set file_bytes=? where id=?","changed".getBytes(),submission)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->service.submit(maker,officialId,result,"../unsafe.csv",bytes,"Unsafe file",null)).hasMessage("statement.error.officialFile");assertThatThrownBy(()->service.submit(maker,officialId,result,"wrong.pdf",bytes,"Wrong file type",null)).hasMessage("statement.error.officialFile");
        UUID correction=service.submit(maker,officialId,result,"correction.csv",bytes,"Reviewed correction to prepared file",submission);service.reviewSubmission(checker,correction,"Independent corrected manual file");assertThat(service.submissions(maker,0)).anyMatch(s->s.id().equals(correction) && s.corrects().equals(submission));
        now=now.plusMinutes(1);UUID reopen=close.proposeClose(third,period,"Verified later correction",true);close.approveClose(fourth,reopen,"Independent correction approval");
        assertThatThrownBy(()->service.submit(maker,officialId,result,"stale.csv",bytes,"Old source is archived",null)).hasMessage("reconciliation.error.approvalRequired");assertThat(service.submissionFile(maker,submission)).isEqualTo(bytes);
    }
}
