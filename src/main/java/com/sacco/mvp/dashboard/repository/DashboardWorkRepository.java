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

    public List<Bucket> loans(AppUserPrincipal actor, List<String> statuses, boolean owner, String reviewStage,
                              OffsetDateTime before, BigDecimal minimum) {
        if (statuses.isEmpty()) return List.of();
        var args = new ArrayList<Object>(List.of(actor.getSaccoId(),actor.getStationId()));
        args.addAll(statuses); args.add(before); args.add(minimum);
        String scope = "";
        if (owner) { scope += " and l.applicant_member_id=?"; args.add(actor.getMemberId()); }
        if (reviewStage != null) {
            scope += " and exists(select 1 from board_reviews r where r.loan_application_id=l.id and r.board_member_id=? and r.review_stage=? and r.decision='PENDING')";
            args.add(actor.getMemberId()); args.add(reviewStage);
        }
        return jdbc.query("select l.status category,l.status grp,count(*) n,sum(l.amount) amount,min(l.created_at) oldest from loan_applications l where l.sacco_id=? and lower(l.station_id)=lower(?) and l.status in ("
            + String.join(",",Collections.nCopies(statuses.size(),"?")) + ") and l.created_at<=? and l.amount>=?" + scope + " group by l.status",
            (r,n)->new Bucket(r.getString("category"),r.getString("grp"),r.getLong("n"),r.getBigDecimal("amount"),r.getObject("oldest",OffsetDateTime.class)),args.toArray());
    }

    public List<Bucket> guarantees(AppUserPrincipal actor, OffsetDateTime before) {
        return jdbc.query("""
            select g.status category,g.status grp,count(*) n,null::numeric amount,min(g.created_at) oldest
            from guarantor_requests g join loan_applications l on l.id=g.loan_application_id
            where l.sacco_id=? and lower(l.station_id)=lower(?) and g.guarantor_member_id=? and g.status='PENDING' and g.created_at<=?
              and not exists(select 1 from loan_applications newer where newer.top_up_source_loan_id=l.id and newer.status<>'DRAFT')
            group by g.status
            """,(r,n)->new Bucket(r.getString("category"),r.getString("grp"),r.getLong("n"),null,r.getObject("oldest",OffsetDateTime.class)),actor.getSaccoId(),actor.getStationId(),actor.getMemberId(),before);
    }
}
