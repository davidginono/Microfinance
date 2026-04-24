package com.sacco.mvp.integration.memberportal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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
            List<LoanPaymentSummaryDto> body = client().get()
                .uri(requestUri)
                .retrieve()
                .body(RESPONSE_TYPE);
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
        return restClientBuilder.baseUrl(baseUrl).build();
    }
}
