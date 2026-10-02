package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ReconciliationRepository {
    private final JdbcTemplate jdbc;
    public boolean workspaceActive(String institution,String branch) {return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from registered_saccos i join sacco_stations b on b.sacco_id=i.sacco_id where i.sacco_id=? and b.station_id=? and i.active and b.active and b.access_status='ACTIVE')",Boolean.class,institution,branch));}
    public boolean statementBalanceExists(String institution,String branch,UUID account,LocalDate date,BigDecimal amount) {return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from reconciliation_statement where sacco_id=? and station_id=? and account_id=? and ends_on=? and closing_balance=?)",Boolean.class,institution,branch,account,date,amount));}
    public Optional<UUID> staff(String institution,String branch,String staffNo) {return jdbc.query("select id from members where sacco_id=? and station_id=? and staff_no=? and status='ACTIVE' and staff_access_status='ACTIVE'",(r,n)->uuid(r,"id"),institution,branch,staffNo).stream().findFirst();}
    public boolean openEvidencePeriod(String institution,LocalDate date) {var states=jdbc.query("select state from accounting_period where sacco_id=? and ? between starts_on and ends_on for share",(r,n)->r.getString(1),institution,date);return states.size()==1&&"OPEN".equals(states.getFirst());}
    public List<Period> evidencePeriods(String institution,LocalDate from,LocalDate through) {return jdbc.query("select * from accounting_period where sacco_id=? and starts_on<=? and ends_on>=? order by starts_on,id limit 370 for share",(r,n)->new Period(uuid(r,"id"),date(r,"starts_on"),date(r,"ends_on"),r.getString("state")),institution,through,from);}
    static UUID uuid(ResultSet r,String key) throws SQLException {return r.getObject(key,UUID.class);}
    static LocalDate date(ResultSet r,String key) throws SQLException {return r.getObject(key,LocalDate.class);}
    static OffsetDateTime time(ResultSet r,String key) throws SQLException {return r.getObject(key,OffsetDateTime.class);}
    private static final RowMapper<Statement> STATEMENT=(r,n)->new Statement(uuid(r,"id"),uuid(r,"account_id"),r.getString("code"),date(r,"starts_on"),date(r,"ends_on"),r.getBigDecimal("opening_balance"),r.getBigDecimal("closing_balance"),r.getString("filename"),r.getString("file_checksum"),uuid(r,"maker_id"),r.getString("evidence"),time(r,"imported_at"));
    private static final RowMapper<Match> MATCH=(r,n)->new Match(uuid(r,"id"),r.getString("kind"),r.getString("state"),uuid(r,"maker_id"),uuid(r,"checker_id"),r.getString("evidence"),uuid(r,"reverses_id"));
    private static final RowMapper<CloseReview> CLOSE=(r,n)->new CloseReview(uuid(r,"id"),uuid(r,"period_id"),r.getString("station_id"),r.getInt("version"),r.getString("state"),r.getString("snapshot_json"),r.getString("checksum"),uuid(r,"maker_id"),uuid(r,"checker_id"),r.getString("evidence"),time(r,"recorded_at"));
    public void lock(String institution,UUID account) {jdbc.queryForObject("select id from gl_account where sacco_id=? and id=? for update",UUID.class,institution,account);}
    public void requestLock(String institution,String branch,UUID key) {jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"RECON/"+institution+"/"+branch+"/"+key);}
    public List<Format> formats(String institution) {return jdbc.query("select * from reconciliation_format where sacco_id=? order by name,id limit 100",(r,n)->new Format(uuid(r,"id"),r.getString("name"),r.getString("version"),uuid(r,"maker_id"),uuid(r,"checker_id"),r.getString("evidence")),institution);}
    public Optional<Format> format(String institution,UUID id) {return jdbc.query("select * from reconciliation_format where sacco_id=? and id=?",(r,n)->new Format(uuid(r,"id"),r.getString("name"),r.getString("version"),uuid(r,"maker_id"),uuid(r,"checker_id"),r.getString("evidence")),institution,id).stream().findFirst();}
    public UUID proposeFormat(String institution,String name,UUID maker,String evidence,OffsetDateTime now) {UUID id=UUID.randomUUID();jdbc.update("insert into reconciliation_format(id,sacco_id,name,version,maker_id,evidence) values(?,?,?,'TZS_CSV_V1',?,?)",id,institution,name,maker,evidence);return id;}
    public void approveFormat(UUID id,UUID checker,String evidence,OffsetDateTime now) {jdbc.update("update reconciliation_format set checker_id=?,approval_evidence=?,approved_at=? where id=? and checker_id is null",checker,evidence,now,id);}
    public List<Statement> statements(String institution,String branch,int offset) {return jdbc.query("select s.*,a.code from reconciliation_statement s join gl_account a on a.id=s.account_id where s.sacco_id=? and s.station_id=? order by imported_at desc,s.id limit 26 offset ?",STATEMENT,institution,branch,offset);}
    public Optional<Statement> statement(String institution,String branch,UUID id) {return jdbc.query("select s.*,a.code from reconciliation_statement s join gl_account a on a.id=s.account_id where s.sacco_id=? and s.station_id=? and s.id=?",STATEMENT,institution,branch,id).stream().findFirst();}
    public Optional<Map<String,Object>> byRequest(String institution,String branch,UUID key) {return jdbc.queryForList("select id,payload_hash,maker_id from reconciliation_statement where sacco_id=? and station_id=? and request_key=?",institution,branch,key).stream().findFirst();}
    public Optional<UUID> duplicateFile(String institution,String branch,StatementCommand c,String checksum) {return jdbc.query("select id from reconciliation_statement where sacco_id=? and station_id=? and account_id=? and file_checksum=? and starts_on=? and ends_on=?",(r,n)->uuid(r,"id"),institution,branch,c.account(),checksum,c.from(),c.through()).stream().findFirst();}
    public UUID insertStatement(String institution,String branch,UUID maker,StatementCommand c,String hash,String checksum,List<ImportedRow> rows,OffsetDateTime now) {
        UUID id=UUID.randomUUID();jdbc.update("insert into reconciliation_statement(id,sacco_id,station_id,account_id,format_id,request_key,payload_hash,starts_on,ends_on,opening_balance,closing_balance,filename,file_checksum,evidence,maker_id,imported_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,institution,branch,c.account(),c.format(),c.key(),hash,c.from(),c.through(),c.opening(),c.closing(),c.filename(),checksum,c.evidence(),maker,now);
        jdbc.batchUpdate("insert into reconciliation_statement_line(id,statement_id,row_number,effective_date,reference,amount,kind,duplicate) values(?,?,?,?,?,?,?,?)",rows,100,(ps,r)->{ps.setObject(1,UUID.randomUUID());ps.setObject(2,id);ps.setInt(3,r.number());ps.setObject(4,r.date());ps.setString(5,r.reference());ps.setBigDecimal(6,r.amount());ps.setString(7,r.kind());ps.setBoolean(8,r.duplicate());});return id;
    }
    public List<StatementRow> rows(UUID statement,int offset) {
        return jdbc.query("""
          select l.*,coalesce((select sum(a.amount) from reconciliation_active_allocation a where a.statement_line_id=l.id),0) matched,
          exists(select 1 from reconciliation_allocation x join reconciliation_match_decision xd on xd.match_id=x.match_id and xd.decision='APPROVED'
          join gl_journal_line jl on jl.id=x.journal_line_id
          join gl_journal j on j.id=jl.journal_id where x.statement_line_id=l.id and (j.reverses_id is not null
          or exists(select 1 from gl_journal r where r.reverses_id=j.id and r.state='POSTED'))) reversed
          from reconciliation_statement_line l where statement_id=? order by row_number limit 26 offset ?
          """,(r,n)->{BigDecimal amount=r.getBigDecimal("amount"),matched=r.getBigDecimal("matched");boolean duplicate=r.getBoolean("duplicate");return new StatementRow(uuid(r,"id"),r.getInt("row_number"),date(r,"effective_date"),r.getString("reference"),amount,r.getString("kind"),duplicate,matched,r.getBoolean("reversed")?"REVERSED":duplicate?"DUPLICATE":matched.signum()==0?"UNMATCHED":matched.compareTo(amount.abs())==0?"MATCHED":"PARTIAL");},statement,offset);
    }
    public List<Candidate> candidates(String institution,String branch,UUID account,LocalDate from,LocalDate through,int offset) {
        return jdbc.query("""
          select l.id,l.journal_id,j.effective_date,j.source_reference,j.source_type,l.debit-l.credit amount,
          coalesce((select sum(a.amount) from reconciliation_active_allocation a where a.journal_line_id=l.id),0) matched,
          (j.reverses_id is not null or exists(select 1 from gl_journal r where r.reverses_id=j.id and r.state='POSTED')) reversed
          from gl_journal_line l join gl_journal j on j.id=l.journal_id
          where j.sacco_id=? and j.station_id=? and l.account_id=? and j.state='POSTED' and j.effective_date between ? and ?
          order by j.effective_date,j.id,l.id limit 26 offset ?
          """,(r,n)->new Candidate(uuid(r,"id"),uuid(r,"journal_id"),date(r,"effective_date"),r.getString("source_reference"),r.getString("source_type"),r.getBigDecimal("amount"),r.getBigDecimal("matched"),r.getBoolean("reversed")),institution,branch,account,from,through,offset);
    }
    public Optional<Map<String,Object>> line(String institution,String branch,UUID id) {return jdbc.queryForList("select l.*,s.account_id from reconciliation_statement_line l join reconciliation_statement s on s.id=l.statement_id where s.sacco_id=? and s.station_id=? and l.id=?",institution,branch,id).stream().findFirst();}
    public Optional<Map<String,Object>> journalLine(String institution,String branch,UUID id) {return jdbc.queryForList("select l.*,j.effective_date,j.source_reference,j.source_type,j.reverses_id,exists(select 1 from gl_journal r where r.reverses_id=j.id and r.state='POSTED') original_reversed from gl_journal_line l join gl_journal j on j.id=l.journal_id where j.sacco_id=? and j.station_id=? and j.state='POSTED' and l.id=?",institution,branch,id).stream().findFirst();}
    /** Matching uses bounded set projections rather than one database round trip per allocation. */
    public Map<UUID,Map<String,Object>> matchingLines(String institution,String branch,Collection<UUID> ids,boolean statement) {
        if(ids.isEmpty()||ids.size()>100)throw new IllegalArgumentException("Allocation ID limit");
        String sql=statement?"select l.*,s.account_id,coalesce((select sum(a.amount) from reconciliation_active_allocation a where a.statement_line_id=l.id),0) allocated from reconciliation_statement_line l join reconciliation_statement s on s.id=l.statement_id where s.sacco_id=? and s.station_id=? and l.id in (":
            "select l.*,j.effective_date,j.source_reference,j.source_type,j.reverses_id,exists(select 1 from gl_journal r where r.reverses_id=j.id and r.state='POSTED') original_reversed,coalesce((select sum(a.amount) from reconciliation_active_allocation a where a.journal_line_id=l.id),0) allocated from gl_journal_line l join gl_journal j on j.id=l.journal_id where j.sacco_id=? and j.station_id=? and j.state='POSTED' and l.id in (";
        var args=new ArrayList<Object>();args.add(institution);args.add(branch);args.addAll(ids);var result=new HashMap<UUID,Map<String,Object>>();
        for(var row:jdbc.queryForList(sql+String.join(",",Collections.nCopies(ids.size(),"?"))+")",args.toArray()))result.put((UUID)row.get("id"),row);return result;
    }
    public boolean openEvidenceDates(String institution,Collection<LocalDate> dates) {
        if(dates.isEmpty()||dates.size()>100)throw new IllegalArgumentException("Allocation date limit");var args=new ArrayList<Object>();args.add(institution);args.addAll(dates);
        var periods=jdbc.query("select p.* from accounting_period p where sacco_id=? and exists(select 1 from(values "+String.join(",",Collections.nCopies(dates.size(),"(?::date)"))+") x(d) where x.d between p.starts_on and p.ends_on) order by p.starts_on,p.id limit 101 for share",(r,n)->new Period(uuid(r,"id"),date(r,"starts_on"),date(r,"ends_on"),r.getString("state")),args.toArray());
        return periods.size()<=100&&dates.stream().allMatch(d->periods.stream().filter(p->!d.isBefore(p.from())&&!d.isAfter(p.through())&&"OPEN".equals(p.state())).count()==1);
    }
    public BigDecimal allocated(String column,UUID id) {if(!Set.of("statement_line_id","journal_line_id").contains(column))throw new IllegalArgumentException();return jdbc.queryForObject("select coalesce(sum(amount),0) from reconciliation_active_allocation where "+column+"=?",BigDecimal.class,id);}
    public UUID match(String institution,String branch,UUID account,String kind,UUID maker,String evidence,UUID reverses,List<Allocation> parts,OffsetDateTime now) {
        UUID id=UUID.randomUUID();jdbc.update("insert into reconciliation_match(id,sacco_id,station_id,account_id,kind,maker_id,evidence,created_at,reverses_id) values(?,?,?,?,?,?,?,?,?)",id,institution,branch,account,kind,maker,evidence,now,reverses);
        if(!parts.isEmpty())jdbc.batchUpdate("insert into reconciliation_allocation(match_id,statement_line_id,journal_line_id,amount) values(?,?,?,?)",parts,100,(ps,p)->{ps.setObject(1,id);ps.setObject(2,p.statementLine());ps.setObject(3,p.journalLine());ps.setBigDecimal(4,p.amount());});return id;
    }
    public Optional<Match> match(String institution,String branch,UUID id) {return jdbc.query("select m.*,coalesce(d.decision,'DRAFT') state,d.checker_id from reconciliation_match m left join reconciliation_match_decision d on d.match_id=m.id where m.sacco_id=? and m.station_id=? and m.id=?",MATCH,institution,branch,id).stream().findFirst();}
    public UUID matchAccount(UUID id) {return jdbc.queryForObject("select account_id from reconciliation_match where id=?",UUID.class,id);}
    public List<Allocation> allocations(UUID id) {return jdbc.query("select * from reconciliation_allocation where match_id=? order by statement_line_id,journal_line_id",(r,n)->new Allocation(uuid(r,"statement_line_id"),uuid(r,"journal_line_id"),r.getBigDecimal("amount")),id);}
    public void decideMatch(UUID id,UUID checker,boolean approved,String evidence,OffsetDateTime now) {jdbc.update("insert into reconciliation_match_decision(match_id,checker_id,decision,evidence,decided_at) values(?,?,?,?,?)",id,checker,approved?"APPROVED":"REJECTED",evidence,now);}
    public List<Match> matches(String institution,String branch,int offset) {return jdbc.query("select m.*,coalesce(d.decision,'DRAFT') state,d.checker_id from reconciliation_match m left join reconciliation_match_decision d on d.match_id=m.id where m.sacco_id=? and m.station_id=? order by created_at desc,m.id limit 26 offset ?",MATCH,institution,branch,offset);}
    public UUID exception(String institution,String branch,UUID line,String kind,UUID assigned,UUID maker,String evidence,OffsetDateTime now) {UUID id=UUID.randomUUID();jdbc.update("insert into reconciliation_exception(id,sacco_id,station_id,statement_line_id,kind,assigned_to,maker_id,evidence,created_at) values(?,?,?,?,?,?,?,?,?)",id,institution,branch,line,kind,assigned,maker,evidence,now);return id;}
    public List<ExceptionRecord> exceptions(String institution,String branch,int offset) {return jdbc.query("select e.*,d.checker_id,case when d.exception_id is null then 'OPEN' else 'REVIEWED_DIFFERENCE' end state from reconciliation_exception e left join reconciliation_exception_decision d on d.exception_id=e.id where e.sacco_id=? and e.station_id=? order by created_at desc,e.id limit 26 offset ?",(r,n)->new ExceptionRecord(uuid(r,"id"),uuid(r,"statement_line_id"),r.getString("kind"),uuid(r,"assigned_to"),r.getString("state"),uuid(r,"maker_id"),uuid(r,"checker_id"),r.getString("evidence")),institution,branch,offset);}
    public Optional<Map<String,Object>> exception(String institution,String branch,UUID id) {return jdbc.queryForList("select * from reconciliation_exception where sacco_id=? and station_id=? and id=?",institution,branch,id).stream().findFirst();}
    public void reviewException(UUID id,UUID checker,String evidence,OffsetDateTime now) {jdbc.update("insert into reconciliation_exception_decision(exception_id,checker_id,evidence,decided_at) values(?,?,?,?)",id,checker,evidence,now);}
    public BigDecimal balance(String institution,String branch,UUID account,LocalDate asOf) {return jdbc.queryForObject("select coalesce(sum(l.debit-l.credit),0) from gl_journal_line l join gl_journal j on j.id=l.journal_id where j.sacco_id=? and j.station_id=? and l.account_id=? and j.state='POSTED' and j.effective_date<=?",BigDecimal.class,institution,branch,account,asOf);}
    public List<AccountBalance> balances(String institution,String branch,LocalDate asOf,int offset) {return jdbc.query("""
          select a.id,a.code,a.name,a.purpose,coalesce(m.balance,0) balance from gl_account a left join
          (select l.account_id,sum(l.debit-l.credit) balance from gl_journal j join gl_journal_line l on l.journal_id=j.id
          where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.effective_date<=? group by l.account_id) m on m.account_id=a.id
          where a.sacco_id=? and a.kind<>'HEADING' order by a.code,a.id limit 26 offset ?
          """,(r,n)->new AccountBalance(uuid(r,"id"),r.getString("code"),r.getString("name"),r.getString("purpose"),r.getBigDecimal("balance")),institution,branch,asOf,institution,offset);}
    public Map<String,Object> account(String institution,UUID account) {var result=jdbc.queryForList("select * from gl_account where sacco_id=? and id=? and kind<>'HEADING'",institution,account);if(result.size()!=1)throw new org.springframework.security.access.AccessDeniedException("Reconciliation account unavailable");return result.getFirst();}
    public BigDecimal loanBalance(String institution,String branch,LocalDate date) {return jdbc.queryForObject("select coalesce(sum(principal),0)-(select coalesce(sum(case when kind='PAYMENT' then principal_amount else -principal_amount end),0) from loan_repayment_transactions where sacco_id=? and station_id=? and payment_date<=?) from loan_ledgers where sacco_id=? and station_id=? and disbursement_date<=?",BigDecimal.class,institution,branch,date,institution,branch,date);}
    public boolean hasExpandedLoanMovements(String institution,String branch,LocalDate date) {return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_journal where sacco_id=? and station_id=? and state='POSTED' and effective_date<=? and source_type in('SETTLEMENT','TOP_UP','WRITE_OFF','RECOVERY','ADVANCE'))",Boolean.class,institution,branch,date));}
    public UUID certificate(String institution,String branch,UUID account,LocalDate asOf,String kind,BigDecimal source,BigDecimal ledger,UUID maker,String evidence,OffsetDateTime now) {UUID id=UUID.randomUUID();jdbc.update("insert into reconciliation_certificate(id,sacco_id,station_id,account_id,as_of,kind,source_balance,ledger_balance,difference,evidence,maker_id,created_at) values(?,?,?,?,?,?,?,?,?,?,?,?)",id,institution,branch,account,asOf,kind,source,ledger,source.subtract(ledger),evidence,maker,now);return id;}
    private static final RowMapper<Certificate> CERT=(r,n)->new Certificate(uuid(r,"id"),uuid(r,"account_id"),r.getString("code"),date(r,"as_of"),r.getString("kind"),r.getBigDecimal("source_balance"),r.getBigDecimal("ledger_balance"),r.getBigDecimal("difference"),r.getString("state"),uuid(r,"maker_id"),uuid(r,"checker_id"),r.getString("evidence"));
    public List<Certificate> certificates(String institution,String branch,int offset) {return jdbc.query("select c.*,a.code,d.checker_id,case when d.certificate_id is null then 'DRAFT' else 'REVIEWED' end state from reconciliation_certificate c join gl_account a on a.id=c.account_id left join reconciliation_certificate_decision d on d.certificate_id=c.id where c.sacco_id=? and c.station_id=? order by c.as_of desc,c.id limit 26 offset ?",CERT,institution,branch,offset);}
    public Optional<Certificate> certificate(String institution,String branch,UUID id) {return jdbc.query("select c.*,a.code,d.checker_id,case when d.certificate_id is null then 'DRAFT' else 'REVIEWED' end state from reconciliation_certificate c join gl_account a on a.id=c.account_id left join reconciliation_certificate_decision d on d.certificate_id=c.id where c.sacco_id=? and c.station_id=? and c.id=?",CERT,institution,branch,id).stream().findFirst();}
    public void reviewCertificate(UUID id,UUID checker,String evidence,OffsetDateTime now) {jdbc.update("insert into reconciliation_certificate_decision(certificate_id,checker_id,evidence,decided_at) values(?,?,?,?)",id,checker,evidence,now);}
    public List<Period> periods(String institution,int offset) {return jdbc.query("select * from accounting_period where sacco_id=? order by starts_on desc,id limit 26 offset ?",(r,n)->new Period(uuid(r,"id"),date(r,"starts_on"),date(r,"ends_on"),r.getString("state")),institution,offset);}
    public Optional<Period> period(String institution,UUID id,boolean lock) {return jdbc.query("select * from accounting_period where sacco_id=? and id=?"+(lock?" for update":""),(r,n)->new Period(uuid(r,"id"),date(r,"starts_on"),date(r,"ends_on"),r.getString("state")),institution,id).stream().findFirst();}
    public void lockPeriodForPublication(String institution,UUID id) {jdbc.queryForObject("select id from accounting_period where sacco_id=? and id=? for share",UUID.class,institution,id);}
    public void lockPriorPeriods(String institution,LocalDate through) {jdbc.query("select id from accounting_period where sacco_id=? and starts_on<=? order by starts_on,id for update",(r,n)->uuid(r,"id"),institution,through);}
    public List<CloseCheck> checks(String institution,String branch,Period p) {
        var result=new ArrayList<CloseCheck>();
        result.add(new CloseCheck("opening",count("select count(*) from gl_cutover_coverage c join gl_journal j on j.id=c.opening_journal_id where c.sacco_id=? and c.station_id=? and c.complete and j.state='POSTED'",institution,branch)==1?0:1));
        result.add(new CloseCheck("drafts",count("select count(*) from gl_journal where sacco_id=? and station_id=? and period_id=? and state<>'POSTED'",institution,branch,p.id())));
        result.add(new CloseCheck("unbalanced",count("select count(*) from (select j.id from gl_journal j join gl_journal_line l on l.journal_id=j.id where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.effective_date<=? group by j.id having sum(l.debit-l.credit)<>0) x",institution,branch,p.through())));
        result.add(new CloseCheck("exceptions",count("select count(*) from reconciliation_exception e join reconciliation_statement_line l on l.id=e.statement_line_id left join reconciliation_exception_decision d on d.exception_id=e.id where e.sacco_id=? and e.station_id=? and l.effective_date<=? and d.exception_id is null",institution,branch,p.through())));
        result.add(new CloseCheck("unmatched",count("""
          select count(*) from reconciliation_statement_line l join reconciliation_statement s on s.id=l.statement_id
          where s.sacco_id=? and s.station_id=? and l.effective_date<=?
          and abs(l.amount)<>coalesce((select sum(a.amount) from reconciliation_active_allocation a where a.statement_line_id=l.id),0)
          and not exists(select 1 from reconciliation_exception e join reconciliation_exception_decision d on d.exception_id=e.id where e.statement_line_id=l.id)
          """,institution,branch,p.through())));
        result.add(new CloseCheck("reversed",count("""
          select count(distinct sl.id) from reconciliation_statement_line sl join reconciliation_statement s on s.id=sl.statement_id
          join reconciliation_allocation a on a.statement_line_id=sl.id join reconciliation_match_decision ad on ad.match_id=a.match_id and ad.decision='APPROVED'
          join gl_journal_line jl on jl.id=a.journal_line_id
          join gl_journal r on r.reverses_id=jl.journal_id and r.state='POSTED' and r.effective_date<=?
          where s.sacco_id=? and s.station_id=? and sl.effective_date<=?
          and not exists(select 1 from reconciliation_exception e join reconciliation_exception_decision d on d.exception_id=e.id
          where e.statement_line_id=sl.id and e.sacco_id=s.sacco_id and e.station_id=s.station_id and e.kind='REVERSED' and d.decided_at>=r.posted_at)
          and not exists(select 1 from reconciliation_active_allocation x join reconciliation_match m on m.id=x.match_id
          join reconciliation_match_decision d on d.match_id=m.id where x.statement_line_id=sl.id and x.journal_line_id=jl.id
          and m.kind='REVERSAL' and d.decided_at>=r.posted_at)
          and not exists(select 1 from reconciliation_match m join reconciliation_match_decision d on d.match_id=m.id
          where m.reverses_id=a.match_id and d.decision='APPROVED' and d.decided_at>=r.posted_at)
          """,p.through(),institution,branch,p.through())));
        result.add(new CloseCheck("accountEvidence",count("""
          select count(*) from gl_account a where a.sacco_id=? and a.kind<>'HEADING'
          and (a.active or exists(select 1 from gl_journal_line hl join gl_journal hj on hj.id=hl.journal_id where hl.account_id=a.id and hj.station_id=? and hj.state='POSTED' and hj.effective_date<=?))
          and a.purpose in('CASH','BANK','MOBILE_MONEY','CLEARING','LOAN_PRINCIPAL','PAYABLE','FUNDING','SUSPENSE','INTERNAL_TRANSFER')
          and not exists(select 1 from reconciliation_certificate c join reconciliation_certificate_decision d on d.certificate_id=c.id
          where c.sacco_id=a.sacco_id and c.station_id=? and c.account_id=a.id and c.as_of=?
          and (a.purpose not in('LOAN_PRINCIPAL','PAYABLE','FUNDING') or c.kind=case a.purpose when 'LOAN_PRINCIPAL' then 'LOAN_CONTROL' when 'PAYABLE' then 'SUPPLIER' when 'FUNDING' then 'FUNDING' end)
          and c.ledger_balance=(select coalesce(sum(l.debit-l.credit),0) from gl_journal_line l join gl_journal j on j.id=l.journal_id
          where l.account_id=a.id and j.station_id=c.station_id and j.state='POSTED' and j.effective_date<=c.as_of))
          """,institution,branch,p.through(),branch,p.through())));
        result.add(new CloseCheck("legacy",count("select count(*) from loan_applications a where a.sacco_id=? and a.station_id=? and a.disbursement_date is not null and not exists(select 1 from loan_ledgers l where l.loan_application_id=a.id)",institution,branch)));
        return List.copyOf(result);
    }
    public Map<String,Object> snapshot(String institution,String branch,Period p) {
        var out=new LinkedHashMap<String,Object>();out.put("schema",1);out.put("period",p.id().toString());out.put("from",p.from().toString());out.put("through",p.through().toString());
        out.put("journals",jdbc.queryForMap("select count(*) posted_count,coalesce(sum(l.total),0) debit_total,max(j.posted_at)::text latest_posted from gl_journal j join lateral(select sum(debit) total from gl_journal_line where journal_id=j.id) l on true where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.effective_date<=?",institution,branch,p.through()));
        out.put("accounts",jdbc.queryForList("""
          select a.id::text,a.code,a.type,a.normal_balance,a.purpose,coalesce(m.opening,0) opening,
          coalesce(m.period_debit,0) period_debit,coalesce(m.period_credit,0) period_credit,coalesce(m.closing,0) closing
          from gl_account a left join(select l.account_id,
          sum(l.debit-l.credit) filter(where j.effective_date<? or j.source_type='OPENING') opening,
          sum(l.debit) filter(where j.effective_date>=? and j.source_type<>'OPENING') period_debit,sum(l.credit) filter(where j.effective_date>=? and j.source_type<>'OPENING') period_credit,
          sum(l.debit-l.credit) closing from gl_journal j join gl_journal_line l on l.journal_id=j.id
          where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.effective_date<=? group by l.account_id) m on m.account_id=a.id
          where a.sacco_id=? and a.kind<>'HEADING' order by a.code,a.id limit 1001
          """,p.from(),p.from(),p.from(),institution,branch,p.through(),institution));
        // A direct counterpart exists only when the journal has one money account. Multiple-money transfers are separately identified.
        out.put("cashMovements",jdbc.queryForList("""
          with money as(select l.journal_id,count(distinct l.account_id) lines,sum(l.debit-l.credit) net from gl_journal_line l join gl_account a on a.id=l.account_id
          join gl_journal j on j.id=l.journal_id where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.effective_date between ? and ?
          and j.source_type<>'OPENING' and a.purpose in('CASH','BANK','MOBILE_MONEY') group by l.journal_id)
          select a.id::text account_id,a.code,a.type,a.purpose,coalesce(sum(l.debit-l.credit),0) counterpart_movement,
          count(distinct j.id) journal_count,m.lines money_lines from gl_journal j join money m on m.journal_id=j.id
          join gl_journal_line l on l.journal_id=j.id join gl_account a on a.id=l.account_id
          where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.source_type<>'OPENING' and j.effective_date between ? and ?
          and a.purpose not in('CASH','BANK','MOBILE_MONEY') group by a.id,m.lines order by a.code,m.lines limit 1001
          """,institution,branch,p.from(),p.through(),institution,branch,p.from(),p.through()));
        out.put("cashTransfers",jdbc.queryForMap("""
          select count(*) filter(where x.money_accounts>1) journal_count,coalesce(sum(x.net) filter(where x.money_accounts>1),0) net_cash_movement,
          count(*) filter(where x.money_legs>0 and (x.nonmoney_legs>1 or (x.money_legs>1 and x.nonmoney_legs>0))) ambiguous_journals,
          count(*) filter(where x.money_legs>0 and (x.nonmoney_legs>1 or (x.money_legs>1 and x.nonmoney_legs>0))) noncash_pairs_possible
          from gl_journal j join lateral
          (select count(distinct l.account_id) filter(where a.purpose in('CASH','BANK','MOBILE_MONEY')) money_accounts,
          count(*) filter(where a.purpose in('CASH','BANK','MOBILE_MONEY')) money_legs,
          count(*) filter(where a.purpose not in('CASH','BANK','MOBILE_MONEY')) nonmoney_legs,
          sum(l.debit-l.credit) filter(where a.purpose in('CASH','BANK','MOBILE_MONEY')) net
          from gl_journal_line l join gl_account a on a.id=l.account_id where l.journal_id=j.id) x on true
          where j.sacco_id=? and j.station_id=? and j.state='POSTED' and j.source_type<>'OPENING' and j.effective_date between ? and ?
          """,institution,branch,p.from(),p.through()));
        out.put("accountingPolicy",jdbc.queryForMap("select p.id::text,p.policy_version,p.authoritative_ledger,p.effective_from::text,p.opening_date::text from accounting_policies p join accounting_period ap on ap.policy_id=p.id where ap.id=?",p.id()));
        out.put("sourcePolicyVersions",jdbc.queryForList("select distinct policy_id::text,policy_version from gl_journal where sacco_id=? and station_id=? and state='POSTED' and effective_date<=? order by policy_version,policy_id::text limit 1001",institution,branch,p.through()));
        out.put("reviewedOpening",opening(institution,branch).orElse(null));
        out.put("checks",checks(institution,branch,p));out.put("certificates",jdbc.queryForList("select c.id::text,c.account_id::text,c.difference::text,d.evidence,d.checker_id::text from reconciliation_certificate c join reconciliation_certificate_decision d on d.certificate_id=c.id where c.sacco_id=? and c.station_id=? and c.as_of=? order by c.id limit 1001",institution,branch,p.through()));
        out.put("statementEvidence",jdbc.queryForList("select id::text,file_checksum,ends_on::text,closing_balance::text from reconciliation_statement where sacco_id=? and station_id=? and ends_on<=? order by id limit 1001",institution,branch,p.through()));
        out.put("matchingEvidence",jdbc.queryForMap("select count(*) reviewed_matches,max(d.decided_at)::text latest_review from reconciliation_match m join reconciliation_match_decision d on d.match_id=m.id where m.sacco_id=? and m.station_id=?",institution,branch));
        out.put("retainedDifferences",jdbc.queryForList("select e.id::text,e.kind,e.evidence,d.evidence review_evidence from reconciliation_exception e join reconciliation_statement_line l on l.id=e.statement_line_id join reconciliation_exception_decision d on d.exception_id=e.id where e.sacco_id=? and e.station_id=? and l.effective_date<=? order by e.id limit 1001",institution,branch,p.through()));return out;
    }
    public UUID closeReview(String institution,String branch,Period p,String action,String snapshot,String checksum,UUID maker,String evidence,OffsetDateTime now) {UUID id=UUID.randomUUID();int version=jdbc.queryForObject("select coalesce(max(version),0)+1 from accounting_close_review where sacco_id=? and station_id=? and period_id=?",Integer.class,institution,branch,p.id());jdbc.update("insert into accounting_close_review(id,sacco_id,station_id,period_id,version,action,snapshot_json,checksum,evidence,maker_id,recorded_at) values(?,?,?,?,?,?,?,?,?,?,?)",id,institution,branch,p.id(),version,action,snapshot,checksum,evidence,maker,now);return id;}
    public List<CloseReview> closes(String institution,String branch,int offset) {return jdbc.query("select r.*,d.checker_id,case when d.review_id is null then 'DRAFT_'||r.action else 'APPROVED_'||r.action end state from accounting_close_review r left join accounting_close_decision d on d.review_id=r.id where r.sacco_id=? and r.station_id=? order by recorded_at desc,r.id limit 26 offset ?",CLOSE,institution,branch,offset);}
    public Optional<CloseReview> close(String institution,String branch,UUID id) {return jdbc.query("select r.*,d.checker_id,case when d.review_id is null then 'DRAFT_'||r.action else 'APPROVED_'||r.action end state from accounting_close_review r left join accounting_close_decision d on d.review_id=r.id where r.sacco_id=? and r.station_id=? and r.id=?",CLOSE,institution,branch,id).stream().findFirst();}
    public void decideClose(UUID id,UUID checker,String evidence,OffsetDateTime now) {jdbc.update("insert into accounting_close_decision(review_id,checker_id,evidence,decided_at) values(?,?,?,?)",id,checker,evidence,now);}
    public List<BranchClose> branchCloses(String institution,UUID period) {return jdbc.query("""
          select distinct on(r.station_id) r.id,r.station_id,r.version,r.checksum from accounting_close_review r
          join accounting_close_decision d on d.review_id=r.id where r.sacco_id=? and r.period_id=? and r.action='CLOSE'
          and r.recorded_at>coalesce((select max(rd.decided_at) from accounting_close_review rr join accounting_close_decision rd on rd.review_id=rr.id where rr.period_id=r.period_id and rr.action='REOPEN'),'-infinity')
          order by r.station_id,r.version desc limit 1001
          """,(r,n)->new BranchClose(uuid(r,"id"),r.getString("station_id"),r.getInt("version"),r.getString("checksum")),institution,period);}
    public boolean currentClose(String institution,String branch,UUID review,UUID period) {return Boolean.TRUE.equals(jdbc.queryForObject("""
          select ?=(select r.id from accounting_close_review r join accounting_close_decision d on d.review_id=r.id
          where r.sacco_id=? and r.station_id=? and r.period_id=? and r.action='CLOSE'
          and r.recorded_at>coalesce((select max(rd.decided_at) from accounting_close_review rr join accounting_close_decision rd on rd.review_id=rr.id where rr.period_id=r.period_id and rr.action='REOPEN'),'-infinity')
          order by r.version desc limit 1)
          """,Boolean.class,review,institution,branch,period));}
    public List<String> branches(String institution,LocalDate through) {return jdbc.query("""
          select station_id from(select station_id from sacco_stations where sacco_id=? and active
          union select station_id from gl_journal where sacco_id=? and state='POSTED' and effective_date<=?
          union select station_id from loan_ledgers where sacco_id=? and disbursement_date<=?
          union select station_id from reconciliation_statement where sacco_id=? and starts_on<=?) b order by station_id limit 1001
          """,(r,n)->r.getString(1),institution,institution,through,institution,through,institution,through);}
    public Optional<OpeningEvidence> opening(String institution,String branch) {return jdbc.query("""
          select c.id,c.opening_journal_id,j.policy_id,j.policy_version,j.payload_hash,j.maker_id,j.checker_id,
          j.evidence_reference,j.approval_evidence_reference,c.reconciled_through,j.checked_at,j.posted_at
          from gl_cutover_coverage c join gl_journal j on j.id=c.opening_journal_id
          where c.sacco_id=? and c.station_id=? and c.complete and j.state='POSTED'
          """,(r,n)->new OpeningEvidence(uuid(r,"id"),uuid(r,"opening_journal_id"),uuid(r,"policy_id"),r.getInt("policy_version"),r.getString("payload_hash"),uuid(r,"maker_id"),uuid(r,"checker_id"),r.getString("evidence_reference"),r.getString("approval_evidence_reference"),date(r,"reconciled_through"),time(r,"checked_at"),time(r,"posted_at")),institution,branch).stream().findFirst();}
    public void setPeriod(UUID id,boolean closed,UUID actor,OffsetDateTime now) {jdbc.update("update accounting_period set state=?,closed_by=?,closed_at=? where id=?",closed?"CLOSED":"OPEN",closed?actor:null,closed?now:null,id);}
    public boolean restated(UUID period) {return count("select count(*) from accounting_close_review r join accounting_close_decision d on d.review_id=r.id where r.period_id=? and r.action='REOPEN'",period)>0;}
    public boolean hasInstitutionHistory(String institution) {return count("select count(*) from (select 1 from reconciliation_statement where sacco_id=? limit 1) x",institution)>0||count("select count(*) from (select 1 from reconciliation_format where sacco_id=? limit 1) x",institution)>0||count("select count(*) from (select 1 from accounting_close_review where sacco_id=? limit 1) x",institution)>0;}
    public boolean hasMemberHistory(UUID member) {return Boolean.TRUE.equals(jdbc.queryForObject("""
          select exists(select 1 from reconciliation_format where maker_id=? or checker_id=?)
          or exists(select 1 from reconciliation_statement where maker_id=?)
          or exists(select 1 from reconciliation_match where maker_id=?) or exists(select 1 from reconciliation_match_decision where checker_id=?)
          or exists(select 1 from reconciliation_exception where maker_id=? or assigned_to=?) or exists(select 1 from reconciliation_exception_decision where checker_id=?)
          or exists(select 1 from reconciliation_certificate where maker_id=?) or exists(select 1 from reconciliation_certificate_decision where checker_id=?)
          or exists(select 1 from accounting_close_review where maker_id=?) or exists(select 1 from accounting_close_decision where checker_id=?)
          """,Boolean.class,member,member,member,member,member,member,member,member,member,member,member,member));}
    private long count(String sql,Object...args) {return jdbc.queryForObject(sql,Long.class,args);}
}
