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
    public record Waiver(UUID id,UUID loanId,long sequence,LocalDate date,BigDecimal amount,UUID actor,String reason,UUID reverses,UUID reversedBy) {}
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
        var result=new HashMap<UUID,BigDecimal>();
        jdbc.query("select id,loan_interest_earned_exact(id,?) earned from loan_ledger_installments where loan_application_id=? order by installment_number limit 600",
            r->{result.put(r.getObject("id",UUID.class),r.getBigDecimal("earned"));},date,loan);
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
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from loan_repayment_transactions t where t.loan_application_id=? and t.sequence>? and t.kind in ('PAYMENT','SETTLEMENT') and not exists(select 1 from loan_repayment_transactions r where r.reverses_transaction_id=t.id))",Boolean.class,loan,sequence));
    }
    private static final String WAIVER="select w.*,r.id reversed_by from loan_interest_waiver w left join loan_interest_waiver r on r.reverses_id=w.id ";
    private Waiver row(java.sql.ResultSet r,int n)throws java.sql.SQLException {
        return new Waiver(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getLong("sequence"),r.getObject("effective_date",LocalDate.class),r.getBigDecimal("amount"),r.getObject("actor_id",UUID.class),r.getString("reason"),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class));
    }
    public Optional<Waiver> request(LoanLedger l,UUID key) {return jdbc.query(WAIVER+"where w.sacco_id=? and w.station_id=? and w.request_key=?",this::row,l.getSaccoId(),l.getStationId(),key).stream().findFirst();}
    public List<Waiver> history(LoanLedger l,int page) {return jdbc.query(WAIVER+"where w.loan_id=? and w.sacco_id=? and w.station_id=? order by w.sequence desc limit 26 offset ?",this::row,l.getLoanApplicationId(),l.getSaccoId(),l.getStationId(),page*25);}
    public Optional<Waiver> waiver(LoanLedger l,UUID id) {return jdbc.query(WAIVER+"where w.id=? and w.loan_id=? and w.sacco_id=? and w.station_id=?",this::row,id,l.getLoanApplicationId(),l.getSaccoId(),l.getStationId()).stream().findFirst();}
    public Map<UUID,BigDecimal> parts(UUID id) {
        var result=new LinkedHashMap<UUID,BigDecimal>();jdbc.query("select installment_id,amount from loan_interest_waiver_part where waiver_id=? limit 600",r->{result.put(r.getObject(1,UUID.class),r.getBigDecimal(2));},id);return result;
    }
    public void waiver(LoanLedger l,UUID id,UUID key,LocalDate date,BigDecimal amount,UUID actor,String reason,UUID reverses,OffsetDateTime now,Map<UUID,BigDecimal> parts) {
        jdbc.update("insert into loan_interest_waiver(id,loan_id,sacco_id,station_id,sequence,kind,request_key,effective_date,amount,actor_id,reason,reverses_id,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,l.getLoanApplicationId(),l.getSaccoId(),l.getStationId(),l.getNextSequence(),reverses==null?"WAIVER":"REVERSAL",key,date,amount,actor,reason,reverses,now);
        jdbc.batchUpdate("insert into loan_interest_waiver_part(waiver_id,installment_id,amount) values(?,?,?)",parts.entrySet(),600,(p,e)->{p.setObject(1,id);p.setObject(2,e.getKey());p.setBigDecimal(3,e.getValue());});
    }
}
