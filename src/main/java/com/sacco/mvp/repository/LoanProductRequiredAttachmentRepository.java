package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanProductRequiredAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LoanProductRequiredAttachmentRepository extends JpaRepository<LoanProductRequiredAttachment, UUID> {
    List<LoanProductRequiredAttachment> findByLoanProductSettingIdAndActiveTrueOrderByDisplayOrderAscCreatedAtAsc(UUID loanProductSettingId);

    List<LoanProductRequiredAttachment> findByLoanProductSettingIdOrderByDisplayOrderAscCreatedAtAsc(UUID loanProductSettingId);

    List<LoanProductRequiredAttachment> findByLoanProductSettingIdInOrderByDisplayOrderAscCreatedAtAsc(Collection<UUID> loanProductSettingIds);

    void deleteByLoanProductSettingId(UUID loanProductSettingId);
}
