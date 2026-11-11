package com.sacco.mvp.dashboard.repository;

import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/** Read-only dashboard projections. No record bodies or financial commands are returned. */
@Repository @RequiredArgsConstructor
public class DashboardWorkRepository {
    private final JdbcTemplate jdbc;
    public record Bucket(String category, String group, long count, BigDecimal amount, OffsetDateTime oldest) {}

    public List<Bucket> vouchers(AppUserPrincipal actor, List<String> types, OffsetDateTime before, BigDecimal minimum) {
        if (types.isEmpty()) return List.of();
        var args = new ArrayList<Object>(List.of(actor.getSaccoId(), actor.getStationId()));
        args.addAll(types); args.add(before); args.add(minimum);
        return jdbc.query("""
            select category,type,count(*) n,sum(total) amount,min(created_at) oldest
            from voucher_workflow w cross join lateral (values
              ('FAILED',w.posting='FAILED'),('ERROR',w.validation='ERROR'),
              ('INITIATED',w.approval='INITIATED'),('REQUIRED',w.approval='REQUIRED'),('REJECTED',w.approval='REJECTED'),
              ('INCOMPLETE',w.validation='INCOMPLETE'),('NOT_VALIDATED',w.validation='NOT_VALIDATED'),
              ('READY',w.validation='VALIDATED' and w.approval in ('NOT_REQUIRED','WORKFLOW_APPROVED','MANUALLY_APPROVED') and w.posting='NOT_POSTED')
            ) c(category,matched) where matched and sacco_id=? and station_id=? and type in (
            """ + String.join(",", Collections.nCopies(types.size(), "?")) + ") and posting<>'POSTED' and created_at<=? and total>=? group by category,type",
            (r,n) -> new Bucket(r.getString("category"),r.getString("type"),r.getLong("n"),r.getBigDecimal("amount"),r.getObject("oldest",OffsetDateTime.class)),args.toArray());
    }

    public List<Bucket> loans(AppUserPrincipal actor, List<String> statuses,
                              OffsetDateTime before, BigDecimal minimum) {
        if (statuses.isEmpty()) return List.of();
        var args = new ArrayList<Object>(List.of(actor.getSaccoId(),actor.getStationId()));
        args.addAll(statuses); args.add(before); args.add(minimum);
        return jdbc.query("select l.status category,l.status grp,count(*) n,sum(l.amount) amount,min(l.created_at) oldest from loan_applications l where l.sacco_id=? and lower(l.station_id)=lower(?) and l.status in ("
            + String.join(",",Collections.nCopies(statuses.size(),"?")) + ") and l.created_at<=? and l.amount>=?" + " group by l.status",
            (r,n)->new Bucket(r.getString("category"),r.getString("grp"),r.getLong("n"),r.getBigDecimal("amount"),r.getObject("oldest",OffsetDateTime.class)),args.toArray());
    }

}
