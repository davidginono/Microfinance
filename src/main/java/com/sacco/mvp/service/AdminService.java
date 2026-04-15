package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private static final long DASHBOARD_RECENT_WINDOW_DAYS = 30L;
    private static final int DEFAULT_LOG_PAGE_SIZE = 50;
    private static final int MAX_LOG_PAGE_SIZE = 100;
    private static final int DEFAULT_USER_PAGE_SIZE = 25;
    private static final int MAX_USER_PAGE_SIZE = 100;
    private final MemberRepository memberRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final NotificationRepository notificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final AdminIncidentRepository adminIncidentRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final AuditService auditService;
    private final AdminAlertService adminAlertService;
    private final NotificationViewService notificationViewService;
    private final UserClaimService userClaimService;
    private final RoleDirectoryService roleDirectoryService;
    private final SaccoConfigurationService saccoConfigurationService;

    public AdminDashboard dashboard(String saccoId, UUID adminId) {
        OffsetDateTime recentWindowStart = OffsetDateTime.now().minusDays(DASHBOARD_RECENT_WINDOW_DAYS);
        List<Member> members = memberRepository.findBySaccoIdOrderByFullNameAsc(saccoId);
        List<LoanApplication> applications = loanApplicationRepository.findAll().stream()
            .filter(app -> saccoId.equals(app.getSaccoId()))
            .sorted(Comparator.comparing(LoanApplication::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .toList();
        List<OutboxEvent> failedEvents = outboxEventRepository.findTop100ByStatusOrderByCreatedAtDesc(OutboxStatus.FAILED);
        List<AuditLog> auditEntries = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        List<AdminIncident> incidents = adminIncidentRepository.findBySaccoIdOrderByCreatedAtDesc(saccoId);

        Map<MemberStatus, Long> memberCounts = members.stream()
            .collect(Collectors.groupingBy(Member::getStatus, () -> new EnumMap<>(MemberStatus.class), Collectors.counting()));
        Map<LoanStatus, Long> applicationCounts = applications.stream()
            .collect(Collectors.groupingBy(LoanApplication::getStatus, () -> new EnumMap<>(LoanStatus.class), Collectors.counting()));
        Map<OutboxStatus, Long> outboxCounts = new EnumMap<>(OutboxStatus.class);
        outboxCounts.put(OutboxStatus.NEW, outboxEventRepository.countByStatus(OutboxStatus.NEW));
        outboxCounts.put(OutboxStatus.PUBLISHED, outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED));
        outboxCounts.put(OutboxStatus.FAILED, outboxEventRepository.countByStatus(OutboxStatus.FAILED));

        boolean attachmentStorageReady = Files.exists(Paths.get("loan-uploads", "applications"));
        return new AdminDashboard(
            memberCounts,
            applicationCounts,
            outboxCounts,
            failedEvents.stream().limit(10).toList(),
            auditEntries.stream()
                .filter(entry -> entry.getCreatedAt() != null && !entry.getCreatedAt().isBefore(recentWindowStart))
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
            attachmentStorageReady,
            members.size(),
            applications.size()
        );
    }

    public List<UserAccessView> users(String saccoId) {
        List<UserAccessView> users = new java.util.ArrayList<>();
        memberRepository.findBySaccoIdOrderByFullNameAsc(saccoId)
            .forEach(member -> users.add(UserAccessView.builder()
                .accountId(member.getId())
                .loginId(member.getMemberNo())
                .fullName(member.getFullName())
                .email(member.getEmail())
                .roleSummary(formatRoleSummary(member.getStaffRolesResolved(), member.isMemberAccess()))
                .staffRoles(member.getStaffRolesResolved())
                .status(member.getStatus())
                .membershipLabel(resolveMembershipLabel(member))
                .build()));
        users.sort(Comparator.comparing(UserAccessView::getFullName, String.CASE_INSENSITIVE_ORDER));
        return users;
    }

    public Page<UserAccessView> usersPage(String saccoId, String query, int page, int size) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_USER_PAGE_SIZE : Math.min(size, MAX_USER_PAGE_SIZE);
        return memberRepository.findUserAccessPage(saccoId, normalizedQuery, PageRequest.of(safePage, safeSize))
            .map(member -> UserAccessView.builder()
                .accountId(member.getId())
                .loginId(member.getMemberNo())
                .fullName(member.getFullName())
                .email(member.getEmail())
                .roleSummary(formatRoleSummary(member.getStaffRolesResolved(), member.isMemberAccess()))
                .staffRoles(member.getStaffRolesResolved())
                .status(member.getStatus())
                .membershipLabel(resolveMembershipLabel(member))
                .build());
    }

    @Transactional
    public Member createUser(String saccoId,
                             UUID adminId,
                             String memberNo,
                             String fullName,
                             String email,
                             String phone,
                             List<Position> positions) {
        LinkedHashSet<Position> staffRoles = validateStaffRoles(positions);
        Position primaryRole = Position.primaryRole(staffRoles, false);
        String normalizedMemberNo = requireValue(memberNo, "Enter a user ID.").toUpperCase();
        String normalizedFullName = requireValue(fullName, "Enter the user's full name.");
        String normalizedEmail = requireValue(email, "Enter the user's email address.").toLowerCase();
        String normalizedPhone = normalizeOptional(phone);

        if (memberRepository.findByMemberNo(normalizedMemberNo).isPresent()) {
            throw new IllegalStateException("That user ID is already in use.");
        }
        if (memberRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new IllegalStateException("That email address is already in use.");
        }
        if (normalizedPhone != null && memberRepository.findByPhone(normalizedPhone).isPresent()) {
            throw new IllegalStateException("That phone number is already in use.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Member user = Member.builder()
            .id(UUID.randomUUID())
            .saccoId(saccoId)
            .memberNo(normalizedMemberNo)
            .fullName(normalizedFullName)
            .email(normalizedEmail)
            .phone(normalizedPhone)
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .position(primaryRole)
            .staffRoles(staffRoles)
            .passwordHash("OTP_ONLY_LOGIN")
            .createdAt(now)
            .build();

        Member saved = memberRepository.save(user);
        auditService.log("STAFF_USER", saved.getId(), "ADMIN_CREATE_STAFF_USER", adminId, null, snapshotMember(saved));
        return saved;
    }

    @Transactional
    public void updateUser(String saccoId, UUID adminId, UUID accountId, List<Position> positions, MemberStatus status) {
        Member member = memberRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        if (!saccoId.equals(member.getSaccoId())) {
            throw new IllegalArgumentException("Member not found in this SACCO");
        }
        LinkedHashSet<Position> staffRoles = validateStaffRoles(positions);
        boolean memberAccess = member.isMemberAccess();
        Position primaryRole = Position.primaryRole(staffRoles, memberAccess);
        if (accountId.equals(adminId) && (!staffRoles.contains(Position.ADMIN) || status != MemberStatus.ACTIVE)) {
            throw new IllegalStateException("You cannot remove your own admin access");
        }

        Map<String, Object> before = snapshotMember(member);
        Position previousPosition = member.getPosition();
        member.setPosition(primaryRole);
        member.setStaffRoles(staffRoles);
        member.setMemberAccount(memberAccess);
        member.setStatus(status);
        if (previousPosition != primaryRole || member.getRank() == null) {
            member.setRank(nextRank(saccoId, primaryRole == null ? Position.MEMBER : primaryRole));
        }
        memberRepository.save(member);
        if (memberAccess) {
            ensureUserSettings(accountId, OffsetDateTime.now());
            ensureSavingsAccount(accountId, OffsetDateTime.now());
        }
        userClaimService.updateClaims(accountId, new java.util.ArrayList<>(userClaimService.defaultClaims(staffRoles, memberAccess)));
        auditService.log("MEMBER", accountId, "ADMIN_UPDATE_MEMBER", adminId, before, snapshotMember(member));
    }

    public List<LoanProductSetting> loanProducts(String saccoId) {
        saccoConfigurationService.ensureDefaultLoanProducts(saccoId);
        return loanProductSettingRepository.findBySaccoIdOrderByLoanTypeAsc(saccoId);
    }

    public SaccoSettings settings(String saccoId) {
        return saccoSettingsRepository.findById(saccoId)
            .orElseThrow(() -> new IllegalArgumentException("SACCO settings not found"));
    }

    public int activeBoardMemberCount(String saccoId) {
        return roleDirectoryService.activeByRole(saccoId, Position.BOARD).size();
    }

    @Transactional
    public void updateLoanProduct(String saccoId, UUID adminId, UUID productId, Integer guarantorsRequired,
                                  BigDecimal ratio, BigDecimal insuranceRate, BigDecimal interestRate,
                                  Integer maxRepaymentMonths, boolean active) {
        LoanProductSetting product = loanProductSettingRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Loan product not found"));
        if (!saccoId.equals(product.getSaccoId())) {
            throw new IllegalArgumentException("Loan product not found in this SACCO");
        }

        Map<String, Object> before = snapshotProduct(product);
        product.setGuarantorsRequired(guarantorsRequired);
        product.setMaxLoanSavingsRatio(ratio);
        product.setInsuranceRate(insuranceRate);
        product.setInterestRate(interestRate);
        product.setMaxRepaymentMonths(maxRepaymentMonths);
        product.setActive(active);
        product.setUpdatedAt(OffsetDateTime.now());
        loanProductSettingRepository.save(product);
        auditService.log("LOAN_PRODUCT", productId, "ADMIN_UPDATE_LOAN_PRODUCT", adminId, before, snapshotProduct(product));
    }

    @Transactional
    public void updateBoardReviewRequirement(String saccoId, UUID adminId, Integer boardQuorum) {
        SaccoSettings settings = settings(saccoId);
        int requiredReviewers = boardQuorum == null ? 0 : boardQuorum;
        int activeBoardMembers = activeBoardMemberCount(saccoId);

        if (requiredReviewers <= 0) {
            throw new IllegalArgumentException("Required board reviewers must be at least 1.");
        }
        if (activeBoardMembers <= 0) {
            throw new IllegalArgumentException("No active board members are configured for this SACCO yet.");
        }
        if (requiredReviewers > activeBoardMembers) {
            throw new IllegalArgumentException("Required board reviewers cannot be more than the active board members in this SACCO.");
        }

        Map<String, Object> before = snapshotSettings(settings);
        settings.setBoardQuorum(requiredReviewers);
        settings.setUpdatedAt(OffsetDateTime.now());
        saccoSettingsRepository.save(settings);
        auditService.log("SACCO_SETTINGS", null, "ADMIN_UPDATE_BOARD_REVIEW_REQUIREMENT", adminId, before, snapshotSettings(settings));
    }

    public List<NotificationViewService.NotificationView> adminMessages(UUID adminId) {
        return notificationViewService.toViews(notificationRepository.findByRecipientMemberIdOrderByCreatedAtDesc(adminId)).stream()
            .filter(view -> "SUPPORT_MESSAGE".equals(view.getType()) && view.getIncidentId() != null)
            .toList();
    }

    public Map<UUID, AdminIncident> adminMessageIncidentMap(List<NotificationViewService.NotificationView> messages) {
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
        return adminIncidentRepository.findAllById(incidentIds).stream()
            .collect(Collectors.toMap(AdminIncident::getId, incident -> incident, (left, right) -> left, LinkedHashMap::new));
    }

    public List<Member> activeMembers(String saccoId) {
        return memberRepository.findBySaccoIdAndStatusOrderByFullNameAsc(saccoId, MemberStatus.ACTIVE);
    }

    public List<AdminIncident> incidents(String saccoId, IncidentStatus status, IncidentSeverity severity) {
        return adminIncidentRepository.findBySaccoIdOrderByCreatedAtDesc(saccoId).stream()
            .filter(item -> status == null || item.getStatus() == status)
            .filter(item -> severity == null || item.getSeverity() == severity)
            .toList();
    }

    public AdminIncident incident(String saccoId, UUID incidentId) {
        AdminIncident incident = adminIncidentRepository.findById(incidentId)
            .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        if (incident.getSaccoId() != null && !saccoId.equals(incident.getSaccoId())) {
            throw new IllegalArgumentException("Incident not found in this SACCO");
        }
        return incident;
    }

    @Transactional
    public void updateIncident(String saccoId, UUID adminId, UUID incidentId, IncidentSeverity severity,
                               IncidentStatus status, String resolutionNote) {
        AdminIncident incident = incident(saccoId, incidentId);
        Map<String, Object> before = snapshotIncident(incident);
        incident.setSeverity(severity);
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
        Member admin = memberRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        List<Member> recipients = memberRepository.findBySaccoIdAndStatusOrderByFullNameAsc(saccoId, MemberStatus.ACTIVE);
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
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Recipient member not found"));
        Member admin = memberRepository.findById(adminId)
            .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        if (!saccoId.equals(member.getSaccoId())) {
            throw new IllegalArgumentException("Recipient member not found in this SACCO");
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
        List<RoleDirectoryService.RoleAccountRef> admins = roleDirectoryService.activeByRole(saccoId, Position.ADMIN);
        if (admins.isEmpty()) {
            throw new IllegalStateException("No active admin is configured for this SACCO");
        }
        adminAlertService.openSupportIncident(
            saccoId,
            memberId,
            "Member Support",
            subject,
            message,
            IncidentSeverity.MEDIUM,
            Map.of("memberNo", sender.getMemberNo(), "senderName", sender.getFullName())
        );
        auditService.log("SUPPORT", memberId, "MEMBER_SUPPORT_MESSAGE", memberId, null,
            Map.of("subject", subject, "message", message, "adminCount", admins.size()));
    }

    public Page<OutboxEvent> outboxEvents(int page, int size, OutboxStatus status, String dateFrom, String dateTo, String loanId) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizePageSize(size));
        String normalizedLoanId = normalizeOptional(loanId);
        DateRange dateRange = resolveDateRange(dateFrom, dateTo);
        return outboxEventRepository.searchMonitorView(
            status == null ? null : status.name(),
            dateRange.start(),
            dateRange.endExclusive(),
            normalizedLoanId,
            pageRequest
        );
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
        DateRange dateRange = resolveDateRange(dateFrom, dateTo);
        String normalizedActorId = normalizeOptional(actorId);
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

    public ReportData reports(String saccoId, String statusFilter, String loanTypeFilter, String dateFrom, String dateTo) {
        List<LoanApplication> applications = loanApplicationRepository.findAll().stream()
            .filter(app -> saccoId.equals(app.getSaccoId()))
            .filter(app -> statusFilter == null || statusFilter.isBlank() || app.getStatus().name().equals(statusFilter))
            .filter(app -> loanTypeFilter == null || loanTypeFilter.isBlank() || app.getLoanType().name().equals(loanTypeFilter))
            .filter(app -> dateFrom == null || dateFrom.isBlank() || (app.getCreatedAt() != null && !app.getCreatedAt().toLocalDate().isBefore(java.time.LocalDate.parse(dateFrom))))
            .filter(app -> dateTo == null || dateTo.isBlank() || (app.getCreatedAt() != null && !app.getCreatedAt().toLocalDate().isAfter(java.time.LocalDate.parse(dateTo))))
            .toList();

        Map<String, Long> byStatus = applications.stream()
            .collect(Collectors.groupingBy(app -> app.getStatus().name(), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byType = applications.stream()
            .collect(Collectors.groupingBy(app -> app.getLoanType().name(), LinkedHashMap::new, Collectors.counting()));
        long pendingGuarantorRequests = guarantorRequestRepository.findAll().stream()
            .filter(req -> applications.stream().anyMatch(app -> app.getId().equals(req.getLoanApplicationId())))
            .filter(req -> req.getStatus() == GuarantorRequestStatus.PENDING)
            .count();
        long rejectedByManager = applications.stream().filter(app -> app.getStatus() == LoanStatus.MANAGER_REJECTED).count();
        long approvedFinal = applications.stream().filter(app -> app.getStatus() == LoanStatus.FINAL_APPROVED).count();
        long managerDecisionCount = managerReviewRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(review -> applications.stream().anyMatch(app -> app.getId().equals(review.getLoanApplicationId())))
            .count();

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
            applications.size()
        );
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
        data.put("memberNo", member.getMemberNo());
        data.put("fullName", member.getFullName());
        data.put("position", member.getPosition());
        data.put("staffRoles", member.getStaffRolesResolved());
        data.put("memberAccount", member.isMemberAccess());
        data.put("status", member.getStatus());
        return data;
    }

    private LinkedHashSet<Position> validateStaffRoles(List<Position> positions) {
        LinkedHashSet<Position> staffRoles = Position.normalizeStaffRoles(positions);
        if (staffRoles.isEmpty()) {
            throw new IllegalStateException("Select at least one staff role for the user.");
        }
        if ((positions != null) && positions.stream().anyMatch(position -> position == Position.MEMBER)) {
            throw new IllegalStateException("Use member registration for member-only accounts. Admin-created users must use a staff role.");
        }
        if (staffRoles.contains(Position.ADMIN) && staffRoles.size() > 1) {
            throw new IllegalStateException("Admin accounts cannot be combined with any other staff role.");
        }
        return staffRoles;
    }

    private String formatRoleSummary(Set<Position> staffRoles, boolean memberAccess) {
        if (memberAccess && (staffRoles == null || staffRoles.isEmpty())) {
            return "MEMBER";
        }
        return Position.normalizeStaffRoles(staffRoles).stream()
            .map(Enum::name)
            .collect(Collectors.joining(", "));
    }

    private String resolveMembershipLabel(Member member) {
        if (!member.isMemberAccess()) {
            return "Staff";
        }
        return member.getStaffRolesResolved().isEmpty() ? "Member" : "Staff And Member";
    }

    private int nextRank(String saccoId, Position position) {
        return memberRepository.findTopBySaccoIdAndPositionOrderByRankDesc(saccoId, position)
            .map(Member::getRank)
            .orElse(0) + 1;
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
    @lombok.Builder
    public static class UserAccessView {
        private UUID accountId;
        private String loginId;
        private String fullName;
        private String email;
        private String roleSummary;
        @lombok.Builder.Default
        private Set<Position> staffRoles = new LinkedHashSet<>();
        private MemberStatus status;
        private String membershipLabel;
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

    private Map<String, Object> snapshotProduct(LoanProductSetting product) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("loanType", product.getLoanType());
        data.put("guarantorsRequired", product.getGuarantorsRequired());
        data.put("ratio", product.getMaxLoanSavingsRatio());
        data.put("insuranceRate", product.getInsuranceRate());
        data.put("interestRate", product.getInterestRate());
        data.put("maxRepaymentMonths", product.getMaxRepaymentMonths());
        data.put("active", product.getActive());
        return data;
    }

    private Map<String, Object> snapshotSettings(SaccoSettings settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requiredGuarantors", settings.getRequiredGuarantors());
        data.put("boardSize", settings.getBoardSize());
        data.put("boardQuorum", settings.getBoardQuorum());
        data.put("defaultLanguage", settings.getDefaultLanguage());
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

    public record AdminDashboard(
        Map<MemberStatus, Long> memberCounts,
        Map<LoanStatus, Long> applicationCounts,
        Map<OutboxStatus, Long> outboxCounts,
        List<OutboxEvent> failedOutboxEvents,
        List<AdminEventItem> recentAuditEntries,
        List<AdminIncident> recentIncidents,
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

        public long getInactiveMemberCount() {
            return memberCounts.getOrDefault(MemberStatus.INACTIVE, 0L);
        }

        public long getOnReviewByManagerCount() {
            return applicationCounts.getOrDefault(LoanStatus.READY_FOR_MANAGER, 0L);
        }

        public long getAwaitingBoardCount() {
            return applicationCounts.getOrDefault(LoanStatus.AWAITING_BOARD, 0L);
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
