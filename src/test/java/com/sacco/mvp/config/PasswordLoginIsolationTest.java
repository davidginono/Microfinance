package com.sacco.mvp.config;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.security.AppUserDetailsService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.StationAccessService;
import com.sacco.mvp.service.UserClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordLoginIsolationTest {
    @Configuration
    @EnableWebSecurity
    static class WebSecurity {}

    @Test
    void passwordFilterRejectsCrossedNumbersEvenWithGeneralUserDetailsServiceRegistered() throws Exception {
        MemberDirectoryService directory = mock(MemberDirectoryService.class);
        AppUserDetailsService users = new AppUserDetailsService(directory,
            mock(StationAccessService.class), mock(UserClaimService.class));
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(WebSecurity.class, SecurityConfig.class);
            context.addBeanFactoryPostProcessor(factory -> {
                for (var method : SecurityConfig.class.getDeclaredMethods()) {
                    if (!method.getName().equals("securityFilterChain")) continue;
                    for (Class<?> type : method.getParameterTypes()) {
                        if (type == HttpSecurity.class || type == PasswordEncoder.class) continue;
                        Object bean = type == AppUserDetailsService.class ? users
                            : type == MemberDirectoryService.class ? directory : mock(type);
                        factory.registerSingleton(type.getSimpleName(), bean);
                    }
                }
            });
            context.refresh();
            Member dualUser = Member.builder().id(UUID.randomUUID()).memberNo("MEM001")
                .staffNo("12345").fullName("Dual User").memberAccount(true)
                .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
                .staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE)
                .passwordHash(context.getBean(PasswordEncoder.class).encode("test-password")).build();
            when(directory.findByMemberNo("MEM001")).thenReturn(Optional.of(dualUser));
            when(directory.findByStaffNo("12345")).thenReturn(Optional.of(dualUser));
            when(directory.findStaffLoginAccount("12345")).thenReturn(Optional.of(dualUser));
            var filter = context.getBean(SecurityFilterChain.class).getFilters().stream()
                .filter(UsernamePasswordAuthenticationFilter.class::isInstance).findFirst().orElseThrow();
            AuthenticationManager manager = (AuthenticationManager) ReflectionTestUtils.getField(filter, "authenticationManager");

            assertThat(login(manager, "", "MEM001").isStaffSession()).isFalse();
            assertThat(login(manager, "staff-password", "12345").isStaffSession()).isTrue();
            assertThatThrownBy(() -> login(manager, "staff-password", "MEM001"))
                .isInstanceOf(AuthenticationException.class);
            assertThatThrownBy(() -> login(manager, "", "12345"))
                .isInstanceOf(AuthenticationException.class);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private AppUserPrincipal login(AuthenticationManager manager, String loginType, String number) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setParameter("loginType", loginType);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return (AppUserPrincipal) manager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(number, "test-password")).getPrincipal();
    }
}
