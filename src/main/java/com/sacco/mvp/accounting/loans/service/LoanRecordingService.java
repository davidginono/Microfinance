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
    private final com.sacco.mvp.accounting.charges.service.LoanChargeService charges;
    private final LoanProductAccountService productAccounts;
    private final com.sacco.mvp.grouplending.service.GroupOrganizationFundingService organizationFunding;
    private final com.sacco.mvp.grouplending.service.GroupSharedLoanService sharedLoans;
    private final com.sacco.mvp.grouplending.service.SharedCreditExposureService sharedCredit;
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    public void authorize(AppUserPrincipal actor,String action){accounting.requireActor(actor,"LOAN_RECORDING_"+action);}
    @Transactional(readOnly=true)
    public List<Choice> repaymentLoans(AppUserPrincipal a,UUID client,String search,int page){
        authorize(a,"VIEW");authorize(a,"POST");check(client!=null && search!=null && search.length()<=100 && page>=0 && page<=10000,"filter");
        return records.repaymentLoans(a,client,search,page,clock.today()).stream().map(l->new Choice(l.id(),l.number()+" - "+l.product(),l.status())).toList();
    }
    @Transactional(readOnly=true) public LoanRow receiptLoan(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.loan(a,id,clock.today());}
    @Transactional(readOnly=true)
    public Optional<LoanProductAccountService.Accounts> linkedAccounts(AppUserPrincipal a,UUID loan){authorize(a,"VIEW");records.record(a,loan,false);return productAccounts.find(a,loan);}
    @Transactional
    public void linkExistingProductAccounts(AppUserPrincipal a,UUID loan){
        authorize(a,"POST");records.record(a,loan,true);var application=loans.findById(loan).orElseThrow();
        check(Set.of(LoanStatus.DISBURSED,LoanStatus.PAR,LoanStatus.DEFAULTED).contains(application.getStatus()),"state");
        productAccounts.bind(a,loan,application.getLoanProductSettingId(),records.principalAccount(loan),"EXISTING_LOAN");
    }
    @Transactional(readOnly=true) public Optional<UUID> receiptForPosting(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.receiptForPosting(a,id);}
    @Transactional(readOnly=true) public Choice receiptClient(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.ledgerClient(a,id).orElseThrow(()->new AccessDeniedException("Client unavailable"));}
    @Transactional(readOnly=true) public Choice receiptInterestAccount(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.account(a,id,"INTEREST",false);}
    @Transactional(readOnly=true) public Choice receiptPrincipalAccount(AppUserPrincipal a,UUID loan){authorize(a,"VIEW");records.loan(a,loan,clock.today());return records.account(a,records.principalAccount(loan),"PRINCIPAL",false);}
    @Transactional(readOnly=true) public Choice receiptMoneyAccount(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.account(a,id,"MONEY",false);}
    @Transactional(readOnly=true)
    public com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.Policy receiptDefaults(AppUserPrincipal a,com.sacco.mvp.accounting.dto.VoucherDtos.Form form){authorize(a,"POST");var policy=repaymentPolicies.effective(a,form.getEffectiveDate()==null?clock.today():form.getEffectiveDate(),false);form.setInterestAccountId(null);return policy;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockReceiptLoans(AppUserPrincipal a,List<UUID> ids,UUID client){authorize(a,"VIEW");check(ids!=null && !ids.isEmpty() && ids.size()<=50 && new HashSet<>(ids).size()==ids.size(),"request");check(records.lockReceiptLoans(a,ids,client).size()==ids.size(),"client");}
    @Transactional(readOnly=true)
    public List<ReceiptLoan> receiptLoans(AppUserPrincipal a,List<UUID> ids,LocalDate date){authorize(a,"VIEW");check(ids!=null && ids.size()<=50 && date!=null,"request");if(ids.isEmpty())return List.of();var found=records.receiptLoans(a,ids,date);if(found.size()!=new HashSet<>(ids).size())throw new AccessDeniedException("Loan unavailable");return found;}
    @Transactional(readOnly=true)
    public List<AllocationPreview> receiptPreview(AppUserPrincipal a,com.sacco.mvp.accounting.dto.VoucherDtos.Form f){
        return previewCollections(a,f.getRows().stream().map(r->new CollectionAmount(r.getLoanId(),f.getClientId(),r.getAmount())).toList(),f.getEffectiveDate(),f.getMoneyAccountId());
    }
    /** Each request supplies its expected borrower; group orchestration verifies cycle ownership separately. */
    @Transactional(readOnly=true)
    public List<AllocationPreview> previewCollections(AppUserPrincipal a,List<CollectionAmount> amounts,LocalDate date,UUID moneyAccount){
        authorize(a,"POST");check(amounts!=null && !amounts.isEmpty() && amounts.size()<=50 && date!=null && !date.isAfter(clock.today()),"request");
        check(amounts.stream().allMatch(r->r!=null && r.loanId()!=null && r.clientId()!=null) && amounts.stream().map(CollectionAmount::loanId).distinct().count()==amounts.size(),"request");
        amounts.forEach(r->money(r.amount()));
        var contexts=receiptLoans(a,amounts.stream().map(CollectionAmount::loanId).toList(),date).stream().collect(java.util.stream.Collectors.toMap(ReceiptLoan::id,java.util.function.Function.identity()));
        records.account(a,moneyAccount,"MONEY",false);var result=new ArrayList<AllocationPreview>();repaymentPolicies.effective(a,date,false);
        for(var row:amounts){var loan=contexts.get(row.loanId());
            check(loan.principalAccountId()!=null && loan.interestAccountId()!=null,"loanAccountsMissing");check(loan.principalAccount()!=null && loan.interestAccount()!=null,"productAccountsInvalid");
            check(loan.clientId()!=null && loan.clientId().equals(row.clientId()),"client");check(Set.of("DISBURSED","PAR","DEFAULTED").contains(loan.status()),"state");
            check(loan.disbursed()!=null && !date.isBefore(loan.disbursed()) && (loan.lastPayment()==null || !date.isBefore(loan.lastPayment())),"date");
            result.add(chargeAllocation(row.amount(),loan.dueInterest(),loan.outstandingPrincipal(),loan.charges()));
        }
        return result;
    }
    @Transactional(readOnly=true)
    public List<com.sacco.mvp.accounting.dto.VoucherDtos.LoanReceiptRow> sharedReceiptRows(AppUserPrincipal a,List<com.sacco.mvp.accounting.dto.VoucherDtos.LoanReceiptRow> rows,boolean preview,String payer){authorize(a,preview?"POST":"VIEW");return sharedLoans.receiptRows(a,rows,preview,payer);}
    @Transactional(readOnly=true)
    public LoanRepaymentLedgerService.PaymentPreview stagedReceiptPreview(AppUserPrincipal a,UUID loan,LocalDate date,BigDecimal amount,List<LoanRepaymentLedgerService.StagedPayment> prior){
        authorize(a,"POST");records.record(a,loan,false);return repayments.previewPayment(loan,a,date,amount,prior);
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
        case "loans"->records.loans(a,new Filter(search,null,null,"","newest",page,null),clock.today(),26,page*25).stream().map(l->new Choice(l.id(),l.number()+" - "+l.product(),l.status())).toList();
        default->throw new IllegalArgumentException("recording.error.filter");};}
    @Transactional(readOnly=true)
    public Choice filterProduct(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.filterProduct(a,id).orElseThrow(()->new IllegalArgumentException("recording.error.filter"));}
    @Transactional(readOnly=true)
    public Choice recordProduct(AppUserPrincipal a,UUID id){authorize(a,"CREATE");return records.filterProduct(a,id).orElseThrow(()->new IllegalArgumentException("recording.error.product"));}
    @Transactional(readOnly=true)
    public TopUpQuote topUpQuote(AppUserPrincipal a,UUID source,LocalDate date,UUID excluded){
        authorize(a,"VIEW");check(date!=null && !date.isAfter(clock.today()),"date");check(records.topUpEnabled(a.getSaccoId()),"topUpDisabled");
        var quote=records.topUpQuote(a,source,date);check(quote.principal().signum()>0,"topUpUnavailable");check(!records.pendingTopUp(source,excluded),"topUpPending");var b=charges.balance(a,source,LocalDate.MAX);check(b.lastDate()==null || !b.lastDate().isAfter(date),"date");return new TopUpQuote(quote.sourceId(),quote.sourceNumber(),quote.clientId(),quote.client(),date,quote.principal(),quote.interest(),quote.excludedInterest(),quote.sequence(),b.outstanding(),b.sequence());
    }
    @Transactional(readOnly=true)
    public Optional<TopUpQuote> disbursementTopUp(AppUserPrincipal a,UUID loan,LocalDate date){
        accounting.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");records.record(a,loan,false);return records.topUpSource(a,loan).map(id->topUpQuote(a,id,date==null?clock.today():date,loan));
    }
    @Transactional(readOnly=true) public Optional<TopUpPosting> topUp(AppUserPrincipal a,UUID loan){authorize(a,"VIEW");records.record(a,loan,false);return records.topUp(a,loan);}
    @Transactional(readOnly=true) public Optional<TopUpPosting> sourceTopUp(AppUserPrincipal a,UUID loan){authorize(a,"VIEW");records.record(a,loan,false);return records.sourceTopUp(a,loan);}
    @Transactional(readOnly=true) public Optional<TopUpPosting> transactionTopUp(AppUserPrincipal a,UUID transaction){authorize(a,"VIEW");return records.transactionTopUp(a,transaction);}
    @Transactional(readOnly=true) public Optional<TopUpQuote> recordedTopUpQuote(AppUserPrincipal a,UUID loan){authorize(a,"VIEW");records.record(a,loan,false);return records.topUpSource(a,loan).map(id->topUpQuote(a,id,clock.today(),loan));}
    @Transactional
    public TopUpPosting reverseTopUp(AppUserPrincipal a,UUID loan,UUID key,LocalDate date,String reason,String evidence){
        authorize(a,"REVERSE");text(reason,500,true);text(evidence,500,true);check(key!=null && date!=null && !date.isAfter(clock.today()),"date");
        var original=records.topUp(a,loan).orElseThrow(()->new IllegalArgumentException("recording.error.topUpUnavailable"));
        var f=new PostForm();f.setLoanId(loan);f.setRequestKey(key);f.setEffectiveDate(date);f.setNotes(reason);f.setEvidence(evidence);f.setAmount(original.principal().add(original.interest()).add(original.cash()).add(original.sourceCharges()).add(original.newDeductions()));
        String hash=hash(f);records.lockRequest(a,key);var prior=records.topUpRequest(a,key,hash);if(prior.isPresent())return prior.get();
        records.lockTopUpPair(a,original.sourceId(),loan);original=records.topUp(a,loan).orElseThrow();
        check(original.reversedBy()==null,"reversal");check(!original.actorId().equals(a.getMemberId()),"checker");check(!date.isBefore(original.date()),"date");
        long sequence=records.topUpCorrectionSequence(a,original);UUID period=periods.period(a.getSaccoId(),date,a.getMemberId(),clock.now());
        UUID id=UUID.randomUUID(),journal=UUID.randomUUID();var now=clock.now();
        records.topUpJournal(a,id,journal,f,hash,period,original.journalId(),now);
        charges.undoCollections(a,original.journalId(),journal,original.id(),date,evidence,reason);
        charges.reverseAssessmentsForFunding(a,loan,key,date,evidence,reason);
        UUID transaction=repayments.undoRecordedTopUp(original.sourceId(),original.transactionId(),a,key,id,date,reason);
        var quote=new TopUpQuote(original.sourceId(),original.sourceNumber(),null,"",date,original.principal(),original.interest(),original.excludedInterest(),sequence,original.sourceCharges(),0);
        records.insertTopUp(a,id,original.sourceId(),original.id(),transaction,journal,f,hash,quote,original.id(),now,original.newDeductions());
        records.lines(a,journal,records.reversedLines(original.journalId()));
        var replacement=loans.findForDisbursement(loan).orElseThrow();replacement.setStatus(LoanStatus.DISBURSEMENT_REVERSED);replacement.setUpdatedAt(now);loans.saveAndFlush(replacement);
        repayments.refreshCancelledTopUp(loan,a);
        records.postJournal(a,journal,now);event(a,id,"LOAN_TOPUP_REVERSED",Map.of("loan",loan,"source",original.sourceId(),"reason",reason));
        return records.topUpRequest(a,key,hash).orElseThrow();
    }
    public record Quote(String product,BigDecimal rate,String method,String frequency,BigDecimal installment,BigDecimal interest,List<ScheduleRow> schedule,String tenureUnit,int tenureValue) {}
    private AllocationPreview chargeAllocation(BigDecimal amount,BigDecimal interest,BigDecimal balance,BigDecimal fees){
        if(amount.compareTo(balance.add(interest).add(fees))>0)throw new IllegalArgumentException("repayment.error.advance");var i=amount.min(interest);var fee=amount.subtract(i).min(fees);var p=amount.subtract(i).subtract(fee);return new AllocationPreview(p,i,balance.subtract(p),fee);
    }
    @Transactional(readOnly=true) public com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan chargeQuote(AppUserPrincipal a,RecordForm f){authorize(a,"CREATE");return charges.quote(a,f,product(a,f),false);}
    @Transactional(readOnly=true) public com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Summary chargeSummary(AppUserPrincipal a,UUID loan,int page){authorize(a,"VIEW");records.record(a,loan,false);return charges.summary(a,loan,page);}
    @Transactional(readOnly=true) public com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Summary fundingCharges(AppUserPrincipal a,UUID loan){accounting.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");return charges.summary(a,loan,0);}
    @Transactional(readOnly=true) public BigDecimal fundingCash(AppUserPrincipal a,UUID id,LocalDate date){accounting.requireActor(a,"DISBURSEMENT_QUEUE_DISBURSE");var loan=records.loan(a,id,clock.today());var source=disbursementTopUp(a,id,date);return loan.principal().subtract(source.map(TopUpQuote::total).orElse(ZERO)).subtract(charges.frozen(a,id).deductions());}
    @Transactional(readOnly=true)
    public AllocationPreview postingPreview(AppUserPrincipal a,PostForm f,String kind){
        authorize(a,kind.equals("DISBURSEMENT")?"DISBURSE":"POST");validatePost(f);records.record(a,f.getLoanId(),false);
        var loan=loans.findById(f.getLoanId()).orElseThrow();
        records.account(a,f.getMoneyAccountId(),"MONEY",false);
        if(kind.equals("DISBURSEMENT")){check(loan.getStatus()==LoanStatus.RECORDED,"state");check(f.getAmount().compareTo(loan.getAmount())==0,"disbursementAmount");records.account(a,f.getPrincipalAccountId(),"PRINCIPAL",false);return new AllocationPreview(f.getAmount(),ZERO,f.getAmount());}
        check(Set.of(LoanStatus.DISBURSED,LoanStatus.PAR,LoanStatus.DEFAULTED).contains(loan.getStatus()),"state");
        var balance=repayments.paymentBalance(f.getLoanId(),a,f.getEffectiveDate());
        var linked=productAccounts.required(a,f.getLoanId());records.account(a,linked.principalId(),"PRINCIPAL",false);records.account(a,linked.interestId(),"INTEREST",false);return chargeAllocation(f.getAmount(),balance.dueInterest(),balance.outstandingPrincipal(),charges.balance(a,f.getLoanId(),f.getEffectiveDate()).outstanding());
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
        return new Quote(p.getDisplayName(),p.getInterestRate(),p.getInterestMethod().name(),p.getResolvedRepaymentFrequency().name(),result.installment(),result.totalInterest(),scheduleRows(result,null),tenure(f).unit(),tenure(f).value());
    }
    @Transactional(readOnly=true)
    public Quote preview(AppUserPrincipal a,RecordForm f){authorize(a,"CREATE");validateRecord(f);check(records.client(a,f.getClientId()).isPresent(),"client");var product=product(a,f);var result=calculate(f,product);
        return new Quote(product.getDisplayName(),product.getInterestRate(),product.getInterestMethod().name(),product.getResolvedRepaymentFrequency().name(),result.installment(),result.totalInterest(),List.of(),tenure(f).unit(),tenure(f).value());}
    @Transactional
    public UUID save(AppUserPrincipal a,RecordForm f){return save(a,f,List.of());}
    @Transactional
    public UUID save(AppUserPrincipal a,RecordForm f,List<org.springframework.web.multipart.MultipartFile> files){
        return saveInternal(a,f,files,null,null);
    }
    /** Called only by the organization origination use case, in the same transaction as its source link. */
    @Transactional
    public UUID saveOrganization(AppUserPrincipal a,RecordForm f,UUID borrowerParty,String payoutSnapshot){
        check(f!=null && f.getClientId()==null && f.getTopUpSourceLoanId()==null && "CONFIGURED".equals(f.getLoanMode()) && borrowerParty!=null && payoutSnapshot!=null,"request");
        authorize(a,"CREATE");check(records.organizationParty(a,borrowerParty),"client");
        return saveInternal(a,f,List.of(),borrowerParty,payoutSnapshot);
    }
    /** Source, joint obligors and conserved allocations must commit with this application. */
    @Transactional
    public UUID saveShared(AppUserPrincipal a,RecordForm f,UUID borrowerParty){
        check(f!=null && f.getClientId()==null && f.getTopUpSourceLoanId()==null && "CONFIGURED".equals(f.getLoanMode()) && borrowerParty!=null,"request");
        authorize(a,"CREATE");check(records.sharedParty(a,borrowerParty),"client");
        return saveInternal(a,f,List.of(),borrowerParty,"{\"available\":false}");
    }
    private UUID saveInternal(AppUserPrincipal a,RecordForm f,List<org.springframework.web.multipart.MultipartFile> files,UUID borrowerParty,String payoutSnapshot){
        validateFiles(files);
        authorize(a,"CREATE");validateRecord(f,borrowerParty!=null);String hash=borrowerParty==null?uploadHash(f,files):hash(Map.of("form",f,"borrowerParty",borrowerParty,"payout",payoutSnapshot));records.lockRequest(a,f.getRequestKey());var previous=records.recordRequest(a,f.getRequestKey(),hash);if(previous.isPresent())return previous.get();
        check(borrowerParty!=null || records.client(a,f.getClientId()).isPresent(),"client");var product=product(a,f);var calculation=calculate(f,product);
        var shared=borrowerParty==null?sharedCredit.lockForRecording(a,f.getClientId()):com.sacco.mvp.grouplending.dto.SharedCreditExposure.NONE;
        if(shared.active()>0){
            check(product.isApplicationWithActiveLoanAllowed(),"sharedActiveLoan");
            if(product.getMaximumActiveLoans()!=null)check(shared.active()+loans.countBySaccoIdAndApplicantMemberIdAndStatusIn(a.getSaccoId(),f.getClientId(),List.of(LoanStatus.DISBURSED,LoanStatus.PAR,LoanStatus.DEFAULTED,LoanStatus.WRITTEN_OFF))<=product.getMaximumActiveLoans(),"sharedActiveLoan");
            if(product.isAffordabilityCheckRequired()){
                var disposable=f.getIncome().subtract(f.getExpenses()).subtract(f.getOtherDebt().max(shared.monthlyPayment()));
                check(disposable.signum()>0&&LoanAmortizationCalculator.monthlyEquivalent(calculation.maximumInstallment(),product.getResolvedRepaymentFrequency()).compareTo(disposable.multiply(product.getResolvedMaxRepaymentToDisposableIncomeRatio()).setScale(2,java.math.RoundingMode.DOWN))<=0,"sharedAffordability");
            }
        }
        var chargePlan=charges.quote(a,f,product,true);var now=clock.now();
        TopUpQuote settlement=null;
        if(f.getTopUpSourceLoanId()!=null){
            check(records.lockReceiptLoans(a,List.of(f.getTopUpSourceLoanId()),f.getClientId()).size()==1,"topUpUnavailable");
            settlement=topUpQuote(a,f.getTopUpSourceLoanId(),f.getApplicationDate(),null);
            check(settlement.clientId().equals(f.getClientId()),"client");check(f.getPrincipal().subtract(chargePlan.deductions()).compareTo(settlement.total())>0,"topUpCash");
            text(f.getEvidence(),500,true);
        }
        var snapshot=new LinkedHashMap<String,Object>();snapshot.put("calculationVersion",LoanAmortizationCalculator.VERSION);snapshot.put("principalAmount",f.getPrincipal());
        snapshot.put("interestRate",product.getInterestRate());snapshot.put("interestMethod",product.getInterestMethod().name());snapshot.put("repaymentFrequency",product.getResolvedRepaymentFrequency().name());
        tenure(f).snapshot(snapshot);snapshot.put("interestEntry",f.getInterestEntry());snapshot.put("interestBasis",f.getInterestBasis());snapshot.put("interestPercent",f.getInterestPercent());snapshot.put("loanMode",f.getLoanMode());snapshot.put("tenorMonths",f.getMonths());snapshot.put("numberOfPayments",calculation.rows().size());snapshot.put("periodicRepaymentAmount",calculation.installment());
        snapshot.put("maximumInstallmentAmount",calculation.maximumInstallment());snapshot.put("interestAmount",calculation.totalInterest());snapshot.put("recordingSource","ACCOUNTANT_DIRECT");
        var selectedProduct=products.findByIdAndSaccoId(product.getId(),a.getSaccoId()).orElseThrow();
        snapshot.put("prepaymentTerms",LoanPrepaymentTerms.product(selectedProduct).snapshot());
        if(f.getGroupDisclosure()!=null)snapshot.put("groupDisclosure",f.getGroupDisclosure());
        if(settlement!=null){snapshot.put("topUpSourceLoanId",settlement.sourceId().toString());snapshot.put("topUpSourceLoanNumber",settlement.sourceNumber());snapshot.put("topUpSettlementAmount",settlement.total());snapshot.put("topUpRequestedAmount",f.getPrincipal().subtract(settlement.total()).subtract(chargePlan.deductions()));snapshot.put("topUpSettlementCharges",settlement.charges());snapshot.put("topUpChargeSequence",settlement.chargeSequence());snapshot.put("topUpSettlementPrincipal",settlement.principal());snapshot.put("topUpSettlementInterest",settlement.interest());snapshot.put("topUpQuoteDate",settlement.date().toString());}
        var policy=recordedPolicy(a,selectedProduct,product.getDisplayName(),product.getGuarantorsRequired());
        if(shared.active()>0)policy.put("sharedCredit",Map.of("active",shared.active(),"maximumLiability",shared.liability(),"monthlyPayment",shared.monthlyPayment(),"assessedAt",now));
        snapshot.put("recordedCharges",chargePlan);snapshot.put("recordedChargeTotal",chargePlan.total());snapshot.put("recordedChargeDeductions",chargePlan.deductions());snapshot.put("recordedNetCashEstimate",f.getPrincipal().subtract(settlement==null?ZERO:settlement.total()).subtract(chargePlan.deductions()));snapshot.put("recordedTotalPayable",f.getPrincipal().add(calculation.totalInterest()).add(chargePlan.total()));policy.put("recordedChargePolicy",Objects.toString(chargePlan.policyId(),""));
        long number=numbers.nextFor(a.getSaccoId());
        var loan=LoanApplication.builder().id(UUID.randomUUID()).applicationNumber(number).saccoId(a.getSaccoId()).stationId(a.getStationId())
            .applicantMemberId(f.getClientId()).borrowerPartyId(borrowerParty).paymentDetailsSnapshot(payoutSnapshot).topUpSourceLoanId(f.getTopUpSourceLoanId()).loanType(product.getLoanType()).loanProductSettingId(product.getId()).amount(f.getPrincipal().setScale(2))
            .tenorMonths(f.getMonths()).status(LoanStatus.RECORDED).requiredGuarantors(product.getGuarantorsRequired()).formData(write(f))
            .policySnapshot(write(policy))
            .financialSnapshot(write(snapshot)).repaymentFrequency(product.getResolvedRepaymentFrequency()).firstRepaymentDate(f.getFirstPaymentDate())
            .installmentAmount(calculation.installment()).createdAt(now).updatedAt(now).build();
        loan.setAttachmentsJson(attachments.store(loan.getId(),files,null));
        loan=loans.saveAndFlush(loan);UUID id=loan.getId();records.insertRecord(a,id,f,hash,product.getDisplayName(),write(f),now);
        charges.freeze(a,id,chargePlan);
        if(borrowerParty!=null && product.getGuarantorsRequired()!=null && product.getGuarantorsRequired()>0)loan.setStatus(LoanStatus.AWAITING_GUARANTORS);
        else routing.moveToFirstReviewStage(loan,a.getMemberId());loans.saveAndFlush(loan);
        event(a,id,"LOAN_APPLICATION_RECORDED",Map.of("principal",f.getPrincipal(),"status",loan.getStatus().name()));return id;
    }
    /** Personal guarantees precede the product-selected review path. */
    @Transactional public void startGuaranteedGroupReview(AppUserPrincipal a,UUID id){
        authorize(a,"CREATE");accounting.requireActor(a,"GROUP_LENDING_SUBMIT");records.record(a,id,false);var loan=loans.findForDisbursement(id).orElseThrow();
        check(loan.getApplicantMemberId()==null && loan.getRequiredGuarantors()!=null && loan.getRequiredGuarantors()>0 && records.personalGuaranteesReady(a,id),"personalGuarantees");
        if(loan.getStatus()!=LoanStatus.AWAITING_GUARANTORS)return;
        routing.moveToFirstReviewStage(loan,a.getMemberId());loans.saveAndFlush(loan);event(a,id,"PERSONAL_GUARANTEES_REVIEW_STARTED",Map.of("status",loan.getStatus().name()));
    }
    @Transactional(readOnly=true)
    public Detail detail(AppUserPrincipal a,UUID id){
        authorize(a,"VIEW");var record=records.record(a,id,false);var loan=loans.findById(id).orElseThrow();var f=read(record.get("metadata").toString(),RecordForm.class);
        var snapshot=schedules.parseSummary(loan.getFinancialSnapshot());var calc=LoanAmortizationCalculator.estimate(loan.getAmount(),loan.getTenorMonths(),snapshot);
        var schedule=loan.getFirstRepaymentDate()==null
            ?scheduleRows(calc,null)
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
        organizationFunding.lock(a,f.getLoanId());
        sharedLoans.lock(a,f.getLoanId());
        var source=records.topUpSource(a,f.getLoanId());
        if(source.isPresent())records.lockTopUpPair(a,source.get(),f.getLoanId());
        var record=records.record(a,f.getLoanId(),true);var loan=loans.findForDisbursement(f.getLoanId()).orElseThrow();check(loan.getStatus()==LoanStatus.READY_FOR_DISBURSEMENT,"state");
        if(loan.getApplicantMemberId()==null)check(Boolean.TRUE.equals(f.getOrganizationAccepted())||Boolean.TRUE.equals(f.getSharedAccepted()),"organizationFundingPending");
        check(allowedTeller(loan,a.getMemberId()),"customTeller");
        String collateralError=loans.collateralError(loan.getId(),a.getSaccoId(),a.getStationId(),clock.today());
        if(collateralError!=null)throw new IllegalArgumentException(collateralError);
        var policy=schedules.parseSummary(loan.getPolicySnapshot());
        if(Boolean.TRUE.equals(policy.get("disbursementProofRequired")))check(files.stream().anyMatch(file->!file.isEmpty()),"attachments");
        check(f.getAmount().compareTo(loan.getAmount())==0,"disbursementAmount");LocalDate first=f.getFirstPaymentDate()!=null?f.getFirstPaymentDate():loan.getFirstRepaymentDate();
        check(first!=null,"date");
        check(!f.getEffectiveDate().isBefore(((java.sql.Date)record.get("application_date")).toLocalDate()) && first.isAfter(f.getEffectiveDate()),"date");
        UUID period=periods.period(a.getSaccoId(),f.getEffectiveDate(),a.getMemberId(),clock.now());
        var chargePlan=charges.frozen(a,loan.getId());charges.assessAtFunding(a,loan.getId(),f.getEffectiveDate(),f.getEvidence());
        BigDecimal deductions=chargePlan.deductions();
        var linked=productAccounts.bind(a,loan.getId(),loan.getLoanProductSettingId(),null,"DISBURSEMENT");
        check(f.getPrincipalAccountId()==null || f.getPrincipalAccountId().equals(linked.principalId()),"account");
        var money=records.account(a,f.getMoneyAccountId(),"MONEY");var principal=records.account(a,linked.principalId(),"PRINCIPAL");
        TopUpQuote settlement=source.isPresent()?topUpQuote(a,source.get(),f.getEffectiveDate(),loan.getId()):null;
        Choice settlementInterest=null;
        if(settlement!=null){
            check(loan.getApplicantMemberId().equals(settlement.clientId()),"client");
            check(f.getSettlementSequence()!=null && f.getSettlementSequence()==settlement.sequence() && f.getSettlementPrincipal()!=null && f.getSettlementInterest()!=null
                && f.getSettlementPrincipal().compareTo(settlement.principal())==0 && f.getSettlementInterest().compareTo(settlement.interest())==0,"topUpChanged");
            check((settlement.charges().signum()==0 && f.getSettlementCharges()==null || f.getSettlementCharges()!=null && f.getSettlementCharges().compareTo(settlement.charges())==0)
                && (settlement.chargeSequence()==0 && f.getSettlementChargeSequence()==null || f.getSettlementChargeSequence()!=null && f.getSettlementChargeSequence()==settlement.chargeSequence()),"topUpChanged");
            check(f.getAmount().subtract(deductions).compareTo(settlement.total())>0,"topUpCash");text(f.getEvidence(),500,true);
            if(settlement.interest().signum()>0){var sourceAccounts=productAccounts.required(a,source.get());check(f.getInterestAccountId()==null || f.getInterestAccountId().equals(sourceAccounts.interestId()),"account");settlementInterest=records.account(a,sourceAccounts.interestId(),"INTEREST");}
        }else check(f.getInterestAccountId()==null,"account");
        UUID id=UUID.randomUUID(),journal=UUID.randomUUID();var now=clock.now();
        if(settlement==null)records.journal(a,journal,id,f,hash,period,deductions.signum()>0?"LOAN_CHARGE_FUNDING":"DISBURSEMENT",null,now);
        else records.topUpJournal(a,id,journal,f,hash,period,null,now);
        if(loan.getApplicantMemberId()==null&&!sharedLoans.execute(a,f,id,journal,first,deductions))organizationFunding.execute(a,f,id,journal,first,deductions);
        records.ticket(a,id,"DISBURSEMENT",f,hash,journal,null,money,principal,null,now);
        var schedule=schedules.buildSchedule(loan,f.getEffectiveDate(),first,loan.getRepaymentFrequency(),null,f.getReference(),f.getNotes());
        loan.setFirstRepaymentDate(first);
        loan.setStatus(LoanStatus.DISBURSED);loan.setDisbursementDate(f.getEffectiveDate());loan.setDisbursementReference(f.getReference().strip());loan.setDisbursementNotes(f.getNotes().strip());
        loan.setAttachmentsJson(attachments.store(loan.getId(),files,loan.getAttachmentsJson(),LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF));
        disbursementNumbers.assign(loan,a.getMemberId(),f.getEffectiveDate());
        loan.setRepaymentScheduleJson(schedule.scheduleJson());loan.setFinalDueDate(schedule.finalDueDate());loan.setUpdatedAt(now);loans.saveAndFlush(loan);
        if(settlement==null){repayments.openAtDisbursement(loan);var lines=new ArrayList<Object[]>();lines.add(new Object[]{principal.id(),f.getAmount(),ZERO});check(f.getAmount().compareTo(deductions)>0,"disbursementAmount");lines.add(new Object[]{money.id(),ZERO,f.getAmount().subtract(deductions)});lines.addAll(charges.collect(a,loan.getId(),"DEDUCT",deductions,f.getEffectiveDate(),journal,id,f.getEvidence(),f.getNotes()));records.lines(a,journal,lines);}
        else {
            repayments.openAtTopUp(loan);
            UUID transaction=repayments.settleRecordedTopUp(source.get(),a,f.getEffectiveDate(),f.getRequestKey(),id,settlement.sequence(),settlement.principal(),settlement.interest());
            loans.flush();
            records.insertTopUp(a,id,source.get(),id,transaction,journal,f,hash,settlement,null,now,deductions);
            var lines=new ArrayList<Object[]>();lines.add(new Object[]{principal.id(),f.getAmount(),ZERO});lines.add(new Object[]{records.principalAccount(source.get()),ZERO,settlement.principal()});
            lines.add(new Object[]{money.id(),ZERO,f.getAmount().subtract(settlement.total()).subtract(deductions)});if(settlementInterest!=null)lines.add(new Object[]{settlementInterest.id(),ZERO,settlement.interest()});
            lines.addAll(charges.collect(a,source.get(),"SETTLE",settlement.charges(),f.getEffectiveDate(),journal,id,f.getEvidence(),f.getNotes()));
            lines.addAll(charges.collect(a,loan.getId(),"DEDUCT",deductions,f.getEffectiveDate(),journal,id,f.getEvidence(),f.getNotes()));
            records.lines(a,journal,lines);
        }
        records.finish(id,null,f.getAmount(),ZERO,f.getAmount());records.postJournal(a,journal,now);
        reviews.save(ManagerReview.builder().id(UUID.randomUUID()).loanApplicationId(loan.getId()).managerMemberId(a.getMemberId()).reviewStage(ApprovalWorkflowStage.DISBURSEMENT_OFFICER).decision(ManagerDecision.ACCEPT).reasons(f.getNotes()).createdAt(now).build());
        routing.announceRecordedDisbursement(loan,a.getMemberId());
        event(a,id,"LOAN_DISBURSEMENT_POSTED",Map.of("loan",loan.getId(),"amount",f.getAmount()));return records.posting(a,id);
    }
    @Transactional
    public Posting repay(AppUserPrincipal a,PostForm f){
        authorize(a,"POST");validatePost(f);var policy=repaymentPolicies.effective(a,f.getEffectiveDate(),false);String hash=hash(f);records.lockRequest(a,f.getRequestKey());var prior=records.postRequest(a,f.getRequestKey(),hash);if(prior.isPresent())return prior.get();
        records.record(a,f.getLoanId(),true);UUID period=periods.period(a.getSaccoId(),f.getEffectiveDate(),a.getMemberId(),clock.now());
        var linked=productAccounts.required(a,f.getLoanId());UUID principalId=records.principalAccount(f.getLoanId());check(principalId.equals(linked.principalId()),"productPrincipalMismatch");check(f.getPrincipalAccountId()==null || principalId.equals(f.getPrincipalAccountId()),"account");
        check(f.getInterestAccountId()==null || linked.interestId().equals(f.getInterestAccountId()),"account");
        var principal=records.account(a,principalId,"PRINCIPAL");var money=records.account(a,f.getMoneyAccountId(),"MONEY");
        var interest=records.account(a,linked.interestId(),"INTEREST");
        var balance=repayments.paymentBalance(f.getLoanId(),a,f.getEffectiveDate());var feeBalance=charges.balance(a,f.getLoanId(),LocalDate.MAX);
        check(feeBalance.lastDate()==null || !f.getEffectiveDate().isBefore(feeBalance.lastDate()),"date");
        var allocation=chargeAllocation(f.getAmount(),balance.dueInterest(),balance.outstandingPrincipal(),feeBalance.outstanding());
        UUID id=UUID.randomUUID(),journal=UUID.randomUUID();var now=clock.now();records.journal(a,journal,id,f,hash,period,allocation.charges().signum()>0?"LOAN_CHARGE_RECEIPT":"PAYMENT",null,now);
        records.ticket(a,id,"PAYMENT",f,hash,journal,null,money,principal,interest,now);
        var feeLines=charges.collect(a,f.getLoanId(),"COLLECT",allocation.charges(),f.getEffectiveDate(),journal,id,f.getEvidence(),f.getNotes());
        var debtAmount=f.getAmount().subtract(allocation.charges());
        var receipt=debtAmount.signum()==0?null:repayments.postRecorded(f.getLoanId(),a,new LoanRepaymentLedgerService.PaymentCommand(debtAmount,f.getEffectiveDate(),LoanRepaymentTransaction.Channel.valueOf(money.purpose()),f.getReference().strip(),f.getRequestKey(),policy.id(),false));
        loans.flush();
        check(allocation.interest().signum()==0 || interest!=null,"interestAccount");var lines=new ArrayList<Object[]>();lines.add(new Object[]{money.id(),f.getAmount(),ZERO});
        if(allocation.principal().signum()>0)lines.add(new Object[]{principal.id(),ZERO,allocation.principal()});if(allocation.interest().signum()>0)lines.add(new Object[]{interest.id(),ZERO,allocation.interest()});lines.addAll(feeLines);
        records.lines(a,journal,lines);records.finish(id,receipt==null?null:receipt.id(),allocation.principal(),allocation.interest(),records.loan(a,f.getLoanId(),clock.today()).outstandingPrincipal(),allocation.charges());
        sharedLoans.payment(a,f.getLoanId(),records.posting(a,id),f.getSharedPayer());records.postJournal(a,journal,now);
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
        charges.beforeCorrection(a,original.loanId(),original.id(),date);
        UUID period=periods.period(a.getSaccoId(),date,a.getMemberId(),clock.now());var raw=records.reversedLines(original.journalId());
        // Original accounts are retained; corrections cannot switch the receiving account or allocation.
        var money=new Choice((UUID)raw.stream().filter(l->((BigDecimal)l[2]).signum()>0).findFirst().orElseThrow()[0],original.moneyAccount(),"");
        var principal=new Choice(records.principalAccount(original.loanId()),original.principalAccount(),"");
        var interest=original.interest().signum()>0?new Choice(records.postingInterestAccount(original.id()),original.interestAccount(),""):null;
        boolean chargeReceipt=charges.hasCollections(original.journalId());UUID id=UUID.randomUUID(),journal=UUID.randomUUID();var now=clock.now();records.journal(a,journal,id,f,hash,period,chargeReceipt?"LOAN_CHARGE_REVERSAL":"REVERSAL",original.journalId(),now);
        records.ticket(a,id,"REVERSAL",f,hash,journal,original.id(),money,principal,interest,now);
        if(chargeReceipt){charges.undoCollections(a,original.journalId(),journal,id,date,original.evidence(),reason);}
        var receipt=original.transactionId()==null?null:repayments.reverseRecorded(original.loanId(),original.transactionId(),a,key,reason.strip(),date);
        loans.flush();
        records.lines(a,journal,raw);records.finish(id,receipt==null?null:receipt.id(),original.principal(),original.interest(),records.loan(a,original.loanId(),clock.today()).outstandingPrincipal(),original.charges());
        sharedLoans.reverse(a,original.loanId(),original.id(),id);records.postJournal(a,journal,now);
        event(a,id,"LOAN_REPAYMENT_REVERSED",Map.of("original",originalId));return records.posting(a,id);
    }
    public record LoanExport(Detail detail,List<Posting> payments,com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan charges) { public LoanExport(Detail detail,List<Posting> payments){this(detail,payments,new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan(null,"","",List.of(),true,""));} }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public LoanExport exportLoan(AppUserPrincipal a,UUID id){
        authorize(a,"EXPORT");var detail=detail(a,id);var rows=records.loanPosts(a,id,501,0);check(rows.size()<=500,"reportLimit");
        return new LoanExport(detail,rows.stream().filter(p->!Set.of("DISBURSEMENT","CHARGE_ASSESS","CHARGE_UNDO").contains(p.kind())).toList(),charges.frozen(a,id));
    }
    @Transactional(readOnly=true) public List<JournalLine> postingLines(AppUserPrincipal a,UUID id){authorize(a,"EXPORT");var posting=records.posting(a,id);return records.journalLines(a,posting.journalId());}
    @Transactional(readOnly=true) public Posting posting(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.posting(a,id);}
    @Transactional(readOnly=true) public UUID disbursementCorrection(AppUserPrincipal a,UUID id){authorize(a,"VIEW");return records.disbursementCorrection(a,id);}
    @Transactional(readOnly=true) public Optional<Page<ScheduleRow>> actualRepaymentSchedule(AppUserPrincipal actor,UUID loanId,int page){
        authorize(actor,"VIEW");check(page>=0 && page<24,"filter");records.record(actor,loanId,false);
        if(!repayments.hasLedger(loanId))return Optional.empty();
        var stored=repayments.schedule(loanId,actor,page);
        return Optional.of(new Page<>(stored.stream().map(r->new ScheduleRow(r.number(),r.dueDate(),r.principal(),r.interest(),r.principal().add(r.interest()),r.beginningBalance(),r.endingBalance())).toList(),page,stored.hasNext()));
    }
    @Transactional(readOnly=true) public Page<Posting> history(AppUserPrincipal a,UUID id,int page){authorize(a,"VIEW");check(page>=0 && page<=10000,"filter");records.record(a,id,false);var rows=records.loanPosts(a,id,26,page*25);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    @Transactional(readOnly=true) public Page<Posting> movements(AppUserPrincipal a,UUID client,int page){authorize(a,"VIEW");check(page>=0 && page<=10000,"filter");check(records.ledgerClient(a,client).isPresent(),"client");var rows=records.clientPosts(a,client,26,page*25);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);}
    public record LoanStatementView(StatementExport source,Posting opening,int page,boolean hasNext) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public LoanStatementView loanStatement(AppUserPrincipal a,UUID client,UUID loan,int page,boolean export){
        authorize(a,export?"EXPORT":"VIEW");check(page>=0 && page<=10000,"filter");
        var owner=records.ledgerClient(a,client).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
        var record=records.loan(a,loan,clock.today());
        if(!Objects.equals(record.clientId(),client))throw new org.springframework.security.access.AccessDeniedException("Loan outside client statement");
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
    @Transactional(readOnly=true) public Statement statement(AppUserPrincipal a,UUID client,int page){authorize(a,"VIEW");var c=records.ledgerClient(a,client).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));var f=new Filter("",null,null,"","newest",page,client);filter(f,false);var t=records.clientTotals(a,f,clock.today());var rows=records.loans(a,f,clock.today(),26,page*25);return new Statement(c.label(),c.label().split(" - ",2)[0],(BigDecimal)t.get("principal"),(BigDecimal)t.get("interest"),(BigDecimal)t.get("arrears"),(BigDecimal)t.get("future_interest"),((Number)t.get("loans")).longValue(),new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25),(BigDecimal)t.get("charges"));}
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
            return LoanProductSetting.builder().id(configured.getId()).saccoId(a.getSaccoId()).loanType(LoanType.CUSTOMIZED_LOAN).productName(configured.getDisplayName()).interestRate(rate).interestMethod(f.getCustomMethod()).repaymentFrequency(f.getCustomFrequency()).guarantorsRequired(0).applicationFee(configured.getApplicationFee()).processingFeeRate(configured.getProcessingFeeRate()).insuranceRate(configured.getInsuranceRate()).build();
        }
        var p=products.findByIdAndSaccoId(f.getProductId(),a.getSaccoId()).orElseThrow(()->new IllegalArgumentException("recording.error.product"));check(p.isAvailableForApplications() && p.getInterestRate()!=null && p.getInterestMethod()!=null,"product");
        check((p.getMinimumAmount()==null || f.getPrincipal().compareTo(p.getMinimumAmount())>=0) && (p.getMaximumAmount()==null || f.getPrincipal().compareTo(p.getMaximumAmount())<=0),"productLimits");
        tenure(f).validate(p);
        return p;}
    private LoanTenure tenure(RecordForm f){Integer value=f.getTenureValue()==null?f.getMonths():f.getTenureValue();check(value!=null,"terms");return new LoanTenure(f.getTenureUnit(),value);}
    private LoanAmortizationCalculator.Result calculate(RecordForm f,LoanProductSetting p){
        var term=tenure(f);BigDecimal flat=term.flatInterest(f.getPrincipal(),p.getInterestRate());
        if("CUSTOM".equals(f.getLoanMode()))flat="AMOUNT".equals(f.getInterestEntry())?f.getInterestAmount():"WHOLE_LOAN".equals(f.getInterestBasis())?f.getPrincipal().multiply(p.getInterestRate()).setScale(2,RoundingMode.HALF_UP):flat;
        return term.calculate(f.getPrincipal(),p.getInterestRate(),p.getInterestMethod(),p.getResolvedRepaymentFrequency(),null,flat,
            "CUSTOM".equals(f.getLoanMode()) && p.getInterestMethod()==InterestMethod.REDUCING_BALANCE && "WHOLE_LOAN".equals(f.getInterestBasis()));
    }
    private List<ScheduleRow> scheduleRows(LoanAmortizationCalculator.Result calculation,LoanApplication loan){
        var result=new ArrayList<ScheduleRow>(calculation.rows().size());
        var balance=calculation.totalRepayment();
        var term=loan==null?null:LoanTenure.fromSnapshot(schedules.parseSummary(loan.getFinancialSnapshot()),loan.getTenorMonths());
        for(var row:calculation.rows()){
            var ending=balance.subtract(row.amount());
            var date=loan==null?null:term.dueDate(loan.getDisbursementDate(),loan.getFirstRepaymentDate(),loan.getRepaymentFrequency(),row.number(),calculation.rows().size());
            result.add(new ScheduleRow(row.number(),date,row.principal(),row.interest(),row.amount(),balance,ending));
            balance=ending;
        }
        return result;
    }
    private List<ScheduleRow> datedSchedule(LoanAmortizationCalculator.Result calculation,LoanApplication loan){return scheduleRows(calculation,loan);}
    private static void nonnegativeMoney(BigDecimal amount){check(amount!=null && amount.signum()>=0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");}
    private static void validateFiles(List<org.springframework.web.multipart.MultipartFile> files){check(files!=null && files.size()<=10 && files.stream().allMatch(f->f.getSize()<=25L*1024*1024),"attachments");}
    @Transactional(readOnly=true) public List<Map<String,Object>> attachments(AppUserPrincipal a,UUID id){authorize(a,"VIEW");records.record(a,id,false);return attachments.parse(loans.findById(id).orElseThrow().getAttachmentsJson());}
    @Transactional(readOnly=true) public LoanAttachmentService.AttachmentResource attachment(AppUserPrincipal a,UUID id,String attachmentId){authorize(a,"VIEW");records.record(a,id,false);return attachments.load(id,attachmentId,loans.findById(id).orElseThrow().getAttachmentsJson());}
    private void validateRecord(RecordForm f){validateRecord(f,false);}
    private void validateRecord(RecordForm f,boolean organization){check(f!=null && f.getRequestKey()!=null && (organization?f.getClientId()==null:f.getClientId()!=null) && ("CUSTOM".equals(f.getLoanMode()) || f.getProductId()!=null) && Set.of("CONFIGURED","CUSTOM").contains(f.getLoanMode()),"request");money(f.getPrincipal());f.setRequestedPrincipal(f.getPrincipal());f.setMonths(tenure(f).compatibilityMonths());check(f.getMonths()!=null && f.getMonths()>=1 && f.getMonths()<=600,"terms");
        if("CONFIGURED".equals(f.getLoanMode())){f.setInterestEntry("PERCENTAGE");f.setInterestBasis("ANNUAL");f.setInterestPercent(null);f.setInterestAmount(null);}
        check(f.getApplicationDate()!=null && !f.getApplicationDate().isAfter(clock.today()),"date");text(f.getPurpose(),500,true);text(f.getBusiness(),1000,false);text(f.getGuarantors(),1000,false);text(f.getCollateral(),1000,false);text(f.getEvidence(),500,false);text(f.getNotes(),500,false);
        check(Set.of("DECLARED","DOCUMENTED","UNAVAILABLE").contains(f.getInformationSource()),"information");for(var amount:Arrays.asList(f.getIncome(),f.getExpenses(),f.getOtherDebt()))if(amount!=null)check(amount.signum()>=0 && amount.scale()<=2 && amount.precision()-amount.scale()<=16,"amount");}
    private void validatePost(PostForm f){check(f!=null && f.getRequestKey()!=null && f.getLoanId()!=null,"request");money(f.getAmount());check(f.getEffectiveDate()!=null && f.getEffectiveDate().getYear()>=1 && !f.getEffectiveDate().isAfter(clock.today()),"date");text(f.getReference(),100,true);text(f.getEvidence(),500,false);text(f.getNotes(),500,false);}
    static void filter(Filter f,boolean posting){check(f!=null && f.search()!=null && f.search().length()<=100 && f.page()>=0 && f.page()<=10000 && com.sacco.mvp.accounting.dto.RegisterSort.valid(f.sort(),posting?Set.of("number","product","client","date","amount","reference","status"):Set.of("number","product","client","date","amount","balance","status")),"filter");check(f.from()==null || f.through()==null || !f.from().isAfter(f.through()),"date");check((posting?Set.of("","POSTED","REVERSED","REVERSAL"):Set.of("","RECORDED","WITHDRAWN","DISBURSED","PAR","DEFAULTED","PAID","WRITTEN_OFF","DISBURSEMENT_REVERSED")).contains(f.status()),"filter");}
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
        check(Set.of("money","principal","interest").contains(category) && search!=null && search.length()<=100 && page>=0 && page<=10000,"filter");
        return records.accounts(actor,category.toUpperCase(Locale.ROOT),search,page);
    }
    @Transactional(readOnly=true)
    public Choice postingAccount(AppUserPrincipal actor,UUID loan,UUID id,String category) {
        accounting.requireActor(actor,"DISBURSEMENT_QUEUE_DISBURSE");records.record(actor,loan,false);
        check(allowedTeller(loans.findById(loan).orElseThrow(),actor.getMemberId()),"customTeller");
        check(Set.of("MONEY","PRINCIPAL","INTEREST").contains(category),"filter");
        return records.account(actor,id,category,false);
    }

    private Map<String,Object> recordedPolicy(AppUserPrincipal a,LoanProductSetting selectedProduct,String label,Integer guarantors) {
        var policy=new LinkedHashMap<String,Object>(workflows.snapshotData(workflows.resolveForProduct(a.getSaccoId(),selectedProduct)));
        policy.put("collateralRequired",selectedProduct.isCollateralRequired());policy.put("collateralCoverageRatio",selectedProduct.getResolvedMinCollateralCoverageRatio());
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
