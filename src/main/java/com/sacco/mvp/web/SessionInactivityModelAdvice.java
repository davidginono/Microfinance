package com.sacco.mvp.web;

import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.SessionTimeoutPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class SessionInactivityModelAdvice {
    private final PlatformSessionSettingsService platformSessionSettingsService;

    @ModelAttribute("sessionTimeoutMs")
    public long sessionTimeoutMs() {
        return policy().timeoutMs();
    }

    @ModelAttribute("sessionTimeoutWarningMs")
    public long sessionTimeoutWarningMs() {
        return policy().warningMs();
    }

    private SessionTimeoutPolicy policy() {
        return platformSessionSettingsService.policy();
    }
}
