package com.sacco.mvp.accounting.loans.service;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service @RequiredArgsConstructor
public class LoanRecordingService {
    private final LoanRecordingRepository records;
    private final LoanApplicationRepository loans;
    private final LoanProductSettingRepository products;
    private final ApplicationNumberService numbers;
    private final RepaymentScheduleService schedules;
    private final LoanRepaymentLedgerService repayments;
    private final GeneralLedgerService accounting;
    private final VoucherRepository periods;
    private final ObjectMapper json;
    private final ApplicationClock clock;
    private final AuditService audit;
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    public void authorize(AppUserPrincipal actor,String action){accounting.requireActor(actor,"LOAN_RECORDING_"+action);}
    @Transactional(readOnly=true)
    public Page<LoanRow> list(AppUserPrincipal a,Filter f){authorize(a,"VIEW");filter(f,false);var rows=records.loans(a,f,clock.today(),26,f.page()*25);return new Page<>(rows.stream().limit(25).toList(),f.page(),rows.size()>25);}
    @Transactional(readOnly=true)
    public Page<Posting> register(AppUserPrincipal a,String kind,Filter f){authorize(a,"VIEW");filter(f,true);kind(kind);var rows=records.posts(a,kind,f,26,f.page()*25);return new Page<>(rows.stream().limit(25).toList(),f.page(),rows.size()>25);}
    @Transactional(readOnly=true)
    public List<Choice> choices(AppUserPrincipal a,String category,String search,int page){authorize(a,"VIEW");check(search!=null && search.length()<=100 && page>=0 && page<=10000,"filter");return switch(category){
        case "clients"->records.clients(a,search,page);case "ledger-clients"->records.ledgerClients(a,search,page);case "products"->records.products(a,search,page);
        case "money"->records.accounts(a,"MONEY",search,page);case "principal"->records.accounts(a,"PRINCIPAL",search,page);case "interest"->records.accounts(a,"INTEREST",search,page);
        case "loans"->records.loans(a,new Filter(search,null,null,"","newest",page,null),clock.today(),26,page*25).stream().map(l->new Choice(l.id(),l.number()+" - "+l.client(),l.status())).toList();
        default->throw new IllegalArgumentException("recording.error.filter");};}
    public record Quote(String product,BigDecimal rate,String method,String frequency,BigDecimal installment,BigDecimal interest,List<ScheduleRow> schedule) {}
    public record AllocationPreview(BigDecimal principal,BigDecimal interest,BigDecimal principalBalance) {}
    @Transactional(readOnly=true)
    public AllocationPreview postingPreview(AppUserPrincipal a,PostForm f,String kind){
        authorize(a,kind.equals("DISBURSEMENT")?"DISBURSE":"POST");validatePost(f);records.record(a,f.getLoanId(),false);
        var loan=loans.findById(f.getLoanId()).orElseThrow();
        records.account(a,f.getMoneyAccountId(),"MONEY",false);
        if(kind.equals("DISBURSEMENT")){check(loan.getStatus()==LoanStatus.RECORDED,"state");check(f.getAmount().compareTo(loan.getAmount())==0,"disbursementAmount");records.account(a,f.getPrincipalAccountId(),"PRINCIPAL",false);return new AllocationPreview(f.getAmount(),ZERO,f.getAmount());}
        check(Set.of(LoanStatus.DISBURSED,LoanStatus.PAR,LoanStatus.DEFAULTED).contains(loan.getStatus()),"state");
        var balance=repayments.paymentBalance(f.getLoanId(),a,f.getEffectiveDate());
        check(f.getAmount().compareTo(balance.duePrincipal().add(balance.dueInterest()))<=0,"advance");
        var interest=f.getAmount().min(balance.dueInterest());var principal=f.getAmount().subtract(interest);
        if(interest.signum()>0)records.account(a,f.getInterestAccountId(),"INTEREST",false);
        return new AllocationPreview(principal,interest,balance.outstandingPrincipal().subtract(principal));
    }
    @Transactional(readOnly=true)
    public Quote preview(AppUserPrincipal a,RecordForm f){authorize(a,"CREATE");validateRecord(f);check(records.client(a,f.getClientId()).isPresent(),"client");var product=product(a,f);var result=calculate(f,product);
        return new Quote(product.getDisplayName(),product.getInterestRate(),product.getInterestMethod().name(),product.getResolvedRepaymentFrequency().name(),result.installment(),result.totalInterest(),schedule(result,f.getFirstPaymentDate(),product.getResolvedRepaymentFrequency()));}
    @Transactional
    public UUID save(AppUserPrincipal a,RecordForm f){
        authorize(a,"CREATE");validateRecord(f);String hash=hash(f);records.lockRequest(a,f.getRequestKey());var previous=records.recordRequest(a,f.getRequestKey(),hash);if(previous.isPresent())return previous.get();
        check(records.client(a,f.getClientId()).isPresent(),"client");var product=product(a,f);var calculation=calculate(f,product);var now=clock.now();
        var snapshot=new LinkedHashMap<String,Object>();snapshot.put("calculationVersion",LoanAmortizationCalculator.VERSION);snapshot.put("principalAmount",f.getPrincipal());
        snapshot.put("interestRate",product.getInterestRate());snapshot.put("interestMethod",product.getInterestMethod().name());snapshot.put("repaymentFrequency",product.getResolvedRepaymentFrequency().name());
        snapshot.put("tenorMonths",f.getMonths());snapshot.put("numberOfPayments",calculation.rows().size());snapshot.put("periodicRepaymentAmount",calculation.installment());
        snapshot.put("maximumInstallmentAmount",calculation.maximumInstallment());snapshot.put("interestAmount",calculation.totalInterest());snapshot.put("recordingSource","ACCOUNTANT_DIRECT");
        long number=numbers.nextFor(a.getSaccoId());
        var loan=LoanApplication.builder().id(UUID.randomUUID()).applicationNumber(number).loanId(Long.toString(number)).saccoId(a.getSaccoId()).stationId(a.getStationId())
            .applicantMemberId(f.getClientId()).loanType(product.getLoanType()).loanProductSettingId(product.getId()).amount(f.getPrincipal().setScale(2))
            .tenorMonths(f.getMonths()).status(LoanStatus.RECORDED).requiredGuarantors(product.getGuarantorsRequired()).formData(write(f))
            .policySnapshot(write(Map.of("source","ACCOUNTANT_DIRECT","product",product.getDisplayName(),"guarantorsRequired",product.getGuarantorsRequired())))
            .financialSnapshot(write(snapshot)).repaymentFrequency(product.getResolvedRepaymentFrequency()).firstRepaymentDate(f.getFirstPaymentDate())
            .installmentAmount(calculation.installment()).createdAt(now).updatedAt(now).build();
        loan=loans.saveAndFlush(loan);UUID id=loan.getId();records.insertRecord(a,id,f,hash,product.getDisplayName(),write(f),now);
        event(a,id,"LOAN_RECORDED",Map.of("principal",f.getPrincipal()));return id;
    }
    @Transactional(readOnly=true)
    public Detail detail(AppUserPrincipal a,UUID id){
        authorize(a,"VIEW");var record=records.record(a,id,false);var loan=loans.findById(id).orElseThrow();var f=read(record.get("metadata").toString(),RecordForm.class);
        var snapshot=schedules.parseSummary(loan.getFinancialSnapshot());var calc=LoanAmortizationCalculator.estimate(loan.getAmount(),loan.getTenorMonths(),snapshot);
        return new Detail(records.loan(a,id,clock.today()),record.get("sacco_name").toString(),record.get("branch_name").toString(),record.get("actor_name").toString(),time(record.get("recorded_at")),
            f.getPurpose(),f.getBusiness(),f.getIncome(),f.getExpenses(),f.getOtherDebt(),f.getInformationSource(),f.getGuarantors(),f.getCollateral(),f.getEvidence(),f.getNotes(),
            new BigDecimal(snapshot.get("interestRate").toString()),snapshot.get("interestMethod").toString(),snapshot.get("repaymentFrequency").toString(),loan.getTenorMonths(),f.getFirstPaymentDate(),calc.installment(),calc.totalInterest(),schedule(calc,f.getFirstPaymentDate(),loan.getRepaymentFrequency()));
    }
    @Transactional
    public Posting disburse(AppUserPrincipal a,PostForm f){
        authorize(a,"DISBURSE");validatePost(f);String hash=hash(f);records.lockRequest(a,f.getRequestKey());var prior=records.postRequest(a,f.getRequestKey(),hash);if(prior.isPresent())return prior.get();
        var record=records.record(a,f.getLoanId(),true);var loan=loans.findById(f.getLoanId()).orElseThrow();check(loan.getStatus()==LoanStatus.RECORDED,"state");
        check(f.getAmount().compareTo(loan.getAmount())==0,"disbursementAmount");LocalDate first=((java.sql.Date)record.get("first_payment_date")).toLocalDate();
        check(!f.getEffectiveDate().isBefore(((java.sql.Date)record.get("application_date")).toLocalDate()) && first.isAfter(f.getEffectiveDate()),"date");
        UUID period=periods.period(a.getSaccoId(),f.getEffectiveDate(),a.getMemberId(),clock.now());
        var money=records.account(a,f.getMoneyAccountId(),"MONEY");var principal=records.account(a,f.getPrincipalAccountId(),"PRINCIPAL");
        check(f.getInterestAccountId()==null,"account");UUID id=UUID.randomUUID(),journal=UUID.randomUUID();var now=clock.now();
        records.journal(a,journal,id,f,hash,period,"DISBURSEMENT",null,now);records.ticket(a,id,"DISBURSEMENT",f,hash,journal,null,money,principal,null,now);
        var schedule=schedules.buildSchedule(loan,f.getEffectiveDate(),first,loan.getRepaymentFrequency(),null,f.getReference(),f.getNotes());
        loan.setStatus(LoanStatus.DISBURSED);loan.setDisbursementDate(f.getEffectiveDate());loan.setDisbursementReference(f.getReference().strip());loan.setDisbursementNotes(f.getNotes().strip());
        loan.setRepaymentScheduleJson(schedule.scheduleJson());loan.setFinalDueDate(schedule.finalDueDate());loan.setUpdatedAt(now);loans.saveAndFlush(loan);repayments.openAtDisbursement(loan);
        records.lines(a,journal,List.of(new Object[]{principal.id(),f.getAmount(),ZERO},new Object[]{money.id(),ZERO,f.getAmount()}));
        records.finish(id,null,f.getAmount(),ZERO,f.getAmount());records.postJournal(a,journal,now);event(a,id,"LOAN_DISBURSEMENT_POSTED",Map.of("loan",loan.getId(),"amount",f.getAmount()));return records.posting(a,id);
    }
    @Transactional
    public Posting repay(AppUserPrincipal a,PostForm f){
        authorize(a,"POST");validatePost(f);String hash=hash(f);records.lockRequest(a,f.getRequestKey());var prior=records.postRequest(a,f.getRequestKey(),hash);if(prior.isPresent())return prior.get();
        records.record(a,f.getLoanId(),true);UUID period=periods.period(a.getSaccoId(),f.getEffectiveDate(),a.getMemberId(),clock.now());
        UUID principalId=records.principalAccount(f.getLoanId());check(f.getPrincipalAccountId()==null || principalId.equals(f.getPrincipalAccountId()),"account");
        var principal=records.account(a,principalId,"PRINCIPAL");var money=records.account(a,f.getMoneyAccountId(),"MONEY");
        var interest=f.getInterestAccountId()==null?null:records.account(a,f.getInterestAccountId(),"INTEREST");
        UUID id=UUID.randomUUID(),journal=UUID.randomUUID();var now=clock.now();records.journal(a,journal,id,f,hash,period,"PAYMENT",null,now);
        records.ticket(a,id,"PAYMENT",f,hash,journal,null,money,principal,interest,now);
        var receipt=repayments.postRecorded(f.getLoanId(),a,new LoanRepaymentLedgerService.PaymentCommand(f.getAmount(),f.getEffectiveDate(),LoanRepaymentTransaction.Channel.valueOf(money.purpose()),f.getReference().strip(),f.getRequestKey()));
        loans.flush();
        check(receipt.interest().signum()==0 || interest!=null,"interestAccount");var lines=new ArrayList<Object[]>();lines.add(new Object[]{money.id(),f.getAmount(),ZERO});
        if(receipt.principal().signum()>0)lines.add(new Object[]{principal.id(),ZERO,receipt.principal()});if(receipt.interest().signum()>0)lines.add(new Object[]{interest.id(),ZERO,receipt.interest()});
        records.lines(a,journal,lines);records.finish(id,receipt.id(),receipt.principal(),receipt.interest(),records.loan(a,f.getLoanId(),clock.today()).outstandingPrincipal());records.postJournal(a,journal,now);
        event(a,id,"LOAN_REPAYMENT_POSTED",Map.of("loan",f.getLoanId(),"amount",f.getAmount()));return records.posting(a,id);
    }
    @Transactional
    public Posting reverse(AppUserPrincipal a,UUID originalId,UUID key,LocalDate date,String reason){
        authorize(a,"REVERSE");check(key!=null,"request");text(reason,500,true);var original=records.posting(a,originalId);check("PAYMENT".equals(original.kind()),"reversal");check(!original.actorId().equals(a.getMemberId()),"independentCorrection");
        var f=new PostForm();f.setRequestKey(key);f.setLoanId(original.loanId());f.setEffectiveDate(date);f.setAmount(original.amount());f.setReference(original.reference());f.setEvidence(original.evidence());f.setNotes(reason.strip());
        validatePost(f);String hash=hash(Map.of("original",originalId,"form",f));records.lockRequest(a,key);var prior=records.postRequest(a,key,hash);if(prior.isPresent())return prior.get();
        records.record(a,original.loanId(),true);original=records.posting(a,originalId);check(original.reversedBy()==null,"reversal");check(!date.isBefore(original.date()),"date");
        UUID period=periods.period(a.getSaccoId(),date,a.getMemberId(),clock.now());var raw=records.reversedLines(original.journalId());
        // Original accounts are retained; corrections cannot switch the receiving account or allocation.
        var money=new Choice((UUID)raw.stream().filter(l->((BigDecimal)l[2]).signum()>0).findFirst().orElseThrow()[0],original.moneyAccount(),"");
        var principal=new Choice(records.principalAccount(original.loanId()),original.principalAccount(),"");
        var interest=original.interest().signum()>0?new Choice((UUID)raw.stream().filter(l->((BigDecimal)l[1]).signum()>0 && !l[0].equals(principal.id())).findFirst().orElseThrow()[0],original.interestAccount(),""):null;
        UUID id=UUID.randomUUID(),journal=UUID.randomUUID();var now=clock.now();records.journal(a,journal,id,f,hash,period,"REVERSAL",original.journalId(),now);
        records.ticket(a,id,"REVERSAL",f,hash,journal,original.id(),money,principal,interest,now);
        var receipt=repayments.reverseRecorded(original.loanId(),original.transactionId(),a,key,reason.strip(),date);
        loans.flush();
        records.lines(a,journal,raw);records.finish(id,receipt.id(),receipt.principal(),receipt.interest(),records.loan(a,original.loanId(),clock.today()).outstandingPrincipal());records.postJournal(a,journal,now);
        event(a,id,"LOAN_REPAYMENT_REVERSED",Map.of("original",originalId));return records.posting(a,id);
    }
    @Transactional(readOnly=true) public Posting posting(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.posting(a,id);}
    @Transactional(readOnly=true) public Page<Posting> history(AppUserPrincipal a,UUID id,int page){authorize(a,"VIEW");check(page>=0 && page<=10000,"filter");records.record(a,id,false);var rows=records.loanPosts(a,id,26,page*25);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public Page<Posting> movements(AppUserPrincipal a,UUID client,int page){authorize(a,"VIEW");check(page>=0 && page<=10000,"filter");check(records.ledgerClient(a,client).isPresent(),"client");var rows=records.clientPosts(a,client,26,page*25);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    public record StatementExport(String client,List<LoanRow> loans,List<Posting> movements) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public StatementExport exportStatement(AppUserPrincipal a,UUID client){
        authorize(a,"EXPORT");var c=records.ledgerClient(a,client).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
        var f=new Filter("",null,null,"","oldest",0,client);var loans=records.loans(a,f,clock.today(),501,0);var movements=records.clientPosts(a,client,501,0);
        check(loans.size()<=500 && movements.size()<=500,"reportLimit");return new StatementExport(c.label(),loans,movements);
    }
    @Transactional(readOnly=true) public Statement statement(AppUserPrincipal a,UUID client,int page){authorize(a,"VIEW");var c=records.ledgerClient(a,client).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));var f=new Filter("",null,null,"","newest",page,client);filter(f,false);var t=records.clientTotals(a,f,clock.today());var rows=records.loans(a,f,clock.today(),26,page*25);return new Statement(c.label(),c.label().split(" - ",2)[0],(BigDecimal)t.get("principal"),(BigDecimal)t.get("interest"),(BigDecimal)t.get("arrears"),(BigDecimal)t.get("future_interest"),((Number)t.get("loans")).longValue(),new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public List<Posting> report(AppUserPrincipal a,String kind,Filter f){authorize(a,"EXPORT");filter(f,true);kind(kind);var total=records.totals(a,kind,f);check(total.count()<=500,"reportLimit");return records.posts(a,kind,f,500,0);}
    @Transactional(readOnly=true) public Totals totals(AppUserPrincipal a,String kind,Filter f){authorize(a,"VIEW");filter(f,true);kind(kind);return records.totals(a,kind,f);}
    private LoanProductSetting product(AppUserPrincipal a,RecordForm f){var p=products.findByIdAndSaccoId(f.getProductId(),a.getSaccoId()).orElseThrow(()->new IllegalArgumentException("recording.error.product"));check(p.isAvailableForApplications() && p.getInterestRate()!=null && p.getInterestMethod()!=null,"product");
        check((p.getMinimumAmount()==null || f.getPrincipal().compareTo(p.getMinimumAmount())>=0) && (p.getMaximumAmount()==null || f.getPrincipal().compareTo(p.getMaximumAmount())<=0),"productLimits");
        check(f.getMonths()>=p.getMinimumRepaymentMonths() && (p.getMaxRepaymentMonths()==null || f.getMonths()<=p.getMaxRepaymentMonths()),"productLimits");
        check((p.getApplicationFee()==null || p.getApplicationFee().signum()==0) && (p.getProcessingFeeRate()==null || p.getProcessingFeeRate().signum()==0) && (p.getInsuranceRate()==null || p.getInsuranceRate().signum()==0),"productFees");return p;}
    private LoanAmortizationCalculator.Result calculate(RecordForm f,LoanProductSetting p){return LoanAmortizationCalculator.calculate(f.getPrincipal(),LoanAmortizationCalculator.numberOfPayments(f.getMonths(),p.getResolvedRepaymentFrequency()),p.getInterestRate(),p.getInterestMethod(),p.getResolvedRepaymentFrequency(),null,LoanAmortizationCalculator.flatInterest(f.getPrincipal(),p.getInterestRate(),f.getMonths()));}
    private List<ScheduleRow> schedule(LoanAmortizationCalculator.Result c,LocalDate first,RepaymentFrequency frequency){return c.rows().stream().map(r->new ScheduleRow(r.number(),frequency==RepaymentFrequency.WEEKLY?first.plusWeeks(r.number()-1L):first.plusMonths(r.number()-1L),r.principal(),r.interest(),r.amount())).toList();}
    private void validateRecord(RecordForm f){check(f!=null && f.getRequestKey()!=null && f.getClientId()!=null && f.getProductId()!=null,"request");money(f.getRequestedPrincipal());money(f.getPrincipal());check(f.getMonths()!=null && f.getMonths()>=1 && f.getMonths()<=600,"terms");
        check(f.getApplicationDate()!=null && !f.getApplicationDate().isAfter(clock.today()) && f.getFirstPaymentDate()!=null && f.getFirstPaymentDate().isAfter(f.getApplicationDate()),"date");text(f.getPurpose(),500,true);text(f.getBusiness(),1000,false);text(f.getGuarantors(),1000,false);text(f.getCollateral(),1000,false);text(f.getEvidence(),500,false);text(f.getNotes(),500,false);
        check(Set.of("DECLARED","DOCUMENTED","UNAVAILABLE").contains(f.getInformationSource()),"information");for(var amount:Arrays.asList(f.getIncome(),f.getExpenses(),f.getOtherDebt()))if(amount!=null)check(amount.signum()>=0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");}
    private void validatePost(PostForm f){check(f!=null && f.getRequestKey()!=null && f.getLoanId()!=null,"request");money(f.getAmount());check(f.getEffectiveDate()!=null && f.getEffectiveDate().getYear()>=1 && !f.getEffectiveDate().isAfter(clock.today()),"date");text(f.getReference(),100,true);text(f.getEvidence(),500,false);text(f.getNotes(),500,false);}
    static void filter(Filter f,boolean posting){check(f!=null && f.search()!=null && f.search().length()<=100 && f.page()>=0 && f.page()<=10000 && Set.of("newest","oldest","amount").contains(f.sort()),"filter");check(f.from()==null || f.through()==null || !f.from().isAfter(f.through()),"date");check((posting?Set.of("","POSTED","REVERSED","REVERSAL"):Set.of("","RECORDED","DISBURSED","PAR","DEFAULTED","PAID")).contains(f.status()),"filter");}
    private void kind(String kind){check(Set.of("DISBURSEMENT","PAYMENT","REVERSAL").contains(kind),"filter");}
    private static void money(BigDecimal amount){check(amount!=null && amount.signum()>0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");}
    private static void text(String text,int max,boolean required){check(text!=null && text.length()<=max && (!required || !text.isBlank()) && text.chars().noneMatch(c->c<32 && c!='\n' && c!='\r' && c!='\t'),"text");}
    private static void check(boolean value,String key){if(!value)throw new IllegalArgumentException("recording.error."+key);}
    private String write(Object value){return json.writeValueAsString(value);}
    private <T>T read(String value,Class<T> type){return json.readValue(value,type);}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(value).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private OffsetDateTime time(Object value){return value instanceof OffsetDateTime t?t:((java.sql.Timestamp)value).toInstant().atOffset(clock.now().getOffset());}
    private void event(AppUserPrincipal a,UUID id,String action,Map<String,Object> detail){audit.logEvent("LOAN_RECORDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"LOAN",id.toString(),a.getSaccoId(),a.getStationId(),detail);}
}
