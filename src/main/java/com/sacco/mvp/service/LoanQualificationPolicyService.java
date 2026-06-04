package com.sacco.mvp.service;

import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStationPolicy;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanQualificationPolicyService {
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final MemberRepository memberRepository;
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
        LoanAnalyticsService.MemberLoanAnalytics analytics = loanAnalyticsService.summarizeAllTime(memberId, saccoId, stationId);
        Integer maxDefaulted = positive(policy.applicantMaxDefaultedLoans());
        if (maxDefaulted != null && analytics.defaultedLoans() >= maxDefaulted) {
            return Optional.of("You cannot apply because your defaulted loan count has reached the station policy limit.");
        }
        Integer waitDays = positive(applicantForfeitedWaitDays(policy));
        if (waitDays != null) {
            Optional<ForfeitedApplicationRestriction> restriction = forfeitedApplicationRestriction(saccoId, memberId, stationId, waitDays);
            if (restriction.isPresent()) {
                return Optional.of(formatForfeitedApplicationRestriction(restriction.get()));
            }
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
        if (maxDefaulted != null && loanAnalyticsService.summarizeAllTime(guarantorMemberId, saccoId, stationId).defaultedLoans() >= maxDefaulted) {
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

    public void assertGuarantorEligible(String saccoId, UUID guarantorMemberId) {
        assertGuarantorEligible(saccoId, guarantorMemberId, null);
    }

    public void assertGuarantorEligible(String saccoId, UUID guarantorMemberId, BigDecimal pendingGuaranteedAmount) {
        guarantorFailureReason(saccoId, guarantorMemberId, pendingGuaranteedAmount).ifPresent(reason -> {
            throw new IllegalArgumentException(reason);
        });
    }

    private BigDecimal guaranteedActiveAmount(String saccoId, UUID guarantorMemberId, String stationId) {
        return guarantorRequestRepository.findByGuarantorMemberIdAndStatusOrderByCreatedAtDesc(
                guarantorMemberId,
                GuarantorRequestStatus.APPROVED
            )
            .stream()
            .map(request -> loanApplicationRepository.findById(request.getLoanApplicationId())
                .filter(app -> matchesScope(app, saccoId, stationId))
                .filter(this::isActiveGuaranteeLoan)
                .map(LoanApplication::getAmount)
                .orElse(null))
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long activeGuaranteeCount(String saccoId, UUID guarantorMemberId, String stationId) {
        return guarantorRequestRepository.findByGuarantorMemberIdAndStatusOrderByCreatedAtDesc(
                guarantorMemberId,
                GuarantorRequestStatus.APPROVED
            )
            .stream()
            .map(request -> loanApplicationRepository.findById(request.getLoanApplicationId()).orElse(null))
            .filter(app -> matchesScope(app, saccoId, stationId))
            .filter(this::isActiveGuaranteeLoan)
            .count();
    }

    private ResolvedQualificationPolicy resolvePolicy(SaccoSettings settings, String stationId) {
        SaccoStationPolicy stationPolicy = stationId == null || stationId.isBlank()
            ? null
            : saccoStationPolicyRepository.findBySaccoIdAndStationId(settings.getSaccoId(), stationId).orElse(null);
        return new ResolvedQualificationPolicy(
            firstNonNull(stationPolicy == null ? null : stationPolicy.getApplicantMaxDefaultedLoans(), settings.getApplicantMaxDefaultedLoans()),
            firstNonNull(stationPolicy == null ? null : stationPolicy.getApplicantMaxForfeitedLoans(), settings.getApplicantMaxForfeitedLoans()),
            firstNonNull(stationPolicy == null ? null : stationPolicy.getApplicantForfeitedLookbackDays(), settings.getApplicantForfeitedLookbackDays()),
            firstNonNull(stationPolicy == null ? null : stationPolicy.getApplicantForfeitedWaitDays(), settings.getApplicantForfeitedWaitDays()),
            firstNonNull(stationPolicy == null ? null : stationPolicy.getGuarantorWithActiveLoanAllowed(), settings.getGuarantorWithActiveLoanAllowed()) == null
                || firstNonNull(stationPolicy == null ? null : stationPolicy.getGuarantorWithActiveLoanAllowed(), settings.getGuarantorWithActiveLoanAllowed()),
            firstNonNull(stationPolicy == null ? null : stationPolicy.getGuarantorMaxGuaranteedLoanAmount(), settings.getGuarantorMaxGuaranteedLoanAmount()),
            firstNonNull(stationPolicy == null ? null : stationPolicy.getGuarantorMaxDefaultedLoans(), settings.getGuarantorMaxDefaultedLoans())
        );
    }

    public Optional<Integer> applicantForfeitedWaitDays(String saccoId, UUID memberId) {
        SaccoSettings settings = saccoSettingsRepository.findById(saccoId).orElse(null);
        if (settings == null) {
            return Optional.empty();
        }
        String stationId = resolveMemberStationId(memberId);
        return Optional.ofNullable(positive(applicantForfeitedWaitDays(resolvePolicy(settings, stationId))));
    }

    private Integer applicantForfeitedWaitDays(ResolvedQualificationPolicy policy) {
        return firstNonNull(policy.applicantForfeitedWaitDays(), policy.applicantForfeitedLookbackDays());
    }

    private Optional<ForfeitedApplicationRestriction> forfeitedApplicationRestriction(String saccoId,
                                                                                      UUID memberId,
                                                                                      String stationId,
                                                                                      int waitDays) {
        LocalDate today = LocalDate.now();
        return loanApplicationRepository
            .findByApplicantMemberIdAndStatusOrderByCreatedAtDesc(memberId, LoanStatus.FORFEITED)
            .stream()
            .filter(app -> matchesScope(app, saccoId, stationId))
            .map(this::forfeitedApplicationDate)
            .map(date -> date.plusDays(waitDays))
            .filter(releaseDate -> releaseDate.isAfter(today))
            .max(LocalDate::compareTo)
            .map(releaseDate -> new ForfeitedApplicationRestriction(
                Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(today, releaseDate)),
                releaseDate
            ));
    }

    private LocalDate forfeitedApplicationDate(LoanApplication app) {
        return firstNonNull(app.getUpdatedAt(), app.getCreatedAt()).toLocalDate();
    }

    private String formatForfeitedApplicationRestriction(ForfeitedApplicationRestriction restriction) {
        String dayLabel = restriction.daysLeft() == 1 ? "day" : "days";
        return "You cannot apply because your last forfeited loan application is still within the station waiting period. You can apply again in "
            + restriction.daysLeft() + " " + dayLabel + ", on " + restriction.releaseDate() + ".";
    }

    private String resolveMemberStationId(UUID memberId) {
        return memberRepository.findById(memberId)
            .map(Member::getStationId)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .orElse(null);
    }

    private boolean matchesStation(LoanApplication app, String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return true;
        }
        return app != null && app.getStationId() != null && stationId.equalsIgnoreCase(app.getStationId());
    }

    private boolean matchesScope(LoanApplication app, String saccoId, String stationId) {
        if (app == null) {
            return false;
        }
        if (saccoId != null && !saccoId.isBlank() && !saccoId.equalsIgnoreCase(app.getSaccoId())) {
            return false;
        }
        return matchesStation(app, stationId);
    }

    private <T> T firstNonNull(T primary, T fallback) {
        return primary == null ? fallback : primary;
    }

    private boolean isActiveGuaranteeLoan(LoanApplication app) {
        return app.getStatus() == com.sacco.mvp.domain.LoanStatus.FINAL_APPROVED
            || app.getStatus() == com.sacco.mvp.domain.LoanStatus.DEFAULTED
            || app.getStatus() == com.sacco.mvp.domain.LoanStatus.READY_FOR_DISBURSEMENT
            || app.getStatus() == com.sacco.mvp.domain.LoanStatus.AWAITING_ACCOUNTANT
            || app.getStatus() == com.sacco.mvp.domain.LoanStatus.AWAITING_BOARD
            || app.getStatus() == com.sacco.mvp.domain.LoanStatus.AWAITING_LOAN_OFFICER
            || app.getStatus() == com.sacco.mvp.domain.LoanStatus.READY_FOR_MANAGER;
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
        Integer applicantMaxForfeitedLoans,
        Integer applicantForfeitedLookbackDays,
        Integer applicantForfeitedWaitDays,
        boolean guarantorWithActiveLoanAllowed,
        BigDecimal guarantorMaxGuaranteedLoanAmount,
        Integer guarantorMaxDefaultedLoans
    ) {
    }

    private record ForfeitedApplicationRestriction(
        long daysLeft,
        LocalDate releaseDate
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
