package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.*;
import com.sacco.mvp.accounting.business.repository.BusinessAccountingRepository;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import com.sacco.mvp.accounting.policy.*;
import com.sacco.mvp.accounting.policy.AccountingPolicyService.PolicySnapshot;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class BusinessAccountingService {
    private final BusinessAccountingRepository sources;
    private final GeneralLedgerService ledger;
    private final AccountingPolicyService policies;
    private final LoanRepaymentLedgerService repayments;
    private final ManagerService manager;
    private final AccessControlService access;
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final SaccoRegistryService institutions;
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    private static final Set<String> MONEY_KEYS=Set.of("CASH","BANK","MOBILE_MONEY");
    private static boolean isReversal(Kind kind){return kind==Kind.BUSINESS_REVERSAL || kind==Kind.LOAN_REPAYMENT_REVERSAL;}
    private record Plan(List<Line> lines,UUID root,BigDecimal delta,BigDecimal principal,BigDecimal interest,BigDecimal fees) {}

    @Transactional
    public Document create(AppUserPrincipal actor,Command command) {
        authorize(actor,UserClaim.ACCOUNTING_BUSINESS_CREATE);validate(command);
        if(command.supplierId()!=null) require(sources.supplierActive(actor.getSaccoId(),actor.getStationId(),command.supplierId()),"supplier");
        if(command.relatedDocumentId()!=null && command.kind()!=Kind.INTERNAL_TRANSFER_IN) related(actor,command,true);
        sources.lockRequest(actor.getSaccoId(),actor.getStationId(),command.requestKey());
        String hash=hash(sources.payload(command));
        var prior=sources.byRequest(actor.getSaccoId(),actor.getStationId(),command.requestKey());
        if(prior.isPresent()) {
            require(prior.get().makerId().equals(actor.getMemberId()) && sources.payload(prior.get().command()).equals(sources.payload(command)),"retry");return prior.get();
        }
        var doc=new Document(UUID.randomUUID(),actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),null,command,"DRAFT",null,null,null,clock.now(),null,ZERO,ZERO,ZERO);
        sources.insert(doc,hash);event(actor,doc,"SOURCE_DRAFTED");return doc;
    }
    @Transactional
    public Document submit(AppUserPrincipal actor,UUID id) {
        authorize(actor,UserClaim.ACCOUNTING_BUSINESS_CREATE);var d=scoped(actor,id,true);
        require(actor.getMemberId().equals(d.makerId()),"maker");
        if(Set.of("SUBMITTED","POSTED").contains(d.state()))return d;
        require("DRAFT".equals(d.state()),"state");
        var policy=policies.requireApprovedLocalPolicy(d.institutionId(),d.command().effectiveDate());policies.requireAllowedPosting(policy,d.command().kind().event());
        var p=plan(actor,d,policy);
        var command=new JournalCommand(d.command().requestKey(),d.id().toString(),d.command().effectiveDate(),d.command().evidenceReference(),d.command().description(),p.lines());
        var j=isReversal(d.command().kind())
            ?ledger.draftSourceReversal(actor,related(actor,d.command(),true).journalId(),command)
            :ledger.draftSourceEvent(actor,d.command().kind().event(),command);
        sources.submitted(d.id(),j.id(),p.principal(),p.interest(),p.fees());event(actor,d,"SOURCE_SUBMITTED");return scoped(actor,id,false);
    }
    @Transactional
    public Document approveAndPost(AppUserPrincipal actor,UUID id,String evidence,boolean confirmed) {
        authorize(actor,UserClaim.ACCOUNTING_BUSINESS_APPROVE);text(evidence,500);require(confirmed,"confirmation");
        var d=scoped(actor,id,true);require(!d.makerId().equals(actor.getMemberId()),"checker");
        if("POSTED".equals(d.state())){require(actor.getMemberId().equals(d.checkerId()) && evidence.equals(d.approvalEvidence()),"retry");return d;}
        require("SUBMITTED".equals(d.state()),"state");
        var policy=policies.requireApprovedLocalPolicy(d.institutionId(),d.command().effectiveDate());var p=plan(actor,d,policy);
        var journal=ledger.journal(actor,d.journalId());require(sameLines(journal.lines(),p.lines()),"changedBalance");
        sources.posting(id,actor.getMemberId(),evidence);
        if(isReversal(d.command().kind()))ledger.approveAndPostSourceReversal(actor,d.journalId(),d.id().toString(),evidence);
        else ledger.approveAndPostSourceEvent(actor,d.journalId(),d.command().kind().event(),d.id().toString(),evidence);
        UUID loanTransaction=null;Command c=d.command();
        if(c.kind()==Kind.LOAN_REPAYMENT) {
            authorize(actor,UserClaim.LOAN_REPAYMENTS_CREATE);
            var receipt=repayments.post(c.loanId(),actor,new LoanRepaymentLedgerService.PaymentCommand(c.amount(),c.effectiveDate(),channel(c.moneyAccountKey()),c.channelReference(),c.requestKey()));
            require(receipt.principal().compareTo(p.principal())==0 && receipt.interest().compareTo(p.interest())==0,"changedBalance");loanTransaction=receipt.id();
        } else if(c.kind()==Kind.LOAN_DISBURSEMENT) {
            manager.disburseLoan(c.loanId(),actor.getMemberId(),c.effectiveDate(),c.firstRepaymentDate(),c.frequency(),c.installmentAmount(),ZERO,c.loanNumber(),c.channelReference(),c.description(),null);
        } else if(c.kind()==Kind.LOAN_REPAYMENT_REVERSAL) {
            authorize(actor,UserClaim.LOAN_REPAYMENTS_REVERSE);
            var original=related(actor,c,true);require(original.loanTransactionId()!=null,"source");
            loanTransaction=repayments.reverseAt(c.loanId(),original.loanTransactionId(),actor,c.requestKey(),c.description(),c.effectiveDate()).id();
        }
        if(p.root()!=null && p.delta().signum()!=0)sources.control(id,p.root(),d.institutionId(),d.branchId(),p.delta(),clock.now());
        if(c.kind()==Kind.ASSET_PURCHASE)sources.asset(d);
        if(c.kind()==Kind.DEPRECIATION)sources.depreciate(c.relatedDocumentId(),c.amount());
        if(c.kind()==Kind.ASSET_DISPOSAL)sources.dispose(c.relatedDocumentId());
        sources.posted(id,loanTransaction,clock.now());event(actor,d,"SOURCE_POSTED");return scoped(actor,id,false);
    }
    @Transactional
    public Document reject(AppUserPrincipal actor,UUID id,String evidence) {
        authorize(actor,UserClaim.ACCOUNTING_BUSINESS_APPROVE);text(evidence,500);var d=scoped(actor,id,true);
        require(!d.makerId().equals(actor.getMemberId()),"checker");
        if("REJECTED".equals(d.state())){
            require(actor.getMemberId().equals(d.checkerId()) && evidence.equals(d.approvalEvidence()),"retry");return d;
        }
        require(Set.of("DRAFT","SUBMITTED").contains(d.state()),"state");
        if(d.journalId()!=null)ledger.cancelSourceEvent(actor,d.journalId(),d.command().kind().event(),d.id().toString(),evidence);
        sources.reject(id,actor.getMemberId(),evidence);event(actor,d,"SOURCE_REJECTED");return scoped(actor,id,false);
    }
    @Transactional(readOnly=true)
    public com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.Page<Document> list(AppUserPrincipal actor,int page) {
        authorize(actor,UserClaim.ACCOUNTING_BUSINESS_VIEW);int n=page(page);var rows=sources.list(actor.getSaccoId(),actor.getStationId(),n*25,26);
        return new com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.Page<>(rows.stream().limit(25).toList(),n,rows.size()>25);
    }
    @Transactional(readOnly=true) public Document view(AppUserPrincipal actor,UUID id){authorize(actor,UserClaim.ACCOUNTING_BUSINESS_VIEW);return scoped(actor,id,false);}
    @Transactional public UUID supplier(AppUserPrincipal actor,String name,String evidence){authorize(actor,UserClaim.ACCOUNTING_BUSINESS_CREATE);text(name,160);text(evidence,500);var id=sources.supplier(actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),name,evidence,clock.now());audit.log("ACCOUNTING",id,"SUPPLIER_REGISTERED",actor.getMemberId(),null,Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId()));return id;}
    @Transactional(readOnly=true) public com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.Page<Supplier> suppliers(AppUserPrincipal actor,int page){authorize(actor,UserClaim.ACCOUNTING_BUSINESS_VIEW);int n=page(page);var rows=sources.suppliers(actor.getSaccoId(),actor.getStationId(),n*25,26);return new com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.Page<>(rows.stream().limit(25).toList(),n,rows.size()>25);}
    @Transactional(readOnly=true) public com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.Page<Asset> assets(AppUserPrincipal actor,int page){authorize(actor,UserClaim.ACCOUNTING_BUSINESS_VIEW);int n=page(page);var rows=sources.assets(actor.getSaccoId(),actor.getStationId(),n*25,26);return new com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.Page<>(rows.stream().limit(25).toList(),n,rows.size()>25);}

    private Plan plan(AppUserPrincipal actor,Document d,PolicySnapshot policy) {
        Command c=d.command();policies.requireAllowedPosting(policy,c.kind().event());
        require(!c.effectiveDate().isAfter(clock.today()),"date");
        BigDecimal amount=money(c.amount()),principal=ZERO,interest=ZERO,fees=ZERO,delta=ZERO;UUID root=null;
        String debit=null,credit=null;List<Line> lines=new ArrayList<>();
        String cash=c.moneyAccountKey();
        switch(c.kind()) {
            case LOAN_DISBURSEMENT -> {principal=sources.readyLoan(d.institutionId(),d.branchId(),c.loanId()).orElseThrow(()->invalid("loan"));require(principal.compareTo(amount)==0 && c.loanNumber()!=null && c.loanNumber().matches("[0-9]{4,20}") && c.firstRepaymentDate()!=null && c.frequency()!=null && c.installmentAmount()!=null,"contract");debit="LOAN_PRINCIPAL";credit=cash;}
            case LOAN_REPAYMENT -> {
                var loan=sources.loan(d.institutionId(),d.branchId(),c.loanId(),c.effectiveDate(),true).orElseThrow(()->invalid("loan"));
                require(!c.effectiveDate().isBefore(loan.disbursementDate()) && amount.compareTo(loan.dueInterest().add(loan.duePrincipal()))<=0,"advance");
                interest=amount.min(loan.dueInterest());principal=amount.subtract(interest);
                add(lines,policy,cash,amount,true);add(lines,policy,"LOAN_PRINCIPAL",principal,false);
                String recognition=policy.decisions().get(PolicyDecision.INTEREST_RECOGNITION);
                require(Set.of("CASH_DUE_INTEREST_V1","ACCRUAL_DUE_INTEREST_V1").contains(recognition),"recognition");
                if("ACCRUAL_DUE_INTEREST_V1".equals(recognition)) {
                    BigDecimal accrued=sources.loanInterestReceivable(d.institutionId(),d.branchId(),c.loanId(),c.effectiveDate());
                    require(accrued.compareTo(interest)>=0,"accrual");
                    credit="INTEREST_RECEIVABLE";
                } else credit="INTEREST_INCOME";
                add(lines,policy,credit,interest,false);
            }
            case INTEREST_ACCRUAL -> {
                require("ACCRUAL_DUE_INTEREST_V1".equals(policy.decisions().get(PolicyDecision.INTEREST_RECOGNITION)),"recognition");
                var loan=sources.loan(d.institutionId(),d.branchId(),c.loanId(),c.effectiveDate(),true).orElseThrow(()->invalid("loan"));
                BigDecimal accrued=sources.loanInterestReceivable(d.institutionId(),d.branchId(),c.loanId(),c.effectiveDate());
                require(amount.compareTo(loan.dueInterest().subtract(accrued))<=0,"accrual");debit="INTEREST_RECEIVABLE";credit="INTEREST_INCOME";root=d.id();delta=amount;
            }
            case LOAN_ADVANCE,UNMATCHED_RECEIPT -> {debit=cash;credit="UNAPPLIED_FUNDS";root=d.id();delta=amount;}
            case REFUND -> {var r=related(actor,c,true);require(Set.of(Kind.LOAN_ADVANCE,Kind.UNMATCHED_RECEIPT).contains(r.command().kind()),"source");remaining(r,amount);root=r.id();delta=amount.negate();debit="UNAPPLIED_FUNDS";credit=cash;}
            case EXPENSE_INVOICE,STAFF_REIMBURSEMENT -> {debit="OPERATING_EXPENSE";credit="SUPPLIER_PAYABLE";root=d.id();delta=amount;}
            case DIRECT_EXPENSE -> {debit="OPERATING_EXPENSE";credit=cash;}
            case PAYABLE_PAYMENT,SUPPLIER_CREDIT -> {var r=related(actor,c,true);require(Set.of(Kind.EXPENSE_INVOICE,Kind.STAFF_REIMBURSEMENT).contains(r.command().kind()),"source");remaining(r,amount);root=r.id();delta=amount.negate();debit="SUPPLIER_PAYABLE";credit=c.kind()==Kind.PAYABLE_PAYMENT?cash:"OPERATING_EXPENSE";}
            case CAPITAL_RECEIPT -> {debit=cash;credit="OWNER_CAPITAL";root=d.id();delta=amount;}
            case OWNER_DISTRIBUTION -> {debit="OWNER_DISTRIBUTIONS";credit=cash;}
            case FUNDING_RECEIPT -> {debit=cash;credit="FUNDING_PRINCIPAL";root=d.id();delta=amount;}
            case FUNDING_PRINCIPAL_PAYMENT -> {var r=related(actor,c,true);require(r.command().kind()==Kind.FUNDING_RECEIPT,"source");remaining(r,amount);root=r.id();delta=amount.negate();debit="FUNDING_PRINCIPAL";credit=cash;}
            case FUNDING_INTEREST -> {var r=related(actor,c,true);require(r.command().kind()==Kind.FUNDING_RECEIPT,"source");debit="FUNDING_INTEREST_EXPENSE";credit=cash;}
            case ASSET_PURCHASE -> {debit="FIXED_ASSET";credit=cash;}
            case DEPRECIATION -> {var asset=sources.asset(d.institutionId(),d.branchId(),c.relatedDocumentId(),true).orElseThrow(()->invalid("asset"));require(!asset.disposed() && !c.effectiveDate().isBefore(asset.acquiredOn()) && amount.compareTo(asset.cost().subtract(asset.depreciation()))<=0,"asset");debit="DEPRECIATION_EXPENSE";credit="ACCUMULATED_DEPRECIATION";}
            case ASSET_DISPOSAL -> {var asset=sources.asset(d.institutionId(),d.branchId(),c.relatedDocumentId(),true).orElseThrow(()->invalid("asset"));require(!asset.disposed() && !c.effectiveDate().isBefore(asset.acquiredOn()),"asset");add(lines,policy,cash,amount,true);add(lines,policy,"ACCUMULATED_DEPRECIATION",asset.depreciation(),true);add(lines,policy,"FIXED_ASSET",asset.cost(),false);var difference=amount.add(asset.depreciation()).subtract(asset.cost());add(lines,policy,difference.signum()>=0?"DISPOSAL_GAIN":"DISPOSAL_LOSS",difference.abs(),difference.signum()<0);}
            case PREPAYMENT -> {debit="PREPAYMENT_ASSET";credit=cash;root=d.id();delta=amount;}
            case PREPAYMENT_RELEASE -> {var r=related(actor,c,true);require(r.command().kind()==Kind.PREPAYMENT,"source");remaining(r,amount);root=r.id();delta=amount.negate();debit="OPERATING_EXPENSE";credit="PREPAYMENT_ASSET";}
            case ACCRUAL -> {debit="OPERATING_EXPENSE";credit="ACCRUED_LIABILITY";root=d.id();delta=amount;}
            case ACCRUAL_PAYMENT -> {var r=related(actor,c,true);require(r.command().kind()==Kind.ACCRUAL,"source");remaining(r,amount);root=r.id();delta=amount.negate();debit="ACCRUED_LIABILITY";credit=cash;}
            case TAX_LIABILITY -> {debit="TAX_EXPENSE";credit="TAX_PAYABLE";root=d.id();delta=amount;}
            case TAX_PAYMENT -> {var r=related(actor,c,true);require(r.command().kind()==Kind.TAX_LIABILITY,"source");remaining(r,amount);root=r.id();delta=amount.negate();debit="TAX_PAYABLE";credit=cash;}
            case INTERNAL_TRANSFER_OUT -> {require(c.destinationBranch()!=null && !c.destinationBranch().equals(d.branchId()) && sources.branchActive(d.institutionId(),c.destinationBranch()),"branch");debit="INTERNAL_DUE_FROM";credit=cash;root=d.id();delta=amount;}
            case INTERNAL_TRANSFER_IN -> {var r=sources.incomingTransfer(d.institutionId(),d.branchId(),c.relatedDocumentId(),true).orElseThrow(()->invalid("source"));require(!sources.receivedTransfer(r.id()) && !c.effectiveDate().isBefore(r.command().effectiveDate()) && amount.compareTo(r.command().amount())==0,"transfer");debit=cash;credit="INTERNAL_DUE_TO";}
            case LOAN_REPAYMENT_REVERSAL,BUSINESS_REVERSAL -> {
                authorize(actor,UserClaim.ACCOUNTING_BUSINESS_REVERSE);policies.requireAllowedPosting(policy,PostingEvent.REVERSAL);var r=related(actor,c,true);
                require(!r.makerId().equals(actor.getMemberId()) && !Objects.equals(r.checkerId(),actor.getMemberId()) && r.command().amount().compareTo(amount)==0,"reversal");
                require(c.kind()==Kind.LOAN_REPAYMENT_REVERSAL?r.command().kind()==Kind.LOAN_REPAYMENT:!Set.of(Kind.LOAN_DISBURSEMENT,Kind.LOAN_REPAYMENT,Kind.LOAN_REPAYMENT_REVERSAL,Kind.BUSINESS_REVERSAL,Kind.ASSET_PURCHASE,Kind.DEPRECIATION,Kind.ASSET_DISPOSAL,Kind.INTERNAL_TRANSFER_IN,Kind.INTERNAL_TRANSFER_OUT).contains(r.command().kind()),"reversal");
                if(c.kind()==Kind.LOAN_REPAYMENT_REVERSAL)require(Objects.equals(c.loanId(),r.command().loanId()),"loan");
                lines.addAll(ledger.journal(actor,r.journalId()).lines().stream().map(l->new Line(l.accountId(),l.credit(),l.debit())).toList());
                if(Set.of(Kind.EXPENSE_INVOICE,Kind.STAFF_REIMBURSEMENT,Kind.FUNDING_RECEIPT,Kind.PREPAYMENT,Kind.ACCRUAL,Kind.TAX_LIABILITY,Kind.LOAN_ADVANCE,Kind.UNMATCHED_RECEIPT,Kind.CAPITAL_RECEIPT).contains(r.command().kind())) {require(sources.remaining(r.id()).compareTo(amount)==0,"dependentEntries");root=r.id();delta=amount.negate();}
                else if(r.command().relatedDocumentId()!=null){root=r.command().relatedDocumentId();related(actor,r.command(),true);delta=amount;}
                principal=r.principal();interest=r.interest();fees=r.fees();
            }
            default -> throw invalid("unsupported");
        }
        if(debit!=null){add(lines,policy,debit,amount,true);add(lines,policy,credit,amount,false);}
        require(lines.size()>=2,"mapping");return new Plan(List.copyOf(lines),root,delta,principal,interest,fees);
    }
    private void remaining(Document root,BigDecimal amount){require(sources.remaining(root.id()).compareTo(amount)>=0,"balance");}
    private Document related(AppUserPrincipal actor,Command c,boolean lock){require(c.relatedDocumentId()!=null,"source");var r=scoped(actor,c.relatedDocumentId(),lock);require("POSTED".equals(r.state()) && !c.effectiveDate().isBefore(r.command().effectiveDate()),"source");return r;}
    private Document scoped(AppUserPrincipal actor,UUID id,boolean lock){return sources.document(actor.getSaccoId(),actor.getStationId(),id,lock).orElseThrow(()->new AccessDeniedException("Source outside current branch"));}
    private void add(List<Line> lines,PolicySnapshot p,String key,BigDecimal amount,boolean debit){if(amount.signum()==0)return;UUID id=p.accountMappings().get(key);require(id!=null,"mapping");lines.add(new Line(id,debit?amount:ZERO,debit?ZERO:amount));}
    private LoanRepaymentTransaction.Channel channel(String key){require(MONEY_KEYS.contains(key),"channel");return LoanRepaymentTransaction.Channel.valueOf(key);}
    private void authorize(AppUserPrincipal actor,UserClaim claim) {
        if(actor==null || !actor.isStaffSession() || actor.isPlatformIdentity() || !access.has(actor,claim) || actor.getMemberId()==null || actor.getSaccoId()==null || actor.getStationId()==null)throw new AccessDeniedException("Accounting staff claim required");
        var m=members.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Staff unavailable"));
        if(!m.isStaffAccessActive() || m.getStatus()!=MemberStatus.ACTIVE || m.getActiveStaffRolesResolved().contains(Position.ADMIN) || !Objects.equals(m.getSaccoId(),actor.getSaccoId()) || !Objects.equals(m.getStationId(),actor.getStationId()) || !claims.effectiveClaims(m.getId(),m.getActiveStaffRolesResolved(),m.isMemberAccess()).contains(claim)
            || institutions.findActiveSacco(actor.getSaccoId()).isEmpty() || institutions.findStation(actor.getSaccoId(),actor.getStationId()).filter(SaccoStation::isActive).filter(s->s.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty())throw new AccessDeniedException("Accounting permission revoked");
    }
    static void validate(Command c){require(c!=null && c.requestKey()!=null && c.kind()!=null && c.effectiveDate()!=null && c.effectiveDate().getYear()>=1 && c.effectiveDate().getYear()<=9999,"validation");require(money(c.amount()).signum()>0,"amount");text(c.description(),500);text(c.evidenceReference(),500);require(c.moneyAccountKey()==null || MONEY_KEYS.contains(c.moneyAccountKey()),"channel");require(c.channelReference()==null || (!c.channelReference().isBlank() && c.channelReference().length()<=100),"reference");
        if(Set.of(Kind.LOAN_DISBURSEMENT,Kind.LOAN_REPAYMENT,Kind.LOAN_ADVANCE,Kind.UNMATCHED_RECEIPT,Kind.REFUND,Kind.DIRECT_EXPENSE,Kind.PAYABLE_PAYMENT,Kind.CAPITAL_RECEIPT,Kind.OWNER_DISTRIBUTION,Kind.FUNDING_RECEIPT,Kind.FUNDING_PRINCIPAL_PAYMENT,Kind.FUNDING_INTEREST,Kind.ASSET_PURCHASE,Kind.ASSET_DISPOSAL,Kind.PREPAYMENT,Kind.ACCRUAL_PAYMENT,Kind.TAX_PAYMENT,Kind.INTERNAL_TRANSFER_OUT,Kind.INTERNAL_TRANSFER_IN,Kind.EARLY_SETTLEMENT,Kind.RECOVERY).contains(c.kind())){require(c.moneyAccountKey()!=null && MONEY_KEYS.contains(c.moneyAccountKey()),"channel");text(c.channelReference(),100);}}
    private boolean sameLines(List<Line> a,List<Line> b){var comparator=Comparator.comparing((Line l)->l.accountId().toString()).thenComparing(Line::debit).thenComparing(Line::credit);return a.stream().sorted(comparator).toList().equals(b.stream().sorted(comparator).toList());}
    private void event(AppUserPrincipal actor,Document d,String action){audit.logEvent("ACCOUNTING",d.id(),action,actor.getMemberId(),AuditEventStatus.SUCCESS,action,"SOURCE",d.id().toString(),d.institutionId(),d.branchId(),Map.of("kind",d.command().kind().name(),"amount",d.command().amount(),"effectiveDate",d.command().effectiveDate()));}
    static BigDecimal money(BigDecimal amount){require(amount!=null && amount.precision()-amount.scale()<=16,"amount");try{return amount.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException e){throw invalid("amount");}}
    private static void text(String s,int max){require(s!=null && !s.isBlank() && s.length()<=max,"validation");}
    private static int page(int p){require(p>=0 && p<=10000,"validation");return p;}
    private static void require(boolean c,String key){if(!c)throw invalid(key);}
    private static IllegalArgumentException invalid(String key){return new IllegalArgumentException("finance.business.error."+key);}
    private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
