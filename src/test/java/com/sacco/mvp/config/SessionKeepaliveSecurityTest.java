package com.sacco.mvp.config;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.PlatformSessionSettingsService;
import com.sacco.mvp.service.SessionReauthenticationService;
import com.sacco.mvp.web.SessionKeepaliveController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class, SessionKeepaliveSecurityTest.Endpoints.class})
class SessionKeepaliveSecurityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private MemberDirectoryService directory;
    @Autowired private PasswordEncoder encoder;
    private MockMvc mvc;
    private AppUserPrincipal principal;
    private MockHttpSession session;
    private CsrfToken csrf;

    @BeforeEach void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Member member = Member.builder().id(UUID.randomUUID()).memberNo("SESSION-TEST")
            .memberAccount(true).status(MemberStatus.ACTIVE).passwordHash(encoder.encode("current-password")).build();
        principal = new AppUserPrincipal(member, Set.of(), false);
        when(directory.find(member.getId())).thenReturn(Optional.of(member));
        var page = mvc.perform(get("/session/test-token").with(user(principal))).andReturn();
        session = (MockHttpSession) page.getRequest().getSession(false);
        csrf = (CsrfToken) page.getRequest().getAttribute(CsrfToken.class.getName());
    }

    @Test void renderedCsrfHeaderAndCurrentPasswordRenewSession() throws Exception {
        mvc.perform(post("/session/keepalive").session(session).with(user(principal))
                .header(csrf.getHeaderName(), csrf.getToken()).param("password", "current-password"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refreshed").value(true));
        assertThat(session.getAttribute("sessionKeepaliveAt")).isInstanceOf(Long.class);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(1800);
    }

    @Test void wrongPasswordAndMissingCsrfCannotRenewSession() throws Exception {
        mvc.perform(post("/session/keepalive").session(session).with(user(principal))
                .header(csrf.getHeaderName(), csrf.getToken()).param("password", "wrong-password"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.refreshed").value(false));
        mvc.perform(post("/session/keepalive").session(session).with(user(principal))
                .param("password", "current-password"))
            .andExpect(status().isForbidden());
        assertThat(session.getAttribute("sessionKeepaliveAt")).isNull();
    }

    @Test void expiredSessionReturnsUnauthorizedRatherThanPasswordFailure() throws Exception {
        mvc.perform(post("/session/keepalive").header(csrf.getHeaderName(), csrf.getToken())
                .param("password", "current-password"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.refreshed").value(false));
    }

    @Test void logoutAfterSessionExpiryReturnsToLoginRatherThanAccessDenied() throws Exception {
        mvc.perform(post("/logout").param(csrf.getParameterName(), csrf.getToken()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?logout"));
    }

    @Test void authenticatedLogoutStillRequiresCurrentCsrf() throws Exception {
        mvc.perform(post("/logout").session(session).with(user(principal)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(user(principal))
                .param(csrf.getParameterName(), csrf.getToken()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Configuration @EnableWebMvc
    static class Endpoints {
        @Bean SessionReauthenticationService reauthentication(MemberDirectoryService directory, PasswordEncoder encoder) {
            return new SessionReauthenticationService(directory, encoder);
        }
        @Bean SessionKeepaliveController keepalive(PlatformSessionSettingsService settings, SessionReauthenticationService reauthentication) {
            return new SessionKeepaliveController(settings, reauthentication);
        }
        @Bean TokenPage tokenPage() { return new TokenPage(); }
    }

    @RestController
    static class TokenPage {
        @GetMapping("/session/test-token") String token(CsrfToken token) { return token.getToken(); }
    }
}
