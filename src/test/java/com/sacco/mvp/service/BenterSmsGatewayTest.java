package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenterSmsGatewayTest {

    @Test
    void sendsDocumentedPayloadWithoutAccessKeyHeader() throws Exception {
        AtomicReference<String> accessKey = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/sms/SendBulkSMS", exchange -> {
            accessKey.set(exchange.getRequestHeaders().getFirst("AccessKey"));
            method.set(exchange.getRequestMethod());
            query.set(exchange.getRequestURI().getRawQuery());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                {
                  "ErrorCode": 0,
                  "ErrorDescription": "null",
                  "Data": [{"MessageErrorCode": 0, "MessageId": "message-id"}]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            BenterSmsGateway gateway = new BenterSmsGateway(RestClient.builder(), objectMapper);
            configure(gateway, "http://localhost:" + server.getAddress().getPort());

            SmsSendResult result = gateway.send("0673054445", "Test Message");

            assertTrue(result.sent());
            assertNull(accessKey.get());
            assertEquals("POST", method.get());
            assertNull(query.get());
            assertEquals("application/json", contentType.get());
            assertEquals(objectMapper.readTree("""
                {
                  "SenderId": "FORESIGHT",
                  "MessageParameters": [{"Number": "255673054445", "Text": "Test Message"}],
                  "ApiKey": "api-key",
                  "ClientId": "foresight"
                }
                """), objectMapper.readTree(requestBody.get()));
            assertTrue(objectMapper.readTree(requestBody.get()).path("MessageParameters").isArray());
        } finally {
            server.stop(0);
        }
    }

    private void configure(BenterSmsGateway gateway, String baseUrl) {
        ReflectionTestUtils.setField(gateway, "enabled", true);
        ReflectionTestUtils.setField(gateway, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(gateway, "sendPath", "/v1/sms/SendBulkSMS");
        ReflectionTestUtils.setField(gateway, "clientId", "foresight");
        ReflectionTestUtils.setField(gateway, "apiKey", "api-key");
        ReflectionTestUtils.setField(gateway, "senderId", "FORESIGHT");
        ReflectionTestUtils.setField(gateway, "connectTimeout", Duration.ofSeconds(3));
        ReflectionTestUtils.setField(gateway, "readTimeout", Duration.ofSeconds(8));
    }
}
