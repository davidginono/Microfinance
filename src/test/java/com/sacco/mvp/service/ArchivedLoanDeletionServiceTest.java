package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.repository.LoanLedgerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArchivedLoanDeletionServiceTest {
    private final EntityManager em = mock(EntityManager.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final AppUserPrincipal actor = mock(AppUserPrincipal.class);
    private final UUID id = UUID.randomUUID();
    private final LoanApplication loan = new LoanApplication();
    private final LoanLedgerRepository ledgers = mock(LoanLedgerRepository.class);
    private final ArchivedLoanDeletionService service = new ArchivedLoanDeletionService(
        em, access, mock(LoanAttachmentService.class), mock(AuditService.class), transactions, ledgers);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        when(access.canAccessDisbursementArea(actor)).thenReturn(true);
        when(access.has(actor, "DISBURSEMENT_QUEUE_DISBURSE")).thenReturn(true);
        when(actor.getStationId()).thenReturn("AR704");
        when(actor.getSaccoId()).thenReturn("S1");
        loan.setId(id);
        loan.setSaccoId("S1");
        loan.setStationId("AR704");
        loan.setLoanId("04050860484983");
        loan.setApplicantMemberId(UUID.randomUUID());
        loan.setStatus(LoanStatus.DISBURSED);
        TypedQuery<LoanApplication> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(LoanApplication.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(loan));
        TypedQuery<Long> count = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(count);
        when(count.setParameter(anyString(), any())).thenReturn(count);
        when(count.getSingleResult()).thenReturn(0L);
    }

    @Test
    void disbursedLocalLoanIsEligibleUsingLoanStation() {
        assertThat(service.canDelete(id, actor)).isTrue();
    }

    @Test
    void ledgerBackedArchiveCannotStartDeletion() {
        when(ledgers.existsById(id)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(id, actor))
            .isInstanceOf(ArchivedLoanDeletionService.EligibilityException.class)
            .hasMessageContaining("financial records must be retained");
        verifyNoInteractions(transactions);
        verify(em, never()).remove(any());
    }

    @Test
    void unauthorizedActorCannotLookupOrDelete() {
        when(access.has(actor, "DISBURSEMENT_QUEUE_DISBURSE")).thenReturn(false);
        assertThatThrownBy(() -> service.delete(id, actor))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(em, transactions);
    }

    @Test
    void nonDisbursedLoanIsRejectedBeforeTransaction() {
        loan.setStatus(LoanStatus.READY_FOR_DISBURSEMENT);
        assertThatThrownBy(() -> service.delete(id, actor)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(transactions);
    }

    @Test
    void topUpBlockIsReportedBeforeTransaction() {
        loan.setTopUpSourceLoanId(UUID.randomUUID());
        assertThatThrownBy(() -> service.canDelete(id, actor))
            .isInstanceOf(ArchivedLoanDeletionService.EligibilityException.class)
            .hasMessage("This loan is linked to a top-up and cannot be deleted.");
    }

    @Test
    void eligibleLocalLoanDeletesRelatedRecordsInTransaction() {
        var transaction = new org.springframework.transaction.support.SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(transaction);
        jakarta.persistence.Query mutation = mock(jakarta.persistence.Query.class);
        when(em.createQuery(anyString())).thenReturn(mutation);
        when(em.createNativeQuery(anyString())).thenReturn(mutation);
        when(mutation.setParameter(anyString(), any())).thenReturn(mutation);

        service.delete(id, actor);

        var order = inOrder(transactions, em);
        order.verify(transactions).getTransaction(any());
        order.verify(em).remove(loan);
        order.verify(transactions).commit(transaction);
        verify(em).createQuery("delete from ReversalRequest r where r.loanApplicationId = :id");
        verify(em).createQuery("delete from GuarantorRequest r where r.loanApplicationId = :id");
        verify(em).createQuery("delete from BoardReview r where r.loanApplicationId = :id");
        verify(em).createQuery("delete from ManagerReview r where r.loanApplicationId = :id");
    }
}
