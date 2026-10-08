package com.sacco.mvp.accounting.repository;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class VoucherRepository {
    private final JdbcTemplate jdbc;
    private static final String HEAD="""
        select v.*,m.full_name posted_by_name,i.sacco_name institution_name,
        coalesce((select nullif(s.address_location,'') from sacco_stations s where s.sacco_id=v.sacco_id and s.station_id=v.station_id),v.station_id) branch_name,
        (select r.id from accounting_voucher r where r.reverses_id=v.id) reversed_by
        from accounting_voucher v join members m on m.id=v.posted_by join registered_saccos i on i.sacco_id=v.sacco_id
        """;
    public void lockRequest(String institution,String branch,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","CORE/"+institution+"/"+branch+"/"+key);}
    public Optional<Voucher> byRequest(String institution,String branch,UUID key){return jdbc.query(HEAD+" where v.sacco_id=? and v.station_id=? and v.request_key=?",this::voucher,institution,branch,key).stream().findFirst();}
    public boolean sameRequest(UUID id,String hash,UUID actor){return Boolean.TRUE.equals(jdbc.queryForObject("select payload_hash=? and posted_by=? from accounting_voucher where id=?",Boolean.class,hash,actor,id));}
    public Optional<Voucher> find(String institution,String branch,Type type,UUID id,boolean lock){return jdbc.query(HEAD+" where v.sacco_id=? and v.station_id=? and v.type=? and v.id=?"+(lock?" for update of v":""),this::voucher,institution,branch,type.name(),id).stream().findFirst();}
    public List<Voucher> list(String institution,String branch,Type type,Filter f){
        String order=switch(f.sort()){case "oldest"->"v.effective_date,v.id";case "amount"->"v.total desc,v.id desc";default->"v.effective_date desc,v.id desc";};
        return jdbc.query(HEAD+"""
            where v.sacco_id=? and v.station_id=? and v.type=?
            and (?='' or strpos(lower(v.number||' '||v.party||' '||v.reference),?)>0)
            and (?::date is null or v.effective_date>=?::date) and (?::date is null or v.effective_date<=?::date)
            and (?='' or (case when v.reverses_id is not null then 'REVERSAL' when exists(select 1 from accounting_voucher r where r.reverses_id=v.id) then 'REVERSED' else 'POSTED' end)=?)
            """+" order by "+order+" limit 26 offset ?",this::voucher,institution,branch,type.name(),f.search().toLowerCase(Locale.ROOT),f.search().toLowerCase(Locale.ROOT),f.from(),f.from(),f.through(),f.through(),f.status(),f.status(),f.page()*25);
    }
    public List<Account> accounts(String institution,String search,int offset){return jdbc.query("select id,code,name,purpose from gl_account where sacco_id=? and active and kind='POSTING' and (strpos(lower(code||' '||name),?)>0) order by code,id limit 26 offset ?",this::account,institution,search.toLowerCase(Locale.ROOT),offset);}
    public List<Account> lockAccounts(String institution,List<UUID> ids){
        return selectedAccounts(institution,ids,true);
    }
    public List<Account> selectedAccounts(String institution,List<UUID> ids,boolean lock){
        if(ids.isEmpty())return List.of();
        var args=new ArrayList<Object>();args.add(institution);args.addAll(ids);
        return jdbc.query("select id,code,name,purpose from gl_account where sacco_id=? and active and kind='POSTING' and id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") order by id"+(lock?" for share":""),this::account,args.toArray());
    }
    public List<Mapping> mappings(String institution,String search,int offset){return mappingQuery(institution,null,search,offset,false);}
    public List<Mapping> lockMappings(String institution,List<UUID> ids){return mappingQuery(institution,ids,"",0,true);}
    private List<Mapping> mappingQuery(String institution,List<UUID> ids,String search,int offset,boolean lock){
        var args=new ArrayList<Object>();args.add(institution);
        String filter;
        if(ids==null){filter=" and strpos(lower(t.code||' '||t.name),?)>0";args.add(search.toLowerCase(Locale.ROOT));}
        else {filter=" and t.id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";args.addAll(ids);}
        if(!lock)args.add(offset);
        return jdbc.query("""
            select t.id,t.code||' - '||t.name transaction_name,p.request_key,d.component,
            da.id debit_id,da.code debit_code,da.name debit_name,da.purpose debit_purpose,
            ca.id credit_id,ca.code credit_code,ca.name credit_name,ca.purpose credit_purpose
            from gl_transaction_code t join gl_activity a on a.id=t.activity_id and a.sacco_id=t.sacco_id
            join gl_transaction_template p on p.transaction_id=t.id and p.sacco_id=t.sacco_id
            join gl_transaction_template_line d on d.transaction_id=t.id and d.sacco_id=t.sacco_id and d.side='DEBIT'
            join gl_transaction_template_line c on c.transaction_id=t.id and c.sacco_id=t.sacco_id and c.component=d.component and c.side='CREDIT'
            join gl_account da on da.id=d.account_id and da.sacco_id=t.sacco_id and da.active and da.kind='POSTING'
            join gl_account ca on ca.id=c.account_id and ca.sacco_id=t.sacco_id and ca.active and ca.kind='POSTING'
            where t.sacco_id=? and t.active and a.active
            """+filter+" order by t.id,d.component"+(lock?" for share of a,t,p,d,c,da,ca":" limit 26 offset ?"),
            (r,n)->new Mapping(r.getObject("id",UUID.class),r.getObject("request_key",UUID.class),r.getString("transaction_name"),r.getString("component"),
                new Account(r.getObject("debit_id",UUID.class),r.getString("debit_code"),r.getString("debit_name"),r.getString("debit_purpose")),
                new Account(r.getObject("credit_id",UUID.class),r.getString("credit_code"),r.getString("credit_name"),r.getString("credit_purpose"))),args.toArray());
    }
    public UUID period(String institution,LocalDate date,UUID actor,OffsetDateTime now){
        var existing=jdbc.queryForList("select id,state from accounting_period where sacco_id=? and starts_on<=? and ends_on>=? for share",institution,date,date);
        if(!existing.isEmpty()){
            if(existing.size()!=1 || !"OPEN".equals(existing.getFirst().get("state")))throw new Invalid("effectiveDate","period");
            return (UUID)existing.getFirst().get("id");
        }
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","GL_SETUP/"+institution);
        var rows=jdbc.queryForList("select id,state from accounting_period where sacco_id=? and starts_on<=? and ends_on>=? for share",institution,date,date);
        if(!rows.isEmpty()){
            if(rows.size()!=1 || !"OPEN".equals(rows.getFirst().get("state")))throw new Invalid("effectiveDate","period");
            return (UUID)rows.getFirst().get("id");
        }
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accounting_period where sacco_id=? and state in ('CLOSED','CLOSING') and ends_on>=?)",Boolean.class,institution,date)))throw new Invalid("effectiveDate","period");
        // A one-day initial period never overlaps an existing institution period.
        UUID id=UUID.randomUUID();jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'OPEN',?,?)",id,institution,date,date,actor,now);return id;
    }
    public String number(Type type,LocalDate date){long n=jdbc.queryForObject("select nextval('accounting_voucher_number_seq')",Long.class);return (type==Type.RECEIPT?"RV":type==Type.PAYMENT?"PV":"JV")+"-"+date.getYear()+"-"+String.format(Locale.ROOT,"%08d",n);}
    public void insert(Voucher v,String institution,String branch,Form f,String hash,UUID period,UUID originalJournal){
        jdbc.update("""
            insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,reverses_id,direct_post)
            values(?,?,?,1,?,?,?,?,?,'TZS','DRAFT',?,?,?,?,?,?,true)
            """,v.journalId(),institution,branch,period,v.reversesId()==null?"CORE_"+v.type().name():"CORE_REVERSAL",v.number(),f.getRequestKey(),hash,v.evidence(),v.description(),v.effectiveDate(),v.postedBy(),v.postedAt(),originalJournal);
        jdbc.update("""
            insert into accounting_voucher(id,sacco_id,station_id,number,type,request_key,payload_hash,effective_date,party,reference,description,evidence,money_account_id,total,transaction_count,journal_id,posted_by,posted_at,reverses_id)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,v.id(),institution,branch,v.number(),v.type().name(),f.getRequestKey(),hash,v.effectiveDate(),v.party(),v.reference(),v.description(),v.evidence(),v.moneyAccountId(),v.total(),v.transactionCount(),v.journalId(),v.postedBy(),v.postedAt(),v.reversesId());
        var transactions=new ArrayList<Object[]>();var lines=new ArrayList<Object[]>();
        for(var t:v.transactions()){
            transactions.add(new Object[]{t.id(),v.id(),institution,branch,t.lineNo(),t.transactionId(),t.templateKey(),t.component(),t.description(),t.amount(),t.debit().id(),t.credit().id(),t.debit().code(),t.debit().name(),t.credit().code(),t.credit().name(),t.transactionName()});
            lines.add(new Object[]{UUID.randomUUID(),v.journalId(),institution,branch,t.debit().id(),t.amount(),java.math.BigDecimal.ZERO});
            lines.add(new Object[]{UUID.randomUUID(),v.journalId(),institution,branch,t.credit().id(),java.math.BigDecimal.ZERO,t.amount()});
        }
        jdbc.batchUpdate("insert into accounting_voucher_transaction(id,voucher_id,sacco_id,station_id,line_no,transaction_id,template_key,component,description,amount,debit_account_id,credit_account_id,debit_code,debit_name,credit_code,credit_name,transaction_name) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",transactions);
        jdbc.batchUpdate("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,?,?)",lines);
        jdbc.update("insert into accounting_outbox(id,journal_id,sacco_id,station_id,event_type,created_at) values(?,?,?,?,?,?)",UUID.randomUUID(),v.journalId(),institution,branch,"CORE_"+v.type(),v.postedAt());
        jdbc.update("update gl_journal set state='POSTED',posted_at=? where id=?",v.postedAt(),v.journalId());
    }
    public List<Transaction> transactions(UUID id){return jdbc.query("select * from accounting_voucher_transaction where voucher_id=? order by line_no limit 50",(r,n)->new Transaction(r.getObject("id",UUID.class),r.getInt("line_no"),r.getObject("transaction_id",UUID.class),r.getObject("template_key",UUID.class),r.getString("transaction_name"),r.getString("component"),r.getString("description"),r.getBigDecimal("amount"),new Account(r.getObject("debit_account_id",UUID.class),r.getString("debit_code"),r.getString("debit_name"),""),new Account(r.getObject("credit_account_id",UUID.class),r.getString("credit_code"),r.getString("credit_name"),"")),id);}
    private Account account(ResultSet r,int n)throws SQLException{return new Account(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("purpose"));}
    private Voucher voucher(ResultSet r,int n)throws SQLException{return new Voucher(r.getObject("id",UUID.class),r.getString("number"),Type.valueOf(r.getString("type")),r.getObject("effective_date",LocalDate.class),r.getString("party"),r.getString("reference"),r.getString("description"),r.getString("evidence"),r.getObject("money_account_id",UUID.class),r.getBigDecimal("total"),r.getInt("transaction_count"),r.getObject("journal_id",UUID.class),r.getObject("posted_by",UUID.class),r.getString("posted_by_name"),r.getObject("posted_at",OffsetDateTime.class),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class),r.getString("institution_name"),r.getString("branch_name"),List.of());}
}
