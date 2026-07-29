package com.sacco.mvp.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final int MAX_WORKFLOW_PRIORITY = 6;
    private static final long DASHBOARD_RECENT_WINDOW_DAYS = 30L;
    private static final int DEFAULT_LOG_PAGE_SIZE = 50;
    private static final int MAX_LOG_PAGE_SIZE = 100;
    private static final int DEFAULT_USER_PAGE_SIZE = 25;
    private static final int MAX_USER_PAGE_SIZE = 100;
    private static final String USER_SEARCH_BY_USER_ID = "userId";
    private static final String USER_SEARCH_BY_NAME = "name";
    private static final int FIRST_GENERATED_USER_ID = 10000;
    private static final int LAST_GENERATED_USER_ID = 99999;
    private static final String INVITED_ACCOUNT_PASSWORD_PLACEHOLDER = "OTP_ONLY_LOGIN";
    private static final int MAX_PRODUCT_VERSION_HISTORY = 3;
    private static final int MAX_LOAN_PRODUCTS_VERSION_HISTORY = 3;
    private static final int MAX_WORKFLOW_COUNT = 15;
    private static final BigDecimal MAX_LOAN_SAVINGS_RATIO = new BigDecimal("10.0000");
    private static final BigDecimal DEFAULT_APPLICATION_FEE = new BigDecimal("15000.00");
    private static final DateTimeFormatter PRODUCT_VERSION_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private final MemberRepository memberRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final LoanProductBoardReviewerRepository loanProductBoardReviewerRepository;
    private final LoanProductVersionRepository loanProductVersionRepository;
    private final LoanProductsVersionRepository loanProductsVersionRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final SaccoStationPolicyRepository saccoStationPolicyRepository;
    private final NotificationRepository notificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final StationSmsAccountRepository stationSmsAccountRepository;
    private final AdminIncidentRepository adminIncidentRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final AuditService auditService;
    private final AdminAlertService adminAlertService;
    private final NotificationViewService notificationViewService;
    private final UserClaimService userClaimService;
    private final RoleDirectoryService roleDirectoryService;
    private final SaccoConfigurationService saccoConfigurationService;
    private final SaccoRegistryService saccoRegistryService;
    private final MinorAdminInvitationService minorAdminInvitationService;
    private final ObjectMapper objectMapper;
    private final NameSignatureService nameSignatureService;
    private final ForesightDirectoryService foresightDirectoryService;

    public AdminDashboard dashboard(String saccoId, UUID adminId) {
        return dashboard(saccoId, null, adminId);
    }

    public AdminDashboard dashboard(String saccoId, String stationId, UUID adminId) {
        OffsetDateTime recentWindowStart = OffsetDateTime.now().minusDays(DASHBOARD_RECENT_WINDOW_DAYS);
        String normalizedStationId = normalizeOptional(stationId);
        List<OutboxEvent> failedEvents = outboxEventRepository.findTop100ByStatusOrderByCreatedAtDesc(OutboxStatus.FAILED);
        List<AuditLog> auditEntries = auditLogRepository.searchEventLogViewScoped(
            recentWindowStart,
            null,
            null,
            saccoId,
            normalizedStationId,
            PageRequest.of(0, 10)
        ).getContent();
        List<AdminIncident> incidents = filterIncidentsByStation(
            adminIncidentRepository.findTop50BySaccoIdAndCreatedAtAfterOrderByCreatedAtDesc(saccoId, recentWindowStart),
            normalizedStationId
        );

        Map<MemberStatus, Long> memberCounts = new EnumMap<>(MemberStatus.class);
        memberRepository.countByStatusForScope(saccoId, normalizedStationId)
            .forEach(row -> memberCounts.put(row.getStatus(), row.getTotal()));
        Map<LoanStatus, Long> applicationCounts = new EnumMap<>(LoanStatus.class);
        loanApplicationRepository.countByStatusForScope(saccoId, normalizedStationId)
            .forEach(row -> applicationCounts.put(row.getStatus(), row.getTotal()));
        Map<OutboxStatus, Long> outboxCounts = new EnumMap<>(OutboxStatus.class);
        outboxCounts.put(OutboxStatus.NEW, outboxEventRepository.countByStatus(OutboxStatus.NEW));
        outboxCounts.put(OutboxStatus.PUBLISHED, outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED));
        outboxCounts.put(OutboxStatus.FAILED, outboxEventRepository.countByStatus(OutboxStatus.FAILED));

        boolean attachmentStorageReady = true;
        return new AdminDashboard(
            memberCounts,
            applicationCounts,
            outboxCounts,
            failedEvents.stream().limit(10).toList(),
            auditEntries.stream()
                .limit(10)
                .map(entry -> new AdminEventItem(
                    entry.getDisplayAction(),
                    entry.getDisplayEntityType(),
                    entry.getCreatedAt() == null ? "" : String.valueOf(entry.getCreatedAt())
                ))
                .toList(),
            incidents.stream()
                .filter(incident -> incident.getCreatedAt() != null && !incident.getCreatedAt().isBefore(recentWindowStart))
                .limit(10)
                .toList(),
            stationSmsBalance(saccoId, normalizedStationId),
            attachmentStorageReady,
            Math.toIntExact(memberRepository.countForScope(saccoId, normalizedStationId)),
            Math.toIntExact(loanApplicationRepository.countForScope(saccoId, normalizedStationId))
        );
    }

    private SmsBalanceSummary stationSmsBalance(String saccoId, String stationId) {
        if (stationId == null || stationId.isBlank()) {
            return new SmsBalanceSummary("-", 0L, SmsUnitStatus.DEPLETED);
        }
        return stationSmsAccountRepository.findBySaccoIdAndStationId(saccoId, stationId)
            .map(account -> new SmsBalanceSummary(account.getStationId(), account.getAvailableUnits(), account.getStatus()))
            .orElseGet(() -> new SmsBalanceSummary(stationId, 0L, SmsUnitStatus.DEPLETED));
    }

    public List<UserAccessView> users(String saccoId) {
        return users(saccoId, null);
    }

    public List<UserAccessView> users(String saccoId, String stationId) {
        List<UserAccessView> users = new java.util.ArrayList<>();
        scopedUserAccessMembers(saccoId, stationId)
            .forEach(member -> users.add(toUserAccessView(member)));
        users.sort(Comparator.comparing(UserAccessView::getFullName, String.CASE_INSENSITIVE_ORDER));
        return users;
    }

    public Page<UserAccessView> usersPage(String saccoId, String query, int page, int size) {
        return usersPage(saccoId, null, USER_SEARCH_BY_USER_ID, query, page, size);
    }

    public Page<UserAccessView> usersPage(String saccoId, String stationId, String query, int page, int size) {
        return usersPage(saccoId, stationId, USER_SEARCH_BY_USER_ID, query, page, size);
    }

    public Page<UserAccessView> usersPage(String saccoId, String stationId, String searchBy, String query, int page, int size) {
        String normalizedSearchBy = normalizeUserSearchBy(searchBy);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_USER_PAGE_SIZE : Math.min(size, MAX_USER_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize);
        String normalizedStationId = normalizeOptional(stationId);
        if (USER_SEARCH_BY_NAME.equals(normalizedSearchBy)) {
            return memberRepository.findUserAccessPageByName(saccoId, normalizedStationId, normalizedQuery, pageRequest)
                .map(this::toUserAccessView);
        }
        return memberRepository.findUserAccessPageByUserId(saccoId, normalizedStationId, normalizedQuery, pageRequest)
            .map(this::toUserAccessView);
    }

    public UserAccessView userAccess(String saccoId, String stationId, UUID accountId) {
        Member member = memberRepository.findUserAccessByScope(saccoId, normalizeOptional(stationId), accountId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found in this SACCO."));
        return toUserAccessView(member);
    }

    public Page<UserAccessView> saccoDetailMembersPage(String saccoId, String stationId, String query, int page, int size) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_USER_PAGE_SIZE : Math.min(size, MAX_USER_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize);
        return memberRepository.findSaccoDetailMemberPage(saccoId, normalizeOptional(stationId), normalizedQuery, pageRequest)
            .map(this::toUserAccessView);
    }

    @Transactional
    public Member createUser(String saccoId,
                             String stationId,
                             UUID adminId,
                             Set<Position> actorRoles,
                             String fullName,
                             String email,
                             String phone,
                             List<Position> positions) {
        LinkedHashSet<Position> staffRoles = validateStaffRoles(actorRoles, positions);
        return createStaffAccount(
            saccoId,
            normalizeOptional(stationId),
            adminId,
            fullName,
            email,
            phone,
            staffRoles,
            "ADMIN_CREATE_STAFF_USER"
        );
    }

    @Transactional
    public void resendMinorAdminInvitation(UUID adminId, UUID accountId) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Minor admin account not found."));
        if (!member.getStaffRolesResolved().contains(Position.MINOR_ADMIN)) {
            throw new IllegalStateException("That account is not a SACCOS Admin.");
        }
        if (member.getStatus() != MemberStatus.INVITED) {
            throw new IllegalStateException("Only accounts awaiting activation can be resent.");
        }
        ensureMinorAdminSlotAvailable(member.getSaccoId(), member.getStationId(), accountId);
        minorAdminInvitationService.issueInvitation(member, adminId);
        auditService.log("STAFF_USER", member.getId(), "ADMIN_RESEND_MINOR_ADMIN_INVITE", adminId, null, snapshotMember(member));
    }

    @Transactional
    public void deactivateMinorAdmin(UUID adminId, UUID accountId) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Minor admin account not found."));
        if (!member.getStaffRolesResolved().contains(Position.MINOR_ADMIN)) {
            throw new IllegalStateException("That account is not a SACCOS Admin.");
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalStateException("Only active accounts can be deactivated.");
        }
        Map<String, Object> before = snapshotMember(member);
        member.setStatus(MemberStatus.INACTIVE);
        memberRepository.save(member);
        auditService.log("STAFF_USER", member.getId(), "ADMIN_DEACTIVATE_MINOR_ADMIN", adminId, before, snapshotMember(member));
    }

    @Transactional
    public void reinviteMinorAdmin(UUID adminId, UUID accountId) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Minor admin account not found."));
        if (!member.getStaffRolesResolved().contains(Position.MINOR_ADMIN)) {
            throw new IllegalStateException("That account is not a SACCOS Admin.");
        }
        if (member.getStatus() == MemberStatus.ACTIVE) {
            throw new IllegalStateException("That account is already active.");
        }
        ensureMinorAdminSlotAvailable(member.getSaccoId(), member.getStationId(), accountId);
        Map<String, Object> before = snapshotMember(member);
        member.setStatus(MemberStatus.INVITED);
        member.setPasswordHash(INVITED_ACCOUNT_PASSWORD_PLACEHOLDER);
        memberRepository.save(member);
        minorAdminInvitationService.issueInvitation(member, adminId);
        auditService.log("STAFF_USER", member.getId(), "ADMIN_REINVITE_MINOR_ADMIN", adminId, before, snapshotMember(member));
    }

    @Transactional
    public void revokeMinorAdminInvitation(UUID adminId, UUID accountId) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Minor admin account not found."));
        if (!member.getStaffRolesResolved().contains(Position.MINOR_ADMIN)) {
            throw new IllegalStateException("That account is not a SACCOS Admin.");
        }
        if (member.getStatus() != MemberStatus.INVITED) {
            throw new IllegalStateException("Only pending invitations can be revoked.");
        }
        minorAdminInvitationService.revokeInvitation(member.getId(), adminId);
        member.setStatus(MemberStatus.INACTIVE);
        memberRepository.save(member);
        auditService.log("STAFF_USER", member.getId(), "ADMIN_REVOKE_MINOR_ADMIN_INVITE", adminId, null, snapshotMember(member));
    }

    @Transactional
    public void cancelStaffInvitation(String saccoId,
                                      String stationId,
                                      UUID adminId,
                                      Set<Position> actorRoles,
                                      UUID accountId) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Staff member not found."));
        String normalizedStationId = normalizeOptional(stationId);
        if (saccoId == null || member.getSaccoId() == null || !saccoId.equalsIgnoreCase(member.getSaccoId())) {
            throw new IllegalArgumentException("Staff member not found in this SACCO.");
        }
        if (normalizedStationId != null && !normalizedStationId.equalsIgnoreCase(normalizeOptional(member.getStationId()))) {
            throw new IllegalArgumentException("Staff member not found in this station.");
        }
        if (member.isMemberAccess() || member.getStaffRolesResolved().isEmpty()) {
            throw new IllegalStateException("Only staff invitations can be cancelled.");
        }
        boolean actorIsSuperAdmin = Position.containsSuperAdminRole(actorRoles);
        if (!actorIsSuperAdmin && member.getStaffRolesResolved().contains(Position.ADMIN)) {
            throw new IllegalStateException("Only super admins can cancel Super Admin invitations.");
        }
        if (member.getStatus() != MemberStatus.INVITED) {
            throw new IllegalStateException("Only pending invitations can be cancelled.");
        }

        Map<String, Object> before = snapshotMember(member);
        minorAdminInvitationService.revokeInvitation(member.getId(), adminId);
        member.setStatus(MemberStatus.INACTIVE);
        memberRepository.save(member);
        auditService.log("STAFF_USER", member.getId(), "ADMIN_CANCEL_STAFF_INVITE", adminId, before, snapshotMember(member));
    }

    @Transactional
    public Member registerMinorAdmin(UUID adminId,
                                     String saccoId,
                                     String stationId,
                                     String fullName,
                                     String email,
                                     String phone) {
        String resolvedSaccoId = saccoRegistryService.resolveRegisteredSacco(saccoId).getSaccoId();
        String resolvedStationId = saccoRegistryService.requireStationForSacco(resolvedSaccoId, stationId);
        return createStaffAccount(
            resolvedSaccoId,
            resolvedStationId,
            adminId,
            fullName,
            email,
            phone,
            new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)),
            "ADMIN_CREATE_MINOR_ADMIN"
        );
    }

    @Transactional
    public void updateMinorAdmin(UUID adminId,
                                 UUID accountId,
                                 String saccoId,
                                 String stationId,
                                 String fullName,
                                 String email,
                                 String phone) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Minor admin account not found."));
        if (!member.getStaffRolesResolved().contains(Position.MINOR_ADMIN)) {
            throw new IllegalStateException("That account is not a SACCOS Admin.");
        }

        String resolvedSaccoId = saccoRegistryService.resolveRegisteredSacco(saccoId).getSaccoId();
        String resolvedStationId = saccoRegistryService.requireStationForSacco(resolvedSaccoId, stationId);
        ensureMinorAdminSlotAvailable(resolvedSaccoId, resolvedStationId, accountId);
        String normalizedFullName = nameSignatureService.requireFullName(fullName, "Enter the user's full name.");
        String normalizedEmail = requireValue(email, "Enter the user's email address.").toLowerCase();
        String normalizedPhone = normalizeAdminPhone(phone);
        if (normalizedPhone == null) {
            throw new IllegalStateException("Enter the SACCOS Admin phone number.");
        }

        if (memberRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, accountId)) {
            throw new IllegalStateException("That email address is already in use.");
        }
        if (normalizedPhone != null && memberRepository.existsByPhoneAndIdNot(normalizedPhone, accountId)) {
            throw new IllegalStateException("That phone number is already in use.");
        }

        Map<String, Object> before = snapshotMember(member);
        OffsetDateTime now = OffsetDateTime.now();
        boolean saccoChanged = !resolvedSaccoId.equals(member.getSaccoId());
        member.setSaccoId(resolvedSaccoId);
        member.setStationId(resolvedStationId);
        member.setFullName(normalizedFullName);
        if (member.getStatus() == MemberStatus.ACTIVE || member.getSignatureRegisteredAt() != null) {
            member.setSignatureText(nameSignatureService.signatureFromFullName(normalizedFullName));
            member.setSignatureRegisteredAt(now);
        }
        member.setEmail(normalizedEmail);
        if (!java.util.Objects.equals(member.getPhone(), normalizedPhone)) {
            member.setPhoneVerifiedAt(member.getStatus() == MemberStatus.ACTIVE ? OffsetDateTime.now() : null);
        }
        member.setPhone(normalizedPhone);
        member.setPosition(Position.MINOR_ADMIN);
        member.setStaffRoles(new LinkedHashSet<>(List.of(Position.MINOR_ADMIN)));
        if (member.getStaffNo() == null || member.getStaffNo().isBlank()) {
            member.setStaffNo(generateUniqueStaffNo());
        }
        member.setStaffAccessStatus(StaffAccessStatus.ACTIVE);
        member.setStaffAccessAssignedAt(member.getStaffAccessAssignedAt() == null ? now : member.getStaffAccessAssignedAt());
        member.setStaffAccessActivatedAt(member.getStaffAccessActivatedAt() == null ? now : member.getStaffAccessActivatedAt());
        if (saccoChanged || member.getRank() == null) {
            member.setRank(nextRank(resolvedSaccoId, Position.MINOR_ADMIN));
        }
        memberRepository.save(member);
        auditService.log("STAFF_USER", member.getId(), "ADMIN_UPDATE_MINOR_ADMIN", adminId, before, snapshotMember(member));
    }

    public List<MinorAdminAccessView> minorAdmins() {
        Comparator<String> textComparator = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        OffsetDateTime now = OffsetDateTime.now();
        return memberRepository.findAllWithRole(Position.MINOR_ADMIN).stream()
            .filter(member -> member.getStaffRolesResolved().contains(Position.MINOR_ADMIN))
            .sorted(Comparator.comparing(Member::getSaccoId, textComparator)
                .thenComparing(Member::getStationId, textComparator)
                .thenComparing(Member::getFullName, textComparator))
            .map(member -> {
                MinorAdminInvitationState state = resolveInvitationState(member, now);
                return new MinorAdminAccessView(
                    member.getId(),
                    displayStaffNo(member),
                    member.getFullName(),
                    member.getEmail(),
                    member.getPhone(),
                    member.getPhoneVerifiedAt() != null,
                    member.getSaccoId(),
                    member.getStationId(),
                    member.getStatus(),
                    state.label(),
                    state.expiresAt()
                );
            })
            .toList();
    }

    private MinorAdminInvitationState resolveInvitationState(Member member, OffsetDateTime now) {
        if (member.getStatus() == MemberStatus.ACTIVE) {
            return new MinorAdminInvitationState("ACTIVE", null);
        }
        if (member.getStatus() != MemberStatus.INVITED) {
            return new MinorAdminInvitationState(member.getStatus().name(), null);
        }
        return minorAdminInvitationService.findActiveInvitation(member.getId())
            .map(invite -> {
                if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(now)) {
                    return new MinorAdminInvitationState("EXPIRED", invite.getExpiresAt());
                }
                return new MinorAdminInvitationState("INVITED", invite.getExpiresAt());
            })
            .orElse(new MinorAdminInvitationState("INVITED", null));
    }

    private record MinorAdminInvitationState(String label, OffsetDateTime expiresAt) {
    }

    @Transactional
    public UserUpdateResult updateUser(String saccoId,
                                       String stationId,
                                       UUID adminId,
                                       Set<Position> actorRoles,
                                       UUID accountId,
                                       List<Position> positions,
                                       MemberStatus status) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        List<UserClaim> defaultClaims = new ArrayList<>(userClaimService.defaultClaims(positions, member.isMemberAccess()));
        return updateUser(saccoId, stationId, adminId, actorRoles, accountId, positions, status, defaultClaims);
    }

    @Transactional
    public UserUpdateResult updateUser(String saccoId,
                                       String stationId,
                                       UUID adminId,
                                       Set<Position> actorRoles,
                                       UUID accountId,
                                       List<Position> positions,
                                       MemberStatus status,
                                       List<UserClaim> claims) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        String normalizedStationId = normalizeOptional(stationId);
        boolean sameSacco = saccoId.equals(member.getSaccoId());
        boolean sameStation = normalizedStationId == null
            || normalizedStationId.equalsIgnoreCase(normalizeOptional(member.getStationId()));
        if (normalizedStationId != null && !sameStation) {
            throw new IllegalArgumentException("Member not found in this station.");
        }
        if (!sameSacco) {
            throw new IllegalArgumentException("Member not found in this SACCO");
        }
        boolean actorIsSuperAdmin = Position.containsSuperAdminRole(actorRoles);
        if (!actorIsSuperAdmin && member.getStaffRolesResolved().contains(Position.ADMIN)) {
            throw new IllegalStateException("Only super admins can update Super Admin accounts.");
        }
        enforceUserStatusTransition(member.getStatus(), status);
        LinkedHashSet<Position> staffRoles = validateStaffRoles(actorRoles, positions);
        if (staffRoles.contains(Position.MINOR_ADMIN)) {
            String slotStationId = normalizedStationId != null ? normalizedStationId : member.getStationId();
            ensureMinorAdminSlotAvailable(saccoId, slotStationId, accountId);
        }
        if (staffRoles.contains(Position.CHAIRPERSON)) {
            ensureChairpersonSlotAvailable(saccoId, accountId);
        }
        List<UserClaim> normalizedClaims = normalizeAssignableClaims(staffRoles, member.isMemberAccess(), claims);
        boolean memberAccess = member.isMemberAccess();
        Position primaryRole = Position.primaryRole(staffRoles, memberAccess);
        if (accountId.equals(adminId) && (!Position.containsAdminRole(staffRoles) || status != MemberStatus.ACTIVE)) {
            throw new IllegalStateException("You cannot remove your own admin workspace access.");
        }

        Map<String, Object> before = snapshotMember(member);
        Position previousPosition = member.getPosition();
        OffsetDateTime now = OffsetDateTime.now();
        UserUpdateResult result = applyStaffAccessState(member, staffRoles, memberAccess, now);
        if (normalizedStationId != null) {
            member.setStationId(normalizedStationId);
        }
        member.setPosition(primaryRole);
        member.setStaffRoles(staffRoles);
        member.setMemberAccount(memberAccess);
        member.setStatus(status);
        if (previousPosition != primaryRole || member.getRank() == null) {
            member.setRank(nextRank(saccoId, primaryRole == null ? Position.MEMBER : primaryRole));
        }
        memberRepository.save(member);
        ensureUserSettings(accountId, now);
        if (memberAccess) {
            ensureSavingsAccount(accountId, now);
        }
        userClaimService.updateClaims(accountId, normalizedClaims);
        if (result.isStaffAccessPending()) {
            notifyPendingStaffAccess(member, adminId, now);
        }
        auditService.log("MEMBER", accountId, "ADMIN_UPDATE_MEMBER", adminId, before, snapshotMember(member));
        return result;
    }

    public List<LoanProductSetting> loanProducts(String saccoId) {
        return loanProductSettingRepository.findBySaccoIdOrderByLoanTypeAsc(saccoId).stream()
            .filter(product -> product.getProductStatus() != LoanProductStatus.RETIRED)
            .sorted(Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .toList();
    }

    public LoanProductSetting loanProduct(String saccoId, UUID productId) {
        LoanProductSetting product = loanProductSettingRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Loan product not found"));
        if (!saccoId.equals(product.getSaccoId()) || product.getProductStatus() == LoanProductStatus.RETIRED) {
            throw new IllegalArgumentException("Loan product not found in this SACCO");
        }
        return product;
    }

    @Transactional
    public void updateApplicantAttachmentRequired(String saccoId, UUID adminId, UUID productId, boolean required) {
        LoanProductSetting product = loanProduct(saccoId, productId);
        if (product.isApplicantAttachmentRequired() == required) {
            return;
        }
        Map<String, Object> before = snapshotProduct(product);
        saveLoanProductSnapshot(product, adminId, "BEFORE_ATTACHMENT_REQUIREMENT_UPDATE");
        product.setApplicantAttachmentRequired(required);
        product.setUpdatedAt(OffsetDateTime.now());
        loanProductSettingRepository.save(product);
        auditService.log("LOAN_PRODUCT", productId, "ADMIN_UPDATE_ATTACHMENT_REQUIREMENT", adminId, before, snapshotProduct(product));
    }

    public Map<UUID, List<LoanProductVersionView>> loanProductVersions(String saccoId) {
        List<LoanProductSetting> products = loanProducts(saccoId);
        if (products.isEmpty()) {
            return Map.of();
        }
        List<UUID> productIds = products.stream()
            .map(LoanProductSetting::getId)
            .toList();
        List<LoanProductVersion> versions = loanProductVersionRepository.findByLoanProductSettingIdInOrderByCreatedAtDesc(productIds);
        Map<UUID, String> actorNames = memberRepository.findAllById(
                versions.stream()
                    .map(LoanProductVersion::getCreatedByMemberId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        Map<UUID, List<LoanProductVersionView>> versionsByProduct = new LinkedHashMap<>();
        versions.forEach(version -> {
            if (!saccoId.equals(version.getSaccoId())) {
                return;
            }
            List<LoanProductVersionView> views = versionsByProduct.computeIfAbsent(
                version.getLoanProductSettingId(),
                ignored -> new java.util.ArrayList<>()
            );
            if (views.size() >= MAX_PRODUCT_VERSION_HISTORY) {
                return;
            }
            LoanProductSnapshot snapshot = parseLoanProductSnapshot(version.getSnapshotJson());
            views.add(toLoanProductVersionView(version, snapshot, actorNames.get(version.getCreatedByMemberId())));
        });

        products.forEach(product -> versionsByProduct.computeIfAbsent(product.getId(), ignored -> List.of()));
        return versionsByProduct;
    }

    public List<LoanProductsVersionView> loanProductsVersionHistory(String saccoId) {
        List<LoanProductsVersion> versions = loanProductsVersionRepository.findBySaccoIdOrderByCreatedAtDesc(saccoId);
        if (versions.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> actorNames = memberRepository.findAllById(
                versions.stream()
                    .map(LoanProductsVersion::getCreatedByMemberId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, Member::getFullName));

        return versions.stream()
            .limit(MAX_LOAN_PRODUCTS_VERSION_HISTORY)
            .map(version -> toLoanProductsVersionView(
                version,
                parseLoanProductsSnapshot(version.getSnapshotJson()),
                actorNames.get(version.getCreatedByMemberId())
            ))
            .toList();
    }

    public boolean customizedLoanProductExists(String saccoId) {
        return false;
    }

    public SaccoSettings settings(String saccoId) {
        return saccoSettingsRepository.findById(saccoId)
            .orElseThrow(() -> new IllegalArgumentException("SACCO settings not found"));
    }

    @Transactional
    public void updateDefaultLanguage(String saccoId, UUID adminId, String defaultLanguage) {
        SaccoSettings settings = settings(saccoId);
        Map<String, Object> before = snapshotSettings(settings);
        settings.setDefaultLanguage(normalizeDefaultLanguage(defaultLanguage));
        settings.setUpdatedAt(OffsetDateTime.now());
        saccoSettingsRepository.save(settings);
        auditService.log("SACCO_SETTINGS", null, "ADMIN_UPDATE_DEFAULT_LANGUAGE", adminId, before, snapshotSettings(settings));
    }

    @Transactional
    public void suspendStationAccess(String saccoId, String stationId, UUID adminId, String reason, LocalDate paymentDueDate) {
        String normalizedSaccoId = normalizeOptional(saccoId);
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedSaccoId == null) {
            throw new IllegalArgumentException("Select the SACCO.");
        }
        if (normalizedStationId == null) {
            throw new IllegalArgumentException("Select the station to suspend.");
        }
        SaccoStation station = saccoStationRepository.findBySaccoIdAndStationId(normalizedSaccoId, normalizedStationId)
            .orElseThrow(() -> new IllegalArgumentException("Station not found for this SACCO."));
        String normalizedReason = normalizeOptional(reason);
        if (normalizedReason == null) {
            throw new IllegalArgumentException("Enter the reason for suspending this station.");
        }
        Map<String, Object> before = snapshotStationAccess(station);
        station.setAccessStatus(SaccoAccessStatus.SUSPENDED);
        station.setPaymentDueDate(paymentDueDate);
        station.setAccessSuspendedAt(OffsetDateTime.now());
        station.setAccessSuspendedByMemberId(adminId);
        station.setAccessRestrictionReason(normalizedReason.length() > 500 ? normalizedReason.substring(0, 500) : normalizedReason);
        station.setUpdatedAt(OffsetDateTime.now());
        saccoStationRepository.save(station);
        auditService.log("SACCO_STATION", station.getId(), "PLATFORM_SUSPEND_STATION_ACCESS", adminId, before, snapshotStationAccess(station));
    }

    @Transactional
    public void restoreStationAccess(String saccoId, String stationId, UUID adminId) {
        String normalizedSaccoId = normalizeOptional(saccoId);
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedSaccoId == null) {
            throw new IllegalArgumentException("Select the SACCO.");
        }
        if (normalizedStationId == null) {
            throw new IllegalArgumentException("Select the station to restore.");
        }
        SaccoStation station = saccoStationRepository.findBySaccoIdAndStationId(normalizedSaccoId, normalizedStationId)
            .orElseThrow(() -> new IllegalArgumentException("Station not found for this SACCO."));
        Map<String, Object> before = snapshotStationAccess(station);
        station.setAccessStatus(SaccoAccessStatus.ACTIVE);
        station.setPaymentDueDate(null);
        station.setAccessSuspendedAt(null);
        station.setAccessSuspendedByMemberId(null);
        station.setAccessRestrictionReason(null);
        station.setUpdatedAt(OffsetDateTime.now());
        saccoStationRepository.save(station);
        auditService.log("SACCO_STATION", station.getId(), "PLATFORM_RESTORE_STATION_ACCESS", adminId, before, snapshotStationAccess(station));
    }

    public int activeBoardMemberCount(String saccoId) {
        return roleDirectoryService.activeByClaim(saccoId, UserClaim.BOARD_QUEUE_APPROVE).size();
    }

    public int activeCreditCommitteeMemberCount(String saccoId) {
        return roleDirectoryService.activeByClaim(saccoId, UserClaim.CREDIT_COMMITTEE_QUEUE_APPROVE).size();
    }

    public int activeChairpersonCount(String saccoId) {
        return roleDirectoryService.activeByClaim(saccoId, UserClaim.CHAIRPERSON_QUEUE_APPROVE).size();
    }

    public List<BoardReviewerOption> activeBoardReviewerOptions(String saccoId) {
        return activeReviewerOptions(saccoId, Position.BOARD);
    }

    public List<BoardReviewerOption> activeCreditCommitteeReviewerOptions(String saccoId) {
        return activeReviewerOptions(saccoId, Position.CREDIT_COMMITTEE);
    }

    private List<BoardReviewerOption> activeReviewerOptions(String saccoId, Position role) {
        UserClaim reviewerClaim = reviewerClaimForRole(role);
        return roleDirectoryService.activeByClaim(saccoId, reviewerClaim).stream()
            .map(ref -> new BoardReviewerOption(
                ref.getId(),
                ref.getFullName(),
                ref.getIdentifier(),
                ref.getStationId()
            ))
            .toList();
    }

    public Map<UUID, String> loanProductBoardReviewerIdTokens(String saccoId) {
        return loanProductReviewerIdTokens(saccoId, ApprovalWorkflowStage.BOARD);
    }

    public Map<UUID, String> loanProductCreditCommitteeReviewerIdTokens(String saccoId) {
        return loanProductReviewerIdTokens(saccoId, ApprovalWorkflowStage.CREDIT_COMMITTEE);
    }

    private Map<UUID, String> loanProductReviewerIdTokens(String saccoId, ApprovalWorkflowStage stage) {
        List<UUID> productIds = loanProducts(saccoId).stream()
            .map(LoanProductSetting::getId)
            .toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, StringBuilder> tokens = new LinkedHashMap<>();
        loanProductBoardReviewerRepository.findByLoanProductSettingIdInAndReviewStageOrderByCreatedAtAsc(productIds, stage)
            .forEach(assignment -> tokens
                .computeIfAbsent(assignment.getLoanProductSettingId(), ignored -> new StringBuilder("|"))
                .append(assignment.getBoardMemberId())
                .append("|"));
        return tokens.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString(), (left, right) -> left, LinkedHashMap::new));
    }

    public int activeLoanOfficerCount(String saccoId) {
        return roleDirectoryService.activeByAnyClaim(saccoId, List.of(
            UserClaim.LOAN_OFFICER_QUEUE_ASSIGN,
            UserClaim.LOAN_OFFICER_QUEUE_APPROVE
        )).size();
    }

    public int activeAccountantCount(String saccoId) {
        return roleDirectoryService.activeByClaim(saccoId, UserClaim.ACCOUNTANT_QUEUE_APPROVE).size();
    }

    public int activeDisbursementOfficerCount(String saccoId) {
        return roleDirectoryService.activeByClaim(saccoId, UserClaim.DISBURSEMENT_QUEUE_DISBURSE).size();
    }

    public int activeDisbursementClaimHolderCount(String saccoId) {
        return (int) roleDirectoryService.activeByClaim(saccoId, UserClaim.DISBURSEMENT_QUEUE_DISBURSE).stream()
            .filter(ref -> roleDirectoryService.hasActiveClaimInSacco(ref.getId(), saccoId, UserClaim.DISBURSEMENT_QUEUE_VIEW))
            .count();
    }

    @Transactional
    public void updateLoanProduct(String saccoId,
                                  UUID adminId,
                                  UUID productId,
                                  String productCode,
                                  String productName,
                                  String productDescription,
                                  Integer displayOrder,
                                  BigDecimal minimumAmount,
                                  BigDecimal maximumAmount,
                                  Integer guarantorsRequired,
                                  BigDecimal ratio,
                                  BigDecimal insuranceRate,
                                  BigDecimal annualRate,
                                  InterestMethod interestMethod,
                                  Integer minRepaymentMonths,
                                  Integer maxRepaymentMonths,
                                  boolean allowApplicationWithActiveLoan,
                                  boolean freshFinancialDataRequired,
                                  boolean managerReviewRequired,
                                  boolean loanOfficerReviewRequired,
                                  ApprovalWorkflowStage workflowStartStage,
                                  boolean committeeReviewRequired,
                                  Integer committeePriority,
                                  Integer committeeMinimumVotes,
                                  Integer committeeApprovalThreshold,
                                  boolean accountantReviewRequired,
                                  Integer accountantPriority,
                                  LoanProductStatus productStatus) {
        updateLoanProduct(
            saccoId,
            adminId,
            productId,
            productCode,
            productName,
            productDescription,
            displayOrder,
            minimumAmount,
            maximumAmount,
            guarantorsRequired,
            ratio,
            true,
            null,
            insuranceRate,
            null,
            annualRate,
            interestMethod,
            minRepaymentMonths,
            maxRepaymentMonths,
            allowApplicationWithActiveLoan,
            freshFinancialDataRequired,
            managerReviewRequired,
            loanOfficerReviewRequired,
            workflowStartStage,
            null,
            null,
            false,
            3,
            List.of(),
            false,
            3,
            List.of(),
            committeeReviewRequired,
            committeePriority,
            committeeMinimumVotes,
            committeeApprovalThreshold,
            List.of(),
            accountantReviewRequired,
            accountantPriority,
            true,
            true,
            false,
            false,
            null,
            productStatus
        );
    }

    @Transactional
    public void updateLoanProduct(String saccoId,
                                  UUID adminId,
                                  UUID productId,
                                  String productCode,
                                  String productName,
                                  String productDescription,
                                  Integer displayOrder,
                                  BigDecimal minimumAmount,
                                  BigDecimal maximumAmount,
                                  Integer guarantorsRequired,
                                  BigDecimal ratio,
                                  BigDecimal insuranceRate,
                                  BigDecimal annualRate,
                                  InterestMethod interestMethod,
                                  Integer minRepaymentMonths,
                                  Integer maxRepaymentMonths,
                                  boolean allowApplicationWithActiveLoan,
                                  boolean freshFinancialDataRequired,
                                  boolean managerReviewRequired,
                                  boolean loanOfficerReviewRequired,
                                  ApprovalWorkflowStage workflowStartStage,
                                  Integer managerPriority,
                                  Integer loanOfficerPriority,
                                  boolean committeeReviewRequired,
                                  Integer committeePriority,
                                  Integer committeeMinimumVotes,
                                  Integer committeeApprovalThreshold,
                                  boolean accountantReviewRequired,
                                  Integer accountantPriority,
                                  LoanProductStatus productStatus) {
        updateLoanProduct(
            saccoId,
            adminId,
            productId,
            productCode,
            productName,
            productDescription,
            displayOrder,
            minimumAmount,
            maximumAmount,
            guarantorsRequired,
            ratio,
            true,
            null,
            insuranceRate,
            null,
            annualRate,
            interestMethod,
            minRepaymentMonths,
            maxRepaymentMonths,
            allowApplicationWithActiveLoan,
            freshFinancialDataRequired,
            managerReviewRequired,
            loanOfficerReviewRequired,
            workflowStartStage,
            managerPriority,
            loanOfficerPriority,
            false,
            3,
            List.of(),
            false,
            3,
            List.of(),
            committeeReviewRequired,
            committeePriority,
            committeeMinimumVotes,
            committeeApprovalThreshold,
            List.of(),
            accountantReviewRequired,
            accountantPriority,
            true,
            true,
            false,
            false,
            null,
            productStatus
        );
    }

    @Transactional
    public void updateLoanProduct(String saccoId,
                                  UUID adminId,
                                  UUID productId,
                                  String productCode,
                                  String productName,
                                  String productDescription,
                                  Integer displayOrder,
                                  BigDecimal minimumAmount,
                                  BigDecimal maximumAmount,
                                  Integer guarantorsRequired,
                                  BigDecimal ratio,
                                  BigDecimal insuranceRate,
                                  BigDecimal annualRate,
                                  InterestMethod interestMethod,
                                  Integer minRepaymentMonths,
                                  Integer maxRepaymentMonths,
                                  boolean allowApplicationWithActiveLoan,
                                  boolean freshFinancialDataRequired,
                                  boolean managerReviewRequired,
                                  boolean loanOfficerReviewRequired,
                                  ApprovalWorkflowStage workflowStartStage,
                                  Integer managerPriority,
                                  Integer loanOfficerPriority,
                                  boolean committeeReviewRequired,
                                  Integer committeePriority,
                                  Integer committeeMinimumVotes,
                                  Integer committeeApprovalThreshold,
                                  boolean accountantReviewRequired,
                                  Integer accountantPriority,
                                  boolean disbursementOfficerRequired,
                                  boolean guarantorMinSavingsCheckRequired,
                                  BigDecimal guarantorMinimumSavings,
                                  LoanProductStatus productStatus) {
        updateLoanProduct(
            saccoId,
            adminId,
            productId,
            productCode,
            productName,
            productDescription,
            displayOrder,
            minimumAmount,
            maximumAmount,
            guarantorsRequired,
            ratio,
            true,
            null,
            insuranceRate,
            null,
            annualRate,
            interestMethod,
            minRepaymentMonths,
            maxRepaymentMonths,
            allowApplicationWithActiveLoan,
            freshFinancialDataRequired,
            managerReviewRequired,
            loanOfficerReviewRequired,
            workflowStartStage,
            managerPriority,
            loanOfficerPriority,
            false,
            3,
            List.of(),
            false,
            3,
            List.of(),
            committeeReviewRequired,
            committeePriority,
            committeeMinimumVotes,
            committeeApprovalThreshold,
            List.of(),
            accountantReviewRequired,
            accountantPriority,
            disbursementOfficerRequired,
            true,
            false,
            guarantorMinSavingsCheckRequired,
            guarantorMinimumSavings,
            productStatus
        );
    }

    @Transactional
    public void updateLoanProduct(String saccoId,
                                  UUID adminId,
                                  UUID productId,
                                  String productCode,
                                  String productName,
                                  String productDescription,
                                  Integer displayOrder,
                                  BigDecimal minimumAmount,
                                  BigDecimal maximumAmount,
                                  Integer guarantorsRequired,
                                  BigDecimal ratio,
                                  boolean savingsLimitCheckRequired,
                                  BigDecimal applicationFee,
                                  BigDecimal insuranceRate,
                                  BigDecimal processingFeeRate,
                                  BigDecimal annualRate,
                                  InterestMethod interestMethod,
                                  Integer minRepaymentMonths,
                                  Integer maxRepaymentMonths,
                                  boolean allowApplicationWithActiveLoan,
                                  boolean freshFinancialDataRequired,
                                  boolean managerReviewRequired,
                                  boolean loanOfficerReviewRequired,
                                  ApprovalWorkflowStage workflowStartStage,
                                  Integer managerPriority,
                                  Integer loanOfficerPriority,
                                  boolean chairpersonReviewRequired,
                                  Integer chairpersonPriority,
                                  List<UUID> chairpersonReviewerIds,
                                  boolean boardReviewRequired,
                                  Integer boardPriority,
                                  List<UUID> boardReviewerIds,
                                  boolean committeeReviewRequired,
                                  Integer committeePriority,
                                  Integer committeeMinimumVotes,
                                  Integer committeeApprovalThreshold,
                                  List<UUID> creditCommitteeReviewerIds,
                                  boolean accountantReviewRequired,
                                  Integer accountantPriority,
                                  boolean disbursementOfficerRequired,
                                  boolean disbursementProofRequired,
                                  boolean applicantAttachmentRequired,
                                  boolean guarantorMinSavingsCheckRequired,
                                  BigDecimal guarantorMinimumSavings,
                                  LoanProductStatus productStatus) {
        LoanProductSetting product = loanProductSettingRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Loan product not found"));
        if (!saccoId.equals(product.getSaccoId())) {
            throw new IllegalArgumentException("Loan product not found in this SACCO");
        }

        Map<String, Object> before = snapshotProduct(product);
        saveLoanProductSnapshot(product, adminId, "BEFORE_UPDATE");
        saveLoanProductsSnapshot(saccoId, adminId, "BEFORE_PRODUCT_UPDATE");
        product.setProductCode(normalizeProductCode(saccoId, product.getLoanType(), productCode, product.getId(), productName));
        product.setProductName(normalizeProductName(product.getLoanType(), productName));
        product.setProductDescription(normalizeRequiredProductDescription(productDescription));
        product.setDisplayOrder(normalizeDisplayOrder(displayOrder));
        product.setMinimumAmount(normalizeMinimumAmount(minimumAmount));
        product.setMaximumAmount(normalizeMaximumAmount(product.getMinimumAmount(), maximumAmount));
        product.setGuarantorsRequired(normalizeGuarantorCount(guarantorsRequired));
        product.setMaxLoanSavingsRatio(normalizeRatio(ratio));
        product.setSavingsLimitCheckRequired(savingsLimitCheckRequired);
        product.setApplicationFee(normalizeApplicationFee(applicationFee));
        product.setInsuranceRate(normalizeInsuranceRate(insuranceRate));
        product.setProcessingFeeRate(normalizePercentageRate(processingFeeRate, "Loan processing fee percentage cannot be negative."));
        product.setInterestRate(normalizeAnnualRate(annualRate));
        product.setInterestMethod(normalizeInterestMethod(interestMethod));
        product.setMinRepaymentMonths(normalizeMinimumRepaymentMonths(minRepaymentMonths));
        product.setMaxRepaymentMonths(normalizeMaximumRepaymentMonths(product.getMinimumRepaymentMonths(), maxRepaymentMonths));
        product.setAllowApplicationWithActiveLoan(allowApplicationWithActiveLoan);
        product.setFreshFinancialDataRequired(freshFinancialDataRequired);
        List<UUID> normalizedChairpersonReviewerIds = singleChairpersonReviewerIds(saccoId, chairpersonReviewRequired);
        List<UUID> normalizedBoardReviewerIds = normalizeReviewerIds(saccoId, boardReviewerIds, Position.BOARD);
        List<UUID> normalizedCreditCommitteeReviewerIds = normalizeReviewerIds(saccoId, creditCommitteeReviewerIds, Position.CREDIT_COMMITTEE);
        Integer assignedCreditCommitteeReviewerCount = committeeReviewRequired ? normalizedCreditCommitteeReviewerIds.size() : 0;
        WorkflowPriorityPlan priorityPlan = normalizeWorkflowPriorities(
            managerReviewRequired,
            managerPriority,
            loanOfficerReviewRequired,
            loanOfficerPriority,
            chairpersonReviewRequired,
            chairpersonPriority,
            boardReviewRequired,
            boardPriority,
            committeeReviewRequired,
            committeePriority,
            accountantReviewRequired,
            accountantPriority,
            workflowStartStage
        );
        validateWorkflowConfiguration(
            saccoId,
            managerReviewRequired,
            loanOfficerReviewRequired,
            priorityPlan.workflowStartStage(),
            priorityPlan.managerPriority(),
            priorityPlan.loanOfficerPriority(),
            chairpersonReviewRequired,
            priorityPlan.chairpersonPriority(),
            boardReviewRequired,
            priorityPlan.boardPriority(),
            committeeReviewRequired,
            priorityPlan.committeePriority(),
            assignedCreditCommitteeReviewerCount,
            assignedCreditCommitteeReviewerCount,
            accountantReviewRequired,
            priorityPlan.accountantPriority(),
            disbursementOfficerRequired
        );
        boolean normalizedManagerReviewRequired = normalizeManagerReviewRequired(managerReviewRequired);
        product.setManagerReviewRequired(normalizedManagerReviewRequired);
        product.setManagerPriority(priorityPlan.managerPriority());
        product.setLoanOfficerReviewRequired(loanOfficerReviewRequired);
        product.setLoanOfficerPriority(priorityPlan.loanOfficerPriority());
        product.setWorkflowStartStage(priorityPlan.workflowStartStage());
        product.setChairpersonReviewRequired(chairpersonReviewRequired);
        product.setChairpersonPriority(priorityPlan.chairpersonPriority());
        product.setBoardReviewRequired(boardReviewRequired);
        product.setBoardPriority(priorityPlan.boardPriority());
        product.setCommitteeReviewRequired(committeeReviewRequired);
        product.setCommitteePriority(priorityPlan.committeePriority());
        product.setCommitteeMinimumVotes(committeeReviewRequired ? assignedCreditCommitteeReviewerCount : 0);
        product.setCommitteeApprovalThreshold(committeeReviewRequired ? assignedCreditCommitteeReviewerCount : 0);
        product.setAccountantReviewRequired(accountantReviewRequired);
        product.setAccountantPriority(priorityPlan.accountantPriority());
        product.setDisbursementOfficerRequired(disbursementOfficerRequired);
        product.setDisbursementProofRequired(disbursementProofRequired);
        product.setApplicantAttachmentRequired(applicantAttachmentRequired);
        product.setGuarantorMinSavingsCheckRequired(guarantorMinSavingsCheckRequired);
        product.setGuarantorMinimumSavings(nonNegativeAmount(guarantorMinimumSavings, "Minimum guarantor savings cannot be negative."));
        validateStageReviewerConfiguration(saccoId, Position.CHAIRPERSON, normalizedChairpersonReviewerIds.size(), chairpersonReviewRequired, "chairperson");
        validateStageReviewerConfiguration(saccoId, Position.BOARD, normalizedBoardReviewerIds.size(), boardReviewRequired, "board member");
        validateStageReviewerConfiguration(saccoId, Position.CREDIT_COMMITTEE, normalizedCreditCommitteeReviewerIds.size(), committeeReviewRequired, "credit committee member");
        LoanProductStatus normalizedStatus = normalizeProductStatus(productStatus);
        product.setProductStatus(normalizedStatus);
        product.setActive(normalizedStatus == LoanProductStatus.ACTIVE);
        product.setUpdatedAt(OffsetDateTime.now());
        loanProductSettingRepository.save(product);
        replaceLoanProductReviewers(product, ApprovalWorkflowStage.CHAIRPERSON, normalizedChairpersonReviewerIds);
        replaceLoanProductReviewers(product, ApprovalWorkflowStage.BOARD, normalizedBoardReviewerIds);
        replaceLoanProductReviewers(product, ApprovalWorkflowStage.CREDIT_COMMITTEE, normalizedCreditCommitteeReviewerIds);
        auditService.log("LOAN_PRODUCT", productId, "ADMIN_UPDATE_LOAN_PRODUCT", adminId, before, snapshotProduct(product));
    }

    @Transactional
    public void archiveLoanProduct(String saccoId, UUID adminId, UUID productId) {
        LoanProductSetting product = loanProductSettingRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Loan product not found"));
        if (!saccoId.equals(product.getSaccoId())) {
            throw new IllegalArgumentException("Loan product not found in this SACCO");
        }
        Map<String, Object> before = snapshotProduct(product);
        saveLoanProductSnapshot(product, adminId, "BEFORE_ARCHIVE");
        saveLoanProductsSnapshot(saccoId, adminId, "BEFORE_PRODUCT_ARCHIVE");
        product.setProductStatus(LoanProductStatus.RETIRED);
        product.setActive(false);
        product.setUpdatedAt(OffsetDateTime.now());
        loanProductSettingRepository.save(product);
        auditService.log("LOAN_PRODUCT", productId, "ADMIN_ARCHIVE_LOAN_PRODUCT", adminId, before, snapshotProduct(product));
    }

    @Transactional
    public void rollbackLoanProductVersion(String saccoId, UUID adminId, UUID productId, UUID versionId) {
        LoanProductSetting product = loanProductSettingRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Loan product not found"));
        if (!saccoId.equals(product.getSaccoId())) {
            throw new IllegalArgumentException("Loan product not found in this SACCO");
        }

        LoanProductVersion version = loanProductVersionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("Loan product version not found."));
        if (!productId.equals(version.getLoanProductSettingId()) || !saccoId.equals(version.getSaccoId())) {
            throw new IllegalArgumentException("Loan product version not found in this SACCO.");
        }

        Map<String, Object> before = snapshotProduct(product);
        saveLoanProductSnapshot(product, adminId, "BEFORE_ROLLBACK");
        saveLoanProductsSnapshot(saccoId, adminId, "BEFORE_PRODUCT_ROLLBACK");
        LoanProductSnapshot snapshot = parseLoanProductSnapshot(version.getSnapshotJson());
        applyLoanProductSnapshot(product, snapshot);
        product.setUpdatedAt(OffsetDateTime.now());
        loanProductSettingRepository.save(product);
        replaceLoanProductReviewers(product, ApprovalWorkflowStage.CHAIRPERSON,
            snapshotChairpersonReviewerIds(snapshot));
        replaceLoanProductReviewers(product, ApprovalWorkflowStage.BOARD,
            snapshotBoardReviewerIds(snapshot));
        replaceLoanProductReviewers(product, ApprovalWorkflowStage.CREDIT_COMMITTEE,
            snapshotCreditCommitteeReviewerIds(snapshot));
        auditService.log("LOAN_PRODUCT", productId, "ADMIN_ROLLBACK_LOAN_PRODUCT", adminId, before, snapshotProduct(product));
    }

    @Transactional
    public LoanProductSetting createLoanProduct(String saccoId,
                                                UUID adminId,
                                                String productCode,
                                                String productName,
                                                String productDescription,
                                                Integer displayOrder,
                                                BigDecimal minimumAmount,
                                                BigDecimal maximumAmount,
                                                Integer guarantorsRequired,
                                                BigDecimal ratio,
                                                boolean savingsLimitCheckRequired,
                                                BigDecimal applicationFee,
                                                BigDecimal insuranceRate,
                                                BigDecimal processingFeeRate,
                                                BigDecimal annualRate,
                                                InterestMethod interestMethod,
                                                Integer minRepaymentMonths,
                                                Integer maxRepaymentMonths,
                                                boolean allowApplicationWithActiveLoan,
                                                boolean freshFinancialDataRequired,
                                                boolean managerReviewRequired,
                                                boolean loanOfficerReviewRequired,
                                                ApprovalWorkflowStage workflowStartStage,
                                                Integer managerPriority,
                                                Integer loanOfficerPriority,
                                                boolean chairpersonReviewRequired,
                                                Integer chairpersonPriority,
                                                List<UUID> chairpersonReviewerIds,
                                                boolean boardReviewRequired,
                                                Integer boardPriority,
                                                List<UUID> boardReviewerIds,
                                                boolean committeeReviewRequired,
                                                Integer committeePriority,
                                                Integer committeeMinimumVotes,
                                                Integer committeeApprovalThreshold,
                                                List<UUID> creditCommitteeReviewerIds,
                                                boolean accountantReviewRequired,
                                                Integer accountantPriority,
                                                boolean disbursementOfficerRequired,
                                                boolean disbursementProofRequired,
                                                boolean applicantAttachmentRequired,
                                                boolean guarantorMinSavingsCheckRequired,
                                                BigDecimal guarantorMinimumSavings,
                                                LoanProductStatus productStatus) {
        return createCustomizedLoanProduct(
            saccoId,
            adminId,
            productCode,
            productName,
            productDescription,
            displayOrder,
            minimumAmount,
            maximumAmount,
            guarantorsRequired,
            ratio,
            savingsLimitCheckRequired,
            applicationFee,
            insuranceRate,
            processingFeeRate,
            annualRate,
            interestMethod,
            minRepaymentMonths,
            maxRepaymentMonths,
            allowApplicationWithActiveLoan,
            freshFinancialDataRequired,
            managerReviewRequired,
            loanOfficerReviewRequired,
            workflowStartStage,
            managerPriority,
            loanOfficerPriority,
            chairpersonReviewRequired,
            chairpersonPriority,
            chairpersonReviewerIds,
            boardReviewRequired,
            boardPriority,
            boardReviewerIds,
            committeeReviewRequired,
            committeePriority,
            committeeMinimumVotes,
            committeeApprovalThreshold,
            creditCommitteeReviewerIds,
            accountantReviewRequired,
            accountantPriority,
            disbursementOfficerRequired,
            disbursementProofRequired,
            applicantAttachmentRequired,
            guarantorMinSavingsCheckRequired,
            guarantorMinimumSavings,
            productStatus
        );
    }

    @Transactional
    public void createCustomizedLoanProduct(String saccoId,
                                            UUID adminId,
                                            String productCode,
                                            String productName,
                                            String productDescription,
                                            Integer displayOrder,
                                            BigDecimal minimumAmount,
                                            BigDecimal maximumAmount,
                                            Integer guarantorsRequired,
                                            BigDecimal ratio,
                                            BigDecimal applicationFee,
                                            BigDecimal insuranceRate,
                                            BigDecimal processingFeeRate,
                                            BigDecimal annualRate,
                                            InterestMethod interestMethod,
                                            Integer minRepaymentMonths,
                                            Integer maxRepaymentMonths,
                                            boolean allowApplicationWithActiveLoan,
                                            boolean freshFinancialDataRequired,
                                            boolean managerReviewRequired,
                                            boolean loanOfficerReviewRequired,
                                            ApprovalWorkflowStage workflowStartStage,
                                            Integer managerPriority,
                                            Integer loanOfficerPriority,
                                            boolean committeeReviewRequired,
                                            Integer committeePriority,
                                            Integer committeeMinimumVotes,
                                            Integer committeeApprovalThreshold,
                                            boolean accountantReviewRequired,
                                            Integer accountantPriority,
                                            boolean disbursementOfficerRequired,
                                            boolean guarantorMinSavingsCheckRequired,
                                            BigDecimal guarantorMinimumSavings,
                                            LoanProductStatus productStatus) {
        createCustomizedLoanProduct(
            saccoId,
            adminId,
            productCode,
            productName,
            productDescription,
            displayOrder,
            minimumAmount,
            maximumAmount,
            guarantorsRequired,
            ratio,
            true,
            applicationFee,
            insuranceRate,
            processingFeeRate,
            annualRate,
            interestMethod,
            minRepaymentMonths,
            maxRepaymentMonths,
            allowApplicationWithActiveLoan,
            freshFinancialDataRequired,
            managerReviewRequired,
            loanOfficerReviewRequired,
            workflowStartStage,
            managerPriority,
            loanOfficerPriority,
            false,
            3,
            List.of(),
            false,
            3,
            List.of(),
            committeeReviewRequired,
            committeePriority,
            committeeMinimumVotes,
            committeeApprovalThreshold,
            List.of(),
            accountantReviewRequired,
            accountantPriority,
            disbursementOfficerRequired,
            true,
            false,
            guarantorMinSavingsCheckRequired,
            guarantorMinimumSavings,
            productStatus
        );
    }

    @Transactional
    public LoanProductSetting createCustomizedLoanProduct(String saccoId,
                                                          UUID adminId,
                                                          String productCode,
                                                          String productName,
                                                          String productDescription,
                                                          Integer displayOrder,
                                                          BigDecimal minimumAmount,
                                                          BigDecimal maximumAmount,
                                                          Integer guarantorsRequired,
                                                          BigDecimal ratio,
                                                          boolean savingsLimitCheckRequired,
                                                          BigDecimal applicationFee,
                                                          BigDecimal insuranceRate,
                                                          BigDecimal processingFeeRate,
                                                          BigDecimal annualRate,
                                                          InterestMethod interestMethod,
                                                          Integer minRepaymentMonths,
                                                          Integer maxRepaymentMonths,
                                                          boolean allowApplicationWithActiveLoan,
                                                          boolean freshFinancialDataRequired,
                                                          boolean managerReviewRequired,
                                                          boolean loanOfficerReviewRequired,
                                                          ApprovalWorkflowStage workflowStartStage,
                                                          Integer managerPriority,
                                                          Integer loanOfficerPriority,
                                                          boolean chairpersonReviewRequired,
                                                          Integer chairpersonPriority,
                                                          List<UUID> chairpersonReviewerIds,
                                                          boolean boardReviewRequired,
                                                          Integer boardPriority,
                                                          List<UUID> boardReviewerIds,
                                                          boolean committeeReviewRequired,
                                                          Integer committeePriority,
                                                          Integer committeeMinimumVotes,
                                                          Integer committeeApprovalThreshold,
                                                          List<UUID> creditCommitteeReviewerIds,
                                                          boolean accountantReviewRequired,
                                                          Integer accountantPriority,
                                                          boolean disbursementOfficerRequired,
                                                          boolean disbursementProofRequired,
                                                          boolean applicantAttachmentRequired,
                                                          boolean guarantorMinSavingsCheckRequired,
                                                          BigDecimal guarantorMinimumSavings,
                                                          LoanProductStatus productStatus) {
        LoanProductStatus normalizedStatus = normalizeProductStatus(productStatus);
        List<UUID> normalizedChairpersonReviewerIds = singleChairpersonReviewerIds(saccoId, chairpersonReviewRequired);
        List<UUID> normalizedBoardReviewerIds = normalizeReviewerIds(saccoId, boardReviewerIds, Position.BOARD);
        List<UUID> normalizedCreditCommitteeReviewerIds = normalizeReviewerIds(saccoId, creditCommitteeReviewerIds, Position.CREDIT_COMMITTEE);
        Integer assignedCreditCommitteeReviewerCount = committeeReviewRequired ? normalizedCreditCommitteeReviewerIds.size() : 0;
        WorkflowPriorityPlan priorityPlan = normalizeWorkflowPriorities(
            managerReviewRequired,
            managerPriority,
            loanOfficerReviewRequired,
            loanOfficerPriority,
            chairpersonReviewRequired,
            chairpersonPriority,
            boardReviewRequired,
            boardPriority,
            committeeReviewRequired,
            committeePriority,
            accountantReviewRequired,
            accountantPriority,
            workflowStartStage
        );
        validateWorkflowConfiguration(
            saccoId,
            managerReviewRequired,
            loanOfficerReviewRequired,
            priorityPlan.workflowStartStage(),
            priorityPlan.managerPriority(),
            priorityPlan.loanOfficerPriority(),
            chairpersonReviewRequired,
            priorityPlan.chairpersonPriority(),
            boardReviewRequired,
            priorityPlan.boardPriority(),
            committeeReviewRequired,
            priorityPlan.committeePriority(),
            assignedCreditCommitteeReviewerCount,
            assignedCreditCommitteeReviewerCount,
            accountantReviewRequired,
            priorityPlan.accountantPriority(),
            disbursementOfficerRequired
        );
        boolean normalizedManagerReviewRequired = normalizeManagerReviewRequired(managerReviewRequired);
        validateStageReviewerConfiguration(saccoId, Position.CHAIRPERSON, normalizedChairpersonReviewerIds.size(), chairpersonReviewRequired, "chairperson");
        validateStageReviewerConfiguration(saccoId, Position.BOARD, normalizedBoardReviewerIds.size(), boardReviewRequired, "board member");
        validateStageReviewerConfiguration(saccoId, Position.CREDIT_COMMITTEE, normalizedCreditCommitteeReviewerIds.size(), committeeReviewRequired, "credit committee member");
        saveLoanProductsSnapshot(saccoId, adminId, "BEFORE_PRODUCT_CREATE");
        LoanProductSetting product = saccoConfigurationService.createLoanProduct(
            saccoId,
            LoanType.CUSTOMIZED_LOAN,
            normalizeProductCode(saccoId, LoanType.CUSTOMIZED_LOAN, productCode, null, productName),
            normalizeProductName(LoanType.CUSTOMIZED_LOAN, productName),
            normalizeRequiredProductDescription(productDescription),
            normalizeDisplayOrder(displayOrder),
            normalizeMinimumAmount(minimumAmount),
            normalizeMaximumAmount(normalizeMinimumAmount(minimumAmount), maximumAmount),
            normalizeGuarantorCount(guarantorsRequired),
            normalizeRatio(ratio),
            savingsLimitCheckRequired,
            normalizeApplicationFee(applicationFee),
            normalizeInsuranceRate(insuranceRate),
            normalizePercentageRate(processingFeeRate, "Loan processing fee percentage cannot be negative."),
            normalizeAnnualRate(annualRate),
            normalizeInterestMethod(interestMethod),
            normalizeMinimumRepaymentMonths(minRepaymentMonths),
            normalizeMaximumRepaymentMonths(normalizeMinimumRepaymentMonths(minRepaymentMonths), maxRepaymentMonths),
            allowApplicationWithActiveLoan,
            freshFinancialDataRequired,
            normalizedManagerReviewRequired,
            loanOfficerReviewRequired,
            priorityPlan.workflowStartStage(),
            priorityPlan.managerPriority(),
            priorityPlan.loanOfficerPriority(),
            chairpersonReviewRequired,
            priorityPlan.chairpersonPriority(),
            boardReviewRequired,
            priorityPlan.boardPriority(),
            committeeReviewRequired,
            priorityPlan.committeePriority(),
            assignedCreditCommitteeReviewerCount,
            assignedCreditCommitteeReviewerCount,
            accountantReviewRequired,
            priorityPlan.accountantPriority(),
            disbursementOfficerRequired,
            disbursementProofRequired,
            applicantAttachmentRequired,
            guarantorMinSavingsCheckRequired,
            nonNegativeAmount(guarantorMinimumSavings, "Minimum guarantor savings cannot be negative."),
            normalizedStatus,
            normalizedStatus == LoanProductStatus.ACTIVE
        );
        replaceLoanProductReviewers(product, ApprovalWorkflowStage.CHAIRPERSON, normalizedChairpersonReviewerIds);
        replaceLoanProductReviewers(product, ApprovalWorkflowStage.BOARD, normalizedBoardReviewerIds);
        replaceLoanProductReviewers(product, ApprovalWorkflowStage.CREDIT_COMMITTEE, normalizedCreditCommitteeReviewerIds);
        auditService.log("LOAN_PRODUCT", product.getId(), "ADMIN_CREATE_LOAN_PRODUCT", adminId, null, snapshotProduct(product));
        return product;
    }

    @Transactional
    public void updateLoanApplicationFee(String saccoId, UUID adminId, BigDecimal applicationFee) {
        SaccoSettings settings = settings(saccoId);
        Map<String, Object> before = snapshotSettings(settings);
        saveLoanProductsSnapshot(saccoId, adminId, "BEFORE_GLOBAL_LOAN_SETTINGS_UPDATE");
        settings.setApplicationFee(normalizeApplicationFee(applicationFee));
        settings.setUpdatedAt(OffsetDateTime.now());
        saccoSettingsRepository.save(settings);
        auditService.log("SACCO_SETTINGS", null, "ADMIN_UPDATE_LOAN_APPLICATION_FEE", adminId, before, snapshotSettings(settings));
    }

    @Transactional
    public void updateQualificationPolicies(String saccoId,
                                            UUID adminId,
                                            Integer applicantMaxDefaultedLoans,
                                            boolean guarantorWithActiveLoanAllowed,
                                            BigDecimal guarantorMaxGuaranteedLoanAmount,
                                            Integer guarantorMaxDefaultedLoans) {
        SaccoSettings settings = settings(saccoId);
        Map<String, Object> before = snapshotSettings(settings);
        settings.setApplicantMaxDefaultedLoans(limitedCount(applicantMaxDefaultedLoans, "Defaulted loan limit", 0));
        settings.setGuarantorWithActiveLoanAllowed(guarantorWithActiveLoanAllowed);
        settings.setGuarantorMaxGuaranteedLoanAmount(limitedWholeNumber(guarantorMaxGuaranteedLoanAmount, "Maximum guarantee count", 0));
        settings.setGuarantorMaxDefaultedLoans(limitedCount(guarantorMaxDefaultedLoans, "Guarantor defaulted loan limit", 0));
        settings.setUpdatedAt(OffsetDateTime.now());
        saccoSettingsRepository.save(settings);
        auditService.log("SACCO_SETTINGS", null, "ADMIN_UPDATE_QUALIFICATION_POLICIES", adminId, before, snapshotSettings(settings));
    }

    @Transactional
    public void updateStationQualificationPolicies(String saccoId,
                                                   String stationId,
                                                   UUID adminId,
                                                   Integer applicantMaxDefaultedLoans,
                                                   boolean guarantorWithActiveLoanAllowed,
                                                   BigDecimal guarantorMaxGuaranteedLoanAmount,
                                                   Integer guarantorMaxDefaultedLoans) {
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedStationId == null) {
            throw new IllegalArgumentException("Choose a station before saving station policies.");
        }
        SaccoStationPolicy policy = saccoStationPolicyRepository.findBySaccoIdAndStationId(saccoId, normalizedStationId)
            .orElseGet(() -> SaccoStationPolicy.builder()
                .id(UUID.randomUUID())
                .saccoId(saccoId)
                .stationId(normalizedStationId)
                .createdAt(OffsetDateTime.now())
                .build());
        Map<String, Object> before = snapshotStationPolicy(policy);
        policy.setApplicantMaxDefaultedLoans(limitedCount(applicantMaxDefaultedLoans, "Defaulted loan limit", 0));
        policy.setGuarantorWithActiveLoanAllowed(guarantorWithActiveLoanAllowed);
        policy.setGuarantorMaxGuaranteedLoanAmount(limitedWholeNumber(guarantorMaxGuaranteedLoanAmount, "Maximum guarantee count", 0));
        policy.setGuarantorMaxDefaultedLoans(limitedCount(guarantorMaxDefaultedLoans, "Guarantor defaulted loan limit", 0));
        policy.setUpdatedAt(OffsetDateTime.now());
        saccoStationPolicyRepository.save(policy);
        auditService.log("SACCO_STATION_POLICY", policy.getId(), "ADMIN_UPDATE_STATION_QUALIFICATION_POLICIES", adminId, before, snapshotStationPolicy(policy));
    }

    public List<SaccoStationPolicy> stationQualificationPolicies(String saccoId) {
        return saccoStationPolicyRepository.findBySaccoIdOrderByStationIdAsc(saccoId);
    }

    public Optional<SaccoStationPolicy> stationQualificationPolicy(String saccoId, String stationId) {
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedStationId == null) {
            return Optional.empty();
        }
        return saccoStationPolicyRepository.findBySaccoIdAndStationId(saccoId, normalizedStationId);
    }

    @Transactional
    public void updateBoardReviewRequirement(String saccoId,
                                             UUID adminId,
                                             boolean loanOfficerReviewRequired,
                                             boolean boardReviewRequired,
                                             Integer boardQuorum) {
        SaccoSettings settings = settings(saccoId);
        int activeBoardMembers = activeBoardMemberCount(saccoId);
        int activeAccountants = activeAccountantCount(saccoId);
        int activeDisbursementClaimHolders = activeDisbursementClaimHolderCount(saccoId);
        int requiredReviewers = boardQuorum == null
            ? Math.max(settings.getBoardQuorum() == null ? 1 : settings.getBoardQuorum(), 1)
            : boardQuorum;
        if (boardReviewRequired) {
            if (requiredReviewers <= 0) {
                throw new IllegalArgumentException("Required board reviewers must be at least 1.");
            }
            if (activeBoardMembers <= 0) {
                throw new IllegalArgumentException("No active board members are configured for this SACCO yet.");
            }
            if (requiredReviewers > activeBoardMembers) {
                throw new IllegalArgumentException("Required board reviewers cannot be more than the active board members in this SACCO.");
            }
        }
        if (activeAccountants <= 0) {
            throw new IllegalArgumentException("Add at least one active Accountant before saving the approval flow.");
        }
        if (activeDisbursementClaimHolders <= 0) {
            throw new IllegalArgumentException("Grant disbursement queue and release claims to at least one active staff user before saving the approval flow.");
        }

        Map<String, Object> before = snapshotSettings(settings);
        settings.setLoanOfficerReviewRequired(loanOfficerReviewRequired);
        settings.setBoardReviewRequired(boardReviewRequired);
        settings.setBoardQuorum(requiredReviewers);
        settings.setUpdatedAt(OffsetDateTime.now());
        saccoSettingsRepository.save(settings);
        auditService.log("SACCO_SETTINGS", null, "ADMIN_UPDATE_APPROVAL_FLOW_CONFIGURATION", adminId, before, snapshotSettings(settings));
    }

    public List<NotificationViewService.NotificationView> adminMessages(UUID adminId) {
        return notificationViewService.toViews(notificationRepository.findTop200ByRecipientMemberIdAndTypeOrderByCreatedAtDesc(adminId, "SUPPORT_MESSAGE")).stream()
            .filter(view -> "SUPPORT_MESSAGE".equals(view.getType()) && view.getIncidentId() != null)
            .toList();
    }

    public Map<UUID, AdminIncident> adminMessageIncidentMap(List<NotificationViewService.NotificationView> messages) {
        return adminMessageIncidentMap(messages, null);
    }

    public Map<UUID, AdminIncident> adminMessageIncidentMap(List<NotificationViewService.NotificationView> messages,
                                                            String stationId) {
        if (messages == null || messages.isEmpty()) {
            return Map.of();
        }
        List<UUID> incidentIds = messages.stream()
            .map(NotificationViewService.NotificationView::getIncidentId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        if (incidentIds.isEmpty()) {
            return Map.of();
        }
        return filterIncidentsByStation(adminIncidentRepository.findAllById(incidentIds), stationId).stream()
            .collect(Collectors.toMap(AdminIncident::getId, incident -> incident, (left, right) -> left, LinkedHashMap::new));
    }

    public List<Member> activeMembers(String saccoId) {
        return activeMembers(saccoId, null);
    }

    public List<Member> activeMembers(String saccoId, String stationId) {
        return filterMembersByStation(
            memberRepository.findBySaccoIdAndStatusOrderByFullNameAsc(saccoId, MemberStatus.ACTIVE),
            stationId
        );
    }

    public List<AdminIncident> incidents(String saccoId, IncidentStatus status, IncidentSeverity severity) {
        return incidents(saccoId, null, status, severity);
    }

    public List<AdminIncident> incidents(String saccoId,
                                         String stationId,
                                         IncidentStatus status,
                                         IncidentSeverity severity) {
        // findRecentForReview already applies the saccoId/status/severity filters
        // (with null-safe matching) and caps the result at the most recent 250
        // rows, so an empty result genuinely means no matching incidents. The
        // previous findAll() fallback re-applied the same filters and could only
        // ever yield the same empty list, so it has been removed.
        List<AdminIncident> base = adminIncidentRepository.findRecentForReview(
            normalizeOptional(saccoId),
            status,
            severity,
            PageRequest.of(0, 250)
        );
        return filterIncidentsByStation(base, stationId);
    }

    public List<AdminIncident> platformSupportIncidents(String saccoId,
                                                        String stationId,
                                                        IncidentStatus status,
                                                        IncidentSeverity severity) {
        return incidents(saccoId, stationId, status, severity).stream()
            .filter(this::isPlatformSupportIncident)
            .toList();
    }

    public List<SupportArchiveView> platformSupportArchive(UUID reporterId) {
        if (reporterId == null) {
            return List.of();
        }
        List<AdminIncident> incidents = adminIncidentRepository.findByReportedByMemberIdOrderByCreatedAtDesc(reporterId).stream()
            .filter(this::isPlatformSupportIncident)
            .toList();
        return toSupportArchiveViews(incidents);
    }

    public List<SupportArchiveView> memberSupportArchive(UUID reporterId) {
        if (reporterId == null) {
            return List.of();
        }
        List<AdminIncident> incidents = adminIncidentRepository.findByReportedByMemberIdOrderByCreatedAtDesc(reporterId).stream()
            .filter(this::isMemberSupportIncident)
            .toList();
        return toSupportArchiveViews(incidents);
    }

    public AdminIncident incident(String saccoId, UUID incidentId) {
        return incident(saccoId, null, incidentId);
    }

    public AdminIncident incident(String saccoId, String stationId, UUID incidentId) {
        AdminIncident incident = adminIncidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        if (normalizeOptional(saccoId) != null && incident.getSaccoId() != null && !saccoId.equals(incident.getSaccoId())) {
            throw new IllegalArgumentException("Incident not found in this SACCO");
        }
        if (!filterIncidentsByStation(List.of(incident), stationId).contains(incident)) {
            throw new IllegalArgumentException("Incident not found in this station");
        }
        return incident;
    }

    public AdminIncident platformSupportIncident(String saccoId, String stationId, UUID incidentId) {
        AdminIncident incident = incident(saccoId, stationId, incidentId);
        if (!isPlatformSupportIncident(incident)) {
            throw new IllegalArgumentException("Incident not found");
        }
        return incident;
    }

    public AdminIncident memberSupportIncident(String saccoId, String stationId, UUID incidentId) {
        AdminIncident incident = incident(saccoId, stationId, incidentId);
        if (!isMemberSupportIncident(incident)) {
            throw new IllegalArgumentException("Incident not found");
        }
        return incident;
    }

    @Transactional
    public void markPlatformSupportIncidentRead(UUID incidentId, UUID adminId) {
        AdminIncident incident = platformSupportIncident(null, null, incidentId);
        markIncidentRelatedNotificationRead(incident, adminId);
    }

    @Transactional
    public void markMemberSupportIncidentRead(String saccoId, String stationId, UUID incidentId, UUID adminId) {
        AdminIncident incident = memberSupportIncident(saccoId, stationId, incidentId);
        markIncidentRelatedNotificationRead(incident, adminId);
    }

    private void markIncidentRelatedNotificationRead(AdminIncident incident, UUID adminId) {
        if (incident == null || adminId == null) {
            return;
        }
        notificationRepository.findTop200ByRecipientMemberIdAndTypeOrderByCreatedAtDesc(adminId, "SUPPORT_MESSAGE").stream()
            .filter(notification -> incident.getId().equals(notificationIncidentId(notification)))
            .filter(notification -> notification.getReadAt() == null)
            .forEach(notification -> {
                notification.setReadAt(OffsetDateTime.now());
                notificationRepository.save(notification);
            });
        if (incident.getRelatedNotificationId() == null) {
            return;
        }
        notificationRepository.findById(incident.getRelatedNotificationId())
            .filter(notification -> adminId.equals(notification.getRecipientMemberId()))
            .filter(notification -> notification.getReadAt() == null)
            .ifPresent(notification -> {
                notification.setReadAt(OffsetDateTime.now());
                notificationRepository.save(notification);
            });
    }

    @Transactional
    public void replyToPlatformSupportReporter(UUID incidentId, UUID adminId, String subject, String message) {
        AdminIncident incident = platformSupportIncident(null, null, incidentId);
        if (incident.getReportedByMemberId() == null) {
            throw new IllegalArgumentException("Incident reporter not found");
        }
        Member admin = memberRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        Member reporter = memberRepository.findById(incident.getReportedByMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Incident reporter not found"));
        createNotification(reporter.getId(), "ADMIN_REPLY", "Platform Admin Reply", subject, message,
            adminId, admin.getFullName(), Map.of("incidentId", incident.getId().toString()), OffsetDateTime.now());
        auditService.log("NOTIFICATION", reporter.getId(), "PLATFORM_ADMIN_REPLY_TO_MINOR_ADMIN", adminId, null,
            Map.of("incidentId", incident.getId(), "subject", subject, "message", message));
    }

    @Transactional
    public void replyToMemberSupportReporter(String saccoId, String stationId, UUID incidentId, UUID adminId, String subject, String message) {
        AdminIncident incident = memberSupportIncident(saccoId, stationId, incidentId);
        if (incident.getReportedByMemberId() == null) {
            throw new IllegalArgumentException("Incident reporter not found");
        }
        Member admin = memberRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        Member reporter = memberRepository.findById(incident.getReportedByMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Incident reporter not found"));
        if (!saccoId.equals(reporter.getSaccoId())) {
            throw new IllegalArgumentException("Incident reporter not found in this SACCO");
        }
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedStationId != null
            && !normalizedStationId.equalsIgnoreCase(normalizeOptional(reporter.getStationId()))) {
            throw new IllegalArgumentException("Incident reporter not found in this station");
        }
        createNotification(reporter.getId(), "ADMIN_REPLY", "Station Admin Reply", subject, message,
            adminId, admin.getFullName(),
            Map.of("incidentId", incident.getId().toString(), "recipientMemberNo", reporter.getMemberNo()),
            OffsetDateTime.now());
        auditService.log("NOTIFICATION", reporter.getId(), "ADMIN_REPLY_TO_MEMBER_SUPPORT_REPORTER", adminId, null,
            Map.of("incidentId", incident.getId(), "subject", subject, "message", message, "recipientMemberNo", reporter.getMemberNo()));
    }

    @Transactional
    public void broadcastToMinorAdmins(UUID adminId, String subject, String message) {
        Member admin = memberRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        List<RoleDirectoryService.RoleAccountRef> recipients =
            roleDirectoryService.activeRoleHoldersByClaim(Position.MINOR_ADMIN, UserClaim.NOTIFICATIONS_VIEW);
        OffsetDateTime now = OffsetDateTime.now();
        for (RoleDirectoryService.RoleAccountRef recipient : recipients) {
            createNotification(recipient.getId(), "ADMIN_BROADCAST", "Platform Admin Broadcast", subject, message,
                adminId, admin.getFullName(), Map.of("recipientMemberNo", recipient.getIdentifier()), now);
        }
        auditService.log("NOTIFICATION", null, "PLATFORM_ADMIN_BROADCAST_MINOR_ADMINS", adminId, null,
            Map.of("subject", subject, "message", message, "recipientCount", recipients.size()));
    }

    @Transactional
    public void replyToMinorAdmin(UUID adminId, UUID memberId, String subject, String message) {
        Member admin = memberRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        Member recipient = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Recipient admin not found"));
        if (!recipient.getStaffRolesResolved().contains(Position.MINOR_ADMIN)
            || !roleDirectoryService.hasActiveStaffClaim(memberId, UserClaim.NOTIFICATIONS_VIEW)) {
            throw new IllegalArgumentException("Recipient admin not found");
        }
        createNotification(memberId, "ADMIN_REPLY", "Platform Admin Reply", subject, message,
            adminId, admin.getFullName(), Map.of("recipientMemberNo", recipient.getMemberNo()), OffsetDateTime.now());
        auditService.log("NOTIFICATION", memberId, "PLATFORM_ADMIN_REPLY_TO_MINOR_ADMIN", adminId, null,
            Map.of("subject", subject, "message", message, "recipientMemberNo", recipient.getMemberNo()));
    }

    @Transactional
    public void updateIncident(String saccoId, UUID adminId, UUID incidentId, IncidentStatus status, String resolutionNote) {
        updateIncident(saccoId, null, adminId, incidentId, status, resolutionNote);
    }

    @Transactional
    public void updateIncident(String saccoId, String stationId, UUID adminId, UUID incidentId,
                               IncidentStatus status, String resolutionNote) {
        AdminIncident incident = incident(saccoId, stationId, incidentId);
        Map<String, Object> before = snapshotIncident(incident);
        incident.setStatus(status);
        incident.setResolutionNote(resolutionNote == null ? null : resolutionNote.trim());
        incident.setUpdatedAt(OffsetDateTime.now());
        if (status == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(OffsetDateTime.now());
            incident.setResolvedByMemberId(adminId);
        } else {
            incident.setResolvedAt(null);
            incident.setResolvedByMemberId(null);
        }
        adminIncidentRepository.save(incident);
        auditService.log("ADMIN_INCIDENT", incidentId, "ADMIN_UPDATE_INCIDENT", adminId, before, snapshotIncident(incident));
    }

    @Transactional
    public void broadcast(String saccoId, UUID adminId, String subject, String message) {
        broadcast(saccoId, null, adminId, subject, message);
    }

    @Transactional
    public void broadcast(String saccoId, String stationId, UUID adminId, String subject, String message) {
        Member admin = memberRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        List<Member> recipients = activeMembers(saccoId, stationId);
        OffsetDateTime now = OffsetDateTime.now();
        for (Member recipient : recipients) {
            createNotification(recipient.getId(), "ADMIN_BROADCAST", "Admin Broadcast", subject, message,
                adminId, admin.getFullName(), Map.of("recipientMemberNo", recipient.getMemberNo()), now);
        }
        auditService.log("NOTIFICATION", null, "ADMIN_BROADCAST", adminId, null,
            Map.of("subject", subject, "message", message, "recipientCount", recipients.size()));
    }

    @Transactional
    public void replyToMember(String saccoId, UUID adminId, UUID memberId, String subject, String message) {
        replyToMember(saccoId, null, adminId, memberId, subject, message);
    }

    @Transactional
    public void replyToMember(String saccoId, String stationId, UUID adminId, UUID memberId, String subject, String message) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Recipient member not found"));
        Member admin = memberRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        if (!saccoId.equals(member.getSaccoId())) {
            throw new IllegalArgumentException("Recipient member not found in this SACCO");
        }
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedStationId != null
            && !normalizedStationId.equalsIgnoreCase(normalizeOptional(member.getStationId()))) {
            throw new IllegalArgumentException("Recipient member not found in this station");
        }

        createNotification(memberId, "ADMIN_REPLY", "Admin Reply", subject, message, adminId, admin.getFullName(),
            Map.of("recipientMemberNo", member.getMemberNo()), OffsetDateTime.now());
        auditService.log("NOTIFICATION", memberId, "ADMIN_REPLY_TO_MEMBER", adminId, null,
            Map.of("subject", subject, "message", message, "recipientMemberNo", member.getMemberNo()));
    }

    @Transactional
    public void submitSupport(String saccoId, UUID memberId, String subject, String message) {
        Member sender = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        if (!saccoId.equals(sender.getSaccoId())) {
            throw new IllegalArgumentException("Member not found in this SACCO");
        }
        String normalizedStationId = normalizeOptional(sender.getStationId());
        List<RoleDirectoryService.RoleAccountRef> admins = roleDirectoryService.activeWorkspaceAdminsByAnyClaimInStation(
            saccoId,
            normalizedStationId,
            List.of(UserClaim.SUPPORT_VIEW, UserClaim.SUPPORT_UPDATE)
        );
        if (admins.isEmpty()) {
            throw new IllegalStateException("No active SACCOS Admin is configured for your station");
        }
        adminAlertService.openSupportIncidentForAdmins(
            admins,
            saccoId,
            memberId,
            "Member Support",
            subject,
            message,
            IncidentSeverity.MEDIUM,
            Map.of(
                "memberNo", sender.getMemberNo(),
                "senderName", sender.getFullName(),
                "stationId", normalizedStationId == null ? "" : normalizedStationId
            )
        );
        auditService.log("SUPPORT", memberId, "MEMBER_SUPPORT_MESSAGE", memberId, null,
            Map.of("subject", subject, "message", message, "adminCount", admins.size()));
    }

    @Transactional
    public void submitPlatformSupport(String saccoId, String stationId, UUID memberId, String subject, String message) {
        Member sender = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        if (!saccoId.equals(sender.getSaccoId())) {
            throw new IllegalArgumentException("Admin not found in this SACCO");
        }
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedStationId != null && !normalizedStationId.equalsIgnoreCase(normalizeOptional(sender.getStationId()))) {
            throw new IllegalArgumentException("Admin not found in this station");
        }
        adminAlertService.openPlatformSupportIncident(
            saccoId,
            memberId,
            "Workspace Admin Support",
            subject,
            message,
            IncidentSeverity.MEDIUM,
            Map.of(
                "memberNo", sender.getMemberNo(),
                "senderName", sender.getFullName(),
                "stationId", normalizedStationId == null ? "" : normalizedStationId
            )
        );
        auditService.log("SUPPORT", memberId, "WORKSPACE_ADMIN_SUPPORT_MESSAGE", memberId, null,
            Map.of("subject", subject, "message", message, "saccoId", saccoId, "stationId", normalizedStationId == null ? "" : normalizedStationId));
    }

    public Page<OutboxEvent> outboxEvents(int page, int size, OutboxStatus status, String dateFrom, String dateTo, String loanApplicationId) {
        return outboxEvents(page, size, status, dateFrom, dateTo, loanApplicationId, null, null);
    }

    public Page<OutboxEvent> outboxEvents(int page,
                                          int size,
                                          OutboxStatus status,
                                          String dateFrom,
                                          String dateTo,
                                          String loanApplicationId,
                                          String saccoId,
                                          String stationId) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizePageSize(size));
        String normalizedLoanApplicationId = normalizeOptional(loanApplicationId);
        String normalizedSaccoId = normalizeOptional(saccoId);
        String normalizedStationId = normalizeOptional(stationId);
        DateRange dateRange = resolveDateRange(dateFrom, dateTo);
        if (normalizedSaccoId != null || normalizedStationId != null) {
            return outboxEventRepository.searchMonitorViewScoped(
                status == null ? null : status.name(),
                dateRange.start(),
                dateRange.endExclusive(),
                normalizedLoanApplicationId,
                normalizedSaccoId,
                normalizedStationId,
                pageRequest
            );
        }
        return outboxEventRepository.searchMonitorView(
            status == null ? null : status.name(),
            dateRange.start(),
            dateRange.endExclusive(),
            normalizedLoanApplicationId,
            pageRequest
        );
    }

    public Map<UUID, String> outboxActorPrefixes(List<OutboxEvent> events) {
        Map<UUID, String> actorPrefixes = new LinkedHashMap<>();
        if (events == null || events.isEmpty()) {
            return actorPrefixes;
        }
        for (OutboxEvent event : events) {
            if (event == null || event.getId() == null) {
                continue;
            }
            actorPrefixes.put(event.getId(), outboxActorPrefix(event));
        }
        return actorPrefixes;
    }

    public Map<UUID, String> outboxActorNames(List<OutboxEvent> events) {
        Map<UUID, UUID> actorIdsByEventId = new LinkedHashMap<>();
        if (events == null || events.isEmpty()) {
            return Map.of();
        }
        for (OutboxEvent event : events) {
            if (event == null || event.getId() == null) {
                continue;
            }
            UUID actorId = parseUuid(outboxActorValue(event));
            if (actorId != null) {
                actorIdsByEventId.put(event.getId(), actorId);
            }
        }
        if (actorIdsByEventId.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> actorNamesByActorId = memberRepository.findAllById(new LinkedHashSet<>(actorIdsByEventId.values())).stream()
            .filter(member -> member.getFullName() != null && !member.getFullName().isBlank())
            .collect(Collectors.toMap(
                Member::getId,
                Member::getFullName,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<UUID, String> actorNamesByEventId = new LinkedHashMap<>();
        actorIdsByEventId.forEach((eventId, actorId) -> {
            String actorName = actorNamesByActorId.get(actorId);
            if (actorName != null) {
                actorNamesByEventId.put(eventId, actorName);
            }
        });
        return actorNamesByEventId;
    }

    private String outboxActorPrefix(OutboxEvent event) {
        return formatUserIdPrefix(outboxActorValue(event));
    }

    private String outboxActorValue(OutboxEvent event) {
        if (event == null) {
            return null;
        }
        Map<String, Object> payload = readOutboxPayload(event.getPayload());
        return firstOutboxValue(
            outboxPayloadValue(payload, "actorId"),
            outboxDetailsValue(payload, "actorId"),
            outboxDetailsValue(payload, "managerId"),
            outboxDetailsValue(payload, "applicantMemberId"),
            outboxDetailsValue(payload, "requesterMemberId")
        );
    }

    private Map<String, Object> readOutboxPayload(String payload) {
        String normalized = normalizeOptional(payload);
        if (normalized == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(normalized, new TypeReference<Map<String, Object>>() {});
        } catch (JacksonException ex) {
            return Map.of();
        }
    }

    private String outboxPayloadValue(Map<String, Object> payload, String key) {
        return payloadValueToString(payload == null ? null : payload.get(key));
    }

    private String outboxDetailsValue(Map<String, Object> payload, String key) {
        if (payload == null || !(payload.get("details") instanceof Map<?, ?> details)) {
            return null;
        }
        return payloadValueToString(details.get(key));
    }

    private String payloadValueToString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = normalizeOptional(String.valueOf(value));
        if (normalized == null || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String firstOutboxValue(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String formatUserIdPrefix(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return "-";
        }
        return normalized.substring(0, Math.min(8, normalized.length()));
    }

    private UUID parseUuid(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Transactional
    public void retryOutbox(UUID adminId, UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Outbox event not found"));
        Map<String, Object> before = Map.of(
            "status", event.getStatus(),
            "eventType", event.getEventType(),
            "aggregateId", event.getAggregateId()
        );
        event.setStatus(OutboxStatus.NEW);
        event.setPublishedAt(null);
        outboxEventRepository.save(event);
        auditService.log("OUTBOX", eventId, "ADMIN_RETRY_OUTBOX", adminId, before,
            Map.of("status", event.getStatus(), "eventType", event.getEventType()));
    }

    public Page<AuditLog> auditEntries(int page, int size, String dateFrom, String dateTo, String actorId) {
        return auditEntries(page, size, dateFrom, dateTo, actorId, null, null);
    }

    public Page<AuditLog> auditEntries(int page,
                                       int size,
                                       String dateFrom,
                                       String dateTo,
                                       String actorId,
                                       String saccoId,
                                       String stationId) {
        DateRange dateRange = resolveDateRange(dateFrom, dateTo);
        String normalizedActorId = normalizeOptional(actorId);
        String normalizedSaccoId = normalizeOptional(saccoId);
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedSaccoId != null || normalizedStationId != null) {
            return auditLogRepository.searchEventLogViewScoped(
                dateRange.start(),
                dateRange.endExclusive(),
                normalizedActorId,
                normalizedSaccoId,
                normalizedStationId,
                PageRequest.of(normalizePage(page), normalizePageSize(size))
            );
        }
        return auditLogRepository.searchEventLogView(
            dateRange.start(),
            dateRange.endExclusive(),
            normalizedActorId,
            PageRequest.of(normalizePage(page), normalizePageSize(size))
        );
    }

    public Page<AuditLog> eventEntries(int page, int size, String dateFrom, String dateTo, String actorId) {
        return auditEntries(page, size, dateFrom, dateTo, actorId);
    }

    public Page<AuditLog> eventEntries(int page,
                                       int size,
                                       String dateFrom,
                                       String dateTo,
                                       String actorId,
                                       String saccoId,
                                       String stationId) {
        return auditEntries(page, size, dateFrom, dateTo, actorId, saccoId, stationId);
    }

    public Map<String, String> actorNamesForEvents(List<AuditLog> entries) {
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<UUID> actorIds = entries.stream()
            .map(AuditLog::getActorMemberId)
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(actorIds).stream()
            .filter(member -> member.getFullName() != null && !member.getFullName().isBlank())
            .collect(Collectors.toMap(
                member -> member.getId().toString(),
                Member::getFullName,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    public ReportData reports(String saccoId, String statusFilter, String loanTypeFilter, String dateFrom, String dateTo) {
        DateRange range = resolveDateRange(dateFrom, dateTo);
        LoanStatus statusEnum = parseEnumFilter(statusFilter, LoanStatus.class);
        LoanType loanTypeEnum = parseEnumFilter(loanTypeFilter, LoanType.class);

        // A non-blank filter that does not match any known enum value can never
        // match a row, so short-circuit to an empty report instead of querying.
        boolean impossibleFilter = (normalizeOptional(statusFilter) != null && statusEnum == null)
            || (normalizeOptional(loanTypeFilter) != null && loanTypeEnum == null);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byType = new LinkedHashMap<>();
        long pendingGuarantorRequests = 0;
        long managerDecisionCount = 0;
        if (!impossibleFilter) {
            loanApplicationRepository.reportStatusCounts(saccoId, statusEnum, loanTypeEnum, range.start(), range.endExclusive())
                .forEach(projection -> byStatus.put(projection.getStatus().name(), projection.getTotal()));
            loanApplicationRepository.reportTypeCounts(saccoId, statusEnum, loanTypeEnum, range.start(), range.endExclusive())
                .forEach(projection -> byType.put(projection.getLoanType().name(), projection.getTotal()));
            pendingGuarantorRequests = guarantorRequestRepository.countPendingForReport(
                saccoId, statusEnum, loanTypeEnum, range.start(), range.endExclusive());
            managerDecisionCount = managerReviewRepository.countDecisionsForReport(
                saccoId, statusEnum, loanTypeEnum, range.start(), range.endExclusive());
        }

        long rejectedByManager = byStatus.getOrDefault(LoanStatus.MANAGER_REJECTED.name(), 0L);
        long approvedFinal = byStatus.getOrDefault(LoanStatus.DISBURSED.name(), 0L);
        long totalApplications = byStatus.values().stream().mapToLong(Long::longValue).sum();

        return new ReportData(
            byStatus,
            byType,
            pendingGuarantorRequests,
            rejectedByManager,
            approvedFinal,
            managerDecisionCount,
            toChartItems(byStatus),
            toChartItems(byType),
            statusFilter == null ? "" : statusFilter,
            loanTypeFilter == null ? "" : loanTypeFilter,
            dateFrom == null ? "" : dateFrom,
            dateTo == null ? "" : dateTo,
            (int) totalApplications
        );
    }

    private <E extends Enum<E>> E parseEnumFilter(String raw, Class<E> type) {
        String normalized = normalizeOptional(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void createNotification(UUID recipientId, String type, String source, String subject, String message,
                                    UUID senderId, String senderName, Map<String, Object> details, OffsetDateTime now) {
        notificationRepository.save(Notification.builder()
            .id(UUID.randomUUID())
            .recipientMemberId(recipientId)
            .type(type)
            .payload(adminAlertService.buildPayload(source, subject, message, senderId, senderName, details))
            .status(NotificationStatus.SENT)
            .createdAt(now)
            .sentAt(now)
            .build());
    }

    private Map<String, Object> snapshotMember(Member member) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("saccoId", member.getSaccoId());
        data.put("memberNo", member.getMemberNo());
        data.put("staffNo", member.getStaffNo());
        data.put("fullName", member.getFullName());
        data.put("stationId", member.getStationId());
        data.put("position", member.getPosition());
        data.put("staffRoles", member.getStaffRolesResolved());
        data.put("staffAccessStatus", member.getStaffAccessStatus());
        data.put("memberAccount", member.isMemberAccess());
        data.put("status", member.getStatus());
        return data;
    }

    private LinkedHashSet<Position> validateStaffRoles(Set<Position> actorRoles, List<Position> positions) {
        LinkedHashSet<Position> staffRoles = Position.normalizeStaffRoles(positions);
        if (staffRoles.isEmpty()) {
            throw new IllegalStateException("Select at least one staff role for the user.");
        }
        if ((positions != null) && positions.stream().anyMatch(position -> position == Position.MEMBER)) {
            throw new IllegalStateException("Use member registration for member-only accounts. Admin-created users must use a staff role.");
        }
        boolean actorIsSuperAdmin = Position.containsSuperAdminRole(actorRoles);
        if (staffRoles.contains(Position.ADMIN) && !actorIsSuperAdmin) {
            throw new IllegalStateException("Only super admins can assign Super Admin access.");
        }
        if (staffRoles.contains(Position.ADMIN) && staffRoles.contains(Position.MINOR_ADMIN)) {
            throw new IllegalStateException("Choose either Super Admin or SACCOS Admin, not both.");
        }
        if (staffRoles.contains(Position.ADMIN) && staffRoles.size() > 1) {
            throw new IllegalStateException("Super Admin accounts cannot be combined with any other staff role.");
        }
        return staffRoles;
    }

    private List<UserClaim> normalizeAssignableClaims(Set<Position> staffRoles,
                                                      boolean memberAccess,
        List<UserClaim> requestedClaims) {
        LinkedHashSet<UserClaim> normalized = new LinkedHashSet<>();
        if (requestedClaims != null) {
            requestedClaims.stream()
                .filter(java.util.Objects::nonNull)
                .forEach(normalized::add);
        } else {
            normalized.addAll(userClaimService.defaultClaims(staffRoles, memberAccess));
        }
        if (!memberAccess) {
            normalized.removeIf(claim -> claim.getFeature() == AccessFeature.MEMBER_LOANS
                || claim.getFeature() == AccessFeature.GUARANTOR_REQUESTS);
        }
        if (normalized.contains(UserClaim.DISBURSEMENT_QUEUE_DISBURSE)) {
            normalized.add(UserClaim.DISBURSEMENT_QUEUE_VIEW);
        }
        List<UserClaim> impliedViewClaims = normalized.stream()
            .filter(claim -> claim.getAction() != AccessAction.VIEW)
            .map(UserClaim::getFeature)
            .distinct()
            .map(feature -> UserClaim.forFeatureAction(feature, AccessAction.VIEW))
            .flatMap(java.util.Optional::stream)
            .toList();
        normalized.addAll(impliedViewClaims);
        return new ArrayList<>(normalized);
    }

    private String formatRoleSummary(Set<Position> staffRoles, boolean memberAccess) {
        if (memberAccess && (staffRoles == null || staffRoles.isEmpty())) {
            return "MEMBER";
        }
        return Position.normalizeStaffRoles(staffRoles).stream()
            .map(Position::getDisplayName)
            .collect(Collectors.joining(", "));
    }

    private String resolveMembershipLabel(Member member) {
        if (!member.isMemberAccess()) {
            return "Staff";
        }
        return member.getStaffRolesResolved().isEmpty() ? "Member" : "Staff And Member";
    }

    private List<Member> filterMembersByStation(List<Member> members, String stationId) {
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedStationId == null) {
            return members;
        }
        return members.stream()
            .filter(member -> normalizedStationId.equalsIgnoreCase(normalizeOptional(member.getStationId())))
            .toList();
    }

    private List<Member> scopedUserAccessMembers(String saccoId, String stationId) {
        List<Member> saccoMembers = memberRepository.findBySaccoIdOrderByFullNameAsc(saccoId);
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedStationId == null) {
            return saccoMembers;
        }

        List<Member> stationMembers = filterMembersByStation(saccoMembers, normalizedStationId);
        List<Member> stationWideMatches = memberRepository.findBySaccoIdAndStationIdIgnoreCaseOrderByFullNameAsc(saccoId, normalizedStationId);
        if (stationWideMatches.isEmpty()) {
            return stationMembers;
        }

        Map<UUID, Member> mergedById = new LinkedHashMap<>();
        stationMembers.forEach(member -> mergedById.put(member.getId(), member));
        stationWideMatches.forEach(member -> mergedById.putIfAbsent(member.getId(), member));
        return mergedById.values().stream()
            .sorted(Comparator.comparing(Member::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }

    private List<AdminIncident> filterIncidentsByStation(List<AdminIncident> incidents, String stationId) {
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedStationId == null || incidents == null || incidents.isEmpty()) {
            return incidents;
        }
        Map<UUID, String> stationByReporterId = memberRepository.findAllById(
                incidents.stream()
                    .map(AdminIncident::getReportedByMemberId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> normalizeOptional(member.getStationId()), (left, right) -> left));
        return incidents.stream()
            .filter(incident -> incident.getReportedByMemberId() == null
                || normalizedStationId.equalsIgnoreCase(stationByReporterId.get(incident.getReportedByMemberId())))
            .toList();
    }

    private boolean isPlatformSupportIncident(AdminIncident incident) {
        return incident != null
            && "SUPPORT_MESSAGE".equals(incident.getCategory())
            && "Workspace Admin Support".equals(incident.getSource());
    }

    private boolean isMemberSupportIncident(AdminIncident incident) {
        return incident != null
            && "SUPPORT_MESSAGE".equals(incident.getCategory())
            && "Member Support".equals(incident.getSource());
    }

    private List<SupportArchiveView> toSupportArchiveViews(List<AdminIncident> incidents) {
        if (incidents == null || incidents.isEmpty()) {
            return List.of();
        }
        Set<UUID> incidentIds = incidents.stream().map(AdminIncident::getId).collect(Collectors.toSet());
        Set<UUID> readIncidentIds = new java.util.HashSet<>(notificationRepository.findReadSupportIncidentIds(incidentIds));
        return incidents.stream()
            .map(incident -> new SupportArchiveView(
                incident.getId(),
                incident.getSubject(),
                incident.getMessage(),
                incident.getStatus(),
                incident.getCreatedAt(),
                readIncidentIds.contains(incident.getId())
            ))
            .toList();
    }

    private UUID notificationIncidentId(Notification notification) {
        if (notification == null || notification.getPayload() == null || notification.getPayload().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(notification.getPayload(), new TypeReference<Map<String, Object>>() {});
            Object detailsValue = payload.get("details");
            if (!(detailsValue instanceof Map<?, ?> details)) {
                return null;
            }
            Object incidentId = details.get("incidentId");
            return incidentId == null ? null : UUID.fromString(String.valueOf(incidentId));
        } catch (Exception ex) {
            return null;
        }
    }

    private UserAccessView toUserAccessView(Member member) {
        boolean memberAccess = member.isMemberAccess();
        return UserAccessView.builder()
            .accountId(member.getId())
            .userIdLabel(formatUserId(member.getId()))
            .loginId(member.isStaffAccessActive() ? displayStaffNo(member) : member.getMemberNo())
            .memberNumber(memberAccess ? displayOrDash(member.getMemberNo()) : "-")
            .staffMemberNumber(displayStaffNo(member))
            .fullName(member.getFullName())
            .email(member.getEmail())
            .phone(displayOrDash(member.getPhone()))
            .roleSummary(formatRoleSummary(member.getStaffRolesResolved(), memberAccess))
            .staffRoles(member.getStaffRolesResolved())
            .claims(userClaimService.effectiveClaims(member.getId(), member.getStaffRolesResolved(), memberAccess))
            .status(member.getStatus())
            .displayStatus(displayUserStatus(member.getStatus()))
            .membershipLabel(resolveMembershipLabel(member))
            .memberAccess(memberAccess)
            .canDeleteStaffRecord(canDeleteStaffRecord(member))
            .build();
    }

    private boolean canDeleteStaffRecord(Member member) {
        return member != null
            && member.getStatus() == MemberStatus.INACTIVE
            && !member.isMemberAccess()
            && !member.getStaffRolesResolved().isEmpty()
            && INVITED_ACCOUNT_PASSWORD_PLACEHOLDER.equals(member.getPasswordHash());
    }

    private String normalizeUserSearchBy(String searchBy) {
        if (searchBy != null && USER_SEARCH_BY_NAME.equalsIgnoreCase(searchBy.trim())) {
            return USER_SEARCH_BY_NAME;
        }
        return USER_SEARCH_BY_USER_ID;
    }

    private void enforceUserStatusTransition(MemberStatus currentStatus, MemberStatus requestedStatus) {
        MemberStatus current = currentStatus == null ? MemberStatus.INACTIVE : currentStatus;
        MemberStatus requested = requestedStatus == null ? current : requestedStatus;
        if (current == MemberStatus.INVITED && requested != MemberStatus.INVITED) {
            throw new IllegalStateException("Invited staff accounts become active only after the invite form is completed.");
        }
        if (current != MemberStatus.INVITED && requested == MemberStatus.INVITED) {
            throw new IllegalStateException("Staff accounts cannot be manually moved back to invited status.");
        }
    }

    private String formatUserId(UUID accountId) {
        if (accountId == null) {
            return "-";
        }
        String value = accountId.toString();
        return value.substring(0, Math.min(8, value.length()));
    }

    private String displayUserStatus(MemberStatus status) {
        if (status == MemberStatus.INVITED) {
            return "Invite email sent - waiting";
        }
        return status == null ? "-" : status.name();
    }

    private String displayOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String displayStaffNo(Member member) {
        if (member == null) {
            return "-";
        }
        if (member.getStaffNo() != null && !member.getStaffNo().isBlank()) {
            return member.getStaffNo();
        }
        if (!member.isMemberAccess() || !member.getStaffRolesResolved().isEmpty()) {
            return displayOrDash(member.getMemberNo());
        }
        return "-";
    }

    private String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private int nextRank(String saccoId, Position position) {
        return memberRepository.findTopBySaccoIdAndPositionOrderByRankDesc(saccoId, position)
            .map(Member::getRank)
            .orElse(0) + 1;
    }

    private void ensureMinorAdminSlotAvailable(String saccoId, String stationId, UUID existingAccountId) {
        String normalizedStationId = normalizeOptional(stationId);
        if (normalizedStationId == null) {
            throw new IllegalStateException("Select a station ID.");
        }
        boolean occupied = existingAccountId == null
            ? memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPosition(saccoId, normalizedStationId, Position.MINOR_ADMIN)
            : memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot(saccoId, normalizedStationId, Position.MINOR_ADMIN, existingAccountId);
        if (occupied) {
            throw new IllegalStateException("Each SACCO station can only have one SACCOS Admin account. Update the existing one instead.");
        }
    }

    private void ensureChairpersonSlotAvailable(String saccoId, UUID existingAccountId) {
        long existingChairpersons = existingAccountId == null
            ? memberRepository.countBySaccoIdAndRole(saccoId, Position.CHAIRPERSON)
            : memberRepository.countBySaccoIdAndRoleAndIdNot(saccoId, Position.CHAIRPERSON, existingAccountId);
        if (existingChairpersons > 0) {
            throw new IllegalStateException("Each SACCO can only have one Chairperson account. Update the existing one instead.");
        }
    }

    private UserUpdateResult applyStaffAccessState(Member member,
                                                   LinkedHashSet<Position> staffRoles,
                                                   boolean memberAccess,
                                                   OffsetDateTime now) {
        if (staffRoles == null || staffRoles.isEmpty()) {
            member.setStaffAccessStatus(StaffAccessStatus.NONE);
            member.setStaffAccessAssignedAt(null);
            member.setStaffAccessActivatedAt(null);
            return UserUpdateResult.noPending(member.getStaffNo());
        }

        if (member.getStaffNo() == null || member.getStaffNo().isBlank()) {
            member.setStaffNo(generateUniqueStaffNo());
        }

        if (!memberAccess) {
            member.setStaffAccessStatus(StaffAccessStatus.ACTIVE);
            member.setStaffAccessAssignedAt(member.getStaffAccessAssignedAt() == null ? now : member.getStaffAccessAssignedAt());
            member.setStaffAccessActivatedAt(member.getStaffAccessActivatedAt() == null ? now : member.getStaffAccessActivatedAt());
            return UserUpdateResult.noPending(member.getStaffNo());
        }

        StaffAccessStatus currentStatus = member.getStaffAccessStatus() == null
            ? StaffAccessStatus.NONE
            : member.getStaffAccessStatus();
        if (currentStatus == StaffAccessStatus.ACTIVE) {
            return UserUpdateResult.noPending(member.getStaffNo());
        }

        boolean newlyPending = currentStatus != StaffAccessStatus.PENDING_ACKNOWLEDGEMENT;
        member.setStaffAccessStatus(StaffAccessStatus.PENDING_ACKNOWLEDGEMENT);
        member.setStaffAccessAssignedAt(member.getStaffAccessAssignedAt() == null ? now : member.getStaffAccessAssignedAt());
        member.setStaffAccessActivatedAt(null);
        return new UserUpdateResult(newlyPending, member.getStaffNo());
    }

    private void notifyPendingStaffAccess(Member member, UUID adminId, OffsetDateTime now) {
        String staffNo = displayStaffNo(member);
        String subject = "Staff access assigned";
        String message = "Your SACCO staff access is ready. Confirm it from your member dashboard. Staff Number: "
            + staffNo + ".";
        String senderName = adminId == null ? "Administrator" : memberRepository.findById(adminId)
            .map(Member::getFullName)
            .orElse("Administrator");
        createNotification(member.getId(), "STAFF_ACCESS_PENDING", "Staff Access", subject, message,
            adminId, senderName, Map.of("staffNo", staffNo), now);
    }

    private Member createStaffAccount(String saccoId,
                                      String stationId,
                                      UUID adminId,
                                      String fullName,
                                      String email,
                                      String phone,
                                      LinkedHashSet<Position> staffRoles,
                                      String auditAction) {
        Position primaryRole = Position.primaryRole(staffRoles, false);
        String normalizedStationId = normalizeOptional(stationId);
        if (staffRoles.contains(Position.MINOR_ADMIN)) {
            ensureMinorAdminSlotAvailable(saccoId, normalizedStationId, null);
        }
        if (staffRoles.contains(Position.CHAIRPERSON)) {
            ensureChairpersonSlotAvailable(saccoId, null);
        }
        String normalizedFullName = nameSignatureService.requireFullName(fullName, "Enter the user's full name.");
        String normalizedEmail = requireValue(email, "Enter the user's email address.").toLowerCase();
        String normalizedPhone = normalizeAdminPhone(phone);
        if (normalizedPhone == null) {
            throw new IllegalStateException("Enter the staff member phone number.");
        }
        ensureStaffIsNotExternalMember(normalizedEmail, normalizedPhone);

        if (memberRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException("That email address is already in use.");
        }
        if (memberRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalStateException("That phone number is already in use.");
        }

        String normalizedStaffNo = generateUniqueStaffNo();
        String internalMemberNo = internalStaffMemberNo(normalizedStaffNo);
        OffsetDateTime now = OffsetDateTime.now();
        // All staff accounts are provisioned passwordless: they receive an activation
        // email (see MinorAdminInvitationService.issueInvitation) and sign in using
        // email OTP thereafter. They remain INVITED until they click the activation
        // link and confirm the one-time code.
        boolean requiresClaim = !staffRoles.isEmpty();
        Member user = Member.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .memberNo(internalMemberNo)
            .staffNo(normalizedStaffNo)
            .stationId(normalizedStationId)
            .fullName(normalizedFullName)
            .email(normalizedEmail)
            .phone(normalizedPhone)
            .signatureText(null)
            .signatureRegisteredAt(null)
            .memberAccount(false)
            .staffAccessStatus(StaffAccessStatus.ACTIVE)
            .staffAccessAssignedAt(now)
            .staffAccessActivatedAt(now)
            .status(requiresClaim ? MemberStatus.INVITED : MemberStatus.ACTIVE)
            .position(primaryRole)
            .staffRoles(staffRoles)
            .passwordHash(INVITED_ACCOUNT_PASSWORD_PLACEHOLDER)
            .createdAt(now)
            .build();

        Member saved = memberRepository.save(user);
        userClaimService.updateClaims(saved.getId(), new ArrayList<>(userClaimService.defaultClaims(staffRoles, saved.isMemberAccess())));
        auditService.log("STAFF_USER", saved.getId(), auditAction, adminId, null, snapshotMember(saved));
        if (requiresClaim) {
            minorAdminInvitationService.issueInvitation(saved, adminId);
        }
        return saved;
    }

    private void ensureStaffIsNotExternalMember(String normalizedEmail, String normalizedPhone) {
        ForesightDirectoryService.MemberProfileLookupResult phoneLookup =
            foresightDirectoryService.lookupMemberProfileByPhone("+" + normalizedPhone);
        ForesightDirectoryService.MemberProfileLookupResult emailLookup =
            foresightDirectoryService.lookupMemberProfileByEmailV2(normalizedEmail);

        if (isExternalMemberFound(phoneLookup) || isExternalMemberFound(emailLookup)) {
            throw new IllegalStateException("This person is already registered as a SACCO member and cannot be added as staff.");
        }
        if (!isExternalMemberNotFound(phoneLookup) || !isExternalMemberNotFound(emailLookup)) {
            throw new IllegalStateException("We could not verify this person against Foresight. Try again later.");
        }
    }

    private boolean isExternalMemberFound(ForesightDirectoryService.MemberProfileLookupResult lookup) {
        return lookup != null && lookup.isFound();
    }

    private boolean isExternalMemberNotFound(ForesightDirectoryService.MemberProfileLookupResult lookup) {
        return lookup != null && lookup.isNotFound();
    }

    public String userIdLabel(UUID accountId) {
        return formatUserId(accountId);
    }

    private String generateUniqueStaffNo() {
        int candidateCount = LAST_GENERATED_USER_ID - FIRST_GENERATED_USER_ID + 1;
        for (int attempt = 0; attempt < candidateCount; attempt++) {
            long candidate = memberRepository.nextStaffNumberValue();
            if (candidate > LAST_GENERATED_USER_ID) {
                break;
            }
            String value = String.valueOf(candidate);
            if (candidate >= FIRST_GENERATED_USER_ID
                && !memberRepository.existsByStaffNoIgnoreCase(value)
                && !memberRepository.existsByMemberNoIgnoreCase(value)
                && !memberRepository.existsByMemberNoIgnoreCase("STAFF-" + value)) {
                return value;
            }
        }
        throw new IllegalStateException("All five-digit staff numbers are already in use.");
    }

    private String internalStaffMemberNo(String staffNo) {
        String base = "STAFF-" + staffNo;
        if (!memberRepository.existsByMemberNoIgnoreCase(base)) {
            return base;
        }
        return "STAFF-" + UUID.randomUUID();
    }

    private String requireValue(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalStateException(message);
        }
        return normalized;
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_LOG_PAGE_SIZE;
        }
        return Math.min(size, MAX_LOG_PAGE_SIZE);
    }

    private DateRange resolveDateRange(String dateFrom, String dateTo) {
        LocalDate from = parseDate(dateFrom, "Invalid start date.");
        LocalDate to = parseDate(dateTo, "Invalid end date.");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }
        ZoneId zoneId = ZoneId.systemDefault();
        OffsetDateTime start = from == null ? null : from.atStartOfDay(zoneId).toOffsetDateTime();
        OffsetDateTime endExclusive = to == null ? null : to.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();
        return new DateRange(start, endExclusive);
    }

    private LocalDate parseDate(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException(message);
        }
    }

    private record DateRange(OffsetDateTime start, OffsetDateTime endExclusive) {
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeAdminPhone(String value) {
        String normalized = TanzaniaPhoneNumber.normalizeOptional(value);
        if (value != null && !value.isBlank() && normalized == null) {
            throw new IllegalStateException("Enter a valid phone number in the format 255XXXXXXXXX.");
        }
        return normalized;
    }

    private String normalizeProductName(LoanType loanType, String productName) {
        String normalized = normalizeOptional(productName);
        if (normalized == null) {
            if (loanType != LoanType.CUSTOMIZED_LOAN) {
                return null;
            }
            throw new IllegalStateException("Enter the loan product name.");
        }
        if (normalized.length() > 120) {
            throw new IllegalStateException("Loan product name must be 120 characters or fewer.");
        }
        return normalized;
    }

    private String normalizeProductCode(String saccoId, LoanType loanType, String productCode, UUID existingId) {
        return normalizeProductCode(saccoId, loanType, productCode, existingId, null);
    }

    private String normalizeProductCode(String saccoId,
                                        LoanType loanType,
                                        String productCode,
                                        UUID existingId,
                                        String productName) {
        String normalized = normalizeOptional(productCode);
        if (normalized == null) {
            if (loanType != LoanType.CUSTOMIZED_LOAN) {
                normalized = loanType.defaultProductCode();
            } else {
                normalized = generateProductCode(productName);
                if (normalized == null) {
                    throw new IllegalStateException("Enter the loan product name.");
                }
            }
        }
        normalized = normalized.toUpperCase().replace(' ', '_');
        if (!normalized.matches("[A-Z0-9_-]{3,64}")) {
            throw new IllegalStateException("Loan product code must be 3-64 characters using letters, numbers, hyphen, or underscore.");
        }
        boolean duplicate = existingId == null
            ? loanProductSettingRepository.existsBySaccoIdAndProductCodeIgnoreCase(saccoId, normalized)
            : loanProductSettingRepository.existsBySaccoIdAndProductCodeIgnoreCaseAndIdNot(saccoId, normalized, existingId);
        if (duplicate) {
            throw new IllegalStateException("That loan product code already exists in this SACCO.");
        }
        return normalized;
    }

    private String generateProductCode(String productName) {
        String normalized = normalizeOptional(productName);
        if (normalized == null) {
            return null;
        }
        String generated = normalized
            .toUpperCase()
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "");
        if (generated.length() < 3) {
            return null;
        }
        return generated.length() > 64 ? generated.substring(0, 64) : generated;
    }

    private String normalizeProductDescription(String productDescription) {
        String normalized = normalizeOptional(productDescription);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 500) {
            throw new IllegalStateException("Loan product description must be 500 characters or fewer.");
        }
        return normalized;
    }

    private String normalizeRequiredProductDescription(String productDescription) {
        String normalized = normalizeProductDescription(productDescription);
        if (normalized == null) {
            throw new IllegalStateException("Enter a loan product description.");
        }
        return normalized;
    }

    private Integer normalizeDisplayOrder(Integer displayOrder) {
        if (displayOrder == null || displayOrder <= 0) {
            throw new IllegalStateException("Display order must be at least 1.");
        }
        return displayOrder;
    }

    private BigDecimal normalizeMinimumAmount(BigDecimal minimumAmount) {
        BigDecimal normalized = minimumAmount == null ? BigDecimal.ZERO : minimumAmount;
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Minimum amount cannot be negative.");
        }
        return normalized.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMaximumAmount(BigDecimal minimumAmount, BigDecimal maximumAmount) {
        if (maximumAmount == null) {
            return null;
        }
        BigDecimal normalized = maximumAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Maximum amount must be greater than zero.");
        }
        if (minimumAmount != null && normalized.compareTo(minimumAmount) < 0) {
            throw new IllegalStateException("Maximum amount cannot be lower than the minimum amount.");
        }
        return normalized;
    }

    private BigDecimal normalizeRatio(BigDecimal ratio) {
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Savings ratio cannot be negative.");
        }
        BigDecimal normalized = ratio.setScale(4, java.math.RoundingMode.HALF_UP);
        if (normalized.compareTo(MAX_LOAN_SAVINGS_RATIO) > 0) {
            throw new IllegalStateException("Savings ratio cannot exceed ten times savings.");
        }
        return normalized;
    }

    private Integer normalizeGuarantorCount(Integer guarantorsRequired) {
        if (guarantorsRequired == null || guarantorsRequired < 0) {
            throw new IllegalStateException("Guarantors required cannot be negative.");
        }
        if (guarantorsRequired > MAX_WORKFLOW_COUNT) {
            throw new IllegalStateException("Guarantors required must be between 0 and 15.");
        }
        return guarantorsRequired;
    }

    private BigDecimal normalizeInsuranceRate(BigDecimal insuranceRate) {
        if (insuranceRate == null || insuranceRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Insurance rate cannot be negative.");
        }
        return insuranceRate.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePercentageRate(BigDecimal rate, String negativeMessage) {
        if (rate == null) {
            return BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP);
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(negativeMessage);
        }
        return rate.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeApplicationFee(BigDecimal applicationFee) {
        if (applicationFee == null) {
            return DEFAULT_APPLICATION_FEE.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (applicationFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Application fee cannot be negative.");
        }
        return applicationFee.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Integer limitedCount(Integer value, String label, int minimum) {
        if (value == null) {
            return null;
        }
        if (value < minimum || value > MAX_WORKFLOW_COUNT) {
            throw new IllegalStateException(label + " must be between " + minimum + " and " + MAX_WORKFLOW_COUNT + ".");
        }
        return value;
    }

    private BigDecimal limitedWholeNumber(BigDecimal value, String label, int minimum) {
        if (value == null) {
            return null;
        }
        BigDecimal whole = nonNegativeWholeNumber(
            value,
            label + " cannot be negative.",
            label + " must be a whole number."
        );
        int count = whole.intValue();
        if (count < minimum || count > MAX_WORKFLOW_COUNT) {
            throw new IllegalStateException(label + " must be between " + minimum + " and " + MAX_WORKFLOW_COUNT + ".");
        }
        return whole;
    }

    private BigDecimal nonNegativeAmount(BigDecimal value, String message) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(message);
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegativeWholeNumber(BigDecimal value, String negativeMessage, String fractionMessage) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(negativeMessage);
        }
        try {
            return new BigDecimal(value.toBigIntegerExact());
        } catch (ArithmeticException ex) {
            throw new IllegalStateException(fractionMessage);
        }
    }

    private BigDecimal normalizeAnnualRate(BigDecimal annualRate) {
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Annual interest rate cannot be negative.");
        }
        return annualRate.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private InterestMethod normalizeInterestMethod(InterestMethod interestMethod) {
        return interestMethod == null ? InterestMethod.FLAT_RATE : interestMethod;
    }

    private boolean normalizeManagerReviewRequired(boolean managerReviewRequired) {
        return managerReviewRequired;
    }

    private ApprovalWorkflowStage normalizeWorkflowStartStage(ApprovalWorkflowStage workflowStartStage,
                                                              boolean managerReviewRequired,
                                                              boolean loanOfficerReviewRequired) {
        if (!managerReviewRequired && loanOfficerReviewRequired) {
            return ApprovalWorkflowStage.LOAN_OFFICER;
        }
        if (!loanOfficerReviewRequired) {
            return ApprovalWorkflowStage.MANAGER;
        }
        return workflowStartStage == ApprovalWorkflowStage.LOAN_OFFICER
            ? ApprovalWorkflowStage.LOAN_OFFICER
            : ApprovalWorkflowStage.MANAGER;
    }

    private Integer normalizeStagePriority(boolean enabled, Integer priority, int fallbackPriority) {
        if (!enabled) {
            return fallbackPriority;
        }
        if (priority == null) {
            return fallbackPriority;
        }
        if (priority < 1 || priority > MAX_WORKFLOW_PRIORITY) {
            throw new IllegalStateException("Stage priority must be between 1 and 6.");
        }
        return priority;
    }

    private Integer normalizeReviewPriority(boolean enabled, Integer priority, int fallbackPriority) {
        if (!enabled) {
            return fallbackPriority;
        }
        if (priority == null) {
            return fallbackPriority;
        }
        if (priority < 1 || priority > MAX_WORKFLOW_PRIORITY) {
            throw new IllegalStateException("Review priority must be between 1 and 6.");
        }
        return priority;
    }

    private WorkflowPriorityPlan normalizeWorkflowPriorities(boolean managerReviewRequired,
                                                             Integer managerPriority,
                                                             boolean loanOfficerReviewRequired,
                                                             Integer loanOfficerPriority,
                                                             boolean chairpersonReviewRequired,
                                                             Integer chairpersonPriority,
                                                             boolean boardReviewRequired,
                                                             Integer boardPriority,
                                                             boolean committeeReviewRequired,
                                                             Integer committeePriority,
                                                             boolean accountantReviewRequired,
                                                             Integer accountantPriority,
                                                             ApprovalWorkflowStage workflowStartStage) {
        List<WorkflowPriorityEntry> entries = new ArrayList<>();
        addWorkflowPriorityEntry(entries, "manager", managerReviewRequired, managerPriority, 1, 1);
        addWorkflowPriorityEntry(entries, "loanOfficer", loanOfficerReviewRequired, loanOfficerPriority, 2, 2);
        addWorkflowPriorityEntry(entries, "chairperson", chairpersonReviewRequired, chairpersonPriority, 3, 3);
        addWorkflowPriorityEntry(entries, "board", boardReviewRequired, boardPriority, 4, 4);
        addWorkflowPriorityEntry(entries, "committee", committeeReviewRequired, committeePriority, 5, 5);
        addWorkflowPriorityEntry(entries, "accountant", accountantReviewRequired, accountantPriority, 6, 6);

        Map<String, Integer> compact = entries.stream()
            .filter(WorkflowPriorityEntry::enabled)
            .sorted(Comparator.comparingInt(WorkflowPriorityEntry::requestedPriority)
                .thenComparingInt(WorkflowPriorityEntry::defaultOrder))
            .collect(LinkedHashMap::new,
                (map, entry) -> map.put(entry.key(), map.size() + 1),
                Map::putAll);

        int normalizedManagerPriority = compact.getOrDefault("manager", 1);
        int normalizedLoanOfficerPriority = compact.getOrDefault("loanOfficer", 2);
        ApprovalWorkflowStage normalizedStartStage = normalizeWorkflowStartStage(workflowStartStage, managerReviewRequired, loanOfficerReviewRequired);
        if (managerReviewRequired && loanOfficerReviewRequired) {
            normalizedStartStage = normalizedLoanOfficerPriority < normalizedManagerPriority
                ? ApprovalWorkflowStage.LOAN_OFFICER
                : ApprovalWorkflowStage.MANAGER;
        }

        return new WorkflowPriorityPlan(
            normalizedManagerPriority,
            normalizedLoanOfficerPriority,
            compact.getOrDefault("chairperson", 3),
            compact.getOrDefault("board", 4),
            compact.getOrDefault("committee", 5),
            compact.getOrDefault("accountant", 6),
            normalizedStartStage
        );
    }

    private void addWorkflowPriorityEntry(List<WorkflowPriorityEntry> entries,
                                          String key,
                                          boolean enabled,
                                          Integer priority,
                                          int fallbackPriority,
                                          int defaultOrder) {
        int requestedPriority = enabled
            ? normalizeReviewPriority(true, priority, fallbackPriority)
            : fallbackPriority;
        entries.add(new WorkflowPriorityEntry(key, enabled, requestedPriority, defaultOrder));
    }

    private record WorkflowPriorityEntry(String key, boolean enabled, int requestedPriority, int defaultOrder) {
    }

    private record WorkflowPriorityPlan(int managerPriority,
                                        int loanOfficerPriority,
                                        int chairpersonPriority,
                                        int boardPriority,
                                        int committeePriority,
                                        int accountantPriority,
                                        ApprovalWorkflowStage workflowStartStage) {
    }

    private void validateUniquePriority(String firstLabel,
                                        boolean firstEnabled,
                                        Integer firstPriority,
                                        String secondLabel,
                                        boolean secondEnabled,
                                        Integer secondPriority) {
        if (firstEnabled && secondEnabled && firstPriority != null && firstPriority.equals(secondPriority)) {
            throw new IllegalStateException(firstLabel + " and " + secondLabel + " cannot share the same priority slot.");
        }
    }

    private void validateWorkflowConfiguration(String saccoId,
                                               boolean managerReviewRequired,
                                               boolean loanOfficerReviewRequired,
                                               ApprovalWorkflowStage workflowStartStage,
                                               Integer managerPriority,
                                               Integer loanOfficerPriority,
                                               boolean chairpersonReviewRequired,
                                               Integer chairpersonPriority,
                                               boolean boardReviewRequired,
                                               Integer boardPriority,
                                               boolean committeeReviewRequired,
                                               Integer committeePriority,
                                               Integer committeeMinimumVotes,
                                               Integer committeeApprovalThreshold,
                                               boolean accountantReviewRequired,
                                               Integer accountantPriority,
                                               boolean disbursementOfficerRequired) {
        if (!managerReviewRequired && !loanOfficerReviewRequired && !chairpersonReviewRequired && !boardReviewRequired && !committeeReviewRequired && !accountantReviewRequired) {
            throw new IllegalStateException("At least one review or approval step must be required before disbursement.");
        }
        if (workflowStartStage != null
            && workflowStartStage != ApprovalWorkflowStage.MANAGER
            && workflowStartStage != ApprovalWorkflowStage.LOAN_OFFICER) {
            throw new IllegalStateException("Workflow start stage must be Manager or Loan Officer.");
        }
        if (workflowStartStage == ApprovalWorkflowStage.LOAN_OFFICER && !loanOfficerReviewRequired) {
            throw new IllegalStateException("Loan Officer must be enabled before it can be selected as the start stage.");
        }
        if (loanOfficerReviewRequired && activeLoanOfficerCount(saccoId) <= 0) {
            throw new IllegalStateException("No active loan officers are configured for this SACCO yet.");
        }
        if (disbursementOfficerRequired && activeDisbursementOfficerCount(saccoId) <= 0) {
            throw new IllegalStateException("Add at least one active Disbursement/Teller Officer before requiring that workflow role.");
        }
        if (!disbursementOfficerRequired && activeDisbursementClaimHolderCount(saccoId) <= 0) {
            throw new IllegalStateException("Grant disbursement queue and release claims to at least one active staff user before removing the Disbursement/Teller Officer requirement.");
        }
        Integer normalizedChairpersonPriority = normalizeStagePriority(chairpersonReviewRequired, chairpersonPriority, 3);
        Integer normalizedCommitteePriority = normalizeStagePriority(committeeReviewRequired, committeePriority, 4);
        Integer normalizedBoardPriority = normalizeStagePriority(boardReviewRequired, boardPriority, 3);
        Integer normalizedAccountantPriority = normalizeStagePriority(accountantReviewRequired, accountantPriority, 5);
        Integer normalizedManagerPriority = normalizeReviewPriority(managerReviewRequired, managerPriority, 1);
        Integer normalizedLoanOfficerPriority = normalizeReviewPriority(loanOfficerReviewRequired, loanOfficerPriority, 2);
        validateUniquePriority("Manager", managerReviewRequired, normalizedManagerPriority, "Loan Officer", loanOfficerReviewRequired, normalizedLoanOfficerPriority);
        validateUniquePriority("Manager", managerReviewRequired, normalizedManagerPriority, "Chairperson", chairpersonReviewRequired, normalizedChairpersonPriority);
        validateUniquePriority("Manager", managerReviewRequired, normalizedManagerPriority, "Board Member", boardReviewRequired, normalizedBoardPriority);
        validateUniquePriority("Manager", managerReviewRequired, normalizedManagerPriority, "Credit Committee", committeeReviewRequired, normalizedCommitteePriority);
        validateUniquePriority("Manager", managerReviewRequired, normalizedManagerPriority, "Accountant", accountantReviewRequired, normalizedAccountantPriority);
        validateUniquePriority("Loan Officer", loanOfficerReviewRequired, normalizedLoanOfficerPriority, "Chairperson", chairpersonReviewRequired, normalizedChairpersonPriority);
        validateUniquePriority("Loan Officer", loanOfficerReviewRequired, normalizedLoanOfficerPriority, "Board Member", boardReviewRequired, normalizedBoardPriority);
        validateUniquePriority("Loan Officer", loanOfficerReviewRequired, normalizedLoanOfficerPriority, "Credit Committee", committeeReviewRequired, normalizedCommitteePriority);
        validateUniquePriority("Loan Officer", loanOfficerReviewRequired, normalizedLoanOfficerPriority, "Accountant", accountantReviewRequired, normalizedAccountantPriority);
        validateUniquePriority("Chairperson", chairpersonReviewRequired, normalizedChairpersonPriority, "Board Member", boardReviewRequired, normalizedBoardPriority);
        validateUniquePriority("Chairperson", chairpersonReviewRequired, normalizedChairpersonPriority, "Credit Committee", committeeReviewRequired, normalizedCommitteePriority);
        validateUniquePriority("Chairperson", chairpersonReviewRequired, normalizedChairpersonPriority, "Accountant", accountantReviewRequired, normalizedAccountantPriority);
        validateUniquePriority("Board Member", boardReviewRequired, normalizedBoardPriority, "Credit Committee", committeeReviewRequired, normalizedCommitteePriority);
        validateUniquePriority("Board Member", boardReviewRequired, normalizedBoardPriority, "Accountant", accountantReviewRequired, normalizedAccountantPriority);
        if (committeeReviewRequired && accountantReviewRequired && normalizedCommitteePriority.equals(normalizedAccountantPriority)) {
            throw new IllegalStateException("Credit Committee and Accountant cannot share the same priority slot.");
        }
        if (committeeReviewRequired && committeeMinimumVotes != null && committeeApprovalThreshold != null
            && committeeApprovalThreshold > committeeMinimumVotes) {
            throw new IllegalStateException("Committee approval threshold cannot be greater than committee minimum votes.");
        }
    }

    private Integer normalizeMinimumRepaymentMonths(Integer minRepaymentMonths) {
        if (minRepaymentMonths == null || minRepaymentMonths <= 0) {
            throw new IllegalStateException("Minimum repayment period must be at least 1 month.");
        }
        return minRepaymentMonths;
    }

    private Integer normalizeMaximumRepaymentMonths(Integer minRepaymentMonths, Integer maxRepaymentMonths) {
        if (maxRepaymentMonths == null || maxRepaymentMonths <= 0) {
            throw new IllegalStateException("Maximum repayment period must be at least 1 month.");
        }
        if (maxRepaymentMonths < minRepaymentMonths) {
            throw new IllegalStateException("Maximum repayment period cannot be lower than the minimum repayment period.");
        }
        return maxRepaymentMonths;
    }

    private Integer normalizeCommitteeMinimumVotes(boolean committeeReviewRequired, Integer committeeMinimumVotes) {
        if (!committeeReviewRequired) {
            return 0;
        }
        if (committeeMinimumVotes == null || committeeMinimumVotes <= 0) {
            throw new IllegalStateException("Committee minimum votes must be at least 1 when committee review is required.");
        }
        if (committeeMinimumVotes > MAX_WORKFLOW_COUNT) {
            throw new IllegalStateException("Committee minimum votes must be between 1 and 15.");
        }
        return committeeMinimumVotes;
    }

    private Integer normalizeCommitteeApprovalThreshold(boolean committeeReviewRequired,
                                                        Integer committeeMinimumVotes,
                                                        Integer committeeApprovalThreshold) {
        if (!committeeReviewRequired) {
            return 0;
        }
        if (committeeApprovalThreshold == null || committeeApprovalThreshold <= 0) {
            throw new IllegalStateException("Committee approval threshold must be at least 1 when committee review is required.");
        }
        if (committeeApprovalThreshold > MAX_WORKFLOW_COUNT) {
            throw new IllegalStateException("Committee approval threshold must be between 1 and 15.");
        }
        if (committeeApprovalThreshold > committeeMinimumVotes) {
            throw new IllegalStateException("Committee approval threshold cannot be greater than committee minimum votes.");
        }
        return committeeApprovalThreshold;
    }

    private LoanProductStatus normalizeProductStatus(LoanProductStatus productStatus) {
        if (productStatus == LoanProductStatus.SUSPENDED) {
            return LoanProductStatus.SUSPENDED;
        }
        return LoanProductStatus.ACTIVE;
    }

    private void validateStageReviewerConfiguration(String saccoId,
                                                    Position role,
                                                    int assignedReviewerCount,
                                                    boolean reviewRequired,
                                                    String roleLabel) {
        if (!reviewRequired) {
            return;
        }
        UserClaim reviewerClaim = reviewerClaimForRole(role);
        int activeReviewerClaimHolders = roleDirectoryService.activeByClaim(saccoId, reviewerClaim).size();
        if (activeReviewerClaimHolders <= 0) {
            throw new IllegalStateException("No active " + roleLabel + "s have the required access claim for this SACCO yet.");
        }
        if (assignedReviewerCount <= 0) {
            throw new IllegalStateException("Assign at least one active " + roleLabel + " before using this stage.");
        }
        if (assignedReviewerCount > activeReviewerClaimHolders) {
            throw new IllegalStateException("Assigned " + roleLabel + "s cannot exceed the active " + roleLabel + " claim-holder count.");
        }
    }

    private List<UUID> normalizeReviewerIds(String saccoId, List<UUID> reviewerIds, Position role) {
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> uniqueIds = reviewerIds.stream()
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        UserClaim reviewerClaim = reviewerClaimForRole(role);
        Set<UUID> activeReviewerIds = roleDirectoryService.activeByClaim(saccoId, reviewerClaim).stream()
            .map(RoleDirectoryService.RoleAccountRef::getId)
            .collect(Collectors.toSet());
        for (UUID reviewerId : uniqueIds) {
            if (!activeReviewerIds.contains(reviewerId)) {
                throw new IllegalArgumentException("Selected reviewer does not have the required access claim for this SACCO.");
            }
        }
        if (uniqueIds.size() > MAX_WORKFLOW_COUNT) {
            throw new IllegalStateException("Reviewers assigned must be between 1 and 15.");
        }
        return List.copyOf(uniqueIds);
    }

    private List<UUID> singleChairpersonReviewerIds(String saccoId, boolean reviewRequired) {
        if (!reviewRequired) {
            return List.of();
        }
        List<RoleDirectoryService.RoleAccountRef> activeChairpersons =
            roleDirectoryService.activeByClaim(saccoId, UserClaim.CHAIRPERSON_QUEUE_APPROVE);
        if (activeChairpersons.isEmpty()) {
            return List.of();
        }
        if (activeChairpersons.size() > 1) {
            throw new IllegalStateException("Only one active Chairperson can be configured for this SACCO.");
        }
        return List.of(activeChairpersons.get(0).getId());
    }

    private UserClaim reviewerClaimForRole(Position role) {
        if (role == null) {
            throw new IllegalArgumentException("Reviewer access claim is required.");
        }
        return switch (role) {
            case CHAIRPERSON -> UserClaim.CHAIRPERSON_QUEUE_APPROVE;
            case BOARD -> UserClaim.BOARD_QUEUE_APPROVE;
            case CREDIT_COMMITTEE -> UserClaim.CREDIT_COMMITTEE_QUEUE_APPROVE;
            default -> throw new IllegalArgumentException("Unsupported reviewer access role.");
        };
    }

    private List<UUID> reviewerIds(UUID productId, ApprovalWorkflowStage stage) {
        if (productId == null) {
            return List.of();
        }
        return loanProductBoardReviewerRepository.findByLoanProductSettingIdAndReviewStageOrderByCreatedAtAsc(productId, stage).stream()
            .map(LoanProductBoardReviewer::getBoardMemberId)
            .toList();
    }

    private void replaceLoanProductReviewers(LoanProductSetting product, ApprovalWorkflowStage stage, List<UUID> reviewerIds) {
        loanProductBoardReviewerRepository.deleteByLoanProductSettingIdAndReviewStage(product.getId(), stage);
        loanProductBoardReviewerRepository.flush();
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        reviewerIds.forEach(reviewerId -> loanProductBoardReviewerRepository.save(LoanProductBoardReviewer.builder()
            .id(UUID.randomUUID())
            .loanProductSettingId(product.getId())
            .saccoId(product.getSaccoId())
            .reviewStage(stage)
            .boardMemberId(reviewerId)
            .createdAt(now)
            .build()));
    }

    private void ensureUserSettings(UUID memberId, OffsetDateTime now) {
        if (userSettingsRepository.existsById(memberId)) {
            return;
        }
        userSettingsRepository.save(UserSettings.builder()
            .memberId(memberId)
            .language("en")
            .notificationPrefs("{}")
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class UserUpdateResult {
        private boolean staffAccessPending;
        private String staffNo;

        private static UserUpdateResult noPending(String staffNo) {
            return new UserUpdateResult(false, staffNo);
        }
    }

    @lombok.Getter
    @lombok.Builder
    public static class UserAccessView {
        private UUID accountId;
        private String userIdLabel;
        private String loginId;
        private String memberNumber;
        private String staffMemberNumber;
        private String fullName;
        private String email;
        private String phone;
        private String roleSummary;
        @lombok.Builder.Default
        private Set<Position> staffRoles = new LinkedHashSet<>();
        @lombok.Builder.Default
        private Set<UserClaim> claims = new LinkedHashSet<>();
        private MemberStatus status;
        private String displayStatus;
        private String membershipLabel;
        private boolean memberAccess;
        private boolean canDeleteStaffRecord;
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class MinorAdminAccessView {
        private UUID accountId;
        private String loginId;
        private String fullName;
        private String email;
        private String phone;
        private boolean phoneVerified;
        private String saccoId;
        private String stationId;
        private MemberStatus status;
        private String invitationState;
        private OffsetDateTime invitationExpiresAt;
    }

    private void ensureSavingsAccount(UUID memberId, OffsetDateTime now) {
        if (savingsAccountRepository.findByMemberId(memberId).isPresent()) {
            return;
        }
        savingsAccountRepository.save(SavingsAccount.builder()
            .id(UUID.randomUUID())
            .memberId(memberId)
            .availableBalance(BigDecimal.ZERO)
            .sharesBalance(BigDecimal.ZERO)
            .depositsBalance(BigDecimal.ZERO)
            .updatedAt(now)
            .summaryLastSyncedAt(now)
            .build());
    }

    private void saveLoanProductSnapshot(LoanProductSetting product, UUID actorMemberId, String snapshotType) {
        LoanProductSnapshot snapshot = snapshotFromProduct(product);
        int nextVersionNumber = loanProductVersionRepository.findTopByLoanProductSettingIdOrderByVersionNumberDesc(product.getId())
            .map(existing -> existing.getVersionNumber() + 1)
            .orElse(1);
        loanProductVersionRepository.save(LoanProductVersion.builder()
            .id(UUID.randomUUID())
            .loanProductSettingId(product.getId())
            .saccoId(product.getSaccoId())
            .versionNumber(nextVersionNumber)
            .snapshotType(snapshotType)
            .snapshotJson(writeLoanProductSnapshot(snapshot))
            .createdByMemberId(actorMemberId)
            .createdAt(OffsetDateTime.now())
            .build());
    }

    private void saveLoanProductsSnapshot(String saccoId, UUID actorMemberId, String snapshotType) {
        LoanProductsSnapshot snapshot = buildLoanProductsSnapshot(saccoId);
        int nextVersionNumber = loanProductsVersionRepository.findTopBySaccoIdOrderByVersionNumberDesc(saccoId)
            .map(existing -> existing.getVersionNumber() + 1)
            .orElse(1);
        loanProductsVersionRepository.save(LoanProductsVersion.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .versionNumber(nextVersionNumber)
            .snapshotType(snapshotType)
            .snapshotJson(writeLoanProductsSnapshot(snapshot))
            .createdByMemberId(actorMemberId)
            .createdAt(OffsetDateTime.now())
            .build());
    }

    private String writeLoanProductSnapshot(LoanProductSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to save loan product version snapshot.", ex);
        }
    }

    private String writeLoanProductsSnapshot(LoanProductsSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to save loan products version snapshot.", ex);
        }
    }

    private LoanProductSnapshot parseLoanProductSnapshot(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, LoanProductSnapshot.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to read loan product version snapshot.", ex);
        }
    }

    private LoanProductsSnapshot parseLoanProductsSnapshot(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, LoanProductsSnapshot.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to read loan products version snapshot.", ex);
        }
    }

    private LoanProductsSnapshot buildLoanProductsSnapshot(String saccoId) {
        List<LoanProductSnapshot> products = loanProducts(saccoId).stream()
            .map(this::snapshotFromProduct)
            .toList();
        return new LoanProductsSnapshot(settings(saccoId).getResolvedApplicationFee(), products);
    }

    private LoanProductSnapshot snapshotFromProduct(LoanProductSetting product) {
        return LoanProductSnapshot.fromProduct(
            product,
            reviewerIds(product.getId(), ApprovalWorkflowStage.CHAIRPERSON),
            reviewerIds(product.getId(), ApprovalWorkflowStage.BOARD),
            reviewerIds(product.getId(), ApprovalWorkflowStage.CREDIT_COMMITTEE)
        );
    }

    private List<UUID> snapshotChairpersonReviewerIds(LoanProductSnapshot snapshot) {
        return snapshot.chairpersonReviewerIds() == null ? List.of() : snapshot.chairpersonReviewerIds();
    }

    private List<UUID> snapshotBoardReviewerIds(LoanProductSnapshot snapshot) {
        return snapshot.boardReviewerIds() == null ? List.of() : snapshot.boardReviewerIds();
    }

    private List<UUID> snapshotCreditCommitteeReviewerIds(LoanProductSnapshot snapshot) {
        if (snapshot.creditCommitteeReviewerIds() != null) {
            return snapshot.creditCommitteeReviewerIds();
        }
        return snapshot.boardReviewRequired() == null && snapshot.boardReviewerIds() != null
            ? snapshot.boardReviewerIds()
            : List.of();
    }

    private void applyLoanProductSnapshot(LoanProductSetting product, LoanProductSnapshot snapshot) {
        product.setProductCode(normalizeProductCode(product.getSaccoId(), product.getLoanType(), snapshot.productCode(), product.getId()));
        product.setProductName(normalizeProductName(product.getLoanType(), snapshot.productName()));
        product.setProductDescription(normalizeProductDescription(snapshot.productDescription()));
        product.setDisplayOrder(normalizeDisplayOrder(snapshot.displayOrder()));
        BigDecimal normalizedMinimumAmount = normalizeMinimumAmount(snapshot.minimumAmount());
        product.setMinimumAmount(normalizedMinimumAmount);
        product.setMaximumAmount(normalizeMaximumAmount(normalizedMinimumAmount, snapshot.maximumAmount()));
        product.setGuarantorsRequired(normalizeGuarantorCount(snapshot.guarantorsRequired()));
        product.setMaxLoanSavingsRatio(normalizeRatio(snapshot.maxLoanSavingsRatio()));
        product.setSavingsLimitCheckRequired(!Boolean.FALSE.equals(snapshot.savingsLimitCheckRequired()));
        product.setApplicationFee(normalizeApplicationFee(snapshot.applicationFee()));
        product.setInsuranceRate(normalizeInsuranceRate(snapshot.insuranceRate()));
        product.setProcessingFeeRate(normalizePercentageRate(snapshot.processingFeeRate(), "Loan processing fee percentage cannot be negative."));
        product.setInterestRate(normalizeAnnualRate(snapshot.interestRate()));
        product.setInterestMethod(normalizeInterestMethod(snapshot.interestMethod()));
        int minRepaymentMonths = normalizeMinimumRepaymentMonths(snapshot.minRepaymentMonths());
        product.setMinRepaymentMonths(minRepaymentMonths);
        product.setMaxRepaymentMonths(normalizeMaximumRepaymentMonths(minRepaymentMonths, snapshot.maxRepaymentMonths()));
        product.setAllowApplicationWithActiveLoan(Boolean.TRUE.equals(snapshot.allowApplicationWithActiveLoan()));
        product.setFreshFinancialDataRequired(Boolean.TRUE.equals(snapshot.freshFinancialDataRequired()));
        validateWorkflowConfiguration(
            product.getSaccoId(),
            Boolean.TRUE.equals(snapshot.managerReviewRequired()),
            Boolean.TRUE.equals(snapshot.loanOfficerReviewRequired()),
            snapshot.workflowStartStage(),
            snapshot.managerPriority(),
            snapshot.loanOfficerPriority(),
            Boolean.TRUE.equals(snapshot.chairpersonReviewRequired()),
            snapshot.chairpersonPriority(),
            Boolean.TRUE.equals(snapshot.boardReviewRequired()),
            snapshot.boardPriority(),
            Boolean.TRUE.equals(snapshot.committeeReviewRequired()),
            snapshot.committeePriority(),
            snapshot.committeeMinimumVotes(),
            snapshot.committeeApprovalThreshold(),
            !Boolean.FALSE.equals(snapshot.accountantReviewRequired()),
            snapshot.accountantPriority(),
            !Boolean.FALSE.equals(snapshot.disbursementOfficerRequired())
        );
        boolean managerReviewRequired = normalizeManagerReviewRequired(Boolean.TRUE.equals(snapshot.managerReviewRequired()));
        boolean chairpersonReviewRequired = Boolean.TRUE.equals(snapshot.chairpersonReviewRequired());
        boolean boardReviewRequired = Boolean.TRUE.equals(snapshot.boardReviewRequired());
        boolean committeeReviewRequired = Boolean.TRUE.equals(snapshot.committeeReviewRequired());
        product.setManagerReviewRequired(managerReviewRequired);
        product.setManagerPriority(normalizeReviewPriority(managerReviewRequired, snapshot.managerPriority(), 1));
        product.setLoanOfficerReviewRequired(Boolean.TRUE.equals(snapshot.loanOfficerReviewRequired()));
        product.setLoanOfficerPriority(normalizeReviewPriority(Boolean.TRUE.equals(snapshot.loanOfficerReviewRequired()), snapshot.loanOfficerPriority(), 2));
        product.setWorkflowStartStage(normalizeWorkflowStartStage(
            snapshot.workflowStartStage(),
            managerReviewRequired,
            Boolean.TRUE.equals(snapshot.loanOfficerReviewRequired())
        ));
        product.setChairpersonReviewRequired(chairpersonReviewRequired);
        product.setChairpersonPriority(normalizeStagePriority(chairpersonReviewRequired, snapshot.chairpersonPriority(), 3));
        product.setBoardReviewRequired(boardReviewRequired);
        product.setBoardPriority(normalizeStagePriority(boardReviewRequired, snapshot.boardPriority(), 3));
        product.setCommitteeReviewRequired(committeeReviewRequired);
        product.setCommitteePriority(normalizeStagePriority(committeeReviewRequired, snapshot.committeePriority(), 4));
        Integer committeeMinimumVotes = normalizeCommitteeMinimumVotes(committeeReviewRequired, snapshot.committeeMinimumVotes());
        Integer committeeApprovalThreshold = normalizeCommitteeApprovalThreshold(
            committeeReviewRequired,
            committeeMinimumVotes,
            snapshot.committeeApprovalThreshold()
        );
        validateStageReviewerConfiguration(product.getSaccoId(), Position.CHAIRPERSON,
            snapshotChairpersonReviewerIds(snapshot).size(),
            chairpersonReviewRequired,
            "chairperson");
        validateStageReviewerConfiguration(product.getSaccoId(), Position.BOARD,
            snapshotBoardReviewerIds(snapshot).size(),
            boardReviewRequired,
            "board member");
        validateStageReviewerConfiguration(product.getSaccoId(), Position.CREDIT_COMMITTEE,
            snapshotCreditCommitteeReviewerIds(snapshot).size(),
            committeeReviewRequired,
            "credit committee member");
        product.setCommitteeMinimumVotes(committeeMinimumVotes);
        product.setCommitteeApprovalThreshold(committeeApprovalThreshold);
        boolean accountantReviewRequired = !Boolean.FALSE.equals(snapshot.accountantReviewRequired());
        product.setAccountantReviewRequired(accountantReviewRequired);
        product.setAccountantPriority(normalizeStagePriority(accountantReviewRequired, snapshot.accountantPriority(), 5));
        product.setDisbursementOfficerRequired(!Boolean.FALSE.equals(snapshot.disbursementOfficerRequired()));
        product.setDisbursementProofRequired(!Boolean.FALSE.equals(snapshot.disbursementProofRequired()));
        product.setApplicantAttachmentRequired(Boolean.TRUE.equals(snapshot.applicantAttachmentRequired()));
        product.setGuarantorMinSavingsCheckRequired(Boolean.TRUE.equals(snapshot.guarantorMinSavingsCheckRequired()));
        product.setGuarantorMinimumSavings(nonNegativeAmount(snapshot.guarantorMinimumSavings(), "Minimum guarantor savings cannot be negative."));
        LoanProductStatus normalizedStatus = normalizeProductStatus(snapshot.productStatus());
        product.setProductStatus(normalizedStatus);
        product.setActive(normalizedStatus == LoanProductStatus.ACTIVE);
    }

    private Map<String, Object> snapshotProduct(LoanProductSetting product) {
        LoanProductSnapshot snapshot = snapshotFromProduct(product);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("loanType", snapshot.loanType());
        data.put("productCode", resolveProductCode(snapshot));
        data.put("productName", snapshot.productName());
        data.put("productDescription", snapshot.productDescription());
        data.put("displayOrder", snapshot.displayOrder());
        data.put("minimumAmount", snapshot.minimumAmount());
        data.put("maximumAmount", snapshot.maximumAmount());
        data.put("guarantorsRequired", snapshot.guarantorsRequired());
        data.put("ratio", snapshot.maxLoanSavingsRatio());
        data.put("savingsLimitCheckRequired", !Boolean.FALSE.equals(snapshot.savingsLimitCheckRequired()));
        data.put("applicationFee", snapshot.applicationFee());
        data.put("insuranceRate", snapshot.insuranceRate());
        data.put("processingFeeRate", snapshot.processingFeeRate());
        data.put("interestRate", snapshot.interestRate());
        data.put("interestMethod", snapshot.interestMethod());
        data.put("minRepaymentMonths", snapshot.minRepaymentMonths());
        data.put("maxRepaymentMonths", snapshot.maxRepaymentMonths());
        data.put("allowApplicationWithActiveLoan", Boolean.TRUE.equals(snapshot.allowApplicationWithActiveLoan()));
        data.put("freshFinancialDataRequired", Boolean.TRUE.equals(snapshot.freshFinancialDataRequired()));
        data.put("managerReviewRequired", Boolean.TRUE.equals(snapshot.managerReviewRequired()));
        data.put("managerPriority", snapshot.managerPriority());
        data.put("loanOfficerReviewRequired", Boolean.TRUE.equals(snapshot.loanOfficerReviewRequired()));
        data.put("loanOfficerPriority", snapshot.loanOfficerPriority());
        data.put("workflowStartStage", snapshot.workflowStartStage());
        data.put("chairpersonReviewRequired", Boolean.TRUE.equals(snapshot.chairpersonReviewRequired()));
        data.put("chairpersonPriority", snapshot.chairpersonPriority());
        data.put("chairpersonReviewerIds", snapshotChairpersonReviewerIds(snapshot));
        data.put("boardReviewRequired", Boolean.TRUE.equals(snapshot.boardReviewRequired()));
        data.put("boardPriority", snapshot.boardPriority());
        data.put("boardReviewerIds", snapshotBoardReviewerIds(snapshot));
        data.put("committeeReviewRequired", Boolean.TRUE.equals(snapshot.committeeReviewRequired()));
        data.put("committeePriority", snapshot.committeePriority());
        data.put("committeeMinimumVotes", snapshot.committeeMinimumVotes());
        data.put("committeeApprovalThreshold", snapshot.committeeApprovalThreshold());
        data.put("creditCommitteeReviewerIds", snapshotCreditCommitteeReviewerIds(snapshot));
        data.put("accountantReviewRequired", !Boolean.FALSE.equals(snapshot.accountantReviewRequired()));
        data.put("accountantPriority", snapshot.accountantPriority());
        data.put("disbursementOfficerRequired", !Boolean.FALSE.equals(snapshot.disbursementOfficerRequired()));
        data.put("disbursementProofRequired", !Boolean.FALSE.equals(snapshot.disbursementProofRequired()));
        data.put("applicantAttachmentRequired", Boolean.TRUE.equals(snapshot.applicantAttachmentRequired()));
        data.put("guarantorMinSavingsCheckRequired", Boolean.TRUE.equals(snapshot.guarantorMinSavingsCheckRequired()));
        data.put("guarantorMinimumSavings", snapshot.guarantorMinimumSavings());
        data.put("productStatus", snapshot.productStatus());
        data.put("active", product.getActive());
        return data;
    }

    private LoanProductVersionView toLoanProductVersionView(LoanProductVersion version,
                                                            LoanProductSnapshot snapshot,
                                                            String actorName) {
        return new LoanProductVersionView(
            version.getId(),
            version.getVersionNumber(),
            formatSnapshotTypeLabel(version.getSnapshotType()),
            version.getCreatedAt() == null ? "" : version.getCreatedAt().format(PRODUCT_VERSION_TIME_FORMATTER),
            actorName == null || actorName.isBlank() ? "System" : actorName,
            resolveProductCode(snapshot),
            resolveProductName(snapshot),
            formatAmountRange(snapshot.minimumAmount(), snapshot.maximumAmount()),
            snapshot.minRepaymentMonths() + " - " + snapshot.maxRepaymentMonths() + " month(s)",
            formatInterestSummary(snapshot.interestMethod(), snapshot.interestRate()),
            formatWorkflowSummary(snapshot),
            snapshot.productStatus() == null ? LoanProductStatus.ACTIVE.name() : snapshot.productStatus().name()
        );
    }

    private LoanProductsVersionView toLoanProductsVersionView(LoanProductsVersion version,
                                                              LoanProductsSnapshot snapshot,
                                                              String actorName) {
        List<LoanProductSnapshot> products = snapshot.products() == null ? List.of() : snapshot.products();
        String productsLabel = products.stream()
            .map(this::resolveProductName)
            .filter(name -> name != null && !name.isBlank())
            .limit(4)
            .collect(Collectors.joining(", "));
        if (products.size() > 4) {
            productsLabel = productsLabel + " +" + (products.size() - 4) + " more";
        }
        return new LoanProductsVersionView(
            version.getId(),
            version.getVersionNumber(),
            formatSnapshotTypeLabel(version.getSnapshotType()),
            version.getCreatedAt() == null ? "" : version.getCreatedAt().format(PRODUCT_VERSION_TIME_FORMATTER),
            actorName == null || actorName.isBlank() ? "System" : actorName,
            formatMoney(snapshot.applicationFee()),
            products.size(),
            productsLabel.isBlank() ? "No products" : productsLabel
        );
    }

    private String resolveProductCode(LoanProductSnapshot snapshot) {
        if (snapshot.productCode() != null && !snapshot.productCode().isBlank()) {
            return snapshot.productCode();
        }
        return snapshot.loanType() == null ? "" : snapshot.loanType().defaultProductCode();
    }

    private String resolveProductName(LoanProductSnapshot snapshot) {
        if (snapshot.productName() != null && !snapshot.productName().isBlank()) {
            return snapshot.productName();
        }
        return snapshot.loanType() == null ? "" : snapshot.loanType().getDisplayLabel();
    }

    private String formatAmountRange(BigDecimal minimumAmount, BigDecimal maximumAmount) {
        return formatMoney(minimumAmount) + " to " + (maximumAmount == null ? "Not set" : formatMoney(maximumAmount));
    }

    private String formatInterestSummary(InterestMethod interestMethod, BigDecimal interestRate) {
        return formatInterestMethodLabel(interestMethod) + " / " + formatPercent(interestRate);
    }

    private String formatWorkflowSummary(LoanProductSnapshot snapshot) {
        List<String> stages = new ArrayList<>();
        boolean managerEnabled = Boolean.TRUE.equals(snapshot.managerReviewRequired());
        boolean loanOfficerEnabled = Boolean.TRUE.equals(snapshot.loanOfficerReviewRequired());
        ApprovalWorkflowStage startStage = snapshot.workflowStartStage() == ApprovalWorkflowStage.LOAN_OFFICER && loanOfficerEnabled
            ? ApprovalWorkflowStage.LOAN_OFFICER
            : ApprovalWorkflowStage.MANAGER;
        if (startStage == ApprovalWorkflowStage.LOAN_OFFICER && loanOfficerEnabled) {
            stages.add("Loan Officer");
            if (managerEnabled) {
                stages.add("Manager");
            }
        } else if (managerEnabled) {
            stages.add("Manager");
            if (loanOfficerEnabled) {
                stages.add("Loan Officer");
            }
        } else if (loanOfficerEnabled) {
            stages.add("Loan Officer");
        }
        if (Boolean.TRUE.equals(snapshot.boardReviewRequired())) {
            stages.add("Board Member");
        }
        if (Boolean.TRUE.equals(snapshot.chairpersonReviewRequired())) {
            stages.add("Chairperson");
        }
        if (Boolean.TRUE.equals(snapshot.committeeReviewRequired())) {
            stages.add("Credit Committee");
        }
        if (!Boolean.FALSE.equals(snapshot.accountantReviewRequired())) {
            stages.add("Accountant");
        }
        stages.add(!Boolean.FALSE.equals(snapshot.disbursementOfficerRequired())
            ? "Disbursement/Teller Officer"
            : "Disbursement Release");
        return String.join(" -> ", stages);
    }

    private String formatSnapshotTypeLabel(String snapshotType) {
        if ("BEFORE_ROLLBACK".equalsIgnoreCase(snapshotType)) {
            return "Before rollback";
        }
        if ("BEFORE_UPDATE".equalsIgnoreCase(snapshotType)) {
            return "Before update";
        }
        return "Saved snapshot";
    }

    private String formatInterestMethodLabel(InterestMethod interestMethod) {
        return switch (interestMethod == null ? InterestMethod.FLAT_RATE : interestMethod) {
            case FLAT_RATE -> "Flat Rate";
            case REDUCING_BALANCE -> "Reducing Balance";
        };
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.##", new java.text.DecimalFormatSymbols(java.util.Locale.US));
        return format.format(amount.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private String formatPercent(BigDecimal ratio) {
        if (ratio == null) {
            return "0.00%";
        }
        return ratio.multiply(BigDecimal.valueOf(100))
            .setScale(2, java.math.RoundingMode.HALF_UP)
            .toPlainString() + "%";
    }

    private String normalizeDefaultLanguage(String defaultLanguage) {
        if (defaultLanguage == null || defaultLanguage.isBlank()) {
            return "en";
        }
        return switch (defaultLanguage.trim().toLowerCase(Locale.ROOT)) {
            case "sw", "sw_tz", "sw-tz", "kiswahili", "swahili" -> "sw";
            default -> "en";
        };
    }

    private Map<String, Object> snapshotSettings(SaccoSettings settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requiredGuarantors", settings.getRequiredGuarantors());
        data.put("boardSize", settings.getBoardSize());
        data.put("boardQuorum", settings.getBoardQuorum());
        data.put("loanOfficerReviewRequired", settings.isLoanOfficerReviewRequired());
        data.put("boardReviewRequired", settings.isBoardReviewRequired());
        data.put("approvalFlow", settings.resolvedApprovalFlow().stream().map(Enum::name).toList());
        data.put("applicationFee", settings.getResolvedApplicationFee());
        data.put("defaultLanguage", settings.getDefaultLanguage());
        data.put("applicantMaxDefaultedLoans", settings.getApplicantMaxDefaultedLoans());
        data.put("guarantorWithActiveLoanAllowed", settings.getGuarantorWithActiveLoanAllowed());
        data.put("guarantorMaxGuaranteedLoanAmount", settings.getGuarantorMaxGuaranteedLoanAmount());
        data.put("guarantorMaxDefaultedLoans", settings.getGuarantorMaxDefaultedLoans());
        return data;
    }

    private Map<String, Object> snapshotStationAccess(SaccoStation station) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("saccoId", station.getSaccoId());
        data.put("stationId", station.getStationId());
        data.put("accessStatus", station.getResolvedAccessStatus());
        data.put("paymentDueDate", station.getPaymentDueDate());
        data.put("accessSuspendedAt", station.getAccessSuspendedAt());
        data.put("accessSuspendedByMemberId", station.getAccessSuspendedByMemberId());
        data.put("accessRestrictionReason", station.getAccessRestrictionReason());
        return data;
    }

    private Map<String, Object> snapshotStationPolicy(SaccoStationPolicy policy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("saccoId", policy.getSaccoId());
        data.put("stationId", policy.getStationId());
        data.put("applicantMaxDefaultedLoans", policy.getApplicantMaxDefaultedLoans());
        data.put("guarantorWithActiveLoanAllowed", policy.getGuarantorWithActiveLoanAllowed());
        data.put("guarantorMaxGuaranteedLoanAmount", policy.getGuarantorMaxGuaranteedLoanAmount());
        data.put("guarantorMaxDefaultedLoans", policy.getGuarantorMaxDefaultedLoans());
        return data;
    }

    private Map<String, Object> snapshotIncident(AdminIncident incident) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subject", incident.getSubject());
        data.put("severity", incident.getSeverity());
        data.put("status", incident.getStatus());
        data.put("resolutionNote", incident.getResolutionNote());
        return data;
    }

    private List<ChartItem> toChartItems(Map<String, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
            .map(entry -> new ChartItem(
                entry.getKey(),
                entry.getValue(),
                total == 0 ? 0 : (int) Math.round((entry.getValue() * 100.0) / total)
            ))
            .sorted(Comparator.comparingLong(ChartItem::getCount).reversed())
            .toList();
    }

    public record SupportArchiveView(
        UUID id,
        String subject,
        String message,
        IncidentStatus status,
        OffsetDateTime createdAt,
        boolean readBySuperAdmin
    ) {
        public UUID getId() {
            return id;
        }

        public String getSubject() {
            return subject;
        }

        public String getMessage() {
            return message;
        }

        public IncidentStatus getStatus() {
            return status;
        }

        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }

        public boolean isReadBySuperAdmin() {
            return readBySuperAdmin;
        }

        public String getReadLabel() {
            return readBySuperAdmin ? "Read by Super Admin" : "Not read yet";
        }

        public String readLabel() {
            return getReadLabel();
        }
    }

    public record AdminDashboard(
        Map<MemberStatus, Long> memberCounts,
        Map<LoanStatus, Long> applicationCounts,
        Map<OutboxStatus, Long> outboxCounts,
        List<OutboxEvent> failedOutboxEvents,
        List<AdminEventItem> recentAuditEntries,
        List<AdminIncident> recentIncidents,
        SmsBalanceSummary smsBalance,
        boolean attachmentStorageReady,
        int totalMembers,
        int totalApplications
    ) {
        public Map<MemberStatus, Long> getMemberCounts() {
            return memberCounts;
        }

        public Map<LoanStatus, Long> getApplicationCounts() {
            return applicationCounts;
        }

        public Map<OutboxStatus, Long> getOutboxCounts() {
            return outboxCounts;
        }

        public List<OutboxEvent> getFailedOutboxEvents() {
            return failedOutboxEvents;
        }

        public List<AdminEventItem> getRecentAuditEntries() {
            return recentAuditEntries;
        }

        public List<AdminIncident> getRecentIncidents() {
            return recentIncidents;
        }

        public SmsBalanceSummary getSmsBalance() {
            return smsBalance;
        }

        public boolean getAttachmentStorageReady() {
            return attachmentStorageReady;
        }

        public int getTotalMembers() {
            return totalMembers;
        }

        public int getTotalApplications() {
            return totalApplications;
        }

        public long getActiveMemberCount() {
            return memberCounts.getOrDefault(MemberStatus.ACTIVE, 0L);
        }

        public long activeMemberCount() {
            return getActiveMemberCount();
        }

        public long getInactiveMemberCount() {
            return memberCounts.getOrDefault(MemberStatus.INACTIVE, 0L);
        }

        public long inactiveMemberCount() {
            return getInactiveMemberCount();
        }

        public long getOnReviewByManagerCount() {
            return applicationCounts.getOrDefault(LoanStatus.READY_FOR_MANAGER, 0L);
        }

        public long getAwaitingBoardCount() {
            return applicationCounts.getOrDefault(LoanStatus.AWAITING_BOARD, 0L)
                + applicationCounts.getOrDefault(LoanStatus.AWAITING_CREDIT_COMMITTEE, 0L);
        }

        public long getOutboxNewCount() {
            return outboxCounts.getOrDefault(OutboxStatus.NEW, 0L);
        }

        public long getOutboxPublishedCount() {
            return outboxCounts.getOrDefault(OutboxStatus.PUBLISHED, 0L);
        }

        public long getOutboxFailedCount() {
            return outboxCounts.getOrDefault(OutboxStatus.FAILED, 0L);
        }
    }

    public record SmsBalanceSummary(
        String stationId,
        long availableUnits,
        SmsUnitStatus status
    ) {
        public String getStationId() {
            return stationId;
        }

        public long getAvailableUnits() {
            return availableUnits;
        }

        public SmsUnitStatus getStatus() {
            return status;
        }

        public String getStatusLabel() {
            if (status == null) {
                return "Depleted";
            }
            String lower = status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            StringBuilder label = new StringBuilder();
            for (String part : lower.split(" ")) {
                if (part.isBlank()) {
                    continue;
                }
                if (label.length() > 0) {
                    label.append(' ');
                }
                label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return label.length() == 0 ? status.name() : label.toString();
        }

        public String statusLabel() {
            return getStatusLabel();
        }
    }

    public record AdminEventItem(
        String actionLabel,
        String sourceLabel,
        String createdAtLabel
    ) {
        public String getActionLabel() {
            return actionLabel;
        }

        public String getSourceLabel() {
            return sourceLabel;
        }

        public String getCreatedAtLabel() {
            return createdAtLabel;
        }
    }

    public record ReportData(
        Map<String, Long> applicationsByStatus,
        Map<String, Long> applicationsByType,
        long pendingGuarantorRequests,
        long rejectedByManager,
        long finalApproved,
        long managerDecisionCount,
        List<ChartItem> statusChart,
        List<ChartItem> typeChart,
        String statusFilter,
        String loanTypeFilter,
        String dateFrom,
        String dateTo,
        int totalFilteredApplications
    ) {
        public Map<String, Long> getApplicationsByStatus() {
            return applicationsByStatus;
        }

        public Map<String, Long> getApplicationsByType() {
            return applicationsByType;
        }

        public long getPendingGuarantorRequests() {
            return pendingGuarantorRequests;
        }

        public long getRejectedByManager() {
            return rejectedByManager;
        }

        public long getFinalApproved() {
            return finalApproved;
        }

        public long getManagerDecisionCount() {
            return managerDecisionCount;
        }

        public List<ChartItem> getStatusChart() {
            return statusChart;
        }

        public List<ChartItem> getTypeChart() {
            return typeChart;
        }

        public String getStatusFilter() {
            return statusFilter;
        }

        public String getLoanTypeFilter() {
            return loanTypeFilter;
        }

        public String getDateFrom() {
            return dateFrom;
        }

        public String getDateTo() {
            return dateTo;
        }

        public int getTotalFilteredApplications() {
            return totalFilteredApplications;
        }
    }

    public record LoanProductVersionView(
        UUID id,
        int versionNumber,
        String snapshotType,
        String savedAtLabel,
        String savedByLabel,
        String productCode,
        String productName,
        String amountRangeLabel,
        String tenureLabel,
        String interestLabel,
        String workflowLabel,
        String statusLabel
    ) {
        public UUID getId() {
            return id;
        }

        public int getVersionNumber() {
            return versionNumber;
        }

        public String getSnapshotType() {
            return snapshotType;
        }

        public String getSavedAtLabel() {
            return savedAtLabel;
        }

        public String getSavedByLabel() {
            return savedByLabel;
        }

        public String getProductCode() {
            return productCode;
        }

        public String getProductName() {
            return productName;
        }

        public String getAmountRangeLabel() {
            return amountRangeLabel;
        }

        public String getTenureLabel() {
            return tenureLabel;
        }

        public String getInterestLabel() {
            return interestLabel;
        }

        public String getWorkflowLabel() {
            return workflowLabel;
        }

        public String getStatusLabel() {
            return statusLabel;
        }
    }

    public record LoanProductsVersionView(
        UUID id,
        int versionNumber,
        String snapshotType,
        String savedAtLabel,
        String savedByLabel,
        String applicationFeeLabel,
        int productCount,
        String productsLabel
    ) {
        public UUID getId() {
            return id;
        }

        public int getVersionNumber() {
            return versionNumber;
        }

        public String getSnapshotType() {
            return snapshotType;
        }

        public String getSavedAtLabel() {
            return savedAtLabel;
        }

        public String getSavedByLabel() {
            return savedByLabel;
        }

        public String getApplicationFeeLabel() {
            return applicationFeeLabel;
        }

        public int getProductCount() {
            return productCount;
        }

        public String getProductsLabel() {
            return productsLabel;
        }
    }

    public static final class BoardReviewerOption {
        private final UUID id;
        private final String fullName;
        private final String memberNo;
        private final String stationId;

        public BoardReviewerOption(UUID id, String fullName, String memberNo, String stationId) {
            this.id = id;
            this.fullName = fullName;
            this.memberNo = memberNo;
            this.stationId = stationId;
        }

        public UUID getId() {
            return id;
        }

        public String getFullName() {
            return fullName;
        }

        public String getMemberNo() {
            return memberNo;
        }

        public String getStationId() {
            return stationId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoanProductsSnapshot(
        BigDecimal applicationFee,
        List<LoanProductSnapshot> products
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoanProductSnapshot(
        LoanType loanType,
        String productCode,
        String productName,
        String productDescription,
        Integer displayOrder,
        BigDecimal minimumAmount,
        BigDecimal maximumAmount,
        Integer guarantorsRequired,
        BigDecimal maxLoanSavingsRatio,
        Boolean savingsLimitCheckRequired,
        BigDecimal applicationFee,
        BigDecimal insuranceRate,
        BigDecimal processingFeeRate,
        BigDecimal interestRate,
        InterestMethod interestMethod,
        Integer minRepaymentMonths,
        Integer maxRepaymentMonths,
        Boolean allowApplicationWithActiveLoan,
        Boolean freshFinancialDataRequired,
        Boolean managerReviewRequired,
        Integer managerPriority,
        Boolean loanOfficerReviewRequired,
        Integer loanOfficerPriority,
        ApprovalWorkflowStage workflowStartStage,
        Boolean chairpersonReviewRequired,
        Integer chairpersonPriority,
        List<UUID> chairpersonReviewerIds,
        Boolean boardReviewRequired,
        Integer boardPriority,
        List<UUID> boardReviewerIds,
        Boolean committeeReviewRequired,
        Integer committeePriority,
        Integer committeeMinimumVotes,
        Integer committeeApprovalThreshold,
        List<UUID> creditCommitteeReviewerIds,
        Boolean accountantReviewRequired,
        Integer accountantPriority,
        Boolean disbursementOfficerRequired,
        Boolean disbursementProofRequired,
        Boolean applicantAttachmentRequired,
        Boolean guarantorMinSavingsCheckRequired,
        BigDecimal guarantorMinimumSavings,
        LoanProductStatus productStatus
    ) {
        public static LoanProductSnapshot fromProduct(LoanProductSetting product) {
            return fromProduct(product, List.of(), List.of(), List.of());
        }

        public static LoanProductSnapshot fromProduct(LoanProductSetting product,
                                                      List<UUID> chairpersonReviewerIds,
                                                      List<UUID> boardReviewerIds,
                                                      List<UUID> creditCommitteeReviewerIds) {
            return new LoanProductSnapshot(
                product.getLoanType(),
                product.getProductCode(),
                product.getProductName(),
                product.getProductDescription(),
                product.getResolvedDisplayOrder(),
                product.getMinimumAmount(),
                product.getMaximumAmount(),
                product.getGuarantorsRequired(),
                product.getMaxLoanSavingsRatio(),
                product.isSavingsLimitCheckRequired(),
                product.getApplicationFee(),
                product.getInsuranceRate(),
                product.getProcessingFeeRate(),
                product.getInterestRate(),
                product.getInterestMethod(),
                product.getMinimumRepaymentMonths(),
                product.getMaxRepaymentMonths(),
                product.getAllowApplicationWithActiveLoan(),
                product.getFreshFinancialDataRequired(),
                product.getManagerReviewRequired(),
                product.getResolvedManagerPriority(),
                product.getLoanOfficerReviewRequired(),
                product.getResolvedLoanOfficerPriority(),
                product.getWorkflowStartStage(),
                product.getChairpersonReviewRequired(),
                product.getResolvedChairpersonPriority(),
                chairpersonReviewerIds == null ? List.of() : List.copyOf(chairpersonReviewerIds),
                product.getBoardReviewRequired(),
                product.getBoardPriority(),
                boardReviewerIds == null ? List.of() : List.copyOf(boardReviewerIds),
                product.getCommitteeReviewRequired(),
                product.getCommitteePriority(),
                product.getCommitteeMinimumVotes(),
                product.getCommitteeApprovalThreshold(),
                creditCommitteeReviewerIds == null ? List.of() : List.copyOf(creditCommitteeReviewerIds),
                product.getAccountantReviewRequired(),
                product.getAccountantPriority(),
                product.getDisbursementOfficerRequired(),
                product.getDisbursementProofRequired(),
                product.getApplicantAttachmentRequired(),
                product.getGuarantorMinSavingsCheckRequired(),
                product.getGuarantorMinimumSavings(),
                product.getStatus()
            );
        }
    }

    public static class ChartItem {
        private final String label;
        private final long count;
        private final int percent;

        public ChartItem(String label, long count, int percent) {
            this.label = label;
            this.count = count;
            this.percent = percent;
        }

        public String getLabel() {
            return label;
        }

        public long getCount() {
            return count;
        }

        public int getPercent() {
            return percent;
        }
    }
}
