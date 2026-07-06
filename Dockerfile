# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jdk-alpine AS runtime

RUN jlink \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,jdk.charsets,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.unsupported,jdk.zipfs \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --output /opt/java/jre

FROM alpine:3.22 AS final

WORKDIR /app

RUN apk add --no-cache ca-certificates fontconfig libstdc++ tzdata ttf-dejavu \
    && addgroup -S app \
    && adduser -S -G app app

ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:${PATH}" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=20.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    SERVER_FORWARD_HEADERS_STRATEGY=native \
    SERVER_TOMCAT_THREADS_MAX=80 \
    SERVER_TOMCAT_THREADS_MIN_SPARE=10 \
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10 \
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=1 \
    SPRING_DEVTOOLS_RESTART_ENABLED=false \
    SPRING_DEVTOOLS_LIVERELOAD_ENABLED=false \
    SERVER_SERVLET_JSP_DEVELOPMENT=false \
    SPRING_WEB_RESOURCES_CACHE_PERIOD=365d

COPY --from=runtime /opt/java/jre /opt/java/openjdk
COPY --from=build --chown=app:app /workspace/target/ROOT.war /app/ROOT.war

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/ROOT.war"]
