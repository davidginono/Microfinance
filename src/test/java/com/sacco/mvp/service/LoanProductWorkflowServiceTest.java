package com.sacco.mvp.service;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanProductWorkflowServiceTest {
    @Mock private LoanProductSettingRepository loanProductSettingRepository;
    @Mock private SaccoSettingsRepository saccoSettingsRepository;

    @Test
    void resolvesWorkflowPermutationsFromProductSettings() {
        LoanProductWorkflowService service = new LoanProductWorkflowService(
            new ObjectMapper(),
            loanProductSettingRepository,
            saccoSettingsRepository
        );

        assertThat(service.resolveForProduct("SACCO-01", product(true, false, false, true, ApprovalWorkflowStage.MANAGER)).stages())
            .containsExactly(ApprovalWorkflowStage.MANAGER, ApprovalWorkflowStage.ACCOUNTANT, ApprovalWorkflowStage.DISBURSEMENT_OFFICER);

        assertThat(service.resolveForProduct("SACCO-01", product(false, true, false, false, ApprovalWorkflowStage.MANAGER)).stages())
            .containsExactly(ApprovalWorkflowStage.LOAN_OFFICER, ApprovalWorkflowStage.DISBURSEMENT_OFFICER);

        assertThat(service.resolveForProduct("SACCO-01", product(true, true, false, false, ApprovalWorkflowStage.LOAN_OFFICER)).stages())
            .containsExactly(ApprovalWorkflowStage.LOAN_OFFICER, ApprovalWorkflowStage.MANAGER, ApprovalWorkflowStage.DISBURSEMENT_OFFICER);

        assertThat(service.resolveForProduct("SACCO-01", product(false, false, true, true, ApprovalWorkflowStage.MANAGER)).stages())
            .containsExactly(ApprovalWorkflowStage.CREDIT_COMMITTEE, ApprovalWorkflowStage.ACCOUNTANT, ApprovalWorkflowStage.DISBURSEMENT_OFFICER);
    }

    @Test
    void keepsDisbursementClaimReleaseFlagInWorkflowDefinition() {
        LoanProductWorkflowService service = new LoanProductWorkflowService(
            new ObjectMapper(),
            loanProductSettingRepository,
            saccoSettingsRepository
        );

        LoanProductWorkflowService.WorkflowDefinition definition = service.resolveForProduct(
            "SACCO-01",
            product(false, true, false, false, ApprovalWorkflowStage.LOAN_OFFICER, false)
        );

        assertThat(definition.startStage()).isEqualTo(ApprovalWorkflowStage.LOAN_OFFICER);
        assertThat(definition.disbursementOfficerRequired()).isFalse();
        assertThat(definition.stages()).isEqualTo(List.of(
            ApprovalWorkflowStage.LOAN_OFFICER,
            ApprovalWorkflowStage.DISBURSEMENT_OFFICER
        ));
    }

    @Test
    void ordersManagerAndLoanOfficerByConfiguredPriorityBeyondOneAndTwo() {
        LoanProductWorkflowService service = new LoanProductWorkflowService(
            new ObjectMapper(),
            loanProductSettingRepository,
            saccoSettingsRepository
        );
        LoanProductSetting product = product(true, true, false, false, ApprovalWorkflowStage.MANAGER);
        product.setManagerPriority(4);
        product.setLoanOfficerPriority(3);

        LoanProductWorkflowService.WorkflowDefinition definition = service.resolveForProduct("SACCO-01", product);

        assertThat(definition.stages())
            .containsExactly(
                ApprovalWorkflowStage.LOAN_OFFICER,
                ApprovalWorkflowStage.MANAGER,
                ApprovalWorkflowStage.DISBURSEMENT_OFFICER
            );
        assertThat(definition.startStage()).isEqualTo(ApprovalWorkflowStage.LOAN_OFFICER);
    }

    @Test
    void ordersAllReviewStagesByConfiguredOpenPrioritySlots() {
        LoanProductWorkflowService service = new LoanProductWorkflowService(
            new ObjectMapper(),
            loanProductSettingRepository,
            saccoSettingsRepository
        );
        LoanProductSetting product = product(true, true, true, true, ApprovalWorkflowStage.MANAGER);
        product.setManagerPriority(4);
        product.setLoanOfficerPriority(3);
        product.setCommitteePriority(1);
        product.setAccountantPriority(2);

        assertThat(service.resolveForProduct("SACCO-01", product).stages())
            .containsExactly(
                ApprovalWorkflowStage.CREDIT_COMMITTEE,
                ApprovalWorkflowStage.ACCOUNTANT,
                ApprovalWorkflowStage.LOAN_OFFICER,
                ApprovalWorkflowStage.MANAGER,
                ApprovalWorkflowStage.DISBURSEMENT_OFFICER
            );
    }

    @Test
    void resolvesPriorityOrderFromSubmittedPolicySnapshot() {
        LoanProductWorkflowService service = new LoanProductWorkflowService(
            new ObjectMapper(),
            loanProductSettingRepository,
            saccoSettingsRepository
        );
        LoanApplication app = LoanApplication.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .status(LoanStatus.AWAITING_LOAN_OFFICER)
            .policySnapshot("""
                {
                  "approvalFlow": ["LOAN_OFFICER", "MANAGER", "BOARD", "ACCOUNTANT", "DISBURSEMENT_OFFICER"],
                  "workflowStartStage": "LOAN_OFFICER",
                  "managerReviewRequired": true,
                  "managerPriority": 2,
                  "loanOfficerReviewRequired": true,
                  "loanOfficerPriority": 1,
                  "committeeReviewRequired": true,
                  "committeePriority": 3,
                  "committeeMinimumVotes": 2,
                  "committeeApprovalThreshold": 2,
                  "accountantReviewRequired": true,
                  "accountantPriority": 4,
                  "disbursementOfficerRequired": true
                }
                """)
            .build();

        LoanProductWorkflowService.WorkflowDefinition definition = service.resolveForApplication(app);

        assertThat(definition.stages()).containsExactly(
            ApprovalWorkflowStage.LOAN_OFFICER,
            ApprovalWorkflowStage.MANAGER,
            ApprovalWorkflowStage.BOARD,
            ApprovalWorkflowStage.ACCOUNTANT,
            ApprovalWorkflowStage.DISBURSEMENT_OFFICER
        );
        assertThat(definition.managerPriority()).isEqualTo(2);
        assertThat(definition.loanOfficerPriority()).isEqualTo(1);
        assertThat(definition.startStage()).isEqualTo(ApprovalWorkflowStage.LOAN_OFFICER);
    }

    @Test
    void resolvesApplicationWorkflowByStoredProductIdWhenLoanTypesAreShared() {
        LoanProductWorkflowService service = new LoanProductWorkflowService(
            new ObjectMapper(),
            loanProductSettingRepository,
            saccoSettingsRepository
        );
        LoanProductSetting selectedProduct = product(false, true, false, false, ApprovalWorkflowStage.LOAN_OFFICER);
        selectedProduct.setLoanType(LoanType.EDUCATION_LOAN);
        LoanApplication app = LoanApplication.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .loanType(LoanType.EDUCATION_LOAN)
            .loanProductSettingId(selectedProduct.getId())
            .status(LoanStatus.DRAFT)
            .build();
        when(loanProductSettingRepository.findByIdAndSaccoId(selectedProduct.getId(), "SACCO-01"))
            .thenReturn(Optional.of(selectedProduct));

        LoanProductWorkflowService.WorkflowDefinition definition = service.resolveForApplication(app);

        assertThat(definition.stages()).containsExactly(
            ApprovalWorkflowStage.LOAN_OFFICER,
            ApprovalWorkflowStage.DISBURSEMENT_OFFICER
        );
        verify(loanProductSettingRepository, never())
            .findBySaccoIdAndLoanType("SACCO-01", LoanType.EDUCATION_LOAN);
    }

    private LoanProductSetting product(boolean manager,
                                       boolean loanOfficer,
                                       boolean committee,
                                       boolean accountant,
                                       ApprovalWorkflowStage startStage) {
        return product(manager, loanOfficer, committee, accountant, startStage, true);
    }

    private LoanProductSetting product(boolean manager,
                                       boolean loanOfficer,
                                       boolean committee,
                                       boolean accountant,
                                       ApprovalWorkflowStage startStage,
                                       boolean disbursementOfficerRequired) {
        OffsetDateTime now = OffsetDateTime.now();
        return LoanProductSetting.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .productName("Custom")
            .managerReviewRequired(manager)
            .loanOfficerReviewRequired(loanOfficer)
            .workflowStartStage(startStage)
            .committeeReviewRequired(committee)
            .committeePriority(3)
            .committeeMinimumVotes(committee ? 1 : 0)
            .committeeApprovalThreshold(committee ? 1 : 0)
            .accountantReviewRequired(accountant)
            .accountantPriority(4)
            .disbursementOfficerRequired(disbursementOfficerRequired)
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
