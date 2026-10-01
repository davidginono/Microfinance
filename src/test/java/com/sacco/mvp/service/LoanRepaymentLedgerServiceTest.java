package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanRepaymentLedgerServiceTest {
    @Mock LoanLedgerRepository ledgers;
    @Mock LoanLedgerInstallmentRepository installments;
    @Mock LoanRepaymentTransactionRepository transactions;
    @Mock LoanRepaymentAllocationRepository allocations;
    @Mock LoanJournalEntryRepository journal;
    @Mock LoanApplicationRepository loans;
    @Mock ApplicationClock clock;
    @Mock AuditService audit;
    @Spy ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    @Spy AccessControlService access = new AccessControlService();
    @InjectMocks LoanRepaymentLedgerService service;
    final UUID loanId = UUID.randomUUID();
    final UUID posterId = UUID.randomUUID();
    final LocalDate today = LocalDate.of(2026, 10, 1);
    LoanLedger ledger;
    LoanApplication loan;
    List<LoanLedgerInstallment> rows;

    @BeforeEach void setup() {
        ledger = LoanLedger.builder().loanApplicationId(loanId).saccoId("I1").stationId("B1").loanId("100012")
            .applicantMemberId(UUID.randomUUID()).disbursementDate(today.minusMonths(2)).principal(money("200"))
            .principalPaid(money("0")).interestPaid(money("0")).nextSequence(1).build();
        loan = LoanApplication.builder().id(loanId).saccoId("I1").stationId("B1").loanId("100012")
            .status(LoanStatus.DISBURSED).amount(money("200")).disbursementDate(today.minusMonths(2))
            .applicantMemberId(ledger.getApplicantMemberId()).financialSnapshot("{\"interestRate\":0.12}").build();
        rows = List.of(row(1, today.minusMonths(1), "100", "10"), row(2, today, "100", "8"));
        lenient().when(clock.today()).thenReturn(today);
        lenient().when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-01T12:00:00+03:00"));
        lenient().when(ledgers.lockScoped(loanId, "I1", "B1")).thenReturn(Optional.of(ledger));
        lenient().when(ledgers.findById(loanId)).thenReturn(Optional.of(ledger));
        lenient().when(loans.findById(loanId)).thenReturn(Optional.of(loan));
        lenient().when(installments.findByLoanApplicationIdOrderByInstallmentNumber(eq(loanId), any(Pageable.class))).thenReturn(rows);
    }

    @Test void partialPaymentPaysAllDueInterestBeforePrincipalAndBalancesJournal() throws Exception {
        var receipt = service.post(loanId, poster(), command("100", today));
        assertThat(receipt.principal()).isEqualByComparingTo("82");
        assertThat(receipt.interest()).isEqualByComparingTo("18");
        assertThat(rows.getFirst().getPrincipalPaid()).isEqualByComparingTo("82");
        assertThat(rows.getLast().getInterestPaid()).isEqualByComparingTo("8");
        assertThat(ledger.getPrincipalPaid()).isEqualByComparingTo("82");
        assertThat(ledger.getNextSequence()).isEqualTo(2);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        var snapshot = mapper.readTree(loan.getFinancialSnapshot());
        assertThat(snapshot.get("balanceSource").asText()).isEqualTo("LOCAL_REPAYMENT_LEDGER");
        assertThat(snapshot.get("interestRate").asText()).isEqualTo("0.12");
        assertThat(new BigDecimal(snapshot.get("paymentSummaryOutstandingPrincipal").asText())).isEqualByComparingTo("118");
        ArgumentCaptor<List<LoanJournalEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(journal).saveAll(entries.capture());
        assertThat(entries.getValue().stream().map(LoanJournalEntry::getDebit).reduce(money("0"), BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(entries.getValue().stream().map(LoanJournalEntry::getCredit).reduce(money("0"), BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(entries.getValue()).allMatch(e -> e.getVoucherId().equals(receipt.id()));
    }

    @Test void paymentUsesActualDateAndDoesNotCollectFutureInterest() {
        var receipt = service.post(loanId, poster(), command("20", today.minusMonths(1)));
        assertThat(receipt.paymentDate()).isEqualTo(today.minusMonths(1));
        assertThat(receipt.postedAt().toLocalDate()).isEqualTo(today);
        assertThat(receipt.interest()).isEqualByComparingTo("10");
        assertThat(receipt.principal()).isEqualByComparingTo("10");
        assertThat(rows.getLast().getInterestPaid()).isZero();
    }

    @Test void earlyOrExcessPaymentDoesNotMutateBalances() {
        assertThatThrownBy(() -> service.post(loanId, poster(), command("111", today.minusMonths(1))))
            .hasMessage("repayment.error.advance");
        verify(transactions, never()).saveAndFlush(any());
        assertThat(ledger.getPrincipalPaid()).isZero();
        assertThat(rows.getFirst().getInterestPaid()).isZero();
    }

    @Test void fullContractPaymentMarksPaidOnlyAfterAllComponentsAreSettled() {
        service.post(loanId, poster(), command("218", today));
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
        assertThat(loan.getPaidAt()).isNotNull();
        assertThat(ledger.getPrincipalPaid()).isEqualByComparingTo("200");
        assertThat(rows).allMatch(r -> r.getPrincipalPaid().compareTo(r.getPrincipal()) == 0
            && r.getInterestPaid().compareTo(r.getInterest()) == 0);
    }

    @Test void exactRequestRetryReturnsOriginalReceiptWithoutPostingAgain() {
        var command = command("100", today);
        LoanRepaymentTransaction existing = original(command, "82", "18");
        when(transactions.findBySaccoIdAndStationIdAndRequestKey("I1", "B1", command.requestKey())).thenReturn(Optional.of(existing));
        assertThat(service.post(loanId, poster(), command).id()).isEqualTo(existing.getId());
        verify(transactions, never()).saveAndFlush(any());
        verify(journal, never()).saveAll(any());
    }

    @Test void requestKeyCannotBeReusedForDifferentAmount() {
        var command = command("100", today);
        when(transactions.findBySaccoIdAndStationIdAndRequestKey("I1", "B1", command.requestKey()))
            .thenReturn(Optional.of(original(command, "82", "18")));
        assertThatThrownBy(() -> service.post(loanId, poster(), new LoanRepaymentLedgerService.PaymentCommand(
            money("50"), today, command.channel(), command.reference(), command.requestKey()))).hasMessage("repayment.error.retry");
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void duplicateChannelReferenceIsRejected() {
        when(transactions.existsBySaccoIdAndStationIdAndChannelAndChannelReferenceAndKind("I1", "B1",
            LoanRepaymentTransaction.Channel.CASH, "cash-1", LoanRepaymentTransaction.Kind.PAYMENT)).thenReturn(true);
        assertThatThrownBy(() -> service.post(loanId, poster(), command("100", today))).hasMessage("repayment.error.duplicate");
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void nonChronologicalAndFutureDatesAreRejected() {
        ledger.setLastPaymentDate(today);
        assertThatThrownBy(() -> service.post(loanId, poster(), command("1", today.minusDays(1)))).hasMessage("repayment.error.date");
        assertThatThrownBy(() -> service.post(loanId, poster(), command("1", today.plusDays(1)))).hasMessage("repayment.error.date");
        assertThatThrownBy(() -> service.post(loanId, poster(), command("1", loan.getDisbursementDate().minusDays(1)))).hasMessage("repayment.error.date");
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void invalidAmountsAreRejectedBeforeWrites() {
        for (String amount : List.of("0", "-1", "1.001", "10000000000000000")) {
            assertThatThrownBy(() -> service.post(loanId, poster(), command(amount, today))).hasMessage("repayment.error.amount");
        }
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void claimsAndExplicitBranchAreRequiredRegardlessOfStaffRole() {
        assertThatThrownBy(() -> service.post(loanId, actor(posterId, "B1", Set.of(), true), command("1", today)))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.post(loanId, actor(posterId, null, Set.of(UserClaim.LOAN_REPAYMENTS_CREATE), true), command("1", today)))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.post(loanId, actor(posterId, "B1", Set.of(UserClaim.LOAN_REPAYMENTS_CREATE), false), command("1", today)))
            .isInstanceOf(AccessDeniedException.class);
        verify(ledgers, never()).lockScoped(any(), any(), any());
    }

    @Test void crossBranchAccessFailsBeforeAnyFinancialWrite() {
        assertThatThrownBy(() -> service.post(loanId, actor(posterId, "B2", Set.of(UserClaim.LOAN_REPAYMENTS_CREATE), true), command("1", today)))
            .isInstanceOf(AccessDeniedException.class);
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void anotherAuthorizedStaffMemberReversesWithoutDeletingHistory() {
        var command = command("100", today);
        LoanRepaymentTransaction original = original(command, "82", "18");
        rows.getFirst().setPrincipalPaid(money("82"));
        rows.getFirst().setInterestPaid(money("10"));
        rows.getLast().setInterestPaid(money("8"));
        ledger.setPrincipalPaid(money("82")); ledger.setInterestPaid(money("18")); ledger.setNextSequence(2);
        when(transactions.latestUnreversed(eq(loanId), any())).thenReturn(List.of(original), List.of());
        when(allocations.findByTransactionId(eq(original.getId()), any())).thenReturn(List.of(
            allocation(original.getId(), rows.getFirst().getId(), "82", "10"),
            allocation(original.getId(), rows.getLast().getId(), "0", "8")));
        var reversed = service.reverse(loanId, original.getId(), actor(UUID.randomUUID(), "B1",
            Set.of(UserClaim.LOAN_REPAYMENTS_REVERSE), true), UUID.randomUUID(), "Wrong collection entry");
        assertThat(reversed.reverses()).isEqualTo(original.getId());
        assertThat(reversed.kind()).isEqualTo("REVERSAL");
        assertThat(ledger.getPrincipalPaid()).isZero(); assertThat(ledger.getInterestPaid()).isZero();
        assertThat(ledger.getLastPaymentDate()).isNull();
        assertThat(rows).allMatch(r -> r.getPrincipalPaid().signum() == 0 && r.getInterestPaid().signum() == 0);
        verify(transactions).saveAndFlush(argThat(t -> t.getReversesTransactionId().equals(original.getId())));
        verify(transactions, never()).delete(any());
    }

    @Test void originalPosterCannotReverseOwnPayment() {
        LoanRepaymentTransaction original = original(command("100", today), "82", "18");
        when(transactions.latestUnreversed(eq(loanId), any())).thenReturn(List.of(original));
        assertThatThrownBy(() -> service.reverse(loanId, original.getId(), actor(posterId, "B1",
            Set.of(UserClaim.LOAN_REPAYMENTS_REVERSE), true), UUID.randomUUID(), "Error"))
            .hasMessage("repayment.error.checker");
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void earlierPaymentCannotBeReversedOutOfOrder() {
        when(transactions.latestUnreversed(eq(loanId), any())).thenReturn(List.of(original(command("100", today), "82", "18")));
        assertThatThrownBy(() -> service.reverse(loanId, UUID.randomUUID(), actor(UUID.randomUUID(), "B1",
            Set.of(UserClaim.LOAN_REPAYMENTS_REVERSE), true), UUID.randomUUID(), "Error"))
            .hasMessage("repayment.error.latest");
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test void newDisbursementRequiresScheduleThatReconcilesToPrincipal() {
        loan.setRepaymentScheduleJson("""
            {"schedule":[{"dueDate":"2026-09-01","amount":110,"principalComponent":100,"interestComponent":10}]}
            """);
        assertThatThrownBy(() -> service.openAtDisbursement(loan)).hasMessage("repayment.error.schedule");
        verify(ledgers, never()).saveAndFlush(any());
    }

    @Test void newDisbursementWritesOpeningVoucherAndPreservesTheContract() {
        loan.setRepaymentScheduleJson("""
            {"schedule":[{"dueDate":"2026-09-01","amount":110,"principalComponent":100,"interestComponent":10},
            {"dueDate":"2026-10-01","amount":108,"principalComponent":100,"interestComponent":8}]}
            """);
        service.openAtDisbursement(loan);
        verify(ledgers).saveAndFlush(argThat(l -> l.getPrincipal().compareTo(money("200")) == 0 && l.getPrincipalPaid().signum() == 0));
        verify(installments).saveAll(argThat(list -> { List<LoanLedgerInstallment> values = new ArrayList<>(); list.forEach(values::add); return values.size() == 2; }));
        assertThat(loan.getRepaymentScheduleJson()).contains("2026-09-01");
    }

    @Test void oldLoanDoesNotGetAFabricatedBalanceFromRefresh() {
        when(ledgers.findById(loanId)).thenReturn(Optional.empty());
        assertThat(service.refreshProjection(loanId)).isFalse();
        verify(loans, never()).save(any());
    }

    @Test void foreignClientCannotDownloadReceipt() {
        assertThatThrownBy(() -> service.receipt(loanId, UUID.randomUUID(), actor(UUID.randomUUID(), "B1",
            Set.of(UserClaim.MEMBER_LOANS_VIEW), false))).isInstanceOf(AccessDeniedException.class);
        verify(transactions, never()).findById(any());
    }

    @Test void topUpDisbursementDoesNotInventANewOpeningBalance() {
        loan.setTopUpSourceLoanId(UUID.randomUUID());
        service.openAtDisbursement(loan);
        verify(ledgers, never()).saveAndFlush(any());
        verify(installments, never()).saveAll(any());
        verify(journal, never()).saveAll(any());
    }

    private AppUserPrincipal poster() { return actor(posterId, "B1", Set.of(UserClaim.LOAN_REPAYMENTS_CREATE), true); }
    static AppUserPrincipal actor(UUID id, String branch, Set<UserClaim> claims, boolean staff) {
        return new AppUserPrincipal(Member.builder().id(id).saccoId("I1").stationId(branch).fullName("Test Operator")
            .memberNo("C001").memberAccount(true).position(Position.MANAGER).status(MemberStatus.ACTIVE).build(), claims, staff);
    }
    private LoanRepaymentLedgerService.PaymentCommand command(String amount, LocalDate date) {
        return new LoanRepaymentLedgerService.PaymentCommand(new BigDecimal(amount), date,
            LoanRepaymentTransaction.Channel.CASH, "cash-1", UUID.randomUUID());
    }
    private LoanLedgerInstallment row(int number, LocalDate date, String principal, String interest) {
        return LoanLedgerInstallment.builder().id(UUID.randomUUID()).loanApplicationId(loanId).installmentNumber(number)
            .dueDate(date).principal(money(principal)).interest(money(interest)).principalPaid(money("0")).interestPaid(money("0")).build();
    }
    private LoanRepaymentTransaction original(LoanRepaymentLedgerService.PaymentCommand c, String principal, String interest) {
        return LoanRepaymentTransaction.builder().id(UUID.randomUUID()).loanApplicationId(loanId).saccoId("I1").stationId("B1")
            .sequence(1).receiptReference("RP-original").requestKey(c.requestKey()).kind(LoanRepaymentTransaction.Kind.PAYMENT)
            .channel(c.channel()).channelReference(c.reference()).paymentDate(c.paymentDate()).amount(c.amount())
            .principalAmount(money(principal)).interestAmount(money(interest)).actorMemberId(posterId)
            .loanStatusBefore(LoanStatus.DISBURSED).postedAt(OffsetDateTime.parse("2026-10-01T12:00:00+03:00")).build();
    }
    private LoanRepaymentAllocation allocation(UUID transaction, UUID row, String principal, String interest) {
        return LoanRepaymentAllocation.builder().id(UUID.randomUUID()).transactionId(transaction).installmentId(row)
            .principalAmount(money(principal)).interestAmount(money(interest)).build();
    }
    private static BigDecimal money(String value) { return new BigDecimal(value).setScale(2); }
}
