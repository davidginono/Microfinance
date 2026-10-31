package com.sacco.mvp.clients.onboarding.repository;

import com.sacco.mvp.clients.onboarding.dto.LoanIntakeDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class LoanIntakeRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public Optional<Request> latest(AppUserPrincipal a,UUID client) {
        return jdbc.query("select id,sequence,mode,details,recorded_at from client_onboarding_loan_request where sacco_id=? and station_id=? and client_id=? order by sequence desc limit 1",
            (r,n)->new Request(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),json.readValue(r.getString(4),Details.class),r.getObject(5,OffsetDateTime.class)),a.getSaccoId(),a.getStationId(),client).stream().findFirst();
    }
    /** Only the monotonic version counter crosses branch history; personal request data stays branch-scoped. */
    public int sequence(AppUserPrincipal a,UUID client) {
        return jdbc.queryForObject("select coalesce(max(sequence),0) from client_onboarding_loan_request where sacco_id=? and client_id=?",Integer.class,a.getSaccoId(),client);
    }
    public void save(AppUserPrincipal a,UUID client,UUID id,Form f,Details details,String hash,OffsetDateTime now) {
        jdbc.update("insert into client_onboarding_loan_request values(?,?,?,?,?,?,?::jsonb,?,?,?,?)",id,client,a.getSaccoId(),a.getStationId(),f.getExpectedSequence()+1,f.getAction(),json.writeValueAsString(details),f.getRequestKey(),hash,a.getMemberId(),now);
    }
    private static final String PRODUCTS="""
        select id,coalesce(product_name,loan_type) label,coalesce(tenure_unit,'MONTHS') unit,minimum_amount,maximum_amount,
          coalesce(min_tenure,case when min_repayment_months>0 then min_repayment_months else 1 end) min_tenure,
          coalesce(max_tenure,max_repayment_months,600) max_tenure
        from loan_product_settings where sacco_id=? and active and not custom_recording_configuration and coalesce(product_status,'ACTIVE')='ACTIVE'
        """;
    private final org.springframework.jdbc.core.RowMapper<Product> productMapper=(r,n)->new Product(r.getObject("id",UUID.class),r.getString("label"),r.getString("unit"),r.getBigDecimal("minimum_amount"),r.getBigDecimal("maximum_amount"),r.getInt("min_tenure"),r.getInt("max_tenure"));
    public List<Product> products(AppUserPrincipal a,String search,int page) {
        return jdbc.query(PRODUCTS+" and strpos(lower(coalesce(product_name,loan_type)),?)>0 order by coalesce(display_order,2147483647),id limit 26 offset ?",productMapper,a.getSaccoId(),search.toLowerCase(Locale.ROOT),page*25);
    }
    public Optional<Product> product(AppUserPrincipal a,UUID id) {
        return jdbc.query(PRODUCTS+" and id=?",productMapper,a.getSaccoId(),id).stream().findFirst();
    }
}
