package com.sacco.mvp.web;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.service.MinorAdminInvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/auth/claim")
@Slf4j
public class AuthClaimController {
    private final MinorAdminInvitationService invitationService;

    @GetMapping
    public String showClaim(@RequestParam(value = "token", required = false) String token,
                            Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("claimError", "This activation link is missing its token.");
            model.addAttribute("claimToken", "");
            model.addAttribute("maskedEmail", "");
            return "auth/claim";
        }
        try {
            MinorAdminInvitationService.ClaimContext ctx = invitationService.loadClaimContext(token);
            Member member = ctx.member();
            model.addAttribute("claimToken", token);
            model.addAttribute("memberFullName", member.getFullName());
            model.addAttribute("memberSaccoId", member.getSaccoId());
            model.addAttribute("memberStationId", member.getStationId());
            model.addAttribute("maskedEmail", maskEmail(member.getEmail()));
            model.addAttribute("invitationExpiresAt", ctx.invitation().getExpiresAt());
        } catch (IllegalStateException ex) {
            model.addAttribute("claimError", ex.getMessage());
            model.addAttribute("claimToken", "");
            model.addAttribute("maskedEmail", "");
        }
        return "auth/claim";
    }

    @PostMapping("/request-otp")
    public String requestOtp(@RequestParam("token") String token,
                             RedirectAttributes ra) {
        try {
            invitationService.requestOtp(token);
            ra.addFlashAttribute("claimMessage", "A one-time code was sent to the email on file.");
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("claimError", ex.getMessage());
        }
        return "redirect:/auth/claim?token=" + token;
    }

    @PostMapping("/verify")
    public String verify(@RequestParam("token") String token,
                         @RequestParam("otpCode") String otpCode,
                         @RequestParam("password") String password,
                         @RequestParam("confirmPassword") String confirmPassword,
                         RedirectAttributes ra) {
        try {
            if (password == null || password.length() < 8) {
                throw new IllegalStateException("Password must be at least 8 characters.");
            }
            if (!password.equals(confirmPassword)) {
                throw new IllegalStateException("Passwords do not match.");
            }
            Member member = invitationService.claimInvitation(token, otpCode, password);
            log.info("Staff account activated memberNo={}", member.getMemberNo());
            ra.addFlashAttribute("loginMessage",
                "Your account is now active. Sign in using your staff member number and password or request an email code.");
            return "redirect:/login?claimed";
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("claimError", ex.getMessage());
            return "redirect:/auth/claim?token=" + token;
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "*".repeat(Math.max(1, email.length() - 2)) + email.substring(Math.max(0, email.length() - 2));
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.length() <= 2
            ? local.substring(0, 1)
            : local.substring(0, 2);
        return visible + "***" + domain;
    }
}
