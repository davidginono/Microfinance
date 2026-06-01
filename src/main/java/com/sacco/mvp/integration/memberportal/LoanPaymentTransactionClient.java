package com.sacco.mvp.integration.memberportal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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

@Service
@Slf4j
public class LoanPaymentTransactionClient {

    private static final ParameterizedTypeReference<List<LoanPaymentTransactionDto>> RESPONSE_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestClient.Builder restClientBuilder;

    @Value("${external.memberportal.base-url:https://api.foresightfin.app}")
    private String baseUrl;

    @Value("${external.memberportal.connect-timeout:3s}")
    private Duration connectTimeout;

    @Value("${external.memberportal.read-timeout:8s}")
    private Duration readTimeout;

    public LoanPaymentTransactionClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * Fetches every payment transaction the memberportal knows about for the
     * given (member, station, loan) tuple. Callers are expected to filter by
     * date range as needed.
     */
    public List<LoanPaymentTransactionDto> fetchTransactions(String memberNumber, String stationId, String loanId) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/loan-payment-transactions")
                .queryParam("memberNumber", memberNumber)
                .queryParam("stationId", stationId)
                .queryParam("loanId", loanId)
                .build()
                .toUri();
            log.info("Memberportal loan-payment-transactions request: {}", requestUri);
            List<LoanPaymentTransactionDto> body = client().get()
                .uri(requestUri)
                .retrieve()
                .body(RESPONSE_TYPE);
            List<LoanPaymentTransactionDto> transactions = body == null ? Collections.emptyList() : body;
            log.info("Memberportal loan-payment-transactions lookup completed: loanId={}, transactions={}",
                loanId, transactions.size());
            return transactions;
        } catch (RestClientResponseException ex) {
            log.warn("Memberportal loan-payment-transactions lookup failed with status {}. Response body: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new LoanPaymentLookupException(
                "Loan payment transactions lookup failed with status " + ex.getStatusCode() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Memberportal loan-payment-transactions lookup could not reach the external service: {}", ex.getMessage());
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
