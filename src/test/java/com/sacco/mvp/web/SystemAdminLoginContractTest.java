package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SystemAdminLoginContractTest {
    @Test
    void systemAdminHasASeparatePlatformOnlyLoginFlow() throws Exception {
        String security = Files.readString(Path.of("src/main/java/com/sacco/mvp/config/SecurityConfig.java"));
        String authController = Files.readString(Path.of("src/main/java/com/sacco/mvp/web/AuthController.java"));
        String login = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/login.jsp"));

        assertThat(security)
            .contains("/system-admin/login")
            .contains("system-admin-password")
            .contains("loadPlatformAdminByLoginId")
            .contains("Use the separate System Admin login.");
        assertThat(authController).contains("{\"/login\", \"/system-admin/login\"}");
        assertThat(login)
            .contains("System Admin Login")
            .contains("name=\"loginType\" value=\"system-admin-password\"")
            .contains("data-forgot-password-open=\"system-admin\"");
    }
}
