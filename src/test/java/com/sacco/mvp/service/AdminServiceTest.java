package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanProductStatus;
import com.sacco.mvp.domain.LoanProductVersion;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.AdminIncidentRepository;
import com.sacco.mvp.repository.AuditLogRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductsVersionRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.LoanProductVersionRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.NotificationRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SavingsAccountRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private SavingsAccountRepository savingsAccountRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanProductSettingRepository loanProductSettingRepository;
    @Mock private LoanProductVersionRepository loanProductVersionRepository;
    @Mock private LoanProductsVersionRepository loanProductsVersionRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AdminIncidentRepository adminIncidentRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;

    private AdminService adminService;
    private AtomicInteger issuedInvitationCount;
    private AtomicReference<Member> lastInvitedMember;
    private AtomicReference<UUID> lastInvitedBy;

    @BeforeEach
    void setUp() {
        issuedInvitationCount = new AtomicInteger();
        lastInvitedMember = new AtomicReference<>();
        lastInvitedBy = new AtomicReference<>();
        ObjectMapper objectMapper = new ObjectMapper();
        RoleDirectoryService roleDirectoryService = new RoleDirectoryService(memberRepository);
        AuditService auditService = new AuditService(auditLogRepository, objectMapper);
        AdminAlertService adminAlertService = new AdminAlertService(
            roleDirectoryService,
            notificationRepository,
            adminIncidentRepository,
            objectMapper
        );
        NotificationViewService notificationViewService = new NotificationViewService(objectMapper, memberRepository);
        UserClaimService userClaimService = new UserClaimService(userSettingsRepository, objectMapper);
        SaccoConfigurationService saccoConfigurationService = new SaccoConfigurationService(loanProductSettingRepository);
        SaccoRegistryService saccoRegistryService = new SaccoRegistryService(
            null,
            null,
            saccoSettingsRepository,
            saccoConfigurationService,
            null
        );
        MinorAdminInvitationService minorAdminInvitationService = new MinorAdminInvitationService(
            null,
            memberRepository,
            null,
            null
        ) {
            @Override
            public com.sacco.mvp.domain.MinorAdminInvitation issueInvitation(Member member, UUID invitedBy) {
                issuedInvitationCount.incrementAndGet();
                lastInvitedMember.set(member);
                lastInvitedBy.set(invitedBy);
                return null;
            }
        };

        adminService = new AdminService(
            memberRepository,
            savingsAccountRepository,
            userSettingsRepository,
            loanApplicationRepository,
            loanProductSettingRepository,
            loanProductVersionRepository,
            loanProductsVersionRepository,
            saccoSettingsRepository,
            notificationRepository,
            outboxEventRepository,
            auditLogRepository,
            adminIncidentRepository,
            guarantorRequestRepository,
            managerReviewRepository,
            auditService,
            adminAlertService,
            notificationViewService,
            userClaimService,
            roleDirectoryService,
            saccoConfigurationService,
            saccoRegistryService,
            minorAdminInvitationService,
            objectMapper
        );
    }

    @Test
    void createUserRejectsSecondMinorAdminForSameSacco() {
        when(memberRepository.existsBySaccoIdAndPosition("SACCO-01", Position.MINOR_ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> adminService.createUser(
            "SACCO-01",
            null,
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            "MINOR001",
            "Minor Admin",
            "minor@example.com",
            null,
            List.of(Position.MINOR_ADMIN)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Each SACCO can only have one Minor Admin account. Update the existing one instead.");

        verify(memberRepository).existsBySaccoIdAndPosition("SACCO-01", Position.MINOR_ADMIN);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void updateUserRejectsAssigningMinorAdminWhenAnotherAccountAlreadyExists() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .memberNo("STAFF001")
            .fullName("Staff User")
            .email("staff@example.com")
            .position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.existsBySaccoIdAndPositionAndIdNot("SACCO-01", Position.MINOR_ADMIN, accountId)).thenReturn(true);

        assertThatThrownBy(() -> adminService.updateUser(
            "SACCO-01",
            null,
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            accountId,
            List.of(Position.MINOR_ADMIN),
            MemberStatus.ACTIVE
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Each SACCO can only have one Minor Admin account. Update the existing one instead.");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void updateUserAllowsExistingMinorAdminToKeepSameSlot() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .memberNo("MINOR001")
            .fullName("Minor Admin")
            .email("minor@example.com")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .rank(1)
            .createdAt(OffsetDateTime.now())
            .build();
        UserSettings settings = UserSettings.builder()
            .memberId(accountId)
            .language("en")
            .notificationPrefs("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.existsBySaccoIdAndPositionAndIdNot("SACCO-01", Position.MINOR_ADMIN, accountId)).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSettingsRepository.findById(accountId)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.updateUser(
            "SACCO-01",
            null,
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            accountId,
            List.of(Position.MINOR_ADMIN),
            MemberStatus.ACTIVE
        );

        verify(memberRepository).save(member);
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    void updateUserAllowsStationScopedLegacyMemberAndRescopesThemToCurrentSacco() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-ARUSHA-001")
            .stationId("AR704")
            .memberNo("MEM007")
            .fullName("Agnes Member")
            .email("agnes@example.com")
            .position(Position.MEMBER)
            .staffRoles(new LinkedHashSet<>())
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .rank(7)
            .createdAt(OffsetDateTime.now())
            .build();
        UserSettings settings = UserSettings.builder()
            .memberId(accountId)
            .language("en")
            .notificationPrefs("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.findTopBySaccoIdAndPositionOrderByRankDesc("TAHA SACCOS", Position.MANAGER)).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSettingsRepository.findById(accountId)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(savingsAccountRepository.findByMemberId(accountId)).thenReturn(Optional.empty());
        when(savingsAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.updateUser(
            "TAHA SACCOS",
            "AR704",
            UUID.randomUUID(),
            Set.of(Position.MINOR_ADMIN),
            accountId,
            List.of(Position.MANAGER),
            MemberStatus.ACTIVE
        );

        assertThat(member.getSaccoId()).isEqualTo("TAHA SACCOS");
        assertThat(member.getStationId()).isEqualTo("AR704");
        assertThat(member.getPosition()).isEqualTo(Position.MANAGER);
        assertThat(member.getStaffRoles()).containsExactly(Position.MANAGER);
        verify(memberRepository).save(member);
    }

    @Test
    void reinviteMinorAdminRestoresPlaceholderPassword() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .memberNo("MINOR001")
            .fullName("Minor Admin")
            .email("minor@example.com")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.INACTIVE)
            .passwordHash("$2a$10$existingHashValue")
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.reinviteMinorAdmin(UUID.randomUUID(), accountId);

        org.assertj.core.api.Assertions.assertThat(member.getStatus()).isEqualTo(MemberStatus.INVITED);
        org.assertj.core.api.Assertions.assertThat(member.getPasswordHash()).isEqualTo("OTP_ONLY_LOGIN");
        verify(memberRepository).save(member);
    }

    @Test
    void createUserForNonMinorAdminStaffAlsoIssuesInvitation() {
        UUID adminId = UUID.randomUUID();
        when(memberRepository.findByMemberNo("MGR001")).thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("manager@example.com")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.createUser(
            "SACCO-01",
            null,
            adminId,
            Set.of(Position.ADMIN),
            "MGR001",
            "Mary Manager",
            "manager@example.com",
            null,
            List.of(Position.MANAGER)
        );

        org.assertj.core.api.Assertions.assertThat(issuedInvitationCount.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(lastInvitedBy.get()).isEqualTo(adminId);
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getStatus()).isEqualTo(MemberStatus.INVITED);
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getPasswordHash()).isEqualTo("OTP_ONLY_LOGIN");
    }

    @Test
    void registerMinorAdminIssuesPasswordSetupInvitation() {
        UUID adminId = UUID.randomUUID();
        when(memberRepository.existsBySaccoIdAndPosition("SACCO-01", Position.MINOR_ADMIN)).thenReturn(false);
        when(memberRepository.findByMemberNo("MINOR001")).thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("minor@example.com")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.createUser(
            "SACCO-01",
            null,
            adminId,
            Set.of(Position.ADMIN),
            "MINOR001",
            "Minor Admin",
            "minor@example.com",
            null,
            List.of(Position.MINOR_ADMIN)
        );

        org.assertj.core.api.Assertions.assertThat(issuedInvitationCount.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(lastInvitedBy.get()).isEqualTo(adminId);
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getStatus()).isEqualTo(MemberStatus.INVITED);
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getPasswordHash()).isEqualTo("OTP_ONLY_LOGIN");
    }

    @Test
    void updateLoanProductPreservesPreviousVersionSnapshot() {
        UUID productId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(productId)
            .saccoId("SACCO-01")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .productCode("DEV_LOAN")
            .productName("Development Loan")
            .displayOrder(4)
            .minimumAmount(new BigDecimal("100000.00"))
            .maximumAmount(new BigDecimal("1000000.00"))
            .guarantorsRequired(2)
            .maxLoanSavingsRatio(new BigDecimal("0.3333"))
            .insuranceRate(new BigDecimal("0.0150"))
            .interestRate(new BigDecimal("0.1000"))
            .interestMethod(InterestMethod.FLAT_RATE)
            .minRepaymentMonths(3)
            .maxRepaymentMonths(12)
            .allowApplicationWithActiveLoan(false)
            .freshFinancialDataRequired(false)
            .managerReviewRequired(true)
            .committeeReviewRequired(false)
            .committeeMinimumVotes(0)
            .committeeApprovalThreshold(0)
            .productStatus(LoanProductStatus.ACTIVE)
            .active(true)
            .createdAt(OffsetDateTime.now().minusDays(2))
            .updatedAt(OffsetDateTime.now().minusDays(1))
            .build();
        SaccoSettings settings = SaccoSettings.builder()
            .saccoId("SACCO-01")
            .applicationFee(new BigDecimal("15000.00"))
            .requiredGuarantors(3)
            .boardSize(3)
            .boardQuorum(2)
            .defaultLanguage("en")
            .createdAt(OffsetDateTime.now().minusDays(2))
            .updatedAt(OffsetDateTime.now().minusDays(1))
            .build();

        when(loanProductSettingRepository.findById(productId)).thenReturn(Optional.of(product));
        when(loanProductSettingRepository.existsBySaccoId("SACCO-01")).thenReturn(true);
        when(loanProductSettingRepository.findBySaccoIdOrderByLoanTypeAsc("SACCO-01")).thenReturn(List.of(product));
        when(loanProductVersionRepository.findTopByLoanProductSettingIdOrderByVersionNumberDesc(productId)).thenReturn(Optional.empty());
        when(loanProductVersionRepository.save(any(LoanProductVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanProductsVersionRepository.findTopBySaccoIdOrderByVersionNumberDesc("SACCO-01")).thenReturn(Optional.empty());
        when(loanProductsVersionRepository.save(any(com.sacco.mvp.domain.LoanProductsVersion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(loanProductSettingRepository.save(any(LoanProductSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanProductSettingRepository.existsBySaccoIdAndProductCodeIgnoreCaseAndIdNot("SACCO-01", "DEV_GROWTH", productId)).thenReturn(false);
        when(saccoSettingsRepository.findById("SACCO-01")).thenReturn(Optional.of(settings));

        adminService.updateLoanProduct(
            "SACCO-01",
            adminId,
            productId,
            "DEV_GROWTH",
            "Growth Loan",
            "Updated",
            6,
            new BigDecimal("250000.00"),
            new BigDecimal("2500000.00"),
            1,
            new BigDecimal("0.4000"),
            new BigDecimal("0.0200"),
            new BigDecimal("0.1200"),
            InterestMethod.REDUCING_BALANCE,
            6,
            24,
            true,
            true,
            true,
            false,
            ApprovalWorkflowStage.MANAGER,
            false,
            3,
            null,
            null,
            true,
            4,
            LoanProductStatus.ACTIVE
        );

        verify(loanProductVersionRepository).save(any(LoanProductVersion.class));
        verify(loanProductsVersionRepository).save(any(com.sacco.mvp.domain.LoanProductsVersion.class));
        verify(loanProductSettingRepository).save(product);
    }

    @Test
    void updateLoanApplicationFeePersistsSaccoWideSetting() {
        UUID adminId = UUID.randomUUID();
        SaccoSettings settings = SaccoSettings.builder()
            .saccoId("SACCO-01")
            .applicationFee(new BigDecimal("15000.00"))
            .requiredGuarantors(3)
            .boardSize(3)
            .boardQuorum(2)
            .defaultLanguage("en")
            .createdAt(OffsetDateTime.now().minusDays(2))
            .updatedAt(OffsetDateTime.now().minusDays(1))
            .build();

        when(saccoSettingsRepository.findById("SACCO-01")).thenReturn(Optional.of(settings));
        when(loanProductsVersionRepository.findTopBySaccoIdOrderByVersionNumberDesc("SACCO-01")).thenReturn(Optional.empty());
        when(loanProductsVersionRepository.save(any(com.sacco.mvp.domain.LoanProductsVersion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(saccoSettingsRepository.save(any(SaccoSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.updateLoanApplicationFee("SACCO-01", adminId, new BigDecimal("22000.00"));

        verify(loanProductsVersionRepository).save(any(com.sacco.mvp.domain.LoanProductsVersion.class));
        verify(saccoSettingsRepository).save(settings);
        assertThat(settings.getApplicationFee()).isEqualByComparingTo("22000.00");
    }
}
