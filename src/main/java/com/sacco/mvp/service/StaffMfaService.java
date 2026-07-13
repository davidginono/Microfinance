package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
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

import java.util.UUID;

/**
 * Step-up MFA for station-configured password logins.
 */
@Service
@RequiredArgsConstructor
public class StaffMfaService {
    public static final String PENDING_MEMBER_ID_ATTR = "loginMfa.pendingMemberId";
    public static final String PENDING_EMAIL_ATTR = "loginMfa.pendingEmail";
    public static final String PENDING_LANDING_ATTR = "loginMfa.pendingLanding";
    public static final String PENDING_LOGIN_TYPE_ATTR = "loginMfa.pendingLoginType";
    public static final String PENDING_DELIVERY_MESSAGE_ATTR = "loginMfa.pendingDeliveryMessage";

    private final MemberRepository memberRepository;
    private final EmailOtpService emailOtpService;
    private final UserClaimService userClaimService;

    public void startChallenge(AppUserPrincipal principal, String landingUrl, HttpServletRequest request) {
        startChallenge(principal, landingUrl, null, request);
    }

    public void startChallenge(AppUserPrincipal principal,
                               String landingUrl,
                               String loginType,
                               HttpServletRequest request) {
        Member member = memberRepository.findById(principal.getMemberId())
            .orElseThrow(() -> new IllegalStateException("Your account could not be located. Contact the administrator."));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalStateException("This account is not active.");
        }

        HttpSession session = request.getSession(true);
        storePendingChallenge(session, member, landingUrl, loginType);
        StationOtpDeliveryService.DeliveryReceipt delivery = emailOtpService.issueOtp(
            member.getEmail(),
            EmailOtpPurpose.LOGIN_MFA,
            member.getId(),
            "Your Loan Application Portal sign-in verification code",
            "We received a sign-in attempt for this account. Enter the verification code below to finish signing in. "
                + "If you did not initiate this sign-in, ignore this message and change your password immediately.",
            member.getSaccoId(),
            member.getStationId(),
            member.getPhone()
        );
        session.setAttribute(PENDING_DELIVERY_MESSAGE_ATTR, deliveryMessage(delivery, "We sent a one-time verification code using the station delivery policy."));
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

    public String pendingDeliveryMessage(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PENDING_DELIVERY_MESSAGE_ATTR);
        return value instanceof String message ? message : null;
    }

    public void resendChallenge(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        UUID memberId = pendingMemberId(session);
        if (memberId == null) {
            throw new IllegalStateException("Your sign-in session expired. Start again from the login page.");
        }
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalStateException("Your account could not be located. Contact the administrator."));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            clear(request);
            throw new IllegalStateException("This account is not active.");
        }

        StationOtpDeliveryService.DeliveryReceipt delivery = emailOtpService.issueOtp(
            member.getEmail(),
            EmailOtpPurpose.LOGIN_MFA,
            member.getId(),
            "Your Loan Application Portal sign-in verification code",
            "We received a sign-in attempt for this account. Enter the verification code below to finish signing in.",
            member.getSaccoId(),
            member.getStationId(),
            member.getPhone()
        );
        if (session != null) {
            session.setAttribute(PENDING_DELIVERY_MESSAGE_ATTR, deliveryMessage(delivery, "We sent a new verification code."));
        }
    }

    public void sendChallengeToEmail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        UUID memberId = pendingMemberId(session);
        if (memberId == null) {
            throw new IllegalStateException("Your sign-in session expired. Start again from the login page.");
        }
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalStateException("Your account could not be located. Contact the administrator."));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            clear(request);
            throw new IllegalStateException("This account is not active.");
        }

        StationOtpDeliveryService.DeliveryReceipt delivery = emailOtpService.issueOtpToEmail(
            member.getEmail(),
            EmailOtpPurpose.LOGIN_MFA,
            member.getId(),
            "Your Loan Application Portal sign-in verification code",
            "We received a sign-in attempt for this account. Enter the verification code below to finish signing in."
        );
        if (session != null) {
            session.setAttribute(PENDING_EMAIL_ATTR, member.getEmail());
            session.setAttribute(PENDING_DELIVERY_MESSAGE_ATTR, deliveryMessage(delivery, "We sent a new verification code to your registered email."));
        }
    }

    public AppUserPrincipal completeChallenge(String otpCode, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        UUID memberId = pendingMemberId(session);
        String email = pendingEmail(session);
        if (memberId == null) {
            throw new IllegalStateException("Your sign-in session expired. Start again from the login page.");
        }

        emailOtpService.consumeOtp(email, EmailOtpPurpose.LOGIN_MFA, memberId, otpCode);

        Member member = memberRepository.findById(memberId)
            .filter(existing -> existing.getStatus() == MemberStatus.ACTIVE)
            .filter(existing -> existing.isMemberAccess() || !existing.getStaffRolesResolved().isEmpty())
            .orElseThrow(() -> new IllegalStateException("Your account is no longer active. Contact the administrator."));

        AppUserPrincipal principal = new AppUserPrincipal(
            member,
            userClaimService.effectiveClaims(member.getId(), member.getStaffRolesResolved(), member.isMemberAccess())
        );
        installSecurityContext(principal, request);
        clearPending(session);
        return principal;
    }

    public void validateChallenge(String otpCode, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        UUID memberId = pendingMemberId(session);
        String email = pendingEmail(session);
        if (memberId == null) {
            throw new IllegalStateException("Your sign-in session expired. Start again from the login page.");
        }
        emailOtpService.validateOtp(email, EmailOtpPurpose.LOGIN_MFA, memberId, otpCode);
    }

    public void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        clearPending(session);
    }

    private String deliveryMessage(StationOtpDeliveryService.DeliveryReceipt delivery, String fallback) {
        return delivery == null || delivery.userMessage() == null || delivery.userMessage().isBlank()
            ? fallback
            : delivery.userMessage();
    }

    private void storePendingChallenge(HttpSession session, Member member, String landingUrl, String loginType) {
        session.setAttribute(PENDING_MEMBER_ID_ATTR, member.getId());
        session.setAttribute(PENDING_EMAIL_ATTR, member.getEmail());
        session.setAttribute(PENDING_LOGIN_TYPE_ATTR, loginType == null ? "" : loginType);
        session.removeAttribute(PENDING_DELIVERY_MESSAGE_ATTR);
        if (landingUrl != null) {
            session.setAttribute(PENDING_LANDING_ATTR, landingUrl);
        } else {
            session.removeAttribute(PENDING_LANDING_ATTR);
        }
    }

    private void clearPending(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(PENDING_MEMBER_ID_ATTR);
        session.removeAttribute(PENDING_EMAIL_ATTR);
        session.removeAttribute(PENDING_LANDING_ATTR);
        session.removeAttribute(PENDING_LOGIN_TYPE_ATTR);
        session.removeAttribute(PENDING_DELIVERY_MESSAGE_ATTR);
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
