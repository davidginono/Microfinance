package com.sacco.mvp.config;

import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserDetailsService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, MemberRepository memberRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers("/login", "/login/staff/**", "/login/member/**", "/register/**", "/css/**", "/error", "/error/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/chairperson/**").hasRole("CHAIRPERSON")
                .requestMatchers("/manager/**").hasRole("MANAGER")
                .requestMatchers("/board/**").hasRole("BOARD")
                .requestMatchers("/app/**").hasRole("MEMBER")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .failureHandler((request, response, exception) -> {
                    String username = request.getParameter("username");
                    String loginType = request.getParameter("loginType");
                    String message = "Invalid member number or password.";
                    if (username != null && !username.isBlank()) {
                        message = memberRepository.findByMemberNo(username.trim())
                            .map(member -> {
                                if (member.getStatus() != MemberStatus.ACTIVE) {
                                    return "This account is not active.";
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
                    request.getSession(true).setAttribute("loginErrorMessage", message);
                    response.sendRedirect("/login?error");
                })
                .successHandler((request, response, authentication) -> {
                    boolean isAdmin = authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                    boolean isChairperson = authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_CHAIRPERSON".equals(a.getAuthority()));
                    boolean isManager = authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_MANAGER".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
                    boolean isBoard = authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_BOARD".equals(a.getAuthority()));

                    if (isAdmin) {
                        response.sendRedirect("/admin/dashboard");
                        return;
                    }
                    if (isChairperson) {
                        response.sendRedirect("/chairperson/manager-decisions");
                        return;
                    }
                    if (isManager) {
                        response.sendRedirect("/manager/dashboard");
                        return;
                    }
                    if (isBoard) {
                        response.sendRedirect("/board/queue");
                        return;
                    }
                    response.sendRedirect("/app/dashboard");
                })
                .permitAll())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
            .exceptionHandling(ex -> ex.accessDeniedPage("/error/403"))
            .csrf(Customizer.withDefaults());

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
}
