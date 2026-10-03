package com.sacco.mvp.accounting.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Published management definitions. Regulatory reference estimates never post provisions. */
public final class LoanRiskMetrics {
    private LoanRiskMetrics() { }
    public static final String VERSION="MANAGEMENT_LOAN_METRICS_V1";
    public static final String REGULATORY_REFERENCE="BOT_TIER2_REG45_2019_ORDINARY_REFERENCE_ONLY";
    public enum Ageing { CURRENT, DAYS_1_30, DAYS_31_60, DAYS_61_90, DAYS_OVER_90 }
    public enum Classification { CURRENT, ESPECIALLY_MENTIONED, SUBSTANDARD, DOUBTFUL, LOSS }
    public record Exposure(String loanId,BigDecimal outstandingPrincipal,BigDecimal overduePrincipal,
            BigDecimal overdueInterest,LocalDate oldestUnpaidDueDate,boolean complete) { }
    public record Classified(Exposure source,int daysPastDue,Ageing ageing,Classification referenceClass,
            BigDecimal referenceRate,BigDecimal referenceProvision) { }
    public record Totals(long coveredLoans,long excludedLoans,BigDecimal outstandingPrincipal,
            BigDecimal overduePrincipal,BigDecimal overdueInterest,BigDecimal par1Principal,
            BigDecimal par30Principal,BigDecimal par90Principal,BigDecimal par1Percent,
            BigDecimal par30Percent,BigDecimal par90Percent,Map<Ageing,BigDecimal> ageing,
            Map<Classification,BigDecimal> referenceClassPrincipal,BigDecimal referenceProvision) {
        public Totals { ageing=Collections.unmodifiableMap(new EnumMap<>(ageing));referenceClassPrincipal=Collections.unmodifiableMap(new EnumMap<>(referenceClassPrincipal)); }
    }
    public static Optional<Classified> classify(Exposure exposure,LocalDate through) {
        if(exposure==null || through==null || !exposure.complete() || !money(exposure.outstandingPrincipal())
                || !money(exposure.overduePrincipal()) || !money(exposure.overdueInterest())
                || exposure.overduePrincipal().compareTo(exposure.outstandingPrincipal())>0)return Optional.empty();
        boolean overdue=exposure.overduePrincipal().add(exposure.overdueInterest()).signum()>0;
        if(overdue && (exposure.oldestUnpaidDueDate()==null || !exposure.oldestUnpaidDueDate().isBefore(through)))return Optional.empty();
        int dpd=overdue?Math.toIntExact(ChronoUnit.DAYS.between(exposure.oldestUnpaidDueDate(),through)):0;
        Ageing ageing=dpd==0?Ageing.CURRENT:dpd<=30?Ageing.DAYS_1_30:dpd<=60?Ageing.DAYS_31_60:dpd<=90?Ageing.DAYS_61_90:Ageing.DAYS_OVER_90;
        Classification classification=dpd<=5?Classification.CURRENT:dpd<=30?Classification.ESPECIALLY_MENTIONED:dpd<=60?Classification.SUBSTANDARD:dpd<=90?Classification.DOUBTFUL:Classification.LOSS;
        BigDecimal rate=new BigDecimal(switch(classification){case CURRENT->"0.01";case ESPECIALLY_MENTIONED->"0.05";case SUBSTANDARD->"0.25";case DOUBTFUL->"0.50";case LOSS->"1.00";});
        // A visible reference estimate rounds a per-loan minimum upward; the institution's actual policy remains separate.
        return Optional.of(new Classified(exposure,dpd,ageing,classification,rate,exposure.outstandingPrincipal().multiply(rate).setScale(2,RoundingMode.CEILING)));
    }
    public static Totals total(List<Exposure> exposures,LocalDate through,long missingLedgers) {
        if(exposures==null || exposures.size()>2000 || through==null || missingLedgers<0)throw new IllegalArgumentException("financial.risk.error.source");
        long covered=0,excluded=missingLedgers;BigDecimal outstanding=BigDecimal.ZERO,principal=BigDecimal.ZERO,interest=BigDecimal.ZERO,par1=BigDecimal.ZERO,par30=BigDecimal.ZERO,par90=BigDecimal.ZERO,provision=BigDecimal.ZERO;
        var ageing=new EnumMap<Ageing,BigDecimal>(Ageing.class);for(var a:Ageing.values())ageing.put(a,BigDecimal.ZERO);
        var classes=new EnumMap<Classification,BigDecimal>(Classification.class);for(var c:Classification.values())classes.put(c,BigDecimal.ZERO);
        for(Exposure exposure:exposures){var row=classify(exposure,through);if(row.isEmpty()){excluded++;continue;}var r=row.get();covered++;var amount=exposure.outstandingPrincipal();outstanding=outstanding.add(amount);principal=principal.add(exposure.overduePrincipal());interest=interest.add(exposure.overdueInterest());
            if(r.daysPastDue()>=1)par1=par1.add(amount);if(r.daysPastDue()>30)par30=par30.add(amount);if(r.daysPastDue()>90)par90=par90.add(amount);
            ageing.merge(r.ageing(),amount,BigDecimal::add);classes.merge(r.referenceClass(),amount,BigDecimal::add);provision=provision.add(r.referenceProvision());}
        return new Totals(covered,excluded,outstanding,principal,interest,par1,par30,par90,excluded==0?percentage(par1,outstanding):null,excluded==0?percentage(par30,outstanding):null,excluded==0?percentage(par90,outstanding):null,ageing,classes,provision);
    }
    static BigDecimal percentage(BigDecimal numerator,BigDecimal denominator){return denominator.signum()==0?null:numerator.multiply(new BigDecimal("100")).divide(denominator,6,RoundingMode.HALF_UP);}
    private static boolean money(BigDecimal value){if(value==null || value.signum()<0)return false;try{value.setScale(2,RoundingMode.UNNECESSARY);return true;}catch(ArithmeticException e){return false;}}
}
