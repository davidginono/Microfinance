package com.sacco.mvp.platform.onboarding.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminSetupService setup;
    private final String password;
    public AdminBootstrapRunner(AdminSetupService setup,@Value("${app.bootstrap.admin-password:}") String password) {
        this.setup=setup;this.password=password;
    }
    @Override public void run(ApplicationArguments args) {
        if(password.isBlank()) {
            log.info("For an uninitialized deployment, set APP_BOOTSTRAP_ADMIN_PASSWORD and sign in as ADM001 at /system-admin/login. No shared bootstrap password is enabled.");
            return;
        }
        if(setup.provision(password))log.info("Initial ADM001 credential provisioned. Complete /system-admin/setup after signing in; remove APP_BOOTSTRAP_ADMIN_PASSWORD after setup.");
    }
}
