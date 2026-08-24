package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.ExternalGuarantorRegistry;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStationPolicy;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.ExternalGuarantorRegistryRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanQualificationPolicyServiceTest {
    private static final String SACCO_ID = "SACCO-01";
    private static final String STATION_ID = "AR704";

    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ExternalGuarantorRegistryRepository externalGuarantorRegistryRepository;
    @Mock private SaccoStationPolicyRepository saccoStationPolicyRepository;
    @Mock private LoanAnalyticsService loanAnalyticsService;
    @Mock private EligibilityService eligibilityService;

    private LoanQualificationPolicyService service;

    @BeforeEach
    void setUp() {
        service = new LoanQualificationPolicyService(
            saccoSettingsRepository,
            guarantorRequestRepository,
            memberRepository,
            externalGuarantorRegistryRepository,
            saccoStationPolicyRepository,
            loanAnalyticsService,
            eligibilityService
        );
    }

    @Test
    void applicantDefaultedPolicyBlocksWhenAnyDefaultedLoanExists() {
        UUID applicantId = UUID.randomUUID();
        givenDefaultedPolicyMember(applicantId);
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .applicantMaxDefaultedLoans(1)
                .build()));
        when(loanAnalyticsService.defaultedRiskLoanCount(applicantId, SACCO_ID, STATION_ID))
            .thenReturn(1L);

        Optional<String> reason = service.applicantFailureReason(SACCO_ID, applicantId);

        assertThat(reason)
            .hasValue("You cannot apply because your defaulted loan count has reached the station policy limit.");
    }

    @Test
    void guarantorDefaultedPolicyBlocksWhenAnyDefaultedLoanExists() {
        UUID guarantorId = UUID.randomUUID();
        givenDefaultedPolicyMember(guarantorId);
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .guarantorMaxDefaultedLoans(1)
                .build()));
        when(loanAnalyticsService.defaultedRiskLoanCount(guarantorId, SACCO_ID, STATION_ID))
            .thenReturn(1L);

        Optional<String> reason = service.guarantorFailureReason(SACCO_ID, guarantorId);

        assertThat(reason)
            .hasValue("Disabled: defaulted loan count has reached the station guarantor limit.");
    }

    @Test
    void guarantorLimitBlocksWhenActiveGuaranteeCountHasReachedTheLimit() {
        UUID guarantorId = UUID.randomUUID();
        givenPolicy(guarantorId, "2");
        when(guarantorRequestRepository.countActiveGuarantees(guarantorId, SACCO_ID, STATION_ID)).thenReturn(2L);

        Optional<String> reason = service.guarantorFailureReason(SACCO_ID, guarantorId);

        assertThat(reason)
            .hasValue("Disabled: active guarantee count has reached the station guarantor limit.");
    }

    @Test
    void guarantorLimitBlocksApprovalWhenPendingGuaranteeWouldExceedTheCountLimit() {
        UUID guarantorId = UUID.randomUUID();
        givenPolicy(guarantorId, "1");
        when(guarantorRequestRepository.countActiveGuarantees(guarantorId, SACCO_ID, STATION_ID)).thenReturn(1L);

        Optional<String> reason = service.guarantorFailureReason(SACCO_ID, guarantorId, new BigDecimal("10000.00"));

        assertThat(reason)
            .hasValue("Disabled: approving this loan would exceed the station guarantor guarantee count limit.");
    }

    @Test
    void guarantorLimitDoesNotBlockByGuaranteedLoanAmountAnymore() {
        UUID guarantorId = UUID.randomUUID();
        givenPolicy(guarantorId, "2");
        when(guarantorRequestRepository.countActiveGuarantees(guarantorId, SACCO_ID, STATION_ID)).thenReturn(1L);

        Optional<String> reason = service.guarantorFailureReason(SACCO_ID, guarantorId);

        assertThat(reason).isEmpty();
    }

    @Test
    void externalGuarantorLimitCountsRegistryBackedApprovals() {
        UUID registryId = UUID.randomUUID();
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .guarantorMaxGuaranteedLoanAmount(new BigDecimal("1"))
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.empty());
        when(externalGuarantorRegistryRepository.findBySaccoIdAndExternalStationIdIgnoreCaseAndExternalMemberNoIgnoreCase(
            SACCO_ID, STATION_ID, "EXT-77"))
            .thenReturn(Optional.of(ExternalGuarantorRegistry.builder()
                .id(registryId)
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .externalStationId(STATION_ID)
                .externalMemberNo("EXT-77")
                .build()));
        when(guarantorRequestRepository.countExternalActiveGuarantees(
            registryId, "EXT-77", STATION_ID, SACCO_ID, STATION_ID))
            .thenReturn(1L);

        Optional<String> reason = service.guarantorFailureReasonForExternal(
            SACCO_ID,
            STATION_ID,
            "EXT-77",
            STATION_ID,
            new BigDecimal("500000.00"),
            0,
            0,
            null,
            null
        );

        assertThat(reason)
            .hasValue("Disabled: active guarantee count has reached the station guarantor limit.");
    }

    @Test
    void externalDefaultedRiskBlocksWhenGuarantorDefaultedPolicyIsEnabled() {
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .guarantorMaxDefaultedLoans(1)
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.empty());

        Optional<String> reason = service.guarantorFailureReasonForExternal(
            SACCO_ID,
            STATION_ID,
            "EXT-77",
            STATION_ID,
            new BigDecimal("500000.00"),
            1,
            1,
            null,
            null
        );

        assertThat(reason)
            .hasValue("Disabled: defaulted loan count has reached the station guarantor limit.");
    }

    @Test
    void stationPortfolioAtRiskDaysOverridesSaccoDefault() {
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .portfolioAtRiskDays(30)
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.of(SaccoStationPolicy.builder()
                .id(UUID.randomUUID())
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .portfolioAtRiskDays(7)
                .build()));

        assertThat(service.resolvedPortfolioAtRiskDays(SACCO_ID, STATION_ID)).isEqualTo(7);
    }

    @Test
    void guarantorActiveLoanPolicyBlocksWhenActiveLoansAreNotAllowed() {
        UUID guarantorId = UUID.randomUUID();
        givenDefaultedPolicyMember(guarantorId);
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .guarantorWithActiveLoanAllowed(false)
                .build()));
        when(loanAnalyticsService.activeLoanAmount(guarantorId, SACCO_ID, STATION_ID))
            .thenReturn(new BigDecimal("1.00"));

        Optional<String> reason = service.guarantorFailureReason(SACCO_ID, guarantorId);

        assertThat(reason)
            .hasValue("Disabled: active loans are not allowed for guarantors under the station policy.");
    }

    @Test
    void stationActiveLoanPolicyOverridesSaccoDefaultForGuarantors() {
        UUID guarantorId = UUID.randomUUID();
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .guarantorWithActiveLoanAllowed(true)
                .build()));
        when(memberRepository.findById(guarantorId))
            .thenReturn(Optional.of(Member.builder()
                .id(guarantorId)
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .memberNo("MEM-ACTIVE")
                .fullName("Active Loan Guarantor")
                .memberAccount(true)
                .status(MemberStatus.ACTIVE)
                .position(Position.MEMBER)
                .createdAt(OffsetDateTime.now())
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.of(SaccoStationPolicy.builder()
                .id(UUID.randomUUID())
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .guarantorWithActiveLoanAllowed(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build()));
        when(loanAnalyticsService.activeLoanAmount(guarantorId, SACCO_ID, STATION_ID))
            .thenReturn(new BigDecimal("1.00"));

        Optional<String> reason = service.guarantorFailureReason(SACCO_ID, guarantorId);

        assertThat(reason)
            .hasValue("Disabled: active loans are not allowed for guarantors under the station policy.");
    }

    @Test
    void productMinimumSavingsPolicyBlocksGuarantorForThatProduct() {
        UUID guarantorId = UUID.randomUUID();
        givenGuarantorSavingsPolicy(guarantorId, "50000.00");

        Optional<String> reason = service.guarantorFailureReason(
            SACCO_ID,
            guarantorId,
            null,
            LoanProductSetting.builder()
                .guarantorMinSavingsCheckRequired(true)
                .guarantorMinimumSavings(new BigDecimal("100000.00"))
                .build()
        );

        assertThat(reason)
            .hasValue("Disabled: guarantor savings are 50000.00, below the required minimum of 100000.00 for this loan product.");
    }

    @Test
    void productMinimumSavingsPolicyIsIgnoredWhenProductCheckIsOff() {
        UUID guarantorId = UUID.randomUUID();
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .build()));
        when(memberRepository.findById(guarantorId))
            .thenReturn(Optional.of(Member.builder()
                .id(guarantorId)
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .memberNo("MEM-001")
                .fullName("Test Guarantor")
                .memberAccount(true)
                .status(MemberStatus.ACTIVE)
                .position(Position.MEMBER)
                .createdAt(OffsetDateTime.now())
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.empty());

        Optional<String> reason = service.guarantorFailureReason(
            SACCO_ID,
            guarantorId,
            null,
            LoanProductSetting.builder()
                .guarantorMinSavingsCheckRequired(false)
                .guarantorMinimumSavings(new BigDecimal("100000.00"))
                .build()
        );

        assertThat(reason).isEmpty();
    }

    private void givenPolicy(UUID guarantorId, String maxGuarantees) {
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .guarantorMaxGuaranteedLoanAmount(new BigDecimal(maxGuarantees))
                .build()));
        when(memberRepository.findById(guarantorId))
            .thenReturn(Optional.of(Member.builder()
                .id(guarantorId)
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .memberNo("MEM-001")
                .fullName("Test Guarantor")
                .memberAccount(true)
                .status(MemberStatus.ACTIVE)
                .position(Position.MEMBER)
                .createdAt(OffsetDateTime.now())
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.empty());
    }

    private void givenDefaultedPolicyMember(UUID memberId) {
        when(memberRepository.findById(memberId))
            .thenReturn(Optional.of(Member.builder()
                .id(memberId)
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .memberNo("MEM-DEFAULTED")
                .fullName("Defaulted Member")
                .memberAccount(true)
                .status(MemberStatus.ACTIVE)
                .position(Position.MEMBER)
                .createdAt(OffsetDateTime.now())
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.empty());
    }

    private LoanAnalyticsService.MemberLoanAnalytics memberAnalytics(long defaultedLoans) {
        return new LoanAnalyticsService.MemberLoanAnalytics(
            defaultedLoans,
            0,
            0,
            defaultedLoans,
            0,
            0,
            BigDecimal.ZERO
        );
    }

    private void givenGuarantorSavingsPolicy(UUID guarantorId, String currentSavings) {
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .build()));
        when(memberRepository.findById(guarantorId))
            .thenReturn(Optional.of(Member.builder()
                .id(guarantorId)
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .memberNo("MEM-001")
                .fullName("Test Guarantor")
                .memberAccount(true)
                .status(MemberStatus.ACTIVE)
                .position(Position.MEMBER)
                .createdAt(OffsetDateTime.now())
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.empty());
        when(eligibilityService.resolveSavings(guarantorId))
            .thenReturn(new BigDecimal(currentSavings));
    }

}
