# syntax=docker/dockerfile:1.7

# Sized for 20 concurrent users on a 2 GB ARM host with Postgres colocated.
# The JVM assumes a ~1.15g cgroup (see docker-compose.prod.yml).
# Build ARM images with: docker build --platform linux/arm64 -t sacco-lms .

FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jdk-alpine AS runtime

RUN jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.xml,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.unsupported,jdk.zipfs \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --output /opt/java/jre

FROM alpine:3.22 AS final

WORKDIR /app

RUN apk add --no-cache ca-certificates fontconfig libstdc++ tzdata ttf-dejavu wget \
    && addgroup -S app \
    && adduser -S -G app app

ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:${PATH}" \
    TZ=Africa/Nairobi \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=55.0 -XX:InitialRAMPercentage=10.0 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=48m -Xss512k -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    SERVER_FORWARD_HEADERS_STRATEGY=native \
    SERVER_TOMCAT_THREADS_MAX=24 \
    SERVER_TOMCAT_THREADS_MIN_SPARE=2 \
    SERVER_TOMCAT_MAX_CONNECTIONS=50 \
    SERVER_TOMCAT_ACCEPT_COUNT=20 \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=8 \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=1 \
    SPRING_DEVTOOLS_RESTART_ENABLED=false \
    SPRING_DEVTOOLS_LIVERELOAD_ENABLED=false \
    SERVER_SERVLET_JSP_DEVELOPMENT=false \
    SPRING_WEB_RESOURCES_CACHE_PERIOD=365d \
    APP_REPORTS_MAX_CONCURRENT_EXPORTS=1

COPY --from=runtime /opt/java/jre /opt/java/openjdk
COPY --from=build --chown=app:app /workspace/target/ROOT.war /app/ROOT.war

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q -O - http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/ROOT.war"]
