package com.sacco.mvp.service;

import com.sacco.mvp.domain.Position;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberRoleConstraintInitializer implements ApplicationRunner {
    private static final String MEMBER_POSITION_CONSTRAINT = "members_position_check";
    private static final String STAFF_ROLE_CONSTRAINT = "member_staff_roles_role_name_check";
    private static final String REQUIRED_ROLE = "CHAIRPERSON";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres()) {
            return;
        }
        updateMemberPositionConstraint();
        updateStaffRoleConstraint();
    }

    private void updateMemberPositionConstraint() {
        String constraintDefinition = currentConstraintDefinition("members", MEMBER_POSITION_CONSTRAINT);
        if (constraintDefinition != null && constraintDefinition.contains(REQUIRED_ROLE)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE members DROP CONSTRAINT IF EXISTS " + MEMBER_POSITION_CONSTRAINT);
        jdbcTemplate.execute("""
            ALTER TABLE members
            ADD CONSTRAINT members_position_check
            CHECK (position IN (%s))
            """.formatted(positionSqlValues()));
        log.info("Updated members position check constraint to include configured staff roles.");
    }

    private void updateStaffRoleConstraint() {
        String constraintDefinition = currentConstraintDefinition("member_staff_roles", STAFF_ROLE_CONSTRAINT);
        if (constraintDefinition != null && constraintDefinition.contains(REQUIRED_ROLE)) {
            return;
        }

        jdbcTemplate.execute("ALTER TABLE member_staff_roles DROP CONSTRAINT IF EXISTS " + STAFF_ROLE_CONSTRAINT);
        jdbcTemplate.execute("""
            ALTER TABLE member_staff_roles
            ADD CONSTRAINT member_staff_roles_role_name_check
            CHECK (role_name IN (%s))
            """.formatted(positionSqlValues()));
        log.info("Updated member_staff_roles role_name check constraint to include configured staff roles.");
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to inspect database product", ex);
        }
    }

    private String currentConstraintDefinition(String tableName, String constraintName) {
        return jdbcTemplate.query("""
            select pg_get_constraintdef(c.oid)
            from pg_constraint c
            join pg_class t on t.oid = c.conrelid
            where t.relname = ?
              and c.conname = ?
            """, rs -> rs.next() ? rs.getString(1) : null, tableName, constraintName);
    }

    private String positionSqlValues() {
        return Arrays.stream(Position.values())
            .map(position -> "'" + position.name() + "'")
            .collect(Collectors.joining(", "));
    }
}
