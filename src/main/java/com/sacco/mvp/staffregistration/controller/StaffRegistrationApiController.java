package com.sacco.mvp.staffregistration.controller;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users/contact-availability")
public class StaffRegistrationApiController {
    private final AdminService adminService;

    @PostMapping
    @PreAuthorize("@authz.workspaceAdminOnly(principal) and @access.has(principal, 'USER_ACCESS_CREATE')")
    public ResponseEntity<Map<String, Boolean>> check(@AuthenticationPrincipal AppUserPrincipal principal,
                                                     @RequestParam String field, @RequestParam String value) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("available", adminService.staffContactAvailable(principal, field, value)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of());
        }
    }
}
