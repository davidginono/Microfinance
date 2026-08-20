package com.sacco.mvp.web;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.LoanPaymentSummarySyncService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanProductDisplayService;
import com.sacco.mvp.service.LoanWorkflowService;
import com.sacco.mvp.service.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerActiveLoanBalanceRefreshTest {
    @Mock private LoanPaymentSummarySyncService loanPaymentSummarySyncService;
    @Mock private LoanWorkflowService loanWorkflowService;
    @Mock private LoanPresentationService loanPresentationService;
    @Mock private LoanProductDisplayService loanProductDisplayService;
    @Mock private UserSettingsService userSettingsService;
    @Mock private MessageSource messageSource;
    @Mock private AppUserPrincipal principal;

    @InjectMocks private AppController controller;

    @Test
    @SuppressWarnings("unchecked")
    void activeLoanBalanceRefreshReturnsUpdatedRows() {
        UUID memberId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        LoanApplication loan = loan(applicationId, memberId);
        Map<LoanType, String> productNames = Map.of(LoanType.EDUCATION_LOAN, "Education Loan");

        when(principal.getMemberId()).thenReturn(memberId);
        when(principal.getSaccoId()).thenReturn("IAA");
        when(loanPaymentSummarySyncService.refreshMemberActiveLoanPaymentSummaries(memberId, 50))
            .thenReturn(new LoanPaymentSummarySyncService.MemberRefreshResult(
                LoanPaymentSummarySyncService.RefreshStatus.UPDATED,
                "Loan balances updated.",
                List.of()
            ));
        when(loanWorkflowService.findActiveDisbursedLoans(memberId)).thenReturn(List.of(loan));
        when(userSettingsService.notificationPrefs(memberId)).thenReturn(Optional.empty());
        when(loanProductDisplayService.namesForSacco("IAA")).thenReturn(productNames);
        when(loanProductDisplayService.displayName(loan, productNames)).thenReturn("Education Loan");
        when(messageSource.getMessage(anyString(), isNull(), anyString(), any(Locale.class)))
            .thenAnswer(invocation -> invocation.getArgument(2));
        when(loanPresentationService.countdownLabel(loan.getFinalDueDate())).thenReturn("163 days left");
        when(loanPresentationService.formatMoneyDisplay(eq(new BigDecimal("700000.00")))).thenReturn("TSh 700,000");
        when(loanPresentationService.activeLoanOutstandingBalance(loan)).thenReturn(new BigDecimal("770000.00"));
        when(loanPresentationService.activeLoanPaidAmount(loan)).thenReturn(new BigDecimal("80000.00"));
        when(loanPresentationService.activeLoanOutstandingPrincipal(loan)).thenReturn(new BigDecimal("650000.00"));
        when(loanPresentationService.activeLoanOutstandingInterest(loan)).thenReturn(new BigDecimal("20000.00"));
        when(loanPresentationService.activeLoanTotalPrincipalPaid(loan)).thenReturn(new BigDecimal("75000.00"));
        when(loanPresentationService.activeLoanTotalInterestPaid(loan)).thenReturn(new BigDecimal("5000.00"));
        when(loanPresentationService.activeLoanLastPaymentDateLabel(loan)).thenReturn("2024-04-10");
        when(loanPresentationService.formatMoneyDisplay(eq(new BigDecimal("770000.00")))).thenReturn("TSh 770,000");
        when(loanPresentationService.formatMoneyDisplay(eq(new BigDecimal("80000.00")))).thenReturn("TSh 80,000");
        when(loanPresentationService.formatMoneyDisplay(eq(new BigDecimal("650000.00")))).thenReturn("TSh 650,000");
        when(loanPresentationService.formatMoneyDisplay(eq(new BigDecimal("20000.00")))).thenReturn("TSh 20,000");
        when(loanPresentationService.formatMoneyDisplay(eq(new BigDecimal("75000.00")))).thenReturn("TSh 75,000");
        when(loanPresentationService.formatMoneyDisplay(eq(new BigDecimal("5000.00")))).thenReturn("TSh 5,000");

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
