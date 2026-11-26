package com.sacco.mvp.accounting.repayment.repository;

import com.sacco.mvp.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class LoanPrepaymentRepository {
    private final JdbcTemplate jdbc;
    public record Terms(LoanPrepaymentTerms rules,InterestMethod method,String source) {}
    public record MonthlyTerms(LocalDate effectiveDate, int firstInstallment, EarlyPayoffInterest payoffInterest,
                               UUID actor, UUID requestKey, String agreementReference, String approvalReference) {}
    public record Waiver(UUID id,UUID loanId,long sequence,LocalDate date,BigDecimal amount,BigDecimal futureAmount,
                         UUID actor,String reason,UUID reverses,UUID reversedBy) {
        public BigDecimal accruedAmount() { return amount.subtract(futureAmount); }
    }
    public record WaiverPart(BigDecimal amount,BigDecimal futureAmount) {}
    public Optional<Terms> terms(UUID loan) {
        return jdbc.query("select * from loan_prepayment_terms where loan_id=?",(r,n)->new Terms(new LoanPrepaymentTerms(
            LoanPrepaymentTerms.Schedule.valueOf(r.getString("schedule")),LoanPrepaymentTerms.PartialInterest.valueOf(r.getString("partial_interest")),
            LoanPrepaymentTerms.DayBasis.valueOf(r.getString("day_basis"))),InterestMethod.valueOf(r.getString("interest_method")),r.getString("source")),loan).stream().findFirst();
    }
    public void bind(LoanLedger l,LoanPrepaymentTerms terms,InterestMethod method,String source,UUID actor,String reason,OffsetDateTime now) {
        jdbc.update("insert into loan_prepayment_terms(loan_id,sacco_id,station_id,schedule,partial_interest,day_basis,interest_method,source,actor_id,reason,created_at) values(?,?,?,?,?,?,?,?,?,?,?)",
            l.getLoanApplicationId(),l.getSaccoId(),l.getStationId(),terms.schedule().name(),terms.partialInterest().name(),terms.dayBasis().name(),method.name(),source,actor,reason,now);
    }
    public Map<UUID,BigDecimal> earned(UUID loan,LocalDate date) {
        return interest(loan,date,false);
    }
    public void bindAccrual(LoanLedger ledger,InterestAccrualMode mode,String tenureUnit) {
        jdbc.update("insert into loan_contract_interest_accrual(loan_id,mode,tenure_unit) values(?,?,?)",
            ledger.getLoanApplicationId(),mode.name(),tenureUnit);
    }
    public Map<UUID,BigDecimal> interest(UUID loan,LocalDate date,boolean collectible) {
        var result=new HashMap<UUID,BigDecimal>();
        String function=collectible?"loan_interest_collectible_exact":"loan_interest_earned_exact";
        jdbc.query("select id,"+function+"(id,?) earned from loan_ledger_installments where loan_application_id=? order by installment_number limit 600",
            r->{result.put(r.getObject("id",UUID.class),r.getBigDecimal("earned"));},date,loan);
        return result;
    }
    public Map<LocalDate,Map<UUID,BigDecimal>> accrualFractions(UUID loan,Collection<LocalDate> dates,boolean collectible) {
        var result=new HashMap<LocalDate,Map<UUID,BigDecimal>>();
        jdbc.query("""
            select d.as_of,i.id,loan_contract_interest_fraction(term.mode,term.tenure_unit,a.repayment_frequency,
              l.disbursement_date,prior.due_date,i.due_date,d.as_of,?) fraction
            from loan_contract_interest_accrual term join loan_ledgers l on l.loan_application_id=term.loan_id
            join loan_applications a on a.id=term.loan_id
            join loan_ledger_installments i on i.loan_application_id=term.loan_id
            left join loan_ledger_installments prior on prior.loan_application_id=i.loan_application_id
               and prior.installment_number=i.installment_number-1
            cross join unnest(string_to_array(?,',')::date[]) d(as_of)
            where term.loan_id=?
            """,r -> {result.computeIfAbsent(r.getObject("as_of",LocalDate.class),unused -> new HashMap<>())
                .put(r.getObject("id",UUID.class),r.getBigDecimal("fraction"));},collectible,
            dates.stream().map(LocalDate::toString).collect(java.util.stream.Collectors.joining(",")),loan);
        return result;
    }
    public Optional<MonthlyTerms> monthlyTerms(LoanLedger ledger,LocalDate date,UUID request) {
        return jdbc.query("""
            select term.* from loan_monthly_interest_terms term
            join loan_ledgers l on l.loan_application_id=term.loan_id
            where term.loan_id=? and l.sacco_id=? and l.station_id=? and term.effective_date<=?
              and (?::uuid is null or term.request_key=?)
            order by term.first_installment_number desc limit 1
            """, (r,n) -> new MonthlyTerms(r.getObject("effective_date",LocalDate.class),r.getInt("first_installment_number"),
            EarlyPayoffInterest.valueOf(r.getString("payoff_interest")),r.getObject("actor_id",UUID.class),
            r.getObject("request_key",UUID.class),r.getString("agreement_reference"),r.getString("approval_reference")),
            ledger.getLoanApplicationId(),ledger.getSaccoId(),ledger.getStationId(),date,request,request).stream().findFirst();
    }
    public int firstMonthlyInstallment(LoanLedger ledger) {
        return jdbc.queryForObject("""
            select coalesce(min(t.first_installment_number),2147483647)
            from loan_monthly_interest_terms t join loan_ledgers l on l.loan_application_id=t.loan_id
            where t.loan_id=? and l.sacco_id=? and l.station_id=?
            """,Integer.class,ledger.getLoanApplicationId(),ledger.getSaccoId(),ledger.getStationId());
    }
    public void bindMonthly(LoanLedger ledger, MonthlyTerms terms, OffsetDateTime now) {
        jdbc.update("""
            insert into loan_monthly_interest_terms(loan_id,effective_date,first_installment_number,payoff_interest,
                actor_id,request_key,agreement_reference,approval_reference,recorded_at)
            values (?,?,?,?,?,?,?,?,?)
            """,ledger.getLoanApplicationId(),terms.effectiveDate(),terms.firstInstallment(),terms.payoffInterest().name(),
            terms.actor(),terms.requestKey(),terms.agreementReference(),terms.approvalReference(),now);
    }
    public Map<UUID,BigDecimal> elapsedInterest(LoanLedger ledger, LocalDate date) {
        var result = new HashMap<UUID,BigDecimal>();
        jdbc.query("""
            select i.id,loan_interest_daily_net_of_waivers(i.id,?,null) earned
            from loan_ledger_installments i join loan_ledgers l on l.loan_application_id=i.loan_application_id
            where i.loan_application_id=? and l.sacco_id=? and l.station_id=?
            order by i.installment_number limit 600
            """,r -> {result.put(r.getObject("id",UUID.class),r.getBigDecimal("earned"));},
            date,ledger.getLoanApplicationId(),ledger.getSaccoId(),ledger.getStationId());
        return result;
    }
    public LocalDate latestDate(UUID loan) {return jdbc.queryForObject("select max(effective_date) from loan_interest_waiver where loan_id=?",LocalDate.class,loan);}
    public BigDecimal charges(LoanLedger l) {
        return jdbc.query("select outstanding from loan_charge_balance where loan_id=? and sacco_id=? and station_id=?",(r,n)->r.getBigDecimal(1),l.getLoanApplicationId(),l.getSaccoId(),l.getStationId()).stream().findFirst().orElse(BigDecimal.ZERO);
    }
    public boolean laterWaiver(UUID loan,long sequence) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_interest_waiver w where w.loan_id=? and w.sequence>? and w.kind='WAIVER' and not exists(select 1 from loan_interest_waiver r where r.reverses_id=w.id))",Boolean.class,loan,sequence));
    }
    public boolean laterPayment(UUID loan,long sequence){
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_repayment_transactions t where t.loan_application_id=? and t.sequence>? and t.kind in ('PAYMENT','SETTLEMENT','PAYOFF') and not exists(select 1 from loan_repayment_transactions r where r.reverses_transaction_id=t.id))",Boolean.class,loan,sequence));
    }
    private static final String WAIVER="select w.*,r.id reversed_by from loan_interest_waiver w left join loan_interest_waiver r on r.reverses_id=w.id ";
    private Waiver row(java.sql.ResultSet r,int n)throws java.sql.SQLException {
        return new Waiver(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getLong("sequence"),r.getObject("effective_date",LocalDate.class),r.getBigDecimal("amount"),r.getBigDecimal("future_amount"),r.getObject("actor_id",UUID.class),r.getString("reason"),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class));
    }
    public Optional<Waiver> request(LoanLedger l,UUID key) {return jdbc.query(WAIVER+"where w.sacco_id=? and w.station_id=? and w.request_key=?",this::row,l.getSaccoId(),l.getStationId(),key).stream().findFirst();}
    public List<Waiver> history(LoanLedger l,int page) {return jdbc.query(WAIVER+"where w.loan_id=? and w.sacco_id=? and w.station_id=? order by "+com.sacco.mvp.support.ResourceTableSort.order("interest-waivers",Map.of("reference","w.sequence","date","w.effective_date","amount","w.amount","kind","case when w.reverses_id is not null then 'REVERSAL' when r.id is not null then 'REVERSED' else 'WAIVER' end","reason","w.reason"),"w.id","w.sequence desc")+" limit 26 offset ?",this::row,l.getLoanApplicationId(),l.getSaccoId(),l.getStationId(),page*25);}
    public Optional<Waiver> waiver(LoanLedger l,UUID id) {return jdbc.query(WAIVER+"where w.id=? and w.loan_id=? and w.sacco_id=? and w.station_id=?",this::row,id,l.getLoanApplicationId(),l.getSaccoId(),l.getStationId()).stream().findFirst();}
    public Map<UUID,WaiverPart> parts(UUID id) {
        var result = new LinkedHashMap<UUID,WaiverPart>();
        jdbc.query("select installment_id,amount,future_amount from loan_interest_waiver_part where waiver_id=? limit 600",
            r->{result.put(r.getObject(1,UUID.class),new WaiverPart(r.getBigDecimal(2),r.getBigDecimal(3)));},id);
        return result;
    }
    public void waiver(LoanLedger l,UUID id,UUID key,LocalDate date,BigDecimal amount,BigDecimal futureAmount,
                       UUID actor,String reason,UUID reverses,OffsetDateTime now,Map<UUID,WaiverPart> parts) {
        jdbc.update("insert into loan_interest_waiver(id,loan_id,sacco_id,station_id,sequence,kind,request_key,effective_date,amount,future_amount,actor_id,reason,reverses_id,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,l.getLoanApplicationId(),l.getSaccoId(),l.getStationId(),l.getNextSequence(),reverses==null?"WAIVER":"REVERSAL",key,date,amount,futureAmount,actor,reason,reverses,now);
        jdbc.batchUpdate("insert into loan_interest_waiver_part(waiver_id,installment_id,amount,future_amount) values(?,?,?,?)",
            parts.entrySet(),600,(p,e)->{
                p.setObject(1,id);
                p.setObject(2,e.getKey());
                p.setBigDecimal(3,e.getValue().amount());
                p.setBigDecimal(4,e.getValue().futureAmount());
            });
    }
}
