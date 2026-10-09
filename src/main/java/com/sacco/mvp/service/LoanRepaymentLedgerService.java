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
    private final com.sacco.mvp.accounting.business.service.BusinessAccountingGuard accountingGuard;

    // Called only by the actual disbursement transaction; never infer an opening balance for an old loan.
    @Transactional(propagation = Propagation.MANDATORY)
    public void openAtDisbursement(LoanApplication loan) {
        if (loan.getTopUpSourceLoanId() != null) {
            return;
        }
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
        return new PaymentBalance(t.duePrincipal(), t.dueInterest(), ledger.getPrincipal().subtract(ledger.getPrincipalPaid()));
    }

    @Transactional(readOnly = true)
    public Page<LedgerListRow> list(AppUserPrincipal actor, String loanNumber, int page) {
        requireStaff(actor, UserClaim.LOAN_REPAYMENTS_VIEW);
        String search = loanNumber == null || loanNumber.isBlank() ? null : loanNumber.trim();
        require(search == null || search.matches("[0-9]{4,20}"), "search");
        return ledgers.listScoped(actor.getSaccoId(), actor.getStationId(), search,
            PageRequest.of(Math.max(0, Math.min(page, 10000)), 25,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("loanApplicationId"))))
            .map(l -> new LedgerListRow(l.getLoanApplicationId(), l.getLoanId(), l.getDisbursementDate(),
                l.getPrincipal().subtract(l.getPrincipalPaid()), l.getPrincipalPaid(), l.getInterestPaid()));
    }

    @Transactional(readOnly = true)
    public LedgerView view(UUID loanId, AppUserPrincipal actor, int page, int schedulePage) {
        LoanLedger ledger = readableLedger(loanId, actor);
        List<LoanLedgerInstallment> rows = rows(loanId);
        Totals totals = totals(rows, clock.today());
        Page<LoanRepaymentTransaction> transactionPage = transactions.findByLoanApplicationIdOrderBySequenceDesc(loanId,
            PageRequest.of(Math.max(0, Math.min(page, 10000)), 25));
        Map<UUID, UUID> reversed = reversalIds(loanId, transactionPage.getContent().stream().map(LoanRepaymentTransaction::getId).toList());
        Page<Receipt> history = transactionPage.map(t -> receipt(t, ledger.getLoanId(), reversed.get(t.getId())));
        UUID latest = transactions.latestUnreversed(loanId, PageRequest.of(0, 1)).stream()
            .map(LoanRepaymentTransaction::getId).findFirst().orElse(null);
        List<JournalRow> entries = !actor.isStaffSession() ? List.of() : journal.findByLoanApplicationIdOrderByPostedAtDescId(loanId,
            PageRequest.of(0, 30)).stream().map(e -> new JournalRow(e.getVoucherId(), e.getEffectiveDate(),
                e.getAccountCode(), e.getDebit(), e.getCredit())).toList();
        PageRequest scheduleRequest = PageRequest.of(Math.max(0, Math.min(schedulePage, 10000)), 25);
        int from = (int) Math.min(scheduleRequest.getOffset(), rows.size());
        Page<InstallmentRow> schedule = new PageImpl<>(rows.subList(from, Math.min(from + 25, rows.size())).stream()
            .map(r -> new InstallmentRow(r.getInstallmentNumber(), r.getDueDate(), r.getPrincipal(), r.getInterest(),
                r.getPrincipalPaid(), r.getInterestPaid())).toList(), scheduleRequest, rows.size());
        return new LedgerView(loanId, ledger.getLoanId(), ledger.getStationId(), clock.today(),
            ledger.getPrincipal().subtract(ledger.getPrincipalPaid()), ledger.getPrincipalPaid(), ledger.getInterestPaid(),
            totals.duePrincipal(), totals.dueInterest(), totals.futureInterest(), totals.oldestOverdue(),
            schedule, history, latest,
            actor.isStaffSession() ? entries : List.of());
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
                && t.getChannelReference().equals(reference) && t.getPaymentDate().equals(command.paymentDate()), "retry");
            return receipt(t, ledger.getLoanId(), reversalIds(loanId, List.of(t.getId())).get(t.getId()));
        }
        LocalDate date = command.paymentDate();
        require(date != null && !date.isAfter(clock.today()) && !date.isBefore(ledger.getDisbursementDate())
            && (ledger.getLastPaymentDate() == null || !date.isBefore(ledger.getLastPaymentDate())), "date");
        require(!transactions.existsBySaccoIdAndStationIdAndChannelAndChannelReferenceAndKind(ledger.getSaccoId(),
            ledger.getStationId(), command.channel(), reference, LoanRepaymentTransaction.Kind.PAYMENT), "duplicate");
        LoanApplication loan = loans.findById(loanId).orElseThrow(() -> new IllegalArgumentException("repayment.error.unavailable"));
        require(Set.of(LoanStatus.DISBURSED, LoanStatus.PAR, LoanStatus.DEFAULTED).contains(loan.getStatus()), "state");
        List<LoanLedgerInstallment> rows = rows(loanId);
        Totals due = totals(rows, date);
        require(amount.compareTo(due.duePrincipal().add(due.dueInterest())) <= 0, "advance");

        UUID id = UUID.randomUUID();
        Map<UUID, BigDecimal[]> parts = new LinkedHashMap<>();
        BigDecimal left = amount;
        // Pay all due interest first, then due principal. New fee assessments are not supported in this increment.
        for (LoanLedgerInstallment row : rows) {
            if (row.getDueDate().isAfter(date)) continue;
            BigDecimal take = left.min(row.getInterest().subtract(row.getInterestPaid()));
            if (take.signum() > 0) {
                parts.computeIfAbsent(row.getId(), ignored -> new BigDecimal[]{ZERO, ZERO})[1] = take;
                row.setInterestPaid(row.getInterestPaid().add(take));
                left = left.subtract(take);
            }
        }
        for (LoanLedgerInstallment row : rows) {
            if (row.getDueDate().isAfter(date)) continue;
            BigDecimal take = left.min(row.getPrincipal().subtract(row.getPrincipalPaid()));
            if (take.signum() > 0) {
                parts.computeIfAbsent(row.getId(), ignored -> new BigDecimal[]{ZERO, ZERO})[0] = take;
                row.setPrincipalPaid(row.getPrincipalPaid().add(take));
                left = left.subtract(take);
            }
        }
        require(left.signum() == 0, "schedule");
        BigDecimal principal = parts.values().stream().map(p -> p[0]).reduce(ZERO, BigDecimal::add);
        BigDecimal interest = amount.subtract(principal);
        LoanRepaymentTransaction transaction = LoanRepaymentTransaction.builder().id(id).loanApplicationId(loanId)
            .saccoId(ledger.getSaccoId()).stationId(ledger.getStationId()).sequence(ledger.getNextSequence())
            .receiptReference("RP-" + id).requestKey(command.requestKey()).kind(LoanRepaymentTransaction.Kind.PAYMENT)
            .channel(command.channel()).channelReference(reference).paymentDate(date).amount(amount)
            .principalAmount(principal).interestAmount(interest).actorMemberId(actor.getMemberId())
            .loanStatusBefore(loan.getStatus()).postedAt(clock.now()).build();
        // The UUID references are deliberately not JPA associations, so flush the parent before child inserts.
        transactions.saveAndFlush(transaction);
        allocations.saveAll(parts.entrySet().stream().map(p -> LoanRepaymentAllocation.builder().id(UUID.randomUUID())
            .transactionId(id).installmentId(p.getKey()).principalAmount(p.getValue()[0]).interestAmount(p.getValue()[1]).build()).toList());
        installments.saveAll(rows);
        ledger.setPrincipalPaid(ledger.getPrincipalPaid().add(principal));
        ledger.setInterestPaid(ledger.getInterestPaid().add(interest));
        ledger.setLastPaymentDate(date);
        ledger.setNextSequence(ledger.getNextSequence() + 1);
        writePaymentJournal(ledger, transaction, false);
        if (totals(rows, LocalDate.MAX).remaining().signum() == 0) {
            loan.setStatus(LoanStatus.PAID);
            loan.setPaidAt(clock.now());
            loan.setPaidMarkedByManagerId(actor.getMemberId());
        }
        updateProjection(ledger, loan, rows);
        audit(transaction);
        return receipt(transaction, ledger.getLoanId(), null);
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
        require(!original.getActorMemberId().equals(actor.getMemberId()), "checker");
        LocalDate correctionDate=effectiveDate==null?original.getPaymentDate():effectiveDate;
        require(!correctionDate.isBefore(original.getPaymentDate()) && !correctionDate.isAfter(clock.today()),"date");
        accountingGuard.reversal(loanId,actor,requestKey,correctionDate,paymentId);
        LoanApplication loan = loans.findById(loanId).orElseThrow(() -> new IllegalArgumentException("repayment.error.unavailable"));
        require(Set.of(LoanStatus.DISBURSED, LoanStatus.PAR, LoanStatus.DEFAULTED, LoanStatus.PAID).contains(loan.getStatus()), "state");
        UUID id = UUID.randomUUID();
        LoanRepaymentTransaction reversal = LoanRepaymentTransaction.builder().id(id).loanApplicationId(loanId)
            .saccoId(ledger.getSaccoId()).stationId(ledger.getStationId()).sequence(ledger.getNextSequence())
            .receiptReference("RV-" + id).requestKey(requestKey).kind(LoanRepaymentTransaction.Kind.REVERSAL)
            .channel(original.getChannel()).channelReference(original.getChannelReference()).paymentDate(correctionDate)
            .amount(original.getAmount()).principalAmount(original.getPrincipalAmount()).interestAmount(original.getInterestAmount())
            .actorMemberId(actor.getMemberId()).reversesTransactionId(paymentId).reason(explanation)
            .loanStatusBefore(loan.getStatus()).postedAt(clock.now()).build();
        List<LoanLedgerInstallment> rows = rows(loanId);
        Map<UUID, LoanLedgerInstallment> byId = new HashMap<>();
        rows.forEach(r -> byId.put(r.getId(), r));
        List<LoanRepaymentAllocation> originalParts = allocations.findByTransactionId(paymentId, PageRequest.of(0, MAX_INSTALLMENTS));
        require(originalParts.stream().map(p -> p.getPrincipalAmount().add(p.getInterestAmount())).reduce(ZERO, BigDecimal::add)
            .compareTo(original.getAmount()) == 0, "schedule");
        originalParts.forEach(p -> {
            LoanLedgerInstallment row = byId.get(p.getInstallmentId());
            require(row != null && row.getPrincipalPaid().compareTo(p.getPrincipalAmount()) >= 0
                && row.getInterestPaid().compareTo(p.getInterestAmount()) >= 0, "schedule");
            row.setPrincipalPaid(row.getPrincipalPaid().subtract(p.getPrincipalAmount()));
            row.setInterestPaid(row.getInterestPaid().subtract(p.getInterestAmount()));
        });
        transactions.saveAndFlush(reversal);
        allocations.saveAll(originalParts.stream().map(p -> LoanRepaymentAllocation.builder().id(UUID.randomUUID())
            .transactionId(id).installmentId(p.getInstallmentId()).principalAmount(p.getPrincipalAmount()).interestAmount(p.getInterestAmount()).build()).toList());
        installments.saveAll(rows);
        ledger.setPrincipalPaid(ledger.getPrincipalPaid().subtract(original.getPrincipalAmount()));
        ledger.setInterestPaid(ledger.getInterestPaid().subtract(original.getInterestAmount()));
        ledger.setLastPaymentDate(transactions.latestUnreversed(loanId, PageRequest.of(0, 1)).stream()
            .map(LoanRepaymentTransaction::getPaymentDate).findFirst().orElse(null));
        ledger.setNextSequence(ledger.getNextSequence() + 1);
        writePaymentJournal(ledger, reversal, true);
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
        updateProjection(locked, loan, rows(loanId));
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<RiskBalance> riskBalance(UUID loanId, LocalDate today) {
        return ledgers.findById(loanId).map(l -> {
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
            && access.has(actor, UserClaim.MEMBER_LOANS_VIEW) && l.getApplicantMemberId().equals(actor.getMemberId());
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

    private Totals totals(List<LoanLedgerInstallment> rows, LocalDate asOf) {
        BigDecimal principal = ZERO, interest = ZERO, future = ZERO, remaining = ZERO;
        LocalDate oldest = null;
        for (LoanLedgerInstallment r : rows) {
            BigDecimal p = r.getPrincipal().subtract(r.getPrincipalPaid());
            BigDecimal i = r.getInterest().subtract(r.getInterestPaid());
            remaining = remaining.add(p).add(i);
            if (!r.getDueDate().isAfter(asOf)) {
                principal = principal.add(p);
                interest = interest.add(i);
                if (r.getDueDate().isBefore(asOf) && p.add(i).signum() > 0 && (oldest == null || r.getDueDate().isBefore(oldest))) oldest = r.getDueDate();
            } else future = future.add(i);
        }
        return new Totals(principal, interest, future, remaining, oldest);
    }

    private void updateProjection(LoanLedger ledger, LoanApplication loan, List<LoanLedgerInstallment> rows) {
        try {
            Map<String, Object> snapshot = loan.getFinancialSnapshot() == null || loan.getFinancialSnapshot().isBlank()
                ? new LinkedHashMap<>() : new LinkedHashMap<>(mapper.readValue(loan.getFinancialSnapshot(), new TypeReference<Map<String, Object>>() {}));
            Totals due = totals(rows, clock.today());
            BigDecimal principal = ledger.getPrincipal().subtract(ledger.getPrincipalPaid());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_OUTSTANDING_PRINCIPAL, principal);
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_OUTSTANDING_INTEREST, due.dueInterest());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_OUTSTANDING, principal.add(due.dueInterest()));
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_PRINCIPAL_PAID, ledger.getPrincipalPaid());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_TOTAL_INTEREST_PAID, ledger.getInterestPaid());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_LAST_PAYMENT_DATE, ledger.getLastPaymentDate());
            snapshot.put(LoanFinancialSnapshotKeys.PAYMENT_SUMMARY_FETCHED_AT, clock.now());
            snapshot.put("ledgerFutureScheduledInterest", due.futureInterest());
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
            Map.of("loanApplicationId", t.getLoanApplicationId(), "amount", t.getAmount(), "paymentDate", t.getPaymentDate()));
    }

    private Receipt receipt(LoanRepaymentTransaction t, String loanNumber, UUID reversedBy) {
        return new Receipt(t.getId(), t.getLoanApplicationId(), loanNumber, t.getReceiptReference(), t.getKind().name(), t.getPaymentDate(),
            t.getPostedAt(), t.getAmount(), t.getPrincipalAmount(), t.getInterestAmount(), t.getChannel().name(),
            t.getChannelReference(), t.getReversesTransactionId(), t.getReason(), reversedBy);
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
    public record PaymentCommand(BigDecimal amount, LocalDate paymentDate, LoanRepaymentTransaction.Channel channel, String reference, UUID requestKey) {}
    public record LedgerListRow(UUID id, String loanId, LocalDate disbursementDate, BigDecimal outstandingPrincipal, BigDecimal principalPaid, BigDecimal interestPaid) {}
    public record InstallmentRow(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest, BigDecimal principalPaid, BigDecimal interestPaid) {}
    public record JournalRow(UUID voucher, LocalDate date, String account, BigDecimal debit, BigDecimal credit) {}
    public record RiskBalance(BigDecimal currentOutstanding, BigDecimal contractualRemaining, LocalDate oldestOverdue) {}
    public record Receipt(UUID id, UUID loanApplicationId, String loanId, String reference, String kind, LocalDate paymentDate, java.time.OffsetDateTime postedAt,
        BigDecimal amount, BigDecimal principal, BigDecimal interest, String channel, String channelReference, UUID reverses, String reason, UUID reversedBy) {}
    public record LedgerView(UUID id, String loanId, String branch, LocalDate asOf, BigDecimal outstandingPrincipal,
        BigDecimal principalPaid, BigDecimal interestPaid, BigDecimal duePrincipal, BigDecimal dueInterest,
        BigDecimal futureInterest, LocalDate oldestOverdue, Page<InstallmentRow> installments, Page<Receipt> history,
        UUID latestPaymentId, List<JournalRow> journal) {}
}
