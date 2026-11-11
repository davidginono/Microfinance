package com.sacco.mvp.web;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.WorkspaceLanding;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.UserClaimService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final com.sacco.mvp.service.SaccoRegistryService saccoRegistryService;
    private final EmailOtpService emailOtpService;
    private final MemberDirectoryService memberDirectoryService;
    private final UserClaimService userClaimService;
    private final AdminScopeService adminScopeService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @GetMapping("/")
    public String root(@org.springframework.security.core.annotation.AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        return "redirect:" + WorkspaceLanding.authenticatedDefault(principal);
    }

    @GetMapping({"/login", "/system-admin/login"})
    public String login(Model model, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object loginErrorMessage = session.getAttribute("loginErrorMessage");
            if (loginErrorMessage instanceof String message && !message.isBlank()) {
                model.addAttribute("errorMessage", message);
                session.removeAttribute("loginErrorMessage");
            }
        }
        model.addAttribute("systemAdminLogin", "/system-admin/login".equals(request.getRequestURI()));
        return "login";
    }

    @PostMapping("/login/password-reset/request-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestPasswordResetOtp(@RequestParam String username,
                                                                       @RequestParam String accountType) {
        String normalizedUsername = normalizeMemberNo(username);
        if (normalizedUsername.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Enter your sign-in identifier."));
        }
        try {
            Member member = findPasswordResetAccount(normalizedUsername, accountType);
            String registeredEmail = normalizeEmail(member.getEmail());
            if (registeredEmail.isBlank()) {
                throw new IllegalStateException("No registered email is available for this account.");
            }
            var delivery = emailOtpService.issueOtp(
                registeredEmail,
                EmailOtpPurpose.PASSWORD_RESET,
                member.getId(),
                "Your password reset code",
                "Use this OTP code to reset your Microfinance password."
            );
            boolean staffAccount = member.isStaffAccessActive()
                && ("staff".equalsIgnoreCase(accountType) || "system-admin".equalsIgnoreCase(accountType));
            auditIdentityEvent(member.getId(), "PASSWORD_RESET_REQUEST", "Password reset request",
                staffAccount ? "STAFF" : "MEMBER",
                (staffAccount ? "Staff " : "Member ")
                    + displayLoginNumber(member, accountType),
                member.getSaccoId(), member.getStationId(), Map.of("loginNo", displayLoginNumber(member, accountType)));
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", deliveryMessage(delivery, "We sent a password reset code using the station delivery policy.")
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/login/password-reset/verify-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyPasswordResetOtp(@RequestParam String username,
                                                                      @RequestParam String accountType,
                                                                      @RequestParam String otpCode) {
        String normalizedUsername = normalizeMemberNo(username);
        if (normalizedUsername.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Enter your sign-in identifier."));
        }
        try {
            Member member = findPasswordResetAccount(normalizedUsername, accountType);
            emailOtpService.validateOtp(member.getEmail(), EmailOtpPurpose.PASSWORD_RESET, member.getId(), otpCode);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Code verified. Enter your new password."
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/login/password-reset/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> savePasswordReset(@RequestParam String username,
                                                                 @RequestParam String accountType,
                                                                 @RequestParam String otpCode,
                                                                 @RequestParam String password,
                                                                 @RequestParam String confirmPassword) {
        String normalizedUsername = normalizeMemberNo(username);
        String passwordError = passwordValidationMessage(password, confirmPassword);
        if (normalizedUsername.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Enter your sign-in identifier."));
        }
        if (passwordError != null) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", passwordError));
        }
        try {
            Member member = findPasswordResetAccount(normalizedUsername, accountType);
            emailOtpService.consumeOtp(member.getEmail(), EmailOtpPurpose.PASSWORD_RESET, member.getId(), otpCode);
            memberDirectoryService.updatePasswordHash(member, passwordEncoder.encode(password));
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Password updated. You can now sign in with your new password."
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/login/staff/request-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestStaffLoginOtp(@RequestParam String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();

        if (normalizedEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "Enter your staff email address."
            ));
        }

        Member user = memberDirectoryService
            .findByEmail(normalizedEmail)
            .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
            .filter(Member::isStaffAccessActive)
            .filter(existing -> !existing.getActiveStaffRolesResolved().contains(Position.ADMIN))
            .orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "No active staff account matches that email address."
            ));
        }
        String accessBlockedMessage = suspendedAccessMessage(user);
        if (accessBlockedMessage != null) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", accessBlockedMessage
            ));
        }

        try {
            var delivery = emailOtpService.issueOtp(
                normalizedEmail,
                EmailOtpPurpose.STAFF_LOGIN,
                user.getId(),
                "Your staff sign-in code",
                "Use this OTP code to sign in to the Microfinance staff workspace."
            );
            auditIdentityEvent(user.getId(), "OTP_REQUEST", "OTP request", "STAFF", "Staff " + displayStaffNo(user),
                user.getSaccoId(), user.getStationId(), Map.of("purpose", EmailOtpPurpose.STAFF_LOGIN.name(), "staffNo", displayStaffNo(user)));
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", deliveryMessage(delivery, "We sent a sign-in code using the station delivery policy.")
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/login/staff/verify-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyStaffLoginOtp(@RequestParam String email,
                                                                   @RequestParam String otpCode,
                                                                   HttpServletRequest request) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();

        if (normalizedEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "Enter your staff email address."
            ));
        }

        try {
            Member user = memberDirectoryService
                .findByEmail(normalizedEmail)
                .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
                .filter(Member::isStaffAccessActive)
                .filter(existing -> !existing.getActiveStaffRolesResolved().contains(Position.ADMIN))
                .orElseThrow(() -> new IllegalStateException("No active staff account matches that email address."));
            ensureSaccoAccessAllowed(user);
            emailOtpService.consumeOtp(normalizedEmail, EmailOtpPurpose.STAFF_LOGIN, otpCode);
            signInPrincipal(new AppUserPrincipal(user,userClaimService.effectiveClaims(user.getId(), user.getActiveStaffRolesResolved(), user.isMemberAccess())), request);
            auditIdentityEvent(user.getId(), "LOGIN", "Login", "STAFF", "Staff " + displayStaffNo(user),
                user.getSaccoId(), user.getStationId(), Map.of("method", "OTP", "staffNo", displayStaffNo(user)));
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "redirectUrl", WorkspaceLanding.staffDashboard(user)
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    private String deliveryMessage(com.sacco.mvp.service.StationOtpDeliveryService.DeliveryReceipt delivery,
                                   String fallback) {
        return delivery == null || delivery.userMessage() == null || delivery.userMessage().isBlank()
            ? fallback
            : delivery.userMessage();
    }

    private void signInPrincipal(AppUserPrincipal principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private void auditIdentityEvent(java.util.UUID entityId,
                                    String action,
                                    String description,
                                    String referenceType,
                                    String referenceValue,
                                    String saccoId,
                                    String stationId,
                                    Map<String, Object> extraDetails) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", "SUCCESS");
        details.put("saccoId", saccoId);
        details.put("stationId", stationId);
        if (extraDetails != null) {
            details.putAll(extraDetails);
        }
        auditService.logEvent(
            referenceType == null || referenceType.isBlank() ? "MEMBER" : referenceType,
            entityId,
            action,
            entityId,
            AuditEventStatus.SUCCESS,
            description,
            referenceType,
            referenceValue,
            saccoId,
            stationId,
            details
        );
    }

    private void ensureSaccoAccessAllowed(Member member) {
        String message = suspendedAccessMessage(member);
        if (message != null) {
            throw new IllegalStateException(message);
        }
    }

    private String suspendedAccessMessage(Member member) {
        if (member == null || member.getActiveStaffRolesResolved().contains(Position.ADMIN)) {
            return null;
        }
        String saccoId = member.getSaccoId();
        String stationId = member.getStationId();
        if (saccoId == null || saccoId.isBlank() || stationId == null || stationId.isBlank()) {
            return null;
        }
        return saccoRegistryService.findStation(saccoId, stationId)
            .filter(SaccoStation::isAccessSuspended)
            .map(this::suspendedMessage)
            .orElse(null);
    }

    private String suspendedMessage(SaccoStation station) {
        String reason = station.getAccessRestrictionReason();
        if (reason == null || reason.isBlank()) {
            return "This station workspace has been suspended. Contact the platform administrator.";
        }
        return "This station workspace has been suspended: " + reason.trim();
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizeMemberNo(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String passwordValidationMessage(String password, String confirmPassword) {
        if (password == null || password.isBlank()) {
            return "Enter your password.";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters.";
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            return "Confirm your password.";
        }
        if (!password.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        return null;
    }

    private Member findPasswordResetAccount(String normalizedUsername, String accountType) {
        String normalizedType = accountType == null ? "" : accountType.trim().toLowerCase();
        if ("system-admin".equals(normalizedType)) {
            return memberDirectoryService.findPlatformAdminLoginAccount(normalizedUsername)
                .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
                .filter(Member::isStaffAccessActive)
                .orElseThrow(() -> new IllegalStateException("No active System Admin account was found for that ID."));
        }
        if ("staff".equals(normalizedType)) {
            return memberDirectoryService.findStaffLoginAccount(normalizedUsername)
                .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
                .filter(Member::isStaffAccessActive)
                .filter(existing -> !existing.getActiveStaffRolesResolved().contains(Position.ADMIN))
                .orElseThrow(() -> new IllegalStateException("No active staff account was found for that email or username."));
        }
        throw new IllegalStateException("Use Staff or System Admin password recovery.");
    }

    private String displayLoginNumber(Member member, String accountType) {
        return "staff".equalsIgnoreCase(accountType) ? displayStaffNo(member) : member.getMemberNo();
    }

    private String displayStaffNo(Member member) {
        if (member == null || member.getStaffNo() == null || member.getStaffNo().isBlank()) {
            return "";
        }
        return member.getStaffNo();
    }

}
