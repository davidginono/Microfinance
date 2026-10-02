package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalAccountStatusServiceTest {
    @Mock private LoanAnalyticsService loanAnalyticsService;
    @InjectMocks private ExternalAccountStatusService service;

    @Test
    void creditProfilePreservesClientInstitutionAndBranchScope() {
        UUID memberId = UUID.randomUUID();
        Member member = Member.builder().id(memberId).saccoId("IAA").stationId("ST-1").build();
        when(loanAnalyticsService.activeLoanAmount(memberId, "IAA", "ST-1"))
            .thenReturn(new BigDecimal("770000.50"));
        when(loanAnalyticsService.defaultedRiskLoanCount(memberId, "IAA", "ST-1")).thenReturn(2L);

        var profile = service.resolve(member);

        assertThat(profile.isAvailable()).isTrue();
        assertThat(profile.isPending()).isFalse();
        assertThat(profile.getActiveExposureLabel()).isEqualTo("TZS 770,000.5");
        assertThat(profile.getRiskHistoryLabel()).isEqualTo("2 PAR/default record(s)");
        verify(loanAnalyticsService).activeLoanAmount(memberId, "IAA", "ST-1");
        verify(loanAnalyticsService).defaultedRiskLoanCount(memberId, "IAA", "ST-1");
    }

    @Test
    void missingClientDoesNotBecomeAZeroExposureOrCleanHistory() {
        var profile = service.resolve(null);

        assertThat(profile.isAvailable()).isFalse();
        assertThat(profile.isPending()).isFalse();
        assertThat(profile.getActiveExposureLabel()).isEqualTo("-");
        assertThat(profile.getRiskHistoryLabel()).isEqualTo("-");
        verifyNoInteractions(loanAnalyticsService);
    }
}
