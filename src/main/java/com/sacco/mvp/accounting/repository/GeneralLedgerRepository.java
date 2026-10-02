package com.sacco.mvp.accounting.repository;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GeneralLedgerRepository {
    private final JdbcTemplate jdbc;

    public boolean hasInstitutionHistory(String institution) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_account where sacco_id=?) or exists(select 1 from accounting_period where sacco_id=?) or exists(select 1 from gl_journal where sacco_id=?)",Boolean.class,institution,institution,institution));
    }
    public boolean hasMemberHistory(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_account where maker_id=?) or exists(select 1 from accounting_period where created_by=? or closed_by=?) or exists(select 1 from gl_journal where maker_id=? or checker_id=?)",Boolean.class,id,id,id,id,id));
    }
    public void lockRequest(String institution, String branch, UUID key) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", institution + "/" + branch + "/" + key);
    }
    public void lockAccounts(String institution) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", "GL_SETUP/" + institution);
    }
    public List<Account> accounts(String institution, int offset, int limit) {
        return jdbc.query("select * from gl_account where sacco_id=? order by code,id limit ? offset ?", this::account, institution,limit,offset);
    }
    public Optional<Account> account(String institution, UUID id) {
        return jdbc.query("select * from gl_account where sacco_id=? and id=?",this::account,institution,id).stream().findFirst();
    }
    public Optional<Account> accountCode(String institution,String code) {
        return jdbc.query("select * from gl_account where sacco_id=? and code=?",this::account,institution,code).stream().findFirst();
    }
    public List<DisplayLine> displayLines(UUID journal) {
        return jdbc.query("select a.code,a.name,l.debit,l.credit from gl_journal_line l join gl_account a on a.id=l.account_id where l.journal_id=? order by a.code,l.id limit 500",(r,n)->new DisplayLine(r.getString(1),r.getString(2),r.getBigDecimal(3),r.getBigDecimal(4)),journal);
    }
    public void createAccount(String institution, UUID id, AccountCommand c, UUID maker, OffsetDateTime now) {
        jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,parent_id,maker_id,created_at) values(?,?,?,?,?,?,?,?,?,?,?)",
            id,institution,c.code(),c.name(),c.type(),c.normalBalance(),c.kind(),c.purpose(),c.parentId(),maker,now);
    }
    public void deactivate(String institution, UUID id) {
        jdbc.update("update gl_account set active=false where sacco_id=? and id=?",institution,id);
    }
    public List<Period> lockOpenPeriod(String institution, LocalDate date) {
        return jdbc.query("select * from accounting_period where sacco_id=? and ? between starts_on and ends_on for share",this::period,institution,date);
    }
    public boolean periodOverlap(String institution, LocalDate start, LocalDate end) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from accounting_period where sacco_id=? and starts_on<=? and ends_on>=?)",Boolean.class,institution,end,start));
    }
    public UUID createPeriod(String institution, LocalDate start, LocalDate end, UUID policy, UUID maker, OffsetDateTime now) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,policy_id,created_by,created_at) values(?,?,?,?,'OPEN',?,?,?)",id,institution,start,end,policy,maker,now);
        return id;
    }
    public Optional<Journal> byRequest(String institution,String branch,UUID key) {
        return jdbc.query("select j.*, exists(select 1 from gl_journal r where r.reverses_id=j.id and r.state='POSTED') reversed from gl_journal j where j.sacco_id=? and j.station_id=? and j.request_key=?",this::journal,institution,branch,key).stream().findFirst();
    }
    public Optional<Journal> journal(String institution,String branch,UUID id,boolean lock) {
        return jdbc.query("select j.*, exists(select 1 from gl_journal r where r.reverses_id=j.id and r.state='POSTED') reversed from gl_journal j where j.sacco_id=? and j.station_id=? and j.id=?"+(lock?" for update of j":""),this::journal,institution,branch,id).stream().findFirst();
    }
    public List<Journal> journals(String institution,String branch,int offset,int limit) {
        return jdbc.query("select j.*, exists(select 1 from gl_journal r where r.reverses_id=j.id and r.state='POSTED') reversed from gl_journal j where j.sacco_id=? and j.station_id=? order by j.recorded_at desc,j.id limit ? offset ?",this::journal,institution,branch,limit,offset);
    }
    public List<Line> lines(UUID journal) {
        return jdbc.query("select account_id,debit,credit from gl_journal_line where journal_id=? order by id",(r,n)->new Line(r.getObject(1,UUID.class),r.getBigDecimal(2),r.getBigDecimal(3)),journal);
    }
    public void createJournal(Journal j) {
        jdbc.update("insert into gl_journal(id,sacco_id,station_id,policy_id,policy_version,period_id,source_type,source_reference,request_key,payload_hash,currency,state,evidence_reference,reason,effective_date,maker_id,recorded_at,reverses_id) values(?,?,?,?,?,?,?,?,?,?,'TZS','DRAFT',?,?,?,?,?,?)",
            j.id(),j.institutionId(),j.branchId(),j.policyId(),j.policyVersion(),j.periodId(),j.sourceType(),j.sourceReference(),j.requestKey(),j.payloadHash(),j.evidenceReference(),j.reason(),j.effectiveDate(),j.makerId(),j.recordedAt(),j.reversesId());
        for(Line l:j.lines()) jdbc.update("insert into gl_journal_line(id,journal_id,sacco_id,station_id,account_id,debit,credit) values(?,?,?,?,?,?,?)",UUID.randomUUID(),j.id(),j.institutionId(),j.branchId(),l.accountId(),l.debit(),l.credit());
    }
    public void approve(UUID id,UUID checker,String evidence,OffsetDateTime now) {
        jdbc.update("update gl_journal set state='APPROVED',checker_id=?,checked_at=?,approval_evidence_reference=? where id=? and state='DRAFT'",checker,now,evidence,id);
    }
    public void post(UUID id,OffsetDateTime now) {
        jdbc.update("update gl_journal set state='POSTED',posted_at=? where id=? and state='APPROVED'",now,id);
    }
    public void outbox(Journal j,OffsetDateTime now) {
        jdbc.update("insert into accounting_outbox(id,journal_id,sacco_id,station_id,event_type,created_at) values(?,?,?,?,'GL_JOURNAL_POSTED',?)",UUID.randomUUID(),j.id(),j.institutionId(),j.branchId(),now);
    }
    private String approvalEvidence(UUID id) {return jdbc.queryForObject("select approval_evidence_reference from gl_journal where id=?",String.class,id);}
    public boolean reviewedOpening(String institution,String branch) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_cutover_coverage c join gl_journal j on j.id=c.opening_journal_id where c.sacco_id=? and c.station_id=? and c.complete and j.state='POSTED')",Boolean.class,institution,branch));
    }
    public void openingCoverage(Journal j,UUID checker,boolean complete,OffsetDateTime now) {
        jdbc.update("insert into gl_cutover_coverage(id,sacco_id,station_id,opening_journal_id,evidence_reference,reconciled_through,maker_id,checker_id,recorded_at,complete) values(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),j.institutionId(),j.branchId(),j.id(),approvalEvidence(j.id()),j.effectiveDate(),j.makerId(),checker,now,complete);
    }
    public long unbridged(String institution,String branch) {
        return jdbc.queryForObject("select count(distinct e.voucher_id) from loan_journal_entries e join loan_ledgers l on l.loan_application_id=e.loan_application_id where l.sacco_id=? and l.station_id=? and not exists(select 1 from gl_operational_bridge b join gl_journal j on j.id=b.journal_id where b.voucher_id=e.voucher_id and j.state='POSTED') and not exists(select 1 from gl_cutover_coverage c join gl_journal j on j.id=c.opening_journal_id where c.sacco_id=l.sacco_id and c.station_id=l.station_id and c.complete and j.state='POSTED' and e.effective_date<=c.reconciled_through)",Long.class,institution,branch);
    }
    public long uncoveredLegacy(String institution,String branch) {
        return jdbc.queryForObject("select count(*) from loan_applications a where a.sacco_id=? and a.station_id=? and a.status in ('DISBURSED','PAID','DEFAULTED') and not exists(select 1 from loan_ledgers l where l.loan_application_id=a.id) and not exists(select 1 from gl_cutover_coverage c join gl_journal j on j.id=c.opening_journal_id where c.sacco_id=a.sacco_id and c.station_id=a.station_id and c.complete and j.state='POSTED')",Long.class,institution,branch);
    }
    public List<OperationalLine> operationalVoucher(String institution,String branch,UUID voucher) {
        return jdbc.query("select e.account_code,e.debit,e.credit,e.effective_date,l.loan_application_id from loan_journal_entries e join loan_ledgers l on l.loan_application_id=e.loan_application_id where l.sacco_id=? and l.station_id=? and e.voucher_id=? order by e.id limit 101",(r,n)->new OperationalLine(r.getString(1),r.getBigDecimal(2),r.getBigDecimal(3),r.getObject(4,LocalDate.class),r.getObject(5,UUID.class)),institution,branch,voucher);
    }
    public boolean coveredAtCutover(String institution,String branch,LocalDate date) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from gl_cutover_coverage c join gl_journal j on j.id=c.opening_journal_id where c.sacco_id=? and c.station_id=? and c.complete and j.state='POSTED' and c.reconciled_through>=?)",Boolean.class,institution,branch,date));
    }
    public void bridge(UUID voucher,Journal j) {
        jdbc.update("insert into gl_operational_bridge(voucher_id,sacco_id,station_id,journal_id,evidence_reference) values(?,?,?,?,?) on conflict(voucher_id) do nothing",voucher,j.institutionId(),j.branchId(),j.id(),j.evidenceReference());
    }
    public record OperationalLine(String accountCode,java.math.BigDecimal debit,java.math.BigDecimal credit,LocalDate date,UUID loanId) { }
    private Account account(ResultSet r,int n) throws SQLException {
        return new Account(r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getString("type"),r.getString("normal_balance"),r.getString("kind"),r.getString("purpose"),r.getObject("parent_id",UUID.class),r.getBoolean("active"));
    }
    private Period period(ResultSet r,int n) throws SQLException {
        return new Period(r.getObject("id",UUID.class),r.getString("sacco_id"),r.getObject("starts_on",LocalDate.class),r.getObject("ends_on",LocalDate.class),r.getString("state"),r.getObject("policy_id",UUID.class));
    }
    private Journal journal(ResultSet r,int n) throws SQLException {
        return new Journal(r.getObject("id",UUID.class),r.getString("sacco_id"),r.getString("station_id"),r.getObject("policy_id",UUID.class),r.getInt("policy_version"),r.getObject("period_id",UUID.class),r.getString("source_type"),r.getString("source_reference"),r.getObject("request_key",UUID.class),r.getString("payload_hash"),r.getString("state"),r.getString("evidence_reference"),r.getString("reason"),r.getObject("effective_date",LocalDate.class),r.getObject("maker_id",UUID.class),r.getObject("checker_id",UUID.class),r.getObject("recorded_at",OffsetDateTime.class),r.getObject("posted_at",OffsetDateTime.class),r.getObject("reverses_id",UUID.class),List.of(),r.getBoolean("reversed"));
    }
}
