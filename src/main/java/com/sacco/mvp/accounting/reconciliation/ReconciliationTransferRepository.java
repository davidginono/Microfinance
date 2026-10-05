package com.sacco.mvp.accounting.reconciliation;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.sacco.mvp.accounting.reconciliation.ReconciliationTransferProjection.*;

/** Bounded, branch-scoped GL facts; this repository never opens a foreign business registry. */
@Repository @RequiredArgsConstructor
class ReconciliationTransferRepository {
    private final NamedParameterJdbcTemplate jdbc;
    List<Journal> journals(String institution,String branch,LocalDate cutover,LocalDate through,OffsetDateTime cutoff){
        var parameters=new MapSqlParameterSource().addValue("institution",institution).addValue("branch",branch).addValue("cutover",cutover).addValue("through",through).addValue("cutoff",cutoff);
        var headers=jdbc.queryForList("""
                SELECT j.id,j.source_type,j.source_reference,j.policy_id,j.policy_version,j.effective_date,j.recorded_at,j.posted_at,j.currency,j.maker_id,j.checker_id,j.reverses_id FROM gl_journal j JOIN accounting_policies p ON (p.id,p.sacco_id)=(j.policy_id,j.sacco_id)
                WHERE j.sacco_id=:institution AND j.station_id=:branch AND j.state='POSTED'
                  AND j.effective_date>:cutover AND j.effective_date<=:through AND j.recorded_at<=:cutoff AND j.posted_at<=:cutoff
                  AND EXISTS(SELECT 1 FROM gl_journal_line l JOIN gl_account a ON (a.id,a.sacco_id)=(l.account_id,l.sacco_id)
                    WHERE l.journal_id=j.id AND (a.purpose IN('INTERNAL_DUE_FROM','INTERNAL_DUE_TO','INTERNAL_TRANSFER')
                      OR a.id::text=p.account_mappings_json::jsonb->>'INTERNAL_DUE_FROM' OR a.id::text=p.account_mappings_json::jsonb->>'INTERNAL_DUE_TO'))
                ORDER BY j.id LIMIT 1001
                """,parameters);
        require(headers.size()<=1000);if(headers.isEmpty())return List.of();parameters.addValue("ids",headers.stream().map(h->(UUID)h.get("id")).toList());
        var lines=jdbc.query("""
                SELECT l.journal_id,l.id,l.account_id,a.purpose,l.debit-l.credit signed_amount,
                    (a.purpose IN('INTERNAL_DUE_FROM','INTERNAL_DUE_TO','INTERNAL_TRANSFER') OR a.id::text=p.account_mappings_json::jsonb->>'INTERNAL_DUE_FROM'
                        OR a.id::text=p.account_mappings_json::jsonb->>'INTERNAL_DUE_TO') internal
                FROM gl_journal_line l JOIN gl_journal j ON (j.id,j.sacco_id,j.station_id)=(l.journal_id,l.sacco_id,l.station_id)
                  JOIN gl_account a ON (a.id,a.sacco_id)=(l.account_id,l.sacco_id) JOIN accounting_policies p ON (p.id,p.sacco_id)=(j.policy_id,j.sacco_id)
                WHERE j.sacco_id=:institution AND j.station_id=:branch AND j.id IN(:ids)
                ORDER BY l.journal_id,l.id LIMIT 10001
                """,parameters,(r,n)->Map.entry(r.getObject("journal_id",UUID.class),new Line(r.getObject("id",UUID.class),r.getObject("account_id",UUID.class),r.getString("purpose"),r.getBigDecimal("signed_amount"),r.getBoolean("internal"))));
        require(lines.size()<=10000);var grouped=new HashMap<UUID,List<Line>>();for(var line:lines)grouped.computeIfAbsent(line.getKey(),key->new ArrayList<>()).add(line.getValue());
        var result=new ArrayList<Journal>();for(var h:headers){UUID id=(UUID)h.get("id");result.add(new Journal(id,(String)h.get("source_type"),(String)h.get("source_reference"),(UUID)h.get("policy_id"),((Number)h.get("policy_version")).intValue(),((java.sql.Date)h.get("effective_date")).toLocalDate(),time(h.get("recorded_at")),time(h.get("posted_at")),(String)h.get("currency"),(UUID)h.get("maker_id"),(UUID)h.get("checker_id"),(UUID)h.get("reverses_id"),grouped.getOrDefault(id,List.of())));}return List.copyOf(result);
    }
    private static OffsetDateTime time(Object value){if(value instanceof OffsetDateTime at)return at;if(value instanceof java.sql.Timestamp at)return at.toInstant().atOffset(ZoneOffset.UTC);throw new IllegalArgumentException("reconciliation.error.transferSource");}
    private static void require(boolean valid){if(!valid)throw new IllegalArgumentException("reconciliation.error.snapshotSize");}
}
