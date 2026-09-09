package com.sacco.mvp.integration.foresight;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;

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

    public MemberProfileLookupResult lookupMemberProfileByEmail(String email) {
        log.info("Foresight member-profile lookup by email started");
        return lookupMemberProfile("/member-profile", "email", email);
    }

    public MemberProfileLookupResult lookupMemberProfileByPhone(String phoneNumber) {
        log.info("Foresight member-profile lookup by phone started");
        return lookupMemberProfile("/member-profile", "phoneNumber", phoneNumber);
    }

    public ForesightAccountSummary fetchAccountSummary(String memberNumber, String stationId) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/account-summary")
                .queryParam("memberNumber", memberNumber)
                .queryParam("stationId", stationId)
                .build()
                .toUri();
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

    public List<ForesightActiveLoan> fetchActiveLoans(String memberNumber, String stationId) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/active-loans/records")
                .queryParam("memberNumber", memberNumber)
                .queryParam("stationId", stationId)
                .build()
                .toUri();
            log.info("Foresight active-loans lookup started: {}", requestUri);
            List<ForesightActiveLoan> loans = client().get()
                .uri(requestUri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ForesightActiveLoan>>() {});
            log.info("Foresight active-loans lookup completed: rows={}", loans == null ? 0 : loans.size());
            return loans == null ? List.of() : loans;
        } catch (RestClientResponseException ex) {
            log.warn("Foresight active-loans lookup failed with status {}. Response body: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new UpstreamAvailabilityException(
                    "External active loans service is temporarily unavailable.", ex);
            }
            throw new IllegalStateException("External active loans lookup failed with status " + ex.getStatusCode().value() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight active-loans lookup could not reach the external directory: {}", ex.getMessage());
            throw new UpstreamAvailabilityException("Unable to reach the external active loans service.", ex);
        }
    }

    public List<ForesightActiveLoan> fetchPaidLoans(String memberNumber, String stationId) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/paid-loans/records")
                .queryParam("memberNumber", memberNumber)
                .queryParam("stationId", stationId)
                .build()
                .toUri();
            log.info("Foresight paid-loans lookup started: {}", requestUri);
            List<ForesightActiveLoan> loans = client().get()
                .uri(requestUri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ForesightActiveLoan>>() {});
            log.info("Foresight paid-loans lookup completed: rows={}", loans == null ? 0 : loans.size());
            return loans == null ? List.of() : loans;
        } catch (RestClientResponseException ex) {
            log.warn("Foresight paid-loans lookup failed with status {}. Response body: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new UpstreamAvailabilityException(
                    "External paid loans service is temporarily unavailable.", ex);
            }
            throw new IllegalStateException("External paid loans lookup failed with status " + ex.getStatusCode().value() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight paid-loans lookup could not reach the external directory: {}", ex.getMessage());
            throw new UpstreamAvailabilityException("Unable to reach the external paid loans service.", ex);
        }
    }

    public List<ForesightInvestment> fetchInvestments(String memberNumber, String stationId, int investmentCode) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/investments")
                .queryParam("memberNumber", memberNumber)
                .queryParam("stationId", stationId)
                .queryParam("investmentCode", investmentCode)
                .build()
                .toUri();
            log.info("Foresight investments lookup started: {}", requestUri);
            List<ForesightInvestment> investments = client().get()
                .uri(requestUri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ForesightInvestment>>() {});
            log.info("Foresight investments lookup completed: investmentCode={}, rows={}",
                investmentCode,
                investments == null ? 0 : investments.size());
            return investments == null ? List.of() : investments;
        } catch (RestClientResponseException ex) {
            log.warn("Foresight investments lookup failed with status {}. Response body: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new UpstreamAvailabilityException(
                    "External investments service is temporarily unavailable.", ex);
            }
            throw new IllegalStateException("External investments lookup failed with status " + ex.getStatusCode().value() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight investments lookup could not reach the external directory: {}", ex.getMessage());
            throw new UpstreamAvailabilityException("Unable to reach the external investments service.", ex);
        }
    }

    public boolean isLoanPaymentSummaryMissing(String memberNumber, String stationId, String loanId) {
        try {
            return fetchLoanPaymentSummary(memberNumber, stationId, loanId).isEmpty();
        } catch (IllegalStateException ex) {
            if (ex.getCause() instanceof RestClientResponseException response
                && response.getStatusCode().value() == 404) {
                return true;
            }
            throw ex;
        }
    }

    public List<ForesightLoanPaymentSummary> fetchLoanPaymentSummary(String memberNumber, String stationId, String loanId) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/loan-payment-summary")
                .queryParam("memberNumber", memberNumber)
                .queryParam("stationId", stationId)
                .queryParam("loanId", loanId)
                .build()
                .toUri();
            log.info("Foresight loan-payment-summary lookup started: {}", requestUri);
            List<ForesightLoanPaymentSummary> summaries = client().get()
                .uri(requestUri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ForesightLoanPaymentSummary>>() {});
            log.info("Foresight loan-payment-summary lookup completed: loanId={}, rows={}",
                loanId,
                summaries == null ? 0 : summaries.size());
            if (summaries == null) {
                throw new IllegalStateException("External loan payment summary returned no JSON body.");
            }
            return summaries;
        } catch (RestClientResponseException ex) {
            log.warn("Foresight loan-payment-summary lookup failed with status {}. Response body: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new UpstreamAvailabilityException(
                    "External loan payment summary service is temporarily unavailable.", ex);
            }
            throw new IllegalStateException("External loan payment summary lookup failed with status " + ex.getStatusCode().value() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight loan-payment-summary lookup could not reach the external directory: {}", ex.getMessage());
            throw new UpstreamAvailabilityException("Unable to reach the external loan payment summary service.", ex);
        }
    }

    public List<ForesightRepaymentScheduleRow> fetchRepaymentSchedule(String memberNumber, String stationId, String loanId) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/repayment-schedule")
                .queryParam("memberNumber", memberNumber)
                .queryParam("stationId", stationId)
                .queryParam("loanId", loanId)
                .build()
                .toUri();
            log.info("Foresight repayment-schedule lookup started: {}", requestUri);
            List<ForesightRepaymentScheduleRow> rows = client().get()
                .uri(requestUri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ForesightRepaymentScheduleRow>>() {});
            log.info("Foresight repayment-schedule lookup completed: loanId={}, rows={}",
                loanId,
                rows == null ? 0 : rows.size());
            return rows == null ? List.of() : rows;
        } catch (RestClientResponseException ex) {
            log.warn("Foresight repayment-schedule lookup failed with status {}. Response body: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new UpstreamAvailabilityException(
                    "External repayment schedule service is temporarily unavailable.", ex);
            }
            throw new IllegalStateException("External repayment schedule lookup failed with status " + ex.getStatusCode().value() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight repayment-schedule lookup could not reach the external directory: {}", ex.getMessage());
            throw new UpstreamAvailabilityException("Unable to reach the external repayment schedule service.", ex);
        }
    }

    public List<ForesightLoanPaymentTransaction> fetchLoanPaymentTransactions(String memberNumber, String stationId, String loanId) {
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/loan-payment-transactions")
                .queryParam("memberNumber", memberNumber)
                .queryParam("stationId", stationId)
                .queryParam("loanId", loanId)
                .build()
                .toUri();
            log.info("Foresight loan-payment-transactions lookup started: {}", requestUri);
            List<ForesightLoanPaymentTransaction> transactions = client().get()
                .uri(requestUri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ForesightLoanPaymentTransaction>>() {});
            log.info("Foresight loan-payment-transactions lookup completed: loanId={}, rows={}",
                loanId,
                transactions == null ? 0 : transactions.size());
            return transactions == null ? List.of() : transactions;
        } catch (RestClientResponseException ex) {
            log.warn("Foresight loan-payment-transactions lookup failed with status {}. Response body: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is5xxServerError()) {
                throw new UpstreamAvailabilityException(
                    "External loan payment transaction service is temporarily unavailable.", ex);
            }
            throw new IllegalStateException("External loan payment transactions lookup failed with status " + ex.getStatusCode().value() + ".", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Foresight loan-payment-transactions lookup could not reach the external directory: {}", ex.getMessage());
            throw new UpstreamAvailabilityException("Unable to reach the external loan payment transaction service.", ex);
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
