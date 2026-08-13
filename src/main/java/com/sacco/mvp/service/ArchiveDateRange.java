package com.sacco.mvp.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

public record ArchiveDateRange(
    LocalDate fromDate,
    LocalDate toDate,
    OffsetDateTime fromInclusive,
    OffsetDateTime toExclusive
) {
    public static ArchiveDateRange inclusive(LocalDate fromDate,
                                             LocalDate toDate,
                                             ApplicationClock applicationClock) {
        Objects.requireNonNull(applicationClock, "applicationClock");
        LocalDate normalizedFrom = fromDate;
        LocalDate normalizedTo = toDate;
        if (normalizedFrom != null && normalizedTo != null && normalizedFrom.isAfter(normalizedTo)) {
            normalizedFrom = toDate;
            normalizedTo = fromDate;
        }
        return new ArchiveDateRange(
            normalizedFrom,
            normalizedTo,
            applicationClock.startOfDay(normalizedFrom),
            applicationClock.dayAfter(normalizedTo)
        );
    }
}
