package com.sacco.mvp.integration.foresight;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
@Slf4j
@RequiredArgsConstructor
public class ForesightDirectoryService {
    private final RestClient.Builder restClientBuilder;

    @Value("${external.foresight.base-url:https://api.foresightfin.app}")
    private String baseUrl;

    public ForesightMemberProfile fetchMemberProfileByEmail(String email) {
        log.info("Foresight member-profile lookup by email started: {}", email);
        return fetchMemberProfile("/member-profile", "email", email);
    }

    public ForesightMemberProfile fetchMemberProfileByEmailV2(String email) {
        log.info("Foresight member-profile-v2 lookup by email started: {}", email);
        return fetchMemberProfile("/member-profile-v2", "email", email);
    }

    public ForesightMemberProfile fetchMemberProfileByPhone(String phoneNumber) {
        log.info("Foresight member-profile lookup by phone started: {}", phoneNumber);
        return fetchMemberProfile("/member-profile", "phoneNumber", phoneNumber);
    }

    public ForesightAccountSummary fetchAccountSummary(String memberNumber, String stationId) {
        try {
            URI requestUri = URI.create(baseUrl + "/account-summary?memberNumber=" + memberNumber + "&stationId=" + stationId);
            log.info("Foresight account-summary lookup started: {}", requestUri);
            ForesightAccountSummary summary = client().get()
                .uri(requestUri)
                .retrieve()
                .body(ForesightAccountSummary.class);
            log.info("Foresight account-summary lookup completed: savingsBalance={}, sharesBalance={}, depositsBalance={}",
                summary == null ? null : summary.savingsBalance(),
                summary == null ? null : summary.sharesBalance(),
                summary == null ? null : summary.depositsBalance());
            return summary;
        } catch (RestClientResponseException ex) {
            log.warn("Foresight account-summary lookup failed with status {}. Response body: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new IllegalStateException("External account summary lookup failed with status " + ex.getRawStatusCode() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight account-summary lookup could not reach the external directory: {}", ex.getMessage());
            throw new IllegalStateException("Unable to reach the external member directory.", ex);
        }
    }

    private ForesightMemberProfile fetchMemberProfile(String path, String paramName, String value) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParam(paramName, value)
                .build()
                .toUri();
            log.info("Foresight member-profile request: {}", requestUri);
            ForesightMemberProfile profile = client().get()
                .uri(requestUri)
                .retrieve()
                .body(ForesightMemberProfile.class);
            log.info("Foresight member-profile lookup completed: memberNo={}, stationId={}, saccoName={}",
                profile == null ? null : profile.memberNo(),
                profile == null ? null : profile.stationId(),
                profile == null ? null : profile.saccoName());
            return profile;
        } catch (RestClientResponseException ex) {
            log.warn("Foresight member-profile lookup failed for {} with status {}. Response body: {}",
                paramName, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ExternalDirectoryLookupException(ex.getRawStatusCode(),
                "External member profile lookup failed with status " + ex.getRawStatusCode() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight member-profile lookup could not reach the external directory: {}", ex.getMessage());
            throw new IllegalStateException("Unable to reach the external member directory.", ex);
        }
    }

    private RestClient client() {
        return restClientBuilder
            .baseUrl(baseUrl)
            .build();
    }
}
