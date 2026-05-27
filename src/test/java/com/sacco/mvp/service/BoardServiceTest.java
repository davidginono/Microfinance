package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private OutboxService outboxService;
    @Mock private LoanProductWorkflowService loanProductWorkflowService;
    @Mock private WorkflowRoutingService workflowRoutingService;
    @Mock private LoanProductSettingRepository loanProductSettingRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;

    @InjectMocks
    private BoardService boardService;

    @Test
    void marksBoardApprovedWhenQuorumReached() {
        // Scenario: board approval advances only after the configured quorum is reached.
        // Given one more approving board decision satisfies the required reviewer count
        // When the board member approves with a verified signature
        // Then the signature is stored and routing advances the loan.
        UUID loanId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        OffsetDateTime verifiedAt = OffsetDateTime.now();

        BoardReview review = BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .boardMemberId(boardId)
            .decision(BoardDecision.PENDING)
            .createdAt(OffsetDateTime.now())
            .build();

        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId(saccoId)
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(BigDecimal.TEN)
            .tenorMonths(1)
            .status(LoanStatus.AWAITING_BOARD)
            .formData("{}")
            .requiredGuarantors(3)
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();

        when(boardReviewRepository.findByLoanApplicationIdAndBoardMemberIdAndReviewStage(
            loanId, boardId, ApprovalWorkflowStage.BOARD)).thenReturn(Optional.of(review));
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(boardReviewRepository.countByLoanApplicationIdAndReviewStageAndDecision(
            loanId, ApprovalWorkflowStage.BOARD, BoardDecision.APPROVED)).thenReturn(2L);
        when(boardReviewRepository.countByLoanApplicationIdAndReviewStageAndDecision(
            loanId, ApprovalWorkflowStage.BOARD, BoardDecision.REJECTED)).thenReturn(0L);
        when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(new LoanProductWorkflowService.WorkflowDefinition(
            java.util.List.of(ApprovalWorkflowStage.BOARD, ApprovalWorkflowStage.DISBURSEMENT_OFFICER),
            ApprovalWorkflowStage.MANAGER,
            false,
            false,
            true,
            3,
            2,
            2,
            false,
            4
        ));
        when(workflowRoutingService.advanceAfterApproval(app, ApprovalWorkflowStage.BOARD, boardId))
            .thenAnswer(invocation -> {
                app.setStatus(LoanStatus.BOARD_APPROVED);
                return LoanStatus.BOARD_APPROVED;
            });
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        boardService.decide(loanId, boardId, BoardDecision.APPROVED, "Looks good", "Board Signer", verifiedAt);

        assertThat(app.getStatus()).isEqualTo(LoanStatus.BOARD_APPROVED);
        assertThat(review.getBoardSignatureText()).isEqualTo("Board Signer");
        assertThat(review.getBoardSignatureVerifiedAt()).isEqualTo(verifiedAt);
        verify(workflowRoutingService).advanceAfterApproval(app, ApprovalWorkflowStage.BOARD, boardId);
    }

    @Test
    void rejectDecisionDoesNotStoreSignature() {
        // Scenario: rejected board decisions must not retain approval signature metadata.
        // Given a board member rejects the loan
        // When the decision is stored
        // Then any submitted signature text is discarded.
        UUID loanId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        BoardReview review = BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .boardMemberId(boardId)
            .decision(BoardDecision.PENDING)
            .createdAt(OffsetDateTime.now())
            .build();

        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("CIRCLE-1001")
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(BigDecimal.TEN)
            .tenorMonths(1)
            .status(LoanStatus.AWAITING_BOARD)
            .formData("{}")
            .requiredGuarantors(3)
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();

        when(boardReviewRepository.findByLoanApplicationIdAndBoardMemberIdAndReviewStage(
            loanId, boardId, ApprovalWorkflowStage.BOARD)).thenReturn(Optional.of(review));
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(boardReviewRepository.countByLoanApplicationIdAndReviewStageAndDecision(
            loanId, ApprovalWorkflowStage.BOARD, BoardDecision.APPROVED)).thenReturn(0L);
        when(boardReviewRepository.countByLoanApplicationIdAndReviewStageAndDecision(
            loanId, ApprovalWorkflowStage.BOARD, BoardDecision.REJECTED)).thenReturn(1L);
        when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(new LoanProductWorkflowService.WorkflowDefinition(
            java.util.List.of(ApprovalWorkflowStage.BOARD, ApprovalWorkflowStage.DISBURSEMENT_OFFICER),
            ApprovalWorkflowStage.MANAGER,
            false,
            false,
            true,
            3,
            1,
            1,
            false,
            4
        ));

        boardService.decide(loanId, boardId, BoardDecision.REJECTED, "Insufficient support", "Should Clear", OffsetDateTime.now());

        assertThat(review.getBoardSignatureText()).isNull();
        assertThat(review.getBoardSignatureVerifiedAt()).isNull();
        assertThat(review.getDecision()).isEqualTo(BoardDecision.REJECTED);
    }

    @Test
    void loanOfficerApprovalAdvancesToNextConfiguredManagerStage() {
        // Scenario: customized products may route through a loan officer before manager review.
        // Given a loan officer approval completes that configured stage
        // When the decision is recorded
        // Then workflow routing advances the loan to the next configured stage.
        UUID loanId = UUID.randomUUID();
        UUID loanOfficerId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        BoardReview review = BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .boardMemberId(loanOfficerId)
            .reviewStage(ApprovalWorkflowStage.LOAN_OFFICER)
            .decision(BoardDecision.PENDING)
            .createdAt(OffsetDateTime.now())
            .build();
        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId(saccoId)
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .status(LoanStatus.AWAITING_LOAN_OFFICER)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        when(boardReviewRepository.findByLoanApplicationIdAndBoardMemberIdAndReviewStage(
            loanId, loanOfficerId, ApprovalWorkflowStage.LOAN_OFFICER)).thenReturn(Optional.of(review));
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(boardReviewRepository.countByLoanApplicationIdAndReviewStageAndDecision(
            loanId, ApprovalWorkflowStage.LOAN_OFFICER, BoardDecision.APPROVED)).thenReturn(1L);
        when(boardReviewRepository.countByLoanApplicationIdAndReviewStageAndDecision(
            loanId, ApprovalWorkflowStage.LOAN_OFFICER, BoardDecision.REJECTED)).thenReturn(0L);
        when(loanProductWorkflowService.resolveForApplication(app)).thenReturn(new LoanProductWorkflowService.WorkflowDefinition(
            java.util.List.of(
                ApprovalWorkflowStage.LOAN_OFFICER,
                ApprovalWorkflowStage.MANAGER,
                ApprovalWorkflowStage.DISBURSEMENT_OFFICER
            ),
            ApprovalWorkflowStage.LOAN_OFFICER,
            true,
            2,
            true,
            1,
            false,
            3,
            0,
            0,
            false,
            4,
            true
        ));
        when(workflowRoutingService.advanceAfterApproval(app, ApprovalWorkflowStage.LOAN_OFFICER, loanOfficerId))
            .thenAnswer(invocation -> {
                app.setStatus(LoanStatus.READY_FOR_MANAGER);
                return LoanStatus.READY_FOR_MANAGER;
            });

        boardService.decide(loanId, loanOfficerId, ApprovalWorkflowStage.LOAN_OFFICER,
            BoardDecision.APPROVED, "Looks good", "Loan Officer", OffsetDateTime.now());

        assertThat(app.getStatus()).isEqualTo(LoanStatus.READY_FOR_MANAGER);
        verify(workflowRoutingService).advanceAfterApproval(app, ApprovalWorkflowStage.LOAN_OFFICER, loanOfficerId);
    }

    @Test
    void undoDecisionClearsStoredSignature() {
        // Scenario: board decisions are final in this workflow and cannot be reversed after approval.
        // Given a loan already marked Board Approved
        // When the board member tries to undo the decision
        // Then the service rejects the reversal.
        UUID loanId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        BoardReview review = BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .boardMemberId(boardId)
            .decision(BoardDecision.APPROVED)
            .comment("Looks good")
            .boardSignatureText("Board Signer")
            .boardSignatureVerifiedAt(OffsetDateTime.now())
            .decidedAt(OffsetDateTime.now())
            .createdAt(OffsetDateTime.now())
            .build();

        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId("CIRCLE-1001")
            .applicantMemberId(UUID.randomUUID())
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(BigDecimal.TEN)
            .tenorMonths(1)
            .status(LoanStatus.BOARD_APPROVED)
            .formData("{}")
            .requiredGuarantors(3)
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> boardService.undoDecision(loanId, boardId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Board review decisions cannot be reversed.");
    }
}
