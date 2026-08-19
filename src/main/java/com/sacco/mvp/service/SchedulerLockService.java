package com.sacco.mvp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerLockService {
    static final long REPAYMENT_REMINDERS = 48_001L;
    static final long OPERATIONAL_RETENTION = 48_002L;
    static final long DATABASE_UTILIZATION = 48_003L;
    static final long INVITATION_CLEANUP = 48_004L;

    private final JdbcTemplate jdbcTemplate;

    public void runExclusive(long lockKey, Runnable task) {
        Boolean ran = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            if (!tryLock(connection, lockKey)) {
                return false;
            }
            try {
                task.run();
                return true;
            } finally {
                unlock(connection, lockKey);
            }
        });
        if (!Boolean.TRUE.equals(ran)) {
            log.debug("Skipped scheduler work; lock {} is held", lockKey);
        }
    }

    private boolean tryLock(java.sql.Connection connection, long lockKey) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            statement.setLong(1, lockKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private void unlock(java.sql.Connection connection, long lockKey) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, lockKey);
            statement.executeQuery().close();
        }
    }
}
