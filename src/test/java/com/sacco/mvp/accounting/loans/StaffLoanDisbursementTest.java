package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.PostForm;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class StaffLoanDisbursementTest {
    @Mock LoanRecordingRepository records;
    @Mock LoanApplicationRepository loans;
    @Mock GeneralLedgerService accounting;
    @Mock LoanRepaymentLedgerService repayments;
    @Mock ApplicationClock clock;
    @Mock com.sacco.mvp.grouplending.service.SimpleGroupLoanService simpleGroups;
    @Mock com.sacco.mvp.grouplending.service.GroupSharedLoanService sharedLoans;
    @Spy ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    @InjectMocks LoanRecordingService service;

    @Test void borrowerCannotPostTheirOwnRecordedLoanEvenWithDisbursementClaim() {
        UUID person = UUID.randomUUID();
        var actor = new AppUserPrincipal(Member.builder().id(person).saccoId("I1").stationId("B1")
            .position(Position.DISBURSEMENT_OFFICER).staffAccessStatus(StaffAccessStatus.ACTIVE).memberAccount(false).status(com.sacco.mvp.domain.MemberStatus.ACTIVE).build(),Set.of(UserClaim.DISBURSEMENT_QUEUE_DISBURSE));
        var loan = LoanApplication.builder().id(UUID.randomUUID()).applicantMemberId(person).status(LoanStatus.READY_FOR_DISBURSEMENT).build();
        var form = new PostForm(); form.setLoanId(loan.getId()); form.setAmount(BigDecimal.TEN);
        form.setEffectiveDate(LocalDate.of(2026,10,30)); form.setReference("Synthetic reference");
        when(clock.today()).thenReturn(form.getEffectiveDate());
        when(loans.findForDisbursement(loan.getId())).thenReturn(Optional.of(loan));
        assertThatThrownBy(() -> service.disburse(actor, form)).hasMessage("staffClient.error.ownLoan");
        verifyNoInteractions(repayments);
        verify(loans, never()).save(any());
    }
}
