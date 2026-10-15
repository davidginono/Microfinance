package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.repository.LoanPostingAuthorizationRepository;
import com.sacco.mvp.accounting.loans.service.LoanPostingGuard;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanRepaymentLedgerService.PaymentCommand;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanPostingGuardTest {
    private final LoanPostingAuthorizationRepository sources = mock(LoanPostingAuthorizationRepository.class);
    private final LoanPostingGuard guard = new LoanPostingGuard(sources);
    private final UUID loan = UUID.randomUUID();
    private final AppUserPrincipal actor = mock(AppUserPrincipal.class);
    private final PaymentCommand payment = new PaymentCommand(new BigDecimal("100.00"), LocalDate.of(2026,10,15), null, "TEST-1", UUID.randomUUID());

    @Test void ordinaryLoansDoNotDependOnRemovedPolicySettings() {
        var application = mock(LoanApplication.class);
        when(application.getId()).thenReturn(loan);
        assertThatCode(() -> guard.disbursement(application, UUID.randomUUID())).doesNotThrowAnyException();
        assertThatCode(() -> guard.repayment(loan, actor, payment)).doesNotThrowAnyException();
        assertThatCode(() -> guard.reversal(loan, actor, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test void retainedSourceOwnershipBlocksDirectChanges() {
        when(sources.retainedSourceOwnsLoan(loan)).thenReturn(true);
        var application = mock(LoanApplication.class);
        when(application.getId()).thenReturn(loan);
        assertThatThrownBy(() -> guard.disbursement(application, UUID.randomUUID())).hasMessage("recording.error.retiredSource");
        assertThatThrownBy(() -> guard.repayment(loan, actor, payment)).hasMessage("recording.error.retiredSource");
        assertThatThrownBy(() -> guard.reversal(loan, actor, UUID.randomUUID())).hasMessage("recording.error.retiredSource");
    }

    @Test void recordedPaymentRequiresItsExactAuthorizedPostingTicket() {
        when(sources.recordedLoan(loan)).thenReturn(true);
        assertThatThrownBy(() -> guard.repayment(loan, actor, payment)).hasMessage("recording.error.directPath");
        when(sources.recordedCommand(loan, actor, payment.requestKey(), "PAYMENT", payment.reference(), payment.amount(), payment.paymentDate())).thenReturn(true);
        assertThatCode(() -> guard.repayment(loan, actor, payment)).doesNotThrowAnyException();
        var changed = new PaymentCommand(new BigDecimal("101.00"), payment.paymentDate(), null, payment.reference(), payment.requestKey());
        assertThatThrownBy(() -> guard.repayment(loan, actor, changed)).hasMessage("recording.error.directPath");
    }

    @Test void recordedReversalRequiresTheOriginalTransactionAndDate() {
        when(sources.recordedLoan(loan)).thenReturn(true);
        UUID key = UUID.randomUUID(), original = UUID.randomUUID();
        LocalDate date = payment.paymentDate();
        assertThatThrownBy(() -> guard.reversal(loan, actor, key, date, original)).hasMessage("recording.error.directPath");
        when(sources.recordedReversal(loan, actor, key, date, original)).thenReturn(true);
        assertThatCode(() -> guard.reversal(loan, actor, key, date, original)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.reversal(loan, actor, key, date.plusDays(1), original)).hasMessage("recording.error.directPath");
    }
}
