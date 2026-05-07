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
        String allowedStaffRoles = Arrays.stream(Position.values())
            .filter(Position::isStaffRole)
            .map(Position::name)
            .map(position -> "'" + position + "'")
            .collect(Collectors.joining(","));
        String allowedMemberStatuses = Arrays.stream(MemberStatus.values())
            .map(MemberStatus::name)
            .map(status -> "'" + status + "'")
            .collect(Collectors.joining(","));

        jdbcTemplate.execute("alter table if exists members drop constraint if exists members_position_check");
        jdbcTemplate.execute("alter table if exists member_staff_roles drop constraint if exists member_staff_roles_role_name_check");
        migrateLegacyRoleNames();
        jdbcTemplate.execute("""
            alter table if exists members
            add constraint members_position_check
            check (position in (%s))
            """.formatted(allowedPositions));
        jdbcTemplate.execute("""
            alter table if exists member_staff_roles
            add constraint member_staff_roles_role_name_check
            check (role_name in (%s))
            """.formatted(allowedStaffRoles));
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
                'MANAGER_REJECTED','MANAGER_ACCEPTED','AWAITING_LOAN_OFFICER','LOAN_OFFICER_REJECTED',
                'LOAN_OFFICER_APPROVED','AWAITING_BOARD','BOARD_REJECTED','BOARD_APPROVED',
                'AWAITING_ACCOUNTANT','ACCOUNTANT_REJECTED','ACCOUNTANT_APPROVED','READY_FOR_DISBURSEMENT',
                'FINAL_REJECTED','FINAL_APPROVED','DEFAULTED','PAID'
            ))
            """);
        ensureWorkflowColumns();
        ensureLoanApplicationStations();
        ensureLoanApplicationIdentifiers();
    }

    private void migrateLegacyRoleNames() {
        jdbcTemplate.update("update members set position = 'LOAN_OFFICER' where position = 'CHAIRPERSON'");
        jdbcTemplate.update("update member_staff_roles set role_name = 'LOAN_OFFICER' where role_name = 'CHAIRPERSON'");
    }

    private void ensureWorkflowColumns() {
        jdbcTemplate.execute("""
            alter table if exists sacco_settings
            add column if not exists loan_officer_review_required boolean
            """);
        jdbcTemplate.execute("""
            alter table if exists sacco_settings
            add column if not exists board_review_required boolean
            """);
        jdbcTemplate.execute("""
            update sacco_settings
            set loan_officer_review_required = coalesce(loan_officer_review_required, false),
                board_review_required = coalesce(board_review_required, true)
            """);
        jdbcTemplate.execute("""
            alter table if exists board_reviews
            add column if not exists review_stage varchar(64)
            """);
        jdbcTemplate.execute("""
            update board_reviews
            set review_stage = coalesce(review_stage, 'BOARD')
            """);
        jdbcTemplate.execute("""
            alter table if exists board_reviews
            alter column review_stage set not null
            """);
        jdbcTemplate.execute("""
            alter table if exists manager_reviews
            add column if not exists review_stage varchar(64)
            """);
        jdbcTemplate.execute("""
            update manager_reviews
            set review_stage = coalesce(review_stage, 'MANAGER')
            """);
        jdbcTemplate.execute("""
            alter table if exists manager_reviews
            alter column review_stage set not null
            """);
    }

    private void ensureLoanApplicationStations() {
        jdbcTemplate.execute("""
            alter table if exists loan_applications
            add column if not exists station_id varchar(64)
            """);
        jdbcTemplate.execute("""
            update loan_applications la
            set station_id = nullif(trim(m.station_id), '')
            from members m
            where la.applicant_member_id = m.id
              and (la.station_id is null or trim(la.station_id) = '')
            """);
        jdbcTemplate.execute("""
            update loan_applications la
            set station_id = nullif(trim(ss.external_station_id), '')
            from sacco_settings ss
            where la.sacco_id = ss.sacco_id
              and (la.station_id is null or trim(la.station_id) = '')
            """);
        jdbcTemplate.execute("""
            update loan_applications la
            set station_id = fallback.station_id
            from (
                select sacco_id, min(station_id) as station_id
                from sacco_stations
                where active = true
                  and station_id is not null
                  and trim(station_id) <> ''
                group by sacco_id
            ) fallback
            where la.sacco_id = fallback.sacco_id
              and (la.station_id is null or trim(la.station_id) = '')
            """);
        jdbcTemplate.execute("""
            create index if not exists ix_loan_applications_sacco_station
                on loan_applications (sacco_id, station_id)
            """);
        Integer missingStations = jdbcTemplate.queryForObject("""
            select count(*)
            from loan_applications
            where station_id is null or trim(station_id) = ''
            """, Integer.class);
        if (missingStations != null && missingStations == 0) {
            jdbcTemplate.execute("""
                alter table if exists loan_applications
                alter column station_id set not null
                """);
        }
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
