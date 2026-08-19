package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportExportLimiterTest {
    @Test
    void runAllowsConfiguredConcurrencyThenRejects() {
        ReportExportLimiter limiter = new ReportExportLimiter(1);

        assertThat(limiter.run(() -> "ok")).isEqualTo("ok");
        assertThat(limiter.tryAcquire()).isTrue();
        assertThatThrownBy(() -> limiter.run(() -> "blocked"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already being generated");
        limiter.release();
        assertThat(limiter.run(() -> "ok-again")).isEqualTo("ok-again");
    }
}
