package com.sacco.mvp.config;

import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
@Order(0)
@RequiredArgsConstructor
public class DatabaseConstraintUpdater implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        String allowedPositions = Arrays.stream(Position.values())
            .map(Position::name)
            .map(position -> "'" + position + "'")
            .collect(Collectors.joining(","));
        String allowedMemberStatuses = Arrays.stream(MemberStatus.values())
            .map(MemberStatus::name)
            .map(status -> "'" + status + "'")
            .collect(Collectors.joining(","));

        jdbcTemplate.execute("alter table if exists members drop constraint if exists members_position_check");
        jdbcTemplate.execute("""
            alter table if exists members
            add constraint members_position_check
            check (position in (%s))
            """.formatted(allowedPositions));
        jdbcTemplate.execute("alter table if exists members drop constraint if exists members_status_check");
        jdbcTemplate.execute("""
            alter table if exists members
            add constraint members_status_check
            check (status in (%s))
            """.formatted(allowedMemberStatuses));
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
                'FINAL_REJECTED','FINAL_APPROVED','DEFAULTED','PAID'
            ))
            """);
        ensureLoanApplicationIdentifiers();
    }

    private void ensureLoanApplicationIdentifiers() {
        jdbcTemplate.execute("""
            alter table if exists loan_applications
            add column if not exists application_number bigint
            """);
        jdbcTemplate.execute("""
            alter table if exists loan_applications
            add column if not exists loan_id varchar(20)
            """);
        jdbcTemplate.execute("""
            with numbered as (
                select id,
                       100000 + row_number() over (
                           partition by sacco_id
                           order by created_at, id
                       ) as new_number
                from loan_applications
            )
            update loan_applications la
            set application_number = n.new_number
            from numbered n
            where la.id = n.id
              and la.application_number is null
            """);
        jdbcTemplate.execute("""
            create table if not exists sacco_loan_app_counter (
                sacco_id varchar(64) primary key,
                last_number bigint not null
            )
            """);
        jdbcTemplate.execute("""
            insert into sacco_loan_app_counter (sacco_id, last_number)
            select sacco_id, coalesce(max(application_number), 100000)
            from loan_applications
            group by sacco_id
            on conflict (sacco_id) do update
                set last_number = greatest(sacco_loan_app_counter.last_number, excluded.last_number)
            """);
        jdbcTemplate.execute("""
            alter table if exists loan_applications
            alter column application_number set not null
            """);
        jdbcTemplate.execute("""
            create unique index if not exists ux_loan_applications_sacco_appnum
                on loan_applications (sacco_id, application_number)
            """);
        jdbcTemplate.execute("""
            create unique index if not exists ux_loan_applications_sacco_loanid
                on loan_applications (sacco_id, loan_id)
                where loan_id is not null
            """);
    }
}
