package com.sacco.mvp.accounting.business.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Immutable, bounded source facts for one branch. No registry page or source evidence is returned to a controller. */
@Repository @RequiredArgsConstructor
public class BusinessTransferSourceRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public record Line(UUID id,UUID account,String purpose,BigDecimal amount) { }
    public record Source(UUID journal,String sourceType,String sourceReference,UUID policy,int policyVersion,
            String mappings,LocalDate effectiveDate,OffsetDateTime recordedAt,OffsetDateTime journalPostedAt,
            UUID journalMaker,UUID journalChecker,String currency,UUID reverses,String journalHash,
            UUID document,String kind,BigDecimal amount,LocalDate documentDate,UUID maker,UUID checker,
            String command,String commandHash,OffsetDateTime createdAt,OffsetDateTime postedAt,
            String moneyKey,String destination,UUID related,boolean linkedOutgoing,List<Line> lines) {
        public Source {lines=List.copyOf(lines);}
    }
    public void lockPeriods(String institution,LocalDate through){
        jdbc.getJdbcTemplate().queryForList("SELECT pg_advisory_xact_lock_shared(hashtextextended(?,0))","GL_SETUP/"+institution);
        var periods=jdbc.getJdbcTemplate().queryForList("SELECT id FROM accounting_period WHERE sacco_id=? AND starts_on<=? ORDER BY starts_on,id LIMIT 1001 FOR SHARE",UUID.class,institution,through);
        bound(!periods.isEmpty() && periods.size()<=1000);
    }
    public boolean reviewedCutover(String institution,String branch,LocalDate date,OffsetDateTime cutoff){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT count(*)=1 FROM gl_cutover_coverage c JOIN gl_journal j ON j.id=c.opening_journal_id
            WHERE c.sacco_id=:institution AND c.station_id=:branch AND c.complete
              AND j.sacco_id=c.sacco_id AND j.station_id=c.station_id AND j.state='POSTED' AND j.source_type='OPENING'
              AND c.reconciled_through=:cutover AND j.effective_date=:cutover
              AND c.recorded_at<=:cutoff AND j.posted_at<=:cutoff AND j.recorded_at<=:cutoff
            """,new MapSqlParameterSource().addValue("institution",institution).addValue("branch",branch)
                .addValue("cutover",date).addValue("cutoff",cutoff),Boolean.class));
    }
    public List<Source> sources(String institution,String branch,LocalDate cutover,LocalDate through,OffsetDateTime cutoff){
        var parameters=new MapSqlParameterSource().addValue("institution",institution).addValue("branch",branch)
                .addValue("cutover",cutover).addValue("through",through).addValue("cutoff",cutoff);
        var headers=jdbc.queryForList("""
            SELECT j.id,j.source_type,j.source_reference,j.policy_id,j.policy_version,p.account_mappings_json mappings,
                j.effective_date,j.recorded_at,j.posted_at journal_posted_at,j.maker_id journal_maker,
                j.checker_id journal_checker,j.currency,j.reverses_id,j.payload_hash journal_hash,
                d.id document,d.kind,d.amount,d.effective_date document_date,d.maker_id,d.checker_id,
                d.command_json,d.payload_hash command_hash,d.created_at,d.posted_at,d.money_account_key,
                d.destination_branch,d.related_document_id,
                EXISTS(SELECT 1 FROM accounting_business_document origin WHERE origin.id=d.related_document_id
                    AND origin.sacco_id=j.sacco_id AND origin.station_id<>j.station_id AND origin.destination_branch=j.station_id
                    AND origin.kind='INTERNAL_TRANSFER_OUT' AND origin.state='POSTED' AND origin.amount=d.amount
                    AND origin.effective_date<=d.effective_date AND origin.posted_at<=d.posted_at AND origin.posted_at<=:cutoff
                    AND (SELECT count(*) FROM accounting_business_document receipt WHERE receipt.sacco_id=j.sacco_id
                        AND receipt.related_document_id=origin.id AND receipt.kind='INTERNAL_TRANSFER_IN'
                        AND receipt.state='POSTED' AND receipt.posted_at<=:cutoff)=1) linked_outgoing
            FROM gl_journal j JOIN accounting_policies p ON (p.id,p.sacco_id)=(j.policy_id,j.sacco_id)
            LEFT JOIN accounting_business_document d ON (d.journal_id,d.sacco_id,d.station_id)=(j.id,j.sacco_id,j.station_id)
                AND d.state='POSTED' AND d.posted_at<=:cutoff AND d.created_at<=:cutoff
            WHERE j.sacco_id=:institution AND j.station_id=:branch AND j.state='POSTED'
              AND j.effective_date>:cutover AND j.effective_date<=:through AND j.recorded_at<=:cutoff AND j.posted_at<=:cutoff
              AND EXISTS(SELECT 1 FROM gl_journal_line l JOIN gl_account a ON (a.id,a.sacco_id)=(l.account_id,l.sacco_id)
                WHERE l.journal_id=j.id AND (a.purpose IN('INTERNAL_DUE_FROM','INTERNAL_DUE_TO','INTERNAL_TRANSFER')
                  OR a.id::text=p.account_mappings_json::jsonb->>'INTERNAL_DUE_FROM'
                  OR a.id::text=p.account_mappings_json::jsonb->>'INTERNAL_DUE_TO'))
            ORDER BY j.id LIMIT 1001
            """,parameters);
        bound(headers.size()<=1000);if(headers.isEmpty())return List.of();
        parameters.addValue("ids",headers.stream().map(h->(UUID)h.get("id")).toList());
        var lines=jdbc.query("""
            SELECT l.journal_id,l.id,l.account_id,a.purpose,l.debit-l.credit signed_amount FROM gl_journal_line l
            JOIN gl_journal j ON (j.id,j.sacco_id,j.station_id)=(l.journal_id,l.sacco_id,l.station_id)
            JOIN gl_account a ON (a.id,a.sacco_id)=(l.account_id,l.sacco_id)
            WHERE j.sacco_id=:institution AND j.station_id=:branch AND j.id IN(:ids)
            ORDER BY l.journal_id,l.id LIMIT 10001
            """,parameters,(r,n)->Map.entry(r.getObject(1,UUID.class),new Line(r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4),r.getBigDecimal(5))));
        bound(lines.size()<=10000);var grouped=new HashMap<UUID,List<Line>>();
        for(var line:lines)grouped.computeIfAbsent(line.getKey(),key->new ArrayList<>()).add(line.getValue());
        var result=new ArrayList<Source>();for(var h:headers){UUID id=(UUID)h.get("id");result.add(new Source(id,
            (String)h.get("source_type"),(String)h.get("source_reference"),(UUID)h.get("policy_id"),((Number)h.get("policy_version")).intValue(),
            (String)h.get("mappings"),date(h.get("effective_date")),time(h.get("recorded_at")),time(h.get("journal_posted_at")),
            (UUID)h.get("journal_maker"),(UUID)h.get("journal_checker"),(String)h.get("currency"),(UUID)h.get("reverses_id"),(String)h.get("journal_hash"),
            (UUID)h.get("document"),(String)h.get("kind"),(BigDecimal)h.get("amount"),date(h.get("document_date")),(UUID)h.get("maker_id"),(UUID)h.get("checker_id"),
            (String)h.get("command_json"),(String)h.get("command_hash"),time(h.get("created_at")),time(h.get("posted_at")),
            (String)h.get("money_account_key"),(String)h.get("destination_branch"),(UUID)h.get("related_document_id"),(Boolean)h.get("linked_outgoing"),grouped.getOrDefault(id,List.of())));}
        return List.copyOf(result);
    }
    private static LocalDate date(Object value){return value==null?null:((java.sql.Date)value).toLocalDate();}
    private static OffsetDateTime time(Object value){if(value==null)return null;if(value instanceof OffsetDateTime at)return at;
        if(value instanceof java.sql.Timestamp at)return at.toInstant().atOffset(ZoneOffset.UTC);throw new IllegalArgumentException("business.source.error.time");}
    private static void bound(boolean valid){if(!valid)throw new IllegalArgumentException("business.source.error.size");}
}
