package com.sacco.mvp.accounting.loans.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class LoanNumberRepository {
    private final JdbcTemplate jdbc;
    public long next(String institution, UUID period) {
        return jdbc.queryForObject("""
            insert into loan_period_counter(sacco_id,period_id,last_number) values(?,?,1)
            on conflict(sacco_id,period_id) do update set last_number=loan_period_counter.last_number+1
            returning last_number
            """, Long.class, institution, period);
    }
}
