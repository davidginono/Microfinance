package com.sacco.mvp.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Presentation conversion only: never changes the persisted instant. */
public final class ApplicationTimestamps {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ApplicationTimestamps() { }

    public static ZonedDateTime zoned(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(ZoneId.systemDefault());
    }

    public static String format(Object value) {
        if (value == null) {
            return "";
        }
        Instant instant;
        if (value instanceof OffsetDateTime timestamp) {
            instant = timestamp.toInstant();
        } else if (value instanceof ZonedDateTime timestamp) {
            instant = timestamp.toInstant();
        } else if (value instanceof Instant timestamp) {
            instant = timestamp;
        } else if (value instanceof java.util.Date timestamp) {
            instant = timestamp.toInstant();
        } else {
            try {
                instant = OffsetDateTime.parse(value.toString()).toInstant();
            } catch (DateTimeParseException ex) {
                // Legacy labels without an offset cannot safely be shifted.
                return value.toString();
            }
        }
        return DISPLAY.format(instant.atZone(ZoneId.systemDefault()));
    }
}
