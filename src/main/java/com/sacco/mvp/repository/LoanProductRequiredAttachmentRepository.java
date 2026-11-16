package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanProductRequiredAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LoanProductRequiredAttachmentRepository extends JpaRepository<LoanProductRequiredAttachment, UUID> {
    List<LoanProductRequiredAttachment> findByLoanProductSettingIdAndActiveTrueOrderByDisplayOrderAscCreatedAtAsc(UUID loanProductSettingId);

    List<LoanProductRequiredAttachment> findByLoanProductSettingIdInOrderByDisplayOrderAscCreatedAtAsc(Collection<UUID> loanProductSettingIds);

    List<LoanProductRequiredAttachment> findByLoanProductSettingIdAndIdIn(UUID productId,Collection<UUID> ids);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @org.springframework.data.jpa.repository.Query("update LoanProductRequiredAttachment r set r.active=false,r.updatedAt=:at where r.loanProductSettingId=:product and r.active=true")
    void deactivateAll(@org.springframework.data.repository.query.Param("product") UUID product,@org.springframework.data.repository.query.Param("at") java.time.OffsetDateTime at);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @org.springframework.data.jpa.repository.Query("update LoanProductRequiredAttachment r set r.active=false,r.updatedAt=:at where r.loanProductSettingId=:product and r.active=true and r.id not in :ids")
    void deactivateOthers(@org.springframework.data.repository.query.Param("product") UUID product,@org.springframework.data.repository.query.Param("ids") Collection<UUID> ids,@org.springframework.data.repository.query.Param("at") java.time.OffsetDateTime at);
}
