package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Layer 2a — Step-up MFA for privileged staff logins.
 *
 * After a successful password authentication, privileged staff accounts
 * (MINOR_ADMIN, ADMIN) must present an email OTP before the SecurityContext
 * is installed. Password alone never grants access to the admin workspace.
 */
@Service
@RequiredArgsConstructor
public class StaffMfaService {
    public static final String PENDING_MEMBER_ID_ATTR = "staffMfa.pendingMemberId";
    public static final String PENDING_EMAIL_ATTR = "staffMfa.pendingEmail";
    public static final String PENDING_LANDING_ATTR = "staffMfa.pendingLanding";

    private static final Set<Position> PRIVILEGED_ROLES = Set.of(Position.ADMIN, Position.MINOR_ADMIN);

    private final MemberRepository memberRepository;
    private final EmailOtpService emailOtpService;
    private final UserClaimService userClaimService;

    public static boolean requiresMfa(Set<Position> staffRoles) {
        if (staffRoles == null || staffRoles.isEmpty()) {
            return false;
        }
        for (Position role : staffRoles) {
            if (PRIVILEGED_ROLES.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public void startChallenge(AppUserPrincipal principal, String landingUrl, HttpServletRequest request) {
        Member member = memberRepository.findById(principal.getMemberId())
            .orElseThrow(() -> new IllegalStateException("Your staff account could not be located. Contact the administrator."));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalStateException("This account is not active.");
        }
        String email = member.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("This staff account has no email on file. Contact the administrator to enable sign-in.");
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(PENDING_MEMBER_ID_ATTR, member.getId());
        session.setAttribute(PENDING_EMAIL_ATTR, email);
        if (landingUrl != null) {
            session.setAttribute(PENDING_LANDING_ATTR, landingUrl);
        }

        emailOtpService.issueOtp(
            email,
            EmailOtpPurpose.STAFF_LOGIN_MFA,
            member.getId(),
            "Your SACCO MVP admin sign-in verification code",
            "We received an admin sign-in attempt for this account. Enter the verification code below to finish signing in. "
                + "If you did not initiate this sign-in, ignore this message and change your password immediately."
        );
    }

    public boolean hasPendingChallenge(HttpSession session) {
        return session != null && session.getAttribute(PENDING_MEMBER_ID_ATTR) instanceof UUID;
    }

    public String pendingEmail(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object email = session.getAttribute(PENDING_EMAIL_ATTR);
        return email instanceof String value ? value : null;
    }

    public UUID pendingMemberId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PENDING_MEMBER_ID_ATTR);
        return value instanceof UUID id ? id : null;
    }

    public String pendingLanding(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PENDING_LANDING_ATTR);
        return value instanceof String landing ? landing : null;
    }

    public void resendChallenge(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        UUID memberId = pendingMemberId(session);
        if (memberId == null) {
            throw new IllegalStateException("Your sign-in session expired. Start again from the login page.");
        }
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalStateException("Your staff account could not be located. Contact the administrator."));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            clear(request);
            throw new IllegalStateException("This account is not active.");
        }
        String email = member.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("This staff account has no email on file. Contact the administrator to enable sign-in.");
        }
        emailOtpService.issueOtp(
            email,
            EmailOtpPurpose.STAFF_LOGIN_MFA,
            member.getId(),
            "Your SACCO MVP admin sign-in verification code",
            "We received an admin sign-in attempt for this account. Enter the verification code below to finish signing in."
        );
    }

    public AppUserPrincipal completeChallenge(String otpCode, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        UUID memberId = pendingMemberId(session);
        String email = pendingEmail(session);
        if (memberId == null || email == null) {
            throw new IllegalStateException("Your sign-in session expired. Start again from the login page.");
        }

        emailOtpService.consumeOtp(email, EmailOtpPurpose.STAFF_LOGIN_MFA, otpCode);

        Member member = memberRepository.findById(memberId)
            .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
            .filter(existing -> !existing.getStaffRolesResolved().isEmpty())
            .orElseThrow(() -> new IllegalStateException("Your staff account is no longer active. Contact the administrator."));

        AppUserPrincipal principal = new AppUserPrincipal(
            member,
            userClaimService.effectiveClaims(member.getId(), member.getStaffRolesResolved(), member.isMemberAccess())
        );
        installSecurityContext(principal, request);
        clearPending(session);
        return principal;
    }

    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        clearPending(session);
    }

    private void clearPending(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(PENDING_MEMBER_ID_ATTR);
        session.removeAttribute(PENDING_EMAIL_ATTR);
        session.removeAttribute(PENDING_LANDING_ATTR);
    }

    private void installSecurityContext(AppUserPrincipal principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true)
            .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
