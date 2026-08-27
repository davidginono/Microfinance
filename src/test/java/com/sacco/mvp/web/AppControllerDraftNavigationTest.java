package com.sacco.mvp.web;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.FormSchemaService;
import com.sacco.mvp.service.LoanWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerDraftNavigationTest {
    @Mock private LoanWorkflowService loanWorkflowService;
    @Mock private FormSchemaService formSchemaService;
    @Mock private AppUserPrincipal principal;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private AppController controller;

    @Test
    void saveDraftReturnsApplicantToSubmitStep() {
        String saccoId = "SACCO-1";
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("700000");
        int tenorMonths = 6;
        LoanProductSetting product = LoanProductSetting.builder()
            .id(productId)
            .saccoId(saccoId)
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .build();
        LoanApplication saved = LoanApplication.builder()
            .id(applicationId)
            .build();
        HttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(principal.getSaccoId()).thenReturn(saccoId);
        when(principal.getMemberId()).thenReturn(memberId);
        when(formSchemaService.getSchema(saccoId, productId, LoanType.CUSTOMIZED_LOAN)).thenReturn(product);
        when(loanWorkflowService.saveDraft(
            eq(saccoId),
            eq(memberId),
            eq(productId),
            eq(LoanType.CUSTOMIZED_LOAN),
            eq(amount),
            eq(tenorMonths),
            anyMap(),
            isNull(),
            anyList(),
            isNull(),
            isNull(),
            isNull(),
            anyMap()
        )).thenReturn(saved);

        String result = controller.createDraft(
            principal,
            productId,
            LoanType.CUSTOMIZED_LOAN,
            amount,
            tenorMonths,
            null,
            "SAVE_DRAFT",
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of(),
            null,
            request,
            redirect,
            new ExtendedModelMap()
        );

        assertThat(result).isEqualTo("redirect:/app/loan-applications/" + applicationId + "/edit?step=5");
        assertThat(redirect.getFlashAttributes().get("message")).isEqualTo("Draft saved successfully. You can continue editing.");
    }

    @Test
    void saveDraftUsesGuarantorSelectionStateWhenDynamicSelectionInputsAreMissing() {
        String saccoId = "SACCO-1";
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("700000");
        int tenorMonths = 6;
        LoanProductSetting product = LoanProductSetting.builder()
            .id(productId)
            .saccoId(saccoId)
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .build();
        LoanApplication saved = LoanApplication.builder()
            .id(applicationId)
            .build();
        HttpServletRequest request = new MockHttpServletRequest();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(principal.getSaccoId()).thenReturn(saccoId);
        when(principal.getMemberId()).thenReturn(memberId);
        when(formSchemaService.getSchema(saccoId, productId, LoanType.CUSTOMIZED_LOAN)).thenReturn(product);
        when(loanWorkflowService.saveDraft(
            eq(saccoId),
            eq(memberId),
            eq(productId),
            eq(LoanType.CUSTOMIZED_LOAN),
            eq(amount),
            eq(tenorMonths),
            anyMap(),
            isNull(),
            eq(List.of(guarantorId.toString())),
            isNull(),
            isNull(),
            isNull(),
            anyMap()
        )).thenReturn(saved);

        String result = controller.createDraft(
            principal,
            productId,
            LoanType.CUSTOMIZED_LOAN,
            amount,
            tenorMonths,
            null,
            "SAVE_DRAFT",
            null,
            List.of(""),
            null,
            "[\"" + guarantorId + "\"]",
            null,
            null,
            Map.of(),
            null,
            request,
            redirect,
            new ExtendedModelMap()
        );

        assertThat(result).isEqualTo("redirect:/app/loan-applications/" + applicationId + "/edit?step=5");
    }

    @Test
    void saveDraftUsesRawRequestGuarantorSelectionWhenListBindingIsEmpty() {
        String saccoId = "SACCO-1";
        UUID memberId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("700000");
        int tenorMonths = 6;
        LoanProductSetting product = LoanProductSetting.builder()
            .id(productId)
            .saccoId(saccoId)
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .build();
        LoanApplication saved = LoanApplication.builder()
            .id(applicationId)
            .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("guarantorSelections", guarantorId.toString());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(principal.getSaccoId()).thenReturn(saccoId);
        when(principal.getMemberId()).thenReturn(memberId);
        when(formSchemaService.getSchema(saccoId, productId, LoanType.CUSTOMIZED_LOAN)).thenReturn(product);
        when(loanWorkflowService.saveDraft(
            eq(saccoId),
            eq(memberId),
            eq(productId),
            eq(LoanType.CUSTOMIZED_LOAN),
            eq(amount),
            eq(tenorMonths),
            anyMap(),
            isNull(),
            eq(List.of(guarantorId.toString())),
            isNull(),
            isNull(),
            isNull(),
            anyMap()
        )).thenReturn(saved);

        String result = controller.createDraft(
            principal,
            productId,
            LoanType.CUSTOMIZED_LOAN,
            amount,
            tenorMonths,
            null,
            "SAVE_DRAFT",
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of(),
            null,
            request,
            redirect,
            new ExtendedModelMap()
        );

        assertThat(result).isEqualTo("redirect:/app/loan-applications/" + applicationId + "/edit?step=5");
    }

    @Test
    void submitDraftWithMissingGuarantorSelectionReturnsToGuarantorStep() {
        UUID memberId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        LoanApplication current = LoanApplication.builder()
            .id(applicationId)
            .status(LoanStatus.DRAFT)
            .requiredGuarantors(1)
            .build();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        when(principal.getMemberId()).thenReturn(memberId);
        when(loanWorkflowService.getMine(applicationId, memberId)).thenReturn(current);
        when(loanWorkflowService.submit(applicationId, memberId))
            .thenThrow(new IllegalStateException("Select exactly 1 guarantors before submitting."));

        String result = controller.submit(applicationId, principal, null, null, redirect);

        assertThat(result).isEqualTo("redirect:/app/loan-applications/" + applicationId + "/edit?step=3");
        assertThat(redirect.getFlashAttributes().get("error"))
            .isEqualTo("Select exactly 1 guarantors before submitting.");
    }
}
