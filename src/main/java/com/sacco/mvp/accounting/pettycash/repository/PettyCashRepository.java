package com.sacco.mvp.accounting.pettycash.repository;

import com.sacco.mvp.accounting.pettycash.dto.PettyCashDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class PettyCashRepository {
    private final JdbcTemplate jdbc;
    public boolean configured(AppUserPrincipal a){
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from petty_cash_fund where sacco_id=? and station_id=?)",Boolean.class,a.getSaccoId(),a.getStationId()));
    }
    public Optional<Choice> choice(AppUserPrincipal a,UUID id){
        return jdbc.query("select id,code,name from gl_account where id=? and sacco_id=?",(r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),id,a.getSaccoId()).stream().findFirst();
    }
    public Optional<Fund> fund(AppUserPrincipal a){
        return jdbc.query("select f.*,a.code,a.name from petty_cash_fund f join gl_account a on a.id=f.account_id where f.sacco_id=? and f.station_id=?",
            (r,n)->new Fund(r.getObject("account_id",UUID.class),r.getString("code"),r.getString("name"),r.getObject("starts_on",LocalDate.class),r.getString("basis"),r.getObject("opening_journal_id",UUID.class),r.getString("evidence")),a.getSaccoId(),a.getStationId()).stream().findFirst();
    }
    public List<Choice> choices(AppUserPrincipal a,String search,int page){
        return jdbc.query("select id,code,name from gl_account where sacco_id=? and active and kind='POSTING' and type='ASSET' and normal_balance='DEBIT' and purpose='CASH' and not gl_account_requires_loan_source(id) and not exists(select 1 from petty_cash_fund f where f.account_id=gl_account.id) and strpos(lower(code||' '||name),?)>0 order by code,id limit 26 offset ?",
            (r,n)->new Choice(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),a.getSaccoId(),search.toLowerCase(Locale.ROOT),page*25);
    }
    public void configure(AppUserPrincipal a,Setup s,LocalDate today,OffsetDateTime now){
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","petty-setup:"+a.getSaccoId()+":"+a.getStationId());
        // The trigger rechecks classification and opening coverage under the same account lock as posting.
        var opening=jdbc.query("select j.id,j.effective_date from accounting_archive.gl_cutover_coverage c join gl_journal j on j.id=c.opening_journal_id where c.sacco_id=? and c.station_id=? and c.complete and j.state='POSTED'",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getObject(2,LocalDate.class)),a.getSaccoId(),a.getStationId()).stream().findFirst();
        jdbc.update("insert into petty_cash_fund(sacco_id,station_id,account_id,starts_on,basis,opening_journal_id,evidence,configured_by,configured_at) values(?,?,?,?,?,?,?,?,?)",
            a.getSaccoId(),a.getStationId(),s.accountId(),opening.map(o->o.getValue().plusDays(1)).orElse(today),opening.isPresent()?"REVIEWED_OPENING":"NEW_ZERO",opening.map(Map.Entry::getKey).orElse(null),s.evidence(),a.getMemberId(),now);
    }
    public Balances balances(AppUserPrincipal a,Fund f,Query q,OffsetDateTime cutoff,LocalDate today){
        return jdbc.queryForObject("""
            select coalesce(sum(l.debit-l.credit) filter(where j.effective_date<?),0),
             coalesce(sum(l.debit-l.credit) filter(where j.effective_date<=?),0),
             coalesce(sum(l.debit-l.credit) filter(where j.effective_date<=?),0)
            from gl_journal j join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
            where j.sacco_id=? and j.station_id=? and l.account_id=? and j.state='POSTED' and j.posted_at<=?
            """,(r,n)->new Balances(q.from().isBefore(f.startsOn())?null:r.getBigDecimal(1),r.getBigDecimal(2),q.through().isBefore(f.startsOn())?null:r.getBigDecimal(3)),
            q.from(),today,q.through(),a.getSaccoId(),a.getStationId(),f.accountId(),cutoff);
    }
    public List<Row> rows(AppUserPrincipal a,Fund f,Query q,OffsetDateTime cutoff){
        boolean movements=q.view().equals("movements");
        String source=movements?"""
            select j.id,j.id journal_id,j.posted_at,j.effective_date date,v.id voucher_id,lower(v.type)||'s' voucher_kind,
             coalesce(v.number,j.source_reference) number,coalesce(v.party,'') party,'' account,
             coalesce(nullif(v.description,''),nullif(j.reason,''),(select string_agg(t.description,'; ' order by t.line_no) from accounting_voucher_transaction t where t.voucher_id=v.id),'') description,
             sum(l.credit-l.debit) paid,sum(l.debit) money_in,sum(l.credit) money_out,
             sum(sum(l.debit-l.credit)) over(order by j.effective_date,j.posted_at,j.id) balance,
             case when j.reverses_id is not null then 'REVERSAL' when exists(select 1 from gl_journal rev where rev.reverses_id=j.id and rev.state='POSTED' and rev.posted_at<=?) then 'REVERSED' else 'POSTED' end state
            from gl_journal j join gl_journal_line l on l.journal_id=j.id and l.sacco_id=j.sacco_id and l.station_id=j.station_id
             left join accounting_voucher v on v.journal_id=j.id
            where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.posted_at<=? and l.account_id=? group by j.id,v.id
            """:"""
            select t.id,j.id journal_id,j.posted_at,v.effective_date date,v.id voucher_id,'payments' voucher_kind,v.number,v.party,
             d.account,t.description,p.paid,0::numeric money_in,0::numeric money_out,null::numeric balance,
             case when v.reverses_id is not null then 'REVERSAL' when exists(select 1 from accounting_voucher rev where rev.reverses_id=v.id and rev.posted_at<=?) then 'REVERSED' else 'POSTED' end state
            from accounting_voucher v join gl_journal j on j.id=v.journal_id and j.state='POSTED'
             join accounting_voucher_transaction t on t.voucher_id=v.id
             join accounting_voucher original on original.id=coalesce(v.reverses_id,v.id)
             join accounting_voucher_transaction ot on ot.voucher_id=original.id and ot.line_no=t.line_no
             join lateral (select sum(case e.side when 'CREDIT' then e.amount else -e.amount end) paid
              from accounting_voucher_posting e where e.transaction_id=t.id and e.account_id=?) p on p.paid is not null
             join lateral (select string_agg(e.account_name,', ' order by e.entry_no) account,
              bool_and(a.type='EXPENSE') expense from accounting_voucher_posting e join gl_account a on a.id=e.account_id
              where e.transaction_id=ot.id and e.side='DEBIT') d on d.expense
            where v.sacco_id=? and v.station_id=? and v.type='PAYMENT' and j.posted_at<=?
            """;
        var args=new ArrayList<Object>();args.add(cutoff);
        if(!movements)args.add(f.accountId());args.add(a.getSaccoId());args.add(a.getStationId());args.add(cutoff);if(movements)args.add(f.accountId());
        args.add(q.from());args.add(q.through());args.add(q.search().toLowerCase(Locale.ROOT));args.add(q.size()+1);args.add(q.page()*q.size());
        String[] sort=q.sort().split("_");String column=switch(sort[0]){case "date"->"date";case "number"->"number";case "party"->"party";case "account"->"account";case "description"->"description";case "paid"->"paid";case "in"->"money_in";case "out"->"money_out";case "balance"->"balance";case "state"->"state";default->throw new IllegalArgumentException("petty.error.filter");};
        String direction=sort[1].equals("asc")?" asc":" desc";
        String ordering=column+direction+(column.equals("date")?",posted_at"+direction+",journal_id"+direction:"")+",id"+direction;
        return jdbc.query("with entries as ("+source+") select * from entries where date between ? and ? and strpos(lower(number||' '||party||' '||account||' '||description),?)>0 order by "+ordering+" limit ? offset ?",
            (r,n)->new Row(r.getObject("id",UUID.class),r.getObject("date",LocalDate.class),r.getObject("voucher_id",UUID.class),r.getString("voucher_kind"),r.getString("number"),r.getString("party"),r.getString("account"),r.getString("description"),r.getBigDecimal("paid"),r.getBigDecimal("money_in"),r.getBigDecimal("money_out"),r.getBigDecimal("balance"),r.getString("state")),args.toArray());
    }
    public List<Pending> pending(AppUserPrincipal a,Fund fund,int page,int size,String sort,String search){
        String order=switch(sort){case "number_asc"->"w.number,w.id";case "number_desc"->"w.number desc,w.id";case "state_asc"->"w.approval,w.id";case "state_desc"->"w.approval desc,w.id";default->throw new IllegalArgumentException("petty.error.filter");};
        return jdbc.query("""
            select w.id,w.number,w.approval from voucher_workflow w where w.sacco_id=? and w.station_id=?
             and w.type='PAYMENT' and w.posting<>'POSTED' and strpos(lower(w.number||' '||w.title),?)>0 and exists(select 1 from voucher_workflow_item i
              where i.workflow_id=w.id and exists(
               select 1 from jsonb_array_elements(case jsonb_typeof(i.form_json->'rows') when 'array' then i.form_json->'rows' else '[]'::jsonb end) r
               where (i.form_json->>'moneyAccountId'=? and jsonb_array_length(case jsonb_typeof(r->'entries') when 'array' then r->'entries' else '[]'::jsonb end)=0)
                or exists(select 1 from jsonb_array_elements(case jsonb_typeof(r->'entries') when 'array' then r->'entries' else '[]'::jsonb end) e
                 where e->>'accountId'=? and e->>'side'='CREDIT')))
            """+" order by "+order+" limit ? offset ?",(r,n)->new Pending(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),fund.accountId().toString(),fund.accountId().toString(),size+1,page*size);
    }
}
