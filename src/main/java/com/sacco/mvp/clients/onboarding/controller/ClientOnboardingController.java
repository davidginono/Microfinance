package com.sacco.mvp.clients.onboarding.controller;

import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ClientOnboardingController {
    private final OnboardingService service;
    @GetMapping("/app/onboarding")
    public String status(@AuthenticationPrincipal AppUserPrincipal a,Model model) {
        model.addAttribute("onboardingStatus",service.ownStatus(a));return "onboarding/status";
    }
}
