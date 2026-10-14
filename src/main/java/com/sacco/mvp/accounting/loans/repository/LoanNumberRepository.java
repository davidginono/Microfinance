package com.sacco.mvp.accounting.loans.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @RequiredArgsConstructor
public class LoanNumberRepository {
    private final JdbcTemplate jdbc;
    public long next() {
        return jdbc.queryForObject("select nextval('loan_disbursement_number_seq')", Long.class);
    }
}
