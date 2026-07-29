package com.sacco.mvp.config;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.Manager;
import org.apache.catalina.session.ManagerBase;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatSessionConfig {
    private static final String SECURE_RANDOM_ALGORITHM = "DRBG";

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatSessionIdGeneratorCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            configureSessionIdGenerator(context);
            context.addLifecycleListener(event -> {
                if (Lifecycle.CONFIGURE_START_EVENT.equals(event.getType())) {
                    configureSessionIdGenerator(context);
                }
            });
        });
    }

    private void configureSessionIdGenerator(Context context) {
        Manager manager = context.getManager();
        if (manager instanceof ManagerBase managerBase) {
            managerBase.setSecureRandomAlgorithm(SECURE_RANDOM_ALGORITHM);
        }
    }
}
