package com.sacco.mvp.accounting.reconciliation.repository;

import com.sacco.mvp.accounting.reconciliation.dto.ReconciliationReport.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Repository @RequiredArgsConstructor
public class ReconciliationRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String SOURCE = source();
    private static final String ORDER = """
        ORDER BY CASE component WHEN 'PRINCIPAL' THEN 0 WHEN 'ALLOWANCE' THEN 1 WHEN 'INTEREST_COLLECTIONS' THEN 2 WHEN 'INTEREST_RECEIVABLE' THEN 3 ELSE 4 END,
          CASE section WHEN 'MOVEMENT' THEN 0 WHEN 'OPENING' THEN 1 WHEN 'CLOSING' THEN 2 ELSE 3 END
        """;
    private static String source() {
        try { return new ClassPathResource("accounting/sl-gl-reconciliation.sql").getContentAsString(StandardCharsets.UTF_8); }
        catch(IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private MapSqlParameterSource parameters(AppUserPrincipal a, Parameters p) {
        return new MapSqlParameterSource().addValue("institution",a.getSaccoId()).addValue("branch",a.getStationId())
            .addValue("from",p.from()).addValue("through",p.through()).addValue("cutoff",p.cutoff())
            .addValue("component",p.component()).addValue("activityId",p.activityId(),java.sql.Types.OTHER)
            .addValue("unassigned",p.unassigned()).addValue("allActivities",p.activityId()==null && !p.unassigned())
            .addValue("offset",Math.multiplyExact(p.page(),25));
    }
    public List<Activity> activities(AppUserPrincipal actor,String search,int page) {
        return jdbc.query("""
            SELECT id,name,name_sw,system_key FROM gl_activity WHERE sacco_id=:institution
              AND (strpos(lower(name),:search)>0 OR strpos(lower(coalesce(name_sw,'')),:search)>0 OR strpos(lower(code),:search)>0)
            ORDER BY name,id LIMIT 26 OFFSET :offset
            """,new MapSqlParameterSource("institution",actor.getSaccoId()).addValue("search",search).addValue("offset",page*25),
            (r,n)->new Activity(r.getObject("id",UUID.class),r.getString("name"),r.getString("name_sw"),r.getString("system_key")));
    }
    public Optional<Activity> activity(AppUserPrincipal actor,UUID id) {
        return jdbc.query("SELECT id,name,name_sw,system_key FROM gl_activity WHERE sacco_id=:institution AND id=:id",
            new MapSqlParameterSource("institution",actor.getSaccoId()).addValue("id",id),
            (r,n)->new Activity(r.getObject("id",UUID.class),r.getString("name"),r.getString("name_sw"),r.getString("system_key"))).stream().findFirst();
    }
    public List<Row> rows(AppUserPrincipal a, Parameters p, boolean export) {
        return jdbc.query(SOURCE+"SELECT * FROM exceptions "+ORDER+",date NULLS LAST,identity,account LIMIT :limit OFFSET :offset",
            parameters(a,p).addValue("limit",export?5001:26).addValue("offset",export?0:p.page()*25),
            (r,n)->new Row(r.getString("component"),r.getString("section"),r.getString("client"),r.getString("loan"),
                r.getString("account"),r.getString("reference"),r.getObject("date",java.time.LocalDate.class),
                r.getString("activity"),r.getString("issue"),r.getBigDecimal("sl_debit"),r.getBigDecimal("sl_credit"),
                r.getBigDecimal("gl_debit"),r.getBigDecimal("gl_credit"),r.getBigDecimal("debit_variance"),r.getBigDecimal("credit_variance"),r.getString("identity")));
    }
    public List<Group> groups(AppUserPrincipal a, Parameters p) {
        return jdbc.query(SOURCE+"""
            SELECT component,section,count(*) n,sum(sl_debit) sd,sum(sl_credit) sc,sum(gl_debit) gd,sum(gl_credit) gc,
              sum(debit_variance) dv,sum(credit_variance) cv FROM exceptions GROUP BY component,section
            """+ORDER,parameters(a,p),(r,n)->new Group(r.getString("component"),r.getString("section"),r.getLong("n"),
                Arrays.asList(r.getBigDecimal("sd"),r.getBigDecimal("sc"),r.getBigDecimal("gd"),r.getBigDecimal("gc"),r.getBigDecimal("dv"),r.getBigDecimal("cv"))));
    }
    public String[] identity(AppUserPrincipal a) {
        return jdbc.queryForObject("""
            SELECT i.sacco_name,coalesce(nullif(s.address_location,''),s.station_id) branch
            FROM registered_saccos i JOIN sacco_stations s ON s.sacco_id=i.sacco_id
            WHERE i.sacco_id=:institution AND s.station_id=:branch
            """,new MapSqlParameterSource().addValue("institution",a.getSaccoId()).addValue("branch",a.getStationId()),
            (r,n)->new String[]{r.getString(1),r.getString(2)});
    }
}
