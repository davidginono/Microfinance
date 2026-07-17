package com.sacco.mvp.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SessionInactivityModelAdvice {
    @Value("${app.session.inactivity-prompt-ms:60000}")
    private long inactivityPromptMs;

    @Value("${app.session.inactivity-grace-ms:60000}")
    private long inactivityGraceMs;

    @ModelAttribute("sessionInactivityPromptMs")
    public long sessionInactivityPromptMs() {
        return inactivityPromptMs;
    }

    @ModelAttribute("sessionInactivityGraceMs")
    public long sessionInactivityGraceMs() {
        return inactivityGraceMs;
    }
}
