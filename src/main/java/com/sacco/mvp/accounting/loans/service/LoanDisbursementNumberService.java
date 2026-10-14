package com.sacco.mvp.accounting.loans.service;

import com.sacco.mvp.accounting.loans.repository.LoanNumberRepository;
import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class LoanDisbursementNumberService {
    private final LoanNumberRepository numbers;
    private final VoucherRepository periods;
    private final ApplicationClock clock;
    @Transactional(propagation=Propagation.MANDATORY)
    public void assign(LoanApplication loan, UUID actor, LocalDate date) {
        UUID period = periods.period(loan.getSaccoId(), date, actor, clock.now());
        loan.setLoanId(Long.toString(numbers.next(loan.getSaccoId(), period)));
        loan.setLoanAccountingPeriodId(period);
    }
}
