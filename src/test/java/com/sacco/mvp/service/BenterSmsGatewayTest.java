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
    void sendsDocumentedBulkPayloadWithApiKeyHeader() throws Exception {
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/version1/messaging/bulk", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
            method.set(exchange.getRequestMethod());
            query.set(exchange.getRequestURI().getRawQuery());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                {
                  "success": true,
                  "message": "Sent to 1/1. Cost: 1 units",
                  "data": [{
                    "statusCode": 200,
                    "description": "Success",
                    "msisdn": "255673054445",
                    "messageId": "message-id"
                  }]
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
                "/version1/messaging/bulk",
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
            assertEquals("api-key", apiKey.get());
            assertEquals("POST", method.get());
            assertNull(query.get());
            assertEquals("application/json", contentType.get());
            assertEquals(objectMapper.readTree("""
                {
                  "senderid": "FORESIGHT",
                  "username": "foresight",
                  "content": [{"msisdn": "255673054445", "message": "Test Message"}]
                }
                """), objectMapper.readTree(requestBody.get()));
            assertTrue(objectMapper.readTree(requestBody.get()).path("content").isArray());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void restoresUnitWhenBenterRejectsRecipientInAcceptedBatchResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/version1/messaging/bulk", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = """
                {
                  "success": true,
                  "message": "Sent to 0/1. Cost: 0 units",
                  "data": [{
                    "statusCode": 403,
                    "description": "Charging user foresight failed, Balance 0 Total Bill (1)",
                    "msisdn": "255673054445",
                    "messageId": ""
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            PlatformSmsGatewaySettingsService settingsService = mock(PlatformSmsGatewaySettingsService.class);
            when(settingsService.resolvedConfig()).thenReturn(new PlatformSmsGatewaySettingsService.ResolvedSmsGatewayConfig(
                true,
                "http://localhost:" + server.getAddress().getPort(),
                "/version1/messaging/bulk",
                "foresight",
                "api-key",
                "FORESIGHT",
                3,
                8,
                true,
                true,
                true
            ));
            BenterSmsGateway gateway = new BenterSmsGateway(RestClient.builder(), new ObjectMapper(), settingsService);

            SmsSendResult result = gateway.send("0673054445", "Test Message");

            assertEquals(SmsSendOutcome.REJECTED, result.outcome());
            assertTrue(result.message().contains("Balance 0"));
        } finally {
            server.stop(0);
        }
    }
}
