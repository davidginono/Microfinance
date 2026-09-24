package com.sacco.mvp.config;

import com.sacco.mvp.service.AppUsageAnalyticsService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppUsageSessionListener implements HttpSessionListener {
    private static final Logger log = LoggerFactory.getLogger(AppUsageSessionListener.class);

    private final AppUsageAnalyticsService usageAnalyticsService;

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        if (event != null && event.getSession() != null) {
            try {
                usageAnalyticsService.endSession(event.getSession().getId());
            } catch (RuntimeException ex) {
                log.warn("App usage session cleanup failed for {}", event.getSession().getId(), ex);
            }
        }
    }
}
