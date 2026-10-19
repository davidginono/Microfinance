package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Scoped posted sources; envelopes never add a second loan collection. */
@Repository @RequiredArgsConstructor
public class ManagementReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public String[] identity(AppUserPrincipal a){return jdbc.queryForObject("select i.sacco_name,coalesce(nullif(s.address_location,''),s.station_id) from registered_saccos i join sacco_stations s on s.sacco_id=i.sacco_id where s.sacco_id=:institution and s.station_id=:branch",parameters(a,null),(r,n)->new String[]{r.getString(1),r.getString(2)});}
    private MapSqlParameterSource parameters(AppUserPrincipal a,Parameters p){var m=new MapSqlParameterSource("institution",a.getSaccoId()).addValue("branch",a.getStationId()).addValue("wide",false);if(p!=null)m.addValue("from",p.from()).addValue("through",p.through()).addValue("cutoff",p.cutoff()).addValue("client",p.client().toLowerCase(Locale.ROOT)).addValue("product",p.product().toLowerCase(Locale.ROOT));return m;}
    public record Data(List<Map<String,Object>> rows,List<Group> groups,Map<String,BigDecimal> totals,long count,long excluded) {}
    public Data read(AppUserPrincipal a,Kind kind,Parameters p,String startCode,String endCode,boolean export){
        var m=parameters(a,p).addValue("start",startCode).addValue("end",endCode).addValue("offset",export?0:p.page()*25).addValue("limit",export?5001:26);
        String source=switch(kind){case DAILY_COLLECTIONS->DAILY;case CASHBOOK->CASH;default->loans(kind);};
        var columns=com.sacco.mvp.accounting.management.dto.ManagementReports.columns(kind);
        var money=columns.stream().filter(Column::money).map(Column::key).toList();
        boolean loan=kind!=Kind.CASHBOOK && kind!=Kind.DAILY_COLLECTIONS;
        String sums=money.stream().map(k->(loan?"case when count(*)=0 then 0 else sum(\""+k+"\") end":kind==Kind.CASHBOOK && !Set.of("moneyIn","moneyOut").contains(k)?"sum(\""+k+"\")":"coalesce(sum(\""+k+"\"),0)")+" as \""+k+"\"").reduce((x,y)->x+","+y).orElseThrow();
        var total=jdbc.queryForMap(source+" select count(*) count,"+sums+" from report_rows",m);
        var rows=jdbc.query(source+" select * from report_rows order by \"groupName\",\"rowOrder\" limit :limit offset :offset",m,(r,n)->row(r));
        var groups=jdbc.query(source+" select \"groupName\",count(*) count,"+sums+" from report_rows group by \"groupName\" order by \"groupName\" limit 1001",m,(r,n)->new Group(r.getString("groupName"),r.getLong("count"),totals(row(r),money)));
        long excluded=kind==Kind.DAILY_COLLECTIONS?jdbc.queryForObject("""
            select count(*) from loan_repayment_transactions t
            join loan_applications a on a.id=t.loan_application_id and a.sacco_id=t.sacco_id and a.station_id=t.station_id
            join members m on m.id=a.applicant_member_id and m.sacco_id=a.sacco_id
            where t.sacco_id=:institution and t.station_id=:branch and t.posted_at<=:cutoff and t.payment_date between :from and :through
            and (:client='' or strpos(lower(m.member_no||' '||m.full_name),:client)>0)
            and not exists(select 1 from accountant_loan_post p where p.transaction_id=t.id and p.state='POSTED' and p.recorded_at<=:cutoff
              and (not exists(select 1 from accountant_loan_receipt_row x where x.posting_id=p.id)
                or exists(select 1 from accountant_loan_receipt_row x join accountant_loan_receipt r on r.id=x.receipt_id
                  where x.posting_id=p.id and r.posted_at<=:cutoff)))
            """,m,Long.class):kind==Kind.CASHBOOK?0:jdbc.queryForObject(source+" select (select count(*) from exposure where not complete)+(select coalesce(sum(excluded),0) from missing)",m,Long.class);
        return new Data(rows,groups,totals(total,money),((Number)total.get("count")).longValue(),excluded);
    }
    private static Map<String,Object> row(ResultSet r)throws SQLException{var out=new LinkedHashMap<String,Object>();for(int n=1;n<=r.getMetaData().getColumnCount();n++)out.put(r.getMetaData().getColumnLabel(n),r.getObject(n));return out;}
    private static Map<String,BigDecimal> totals(Map<String,Object> row,List<String> fields){var out=new LinkedHashMap<String,BigDecimal>();for(var k:fields)out.put(k,(BigDecimal)row.get(k));return out;}
    private static final String DAILY="""
        with receipts as (
          select v.id,v.sacco_id,v.station_id,v.effective_date,v.posted_at,v.number,v.reference,v.party,v.money_account_id,v.posted_by,
            t.line_no,t.description,t.transaction_name,tc.code transaction_code,coalesce(ac.code||' — '||ac.name,'@general') activity,t.component,
            null::text loan_id,null::text client_number,null::text phone,
            case when v.reverses_id is null then 0 else t.amount end debit,
            case when v.reverses_id is null then t.amount else 0 end credit
          from accounting_voucher v join accounting_voucher_transaction t on t.voucher_id=v.id and t.sacco_id=v.sacco_id and t.station_id=v.station_id
          join gl_journal j on j.id=v.journal_id and j.sacco_id=v.sacco_id and j.station_id=v.station_id and j.state='POSTED' and j.posted_at<=:cutoff
          left join gl_transaction_code tc on tc.id=t.transaction_id and tc.sacco_id=v.sacco_id
          left join gl_activity ac on ac.id=tc.activity_id and ac.sacco_id=tc.sacco_id
          where v.sacco_id=:institution and v.station_id=:branch and v.type='RECEIPT' and v.posted_at<=:cutoff and v.effective_date between :from and :through
          union all
          select r.id,r.sacco_id,r.station_id,r.effective_date,r.posted_at,r.number,r.reference,r.party,r.money_account_id,r.posted_by,
            x.line_no*2+part.n,x.description,null::text,null::text,'@loan',part.component,a.loan_id,r.client_number,m.phone,
            case when r.reverses_id is null then 0 else part.amount end,
            case when r.reverses_id is null then part.amount else 0 end
          from accountant_loan_receipt r join accountant_loan_receipt_row x on x.receipt_id=r.id and x.sacco_id=r.sacco_id and x.station_id=r.station_id
          join accountant_loan_post p on p.id=x.posting_id and p.sacco_id=r.sacco_id and p.station_id=r.station_id and p.state='POSTED' and p.recorded_at<=:cutoff
          join loan_applications a on a.id=p.loan_id and a.sacco_id=p.sacco_id and a.station_id=p.station_id
          cross join lateral (values (0,'PRINCIPAL',p.principal_amount),(1,'INTEREST',p.interest_amount)) part(n,component,amount)
          join gl_journal j on j.id=p.journal_id and j.sacco_id=p.sacco_id and j.station_id=p.station_id and j.state='POSTED' and j.posted_at<=:cutoff
          join members m on m.id=r.client_id and m.sacco_id=r.sacco_id
          where r.sacco_id=:institution and r.station_id=:branch and r.posted_at<=:cutoff and r.effective_date between :from and :through and part.amount>0
          union all
          select p.id,p.sacco_id,p.station_id,p.effective_date,p.recorded_at,p.reference,p.reference,m.full_name,p.money_account_id,p.actor_id,
            part.n,p.notes,null::text,null::text,'@loan',part.component,a.loan_id,m.member_no,m.phone,
            case when p.kind='REVERSAL' then part.amount else 0 end,case when p.kind='PAYMENT' then part.amount else 0 end
          from accountant_loan_post p join loan_applications a on a.id=p.loan_id and a.sacco_id=p.sacco_id and a.station_id=p.station_id
          join members m on m.id=a.applicant_member_id and m.sacco_id=p.sacco_id
          join gl_journal j on j.id=p.journal_id and j.sacco_id=p.sacco_id and j.station_id=p.station_id and j.state='POSTED' and j.posted_at<=:cutoff
          cross join lateral (values (0,'PRINCIPAL',p.principal_amount),(1,'INTEREST',p.interest_amount)) part(n,component,amount)
          where p.sacco_id=:institution and p.station_id=:branch and p.state='POSTED'
            and (p.kind='PAYMENT' or p.kind='REVERSAL' and exists(select 1 from accountant_loan_post original where original.id=p.reverses_id and original.kind='PAYMENT'))
            and p.recorded_at<=:cutoff and p.effective_date between :from and :through and part.amount>0
            and not exists(select 1 from accountant_loan_receipt_row x where x.posting_id=p.id)
        ), report_rows as (
          select r.effective_date::text||' | '||r.activity as "groupName",r.posted_at::text||r.id::text||lpad(r.line_no::text,3,'0') as "rowOrder",
            r.client_number as "clientNumber",r.loan_id as "loanId",r.component,r.party as name,r.reference as "documentReference",r.number as "documentNumber",r.effective_date as "receiptDate",
            case when r.transaction_name is null then r.description else r.transaction_name||' — '||r.description end as transaction,r.transaction_code as "transactionCode",r.debit,r.credit,r.phone,u.full_name as "recordedBy"
          from receipts r join gl_account b on b.id=r.money_account_id and b.sacco_id=r.sacco_id join members u on u.id=r.posted_by
          where (:start='' or b.code>=:start) and (:end='' or b.code<=:end)
            and (:client='' or strpos(lower(coalesce(r.client_number,'')||' '||r.party),:client)>0)
        )
        """;
    private static final String CASH="""
        with report_rows as (
          select b.code||' | '||b.name as "groupName",j.effective_date::text||j.posted_at::text||l.id::text as "rowOrder",
            j.effective_date as date,m.member_no as "clientNumber",coalesce(v.party,r.party,m.full_name,'') as name,
            coalesce(nullif(v.description,''),nullif(p.notes,''),nullif(j.reason,''),j.source_reference) as particulars,coalesce(v.reference,r.reference,p.reference,j.source_reference) as reference,
            l.debit as "moneyIn",l.credit as "moneyOut",
            case when q.kind='DISBURSEMENT_REVERSAL' then q.amount when p.id is not null then case when l.debit>0 then p.principal_amount else 0 end else null end as "principalIn",
            case when p.id is not null then case when l.credit>0 then p.principal_amount else 0 end else null end as "principalOut",
            case when p.id is not null then case when l.debit>0 then p.interest_amount else 0 end else null end as "interestIn",
            case when p.id is not null then case when l.credit>0 then p.interest_amount else 0 end else null end as "interestOut"
          from gl_journal j join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
          join gl_account b on b.id=l.account_id and b.sacco_id=j.sacco_id and b.purpose in ('CASH','BANK','MOBILE_MONEY')
          left join accounting_voucher v on v.journal_id=j.id and v.sacco_id=j.sacco_id and v.station_id=j.station_id
          left join accountant_loan_post p on p.journal_id=j.id and p.sacco_id=j.sacco_id and p.station_id=j.station_id and p.state='POSTED' and p.recorded_at<=:cutoff
          left join loan_adjustment_decision ad on ad.journal_id=j.id
          left join loan_adjustment_request q on q.id=ad.request_id and q.sacco_id=j.sacco_id and q.station_id=j.station_id
          left join loan_applications a on a.id=coalesce(p.loan_id,q.loan_id) and a.sacco_id=j.sacco_id and a.station_id=j.station_id
          left join members m on m.id=a.applicant_member_id and m.sacco_id=j.sacco_id
          left join accountant_loan_receipt_row x on x.posting_id=p.id
          left join accountant_loan_receipt r on r.id=x.receipt_id and r.sacco_id=j.sacco_id and r.station_id=j.station_id and r.posted_at<=:cutoff
          where j.sacco_id=:institution and j.station_id=:branch and j.state='POSTED' and j.posted_at<=:cutoff and j.recorded_at<=:cutoff
            and j.effective_date between :from and :through and (:start='' or b.code>=:start) and (:end='' or b.code<=:end)
            and (:client='' or strpos(lower(coalesce(m.member_no,'')||' '||coalesce(v.party,r.party,m.full_name,'')),:client)>0)
        )
        """;
    private static String loans(Kind kind){
        String band="case when e.dpd<=30 then 0 when e.dpd<=90 then 1 when e.dpd<=180 then 2 when e.dpd<=365 then 3 else 4 end";
        var values=new LinkedHashMap<String,String>();
        values.put("disbursed","e.original_principal");values.put("contractTotal","e.original_principal+e.scheduled_interest");values.put("instalment","e.periodic_payment");
        values.put("principalBalance","e.principal");values.put("interestBalance","e.scheduled_interest-e.paid_interest-e.excluded_interest-e.waived_interest");values.put("totalPrincipal","e.original_principal");
        values.put("principalPaid","e.paid_principal");values.put("principalArrears","e.overdue_principal");values.put("totalInterest","e.scheduled_interest");values.put("interestPaid","e.paid_interest");values.put("interestArrears","e.overdue_interest");
        for(int i=0;i<5;i++)values.put("band"+i,"case when "+band+"="+i+" then e.principal else 0 end");
        values.put("overdue","e.overdue_principal+e.overdue_interest");
        values.put("referenceProvision","round(e.principal*(case when e.dpd<=30 then 0 when e.dpd<=90 then .10 when e.dpd<=180 then .30 when e.dpd<=365 then .50 else 1 end),2)");
        String money=values.entrySet().stream().map(v->"case when e.complete then "+v.getValue()+" else null end as \""+v.getKey()+"\"").reduce((x,y)->x+","+y).orElseThrow();
        return LoanPortfolioReportRepository.SOURCE+", report_rows as (select e.complete,coalesce(r.product_name,a.loan_type) as \"groupName\",e.loan_id||e.loan_application_id::text as \"rowOrder\",m.member_no as \"clientNumber\",m.full_name as name,m.phone,e.loan_id as \"loanId\",null::text gender,null::text as \"defaultRate\",e.disbursement_date as \"disbursementDate\",case when e.complete then e.maturity end as maturity,case when e.complete then e.installments end as periods,a.financial_snapshot->>'repaymentFrequency' frequency,"+money+" from exposure e join loan_applications a on a.id=e.loan_application_id and a.sacco_id=:institution and a.station_id=:branch join members m on m.id=a.applicant_member_id and m.sacco_id=a.sacco_id left join accountant_loan_record r on r.loan_id=a.id and r.sacco_id=a.sacco_id and r.station_id=a.station_id where (:client='' or strpos(lower(m.member_no||' '||m.full_name),:client)>0) and (:product='' or strpos(lower(coalesce(r.product_name,a.loan_type)),:product)>0)"+(kind==Kind.LOAN_PORTFOLIO?" and e.disbursement_date>=:from":"")+") ";
    }
}
