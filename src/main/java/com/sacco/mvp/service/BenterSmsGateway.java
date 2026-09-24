package com.sacco.mvp.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BenterSmsGateway implements SmsGateway {
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final PlatformSmsGatewaySettingsService smsGatewaySettingsService;

    @Override
    public SmsSendResult send(String phoneNumber, String message) {
        PlatformSmsGatewaySettingsService.ResolvedSmsGatewayConfig config = smsGatewaySettingsService.resolvedConfig();
        String normalizedPhone = TanzaniaPhoneNumber.normalizeOptional(phoneNumber);
        if (!config.enabled()) {
            return SmsSendResult.skipped("SMS is disabled");
        }
        if (config.username().isBlank() || config.apiKey().isBlank()) {
            return SmsSendResult.skipped("Benter Group credentials are not configured");
        }
        if (config.senderId().isBlank()) {
            return SmsSendResult.skipped("Benter Group sender ID is not configured");
        }
        if (normalizedPhone == null) {
            return SmsSendResult.skipped("Recipient phone number is missing");
        }
        if (message == null || message.isBlank()) {
            return SmsSendResult.skipped("SMS message is empty");
        }

        BenterSendRequest payload = new BenterSendRequest(
            config.senderId(),
            config.username(),
            List.of(new BenterMessage(normalizedPhone, trimMessage(message)))
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            String response = client(config).post()
                .uri(config.sendPath())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header("apikey", config.apiKey())
                .body(jsonBody)
                .retrieve()
                .body(String.class);
            return resultFromResponse(response);
        } catch (RestClientResponseException ex) {
            log.warn("Benter Group SMS failed with status {}", ex.getStatusCode());
            return SmsSendResult.rejected("The SMS gateway returned an error.");
        } catch (ResourceAccessException ex) {
            log.warn("Benter Group SMS request failed: {}", ex.getMessage());
            if (causedByTimeout(ex)) {
                return SmsSendResult.acceptanceUnknown("The SMS gateway connection timed out.");
            }
            return SmsSendResult.acceptanceUnknown("The SMS gateway could not be reached.");
        } catch (Exception ex) {
            log.warn("Benter Group SMS failed: {}", ex.getMessage());
            return SmsSendResult.acceptanceUnknown("The SMS gateway could not be reached.");
        }
    }

    private RestClient client(PlatformSmsGatewaySettingsService.ResolvedSmsGatewayConfig config) {
        return restClientBuilder
            .requestFactory(requestFactory(config))
            .baseUrl(config.baseUrl())
            .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(PlatformSmsGatewaySettingsService.ResolvedSmsGatewayConfig config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(config.readTimeoutSeconds()));
        return requestFactory;
    }

    private SmsSendResult resultFromResponse(String response) throws Exception {
        if (response == null || response.isBlank()) {
            return SmsSendResult.acceptanceUnknown("Benter Group SMS returned an empty response");
        }
        JsonNode root = objectMapper.readTree(response);
        JsonNode firstMessage = root.path("data").isArray() && root.path("data").size() > 0
            ? root.path("data").get(0)
            : null;
        if (firstMessage == null) {
            return SmsSendResult.acceptanceUnknown("Benter Group SMS returned no message result");
        }
        int statusCode = firstMessage.path("statusCode").asInt(-1);
        if (statusCode != 200) {
            String description = firstMessage.path("description").asString("Unknown Benter Group SMS message error");
            return SmsSendResult.rejected("Benter Group SMS failed: " + description);
        }
        String messageId = firstMessage.path("messageId").asString();
        if (messageId == null || messageId.isBlank()) {
            return SmsSendResult.acceptanceUnknown("Benter Group SMS accepted without a message id");
        }
        return SmsSendResult.sent(messageId);
    }

    private boolean causedByTimeout(Throwable ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 8) {
            if (current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (message.contains("timed out") || message.contains("connect timed out")) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private String trimMessage(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= 320 ? trimmed : trimmed.substring(0, 317) + "...";
    }

    private record BenterSendRequest(
        @JsonProperty("senderid") String senderId,
        @JsonProperty("username") String username,
        @JsonProperty("content") List<BenterMessage> content
    ) {
    }

    private record BenterMessage(
        @JsonProperty("msisdn") String msisdn,
        @JsonProperty("message") String message
    ) {
    }
}
