package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.accounting.repayment.repository.LoanPrepaymentRepository;
import com.sacco.mvp.accounting.repayment.service.LoanPrepaymentService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanPrepaymentCalculationTest {
    static BigDecimal m(String s){return new BigDecimal(s);}
    final UUID loan=UUID.randomUUID();
    final LocalDate start=LocalDate.of(2026,1,1),payment=LocalDate.of(2026,1,16);
    LoanLedger ledger(){return LoanLedger.builder().loanApplicationId(loan).principal(m("1000.00")).disbursementDate(start).build();}
    LoanLedgerInstallment row(int n,LocalDate date,String interest){return LoanLedgerInstallment.builder().id(UUID.randomUUID()).loanApplicationId(loan).installmentNumber(n).dueDate(date).principal(m("500.00")).principalPaid(m("0.00")).interest(m(interest)).interestPaid(m("0.00")).build();}
    @Test void defaultsAndSnapshotAreExplicitAndIndependentOfProductPricing(){
        var terms=LoanPrepaymentTerms.product(LoanProductSetting.builder().build());
        assertThat(terms.schedule()).isEqualTo(LoanPrepaymentTerms.Schedule.LAST_INSTALMENTS);
        assertThat(terms.partialInterest()).isEqualTo(LoanPrepaymentTerms.PartialInterest.RECALCULATE);
        assertThat(LoanPrepaymentTerms.read(terms.snapshot())).isEqualTo(terms);
        assertThat(LoanPrepaymentTerms.read(Map.of())).isNull();
        for(var role:Position.values())assertThat(UserClaim.defaultClaims(List.of(role),false)).doesNotContain(UserClaim.LOAN_INTEREST_WAIVE);
    }
    @Test void actualPeriodProrationExcludesTheSettlementDayAndRespectsLeapYears(){
        assertThat(LoanAmortizationCalculator.periodFraction(start,start.plusMonths(1),start,LoanPrepaymentTerms.DayBasis.ACTUAL_PERIOD)).isZero();
        assertThat(LoanAmortizationCalculator.periodFraction(start,start.plusMonths(1),start.plusMonths(1),LoanPrepaymentTerms.DayBasis.ACTUAL_PERIOD)).isOne();
        assertThat(LoanAmortizationCalculator.periodFraction(LocalDate.of(2024,2,1),LocalDate.of(2024,3,1),LocalDate.of(2024,2,15),LoanPrepaymentTerms.DayBasis.ACTUAL_PERIOD))
            .isEqualByComparingTo(m("14").divide(m("29"),java.math.MathContext.DECIMAL128));
    }
    @Test void thirtyE360UsesThirtyDayMonths(){
        assertThat(LoanAmortizationCalculator.periodFraction(start,start.plusMonths(1),payment,LoanPrepaymentTerms.DayBasis.THIRTY_360)).isEqualByComparingTo("0.5");
    }
    @Test void principalStrategiesConserveEveryCent(){
        var remaining=List.of(m("100.00"),m("100.00"),m("100.00"));
        assertThat(LoanAmortizationCalculator.allocateAdvance(m("125.01"),remaining,LoanPrepaymentTerms.Schedule.NEXT_INSTALMENTS)).containsExactly(m("100.00"),m("25.01"),m("0.00"));
        assertThat(LoanAmortizationCalculator.allocateAdvance(m("125.01"),remaining,LoanPrepaymentTerms.Schedule.LAST_INSTALMENTS)).containsExactly(m("0.00"),m("25.01"),m("100.00"));
        assertThat(LoanAmortizationCalculator.allocateAdvance(m("100.00"),remaining,LoanPrepaymentTerms.Schedule.PRO_RATA)).containsExactly(m("33.33"),m("33.33"),m("33.34"));
        assertThatThrownBy(()->LoanAmortizationCalculator.allocateAdvance(m("300.01"),remaining,LoanPrepaymentTerms.Schedule.PRO_RATA)).hasMessage("repayment.error.advance");
    }
    LoanPrepaymentService service(InterestMethod method,LoanPrepaymentTerms.PartialInterest partial){
        var repository=mock(LoanPrepaymentRepository.class);
        when(repository.terms(loan)).thenReturn(Optional.of(new LoanPrepaymentRepository.Terms(new LoanPrepaymentTerms(LoanPrepaymentTerms.Schedule.LAST_INSTALMENTS,partial,LoanPrepaymentTerms.DayBasis.ACTUAL_PERIOD),method,"CONTRACT")));
        return new LoanPrepaymentService(repository,mock(ApplicationClock.class),mock(AuditService.class),mock(com.sacco.mvp.accounting.service.VoucherService.class));
    }
    @Test void reducingBalanceRebateUsesThePrepaidPrincipalUntilItsOriginalDueDate(){
        var a=row(1,start.plusMonths(1),"100.00");var b=row(2,start.plusMonths(2),"50.00");
        var result=service(InterestMethod.REDUCING_BALANCE,LoanPrepaymentTerms.PartialInterest.RECALCULATE).reductions(ledger(),List.of(a,b),payment,Map.of(b.getId(),m("400.00")),Map.of(a.getId(),m("48.39")),false);
        assertThat(result.get(a.getId())).isEqualByComparingTo("20.65");assertThat(result.get(b.getId())).isEqualByComparingTo("40.00");
        result=service(InterestMethod.REDUCING_BALANCE,LoanPrepaymentTerms.PartialInterest.RECALCULATE).reductions(ledger(),List.of(a,b),payment,Map.of(a.getId(),m("400.00")),Map.of(a.getId(),m("48.39")),false);
        assertThat(result).containsOnlyKeys(a.getId());
    }
    @Test void flatRebateUsesOriginalPrincipalAndUnusedTime(){
        var a=row(1,start.plusMonths(1),"100.00");var b=row(2,start.plusMonths(2),"100.00");
        var result=service(InterestMethod.FLAT_RATE,LoanPrepaymentTerms.PartialInterest.RECALCULATE).reductions(ledger(),List.of(a,b),payment,Map.of(b.getId(),m("400.00")),Map.of(a.getId(),m("48.39")),false);
        assertThat(result.get(a.getId())).isEqualByComparingTo("20.65");assertThat(result.get(b.getId())).isEqualByComparingTo("40.00");
    }
    @Test void retainingThePartialScheduleNeverDisablesFullSettlementExclusion(){
        var a=row(1,start.plusMonths(1),"100.00");var b=row(2,start.plusMonths(2),"50.00");a.setInterestPaid(m("48.39"));
        var service=service(InterestMethod.FLAT_RATE,LoanPrepaymentTerms.PartialInterest.RETAIN_UNTIL_SETTLEMENT);
        assertThat(service.reductions(ledger(),List.of(a,b),payment,Map.of(),Map.of(a.getId(),m("48.39")),false)).isEmpty();
        var result=service.reductions(ledger(),List.of(a,b),payment,Map.of(),Map.of(a.getId(),m("48.39")),true);
        assertThat(result.get(a.getId())).isEqualByComparingTo("51.61");assertThat(result.get(b.getId())).isEqualByComparingTo("50.00");
    }
}
