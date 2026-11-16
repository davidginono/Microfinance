package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.staffoffice.service.StaffOfficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletResponse;

@Controller @RequiredArgsConstructor
public class StaffDashboardController {
    private final StaffOfficeService offices;
    @GetMapping("/staff/dashboard")
    @PreAuthorize("@access.staffDashboardAccess(principal)")
    public String view(@AuthenticationPrincipal AppUserPrincipal actor,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        var dashboard=offices.dashboard(actor);
        model.addAttribute("personalDashboard",dashboard);
        model.addAttribute("dashboard",dashboard.administration());
        model.addAttribute("dashboardPersonalScope",dashboard.layoutScope());
        return "staff/dashboard";
    }
}
