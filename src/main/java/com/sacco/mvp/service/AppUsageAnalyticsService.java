package com.sacco.mvp.service;

import com.sacco.mvp.domain.AppUsageEvent;
import com.sacco.mvp.domain.AppUsageEventType;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.repository.AppUsageEventRepository;
import com.sacco.mvp.repository.AppUsagePageMetricRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppUsageAnalyticsService {
    private static final Duration ACTIVE_WINDOW = Duration.ofMinutes(5);
    private static final Duration SESSION_STALE_AFTER = Duration.ofMinutes(45);
    private static final int RETENTION_DAYS = 90;
    private static final int PAGE_METRIC_RETENTION_DAYS = 400;
    private static final int MAX_ACTIVE_USERS = 12;
    private static final String PLATFORM_SCOPE = "Platform";
    private static final String UNASSIGNED_STATION_SCOPE = "NO_STATION";
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm");

    private final AppUsageEventRepository usageEventRepository;
    private final AppUsagePageMetricRepository pageMetricRepository;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final ApplicationClock applicationClock;
    private final Map<String, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    @Transactional
    public void recordAuthenticatedRequest(AppUserPrincipal principal, HttpServletRequest request) {
        if (principal == null || request == null || request.getSession(false) == null) {
            return;
        }
        String path = normalizePath(request.getRequestURI());
        if (!isApplicationPath(path) || isExcludedPath(path)) {
            return;
        }

        OffsetDateTime now = applicationClock.now();
        String sessionId = request.getSession(false).getId();
        String deviceType = deviceType(request.getHeader("User-Agent"));
        String browserFamily = browserFamily(request.getHeader("User-Agent"));
        ActiveSession session = toActiveSession(sessionId, principal, path, deviceType, browserFamily, now);
        ActiveSession previous = activeSessions.put(sessionId, session);
        if (previous == null || !principal.getMemberId().equals(previous.memberId())) {
            usageEventRepository.save(toEvent(AppUsageEventType.LOGIN, session, null, now));
        }

        if (isPageViewRequest(request, path)) {
            pageMetricRepository.incrementPageView(
                UUID.randomUUID(),
                hourlyBucket(now),
                metricScope(session.saccoId(), PLATFORM_SCOPE),
                metricScope(session.stationId(), UNASSIGNED_STATION_SCOPE),
                path,
                deviceType,
                browserFamily,
                now
            );
        }
        pruneStaleSessions(now);
    }

    @Transactional
    public void endSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ActiveSession session = activeSessions.remove(sessionId);
        if (session != null) {
            usageEventRepository.save(toEvent(AppUsageEventType.LOGOUT, session, session.currentPage(), applicationClock.now()));
        }
    }

    public UsageDashboardPayload dashboard(String range, String saccoId, String stationId) {
        UsageRange usageRange = UsageRange.from(range);
        OffsetDateTime now = applicationClock.now();
        ZoneId zoneId = applicationClock.zoneId();
        OffsetDateTime from = usageRange.start(now, zoneId);
        String normalizedSaccoId = normalizeScope(saccoId);
        String normalizedStationId = normalizeScope(stationId);
        long pageViews = pageMetricRepository.countPageViews(from, now, normalizedSaccoId, normalizedStationId);
        long logins = usageEventRepository.countScoped(AppUsageEventType.LOGIN, from, now, normalizedSaccoId, normalizedStationId);
        List<ActiveUserRow> activeUsers = activeUsers(now, normalizedSaccoId, normalizedStationId);
        List<AppUsageEventRepository.TopPageRow> topPages = pageMetricRepository.topPages(from, now, normalizedSaccoId, normalizedStationId);
        Map<String, String> saccoNamesById = saccoNamesById(topPages, activeUsers);
        List<ActiveUserRow> displayActiveUsers = activeUserRows(activeUsers, saccoNamesById);
        long activeUserCount = activeUsers.stream().map(ActiveUserRow::memberId).distinct().count();

        return new UsageDashboardPayload(
            usageRange.key(),
            activeUsers.size(),
            activeUserCount,
            pageViews,
            logins,
            timelineRows(
                usageRange,
                usageEventRepository.countEventsByBucket(AppUsageEventType.LOGIN.name(), from, now, normalizedSaccoId, normalizedStationId, usageRange.sqlPattern(), zoneId.getId()),
                from,
                now,
                zoneId
            ),
            topPageRows(topPages, saccoNamesById),
            percentRows(pageMetricRepository.deviceBreakdown(from, now, normalizedSaccoId, normalizedStationId)),
            percentRows(pageMetricRepository.browserBreakdown(from, now, normalizedSaccoId, normalizedStationId)),
            displayActiveUsers,
            TIME_LABEL.format(now.atZoneSameInstant(zoneId))
        );
    }

    @Scheduled(cron = "0 20 2 * * *")
    @Transactional
    public void deleteExpiredUsageEvents() {
        usageEventRepository.deleteByOccurredAtBefore(applicationClock.now().minusDays(RETENTION_DAYS));
        pageMetricRepository.deleteByBucketStartBefore(applicationClock.now().minusDays(PAGE_METRIC_RETENTION_DAYS));
    }

    private OffsetDateTime hourlyBucket(OffsetDateTime now) {
        return now.withMinute(0).withSecond(0).withNano(0);
    }

    private List<ActiveUserRow> activeUsers(OffsetDateTime now, String saccoId, String stationId) {
        OffsetDateTime activeAfter = now.minus(ACTIVE_WINDOW);
        pruneStaleSessions(now);
        return activeSessions.values().stream()
            .filter(session -> !session.lastActivity().isBefore(activeAfter))
            .filter(session -> saccoId == null || saccoId.equalsIgnoreCase(nullToEmpty(session.saccoId())))
            .filter(session -> stationId == null || stationId.equalsIgnoreCase(nullToEmpty(session.stationId())))
            .sorted(Comparator.comparing(ActiveSession::lastActivity).reversed())
            .limit(MAX_ACTIVE_USERS)
            .map(session -> new ActiveUserRow(
                session.memberId(),
                displayName(session),
                session.saccoId(),
                session.roles(),
                session.currentPage(),
                relativeTime(session.lastActivity(), now)
            ))
            .toList();
    }

    private void pruneStaleSessions(OffsetDateTime now) {
        OffsetDateTime staleBefore = now.minus(SESSION_STALE_AFTER);
        activeSessions.entrySet().removeIf(entry -> entry.getValue().lastActivity().isBefore(staleBefore));
    }

    private ActiveSession toActiveSession(String sessionId,
                                          AppUserPrincipal principal,
                                          String path,
                                          String deviceType,
                                          String browserFamily,
                                          OffsetDateTime now) {
        return new ActiveSession(
            sessionId,
            principal.getMemberId(),
            principal.getUsername(),
            blankToFallback(principal.getFullName(), principal.getUsername()),
            rolesLabel(principal),
            principal.getSaccoId(),
            principal.getStationId(),
            path,
            deviceType,
            browserFamily,
            now
        );
    }

    private AppUsageEvent toEvent(AppUsageEventType eventType, ActiveSession session, String pagePath, OffsetDateTime now) {
        return AppUsageEvent.builder()
            .id(UUID.randomUUID())
            .eventType(eventType)
            .memberId(session.memberId())
            .username(session.username())
            .displayName(session.displayName())
            .roles(session.roles())
            .saccoId(normalizeScope(session.saccoId()))
            .stationId(normalizeScope(session.stationId()))
            .pagePath(pagePath)
            .deviceType(session.deviceType())
            .browserFamily(session.browserFamily())
            .occurredAt(now)
            .build();
    }

    private boolean isPageViewRequest(HttpServletRequest request, String path) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept == null || accept.isBlank() || accept.contains("text/html") || accept.contains("*/*");
    }

    private boolean isApplicationPath(String path) {
        return path.startsWith("/admin/")
            || path.startsWith("/app/")
            || path.startsWith("/manager/")
            || path.startsWith("/board/")
            || path.startsWith("/chairperson/")
            || path.startsWith("/loan-officer/")
            || path.startsWith("/accountant/")
            || path.startsWith("/disbursement/")
            || path.startsWith("/staff/");
    }

    private boolean isExcludedPath(String path) {
        return path.startsWith("/css/")
            || path.startsWith("/js/")
            || path.startsWith("/images/")
            || path.startsWith("/webjars/")
            || path.startsWith("/actuator")
            || path.startsWith("/error")
            || path.equals("/favicon.ico")
            || path.equals("/admin/dashboard/usage-activity")
            || path.equals("/admin/dashboard/database-utilization")
            || path.startsWith("/notifications/");
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        int queryStart = value.indexOf('?');
        String path = queryStart >= 0 ? value.substring(0, queryStart) : value;
        return path.length() > 240 ? path.substring(0, 240) : path;
    }

    private String rolesLabel(AppUserPrincipal principal) {
        if (principal.getStaffRoles() != null && !principal.getStaffRoles().isEmpty()) {
            return principal.getStaffRoles().stream()
                .map(this::roleLabel)
                .reduce((left, right) -> left + ", " + right)
                .orElse("Staff");
        }
        return principal.isMemberAccess() ? "Member" : roleLabel(principal.getPosition());
    }

    private String roleLabel(Position role) {
        if (role == null) {
            return "User";
        }
        return switch (role) {
            case ADMIN -> "Super Admin";
            case MINOR_ADMIN -> "Minor Admin";
            case LOAN_OFFICER -> "Loan Officer";
            case DISBURSEMENT_OFFICER -> "Disbursement/Teller Officer";
            default -> titleCase(role.name().replace('_', ' '));
        };
    }

    private List<UsageCountRow> rows(List<AppUsageEventRepository.CountRow> rows) {
        return rows.stream()
            .map(row -> new UsageCountRow(row.getLabel(), row.getTotal(), 0))
            .toList();
    }

    private List<TopPageRow> topPageRows(List<AppUsageEventRepository.TopPageRow> rows, Map<String, String> saccoNamesById) {
        return rows.stream()
            .map(row -> new TopPageRow(row.getLabel(), saccoLabel(row.getSaccoId(), saccoNamesById), row.getTotal()))
            .toList();
    }

    private List<ActiveUserRow> activeUserRows(List<ActiveUserRow> rows, Map<String, String> saccoNamesById) {
        return rows.stream()
            .map(row -> new ActiveUserRow(
                row.memberId(),
                row.displayName(),
                saccoLabel(row.saccoId(), saccoNamesById),
                row.roles(),
                row.currentPage(),
                row.lastActivityLabel()
            ))
            .toList();
    }

    private List<UsageCountRow> timelineRows(UsageRange range,
                                             List<AppUsageEventRepository.CountRow> rows,
                                             OffsetDateTime from,
                                             OffsetDateTime now,
                                             ZoneId zoneId) {
        Map<String, Long> countsByLabel = rows.stream()
            .collect(java.util.stream.Collectors.toMap(
                AppUsageEventRepository.CountRow::getLabel,
                AppUsageEventRepository.CountRow::getTotal,
                Long::sum,
                LinkedHashMap::new
            ));
        List<UsageCountRow> timeline = new java.util.ArrayList<>();
        ZonedDateTime cursor = range.cursorStart(from, zoneId);
        ZonedDateTime end = range.cursorStart(now, zoneId);
        while (!cursor.isAfter(end)) {
            String key = range.bucketKey(cursor);
            timeline.add(new UsageCountRow(range.displayLabel(cursor), countsByLabel.getOrDefault(key, 0L), 0));
            cursor = range.next(cursor);
        }
        return timeline;
    }

    private List<UsageCountRow> percentRows(List<AppUsageEventRepository.CountRow> rows) {
        long total = rows.stream().mapToLong(AppUsageEventRepository.CountRow::getTotal).sum();
        if (total <= 0) {
            return List.of();
        }
        return rows.stream()
            .map(row -> new UsageCountRow(row.getLabel(), row.getTotal(), Math.round((row.getTotal() * 100.0) / total)))
            .toList();
    }

    private String deviceType(String userAgent) {
        String ua = nullToEmpty(userAgent).toLowerCase(Locale.ROOT);
        if (ua.contains("mobi") || ua.contains("android") || ua.contains("iphone") || ua.contains("ipad")) {
            return "Mobile";
        }
        return "Desktop";
    }

    private String browserFamily(String userAgent) {
        String ua = nullToEmpty(userAgent).toLowerCase(Locale.ROOT);
        if (ua.contains("edg/")) {
            return "Edge";
        }
        if (ua.contains("chrome/") || ua.contains("crios/")) {
            return "Chrome";
        }
        if (ua.contains("firefox/")) {
            return "Firefox";
        }
        if (ua.contains("safari/")) {
            return "Safari";
        }
        return "Other";
    }

    private String displayName(ActiveSession session) {
        return blankToFallback(session.displayName(), session.username());
    }

    private Map<String, String> saccoNamesById(List<AppUsageEventRepository.TopPageRow> topPages, List<ActiveUserRow> activeUsers) {
        Set<String> saccoIds = new LinkedHashSet<>();
        topPages.forEach(row -> collectSaccoId(row.getSaccoId(), saccoIds));
        activeUsers.forEach(row -> collectSaccoId(row.saccoId(), saccoIds));
        if (saccoIds.isEmpty()) {
            return Map.of();
        }
        return registeredSaccoRepository.findBySaccoIdIn(saccoIds).stream()
            .collect(Collectors.toMap(
                RegisteredSacco::getSaccoId,
                sacco -> blankToFallback(sacco.getSaccoName(), sacco.getSaccoId()),
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private void collectSaccoId(String value, Set<String> saccoIds) {
        String normalized = normalizeScope(value);
        if (normalized == null || "Platform".equalsIgnoreCase(normalized)) {
            return;
        }
        saccoIds.add(normalized);
    }

    private String saccoLabel(String value, Map<String, String> saccoNamesById) {
        String normalized = normalizeScope(value);
        if (normalized == null || "Platform".equalsIgnoreCase(normalized)) {
            return "Platform";
        }
        return saccoNamesById.getOrDefault(normalized, normalized);
    }

    private String relativeTime(OffsetDateTime value, OffsetDateTime now) {
        long seconds = Math.max(0, Duration.between(value, now).toSeconds());
        if (seconds < 60) {
            return "Just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        return hours + " hr ago";
    }

    private String normalizeScope(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String metricScope(String value, String fallback) {
        String normalized = normalizeScope(value);
        return normalized == null ? fallback : normalized;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String titleCase(String value) {
        String[] parts = value.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? value : builder.toString();
    }

    private enum UsageRange {
        TODAY("today", "YYYY-MM-DD HH24:00"),
        SEVEN_DAYS("7d", "YYYY-MM-DD"),
        THIRTY_DAYS("30d", "YYYY-MM-DD");

        private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        private static final DateTimeFormatter HOUR_DISPLAY = DateTimeFormatter.ofPattern("HH:00");
        private static final DateTimeFormatter DAY_DISPLAY = DateTimeFormatter.ofPattern("MMM d");

        private final String key;
        private final String sqlPattern;

        UsageRange(String key, String sqlPattern) {
            this.key = key;
            this.sqlPattern = sqlPattern;
        }

        static UsageRange from(String value) {
            if ("7d".equalsIgnoreCase(value)) {
                return SEVEN_DAYS;
            }
            if ("30d".equalsIgnoreCase(value)) {
                return THIRTY_DAYS;
            }
            return TODAY;
        }

        OffsetDateTime start(OffsetDateTime now, ZoneId zoneId) {
            LocalDate today = LocalDate.now(zoneId);
            return switch (this) {
                case TODAY -> today.atStartOfDay(zoneId).toOffsetDateTime();
                case SEVEN_DAYS -> today.minusDays(6).atStartOfDay(zoneId).toOffsetDateTime();
                case THIRTY_DAYS -> today.minusDays(29).atStartOfDay(zoneId).toOffsetDateTime();
            };
        }

        String key() {
            return key;
        }

        String sqlPattern() {
            return sqlPattern;
        }

        ZonedDateTime cursorStart(OffsetDateTime value, ZoneId zoneId) {
            ZonedDateTime zoned = value.atZoneSameInstant(zoneId);
            return switch (this) {
                case TODAY -> zoned.withMinute(0).withSecond(0).withNano(0);
                case SEVEN_DAYS, THIRTY_DAYS -> zoned.toLocalDate().atStartOfDay(zoneId);
            };
        }

        ZonedDateTime next(ZonedDateTime value) {
            return switch (this) {
                case TODAY -> value.plusHours(1);
                case SEVEN_DAYS, THIRTY_DAYS -> value.plusDays(1);
            };
        }

        String bucketKey(ZonedDateTime value) {
            return switch (this) {
                case TODAY -> HOUR_KEY.format(value);
                case SEVEN_DAYS, THIRTY_DAYS -> DAY_KEY.format(value);
            };
        }

        String displayLabel(ZonedDateTime value) {
            return switch (this) {
                case TODAY -> HOUR_DISPLAY.format(value);
                case SEVEN_DAYS, THIRTY_DAYS -> DAY_DISPLAY.format(value);
            };
        }
    }

    private record ActiveSession(
        String sessionId,
        UUID memberId,
        String username,
        String displayName,
        String roles,
        String saccoId,
        String stationId,
        String currentPage,
        String deviceType,
        String browserFamily,
        OffsetDateTime lastActivity
    ) {}

    public record UsageDashboardPayload(
        String range,
        long activeSessions,
        long activeUsers,
        long pageViews,
        long logins,
        List<UsageCountRow> timeline,
        List<TopPageRow> topPages,
        List<UsageCountRow> devices,
        List<UsageCountRow> browsers,
        List<ActiveUserRow> activeUserRows,
        String capturedAtLabel
    ) {}

    public record UsageCountRow(String label, long total, long percent) {}

    public record TopPageRow(String label, String saccoId, long total) {}

    public record ActiveUserRow(UUID memberId, String displayName, String saccoId, String roles, String currentPage, String lastActivityLabel) {}
}
