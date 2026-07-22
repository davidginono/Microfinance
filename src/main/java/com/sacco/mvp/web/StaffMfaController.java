package com.sacco.mvp.web;

import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.WorkspaceLanding;
import com.sacco.mvp.service.AuditService;
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
 * Hosts the MFA challenge after a station-configured password login.
 */
@Controller
@RequiredArgsConstructor
public class StaffMfaController {
    private final StaffMfaService staffMfaService;
    private final AuditService auditService;

    @GetMapping("/login/mfa")
    public String challenge(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (!staffMfaService.hasPendingChallenge(session)) {
            return "redirect:/login";
        }
        StaffMfaService.ChallengeCompletion completion = staffMfaService.continueWithoutChallengeIfNoLongerRequired(request);
        if (completion != null) {
            auditLogin(completion.principal(), "Password login, MFA no longer required");
            return "redirect:" + redirectUrl(completion);
        }
        model.addAttribute("maskedEmail", maskEmail(staffMfaService.pendingEmail(session)));
        model.addAttribute("deliveryMessage", staffMfaService.pendingDeliveryMessage(session));
        return "auth/staff-mfa";
    }

    @GetMapping("/login/staff/mfa")
    public String legacyChallenge() {
        return "redirect:/login/mfa";
    }

    @PostMapping({"/login/mfa/resend", "/login/staff/mfa/resend"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resend(HttpServletRequest request) {
        try {
            StaffMfaService.ChallengeCompletion completion = staffMfaService.continueWithoutChallengeIfNoLongerRequired(request);
            if (completion != null) {
                auditLogin(completion.principal(), "Password login, MFA no longer required");
                return completedChallengeResponse(completion);
            }
            staffMfaService.resendChallenge(request);
            HttpSession session = request.getSession(false);
            String deliveryMessage = staffMfaService.pendingDeliveryMessage(session);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", deliveryMessage == null || deliveryMessage.isBlank()
                    ? "We sent a new verification code."
                    : deliveryMessage
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping({"/login/mfa/send-email", "/login/staff/mfa/send-email"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendEmail(HttpServletRequest request) {
        try {
            StaffMfaService.ChallengeCompletion completion = staffMfaService.continueWithoutChallengeIfNoLongerRequired(request);
            if (completion != null) {
                auditLogin(completion.principal(), "Password login, MFA no longer required");
                return completedChallengeResponse(completion);
            }
            staffMfaService.sendChallengeToEmail(request);
            HttpSession session = request.getSession(false);
            String deliveryMessage = staffMfaService.pendingDeliveryMessage(session);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", deliveryMessage == null || deliveryMessage.isBlank()
                    ? "We sent a new verification code to your registered email."
                    : deliveryMessage,
                "redirectUrl", "/login/mfa"
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping({"/login/mfa/verify", "/login/staff/mfa/verify"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verify(@RequestParam String otpCode,
                                                      HttpServletRequest request) {
        StaffMfaService.ChallengeCompletion completion = staffMfaService.continueWithoutChallengeIfNoLongerRequired(request);
        if (completion != null) {
            auditLogin(completion.principal(), "Password login, MFA no longer required");
            return completedChallengeResponse(completion);
        }
        if (otpCode == null || otpCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "Enter the verification code."
            ));
        }
        try {
            HttpSession sessionBefore = request.getSession(false);
            String landing = staffMfaService.pendingLanding(sessionBefore);
            AppUserPrincipal principal = staffMfaService.completeChallenge(otpCode.trim(), request);
            auditLogin(principal);
            String redirectUrl = landing != null && !landing.isBlank()
                ? landing
                : WorkspaceLanding.authenticatedDefault(principal);
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

    @PostMapping({"/login/mfa/check", "/login/staff/mfa/check"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> check(@RequestParam String otpCode,
                                                     HttpServletRequest request) {
        StaffMfaService.ChallengeCompletion completion = staffMfaService.continueWithoutChallengeIfNoLongerRequired(request);
        if (completion != null) {
            auditLogin(completion.principal(), "Password login, MFA no longer required");
            return completedChallengeResponse(completion);
        }
        if (otpCode == null || otpCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "Enter the verification code."
            ));
        }
        try {
            staffMfaService.validateChallenge(otpCode.trim(), request);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "OTP code verified."
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping({"/login/mfa/cancel", "/login/staff/mfa/cancel"})
    public String cancel(HttpServletRequest request) {
        staffMfaService.clear(request);
        return "redirect:/login";
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "your registered contact";
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

    private void auditLogin(AppUserPrincipal principal) {
        auditLogin(principal, "Password login with MFA");
    }

    private void auditLogin(AppUserPrincipal principal, String method) {
        if (principal == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", "SUCCESS");
        details.put("method", method);
        details.put("saccoId", principal.getSaccoId());
        details.put("stationId", principal.getStationId());
        details.put("memberNo", principal.getUsername());
        auditService.logEvent(
            "STAFF_USER",
            principal.getMemberId(),
            "LOGIN",
            principal.getMemberId(),
            AuditEventStatus.SUCCESS,
            "Login",
            "STAFF",
            "Staff " + principal.getUsername(),
            principal.getSaccoId(),
            principal.getStationId(),
            details
        );
    }

    private ResponseEntity<Map<String, Object>> completedChallengeResponse(StaffMfaService.ChallengeCompletion completion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", true);
        payload.put("message", "Sign-in verification is no longer required for this station.");
        payload.put("redirectUrl", redirectUrl(completion));
        return ResponseEntity.ok(payload);
    }

    private String redirectUrl(StaffMfaService.ChallengeCompletion completion) {
        if (completion == null || completion.principal() == null) {
            return "/";
        }
        return completion.landing() != null && !completion.landing().isBlank()
            ? completion.landing()
            : WorkspaceLanding.authenticatedDefault(completion.principal());
    }
}
