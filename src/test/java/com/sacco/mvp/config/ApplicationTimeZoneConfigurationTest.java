package com.sacco.mvp.config;

import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.ApplicationTimestamps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock("java.util.TimeZone.default")
class ApplicationTimeZoneConfigurationTest {
    private TimeZone previous;

    @BeforeEach
    void simulateUtcServer() {
        previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(previous);
    }

    @Test
    void initializesNairobiBeforeTimestampProducingBeans() {
        try (var context = startApplicationTime()) {
            assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Africa/Nairobi"));
            assertThat(context.getBean(OffsetDateTime.class).getOffset()).isEqualTo(ZoneOffset.ofHours(3));
            assertThat(context.getBean(ApplicationClock.class).now().getOffset()).isEqualTo(ZoneOffset.ofHours(3));
        }
    }

    @Test
    void storedUtcApprovalAndLoginTimesDisplayInNairobiWithoutChangingTheirInstant() {
        try (var context = startApplicationTime()) {
            OffsetDateTime stored = OffsetDateTime.parse("2026-09-09T21:30:00Z");
            assertThat(ApplicationTimestamps.format(stored)).isEqualTo("2026-09-10 00:30");
            assertThat(ApplicationTimestamps.format(stored.toString())).isEqualTo("2026-09-10 00:30");
            assertThat(ApplicationTimestamps.format(stored.toInstant())).isEqualTo("2026-09-10 00:30");
            assertThat(ApplicationTimestamps.zoned(stored).toInstant()).isEqualTo(stored.toInstant());
            assertThat(ApplicationTimestamps.format(stored.withOffsetSameInstant(ZoneOffset.ofHours(3))))
                .isEqualTo("2026-09-10 00:30");
            assertThat(context.getBean(ApplicationClock.class).zoned(stored).toLocalDate().toString())
                .isEqualTo("2026-09-10");
        }
    }

    @Test
    void preservesDateOnlyAndLegacyLabelsAndHandlesEmptyValues() {
        try (var context = startApplicationTime()) {
            assertThat(ApplicationTimestamps.format(null)).isEmpty();
            assertThat(ApplicationTimestamps.format("2026-09-09")).isEqualTo("2026-09-09");
            assertThat(ApplicationTimestamps.format("2026-09-09 09:30")).isEqualTo("2026-09-09 09:30");
            assertThat(ApplicationTimestamps.format("-")).isEqualTo("-");
        }
    }

    private AnnotationConfigApplicationContext startApplicationTime() {
        var context = new AnnotationConfigApplicationContext();
        context.register(ApplicationTimeZoneConfiguration.class, ApplicationClock.class);
        context.registerBean(OffsetDateTime.class, (java.util.function.Supplier<OffsetDateTime>) OffsetDateTime::now);
        context.refresh();
        return context;
    }
}
