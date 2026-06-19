package com.sacco.mvp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Service
public class ApplicationClock {
    private final ZoneId zoneId;

    public ApplicationClock(@Value("${app.time-zone:Africa/Nairobi}") String zoneId) {
        this.zoneId = ZoneId.of(zoneId);
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(zoneId);
    }

    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    public YearMonth currentYearMonth() {
        return YearMonth.now(zoneId);
    }

    public OffsetDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(zoneId).toOffsetDateTime();
    }

    public OffsetDateTime dayAfter(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();
    }

    public OffsetDateTime endOfDay(LocalDate date) {
        OffsetDateTime nextDay = dayAfter(date);
        return nextDay == null ? null : nextDay.minusNanos(1);
    }

    public ZonedDateTime zoned(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(zoneId);
    }
}
