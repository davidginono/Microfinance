package com.sacco.mvp.accounting.loans.repository;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository @RequiredArgsConstructor
public class LoanRecordingRepository {
    private final JdbcTemplate jdbc;
    private static final String LOANS="""
        select a.id,a.loan_id,m.id client_id,m.full_name,m.member_no,r.product_name,r.application_date,r.requested_principal,
        a.amount,a.status,coalesce(l.principal-l.principal_paid,0) outstanding,coalesce(l.principal_paid,0) principal_paid,
        coalesce(l.interest_paid,0) interest_paid,coalesce(d.interest,0) due_interest,coalesce(d.arrears,0) arrears,coalesce(d.future,0) future_interest
        from accountant_loan_record r join loan_applications a on a.id=r.loan_id join members m on m.id=a.applicant_member_id
        left join loan_ledgers l on l.loan_application_id=a.id
        left join lateral (select sum(case when due_date<=? then interest-interest_paid else 0 end) interest,
        sum(case when due_date<? then principal-principal_paid+interest-interest_paid else 0 end) arrears,
        sum(case when due_date>? then interest-interest_paid else 0 end) future
        from loan_ledger_installments where loan_application_id=a.id) d on true
        """;
    private static final String POST_FROM="""
        from accountant_loan_post p join loan_applications a on a.id=p.loan_id
        join members m on m.id=a.applicant_member_id join members u on u.id=p.actor_id
        """;
    private static final String POSTS="""
        select p.*,a.loan_id loan_number,m.full_name,m.member_no,u.full_name actor_name,
        (select x.id from accountant_loan_post x where x.reverses_id=p.id) reversed_by
        """+POST_FROM;
    public void lockRequest(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","LOAN_RECORD/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
    public Optional<UUID> recordRequest(AppUserPrincipal a,UUID key,String hash){
        var rows=jdbc.queryForList("select loan_id,payload_hash,actor_id from accountant_loan_record where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();
        if(!hash.equals(row.get("payload_hash")) || !a.getMemberId().equals(row.get("actor_id")))throw new IllegalArgumentException("recording.error.retry");
        return Optional.of((UUID)row.get("loan_id"));
    }
    public void insertRecord(AppUserPrincipal a,UUID id,RecordForm f,String hash,String product,String metadata,OffsetDateTime now){
        jdbc.update("""
            insert into accountant_loan_record(loan_id,sacco_id,station_id,request_key,payload_hash,actor_id,recorded_at,application_date,
            requested_principal,first_payment_date,product_name,metadata) values(?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
            """,id,a.getSaccoId(),a.getStationId(),f.getRequestKey(),hash,a.getMemberId(),now,f.getApplicationDate(),f.getRequestedPrincipal(),f.getFirstPaymentDate(),product,metadata);
    }
    public Map<String,Object> record(AppUserPrincipal a,UUID id,boolean lock){
        var rows=jdbc.queryForList("""
            select r.*,i.sacco_name,coalesce(nullif(s.address_location,''),r.station_id) branch_name,m.full_name actor_name
            from accountant_loan_record r join registered_saccos i on i.sacco_id=r.sacco_id
            join sacco_stations s on s.sacco_id=r.sacco_id and s.station_id=r.station_id join members m on m.id=r.actor_id
            where r.loan_id=? and r.sacco_id=? and r.station_id=?
            """+(lock?" for update of r":""),id,a.getSaccoId(),a.getStationId());
        if(rows.isEmpty())throw new org.springframework.security.access.AccessDeniedException("Loan unavailable");return rows.getFirst();
    }
    public LoanRow loan(AppUserPrincipal a,UUID id,LocalDate today){return jdbc.query(LOANS+" where a.id=? and r.sacco_id=? and r.station_id=?",(org.springframework.jdbc.core.RowMapper<LoanRow>) this::loan,today,today,today,id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Loan unavailable"));}
    private String loanWhere(){return """
        where r.sacco_id=? and r.station_id=? and (?='' or strpos(lower(a.loan_id||' '||m.full_name||' '||m.member_no),?)>0)
        and (?::date is null or r.application_date>=?::date) and (?::date is null or r.application_date<=?::date)
        and (?='' or a.status=?) and (?::uuid is null or m.id=?::uuid)
        """;}
    private Object[] loanArgs(AppUserPrincipal a,Filter f,LocalDate today){return new Object[]{today,today,today,a.getSaccoId(),a.getStationId(),f.search().toLowerCase(Locale.ROOT),f.search().toLowerCase(Locale.ROOT),f.from(),f.from(),f.through(),f.through(),f.status(),f.status(),f.clientId(),f.clientId()};}
    public List<LoanRow> loans(AppUserPrincipal a,Filter f,LocalDate today,int limit,int offset){
        var args=new ArrayList<>(Arrays.asList(loanArgs(a,f,today)));args.add(limit);args.add(offset);
        String order=switch(f.sort()){case "oldest"->"r.application_date,a.id";case "amount"->"a.amount desc,a.id";default->"r.application_date desc,a.id desc";};
        return jdbc.query(LOANS+loanWhere()+" order by "+order+" limit ? offset ?",(org.springframework.jdbc.core.RowMapper<LoanRow>) this::loan,args.toArray());
    }
    public List<Choice> clients(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select id,member_no||' - '||full_name label from members where sacco_id=? and station_id=? and status='ACTIVE' and is_member
        and strpos(lower(member_no||' '||full_name),?)>0 order by member_no,id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Optional<Choice> client(AppUserPrincipal a,UUID id){return jdbc.query("select id,member_no||' - '||full_name label from members where id=? and sacco_id=? and station_id=? and status='ACTIVE' and is_member",(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<Choice> ledgerClients(AppUserPrincipal a,String search,int page){return jdbc.query("select m.id,m.member_no||' - '||m.full_name label from members m where m.sacco_id=? and ((m.station_id=? and m.is_member) or exists(select 1 from accountant_loan_record r join loan_applications l on l.id=r.loan_id where l.applicant_member_id=m.id and r.sacco_id=? and r.station_id=?)) and strpos(lower(m.member_no||' '||m.full_name),?)>0 order by m.member_no,m.id limit 26 offset ?",(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Optional<Choice> ledgerClient(AppUserPrincipal a,UUID id){return jdbc.query("select m.id,m.member_no||' - '||m.full_name label from members m where m.id=? and m.sacco_id=? and (m.station_id=? or exists(select 1 from accountant_loan_record r join loan_applications l on l.id=r.loan_id where l.applicant_member_id=m.id and r.sacco_id=? and r.station_id=?))",(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),id,a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<Choice> products(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select id,coalesce(product_name,loan_type) label from loan_product_settings where sacco_id=? and active
        and coalesce(product_status,'ACTIVE')='ACTIVE' and strpos(lower(coalesce(product_name,loan_type)),?)>0
        order by coalesce(display_order,2147483647),id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),a.getSaccoId(),search.toLowerCase(Locale.ROOT),page*25);}
    public List<Choice> accounts(AppUserPrincipal a,String purpose,String search,int page){return jdbc.query("""
        select id,code||' - '||name label,purpose from gl_account where sacco_id=? and active and
        ((?='MONEY' and kind='POSTING' and purpose in ('CASH','BANK','MOBILE_MONEY') and type='ASSET')
        or (?='PRINCIPAL' and kind='CONTROL' and purpose='LOAN_PRINCIPAL' and type='ASSET')
        or (?='INTEREST' and kind='POSTING' and purpose='CLEARING'))
        and strpos(lower(code||' '||name),?)>0 order by code,id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),r.getString("purpose")),a.getSaccoId(),purpose,purpose,purpose,search.toLowerCase(Locale.ROOT),page*25);}
    public Choice account(AppUserPrincipal a,UUID id,String category){
        return account(a,id,category,true);
    }
    public Choice account(AppUserPrincipal a,UUID id,String category,boolean lock){
        var rows=jdbc.queryForList("select id,code,name,purpose,kind,type,normal_balance from gl_account where sacco_id=? and id=? and active"+(lock?" for share":""),a.getSaccoId(),id);
        if(rows.isEmpty())throw new IllegalArgumentException("recording.error.account");var r=rows.getFirst();String purpose=(String)r.get("purpose");
        boolean valid=switch(category){case "MONEY"->"POSTING".equals(r.get("kind")) && "ASSET".equals(r.get("type")) && Set.of("CASH","BANK","MOBILE_MONEY").contains(purpose);
            case "PRINCIPAL"->"CONTROL".equals(r.get("kind")) && "LOAN_PRINCIPAL".equals(purpose) && "ASSET".equals(r.get("type")) && "DEBIT".equals(r.get("normal_balance"));
            default->"POSTING".equals(r.get("kind")) && "CLEARING".equals(purpose);};
        if(!valid)throw new IllegalArgumentException("recording.error.account");return new Choice(id,r.get("code")+" - "+r.get("name"),purpose);
    }
    public Optional<Posting> postRequest(AppUserPrincipal a,UUID key,String hash){
        var row=jdbc.queryForList("select id,payload_hash,actor_id from accountant_loan_post where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(row.isEmpty())return Optional.empty();var r=row.getFirst();if(!hash.equals(r.get("payload_hash")) || !a.getMemberId().equals(r.get("actor_id")))throw new IllegalArgumentException("recording.error.retry");
        return Optional.of(posting(a,(UUID)r.get("id")));
    }
    public void ticket(AppUserPrincipal a,UUID id,String kind,PostForm f,String hash,UUID journal,UUID reverses,Choice money,Choice principal,Choice interest,OffsetDateTime now){
        jdbc.update("""
            insert into accountant_loan_post(id,loan_id,sacco_id,station_id,kind,state,request_key,payload_hash,effective_date,reference,amount,
            money_account_id,principal_account_id,interest_account_id,money_account_name,principal_account_name,interest_account_name,evidence,notes,actor_id,recorded_at,journal_id,reverses_id)
            values(?,?,?,?,?,'POSTING',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,f.getLoanId(),a.getSaccoId(),a.getStationId(),kind,f.getRequestKey(),hash,f.getEffectiveDate(),f.getReference().strip(),f.getAmount(),
            money.id(),principal.id(),interest==null?null:interest.id(),money.label(),principal.label(),interest==null?"":interest.label(),f.getEvidence().strip(),f.getNotes().strip(),a.getMemberId(),now,journal,reverses);
    }
    public void finish(UUID id,UUID transaction,BigDecimal principal,BigDecimal interest,BigDecimal balance){jdbc.update("update accountant_loan_post set transaction_id=?,principal_amount=?,interest_amount=?,principal_balance=?,state='POSTED' where id=? and state='POSTING'",transaction,principal,interest,balance,id);}
    public Posting posting(AppUserPrincipal a,UUID id){return jdbc.query(POSTS+" where p.id=? and p.sacco_id=? and p.station_id=?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Posting unavailable"));}
    public List<Posting> loanPosts(AppUserPrincipal a,UUID loan,int limit,int offset){return jdbc.query(POSTS+" where p.loan_id=? and p.sacco_id=? and p.station_id=? order by p.recorded_at,p.id limit ? offset ?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,loan,a.getSaccoId(),a.getStationId(),limit,offset);}
    public List<Posting> clientPosts(AppUserPrincipal a,UUID client,int limit,int offset){return jdbc.query(POSTS+" where a.applicant_member_id=? and p.sacco_id=? and p.station_id=? and p.state='POSTED' order by p.recorded_at,p.id limit ? offset ?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,client,a.getSaccoId(),a.getStationId(),limit,offset);}
    private String postWhere(){return """
        where p.sacco_id=? and p.station_id=? and (p.kind=? or (?='PAYMENT' and p.kind='REVERSAL')) and p.state='POSTED'
        and (?='' or strpos(lower(a.loan_id||' '||m.full_name||' '||m.member_no||' '||p.reference),?)>0)
        and (?::date is null or p.effective_date>=?::date) and (?::date is null or p.effective_date<=?::date)
        and (?='' or (case when p.reverses_id is not null then 'REVERSAL' when exists(select 1 from accountant_loan_post x where x.reverses_id=p.id) then 'REVERSED' else 'POSTED' end)=?)
        """;}
    private Object[] postArgs(AppUserPrincipal a,String kind,Filter f){return new Object[]{a.getSaccoId(),a.getStationId(),kind,kind,f.search().toLowerCase(Locale.ROOT),f.search().toLowerCase(Locale.ROOT),f.from(),f.from(),f.through(),f.through(),f.status(),f.status()};}
    public List<Posting> posts(AppUserPrincipal a,String kind,Filter f,int limit,int offset){
        var args=new ArrayList<>(Arrays.asList(postArgs(a,kind,f)));args.add(limit);args.add(offset);String order=switch(f.sort()){case "oldest"->"p.effective_date,p.id";case "amount"->"p.amount desc,p.id";default->"p.effective_date desc,p.id desc";};
        return jdbc.query(POSTS+postWhere()+" order by "+order+" limit ? offset ?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,args.toArray());
    }
    public Totals totals(AppUserPrincipal a,String kind,Filter f){return jdbc.query( "select count(*) n,coalesce(sum(case when p.kind='REVERSAL' then -p.amount else p.amount end),0) amount,coalesce(sum(case when p.kind='REVERSAL' then -p.principal_amount else p.principal_amount end),0) principal,coalesce(sum(case when p.kind='REVERSAL' then -p.interest_amount else p.interest_amount end),0) interest "+POST_FROM+postWhere(),(r,n)->new Totals(r.getLong("n"),r.getBigDecimal("amount"),r.getBigDecimal("principal"),r.getBigDecimal("interest")),postArgs(a,kind,f)).getFirst();}
    public Map<String,Object> clientTotals(AppUserPrincipal a,Filter f,LocalDate today){return jdbc.queryForMap("select count(*) loans,coalesce(sum(outstanding),0) principal,coalesce(sum(due_interest),0) interest,coalesce(sum(arrears),0) arrears,coalesce(sum(future_interest),0) future_interest from ("+LOANS+loanWhere()+") q",loanArgs(a,f,today));}
    public void journal(AppUserPrincipal a,UUID journal,UUID source,PostForm f,String hash,UUID period,String kind,UUID reverses,OffsetDateTime now){
        jdbc.update("""
            insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,reverses_id,direct_post)
            values(?,?,?,1,?,?,?,?,?,'TZS','DRAFT',?,?,?,?,?,?,true)
            """,journal,a.getSaccoId(),a.getStationId(),period,"LOAN_RECORD_"+kind,source.toString(),f.getRequestKey(),hash,f.getEvidence().strip(),f.getNotes().strip(),f.getEffectiveDate(),a.getMemberId(),now,reverses);
    }
    public void lines(AppUserPrincipal a,UUID journal,List<Object[]> amounts){var rows=new ArrayList<Object[]>();for(var line:amounts)rows.add(new Object[]{UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),line[0],line[1],line[2]});jdbc.batchUpdate("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,?,?)",rows);}
    public void postJournal(AppUserPrincipal a,UUID journal,OffsetDateTime now){jdbc.update("insert into accounting_outbox(id,journal_id,sacco_id,station_id,event_type,created_at) values(?,?,?,?,?,?)",UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),"LOAN_RECORD_POSTED",now);jdbc.update("update gl_journal set state='POSTED',posted_at=? where id=?",now,journal);}
    public List<Object[]> reversedLines(UUID journal){return jdbc.query("select account_id,credit,debit from gl_journal_line where journal_id=? order by account_id",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBigDecimal(3)},journal);}
    public UUID principalAccount(UUID loan){return jdbc.queryForObject("select principal_account_id from accountant_loan_post where loan_id=? and kind='DISBURSEMENT'",UUID.class,loan);}
    private LoanRow loan(ResultSet r,int n)throws SQLException{return new LoanRow(r.getObject("id",UUID.class),r.getString("loan_id"),r.getObject("client_id",UUID.class),r.getString("full_name"),r.getString("member_no"),r.getString("product_name"),r.getObject("application_date",LocalDate.class),r.getBigDecimal("requested_principal"),r.getBigDecimal("amount"),r.getString("status"),r.getBigDecimal("outstanding"),r.getBigDecimal("due_interest"),r.getBigDecimal("arrears"),r.getBigDecimal("future_interest"),r.getBigDecimal("principal_paid"),r.getBigDecimal("interest_paid"));}
    private Posting posting(ResultSet r,int n)throws SQLException{return new Posting(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("full_name"),r.getString("member_no"),r.getString("kind"),r.getObject("effective_date",LocalDate.class),r.getString("reference"),r.getBigDecimal("amount"),r.getBigDecimal("principal_amount"),r.getBigDecimal("interest_amount"),r.getString("money_account_name"),r.getString("principal_account_name"),r.getString("interest_account_name"),r.getString("evidence"),r.getString("notes"),r.getObject("actor_id",UUID.class),r.getString("actor_name"),r.getObject("recorded_at",OffsetDateTime.class),r.getObject("journal_id",UUID.class),r.getObject("transaction_id",UUID.class),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class),r.getBigDecimal("principal_balance"));}
}
