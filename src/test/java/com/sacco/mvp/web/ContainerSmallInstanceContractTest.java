package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerSmallInstanceContractTest {
    @Test
    void containerRuntimeIsSizedForTwentyConcurrentUsersOnOneSmallHost() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String prod = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        String security = Files.readString(Path.of("src/main/java/com/sacco/mvp/config/SecurityConfig.java"));
        String compose = Files.readString(Path.of("docker-compose.yml"));
        String prodCompose = Files.readString(Path.of("docker-compose.prod.yml"));
        String lightsailDeploy = Files.readString(Path.of("deploy/aws/deploy-lightsail.ps1"));

        assertThat(dockerfile)
            .contains("SPRING_PROFILES_ACTIVE=prod")
            .contains("SERVER_PORT=8080")
            .contains("-XX:+UseSerialGC")
            .contains("MaxRAMPercentage=55.0")
            .contains("SERVER_TOMCAT_THREADS_MAX=24")
            .contains("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=8")
            .contains("HEALTHCHECK")
            .contains("/actuator/health")
            .contains("linux/amd64");
        assertThat(prod)
            .contains("maximum-pool-size: ${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:8}")
            .contains("max: ${SERVER_TOMCAT_THREADS_MAX:24}")
            .contains("max-concurrent-exports: ${APP_REPORTS_MAX_CONCURRENT_EXPORTS:1}")
            .contains("local-dev-minor-admin-password-login-enabled: false")
            .contains("include: health");
        assertThat(security).contains("\"/actuator/health\"");
        assertThat(compose)
            .contains("SERVER_PORT: 8080")
            .contains("SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}");
        assertThat(prodCompose)
            .contains("mem_limit: 1150m")
            .contains("mem_limit: 512m")
            .contains("max_connections=30")
            .contains("shared_buffers=96MB")
            .contains("linux/amd64")
            .contains("/mnt/saccos-data/postgres")
            .contains("/mnt/saccos-data/uploads")
            .contains("/mnt/saccos-data/saccos")
            .contains("APP_UPLOADS_FILES_ROOT: /var/lib/saccos-lms/uploads")
            .contains("APP_SACCOS_FILES_ROOT: /var/lib/saccos-lms/saccos")
            .doesNotContain("mailpit")
            .doesNotContain("5432:5432");
        assertThat(lightsailDeploy)
            .contains("sudo chown -R 100:101 /mnt/saccos-data/uploads /mnt/saccos-data/saccos")
            .contains("sudo chmod -R u+rwX,g+rwX,o-rwx /mnt/saccos-data/uploads /mnt/saccos-data/saccos");
    }
}
