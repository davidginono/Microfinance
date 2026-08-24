package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStationPolicy;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.ExternalGuarantorRegistryRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanQualificationPolicyService {
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final MemberRepository memberRepository;
    private final ExternalGuarantorRegistryRepository externalGuarantorRegistryRepository;
    private final SaccoStationPolicyRepository saccoStationPolicyRepository;
    private final LoanAnalyticsService loanAnalyticsService;
    private final EligibilityService eligibilityService;

    public void assertApplicantEligible(String saccoId, UUID memberId) {
        applicantFailureReason(saccoId, memberId).ifPresent(reason -> {
            throw new IllegalStateException(reason);
        });
    }

    public Optional<String> applicantFailureReason(String saccoId, UUID memberId) {
        SaccoSettings settings = saccoSettingsRepository.findById(saccoId).orElse(null);
        if (settings == null) {
            return Optional.empty();
        }
        String stationId = resolveMemberStationId(memberId);
        ResolvedQualificationPolicy policy = resolvePolicy(settings, stationId);
        Integer maxDefaulted = positive(policy.applicantMaxDefaultedLoans());
        if (maxDefaulted != null && loanAnalyticsService.defaultedRiskLoanCount(memberId, saccoId, stationId) >= maxDefaulted) {
            return Optional.of("You cannot apply because your defaulted loan count has reached the station policy limit.");
        }
        return Optional.empty();
    }

    public EligibilitySummary eligibilitySummary(String saccoId, UUID memberId) {
        Optional<String> applicantReason = applicantFailureReason(saccoId, memberId);
        Optional<String> guarantorReason = guarantorFailureReason(saccoId, memberId);
        String stationId = resolveMemberStationId(memberId);
        BigDecimal totalGuaranteedAmount = guaranteedActiveAmount(saccoId, memberId, stationId);
        String reason = applicantReason.or(() -> guarantorReason)
            .orElse("All eligibility requirements are met.");
        return new EligibilitySummary(
            applicantReason.isEmpty(),
            guarantorReason.isEmpty(),
            applicantReason.orElse(null),
            guarantorReason.orElse(null),
            totalGuaranteedAmount,
            reason
        );
    }

    public Optional<String> guarantorFailureReason(String saccoId, UUID guarantorMemberId) {
        return guarantorFailureReason(saccoId, guarantorMemberId, null);
    }

    public Optional<String> guarantorFailureReason(String saccoId, UUID guarantorMemberId, BigDecimal pendingGuaranteedAmount) {
        return guarantorFailureReason(saccoId, guarantorMemberId, pendingGuaranteedAmount, null);
    }

    public Optional<String> guarantorFailureReason(String saccoId, UUID guarantorMemberId, BigDecimal pendingGuaranteedAmount, LoanProductSetting product) {
        SaccoSettings settings = saccoSettingsRepository.findById(saccoId).orElse(null);
        if (settings == null) {
            return Optional.empty();
        }
        String stationId = resolveMemberStationId(guarantorMemberId);
        ResolvedQualificationPolicy policy = resolvePolicy(settings, stationId);
        BigDecimal minSavings = product == null || !product.isGuarantorMinSavingsCheckRequired()
            ? null
            : positive(product.getGuarantorMinimumSavings());
        if (minSavings != null) {
            BigDecimal savings = eligibilityService.resolveSavings(guarantorMemberId);
            if (savings.compareTo(minSavings) < 0) {
                return Optional.of("Disabled: guarantor savings are "
                    + formatAmount(savings)
                    + ", below the required minimum of "
                    + formatAmount(minSavings)
                    + " for this loan product.");
            }
        }
        if (!policy.guarantorWithActiveLoanAllowed()
            && nullToZero(loanAnalyticsService.activeLoanAmount(guarantorMemberId, saccoId, stationId)).compareTo(BigDecimal.ZERO) > 0) {
            return Optional.of("Disabled: active loans are not allowed for guarantors under the station policy.");
        }
        Integer maxDefaulted = positive(policy.guarantorMaxDefaultedLoans());
        if (maxDefaulted != null && loanAnalyticsService.defaultedRiskLoanCount(guarantorMemberId, saccoId, stationId) >= maxDefaulted) {
            return Optional.of("Disabled: defaulted loan count has reached the station guarantor limit.");
        }
        Integer maxGuarantees = positiveGuaranteeCount(policy.guarantorMaxGuaranteedLoanAmount());
        if (maxGuarantees != null) {
            long currentGuarantees = activeGuaranteeCount(saccoId, guarantorMemberId, stationId);
            long projectedGuarantees = currentGuarantees + (pendingGuaranteedAmount == null ? 0 : 1);
            if (pendingGuaranteedAmount != null && projectedGuarantees > maxGuarantees) {
                return Optional.of("Disabled: approving this loan would exceed the station guarantor guarantee count limit.");
            }
            if (currentGuarantees >= maxGuarantees) {
                return Optional.of("Disabled: active guarantee count has reached the station guarantor limit.");
            }
        }
        return Optional.empty();
    }

    public Optional<String> guarantorFailureReasonForExternal(String saccoId,
                                                              String stationId,
                                                              String externalMemberNo,
                                                              String externalStationId,
                                                              BigDecimal savings,
                                                              int activeLoanCount,
                                                              int defaultedRiskLoanCount,
                                                              BigDecimal pendingGuaranteedAmount,
                                                              LoanProductSetting product) {
        SaccoSettings settings = saccoSettingsRepository.findById(saccoId).orElse(null);
        if (settings == null) {
            return Optional.empty();
        }
        ResolvedQualificationPolicy policy = resolvePolicy(settings, stationId);
        BigDecimal minSavings = product == null || !product.isGuarantorMinSavingsCheckRequired()
            ? null
            : positive(product.getGuarantorMinimumSavings());
        if (minSavings != null && nullToZero(savings).compareTo(minSavings) < 0) {
            return Optional.of("Disabled: guarantor savings are "
                + formatAmount(savings)
                + ", below the required minimum of "
                + formatAmount(minSavings)
                + " for this loan product.");
        }
        if (!policy.guarantorWithActiveLoanAllowed() && activeLoanCount > 0) {
            return Optional.of("Disabled: active loans are not allowed for guarantors under the station policy.");
        }
        Integer maxDefaulted = positive(policy.guarantorMaxDefaultedLoans());
        if (maxDefaulted != null && defaultedRiskLoanCount >= maxDefaulted) {
            return Optional.of("Disabled: defaulted loan count has reached the station guarantor limit.");
        }
        Integer maxGuarantees = positiveGuaranteeCount(policy.guarantorMaxGuaranteedLoanAmount());
        if (maxGuarantees != null && externalMemberNo != null && externalStationId != null) {
            UUID registryId = externalGuarantorRegistryRepository
                .findBySaccoIdAndExternalStationIdIgnoreCaseAndExternalMemberNoIgnoreCase(
                    saccoId,
                    externalStationId,
                    externalMemberNo
                )
                .map(com.sacco.mvp.domain.ExternalGuarantorRegistry::getId)
                .orElse(null);
            long currentGuarantees = guarantorRequestRepository.countExternalActiveGuarantees(
                registryId,
                externalMemberNo,
                externalStationId,
                saccoId,
                stationId
            );
            long projectedGuarantees = currentGuarantees + (pendingGuaranteedAmount == null ? 0 : 1);
            if (pendingGuaranteedAmount != null && projectedGuarantees > maxGuarantees) {
                return Optional.of("Disabled: approving this loan would exceed the station guarantor guarantee count limit.");
            }
            if (currentGuarantees >= maxGuarantees) {
                return Optional.of("Disabled: active guarantee count has reached the station guarantor limit.");
            }
        }
        return Optional.empty();
    }

    public void assertGuarantorEligible(String saccoId, UUID guarantorMemberId) {
        assertGuarantorEligible(saccoId, guarantorMemberId, null);
    }

    public void assertGuarantorEligible(String saccoId, UUID guarantorMemberId, BigDecimal pendingGuaranteedAmount) {
        guarantorFailureReason(saccoId, guarantorMemberId, pendingGuaranteedAmount).ifPresent(reason -> {
            throw new IllegalArgumentException(reason);
        });
    }

    private BigDecimal guaranteedActiveAmount(String saccoId, UUID guarantorMemberId, String stationId) {
        return guarantorRequestRepository.sumActiveGuaranteedAmount(guarantorMemberId, saccoId, stationId);
    }

    private long activeGuaranteeCount(String saccoId, UUID guarantorMemberId, String stationId) {
        return guarantorRequestRepository.countActiveGuarantees(guarantorMemberId, saccoId, stationId);
    }

    private ResolvedQualificationPolicy resolvePolicy(SaccoSettings settings, String stationId) {
        SaccoStationPolicy stationPolicy = stationId == null || stationId.isBlank()
            ? null
            : saccoStationPolicyRepository.findBySaccoIdAndStationId(settings.getSaccoId(), stationId).orElse(null);
        return new ResolvedQualificationPolicy(
            firstNonNull(stationPolicy == null ? null : stationPolicy.getApplicantMaxDefaultedLoans(), settings.getApplicantMaxDefaultedLoans()),
            firstNonNull(stationPolicy == null ? null : stationPolicy.getGuarantorWithActiveLoanAllowed(), settings.getGuarantorWithActiveLoanAllowed()) == null
                || firstNonNull(stationPolicy == null ? null : stationPolicy.getGuarantorWithActiveLoanAllowed(), settings.getGuarantorWithActiveLoanAllowed()),
            firstNonNull(stationPolicy == null ? null : stationPolicy.getGuarantorMaxGuaranteedLoanAmount(), settings.getGuarantorMaxGuaranteedLoanAmount()),
            firstNonNull(stationPolicy == null ? null : stationPolicy.getGuarantorMaxDefaultedLoans(), settings.getGuarantorMaxDefaultedLoans()),
            resolvedPortfolioAtRiskDays(settings, stationPolicy)
        );
    }

    public int resolvedPortfolioAtRiskDays(String saccoId, String stationId) {
        SaccoSettings settings = saccoSettingsRepository.findById(saccoId).orElse(null);
        if (settings == null) {
            return 30;
        }
        SaccoStationPolicy stationPolicy = stationId == null || stationId.isBlank()
            ? null
            : saccoStationPolicyRepository.findBySaccoIdAndStationId(settings.getSaccoId(), stationId).orElse(null);
        return resolvedPortfolioAtRiskDays(settings, stationPolicy);
    }

    public boolean externalDefaultedRiskCheckRequired(String saccoId, String stationId) {
        SaccoSettings settings = saccoSettingsRepository.findById(saccoId).orElse(null);
        if (settings == null) {
            return false;
        }
        return positive(resolvePolicy(settings, stationId).guarantorMaxDefaultedLoans()) != null;
    }

    private int resolvedPortfolioAtRiskDays(SaccoSettings settings, SaccoStationPolicy stationPolicy) {
        Integer configured = firstNonNull(
            stationPolicy == null ? null : stationPolicy.getPortfolioAtRiskDays(),
            settings == null ? null : settings.getPortfolioAtRiskDays()
        );
        if (configured == null) {
            return 30;
        }
        return Math.max(1, Math.min(365, configured));
    }

    private String resolveMemberStationId(UUID memberId) {
        return memberRepository.findById(memberId)
            .map(Member::getStationId)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .orElse(null);
    }

    private <T> T firstNonNull(T primary, T fallback) {
        return primary == null ? fallback : primary;
    }

    private Integer positive(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private BigDecimal positive(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0 ? null : value;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String formatAmount(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private Integer positiveGuaranteeCount(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0 ? null : value.intValue();
    }

    private record ResolvedQualificationPolicy(
        Integer applicantMaxDefaultedLoans,
        boolean guarantorWithActiveLoanAllowed,
        BigDecimal guarantorMaxGuaranteedLoanAmount,
        Integer guarantorMaxDefaultedLoans,
        Integer portfolioAtRiskDays
    ) {
    }

    public record EligibilitySummary(
        boolean canApply,
        boolean canGuarantee,
        String applicantReason,
        String guarantorReason,
        BigDecimal totalGuaranteedAmount,
        String reason
    ) {
        public boolean isCanApply() {
            return canApply;
        }

        public boolean isCanGuarantee() {
            return canGuarantee;
        }

        public String getApplicantReason() {
            return applicantReason;
        }

        public String getGuarantorReason() {
            return guarantorReason;
        }

        public BigDecimal getTotalGuaranteedAmount() {
            return totalGuaranteedAmount;
        }

        public String getReason() {
            return reason;
        }
    }
}
