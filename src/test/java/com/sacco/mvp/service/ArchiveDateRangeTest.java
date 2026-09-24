package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveDateRangeTest {

    private final ApplicationClock applicationClock = new ApplicationClock("Africa/Nairobi");

    @Test
    void inclusiveRangeUsesApplicationTimeZoneAndExclusiveFollowingDay() {
        ArchiveDateRange range = ArchiveDateRange.inclusive(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 13),
            applicationClock
        );

        assertThat(range.fromInclusive()).isEqualTo(OffsetDateTime.parse("2026-08-01T00:00+03:00"));
        assertThat(range.toExclusive()).isEqualTo(OffsetDateTime.parse("2026-08-14T00:00+03:00"));
    }

    @Test
    void reversedDatesAreNormalizedBeforeQuerying() {
        ArchiveDateRange range = ArchiveDateRange.inclusive(
            LocalDate.of(2026, 8, 13),
            LocalDate.of(2026, 8, 1),
            applicationClock
        );

        assertThat(range.fromDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(range.toDate()).isEqualTo(LocalDate.of(2026, 8, 13));
    }

    @Test
    void missingBoundRemainsOpenEnded() {
        ArchiveDateRange range = ArchiveDateRange.inclusive(null, LocalDate.of(2026, 8, 13), applicationClock);

        assertThat(range.fromInclusive()).isNull();
        assertThat(range.toExclusive()).isEqualTo(OffsetDateTime.parse("2026-08-14T00:00+03:00"));
    }
}
