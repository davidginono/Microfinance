package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictAnalyticsLocalDateEditorTest {

    @Test
    void parsesDisplayAndLegacyIsoFormats() {
        StrictAnalyticsLocalDateEditor editor = new StrictAnalyticsLocalDateEditor();

        editor.setAsText("10/07/2025");
        assertThat(editor.getValue()).isEqualTo(LocalDate.of(2025, 7, 10));

        editor.setAsText("2025-07-10");
        assertThat(editor.getValue()).isEqualTo(LocalDate.of(2025, 7, 10));
    }

    @Test
    void rejectsInvalidCalendarDates() {
        StrictAnalyticsLocalDateEditor editor = new StrictAnalyticsLocalDateEditor();

        assertThatThrownBy(() -> editor.setAsText("31/02/2025"))
            .isInstanceOf(java.time.format.DateTimeParseException.class);
    }
}
