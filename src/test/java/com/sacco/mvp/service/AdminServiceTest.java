package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.AdminIncident;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.IncidentSeverity;
import com.sacco.mvp.domain.IncidentStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Notification;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanProductStatus;
import com.sacco.mvp.domain.LoanProductVersion;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.AdminIncidentRepository;
import com.sacco.mvp.repository.AuditLogRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductBoardReviewerRepository;
import com.sacco.mvp.repository.LoanProductsVersionRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.LoanProductVersionRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.NotificationRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private SavingsAccountRepository savingsAccountRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private LoanProductSettingRepository loanProductSettingRepository;
    @Mock private LoanProductBoardReviewerRepository loanProductBoardReviewerRepository;
    @Mock private LoanProductVersionRepository loanProductVersionRepository;
    @Mock private LoanProductsVersionRepository loanProductsVersionRepository;
    @Mock private RegisteredSaccoRepository registeredSaccoRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private SaccoStationRepository saccoStationRepository;
    @Mock private SaccoStationPolicyRepository saccoStationPolicyRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AdminIncidentRepository adminIncidentRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;

    private AdminService adminService;
    private AtomicInteger issuedInvitationCount;
    private AtomicInteger revokedInvitationCount;
    private AtomicReference<Member> lastInvitedMember;
    private AtomicReference<UUID> lastInvitedBy;
    private AtomicReference<UUID> lastRevokedMemberId;
    private AtomicReference<UUID> lastRevokedBy;

    @BeforeEach
    void setUp() {
        issuedInvitationCount = new AtomicInteger();
        revokedInvitationCount = new AtomicInteger();
        lastInvitedMember = new AtomicReference<>();
        lastInvitedBy = new AtomicReference<>();
        lastRevokedMemberId = new AtomicReference<>();
        lastRevokedBy = new AtomicReference<>();
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
            registeredSaccoRepository,
            saccoStationRepository,
            saccoSettingsRepository,
            null,
            org.mockito.Mockito.mock(SmsUnitTransactionService.class)
        );
        MinorAdminInvitationService minorAdminInvitationService = new MinorAdminInvitationService(
            null,
            memberRepository,
            null,
            null,
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

            @Override
            public void revokeInvitation(UUID memberId, UUID revokedBy) {
                revokedInvitationCount.incrementAndGet();
                lastRevokedMemberId.set(memberId);
                lastRevokedBy.set(revokedBy);
            }
        };

        adminService = new AdminService(
            memberRepository,
            savingsAccountRepository,
            userSettingsRepository,
            loanApplicationRepository,
            loanProductSettingRepository,
            loanProductBoardReviewerRepository,
            loanProductVersionRepository,
            loanProductsVersionRepository,
            saccoSettingsRepository,
            saccoStationRepository,
            saccoStationPolicyRepository,
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

    private void stubActiveRoleDirectory(String saccoId, List<Member> activeMembers) {
        lenient().when(memberRepository.findBySaccoIdAndStatusOrderByFullNameAsc(saccoId, MemberStatus.ACTIVE))
            .thenReturn(activeMembers);
        lenient().when(memberRepository.findActiveRoleMembers(eq(saccoId), any())).thenAnswer(invocation -> {
            Position position = invocation.getArgument(1);
            return activeMembers.stream()
                .filter(member -> member.getStaffRolesResolved().contains(position))
                .toList();
        });
    }

    @Test
    void platformSupportIncidentsExcludeSystemAlerts() {
        AdminIncident supportIncident = AdminIncident.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .category("SUPPORT_MESSAGE")
            .source("Workspace Admin Support")
            .subject("Station login issue")
            .message("Users cannot sign in at the branch.")
            .severity(IncidentSeverity.MEDIUM)
            .status(IncidentStatus.OPEN)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        AdminIncident systemAlert = AdminIncident.builder()
            .id(UUID.randomUUID())
            .category("SYSTEM_ALERT")
            .source("Web Request")
            .subject("Unhandled application error")
            .message("Stack trace omitted")
            .severity(IncidentSeverity.HIGH)
            .status(IncidentStatus.OPEN)
            .createdAt(OffsetDateTime.now().minusMinutes(1))
            .updatedAt(OffsetDateTime.now().minusMinutes(1))
            .build();
        when(adminIncidentRepository.findRecentForReview(any(), any(), any(), any()))
            .thenReturn(List.of(systemAlert, supportIncident));

        List<AdminIncident> incidents = adminService.platformSupportIncidents(null, null, null, null);

        assertThat(incidents).containsExactly(supportIncident);
    }

    @Test
    void submitSupportNotifiesOnlyMinorAdminsInMembersStation() {
        UUID memberId = UUID.randomUUID();
        UUID sameStationAdminId = UUID.randomUUID();
        UUID otherStationAdminId = UUID.randomUUID();
        Member member = Member.builder()
            .id(memberId)
            .saccoId("SACCO-01")
            .stationId("AR704")
            .memberNo("MEM001")
            .fullName("Member One")
            .status(MemberStatus.ACTIVE)
            .build();
        Member sameStationAdmin = Member.builder()
            .id(sameStationAdminId)
            .saccoId("SACCO-01")
            .stationId("AR704")
            .memberNo("MIN001")
            .fullName("Same Station Admin")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .status(MemberStatus.ACTIVE)
            .build();
        Member otherStationAdmin = Member.builder()
            .id(otherStationAdminId)
            .saccoId("SACCO-01")
            .stationId("BR001")
            .memberNo("MIN002")
            .fullName("Other Station Admin")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .status(MemberStatus.ACTIVE)
            .build();
        AtomicReference<AdminIncident> savedIncident = new AtomicReference<>();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.findActiveMembersWithAnyRoleInStation(eq("SACCO-01"), eq("AR704"), any()))
            .thenReturn(List.of(sameStationAdmin));
        when(adminIncidentRepository.save(any(AdminIncident.class))).thenAnswer(invocation -> {
            AdminIncident incident = invocation.getArgument(0);
            savedIncident.set(incident);
            return incident;
        });
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.submitSupport("SACCO-01", memberId, "Help", "I need support");

        verify(notificationRepository).save(argThat(notification ->
            sameStationAdminId.equals(notification.getRecipientMemberId())
                && "SUPPORT_MESSAGE".equals(notification.getType())
        ));
        verify(notificationRepository, never()).save(argThat(notification ->
            otherStationAdminId.equals(notification.getRecipientMemberId())
        ));
    }

    @Test
    void memberSupportArchiveReportsWhenStationAdminReadsIncidentNotification() {
        UUID memberId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        AdminIncident incident = AdminIncident.builder()
            .id(incidentId)
            .saccoId("SACCO-01")
            .reportedByMemberId(memberId)
            .category("SUPPORT_MESSAGE")
            .source("Member Support")
            .subject("Loan page issue")
            .message("I cannot open my application.")
            .severity(IncidentSeverity.MEDIUM)
            .status(IncidentStatus.OPEN)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        Notification notification = Notification.builder()
            .id(UUID.randomUUID())
            .recipientMemberId(UUID.randomUUID())
            .type("SUPPORT_MESSAGE")
            .payload("{\"details\":{\"incidentId\":\"" + incidentId + "\"}}")
            .readAt(OffsetDateTime.now())
            .createdAt(OffsetDateTime.now())
            .build();
        when(adminIncidentRepository.findByReportedByMemberIdOrderByCreatedAtDesc(memberId))
            .thenReturn(List.of(incident));
        when(notificationRepository.findReadSupportIncidentIds(Set.of(incidentId)))
            .thenReturn(List.of(incidentId));

        List<AdminService.SupportArchiveView> archive = adminService.memberSupportArchive(memberId);

        assertThat(archive).hasSize(1);
        assertThat(archive.get(0).isReadBySuperAdmin()).isTrue();
    }

    @Test
    void suspendAndRestoreStationAccessUpdatesStationAndAuditsChange() {
        UUID adminId = UUID.randomUUID();
        SaccoStation station = SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-01", "AR704")).thenReturn(Optional.of(station));
        when(saccoStationRepository.save(any(SaccoStation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.suspendStationAccess("SACCO-01", "AR704", adminId, "Payment overdue", java.time.LocalDate.of(2026, 5, 31));

        assertThat(station.isAccessSuspended()).isTrue();
        assertThat(station.getAccessRestrictionReason()).isEqualTo("Payment overdue");
        assertThat(station.getPaymentDueDate()).isEqualTo(java.time.LocalDate.of(2026, 5, 31));
        assertThat(station.getAccessSuspendedByMemberId()).isEqualTo(adminId);
        verify(auditLogRepository).save(argThat(log -> "PLATFORM_SUSPEND_STATION_ACCESS".equals(log.getAction())));

        adminService.restoreStationAccess("SACCO-01", "AR704", adminId);

        assertThat(station.isAccessSuspended()).isFalse();
        assertThat(station.getPaymentDueDate()).isNull();
        assertThat(station.getAccessRestrictionReason()).isNull();
        assertThat(station.getAccessSuspendedByMemberId()).isNull();
        verify(auditLogRepository).save(argThat(log -> "PLATFORM_RESTORE_STATION_ACCESS".equals(log.getAction())));
    }

    @Test
    void createUserRejectsSecondMinorAdminForSameSaccoStation() {
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPosition("SACCO-01", "ST-1", Position.MINOR_ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> adminService.createUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            "MINOR001",
            "Minor Admin",
            "minor@example.com",
            null,
            List.of(Position.MINOR_ADMIN)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Each SACCO station can only have one Minor Admin account. Update the existing one instead.");

        verify(memberRepository).existsBySaccoIdAndStationIdIgnoreCaseAndPosition("SACCO-01", "ST-1", Position.MINOR_ADMIN);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void createUserRejectsEmailAlreadyUsedByAnyAccount() {
        when(memberRepository.findByMemberNo("MGR001")).thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> adminService.createUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            "MGR001",
            "Mary Manager",
            " Existing@Example.com ",
            null,
            List.of(Position.MANAGER)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("That email address is already in use.");

        verify(memberRepository).existsByEmailIgnoreCase("existing@example.com");
        verify(memberRepository, never()).save(any(Member.class));
        assertThat(issuedInvitationCount.get()).isZero();
    }

    @Test
    void updateUserRejectsAssigningMinorAdminWhenAnotherAccountAlreadyExistsForStation() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
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
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot("SACCO-01", "ST-1", Position.MINOR_ADMIN, accountId)).thenReturn(true);

        assertThatThrownBy(() -> adminService.updateUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            accountId,
            List.of(Position.MINOR_ADMIN),
            MemberStatus.ACTIVE
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Each SACCO station can only have one Minor Admin account. Update the existing one instead.");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void updateUserKeepsAdminClassClaimsLimitedToAdminDefaults() {
        UUID accountId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("Minor Admin")
            .email("minor@example.com")
            .status(MemberStatus.ACTIVE)
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .createdAt(OffsetDateTime.now().minusDays(1))
            .build();
        UserSettings settings = UserSettings.builder()
            .memberId(accountId)
            .language("en")
            .notificationPrefs("{}")
            .createdAt(OffsetDateTime.now().minusDays(1))
            .updatedAt(OffsetDateTime.now().minusDays(1))
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot("SACCO-01", "ST-1", Position.MINOR_ADMIN, accountId)).thenReturn(false);
        when(userSettingsRepository.existsById(accountId)).thenReturn(true);
        when(userSettingsRepository.findById(accountId)).thenReturn(Optional.of(settings));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.updateUser(
            "SACCO-01",
            "ST-1",
            adminId,
            Set.of(Position.MINOR_ADMIN),
            accountId,
            List.of(Position.MINOR_ADMIN),
            MemberStatus.ACTIVE,
            List.of(UserClaim.ACCESS_ADMIN_SETTINGS, UserClaim.ACCESS_DISBURSEMENT_QUEUE, UserClaim.DISBURSE_LOAN)
        );

        verify(userSettingsRepository).save(argThat(saved ->
            saved.getNotificationPrefs().contains("ACCESS_ADMIN_SETTINGS")
                && saved.getNotificationPrefs().contains("ACCESS_OUTBOX_MONITOR")
                && !saved.getNotificationPrefs().contains("ACCESS_DISBURSEMENT_QUEUE")
                && !saved.getNotificationPrefs().contains("DISBURSE_LOAN")
        ));
    }

    @Test
    void updateUserRemovesMemberOnlyClaimsFromStaffOnlyAccounts() {
        UUID accountId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MGR001")
            .fullName("Manager User")
            .email("manager@example.com")
            .status(MemberStatus.ACTIVE)
            .position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .memberAccount(false)
            .createdAt(OffsetDateTime.now().minusDays(1))
            .build();
        UserSettings settings = UserSettings.builder()
            .memberId(accountId)
            .language("en")
            .notificationPrefs("{}")
            .createdAt(OffsetDateTime.now().minusDays(1))
            .updatedAt(OffsetDateTime.now().minusDays(1))
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSettingsRepository.existsById(accountId)).thenReturn(true);
        when(userSettingsRepository.findById(accountId)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.updateUser(
            "SACCO-01",
            "ST-1",
            adminId,
            Set.of(Position.ADMIN),
            accountId,
            List.of(Position.MANAGER),
            MemberStatus.ACTIVE,
            List.of(UserClaim.APPLY_LOANS, UserClaim.APPROVE_GUARANTOR_REQUESTS, UserClaim.REVIEW_MANAGER_QUEUE)
        );

        verify(userSettingsRepository).save(argThat(saved ->
            saved.getNotificationPrefs().contains("REVIEW_MANAGER_QUEUE")
                && !saved.getNotificationPrefs().contains("APPLY_LOANS")
                && !saved.getNotificationPrefs().contains("APPROVE_GUARANTOR_REQUESTS")
        ));
    }

    @Test
    void updateUserAllowsExistingMinorAdminToKeepSameSlot() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
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
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot("SACCO-01", "ST-1", Position.MINOR_ADMIN, accountId)).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSettingsRepository.findById(accountId)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.updateUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            accountId,
            List.of(Position.MINOR_ADMIN),
            MemberStatus.ACTIVE
        );

        verify(memberRepository).save(member);
        verify(userSettingsRepository, times(2)).save(any(UserSettings.class));
    }

    @Test
    void updateMinorAdminRejectsOccupiedSaccoStation() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("Minor Admin")
            .email("minor@example.com")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .build();
        RegisteredSacco registeredSacco = RegisteredSacco.builder()
            .saccoId("SACCO-01")
            .saccoName("SACCO One")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        SaccoStation station = SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("ST-2")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(registeredSaccoRepository.findById("SACCO-01")).thenReturn(Optional.of(registeredSacco));
        when(saccoStationRepository.findBySaccoIdAndStationIdAndActiveTrue("SACCO-01", "ST-2")).thenReturn(Optional.of(station));
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot("SACCO-01", "ST-2", Position.MINOR_ADMIN, accountId)).thenReturn(true);

        assertThatThrownBy(() -> adminService.updateMinorAdmin(
            UUID.randomUUID(),
            accountId,
            "SACCO-01",
            "ST-2",
            "MINOR001",
            "Minor Admin",
            "minor@example.com",
            null
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Each SACCO station can only have one Minor Admin account. Update the existing one instead.");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void updateMinorAdminAllowsMoveToOpenStationInSameSacco() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
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
        RegisteredSacco registeredSacco = RegisteredSacco.builder()
            .saccoId("SACCO-01")
            .saccoName("SACCO One")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        SaccoStation station = SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("ST-2")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(registeredSaccoRepository.findById("SACCO-01")).thenReturn(Optional.of(registeredSacco));
        when(saccoStationRepository.findBySaccoIdAndStationIdAndActiveTrue("SACCO-01", "ST-2")).thenReturn(Optional.of(station));
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot("SACCO-01", "ST-2", Position.MINOR_ADMIN, accountId)).thenReturn(false);
        when(memberRepository.existsByMemberNoIgnoreCaseAndIdNot("MINOR002", accountId)).thenReturn(false);
        when(memberRepository.existsByEmailIgnoreCaseAndIdNot("minor2@example.com", accountId)).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.updateMinorAdmin(
            UUID.randomUUID(),
            accountId,
            "SACCO-01",
            "ST-2",
            "MINOR002",
            "Minor Admin Two",
            "minor2@example.com",
            "255712345679"
        );

        assertThat(member.getStationId()).isEqualTo("ST-2");
        assertThat(member.getMemberNo()).isEqualTo("MINOR002");
        assertThat(member.getEmail()).isEqualTo("minor2@example.com");
        verify(memberRepository).save(member);
    }

    @Test
    void updateUserRejectsStationScopedLegacyMemberFromAnotherSacco() {
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
        assertThatThrownBy(() -> adminService.updateUser(
            "TAHA SACCOS",
            "AR704",
            UUID.randomUUID(),
            Set.of(Position.MINOR_ADMIN),
            accountId,
            List.of(Position.MANAGER),
            MemberStatus.ACTIVE
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Member not found in this SACCO");

        assertThat(member.getSaccoId()).isEqualTo("SACCO-ARUSHA-001");
        assertThat(member.getStationId()).isEqualTo("AR704");
        verify(memberRepository, never()).save(member);
    }

    @Test
    void reinviteMinorAdminRestoresPlaceholderPassword() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
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
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot("SACCO-01", "ST-1", Position.MINOR_ADMIN, accountId)).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.reinviteMinorAdmin(UUID.randomUUID(), accountId);

        org.assertj.core.api.Assertions.assertThat(member.getStatus()).isEqualTo(MemberStatus.INVITED);
        org.assertj.core.api.Assertions.assertThat(member.getPasswordHash()).isEqualTo("OTP_ONLY_LOGIN");
        verify(memberRepository).save(member);
    }

    @Test
    void resendMinorAdminInvitationOnlyWorksForPendingMinorAdmin() {
        UUID accountId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("Minor Admin")
            .email("minor@example.com")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.INVITED)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot("SACCO-01", "ST-1", Position.MINOR_ADMIN, accountId)).thenReturn(false);

        adminService.resendMinorAdminInvitation(adminId, accountId);

        assertThat(issuedInvitationCount.get()).isEqualTo(1);
        assertThat(lastInvitedMember.get()).isEqualTo(member);
        assertThat(lastInvitedBy.get()).isEqualTo(adminId);
    }

    @Test
    void resendMinorAdminInvitationRejectsOccupiedSaccoStation() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("Minor Admin")
            .email("minor@example.com")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.INVITED)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot("SACCO-01", "ST-1", Position.MINOR_ADMIN, accountId)).thenReturn(true);

        assertThatThrownBy(() -> adminService.resendMinorAdminInvitation(UUID.randomUUID(), accountId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Each SACCO station can only have one Minor Admin account. Update the existing one instead.");

        assertThat(issuedInvitationCount.get()).isZero();
    }

    @Test
    void revokeMinorAdminInvitationMarksAccountInactive() {
        UUID accountId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("Minor Admin")
            .email("minor@example.com")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.INVITED)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.revokeMinorAdminInvitation(adminId, accountId);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(revokedInvitationCount.get()).isEqualTo(1);
        assertThat(lastRevokedMemberId.get()).isEqualTo(accountId);
        assertThat(lastRevokedBy.get()).isEqualTo(adminId);
        verify(memberRepository).save(member);
    }

    @Test
    void deactivateMinorAdminOnlyWorksForActiveAccount() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("Minor Admin")
            .email("minor@example.com")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.deactivateMinorAdmin(UUID.randomUUID(), accountId);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        verify(memberRepository).save(member);
    }

    @Test
    void reinviteMinorAdminRejectsActiveAccount() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("Minor Admin")
            .email("minor@example.com")
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminService.reinviteMinorAdmin(UUID.randomUUID(), accountId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("That account is already active.");

        verify(memberRepository, never()).save(any(Member.class));
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
        RegisteredSacco registeredSacco = RegisteredSacco.builder()
            .saccoId("SACCO-01")
            .saccoName("SACCO One")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        SaccoStation station = SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        when(registeredSaccoRepository.findById("SACCO-01")).thenReturn(Optional.of(registeredSacco));
        when(saccoStationRepository.findBySaccoIdAndStationIdAndActiveTrue("SACCO-01", "ST-1")).thenReturn(Optional.of(station));
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPosition("SACCO-01", "ST-1", Position.MINOR_ADMIN)).thenReturn(false);
        when(memberRepository.findByMemberNo("MINOR001")).thenReturn(Optional.empty());
        when(memberRepository.existsByEmailIgnoreCase("minor@example.com")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.registerMinorAdmin(
            adminId,
            "SACCO-01",
            "ST-1",
            "MINOR001",
            "Minor Admin",
            "minor@example.com",
            "255712345678"
        );

        org.assertj.core.api.Assertions.assertThat(issuedInvitationCount.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(lastInvitedBy.get()).isEqualTo(adminId);
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getStationId()).isEqualTo("ST-1");
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
        when(loanProductSettingRepository.findBySaccoIdOrderByLoanTypeAsc("SACCO-01")).thenReturn(List.of(product));
        when(loanProductVersionRepository.findTopByLoanProductSettingIdOrderByVersionNumberDesc(productId)).thenReturn(Optional.empty());
        when(loanProductVersionRepository.save(any(LoanProductVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanProductsVersionRepository.findTopBySaccoIdOrderByVersionNumberDesc("SACCO-01")).thenReturn(Optional.empty());
        when(loanProductsVersionRepository.save(any(com.sacco.mvp.domain.LoanProductsVersion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(loanProductSettingRepository.save(any(LoanProductSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanProductSettingRepository.existsBySaccoIdAndProductCodeIgnoreCaseAndIdNot("SACCO-01", "DEV_GROWTH", productId)).thenReturn(false);
        when(saccoSettingsRepository.findById("SACCO-01")).thenReturn(Optional.of(settings));
        stubActiveRoleDirectory("SACCO-01", List.of(
            Member.builder()
                .id(UUID.randomUUID())
                .saccoId("SACCO-01")
                .memberNo("DISB-1")
                .fullName("Disbursement Officer")
                .status(MemberStatus.ACTIVE)
                .position(Position.DISBURSEMENT_OFFICER)
                .staffRoles(new LinkedHashSet<>(List.of(Position.DISBURSEMENT_OFFICER)))
                .build()
        ));

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
    void updateLoanProductPersistsCustomWorkflowPriorityOrder() {
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
            .managerPriority(1)
            .loanOfficerReviewRequired(true)
            .loanOfficerPriority(2)
            .workflowStartStage(ApprovalWorkflowStage.MANAGER)
            .committeeReviewRequired(true)
            .committeePriority(3)
            .committeeMinimumVotes(1)
            .committeeApprovalThreshold(1)
            .accountantReviewRequired(true)
            .accountantPriority(4)
            .disbursementOfficerRequired(true)
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
            .boardQuorum(1)
            .defaultLanguage("en")
            .createdAt(OffsetDateTime.now().minusDays(2))
            .updatedAt(OffsetDateTime.now().minusDays(1))
            .build();

        when(loanProductSettingRepository.findById(productId)).thenReturn(Optional.of(product));
        when(loanProductSettingRepository.findBySaccoIdOrderByLoanTypeAsc("SACCO-01")).thenReturn(List.of(product));
        when(loanProductVersionRepository.findTopByLoanProductSettingIdOrderByVersionNumberDesc(productId)).thenReturn(Optional.empty());
        when(loanProductVersionRepository.save(any(LoanProductVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanProductsVersionRepository.findTopBySaccoIdOrderByVersionNumberDesc("SACCO-01")).thenReturn(Optional.empty());
        when(loanProductsVersionRepository.save(any(com.sacco.mvp.domain.LoanProductsVersion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(loanProductSettingRepository.save(any(LoanProductSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanProductSettingRepository.existsBySaccoIdAndProductCodeIgnoreCaseAndIdNot("SACCO-01", "DEV_LOAN", productId)).thenReturn(false);
        when(saccoSettingsRepository.findById("SACCO-01")).thenReturn(Optional.of(settings));
        UUID boardReviewerId = UUID.randomUUID();
        stubActiveRoleDirectory("SACCO-01", List.of(
            Member.builder()
                .id(UUID.randomUUID())
                .saccoId("SACCO-01")
                .memberNo("LO-1")
                .fullName("Loan Officer")
                .status(MemberStatus.ACTIVE)
                .position(Position.LOAN_OFFICER)
                .staffRoles(new LinkedHashSet<>(List.of(Position.LOAN_OFFICER)))
                .build(),
            Member.builder()
                .id(boardReviewerId)
                .saccoId("SACCO-01")
                .memberNo("BOARD-1")
                .fullName("Committee Reviewer")
                .status(MemberStatus.ACTIVE)
                .position(Position.BOARD)
                .staffRoles(new LinkedHashSet<>(List.of(Position.BOARD)))
                .build(),
            Member.builder()
                .id(UUID.randomUUID())
                .saccoId("SACCO-01")
                .memberNo("ACC-1")
                .fullName("Accountant")
                .status(MemberStatus.ACTIVE)
                .position(Position.ACCOUNTANT)
                .staffRoles(new LinkedHashSet<>(List.of(Position.ACCOUNTANT)))
                .build(),
            Member.builder()
                .id(UUID.randomUUID())
                .saccoId("SACCO-01")
                .memberNo("DISB-1")
                .fullName("Disbursement Officer")
                .status(MemberStatus.ACTIVE)
                .position(Position.DISBURSEMENT_OFFICER)
                .staffRoles(new LinkedHashSet<>(List.of(Position.DISBURSEMENT_OFFICER)))
                .build()
        ));

        adminService.updateLoanProduct(
            "SACCO-01",
            adminId,
            productId,
            "DEV_LOAN",
            "Development Loan",
            "Updated",
            4,
            new BigDecimal("100000.00"),
            new BigDecimal("1000000.00"),
            2,
            new BigDecimal("0.3333"),
            new BigDecimal("0.0150"),
            new BigDecimal("0.1000"),
            InterestMethod.FLAT_RATE,
            3,
            12,
            false,
            false,
            true,
            true,
            ApprovalWorkflowStage.LOAN_OFFICER,
            4,
            3,
            true,
            1,
            1,
            1,
            List.of(boardReviewerId),
            true,
            2,
            true,
            true,
            false,
            false,
            BigDecimal.ZERO,
            LoanProductStatus.ACTIVE
        );

        assertThat(product.getManagerPriority()).isEqualTo(4);
        assertThat(product.getLoanOfficerPriority()).isEqualTo(3);
        assertThat(product.getCommitteePriority()).isEqualTo(1);
        assertThat(product.getAccountantPriority()).isEqualTo(2);
        assertThat(product.getWorkflowStartStage()).isEqualTo(ApprovalWorkflowStage.LOAN_OFFICER);
    }

    @Test
    void createCustomizedLoanProductRejectsDuplicateManagerAndLoanOfficerPriorities() {
        stubActiveRoleDirectory("SACCO-01", List.of(
            Member.builder()
                .id(UUID.randomUUID())
                .saccoId("SACCO-01")
                .memberNo("LO-1")
                .fullName("Loan Officer")
                .position(Position.LOAN_OFFICER)
                .staffRoles(new LinkedHashSet<>(List.of(Position.LOAN_OFFICER)))
                .status(MemberStatus.ACTIVE)
                .build(),
            Member.builder()
                .id(UUID.randomUUID())
                .saccoId("SACCO-01")
                .memberNo("DISB-1")
                .fullName("Disbursement Officer")
                .position(Position.DISBURSEMENT_OFFICER)
                .staffRoles(new LinkedHashSet<>(List.of(Position.DISBURSEMENT_OFFICER)))
                .status(MemberStatus.ACTIVE)
                .build()
        ));

        assertThatThrownBy(() -> adminService.createCustomizedLoanProduct(
            "SACCO-01",
            UUID.randomUUID(),
            "CUSTOM_PRIORITY",
            "Custom Priority Loan",
            "Priority validation",
            5,
            new BigDecimal("100000.00"),
            new BigDecimal("500000.00"),
            1,
            new BigDecimal("0.3333"),
            new BigDecimal("0.0100"),
            new BigDecimal("0.1000"),
            InterestMethod.FLAT_RATE,
            1,
            12,
            false,
            false,
            true,
            true,
            ApprovalWorkflowStage.MANAGER,
            1,
            1,
            false,
            3,
            null,
            null,
            true,
            4,
            true,
            false,
            BigDecimal.ZERO,
            LoanProductStatus.ACTIVE
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Manager and Loan Officer cannot share the same priority slot.");

        verify(loanProductsVersionRepository, never()).save(any(com.sacco.mvp.domain.LoanProductsVersion.class));
        verify(loanProductSettingRepository, never()).save(any(LoanProductSetting.class));
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
