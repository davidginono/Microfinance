package com.sacco.mvp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
@RequiredArgsConstructor
public class DatabaseConstraintUpdater implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("alter table if exists members drop constraint if exists members_position_check");
        jdbcTemplate.execute("""
            alter table if exists members
            add constraint members_position_check
            check (position in ('MEMBER','MANAGER','BOARD','CHAIRPERSON','ADMIN'))
            """);
        jdbcTemplate.update("""
            update loan_applications
            set status = case
                when coalesce(required_guarantors, 0) > 0 then 'AWAITING_GUARANTORS'
                else 'READY_FOR_MANAGER'
            end
            where status = 'SUBMITTED'
            """);
        jdbcTemplate.execute("alter table if exists loan_applications drop constraint if exists loan_applications_status_check");
        jdbcTemplate.execute("""
            alter table if exists loan_applications
            add constraint loan_applications_status_check
            check (status in (
                'DRAFT','SUBMITTED','AWAITING_GUARANTORS','ALL_GUARANTORS_APPROVED','READY_FOR_MANAGER',
                'MANAGER_REJECTED','MANAGER_ACCEPTED','AWAITING_BOARD','BOARD_REJECTED','BOARD_APPROVED',
                'FINAL_REJECTED','FINAL_APPROVED','PAID'
            ))
            """);
    }
}
