package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArchivedLoanDeletionService {
    private final EntityManager entityManager;
    private final MemberRepository memberRepository;
    private final ForesightDirectoryService foresightDirectoryService;
    private final AccessControlService access;
    private final LoanAttachmentService loanAttachmentService;
    private final AuditService auditService;
    private final PlatformTransactionManager transactionManager;

    public boolean canDelete(UUID id, AppUserPrincipal actor) {
        LoanApplication loan = load(id, actor, false);
        Member member = memberRepository.findById(loan.getApplicantMemberId()).orElseThrow();
        if (member.getMemberNo() == null || member.getMemberNo().isBlank()) {
            throw new IllegalStateException("Applicant member number is missing.");
        }
        return foresightDirectoryService.isLoanPaymentSummaryMissing(
            member.getMemberNo().trim(), loan.getStationId(), loan.getLoanId());
    }

    public void delete(UUID id, AppUserPrincipal actor) {
        LoanApplication checked = load(id, actor, false);
        String checkedLoanId = checked.getLoanId();
        UUID checkedMemberId = checked.getApplicantMemberId();
        if (!canDelete(id, actor)) {
            throw new IllegalStateException("This loan exists in Foresight and cannot be deleted.");
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LoanApplication loan = load(id, actor, true);
            if (!Objects.equals(checkedLoanId, loan.getLoanId())
                || !Objects.equals(checkedMemberId, loan.getApplicantMemberId())) {
                throw new IllegalStateException("The loan changed. Please check it again.");
            }
            for (String entity : new String[]{"ReversalRequest", "GuarantorRequest", "BoardReview", "ManagerReview"}) {
                entityManager.createQuery("delete from " + entity + " r where r.loanApplicationId = :id")
                    .setParameter("id", id).executeUpdate();
            }
            entityManager.createQuery("delete from OutboxEvent e where e.aggregateType = 'LOAN' and e.aggregateId = :id")
                .setParameter("id", id).executeUpdate();
            entityManager.createNativeQuery("delete from outbox_events where payload -> 'details' ->> 'loanId' = :id or payload -> 'details' ->> 'applicationId' = :id")
                .setParameter("id", id.toString()).executeUpdate();
            entityManager.createNativeQuery("""
                delete from notifications where payload ->> 'loanId' = :id
                or payload -> 'details' ->> 'loanId' = :id
                or payload -> 'details' ->> 'applicationId' = :id
                """)
                .setParameter("id", id.toString()).executeUpdate();
            loanAttachmentService.deleteAll(id);
            auditService.logEvent("LOAN_APPLICATION", id, "ARCHIVED_LOAN_DELETED", actor.getMemberId(),
                AuditEventStatus.SUCCESS, "Archived loan deleted after Foresight confirmed it was not found",
                "LOAN", loan.getLoanId(), loan.getSaccoId(), loan.getStationId(),
                Map.of("loanId", loan.getLoanId(), "reason", "FORESIGHT_NOT_FOUND"));
            entityManager.remove(loan);
        });
    }

    private LoanApplication load(UUID id, AppUserPrincipal actor, boolean lock) {
        if (!access.canAccessDisbursementArea(actor) || !access.has(actor, "DISBURSEMENT_QUEUE_DISBURSE")
            || actor.getStationId() == null || actor.getStationId().isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Deletion is not permitted.");
        }
        var query = entityManager.createQuery("""
            select l from LoanApplication l where l.id = :id and l.saccoId = :sacco
            and lower(l.stationId) = lower(:station)
            """, LoanApplication.class).setParameter("id", id)
            .setParameter("sacco", actor.getSaccoId()).setParameter("station", actor.getStationId());
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        LoanApplication loan = query.getResultList().stream().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Loan not found in this station."));
        if (lock) entityManager.refresh(loan, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(loan.getSaccoId(), actor.getSaccoId())
            || loan.getStationId() == null || !loan.getStationId().equalsIgnoreCase(actor.getStationId())) {
            throw new org.springframework.security.access.AccessDeniedException("Loan is outside this station.");
        }
        if (loan.getStatus() != LoanStatus.DISBURSED || loan.getLoanId() == null || loan.getLoanId().isBlank()) {
            throw new IllegalStateException("Only archived disbursed loans can be deleted.");
        }
        Long references = entityManager.createQuery(
            "select count(l) from LoanApplication l where l.topUpSourceLoanId = :id", Long.class)
            .setParameter("id", id).getSingleResult();
        if (references > 0 || loan.getTopUpSourceLoanId() != null) {
            throw new IllegalStateException("This loan is linked to a top-up and cannot be deleted.");
        }
        return loan;
    }
}
