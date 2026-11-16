package com.sacco.mvp.staffoffice.controller;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.staffoffice.dto.PersonalDashboard;
import com.sacco.mvp.staffoffice.service.StaffOfficeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/dashboard/activities")
public class PersonalDashboardApiController {
    private final StaffOfficeService offices;
    @GetMapping("/{source}")
    @PreAuthorize("@access.staffDashboardAccess(principal)")
    public PersonalDashboard.Summary summary(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable String source,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return offices.activity(actor,source);
    }
}
