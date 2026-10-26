package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanRepaymentLedgerService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int MAX_INSTALLMENTS = 600;
    private final LoanLedgerRepository ledgers;
    private final LoanLedgerInstallmentRepository installments;
    private final LoanRepaymentTransactionRepository transactions;
    private final LoanRepaymentAllocationRepository allocations;
    private final LoanJournalEntryRepository journal;
    private final LoanApplicationRepository loans;
    private final ObjectMapper mapper;
    private final ApplicationClock clock;
    private final AccessControlService access;
    private final AuditService audit;
    private final com.sacco.mvp.accounting.loans.service.LoanPostingGuard accountingGuard;
    private final com.sacco.mvp.accounting.repayment.service.LoanPrepaymentService prepayments;

    // Called only by the actual disbursement transaction; never infer an opening balance for an old loan.
    @Transactional(propagation = Propagation.MANDATORY)
    public void openAtDisbursement(LoanApplication loan) {
        if (loan.getTopUpSourceLoanId() != null) {
            return;
        }
        openLedger(loan);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void openAtTopUp(LoanApplication loan) {
        require(loan.getTopUpSourceLoanId()!=null && accountingGuard.recordedLoan(loan.getId()),"state");
        openLedger(loan);
    }

    private void openLedger(LoanApplication loan) {
        require(loan.getStatus() == LoanStatus.DISBURSED && loan.getStationId() != null
            && !loan.getStationId().isBlank() && loan.getDisbursementDate() != null
            && !loan.getDisbursementDate().isAfter(clock.today()), "schedule");
        require(!ledgers.existsById(loan.getId()), "duplicate");
        BigDecimal principal = money(loan.getAmount());
        require(principal.signum() > 0, "amount");
        List<LoanLedgerInstallment> rows = parseSchedule(loan);
        require(rows.stream().map(LoanLedgerInstallment::getPrincipal).reduce(ZERO, BigDecimal::add)
            .compareTo(principal) == 0, "schedule");
        LoanLedger ledger = LoanLedger.builder().loanApplicationId(loan.getId()).saccoId(loan.getSaccoId())
            .stationId(loan.getStationId()).loanId(loan.getLoanId()).applicantMemberId(loan.getApplicantMemberId())
            .disbursementDate(loan.getDisbursementDate()).principal(principal).principalPaid(ZERO)
            .interestPaid(ZERO).nextSequence(1).createdAt(clock.now()).build();
        ledgers.saveAndFlush(ledger);
        installments.saveAll(rows);
        installments.flush();
        prepayments.bind(ledger,mapper.readValue(loan.getFinancialSnapshot(),new TypeReference<Map<String,Object>>() {}));
        UUID voucher = UUID.randomUUID();
        journal.saveAll(List.of(entry(ledger, null, voucher, "LOAN_PRINCIPAL", principal, ZERO, loan.getDisbursementDate()),
            entry(ledger, null, voucher, "DISBURSEMENT_CLEARING", ZERO, principal, loan.getDisbursementDate())));
        updateProjection(ledger, loan, rows);
    }

    @Transactional(readOnly = true)
    public boolean hasLedger(UUID loanId) {
        return ledgers.existsById(loanId);
    }

    public record PaymentBalance(BigDecimal duePrincipal, BigDecimal dueInterest, BigDecimal outstandingPrincipal) {}
    @Transactional(readOnly = true)
    public PaymentBalance paymentBalance(UUID id, AppUserPrincipal actor, LocalDate effectiveDate) {
        LoanLedger ledger = readableLedger(id, actor);
        require(effectiveDate != null && !effectiveDate.isBefore(ledger.getDisbursementDate()) && !effectiveDate.isAfter(clock.today()), "date");
        Totals t = totals(rows(id), effectiveDate);
        if(transactions.settledThroughTopUp(id))return new PaymentBalance(ZERO,ZERO,ZERO);
        return new PaymentBalance(t.duePrincipal(), t.dueInterest(), ledger.getPrincipal().subtract(ledger.getPrincipalPaid()));
    }

    public record SettlementQuote(UUID loanId,String loanNumber,LocalDate date,long sequence,BigDecimal principal,
        BigDecimal interest,BigDecimal futureInterest,BigDecimal charges,BigDecimal total,LoanPrepaymentTerms terms,boolean legacy) {}
    @Transactional(readOnly=true)
    public SettlementQuote settlementQuote(UUID id,AppUserPrincipal actor,LocalDate date){
        LoanLedger l=readableLedger(id,actor);
        require(date!=null && !date.isBefore(clock.today()) && !date.isAfter(clock.today().plusYears(1)),"date");
        require(!transactions.settledThroughTopUp(id),"state");
        var status=loans.findById(id).orElseThrow().getStatus();require(Set.of(LoanStatus.DISBURSED,LoanStatus.PAR,LoanStatus.DEFAULTED).contains(status),"state");
        var due=totals(rows(id),date);var principal=l.getPrincipal().subtract(l.getPrincipalPaid());var policy=prepayments.terms(id);
        var fees=prepayments.charges(l);
        return new SettlementQuote(id,l.getLoanId(),date,l.getNextSequence(),principal,due.dueInterest(),due.futureInterest(),fees,principal.add(due.dueInterest()).add(fees),policy.map(p->p.rules()).orElse(null),policy.isEmpty());
    }
    @Transactional
    public void adoptPrepaymentTerms(UUID id,AppUserPrincipal actor,String reason){
        requireStaff(actor,UserClaim.LOAN_INTEREST_WAIVE);LoanLedger l=lockedLedger(id,actor);readableLedger(id,actor);
        require(l.getNextSequence()==1 && l.getPrincipalPaid().signum()==0 && l.getInterestPaid().signum()==0,"state");
        require(ledgers.latestAdjustmentDate(id,l.getSaccoId(),l.getStationId())==null,"state");
        var loan=loans.findById(id).orElseThrow();require(loan.getStatus()==LoanStatus.DISBURSED,"state");
        var snapshot=mapper.readValue(loan.getFinancialSnapshot(),new TypeReference<Map<String,Object>>() {});
        prepayments.adopt(l,actor,InterestMethod.valueOf(String.valueOf(snapshot.get("interestMethod"))),reason);
    }
    @Transactional(readOnly=true)
    public List<com.sacco.mvp.accounting.repayment.repository.LoanPrepaymentRepository.Waiver> interestWaivers(UUID id,AppUserPrincipal actor,int page){
        return prepayments.history(readableLedger(id,actor),page);
    }
    @Transactional
    public UUID waiveInterest(UUID id,AppUserPrincipal actor,UUID key,LocalDate date,BigDecimal amount,String reason,UUID reverses){
        requireStaff(actor,UserClaim.LOAN_INTEREST_WAIVE);
        if(reverses!=null)requireStaff(actor,accountingGuard.recordedLoan(id)?UserClaim.LOAN_RECORDING_REVERSE:UserClaim.LOAN_REPAYMENTS_REVERSE);
        // Recorded repayment commands lock the owning loan before its ledger.
        if(accountingGuard.recordedLoan(id))loans.lockFinancialRecord(id,actor.getSaccoId(),actor.getStationId()).orElseThrow(()->new AccessDeniedException("Forbidden"));
        LoanLedger l=lockedLedger(id,actor);readableLedger(id,actor);
        var loan=loans.findById(id).orElseThrow();require(Set.of(LoanStatus.DISBURSED,LoanStatus.PAR,LoanStatus.DEFAULTED).contains(loan.getStatus()),"state");
        require(date!=null,"date");requireAfterAdjustment(l,date);var rows=rows(id);
        UUID result=prepayments.waive(l,rows,actor,key,date,money(amount),reason,reverses);installments.saveAll(rows);installments.flush();
        updateProjection(l,loan,rows);return result;
    }

    public record PreviewAllocation(UUID installmentId,BigDecimal principal,BigDecimal interest,BigDecimal excluded) {}
    public record StagedPayment(LocalDate date,List<PreviewAllocation> allocations) {}
    public record PaymentPreview(PaymentBalance before,BigDecimal principal,BigDecimal interest,BigDecimal principalBalance,List<PreviewAllocation> allocations) {}
    @Transactional(readOnly=true)
    public PaymentPreview previewPayment(UUID id,AppUserPrincipal actor,LocalDate date,BigDecimal amount,List<StagedPayment> prior){
        var l=readableLedger(id,actor);require(date!=null && !date.isBefore(l.getDisbursementDate()) && !date.isAfter(clock.today()),"date");
        var rows=rows(id).stream().map(r->new LoanLedgerInstallment(r.getId(),r.getLoanApplicationId(),r.getInstallmentNumber(),r.getDueDate(),r.getPrincipal(),r.getInterest(),r.getPrincipalPaid(),r.getInterestPaid(),r.getInterestExcluded(),r.getInterestWaived())).toList();
        var earned=new HashMap<>(prepayments.earned(id,date));var policy=prepayments.terms(id);
        for(var p:prior){require(p.date()!=null && !p.date().isAfter(date) && p.allocations()!=null,"date");
            var stagedParts=new HashMap<UUID,PreviewAllocation>();p.allocations().forEach(a->stagedParts.put(a.installmentId(),a));
            LocalDate start=l.getDisbursementDate();for(var row:rows){
                var part=stagedParts.get(row.getId());
                if(part!=null){row.setPrincipalPaid(row.getPrincipalPaid().add(part.principal()));row.setInterestPaid(row.getInterestPaid().add(part.interest()));row.setInterestExcluded(row.getInterestExcluded().add(part.excluded()));
                    if(policy.isPresent() && part.excluded().signum()>0){var from=p.date().isAfter(start)?p.date():start;
                        var accruedReduction=part.excluded().multiply(LoanAmortizationCalculator.periodFraction(from,row.getDueDate(),date,policy.get().rules().dayBasis()));
                        earned.put(row.getId(),earned.getOrDefault(row.getId(),ZERO).subtract(accruedReduction).max(ZERO));}}
                start=row.getDueDate();
            }
        }
        return calculatePayment(l,rows,date,amount,earned,policy,false);
    }

    private PaymentPreview calculatePayment(LoanLedger l,List<LoanLedgerInstallment> rows,LocalDate date,BigDecimal amount,
        Map<UUID,BigDecimal> earned,Optional<com.sacco.mvp.accounting.repayment.repository.LoanPrepaymentRepository.Terms> policy,boolean principalFirst){
        var duePrincipal=ZERO;var dueInterest=ZERO;var outstanding=ZERO;
        for(var row:rows){var p=row.getPrincipal().subtract(row.getPrincipalPaid());outstanding=outstanding.add(p);if(!row.getDueDate().isAfter(date))duePrincipal=duePrincipal.add(p);dueInterest=dueInterest.add(earnedInterest(row,date,earned).subtract(row.getInterestPaid()).max(ZERO));}
        var split=com.sacco.mvp.accounting.repayment.service.RepaymentAllocationCalculator.allocate(money(amount),duePrincipal,dueInterest,outstanding,principalFirst?com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.AllocationOrder.PRINCIPAL_FIRST:com.sacco.mvp.accounting.repayment.dto.RepaymentPolicyDtos.AllocationOrder.INTEREST_FIRST);
        var parts=new LinkedHashMap<UUID,BigDecimal[]>();BigDecimal interestLeft=split.interest(),principalLeft=split.principal();
        for(var row:rows){var take=interestLeft.min(earnedInterest(row,date,earned).subtract(row.getInterestPaid()).max(ZERO));if(take.signum()>0){parts.computeIfAbsent(row.getId(),x->new BigDecimal[]{ZERO,ZERO,ZERO})[1]=take;row.setInterestPaid(row.getInterestPaid().add(take));interestLeft=interestLeft.subtract(take);}
            if(!row.getDueDate().isAfter(date)){take=principalLeft.min(row.getPrincipal().subtract(row.getPrincipalPaid()));if(take.signum()>0){parts.computeIfAbsent(row.getId(),x->new BigDecimal[]{ZERO,ZERO,ZERO})[0]=take;row.setPrincipalPaid(row.getPrincipalPaid().add(take));principalLeft=principalLeft.subtract(take);}}}
        var future=rows.stream().filter(r->r.getDueDate().isAfter(date)).toList();var advances=LoanAmortizationCalculator.allocateAdvance(principalLeft,future.stream().map(r->r.getPrincipal().subtract(r.getPrincipalPaid())).toList(),policy.map(p->p.rules().schedule()).orElse(LoanPrepaymentTerms.Schedule.NEXT_INSTALMENTS));var prepaid=new HashMap<UUID,BigDecimal>();
        for(int n=0;n<future.size();n++){var row=future.get(n);var take=advances.get(n);if(take.signum()>0){parts.computeIfAbsent(row.getId(),x->new BigDecimal[]{ZERO,ZERO,ZERO})[0]=take;prepaid.put(row.getId(),take);row.setPrincipalPaid(row.getPrincipalPaid().add(take));}}
        boolean full=split.principalBalance().signum()==0 && split.interest().compareTo(dueInterest)==0;
        var reductions=prepayments.reductions(l,rows,date,prepaid,earned,full);
        for(var row:rows){var reduction=reductions.getOrDefault(row.getId(),ZERO);if(policy.isEmpty() && full && row.getDueDate().isAfter(date))reduction=row.getInterest().subtract(row.getInterestPaid()).subtract(row.getInterestExcluded()).subtract(row.getInterestWaived());if(reduction.signum()>0)parts.computeIfAbsent(row.getId(),x->new BigDecimal[]{ZERO,ZERO,ZERO})[2]=reduction;}
        return new PaymentPreview(new PaymentBalance(duePrincipal,dueInterest,outstanding),split.principal(),split.interest(),split.principalBalance(),parts.entrySet().stream().map(e->new PreviewAllocation(e.getKey(),e.getValue()[0],e.getValue()[1],e.getValue()[2])).toList());
    }

    @Transactional(readOnly = true)
    public Page<LedgerListRow> list(AppUserPrincipal actor, String loanNumber, int page) {
        requireStaff(actor, UserClaim.LOAN_REPAYMENTS_VIEW);
        String search = loanNumber == null || loanNumber.isBlank() ? null : loanNumber.trim();
        require(search == null || search.matches("[0-9]{4,20}"), "search");
        return ledgers.balancesScoped(actor.getSaccoId(), actor.getStationId(), search,
            PageRequest.of(Math.max(0, Math.min(page, 10000)), 25,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("loanApplicationId"))))
            .map(l -> new LedgerListRow(l.getLoanApplicationId(), l.getLoanId(), l.getDisbursementDate(),
                l.getOutstandingPrincipal(), l.getPrincipalPaid(), l.getInterestPaid()));
    }

    @Transactional(readOnly = true)
    public Page<InstallmentRow> schedule(UUID loanId, AppUserPrincipal actor, int page) {
        require(page >= 0 && page < MAX_INSTALLMENTS / 25, "schedule");
        readableLedger(loanId, actor);
        var stored=installments.findAllByLoanApplicationIdOrderByInstallmentNumber(loanId, PageRequest.of(page, 25));
        if(stored.isEmpty())return stored.map(r->new InstallmentRow(r.getInstallmentNumber(),r.getDueDate(),r.getPrincipal(),r.getInterest(),r.getPrincipalPaid(),r.getInterestPaid()));
        var beginning=installments.scheduledAmountFrom(loanId,stored.getContent().getFirst().getInstallmentNumber());
        return new PageImpl<>(scheduleRows(stored.getContent(),beginning),stored.getPageable(),stored.getTotalElements());
    }

    @Transactional(readOnly = true)
    public LedgerView view(UUID loanId, AppUserPrincipal actor, int page, int schedulePage) {
        LoanLedger ledger = readableLedger(loanId, actor);
        List<LoanLedgerInstallment> rows = rows(loanId);
        Totals totals = totals(rows, clock.today());
        String financialState=loans.findById(loanId).map(l->l.getStatus().name()).orElse("");
        boolean cancelled=financialState.equals("DISBURSEMENT_REVERSED");
        if(cancelled || transactions.settledThroughTopUp(loanId))totals=new Totals(ZERO,ZERO,ZERO,ZERO,null);
        Page<LoanRepaymentTransaction> transactionPage = transactions.findByLoanApplicationIdOrderBySequenceDesc(loanId,
            PageRequest.of(Math.max(0, Math.min(page, 10000)), 25));
        Map<UUID, UUID> reversed = reversalIds(loanId, transactionPage.getContent().stream().map(LoanRepaymentTransaction::getId).toList());
        Page<Receipt> history = transactionPage.map(t -> receipt(t, ledger.getLoanId(), reversed.get(t.getId())));
        LoanRepaymentTransaction latest = transactions.latestUnreversed(loanId, PageRequest.of(0, 1)).stream()
            .filter(t->t.getKind()==LoanRepaymentTransaction.Kind.PAYMENT)
            .findFirst().orElse(null);
        List<JournalRow> entries = !actor.isStaffSession() ? List.of() : journal.findByLoanApplicationIdOrderByPostedAtDescId(loanId,
            PageRequest.of(0, 30)).stream().map(e -> new JournalRow(e.getVoucherId(), e.getEffectiveDate(),
                e.getAccountCode(), e.getDebit(), e.getCredit())).toList();
        PageRequest scheduleRequest = PageRequest.of(Math.max(0, Math.min(schedulePage, 10000)), 25);
        int from = (int) Math.min(scheduleRequest.getOffset(), rows.size());
        var displayRows=scheduleRows(rows,rows.stream().map(row->row.getPrincipal().add(row.getInterest())).reduce(BigDecimal.ZERO,BigDecimal::add));
        displayRows=com.sacco.mvp.support.ResourceTableSort.rows("repayments-loan-2",displayRows,Map.<String,java.util.Comparator<InstallmentRow>>of("number",java.util.Comparator.comparing(InstallmentRow::number),"due",java.util.Comparator.comparing(InstallmentRow::dueDate),"beginning",java.util.Comparator.comparing(InstallmentRow::beginningBalance),"payment",java.util.Comparator.comparing(InstallmentRow::scheduledPayment),"principal",java.util.Comparator.comparing(InstallmentRow::principal),"interest",java.util.Comparator.comparing(InstallmentRow::interest),"ending",java.util.Comparator.comparing(InstallmentRow::endingBalance),"principalPaid",java.util.Comparator.comparing(InstallmentRow::principalPaid),"interestPaid",java.util.Comparator.comparing(InstallmentRow::interestPaid)),java.util.Comparator.comparing(InstallmentRow::number));
        Page<InstallmentRow> schedule = new PageImpl<>(displayRows.subList(from, Math.min(from + 25, rows.size())), scheduleRequest, rows.size());
        return new LedgerView(loanId, ledger.getLoanId(), ledger.getStationId(), clock.today(),
            cancelled?ZERO:ledger.getPrincipal().subtract(ledger.getPrincipalPaid()), ledger.getPrincipalPaid(), ledger.getInterestPaid(),
            totals.duePrincipal(), totals.dueInterest(), totals.futureInterest(), totals.oldestOverdue(),
            schedule, history, latest == null ? null : latest.getId(),
            actor.isStaffSession() ? entries : List.of(),financialState,
            latest == null ? null : receipt(latest,ledger.getLoanId(),null));
    }

    @Transactional(readOnly = true)
    public Receipt receipt(UUID loanId, UUID transactionId, AppUserPrincipal actor) {
        LoanLedger ledger = readableLedger(loanId, actor);
        LoanRepaymentTransaction transaction = transactions.findById(transactionId)
            .filter(t -> t.getLoanApplicationId().equals(loanId)).orElseThrow(() -> new AccessDeniedException("Forbidden"));
        return receipt(transaction, ledger.getLoanId(), reversalIds(loanId, List.of(transactionId)).get(transactionId));
    }

    @Transactional
    public Receipt post(UUID loanId, AppUserPrincipal actor, PaymentCommand command) {
        requireStaff(actor, UserClaim.LOAN_REPAYMENTS_CREATE);
        return postInternal(loanId, actor, command);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Receipt postRecorded(UUID loanId, AppUserPrincipal actor, PaymentCommand command) {
        requireStaff(actor, UserClaim.LOAN_RECORDING_POST);
        require(accountingGuard.recordedLoan(loanId), "state");
        return postInternal(loanId, actor, command);
    }

    private Receipt postInternal(UUID loanId, AppUserPrincipal actor, PaymentCommand command) {
        require(command.channel()!=LoanRepaymentTransaction.Channel.INTERNAL,"channel");
        accountingGuard.repayment(loanId, actor, command);
        LoanLedger ledger = lockedLedger(loanId, actor);
        BigDecimal amount = money(command.amount());
        String reference = text(command.reference(), 100);
        require(amount.signum() > 0 && command.channel() != null && command.requestKey() != null, "amount");
        Optional<LoanRepaymentTransaction> retry = transactions.findBySaccoIdAndStationIdAndRequestKey(
            ledger.getSaccoId(), ledger.getStationId(), command.requestKey());
        if (retry.isPresent()) {
            LoanRepaymentTransaction t = retry.get();
            require(t.getKind() == LoanRepaymentTransaction.Kind.PAYMENT && t.getLoanApplicationId().equals(loanId)
                && t.getAmount().compareTo(amount) == 0 && t.getChannel() == command.channel()
                && t.getChannelReference().equals(reference) && t.getPaymentDate().equals(command.paymentDate())
                && Objects.equals(t.getAllocationPolicyId(),command.policyId()), "retry");
            return receipt(t, ledger.getLoanId(), reversalIds(loanId, List.of(t.getId())).get(t.getId()));
        }
        LocalDate date = command.paymentDate();
        require(date != null && !date.isAfter(clock.today()) && !date.isBefore(ledger.getDisbursementDate())
            && (ledger.getLastPaymentDate() == null || !date.isBefore(ledger.getLastPaymentDate())), "date");
        requireAfterAdjustment(ledger,date);
        require(!transactions.existsBySaccoIdAndStationIdAndChannelAndChannelReferenceAndKind(ledger.getSaccoId(),
            ledger.getStationId(), command.channel(), reference, LoanRepaymentTransaction.Kind.PAYMENT), "duplicate");
        LoanApplication loan = loans.findById(loanId).orElseThrow(() -> new IllegalArgumentException("repayment.error.unavailable"));
        require(Set.of(LoanStatus.DISBURSED, LoanStatus.PAR, LoanStatus.DEFAULTED).contains(loan.getStatus()), "state");
        List<LoanLedgerInstallment> rows = rows(loanId);
        require(!command.principalFirst() || command.policyId()!=null,"request");
        var preview=calculatePayment(ledger,rows,date,amount,prepayments.earned(loanId,date),prepayments.terms(loanId),command.principalFirst());
        UUID id = UUID.randomUUID();
        Map<UUID, BigDecimal[]> parts = new LinkedHashMap<>();
        BigDecimal principal=preview.principal(),interest=preview.interest(),excluded=ZERO;
        var byId=new HashMap<UUID,LoanLedgerInstallment>();rows.forEach(r->byId.put(r.getId(),r));
        for(var part:preview.allocations()){
            parts.put(part.installmentId(),new BigDecimal[]{part.principal(),part.interest(),part.excluded()});
            var row=byId.get(part.installmentId());row.setInterestExcluded(row.getInterestExcluded().add(part.excluded()));excluded=excluded.add(part.excluded());
        }
        LoanRepaymentTransaction transaction = LoanRepaymentTransaction.builder().id(id).loanApplicationId(loanId)
            .saccoId(ledger.getSaccoId()).stationId(ledger.getStationId()).sequence(ledger.getNextSequence())
            .receiptReference("RP-" + id).requestKey(command.requestKey()).kind(LoanRepaymentTransaction.Kind.PAYMENT)
            .channel(command.channel()).channelReference(reference).paymentDate(date).amount(amount)
            .principalAmount(principal).interestAmount(interest).futureInterestExcluded(excluded).actorMemberId(actor.getMemberId())
            .loanStatusBefore(loan.getStatus()).postedAt(clock.now()).allocationPolicyId(command.policyId()).build();
        // The UUID references are deliberately not JPA associations, so flush the parent before child inserts.
        transactions.saveAndFlush(transaction);
        allocations.saveAll(parts.entrySet().stream().map(p -> LoanRepaymentAllocation.builder().id(UUID.randomUUID())
            .transactionId(id).installmentId(p.getKey()).principalAmount(p.getValue()[0]).interestAmount(p.getValue()[1]).interestExcluded(p.getValue()[2]).build()).toList());
        installments.saveAll(rows);
        ledger.setPrincipalPaid(ledger.getPrincipalPaid().add(principal));
        ledger.setInterestPaid(ledger.getInterestPaid().add(interest));
        ledger.setLastPaymentDate(date);
        ledger.setNextSequence(ledger.getNextSequence() + 1);
        writePaymentJournal(ledger, transaction, false);
        allocations.flush();installments.flush();
        if (totals(rows, LocalDate.MAX).remaining().signum() == 0) {
            loan.setStatus(LoanStatus.PAID);
            loan.setPaidAt(clock.now());
            loan.setPaidMarkedByManagerId(actor.getMemberId());
        }
        updateProjection(ledger, loan, rows);
        audit(transaction);
        return receipt(transaction, ledger.getLoanId(), null);
    }

    private void requireAfterAdjustment(LoanLedger ledger,LocalDate date) {
        LocalDate latest=ledgers.latestAdjustmentDate(ledger.getLoanApplicationId(),ledger.getSaccoId(),ledger.getStationId());
        require(latest==null || !date.isBefore(latest),"adjustmentDate");
        var waiverDate=prepayments.latestWaiverDate(ledger.getLoanApplicationId());
        require(waiverDate==null || !date.isBefore(waiverDate),"adjustmentDate");
    }

    @Transactional
    public Receipt reverse(UUID loanId, UUID paymentId, AppUserPrincipal actor, UUID requestKey, String reason) {
        return reverseAt(loanId,paymentId,actor,requestKey,reason,null);
    }

    /** An approved source correction may take effect in a later open period without rewriting the receipt. */
    @Transactional
    public Receipt reverseAt(UUID loanId, UUID paymentId, AppUserPrincipal actor, UUID requestKey, String reason, LocalDate effectiveDate) {
        requireStaff(actor, UserClaim.LOAN_REPAYMENTS_REVERSE);
        return reverseInternal(loanId,paymentId,actor,requestKey,reason,effectiveDate);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Receipt reverseRecorded(UUID loanId, UUID paymentId, AppUserPrincipal actor, UUID requestKey, String reason, LocalDate effectiveDate) {
        requireStaff(actor, UserClaim.LOAN_RECORDING_REVERSE);
        require(accountingGuard.recordedLoan(loanId), "state");
        return reverseInternal(loanId,paymentId,actor,requestKey,reason,effectiveDate);
    }

    private Receipt reverseInternal(UUID loanId, UUID paymentId, AppUserPrincipal actor, UUID requestKey, String reason, LocalDate effectiveDate) {
        accountingGuard.reversal(loanId, actor, requestKey);
        LoanLedger ledger = lockedLedger(loanId, actor);
        String explanation = text(reason, 500);
        require(requestKey != null, "retry");
        Optional<LoanRepaymentTransaction> retry = transactions.findBySaccoIdAndStationIdAndRequestKey(
            ledger.getSaccoId(), ledger.getStationId(), requestKey);
        if (retry.isPresent()) {
            LoanRepaymentTransaction r = retry.get();
            require(r.getKind() == LoanRepaymentTransaction.Kind.REVERSAL && r.getLoanApplicationId().equals(loanId)
                && paymentId.equals(r.getReversesTransactionId()) && explanation.equals(r.getReason())
                && (effectiveDate==null || effectiveDate.equals(r.getPaymentDate())), "retry");
            return receipt(r, ledger.getLoanId(), null);
        }
        LoanRepaymentTransaction original = transactions.latestUnreversed(loanId, PageRequest.of(0, 1)).stream()
            .findFirst().filter(t -> t.getId().equals(paymentId)).orElseThrow(() -> new IllegalArgumentException("repayment.error.latest"));
        require(original.getKind()==LoanRepaymentTransaction.Kind.PAYMENT,"state");
        prepayments.checkReversal(loanId,original.getSequence());
        require(!original.getActorMemberId().equals(actor.getMemberId()), "checker");
        LocalDate correctionDate=effectiveDate==null?original.getPaymentDate():effectiveDate;
        require(!correctionDate.isBefore(original.getPaymentDate()) && !correctionDate.isAfter(clock.today()),"date");
        requireAfterAdjustment(ledger,correctionDate);
        accountingGuard.reversal(loanId,actor,requestKey,correctionDate,paymentId);
        LoanApplication loan = loans.findById(loanId).orElseThrow(() -> new IllegalArgumentException("repayment.error.unavailable"));
        require(Set.of(LoanStatus.DISBURSED, LoanStatus.PAR, LoanStatus.DEFAULTED, LoanStatus.PAID).contains(loan.getStatus()), "state");
        UUID id = UUID.randomUUID();
        LoanRepaymentTransaction reversal = LoanRepaymentTransaction.builder().id(id).loanApplicationId(loanId)
            .saccoId(ledger.getSaccoId()).stationId(ledger.getStationId()).sequence(ledger.getNextSequence())
            .receiptReference("RV-" + id).requestKey(requestKey).kind(LoanRepaymentTransaction.Kind.REVERSAL)
            .channel(original.getChannel()).channelReference(original.getChannelReference()).paymentDate(correctionDate)
            .amount(original.getAmount()).principalAmount(original.getPrincipalAmount()).interestAmount(original.getInterestAmount()).futureInterestExcluded(original.getFutureInterestExcluded())
            .actorMemberId(actor.getMemberId()).reversesTransactionId(paymentId).reason(explanation)
            .loanStatusBefore(loan.getStatus()).postedAt(clock.now()).allocationPolicyId(original.getAllocationPolicyId()).build();
        List<LoanLedgerInstallment> rows = rows(loanId);
        Map<UUID, LoanLedgerInstallment> byId = new HashMap<>();
        rows.forEach(r -> byId.put(r.getId(), r));
        List<LoanRepaymentAllocation> originalParts = allocations.findByTransactionId(paymentId, PageRequest.of(0, MAX_INSTALLMENTS));
        require(originalParts.stream().map(p -> p.getPrincipalAmount().add(p.getInterestAmount())).reduce(ZERO, BigDecimal::add)
            .compareTo(original.getAmount()) == 0, "schedule");
        originalParts.forEach(p -> {
            LoanLedgerInstallment row = byId.get(p.getInstallmentId());
            require(row != null && row.getPrincipalPaid().compareTo(p.getPrincipalAmount()) >= 0
                && row.getInterestPaid().compareTo(p.getInterestAmount()) >= 0 && row.getInterestExcluded().compareTo(p.getInterestExcluded())>=0, "schedule");
            row.setPrincipalPaid(row.getPrincipalPaid().subtract(p.getPrincipalAmount()));
            row.setInterestPaid(row.getInterestPaid().subtract(p.getInterestAmount()));
            row.setInterestExcluded(row.getInterestExcluded().subtract(p.getInterestExcluded()));
        });
        transactions.saveAndFlush(reversal);
        allocations.saveAll(originalParts.stream().map(p -> LoanRepaymentAllocation.builder().id(UUID.randomUUID())
            .transactionId(id).installmentId(p.getInstallmentId()).principalAmount(p.getPrincipalAmount()).interestAmount(p.getInterestAmount()).interestExcluded(p.getInterestExcluded()).build()).toList());
        installments.saveAll(rows);
        ledger.setPrincipalPaid(ledger.getPrincipalPaid().subtract(original.getPrincipalAmount()));
        ledger.setInterestPaid(ledger.getInterestPaid().subtract(original.getInterestAmount()));
        ledger.setLastPaymentDate(transactions.latestUnreversed(loanId, PageRequest.of(0, 1)).stream()
            .map(LoanRepaymentTransaction::getPaymentDate).findFirst().orElse(null));
        ledger.setNextSequence(ledger.getNextSequence() + 1);
        writePaymentJournal(ledger, reversal, true);
        allocations.flush();installments.flush();
        if (loan.getStatus() == LoanStatus.PAID) {
            loan.setStatus(original.getLoanStatusBefore());
            loan.setPaidAt(null);
            loan.setPaidMarkedByManagerId(null);
        }
        updateProjection(ledger, loan, rows);
        audit(reversal);
        return receipt(reversal, ledger.getLoanId(), null);
    }

    @Transactional
    public boolean refreshProjection(UUID loanId) {
        Optional<LoanLedger> found = ledgers.findById(loanId);
        if (found.isEmpty()) return false;
        LoanLedger l = found.get();
        LoanLedger locked = ledgers.lockScoped(loanId, l.getSaccoId(), l.getStationId()).orElseThrow();
        LoanApplication loan = loans.findById(loanId).orElseThrow();
        if(loan.getStatus()==LoanStatus.DISBURSEMENT_REVERSED)return false;
        updateProjection(locked, loan, rows(loanId));
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<RiskBalance> riskBalance(UUID loanId, LocalDate today) {
        return ledgers.findById(loanId).map(l -> {
            if(transactions.settledThroughTopUp(loanId))return new RiskBalance(ZERO,ZERO,null);
            if(loans.findById(loanId).map(a->a.getStatus()==LoanStatus.DISBURSEMENT_REVERSED).orElse(false))return new RiskBalance(ZERO,ZERO,null);
            Totals t = totals(rows(loanId), today);
            return new RiskBalance(l.getPrincipal().subtract(l.getPrincipalPaid()).add(t.dueInterest()),
                t.remaining(), t.oldestOverdue());
        });
    }

    private Map<UUID, UUID> reversalIds(UUID loanId, List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, UUID> result = new HashMap<>();
        transactions.findByLoanApplicationIdAndReversesTransactionIdIn(loanId, ids)
            .forEach(r -> result.put(r.getReversesTransactionId(), r.getId()));
        return result;
    }

    private LoanLedger readableLedger(UUID id, AppUserPrincipal actor) {
        if (actor == null) throw new AccessDeniedException("Forbidden");
        LoanLedger l = ledgers.findById(id).orElseThrow(() -> new AccessDeniedException("Forbidden"));
        boolean owner = !actor.isStaffSession() && actor.isMemberAccess()
            && access.has(actor, UserClaim.MEMBER_LOANS_VIEW) && actor.getMemberId().equals(l.getApplicantMemberId());
        boolean staff = actor.isStaffSession() && (access.has(actor, UserClaim.LOAN_REPAYMENTS_VIEW)
            || (access.has(actor, UserClaim.LOAN_RECORDING_VIEW) && accountingGuard.recordedLoan(id)))
            && l.getStationId().equals(actor.getStationId());
        if (!l.getSaccoId().equals(actor.getSaccoId()) || (!owner && !staff)) throw new AccessDeniedException("Forbidden");
        return l;
    }

    private void requireStaff(AppUserPrincipal actor, UserClaim claim) {
        if (actor == null || !actor.isStaffSession() || !access.has(actor, claim)
            || actor.getSaccoId() == null || actor.getSaccoId().isBlank()
            || actor.getStationId() == null || actor.getStationId().isBlank()) throw new AccessDeniedException("Forbidden");
    }

    private LoanLedger lockedLedger(UUID id, AppUserPrincipal actor) {
        return ledgers.lockScoped(id, actor.getSaccoId(), actor.getStationId())
            .orElseThrow(() -> new AccessDeniedException("Forbidden"));
    }

    private List<LoanLedgerInstallment> rows(UUID id) {
        return installments.findByLoanApplicationIdOrderByInstallmentNumber(id, PageRequest.of(0, MAX_INSTALLMENTS));
    }

    private List<LoanLedgerInstallment> parseSchedule(LoanApplication loan) {
        try {
            Map<String, Object> schedule = mapper.readValue(loan.getRepaymentScheduleJson(), new TypeReference<Map<String, Object>>() {});
            Object raw = schedule.get("schedule");
            require(raw instanceof List<?> && !((List<?>) raw).isEmpty() && ((List<?>) raw).size() <= MAX_INSTALLMENTS, "schedule");
            List<LoanLedgerInstallment> result = new ArrayList<>();
            LocalDate previous = loan.getDisbursementDate();
            for (Object value : (List<?>) raw) {
                require(value instanceof Map<?, ?>, "schedule");
                Map<?, ?> r = (Map<?, ?>) value;
                LocalDate date = LocalDate.parse(String.valueOf(r.get("dueDate")));
                BigDecimal principal = money(new BigDecimal(String.valueOf(r.get("principalComponent"))));
                BigDecimal interest = money(new BigDecimal(String.valueOf(r.get("interestComponent"))));
                BigDecimal amount = money(new BigDecimal(String.valueOf(r.get("amount"))));
                require(date.isAfter(previous) && principal.signum() >= 0 && interest.signum() >= 0
                    && amount.signum() > 0 && amount.compareTo(principal.add(interest)) == 0, "schedule");
                result.add(LoanLedgerInstallment.builder().id(UUID.randomUUID()).loanApplicationId(loan.getId())
                    .installmentNumber(result.size() + 1).dueDate(date).principal(principal).interest(interest)
                    .principalPaid(ZERO).interestPaid(ZERO).build());
                previous = date;
            }
            return result;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("repayment.error.schedule");
        } catch (Exception ex) {
            throw new IllegalArgumentException("repayment.error.schedule", ex);
        }
    }

    private BigDecimal earnedInterest(LoanLedgerInstallment r,LocalDate date,Map<UUID,BigDecimal> earned){
        return earned.getOrDefault(r.getId(),r.getDueDate().isAfter(date)?ZERO:r.getInterest().subtract(r.getInterestExcluded()).subtract(r.getInterestWaived())).setScale(2,RoundingMode.HALF_UP);
    }
    private Totals totals(List<LoanLedgerInstallment> rows, LocalDate asOf) {
        BigDecimal principal = ZERO, interest = ZERO, future = ZERO, remaining = ZERO;
        LocalDate oldest = null;
        var earned=rows.isEmpty() || asOf.equals(LocalDate.MAX)?Map.<UUID,BigDecimal>of():prepayments.earned(rows.getFirst().getLoanApplicationId(),asOf);
        for (LoanLedgerInstallment r : rows) {
            BigDecimal p = r.getPrincipal().subtract(r.getPrincipalPaid());
            BigDecimal i = r.getInterest().subtract(r.getInterestPaid()).subtract(r.getInterestExcluded()).subtract(r.getInterestWaived());
            remaining = remaining.add(p).add(i);
            BigDecimal due=asOf.equals(LocalDate.MAX)?i:earnedInterest(r,asOf,earned).subtract(r.getInterestPaid()).max(ZERO);
            interest=interest.add(due);future=future.add(i.subtract(due).max(ZERO));
            if (!r.getDueDate().isAfter(asOf)) {
                principal = principal.add(p);
                if (r.getDueDate().isBefore(asOf) && p.add(i).signum() > 0 && (oldest == null || r.getDueDate().isBefore(oldest))) oldest = r.getDueDate();
            }
        }
        return new Totals(principal, interest, future, remaining, oldest);
    }

    private void updateProjection(LoanLedger ledger, LoanApplication loan, List<LoanLedgerInstallment> rows) {
        try {
            Map<String, Object> snapshot = loan.getFinancialSnapshot() == null || loan.getFinancialSnapshot().isBlank()
                ? new LinkedHashMap<>() : new LinkedHashMap<>(mapper.readValue(loan.getFinancialSnapshot(), new TypeReference<Map<String, Object>>() {}));
            Totals due = totals(rows, clock.today());
            boolean cancelled=loan.getStatus()==LoanStatus.DISBURSEMENT_REVERSED;
            if(cancelled || transactions.settledThroughTopUp(loan.getId()))due=new Totals(ZERO,ZERO,ZERO,ZERO,null);
            BigDecimal principal = cancelled?ZERO:ledger.getPrincipal().subtract(ledger.getPrincipalPaid());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_OUTSTANDING_PRINCIPAL, principal);
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_OUTSTANDING_INTEREST, due.dueInterest());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_OUTSTANDING, principal.add(due.dueInterest()));
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_PRINCIPAL_PAID, ledger.getPrincipalPaid());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_INTEREST_PAID, ledger.getInterestPaid());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_LAST_PAYMENT_DATE, ledger.getLastPaymentDate());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_FETCHED_AT, clock.now());
            snapshot.put("ledgerFutureScheduledInterest", due.futureInterest());
            snapshot.put("ledgerExcludedFutureInterest",rows.stream().map(LoanLedgerInstallment::getInterestExcluded).reduce(ZERO,BigDecimal::add));
            snapshot.put("ledgerWaivedInterest",rows.stream().map(LoanLedgerInstallment::getInterestWaived).reduce(ZERO,BigDecimal::add));
            snapshot.put("balanceSource", "LOCAL_REPAYMENT_LEDGER");
            loan.setFinancialSnapshot(mapper.writeValueAsString(snapshot));
            loan.setUpdatedAt(clock.now());
            loans.save(loan);
            ledgers.save(ledger);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to update the repayment balance", ex);
        }
    }

    private void writePaymentJournal(LoanLedger ledger, LoanRepaymentTransaction t, boolean reverse) {
        String receiving = switch (t.getChannel()) {
            case CASH -> "CASH_CLEARING";
            case BANK -> "BANK_CLEARING";
            case MOBILE_MONEY -> "MOBILE_MONEY_CLEARING";
            case INTERNAL -> "TOPUP_CLEARING";
        };
        List<LoanJournalEntry> lines = new ArrayList<>();
        lines.add(entry(ledger, t.getId(), t.getId(), receiving, reverse ? ZERO : t.getAmount(), reverse ? t.getAmount() : ZERO, t.getPaymentDate()));
        if (t.getPrincipalAmount().signum() > 0) lines.add(entry(ledger, t.getId(), t.getId(), "LOAN_PRINCIPAL",
            reverse ? t.getPrincipalAmount() : ZERO, reverse ? ZERO : t.getPrincipalAmount(), t.getPaymentDate()));
        if (t.getInterestAmount().signum() > 0) lines.add(entry(ledger, t.getId(), t.getId(), "INTEREST_COLLECTIONS_CLEARING",
            reverse ? t.getInterestAmount() : ZERO, reverse ? ZERO : t.getInterestAmount(), t.getPaymentDate()));
        journal.saveAll(lines);
    }

    private LoanJournalEntry entry(LoanLedger l, UUID transaction, UUID voucher, String account, BigDecimal debit, BigDecimal credit, LocalDate date) {
        return LoanJournalEntry.builder().id(UUID.randomUUID()).loanApplicationId(l.getLoanApplicationId())
            .transactionId(transaction).voucherId(voucher).accountCode(account).debit(debit).credit(credit)
            .effectiveDate(date).postedAt(clock.now()).build();
    }

    private void audit(LoanRepaymentTransaction t) {
        audit.logEvent("LOAN_REPAYMENT", t.getId(), "LOAN_REPAYMENT_" + t.getKind(), t.getActorMemberId(), AuditEventStatus.SUCCESS,
            "Loan repayment " + t.getKind().name().toLowerCase(Locale.ROOT), "RECEIPT", t.getReceiptReference(), t.getSaccoId(), t.getStationId(),
            Map.of("loanApplicationId", t.getLoanApplicationId(), "amount", t.getAmount(), "paymentDate", t.getPaymentDate(),"futureInterestExcluded",t.getFutureInterestExcluded()));
    }

    private Receipt receipt(LoanRepaymentTransaction t, String loanNumber, UUID reversedBy) {
        return new Receipt(t.getId(), t.getLoanApplicationId(), loanNumber, t.getReceiptReference(), t.getKind().name(), t.getPaymentDate(),
            t.getPostedAt(), t.getAmount(), t.getPrincipalAmount(), t.getInterestAmount(), t.getChannel().name(),
            t.getChannelReference(), t.getReversesTransactionId(), t.getReason(), reversedBy);
    }

    /** Internal refinancing is a distinct append-only movement, never a cash receipt. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshCancelledTopUp(UUID loanId,AppUserPrincipal actor) {
        requireStaff(actor,UserClaim.LOAN_RECORDING_REVERSE);var ledger=lockedLedger(loanId,actor);var loan=loans.findById(loanId).orElseThrow();
        require(loan.getTopUpSourceLoanId()!=null && loan.getStatus()==LoanStatus.DISBURSEMENT_REVERSED && accountingGuard.recordedLoan(loanId),"state");
        updateProjection(ledger,loan,rows(loanId));loans.flush();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID settleRecordedTopUp(UUID source,AppUserPrincipal actor,LocalDate date,UUID key,UUID event,long sequence,BigDecimal principal,BigDecimal interest) {
        requireStaff(actor,UserClaim.DISBURSEMENT_QUEUE_DISBURSE);
        require(accountingGuard.recordedLoan(source),"state");
        LoanLedger ledger=lockedLedger(source,actor);LoanApplication loan=loans.findById(source).orElseThrow();
        var rows=rows(source);var due=totals(rows,date);
        require(loan.getStatus()==LoanStatus.DISBURSED && ledger.getNextSequence()==sequence,"state");
        require(principal.compareTo(ledger.getPrincipal().subtract(ledger.getPrincipalPaid()))==0 && interest.compareTo(due.dueInterest())==0,"amount");
        require(date!=null && !date.isAfter(clock.today()) && !date.isBefore(ledger.getDisbursementDate()) && (ledger.getLastPaymentDate()==null || !date.isBefore(ledger.getLastPaymentDate())),"date");
        requireAfterAdjustment(ledger,date);
        UUID id=UUID.randomUUID();
        var transaction=LoanRepaymentTransaction.builder().id(id).loanApplicationId(source).saccoId(ledger.getSaccoId()).stationId(ledger.getStationId())
            .sequence(sequence).receiptReference("TS-"+id).requestKey(key).kind(LoanRepaymentTransaction.Kind.SETTLEMENT).channel(LoanRepaymentTransaction.Channel.INTERNAL)
            .channelReference(event.toString()).paymentDate(date).amount(principal.add(interest)).principalAmount(principal).interestAmount(interest)
            .actorMemberId(actor.getMemberId()).loanStatusBefore(loan.getStatus()).postedAt(clock.now()).build();
        transactions.saveAndFlush(transaction);
        var parts=new ArrayList<LoanRepaymentAllocation>();
        var earned=prepayments.earned(source,date);
        for(var row:rows){var p=row.getPrincipal().subtract(row.getPrincipalPaid());var i=earnedInterest(row,date,earned).subtract(row.getInterestPaid()).max(ZERO);
            if(p.add(i).signum()>0)parts.add(LoanRepaymentAllocation.builder().id(UUID.randomUUID()).transactionId(id).installmentId(row.getId()).principalAmount(p).interestAmount(i).build());
            row.setPrincipalPaid(row.getPrincipal());row.setInterestPaid(row.getInterestPaid().add(i));}
        allocations.saveAll(parts);installments.saveAll(rows);ledger.setPrincipalPaid(ledger.getPrincipal());ledger.setInterestPaid(ledger.getInterestPaid().add(interest));
        ledger.setLastPaymentDate(date);ledger.setNextSequence(sequence+1);writePaymentJournal(ledger,transaction,false);
        loan.setStatus(LoanStatus.PAID);loan.setPaidAt(clock.now());loan.setPaidMarkedByManagerId(actor.getMemberId());
        updateProjection(ledger,loan,rows);audit(transaction);return id;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID undoRecordedTopUp(UUID source,UUID originalId,AppUserPrincipal actor,UUID key,UUID event,LocalDate date,String reason) {
        requireStaff(actor,UserClaim.LOAN_RECORDING_REVERSE);LoanLedger ledger=lockedLedger(source,actor);
        var original=transactions.findById(originalId).orElseThrow();
        require(original.getLoanApplicationId().equals(source) && original.getKind()==LoanRepaymentTransaction.Kind.SETTLEMENT
            && !original.getActorMemberId().equals(actor.getMemberId()) && transactions.settledThroughTopUp(source),"state");
        require(!date.isBefore(original.getPaymentDate()) && !date.isAfter(clock.today()),"date");
        var loan=loans.findById(source).orElseThrow();var rows=rows(source);var byId=new HashMap<UUID,LoanLedgerInstallment>();rows.forEach(r->byId.put(r.getId(),r));
        var parts=allocations.findByTransactionId(originalId,PageRequest.of(0,MAX_INSTALLMENTS));UUID id=UUID.randomUUID();
        var reversal=LoanRepaymentTransaction.builder().id(id).loanApplicationId(source).saccoId(ledger.getSaccoId()).stationId(ledger.getStationId())
            .sequence(ledger.getNextSequence()).receiptReference("TU-"+id).requestKey(key).kind(LoanRepaymentTransaction.Kind.SETTLEMENT_UNDO).channel(LoanRepaymentTransaction.Channel.INTERNAL)
            .channelReference(event.toString()).paymentDate(date).amount(original.getAmount()).principalAmount(original.getPrincipalAmount()).interestAmount(original.getInterestAmount())
            .actorMemberId(actor.getMemberId()).loanStatusBefore(loan.getStatus()).postedAt(clock.now()).reversesTransactionId(originalId).reason(reason).build();
        transactions.saveAndFlush(reversal);
        allocations.saveAll(parts.stream().map(p->{var r=byId.get(p.getInstallmentId());r.setPrincipalPaid(r.getPrincipalPaid().subtract(p.getPrincipalAmount()));r.setInterestPaid(r.getInterestPaid().subtract(p.getInterestAmount()));
            return LoanRepaymentAllocation.builder().id(UUID.randomUUID()).transactionId(id).installmentId(r.getId()).principalAmount(p.getPrincipalAmount()).interestAmount(p.getInterestAmount()).build();}).toList());
        installments.saveAll(rows);ledger.setPrincipalPaid(ledger.getPrincipalPaid().subtract(original.getPrincipalAmount()));ledger.setInterestPaid(ledger.getInterestPaid().subtract(original.getInterestAmount()));
        ledger.setNextSequence(ledger.getNextSequence()+1);ledger.setLastPaymentDate(date);writePaymentJournal(ledger,reversal,true);
        loan.setStatus(original.getLoanStatusBefore());loan.setPaidAt(null);loan.setPaidMarkedByManagerId(null);updateProjection(ledger,loan,rows);audit(reversal);return id;
    }

    private static BigDecimal money(BigDecimal value) {
        require(value != null && value.precision() - value.scale() <= 16, "amount");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("repayment.error.amount"); }
    }

    private static String text(String value, int max) {
        require(value != null && !value.isBlank() && value.trim().length() <= max, "reference");
        return value.trim();
    }

    private static void require(boolean condition, String key) {
        if (!condition) throw new IllegalArgumentException("repayment.error." + key);
    }

    private record Totals(BigDecimal duePrincipal, BigDecimal dueInterest, BigDecimal futureInterest, BigDecimal remaining, LocalDate oldestOverdue) {}
    public record PaymentCommand(BigDecimal amount, LocalDate paymentDate, LoanRepaymentTransaction.Channel channel, String reference, UUID requestKey,UUID policyId,boolean principalFirst) {
        public PaymentCommand(BigDecimal amount,LocalDate date,LoanRepaymentTransaction.Channel channel,String reference,UUID key){this(amount,date,channel,reference,key,null,false);}
    }
    public record LedgerListRow(UUID id, String loanId, LocalDate disbursementDate, BigDecimal outstandingPrincipal, BigDecimal principalPaid, BigDecimal interestPaid) {}
    private List<InstallmentRow> scheduleRows(List<LoanLedgerInstallment> rows,BigDecimal beginning){
        var result=new ArrayList<InstallmentRow>(rows.size());var balance=beginning;
        for(var row:rows){var ending=balance.subtract(row.getPrincipal()).subtract(row.getInterest());result.add(new InstallmentRow(row.getInstallmentNumber(),row.getDueDate(),row.getPrincipal(),row.getInterest(),row.getPrincipalPaid(),row.getInterestPaid(),balance,ending,row.getInterestExcluded(),row.getInterestWaived()));balance=ending;}
        return result;
    }
    public record InstallmentRow(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest, BigDecimal principalPaid, BigDecimal interestPaid,BigDecimal beginningBalance,BigDecimal endingBalance,BigDecimal interestExcluded,BigDecimal interestWaived) {
        public BigDecimal scheduledPayment(){return principal.add(interest);}
        public BigDecimal getScheduledPayment(){return scheduledPayment();}
        public BigDecimal remainingPrincipal(){return principal.subtract(principalPaid);}
        public BigDecimal getRemainingPrincipal(){return remainingPrincipal();}
        public BigDecimal remainingInterest(){return interest.subtract(interestPaid).subtract(interestExcluded).subtract(interestWaived);}
        public BigDecimal getRemainingInterest(){return remainingInterest();}
        public BigDecimal remainingPayment(){return remainingPrincipal().add(remainingInterest());}
        public BigDecimal getRemainingPayment(){return remainingPayment();}
        public InstallmentRow(int number,LocalDate date,BigDecimal principal,BigDecimal interest,BigDecimal principalPaid,BigDecimal interestPaid,BigDecimal beginning,BigDecimal ending){this(number,date,principal,interest,principalPaid,interestPaid,beginning,ending,ZERO,ZERO);}

        public InstallmentRow(int number,LocalDate dueDate,BigDecimal principal,BigDecimal interest,BigDecimal principalPaid,BigDecimal interestPaid){this(number,dueDate,principal,interest,principalPaid,interestPaid,null,null);}
    }
    public record JournalRow(UUID voucher, LocalDate date, String account, BigDecimal debit, BigDecimal credit) {}
    public record RiskBalance(BigDecimal currentOutstanding, BigDecimal contractualRemaining, LocalDate oldestOverdue) {}
    public record Receipt(UUID id, UUID loanApplicationId, String loanId, String reference, String kind, LocalDate paymentDate, java.time.OffsetDateTime postedAt,
        BigDecimal amount, BigDecimal principal, BigDecimal interest, String channel, String channelReference, UUID reverses, String reason, UUID reversedBy) {}
    public record LedgerView(UUID id, String loanId, String branch, LocalDate asOf, BigDecimal outstandingPrincipal,
        BigDecimal principalPaid, BigDecimal interestPaid, BigDecimal duePrincipal, BigDecimal dueInterest,
        BigDecimal futureInterest, LocalDate oldestOverdue, Page<InstallmentRow> installments, Page<Receipt> history,
        UUID latestPaymentId, List<JournalRow> journal,String financialState, Receipt latestReceipt) {}
}
