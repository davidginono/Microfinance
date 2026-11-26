package com.sacco.mvp.accounting.statements.repository;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.reports.PostingDescription;
import com.sacco.mvp.accounting.reports.PostingDescriptionSql;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class StatementRepository {
    private final JdbcTemplate jdbc;
    // Stable report sections, actual parent IDs and current approved versions. Legacy cash/note
    // settings are read only as migration compatibility; legacy line/section overrides are retired.
    private static final String MAPPING="""
      coalesce(f.name,'fs.unclassified.'||lower(a.type)) line_name,
      case when a.type in ('EQUITY','INCOME','EXPENSE') then a.type
           when f.id is not null then coalesce(role.section,'UNCLASSIFIED') else 'UNCLASSIFIED' end section,
      coalesce(nullif(av.snapshot->'reporting'->>'cashSection',''),nullif(fv.snapshot->'reporting'->>'cashSection',''),nullif(gv.snapshot->'reporting'->>'cashSection',''),nullif(rv.snapshot->'reporting'->>'cashSection',''),m.cash_section,'UNCLASSIFIED') cash_section,
      coalesce(nullif(av.snapshot->'reporting'->>'equityComponent',''),nullif(fv.snapshot->'reporting'->>'equityComponent',''),nullif(gv.snapshot->'reporting'->>'equityComponent',''),nullif(rv.snapshot->'reporting'->>'equityComponent',''),m.equity_component,'') equity_component,
      coalesce((av.snapshot->'reporting'->>'note')::int,(fv.snapshot->'reporting'->>'note')::int,(gv.snapshot->'reporting'->>'note')::int,(rv.snapshot->'reporting'->>'note')::int,m.note_no,0) note_no,
      coalesce(nullif(av.snapshot->'reporting'->>'noteText',''),nullif(fv.snapshot->'reporting'->>'noteText',''),nullif(gv.snapshot->'reporting'->>'noteText',''),nullif(rv.snapshot->'reporting'->>'noteText',''),m.note_text,'') note_text,
      f.id group_id,f.code group_code
      """;

    public record Stored(UUID id,LocalDate from,LocalDate through,OffsetDateTime generated,boolean wide,boolean saved,String payload,String checksum) {}
    public record Artifact(byte[] bytes,String checksum) {}
    public List<LedgerAccount> ledgerAccounts(String institution,String search,int offset,UUID selected){
        return jdbc.query("""
            select id,code||' — '||name as label from gl_account
            where sacco_id=? and kind<>'HEADING' and (?::uuid is null or id=?)
              and strpos(lower(code||' '||name),lower(?))>0
            order by code,id limit 26 offset ?
            """,(r,n)->new LedgerAccount(r.getObject("id",UUID.class),r.getString("label")),institution,selected,selected,search,offset);
    }
    public Optional<Stored> duplicate(AppUserPrincipal a,UUID key){return jdbc.query("select * from statement_run where sacco_id=? and station_id=? and requested_by=? and request_key=?",this::stored,a.getSaccoId(),a.getStationId(),a.getMemberId(),key).stream().findFirst();}
    public void lockRequest(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","STATEMENT/"+a.getSaccoId()+"/"+a.getStationId()+"/"+a.getMemberId()+"/"+key);}
    public void create(UUID id,AppUserPrincipal a,Parameters p,OffsetDateTime now,UUID key){jdbc.update("insert into statement_run(id,sacco_id,station_id,institution_wide,requested_by,request_key,date_from,date_through,generated_at) values(?,?,?,?,?,?,?,?,?)",id,a.getSaccoId(),a.getStationId(),p.institutionWide(),a.getMemberId(),key,p.from(),p.through(),now);}
    public int capture(UUID id,AppUserPrincipal a,Parameters p,OffsetDateTime cutoff){
        return jdbc.update("""
            insert into statement_run_journal(run_id,journal_id)
            select ?,j.id from gl_journal j where j.sacco_id=? and (? or j.station_id=?)
            and j.state='POSTED' and j.posted_at<=?
            and (j.effective_date between ? and ? or j.effective_date between ? and ?) order by j.id limit 100001
            """,id,a.getSaccoId(),p.institutionWide(),a.getStationId(),cutoff,p.from(),p.through(),p.previousFrom(),p.previousThrough());
    }
    public String[] identity(AppUserPrincipal a){return jdbc.queryForObject("select i.sacco_name,coalesce(nullif(s.address_location,''),s.station_id) branch from registered_saccos i join sacco_stations s on s.sacco_id=i.sacco_id where i.sacco_id=? and s.station_id=?",(r,n)->new String[]{r.getString(1),r.getString(2)},a.getSaccoId(),a.getStationId());}
    public List<Balance> balances(UUID id,String institution,Parameters p){
        return jdbc.query("""
            with amounts as (
             select l.account_id,
              coalesce(sum(l.debit-l.credit) filter(where j.effective_date<?),0) opening,
              coalesce(sum(l.debit) filter(where j.effective_date between ? and ?),0) debit,
              coalesce(sum(l.credit) filter(where j.effective_date between ? and ?),0) credit,
              sum(l.debit-l.credit) closing,
              coalesce(sum(l.debit-l.credit) filter(where j.effective_date<?),0) previous_opening,
              coalesce(sum(l.debit) filter(where j.effective_date between ? and ?),0) previous_debit,
              coalesce(sum(l.credit) filter(where j.effective_date between ? and ?),0) previous_credit,
              coalesce(sum(l.debit-l.credit) filter(where j.effective_date<=?),0) previous_closing
             from statement_run c join gl_journal j on j.sacco_id=c.sacco_id
              and (c.institution_wide or j.station_id=c.station_id) and j.state='POSTED'
              and j.posted_at<=c.generated_at and j.effective_date<=c.date_through
             join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
             where c.id=? group by l.account_id
            ) select a.id,a.code,a.name,a.type,a.purpose,
            """+MAPPING+"""
             ,coalesce(x.opening,0) opening,coalesce(x.debit,0) debit,coalesce(x.credit,0) credit,coalesce(x.closing,0) closing,
             coalesce(x.previous_opening,0) previous_opening,coalesce(x.previous_debit,0) previous_debit,
             coalesce(x.previous_credit,0) previous_credit,coalesce(x.previous_closing,0) previous_closing
             from gl_account a left join amounts x on x.account_id=a.id
             left join gl_account g0 on g0.id=a.parent_id and g0.sacco_id=a.sacco_id and g0.kind='HEADING'
             left join gl_account g on g.id=g0.parent_id and g.sacco_id=a.sacco_id and g.kind='HEADING'
             left join gl_account r on r.id=g.parent_id and r.sacco_id=a.sacco_id and r.kind='HEADING' and r.parent_id is null and r.type=a.type
             left join gl_account f on f.id=g0.id and f.sacco_id=a.sacco_id and r.id is not null and g.type=a.type and g0.type=a.type
             left join gl_account_reporting_section role on role.account_id=g.id and role.sacco_id=a.sacco_id
             left join gl_account_version av on av.id=a.current_version_id and av.sacco_id=a.sacco_id
             left join gl_account_version fv on fv.id=f.current_version_id and fv.sacco_id=a.sacco_id
             left join gl_account_version gv on gv.id=g.current_version_id and gv.sacco_id=a.sacco_id
             left join gl_account_version rv on rv.id=r.current_version_id and rv.sacco_id=a.sacco_id
             left join statement_account_mapping m on m.account_id=a.id and m.sacco_id=a.sacco_id
             where a.sacco_id=? and a.kind<>'HEADING'
             order by coalesce(r.code,a.code),coalesce(g.code,a.code),coalesce(f.code,a.code),a.code,a.id limit 2001
            """,(r,n)->new Balance(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("type"),r.getString("purpose"),mapping(r),r.getBigDecimal("opening"),r.getBigDecimal("debit"),r.getBigDecimal("credit"),r.getBigDecimal("closing"),r.getBigDecimal("previous_opening"),r.getBigDecimal("previous_debit"),r.getBigDecimal("previous_credit"),r.getBigDecimal("previous_closing")),
            p.from(),p.from(),p.through(),p.from(),p.through(),p.previousFrom(),p.previousFrom(),p.previousThrough(),p.previousFrom(),p.previousThrough(),p.previousThrough(),id,institution);
    }
    public List<Cash> cash(UUID id,Parameters p,List<Balance> balances){
        var moneyAccounts=balances.stream().filter(Balance::money).map(Balance::id).toArray(UUID[]::new);
        if(moneyAccounts.length==0)return List.of();
        return jdbc.query("""
        select j.id,j.effective_date,l.account_id,a.type,a.purpose,sum(l.debit) debit,sum(l.credit) credit
        from statement_run_journal c join gl_journal j on j.id=c.journal_id
        join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
        join gl_account a on a.id=l.account_id and a.sacco_id=l.sacco_id
        where c.run_id=? and (j.effective_date between ? and ? or j.effective_date between ? and ?)
        and exists(select 1 from gl_journal_line ml where ml.journal_id=j.id and ml.sacco_id=j.sacco_id
                   and ml.station_id=j.station_id and ml.account_id=any(?))
        group by j.id,j.effective_date,l.account_id,a.type,a.purpose order by j.id,l.account_id limit 20001
        """,(r,n)->new Cash(r.getObject("id",UUID.class),r.getObject("effective_date",LocalDate.class),r.getObject("account_id",UUID.class),r.getString("type"),r.getString("purpose"),r.getBigDecimal("debit"),r.getBigDecimal("credit")),id,p.from(),p.through(),p.previousFrom(),p.previousThrough(),new SqlArrayValue("uuid", (Object[])moneyAccounts));
    }
    public void payload(UUID id,String value,String checksum){jdbc.update("update statement_run set payload=?,checksum=? where id=? and payload is null",value,checksum,id);}
    public Optional<Stored> find(UUID id,AppUserPrincipal a){return jdbc.query("select * from statement_run where id=? and sacco_id=? and station_id=?",this::stored,id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<Stored> history(AppUserPrincipal a,int page,String sort,boolean wideAllowed){String order=switch(sort){case "oldest"->"generated_at asc,id";case "from"->"date_from asc,id";case "from_desc"->"date_from desc,id";case "through"->"date_through asc,id";case "through_desc"->"date_through desc,id";case "status"->"saved_at nulls first,id";case "status_desc"->"saved_at desc nulls last,id";default->"generated_at desc,id";};return jdbc.query("select id,date_from,date_through,generated_at,institution_wide,saved_at,null::text payload,null::text checksum from statement_run where sacco_id=? and station_id=? and (not institution_wide or ?) and payload is not null order by "+order+" limit 31 offset ?",this::stored,a.getSaccoId(),a.getStationId(),wideAllowed,page*30);}
    public List<Stored> branchSaved(String institution,String branch,int page) {
        return jdbc.query("""
            select id,date_from,date_through,generated_at,institution_wide,saved_at,null::text payload,null::text checksum
            from statement_run where sacco_id=? and station_id=? and not institution_wide and saved_at is not null and payload is not null
            order by generated_at desc,id desc limit 31 offset ?
            """,this::stored,institution,branch,page*30);
    }
    public Optional<Stored> branchSaved(String institution,String branch,UUID id) {
        return jdbc.query("""
            select id,date_from,date_through,generated_at,institution_wide,saved_at,payload,checksum
            from statement_run where id=? and sacco_id=? and station_id=? and not institution_wide and saved_at is not null
            """,this::stored,id,institution,branch).stream().findFirst();
    }
    public void save(UUID id,AppUserPrincipal a,OffsetDateTime now){jdbc.update("update statement_run set saved_at=? where id=? and sacco_id=? and station_id=? and saved_at is null",now,id,a.getSaccoId(),a.getStationId());}
    public Set<UUID> accountsForVoucher(UUID run,String search) {
        return new HashSet<>(jdbc.query("""
            select distinct l.account_id
            from statement_run s join statement_run_journal captured on captured.run_id=s.id
            join gl_journal j on j.id=captured.journal_id and j.sacco_id=s.sacco_id and j.station_id=s.station_id
            join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
            left join accounting_posted_voucher_document d on d.journal_id=j.id and d.sacco_id=j.sacco_id
                and d.station_id=j.station_id and d.issued_at<=s.generated_at
            where s.id=? and not s.institution_wide and
                (strpos(lower(coalesce(d.number,'')),?)>0 or strpos(lower(coalesce(j.source_reference,'')),?)>0)
            limit 2001
            """,(row,index)->row.getObject("account_id",UUID.class),run,search,search));
    }

    public List<Activity> activity(UUID run,UUID account,Parameters p,int page,int limit){
        return ledgerRows(run,List.of(account),p,"",Set.of(),page,limit).stream().map(LedgerRow::activity)
            .filter(Objects::nonNull).toList();
    }

    public List<LedgerRow> ledgerRows(UUID run,List<UUID> accounts,Parameters p,String search,
                                     Set<UUID> accountMatches,int page,int limit){
        String accountIds="{"+String.join(",",accounts.stream().map(UUID::toString).toList())+"}";
        String matches="{"+String.join(",",accountMatches.stream().map(UUID::toString).toList())+"}";
        return jdbc.query("""
        with snapshot as (select id,generated_at from statement_run where id=?),
        selected as (select account_id,ordinality from unnest(?::uuid[]) with ordinality a(account_id,ordinality)),
        movement as (
         select j.id,j.sacco_id,j.station_id,j.effective_date,j.source_type,j.source_reference,j.reason,j.reverses_id,l.account_id,
          sum(l.debit) debit,sum(l.credit) credit,
          sum(sum(l.debit-l.credit)) over(partition by l.account_id order by j.effective_date,j.id) running
         from snapshot s join statement_run_journal c on c.run_id=s.id join gl_journal j on j.id=c.journal_id
         join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
         join selected a on a.account_id=l.account_id
         where j.effective_date between ? and ?
         group by j.id,l.account_id
        ), terms as (select ?::text search,?::uuid[] account_matches),
        activity as (
         select a.account_id selected_account,a.ordinality,m.*
         from selected a cross join terms q cross join snapshot s left join movement m on m.account_id=a.account_id
         where q.search='' or a.account_id=any(q.account_matches)
            or strpos(lower(coalesce(m.source_reference,'')),q.search)>0
            or exists(select 1 from accounting_posted_voucher_document d where d.journal_id=m.id
                and d.sacco_id=m.sacco_id and d.station_id=m.station_id and d.issued_at<=s.generated_at
                and strpos(lower(d.number),q.search)>0)
         order by a.ordinality,m.effective_date,m.id limit ? offset ?
        )
        select activity.*,v.id voucher_id,v.type voucher_type,coalesce(subledger.account_number,v.client_number) account_number,
         coalesce(d.number,t.receipt_reference) voucher_number, v.description voucher_description,entries.narration,
         coalesce(repayment_activity.name,entries.name) activity_name,
         coalesce(repayment_activity.name_sw,entries.name_sw) activity_name_sw,
         case when r.id is not null then %s end receipt_line,r.description receipt_description,
         %s posting_notes,%s journal_reason,case when activity.reverses_id is not null then %s end correction_reason
        from activity cross join snapshot s
        left join accounting_posted_voucher_document d on d.journal_id=activity.id and d.sacco_id=activity.sacco_id and d.station_id=activity.station_id and d.issued_at<=s.generated_at
        left join accounting_voucher v on v.journal_id=activity.id and v.sacco_id=activity.sacco_id and v.station_id=activity.station_id and v.posted_at<=s.generated_at
        left join lateral (
         select %s narration,%s name,%s name_sw
         from accounting_voucher_transaction t join accounting_voucher_posting e on e.transaction_id=t.id and e.voucher_id=t.voucher_id
          and e.sacco_id=t.sacco_id and e.station_id=t.station_id and e.account_id=activity.account_id
         left join gl_transaction_code def on def.id=t.transaction_id and def.sacco_id=t.sacco_id
         where t.voucher_id=v.id and t.sacco_id=v.sacco_id and t.station_id=v.station_id
        ) entries on true
        left join accountant_loan_post p on p.journal_id=activity.id and p.sacco_id=activity.sacco_id and p.station_id=activity.station_id and p.state='POSTED'
        left join loan_applications posted_loan on posted_loan.id=p.loan_id and posted_loan.sacco_id=p.sacco_id and posted_loan.station_id=p.station_id
        left join accountant_loan_record lr on lr.loan_id=p.loan_id and lr.sacco_id=p.sacco_id and lr.station_id=p.station_id
        %s
        left join accountant_loan_receipt_row x on x.posting_id=p.id and x.sacco_id=p.sacco_id and x.station_id=p.station_id
        left join accountant_loan_receipt r on r.id=x.receipt_id and r.sacco_id=x.sacco_id and r.station_id=x.station_id and r.posted_at<=s.generated_at
        left join accounting_archive.gl_operational_bridge bridge on bridge.journal_id=activity.id and bridge.sacco_id=activity.sacco_id and bridge.station_id=activity.station_id
        left join loan_repayment_transactions t on t.id=coalesce(p.transaction_id,bridge.voucher_id) and t.sacco_id=activity.sacco_id and t.station_id=activity.station_id
         and t.kind in ('PAYMENT','REVERSAL') and t.posted_at<=s.generated_at
         and (t.loan_application_id=p.loan_id or (bridge.voucher_id=t.id and exists(
          select 1 from loan_journal_entries e where e.voucher_id=bridge.voucher_id and e.transaction_id=t.id and e.loan_application_id=t.loan_application_id)))
        left join lateral (
         select string_agg(distinct nullif(a.loan_id,''),', ' order by nullif(a.loan_id,'')) account_number
         from (
          select p.loan_id where p.id is not null
          union
          select loan_id from loan_charge_command where journal_id=activity.id and sacco_id=activity.sacco_id and station_id=activity.station_id
          union
          select q.loan_id from loan_adjustment_decision d join loan_adjustment_request q on q.id=d.request_id and q.sacco_id=d.sacco_id and q.station_id=d.station_id
           where d.journal_id=activity.id and d.sacco_id=activity.sacco_id and d.station_id=activity.station_id and d.decision='APPROVED'
          union
          select loans.loan_id from accountant_loan_topup t cross join lateral (values(t.loan_id),(t.source_id)) loans(loan_id)
           where t.journal_id=activity.id and t.sacco_id=activity.sacco_id and t.station_id=activity.station_id
         ) linked
         join loan_applications a on a.id=linked.loan_id and a.sacco_id=activity.sacco_id and a.station_id=activity.station_id
        ) subledger on true
        order by activity.ordinality,activity.effective_date,activity.id
        """.formatted(
            PostingDescriptionSql.withoutLoanPickerLabel("x.description","posted_loan.loan_id","lr.product_name"),
            PostingDescriptionSql.withoutLoanPickerLabel("p.notes","posted_loan.loan_id","lr.product_name"),
            PostingDescriptionSql.withoutLoanPickerLabel("activity.reason","posted_loan.loan_id","lr.product_name"),
            PostingDescriptionSql.withoutLoanPickerLabel("activity.reason","posted_loan.loan_id","lr.product_name"),
            PostingDescriptionSql.singleValue(PostingDescriptionSql.withoutTransactionPickerLabel("t")),
            PostingDescriptionSql.singleValue(PostingDescriptionSql.transactionName("t","def")),
            PostingDescriptionSql.singleValue(PostingDescriptionSql.transactionNameSw("t","def")),
            PostingDescriptionSql.repaymentActivity("activity")),
            (r,n)->new LedgerRow(r.getObject("selected_account",UUID.class),r.getObject("id")==null?null:new Activity(r.getObject("id",UUID.class),r.getObject("effective_date",LocalDate.class),r.getString("account_number"),r.getString("voucher_number"),
            PostingDescription.resolveWithActivity(r.getString("source_type"),r.getString("source_reference"),r.getString("voucher_number"),
                r.getString("activity_name"),r.getString("activity_name_sw"),r.getString("narration"),r.getString("voucher_description"),
                r.getString("correction_reason"),r.getString("receipt_line"),r.getString("receipt_description"),r.getString("posting_notes"),r.getString("journal_reason")),
            r.getBigDecimal("debit"),r.getBigDecimal("credit"),r.getBigDecimal("running"),source(r))),run,accountIds,p.from(),p.through(),search,matches,limit,page*30);}
    // Keep earlier immutable documents while serving the current, simplified layout.
    public Optional<Artifact> artifact(UUID id,Kind kind,String format,String language){return jdbc.query("select bytes,checksum from statement_run_artifact where run_id=? and kind=? and format=? and language=?",(r,n)->new Artifact(r.getBytes(1),r.getString(2)),id,artifactKind(kind),format,language).stream().findFirst();}
    public void artifact(UUID id,Kind kind,String format,String language,byte[] bytes,String checksum){jdbc.update("insert into statement_run_artifact(run_id,kind,format,language,bytes,checksum) values(?,?,?,?,?,?) on conflict do nothing",id,artifactKind(kind),format,language,bytes,checksum);}
    private static String artifactKind(Kind kind){return kind.name()+switch(kind){case EQUITY->"_V8";case LEDGER->"_V7";default->"_V4";};}
    private Stored stored(ResultSet r,int n)throws SQLException{return new Stored(r.getObject("id",UUID.class),r.getObject("date_from",LocalDate.class),r.getObject("date_through",LocalDate.class),r.getObject("generated_at",OffsetDateTime.class),r.getBoolean("institution_wide"),r.getObject("saved_at")!=null,r.getString("payload"),r.getString("checksum"));}
    private Mapping mapping(ResultSet r)throws SQLException{return new Mapping(r.getObject("id",UUID.class),r.getString("line_name"),r.getString("section"),r.getString("cash_section"),r.getString("equity_component"),r.getInt("note_no"),r.getString("note_text"),r.getObject("group_id",UUID.class),r.getString("group_code"));}
    private String source(ResultSet r)throws SQLException{if(r.getObject("voucher_id")!=null)return "/finance/vouchers/"+switch(r.getString("voucher_type")){case "RECEIPT"->"receipts";case "PAYMENT"->"payments";default->"journals";}+"/"+r.getObject("voucher_id");return null;}
}
