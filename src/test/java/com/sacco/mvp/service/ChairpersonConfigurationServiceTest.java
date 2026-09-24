package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanProductStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.SaccoStationPolicy;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.LoanProductBoardReviewerRepository;
import com.sacco.mvp.repository.LoanProductRequiredAttachmentRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChairpersonConfigurationServiceTest {
    @Mock SaccoSettingsRepository settingsRepository;
    @Mock SaccoStationRepository stationRepository;
    @Mock SaccoStationPolicyRepository policyRepository;
    @Mock LoanProductSettingRepository productRepository;
    @Mock LoanProductBoardReviewerRepository reviewerRepository;
    @Mock LoanProductRequiredAttachmentRepository attachmentRepository;
    @Mock MemberRepository memberRepository;

    ChairpersonConfigurationService service;

    @BeforeEach
    void setUp() {
        service = new ChairpersonConfigurationService(settingsRepository, stationRepository, policyRepository,
            productRepository, reviewerRepository, attachmentRepository, memberRepository);
    }

    @Test
    void overviewResolvesEachStationOverrideWithSaccoFallbackAndUsesBoundedProductPage() {
        SaccoSettings settings = SaccoSettings.builder().saccoId("SACCO-1").externalStationId("ST-1")
            .requiredGuarantors(3).boardSize(7).boardQuorum(4).loanOfficerReviewRequired(true)
            .boardReviewRequired(true).maxLoanSavingsRatio(new BigDecimal("3.0000"))
            .applicationFee(new BigDecimal("15000")).applicantMaxDefaultedLoans(2)
            .guarantorWithActiveLoanAllowed(false).guarantorMaxGuaranteedLoanAmount(new BigDecimal("5"))
            .guarantorMaxDefaultedLoans(1).defaultLanguage("sw").createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now()).build();
        SaccoStation station = SaccoStation.builder().id(UUID.randomUUID()).saccoId("SACCO-1").stationId("ST-1")
            .active(true).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        SaccoStationPolicy policy = SaccoStationPolicy.builder().id(UUID.randomUUID()).saccoId("SACCO-1")
            .stationId("ST-1").applicantMaxDefaultedLoans(0).guarantorWithActiveLoanAllowed(null)
            .guarantorMaxGuaranteedLoanAmount(new BigDecimal("2")).guarantorMaxDefaultedLoans(null)
            .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        when(settingsRepository.findById("SACCO-1")).thenReturn(Optional.of(settings));
        when(stationRepository.findBySaccoIdAndStationId("SACCO-1", "ST-1")).thenReturn(Optional.of(station));
        when(policyRepository.findBySaccoIdAndStationId("SACCO-1", "ST-1")).thenReturn(Optional.of(policy));
        when(productRepository.findConfigurationPage(eq("SACCO-1"),
            eq(Set.of(LoanProductStatus.DRAFT, LoanProductStatus.RETIRED)), eq("edu"), any(Pageable.class)))
            .thenReturn(Page.empty());

        var configuration = service.overview(principal(), " EDU ", 0);

        assertThat(configuration.policies().applicantMaxDefaults().sourceCode()).isEqualTo("stationOverride");
        assertThat(configuration.policies().applicantMaxDefaults().value()).isZero();
        assertThat(configuration.policies().guarantorActiveLoanAllowed().sourceCode()).isEqualTo("saccoDefault");
        assertThat(configuration.policies().guarantorActiveLoanAllowed().value()).isFalse();
        assertThat(configuration.policies().guarantorMaximumActiveGuarantees().value()).isEqualByComparingTo("2");
        assertThat(configuration.search()).isEqualTo("edu");
    }

    @Test
    void overviewTreatsMissingActiveLoanPolicyAsAllowed() {
        SaccoSettings settings = SaccoSettings.builder().saccoId("SACCO-1").externalStationId("ST-1")
            .requiredGuarantors(1).boardSize(3).boardQuorum(2).loanOfficerReviewRequired(false)
            .boardReviewRequired(false).maxLoanSavingsRatio(BigDecimal.ONE)
            .defaultLanguage("en").createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        SaccoStation station = SaccoStation.builder().id(UUID.randomUUID()).saccoId("SACCO-1").stationId("ST-1")
            .active(true).createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        when(settingsRepository.findById("SACCO-1")).thenReturn(Optional.of(settings));
        when(stationRepository.findBySaccoIdAndStationId("SACCO-1", "ST-1")).thenReturn(Optional.of(station));
        when(policyRepository.findBySaccoIdAndStationId("SACCO-1", "ST-1")).thenReturn(Optional.empty());
        when(productRepository.findConfigurationPage(eq("SACCO-1"),
            eq(Set.of(LoanProductStatus.DRAFT, LoanProductStatus.RETIRED)), eq(""), any(Pageable.class)))
            .thenReturn(Page.empty());

        var configuration = service.overview(principal(), "", 0);

        assertThat(configuration.policies().guarantorActiveLoanAllowed().value()).isTrue();
        assertThat(configuration.policies().guarantorActiveLoanAllowed().sourceCode()).isEqualTo("saccoDefault");
    }

    @Test
    void productIdentifiersAreAlwaysQueriedInsideCurrentSaccoScope() {
        UUID productId = UUID.randomUUID();
        when(settingsRepository.findById("SACCO-1")).thenReturn(Optional.of(SaccoSettings.builder()
            .saccoId("SACCO-1").requiredGuarantors(1).boardSize(1).boardQuorum(1)
            .maxLoanSavingsRatio(BigDecimal.ONE).defaultLanguage("en").build()));
        when(productRepository.findByIdAndSaccoId(productId, "SACCO-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.product(principal(), productId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    private AppUserPrincipal principal() {
        Member member = Member.builder().id(UUID.randomUUID()).saccoId("SACCO-1").stationId("ST-1")
            .memberNo("STAFF-1").staffNo("STAFF-1").fullName("Configuration Viewer").memberAccount(false)
            .staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).position(Position.ACCOUNTANT)
            .staffRoles(new LinkedHashSet<>(List.of(Position.ACCOUNTANT))).passwordHash("x")
            .createdAt(OffsetDateTime.now()).build();
        return new AppUserPrincipal(member, Set.of(UserClaim.SACCO_CONFIGURATIONS_VIEW), true);
    }
}
