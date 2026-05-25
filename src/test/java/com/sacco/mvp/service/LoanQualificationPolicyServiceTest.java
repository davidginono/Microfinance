package com.sacco.mvp.service;

import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanQualificationPolicyServiceTest {
    private static final String SACCO_ID = "SACCO-01";
    private static final String STATION_ID = "AR704";

    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private SaccoStationPolicyRepository saccoStationPolicyRepository;
    @Mock private LoanAnalyticsService loanAnalyticsService;
    @Mock private EligibilityService eligibilityService;

    private LoanQualificationPolicyService service;

    @BeforeEach
    void setUp() {
        service = new LoanQualificationPolicyService(
            saccoSettingsRepository,
            loanApplicationRepository,
            guarantorRequestRepository,
            memberRepository,
            saccoStationPolicyRepository,
            loanAnalyticsService,
            eligibilityService
        );
    }

    @Test
    void guarantorLimitBlocksWhenActiveGuaranteeCountHasReachedTheLimit() {
        UUID guarantorId = UUID.randomUUID();
        UUID firstLoanId = UUID.randomUUID();
        UUID secondLoanId = UUID.randomUUID();
        givenPolicy(guarantorId, "2");
        givenApprovedGuarantees(guarantorId, firstLoanId, secondLoanId);
        givenLoan(firstLoanId, LoanStatus.FINAL_APPROVED, "9000000.00");
        givenLoan(secondLoanId, LoanStatus.READY_FOR_DISBURSEMENT, "500000.00");

        Optional<String> reason = service.guarantorFailureReason(SACCO_ID, guarantorId);

        assertThat(reason)
            .hasValue("Disabled: active guarantee count has reached the station guarantor limit.");
    }

    @Test
    void guarantorLimitBlocksApprovalWhenPendingGuaranteeWouldExceedTheCountLimit() {
        UUID guarantorId = UUID.randomUUID();
        UUID existingLoanId = UUID.randomUUID();
        givenPolicy(guarantorId, "1");
        givenApprovedGuarantees(guarantorId, existingLoanId);
        givenLoan(existingLoanId, LoanStatus.FINAL_APPROVED, "250000.00");

        Optional<String> reason = service.guarantorFailureReason(SACCO_ID, guarantorId, new BigDecimal("10000.00"));

        assertThat(reason)
            .hasValue("Disabled: approving this loan would exceed the station guarantor guarantee count limit.");
    }

    @Test
    void guarantorLimitDoesNotBlockByGuaranteedLoanAmountAnymore() {
        UUID guarantorId = UUID.randomUUID();
        UUID existingLoanId = UUID.randomUUID();
        givenPolicy(guarantorId, "2");
        givenApprovedGuarantees(guarantorId, existingLoanId);
        givenLoan(existingLoanId, LoanStatus.FINAL_APPROVED, "9000000.00");

        Optional<String> reason = service.guarantorFailureReason(SACCO_ID, guarantorId);

        assertThat(reason).isEmpty();
    }

    @Test
    void guarantorCommitmentCapacityIgnoresPaidGuaranteedLoans() {
        UUID guarantorId = UUID.randomUUID();
        UUID activeLoanId = UUID.randomUUID();
        UUID paidLoanId = UUID.randomUUID();
        givenGuarantorSavingsPolicy(guarantorId, "500000.00");
        givenApprovedGuaranteesWithCommitments(guarantorId,
            guarantee(activeLoanId, "300000.00"),
            guarantee(paidLoanId, "250000.00"));
        givenLoan(activeLoanId, LoanStatus.FINAL_APPROVED, "300000.00");
        givenLoan(paidLoanId, LoanStatus.PAID, "250000.00");

        service.assertGuarantorCanCommit(SACCO_ID, guarantorId, new BigDecimal("200000.00"));
    }

    @Test
    void guarantorCommitmentCapacityKeepsDefaultedGuaranteedLoansCommitted() {
        UUID guarantorId = UUID.randomUUID();
        UUID defaultedLoanId = UUID.randomUUID();
        givenGuarantorSavingsPolicy(guarantorId, "500000.00");
        givenApprovedGuaranteesWithCommitments(guarantorId, guarantee(defaultedLoanId, "450000.00"));
        givenLoan(defaultedLoanId, LoanStatus.DEFAULTED, "450000.00");

        assertThatThrownBy(() -> service.assertGuarantorCanCommit(SACCO_ID, guarantorId, new BigDecimal("100000.00")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("This guarantor does not have enough available savings after existing active guarantee commitments are deducted.");
    }

    @Test
    void applicantForfeitedWaitingPeriodDoesNotBlockWhenNoWaitingPeriodIsConfigured() {
        UUID applicantId = UUID.randomUUID();
        givenApplicantForfeitedPolicy(applicantId, null, null, null, 2);

        Optional<String> reason = service.applicantFailureReason(SACCO_ID, applicantId);

        assertThat(reason).isEmpty();
    }

    @Test
    void applicantForfeitedWaitingPeriodAllowsApplicantWhenLastForfeitureHasExpired() {
        UUID applicantId = UUID.randomUUID();
        givenApplicantForfeitedPolicy(applicantId, null, null, 1, 2);
        givenForfeitedApplications(applicantId, OffsetDateTime.now().minusDays(2));

        Optional<String> reason = service.applicantFailureReason(SACCO_ID, applicantId);

        assertThat(reason).isEmpty();
    }

    @Test
    void applicantForfeitedWaitingPeriodBlocksApplicantInsideWaitingPeriodWithReleaseDate() {
        UUID applicantId = UUID.randomUUID();
        OffsetDateTime forfeitedAt = OffsetDateTime.now().minusDays(2);
        givenApplicantForfeitedPolicy(applicantId, null, null, 30, 2);
        givenForfeitedApplications(applicantId, forfeitedAt);

        Optional<String> reason = service.applicantFailureReason(SACCO_ID, applicantId);

        assertThat(reason)
            .hasValueSatisfying(message -> assertThat(message)
                .contains("last forfeited loan application is still within the station waiting period")
                .contains("You can apply again in")
                .contains(forfeitedAt.toLocalDate().plusDays(30).toString()));
    }

    @Test
    void applicantForfeitedWaitingPeriodUsesOnlyTheLatestForfeiture() {
        UUID applicantId = UUID.randomUUID();
        OffsetDateTime oldForfeiture = OffsetDateTime.now().minusDays(40);
        OffsetDateTime latestForfeiture = OffsetDateTime.now().minusDays(3);
        givenApplicantForfeitedPolicy(applicantId, null, null, 10, 2);
        givenForfeitedApplications(applicantId, oldForfeiture, latestForfeiture);

        Optional<String> reason = service.applicantFailureReason(SACCO_ID, applicantId);

        assertThat(reason)
            .hasValueSatisfying(message -> assertThat(message)
                .contains(latestForfeiture.toLocalDate().plusDays(10).toString())
                .doesNotContain(oldForfeiture.toLocalDate().plusDays(10).toString()));
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

    private void givenApplicantForfeitedPolicy(UUID applicantId,
                                               Integer maxForfeitedLoans,
                                               Integer forfeitedLookbackDays,
                                               Integer forfeitedWaitDays,
                                               long allTimeForfeitedLoans) {
        when(saccoSettingsRepository.findById(SACCO_ID))
            .thenReturn(Optional.of(SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .applicantMaxForfeitedLoans(maxForfeitedLoans)
                .applicantForfeitedLookbackDays(forfeitedLookbackDays)
                .applicantForfeitedWaitDays(forfeitedWaitDays)
                .build()));
        when(memberRepository.findById(applicantId))
            .thenReturn(Optional.of(Member.builder()
                .id(applicantId)
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .memberNo("MEM-002")
                .fullName("Test Applicant")
                .memberAccount(true)
                .status(MemberStatus.ACTIVE)
                .position(Position.MEMBER)
                .createdAt(OffsetDateTime.now())
                .build()));
        when(saccoStationPolicyRepository.findBySaccoIdAndStationId(SACCO_ID, STATION_ID))
            .thenReturn(Optional.empty());
        when(loanAnalyticsService.summarizeAllTime(applicantId, STATION_ID))
            .thenReturn(new LoanAnalyticsService.MemberLoanAnalytics(
                0,
                0,
                0,
                allTimeForfeitedLoans,
                allTimeForfeitedLoans,
                0,
                0,
                BigDecimal.ZERO
            ));
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

    private void givenForfeitedApplications(UUID applicantId, OffsetDateTime... dates) {
        List<LoanApplication> applications = java.util.Arrays.stream(dates)
            .map(date -> LoanApplication.builder()
                .id(UUID.randomUUID())
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .applicantMemberId(applicantId)
                .loanType(LoanType.LOAN_ADVANCE)
                .amount(new BigDecimal("100000.00"))
                .tenorMonths(12)
                .status(LoanStatus.FORFEITED)
                .formData("{}")
                .requiredGuarantors(1)
                .policySnapshot("{}")
                .createdAt(date)
                .updatedAt(date)
                .version(0)
                .build())
            .toList();
        when(loanApplicationRepository.findByApplicantMemberIdAndStatusOrderByCreatedAtDesc(applicantId, LoanStatus.FORFEITED))
            .thenReturn(applications);
    }

    private void givenApprovedGuarantees(UUID guarantorId, UUID... loanIds) {
        List<GuarantorRequest> requests = java.util.Arrays.stream(loanIds)
            .map(loanId -> GuarantorRequest.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(loanId)
                .guarantorMemberId(guarantorId)
                .status(GuarantorRequestStatus.APPROVED)
                .createdAt(OffsetDateTime.now())
                .version(0)
                .build())
            .toList();
        when(guarantorRequestRepository.findByGuarantorMemberIdAndStatusOrderByCreatedAtDesc(
            guarantorId,
            GuarantorRequestStatus.APPROVED
        )).thenReturn(requests);
    }

    private void givenApprovedGuaranteesWithCommitments(UUID guarantorId, GuaranteeFixture... guarantees) {
        List<GuarantorRequest> requests = java.util.Arrays.stream(guarantees)
            .map(guarantee -> GuarantorRequest.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(guarantee.loanId())
                .guarantorMemberId(guarantorId)
                .status(GuarantorRequestStatus.APPROVED)
                .committedAmount(new BigDecimal(guarantee.committedAmount()))
                .createdAt(OffsetDateTime.now())
                .version(0)
                .build())
            .toList();
        when(guarantorRequestRepository.findByGuarantorMemberIdAndStatusOrderByCreatedAtDesc(
            guarantorId,
            GuarantorRequestStatus.APPROVED
        )).thenReturn(requests);
    }

    private GuaranteeFixture guarantee(UUID loanId, String committedAmount) {
        return new GuaranteeFixture(loanId, committedAmount);
    }

    private void givenLoan(UUID loanId, LoanStatus status, String amount) {
        when(loanApplicationRepository.findById(loanId))
            .thenReturn(Optional.of(LoanApplication.builder()
                .id(loanId)
                .saccoId(SACCO_ID)
                .stationId(STATION_ID)
                .applicantMemberId(UUID.randomUUID())
                .loanType(LoanType.LOAN_ADVANCE)
                .amount(new BigDecimal(amount))
                .tenorMonths(12)
                .status(status)
                .formData("{}")
                .requiredGuarantors(1)
                .policySnapshot("{}")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .version(0)
                .build()));
    }

    private record GuaranteeFixture(UUID loanId, String committedAmount) {
    }
}
