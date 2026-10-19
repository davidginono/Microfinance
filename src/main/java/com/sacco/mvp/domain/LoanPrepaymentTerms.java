package com.sacco.mvp.domain;

import java.util.Map;

/** Servicing conventions retained with the accepted financial snapshot. */
public record LoanPrepaymentTerms(Schedule schedule, PartialInterest partialInterest, DayBasis dayBasis) {
    public enum Schedule { NEXT_INSTALMENTS, LAST_INSTALMENTS, PRO_RATA }
    public enum PartialInterest { RECALCULATE, RETAIN_UNTIL_SETTLEMENT }
    public enum DayBasis { ACTUAL_PERIOD, THIRTY_360 }
    public static LoanPrepaymentTerms defaults() {
        return new LoanPrepaymentTerms(Schedule.LAST_INSTALMENTS, PartialInterest.RECALCULATE, DayBasis.ACTUAL_PERIOD);
    }
    public LoanPrepaymentTerms {
        if (schedule == null || partialInterest == null || dayBasis == null) throw new IllegalArgumentException("prepayment.error.terms");
    }
    public static LoanPrepaymentTerms product(LoanProductSetting p) {
        var d=defaults();
        return p==null?d:new LoanPrepaymentTerms(p.getPrepaymentSchedule()==null?d.schedule():p.getPrepaymentSchedule(),
            p.getPartialPrepaymentInterest()==null?d.partialInterest():p.getPartialPrepaymentInterest(),
            p.getSettlementDayBasis()==null?d.dayBasis():p.getSettlementDayBasis());
    }
    public Map<String,String> snapshot() {
        return Map.of("version","PREPAYMENT_V1","schedule",schedule.name(),"partialInterest",partialInterest.name(),"dayBasis",dayBasis.name());
    }
    public static LoanPrepaymentTerms read(Object raw) {
        if (!(raw instanceof Map<?,?> m) || !"PREPAYMENT_V1".equals(m.get("version"))) return null;
        return new LoanPrepaymentTerms(Schedule.valueOf(m.get("schedule").toString()),PartialInterest.valueOf(m.get("partialInterest").toString()),DayBasis.valueOf(m.get("dayBasis").toString()));
    }
}
