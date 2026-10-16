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
import org.springframework.security.access.AccessDeniedException;
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
    private final LoanAttachmentService attachments;
    private final LoanDisbursementNumberService disbursementNumbers;
    private final LoanProductWorkflowService workflows;
    private final WorkflowRoutingService routing;
    private final LoanProductBoardReviewerRepository productReviewers;
    private final ManagerReviewRepository reviews;
    private final com.sacco.mvp.accounting.repayment.service.RepaymentPolicyService repaymentPolicies;
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    public void authorize(AppUserPrincipal actor,String action){accounting.requireActor(actor,"LOAN_RECORDING_"+action);}
    @Transactional(readOnly=true)
    public List<Choice> repaymentLoans(AppUserPrincipal a,UUID client,String search,int page){
        authorize(a,"VIEW");authorize(a,"POST");check(client!=null && search!=null && search.length()<=100 && page>=0 && page<=10000,"filter");
        return records.repaymentLoans(a,client,search,page,clock.today()).stream().map(l->new Choice(l.id(),l.number()+" - "+l.client(),l.status())).toList();
    }
    @Transactional(readOnly=true) public LoanRow receiptLoan(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.loan(a,id,clock.today());}
    @Transactional(readOnly=true) public Optional<UUID> receiptForPosting(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.receiptForPosting(a,id);}
    @Transactional(readOnly=true) public Choice receiptClient(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.ledgerClient(a,id).orElseThrow(()->new AccessDeniedException("Client unavailable"));}
    @Transactional(readOnly=true) public Choice receiptInterestAccount(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.account(a,id,"INTEREST",false);}
    @Transactional(readOnly=true) public Choice receiptPrincipalAccount(AppUserPrincipal a,UUID loan){authorize(a,"VIEW");records.loan(a,loan,clock.today());return records.account(a,records.principalAccount(loan),"PRINCIPAL",false);}
    @Transactional(readOnly=true) public Choice receiptMoneyAccount(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.account(a,id,"MONEY",false);}
    @Transactional(readOnly=true)
    public com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.Policy receiptDefaults(AppUserPrincipal a,com.sacco.mvp.accounting.dto.VoucherDtos.Form form){authorize(a,"POST");var policy=repaymentPolicies.effective(a,form.getEffectiveDate()==null?clock.today():form.getEffectiveDate(),false);if(policy.id()!=null)form.setInterestAccountId(policy.interestAccountId());return policy;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockReceiptLoans(AppUserPrincipal a,List<UUID> ids,UUID client){authorize(a,"VIEW");check(ids!=null && !ids.isEmpty() && ids.size()<=50 && new HashSet<>(ids).size()==ids.size(),"request");check(records.lockReceiptLoans(a,ids,client).size()==ids.size(),"client");}
    @Transactional(readOnly=true)
    public List<ReceiptLoan> receiptLoans(AppUserPrincipal a,List<UUID> ids,LocalDate date){authorize(a,"VIEW");check(ids!=null && ids.size()<=50 && date!=null,"request");if(ids.isEmpty())return List.of();var found=records.receiptLoans(a,ids,date);if(found.size()!=new HashSet<>(ids).size())throw new AccessDeniedException("Loan unavailable");return found;}
    @Transactional(readOnly=true)
    public List<AllocationPreview> receiptPreview(AppUserPrincipal a,com.sacco.mvp.accounting.dto.VoucherDtos.Form f){
        authorize(a,"POST");var contexts=receiptLoans(a,f.getRows().stream().map(r->r.getLoanId()).toList(),f.getEffectiveDate());
        records.account(a,f.getMoneyAccountId(),"MONEY",false);var result=new ArrayList<AllocationPreview>();var policy=repaymentPolicies.effective(a,f.getEffectiveDate(),false);
        for(var row:f.getRows()){var loan=contexts.stream().filter(l->l.id().equals(row.getLoanId())).findFirst().orElseThrow();
            check(loan.clientId().equals(f.getClientId()),"client");check(Set.of("DISBURSED","PAR","DEFAULTED").contains(loan.status()),"state");
            check(loan.disbursed()!=null && !f.getEffectiveDate().isBefore(loan.disbursed()) && (loan.lastPayment()==null || !f.getEffectiveDate().isBefore(loan.lastPayment())),"date");
            check(row.getAmount().compareTo(loan.duePrincipal().add(loan.dueInterest()))<=0,"advance");var split=com.sacco.mvp.accounting.repayment.service.RepaymentAllocationCalculator.allocate(row.getAmount(),loan.duePrincipal(),loan.dueInterest(),loan.outstandingPrincipal(),policy.allocationOrder());result.add(new AllocationPreview(split.principal(),split.interest(),split.principalBalance()));
        }
        if(result.stream().anyMatch(p->p.interest().signum()>0))records.account(a,policy.id()==null?f.getInterestAccountId():policy.interestAccountId(),"INTEREST",false);return result;
    }
    @Transactional(readOnly=true)
    public Page<LoanRow> list(AppUserPrincipal a,Filter f){authorize(a,"VIEW");filter(f,false);var rows=records.loans(a,f,clock.today(),26,f.page()*25);return new Page<>(rows.stream().limit(25).toList(),f.page(),rows.size()>25);}
    @Transactional(readOnly=true)
    public Page<Posting> register(AppUserPrincipal a,String kind,Filter f){authorize(a,"VIEW");filter(f,true);kind(kind);var rows=records.posts(a,kind,f,26,f.page()*25);return new Page<>(rows.stream().limit(25).toList(),f.page(),rows.size()>25);}
    @Transactional(readOnly=true)
    public List<Choice> choices(AppUserPrincipal a,String category,String search,int page){authorize(a,"VIEW");check(search!=null && search.length()<=100 && page>=0 && page<=10000,"filter");return switch(category){
        case "clients"->records.clients(a,search,page);case "ledger-clients"->records.ledgerClients(a,search,page);case "products"->records.products(a,search,page);
        case "filter-products"->records.filterProducts(a,search,page);
        case "money"->records.accounts(a,"MONEY",search,page);case "principal"->records.accounts(a,"PRINCIPAL",search,page);case "interest"->records.accounts(a,"INTEREST",search,page);
        case "disbursement-clients"->records.loans(a,new Filter(search,null,null,"RECORDED","newest",page,null),clock.today(),26,page*25).stream().map(l->new Choice(l.id(),l.clientNumber()+" - "+l.client()+" ("+l.number()+")",l.status())).toList();
        case "loans"->records.loans(a,new Filter(search,null,null,"","newest",page,null),clock.today(),26,page*25).stream().map(l->new Choice(l.id(),l.number()+" - "+l.client(),l.status())).toList();
        default->throw new IllegalArgumentException("recording.error.filter");};}
    @Transactional(readOnly=true)
    public Choice filterProduct(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.filterProduct(a,id).orElseThrow(()->new IllegalArgumentException("recording.error.filter"));}
    public record Quote(String product,BigDecimal rate,String method,String frequency,BigDecimal installment,BigDecimal interest,List<ScheduleRow> schedule,String tenureUnit,int tenureValue) {}
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
        var policy=repaymentPolicies.effective(a,f.getEffectiveDate(),false);var split=com.sacco.mvp.accounting.repayment.service.RepaymentAllocationCalculator.allocate(f.getAmount(),balance.duePrincipal(),balance.dueInterest(),balance.outstandingPrincipal(),policy.allocationOrder());if(split.interest().signum()>0)records.account(a,policy.id()==null?f.getInterestAccountId():policy.interestAccountId(),"INTEREST",false);return new AllocationPreview(split.principal(),split.interest(),split.principalBalance());
    }
    @Transactional(readOnly=true)
    public Quote interestQuote(AppUserPrincipal a,RecordForm f){
        authorize(a,"CREATE");money(f.getPrincipal());check(Set.of("CONFIGURED","CUSTOM").contains(f.getLoanMode()),"terms");
        if("CONFIGURED".equals(f.getLoanMode()) && f.getTenureValue()==null){
            check(f.getProductId()!=null,"product");var p=products.findByIdAndSaccoId(f.getProductId(),a.getSaccoId()).orElseThrow(()->new IllegalArgumentException("recording.error.product"));
            f.setTenureUnit(p.getResolvedTenureUnit());f.setTenureValue(p.getResolvedMinimumTenure());
        }
        if(f.getTenureValue()==null){check(f.getCustomMethod()!=InterestMethod.REDUCING_BALANCE && (!"ANNUAL".equals(f.getInterestBasis()) || "AMOUNT".equals(f.getInterestEntry())),"terms");f.setTenureUnit("MONTHS");f.setTenureValue(1);}
        f.setMonths(tenure(f).compatibilityMonths());var p=product(a,f);var result=calculate(f,p);
        return new Quote(p.getDisplayName(),p.getInterestRate(),p.getInterestMethod().name(),p.getResolvedRepaymentFrequency().name(),result.installment(),result.totalInterest(),result.rows().stream().map(r->new ScheduleRow(r.number(),null,r.principal(),r.interest(),r.amount())).toList(),tenure(f).unit(),tenure(f).value());
    }
    @Transactional(readOnly=true)
    public Quote preview(AppUserPrincipal a,RecordForm f){authorize(a,"CREATE");validateRecord(f);check(records.client(a,f.getClientId()).isPresent(),"client");var product=product(a,f);var result=calculate(f,product);
        return new Quote(product.getDisplayName(),product.getInterestRate(),product.getInterestMethod().name(),product.getResolvedRepaymentFrequency().name(),result.installment(),result.totalInterest(),List.of(),tenure(f).unit(),tenure(f).value());}
    @Transactional
    public UUID save(AppUserPrincipal a,RecordForm f){return save(a,f,List.of());}
    @Transactional
    public UUID save(AppUserPrincipal a,RecordForm f,List<org.springframework.web.multipart.MultipartFile> files){
        validateFiles(files);
        authorize(a,"CREATE");validateRecord(f);String hash=uploadHash(f,files);records.lockRequest(a,f.getRequestKey());var previous=records.recordRequest(a,f.getRequestKey(),hash);if(previous.isPresent())return previous.get();
        check(records.client(a,f.getClientId()).isPresent(),"client");var product=product(a,f);var calculation=calculate(f,product);var now=clock.now();
        var snapshot=new LinkedHashMap<String,Object>();snapshot.put("calculationVersion",LoanAmortizationCalculator.VERSION);snapshot.put("principalAmount",f.getPrincipal());
        snapshot.put("interestRate",product.getInterestRate());snapshot.put("interestMethod",product.getInterestMethod().name());snapshot.put("repaymentFrequency",product.getResolvedRepaymentFrequency().name());
        tenure(f).snapshot(snapshot);snapshot.put("interestEntry",f.getInterestEntry());snapshot.put("interestBasis",f.getInterestBasis());snapshot.put("interestPercent",f.getInterestPercent());snapshot.put("loanMode",f.getLoanMode());snapshot.put("tenorMonths",f.getMonths());snapshot.put("numberOfPayments",calculation.rows().size());snapshot.put("periodicRepaymentAmount",calculation.installment());
        snapshot.put("maximumInstallmentAmount",calculation.maximumInstallment());snapshot.put("interestAmount",calculation.totalInterest());snapshot.put("recordingSource","ACCOUNTANT_DIRECT");
        var selectedProduct=products.findByIdAndSaccoId(product.getId(),a.getSaccoId()).orElseThrow();
        var policy=recordedPolicy(a,selectedProduct,product.getDisplayName(),product.getGuarantorsRequired());
        long number=numbers.nextFor(a.getSaccoId());
        var loan=LoanApplication.builder().id(UUID.randomUUID()).applicationNumber(number).saccoId(a.getSaccoId()).stationId(a.getStationId())
            .applicantMemberId(f.getClientId()).loanType(product.getLoanType()).loanProductSettingId(product.getId()).amount(f.getPrincipal().setScale(2))
            .tenorMonths(f.getMonths()).status(LoanStatus.RECORDED).requiredGuarantors(product.getGuarantorsRequired()).formData(write(f))
            .policySnapshot(write(policy))
            .financialSnapshot(write(snapshot)).repaymentFrequency(product.getResolvedRepaymentFrequency()).firstRepaymentDate(f.getFirstPaymentDate())
            .installmentAmount(calculation.installment()).createdAt(now).updatedAt(now).build();
        loan.setAttachmentsJson(attachments.store(loan.getId(),files,null));
        loan=loans.saveAndFlush(loan);UUID id=loan.getId();records.insertRecord(a,id,f,hash,product.getDisplayName(),write(f),now);
        routing.moveToFirstReviewStage(loan,a.getMemberId());loans.saveAndFlush(loan);
        event(a,id,"LOAN_APPLICATION_RECORDED",Map.of("principal",f.getPrincipal(),"status",loan.getStatus().name()));return id;
    }
    @Transactional(readOnly=true)
    public Detail detail(AppUserPrincipal a,UUID id){
        authorize(a,"VIEW");var record=records.record(a,id,false);var loan=loans.findById(id).orElseThrow();var f=read(record.get("metadata").toString(),RecordForm.class);
        var snapshot=schedules.parseSummary(loan.getFinancialSnapshot());var calc=LoanAmortizationCalculator.estimate(loan.getAmount(),loan.getTenorMonths(),snapshot);
        var schedule=loan.getFirstRepaymentDate()==null
            ?calc.rows().stream().map(r->new ScheduleRow(r.number(),null,r.principal(),r.interest(),r.amount())).toList()
            :datedSchedule(calc,loan);
        return new Detail(records.loan(a,id,clock.today()),record.get("sacco_name").toString(),record.get("branch_name").toString(),record.get("actor_name").toString(),time(record.get("recorded_at")),
            f.getPurpose(),f.getBusiness(),f.getIncome(),f.getExpenses(),f.getOtherDebt(),f.getInformationSource(),f.getGuarantors(),f.getCollateral(),f.getEvidence(),f.getNotes(),
            new BigDecimal(snapshot.get("interestRate").toString()),snapshot.get("interestMethod").toString(),snapshot.get("repaymentFrequency").toString(),loan.getTenorMonths(),loan.getFirstRepaymentDate(),calc.installment(),calc.totalInterest(),schedule,tenure(f).unit(),tenure(f).value(),f.getInterestBasis(),f.getLoanMode(),f.getInterestEntry());
    }
    @Transactional
    public Posting disburse(AppUserPrincipal a,PostForm f){return disburse(a,f,List.of());}
    @Transactional
    public Posting disburse(AppUserPrincipal a,PostForm f,List<org.springframework.web.multipart.MultipartFile> files){
        validateFiles(files);
        accounting.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");validatePost(f);String hash=uploadHash(f,files);records.lockRequest(a,f.getRequestKey());var prior=records.postRequest(a,f.getRequestKey(),hash);if(prior.isPresent())return prior.get();
        var record=records.record(a,f.getLoanId(),true);var loan=loans.findForDisbursement(f.getLoanId()).orElseThrow();check(loan.getStatus()==LoanStatus.READY_FOR_DISBURSEMENT,"state");
        check(allowedTeller(loan,a.getMemberId()),"customTeller");
        var policy=schedules.parseSummary(loan.getPolicySnapshot());
        if(Boolean.TRUE.equals(policy.get("disbursementProofRequired")))check(files.stream().anyMatch(file->!file.isEmpty()),"attachments");
        check(f.getAmount().compareTo(loan.getAmount())==0,"disbursementAmount");LocalDate first=f.getFirstPaymentDate()!=null?f.getFirstPaymentDate():loan.getFirstRepaymentDate();
        check(first!=null,"date");
        check(!f.getEffectiveDate().isBefore(((java.sql.Date)record.get("application_date")).toLocalDate()) && first.isAfter(f.getEffectiveDate()),"date");
        UUID period=periods.period(a.getSaccoId(),f.getEffectiveDate(),a.getMemberId(),clock.now());
        var money=records.account(a,f.getMoneyAccountId(),"MONEY");var principal=records.account(a,f.getPrincipalAccountId(),"PRINCIPAL");
        check(f.getInterestAccountId()==null,"account");UUID id=UUID.randomUUID(),journal=UUID.randomUUID();var now=clock.now();
        records.journal(a,journal,id,f,hash,period,"DISBURSEMENT",null,now);records.ticket(a,id,"DISBURSEMENT",f,hash,journal,null,money,principal,null,now);
        var schedule=schedules.buildSchedule(loan,f.getEffectiveDate(),first,loan.getRepaymentFrequency(),null,f.getReference(),f.getNotes());
        loan.setFirstRepaymentDate(first);
        loan.setStatus(LoanStatus.DISBURSED);loan.setDisbursementDate(f.getEffectiveDate());loan.setDisbursementReference(f.getReference().strip());loan.setDisbursementNotes(f.getNotes().strip());
        loan.setAttachmentsJson(attachments.store(loan.getId(),files,loan.getAttachmentsJson(),LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF));
        disbursementNumbers.assign(loan,a.getMemberId(),f.getEffectiveDate());
        loan.setRepaymentScheduleJson(schedule.scheduleJson());loan.setFinalDueDate(schedule.finalDueDate());loan.setUpdatedAt(now);loans.saveAndFlush(loan);repayments.openAtDisbursement(loan);
        records.lines(a,journal,List.of(new Object[]{principal.id(),f.getAmount(),ZERO},new Object[]{money.id(),ZERO,f.getAmount()}));
        records.finish(id,null,f.getAmount(),ZERO,f.getAmount());records.postJournal(a,journal,now);
        reviews.save(ManagerReview.builder().id(UUID.randomUUID()).loanApplicationId(loan.getId()).managerMemberId(a.getMemberId()).reviewStage(ApprovalWorkflowStage.DISBURSEMENT_OFFICER).decision(ManagerDecision.ACCEPT).reasons(f.getNotes()).createdAt(now).build());
        routing.announceRecordedDisbursement(loan,a.getMemberId());
        event(a,id,"LOAN_DISBURSEMENT_POSTED",Map.of("loan",loan.getId(),"amount",f.getAmount()));return records.posting(a,id);
    }
    @Transactional
    public Posting repay(AppUserPrincipal a,PostForm f){
        authorize(a,"POST");validatePost(f);var policy=repaymentPolicies.effective(a,f.getEffectiveDate(),false);if(policy.id()!=null)f.setInterestAccountId(policy.interestAccountId());String hash=hash(f);records.lockRequest(a,f.getRequestKey());var prior=records.postRequest(a,f.getRequestKey(),hash);if(prior.isPresent())return prior.get();
        records.record(a,f.getLoanId(),true);UUID period=periods.period(a.getSaccoId(),f.getEffectiveDate(),a.getMemberId(),clock.now());
        UUID principalId=records.principalAccount(f.getLoanId());check(f.getPrincipalAccountId()==null || principalId.equals(f.getPrincipalAccountId()),"account");
        var principal=records.account(a,principalId,"PRINCIPAL");var money=records.account(a,f.getMoneyAccountId(),"MONEY");
        var interest=f.getInterestAccountId()==null?null:records.account(a,f.getInterestAccountId(),"INTEREST");
        UUID id=UUID.randomUUID(),journal=UUID.randomUUID();var now=clock.now();records.journal(a,journal,id,f,hash,period,"PAYMENT",null,now);
        records.ticket(a,id,"PAYMENT",f,hash,journal,null,money,principal,interest,now);
        var receipt=repayments.postRecorded(f.getLoanId(),a,new LoanRepaymentLedgerService.PaymentCommand(f.getAmount(),f.getEffectiveDate(),LoanRepaymentTransaction.Channel.valueOf(money.purpose()),f.getReference().strip(),f.getRequestKey(),policy.id(),policy.allocationOrder()==com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.AllocationOrder.PRINCIPAL_FIRST));
        loans.flush();
        check(receipt.interest().signum()==0 || interest!=null,"interestAccount");var lines=new ArrayList<Object[]>();lines.add(new Object[]{money.id(),f.getAmount(),ZERO});
        if(receipt.principal().signum()>0)lines.add(new Object[]{principal.id(),ZERO,receipt.principal()});if(receipt.interest().signum()>0)lines.add(new Object[]{interest.id(),ZERO,receipt.interest()});
        records.lines(a,journal,lines);records.finish(id,receipt.id(),receipt.principal(),receipt.interest(),records.loan(a,f.getLoanId(),clock.today()).outstandingPrincipal());records.postJournal(a,journal,now);
        event(a,id,"LOAN_REPAYMENT_POSTED",Map.of("loan",f.getLoanId(),"amount",f.getAmount()));return records.posting(a,id);
    }
    @Transactional
    public Posting reverse(AppUserPrincipal a,UUID originalId,UUID key,LocalDate date,String reason){
        check(records.receiptForPosting(a,originalId).isEmpty(),"receiptCorrection");
        return reverseInternal(a,originalId,key,date,reason);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Posting reverseReceiptRow(AppUserPrincipal a,UUID receiptId,UUID originalId,UUID key,LocalDate date,String reason){
        check(records.receiptForPosting(a,originalId).filter(receiptId::equals).isPresent(),"receiptCorrection");
        return reverseInternal(a,originalId,key,date,reason);
    }
    private Posting reverseInternal(AppUserPrincipal a,UUID originalId,UUID key,LocalDate date,String reason){
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
    @Transactional(readOnly=true) public Optional<Page<ScheduleRow>> actualRepaymentSchedule(AppUserPrincipal actor,UUID loanId,int page){
        authorize(actor,"VIEW");check(page>=0 && page<24,"filter");records.record(actor,loanId,false);
        if(!repayments.hasLedger(loanId))return Optional.empty();
        var stored=repayments.schedule(loanId,actor,page);
        return Optional.of(new Page<>(stored.stream().map(r->new ScheduleRow(r.number(),r.dueDate(),r.principal(),r.interest(),r.principal().add(r.interest()))).toList(),page,stored.hasNext()));
    }
    @Transactional(readOnly=true) public Page<Posting> history(AppUserPrincipal a,UUID id,int page){authorize(a,"VIEW");check(page>=0 && page<=10000,"filter");records.record(a,id,false);var rows=records.loanPosts(a,id,26,page*25);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public Page<Posting> movements(AppUserPrincipal a,UUID client,int page){authorize(a,"VIEW");check(page>=0 && page<=10000,"filter");check(records.ledgerClient(a,client).isPresent(),"client");var rows=records.clientPosts(a,client,26,page*25);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    public record LoanStatementView(StatementExport source,Posting opening,int page,boolean hasNext) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public LoanStatementView loanStatement(AppUserPrincipal a,UUID client,UUID loan,int page,boolean export){
        authorize(a,export?"EXPORT":"VIEW");check(page>=0 && page<=10000,"filter");
        var owner=records.ledgerClient(a,client).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
        var record=records.loan(a,loan,clock.today());
        if(!record.clientId().equals(client))throw new org.springframework.security.access.AccessDeniedException("Loan outside client statement");
        var rows=records.loanPosts(a,loan,export?501:26,export?0:page*25);if(export)check(rows.size()<=500,"reportLimit");
        return new LoanStatementView(new StatementExport(owner.label(),List.of(record),export?rows:rows.stream().limit(25).toList(),records.statementTerms(a,client,loan)),records.loanOpening(a,loan).orElse(null),page,!export && rows.size()>25);
    }
    public record StatementExport(String client,List<LoanRow> loans,List<Posting> movements,List<StatementTerms> terms) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public StatementExport exportStatement(AppUserPrincipal a,UUID client){
        authorize(a,"EXPORT");var c=records.ledgerClient(a,client).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
        var f=new Filter("",null,null,"","oldest",0,client);var loans=records.loans(a,f,clock.today(),501,0);var movements=records.clientPosts(a,client,501,0);
        check(loans.size()<=500 && movements.size()<=500,"reportLimit");return new StatementExport(c.label(),loans,movements,records.statementTerms(a,client));
    }
    @Transactional(readOnly=true) public Statement statement(AppUserPrincipal a,UUID client,int page){authorize(a,"VIEW");var c=records.ledgerClient(a,client).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));var f=new Filter("",null,null,"","newest",page,client);filter(f,false);var t=records.clientTotals(a,f,clock.today());var rows=records.loans(a,f,clock.today(),26,page*25);return new Statement(c.label(),c.label().split(" - ",2)[0],(BigDecimal)t.get("principal"),(BigDecimal)t.get("interest"),(BigDecimal)t.get("arrears"),(BigDecimal)t.get("future_interest"),((Number)t.get("loans")).longValue(),new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public List<Posting> report(AppUserPrincipal a,String kind,Filter f){authorize(a,"EXPORT");filter(f,true);kind(kind);var total=records.totals(a,kind,f);check(total.count()<=500,"reportLimit");return records.posts(a,kind,f,500,0);}
    @Transactional(readOnly=true) public Totals totals(AppUserPrincipal a,String kind,Filter f){authorize(a,"VIEW");filter(f,true);kind(kind);return records.totals(a,kind,f);}
    private LoanProductSetting product(AppUserPrincipal a,RecordForm f){
        if ("CUSTOM".equals(f.getLoanMode())) {
            var configured=products.findBySaccoIdAndCustomRecordingConfigurationTrue(a.getSaccoId()).orElseThrow(()->new IllegalArgumentException("recording.error.customConfiguration"));
            check(configured.getStatus()==LoanProductStatus.ACTIVE,"customConfiguration");
            check(Set.of("PERCENTAGE","AMOUNT").contains(f.getInterestEntry()) && Set.of("WHOLE_LOAN","ANNUAL").contains(f.getInterestBasis()) && f.getCustomFrequency()!=null && f.getCustomMethod()!=null,"terms");
            check(f.getCustomMethod()!=InterestMethod.REDUCING_BALANCE || "PERCENTAGE".equals(f.getInterestEntry()),"terms");
            BigDecimal rate=BigDecimal.ZERO;
            if("AMOUNT".equals(f.getInterestEntry()))nonnegativeMoney(f.getInterestAmount());
            else {check(f.getInterestPercent()!=null && f.getInterestPercent().signum()>=0 && f.getInterestPercent().compareTo(new BigDecimal("10000"))<=0 && f.getInterestPercent().scale()<=4,"terms");rate=f.getInterestPercent().movePointLeft(2);}
            return LoanProductSetting.builder().id(configured.getId()).saccoId(a.getSaccoId()).loanType(LoanType.CUSTOMIZED_LOAN).productName(configured.getDisplayName()).interestRate(rate).interestMethod(f.getCustomMethod()).repaymentFrequency(f.getCustomFrequency()).guarantorsRequired(0).build();
        }
        var p=products.findByIdAndSaccoId(f.getProductId(),a.getSaccoId()).orElseThrow(()->new IllegalArgumentException("recording.error.product"));check(p.isAvailableForApplications() && p.getInterestRate()!=null && p.getInterestMethod()!=null,"product");
        check((p.getMinimumAmount()==null || f.getPrincipal().compareTo(p.getMinimumAmount())>=0) && (p.getMaximumAmount()==null || f.getPrincipal().compareTo(p.getMaximumAmount())<=0),"productLimits");
        tenure(f).validate(p);
        check((p.getApplicationFee()==null || p.getApplicationFee().signum()==0) && (p.getProcessingFeeRate()==null || p.getProcessingFeeRate().signum()==0) && (p.getInsuranceRate()==null || p.getInsuranceRate().signum()==0),"productFees");return p;}
    private LoanTenure tenure(RecordForm f){Integer value=f.getTenureValue()==null?f.getMonths():f.getTenureValue();check(value!=null,"terms");return new LoanTenure(f.getTenureUnit(),value);}
    private LoanAmortizationCalculator.Result calculate(RecordForm f,LoanProductSetting p){
        var term=tenure(f);BigDecimal flat=term.flatInterest(f.getPrincipal(),p.getInterestRate());
        if("CUSTOM".equals(f.getLoanMode()))flat="AMOUNT".equals(f.getInterestEntry())?f.getInterestAmount():"WHOLE_LOAN".equals(f.getInterestBasis())?f.getPrincipal().multiply(p.getInterestRate()).setScale(2,RoundingMode.HALF_UP):flat;
        return term.calculate(f.getPrincipal(),p.getInterestRate(),p.getInterestMethod(),p.getResolvedRepaymentFrequency(),null,flat,
            "CUSTOM".equals(f.getLoanMode()) && p.getInterestMethod()==InterestMethod.REDUCING_BALANCE && "WHOLE_LOAN".equals(f.getInterestBasis()));
    }
    private List<ScheduleRow> datedSchedule(LoanAmortizationCalculator.Result c,LoanApplication loan){var t=LoanTenure.fromSnapshot(schedules.parseSummary(loan.getFinancialSnapshot()),loan.getTenorMonths());return c.rows().stream().map(r->new ScheduleRow(r.number(),t.dueDate(loan.getDisbursementDate(),loan.getFirstRepaymentDate(),loan.getRepaymentFrequency(),r.number(),c.rows().size()),r.principal(),r.interest(),r.amount())).toList();}
    private static void nonnegativeMoney(BigDecimal amount){check(amount!=null && amount.signum()>=0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");}
    private static void validateFiles(List<org.springframework.web.multipart.MultipartFile> files){check(files!=null && files.size()<=10 && files.stream().allMatch(f->f.getSize()<=25L*1024*1024),"attachments");}
    @Transactional(readOnly=true) public List<Map<String,Object>> attachments(AppUserPrincipal a,UUID id){authorize(a,"VIEW");records.record(a,id,false);return attachments.parse(loans.findById(id).orElseThrow().getAttachmentsJson());}
    @Transactional(readOnly=true) public LoanAttachmentService.AttachmentResource attachment(AppUserPrincipal a,UUID id,String attachmentId){authorize(a,"VIEW");records.record(a,id,false);return attachments.load(id,attachmentId,loans.findById(id).orElseThrow().getAttachmentsJson());}
    private void validateRecord(RecordForm f){check(f!=null && f.getRequestKey()!=null && f.getClientId()!=null && ("CUSTOM".equals(f.getLoanMode()) || f.getProductId()!=null) && Set.of("CONFIGURED","CUSTOM").contains(f.getLoanMode()),"request");money(f.getPrincipal());f.setRequestedPrincipal(f.getPrincipal());f.setMonths(tenure(f).compatibilityMonths());check(f.getMonths()!=null && f.getMonths()>=1 && f.getMonths()<=600,"terms");
        if("CONFIGURED".equals(f.getLoanMode())){f.setInterestEntry("PERCENTAGE");f.setInterestBasis("ANNUAL");f.setInterestPercent(null);f.setInterestAmount(null);}
        check(f.getApplicationDate()!=null && !f.getApplicationDate().isAfter(clock.today()),"date");text(f.getPurpose(),500,true);text(f.getBusiness(),1000,false);text(f.getGuarantors(),1000,false);text(f.getCollateral(),1000,false);text(f.getEvidence(),500,false);text(f.getNotes(),500,false);
        check(Set.of("DECLARED","DOCUMENTED","UNAVAILABLE").contains(f.getInformationSource()),"information");for(var amount:Arrays.asList(f.getIncome(),f.getExpenses(),f.getOtherDebt()))if(amount!=null)check(amount.signum()>=0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");}
    private void validatePost(PostForm f){check(f!=null && f.getRequestKey()!=null && f.getLoanId()!=null,"request");money(f.getAmount());check(f.getEffectiveDate()!=null && f.getEffectiveDate().getYear()>=1 && !f.getEffectiveDate().isAfter(clock.today()),"date");text(f.getReference(),100,true);text(f.getEvidence(),500,false);text(f.getNotes(),500,false);}
    static void filter(Filter f,boolean posting){check(f!=null && f.search()!=null && f.search().length()<=100 && f.page()>=0 && f.page()<=10000 && com.sacco.mvp.accounting.dto.RegisterSort.valid(f.sort(),posting?Set.of("number","product","client","date","amount","reference","status"):Set.of("number","product","client","date","amount","balance","status")),"filter");check(f.from()==null || f.through()==null || !f.from().isAfter(f.through()),"date");check((posting?Set.of("","POSTED","REVERSED","REVERSAL"):Set.of("","RECORDED","DISBURSED","PAR","DEFAULTED","PAID")).contains(f.status()),"filter");}
    private void kind(String kind){check(Set.of("DISBURSEMENT","PAYMENT","REVERSAL").contains(kind),"filter");}
    private static void money(BigDecimal amount){check(amount!=null && amount.signum()>0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");}
    private static void text(String text,int max,boolean required){check(text!=null && text.length()<=max && (!required || !text.isBlank()) && text.chars().noneMatch(c->c<32 && c!='\n' && c!='\r' && c!='\t'),"text");}
    private static void check(boolean value,String key){if(!value)throw new IllegalArgumentException("recording.error."+key);}
    private String write(Object value){return json.writeValueAsString(value);}
    private <T>T read(String value,Class<T> type){return json.readValue(value,type);}
    private String uploadHash(Object form,List<org.springframework.web.multipart.MultipartFile> files){
        try {var evidence=new ArrayList<String>();for(var file:files)if(!file.isEmpty())evidence.add(Objects.toString(file.getOriginalFilename(),"")+":"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes())));return hash(Map.of("form",form,"attachments",evidence));}
        catch(java.io.IOException|java.security.NoSuchAlgorithmException ex){throw new IllegalArgumentException("recording.error.attachments",ex);}
    }
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(value).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private OffsetDateTime time(Object value){return value instanceof OffsetDateTime t?t:((java.sql.Timestamp)value).toInstant().atOffset(clock.now().getOffset());}
    private void event(AppUserPrincipal a,UUID id,String action,Map<String,Object> detail){audit.logEvent("LOAN_RECORDING",id,action,a.getMemberId(),AuditEventStatus.SUCCESS,action,"LOAN",id.toString(),a.getSaccoId(),a.getStationId(),detail);}

    @Transactional(readOnly=true)
    public boolean recordedForStaff(AppUserPrincipal actor,UUID loan) {
        accounting.requireActor(actor,actor.getClaims().contains("DISBURSEMENT_QUEUE_DISBURSE")?"DISBURSEMENT_QUEUE_DISBURSE":"DISBURSEMENT_QUEUE_VIEW");
        return records.recorded(actor,loan);
    }
    @Transactional(readOnly=true)
    public boolean allowedTeller(LoanApplication loan,UUID actor) {
        var policy=schedules.parseSummary(loan.getPolicySnapshot());
        var ids=policy.get("disbursementOfficerIds");
        if("CUSTOM".equals(schedules.parseSummary(loan.getFinancialSnapshot()).get("loanMode")))return ids instanceof List<?> assigned && !assigned.isEmpty() && assigned.contains(actor.toString());
        return !(ids instanceof List<?> assigned) || assigned.contains(actor.toString());
    }
    public boolean disbursementProofRequired(LoanApplication loan) {
        return Boolean.TRUE.equals(schedules.parseSummary(loan.getPolicySnapshot()).get("disbursementProofRequired"));
    }
    @Transactional(readOnly=true)
    public List<Choice> postingAccounts(AppUserPrincipal actor,UUID loan,String category,String search,int page) {
        accounting.requireActor(actor,"DISBURSEMENT_QUEUE_DISBURSE");records.record(actor,loan,false);
        check(allowedTeller(loans.findById(loan).orElseThrow(),actor.getMemberId()),"customTeller");
        check(Set.of("money","principal").contains(category) && search!=null && search.length()<=100 && page>=0 && page<=10000,"filter");
        return records.accounts(actor,category.toUpperCase(Locale.ROOT),search,page);
    }
    @Transactional(readOnly=true)
    public Choice postingAccount(AppUserPrincipal actor,UUID loan,UUID id,String category) {
        accounting.requireActor(actor,"DISBURSEMENT_QUEUE_DISBURSE");records.record(actor,loan,false);
        check(allowedTeller(loans.findById(loan).orElseThrow(),actor.getMemberId()),"customTeller");
        check(Set.of("MONEY","PRINCIPAL").contains(category),"filter");
        return records.account(actor,id,category,false);
    }

    private Map<String,Object> recordedPolicy(AppUserPrincipal a,LoanProductSetting selectedProduct,String label,Integer guarantors) {
        var policy=new LinkedHashMap<String,Object>(workflows.snapshotData(workflows.resolveForProduct(a.getSaccoId(),selectedProduct)));
        policy.put("source","ACCOUNTANT_DIRECT");policy.put("product",label);policy.put("guarantorsRequired",guarantors);policy.put("disbursementProofRequired",selectedProduct.isDisbursementProofRequired());policy.put("recordingWorkflowVersion","STAFF_REVIEW_V1");
        if(selectedProduct.isCustomRecordingConfiguration()) {
            var tellers=productReviewers.findByLoanProductSettingIdAndReviewStageOrderByCreatedAtAsc(selectedProduct.getId(),ApprovalWorkflowStage.DISBURSEMENT_OFFICER).stream().map(LoanProductBoardReviewer::getBoardMemberId).toList();
            check(!tellers.isEmpty() && records.availableTeller(a,tellers),"customTellers");policy.put("disbursementOfficerIds",tellers.stream().map(UUID::toString).toList());
        }
        for(var stage:List.of(ApprovalWorkflowStage.CHAIRPERSON,ApprovalWorkflowStage.BOARD,ApprovalWorkflowStage.CREDIT_COMMITTEE))
            policy.put("recordingReviewers_"+stage.name(),productReviewers.findByLoanProductSettingIdAndReviewStageOrderByCreatedAtAsc(selectedProduct.getId(),stage).stream().map(r->r.getBoardMemberId().toString()).toList());
        return policy;
    }
    @Transactional
    public void submitRecordedApplication(AppUserPrincipal actor,UUID id) {
        authorize(actor,"CREATE");var record=records.record(actor,id,true);var application=loans.findForDisbursement(id).orElseThrow();
        if(application.getStatus()!=LoanStatus.RECORDED){check("STAFF_REVIEW_V1".equals(schedules.parseSummary(application.getPolicySnapshot()).get("recordingWorkflowVersion")),"state");return;}
        var form=read(record.get("metadata").toString(),RecordForm.class);
        var product="CUSTOM".equals(form.getLoanMode())?products.findBySaccoIdAndCustomRecordingConfigurationTrue(actor.getSaccoId()).orElseThrow(()->new IllegalArgumentException("recording.error.customConfiguration")):
            products.findByIdAndSaccoId(application.getLoanProductSettingId(),actor.getSaccoId()).orElseThrow(()->new IllegalArgumentException("recording.error.product"));
        check(product.getStatus()==LoanProductStatus.ACTIVE,"product");
        application.setLoanProductSettingId(product.getId());application.setPolicySnapshot(write(recordedPolicy(actor,product,record.get("product_name").toString(),application.getRequiredGuarantors())));
        routing.moveToFirstReviewStage(application,actor.getMemberId());loans.saveAndFlush(application);
        event(actor,id,"RECORDED_APPLICATION_SUBMITTED",Map.of("status",application.getStatus().name()));
    }
}
