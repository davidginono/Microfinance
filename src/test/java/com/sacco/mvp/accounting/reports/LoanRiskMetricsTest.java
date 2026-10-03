package com.sacco.mvp.accounting.reports;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.sacco.mvp.accounting.reports.LoanRiskMetrics.*;
import static org.assertj.core.api.Assertions.*;

class LoanRiskMetricsTest {
    final LocalDate day=LocalDate.of(2026,10,3);
    Exposure row(String principal,String overduePrincipal,String overdueInterest,int days){return new Exposure("Synthetic",new BigDecimal(principal),new BigDecimal(overduePrincipal),new BigDecimal(overdueInterest),days==0?null:day.minusDays(days),true);}
    @Test void parUsesEntireExposedPrincipalAndIsPrincipalWeighted(){var totals=total(List.of(row("1000.01","1.00","0.00",31),row("9000.00","0.00","0.00",0)),day,0);assertThat(totals.par30Principal()).isEqualByComparingTo("1000.01");assertThat(totals.par30Percent()).isEqualByComparingTo("10.000090");assertThat(totals.outstandingPrincipal()).isEqualByComparingTo("10000.01");}
    @Test void unpaidInterestAlonePlacesFullPrincipalAtRisk(){var result=total(List.of(row("500.00","0.00","1.01",91)),day,0);assertThat(result.par90Principal()).isEqualByComparingTo("500.00");assertThat(result.overduePrincipal()).isZero();assertThat(result.overdueInterest()).isEqualByComparingTo("1.01");}
    @Test void missingExposureCannotProduceCleanHeadlinePar(){var result=total(List.of(row("500.00","0.00","0.00",0)),day,1);assertThat(result.outstandingPrincipal()).isEqualByComparingTo("500.00");assertThat(result.par1Percent()).isNull();assertThat(result.par30Percent()).isNull();assertThat(result.par90Percent()).isNull();}
    @Test void zeroDenominatorIsUnavailableAndMissingDataIsExcluded(){var result=total(List.of(row("0.00","0.00","0.00",0),new Exposure("Unknown",null,null,null,null,false)),day,2);assertThat(result.par1Percent()).isNull();assertThat(result.excludedLoans()).isEqualTo(3);}
    @Test void managementAgeingAndRegulatoryReferenceHaveDifferentBoundaries(){var five=classify(row("100.00","1.00","0.00",5),day).orElseThrow();var six=classify(row("100.00","1.00","0.00",6),day).orElseThrow();assertThat(five.ageing()).isEqualTo(Ageing.DAYS_1_30);assertThat(five.referenceClass()).isEqualTo(Classification.CURRENT);assertThat(six.referenceClass()).isEqualTo(Classification.ESPECIALLY_MENTIONED);}
    @Test void referenceMinimumRoundingIsExplicitAndDoesNotPost(){assertThat(classify(row("100.01","0.00","0.00",0),day).orElseThrow().referenceProvision()).isEqualByComparingTo("1.01");}
    @Test void invalidOutstandingOrMissingDueDateCannotBecomeCleanHistory(){assertThat(classify(new Exposure("Bad",new BigDecimal("10.00"),new BigDecimal("11.00"),BigDecimal.ZERO,day.minusDays(1),true),day)).isEmpty();assertThat(classify(new Exposure("Bad",new BigDecimal("10.00"),BigDecimal.ONE,BigDecimal.ZERO,null,true),day)).isEmpty();}
    @Test void parThresholdsUseElapsedDaysExactly(){var result=total(List.of(row("10.00","1.00","0.00",30),row("20.00","1.00","0.00",31),row("40.00","1.00","0.00",90),row("80.00","1.00","0.00",91)),day,0);assertThat(result.par1Principal()).isEqualByComparingTo("150.00");assertThat(result.par30Principal()).isEqualByComparingTo("140.00");assertThat(result.par90Principal()).isEqualByComparingTo("80.00");}
}
