package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
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
import com.sacco.mvp.domain.MemberAccessClaim;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.SmsUnitStatus;
import com.sacco.mvp.domain.StationSmsAccount;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.ForesightMemberProfile;
import com.sacco.mvp.repository.AdminIncidentRepository;
import com.sacco.mvp.repository.AuditLogRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductBoardReviewerRepository;
import com.sacco.mvp.repository.LoanProductsVersionRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.LoanProductVersionRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberAccessClaimRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.NotificationRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.repository.SavingsAccountRepository;
import com.sacco.mvp.repository.StationSmsAccountRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock private StationSmsAccountRepository stationSmsAccountRepository;
    @Mock private AdminIncidentRepository adminIncidentRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private MemberAccessClaimRepository memberAccessClaimRepository;
    @Mock private ForesightDirectoryService foresightDirectoryService;

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
        UserClaimService userClaimService = new UserClaimService(memberAccessClaimRepository, userSettingsRepository, objectMapper);
        RoleDirectoryService roleDirectoryService = new RoleDirectoryService(memberRepository, userClaimService);
        AuditService auditService = new AuditService(auditLogRepository, objectMapper);
        AdminAlertService adminAlertService = new AdminAlertService(
            roleDirectoryService,
            notificationRepository,
            adminIncidentRepository,
            objectMapper
        );
        NotificationViewService notificationViewService = new NotificationViewService(objectMapper, memberRepository, new AccessControlService());
        SaccoConfigurationService saccoConfigurationService = new SaccoConfigurationService(loanProductSettingRepository);
        NameSignatureService nameSignatureService = new NameSignatureService();
        SaccoRegistryService saccoRegistryService = new SaccoRegistryService(
            registeredSaccoRepository,
            saccoStationRepository,
            saccoSettingsRepository,
            null,
            org.mockito.Mockito.mock(SmsUnitTransactionService.class),
            auditService,
            org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class)
        );
        MinorAdminInvitationService minorAdminInvitationService = new MinorAdminInvitationService(
            null,
            memberRepository,
            null,
            null,
            null,
            nameSignatureService
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
        lenient().when(foresightDirectoryService.lookupMemberProfileByPhone(any()))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.notFound());
        lenient().when(foresightDirectoryService.lookupMemberProfileByEmailV2(any()))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.notFound());
        AtomicInteger staffNumberSequence = new AtomicInteger(10000);
        lenient().when(memberRepository.nextStaffNumberValue())
            .thenAnswer(invocation -> (long) staffNumberSequence.getAndIncrement());
        lenient().when(memberAccessClaimRepository.findByIdMemberId(any()))
            .thenReturn(List.of());

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
            stationSmsAccountRepository,
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
            objectMapper,
            nameSignatureService,
            foresightDirectoryService
        );
    }

    @Test
    void dashboardReadsSelectedStationSmsBalanceWithoutCreatingAccount() {
        StationSmsAccount account = StationSmsAccount.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .availableUnits(17)
            .status(SmsUnitStatus.LOW)
            .build();
        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtDesc(com.sacco.mvp.domain.OutboxStatus.FAILED))
            .thenReturn(List.of());
        when(auditLogRepository.searchEventLogViewScoped(any(), any(), any(), eq("SACCO-1"), eq("ST-1"), any()))
            .thenReturn(new PageImpl<>(List.of()));
        when(adminIncidentRepository.findTop50BySaccoIdAndCreatedAtAfterOrderByCreatedAtDesc(eq("SACCO-1"), any()))
            .thenReturn(List.of());
        when(memberRepository.countByStatusForScope("SACCO-1", "ST-1")).thenReturn(List.of());
        when(loanApplicationRepository.countByStatusForScope("SACCO-1", "ST-1")).thenReturn(List.of());
        when(stationSmsAccountRepository.findBySaccoIdAndStationId("SACCO-1", "ST-1")).thenReturn(Optional.of(account));

        AdminService.AdminDashboard dashboard = adminService.dashboard("SACCO-1", "ST-1", UUID.randomUUID());

        assertThat(dashboard.getSmsBalance().getAvailableUnits()).isEqualTo(17);
        assertThat(dashboard.getSmsBalance().getStationId()).isEqualTo("ST-1");
        assertThat(dashboard.getSmsBalance().getStatusLabel()).isEqualTo("Low");
        verify(stationSmsAccountRepository, never()).save(any());
    }

    @Test
    void usersPageSearchesByUserIdPrefixAndSeparatesStaffNumber() {
        UUID accountId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        Member staff = Member.builder()
            .id(accountId)
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .memberNo("10002")
            .fullName("Loan Officer")
            .email("officer@example.com")
            .phone("255712345678")
            .position(Position.LOAN_OFFICER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.LOAN_OFFICER)))
            .memberAccount(false)
            .status(MemberStatus.INVITED)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findUserAccessPageByUserId(eq("SACCO-1"), eq("ST-1"), eq("1234"), any()))
            .thenReturn(new PageImpl<>(List.of(staff)));

        AdminService.UserAccessView row = adminService
            .usersPage("SACCO-1", "ST-1", "userId", "1234", 0, 25)
            .getContent()
            .get(0);

        assertThat(row.getUserIdLabel()).isEqualTo("12345678");
        assertThat(row.getMemberNumber()).isEqualTo("-");
        assertThat(row.getStaffMemberNumber()).isEqualTo("10002");
        assertThat(row.getPhone()).isEqualTo("255712345678");
        assertThat(row.getDisplayStatus()).isEqualTo("Invite email sent - waiting");
        verify(memberRepository, never()).findUserAccessPageByName(any(), any(), any(), any());
    }

    @Test
    void usersPageSearchesByNameAndSeparatesMemberNumber() {
        UUID accountId = UUID.fromString("87654321-1234-1234-1234-123456789abc");
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .memberNo("MEM001")
            .fullName("Member User")
            .email("member@example.com")
            .phone("255798765432")
            .position(Position.MEMBER)
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findUserAccessPageByName(eq("SACCO-1"), eq("ST-1"), eq("member"), any()))
            .thenReturn(new PageImpl<>(List.of(member)));

        AdminService.UserAccessView row = adminService
            .usersPage("SACCO-1", "ST-1", "name", " Member ", 0, 25)
            .getContent()
            .get(0);

        assertThat(row.getUserIdLabel()).isEqualTo("87654321");
        assertThat(row.getMemberNumber()).isEqualTo("MEM001");
        assertThat(row.getStaffMemberNumber()).isEqualTo("-");
        assertThat(row.getPhone()).isEqualTo("255798765432");
        assertThat(row.getDisplayStatus()).isEqualTo("ACTIVE");
        verify(memberRepository, never()).findUserAccessPageByUserId(any(), any(), any(), any());
    }

    @Test
    void userAccessLoadsSingleUserInsideSaccoAndStationScope() {
        UUID accountId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        Member staff = Member.builder()
            .id(accountId)
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .memberNo("STAFF-10002")
            .staffNo("10002")
            .fullName("Manager User")
            .email("manager@example.com")
            .phone("255712345678")
            .position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findUserAccessByScope("SACCO-1", "ST-1", accountId))
            .thenReturn(Optional.of(staff));

        AdminService.UserAccessView view = adminService.userAccess("SACCO-1", "ST-1", accountId);

        assertThat(view.getAccountId()).isEqualTo(accountId);
        assertThat(view.getStaffMemberNumber()).isEqualTo("10002");
        assertThat(view.getRoleSummary()).isEqualTo("Manager");
    }

    @Test
    void userAccessRejectsUsersOutsideSaccoOrStationScope() {
        UUID accountId = UUID.randomUUID();
        when(memberRepository.findUserAccessByScope("SACCO-1", "ST-1", accountId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.userAccess("SACCO-1", "ST-1", accountId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Member not found in this SACCO.");
    }

    @Test
    void saccoDetailMembersPageUsesCombinedSearchAndAccountLabels() {
        UUID accountId = UUID.fromString("abcdef12-1234-1234-1234-123456789abc");
        Member staffMember = Member.builder()
            .id(accountId)
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .memberNo("MBR100")
            .fullName("Dual Access User")
            .email("dual@example.com")
            .phone("255700000001")
            .position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findSaccoDetailMemberPage(eq("SACCO-1"), eq("ST-1"), eq("dual"), any()))
            .thenReturn(new PageImpl<>(List.of(staffMember)));

        AdminService.UserAccessView row = adminService
            .saccoDetailMembersPage("SACCO-1", "ST-1", " Dual ", 0, 25)
            .getContent()
            .get(0);

        assertThat(row.getAccountId()).isEqualTo(accountId);
        assertThat(row.getLoginId()).isEqualTo("MBR100");
        assertThat(row.getMembershipLabel()).isEqualTo("Staff And Member");
        assertThat(row.getRoleSummary()).isEqualTo("Manager");
        assertThat(row.getPhone()).isEqualTo("255700000001");
    }

    @Test
    void outboxEventsSearchesByLoanApplicationId() {
        when(outboxEventRepository.searchMonitorView(isNull(), isNull(), isNull(), eq("abc123"), any()))
            .thenReturn(new PageImpl<>(List.of()));

        adminService.outboxEvents(0, 25, null, null, null, " abc123 ");

        verify(outboxEventRepository).searchMonitorView(isNull(), isNull(), isNull(), eq("abc123"), any());
        verify(outboxEventRepository, never()).searchMonitorViewScoped(any(), any(), any(), any(), any(), any(), any());
    }

    private void stubActiveRoleDirectory(String saccoId, List<Member> activeMembers) {
        lenient().when(memberRepository.findBySaccoIdAndStatusOrderByFullNameAsc(saccoId, MemberStatus.ACTIVE))
            .thenReturn(activeMembers);
        activeMembers.forEach(member -> lenient().when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member)));
        lenient().when(memberRepository.findActiveRoleMembers(eq(saccoId), any())).thenAnswer(invocation -> {
            Position position = invocation.getArgument(1);
            return activeMembers.stream()
                .filter(member -> member.getStaffRolesResolved().contains(position))
                .toList();
        });
        lenient().when(memberRepository.findActiveRoleMembersInStation(eq(saccoId), any(), any())).thenAnswer(invocation -> {
            String stationId = invocation.getArgument(1);
            Position position = invocation.getArgument(2);
            return activeMembers.stream()
                .filter(member -> stationId == null || stationId.equalsIgnoreCase(member.getStationId()))
                .filter(member -> member.getStaffRolesResolved().contains(position))
                .toList();
        });
        lenient().when(memberRepository.findActiveMembersWithClaimInStation(eq(saccoId), any(), any())).thenAnswer(invocation -> {
            String stationId = invocation.getArgument(1);
            String claimName = invocation.getArgument(2);
            UserClaim claim = UserClaim.valueOf(claimName);
            return activeMembers.stream()
                .filter(member -> stationId == null || stationId.equalsIgnoreCase(member.getStationId()))
                .filter(member -> UserClaim.defaultClaims(member.getStaffRolesResolved(), member.isMemberAccess()).contains(claim))
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
        stubActiveRoleDirectory("SACCO-01", List.of(sameStationAdmin, otherStationAdmin));
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
            "SACCOS Admin",
            "minor@example.com",
            null,
            List.of(Position.MINOR_ADMIN)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Each SACCO station can only have one SACCOS Admin account. Update the existing one instead.");

        verify(memberRepository).existsBySaccoIdAndStationIdIgnoreCaseAndPosition("SACCO-01", "ST-1", Position.MINOR_ADMIN);
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void createUserRejectsEmailAlreadyUsedByAnyAccount() {
        when(memberRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> adminService.createUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            "Mary Manager",
            " Existing@Example.com ",
            "255700000005",
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
            .hasMessage("Each SACCO station can only have one SACCOS Admin account. Update the existing one instead.");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void updateUserAllowsSaccosAdminToOverlapWithEveryStaffRoleAndDefaultClaims() {
        UUID accountId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("SACCOS Admin")
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

        List<Position> staffRoles = List.of(
            Position.MINOR_ADMIN,
            Position.MANAGER,
            Position.CHAIRPERSON,
            Position.BOARD,
            Position.CREDIT_COMMITTEE,
            Position.LOAN_OFFICER,
            Position.ACCOUNTANT,
            Position.DISBURSEMENT_OFFICER
        );

        adminService.updateUser(
            "SACCO-01",
            "ST-1",
            adminId,
            Set.of(Position.MINOR_ADMIN),
            accountId,
            staffRoles,
            MemberStatus.ACTIVE,
            null
        );

        verify(memberRepository).save(argThat(saved ->
            saved.getStaffRolesResolved().containsAll(staffRoles)
                && saved.getPosition() == Position.MINOR_ADMIN
        ));
        verify(memberAccessClaimRepository).deleteByMemberId(accountId);
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.WORKSPACE_SETTINGS_CONFIGURE));
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.MANAGER_QUEUE_APPROVE));
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.CHAIRPERSON_QUEUE_APPROVE));
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.BOARD_QUEUE_APPROVE));
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.CREDIT_COMMITTEE_QUEUE_APPROVE));
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.LOAN_OFFICER_QUEUE_ASSIGN));
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.ACCOUNTANT_QUEUE_APPROVE));
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.DISBURSEMENT_QUEUE_DISBURSE));
    }

    @Test
    void updateUserRejectsSuperAdminOverlappingWithOtherRoles() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("ADMIN001")
            .fullName("Super Admin")
            .email("admin@example.com")
            .status(MemberStatus.ACTIVE)
            .position(Position.ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.ADMIN)))
            .memberAccount(false)
            .createdAt(OffsetDateTime.now().minusDays(1))
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminService.updateUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            accountId,
            List.of(Position.ADMIN, Position.MANAGER),
            MemberStatus.ACTIVE
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Super Admin accounts cannot be combined with any other staff role.");

        verify(memberRepository, never()).save(any(Member.class));
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
            List.of(UserClaim.MEMBER_LOANS_VIEW, UserClaim.GUARANTOR_REQUESTS_VIEW, UserClaim.MANAGER_QUEUE_APPROVE)
        );

        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.MANAGER_QUEUE_APPROVE));
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.MANAGER_QUEUE_VIEW));
        verify(memberAccessClaimRepository, never()).save(claimNamed(UserClaim.MEMBER_LOANS_VIEW));
        verify(memberAccessClaimRepository, never()).save(claimNamed(UserClaim.GUARANTOR_REQUESTS_VIEW));
    }

    @Test
    void updateUserRejectsStaffRoleWithoutItsQueueViewClaim() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MGR002")
            .fullName("Manager User")
            .email("manager@example.com")
            .status(MemberStatus.ACTIVE)
            .position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .memberAccount(false)
            .createdAt(OffsetDateTime.now().minusDays(1))
            .build();
        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminService.updateUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.MINOR_ADMIN),
            accountId,
            List.of(Position.MANAGER),
            MemberStatus.ACTIVE,
            List.of(UserClaim.NOTIFICATIONS_VIEW)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Manager access requires Manager Queue - View. Select that permission or use Restore Default Permissions.");

        verify(memberRepository, never()).save(any(Member.class));
        verifyNoInteractions(memberAccessClaimRepository);
    }

    @Test
    void updateUserRejectsAdministratorWithoutRecoveryClaims() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("ADMIN002")
            .fullName("SACCOS Admin")
            .email("admin@example.com")
            .status(MemberStatus.ACTIVE)
            .position(Position.MINOR_ADMIN)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .memberAccount(false)
            .createdAt(OffsetDateTime.now().minusDays(1))
            .build();
        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot(
            "SACCO-01", "ST-1", Position.MINOR_ADMIN, accountId
        )).thenReturn(false);

        assertThatThrownBy(() -> adminService.updateUser(
            "SACCO-01",
            "ST-1",
            accountId,
            Set.of(Position.MINOR_ADMIN),
            accountId,
            List.of(Position.MINOR_ADMIN),
            MemberStatus.ACTIVE,
            List.of(UserClaim.USER_ACCESS_UPDATE)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Administrator access requires Admin Dashboard - View, Access Matrix - View and Update, "
                    + "and User Access - View and Update. Select these permissions or use Restore Default Permissions."
            );

        verify(memberRepository, never()).save(any(Member.class));
        verifyNoInteractions(memberAccessClaimRepository);
    }

    @Test
    void updateUserAllowsExistingMinorAdminToKeepSameSlot() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("SACCOS Admin")
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
            .fullName("SACCOS Admin")
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
            "SACCOS Admin",
            "minor@example.com",
            null
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Each SACCO station can only have one SACCOS Admin account. Update the existing one instead.");

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
            .fullName("SACCOS Admin")
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
        when(memberRepository.existsByEmailIgnoreCaseAndIdNot("minor2@example.com", accountId)).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.updateMinorAdmin(
            UUID.randomUUID(),
            accountId,
            "SACCO-01",
            "ST-2",
            "SACCOS Admin Two",
            "minor2@example.com",
            "255712345679"
        );

        assertThat(member.getStationId()).isEqualTo("ST-2");
        assertThat(member.getMemberNo()).isEqualTo("MINOR001");
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
    void updateUserRejectsManualActivationForInvitedStaff() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("10002")
            .fullName("Invited Staff")
            .email("invited@example.com")
            .position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .memberAccount(false)
            .status(MemberStatus.INVITED)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminService.updateUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.MINOR_ADMIN),
            accountId,
            List.of(Position.MANAGER),
            MemberStatus.ACTIVE
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Invited staff accounts become active only after the invite form is completed.");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void updateUserRejectsManualDeactivationForInvitedStaff() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("10002")
            .fullName("Invited Staff")
            .email("invited@example.com")
            .position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .memberAccount(false)
            .status(MemberStatus.INVITED)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminService.updateUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.MINOR_ADMIN),
            accountId,
            List.of(Position.MANAGER),
            MemberStatus.INACTIVE
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Invited staff accounts become active only after the invite form is completed.");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void updateUserRejectsMovingActiveStaffBackToInvited() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("10002")
            .fullName("Active Staff")
            .email("active@example.com")
            .position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminService.updateUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.MINOR_ADMIN),
            accountId,
            List.of(Position.MANAGER),
            MemberStatus.INVITED
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Staff accounts cannot be manually moved back to invited status.");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void updateUserAllowsRoleAndClaimEditsForInvitedStaffWhenStatusRemainsInvited() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("10002")
            .fullName("Invited Staff")
            .email("invited@example.com")
            .position(Position.MANAGER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)))
            .memberAccount(false)
            .status(MemberStatus.INVITED)
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
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userSettingsRepository.existsById(accountId)).thenReturn(true);
        when(userSettingsRepository.findById(accountId)).thenReturn(Optional.of(settings));

        adminService.updateUser(
            "SACCO-01",
            "ST-1",
            UUID.randomUUID(),
            Set.of(Position.MINOR_ADMIN),
            accountId,
            List.of(Position.LOAN_OFFICER),
            MemberStatus.INVITED,
            List.of(UserClaim.LOAN_OFFICER_QUEUE_APPROVE)
        );

        assertThat(member.getStatus()).isEqualTo(MemberStatus.INVITED);
        assertThat(member.getStaffRolesResolved()).contains(Position.LOAN_OFFICER);
        verify(memberRepository).save(member);
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.LOAN_OFFICER_QUEUE_APPROVE));
    }

    private MemberAccessClaim claimNamed(UserClaim claim) {
        return argThat(saved -> saved != null
            && saved.getId() != null
            && claim.name().equals(saved.getId().getClaimName()));
    }

    @Test
    void reinviteMinorAdminRestoresPlaceholderPassword() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("SACCOS Admin")
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
            .fullName("SACCOS Admin")
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
            .fullName("SACCOS Admin")
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
            .hasMessage("Each SACCO station can only have one SACCOS Admin account. Update the existing one instead.");

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
            .fullName("SACCOS Admin")
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
    void cancelStaffInvitationMarksAccountInactive() {
        UUID accountId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("STAFF-10002")
            .staffNo("10002")
            .fullName("Loan Officer")
            .email("loan.officer@example.com")
            .position(Position.LOAN_OFFICER)
            .staffRoles(new LinkedHashSet<>(List.of(Position.LOAN_OFFICER)))
            .memberAccount(false)
            .status(MemberStatus.INVITED)
            .passwordHash("OTP_ONLY_LOGIN")
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.cancelStaffInvitation("SACCO-01", "ST-1", adminId, Set.of(Position.ADMIN), accountId);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(revokedInvitationCount.get()).isEqualTo(1);
        assertThat(lastRevokedMemberId.get()).isEqualTo(accountId);
        assertThat(lastRevokedBy.get()).isEqualTo(adminId);
        verify(memberRepository).save(member);
    }

    @Test
    void cancelStaffInvitationRejectsStaffOutsideStationScope() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-2")
            .memberNo("STAFF-10003")
            .staffNo("10003")
            .fullName("Board Member")
            .email("board@example.com")
            .position(Position.BOARD)
            .staffRoles(new LinkedHashSet<>(List.of(Position.BOARD)))
            .memberAccount(false)
            .status(MemberStatus.INVITED)
            .passwordHash("OTP_ONLY_LOGIN")
            .createdAt(OffsetDateTime.now())
            .build();

        when(memberRepository.findById(accountId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminService.cancelStaffInvitation("SACCO-01", "ST-1", UUID.randomUUID(), Set.of(Position.MINOR_ADMIN), accountId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Staff member not found in this station.");

        assertThat(revokedInvitationCount.get()).isZero();
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void deactivateMinorAdminOnlyWorksForActiveAccount() {
        UUID accountId = UUID.randomUUID();
        Member member = Member.builder()
            .id(accountId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("MINOR001")
            .fullName("SACCOS Admin")
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
            .fullName("SACCOS Admin")
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
        when(memberRepository.nextStaffNumberValue()).thenReturn(10002L);
        when(memberRepository.existsByEmailIgnoreCase("manager@example.com")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.createUser(
            "SACCO-01",
            null,
            adminId,
            Set.of(Position.ADMIN),
            "Mary Manager",
            "manager@example.com",
            "255700000001",
            List.of(Position.MANAGER)
        );

        org.assertj.core.api.Assertions.assertThat(issuedInvitationCount.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(lastInvitedBy.get()).isEqualTo(adminId);
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getStaffNo()).isEqualTo("10002");
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getMemberNo()).isEqualTo("STAFF-10002");
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getStatus()).isEqualTo(MemberStatus.INVITED);
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getPasswordHash()).isEqualTo("OTP_ONLY_LOGIN");
        verify(memberAccessClaimRepository).deleteByMemberId(lastInvitedMember.get().getId());
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.MANAGER_QUEUE_APPROVE));
        verify(memberAccessClaimRepository).save(claimNamed(UserClaim.LOAN_DOCUMENTS_VIEW));
        verify(foresightDirectoryService).lookupMemberProfileByPhone("+255700000001");
        verify(foresightDirectoryService).lookupMemberProfileByEmailV2("manager@example.com");
    }

    @Test
    void createUserSkipsGeneratedStaffNumberAlreadyInUse() {
        UUID adminId = UUID.randomUUID();
        when(memberRepository.nextStaffNumberValue()).thenReturn(10002L, 10003L);
        when(memberRepository.existsByStaffNoIgnoreCase("10002")).thenReturn(true);
        when(memberRepository.existsByEmailIgnoreCase("manager@example.com")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.createUser(
            "SACCO-01",
            null,
            adminId,
            Set.of(Position.ADMIN),
            "Mary Manager",
            "manager@example.com",
            "255700000001",
            List.of(Position.MANAGER)
        );

        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getStaffNo()).isEqualTo("10003");
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getMemberNo()).isEqualTo("STAFF-10003");
    }

    @Test
    void createUserRejectsWhenForesightPhoneProfileExists() {
        when(foresightDirectoryService.lookupMemberProfileByPhone("+255700000002"))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.found(
                new ForesightMemberProfile("Smith", "Anna", "MBR-001", "ST-1", "Demo SACCO")
            ));

        assertThatThrownBy(() -> adminService.createUser(
            "SACCO-01",
            null,
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            "Anna Smith",
            "anna.staff@example.com",
            "255700000002",
            List.of(Position.MANAGER)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("This person is already registered as a SACCO member and cannot be added as staff.");

        verify(foresightDirectoryService).lookupMemberProfileByPhone("+255700000002");
        verify(foresightDirectoryService).lookupMemberProfileByEmailV2("anna.staff@example.com");
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void createUserRejectsWhenForesightEmailProfileExists() {
        when(foresightDirectoryService.lookupMemberProfileByEmailV2("member@example.com"))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.found(
                new ForesightMemberProfile("Member", "Existing", "MBR-002", "ST-1", "Demo SACCO")
            ));

        assertThatThrownBy(() -> adminService.createUser(
            "SACCO-01",
            null,
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            "Existing Member",
            "member@example.com",
            "255700000003",
            List.of(Position.MANAGER)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("This person is already registered as a SACCO member and cannot be added as staff.");

        verify(foresightDirectoryService).lookupMemberProfileByPhone("+255700000003");
        verify(foresightDirectoryService).lookupMemberProfileByEmailV2("member@example.com");
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void createUserRejectsWhenAnyForesightLookupIsUnavailable() {
        when(foresightDirectoryService.lookupMemberProfileByEmailV2("offline@example.com"))
            .thenReturn(ForesightDirectoryService.MemberProfileLookupResult.unavailable());

        assertThatThrownBy(() -> adminService.createUser(
            "SACCO-01",
            null,
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            "Offline Lookup",
            "offline@example.com",
            "255700000004",
            List.of(Position.MANAGER)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("We could not verify this person against Foresight. Try again later.");

        verify(foresightDirectoryService).lookupMemberProfileByPhone("+255700000004");
        verify(foresightDirectoryService).lookupMemberProfileByEmailV2("offline@example.com");
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    void createUserRejectsMissingPhoneBeforeSaving() {
        assertThatThrownBy(() -> adminService.createUser(
            "SACCO-01",
            null,
            UUID.randomUUID(),
            Set.of(Position.ADMIN),
            "No Phone",
            "no-phone@example.com",
            null,
            List.of(Position.MANAGER)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Enter the staff member phone number.");

        verify(foresightDirectoryService, never()).lookupMemberProfileByPhone(any());
        verify(foresightDirectoryService, never()).lookupMemberProfileByEmailV2(any());
        verify(memberRepository, never()).save(any(Member.class));
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
        when(memberRepository.nextStaffNumberValue()).thenReturn(10001L);
        when(memberRepository.existsByEmailIgnoreCase("minor@example.com")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminService.registerMinorAdmin(
            adminId,
            "SACCO-01",
            "ST-1",
            "SACCOS Admin",
            "minor@example.com",
            "255712345678"
        );

        org.assertj.core.api.Assertions.assertThat(issuedInvitationCount.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(lastInvitedBy.get()).isEqualTo(adminId);
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getStaffNo()).isEqualTo("10001");
        org.assertj.core.api.Assertions.assertThat(lastInvitedMember.get().getMemberNo()).isEqualTo("STAFF-10001");
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
        UUID creditCommitteeReviewerId = UUID.randomUUID();
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
                .fullName("Board Reviewer")
                .status(MemberStatus.ACTIVE)
                .position(Position.BOARD)
                .staffRoles(new LinkedHashSet<>(List.of(Position.BOARD)))
                .build(),
            Member.builder()
                .id(creditCommitteeReviewerId)
                .saccoId("SACCO-01")
                .memberNo("CC-1")
                .fullName("Credit Committee Reviewer")
                .status(MemberStatus.ACTIVE)
                .position(Position.CREDIT_COMMITTEE)
                .staffRoles(new LinkedHashSet<>(List.of(Position.CREDIT_COMMITTEE)))
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
            true,
            new BigDecimal("15000.00"),
            new BigDecimal("0.0150"),
            BigDecimal.ZERO,
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
            false,
            3,
            List.of(),
            true,
            5,
            List.of(boardReviewerId),
            true,
            1,
            1,
            1,
            List.of(creditCommitteeReviewerId),
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
        InOrder reviewerReplacement = inOrder(loanProductBoardReviewerRepository);
        reviewerReplacement.verify(loanProductBoardReviewerRepository)
            .deleteByLoanProductSettingIdAndReviewStage(productId, ApprovalWorkflowStage.BOARD);
        reviewerReplacement.verify(loanProductBoardReviewerRepository).flush();
        reviewerReplacement.verify(loanProductBoardReviewerRepository).save(any());
        reviewerReplacement.verify(loanProductBoardReviewerRepository)
            .deleteByLoanProductSettingIdAndReviewStage(productId, ApprovalWorkflowStage.CREDIT_COMMITTEE);
        reviewerReplacement.verify(loanProductBoardReviewerRepository).flush();
        reviewerReplacement.verify(loanProductBoardReviewerRepository).save(any());
    }

    @Test
    void createCustomizedLoanProductCompactsDuplicateManagerAndLoanOfficerPriorities() {
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

        SaccoSettings settings = SaccoSettings.builder()
            .saccoId("SACCO-01")
            .applicationFee(new BigDecimal("15000.00"))
            .requiredGuarantors(1)
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
        AtomicReference<LoanProductSetting> savedProduct = new AtomicReference<>();
        when(loanProductSettingRepository.save(any(LoanProductSetting.class))).thenAnswer(invocation -> {
            LoanProductSetting product = invocation.getArgument(0);
            savedProduct.set(product);
            return product;
        });

        adminService.createCustomizedLoanProduct(
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
            new BigDecimal("15000.00"),
            new BigDecimal("0.0100"),
            BigDecimal.ZERO,
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
        );

        LoanProductSetting product = savedProduct.get();
        assertThat(product).isNotNull();
        assertThat(product.getManagerPriority()).isEqualTo(1);
        assertThat(product.getLoanOfficerPriority()).isEqualTo(2);
        assertThat(product.getWorkflowStartStage()).isEqualTo(ApprovalWorkflowStage.MANAGER);
        verify(loanProductsVersionRepository).save(any(com.sacco.mvp.domain.LoanProductsVersion.class));
        verify(loanProductSettingRepository).save(any(LoanProductSetting.class));
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
