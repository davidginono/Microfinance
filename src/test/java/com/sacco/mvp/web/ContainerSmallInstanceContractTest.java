package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerSmallInstanceContractTest {
    @Test
    void containerRuntimeIsSizedForASmallOffBoxDatabaseInstance() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String prod = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        String security = Files.readString(Path.of("src/main/java/com/sacco/mvp/config/SecurityConfig.java"));
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(dockerfile)
            .contains("SPRING_PROFILES_ACTIVE=prod")
            .contains("SERVER_PORT=8080")
            .contains("-XX:+UseSerialGC")
            .contains("SERVER_TOMCAT_THREADS_MAX=32")
            .contains("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=6")
            .contains("HEALTHCHECK")
            .contains("/actuator/health");
        assertThat(prod)
            .contains("maximum-pool-size: ${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:6}")
            .contains("max: ${SERVER_TOMCAT_THREADS_MAX:32}")
            .contains("max-concurrent-exports: ${APP_REPORTS_MAX_CONCURRENT_EXPORTS:1}")
            .contains("local-dev-minor-admin-password-login-enabled: false")
            .contains("include: health");
        assertThat(security).contains("\"/actuator/health\"");
        assertThat(compose)
            .contains("SERVER_PORT: 8080")
            .contains("SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}");
    }
}
