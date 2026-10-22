package com.sacco.mvp.dashboard.controller;

import com.sacco.mvp.dashboard.service.DashboardWorkService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController @RequiredArgsConstructor @RequestMapping("/api/dashboard/work")
public class DashboardWorkController {
    private final DashboardWorkService work;
    @GetMapping
    @PreAuthorize("principal.stationId != null and principal.stationId != '' and (@access.canAccessMemberArea(principal) or @access.staffDashboardAccess(principal) or (principal.staffSession and @access.has(principal, 'VOUCHER_WORKSPACE_VIEW')))")
    public ResponseEntity<DashboardWorkService.Work> read(@AuthenticationPrincipal AppUserPrincipal actor,
        @RequestParam(defaultValue="0") int days,@RequestParam(defaultValue="0") BigDecimal amount) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(work.read(actor,days,amount));
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public String invalid() { return "Invalid dashboard display filters"; }
}
