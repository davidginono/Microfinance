package com.sacco.mvp.accounting.statements.repository;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class StatementRepository {
    private final JdbcTemplate jdbc;
    private static final String MAPPING="m.line_name,m.section,m.cash_section,m.equity_component,m.note_no,m.note_text,m.revision";
    public record Stored(UUID id,LocalDate from,LocalDate through,OffsetDateTime generated,boolean wide,boolean saved,String payload,String checksum) {}
    public record Artifact(byte[] bytes,String checksum) {}
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
             left join statement_account_mapping m on m.account_id=a.id and m.sacco_id=a.sacco_id
             where a.sacco_id=? and a.kind<>'HEADING' order by a.code,a.id limit 2001
            """,(r,n)->new Balance(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("type"),r.getString("purpose"),mapping(r),r.getBigDecimal("opening"),r.getBigDecimal("debit"),r.getBigDecimal("credit"),r.getBigDecimal("closing"),r.getBigDecimal("previous_opening"),r.getBigDecimal("previous_debit"),r.getBigDecimal("previous_credit"),r.getBigDecimal("previous_closing")),
            p.from(),p.from(),p.through(),p.from(),p.through(),p.previousFrom(),p.previousFrom(),p.previousThrough(),p.previousFrom(),p.previousThrough(),p.previousThrough(),id,institution);
    }
    public List<Cash> cash(UUID id,Parameters p){return jdbc.query("""
        select j.id,j.effective_date,l.account_id,a.type,a.purpose,sum(l.debit) debit,sum(l.credit) credit
        from statement_run_journal c join gl_journal j on j.id=c.journal_id
        join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
        join gl_account a on a.id=l.account_id and a.sacco_id=l.sacco_id
        where c.run_id=? and (j.effective_date between ? and ? or j.effective_date between ? and ?)
        and exists(select 1 from gl_journal_line ml join gl_account ma on ma.id=ml.account_id and ma.sacco_id=ml.sacco_id
                   where ml.journal_id=j.id and ma.purpose in ('CASH','BANK','MOBILE_MONEY'))
        group by j.id,j.effective_date,l.account_id,a.type,a.purpose order by j.id,l.account_id limit 20001
        """,(r,n)->new Cash(r.getObject("id",UUID.class),r.getObject("effective_date",LocalDate.class),r.getObject("account_id",UUID.class),r.getString("type"),r.getString("purpose"),r.getBigDecimal("debit"),r.getBigDecimal("credit")),id,p.from(),p.through(),p.previousFrom(),p.previousThrough());}
    public void payload(UUID id,String value,String checksum){jdbc.update("update statement_run set payload=?,checksum=? where id=? and payload is null",value,checksum,id);}
    public Optional<Stored> find(UUID id,AppUserPrincipal a){return jdbc.query("select * from statement_run where id=? and sacco_id=? and station_id=?",this::stored,id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<Stored> history(AppUserPrincipal a,int page,String sort,boolean wideAllowed){String order=switch(sort){case "oldest"->"generated_at asc,id";case "from"->"date_from asc,id";case "from_desc"->"date_from desc,id";case "through"->"date_through asc,id";case "through_desc"->"date_through desc,id";case "status"->"saved_at nulls first,id";case "status_desc"->"saved_at desc nulls last,id";default->"generated_at desc,id";};return jdbc.query("select id,date_from,date_through,generated_at,institution_wide,saved_at,null::text payload,null::text checksum from statement_run where sacco_id=? and station_id=? and (not institution_wide or ?) and payload is not null order by "+order+" limit 31 offset ?",this::stored,a.getSaccoId(),a.getStationId(),wideAllowed,page*30);}
    public void save(UUID id,AppUserPrincipal a,OffsetDateTime now){jdbc.update("update statement_run set saved_at=? where id=? and sacco_id=? and station_id=? and saved_at is null",now,id,a.getSaccoId(),a.getStationId());}
    public List<MappingRow> mappings(String institution,String search,int page,String sort,String direction){String column=Map.of("code","a.code","name","a.name","line","coalesce(m.line_name,'')","section","coalesce(m.section,'')").get(sort);if(column==null||!Set.of("asc","desc").contains(direction))throw new IllegalArgumentException("fs.error.filter");return jdbc.query("select a.id,a.code,a.name,a.type,a.purpose,"+MAPPING+" from gl_account a left join statement_account_mapping m on m.account_id=a.id and m.sacco_id=a.sacco_id where a.sacco_id=? and a.kind<>'HEADING' and strpos(lower(a.code||' '||a.name),?)>0 order by "+column+" "+direction+",a.id limit 31 offset ?",(r,n)->new MappingRow(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("type"),r.getString("purpose"),mapping(r),r.getObject("revision",UUID.class)),institution,search.toLowerCase(Locale.ROOT),page*30);}
    public Optional<MappingRow> account(String institution,UUID id){return jdbc.query("select a.id,a.code,a.name,a.type,a.purpose,"+MAPPING+" from gl_account a left join statement_account_mapping m on m.account_id=a.id and m.sacco_id=a.sacco_id where a.sacco_id=? and a.id=? and a.kind<>'HEADING'",(r,n)->new MappingRow(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("type"),r.getString("purpose"),mapping(r),r.getObject("revision",UUID.class)),institution,id).stream().findFirst();}

    public int map(AppUserPrincipal a,Mapping m,UUID expected,OffsetDateTime now){return jdbc.update("""
        insert into statement_account_mapping(account_id,sacco_id,line_name,section,cash_section,equity_component,note_no,note_text,revision,updated_by,updated_at)
        values(?,?,?,?,?,?,?,?,?,?,?) on conflict(account_id) do update set line_name=excluded.line_name,section=excluded.section,
        cash_section=excluded.cash_section,equity_component=excluded.equity_component,note_no=excluded.note_no,note_text=excluded.note_text,
        revision=excluded.revision,updated_by=excluded.updated_by,updated_at=excluded.updated_at
        where statement_account_mapping.revision=? and statement_account_mapping.sacco_id=excluded.sacco_id
        """,m.accountId(),a.getSaccoId(),m.line(),m.section(),m.cashSection(),m.equityComponent(),m.note(),m.noteText(),UUID.randomUUID(),a.getMemberId(),now,expected);}
    public List<Activity> activity(UUID run,UUID account,Parameters p,int page,int limit){return jdbc.query("""
        with activity as (
         select j.id,j.sacco_id,j.station_id,j.effective_date,j.reason,sum(l.debit) debit,sum(l.credit) credit,
          sum(sum(l.debit-l.credit)) over(order by j.effective_date,j.id) running,
          v.id voucher_id,v.type voucher_type,v.client_number
         from statement_run_journal c join gl_journal j on j.id=c.journal_id
         join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
         left join accounting_voucher v on v.journal_id=j.id and v.sacco_id=j.sacco_id and v.station_id=j.station_id
         where c.run_id=? and l.account_id=? and j.effective_date between ? and ?
         group by j.id,v.id,v.type order by j.effective_date,j.id limit ? offset ?
        )
        select activity.*,coalesce(subledger.account_number,activity.client_number) account_number from activity
        left join lateral (
         select string_agg(distinct nullif(a.loan_id,''),', ' order by nullif(a.loan_id,'')) account_number
         from (
          select loan_id from accountant_loan_post where journal_id=activity.id and sacco_id=activity.sacco_id and station_id=activity.station_id and state='POSTED'
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
        order by activity.effective_date,activity.id
        """,(r,n)->new Activity(r.getObject("id",UUID.class),r.getObject("effective_date",LocalDate.class),r.getString("account_number"),r.getString("reason"),r.getBigDecimal("debit"),r.getBigDecimal("credit"),r.getBigDecimal("running"),source(r)),run,account,p.from(),p.through(),limit,page*30);}
    // Keep earlier immutable documents while serving the current, simplified layout.
    public Optional<Artifact> artifact(UUID id,Kind kind,String format,String language){return jdbc.query("select bytes,checksum from statement_run_artifact where run_id=? and kind=? and format=? and language=?",(r,n)->new Artifact(r.getBytes(1),r.getString(2)),id,artifactKind(kind),format,language).stream().findFirst();}
    public void artifact(UUID id,Kind kind,String format,String language,byte[] bytes,String checksum){jdbc.update("insert into statement_run_artifact(run_id,kind,format,language,bytes,checksum) values(?,?,?,?,?,?) on conflict do nothing",id,artifactKind(kind),format,language,bytes,checksum);}
    private static String artifactKind(Kind kind){return kind.name()+"_V3";}
    private Stored stored(ResultSet r,int n)throws SQLException{return new Stored(r.getObject("id",UUID.class),r.getObject("date_from",LocalDate.class),r.getObject("date_through",LocalDate.class),r.getObject("generated_at",OffsetDateTime.class),r.getBoolean("institution_wide"),r.getObject("saved_at")!=null,r.getString("payload"),r.getString("checksum"));}
    private Mapping mapping(ResultSet r)throws SQLException{return r.getString("line_name")==null?null:new Mapping(r.getObject("id",UUID.class),r.getString("line_name"),r.getString("section"),r.getString("cash_section"),r.getString("equity_component"),r.getInt("note_no"),r.getString("note_text"));}
    private String source(ResultSet r)throws SQLException{if(r.getObject("voucher_id")!=null)return "/finance/vouchers/"+switch(r.getString("voucher_type")){case "RECEIPT"->"receipts";case "PAYMENT"->"payments";default->"journals";}+"/"+r.getObject("voucher_id");return null;}
}
