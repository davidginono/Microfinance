package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
            PlatformSmsGatewaySettingsService settingsService = mock(PlatformSmsGatewaySettingsService.class);
            when(settingsService.resolvedConfig()).thenReturn(new PlatformSmsGatewaySettingsService.ResolvedSmsGatewayConfig(
                true,
                "http://localhost:" + server.getAddress().getPort(),
                "/v1/sms/SendBulkSMS",
                "foresight",
                "api-key",
                "FORESIGHT",
                3,
                8,
                true,
                true,
                true
            ));
            BenterSmsGateway gateway = new BenterSmsGateway(RestClient.builder(), objectMapper, settingsService);

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
}
