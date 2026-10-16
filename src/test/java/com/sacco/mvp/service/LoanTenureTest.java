package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LoanTenureTest {
    @Test void wholeLoanReducingRatesUseActualPaymentsForEveryTenureAndFrequency() {
        for(var term:List.of(new LoanTenure("DAYS",9),new LoanTenure("MONTHS",6),new LoanTenure("YEARS",2))) {
            for(var frequency:RepaymentFrequency.values()) {
                var principal=new BigDecimal("1000.01");var rate=new BigDecimal("0.12");
                var result=term.calculate(principal,rate,InterestMethod.REDUCING_BALANCE,frequency,null,BigDecimal.ZERO,true);
                var expectedFirst=principal.multiply(rate).divide(BigDecimal.valueOf(term.payments(frequency)),2,java.math.RoundingMode.HALF_UP);
                assertThat(result.rows().getFirst().interest()).isEqualByComparingTo(expectedFirst);
                assertThat(result.rows().stream().map(LoanAmortizationCalculator.Installment::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(principal);
                assertThat(result.rows().getLast().remainingPrincipal()).isEqualByComparingTo("0.00");
                assertThat(result.totalRepayment()).isEqualByComparingTo(principal.add(result.totalInterest()));
                var snapshot=new HashMap<String,Object>();term.snapshot(snapshot);snapshot.put("calculationVersion",LoanAmortizationCalculator.VERSION);
                snapshot.put("repaymentFrequency",frequency.name());snapshot.put("interestMethod","REDUCING_BALANCE");snapshot.put("interestRate",rate);
                snapshot.put("loanMode","CUSTOM");snapshot.put("interestBasis","WHOLE_LOAN");snapshot.put("interestAmount",result.totalInterest());
                assertThat(LoanAmortizationCalculator.estimate(principal,term.compatibilityMonths(),snapshot)).isEqualTo(result);
            }
        }
    }
    @Test void yearsPreserveExistingMonthlyPricingAndExactPrincipal() {
        var term=new LoanTenure("YEARS",2);
        var a=term.calculate(new BigDecimal("100000.01"),new BigDecimal("0.12"),InterestMethod.REDUCING_BALANCE,RepaymentFrequency.MONTHLY,null,BigDecimal.ZERO);
        var b=LoanAmortizationCalculator.calculate(new BigDecimal("100000.01"),24,new BigDecimal("0.12"),InterestMethod.REDUCING_BALANCE,RepaymentFrequency.MONTHLY,null,BigDecimal.ZERO);
        assertThat(a).isEqualTo(b);assertThat(a.rows().stream().map(LoanAmortizationCalculator.Installment::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100000.01");
    }
    @Test void daysRemainExactAndUse365DayAnnualBasis() {
        var term=new LoanTenure("DAYS",10);var principal=new BigDecimal("1000.00");var rate=new BigDecimal("0.365");
        assertThat(term.flatInterest(principal,rate)).isEqualByComparingTo("10.00");
        assertThat(term.payments(RepaymentFrequency.MONTHLY)).isEqualTo(1);
        var a=term.calculate(principal,rate,InterestMethod.REDUCING_BALANCE,RepaymentFrequency.MONTHLY,null,BigDecimal.ZERO);
        assertThat(a.totalInterest()).isEqualByComparingTo("10.00");
        var snapshot=new HashMap<String,Object>();term.snapshot(snapshot);snapshot.put("calculationVersion",LoanAmortizationCalculator.VERSION);snapshot.put("repaymentFrequency","MONTHLY");snapshot.put("interestMethod","REDUCING_BALANCE");snapshot.put("interestRate",rate);snapshot.put("interestAmount",a.totalInterest());
        assertThat(LoanAmortizationCalculator.estimate(principal,term.compatibilityMonths(),snapshot)).isEqualTo(a);
        var start=LocalDate.of(2026,10,1);assertThat(term.dueDate(start,start.plusDays(10),RepaymentFrequency.MONTHLY,1,1)).isEqualTo(start.plusDays(10));
        assertThatThrownBy(()->term.dueDate(start,start.plusDays(30),RepaymentFrequency.MONTHLY,1,1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void shortDayLoansUseActualPaymentDensityForMonthlyAffordability(){
        var term=new LoanTenure("DAYS",10);var amount=new BigDecimal("1010.00");
        assertThat(LoanAmortizationCalculator.monthlyEquivalent(amount,term.payments(RepaymentFrequency.MONTHLY),term.months())).isEqualByComparingTo("3072.09");
        var weekly=new BigDecimal("505.00");assertThat(LoanAmortizationCalculator.monthlyEquivalent(weekly,term.payments(RepaymentFrequency.WEEKLY),term.months())).isGreaterThan(LoanAmortizationCalculator.monthlyEquivalent(weekly,RepaymentFrequency.WEEKLY));
        assertThatThrownBy(()->LoanAmortizationCalculator.monthlyEquivalent(BigDecimal.ZERO,1,term.months())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unversionedQuotesKeepTheirOriginalPeriodCountDespiteTenureHints(){
        var snapshot=new HashMap<String,Object>();snapshot.put("tenureUnit","DAYS");snapshot.put("tenureValue",10);snapshot.put("interestMethod","FLAT_RATE");snapshot.put("interestAmount",new BigDecimal("20.00"));
        var legacy=LoanAmortizationCalculator.estimate(new BigDecimal("1000.00"),6,snapshot);
        assertThat(legacy.rows()).hasSize(6);assertThat(legacy.totalInterest()).isEqualByComparingTo("20.00");
        assertThat(LoanTenure.fromSnapshot(snapshot,6)).isEqualTo(new LoanTenure("MONTHS",6));
    }
    @Test void explicitDayLimitsCannotBeBypassedThroughRoundedMonths() {
        var product=LoanProductSetting.builder().tenureUnit("DAYS").minTenure(5).maxTenure(20).build();
        new LoanTenure("DAYS",10).validate(product);
        assertThatThrownBy(()->new LoanTenure("DAYS",21).validate(product)).hasMessage("recording.error.productLimits");
        assertThatThrownBy(()->new LoanTenure("MONTHS",1).validate(product)).hasMessage("recording.error.productLimits");
        assertThatThrownBy(()->new LoanTenure("YEARS",0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new LoanTenure("YEARS",50).payments(RepaymentFrequency.WEEKLY)).isInstanceOf(IllegalArgumentException.class);
    }
}
