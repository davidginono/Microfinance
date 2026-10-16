package com.sacco.mvp.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(MvcSecurityTestConfiguration.class)
class LoginCsrfAccessDeniedTest {
    private static final String EXPIRED_LOGIN_MESSAGE = "Your sign-in page expired. Please try logging in again.";

    @Autowired private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    void expiredStaffLoginSubmissionReturnsToStaffLoginTab() throws Exception {
        var result = mockMvc.perform(post("/login")
                .param("loginType", "staff-password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error&tab=staff"))
            .andReturn();

        assertThat(result.getRequest().getSession(false).getAttribute("loginErrorMessage"))
            .isEqualTo(EXPIRED_LOGIN_MESSAGE);
    }

    @Test
    void expiredSystemAdminLoginSubmissionReturnsToSystemAdminLogin() throws Exception {
        var result = mockMvc.perform(post("/login")
                .param("loginType", "system-admin-password"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system-admin/login?error"))
            .andReturn();

        assertThat(result.getRequest().getSession(false).getAttribute("loginErrorMessage"))
            .isEqualTo(EXPIRED_LOGIN_MESSAGE);
    }

}
