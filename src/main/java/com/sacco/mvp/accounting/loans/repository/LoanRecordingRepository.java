package com.sacco.mvp.accounting.loans.repository;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.dto.RegisterSort;
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
        select coalesce(c.outstanding,0) charges,a.id,coalesce(a.loan_id,a.application_number::text) loan_id,m.id client_id,m.full_name,m.member_no,r.product_name,r.application_date,r.requested_principal,
        a.amount,a.status,(a.top_up_source_loan_id is null and a.status='DISBURSED' and coalesce(l.principal-l.principal_paid,0)>0 and coalesce((select loan_top_up_enabled from sacco_settings where sacco_id=a.sacco_id),true) and not exists(select 1 from loan_applications child where child.top_up_source_loan_id=a.id and child.status not in ('MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED','DISBURSEMENT_REVERSED','PAID'))) topup_eligible,case when a.status='DISBURSEMENT_REVERSED' then 0 else coalesce(l.principal-l.principal_paid,0) end outstanding,coalesce(l.principal_paid,0) principal_paid,
        coalesce(l.interest_paid,0) interest_paid,case when a.status='DISBURSEMENT_REVERSED' or exists(select 1 from accountant_loan_topup ts where ts.source_id=a.id and ts.reverses_id is null and not exists(select 1 from accountant_loan_topup tr where tr.reverses_id=ts.id)) then 0 else coalesce(d.interest,0) end due_interest,case when a.status='DISBURSEMENT_REVERSED' or exists(select 1 from accountant_loan_topup ts where ts.source_id=a.id and ts.reverses_id is null and not exists(select 1 from accountant_loan_topup tr where tr.reverses_id=ts.id)) then 0 else coalesce(d.arrears,0) end arrears,case when a.status='DISBURSEMENT_REVERSED' or exists(select 1 from accountant_loan_topup ts where ts.source_id=a.id and ts.reverses_id is null and not exists(select 1 from accountant_loan_topup tr where tr.reverses_id=ts.id)) then 0 else coalesce(d.future,0) end future_interest
        from accountant_loan_record r join loan_applications a on a.id=r.loan_id join members m on m.id=a.applicant_member_id
        left join loan_charge_balance c on c.loan_id=a.id and c.sacco_id=r.sacco_id and c.station_id=r.station_id
        left join loan_ledgers l on l.loan_application_id=a.id
        left join lateral (select sum(loan_interest_due(id,?)) interest,
        sum(case when due_date<? then principal-principal_paid+interest-interest_paid-interest_excluded-interest_waived else 0 end) arrears,
        sum(greatest(0,interest-interest_paid-interest_excluded-interest_waived-loan_interest_due(id,?))) future
        from loan_ledger_installments where loan_application_id=a.id) d on true
        """;
    private static final String POST_FROM="""
        from accountant_loan_movement p join loan_applications a on a.id=p.loan_id
        join accountant_loan_record r on r.loan_id=p.loan_id and r.sacco_id=p.sacco_id and r.station_id=p.station_id
        join members m on m.id=a.applicant_member_id join members u on u.id=p.actor_id
        """;
    private static final String POSTS="""
        select p.*,coalesce((select cash from accountant_loan_topup where id=p.id),p.amount-case when p.kind in ('DISBURSEMENT','FUNDING_UNDO') then coalesce((select sum(amount) from loan_charge_event where journal_id=p.journal_id and kind in ('DEDUCT','DEDUCT_UNDO')),0) else 0 end) cash_amount,a.loan_id loan_number,m.full_name,m.member_no,u.full_name actor_name,r.product_name,
        coalesce((select x.id from accountant_loan_movement x where x.reverses_id=p.id),(select x.id from accountant_loan_topup x where x.reverses_id=p.id)) reversed_by
        """+POST_FROM;
    public void lockRequest(AppUserPrincipal a,UUID key){jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","LOAN_RECORD/"+a.getSaccoId()+"/"+a.getStationId()+"/"+key);}
    public UUID disbursementCorrection(AppUserPrincipal a,UUID posting){return jdbc.query("select q.id from loan_adjustment_request q join loan_adjustment_decision d on d.request_id=q.id join gl_journal j on j.id=d.journal_id and j.state='POSTED' join accountant_loan_post p on p.journal_id=q.reverses_journal_id where p.id=? and q.sacco_id=? and q.station_id=? and q.kind='DISBURSEMENT_REVERSAL'",(r,n)->r.getObject(1,UUID.class),posting,a.getSaccoId(),a.getStationId()).stream().findFirst().orElse(null);}
    public Optional<UUID> receiptForPosting(AppUserPrincipal a,UUID id){return jdbc.query("select receipt_id from accountant_loan_receipt_row where posting_id=? and sacco_id=? and station_id=?",(r,n)->r.getObject(1,UUID.class),id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<LoanRow> repaymentLoans(AppUserPrincipal a,UUID client,String search,int page,LocalDate today){
        return jdbc.query(LOANS+" where r.sacco_id=? and r.station_id=? and m.id=? and a.status in ('DISBURSED','PAR','DEFAULTED') and l.loan_application_id is not null and strpos(lower(a.loan_id),?)>0 order by a.loan_id,a.id limit 26 offset ?",(org.springframework.jdbc.core.RowMapper<LoanRow>)this::loan,today,today,today,a.getSaccoId(),a.getStationId(),client,search.toLowerCase(Locale.ROOT),page*25);
    }
    public List<UUID> lockReceiptLoans(AppUserPrincipal a,List<UUID> ids,UUID client){
        var args=new ArrayList<Object>();args.add(a.getSaccoId());args.add(a.getStationId());args.add(client);args.add(client);args.addAll(ids);
        return jdbc.query("select r.loan_id from accountant_loan_record r join loan_applications l on l.id=r.loan_id where r.sacco_id=? and r.station_id=? and (?::uuid is null or l.applicant_member_id=?::uuid) and r.loan_id in ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") order by r.loan_id for update of r",(r,n)->r.getObject(1,UUID.class),args.toArray());
    }
    public List<ReceiptLoan> receiptLoans(AppUserPrincipal a,List<UUID> ids,LocalDate date){
        var args=new ArrayList<Object>();args.add(date);args.add(date);args.add(a.getSaccoId());args.add(a.getStationId());args.addAll(ids);
        return jdbc.query("""
            select l.id,l.applicant_member_id,l.loan_id,m.full_name,m.member_no,l.status,b.disbursement_date,greatest(b.last_payment_date,c.last_date,(select max(effective_date) from loan_interest_waiver where loan_id=l.id)) last_payment_date,
            b.principal-b.principal_paid outstanding,coalesce(d.principal,0) due_principal,coalesce(d.interest,0) due_interest,coalesce(c.outstanding,0) charges,
            r.product_name,ab.principal_account_id,ab.interest_account_id,pa.code||' - '||pa.name principal_account,ia.code||' - '||ia.name interest_account
            from accountant_loan_record r join loan_applications l on l.id=r.loan_id join members m on m.id=l.applicant_member_id
            left join loan_ledgers b on b.loan_application_id=l.id
            left join loan_charge_balance c on c.loan_id=l.id
            left join loan_product_account_binding ab on ab.loan_id=l.id and ab.sacco_id=r.sacco_id and ab.station_id=r.station_id
            left join gl_account pa on pa.id=ab.principal_account_id and pa.sacco_id=r.sacco_id and pa.active and pa.kind='CONTROL' and pa.type='ASSET' and pa.normal_balance='DEBIT' and pa.purpose='LOAN_PRINCIPAL'
            left join gl_account ia on ia.id=ab.interest_account_id and ia.sacco_id=r.sacco_id and ia.active and ia.kind='POSTING' and (ia.purpose='CLEARING' or (ia.type='INCOME' and ia.normal_balance='CREDIT' and ia.purpose='INCOME'))
            left join lateral(select sum(case when due_date<=? then principal-principal_paid else 0 end) principal,sum(loan_interest_due(id,?)) interest
              from loan_ledger_installments where loan_application_id=l.id) d on true
            where r.sacco_id=? and r.station_id=? and l.id in (
            """+String.join(",",Collections.nCopies(ids.size(),"?"))+") order by l.id",(r,n)->new ReceiptLoan(r.getObject("id",UUID.class),r.getObject("applicant_member_id",UUID.class),r.getString("loan_id"),r.getString("full_name"),r.getString("member_no"),r.getBigDecimal("due_principal"),r.getBigDecimal("due_interest"),r.getBigDecimal("outstanding"),r.getObject("disbursement_date",LocalDate.class),r.getObject("last_payment_date",LocalDate.class),r.getString("status"),r.getBigDecimal("charges"),r.getString("product_name"),r.getObject("principal_account_id",UUID.class),r.getString("principal_account"),r.getObject("interest_account_id",UUID.class),r.getString("interest_account")),args.toArray());
    }
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
        where r.sacco_id=? and r.station_id=? and (?='' or strpos(lower(coalesce(a.loan_id,a.application_number::text)||' '||m.full_name||' '||m.member_no),?)>0)
        and (?::date is null or r.application_date>=?::date) and (?::date is null or r.application_date<=?::date)
        and (?='' or a.status=?) and (?::uuid is null or m.id=?::uuid)
        and (?::uuid is null or a.loan_product_setting_id=?::uuid)
        """;}
    private Object[] loanArgs(AppUserPrincipal a,Filter f,LocalDate today){return new Object[]{today,today,today,a.getSaccoId(),a.getStationId(),f.search().toLowerCase(Locale.ROOT),f.search().toLowerCase(Locale.ROOT),f.from(),f.from(),f.through(),f.through(),f.status(),f.status(),f.clientId(),f.clientId(),f.productId(),f.productId()};}
    public List<LoanRow> loans(AppUserPrincipal a,Filter f,LocalDate today,int limit,int offset){
        var args=new ArrayList<>(Arrays.asList(loanArgs(a,f,today)));args.add(limit);args.add(offset);
        String order=RegisterSort.order(f.sort(),Map.of("number","coalesce(a.loan_id,a.application_number::text)","product","r.product_name","client","m.full_name","date","r.application_date","amount","a.amount","balance","coalesce(l.principal-l.principal_paid,0)","status","a.status"),"a.id");
        return jdbc.query(LOANS+loanWhere()+" order by "+order+" limit ? offset ?",(org.springframework.jdbc.core.RowMapper<LoanRow>) this::loan,args.toArray());
    }
    public List<Choice> clients(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select id,member_no||' - '||full_name label from members where sacco_id=? and station_id=? and status='ACTIVE' and is_member
        and strpos(lower(member_no||' '||full_name),?)>0 order by member_no,id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Optional<Choice> client(AppUserPrincipal a,UUID id){return jdbc.query("select id,member_no||' - '||full_name label from members where id=? and sacco_id=? and station_id=? and status='ACTIVE' and is_member",(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),id,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<Choice> ledgerClients(AppUserPrincipal a,String search,int page){return jdbc.query("select m.id,m.member_no||' - '||m.full_name label from members m where m.sacco_id=? and ((m.station_id=? and m.is_member) or exists(select 1 from accountant_loan_record r join loan_applications l on l.id=r.loan_id where l.applicant_member_id=m.id and r.sacco_id=? and r.station_id=?)) and strpos(lower(m.member_no||' '||m.full_name),?)>0 order by m.member_no,m.id limit 26 offset ?",(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Optional<Choice> ledgerClient(AppUserPrincipal a,UUID id){return jdbc.query("select m.id,m.member_no||' - '||m.full_name label from members m where m.id=? and m.sacco_id=? and (m.station_id=? or exists(select 1 from accountant_loan_record r join loan_applications l on l.id=r.loan_id where l.applicant_member_id=m.id and r.sacco_id=? and r.station_id=?))",(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),id,a.getSaccoId(),a.getStationId(),a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<StatementTerms> statementTerms(AppUserPrincipal a,UUID client){return statementTerms(a,client,null);}
    public List<StatementTerms> statementTerms(AppUserPrincipal a,UUID client,UUID loan){return jdbc.query("""
        select coalesce((a.financial_snapshot->'recordedCharges')::text,'') charge_snapshot,a.id,a.tenor_months,coalesce(a.first_repayment_date,r.first_payment_date) first_payment_date,a.final_due_date,
        coalesce(a.financial_snapshot->>'tenureUnit','MONTHS') tenure_unit,
        coalesce((a.financial_snapshot->>'tenureValue')::integer,a.tenor_months) tenure_value,
        case when a.financial_snapshot->>'loanMode'='CUSTOM' then case when a.financial_snapshot->>'interestEntry'='AMOUNT' then 'AMOUNT' else coalesce(a.financial_snapshot->>'interestBasis','WHOLE_LOAN') end else 'ANNUAL' end rate_basis,
        (a.financial_snapshot::jsonb->>'interestRate')::numeric rate,
        a.financial_snapshot::jsonb->>'repaymentFrequency' frequency,
        (a.financial_snapshot::jsonb->>'numberOfPayments')::integer installments,
        (a.financial_snapshot::jsonb->>'periodicRepaymentAmount')::numeric installment,
        i.sacco_name,coalesce(nullif(s.address_location,''),r.station_id) branch_name
        from accountant_loan_record r join loan_applications a on a.id=r.loan_id
        join registered_saccos i on i.sacco_id=r.sacco_id
        join sacco_stations s on s.sacco_id=r.sacco_id and s.station_id=r.station_id
        where a.applicant_member_id=? and r.sacco_id=? and r.station_id=? and (?::uuid is null or a.id=?)
        order by r.application_date,a.id limit 501
        """,(r,n)->new StatementTerms(r.getObject("id",UUID.class),r.getBigDecimal("rate"),r.getString("frequency"),
            r.getInt("tenor_months"),r.getInt("installments"),r.getObject("first_payment_date",LocalDate.class),
            r.getBigDecimal("installment"),r.getString("sacco_name"),r.getString("branch_name"),r.getString("rate_basis"),r.getString("tenure_unit"),r.getInt("tenure_value"),r.getObject("final_due_date",LocalDate.class),r.getString("charge_snapshot")),client,a.getSaccoId(),a.getStationId(),loan,loan);}
    public List<Choice> products(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select id,coalesce(product_name,loan_type) label,coalesce(tenure_unit,'MONTHS') tenure_unit,minimum_amount,maximum_amount,
        coalesce(min_tenure,case when min_repayment_months>0 then min_repayment_months else 1 end) minimum_tenure,
        coalesce(max_tenure,max_repayment_months,600) maximum_tenure from loan_product_settings where sacco_id=? and active and not custom_recording_configuration
        and coalesce(product_status,'ACTIVE')='ACTIVE' and strpos(lower(coalesce(product_name,loan_type)),?)>0
        order by coalesce(display_order,2147483647),id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),"",r.getString("tenure_unit"),r.getString("minimum_amount"),r.getString("maximum_amount"),r.getInt("minimum_tenure"),r.getInt("maximum_tenure")),a.getSaccoId(),search.toLowerCase(Locale.ROOT),page*25);}
    public List<Choice> filterProducts(AppUserPrincipal a,String search,int page){return jdbc.query("""
        select id,coalesce(product_name,loan_type) label from loan_product_settings where sacco_id=?
        and strpos(lower(coalesce(product_name,loan_type)),?)>0
        order by coalesce(display_order,2147483647),id limit 26 offset ?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),""),a.getSaccoId(),search.toLowerCase(Locale.ROOT),page*25);}
    public Optional<Choice> filterProduct(AppUserPrincipal a,UUID id){return jdbc.query("""
        select id,coalesce(product_name,loan_type) label,coalesce(tenure_unit,'MONTHS') tenure_unit,minimum_amount,maximum_amount,
        coalesce(min_tenure,case when min_repayment_months>0 then min_repayment_months else 1 end) minimum_tenure,
        coalesce(max_tenure,max_repayment_months,600) maximum_tenure from loan_product_settings where sacco_id=? and id=?
        """,(r,n)->new Choice(r.getObject("id",UUID.class),r.getString("label"),"",r.getString("tenure_unit"),r.getString("minimum_amount"),r.getString("maximum_amount"),r.getInt("minimum_tenure"),r.getInt("maximum_tenure")),a.getSaccoId(),id).stream().findFirst();}
    public List<Choice> accounts(AppUserPrincipal a,String purpose,String search,int page){return jdbc.query("""
        select id,code||' - '||name label,purpose from gl_account where sacco_id=? and active and
        ((?='MONEY' and kind='POSTING' and purpose in ('CASH','BANK','MOBILE_MONEY') and type='ASSET')
        or (?='PRINCIPAL' and kind='CONTROL' and purpose='LOAN_PRINCIPAL' and type='ASSET')
        or (?='INTEREST' and kind='POSTING' and (purpose='CLEARING' or (type='INCOME' and normal_balance='CREDIT' and purpose='INCOME'))))
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
            default->"POSTING".equals(r.get("kind")) && ("CLEARING".equals(purpose) || ("INCOME".equals(r.get("type")) && "CREDIT".equals(r.get("normal_balance")) && "INCOME".equals(purpose)));};
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
    public void finish(UUID id,UUID transaction,BigDecimal principal,BigDecimal interest,BigDecimal balance){finish(id,transaction,principal,interest,balance,BigDecimal.ZERO);}
    public void finish(UUID id,UUID transaction,BigDecimal principal,BigDecimal interest,BigDecimal balance,BigDecimal charges){jdbc.update("update accountant_loan_post set transaction_id=?,principal_amount=?,interest_amount=?,principal_balance=?,charge_amount=?,state='POSTED' where id=? and state='POSTING'",transaction,principal,interest,balance,charges,id);}
    public Posting posting(AppUserPrincipal a,UUID id){return jdbc.query(POSTS+" where p.id=? and p.sacco_id=? and p.station_id=?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,id,a.getSaccoId(),a.getStationId()).stream().findFirst().orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Posting unavailable"));}
    public Optional<Posting> loanOpening(AppUserPrincipal a,UUID loan){return jdbc.query(POSTS+" where p.loan_id=? and p.sacco_id=? and p.station_id=? and p.kind='DISBURSEMENT' order by p.recorded_at,p.id limit 1",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,loan,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public List<Posting> loanPosts(AppUserPrincipal a,UUID loan,int limit,int offset){return jdbc.query(POSTS+" where p.loan_id=? and p.sacco_id=? and p.station_id=? order by p.recorded_at,p.id limit ? offset ?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,loan,a.getSaccoId(),a.getStationId(),limit,offset);}
    public List<Posting> clientPosts(AppUserPrincipal a,UUID client,int limit,int offset){return jdbc.query(POSTS+" where a.applicant_member_id=? and p.sacco_id=? and p.station_id=? and p.state='POSTED' order by p.recorded_at,p.id limit ? offset ?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,client,a.getSaccoId(),a.getStationId(),limit,offset);}
    private String postWhere(){return """
        where p.sacco_id=? and p.station_id=? and (p.kind=? or (?='PAYMENT' and p.kind='REVERSAL') or (?='DISBURSEMENT' and p.kind='FUNDING_UNDO')) and p.state='POSTED'
        and (?='' or strpos(lower(coalesce(a.loan_id,a.application_number::text)||' '||m.full_name||' '||m.member_no||' '||p.reference),?)>0)
        and (?::date is null or p.effective_date>=?::date) and (?::date is null or p.effective_date<=?::date)
        and (?='' or (case when p.reverses_id is not null then 'REVERSAL' when (exists(select 1 from accountant_loan_post x where x.reverses_id=p.id) or exists(select 1 from accountant_loan_topup x where x.reverses_id=p.id)) then 'REVERSED' else 'POSTED' end)=?)
        and (?::uuid is null or a.loan_product_setting_id=?::uuid)
        """;}
    private Object[] postArgs(AppUserPrincipal a,String kind,Filter f){return new Object[]{a.getSaccoId(),a.getStationId(),kind,kind,kind,f.search().toLowerCase(Locale.ROOT),f.search().toLowerCase(Locale.ROOT),f.from(),f.from(),f.through(),f.through(),f.status(),f.status(),f.productId(),f.productId()};}
    public List<Posting> posts(AppUserPrincipal a,String kind,Filter f,int limit,int offset){
        var args=new ArrayList<>(Arrays.asList(postArgs(a,kind,f)));args.add(limit);args.add(offset);
        String order=RegisterSort.order(f.sort(),Map.of("number","a.loan_id","product","r.product_name","client","m.full_name","date","p.effective_date","amount","case when p.kind in ('REVERSAL','FUNDING_UNDO') then -p.amount else coalesce((select cash from accountant_loan_topup where id=p.id),p.amount-case when p.kind in ('DISBURSEMENT','FUNDING_UNDO') then coalesce((select sum(amount) from loan_charge_event where journal_id=p.journal_id and kind in ('DEDUCT','DEDUCT_UNDO')),0) else 0 end) end","reference","p.reference","status","case when p.reverses_id is not null then 'REVERSAL' when (exists(select 1 from accountant_loan_post x where x.reverses_id=p.id) or exists(select 1 from accountant_loan_topup x where x.reverses_id=p.id)) then 'REVERSED' else 'POSTED' end"),"p.id");
        return jdbc.query(POSTS+postWhere()+" order by "+order+" limit ? offset ?",(org.springframework.jdbc.core.RowMapper<Posting>) this::posting,args.toArray());
    }
    public Totals totals(AppUserPrincipal a,String kind,Filter f){return jdbc.query( "select count(*) n,coalesce(sum(case when p.kind in ('REVERSAL','FUNDING_UNDO') then -p.amount else coalesce((select cash from accountant_loan_topup where id=p.id),p.amount-case when p.kind in ('DISBURSEMENT','FUNDING_UNDO') then coalesce((select sum(amount) from loan_charge_event where journal_id=p.journal_id and kind in ('DEDUCT','DEDUCT_UNDO')),0) else 0 end) end),0) amount,coalesce(sum(case when p.kind in ('REVERSAL','FUNDING_UNDO') then -p.principal_amount else p.principal_amount end),0) principal,coalesce(sum(case when p.kind in ('REVERSAL','FUNDING_UNDO') then -p.interest_amount else p.interest_amount end),0) interest "+POST_FROM+postWhere(),(r,n)->new Totals(r.getLong("n"),r.getBigDecimal("amount"),r.getBigDecimal("principal"),r.getBigDecimal("interest")),postArgs(a,kind,f)).getFirst();}
    public Map<String,Object> clientTotals(AppUserPrincipal a,Filter f,LocalDate today){return jdbc.queryForMap("select count(*) loans,coalesce(sum(outstanding),0) principal,coalesce(sum(due_interest),0) interest,coalesce(sum(arrears),0) arrears,coalesce(sum(future_interest),0) future_interest,coalesce(sum(charges),0) charges from ("+LOANS+loanWhere()+") q",loanArgs(a,f,today));}
    public void journal(AppUserPrincipal a,UUID journal,UUID source,PostForm f,String hash,UUID period,String kind,UUID reverses,OffsetDateTime now){
        jdbc.update("""
            insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,reverses_id,direct_post)
            values(?,?,?,1,?,?,?,?,?,'TZS','DRAFT',?,?,?,?,?,?,true)
            """,journal,a.getSaccoId(),a.getStationId(),period,kind.startsWith("LOAN_CHARGE_")?kind:"LOAN_RECORD_"+kind,source.toString(),f.getRequestKey(),hash,f.getEvidence().strip(),f.getNotes().strip(),f.getEffectiveDate(),a.getMemberId(),now,reverses);
    }
    public void lines(AppUserPrincipal a,UUID journal,List<Object[]> amounts){var rows=new ArrayList<Object[]>();for(var line:amounts)rows.add(new Object[]{UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),line[0],line[1],line[2]});jdbc.batchUpdate("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,?,?)",rows);}
    public void postJournal(AppUserPrincipal a,UUID journal,OffsetDateTime now){jdbc.update("insert into accounting_outbox(id,journal_id,sacco_id,station_id,event_type,created_at) values(?,?,?,?,?,?)",UUID.randomUUID(),journal,a.getSaccoId(),a.getStationId(),"LOAN_RECORD_POSTED",now);jdbc.update("update gl_journal set state='POSTED',posted_at=? where id=?",now,journal);}
    public List<JournalLine> journalLines(AppUserPrincipal a,UUID journal){return jdbc.query("select g.code||' - '||g.name account,z.debit,z.credit from gl_journal_line z join gl_account g on g.id=z.account_id where z.journal_id=? and z.sacco_id=? and z.station_id=? order by z.debit desc,z.id limit 10",(r,n)->new JournalLine(r.getString(1),r.getBigDecimal(2),r.getBigDecimal(3)),journal,a.getSaccoId(),a.getStationId());}
    public List<Object[]> reversedLines(UUID journal){return jdbc.query("select account_id,credit,debit from gl_journal_line where journal_id=? order by account_id",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBigDecimal(3)},journal);}
    public UUID postingInterestAccount(UUID id){return jdbc.queryForObject("select interest_account_id from accountant_loan_post where id=?",UUID.class,id);}
    public UUID principalAccount(UUID loan){return jdbc.queryForObject("select principal_account_id from accountant_loan_post where loan_id=? and kind='DISBURSEMENT'",UUID.class,loan);}
    private LoanRow loan(ResultSet r,int n)throws SQLException{return new LoanRow(r.getObject("id",UUID.class),r.getString("loan_id"),r.getObject("client_id",UUID.class),r.getString("full_name"),r.getString("member_no"),r.getString("product_name"),r.getObject("application_date",LocalDate.class),r.getBigDecimal("requested_principal"),r.getBigDecimal("amount"),r.getString("status"),r.getBigDecimal("outstanding"),r.getBigDecimal("due_interest"),r.getBigDecimal("arrears"),r.getBigDecimal("future_interest"),r.getBigDecimal("principal_paid"),r.getBigDecimal("interest_paid"),r.getBoolean("topup_eligible"),r.getBigDecimal("charges"));}
    private Posting posting(ResultSet r,int n)throws SQLException{return new Posting(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getString("loan_number"),r.getString("full_name"),r.getString("member_no"),r.getString("kind"),r.getObject("effective_date",LocalDate.class),r.getString("reference"),r.getBigDecimal("cash_amount"),r.getBigDecimal("principal_amount"),r.getBigDecimal("interest_amount"),r.getString("money_account_name"),r.getString("principal_account_name"),r.getString("interest_account_name"),r.getString("evidence"),r.getString("notes"),r.getObject("actor_id",UUID.class),r.getString("actor_name"),r.getObject("recorded_at",OffsetDateTime.class),r.getObject("journal_id",UUID.class),r.getObject("transaction_id",UUID.class),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class),r.getBigDecimal("principal_balance"),r.getString("product_name"));}

    public boolean recorded(AppUserPrincipal actor,UUID loan) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accountant_loan_record where loan_id=? and sacco_id=? and station_id=?)",Boolean.class,loan,actor.getSaccoId(),actor.getStationId()));
    }
    public boolean availableTeller(AppUserPrincipal actor,List<UUID> ids) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from members where id=any(string_to_array(?,',')::uuid[]) and sacco_id=? and station_id=? and status='ACTIVE' and coalesce(staff_access_status,'ACTIVE')='ACTIVE')",Boolean.class,ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")),actor.getSaccoId(),actor.getStationId()));
    }

    public TopUpQuote topUpQuote(AppUserPrincipal a,UUID source,LocalDate date) {
        return jdbc.query("""
            select l.loan_application_id,l.loan_id,l.applicant_member_id,m.full_name,l.principal-l.principal_paid principal,l.next_sequence,
              coalesce(sum(loan_interest_due(i.id,?)),0) interest,
              coalesce(sum(greatest(0,i.interest-i.interest_paid-i.interest_excluded-i.interest_waived-loan_interest_due(i.id,?))),0) excluded
            from accountant_loan_record r join loan_applications b on b.id=r.loan_id
            join loan_ledgers l on l.loan_application_id=b.id join members m on m.id=b.applicant_member_id
            join loan_ledger_installments i on i.loan_application_id=b.id
            join accountant_loan_post p on p.loan_id=b.id and p.kind='DISBURSEMENT' and p.state='POSTED'
            join gl_journal j on j.id=p.journal_id and j.state='POSTED'
            join loan_adjustment_balance ab on ab.loan_id=b.id
            where b.id=? and r.sacco_id=? and r.station_id=? and b.sacco_id=r.sacco_id and b.station_id=r.station_id
             and l.sacco_id=r.sacco_id and l.station_id=r.station_id and b.status='DISBURSED'
             and l.disbursement_date<=? and (l.last_payment_date is null or l.last_payment_date<=?)
             and ab.allowance=0 and ab.written_off=0 and (ab.last_date is null or ab.last_date<=?)
             and (coalesce((b.policy_snapshot->>'applicationFee')::numeric,0)=0 or exists(select 1 from loan_charge_contract where loan_id=b.id))
            group by l.loan_application_id,m.full_name
            """,(r,n)->new TopUpQuote(source,r.getString("loan_id"),r.getObject("applicant_member_id",UUID.class),r.getString("full_name"),date,
                r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("excluded"),r.getLong("next_sequence")),
            date,date,source,a.getSaccoId(),a.getStationId(),date,date,date).stream().findFirst().orElseThrow(()->new IllegalArgumentException("recording.error.topUpUnavailable"));
    }
    public boolean topUpEnabled(String institution){return !Boolean.FALSE.equals(jdbc.query("select loan_top_up_enabled from sacco_settings where sacco_id=?",(r,n)->r.getBoolean(1),institution).stream().findFirst().orElse(true));}
    public boolean pendingTopUp(UUID source,UUID excluded){return Boolean.TRUE.equals(jdbc.queryForObject("""
        select exists(select 1 from loan_applications where top_up_source_loan_id=? and (?::uuid is null or id<>?)
        and status not in ('MANAGER_REJECTED','LOAN_OFFICER_REJECTED','CHAIRPERSON_REJECTED','BOARD_REJECTED','CREDIT_COMMITTEE_REJECTED','ACCOUNTANT_REJECTED','REJECTED','DISBURSEMENT_REVERSED','PAID'))
        """,Boolean.class,source,excluded,excluded));}
    public void lockTopUpPair(AppUserPrincipal a,UUID source,UUID loan){
        var ids=List.of(source,loan).stream().sorted().toList();
        if(lockReceiptLoans(a,ids,null).size()!=2)throw new org.springframework.security.access.AccessDeniedException("Loan unavailable");
        jdbc.queryForList("select loan_application_id from loan_ledgers where loan_application_id in (?,?) order by loan_application_id for update",ids.get(0),ids.get(1));
    }
    public Optional<UUID> topUpSource(AppUserPrincipal a,UUID loan){return jdbc.query("select a.top_up_source_loan_id from accountant_loan_record r join loan_applications a on a.id=r.loan_id where r.loan_id=? and r.sacco_id=? and r.station_id=? and a.top_up_source_loan_id is not null",(r,n)->r.getObject(1,UUID.class),loan,a.getSaccoId(),a.getStationId()).stream().findFirst();}
    public Optional<TopUpPosting> topUp(AppUserPrincipal a,UUID loan){return topUpRows(a,"t.loan_id=? and t.reverses_id is null",loan).stream().findFirst();}
    public Optional<TopUpPosting> sourceTopUp(AppUserPrincipal a,UUID loan){return topUpRows(a,"t.source_id=? and t.reverses_id is null and not exists(select 1 from accountant_loan_topup z where z.reverses_id=t.id)",loan).stream().findFirst();}
    public Optional<TopUpPosting> transactionTopUp(AppUserPrincipal a,UUID transaction){return topUpRows(a,"t.id=(select coalesce(e.reverses_id,e.id) from accountant_loan_topup e where e.transaction_id=?)",transaction).stream().findFirst();}
    public Optional<TopUpPosting> topUpRequest(AppUserPrincipal a,UUID key,String hash){
        var rows=jdbc.queryForList("select loan_id,payload_hash,actor_id from accountant_loan_topup where sacco_id=? and station_id=? and request_key=?",a.getSaccoId(),a.getStationId(),key);
        if(rows.isEmpty())return Optional.empty();var r=rows.getFirst();if(!hash.equals(r.get("payload_hash")) || !a.getMemberId().equals(r.get("actor_id")))throw new IllegalArgumentException("recording.error.retry");
        return topUpRows(a,"t.request_key=?",key).stream().findFirst();
    }
    private List<TopUpPosting> topUpRows(AppUserPrincipal a,String condition,UUID id){return jdbc.query("""
        select t.*,n.loan_id new_number,o.loan_id source_number,(select z.id from accountant_loan_topup z where z.reverses_id=t.id) reversed_by
        from accountant_loan_topup t join loan_applications n on n.id=t.loan_id join loan_applications o on o.id=t.source_id
        where t.sacco_id=? and t.station_id=? and
        """+condition+" order by t.recorded_at,t.id limit 1",(r,n)->new TopUpPosting(r.getObject("id",UUID.class),r.getObject("loan_id",UUID.class),r.getObject("source_id",UUID.class),r.getString("new_number"),r.getString("source_number"),r.getObject("effective_date",LocalDate.class),r.getBigDecimal("principal"),r.getBigDecimal("interest"),r.getBigDecimal("excluded_interest"),r.getBigDecimal("cash"),r.getObject("journal_id",UUID.class),r.getObject("transaction_id",UUID.class),r.getObject("reverses_id",UUID.class),r.getObject("reversed_by",UUID.class),r.getObject("actor_id",UUID.class),r.getString("evidence"),r.getString("reason"),r.getBigDecimal("settled_charges"),r.getBigDecimal("deducted_charges")),a.getSaccoId(),a.getStationId(),id);}
    public void insertTopUp(AppUserPrincipal a,UUID id,UUID source,UUID posting,UUID transaction,UUID journal,PostForm f,String hash,TopUpQuote q,UUID reverses,OffsetDateTime now){
        insertTopUp(a,id,source,posting,transaction,journal,f,hash,q,reverses,now,new BigDecimal("0.00"));
    }
    public void insertTopUp(AppUserPrincipal a,UUID id,UUID source,UUID posting,UUID transaction,UUID journal,PostForm f,String hash,TopUpQuote q,UUID reverses,OffsetDateTime now,BigDecimal deductions){
        jdbc.update("""
            insert into accountant_loan_topup(id,loan_id,source_id,posting_id,sacco_id,station_id,request_key,payload_hash,effective_date,
             principal,interest,excluded_interest,cash,source_sequence,transaction_id,journal_id,reverses_id,actor_id,evidence,reason,recorded_at,settled_charges,deducted_charges)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,f.getLoanId(),source,posting,a.getSaccoId(),a.getStationId(),f.getRequestKey(),hash,f.getEffectiveDate(),q.principal(),q.interest(),q.excludedInterest(),f.getAmount().subtract(q.total()).subtract(deductions),q.sequence(),transaction,journal,reverses,a.getMemberId(),f.getEvidence(),f.getNotes(),now,q.charges(),deductions);
    }
    public void topUpJournal(AppUserPrincipal a,UUID id,UUID journal,PostForm f,String hash,UUID period,UUID reverses,OffsetDateTime now){
        jdbc.update("""
            insert into gl_journal(id,sacco_id,station_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,reverses_id,direct_post)
            values(?,?,?,1,?,?,?,?,?,'TZS','DRAFT',?,?,?,?,?,?,true)
            """,journal,a.getSaccoId(),a.getStationId(),period,reverses==null?"LOAN_TOPUP":"LOAN_TOPUP_UNDO",id.toString(),f.getRequestKey(),hash,f.getEvidence(),f.getNotes(),f.getEffectiveDate(),a.getMemberId(),now,reverses);
    }
    public long topUpCorrectionSequence(AppUserPrincipal a,TopUpPosting original){
        var rows=jdbc.queryForList("""
            select s.next_sequence from loan_ledgers s join loan_ledgers n on n.loan_application_id=?
            join loan_repayment_transactions t on t.id=?
            join loan_applications l on l.id=n.loan_application_id
            where s.loan_application_id=? and s.sacco_id=? and s.station_id=? and s.next_sequence=t.sequence+1
             and n.next_sequence=1 and n.principal_paid=0 and n.interest_paid=0 and l.status='DISBURSED'
             and not exists(select 1 from loan_adjustment_request where loan_id in (?,?))
            """,original.loanId(),original.transactionId(),original.sourceId(),a.getSaccoId(),a.getStationId(),original.loanId(),original.sourceId());
        if(rows.isEmpty() || pendingTopUp(original.loanId(),null))throw new IllegalArgumentException("recording.error.topUpDependencies");
        return ((Number)rows.getFirst().get("next_sequence")).longValue();
    }
}
