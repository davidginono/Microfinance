package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.UserOtpPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/account/security")
@RequiredArgsConstructor
public class AccountSecurityController {
    private final UserOtpPreferenceService userOtpPreferenceService;

    @GetMapping
    public String security(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        UserOtpPreferenceService.OtpSecuritySettings settings =
            userOtpPreferenceService.current(principal.getMemberId());
        model.addAttribute("otpPreferences", settings.preferences());
        model.addAttribute("otpSelectionPolicy", settings.selectionPolicy());
        return "account/security";
    }

    @PostMapping("/preferences")
    public String updatePreferences(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @RequestParam(defaultValue = "false") boolean loginOtpEnabled,
                                    @RequestParam(defaultValue = "false") boolean approvalOtpEnabled,
                                    @RequestParam(required = false) String currentPassword,
                                    @RequestParam(required = false) String otpCode,
                                    RedirectAttributes ra) {
        try {
            userOtpPreferenceService.update(
                principal.getMemberId(),
                loginOtpEnabled,
                approvalOtpEnabled,
                currentPassword,
                otpCode
            );
            ra.addFlashAttribute("message", "OTP security preferences updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/account/security";
    }

    @PostMapping("/request-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestConfirmationOtp(
        @AuthenticationPrincipal AppUserPrincipal principal
    ) {
        try {
            EmailOtpService.OtpIssueResult result =
                userOtpPreferenceService.requestConfirmationOtp(principal.getMemberId());
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", result.deliveryReceipt().userMessage(),
                "resendAttemptsRemaining", result.resendAttemptsRemaining()
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", ex.getMessage()));
        }
    }
}
