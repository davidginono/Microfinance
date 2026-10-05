package com.sacco.mvp.accounting.reports;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.sacco.mvp.accounting.reports.LedgerReportService.*;

/** Ordinary loan metrics are derived from immutable postings, never mutable paid/status projections. */
@Repository
@RequiredArgsConstructor
public class LoanPortfolioReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public record Summary(long covered,long excluded,BigDecimal principal,BigDecimal overduePrincipal,BigDecimal overdueInterest,
            BigDecimal par1,BigDecimal par30,BigDecimal par90,BigDecimal disbursed,BigDecimal collectedPrincipal,BigDecimal collectedInterest) { }
    public record SourceRow(UUID id,String number,BigDecimal principal,BigDecimal overduePrincipal,BigDecimal overdueInterest,
            LocalDate oldest,boolean complete) { }
    public record Bucket(int band,long loans,BigDecimal principal) { }
    public record BranchPerformance(String branch,Summary summary) { }
    private MapSqlParameterSource parameters(Scope s,Parameters p){return new MapSqlParameterSource().addValue("institution",s.institution()).addValue("branch",s.branch()).addValue("wide",s.institutionWide()).addValue("from",p.from()).addValue("through",p.through()).addValue("cutoff",p.recordedCutoff()).addValue("offset",Math.multiplyExact(p.page(),25));}
    private static final String SOURCE="""
        WITH cohort AS (
          SELECT l.loan_application_id,l.sacco_id,l.station_id,l.loan_id,l.applicant_member_id,l.disbursement_date,l.principal, a.station_id application_branch,a.sacco_id application_institution,
            a.applicant_member_id application_client,a.top_up_source_loan_id,a.repayment_schedule_json contract
          FROM loan_ledgers l JOIN loan_applications a ON a.id=l.loan_application_id
          WHERE l.sacco_id=:institution AND (:wide OR l.station_id=:branch)
            AND l.created_at<=:cutoff AND l.disbursement_date<=:through
        ), origins AS (
          SELECT c.loan_application_id,count(*) lines,count(DISTINCT j.voucher_id) vouchers,
            sum(j.debit-j.credit) net,
            sum(CASE WHEN j.account_code='LOAN_PRINCIPAL' THEN j.debit-j.credit ELSE 0 END) principal,
            sum(CASE WHEN j.account_code='DISBURSEMENT_CLEARING' THEN j.credit-j.debit ELSE 0 END) cleared,
            bool_and(j.effective_date=c.disbursement_date) dates
          FROM cohort c JOIN loan_journal_entries j ON j.loan_application_id=c.loan_application_id
          WHERE j.transaction_id IS NULL AND j.posted_at<=:cutoff AND j.effective_date<=:through
          GROUP BY c.loan_application_id
        ), transactions AS (
          SELECT t.*,CASE WHEN t.kind='PAYMENT' THEN 1 ELSE -1 END sign
          FROM loan_repayment_transactions t JOIN cohort c ON c.loan_application_id=t.loan_application_id
          WHERE t.posted_at<=:cutoff AND t.payment_date<=:through
        ), transaction_allocations AS (
          SELECT t.id,sum(a.principal_amount) principal,sum(a.interest_amount) interest,
            bool_and(i.loan_application_id=t.loan_application_id) owned
          FROM transactions t LEFT JOIN loan_repayment_allocations a ON a.transaction_id=t.id
          LEFT JOIN loan_ledger_installments i ON i.id=a.installment_id GROUP BY t.id
        ), transaction_journals AS (
          SELECT t.id,count(j.id) lines,sum(j.debit-j.credit) net,
            sum(CASE WHEN j.account_code='LOAN_PRINCIPAL' THEN j.debit-j.credit ELSE 0 END) principal,
            sum(CASE WHEN j.account_code='INTEREST_COLLECTIONS_CLEARING' THEN j.debit-j.credit ELSE 0 END) interest,
            sum(CASE WHEN j.account_code=CASE t.channel WHEN 'CASH' THEN 'CASH_CLEARING' WHEN 'BANK' THEN 'BANK_CLEARING'
              ELSE 'MOBILE_MONEY_CLEARING' END THEN j.debit-j.credit ELSE 0 END) receiving,
            bool_and(j.loan_application_id=t.loan_application_id AND j.voucher_id=t.id
              AND j.effective_date=t.payment_date AND j.posted_at<=:cutoff) owned
          FROM transactions t LEFT JOIN loan_journal_entries j ON j.transaction_id=t.id AND j.posted_at<=:cutoff GROUP BY t.id
        ), payments AS (
          SELECT c.loan_application_id,coalesce(sum(t.sign*t.principal_amount),0) principal,
            coalesce(sum(t.sign*t.interest_amount),0) interest,
            coalesce(sum(CASE WHEN t.payment_date>=:from THEN t.sign*t.principal_amount ELSE 0 END),0) collected_principal,
            coalesce(sum(CASE WHEN t.payment_date>=:from THEN t.sign*t.interest_amount ELSE 0 END),0) collected_interest,
            coalesce(bool_and(t.sacco_id=c.sacco_id AND t.station_id=c.station_id AND t.payment_date>=c.disbursement_date
              AND a.owned IS TRUE AND a.principal=t.principal_amount AND a.interest=t.interest_amount
              AND j.owned IS TRUE AND j.lines=1+(CASE WHEN t.principal_amount>0 THEN 1 ELSE 0 END)+(CASE WHEN t.interest_amount>0 THEN 1 ELSE 0 END)
              AND j.net=0 AND j.principal=-t.sign*t.principal_amount AND j.receiving=t.sign*t.amount
              AND j.interest=-t.sign*t.interest_amount
              AND (t.kind='PAYMENT' OR (r.kind='PAYMENT' AND r.loan_application_id=t.loan_application_id
                AND r.sacco_id=t.sacco_id AND r.station_id=t.station_id AND r.posted_at<=t.posted_at
                AND r.principal_amount=t.principal_amount AND r.interest_amount=t.interest_amount AND r.amount=t.amount))) FILTER(WHERE t.id IS NOT NULL),true) valid
          FROM cohort c LEFT JOIN transactions t ON t.loan_application_id=c.loan_application_id
          LEFT JOIN transaction_allocations a ON a.id=t.id LEFT JOIN transaction_journals j ON j.id=t.id
          LEFT JOIN loan_repayment_transactions r ON r.id=t.reverses_transaction_id GROUP BY c.loan_application_id
        ), installment_allocations AS (
          SELECT a.installment_id,sum(t.sign*a.principal_amount) principal,sum(t.sign*a.interest_amount) interest
          FROM loan_repayment_allocations a JOIN transactions t ON t.id=a.transaction_id GROUP BY a.installment_id
        ), contract_terms AS (
          SELECT c.loan_application_id,n.number,n.item->>'dueDate' due_date,
            CASE WHEN n.item->>'principalComponent' ~ '^[0-9]{1,16}([.][0-9]{1,8})?$' THEN (n.item->>'principalComponent')::numeric END principal,
            CASE WHEN n.item->>'interestComponent' ~ '^[0-9]{1,16}([.][0-9]{1,8})?$' THEN (n.item->>'interestComponent')::numeric END interest
          FROM cohort c CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(c.contract->'schedule')='array'
            THEN CASE WHEN jsonb_array_length(c.contract->'schedule')<=600 THEN c.contract->'schedule' ELSE '[]'::jsonb END
            ELSE '[]'::jsonb END) WITH ORDINALITY n(item,number)
        ), contract_counts AS (
          SELECT loan_application_id,count(*) total FROM contract_terms GROUP BY loan_application_id
        ), terms AS (
          SELECT c.loan_application_id,count(i.id) installments,sum(i.principal) scheduled_principal,
            coalesce(bool_and(coalesce(a.principal,0)>=0 AND coalesce(a.principal,0)<=i.principal
              AND coalesce(a.interest,0)>=0 AND coalesce(a.interest,0)<=i.interest
              AND ct.number IS NOT NULL AND ct.principal IS NOT NULL AND ct.interest IS NOT NULL
              AND ct.due_date=i.due_date::text AND ct.principal=i.principal AND ct.interest=i.interest),false) valid,
            coalesce(cc.total,0) contract_count,
            coalesce(sum(CASE WHEN i.due_date<:through THEN i.principal-coalesce(a.principal,0) ELSE 0 END),0) overdue_principal,
            coalesce(sum(CASE WHEN i.due_date<:through THEN i.interest-coalesce(a.interest,0) ELSE 0 END),0) overdue_interest,
            min(i.due_date) FILTER(WHERE i.due_date<:through AND (i.principal>coalesce(a.principal,0) OR i.interest>coalesce(a.interest,0))) oldest
          FROM cohort c LEFT JOIN loan_ledger_installments i ON i.loan_application_id=c.loan_application_id
          LEFT JOIN installment_allocations a ON a.installment_id=i.id
          LEFT JOIN contract_terms ct ON ct.loan_application_id=c.loan_application_id AND ct.number=i.installment_number
          LEFT JOIN contract_counts cc ON cc.loan_application_id=c.loan_application_id GROUP BY c.loan_application_id,cc.total
        ), exposure AS MATERIALIZED (
          SELECT c.loan_application_id,c.loan_id,c.station_id,c.disbursement_date,c.principal original_principal,
            c.principal-p.principal principal,t.overdue_principal,t.overdue_interest,t.oldest,
            coalesce(:through-t.oldest,0) dpd,p.collected_principal,p.collected_interest,
            coalesce(c.application_branch=c.station_id AND c.application_institution=c.sacco_id
              AND c.application_client=c.applicant_member_id AND c.top_up_source_loan_id IS NULL
              AND o.lines=2 AND o.vouchers=1 AND o.net=0 AND o.principal=c.principal AND o.cleared=c.principal AND o.dates
              AND p.valid AND p.principal>=0 AND p.principal<=c.principal
              AND t.installments>0 AND t.installments<=600 AND t.contract_count=t.installments AND t.scheduled_principal=c.principal AND t.valid
              AND NOT EXISTS(SELECT 1 FROM accounting_business_document b WHERE b.loan_id=c.loan_application_id
                AND b.state='POSTED' AND b.posted_at<=:cutoff AND b.effective_date<=:through
                AND (b.sacco_id<>c.sacco_id OR b.station_id<>c.station_id OR
                  (b.kind NOT IN('LOAN_DISBURSEMENT','LOAN_REPAYMENT','LOAN_REPAYMENT_REVERSAL','INTEREST_ACCRUAL')
                    AND NOT(b.kind IN('LOAN_ADVANCE','UNMATCHED_RECEIPT','REFUND') AND b.principal=0 AND b.interest=0 AND b.fees=0)))),false) complete
          FROM cohort c LEFT JOIN origins o USING(loan_application_id) JOIN payments p USING(loan_application_id)
            JOIN terms t USING(loan_application_id)
        ), missing AS (
          SELECT a.station_id,count(*) excluded FROM loan_applications a
          WHERE a.sacco_id=:institution AND (:wide OR a.station_id=:branch) AND a.created_at<=:cutoff
            AND a.disbursement_date<=:through AND NOT EXISTS(SELECT 1 FROM cohort c WHERE c.loan_application_id=a.id)
          GROUP BY a.station_id
        )
        """;
    private static final String TOTALS="""
        count(*) FILTER(WHERE complete) covered,count(*) FILTER(WHERE NOT complete) excluded,
        coalesce(sum(principal) FILTER(WHERE complete),0) principal,
        coalesce(sum(overdue_principal) FILTER(WHERE complete),0) overdue_principal,
        coalesce(sum(overdue_interest) FILTER(WHERE complete),0) overdue_interest,
        coalesce(sum(principal) FILTER(WHERE complete AND dpd>=1),0) par1,
        coalesce(sum(principal) FILTER(WHERE complete AND dpd>30),0) par30,
        coalesce(sum(principal) FILTER(WHERE complete AND dpd>90),0) par90,
        coalesce(sum(original_principal) FILTER(WHERE complete AND disbursement_date>=:from),0) disbursed,
        coalesce(sum(collected_principal) FILTER(WHERE complete),0) collected_principal,
        coalesce(sum(collected_interest) FILTER(WHERE complete),0) collected_interest
        """;
    private static Summary summary(java.sql.ResultSet r,long missing)throws java.sql.SQLException {return new Summary(r.getLong("covered"),Math.addExact(r.getLong("excluded"),missing),r.getBigDecimal("principal"),r.getBigDecimal("overdue_principal"),r.getBigDecimal("overdue_interest"),r.getBigDecimal("par1"),r.getBigDecimal("par30"),r.getBigDecimal("par90"),r.getBigDecimal("disbursed"),r.getBigDecimal("collected_principal"),r.getBigDecimal("collected_interest"));}
    public Summary summary(Scope s,Parameters p){return jdbc.queryForObject(SOURCE+"SELECT "+TOTALS+",(SELECT coalesce(sum(excluded),0) FROM missing) missing FROM exposure",parameters(s,p),(r,n)->summary(r,r.getLong("missing")));}
    public List<SourceRow> rows(Scope s,Parameters p){if(s.institutionWide())throw new IllegalArgumentException("Branch rows required");return jdbc.query(SOURCE+"SELECT * FROM exposure ORDER BY loan_id,loan_application_id LIMIT 26 OFFSET :offset",parameters(s,p),(r,n)->new SourceRow(r.getObject("loan_application_id",UUID.class),r.getString("loan_id"),r.getBoolean("complete")?r.getBigDecimal("principal"):null,r.getBoolean("complete")?r.getBigDecimal("overdue_principal"):null,r.getBoolean("complete")?r.getBigDecimal("overdue_interest"):null,r.getObject("oldest",LocalDate.class),r.getBoolean("complete")));}
    public List<Bucket> buckets(Scope s,Parameters p,boolean regulatoryReference){String bands=regulatoryReference?"CASE WHEN dpd<=5 THEN 0 WHEN dpd<=30 THEN 1 WHEN dpd<=60 THEN 2 WHEN dpd<=90 THEN 3 ELSE 4 END":"CASE WHEN dpd=0 THEN 0 WHEN dpd<=30 THEN 1 WHEN dpd<=60 THEN 2 WHEN dpd<=90 THEN 3 ELSE 4 END";return jdbc.query(SOURCE+"SELECT "+bands+" band,count(*) loans,sum(principal) principal FROM exposure WHERE complete GROUP BY band ORDER BY band",parameters(s,p),(r,n)->new Bucket(r.getInt("band"),r.getLong("loans"),r.getBigDecimal("principal")));}
    public List<BranchPerformance> branches(Scope s,Parameters p){return jdbc.query(SOURCE+", grouped AS (SELECT station_id,"+TOTALS+" FROM exposure GROUP BY station_id), branch_ids AS (SELECT station_id FROM grouped UNION SELECT station_id FROM missing) SELECT b.station_id,coalesce(g.covered,0) covered,coalesce(g.excluded,0) excluded,coalesce(m.excluded,0) missing,coalesce(g.principal,0) principal,coalesce(g.overdue_principal,0) overdue_principal,coalesce(g.overdue_interest,0) overdue_interest,coalesce(g.par1,0) par1,coalesce(g.par30,0) par30,coalesce(g.par90,0) par90,coalesce(g.disbursed,0) disbursed,coalesce(g.collected_principal,0) collected_principal,coalesce(g.collected_interest,0) collected_interest FROM branch_ids b LEFT JOIN grouped g USING(station_id) LEFT JOIN missing m USING(station_id) ORDER BY b.station_id LIMIT 1001",parameters(s,p),(r,n)->new BranchPerformance(r.getString("station_id"),summary(r,r.getLong("missing"))));}
}
