package com.sacco.mvp.web;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.EmailOtpService;
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

    @GetMapping("/")
    public String root(@org.springframework.security.core.annotation.AuthenticationPrincipal AppUserPrincipal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        return switch (principal.getPosition()) {
            case ADMIN -> "redirect:/admin/dashboard";
            case MINOR_ADMIN -> "redirect:/admin/dashboard";
            case LOAN_OFFICER -> "redirect:/loan-officer/queue";
            case MANAGER -> "redirect:/manager/loan-applications?status=READY_FOR_MANAGER";
            case ACCOUNTANT -> "redirect:/accountant/loan-applications?filter=AWAITING_ACCOUNTANT";
            case DISBURSEMENT_OFFICER -> "redirect:/disbursement/loan-applications?filter=READY_FOR_DISBURSEMENT";
            case BOARD -> "redirect:/board/queue";
            case MEMBER -> "redirect:/app/dashboard";
        };
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

        try {
            MemberRegistrationService.VerifiedExternalMember verified = memberRegistrationService.verifyExternalMember(form);
            memberRegistrationService.ensureLocalUniqueness(
                verified.memberNo(),
                verified.email(),
                form.getPhone()
            );
            emailOtpService.issueOtp(
                verified.email(),
                EmailOtpPurpose.REGISTRATION,
                null,
                "Your SACCO MVP registration code",
                "We verified your member details. Use the OTP code below to complete your registration."
            );
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", true);
            response.put("memberNo", verified.memberNo());
            response.put("fullName", verified.fullName());
            response.put("saccoId", verified.saccoId());
            response.put("stationId", verified.stationId());
            response.put("message", "We sent an OTP code to " + verified.email() + ".");
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

        try {
            if (form.getOtpCode() == null || form.getOtpCode().isBlank()) {
                bindingResult.rejectValue("otpCode", "registration.otp.required", "Enter the OTP code sent to your email.");
                populateRegistrationOptions(model);
                return "register-member";
            }
            emailOtpService.consumeOtp(form.getEmail(), EmailOtpPurpose.REGISTRATION, form.getOtpCode());
            memberRegistrationService.register(form);
            ra.addFlashAttribute("message", "Registration completed successfully. Request a sign-in code with your email to access the member workspace.");
            return "redirect:/login";
        } catch (IllegalStateException ex) {
            bindingResult.reject("registration.failed", ex.getMessage());
            populateRegistrationOptions(model);
            return "register-member";
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

        emailOtpService.issueOtp(
            normalizedEmail,
            EmailOtpPurpose.LOGIN,
            member.getId(),
            "Your SACCO MVP sign-in code",
            "Use this OTP code to sign in to the SACCO Loan MVP."
        );
        return ResponseEntity.ok(Map.of(
            "valid", true,
            "message", "We sent a sign-in code to " + normalizedEmail + "."
        ));
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
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "redirectUrl", defaultLanding(member)
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
            .filter(existing -> !existing.getStaffRolesResolved().isEmpty())
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

        emailOtpService.issueOtp(
            normalizedEmail,
            EmailOtpPurpose.STAFF_LOGIN,
            user.getId(),
            "Your SACCO MVP staff sign-in code",
            "Use this OTP code to sign in to the SACCO Loan MVP staff workspace."
        );
        return ResponseEntity.ok(Map.of(
            "valid", true,
            "message", "We sent a sign-in code to " + normalizedEmail + "."
        ));
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
                .filter(existing -> !existing.getStaffRolesResolved().isEmpty())
                .orElseThrow(() -> new IllegalStateException("No active staff account matches that email address."));
            ensureSaccoAccessAllowed(user);
            emailOtpService.consumeOtp(normalizedEmail, EmailOtpPurpose.STAFF_LOGIN, otpCode);
            signInPrincipal(new AppUserPrincipal(
                user,
                userClaimService.effectiveClaims(user.getId(), user.getStaffRolesResolved(), user.isMemberAccess())
            ), request);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "redirectUrl", defaultLanding(user)
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
            userClaimService.effectiveClaims(member.getId(), member.getStaffRolesResolved(), member.isMemberAccess())
        );
        signInPrincipal(principal, request);
    }

    private void signInPrincipal(AppUserPrincipal principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private void ensureSaccoAccessAllowed(Member member) {
        String message = suspendedAccessMessage(member);
        if (message != null) {
            throw new IllegalStateException(message);
        }
    }

    private String suspendedAccessMessage(Member member) {
        if (member == null || member.getStaffRolesResolved().contains(Position.ADMIN)) {
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

    private String defaultLanding(Member member) {
        return switch (Position.primaryRole(member.getStaffRolesResolved(), member.isMemberAccess())) {
            case ADMIN -> "/admin/dashboard";
            case MINOR_ADMIN -> "/admin/dashboard";
            case LOAN_OFFICER -> "/loan-officer/queue";
            case MANAGER -> "/manager/loan-applications?status=READY_FOR_MANAGER";
            case ACCOUNTANT -> "/accountant/loan-applications?filter=AWAITING_ACCOUNTANT";
            case DISBURSEMENT_OFFICER -> "/disbursement/loan-applications?filter=READY_FOR_DISBURSEMENT";
            case BOARD -> "/board/queue";
            case MEMBER -> "/app/dashboard";
        };
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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
