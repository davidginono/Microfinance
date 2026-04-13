package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.ReversalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReversalRequestService {
    private static final long REVERSAL_WINDOW_HOURS = 24L;
    private final ReversalRequestRepository reversalRequestRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanWorkflowService loanWorkflowService;
    private final RoleDirectoryService roleDirectoryService;
    private final OutboxService outboxService;

    @Transactional
    public void requestGuarantorUndo(UUID guarantorRequestId, UUID guarantorMemberId) {
        GuarantorRequest request = guarantorRequestRepository.findByIdAndGuarantorMemberId(guarantorRequestId, guarantorMemberId)
            .orElseThrow(() -> new IllegalArgumentException("Guarantor request not found"));
        if (request.getStatus() == GuarantorRequestStatus.PENDING) {
            throw new IllegalStateException("Respond to the guarantor request first before asking to be removed.");
        }
        if (!isWithinReversalWindow(request.getDecidedAt())) {
            throw new IllegalStateException("The 24-hour removal window for this guarantor decision has already closed.");
        }
        LoanApplication app = loanApplicationRepository.findById(request.getLoanApplicationId())
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        if (app.getStatus() != LoanStatus.AWAITING_GUARANTORS && app.getStatus() != LoanStatus.ALL_GUARANTORS_APPROVED) {
            throw new IllegalStateException("This loan is already beyond the guarantor stage, so you can no longer ask to be removed.");
        }
        reversalRequestRepository.findTopByGuarantorRequestIdAndTypeAndStatusOrderByCreatedAtDesc(
            guarantorRequestId, ReversalRequestType.GUARANTOR_DECISION_UNDO, ReversalRequestStatus.PENDING
        ).ifPresent(existing -> {
            throw new IllegalStateException("A guarantor removal request is already waiting for the applicant to review.");
        });

        ReversalRequest reversalRequest = reversalRequestRepository.save(ReversalRequest.builder()
            .id(UUID.randomUUID())
            .saccoId(app.getSaccoId())
            .loanApplicationId(app.getId())
            .guarantorRequestId(guarantorRequestId)
            .type(ReversalRequestType.GUARANTOR_DECISION_UNDO)
            .status(ReversalRequestStatus.PENDING)
            .requesterMemberId(guarantorMemberId)
            .approverMemberId(app.getApplicantMemberId())
            .createdAt(OffsetDateTime.now())
            .build());

        outboxService.enqueue(
            "REVERSAL_REQUEST",
            reversalRequest.getId(),
            "GUARANTOR_UNDO_REQUESTED",
            app.getApplicantMemberId(),
            Map.of(
                "loanId", app.getId().toString(),
                "guarantorRequestId", guarantorRequestId.toString(),
                "reversalRequestId", reversalRequest.getId().toString()
            )
        );
    }

    @Transactional
    public void requestManagerStageWithdrawal(UUID loanApplicationId, UUID applicantMemberId) {
        LoanApplication app = loanWorkflowService.getMine(loanApplicationId, applicantMemberId);
        if (app.getStatus() != LoanStatus.READY_FOR_MANAGER) {
            throw new IllegalStateException("A manager can only approve removal while the application is still on review by manager.");
        }
        if (!isWithinReversalWindow(app.getUpdatedAt())) {
            throw new IllegalStateException("The 24-hour removal window for this manager review stage has already closed.");
        }
        reversalRequestRepository.findTopByLoanApplicationIdAndTypeAndStatusOrderByCreatedAtDesc(
            loanApplicationId, ReversalRequestType.MANAGER_STAGE_WITHDRAWAL, ReversalRequestStatus.PENDING
        ).ifPresent(existing -> {
            throw new IllegalStateException("A manager removal request is already pending for this application.");
        });

        ReversalRequest reversalRequest = reversalRequestRepository.save(ReversalRequest.builder()
            .id(UUID.randomUUID())
            .saccoId(app.getSaccoId())
            .loanApplicationId(loanApplicationId)
            .type(ReversalRequestType.MANAGER_STAGE_WITHDRAWAL)
            .status(ReversalRequestStatus.PENDING)
            .requesterMemberId(applicantMemberId)
            .approverRole(Position.MANAGER)
            .createdAt(OffsetDateTime.now())
            .build());

        List<RoleDirectoryService.RoleAccountRef> managers = roleDirectoryService.activeByRole(app.getSaccoId(), Position.MANAGER);
        for (RoleDirectoryService.RoleAccountRef manager : managers) {
            outboxService.enqueue(
                "REVERSAL_REQUEST",
                reversalRequest.getId(),
                "MANAGER_REVERSAL_REQUESTED",
                manager.getId(),
                Map.of(
                    "loanId", loanApplicationId.toString(),
                    "reversalRequestId", reversalRequest.getId().toString()
                )
            );
        }
    }

    @Transactional
    public void decideGuarantorUndo(UUID reversalRequestId, UUID applicantMemberId, boolean approve) {
        ReversalRequest reversalRequest = reversalRequestRepository.findById(reversalRequestId)
            .orElseThrow(() -> new IllegalArgumentException("Reversal request not found"));
        if (reversalRequest.getType() != ReversalRequestType.GUARANTOR_DECISION_UNDO) {
            throw new IllegalArgumentException("That request is not a guarantor removal request.");
        }
        if (reversalRequest.getStatus() != ReversalRequestStatus.PENDING) {
            throw new IllegalStateException("This reversal request has already been decided.");
        }
        if (!applicantMemberId.equals(reversalRequest.getApproverMemberId())) {
            throw new IllegalArgumentException("Only the applicant can decide this guarantor removal request.");
        }

        if (approve) {
            loanWorkflowService.removeGuarantorFromLoan(reversalRequest.getGuarantorRequestId(), reversalRequest.getRequesterMemberId());
            reversalRequest.setStatus(ReversalRequestStatus.APPROVED);
            outboxService.enqueue(
                "REVERSAL_REQUEST",
                reversalRequest.getId(),
                "GUARANTOR_UNDO_APPROVED",
                reversalRequest.getRequesterMemberId(),
                Map.of(
                    "loanId", reversalRequest.getLoanApplicationId().toString(),
                    "reversalRequestId", reversalRequest.getId().toString()
                )
            );
        } else {
            reversalRequest.setStatus(ReversalRequestStatus.REJECTED);
            outboxService.enqueue(
                "REVERSAL_REQUEST",
                reversalRequest.getId(),
                "GUARANTOR_UNDO_REJECTED",
                reversalRequest.getRequesterMemberId(),
                Map.of(
                    "loanId", reversalRequest.getLoanApplicationId().toString(),
                    "reversalRequestId", reversalRequest.getId().toString()
                )
            );
        }
        reversalRequest.setDecidedByMemberId(applicantMemberId);
        reversalRequest.setDecidedAt(OffsetDateTime.now());
        reversalRequestRepository.save(reversalRequest);
    }

    @Transactional
    public void decideManagerStageWithdrawal(UUID reversalRequestId, UUID managerMemberId, boolean approve) {
        ReversalRequest reversalRequest = reversalRequestRepository.findById(reversalRequestId)
            .orElseThrow(() -> new IllegalArgumentException("Reversal request not found"));
        if (reversalRequest.getType() != ReversalRequestType.MANAGER_STAGE_WITHDRAWAL) {
            throw new IllegalArgumentException("That request is not a manager-stage application removal.");
        }
        if (reversalRequest.getStatus() != ReversalRequestStatus.PENDING) {
            throw new IllegalStateException("This reversal request has already been decided.");
        }

        LoanApplication app = loanApplicationRepository.findById(reversalRequest.getLoanApplicationId())
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
        if (!app.getSaccoId().equals(reversalRequest.getSaccoId())) {
            throw new IllegalArgumentException("Loan application not found");
        }

        if (approve) {
            loanWorkflowService.removeApplicationAtManagerStage(reversalRequest.getLoanApplicationId(), reversalRequest.getRequesterMemberId());
            reversalRequest.setStatus(ReversalRequestStatus.APPROVED);
            outboxService.enqueue(
                "REVERSAL_REQUEST",
                reversalRequest.getId(),
                "MANAGER_REVERSAL_APPROVED",
                reversalRequest.getRequesterMemberId(),
                Map.of(
                    "reversalRequestId", reversalRequest.getId().toString()
                )
            );
        } else {
            reversalRequest.setStatus(ReversalRequestStatus.REJECTED);
            outboxService.enqueue(
                "REVERSAL_REQUEST",
                reversalRequest.getId(),
                "MANAGER_REVERSAL_REJECTED",
                reversalRequest.getRequesterMemberId(),
                Map.of(
                    "loanId", reversalRequest.getLoanApplicationId().toString(),
                    "reversalRequestId", reversalRequest.getId().toString()
                )
            );
        }
        reversalRequest.setDecidedByMemberId(managerMemberId);
        reversalRequest.setDecidedAt(OffsetDateTime.now());
        reversalRequestRepository.save(reversalRequest);
    }

    @Transactional(readOnly = true)
    public ReversalRequest pendingGuarantorUndo(UUID guarantorRequestId) {
        return reversalRequestRepository.findTopByGuarantorRequestIdAndTypeAndStatusOrderByCreatedAtDesc(
            guarantorRequestId, ReversalRequestType.GUARANTOR_DECISION_UNDO, ReversalRequestStatus.PENDING
        ).orElse(null);
    }

    @Transactional(readOnly = true)
    public ReversalRequest pendingManagerStageWithdrawal(UUID loanApplicationId) {
        return reversalRequestRepository.findTopByLoanApplicationIdAndTypeAndStatusOrderByCreatedAtDesc(
            loanApplicationId, ReversalRequestType.MANAGER_STAGE_WITHDRAWAL, ReversalRequestStatus.PENDING
        ).orElse(null);
    }

    private boolean isWithinReversalWindow(OffsetDateTime referenceAt) {
        return referenceAt != null && referenceAt.plusHours(REVERSAL_WINDOW_HOURS).isAfter(OffsetDateTime.now());
    }
}
