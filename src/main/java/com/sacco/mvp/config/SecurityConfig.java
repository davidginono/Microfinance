package com.sacco.mvp.config;

import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserDetailsService;
import com.sacco.mvp.security.AppUsageTrackingFilter;
import com.sacco.mvp.security.AuthzService;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.security.SaccoAccessFilter;
import com.sacco.mvp.security.WorkspaceLanding;
import com.sacco.mvp.service.AppUsageAnalyticsService;
import com.sacco.mvp.service.StaffMfaService;
import com.sacco.mvp.service.StationOtpSettingsService;
import com.sacco.mvp.service.UserClaimService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {
    @Value("${app.auth.local-dev-minor-admin-password-login-enabled:false}")
    private boolean localDevMinorAdminPasswordLoginEnabled;

    @Value("${app.auth.google-sso.enabled:false}")
    private boolean googleSsoEnabled;

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret:}")
    private String googleClientSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   MemberRepository memberRepository,
                                                   SaccoStationRepository saccoStationRepository,
                                                   AdminScopeService adminScopeService,
                                                   SaccoAccessFilter saccoAccessFilter,
                                                   AuthzService authzService,
                                                   StaffMfaService staffMfaService,
                                                   StationOtpSettingsService stationOtpSettingsService,
                                                   UserClaimService userClaimService,
                                                   AppUsageAnalyticsService appUsageAnalyticsService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers("/login", "/login/mfa/**", "/login/staff/**", "/login/member/**", "/login/password-reset/**", "/register/**", "/auth/claim/**", "/css/**", "/js/**", "/images/**", "/error", "/error/**").permitAll()
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "MINOR_ADMIN")
                .requestMatchers("/loan-officer/**").hasRole("LOAN_OFFICER")
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .requestMatchers("/accountant/**").hasRole("ACCOUNTANT")
                .requestMatchers("/disbursement/**").access(new WebExpressionAuthorizationManager(
                    "isAuthenticated() and !hasRole('ADMIN') and (principal.claims.contains('ACCESS_DISBURSEMENT_QUEUE') or principal.claims.contains('DISBURSE_LOAN'))"))
                .requestMatchers("/chairperson/**").hasRole("CHAIRPERSON")
                .requestMatchers("/credit-committee/**").hasRole("CREDIT_COMMITTEE")
                .requestMatchers("/board/**").hasRole("BOARD")
                .requestMatchers("/staff/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && authzService.staffAnalyticsAccess(appUser));
                })
                .requestMatchers("/app/**").hasRole("MEMBER")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .failureHandler((request, response, exception) -> {
                    String username = request.getParameter("username");
                    String loginType = request.getParameter("loginType");
                    String message = "Invalid member number or password.";
                    String redirectTarget = "/login?error";
                    if (username != null && !username.isBlank()) {
                        message = memberRepository.findByMemberNo(username.trim())
                            .map(member -> {
                                if (member.getStatus() != MemberStatus.ACTIVE) {
                                    return "This account is not active.";
                                }
                                String suspensionReason = member.getSaccoId() == null || member.getStationId() == null
                                    ? null
                                    : saccoStationRepository.findBySaccoIdAndStationId(member.getSaccoId(), member.getStationId())
                                    .filter(SaccoStation::isAccessSuspended)
                                    .map(station -> {
                                        String reason = station.getAccessRestrictionReason();
                                        return reason == null || reason.isBlank()
                                            ? "This station workspace has been suspended. Contact the platform administrator."
                                            : "This station workspace has been suspended: " + reason.trim();
                                    })
                                    .orElse(null);
                                if (suspensionReason != null && !member.getStaffRolesResolved().contains(com.sacco.mvp.domain.Position.ADMIN)) {
                                    return suspensionReason;
                                }
                                if ("staff-password".equals(loginType)) {
                                    if (member.getStaffRolesResolved().isEmpty()) {
                                        return "This account has no staff access. Sign in through Members instead.";
                                    }
                                    return "Invalid staff number or password.";
                                }
                                if (!member.isMemberAccess()) {
                                    return "You are not registered as a member. Sign in through Staff instead.";
                                }
                                return "Invalid member number or password.";
                            })
                            .orElse("No member account was found for that member number. Please register yourself first.");
                    }
                    if ("staff-password".equals(loginType)) {
                        redirectTarget = "/login?error&tab=staff";
                    }
                    request.getSession(true).setAttribute("loginErrorMessage", message);
                    response.sendRedirect(redirectTarget);
                })
                .successHandler((request, response, authentication) -> {
                    AppUserPrincipal principal = authentication.getPrincipal() instanceof AppUserPrincipal appUser
                        ? appUser
                        : null;
                    String loginType = request.getParameter("loginType");
                    boolean staffPasswordLogin = "staff-password".equals(loginType);
                    boolean isSuperAdmin = principal != null && principal.hasRole(com.sacco.mvp.domain.Position.ADMIN);
                    boolean isMinorAdmin = principal != null && principal.hasRole(com.sacco.mvp.domain.Position.MINOR_ADMIN);
                    String savedTarget = savedRequestTarget(request);

                    // Layer 2a — Step-up MFA. Privileged staff (ADMIN, MINOR_ADMIN) must
                    // present an email OTP before the authenticated SecurityContext is
                    // persisted. Password alone cannot grant admin access.
                    // Super admins now bypass this challenge; only MINOR_ADMIN can still
                    // be routed through staff MFA when the local dev bypass is off.
                    boolean requireLoginMfa = requiresPasswordLoginMfa(principal, staffPasswordLogin, isMinorAdmin, stationOtpSettingsService);
                    if (requireLoginMfa) {
                        if (principal != null) {
                            String landing = savedTarget == null ? landingFor(principal, staffPasswordLogin) : savedTarget;
                            try {
                                staffMfaService.startChallenge(principal, landing, loginType, request);
                            } catch (IllegalStateException ex) {
                                clearAuthenticationContext(request);
                                request.getSession(true).setAttribute("loginErrorMessage", ex.getMessage());
                                response.sendRedirect("/login?error" + (staffPasswordLogin ? "&tab=staff" : ""));
                                return;
                            }
                            clearAuthenticationContext(request);
                            if (isSuperAdmin) {
                                adminScopeService.clearScope();
                            }
                            response.sendRedirect("/login/mfa");
                            return;
                        }
                    }

                    if (savedTarget != null) {
                        response.sendRedirect(savedTarget);
                        return;
                    }
                    if (!staffPasswordLogin && principal != null && principal.isMemberAccess()) {
                        response.sendRedirect(WorkspaceLanding.memberDashboard());
                        return;
                    }
                    if (isSuperAdmin) {
                        adminScopeService.clearScope();
                        response.sendRedirect(WorkspaceLanding.staffDashboard(principal));
                        return;
                    }
                    if (principal != null && (staffPasswordLogin || !principal.getStaffRoles().isEmpty())) {
                        response.sendRedirect(WorkspaceLanding.staffDashboard(principal));
                        return;
                    }
                    response.sendRedirect(WorkspaceLanding.memberDashboard());
                })
                .permitAll())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
            .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"))
            .csrf(Customizer.withDefaults())
            .addFilterBefore(saccoAccessFilter, AuthorizationFilter.class)
            .addFilterAfter(new AppUsageTrackingFilter(appUsageAnalyticsService), AuthorizationFilter.class);

        if (isGoogleSsoConfigured()) {
            http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    OAuth2User oauthUser = authentication.getPrincipal() instanceof OAuth2User user ? user : null;
                    String email = oauthUser == null ? "" : String.valueOf(oauthUser.getAttribute("email")).trim().toLowerCase();
                    if (email.isBlank() || "null".equals(email)) {
                        request.getSession(true).setAttribute("loginErrorMessage", "Google did not return a verified email address.");
                        response.sendRedirect("/login?error");
                        return;
                    }

                    var ssoMember = memberRepository.findByEmailIgnoreCase(email)
                        .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                        .filter(member -> member.isMemberAccess() || !member.getStaffRolesResolved().isEmpty())
                        .orElse(null);
                    if (ssoMember == null) {
                        request.getSession(true).setAttribute("loginErrorMessage", "No active SACCO account is linked to that Google email.");
                        response.sendRedirect("/login?error");
                        return;
                    }
                    if (!ssoMember.getStaffRolesResolved().contains(com.sacco.mvp.domain.Position.ADMIN)
                        && ssoMember.getSaccoId() != null
                        && !ssoMember.getSaccoId().isBlank()
                        && ssoMember.getStationId() != null
                        && !ssoMember.getStationId().isBlank()) {
                        String blockedMessage = saccoStationRepository.findBySaccoIdAndStationId(ssoMember.getSaccoId(), ssoMember.getStationId())
                            .filter(SaccoStation::isAccessSuspended)
                            .map(this::suspendedMessage)
                            .orElse(null);
                        if (blockedMessage != null) {
                            request.getSession(true).setAttribute("loginErrorMessage", blockedMessage);
                            response.sendRedirect("/login?error");
                            return;
                        }
                    }

                    AppUserPrincipal principal = new AppUserPrincipal(
                        ssoMember,
                        userClaimService.effectiveClaims(ssoMember.getId(), ssoMember.getStaffRolesResolved(), ssoMember.isMemberAccess())
                    );

                    UsernamePasswordAuthenticationToken localAuth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(localAuth);
                    SecurityContextHolder.setContext(context);
                    request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

                    if (principal.hasRole(com.sacco.mvp.domain.Position.ADMIN)) {
                        adminScopeService.clearScope();
                    }
                    String savedTarget = savedRequestTarget(request);
                    if (savedTarget != null) {
                        response.sendRedirect(savedTarget);
                        return;
                    }
                    response.sendRedirect(WorkspaceLanding.authenticatedDefault(principal));
                })
                .failureHandler((request, response, exception) -> {
                    request.getSession(true).setAttribute("loginErrorMessage", "Google sign-in could not be completed.");
                    response.sendRedirect("/login?error");
                }));
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return bcrypt.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                if (encodedPassword == null) {
                    return false;
                }
                if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$")
                    || encodedPassword.startsWith("$2y$")) {
                    return bcrypt.matches(rawPassword, encodedPassword);
                }
                return rawPassword.toString().equals(encodedPassword);
            }
        };
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(AppUserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider() {
            @Override
            protected void additionalAuthenticationChecks(UserDetails userDetails,
                                                          org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication) {
                super.additionalAuthenticationChecks(userDetails, authentication);
                if (!(userDetails instanceof AppUserPrincipal principal)) {
                    return;
                }
                String loginType = resolveCurrentLoginType();
                if ("staff-password".equals(loginType)) {
                    if (principal.getStaffRoles() == null || principal.getStaffRoles().isEmpty()) {
                        throw new AuthenticationServiceException("This account has no staff access. Sign in through Members instead.");
                    }
                    return;
                }
                if (!principal.isMemberAccess()) {
                    throw new AuthenticationServiceException("This account is staff-only. Sign in through Staff instead.");
                }
            }

            private String resolveCurrentLoginType() {
                RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
                if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
                    return "";
                }
                HttpServletRequest request = servletAttributes.getRequest();
                return request == null ? "" : String.valueOf(request.getParameter("loginType"));
            }
        };
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    private String suspendedMessage(SaccoStation station) {
        String reason = station.getAccessRestrictionReason();
        if (reason == null || reason.isBlank()) {
            return "This station workspace has been suspended. Contact the platform administrator.";
        }
        return "This station workspace has been suspended: " + reason.trim();
    }

    private boolean requiresPasswordLoginMfa(AppUserPrincipal principal,
                                             boolean staffPasswordLogin,
                                             boolean isMinorAdmin,
                                             StationOtpSettingsService stationOtpSettingsService) {
        if (principal == null) {
            return false;
        }
        if (hasStationScope(principal)) {
            return stationOtpSettingsService.requiresLoginMfa(principal.getSaccoId(), principal.getStationId());
        }
        return staffPasswordLogin && isMinorAdmin && !localDevMinorAdminPasswordLoginEnabled;
    }

    private boolean hasStationScope(AppUserPrincipal principal) {
        return principal.getSaccoId() != null
            && !principal.getSaccoId().isBlank()
            && principal.getStationId() != null
            && !principal.getStationId().isBlank();
    }

    private String landingFor(AppUserPrincipal principal, boolean staffPasswordLogin) {
        if (!staffPasswordLogin && principal.isMemberAccess()) {
            return WorkspaceLanding.memberDashboard();
        }
        if (staffPasswordLogin || !principal.getStaffRoles().isEmpty()) {
            return WorkspaceLanding.staffDashboard(principal);
        }
        return WorkspaceLanding.memberDashboard();
    }

    private void clearAuthenticationContext(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
    }

    private String savedRequestTarget(HttpServletRequest request) {
        SavedRequest savedRequest = new HttpSessionRequestCache().getRequest(request, null);
        if (savedRequest == null || savedRequest.getRedirectUrl() == null || savedRequest.getRedirectUrl().isBlank()) {
            return null;
        }
        return savedRequest.getRedirectUrl();
    }

    private boolean isGoogleSsoConfigured() {
        return googleSsoEnabled
            && googleClientId != null
            && !googleClientId.isBlank()
            && googleClientSecret != null
            && !googleClientSecret.isBlank();
    }
}
