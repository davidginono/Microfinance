package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
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
    @Mock private SaccoSettingsRepository saccoSettingsRepository;
    @Mock private OutboxService outboxService;

    @InjectMocks
    private BoardService boardService;

    @Test
    void marksBoardApprovedWhenQuorumReached() {
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

        when(boardReviewRepository.findByLoanApplicationIdAndBoardMemberId(loanId, boardId)).thenReturn(Optional.of(review));
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(boardReviewRepository.countByLoanApplicationIdAndDecision(loanId, BoardDecision.APPROVED)).thenReturn(2L);
        when(boardReviewRepository.countByLoanApplicationIdAndDecision(loanId, BoardDecision.REJECTED)).thenReturn(0L);
        when(saccoSettingsRepository.findById(saccoId)).thenReturn(Optional.of(
            SaccoSettings.builder()
                .saccoId(saccoId)
                .externalStationId(saccoId)
                .requiredGuarantors(3)
                .boardSize(3)
                .boardQuorum(2)
                .maxLoanSavingsRatio(new BigDecimal("0.3333"))
                .defaultLanguage("en")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build()));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        boardService.decide(loanId, boardId, BoardDecision.APPROVED, "Looks good", "Board Signer", verifiedAt);

        assertThat(app.getStatus()).isEqualTo(LoanStatus.BOARD_APPROVED);
        assertThat(review.getBoardSignatureText()).isEqualTo("Board Signer");
        assertThat(review.getBoardSignatureVerifiedAt()).isEqualTo(verifiedAt);
        verify(outboxService).enqueue(eq("LOAN"), eq(loanId), eq("BOARD_APPROVED"), eq(app.getApplicantMemberId()), any());
    }

    @Test
    void rejectDecisionDoesNotStoreSignature() {
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

        when(boardReviewRepository.findByLoanApplicationIdAndBoardMemberId(loanId, boardId)).thenReturn(Optional.of(review));
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(boardReviewRepository.countByLoanApplicationIdAndDecision(loanId, BoardDecision.APPROVED)).thenReturn(0L);
        when(boardReviewRepository.countByLoanApplicationIdAndDecision(loanId, BoardDecision.REJECTED)).thenReturn(1L);

        boardService.decide(loanId, boardId, BoardDecision.REJECTED, "Insufficient support", "Should Clear", OffsetDateTime.now());

        assertThat(review.getBoardSignatureText()).isNull();
        assertThat(review.getBoardSignatureVerifiedAt()).isNull();
        assertThat(review.getDecision()).isEqualTo(BoardDecision.REJECTED);
    }

    @Test
    void undoDecisionClearsStoredSignature() {
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

        when(boardReviewRepository.findByLoanApplicationIdAndBoardMemberId(loanId, boardId)).thenReturn(Optional.of(review));
        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(boardReviewRepository.countByLoanApplicationIdAndDecision(loanId, BoardDecision.APPROVED)).thenReturn(0L);
        when(boardReviewRepository.countByLoanApplicationIdAndDecision(loanId, BoardDecision.REJECTED)).thenReturn(0L);
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        boardService.undoDecision(loanId, boardId);

        assertThat(review.getDecision()).isEqualTo(BoardDecision.PENDING);
        assertThat(review.getComment()).isNull();
        assertThat(review.getBoardSignatureText()).isNull();
        assertThat(review.getBoardSignatureVerifiedAt()).isNull();
        assertThat(review.getDecidedAt()).isNull();
    }
}
