package com.sacco.mvp.service;

import com.sacco.mvp.domain.AppUsageEvent;
import com.sacco.mvp.domain.AppUsageEventType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.repository.AppUsageEventRepository;
import com.sacco.mvp.repository.AppUsagePageMetricRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUsageAnalyticsServiceTest {
    @Mock private AppUsageEventRepository usageEventRepository;
    @Mock private AppUsagePageMetricRepository pageMetricRepository;
    @Mock private RegisteredSaccoRepository registeredSaccoRepository;

    @Test
    void recordsLoginAuditEventAndRollsUpPageViewForFirstAuthenticatedPageRequest() {
        AppUsageAnalyticsService service = service();
        MockHttpServletRequest request = htmlRequest("/admin/dashboard");

        service.recordAuthenticatedRequest(principal("SACCO-1", "ST-1", Position.MINOR_ADMIN), request);

        ArgumentCaptor<AppUsageEvent> eventCaptor = ArgumentCaptor.forClass(AppUsageEvent.class);
        verify(usageEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(AppUsageEventType.LOGIN);
        verify(pageMetricRepository, never()).incrementPageView(any(), any(), any(), any(), any(), any(), any(), any());
        service.flushPendingPageViews();
        verify(pageMetricRepository).incrementPageViews(
            any(),
            any(),
            eq("SACCO-1"),
            eq("ST-1"),
            eq("/admin/dashboard"),
            eq("Desktop"),
            eq("Chrome"),
            eq(1L),
            any()
        );
    }

    @Test
    void repeatedPageHitsDoNotAppendRawPageViewEvents() {
        AppUsageAnalyticsService service = service();
        AppUserPrincipal principal = principal("SACCO-1", "ST-1", Position.MINOR_ADMIN);
        MockHttpServletRequest request = htmlRequest("/admin/dashboard");

        service.recordAuthenticatedRequest(principal, request);
        service.recordAuthenticatedRequest(principal, request);

        ArgumentCaptor<AppUsageEvent> eventCaptor = ArgumentCaptor.forClass(AppUsageEvent.class);
        verify(usageEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo(AppUsageEventType.LOGIN);
        verify(pageMetricRepository, never()).incrementPageView(any(), any(), any(), any(), any(), any(), any(), any());
        service.flushPendingPageViews();
        verify(pageMetricRepository).incrementPageViews(
            any(),
            any(),
            eq("SACCO-1"),
            eq("ST-1"),
            eq("/admin/dashboard"),
            eq("Desktop"),
            eq("Chrome"),
            eq(2L),
            any()
        );
    }

    @Test
    void excludesAnalyticsEndpointFromRecordedUsage() {
        AppUsageAnalyticsService service = service();

        service.recordAuthenticatedRequest(principal("SACCO-1", "ST-1", Position.MINOR_ADMIN), htmlRequest("/admin/dashboard/usage-activity"));

        verify(usageEventRepository, never()).save(any());
        verify(pageMetricRepository, never()).incrementPageView(any(), any(), any(), any(), any(), any(), any(), any());
        verify(pageMetricRepository, never()).incrementPageViews(any(), any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void scopesActiveUsersForMinorAdminDashboard() {
        AppUsageAnalyticsService service = service();
        stubDashboardQueries();
        when(registeredSaccoRepository.findBySaccoIdIn(any())).thenReturn(List.of(registeredSacco("SACCO-1", "Arusha Central SACCO")));
        service.recordAuthenticatedRequest(principal("SACCO-1", "ST-1", Position.MINOR_ADMIN), htmlRequest("/admin/dashboard"));
        service.recordAuthenticatedRequest(principal("SACCO-2", "ST-2", Position.MANAGER), htmlRequest("/manager/dashboard"));

        AppUsageAnalyticsService.UsageDashboardPayload payload = service.dashboard("7d", "SACCO-1", "ST-1");

        assertThat(payload.activeSessions()).isEqualTo(1);
        assertThat(payload.activeUserRows()).hasSize(1);
        assertThat(payload.activeUserRows().getFirst().saccoId()).isEqualTo("Arusha Central SACCO");
        assertThat(payload.activeUserRows().getFirst().roles()).isEqualTo("Institution Admin");
    }

    @Test
    void returnsHistoricalSummariesForSelectedRange() {
        AppUsageAnalyticsService service = service();
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String yesterday = keyFormatter.format(LocalDate.now().minusDays(1));
        String today = keyFormatter.format(LocalDate.now());
        when(pageMetricRepository.countPageViews(any(), any(), eq(null), eq(null))).thenReturn(54L);
        when(usageEventRepository.countScoped(eq(AppUsageEventType.LOGIN), any(), any(), eq(null), eq(null))).thenReturn(7L);
        when(usageEventRepository.countEventsByBucket(eq(AppUsageEventType.LOGIN.name()), any(), any(), eq(null), eq(null), eq("YYYY-MM-DD"), any()))
            .thenReturn(List.of(row(yesterday, 12), row(today, 18)));
        when(pageMetricRepository.topPages(any(), any(), eq(null), eq(null)))
            .thenReturn(List.of(topPage("/admin/dashboard", "SACCO-1", 20)));
        when(pageMetricRepository.deviceBreakdown(any(), any(), eq(null), eq(null)))
            .thenReturn(List.of(row("Mobile", 3), row("Desktop", 1)));
        when(pageMetricRepository.browserBreakdown(any(), any(), eq(null), eq(null)))
            .thenReturn(List.of(row("Chrome", 4)));
        when(registeredSaccoRepository.findBySaccoIdIn(any())).thenReturn(List.of(registeredSacco("SACCO-1", "Arusha Central SACCO")));

        AppUsageAnalyticsService.UsageDashboardPayload payload = service.dashboard("30d", null, null);

        assertThat(payload.range()).isEqualTo("30d");
        assertThat(payload.pageViews()).isEqualTo(54);
        assertThat(payload.logins()).isEqualTo(7);
        assertThat(payload.timeline()).hasSize(30);
        assertThat(payload.timeline()).extracting(AppUsageAnalyticsService.UsageCountRow::total)
            .contains(12L, 18L);
        assertThat(payload.topPages().getFirst().saccoId()).isEqualTo("Arusha Central SACCO");
        assertThat(payload.devices().getFirst().percent()).isEqualTo(75);
    }

    @Test
    void cleanupDeletesEventsOlderThanRetentionWindow() {
        AppUsageAnalyticsService service = service();

        service.deleteExpiredUsageEvents();

        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(usageEventRepository).deleteByOccurredAtBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBefore(new ApplicationClock("Africa/Nairobi").now().minusDays(89));
        verify(pageMetricRepository).deleteByBucketStartBefore(any());
    }

    private AppUsageAnalyticsService service() {
        return new AppUsageAnalyticsService(usageEventRepository, pageMetricRepository, registeredSaccoRepository, new ApplicationClock("Africa/Nairobi"));
    }

    private void stubDashboardQueries() {
        when(pageMetricRepository.countPageViews(any(), any(), any(), any())).thenReturn(0L);
        when(usageEventRepository.countScoped(eq(AppUsageEventType.LOGIN), any(), any(), any(), any())).thenReturn(0L);
        when(usageEventRepository.countEventsByBucket(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(pageMetricRepository.topPages(any(), any(), any(), any())).thenReturn(List.of());
        when(pageMetricRepository.deviceBreakdown(any(), any(), any(), any())).thenReturn(List.of());
        when(pageMetricRepository.browserBreakdown(any(), any(), any(), any())).thenReturn(List.of());
    }

    private MockHttpServletRequest htmlRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Accept", "text/html");
        request.addHeader("User-Agent", "Mozilla/5.0 Chrome/125.0 Safari/537.36");
        request.getSession(true);
        return request;
    }

    private AppUserPrincipal principal(String saccoId, String stationId, Position role) {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        member.setMemberNo("USR-" + role.name());
        member.setFullName(role.name() + " User");
        member.setEmail(role.name().toLowerCase() + "@example.com");
        member.setSaccoId(saccoId);
        member.setStationId(stationId);
        member.setMemberAccount(role == Position.MEMBER);
        member.setPosition(role);
        member.setStaffRoles(new LinkedHashSet<>(role == Position.MEMBER ? Set.of() : Set.of(role)));
        return new AppUserPrincipal(member, Set.of());
    }

    private AppUsageEventRepository.CountRow row(String label, long total) {
        return new AppUsageEventRepository.CountRow() {
            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private AppUsageEventRepository.TopPageRow topPage(String label, String saccoId, long total) {
        return new AppUsageEventRepository.TopPageRow() {
            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public String getSaccoId() {
                return saccoId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private RegisteredSacco registeredSacco(String saccoId, String saccoName) {
        return RegisteredSacco.builder()
            .saccoId(saccoId)
            .saccoName(saccoName)
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
