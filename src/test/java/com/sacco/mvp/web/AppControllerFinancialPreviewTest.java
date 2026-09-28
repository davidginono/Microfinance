package com.sacco.mvp.web;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.EligibilityService;
import com.sacco.mvp.service.FinancialDetailsService;
import com.sacco.mvp.service.FormSchemaService;
import com.sacco.mvp.service.LoanPresentationService;
import com.sacco.mvp.service.LoanWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerFinancialPreviewTest {
    @Mock private LoanWorkflowService loanWorkflowService;
    @Mock private FormSchemaService formSchemaService;
    @Mock private FinancialDetailsService financialDetailsService;
    @Mock private EligibilityService eligibilityService;
    @Mock private LoanPresentationService loanPresentationService;
    @Mock private AppUserPrincipal principal;

    @InjectMocks private AppController controller;

    @Test
    void calculatorDoesNotApplyNewApplicationLocks() {
        String saccoId = "SACCO-1";
        UUID memberId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100000");
        int tenorMonths = 12;
        LoanProductSetting product = LoanProductSetting.builder()
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .build();
        Map<String, Object> snapshot = Map.of(
            "interestRate", BigDecimal.ZERO,
            "principalPlusInterest", amount
        );

        when(principal.getSaccoId()).thenReturn(saccoId);
        when(principal.getMemberId()).thenReturn(memberId);
        when(formSchemaService.getSchema(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(financialDetailsService.generateSnapshot(
            saccoId, memberId, product, amount, tenorMonths, null
        )).thenReturn(snapshot);
        when(financialDetailsService.toJson(snapshot)).thenReturn("{}");
        when(loanPresentationService.parseFinancialFields("{}")).thenReturn(Map.of("Loan Amount", "TSh 100,000.00"));
        when(eligibilityService.check(eq(saccoId), eq(memberId), eq(product), eq(amount), anyMap(), anyMap())).thenReturn(
            new EligibilityService.EligibilityResult(
                true,
                new BigDecimal("3"),
                new BigDecimal("50000"),
                new BigDecimal("150000")
            )
        );

        var response = controller.financialPreview(
            principal, LoanType.DEVELOPMENT_LOAN, amount, tenorMonths, null, null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Loan calculations loaded");
        verify(loanWorkflowService, never()).assertCanApplyForProduct(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void financialPreviewScheduleUsesLoadedInterestAmountForFlatRateRows() {
        String saccoId = "SACCO-1";
        UUID memberId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000000");
        int tenorMonths = 6;
        LoanProductSetting product = LoanProductSetting.builder()
            .saccoId(saccoId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .build();
        Map<String, Object> snapshot = Map.of(
            "interestRate", new BigDecimal("0.1200"),
            "interestAmount", new BigDecimal("120000.00"),
            "principalPlusInterest", new BigDecimal("1120000.00"),
            "interestMethod", "FLAT_RATE"
        );

        when(principal.getSaccoId()).thenReturn(saccoId);
        when(principal.getMemberId()).thenReturn(memberId);
        when(formSchemaService.getSchema(saccoId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(financialDetailsService.generateSnapshot(
            saccoId, memberId, product, amount, tenorMonths, null
        )).thenReturn(snapshot);
        when(financialDetailsService.toJson(snapshot)).thenReturn("{}");
        when(loanPresentationService.parseFinancialFields("{}")).thenReturn(Map.of("Interest", "TSh 120,000.00"));
        when(eligibilityService.check(eq(saccoId), eq(memberId), eq(product), eq(amount), anyMap(), anyMap())).thenReturn(
            new EligibilityService.EligibilityResult(
                true,
                new BigDecimal("3"),
                new BigDecimal("500000"),
                new BigDecimal("1500000")
            )
        );

        var response = controller.financialPreview(
            principal, LoanType.DEVELOPMENT_LOAN, amount, tenorMonths, null, null
        );

        List<Map<String, String>> rows = (List<Map<String, String>>) response.getBody().get("repaymentSchedule");

        assertThat(rows).hasSize(6);
        assertThat(rows.get(0)).containsEntry("pmtNo", "1");
        assertThat(rows.get(0)).containsEntry("month", "Month 1");
        assertThat(rows.get(0)).containsEntry("beginningBalance", "TSh 1,120,000");
        assertThat(rows.get(0)).containsEntry("payment", "TSh 186,666.67");
        assertThat(rows.get(0)).containsEntry("loanAmount", "TSh 166,666.67");
        assertThat(rows.get(0)).containsEntry("interest", "TSh 20,000");
        assertThat(rows.get(0)).containsEntry("endingBalance", "TSh 933,333.33");
        assertThat(rows.get(5)).containsEntry("interest", "TSh 20,000");
        assertThat(rows.get(5)).containsEntry("endingBalance", "TSh 0");
    }
}
