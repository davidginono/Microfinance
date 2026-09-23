package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.AuditLogRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.OutboxEventRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.repository.SavingsAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminServiceTest {
    @Mock private RegisteredSaccoRepository registeredSaccoRepository;
    @Mock private SaccoStationRepository saccoStationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private SavingsAccountRepository savingsAccountRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private SaccoLogoStorageService saccoLogoStorageService;

    private PlatformAdminService platformAdminService;

    @BeforeEach
    void setUp() {
        platformAdminService = new PlatformAdminService(
            registeredSaccoRepository,
            saccoStationRepository,
            memberRepository,
            loanApplicationRepository,
            savingsAccountRepository,
            auditLogRepository,
            outboxEventRepository,
            saccoSettingsRepository,
            saccoLogoStorageService
        );
    }

    @Test
    void saccoDetailExposesScopedLoanApplicationStatusCounts() {
        OffsetDateTime now = OffsetDateTime.now();
        RegisteredSacco sacco = RegisteredSacco.builder()
            .saccoId("SACCO-1")
            .saccoName("Example SACCO")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        SaccoStation station = SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        when(registeredSaccoRepository.findById("SACCO-1")).thenReturn(Optional.of(sacco));
        when(saccoStationRepository.findBySaccoIdAndActiveTrueOrderByStationIdAsc("SACCO-1"))
            .thenReturn(List.of(station));
        when(memberRepository.countByStatusForScope("SACCO-1", "ST-1")).thenReturn(List.of());
        when(loanApplicationRepository.summarizeLoansForScope("SACCO-1", "ST-1")).thenReturn(Optional.empty());
        when(loanApplicationRepository.countByStatusForScope("SACCO-1", "ST-1"))
            .thenReturn(List.of(
                statusCount(LoanStatus.PAID, 1),
                statusCount(LoanStatus.READY_FOR_MANAGER, 3)
            ));
        when(loanApplicationRepository.findRecentForScope(eq("SACCO-1"), eq("ST-1"), any()))
            .thenReturn(List.of());
        when(memberRepository.findAllById(any())).thenReturn(List.of());
        when(saccoSettingsRepository.findById("SACCO-1")).thenReturn(Optional.empty());
        when(auditLogRepository.searchEventLogViewScoped(
            isNull(), isNull(), isNull(), eq("SACCO-1"), eq("ST-1"), any()))
            .thenReturn(new PageImpl<>(List.of()));

        PlatformAdminService.SaccoDetailView detail = platformAdminService.saccoDetail(" sacco-1 ", "ST-1");

        assertThat(detail.getTotalLoanApplicationCount()).isEqualTo(4);
        assertThat(detail.getLoanStatusCounts())
            .extracting(
                PlatformAdminService.LoanStatusCount::getStatus,
                PlatformAdminService.LoanStatusCount::getStatusLabel,
                PlatformAdminService.LoanStatusCount::getCount
            )
            .containsExactly(
                tuple(LoanStatus.READY_FOR_MANAGER, "On Review By Manager", 3L),
                tuple(LoanStatus.PAID, "Paid", 1L)
            );
        verify(loanApplicationRepository).countByStatusForScope("SACCO-1", "ST-1");
    }

    private static LoanApplicationRepository.StatusCountProjection statusCount(LoanStatus status, long total) {
        return new LoanApplicationRepository.StatusCountProjection() {
            @Override
            public LoanStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
