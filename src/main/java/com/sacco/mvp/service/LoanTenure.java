package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Explicit tenure for new quotes; whole-month compatibility fields are never the pricing source. */
public record LoanTenure(String unit, int value) {
    public LoanTenure {
        if (unit == null || !java.util.Set.of("DAYS", "MONTHS", "YEARS").contains(unit) || value < 1
            || value > (unit.equals("DAYS") ? 18250 : unit.equals("YEARS") ? 50 : 600))
            throw new IllegalArgumentException("recording.error.terms");
    }
    public static LoanTenure from(Map<String, ?> data, int legacyMonths) {
        return data == null || data.get("tenureUnit") == null ? new LoanTenure("MONTHS", legacyMonths)
            : new LoanTenure(String.valueOf(data.get("tenureUnit")), Integer.parseInt(String.valueOf(data.get("tenureValue"))));
    }
    public static LoanTenure fromSnapshot(Map<String, ?> data,int legacyMonths) {
        return data!=null && LoanAmortizationCalculator.VERSION.equals(data.get("calculationVersion"))
            ? from(data,legacyMonths) : new LoanTenure("MONTHS",legacyMonths);
    }
    public BigDecimal months() {
        return unit.equals("DAYS") ? BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(365), MathContext.DECIMAL128)
            : BigDecimal.valueOf(unit.equals("YEARS") ? value * 12L : value);
    }
    public int compatibilityMonths() { return months().setScale(0, RoundingMode.CEILING).intValueExact(); }
    public int payments(RepaymentFrequency frequency) {
        int count = months().multiply(BigDecimal.valueOf(frequency == RepaymentFrequency.WEEKLY ? 52 : 12))
            .divide(BigDecimal.valueOf(12), 0, RoundingMode.CEILING).intValueExact();
        if (count > LoanAmortizationCalculator.MAX_PAYMENTS) throw new IllegalArgumentException("recording.error.terms");
        return count;
    }
    public void validate(LoanProductSetting p) {
        var min = new LoanTenure(p.getResolvedTenureUnit(), p.getResolvedMinimumTenure());
        var max = new LoanTenure(p.getResolvedTenureUnit(), p.getResolvedMaximumTenure());
        if (months().compareTo(min.months()) < 0 || months().compareTo(max.months()) > 0)
            throw new IllegalArgumentException("recording.error.productLimits");
    }
    public BigDecimal flatInterest(BigDecimal principal, BigDecimal annualRate) {
        return principal.multiply(annualRate).multiply(months()).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
    public LoanAmortizationCalculator.Result calculate(BigDecimal principal, BigDecimal annualRate, InterestMethod method,
                                                        RepaymentFrequency frequency, BigDecimal override, BigDecimal flat) {
        return calculate(principal, annualRate, method, frequency, override, flat, false);
    }
    public LoanAmortizationCalculator.Result calculate(BigDecimal principal, BigDecimal rate, InterestMethod method,
                                                        RepaymentFrequency frequency, BigDecimal override, BigDecimal flat, boolean wholeLoanRate) {
        int count = payments(frequency);
        BigDecimal effectiveRate = !wholeLoanRate && unit.equals("DAYS") ? rate.multiply(months()).multiply(BigDecimal.valueOf(frequency == RepaymentFrequency.WEEKLY ? 52 : 12))
            .divide(BigDecimal.valueOf(12L * count), MathContext.DECIMAL128) : rate;
        return LoanAmortizationCalculator.calculate(principal, count, effectiveRate, method, frequency, override, flat, wholeLoanRate);
    }
    public LocalDate firstDate(LocalDate start,RepaymentFrequency frequency){return unit.equals("DAYS")?start.plusDays((value+payments(frequency)-1)/payments(frequency)):frequency==RepaymentFrequency.WEEKLY?start.plusWeeks(1):start.plusMonths(1);}
    public LocalDate end(LocalDate start) { return unit.equals("DAYS") ? start.plusDays(value) : start.plusMonths(compatibilityMonths()); }
    public LocalDate dueDate(LocalDate start, LocalDate first, RepaymentFrequency frequency, int number, int count) {
        if (!unit.equals("DAYS")) return frequency == RepaymentFrequency.WEEKLY ? first.plusWeeks(number - 1L) : first.plusMonths(number - 1L);
        LocalDate last = end(start);
        long remaining = ChronoUnit.DAYS.between(first, last);
        if (remaining < count - 1L || (count == 1 && !first.equals(last))) throw new IllegalArgumentException("recording.error.date");
        return count == 1 ? first : first.plusDays(remaining * (number - 1L) / (count - 1L));
    }
    public void snapshot(Map<String,Object> data) { data.put("tenureUnit",unit); data.put("tenureValue",value); }
}
