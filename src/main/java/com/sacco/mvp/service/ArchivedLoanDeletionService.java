package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.repository.LoanLedgerRepository;
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
@lombok.extern.slf4j.Slf4j
public class ArchivedLoanDeletionService {
    private final EntityManager entityManager;
    private final AccessControlService access;
    private final LoanAttachmentService loanAttachmentService;
    private final AuditService auditService;
    private final PlatformTransactionManager transactionManager;
    private final LoanLedgerRepository loanLedgerRepository;

    public boolean canDelete(UUID id, AppUserPrincipal actor) {
        load(id, actor, false);
        return true;
    }

    public void delete(UUID id, AppUserPrincipal actor) {
        LoanApplication checked = load(id, actor, false);
        String checkedLoanId = checked.getLoanId();
        UUID checkedMemberId = checked.getApplicantMemberId();
        if (!canDelete(id, actor)) {
            throw new EligibilityException("This loan cannot be deleted from the local archive.");
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            LoanApplication loan = load(id, actor, true);
            if (!Objects.equals(checkedLoanId, loan.getLoanId())
                || !Objects.equals(checkedMemberId, loan.getApplicantMemberId())) {
                throw new EligibilityException("The loan changed. Please check it again.");
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
                AuditEventStatus.SUCCESS, "Archived loan deleted after local archive checks passed",
                "LOAN", loan.getLoanId(), loan.getSaccoId(), loan.getStationId(),
                Map.of("loanId", loan.getLoanId(), "reason", "LOCAL_ARCHIVE_DELETE"));
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
            .orElseThrow(() -> new EligibilityException("Loan not found in this station."));
        if (lock) entityManager.refresh(loan, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(loan.getSaccoId(), actor.getSaccoId())
            || loan.getStationId() == null || !loan.getStationId().equalsIgnoreCase(actor.getStationId())) {
            throw new org.springframework.security.access.AccessDeniedException("Loan is outside this station.");
        }
        if (loan.getStatus() != LoanStatus.DISBURSED || loan.getLoanId() == null || loan.getLoanId().isBlank()) {
            throw new EligibilityException("Only archived disbursed loans with a Loan ID can be deleted.");
        }
        if (loanLedgerRepository.existsById(id)) {
            throw new EligibilityException("Posted financial records must be retained. This loan cannot be deleted.");
        }
        Long references = entityManager.createQuery(
            "select count(l) from LoanApplication l where l.topUpSourceLoanId = :id", Long.class)
            .setParameter("id", id).getSingleResult();
        if (references > 0 || loan.getTopUpSourceLoanId() != null) {
            throw new EligibilityException("This loan is linked to a top-up and cannot be deleted.");
        }
        return loan;
    }

    public static class EligibilityException extends IllegalStateException {
        public EligibilityException(String message) {
            super(message);
        }
    }
}
