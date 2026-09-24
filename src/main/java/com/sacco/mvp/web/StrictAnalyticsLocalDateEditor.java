package com.sacco.mvp.web;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.Locale;

final class StrictAnalyticsLocalDateEditor extends PropertyEditorSupport {
    private static final DateTimeFormatter DISPLAY_FORMAT = new DateTimeFormatterBuilder()
        .appendPattern("dd/MM/uuuu")
        .toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public void setAsText(String text) {
        if (text == null || text.isBlank()) {
            setValue(null);
            return;
        }
        String value = text.trim();
        try {
            setValue(LocalDate.parse(value, DISPLAY_FORMAT));
        } catch (java.time.format.DateTimeParseException ignored) {
            setValue(LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE));
        }
    }

    static String format(LocalDate date) {
        return date == null ? "" : DISPLAY_FORMAT.format(date);
    }
}
