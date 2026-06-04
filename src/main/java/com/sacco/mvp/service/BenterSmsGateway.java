package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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

    @Value("${app.sms.benter.access-key:}")
    private String accessKey;

    @Value("${app.sms.benter.sender-id:INFO}")
    private String senderId;

    @Value("${app.sms.connect-timeout:3s}")
    private Duration connectTimeout;

    @Value("${app.sms.read-timeout:8s}")
    private Duration readTimeout;

    @Override
    public SmsSendResult send(String phoneNumber, String message) {
        String normalizedPhone = normalizePhone(phoneNumber);
        if (!enabled) {
            return SmsSendResult.skipped("SMS is disabled");
        }
        if (clientId.isBlank() || apiKey.isBlank()) {
            return SmsSendResult.skipped("Benter Group credentials are not configured");
        }
        if (resolvedAccessKey().isBlank()) {
            return SmsSendResult.skipped("Benter Group access key is not configured");
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

        Map<String, Object> payload = Map.of(
            "SenderId", senderId,
            "IsUnicode", false,
            "IsFlash", false,
            "MessageParameters", List.of(Map.of(
                "Number", normalizedPhone,
                "Text", trimMessage(message)
            )),
            "ApiKey", apiKey,
            "ClientId", clientId
        );

        try {
            String response = client().post()
                .uri(sendPath)
                .header("AccessKey", resolvedAccessKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(payload)
                .retrieve()
                .body(String.class);
            return resultFromResponse(response);
        } catch (RestClientResponseException ex) {
            log.warn("Benter Group SMS failed with status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return SmsSendResult.skipped("Benter Group SMS failed with status " + ex.getStatusCode());
        } catch (Exception ex) {
            log.warn("Benter Group SMS failed: {}", ex.getMessage());
            return SmsSendResult.skipped("Benter Group SMS failed");
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

    private String resolvedAccessKey() {
        return accessKey.isBlank() ? apiKey : accessKey;
    }

    private SmsSendResult resultFromResponse(String response) throws Exception {
        if (response == null || response.isBlank()) {
            return SmsSendResult.skipped("Benter Group SMS returned an empty response");
        }
        JsonNode root = objectMapper.readTree(response);
        String errorCode = root.path("ErrorCode").asText();
        if (!"0".equals(errorCode) && !"000".equals(errorCode)) {
            String errorDescription = root.path("ErrorDescription").asText("Unknown Benter Group SMS error");
            return SmsSendResult.skipped("Benter Group SMS failed: " + errorDescription);
        }
        JsonNode firstMessage = root.path("Data").isArray() && root.path("Data").size() > 0
            ? root.path("Data").get(0)
            : null;
        String messageId = firstMessage == null ? response : firstMessage.path("MessageId").asText(response);
        return SmsSendResult.sent(messageId);
    }

    private String normalizePhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String digits = phoneNumber.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("0") && digits.length() == 10) {
            return "255" + digits.substring(1);
        }
        return digits.isBlank() ? null : digits;
    }

    private String trimMessage(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= 320 ? trimmed : trimmed.substring(0, 317) + "...";
    }
}
