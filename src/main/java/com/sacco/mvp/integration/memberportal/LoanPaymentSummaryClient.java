package com.sacco.mvp.integration.memberportal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class LoanPaymentSummaryClient {

    private static final ParameterizedTypeReference<List<LoanPaymentSummaryDto>> RESPONSE_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestClient.Builder restClientBuilder;

    @Value("${external.memberportal.base-url:https://api.foresightfin.app}")
    private String baseUrl;

    @Value("${external.memberportal.connect-timeout:3s}")
    private Duration connectTimeout;

    @Value("${external.memberportal.read-timeout:8s}")
    private Duration readTimeout;

    public LoanPaymentSummaryClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public Optional<LoanPaymentSummaryDto> fetchSummary(String memberNumber, String stationId, String loanId) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/loan-payment-summary")
                .queryParam("memberNumber", memberNumber)
                .queryParam("stationId", stationId)
                .queryParam("loanId", loanId)
                .build()
                .toUri();
            log.info("Memberportal loan-payment-summary request: {}", requestUri);
            ResponseEntity<List<LoanPaymentSummaryDto>> response = client().get()
                .uri(requestUri)
                .header("Content-Type", "application/json")
                .retrieve()
                .toEntity(RESPONSE_TYPE);
            if (!HttpStatus.OK.equals(response.getStatusCode())) {
                throw new LoanPaymentLookupException(
                    "Loan payment summary lookup failed with status " + response.getStatusCode() + ".", null);
            }
            List<LoanPaymentSummaryDto> body = response.getBody();
            List<LoanPaymentSummaryDto> summaries = body == null ? Collections.emptyList() : body;
            log.info("Memberportal loan-payment-summary lookup completed: loanId={}, summaries={}",
                loanId, summaries.size());
            return summaries.stream().filter(java.util.Objects::nonNull).findFirst();
        } catch (RestClientResponseException ex) {
            log.warn("Memberportal loan-payment-summary lookup failed with status {}. Response body: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new LoanPaymentLookupException(
                "Loan payment summary lookup failed with status " + ex.getStatusCode() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Memberportal loan-payment-summary lookup could not reach the external service: {}", ex.getMessage());
            throw new LoanPaymentLookupException("Unable to reach the memberportal service.", ex);
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
}
