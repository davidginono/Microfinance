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
    // The institution's Cash and Bank family defines the range, including older posting accounts with purpose OTHER.
    private static final String CASH_BANK_ACCOUNTS="""
        from gl_cash_bank_group g join gl_account a on a.parent_id=g.id and a.sacco_id=g.sacco_id
        where g.sacco_id=:institution and a.type='ASSET' and a.kind<>'HEADING'
        """;
    private static final String CASH_RANGE="with cash_accounts as (select a.id,a.code "+CASH_BANK_ACCOUNTS+"""
        ), cash_range as (
          select a.id from cash_accounts a
          left join cash_accounts first_account on first_account.id=:startAccount
          left join cash_accounts last_account on last_account.id=:endAccount
          where (cast(:startAccount as uuid) is null or a.code collate "C">=first_account.code)
            and (cast(:endAccount as uuid) is null or a.code collate "C"<=last_account.code)
        ),
        """;
    public List<Choice> accounts(AppUserPrincipal a,String search,int page,List<UUID> selected) {
        var p=parameters(a,null).addValue("search",search.toLowerCase(Locale.ROOT)).addValue("offset",page*25);
        String selection="";
        if(!selected.isEmpty()) {
            p.addValue("ids",selected);
            selection=" and a.id in (:ids) ";
        }
        return jdbc.query("select a.id,a.code,a.name "+CASH_BANK_ACCOUNTS+selection+"""
            and strpos(lower(a.code||' '||a.name),:search)>0
            order by a.code collate "C",a.id limit 26 offset :offset
            """,p,this::choice);
    }
    public List<Choice> clients(AppUserPrincipal a,String search,int page,UUID selected) {
        return jdbc.query("""
          select b.party_id,b.member_no,b.full_name from loan_borrower_directory b left join members m on m.id=b.client_id and m.sacco_id=b.sacco_id
          where b.sacco_id=:institution and ((b.kind='CLIENT' and m.station_id=:branch and m.is_member) or (b.kind<>'CLIENT' and b.group_station_id=:branch))
           and (cast(:id as uuid) is null or b.party_id=:id) and strpos(lower(b.member_no||' '||b.full_name),:search)>0 order by b.member_no,b.party_id limit 26 offset :offset
          """,parameters(a,null).addValue("search",search.toLowerCase(Locale.ROOT)).addValue("offset",page*25).addValue("id",selected,java.sql.Types.OTHER),
          this::choice);
    }
    private Choice choice(ResultSet row,int index)throws SQLException {
        String code=row.getString(2),name=row.getString(3);
        return new Choice(row.getObject(1,UUID.class),code,name,code+" — "+name);
    }
    public List<Choice> products(AppUserPrincipal a,String search,int page,UUID selected,String customisedLabel) {
        var p=parameters(a,null).addValue("search",search.toLowerCase(Locale.ROOT))
            .addValue("selected",selected,Types.OTHER).addValue("offset",selected==null?page*25:0).addValue("customisedLabel",customisedLabel);
        return jdbc.query("""
            select id,case when custom_recording_configuration then :customisedLabel else coalesce(product_name,loan_type) end label from loan_product_settings
            where sacco_id=:institution and (cast(:selected as uuid) is null or id=:selected)
              and (cast(:selected as uuid) is not null or strpos(lower(case when custom_recording_configuration then :customisedLabel else coalesce(product_name,loan_type) end),:search)>0)
            order by custom_recording_configuration desc,coalesce(display_order,2147483647),id limit 26 offset :offset
            """,p,(r,n)->new Choice(r.getObject("id",UUID.class),"",r.getString("label")));
    }
    public String[] identity(AppUserPrincipal a){return jdbc.queryForObject("select i.sacco_name,coalesce(nullif(s.address_location,''),s.station_id) from registered_saccos i join sacco_stations s on s.sacco_id=i.sacco_id where s.sacco_id=:institution and s.station_id=:branch",parameters(a,null),(r,n)->new String[]{r.getString(1),r.getString(2)});}
    private MapSqlParameterSource parameters(AppUserPrincipal a,Parameters p){var m=new MapSqlParameterSource("institution",a.getSaccoId()).addValue("branch",a.getStationId()).addValue("wide",false);if(p!=null)m.addValue("from",p.from()).addValue("through",p.through()).addValue("cutoff",p.cutoff()).addValue("clientId",p.clientId(),java.sql.Types.OTHER).addValue("productId",p.productId(),Types.OTHER).addValue("product",p.productId()==null?p.product().toLowerCase(Locale.ROOT):"");return m;}
    public record Data(List<Map<String,Object>> rows,List<Group> groups,Map<String,BigDecimal> totals,long count,long excluded) {}
    public Data read(AppUserPrincipal a,Kind kind,Parameters p,boolean export){
        var m=parameters(a,p).addValue("startAccount",p.startAccount(),Types.OTHER)
            .addValue("endAccount",p.endAccount(),Types.OTHER).addValue("bounded",p.startAccount()!=null || p.endAccount()!=null)
            .addValue("offset",export?0:p.page()*25).addValue("limit",export?5001:26);
        String source=switch(kind){case DAILY_COLLECTIONS->DAILY;case CASHBOOK->CASH;case DISBURSED_LOANS_AUDIT->disbursements();case EXPECTED_REPAYMENTS->expected();case LOAN_REPAYMENT_TRANSACTIONS->repayments();default->loans(kind);};
        var money=com.sacco.mvp.accounting.management.dto.ManagementReports.totalFields(kind);
        boolean loan=kind!=Kind.CASHBOOK && kind!=Kind.DAILY_COLLECTIONS;
        String sums=money.stream().map(k->(loan?"case when count(*)=0 then 0 else sum(\""+k+"\") end":kind==Kind.CASHBOOK && !Set.of("moneyIn","moneyOut").contains(k)?"sum(\""+k+"\")":"coalesce(sum(\""+k+"\"),0)")+" as \""+k+"\"").reduce((x,y)->x+","+y).orElseThrow();
        var total=jdbc.queryForMap(source+" select count(*) count,"+sums+" from report_rows",m);
        var rows=jdbc.query(source+" select * from report_rows order by \"groupName\",\"rowOrder\" limit :limit offset :offset",m,(r,n)->row(r));
        var groups=jdbc.query(source+" select \"groupName\",count(*) count,"+sums+" from report_rows group by \"groupName\" order by \"groupName\" limit 1001",m,(r,n)->new Group(r.getString("groupName"),r.getLong("count"),totals(row(r),money)));
        long excluded=kind==Kind.DAILY_COLLECTIONS?jdbc.queryForObject("""
            select count(*) from loan_repayment_transactions t
            join loan_applications a on a.id=t.loan_application_id and a.sacco_id=t.sacco_id and a.station_id=t.station_id
            join loan_borrower_contact m on m.loan_id=a.id and m.sacco_id=a.sacco_id and m.station_id=a.station_id
            where t.sacco_id=:institution and t.station_id=:branch and t.posted_at<=:cutoff and t.payment_date between :from and :through
            and (cast(:clientId as uuid) is null or m.id=:clientId)
            and not exists(select 1 from accountant_loan_post p where p.transaction_id=t.id and p.state='POSTED' and p.recorded_at<=:cutoff
              and (not exists(select 1 from accountant_loan_receipt_row x where x.posting_id=p.id)
                or exists(select 1 from accountant_loan_receipt_row x join accountant_loan_receipt r on r.id=x.receipt_id
                  where x.posting_id=p.id and r.posted_at<=:cutoff)))
            """,m,Long.class):kind==Kind.CASHBOOK?0:jdbc.queryForObject(source+" select (select count(*) from exposure where not complete)+(select coalesce(sum(excluded),0) from missing)"+(Set.of(Kind.DISBURSED_LOANS_AUDIT,Kind.EXPECTED_REPAYMENTS,Kind.LOAN_REPAYMENT_TRANSACTIONS).contains(kind)?"+(select count(*) from exposure e join cohort c using(loan_application_id) left join lifecycle x using(loan_application_id) where e.complete and ((c.application_client is null and not exists(select 1 from loan_borrower_contact b where b.loan_id=c.loan_application_id and b.sacco_id=c.sacco_id and b.station_id=c.station_id))"+(kind==Kind.EXPECTED_REPAYMENTS?" or x.written_off<>0 or x.cancelled<>0":"")+"))":""),m,Long.class);
        if(loan && excluded>0 && ((Number)total.get("count")).longValue()==0)money.forEach(key->total.put(key,null));
        return new Data(rows,groups,totals(total,money),((Number)total.get("count")).longValue(),excluded);
    }
    private static Map<String,Object> row(ResultSet r)throws SQLException{var out=new LinkedHashMap<String,Object>();for(int n=1;n<=r.getMetaData().getColumnCount();n++)out.put(r.getMetaData().getColumnLabel(n),r.getObject(n));return out;}
    private static Map<String,BigDecimal> totals(Map<String,Object> row,List<String> fields){var out=new LinkedHashMap<String,BigDecimal>();for(var k:fields)out.put(k,(BigDecimal)row.get(k));return out;}
    private static final String DAILY=CASH_RANGE+"""
        receipts as (
          select v.id,v.sacco_id,v.station_id,v.effective_date,v.posted_at,v.number,v.reference,v.party,e.account_id money_account_id,v.posted_by,v.client_id,
            t.line_no,t.description,t.transaction_name,tc.code transaction_code,coalesce(ac.code||' — '||ac.name,'@general') activity,t.component,
            null::text loan_id,v.client_number,null::text phone,
            case when v.reverses_id is null then 0 else e.amount end debit,
            case when v.reverses_id is null then e.amount else 0 end credit
          from accounting_voucher v join accounting_voucher_transaction t on t.voucher_id=v.id and t.sacco_id=v.sacco_id and t.station_id=v.station_id
          join accounting_voucher_posting e on e.transaction_id=t.id and e.side=case when v.reverses_id is null then 'DEBIT' else 'CREDIT' end
          join gl_journal j on j.id=v.journal_id and j.sacco_id=v.sacco_id and j.station_id=v.station_id and j.state='POSTED' and j.posted_at<=:cutoff
          left join gl_transaction_code tc on tc.id=t.transaction_id and tc.sacco_id=v.sacco_id
          left join gl_activity ac on ac.id=tc.activity_id and ac.sacco_id=tc.sacco_id
          where v.sacco_id=:institution and v.station_id=:branch and v.type='RECEIPT' and v.posted_at<=:cutoff and v.effective_date between :from and :through
          union all
          select r.id,r.sacco_id,r.station_id,r.effective_date,r.posted_at,r.number,r.reference,r.party,r.money_account_id,r.posted_by,r.borrower_party_id,
            x.line_no*2+part.n,x.description,null::text,null::text,'@loan',part.component,a.loan_id,r.client_number,m.phone,
            case when r.reverses_id is null then 0 else part.amount end,
            case when r.reverses_id is null then part.amount else 0 end
          from accountant_loan_receipt r join accountant_loan_receipt_row x on x.receipt_id=r.id and x.sacco_id=r.sacco_id and x.station_id=r.station_id
          join accountant_loan_post p on p.id=x.posting_id and p.sacco_id=r.sacco_id and p.station_id=r.station_id and p.state='POSTED' and p.recorded_at<=:cutoff
          join loan_applications a on a.id=p.loan_id and a.sacco_id=p.sacco_id and a.station_id=p.station_id
          cross join lateral (values (0,'PRINCIPAL',p.principal_amount),(1,'INTEREST',p.interest_amount)) part(n,component,amount)
          join gl_journal j on j.id=p.journal_id and j.sacco_id=p.sacco_id and j.station_id=p.station_id and j.state='POSTED' and j.posted_at<=:cutoff
          join loan_borrower_contact m on m.loan_id=a.id and m.id=r.borrower_party_id and m.sacco_id=r.sacco_id and m.station_id=r.station_id
          where r.sacco_id=:institution and r.station_id=:branch and r.posted_at<=:cutoff and r.effective_date between :from and :through and part.amount>0
          union all
          select p.id,p.sacco_id,p.station_id,p.effective_date,p.recorded_at,p.reference,p.reference,m.full_name,p.money_account_id,p.actor_id,m.id,
            part.n,p.notes,null::text,null::text,'@loan',part.component,a.loan_id,m.member_no,m.phone,
            case when p.kind='REVERSAL' then part.amount else 0 end,case when p.kind='PAYMENT' then part.amount else 0 end
          from accountant_loan_post p join loan_applications a on a.id=p.loan_id and a.sacco_id=p.sacco_id and a.station_id=p.station_id
          join loan_borrower_contact m on m.loan_id=a.id and m.sacco_id=p.sacco_id and m.station_id=p.station_id
          join gl_journal j on j.id=p.journal_id and j.sacco_id=p.sacco_id and j.station_id=p.station_id and j.state='POSTED' and j.posted_at<=:cutoff
          cross join lateral (values (0,'PRINCIPAL',p.principal_amount),(1,'INTEREST',p.interest_amount)) part(n,component,amount)
          where p.sacco_id=:institution and p.station_id=:branch and p.state='POSTED'
            and (p.kind='PAYMENT' or p.kind='REVERSAL' and exists(select 1 from accountant_loan_post original where original.id=p.reverses_id and original.kind='PAYMENT'))
            and p.recorded_at<=:cutoff and p.effective_date between :from and :through and part.amount>0
            and not exists(select 1 from accountant_loan_receipt_row x where x.posting_id=p.id)
        ), report_rows as (
          select r.effective_date::text||' | '||r.activity as "groupName",r.posted_at::text||r.id::text||lpad(r.line_no::text,3,'0')||r.money_account_id::text as "rowOrder",
            r.client_number as "clientNumber",r.loan_id as "loanId",r.component,r.party as name,r.reference as "documentReference",r.number as "documentNumber",r.effective_date as "receiptDate",
            case when r.transaction_name is null then r.description else r.transaction_name||' — '||r.description end as transaction,r.transaction_code as "transactionCode",r.debit,r.credit,r.phone,u.full_name as "recordedBy"
          from receipts r join gl_account b on b.id=r.money_account_id and b.sacco_id=r.sacco_id join members u on u.id=r.posted_by
          where (not :bounded or b.id in(select id from cash_range))
            and (cast(:clientId as uuid) is null or r.client_id=:clientId)
        )
        """;
    private static final String CASH=CASH_RANGE+"""
        report_rows as (
          select b.code||' | '||b.name as "groupName",j.effective_date::text||j.posted_at::text||l.id::text as "rowOrder",
            j.effective_date as date,coalesce(v.client_number,r.client_number,m.member_no) as "clientNumber",coalesce(v.party,r.party,m.full_name,'') as name,
            coalesce(nullif(v.description,''),nullif(p.notes,''),nullif(j.reason,''),j.source_reference) as particulars,coalesce(v.reference,r.reference,p.reference,j.source_reference) as reference,
            l.debit as "moneyIn",l.credit as "moneyOut",
            case when q.kind='DISBURSEMENT_REVERSAL' then q.amount when p.id is not null then case when l.debit>0 then p.principal_amount else 0 end else null end as "principalIn",
            case when p.id is not null then case when l.credit>0 then p.principal_amount else 0 end else null end as "principalOut",
            case when p.id is not null then case when l.debit>0 then p.interest_amount else 0 end else null end as "interestIn",
            case when p.id is not null then case when l.credit>0 then p.interest_amount else 0 end else null end as "interestOut"
          from gl_journal j join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
          join gl_account b on b.id=l.account_id and b.sacco_id=j.sacco_id
          left join accounting_voucher v on v.journal_id=j.id and v.sacco_id=j.sacco_id and v.station_id=j.station_id
          left join accountant_loan_post p on p.journal_id=j.id and p.sacco_id=j.sacco_id and p.station_id=j.station_id and p.state='POSTED' and p.recorded_at<=:cutoff
          left join loan_adjustment_decision ad on ad.journal_id=j.id
          left join loan_adjustment_request q on q.id=ad.request_id and q.sacco_id=j.sacco_id and q.station_id=j.station_id
          left join loan_applications a on a.id=coalesce(p.loan_id,q.loan_id) and a.sacco_id=j.sacco_id and a.station_id=j.station_id
          left join loan_borrower_contact m on m.loan_id=a.id and m.sacco_id=j.sacco_id and m.station_id=j.station_id
          left join accountant_loan_receipt_row x on x.posting_id=p.id
          left join accountant_loan_receipt r on r.id=x.receipt_id and r.sacco_id=j.sacco_id and r.station_id=j.station_id and r.posted_at<=:cutoff
          where j.sacco_id=:institution and j.station_id=:branch and j.state='POSTED' and j.posted_at<=:cutoff and j.recorded_at<=:cutoff
            and j.effective_date between :from and :through
            and (b.id in(select id from cash_range) or (not :bounded and b.purpose in ('CASH','BANK','MOBILE_MONEY')))
            and (cast(:clientId as uuid) is null or coalesce(v.client_id,r.borrower_party_id,m.id)=:clientId)
        )
        """;
    private static final String CLIENT_JOINS="""
        join loan_applications a on a.id=e.loan_application_id and a.sacco_id=:institution and a.station_id=:branch
        join loan_borrower_contact m on m.loan_id=a.id and m.sacco_id=a.sacco_id and m.station_id=a.station_id
        left join accountant_loan_record r on r.loan_id=a.id and r.sacco_id=a.sacco_id and r.station_id=a.station_id
        """;
    private static final String CLIENT_FILTER="""
        (cast(:clientId as uuid) is null or m.id=:clientId)
        and (:product='' or strpos(lower(coalesce(r.product_name,a.loan_type)),:product)>0)
        and (cast(:productId as uuid) is null or a.loan_product_setting_id=:productId)
        """;
    private static String disbursements(){
        return LoanPortfolioReportRepository.SOURCE+"""
            , report_rows as (
              select to_char(e.disbursement_date,'YYYY-MM')||' | '||coalesce(r.product_name,a.loan_type)||' | '||coalesce(p.money_account_name,'@unknown') as "groupName",
                to_char(e.disbursement_date,'YYYY-MM') as month,coalesce(r.product_name,a.loan_type) as product,
                e.disbursement_date::text||e.loan_id||a.id::text as "rowOrder",m.member_no as "clientNumber",m.full_name as name,e.loan_id as "loanId",
                case when e.complete then e.original_principal end as disbursed,
                case when e.complete then e.scheduled_interest end as "totalInterest",
                case when e.complete then e.periodic_payment end as instalment,
                a.repayment_frequency as frequency,e.disbursement_date as "disbursementDate",
                case when e.complete then e.maturity end as maturity,case when e.complete then e.installments end as periods,
                p.reference,p.money_account_name as "disbursementAccount"
              from exposure e
            """+CLIENT_JOINS+"""
              left join accountant_loan_post p on p.loan_id=a.id and p.sacco_id=a.sacco_id and p.station_id=a.station_id
                and p.kind='DISBURSEMENT' and p.state='POSTED' and p.recorded_at<=:cutoff
                and exists(select 1 from gl_journal j where j.id=p.journal_id and j.state='POSTED' and j.posted_at<=:cutoff)
              where e.disbursement_date>=:from and
            """+CLIENT_FILTER+") ";
    }
    private static String expected(){
        String known=LoanPortfolioReportRepository.SOURCE.replace(":through","least(:through,cast(:cutoff at time zone 'Africa/Dar_es_Salaam' as date))");
        return known+"""
            , report_rows as (
              select coalesce(r.product_name,a.loan_type) as "groupName",i.due_date::text||e.loan_id||i.id::text as "rowOrder",
                m.member_no as "clientNumber",m.full_name as name,m.phone,e.loan_id as "loanId",i.due_date as "dueDate",
                e.principal as "principalBalance",i.principal-coalesce(pa.principal,0) as "principalDue",
                i.interest-coalesce(pa.interest,0)-coalesce(pa.excluded,0)-coalesce(w.waived,0) as "interestDue",
                i.principal-coalesce(pa.principal,0)+i.interest-coalesce(pa.interest,0)-coalesce(pa.excluded,0)-coalesce(w.waived,0) as "expectedTotal"
              from exposure e
            """+CLIENT_JOINS+"""
              join loan_ledger_installments i on i.loan_application_id=e.loan_application_id
              left join installment_allocations pa on pa.installment_id=i.id
              left join waiver_allocations w on w.installment_id=i.id
              where e.complete and not exists(select 1 from lifecycle x where x.loan_application_id=e.loan_application_id and (x.written_off<>0 or x.cancelled<>0))
                and i.due_date between :from and :through
                and (i.principal>coalesce(pa.principal,0) or i.interest>coalesce(pa.interest,0)+coalesce(pa.excluded,0)+coalesce(w.waived,0)) and
            """+CLIENT_FILTER+") ";
    }
    private static String repayments(){
        return LoanPortfolioReportRepository.SOURCE+"""
            , principal_events as (
              select t.loan_application_id,t.id,t.id transaction_id,t.payment_date date,t.posted_at,t.sequence,-t.sign*t.principal_amount change
              from transactions t join exposure e on e.loan_application_id=t.loan_application_id where e.complete
              union all
              select q.loan_id,q.id,null::uuid,q.effective_date,j.posted_at,0,q.principal_change
              from loan_adjustment_request q join loan_adjustment_decision d on d.request_id=q.id
              join gl_journal j on j.id=d.journal_id and j.state='POSTED'
              join exposure e on e.loan_application_id=q.loan_id and e.complete
              where q.sacco_id=:institution and q.station_id=:branch and q.effective_date<=:through and j.posted_at<=:cutoff
            ), running_principal as (
              select v.*,e.original_principal+sum(v.change) over(partition by v.loan_application_id order by v.date,v.posted_at,v.sequence,v.id rows unbounded preceding) balance
              from principal_events v join exposure e on e.loan_application_id=v.loan_application_id
            ), report_rows as (
              select m.member_no||' | '||m.full_name||' | '||e.loan_id||' | '||coalesce(r.product_name,a.loan_type) as "groupName",
                t.payment_date::text||t.posted_at::text||lpad(t.sequence::text,20,'0')||t.id::text as "rowOrder",
                t.payment_date date,t.receipt_reference reference,t.kind as "entryKind",original.receipt_reference as "reversesReference",
                v.balance-v.change as "openingPrincipal",t.sign*t.principal_amount as "principalPaid",t.sign*t.interest_amount as "interestPaid",
                t.sign*t.amount as payment,t.sign*t.future_interest_excluded as "interestExcluded",v.balance as "closingPrincipal",e.original_principal disbursed,e.principal as "principalBalance",
                m.member_no as "clientNumber",m.full_name name,e.loan_id as "loanId",coalesce(r.product_name,a.loan_type) product,initial.due_date as "startDate"
              from exposure e
            """+CLIENT_JOINS+"""
              join transactions t on t.loan_application_id=e.loan_application_id
              join running_principal v on v.transaction_id=t.id
              left join loan_ledger_installments initial on initial.loan_application_id=e.loan_application_id and initial.installment_number=1
              left join loan_repayment_transactions original on original.id=t.reverses_transaction_id
                and original.sacco_id=:institution and original.station_id=:branch
              where e.complete and t.payment_date>=:from and
            """+CLIENT_FILTER+") ";
    }
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
        return LoanPortfolioReportRepository.SOURCE+", report_rows as (select e.complete,coalesce(r.product_name,a.loan_type) as \"groupName\",e.loan_id||e.loan_application_id::text as \"rowOrder\",m.member_no as \"clientNumber\",m.full_name as name,m.phone,e.loan_id as \"loanId\",null::text gender,null::text as \"defaultRate\",e.disbursement_date as \"disbursementDate\",case when e.complete then e.maturity end as maturity,case when e.complete then e.installments end as periods,a.financial_snapshot->>'repaymentFrequency' frequency,"+money+" from exposure e join loan_applications a on a.id=e.loan_application_id and a.sacco_id=:institution and a.station_id=:branch join loan_borrower_contact m on m.loan_id=a.id and m.sacco_id=a.sacco_id and m.station_id=a.station_id left join accountant_loan_record r on r.loan_id=a.id and r.sacco_id=a.sacco_id and r.station_id=a.station_id where (cast(:clientId as uuid) is null or m.id=:clientId) and (:product='' or strpos(lower(coalesce(r.product_name,a.loan_type)),:product)>0) and (cast(:productId as uuid) is null or a.loan_product_setting_id=:productId)"+(kind==Kind.LOAN_PORTFOLIO?" and e.disbursement_date>=:from":"")+") ";
    }
}
