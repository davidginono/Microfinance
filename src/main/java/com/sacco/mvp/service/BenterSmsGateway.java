package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.sms.enabled:false}")
    private boolean enabled;

    @Value("${app.sms.benter.base-url:https://api.onfonmedia.co.ke}")
    private String baseUrl;

    @Value("${app.sms.benter.send-path:/v1/sms/SendBulkSMS}")
    private String sendPath;

    @Value("${app.sms.benter.client-id:}")
    private String clientId;

    @Value("${app.sms.benter.api-key:}")
    private String apiKey;

    @Value("${app.sms.benter.sender-id:INFO}")
    private String senderId;

    @Value("${app.sms.connect-timeout:3s}")
    private Duration connectTimeout;

    @Value("${app.sms.read-timeout:8s}")
    private Duration readTimeout;

    @Override
    public SmsSendResult send(String phoneNumber, String message) {
        String normalizedPhone = TanzaniaPhoneNumber.normalizeOptional(phoneNumber);
        if (!enabled) {
            return SmsSendResult.skipped("SMS is disabled");
        }
        if (clientId.isBlank() || apiKey.isBlank()) {
            return SmsSendResult.skipped("Benter Group credentials are not configured");
        }
        if (senderId.isBlank()) {
            return SmsSendResult.skipped("Benter Group sender ID is not configured");
        }
        if (normalizedPhone == null) {
            return SmsSendResult.skipped("Recipient phone number is missing");
        }
        if (message == null || message.isBlank()) {
            return SmsSendResult.skipped("SMS message is empty");
        }

        BenterSendRequest payload = new BenterSendRequest(
            senderId,
            List.of(new BenterMessage(normalizedPhone, trimMessage(message))),
            apiKey,
            clientId
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            String response = client().post()
                .uri(sendPath)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(jsonBody)
                .retrieve()
                .body(String.class);
            return resultFromResponse(response);
        } catch (RestClientResponseException ex) {
            log.warn("Benter Group SMS failed with status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return SmsSendResult.rejected("Benter Group SMS failed with status " + ex.getStatusCode());
        } catch (ResourceAccessException ex) {
            log.warn("Benter Group SMS acceptance is unknown: {}", ex.getMessage());
            return SmsSendResult.acceptanceUnknown("Benter Group SMS acceptance is unknown");
        } catch (Exception ex) {
            log.warn("Benter Group SMS failed: {}", ex.getMessage());
            return SmsSendResult.acceptanceUnknown("Benter Group SMS acceptance is unknown");
        }
    }

    private RestClient client() {
        return restClientBuilder
            .requestFactory(requestFactory())
            .baseUrl(baseUrl)
            .build();
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }

    private SmsSendResult resultFromResponse(String response) throws Exception {
        if (response == null || response.isBlank()) {
            return SmsSendResult.acceptanceUnknown("Benter Group SMS returned an empty response");
        }
        JsonNode root = objectMapper.readTree(response);
        String errorCode = root.path("ErrorCode").asText();
        if (!"0".equals(errorCode) && !"000".equals(errorCode)) {
            String errorDescription = root.path("ErrorDescription").asText("Unknown Benter Group SMS error");
            return SmsSendResult.rejected("Benter Group SMS failed: " + errorDescription);
        }
        JsonNode firstMessage = root.path("Data").isArray() && root.path("Data").size() > 0
            ? root.path("Data").get(0)
            : null;
        if (firstMessage == null) {
            return SmsSendResult.acceptanceUnknown("Benter Group SMS returned no message result");
        }
        if (firstMessage.path("MessageErrorCode").asInt(-1) != 0) {
            String description = firstMessage.path("MessageErrorDescription").asText("Unknown Benter Group SMS message error");
            return SmsSendResult.rejected("Benter Group SMS failed: " + description);
        }
        String messageId = firstMessage.path("MessageId").asText(response);
        return SmsSendResult.sent(messageId);
    }

    private String trimMessage(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= 320 ? trimmed : trimmed.substring(0, 317) + "...";
    }

    private record BenterSendRequest(
        @JsonProperty("SenderId") String senderId,
        @JsonProperty("MessageParameters") List<BenterMessage> messageParameters,
        @JsonProperty("ApiKey") String apiKey,
        @JsonProperty("ClientId") String clientId
    ) {
    }

    private record BenterMessage(
        @JsonProperty("Number") String number,
        @JsonProperty("Text") String text
    ) {
    }
}
