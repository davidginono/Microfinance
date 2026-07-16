package com.sacco.mvp.web;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.WorkspaceLanding;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.MemberRegistrationService;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.UserClaimService;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final MemberRegistrationService memberRegistrationService;
    private final EmailOtpService emailOtpService;
    private final MemberRepository memberRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final UserClaimService userClaimService;
    private final SaccoRegistryService saccoRegistryService;
    private final AdminScopeService adminScopeService;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @org.springframework.beans.factory.annotation.Value("${app.auth.google-sso.enabled:false}")
    private boolean googleSsoEnabled;

    @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String googleClientSecret;

    @GetMapping("/")
    public String root(@org.springframework.security.core.annotation.AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        return "redirect:" + WorkspaceLanding.authenticatedDefault(principal);
    }

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object loginErrorMessage = session.getAttribute("loginErrorMessage");
            if (loginErrorMessage instanceof String message && !message.isBlank()) {
                model.addAttribute("errorMessage", message);
                session.removeAttribute("loginErrorMessage");
            }
        }
        model.addAttribute("googleSsoEnabled", isGoogleSsoConfigured());
        return "login";
    }

    @GetMapping("/register/member")
    public String registerMember(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new MemberRegistrationForm());
        }
        populateRegistrationOptions(model);
        return "register-member";
    }

    @PostMapping("/register/member/request-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestMemberRegistrationOtp(@Valid @ModelAttribute("registrationForm") MemberRegistrationForm form,
                                                                            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", bindingResult.getAllErrors().getFirst().getDefaultMessage()
            ));
        }
        String passwordError = passwordValidationMessage(form.getPassword(), form.getConfirmPassword());
        if (passwordError != null) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", passwordError
            ));
        }

        try {
            MemberRegistrationService.VerifiedExternalMember verified = memberRegistrationService.verifyExternalMember(form);
            memberRegistrationService.ensureLocalUniqueness(
                verified.memberNo(),
                verified.email(),
                form.getPhone()
            );
            var delivery = emailOtpService.issueOtp(
                verified.email(),
                EmailOtpPurpose.REGISTRATION,
                null,
                "Your Loan Application Portal registration code",
                "We verified your member details. Use the OTP code below to complete your registration.",
                verified.saccoId(),
                verified.stationId(),
                form.getPhone()
            );
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", true);
            response.put("memberNo", verified.memberNo());
            response.put("fullName", verified.fullName());
            response.put("saccoId", verified.saccoId());
            response.put("stationId", verified.stationId());
            response.put("message", deliveryMessage(delivery, "We sent an OTP code using the station delivery policy."));
            auditIdentityEvent(null, "OTP_REQUEST", "OTP request", "MEMBER", "Member " + verified.memberNo(),
                verified.saccoId(), verified.stationId(), Map.of("purpose", EmailOtpPurpose.REGISTRATION.name(), "memberNo", verified.memberNo()));
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/register/member")
    public String registerMemberSubmit(@Valid @ModelAttribute("registrationForm") MemberRegistrationForm form,
                                       BindingResult bindingResult,
                                       Model model,
                                       RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            populateRegistrationOptions(model);
            return "register-member";
        }
        String passwordError = passwordValidationMessage(form.getPassword(), form.getConfirmPassword());
        if (passwordError != null) {
            bindingResult.rejectValue("password", "registration.password.invalid", passwordError);
            populateRegistrationOptions(model);
            return "register-member";
        }

        try {
            if (form.getOtpCode() == null || form.getOtpCode().isBlank()) {
                bindingResult.rejectValue("otpCode", "registration.otp.required", "Enter the OTP code that was sent to you.");
                populateRegistrationOptions(model);
                return "register-member";
            }
            emailOtpService.consumeOtp(form.getEmail(), EmailOtpPurpose.REGISTRATION, form.getOtpCode());
            Member registered = memberRegistrationService.register(form);
            auditIdentityEvent(registered.getId(), "REGISTRATION", "Registration", "MEMBER", "Member " + registered.getMemberNo(),
                registered.getSaccoId(), registered.getStationId(), Map.of("memberNo", registered.getMemberNo()));
            ra.addFlashAttribute("message", "Registration completed successfully. Request a sign-in code with your email to access the member workspace.");
            return "redirect:/login";
        } catch (IllegalStateException ex) {
            bindingResult.reject("registration.failed", ex.getMessage());
            populateRegistrationOptions(model);
            return "register-member";
        }
    }

    @PostMapping("/login/password-reset/request-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestPasswordResetOtp(@RequestParam String username,
                                                                       @RequestParam String accountType) {
        String normalizedUsername = normalizeMemberNo(username);
        if (normalizedUsername.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Enter your member number."));
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
                "Your Loan Application Portal password reset code",
                "Use this OTP code to reset your Loan Application Portal password."
            );
            auditIdentityEvent(member.getId(), "PASSWORD_RESET_REQUEST", "Password reset request",
                member.isStaffAccessActive() && "staff".equalsIgnoreCase(accountType) ? "STAFF" : "MEMBER",
                (member.isStaffAccessActive() && "staff".equalsIgnoreCase(accountType) ? "Staff " : "Member ")
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
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Enter your member number."));
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
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Enter your member number."));
        }
        if (passwordError != null) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", passwordError));
        }
        try {
            Member member = findPasswordResetAccount(normalizedUsername, accountType);
            emailOtpService.consumeOtp(member.getEmail(), EmailOtpPurpose.PASSWORD_RESET, member.getId(), otpCode);
            member.setPasswordHash(passwordEncoder.encode(password));
            memberRepository.save(member);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Password updated. You can now sign in with your new password."
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/login/member/request-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestMemberLoginOtp(@RequestParam String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Enter your email address."));
        }

        Member matchedAccount = memberRepository.findByEmailIgnoreCase(normalizedEmail)
            .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
            .orElse(null);
        if (matchedAccount != null && !matchedAccount.isMemberAccess()) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "You are not registered as a member. Sign in through Staff instead."
            ));
        }
        Member member = matchedAccount != null && matchedAccount.isMemberAccess() ? matchedAccount : null;
        if (member == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", "No member account was found for that email address. Please register yourself first."
            ));
        }
        String accessBlockedMessage = suspendedAccessMessage(member);
        if (accessBlockedMessage != null) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", accessBlockedMessage
            ));
        }

        try {
            var delivery = emailOtpService.issueOtp(
                normalizedEmail,
                EmailOtpPurpose.LOGIN,
                member.getId(),
                "Your Loan Application Portal sign-in code",
                "Use this OTP code to sign in to Loan Application Portal."
            );
            auditIdentityEvent(member.getId(), "OTP_REQUEST", "OTP request", "MEMBER", "Member " + member.getMemberNo(),
                member.getSaccoId(), member.getStationId(), Map.of("purpose", EmailOtpPurpose.LOGIN.name(), "memberNo", member.getMemberNo()));
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", deliveryMessage(delivery, "We sent a sign-in code using the station delivery policy.")
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/login/member/verify-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyMemberLoginOtp(@RequestParam String email,
                                                                    @RequestParam String otpCode,
                                                                    HttpServletRequest request) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Enter your email address."));
        }

        try {
            Member member = memberRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No member account was found for that email address. Please register yourself first."));
            if (!member.isMemberAccess()) {
                throw new IllegalStateException("You are not registered as a member. Sign in through Staff instead.");
            }
            ensureSaccoAccessAllowed(member);
            emailOtpService.consumeOtp(normalizedEmail, EmailOtpPurpose.LOGIN, otpCode);
            signInMember(member, request);
            auditIdentityEvent(member.getId(), "LOGIN", "Login", "MEMBER", "Member " + member.getMemberNo(),
                member.getSaccoId(), member.getStationId(), Map.of("method", "OTP", "memberNo", member.getMemberNo()));
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "redirectUrl", WorkspaceLanding.memberDashboard()
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "message", ex.getMessage()
            ));
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

        Member user = memberRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
            .filter(Member::isStaffAccessActive)
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
                "Your Loan Application Portal staff sign-in code",
                "Use this OTP code to sign in to the Loan Application Portal staff workspace."
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
            Member user = memberRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
                .filter(Member::isStaffAccessActive)
                .orElseThrow(() -> new IllegalStateException("No active staff account matches that email address."));
            ensureSaccoAccessAllowed(user);
            emailOtpService.consumeOtp(normalizedEmail, EmailOtpPurpose.STAFF_LOGIN, otpCode);
            signInPrincipal(new AppUserPrincipal(
                user,
                userClaimService.effectiveClaims(user.getId(), user.getActiveStaffRolesResolved(), user.isMemberAccess()),
                true
            ), request);
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

    private void signInMember(Member member, HttpServletRequest request) {
        ensureSaccoAccessAllowed(member);
        AppUserPrincipal principal = new AppUserPrincipal(
            member,
            userClaimService.defaultClaims(java.util.List.of(), true),
            false
        );
        signInPrincipal(principal, request);
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
        return saccoStationRepository.findBySaccoIdAndStationId(saccoId, stationId)
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

    private boolean isGoogleSsoConfigured() {
        return googleSsoEnabled
            && googleClientId != null
            && !googleClientId.isBlank()
            && googleClientSecret != null
            && !googleClientSecret.isBlank();
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
        if ("staff".equals(normalizedType)) {
            return memberRepository.findByStaffNo(normalizedUsername)
                .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
                .filter(Member::isStaffAccessActive)
                .orElseThrow(() -> new IllegalStateException("No active staff account was found for that staff member number."));
        }
        Member member = memberRepository.findByMemberNo(normalizedUsername)
            .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new IllegalStateException("No active account was found for that member number."));
        if (!member.isMemberAccess()) {
            throw new IllegalStateException("No active member account was found for that member number.");
        }
        return member;
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

    private void populateRegistrationOptions(Model model) {
        java.util.List<SaccoRegistryService.RegisteredSaccoView> options = saccoRegistryService.listRegisteredSaccos();
        model.addAttribute("registrationSaccos", options);
        try {
            model.addAttribute("registrationSaccosJson", objectMapper.writeValueAsString(options));
        } catch (JsonProcessingException ex) {
            model.addAttribute("registrationSaccosJson", "[]");
        }
    }
}
