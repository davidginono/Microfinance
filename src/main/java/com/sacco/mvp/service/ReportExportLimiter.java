package com.sacco.mvp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
public class ReportExportLimiter {
    private final Semaphore slots;

    public ReportExportLimiter(
        @Value("${app.reports.max-concurrent-exports:1}") int maxConcurrentExports
    ) {
        this.slots = new Semaphore(Math.max(1, maxConcurrentExports));
    }

    public <T> T run(Supplier<T> work) {
        if (!tryAcquire()) {
            throw busy();
        }
        try {
            return work.get();
        } finally {
            release();
        }
    }

    public boolean tryAcquire() {
        return slots.tryAcquire();
    }

    public void release() {
        slots.release();
    }

    public static ResponseStatusException busy() {
        return new ResponseStatusException(
            HttpStatus.TOO_MANY_REQUESTS,
            "A report is already being generated. Try again shortly."
        );
    }
}
