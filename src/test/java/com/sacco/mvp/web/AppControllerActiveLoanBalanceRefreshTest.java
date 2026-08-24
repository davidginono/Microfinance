package com.sacco.mvp.web;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ExternalAccountStatusService;
import com.sacco.mvp.service.ActiveLoanDisplayService;
import com.sacco.mvp.service.LoanPaymentSummarySyncService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanProductDisplayService;
import com.sacco.mvp.service.LoanWorkflowService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerActiveLoanBalanceRefreshTest {
    @Mock private LoanPaymentSummarySyncService loanPaymentSummarySyncService;
    @Mock private ActiveLoanDisplayService activeLoanDisplayService;
    @Mock private LoanWorkflowService loanWorkflowService;
    @Mock private LoanPresentationService loanPresentationService;
    @Mock private LoanProductDisplayService loanProductDisplayService;
    @Mock private MemberDirectoryService memberDirectoryService;
    @Mock private UserSettingsService userSettingsService;
    @Mock private ExternalAccountStatusService externalAccountStatusService;
    @Mock private MessageSource messageSource;
    @Mock private AppUserPrincipal principal;

    @InjectMocks private AppController controller;

    @Test
    void progressiveDashboardRendersShellWithoutSynchronousDashboardQueries() {
        ExternalAccountStatusService.ExternalAccountStatusView loadingStatus =
            ExternalAccountStatusService.ExternalAccountStatusView.loading("loan.loadingLiveBalances");
        when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
            .thenAnswer(invocation -> invocation.getArgument(2));
        when(externalAccountStatusService.loading("loan.loadingLiveBalances")).thenReturn(loadingStatus);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.dashboard(principal, true, model);

        assertThat(view).isEqualTo("app/dashboard");
        assertThat(model.get("dashboardProgressive")).isEqualTo(true);
        assertThat(model.get("dashboardExternalAccountStatus")).isSameAs(loadingStatus);
        assertThat((List<?>) model.get("statusChartRows")).isEmpty();
        assertThat((List<?>) model.get("activeLoanChartRows")).isEmpty();
        verify(loanWorkflowService, never()).memberDashboard(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeLoanBalanceRefreshReturnsUpdatedRows() {
        UUID memberId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        LoanApplication loan = loan(applicationId, memberId);
        Map<String, Object> row = activeLoanRow(applicationId);

        when(principal.getMemberId()).thenReturn(memberId);
        when(principal.getSaccoId()).thenReturn("IAA");
        when(loanPaymentSummarySyncService.refreshMemberActiveLoanPaymentSummaries(memberId, 50))
            .thenReturn(new LoanPaymentSummarySyncService.MemberRefreshResult(
                LoanPaymentSummarySyncService.RefreshStatus.UPDATED,
                "Loan balances updated.",
                List.of()
            ));
        when(loanWorkflowService.findActiveDisbursedLoans(memberId)).thenReturn(List.of(loan));
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.empty());
        when(userSettingsService.notificationPrefs(memberId)).thenReturn(Optional.empty());
        when(activeLoanDisplayService.memberDashboardRows(isNull(), eq("IAA"), eq(List.of(loan)), anySet()))
            .thenReturn(new ActiveLoanDisplayService.ActiveLoanDisplay(
                List.of(row),
                1,
                "TSh 770,000",
                "AVAILABLE",
                "Active loans loaded."
            ));

        var response = controller.refreshDashboardActiveLoanBalances(principal);

        assertThat(response.getBody()).containsEntry("status", "UPDATED");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.getBody().get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
            .containsEntry("applicationId", applicationId.toString())
            .containsEntry("loanId", "1001")
            .containsEntry("currentBalance", "TSh 770,000")
            .containsEntry("paidAmount", "TSh 80,000")
            .containsEntry("outstandingPrincipal", "TSh 650,000")
            .containsEntry("outstandingInterest", "TSh 20,000")
            .containsEntry("totalPrincipalPaid", "TSh 75,000")
            .containsEntry("totalInterestPaid", "TSh 5,000")
            .containsEntry("lastPaymentDate", "2024-04-10");
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeLoanDashboardEndpointReturnsMergedDisplayPayload() {
        UUID memberId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        LoanApplication loan = loan(applicationId, memberId);
        Map<String, Object> row = activeLoanRow(applicationId);

        when(principal.getMemberId()).thenReturn(memberId);
        when(principal.getSaccoId()).thenReturn("IAA");
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.empty());
        when(loanWorkflowService.findActiveDisbursedLoans(memberId)).thenReturn(List.of(loan));
        when(userSettingsService.notificationPrefs(memberId)).thenReturn(Optional.empty());
        when(activeLoanDisplayService.memberDashboardRows(isNull(), eq("IAA"), eq(List.of(loan)), anySet()))
            .thenReturn(new ActiveLoanDisplayService.ActiveLoanDisplay(
                List.of(row),
                1,
                "TSh 770,000",
                "AVAILABLE",
                "Active loans loaded."
            ));

        var response = controller.dashboardActiveLoans(principal);

        assertThat(response.getBody()).containsEntry("count", 1);
        assertThat(response.getBody()).containsEntry("totalExposure", "TSh 770,000");
        assertThat(response.getBody()).containsEntry("status", "AVAILABLE");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.getBody().get("rows");
        assertThat(rows).containsExactly(row);
    }

    @Test
    void activeLoanBalanceRefreshRouteRequiresMemberUpdateAccess() throws Exception {
        Method method = AppController.class.getMethod(
            "refreshDashboardActiveLoanBalances",
            AppUserPrincipal.class
        );

        assertThat(method.getAnnotation(PostMapping.class).value())
            .containsExactly("/dashboard/active-loans/balances/refresh");
        assertThat(method.getAnnotation(ResponseBody.class)).isNotNull();
        assertThat(method.getAnnotation(PreAuthorize.class).value())
            .contains("MEMBER_LOANS_VIEW")
            .contains("MEMBER_LOANS_UPDATE");
    }

    @Test
    void activeLoanDashboardRouteRequiresMemberViewAccess() throws Exception {
        Method method = AppController.class.getMethod(
            "dashboardActiveLoans",
            AppUserPrincipal.class
        );

        assertThat(method.getAnnotation(GetMapping.class).value())
            .containsExactly("/dashboard/active-loans");
        assertThat(method.getAnnotation(ResponseBody.class)).isNotNull();
        assertThat(method.getAnnotation(PreAuthorize.class).value())
            .contains("MEMBER_LOANS_VIEW");
    }

    private Map<String, Object> activeLoanRow(UUID applicationId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("applicationId", applicationId.toString());
        row.put("loanId", "1001");
        row.put("currentBalance", "TSh 770,000");
        row.put("paidAmount", "TSh 80,000");
        row.put("outstandingPrincipal", "TSh 650,000");
        row.put("outstandingInterest", "TSh 20,000");
        row.put("totalPrincipalPaid", "TSh 75,000");
        row.put("totalInterestPaid", "TSh 5,000");
        row.put("lastPaymentDate", "2024-04-10");
        row.put("scheduleAvailable", true);
        return row;
    }

    private LoanApplication loan(UUID applicationId, UUID memberId) {
        return LoanApplication.builder()
            .id(applicationId)
            .applicantMemberId(memberId)
            .saccoId("IAA")
            .stationId("ST01")
            .loanId("1001")
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal("700000.00"))
            .status(LoanStatus.DISBURSED)
            .createdAt(OffsetDateTime.parse("2026-07-08T10:00:00Z"))
            .disbursementDate(LocalDate.of(2026, 7, 8))
            .finalDueDate(LocalDate.of(2027, 1, 30))
            .build();
    }
}
