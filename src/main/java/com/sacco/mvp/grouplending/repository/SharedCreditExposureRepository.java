package com.sacco.mvp.grouplending.repository;

import com.sacco.mvp.grouplending.dto.SharedCreditExposure;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class SharedCreditExposureRepository {
    private final JdbcTemplate jdbc;
    public boolean lock(String institution,String branch,UUID client){return Boolean.TRUE.equals(jdbc.queryForObject("select lending_guarantor_capacity_lock(?,?,?)",Boolean.class,client,institution,branch));}
    public SharedCreditExposure summary(String institution,UUID client,String branch){return jdbc.queryForObject("""
        select count(*) filter(where funded and (maximum_liability>0 or missing_history)) active,
          count(*) filter(where pending) pending,count(*) filter(where status in ('PAR','DEFAULTED','WRITTEN_OFF')) risk,
          count(*) filter(where missing_history)+case when ?::text is not null and exists(
            select 1 from lending_shared_credit_obligation other where other.sacco_id=? and other.client_id=? and other.station_id<>?::text)
            then 1 else 0 end missing,
          coalesce(sum(maximum_liability) filter(where funded),0) liability,
          coalesce(sum(monthly_payment) filter(where funded),0) monthly
        from lending_shared_credit_obligation where sacco_id=? and client_id=? and (?::text is null or station_id=?::text)
        """,(r,n)->new SharedCreditExposure(r.getLong("active"),r.getLong("pending"),r.getLong("risk"),r.getLong("missing"),r.getBigDecimal("liability"),r.getBigDecimal("monthly")),branch,institution,client,branch,institution,client,branch,branch);}
}
