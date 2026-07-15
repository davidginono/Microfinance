package com.sacco.mvp.integration.foresight;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ForesightDirectoryServiceTest {

    @Test
    void phoneLookupReturnsFoundForValidMemberProfile() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = server(200, """
            {"memberNo":"MBR-001","stationId":"ST-1","saccoName":"Demo SACCO"}
            """, path, query);
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            ForesightDirectoryService.MemberProfileLookupResult result =
                service.lookupMemberProfileByPhone("+2556764239920");

            assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.FOUND);
            assertThat(result.profile().memberNo()).isEqualTo("MBR-001");
            assertThat(path.get()).isEqualTo("/member-profile");
            assertThat(query.get()).isEqualTo("phoneNumber=+2556764239920");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emailV2LookupReturnsFoundForValidMemberProfile() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = server(200, """
            {"memberNo":"MBR-002","stationId":"ST-1","saccoName":"Demo SACCO"}
            """, path, query);
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            ForesightDirectoryService.MemberProfileLookupResult result =
                service.lookupMemberProfileByEmailV2("anna.smith@example.com");

            assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.FOUND);
            assertThat(result.profile().memberNo()).isEqualTo("MBR-002");
            assertThat(path.get()).isEqualTo("/member-profile-v2");
            assertThat(query.get()).isEqualTo("email=anna.smith@example.com");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void lookupReturnsNotFoundFor404() throws Exception {
        HttpServer server = server(404, "", new AtomicReference<>(), new AtomicReference<>());
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            ForesightDirectoryService.MemberProfileLookupResult result =
                service.lookupMemberProfileByEmailV2("missing@example.com");

            assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.NOT_FOUND);
            assertThat(result.profile()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void lookupReturnsUnavailableWhenNoResponseCanBeRead() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        ForesightDirectoryService service = service("http://localhost:" + port, Duration.ofMillis(100));

        ForesightDirectoryService.MemberProfileLookupResult result =
            service.lookupMemberProfileByPhone("+2556764239920");

        assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.UNAVAILABLE);
    }

    @Test
    void lookupReturnsUnavailableForEmptyProfileBody() throws Exception {
        HttpServer server = server(200, "{}", new AtomicReference<>(), new AtomicReference<>());
        server.start();
        try {
            ForesightDirectoryService service = service("http://localhost:" + server.getAddress().getPort());

            ForesightDirectoryService.MemberProfileLookupResult result =
                service.lookupMemberProfileByPhone("+2556764239920");

            assertThat(result.status()).isEqualTo(ForesightDirectoryService.MemberProfileLookupStatus.UNAVAILABLE);
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(int status,
                              String responseBody,
                              AtomicReference<String> path,
                              AtomicReference<String> query) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        return server;
    }

    private ForesightDirectoryService service(String baseUrl) {
        return service(baseUrl, Duration.ofSeconds(2));
    }

    private ForesightDirectoryService service(String baseUrl, Duration timeout) {
        ForesightDirectoryService service = new ForesightDirectoryService(RestClient.builder());
        ReflectionTestUtils.setField(service, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(service, "connectTimeout", timeout);
        ReflectionTestUtils.setField(service, "readTimeout", timeout);
        return service;
    }
}
