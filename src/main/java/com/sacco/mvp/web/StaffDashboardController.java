package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.WorkspaceLanding;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaffDashboardController {
    @GetMapping("/staff/dashboard")
    @PreAuthorize("@access.staffDashboardAccess(principal)")
    public String view(@AuthenticationPrincipal AppUserPrincipal actor){
        return "redirect:"+WorkspaceLanding.staffDashboard(actor);
    }
}
