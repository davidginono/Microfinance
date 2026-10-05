package com.sacco.mvp.accounting.ledger.repository;

import com.sacco.mvp.accounting.ledger.dto.LedgerDtos.*;
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

/** Scoped, bounded persistence. Called only by the owning ledger service. */
@Repository
@RequiredArgsConstructor
public class AccountingLedgerRepository {
    private final JdbcTemplate jdbc;

    public List<AccountView> accounts(String institution, int page) {
        return jdbc.query("SELECT * FROM accounting_account WHERE sacco_id=? ORDER BY code,id LIMIT 25 OFFSET ?",
            this::account, institution, Math.multiplyExact(page, 25));
    }
    public long accountCount(String institution) {
        return jdbc.queryForObject("SELECT count(*) FROM accounting_account WHERE sacco_id=?", Long.class, institution);
    }
    public Optional<AccountView> account(String institution, String code) {
        return jdbc.query("SELECT * FROM accounting_account WHERE sacco_id=? AND code=? FOR SHARE", this::account, institution, code).stream().findFirst();
    }
    public void createAccount(UUID id, String institution, UUID actor, OffsetDateTime now, AccountCommand c) {
        jdbc.update("INSERT INTO accounting_account(id,sacco_id,code,name,kind,normal_balance,parent_id,usage,category,active,created_by,recorded_at) VALUES(?,?,?,?,?,?,?,?,?,true,?,?)",
            id, institution, c.code(), c.name(), c.kind().name(), c.normalBalance().name(), c.parentId(), c.usage().name(), c.category().name(), actor, now);
    }
    public boolean deactivateAccount(String institution, UUID id) {
        return jdbc.update("UPDATE accounting_account SET active=false WHERE sacco_id=? AND id=?", institution, id)==1;
    }
    public void createPeriod(UUID id, String institution, LocalDate start, LocalDate end, UUID actor, OffsetDateTime now) {
        jdbc.update("INSERT INTO accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,recorded_at) VALUES(?,?,?,?,'OPEN',?,?)",id,institution,start,end,actor,now);
    }
    public Optional<UUID> openPeriod(String institution, LocalDate date) {
        return jdbc.query("SELECT id FROM accounting_period WHERE sacco_id=? AND starts_on<=? AND ends_on>=? AND state='OPEN' FOR SHARE",
            (rs,n)->rs.getObject("id",UUID.class),institution,date,date).stream().findFirst();
    }
    public List<JournalSummary> journals(String institution, String branch, int page) {
        return jdbc.query("SELECT j.id,j.effective_date,j.recorded_at,j.description,j.state,j.source_kind,(SELECT sum(l.debit) FROM accounting_journal_line l WHERE l.journal_id=j.id) AS total_debit FROM accounting_journal j WHERE sacco_id=? AND station_id=? ORDER BY recorded_at DESC,id LIMIT 25 OFFSET ?",
            (rs,n)->new JournalSummary(rs.getObject("id",UUID.class),rs.getObject("effective_date",LocalDate.class),rs.getObject("recorded_at",OffsetDateTime.class),rs.getString("description"),rs.getString("state"),rs.getString("source_kind"),rs.getBigDecimal("total_debit")),institution,branch,Math.multiplyExact(page,25));
    }
    public long journalCount(String institution, String branch) {
        return jdbc.queryForObject("SELECT count(*) FROM accounting_journal WHERE sacco_id=? AND station_id=?",Long.class,institution,branch);
    }
    public Optional<JournalView> journal(String institution, String branch, UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM accounting_journal WHERE sacco_id=? AND station_id=? AND id=?"+(lock?" FOR UPDATE":""),this::journal,institution,branch,id).stream().findFirst();
    }
    public Optional<JournalView> request(String institution, String branch, UUID key) {
        return jdbc.query("SELECT * FROM accounting_journal WHERE sacco_id=? AND station_id=? AND request_key=?",this::journal,institution,branch,key).stream().findFirst();
    }
    public Optional<JournalView> source(String institution, String branch, String kind, String reference) {
        return jdbc.query("SELECT * FROM accounting_journal WHERE sacco_id=? AND station_id=? AND source_kind=? AND source_reference=?",this::journal,institution,branch,kind,reference).stream().findFirst();
    }
    public List<LineView> lines(String institution, UUID journalId) {
        return jdbc.query("SELECT l.*,a.code,a.name FROM accounting_journal_line l JOIN accounting_account a ON a.id=l.account_id AND a.sacco_id=l.sacco_id WHERE l.sacco_id=? AND l.journal_id=? ORDER BY l.line_number LIMIT 200",
            (rs,n)->new LineView(rs.getInt("line_number"),rs.getObject("account_id",UUID.class),rs.getString("code"),rs.getString("name"),rs.getBigDecimal("debit"),rs.getBigDecimal("credit"),rs.getString("memo")),institution,journalId);
    }
    public void insertJournal(JournalView j) {
        jdbc.update("INSERT INTO accounting_journal(id,sacco_id,station_id,period_id,effective_date,recorded_at,currency,policy_id,policy_version,policy_hash,event_type,source_kind,source_reference,request_key,payload_hash,description,evidence_reference,state,maker_id,reverses_journal_id,reversal_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?)",
            j.id(),j.institution(),j.branch(),j.periodId(),j.effectiveDate(),j.recordedAt(),j.currency(),j.policyId(),j.policyVersion(),j.policyHash(),j.eventType(),j.sourceKind(),j.sourceReference(),j.requestKey(),j.payloadHash(),j.description(),j.evidenceReference(),j.makerId(),j.reversesJournalId(),j.reversalReason());
    }
    public void insertLines(String institution, UUID journalId, List<LineView> lines) {
        jdbc.batchUpdate("INSERT INTO accounting_journal_line(id,journal_id,sacco_id,account_id,line_number,debit,credit,memo) VALUES(?,?,?,?,?,?,?,?)",
            lines,200,(ps,l)-> { ps.setObject(1,UUID.randomUUID());ps.setObject(2,journalId);ps.setString(3,institution);ps.setObject(4,l.accountId());ps.setInt(5,l.lineNumber());ps.setBigDecimal(6,l.debit());ps.setBigDecimal(7,l.credit());ps.setString(8,l.memo()); });
    }
    public void approve(UUID id, UUID checker, OffsetDateTime now) {
        jdbc.update("UPDATE accounting_journal SET state='APPROVED',approved_by=?,approved_at=? WHERE id=? AND state='DRAFT'",checker,now,id);
    }
    public void post(UUID id, UUID poster, OffsetDateTime now) {
        jdbc.update("UPDATE accounting_journal SET state='POSTED',posted_by=?,posted_at=? WHERE id=? AND state='APPROVED'",poster,now,id);
    }
    public void markReversed(UUID id) {
        jdbc.update("UPDATE accounting_journal SET state='REVERSED' WHERE id=? AND state='POSTED'",id);
    }
    public Optional<UUID> reversal(UUID originalId) {
        return jdbc.query("SELECT id FROM accounting_journal WHERE reverses_journal_id=?",(rs,n)->rs.getObject(1,UUID.class),originalId).stream().findFirst();
    }
    public void event(String institution,String branch,UUID journal, String action, UUID actor, String evidence,OffsetDateTime now, boolean outbox) {
        jdbc.update("INSERT INTO accounting_audit_event(id,sacco_id,station_id,journal_id,action,actor_id,evidence_reference,recorded_at) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID(),institution,branch,journal,action,actor,evidence,now);
        if(outbox) jdbc.update("INSERT INTO accounting_outbox(id,sacco_id,station_id,journal_id,event_type,recorded_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),institution,branch,journal,action,now);
    }
    public void requestLock(String institution,String branch,String kind,String reference) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))::text",String.class,institution+":"+branch+":"+kind+":"+reference);
    }
    public void opening(UUID id,String institution,String branch,OpeningCommand c,UUID journal,String hash,UUID maker,OffsetDateTime now,UUID policyId,int policyVersion,String policyHash) {
        jdbc.update("INSERT INTO accounting_opening_batch(id,sacco_id,station_id,cutoff,journal_id,kind,policy_id,policy_version,policy_hash,request_key,payload_hash,source_evidence,state,maker_id,recorded_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'PREVIEW',?,?)",id,institution,branch,c.cutoff(),journal,journal==null?"ZERO_CERTIFICATE":"JOURNAL",policyId,policyVersion,policyHash,c.requestKey(),hash,c.sourceEvidence(),maker,now);
    }
    public Optional<OpeningView> opening(String institution,String branch,UUID id,boolean lock) {
        return jdbc.query("SELECT * FROM accounting_opening_batch WHERE sacco_id=? AND station_id=? AND id=?"+(lock?" FOR UPDATE":""),this::opening,institution,branch,id).stream().findFirst();
    }
    public Optional<OpeningView> openingRequest(String institution,String branch,UUID key) {
        return jdbc.query("SELECT * FROM accounting_opening_batch WHERE sacco_id=? AND station_id=? AND request_key=?",this::opening,institution,branch,key).stream().findFirst();
    }
    public List<OpeningView> openings(String institution,String branch,int page) {
        return jdbc.query("SELECT * FROM accounting_opening_batch WHERE sacco_id=? AND station_id=? ORDER BY recorded_at DESC,id LIMIT 25 OFFSET ?",this::opening,institution,branch,Math.multiplyExact(page,25));
    }
    public long openingCount(String institution,String branch) {
        return jdbc.queryForObject("SELECT count(*) FROM accounting_opening_batch WHERE sacco_id=? AND station_id=?",Long.class,institution,branch);
    }
    public void reviewOpening(UUID id,UUID checker,String evidence,OffsetDateTime now) {
        jdbc.update("UPDATE accounting_opening_batch SET state='APPROVED',reviewed_by=?,reviewed_at=?,reconciliation_evidence=? WHERE id=? AND state='PREVIEW'",checker,now,evidence,id);
    }
    public void postedOpening(UUID journal) { jdbc.update("UPDATE accounting_opening_batch SET state='POSTED' WHERE journal_id=? AND state='APPROVED'",journal); }
    public Optional<LocalDate> cutoff(String institution,String branch) {
        return jdbc.query("SELECT c.cutoff FROM accounting_cutover c JOIN accounting_opening_batch b ON b.id=c.opening_batch_id LEFT JOIN accounting_journal j ON j.id=b.journal_id WHERE c.sacco_id=? AND c.station_id=? AND ((b.kind='JOURNAL' AND b.state='POSTED' AND j.state='POSTED') OR (b.kind='ZERO_CERTIFICATE' AND b.state='APPROVED'))",(rs,n)->rs.getObject(1,LocalDate.class),institution,branch).stream().findFirst();
    }
    public boolean cutoverOpening(UUID journal) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM accounting_cutover c JOIN accounting_opening_batch b ON b.id=c.opening_batch_id WHERE b.journal_id=?)",Boolean.class,journal));
    }
    public java.math.BigDecimal openingPrincipal(String institution,UUID journal) {
        return jdbc.queryForObject("SELECT coalesce(sum(l.debit-l.credit),0) FROM accounting_journal_line l JOIN accounting_account a ON a.id=l.account_id AND a.sacco_id=l.sacco_id WHERE l.sacco_id=? AND l.journal_id=? AND a.category='LOAN_PRINCIPAL'",java.math.BigDecimal.class,institution,journal);
    }
    public java.math.BigDecimal subledgerPrincipal(String institution,String branch,LocalDate cutoff) {
        return jdbc.queryForObject("SELECT coalesce(sum(l.principal-coalesce(p.paid,0)),0) FROM loan_ledgers l LEFT JOIN (SELECT loan_application_id,sum(CASE WHEN kind='PAYMENT' THEN principal_amount ELSE -principal_amount END) AS paid FROM loan_repayment_transactions WHERE sacco_id=? AND station_id=? AND payment_date<=? GROUP BY loan_application_id) p ON p.loan_application_id=l.loan_application_id WHERE l.sacco_id=? AND l.station_id=? AND l.disbursement_date<=?",java.math.BigDecimal.class,institution,branch,cutoff,institution,branch,cutoff);
    }
    public void cutover(String institution,String branch,OpeningView b,UUID checker,String evidence,OffsetDateTime now) {
        jdbc.update("INSERT INTO accounting_cutover(sacco_id,station_id,cutoff,opening_batch_id,source_recorded_cutoff,reviewed_by,reviewed_at,evidence_reference) VALUES(?,?,?,?,(SELECT recorded_at FROM accounting_opening_batch WHERE id=?),?,?,?)",institution,branch,b.cutoff(),b.id(),b.id(),checker,now,evidence);
    }
    public long unknownLoans(String institution,String branch) {
        return jdbc.queryForObject("SELECT count(*) FROM loan_applications l WHERE l.sacco_id=? AND l.station_id=? AND l.status IN ('DISBURSED','PAR','DEFAULTED','PAID') AND NOT EXISTS(SELECT 1 FROM loan_ledgers g WHERE g.loan_application_id=l.id AND g.sacco_id=l.sacco_id AND g.station_id=l.station_id)",Long.class,institution,branch);
    }
    public long unbridged(String institution,String branch,LocalDate cutoff) {
        return jdbc.queryForObject("SELECT count(*) FROM (SELECT e.voucher_id FROM loan_journal_entries e JOIN loan_ledgers l ON l.loan_application_id=e.loan_application_id LEFT JOIN accounting_cutover c ON c.sacco_id=l.sacco_id AND c.station_id=l.station_id WHERE l.sacco_id=? AND l.station_id=? AND (?::date IS NULL OR e.effective_date>?::date OR e.posted_at>c.source_recorded_cutoff) AND NOT EXISTS(SELECT 1 FROM accounting_journal j WHERE j.sacco_id=l.sacco_id AND j.station_id=l.station_id AND j.source_kind='OPERATIONAL_CLEARING' AND j.source_reference=e.voucher_id::text AND j.state IN ('POSTED','REVERSED')) GROUP BY e.voucher_id) v",Long.class,institution,branch,cutoff,cutoff);
    }
    public List<LineCommand> operationalVoucher(String institution,String branch,UUID voucher) {
        return jdbc.query("SELECT e.account_code,e.debit,e.credit FROM loan_journal_entries e JOIN loan_ledgers l ON l.loan_application_id=e.loan_application_id WHERE l.sacco_id=? AND l.station_id=? AND e.voucher_id=? ORDER BY e.id LIMIT 201",
            (rs,n)->new LineCommand(rs.getString(1),rs.getBigDecimal(2),rs.getBigDecimal(3),""),institution,branch,voucher);
    }
    public Optional<LocalDate> operationalDate(String institution,String branch,UUID voucher) {
        return jdbc.query("SELECT min(e.effective_date) FROM loan_journal_entries e JOIN loan_ledgers l ON l.loan_application_id=e.loan_application_id WHERE l.sacco_id=? AND l.station_id=? AND e.voucher_id=? HAVING min(e.effective_date)=max(e.effective_date)",
            (rs,n)->rs.getObject(1,LocalDate.class),institution,branch,voucher).stream().findFirst();
    }
    private AccountView account(ResultSet rs,int n) throws SQLException { return new AccountView(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),rs.getString("kind"),rs.getString("normal_balance"),rs.getObject("parent_id",UUID.class),rs.getString("usage"),rs.getString("category"),rs.getBoolean("active")); }
    private OpeningView opening(ResultSet rs,int n) throws SQLException { return new OpeningView(rs.getObject("id",UUID.class),rs.getObject("cutoff",LocalDate.class),rs.getObject("journal_id",UUID.class),rs.getString("state"),rs.getString("source_evidence"),rs.getString("reconciliation_evidence"),rs.getObject("maker_id",UUID.class),rs.getObject("reviewed_by",UUID.class),rs.getString("kind"),rs.getObject("policy_id",UUID.class),rs.getInt("policy_version"),rs.getString("policy_hash"),rs.getObject("request_key",UUID.class),rs.getString("payload_hash")); }
    private JournalView journal(ResultSet rs,int n) throws SQLException {
        return new JournalView(rs.getObject("id",UUID.class),rs.getString("sacco_id"),rs.getString("station_id"),rs.getObject("period_id",UUID.class),rs.getObject("effective_date",LocalDate.class),rs.getObject("recorded_at",OffsetDateTime.class),rs.getString("currency"),rs.getObject("policy_id",UUID.class),rs.getInt("policy_version"),rs.getString("policy_hash"),rs.getString("event_type"),rs.getString("source_kind"),rs.getString("source_reference"),rs.getObject("request_key",UUID.class),rs.getString("payload_hash"),rs.getString("description"),rs.getString("evidence_reference"),rs.getString("state"),rs.getObject("maker_id",UUID.class),rs.getObject("approved_by",UUID.class),rs.getObject("approved_at",OffsetDateTime.class),rs.getObject("posted_by",UUID.class),rs.getObject("posted_at",OffsetDateTime.class),rs.getObject("reverses_journal_id",UUID.class),rs.getString("reversal_reason"),List.of());
    }
}
