package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationClockTest {
    @Test
    void usesConfiguredNairobiZone() {
        ApplicationClock clock = new ApplicationClock("Africa/Nairobi");

        assertThat(clock.zoneId()).isEqualTo(ZoneId.of("Africa/Nairobi"));
        assertThat(clock.now().getOffset()).isEqualTo(clock.today().atStartOfDay(clock.zoneId()).getOffset());
    }
}
