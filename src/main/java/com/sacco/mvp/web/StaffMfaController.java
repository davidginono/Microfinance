package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.WorkspaceLanding;
import com.sacco.mvp.service.StaffMfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hosts the Step-up MFA challenge after a privileged staff password login.
 * Until this OTP is consumed, no SecurityContext is persisted — the user
 * cannot reach any authenticated route.
 */
@Controller
@RequiredArgsConstructor
public class StaffMfaController {
    private final StaffMfaService staffMfaService;

    @GetMapping("/login/staff/mfa")
    public String challenge(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (!staffMfaService.hasPendingChallenge(session)) {
            return "redirect:/login";
        }
        model.addAttribute("maskedEmail", maskEmail(staffMfaService.pendingEmail(session)));
        return "auth/staff-mfa";
    }

    @PostMapping("/login/staff/mfa/resend")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resend(HttpServletRequest request) {
        try {
            staffMfaService.resendChallenge(request);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "We sent a new verification code."
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/login/staff/mfa/verify")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verify(@RequestParam String otpCode,
                                                      HttpServletRequest request) {
        if (otpCode == null || otpCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "Enter the verification code from your email."
            ));
        }
        try {
            HttpSession sessionBefore = request.getSession(false);
            String landing = staffMfaService.pendingLanding(sessionBefore);
            AppUserPrincipal principal = staffMfaService.completeChallenge(otpCode.trim(), request);
            String redirectUrl = landing != null && !landing.isBlank()
                ? landing
                : WorkspaceLanding.staffDashboard(principal);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("valid", true);
            payload.put("redirectUrl", redirectUrl);
            return ResponseEntity.ok(payload);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/login/staff/mfa/cancel")
    public String cancel(HttpServletRequest request) {
        staffMfaService.clear(request);
        return "redirect:/login";
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "your email on file";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + domain;
    }
}
