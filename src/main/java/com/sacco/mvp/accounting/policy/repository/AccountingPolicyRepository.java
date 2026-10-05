package com.sacco.mvp.accounting.policy.repository;

import com.sacco.mvp.accounting.policy.model.*;
import com.sacco.mvp.accounting.policy.dto.PolicyListRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class AccountingPolicyRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<AccountingPolicyRecord> ROW = AccountingPolicyRepository::row;

    public void lockGovernance(String institution) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,4201))", Object.class, institution);
    }
    public int nextVersion(String institution) {
        return jdbc.queryForObject("SELECT coalesce(max(policy_version),0)+1 FROM accounting_policy WHERE sacco_id=?", Integer.class, institution);
    }
    public Optional<AccountingPolicyRecord> find(String institution, UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM accounting_policy WHERE sacco_id=? AND id=?" + (lock ? " FOR UPDATE" : ""), ROW, institution, id).stream().findFirst();
    }
    public Optional<AccountingPolicyRecord> retry(String institution, UUID requestKey) {
        return jdbc.query("SELECT * FROM accounting_policy WHERE sacco_id=? AND request_key=?", ROW, institution, requestKey).stream().findFirst();
    }
    public Optional<AccountingPolicyRecord> applicable(String institution, LocalDate date) {
        return jdbc.query("SELECT * FROM accounting_policy WHERE sacco_id=? AND state='APPROVED' AND effective_from<=? ORDER BY effective_from DESC LIMIT 1",
            ROW, institution, date).stream().findFirst();
    }
    public Optional<AccountingPolicyRecord> latestApproved(String institution) {
        return jdbc.query("SELECT * FROM accounting_policy WHERE sacco_id=? AND state='APPROVED' ORDER BY effective_from DESC LIMIT 1", ROW, institution).stream().findFirst();
    }
    public Optional<AccountingPolicyRecord> firstApproved(String institution) {
        return jdbc.query("SELECT * FROM accounting_policy WHERE sacco_id=? AND state='APPROVED' ORDER BY effective_from ASC LIMIT 1", ROW, institution).stream().findFirst();
    }
    public List<PolicyListRow> list(String institution, int offset) {
        return jdbc.query("SELECT id,policy_version,effective_from,authority,state FROM accounting_policy WHERE sacco_id=? ORDER BY policy_version DESC LIMIT 26 OFFSET ?",
            (rs,n) -> new PolicyListRow(rs.getObject("id",UUID.class),rs.getInt("policy_version"),rs.getObject("effective_from",LocalDate.class),GlAuthority.valueOf(rs.getString("authority")),rs.getString("state")), institution, offset);
    }
    public void insert(AccountingPolicyRecord p) {
        jdbc.update("INSERT INTO accounting_policy(id,sacco_id,station_id,policy_version,state,authority,effective_from,content_json,content_hash,created_by,created_at,request_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            p.id(),p.saccoId(),p.stationId(),p.version(),p.state(),p.authority().name(),p.effectiveFrom(),p.contentJson(),p.contentHash(),p.createdBy(),p.createdAt(),p.requestKey());
    }
    public void decide(String institution, UUID id, String state, UUID checker, OffsetDateTime when, String evidence, String reason) {
        int changed = jdbc.update("UPDATE accounting_policy SET state=?,checked_by=?,checked_at=?,review_evidence=?,review_reason=? WHERE sacco_id=? AND id=? AND state='DRAFT'",
            state,checker,when,evidence,reason,institution,id);
        if (changed != 1) throw new IllegalArgumentException("policy.error.conflict");
    }
    public void audit(AccountingPolicyRecord p, String event, UUID actor, String station, OffsetDateTime when, String evidence, String reason) {
        jdbc.update("INSERT INTO accounting_policy_audit(id,sacco_id,policy_id,station_id,event,actor_id,recorded_at,evidence_reference,reason,content_hash) VALUES (?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(),p.saccoId(),p.id(),station,event,actor,when,evidence,reason,p.contentHash());
    }
    private static AccountingPolicyRecord row(ResultSet rs, int n) throws SQLException {
        return new AccountingPolicyRecord(rs.getObject("id",UUID.class),rs.getString("sacco_id"),rs.getString("station_id"),
            rs.getInt("policy_version"),rs.getString("state"),GlAuthority.valueOf(rs.getString("authority")),rs.getObject("effective_from",LocalDate.class),
            rs.getString("content_json"),rs.getString("content_hash"),rs.getObject("created_by",UUID.class),rs.getObject("created_at",OffsetDateTime.class),
            rs.getObject("checked_by",UUID.class),rs.getObject("checked_at",OffsetDateTime.class),rs.getString("review_evidence"),rs.getString("review_reason"),rs.getObject("request_key",UUID.class));
    }
}
