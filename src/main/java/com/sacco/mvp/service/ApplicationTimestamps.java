package com.sacco.mvp.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Presentation conversion only: never changes the persisted instant. */
public final class ApplicationTimestamps {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");

    private ApplicationTimestamps() { }

    public static ZonedDateTime zoned(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(ZoneId.systemDefault());
    }

    /** Local dates are calendar dates: never shift them through a time zone. */
    public static String date(Object value) {
        if(value==null)return "";
        if(value instanceof java.time.LocalDate date)return date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"));
        if(value instanceof java.sql.Date date)return date(date.toLocalDate());
        try{return date(java.time.LocalDate.parse(value.toString()));}catch(DateTimeParseException ignored){return value.toString();}
    }

    /** Typed report cells and computed date-group headings, without changing stored query keys. */
    public static String display(Object value) {
        if(value instanceof java.time.LocalDate || value instanceof java.sql.Date)return date(value);
        if(value instanceof OffsetDateTime || value instanceof ZonedDateTime || value instanceof Instant || value instanceof java.util.Date)return format(value);
        if(value==null)return "";
        String text=value.toString();
        if(text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}(?: .*|T.*)?")){
            String first=date(text.substring(0,10));
            if(!first.equals(text.substring(0,10)))return first+text.substring(10);
        }
        return text;
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
                return display(value);
            }
        }
        return DISPLAY.format(instant.atZone(ZoneId.systemDefault()));
    }
}
