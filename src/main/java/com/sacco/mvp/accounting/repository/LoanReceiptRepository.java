package com.sacco.mvp.accounting.repository;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.dto.RegisterSort;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

import static com.sacco.mvp.accounting.batch.repository.ReceiptBatchReferenceSql.*;

@Repository @RequiredArgsConstructor
public class LoanReceiptRepository {
    private final JdbcTemplate jdbc;
    private static final String HEAD="""
        select r.*,%s display_reference,null::uuid journal_id,'RECEIPT' type,i.sacco_name institution_name,
        coalesce(nullif(s.address_location,''),r.station_id) branch_name,
        correction.id reversed_by,coalesce(original.number,correction.number) linked_number
        from accountant_loan_receipt r join registered_saccos i on i.sacco_id=r.sacco_id
        join sacco_stations s on s.sacco_id=r.sacco_id and s.station_id=r.station_id
        left join accountant_loan_receipt original on original.id=r.reverses_id and original.sacco_id=r.sacco_id and original.station_id=r.station_id
        left join accountant_loan_receipt correction on correction.reverses_id=r.id and correction.sacco_id=r.sacco_id and correction.station_id=r.station_id
        """.formatted(RECEIPT_REFERENCE)+RECEIPT_JOINS;
    private static final String LEGACY="""
        select p.id,p.sacco_id,p.station_id,coalesce(v.number,t.receipt_reference) number,'RECEIPT' type,p.effective_date,
        m.full_name party,m.member_no client_number,
        case when %s is null then p.reference else coalesce(v.number,t.receipt_reference) end reference,
        p.notes description,j.source_type,j.source_reference,p.evidence,p.money_account_id,
        p.amount total,1 transaction_count,null::uuid journal_id,p.actor_id posted_by,u.full_name posted_by_name,
        p.recorded_at posted_at,p.reverses_id,correction.id reversed_by,
        coalesce(linked_v.number,linked_t.receipt_reference) linked_number,
        i.sacco_name institution_name,coalesce(nullif(s.address_location,''),p.station_id) branch_name,
        p.id posting_id,p.loan_id,a.borrower_party_id,a.loan_id loan_number,p.amount,p.principal_amount,p.interest_amount,p.principal_balance,
        p.money_account_name,p.principal_account_name,p.interest_account_name,coalesce((select t1.receivable_account_name from loan_charge_event e join loan_charge_term t1 on t1.id=e.term_id where e.posting_id=p.id limit 1),'') charge_account,1 line_no
        from accountant_loan_post p left join loan_repayment_transactions t on t.id=p.transaction_id and t.sacco_id=p.sacco_id and t.station_id=p.station_id
        join gl_journal j on j.id=p.journal_id and j.sacco_id=p.sacco_id and j.station_id=p.station_id
        left join accounting_system_voucher v on v.journal_id=p.journal_id and v.sacco_id=p.sacco_id and v.station_id=p.station_id
        join loan_applications a on a.id=p.loan_id join loan_borrower_directory m on m.party_id=a.borrower_party_id and m.sacco_id=a.sacco_id join members u on u.id=p.actor_id
        join registered_saccos i on i.sacco_id=p.sacco_id join sacco_stations s on s.sacco_id=p.sacco_id and s.station_id=p.station_id
        left join accountant_loan_post correction on correction.reverses_id=p.id and correction.sacco_id=p.sacco_id and correction.station_id=p.station_id
        left join accountant_loan_post linked on linked.id=coalesce(p.reverses_id,correction.id) and linked.sacco_id=p.sacco_id and linked.station_id=p.station_id
        left join accounting_posted_voucher_document linked_v on linked_v.journal_id=linked.journal_id and linked_v.sacco_id=linked.sacco_id and linked_v.station_id=linked.station_id
        left join loan_repayment_transactions linked_t on linked_t.id=linked.transaction_id and linked_t.sacco_id=linked.sacco_id and linked_t.station_id=linked.station_id
        where p.sacco_id=? and p.station_id=? and p.id=? and p.kind in ('PAYMENT','REVERSAL') and (v.type='RECEIPT' or t.receipt_reference is not null)
        and not exists(select 1 from accountant_loan_receipt_row x where x.posting_id=p.id)
        and not exists(select 1 from lending_group_receipt_loan x where x.posting_id=p.id)
        """.formatted(POST_ITEM_ID);
    public Optional<LoanReceipt> legacy(AppUserPrincipal a,UUID id){return jdbc.query(LEGACY,(r,n)->{
        String description=com.sacco.mvp.accounting.reports.PostingDescription.resolve(r.getString("source_type"),r.getString("source_reference"),r.getString("number"),r.getString("description"));
        return new LoanReceipt(header(r,"reference",description),r.getString("client_number"),List.of(row(r,"reference",description)),null,"",r.getString("linked_number"));
    },a.getSaccoId(),a.getStationId(),id).stream().findFirst();}
    public Optional<LoanReceipt> find(AppUserPrincipal a,UUID id,boolean lock){
        return jdbc.query(HEAD+" where r.sacco_id=? and r.station_id=? and r.id=?"+(lock?" for update of r":""),
            (r,n)->new LoanReceipt(header(r,lock?"reference":"display_reference"),r.getString("client_number"),List.of(),null,r.getString("payer_name"),r.getString("linked_number")),a.getSaccoId(),a.getStationId(),id).stream().findFirst();
    }
    public void issueStandaloneReceiptBeforeReload(){
        // An enclosing multi-loan receipt normally links its rows before this trigger runs at commit.
        jdbc.execute("set constraints system_voucher_on_post immediate");
        jdbc.execute("set constraints system_voucher_on_post deferred");
    }
    public Optional<UUID> retry(AppUserPrincipal a,UUID key,String hash){
        var rows=jdbc.queryForList("select id,payload_hash,posted_by from accountant_loan_receipt where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var r=rows.getFirst();
        if(!hash.equals(r.get("payload_hash")) || !a.getMemberId().equals(r.get("posted_by")))throw new Invalid("","retry");
        return Optional.of((UUID)r.get("id"));
    }
    public List<LoanReceiptRow> rows(AppUserPrincipal a,UUID id){return jdbc.query("""
        select x.line_no,x.description,p.*,p.id posting_id,l.loan_id loan_number,
        case when %s is null then p.reference else (%s)||' / '||x.line_no end display_reference,
        coalesce((select t.receivable_account_name from loan_charge_event e join loan_charge_term t on t.id=e.term_id where e.posting_id=p.id limit 1),'') charge_account from accountant_loan_receipt_row x
        join accountant_loan_post p on p.id=x.posting_id join loan_applications l on l.id=p.loan_id
        join accountant_loan_receipt r on r.id=x.receipt_id and r.sacco_id=x.sacco_id and r.station_id=x.station_id
        %s
        where x.receipt_id=? and x.sacco_id=? and x.station_id=? order by x.line_no limit 50
        """.formatted(RECEIPT_ITEM_ID,RECEIPT_REFERENCE,RECEIPT_JOINS),(r,n)->row(r,"display_reference"),id,a.getSaccoId(),a.getStationId());}
    public void insert(AppUserPrincipal a,Voucher v,UUID requestKey,UUID client,String clientNumber,String hash,List<LoanReceiptRow> rows,String payer){
        jdbc.update("""
            insert into accountant_loan_receipt(id,sacco_id,station_id,number,request_key,payload_hash,effective_date,borrower_party_id,client_id,party,client_number,
            reference,description,evidence,money_account_id,total,transaction_count,posted_by,posted_by_name,posted_at,reverses_id,payer_name)
            values(?,?,?,?,?,?,?,?,(select client_id from loan_borrower_party where id=? and sacco_id=?),?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,v.id(),a.getSaccoId(),a.getStationId(),v.number(),requestKey,hash,v.effectiveDate(),client,client,a.getSaccoId(),v.party(),clientNumber,v.reference(),v.description(),v.evidence(),v.moneyAccountId(),v.total(),rows.size(),a.getMemberId(),v.postedByName(),v.postedAt(),v.reversesId(),payer==null?"":payer.strip());
        jdbc.batchUpdate("insert into accountant_loan_receipt_row(receipt_id,sacco_id,station_id,line_no,posting_id,description) values(?,?,?,?,?,?)",
            rows.stream().map(r->new Object[]{v.id(),a.getSaccoId(),a.getStationId(),r.lineNo(),r.postingId(),r.description()}).toList());
    }
    public List<Voucher> list(AppUserPrincipal a,Filter f,boolean includeLoans){return list(a,f,includeLoans,false);}
    public List<Voucher> list(AppUserPrincipal a,Filter f,boolean includeLoans,boolean includeGroups){
        String order=RegisterSort.order(f.sort(),Map.of("number","v.number","date","v.effective_date","party","v.party","reference","nullif(v.reference,v.number)","transactions","v.transaction_count","amount","v.total","status","case when v.reverses_id is not null then 'REVERSAL' when v.reversed_by is not null then 'REVERSED' else 'POSTED' end"),"v.id");
        return jdbc.query("""
            with receipts as not materialized (
              select id,sacco_id,station_id,number,request_key,effective_date,party,reference,description,evidence,money_account_id,
              total,transaction_count,journal_id,posted_by,posted_at,reverses_id,client_id as borrower_party_id,client_number from accounting_voucher where type='RECEIPT' and sacco_id=? and station_id=?
              union all
              select r.id,r.sacco_id,r.station_id,r.number,r.request_key,r.effective_date,r.party,%s,r.description,r.evidence,r.money_account_id,
              r.total,r.transaction_count,null::uuid,r.posted_by,r.posted_at,r.reverses_id,r.borrower_party_id,r.client_number
              from accountant_loan_receipt r %s
              where r.sacco_id=? and r.station_id=? and ? and (? or not exists(select 1 from loan_borrower_party party where party.id=r.borrower_party_id and party.kind='JOINT_GROUP'))
              union all
              select p.id,p.sacco_id,p.station_id,coalesce(v.number,t.receipt_reference),p.request_key,p.effective_date,m.full_name,case when %s is null then p.reference else coalesce(v.number,t.receipt_reference) end,p.notes,p.evidence,p.money_account_id,
              p.amount,1,null::uuid,p.actor_id,p.recorded_at,p.reverses_id,l.borrower_party_id,m.member_no from accountant_loan_post p
              left join accounting_system_voucher v on v.journal_id=p.journal_id and v.sacco_id=p.sacco_id and v.station_id=p.station_id
              left join loan_repayment_transactions t on t.id=p.transaction_id and t.sacco_id=p.sacco_id and t.station_id=p.station_id join loan_applications l on l.id=p.loan_id join loan_borrower_directory m on m.party_id=l.borrower_party_id and m.sacco_id=l.sacco_id
              where p.sacco_id=? and p.station_id=? and ? and (? or m.kind<>'JOINT_GROUP') and p.kind in ('PAYMENT','REVERSAL') and (v.type='RECEIPT' or t.receipt_reference is not null)
              and not exists(select 1 from accountant_loan_receipt_row x where x.posting_id=p.id)
              and not exists(select 1 from lending_group_receipt_loan x where x.posting_id=p.id)
              union all
              select id,sacco_id,station_id,number,request_key,effective_date,remitter,reference,description,evidence,money_account_id,
              total,transaction_count,null::uuid,posted_by,posted_at,reverses_id,null::uuid,null::text from lending_group_receipt where sacco_id=? and station_id=? and ?
            ), all_receipts as (
              select v.*,m.full_name posted_by_name,i.sacco_name institution_name,
              coalesce(nullif(s.address_location,''),v.station_id) branch_name,
              (select r.id from receipts r where r.reverses_id=v.id) reversed_by
              from receipts v join members m on m.id=v.posted_by join registered_saccos i on i.sacco_id=v.sacco_id
              join sacco_stations s on s.sacco_id=v.sacco_id and s.station_id=v.station_id
            ) select v.* from all_receipts v
            where (?='' or strpos(lower(v.number||' '||v.party||' '||v.reference),?)>0)
            and (?::date is null or v.effective_date>=?::date) and (?::date is null or v.effective_date<=?::date)
            and (?='' or (case when v.reverses_id is not null then 'REVERSAL' when v.reversed_by is not null then 'REVERSED' else 'POSTED' end)=?)
            """.formatted(RECEIPT_REFERENCE,RECEIPT_JOINS,POST_ITEM_ID)+" order by "+order+" limit ? offset ?",(r,n)->header(r),a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId(),includeLoans,includeGroups,a.getSaccoId(),a.getStationId(),includeLoans,includeGroups,a.getSaccoId(),a.getStationId(),includeGroups,f.search().toLowerCase(Locale.ROOT),f.search().toLowerCase(Locale.ROOT),f.from(),f.from(),f.through(),f.through(),f.status(),f.status(),f.size()+1,f.page()*f.size());
    }
    private LoanReceiptRow row(ResultSet r,String referenceColumn)throws SQLException{return row(r,referenceColumn,r.getString("description"));}
    private LoanReceiptRow row(ResultSet r,String referenceColumn,String description)throws SQLException{return new LoanReceiptRow(r.getInt("line_no"),r.getObject("posting_id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("loan_number"),description,r.getBigDecimal("amount"),r.getBigDecimal("principal_amount"),r.getBigDecimal("interest_amount"),r.getBigDecimal("principal_balance"),r.getString("money_account_name"),r.getString("principal_account_name"),r.getString("interest_account_name"),r.getString(referenceColumn),r.getBigDecimal("amount").subtract(r.getBigDecimal("principal_amount")).subtract(r.getBigDecimal("interest_amount")).signum()>0?r.getString("charge_account"):"");}
    private Voucher header(ResultSet r)throws SQLException{return header(r,"reference");}
    private Voucher header(ResultSet r,String referenceColumn)throws SQLException{return header(r,referenceColumn,r.getString("description"));}
    private Voucher header(ResultSet r,String referenceColumn,String description)throws SQLException{return new Voucher(r.getObject("id",UUID.class),r.getString("number"),Type.RECEIPT,r.getObject("effective_date",LocalDate.class),r.getString("party"),r.getString(referenceColumn),description,r.getString("evidence"),r.getObject("money_account_id",UUID.class),r.getBigDecimal("total"),r.getInt("transaction_count"),r.getObject("journal_id",UUID.class),r.getObject("posted_by",UUID.class),r.getString("posted_by_name"),r.getObject("posted_at",OffsetDateTime.class),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class),r.getString("institution_name"),r.getString("branch_name"),List.of(),r.getObject("borrower_party_id",UUID.class),r.getString("client_number"));}
}
