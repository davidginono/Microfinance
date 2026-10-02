package com.sacco.mvp.config;

import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.AppUserDetailsService;
import com.sacco.mvp.security.AppUsageTrackingFilter;
import com.sacco.mvp.security.AuthzService;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.security.SaccoAccessFilter;
import com.sacco.mvp.security.SessionTimeoutPolicyFilter;
import com.sacco.mvp.security.WorkspaceLanding;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.AppUsageAnalyticsService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.StaffMfaService;
import com.sacco.mvp.service.StationAccessService;
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
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

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

    @Value("${app.benchmark.public-actuator-metrics:false}")
    private boolean publicActuatorMetrics;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   MemberDirectoryService memberDirectoryService,
                                                   StationAccessService stationAccessService,
                                                   AdminScopeService adminScopeService,
                                                   SaccoAccessFilter saccoAccessFilter,
                                                   AuthzService authzService,
                                                   AccessControlService accessControlService,
                                                   StaffMfaService staffMfaService,
                                                   StationOtpSettingsService stationOtpSettingsService,
                                                   UserClaimService userClaimService,
                                                   AppUsageAnalyticsService appUsageAnalyticsService,
                                                   PlatformSessionSettingsService platformSessionSettingsService,
                                                   AuditService auditService,
                                                   AppUserDetailsService userDetailsService,
                                                   PasswordEncoder passwordEncoder) throws Exception {
        http
            .authenticationProvider(authenticationProvider(userDetailsService, passwordEncoder))
            .authorizeHttpRequests(auth -> {
                auth.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/login", "/system-admin/login", "/login/mfa/**", "/login/staff/**", "/login/member/**", "/login/password-reset/**", "/register/**", "/auth/claim/**", "/css/**", "/js/**", "/images/**", "/fonts/**", "/error", "/error/**", "/actuator/health", "/actuator/health/**").permitAll();
                if (publicActuatorMetrics) {
                    auth.requestMatchers("/actuator/metrics", "/actuator/metrics/**").permitAll();
                }
                auth
                .requestMatchers("/finance/**", "/reports/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && appUser.isStaffSession()
                        && appUser.getSaccoId() != null && !appUser.getSaccoId().isBlank()
                        && appUser.getStationId() != null && !appUser.getStationId().isBlank());
                })
                .requestMatchers("/admin/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canAccessAdminArea(appUser));
                })
                .requestMatchers("/loan-officer/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canAccessLoanOfficerArea(appUser));
                })
                .requestMatchers("/manager/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canAccessManagerArea(appUser));
                })
                .requestMatchers("/accountant/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canAccessAccountantArea(appUser));
                })
                .requestMatchers("/disbursement/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canAccessDisbursementArea(appUser));
                })
                .requestMatchers("/chairperson/processed-loans", "/chairperson/processed-loans/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canViewProcessedLoans(appUser));
                })
                .requestMatchers("/chairperson/configurations", "/chairperson/configurations/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canViewSaccoConfigurations(appUser));
                })
                .requestMatchers("/chairperson/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canAccessChairpersonArea(appUser));
                })
                .requestMatchers("/credit-committee/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canAccessCreditCommitteeArea(appUser));
                })
                .requestMatchers("/board/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canAccessBoardArea(appUser));
                })
                .requestMatchers("/staff/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && authzService.staffAnalyticsAccess(appUser));
                })
                .requestMatchers("/app/**").access((authentication, context) -> {
                    Object principal = authentication.get().getPrincipal();
                    return new AuthorizationDecision(principal instanceof AppUserPrincipal appUser
                        && accessControlService.canAccessMemberArea(appUser));
                })
                .anyRequest().authenticated();
            })
            .formLogin(form -> form
                .loginPage("/login")
                .failureHandler((request, response, exception) -> {
                    String username = request.getParameter("username");
                    String loginType = request.getParameter("loginType");
                    String message = "Invalid member number or password.";
                    String redirectTarget = "/login?error";
                    Member auditMember = null;
                    if (username != null && !username.isBlank()) {
                        boolean staffPasswordLogin = "staff-password".equals(loginType);
                        boolean systemAdminPasswordLogin = "system-admin-password".equals(loginType);
                        String normalizedUsername = username.trim().toUpperCase(java.util.Locale.ROOT);
                        java.util.Optional<Member> matchedMember = systemAdminPasswordLogin
                            ? memberDirectoryService.findPlatformAdminLoginAccount(normalizedUsername)
                            : staffPasswordLogin
                                ? memberDirectoryService.findStaffLoginAccount(normalizedUsername)
                                : memberDirectoryService.findByMemberNo(normalizedUsername);
                        auditMember = matchedMember.orElse(null);
                        message = matchedMember
                            .map(member -> {
                                if (member.getStatus() != MemberStatus.ACTIVE) {
                                    return "This account is not active.";
                                }
                                String suspensionReason = member.getSaccoId() == null || member.getStationId() == null
                                    ? null
                                    : stationAccessService.suspendedStation(member.getSaccoId(), member.getStationId())
                                    .filter(SaccoStation::isAccessSuspended)
                                    .map(station -> {
                                        String reason = station.getAccessRestrictionReason();
                                        return reason == null || reason.isBlank()
                                            ? "This station workspace has been suspended. Contact the platform administrator."
                                            : "This station workspace has been suspended: " + reason.trim();
                                    })
                                    .orElse(null);
                                if (suspensionReason != null && !member.getActiveStaffRolesResolved().contains(com.sacco.mvp.domain.Position.ADMIN)) {
                                    return suspensionReason;
                                }
                                if (systemAdminPasswordLogin) {
                                    return "Invalid System Admin ID or password.";
                                }
                                if (staffPasswordLogin) {
                                    if (member.isStaffAccessPendingAcknowledgement()) {
                                        return "Staff access is pending acknowledgement. Sign in through Members to activate it.";
                                    }
                                    if (!member.isStaffAccessActive()) {
                                        return "This account has no staff access. Sign in through Members instead.";
                                    }
                                    return "Invalid staff number or password.";
                                }
                                if (!member.isMemberAccess()) {
                                    return "You are not registered as a member. Sign in through Staff instead.";
                                }
                                return "Invalid member number or password.";
                            })
                            .orElse(systemAdminPasswordLogin
                                ? "Invalid System Admin ID or password."
                                : staffPasswordLogin
                                    ? "No active staff account was found for that staff number."
                                    : "No member account was found for that member number. Please register yourself first.");
                    }
                    if ("system-admin-password".equals(loginType)) {
                        redirectTarget = "/system-admin/login?error";
                    } else if ("staff-password".equals(loginType)) {
                        redirectTarget = "/login?error&tab=staff";
                    }
                    auditLogin(auditService, auditMember, null, AuditEventStatus.FAIL, "Password login", message);
                    request.getSession(true).setAttribute("loginErrorMessage", message);
                    response.sendRedirect(redirectTarget);
                })
                .successHandler((request, response, authentication) -> {
                    AppUserPrincipal principal = authentication.getPrincipal() instanceof AppUserPrincipal appUser
                        ? appUser
                        : null;
                    String loginType = request.getParameter("loginType");
                    boolean staffPasswordLogin = "staff-password".equals(loginType);
                    boolean systemAdminPasswordLogin = "system-admin-password".equals(loginType);
                    boolean staffSessionLogin = staffPasswordLogin || systemAdminPasswordLogin;
                    boolean isSuperAdmin = principal != null && principal.isPlatformIdentity();
                    boolean isMinorAdmin = principal != null && principal.isWorkspaceAdminScope();
                    String savedTarget = systemAdminPasswordLogin ? null : savedRequestTarget(request);

                    // Apply the authenticated user's login OTP preference before the
                    // SecurityContext is persisted. The local minor-admin bypass remains
                    // available only for explicitly configured development environments.
                    boolean requireLoginMfa = requiresPasswordLoginMfa(
                        principal, staffPasswordLogin, isSuperAdmin, isMinorAdmin, stationOtpSettingsService);
                    if (requireLoginMfa) {
                        if (principal != null) {
                            String landing = savedTarget == null ? landingFor(principal, staffSessionLogin) : savedTarget;
                            try {
                                staffMfaService.startChallenge(principal, landing, loginType, request);
                            } catch (IllegalStateException ex) {
                                auditLogin(auditService, null, principal, AuditEventStatus.FAIL, "Password login", ex.getMessage());
                                clearAuthenticationContext(request);
                                request.getSession(true).setAttribute("loginErrorMessage", ex.getMessage());
                                response.sendRedirect(systemAdminPasswordLogin
                                    ? "/system-admin/login?error"
                                    : "/login?error" + (staffPasswordLogin ? "&tab=staff" : ""));
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
                        auditLogin(auditService, null, principal, AuditEventStatus.SUCCESS, "Password login", null);
                        response.sendRedirect(savedTargetAfterLogin(principal, savedTarget));
                        return;
                    }
                    if (!staffPasswordLogin && principal != null && principal.isMemberAccess()) {
                        auditLogin(auditService, null, principal, AuditEventStatus.SUCCESS, "Password login", null);
                        response.sendRedirect(WorkspaceLanding.memberDashboardAfterLogin());
                        return;
                    }
                    if (isSuperAdmin) {
                        adminScopeService.clearScope();
                        auditLogin(auditService, null, principal, AuditEventStatus.SUCCESS, "Password login", null);
                        response.sendRedirect(WorkspaceLanding.staffDashboard(principal));
                        return;
                    }
                    if (principal != null && (staffSessionLogin || !principal.getStaffRoles().isEmpty())) {
                        auditLogin(auditService, null, principal, AuditEventStatus.SUCCESS, "Password login", null);
                        response.sendRedirect(WorkspaceLanding.staffDashboard(principal));
                        return;
                    }
                    auditLogin(auditService, null, principal, AuditEventStatus.SUCCESS, "Password login", null);
                    response.sendRedirect(WorkspaceLanding.memberDashboardAfterLogin());
                })
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    boolean platformAdmin = authentication != null
                        && authentication.getPrincipal() instanceof AppUserPrincipal principal
                        && principal.isPlatformIdentity();
                    response.sendRedirect(platformAdmin ? "/system-admin/login?logout" : "/login?logout");
                }))
            .exceptionHandling(ex -> ex.accessDeniedHandler(loginAwareAccessDeniedHandler()))
            .csrf(Customizer.withDefaults())
            .addFilterBefore(saccoAccessFilter, AuthorizationFilter.class)
            .addFilterAfter(new SessionTimeoutPolicyFilter(platformSessionSettingsService), AuthorizationFilter.class)
            .addFilterAfter(new AppUsageTrackingFilter(appUsageAnalyticsService), AuthorizationFilter.class);

        if (isGoogleSsoConfigured()) {
            http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    OAuth2User oauthUser = authentication.getPrincipal() instanceof OAuth2User user ? user : null;
                    String email = oauthUser == null ? "" : String.valueOf(oauthUser.getAttribute("email")).trim().toLowerCase();
                    if (email.isBlank() || "null".equals(email)) {
                        auditLogin(auditService, null, null, AuditEventStatus.FAIL, "Google SSO", "Google did not return a verified email address.");
                        request.getSession(true).setAttribute("loginErrorMessage", "Google did not return a verified email address.");
                        response.sendRedirect("/login?error");
                        return;
                    }

                    var ssoMember = memberDirectoryService.findByEmail(email)
                        .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                        .filter(member -> member.isMemberAccess() || member.isStaffAccessActive())
                        .orElse(null);
                    if (ssoMember == null) {
                        auditLogin(auditService, null, null, AuditEventStatus.FAIL, "Google SSO", "No active SACCO account is linked to that Google email.");
                        request.getSession(true).setAttribute("loginErrorMessage", "No active SACCO account is linked to that Google email.");
                        response.sendRedirect("/login?error");
                        return;
                    }
                    if (ssoMember.getActiveStaffRolesResolved().contains(com.sacco.mvp.domain.Position.ADMIN)) {
                        String message = "Use the separate System Admin login.";
                        auditLogin(auditService, ssoMember, null, AuditEventStatus.FAIL, "Google SSO", message);
                        request.getSession(true).setAttribute("loginErrorMessage", message);
                        response.sendRedirect("/system-admin/login?error");
                        return;
                    }
                    boolean ssoStaffSession = !ssoMember.isMemberAccess();
                    if (!ssoMember.getActiveStaffRolesResolved().contains(com.sacco.mvp.domain.Position.ADMIN)
                        && ssoMember.getSaccoId() != null
                        && !ssoMember.getSaccoId().isBlank()
                        && ssoMember.getStationId() != null
                        && !ssoMember.getStationId().isBlank()) {
                        String blockedMessage = stationAccessService.suspendedStation(ssoMember.getSaccoId(), ssoMember.getStationId())
                            .filter(SaccoStation::isAccessSuspended)
                            .map(this::suspendedMessage)
                            .orElse(null);
                        if (blockedMessage != null) {
                            auditLogin(auditService, ssoMember, null, AuditEventStatus.FAIL, "Google SSO", blockedMessage);
                            request.getSession(true).setAttribute("loginErrorMessage", blockedMessage);
                            response.sendRedirect("/login?error");
                            return;
                        }
                    }

                    AppUserPrincipal principal = new AppUserPrincipal(
                        ssoMember,
                        ssoStaffSession
                            ? userClaimService.effectiveClaims(ssoMember.getId(), ssoMember.getActiveStaffRolesResolved(), ssoMember.isMemberAccess())
                            : userClaimService.defaultClaims(java.util.List.of(), true),
                        ssoStaffSession
                    );

                    UsernamePasswordAuthenticationToken localAuth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(localAuth);
                    SecurityContextHolder.setContext(context);
                    request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

                    if (principal.isPlatformIdentity()) {
                        adminScopeService.clearScope();
                    }
                    String savedTarget = savedRequestTarget(request);
                    if (savedTarget != null) {
                        auditLogin(auditService, null, principal, AuditEventStatus.SUCCESS, "Google SSO", null);
                        response.sendRedirect(savedTargetAfterLogin(principal, savedTarget));
                        return;
                    }
                    auditLogin(auditService, null, principal, AuditEventStatus.SUCCESS, "Google SSO", null);
                    response.sendRedirect(authenticatedLandingAfterLogin(principal));
                })
                .failureHandler((request, response, exception) -> {
                    auditLogin(auditService, null, null, AuditEventStatus.FAIL, "Google SSO", "Google sign-in could not be completed.");
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

    // Register the strict provider globally so Spring cannot create an unrestricted fallback.
    @Bean
    public DaoAuthenticationProvider authenticationProvider(AppUserDetailsService userDetailsService,
                                                             PasswordEncoder passwordEncoder) {
        org.springframework.security.core.userdetails.UserDetailsService loginUserDetailsService = username -> {
            String loginType = resolveCurrentLoginType();
            String normalizedUsername = username == null ? "" : username.trim().toUpperCase(java.util.Locale.ROOT);
            if ("system-admin-password".equals(loginType)) {
                return userDetailsService.loadPlatformAdminByLoginId(normalizedUsername);
            }
            if ("staff-password".equals(loginType)) {
                return userDetailsService.loadStaffByStaffNo(normalizedUsername);
            }
            return userDetailsService.loadMemberByMemberNo(normalizedUsername);
        };
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(loginUserDetailsService) {
            @Override
            protected void additionalAuthenticationChecks(UserDetails userDetails,
                                                          org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication) {
                super.additionalAuthenticationChecks(userDetails, authentication);
                if (!(userDetails instanceof AppUserPrincipal principal)) {
                    return;
                }
                String loginType = resolveCurrentLoginType();
                if ("system-admin-password".equals(loginType)) {
                    if (!principal.isPlatformIdentity()) {
                        throw new AuthenticationServiceException("This login is restricted to the System Admin.");
                    }
                    return;
                }
                if ("staff-password".equals(loginType)) {
                    if (principal.isPlatformIdentity()) {
                        throw new AuthenticationServiceException("Use the separate System Admin login.");
                    }
                    if (principal.getStaffRoles() == null || principal.getStaffRoles().isEmpty()) {
                        throw new AuthenticationServiceException("This account has no staff access. Sign in through Members instead.");
                    }
                    return;
                }
                if (!principal.isMemberAccess()) {
                    throw new AuthenticationServiceException("This account is staff-only. Sign in through Staff instead.");
                }
            }

        };
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    private String resolveCurrentLoginType() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return "";
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return request == null ? "" : String.valueOf(request.getParameter("loginType"));
    }

    private String suspendedMessage(SaccoStation station) {
        String reason = station.getAccessRestrictionReason();
        if (reason == null || reason.isBlank()) {
            return "This station workspace has been suspended. Contact the platform administrator.";
        }
        return "This station workspace has been suspended: " + reason.trim();
    }

    private void auditLogin(AuditService auditService,
                            Member member,
                            AppUserPrincipal principal,
                            AuditEventStatus status,
                            String method,
                            String message) {
        if (auditService == null) {
            return;
        }
        UUID actorId = principal == null ? (member == null ? null : member.getId()) : principal.getMemberId();
        String saccoId = principal == null ? (member == null ? null : member.getSaccoId()) : principal.getSaccoId();
        String stationId = principal == null ? (member == null ? null : member.getStationId()) : principal.getStationId();
        boolean staff = principal != null
            ? principal.getStaffRoles() != null && !principal.getStaffRoles().isEmpty()
            : member != null && member.isStaffAccessActive();
        String memberNo = principal == null
            ? (member == null ? null : (staff && member.getStaffNo() != null && !member.getStaffNo().isBlank()
                ? member.getStaffNo()
                : member.getMemberNo()))
            : principal.getUsername();
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("result", status == AuditEventStatus.FAIL ? "ERROR" : "SUCCESS");
        details.put("method", method);
        details.put("saccoId", saccoId);
        details.put("stationId", stationId);
        details.put("memberNo", memberNo);
        if (message != null && !message.isBlank()) {
            details.put("message", message);
        }
        auditService.logEvent(
            staff ? "STAFF_USER" : "MEMBER",
            actorId,
            "LOGIN",
            actorId,
            status,
            "Login",
            staff ? "STAFF" : "MEMBER",
            memberNo == null || memberNo.isBlank()
                ? "-"
                : (staff ? "Staff " : "Member ") + memberNo,
            saccoId,
            stationId,
            details
        );
    }

    private boolean requiresPasswordLoginMfa(AppUserPrincipal principal,
                                             boolean staffPasswordLogin,
                                             boolean isSuperAdmin,
                                             boolean isMinorAdmin,
                                             StationOtpSettingsService stationOtpSettingsService) {
        if (principal == null) {
            return false;
        }
        return !(staffPasswordLogin && isMinorAdmin && localDevMinorAdminPasswordLoginEnabled)
            && stationOtpSettingsService.requiresLoginMfa(
                principal.getMemberId(), principal.getSaccoId(), principal.getStationId(), isSuperAdmin);
    }

    private String landingFor(AppUserPrincipal principal, boolean staffPasswordLogin) {
        if (!staffPasswordLogin && principal.isMemberAccess()) {
            return WorkspaceLanding.memberDashboardAfterLogin();
        }
        if (staffPasswordLogin || !principal.getStaffRoles().isEmpty()) {
            return WorkspaceLanding.staffDashboard(principal);
        }
        return WorkspaceLanding.memberDashboardAfterLogin();
    }

    private String authenticatedLandingAfterLogin(AppUserPrincipal principal) {
        if (principal != null && principal.isMemberAccess() && !principal.isStaffSession()) {
            return WorkspaceLanding.memberDashboardAfterLogin();
        }
        return WorkspaceLanding.authenticatedDefault(principal);
    }

    private String savedTargetAfterLogin(AppUserPrincipal principal, String savedTarget) {
        if (principal == null || !principal.isMemberAccess() || principal.isStaffSession() || !isMemberDashboardTarget(savedTarget)) {
            return savedTarget;
        }
        if (savedTarget.contains("progressive=true")) {
            return savedTarget;
        }
        int fragmentIndex = savedTarget.indexOf('#');
        String baseTarget = fragmentIndex >= 0 ? savedTarget.substring(0, fragmentIndex) : savedTarget;
        String fragment = fragmentIndex >= 0 ? savedTarget.substring(fragmentIndex) : "";
        return baseTarget + (baseTarget.contains("?") ? "&" : "?") + "progressive=true" + fragment;
    }

    private boolean isMemberDashboardTarget(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        try {
            return "/app/dashboard".equals(java.net.URI.create(target).getPath());
        } catch (IllegalArgumentException ex) {
            int queryIndex = target.indexOf('?');
            int fragmentIndex = target.indexOf('#');
            int endIndex = queryIndex >= 0 && fragmentIndex >= 0
                ? Math.min(queryIndex, fragmentIndex)
                : Math.max(queryIndex, fragmentIndex);
            return "/app/dashboard".equals(endIndex >= 0 ? target.substring(0, endIndex) : target);
        }
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

    private AccessDeniedHandler loginAwareAccessDeniedHandler() {
        AccessDeniedHandlerImpl fallback = new AccessDeniedHandlerImpl();
        fallback.setErrorPage("/error/403");
        return (request, response, exception) -> {
            if (isExpiredLoginSubmission(request, exception)) {
                request.getSession(true).setAttribute(
                    "loginErrorMessage",
                    "Your sign-in page expired. Please try logging in again."
                );
                response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + loginRedirectTarget(request)));
                return;
            }
            fallback.handle(request, response, exception);
        };
    }

    private boolean isExpiredLoginSubmission(HttpServletRequest request,
                                             org.springframework.security.access.AccessDeniedException exception) {
        return exception instanceof CsrfException
            && "POST".equalsIgnoreCase(request.getMethod())
            && "/login".equals(pathWithinApplication(request));
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private String loginRedirectTarget(HttpServletRequest request) {
        String loginType = request.getParameter("loginType");
        if ("system-admin-password".equals(loginType)) {
            return "/system-admin/login?error";
        }
        if ("staff-password".equals(loginType)) {
            return "/login?error&tab=staff";
        }
        return "/login?error";
    }
}
