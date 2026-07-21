package com.sacco.mvp.integration.foresight;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class ForesightDirectoryService {
    private final RestClient.Builder restClientBuilder;

    @Value("${external.foresight.base-url:https://api.foresightfin.app}")
    private String baseUrl;

    @Value("${external.foresight.connect-timeout:3s}")
    private Duration connectTimeout;

    @Value("${external.foresight.read-timeout:8s}")
    private Duration readTimeout;

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

    public MemberProfileLookupResult lookupMemberProfileByEmailV2(String email) {
        log.info("Foresight staff-precheck member-profile-v2 lookup by email started: {}", email);
        return lookupMemberProfile("/member-profile-v2", "email", email);
    }

    public MemberProfileLookupResult lookupMemberProfileByPhone(String phoneNumber) {
        log.info("Foresight staff-precheck member-profile lookup by phone started: {}", phoneNumber);
        return lookupMemberProfile("/member-profile", "phoneNumber", phoneNumber);
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
            if (ex.getStatusCode().is5xxServerError()) {
                throw new UpstreamAvailabilityException(
                    "External account summary service is temporarily unavailable.", ex);
            }
            throw new IllegalStateException("External account summary lookup failed with status " + ex.getStatusCode().value() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight account-summary lookup could not reach the external directory: {}", ex.getMessage());
            throw new UpstreamAvailabilityException("Unable to reach the external member directory.", ex);
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
            throw new ExternalDirectoryLookupException(ex.getStatusCode().value(),
                "External member profile lookup failed with status " + ex.getStatusCode().value() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight member-profile lookup could not reach the external directory: {}", ex.getMessage());
            throw new IllegalStateException("Unable to reach the external member directory.", ex);
        }
    }

    private MemberProfileLookupResult lookupMemberProfile(String path, String paramName, String value) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParam(paramName, value)
                .build()
                .toUri();
            log.info("Foresight staff-precheck member-profile request: {}", requestUri);
            ForesightMemberProfile profile = client().get()
                .uri(requestUri)
                .retrieve()
                .body(ForesightMemberProfile.class);
            if (!hasProfileIdentity(profile)) {
                log.warn("Foresight staff-precheck member-profile returned an empty 200 response for {}", paramName);
                return MemberProfileLookupResult.unavailable();
            }
            return MemberProfileLookupResult.found(profile);
        } catch (RestClientResponseException ex) {
            log.warn("Foresight staff-precheck member-profile lookup failed for {} with status {}. Response body: {}",
                paramName, ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 404) {
                return MemberProfileLookupResult.notFound();
            }
            return MemberProfileLookupResult.unavailable();
        } catch (ResourceAccessException ex) {
            log.warn("Foresight staff-precheck member-profile lookup could not reach the external directory: {}", ex.getMessage());
            return MemberProfileLookupResult.unavailable();
        }
    }

    private boolean hasProfileIdentity(ForesightMemberProfile profile) {
        return profile != null && profile.memberNo() != null && !profile.memberNo().isBlank();
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

    public enum MemberProfileLookupStatus {
        FOUND,
        NOT_FOUND,
        UNAVAILABLE
    }

    public record MemberProfileLookupResult(MemberProfileLookupStatus status, ForesightMemberProfile profile) {
        public static MemberProfileLookupResult found(ForesightMemberProfile profile) {
            return new MemberProfileLookupResult(MemberProfileLookupStatus.FOUND, profile);
        }

        public static MemberProfileLookupResult notFound() {
            return new MemberProfileLookupResult(MemberProfileLookupStatus.NOT_FOUND, null);
        }

        public static MemberProfileLookupResult unavailable() {
            return new MemberProfileLookupResult(MemberProfileLookupStatus.UNAVAILABLE, null);
        }

        public boolean isFound() {
            return status == MemberProfileLookupStatus.FOUND;
        }

        public boolean isNotFound() {
            return status == MemberProfileLookupStatus.NOT_FOUND;
        }
    }
}
