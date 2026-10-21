package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.StaffDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller @RequiredArgsConstructor
public class StaffDashboardController {
    private final StaffDashboardService dashboard;
    @GetMapping("/staff/dashboard")
    @PreAuthorize("@access.staffDashboardAccess(principal)")
    public String view(@AuthenticationPrincipal AppUserPrincipal actor,Model model){
        var result=dashboard.read(actor);model.addAttribute("staffDashboard",result);
        model.addAttribute("dashboard",result.administration());return "staff/dashboard";
    }
}
