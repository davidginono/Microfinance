package com.sacco.mvp.repository;

import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuarantorRequestRepository extends JpaRepository<GuarantorRequest, UUID> {
    List<GuarantorRequest> findByLoanApplicationId(UUID loanApplicationId);

    List<GuarantorRequest> findByLoanApplicationIdIn(Collection<UUID> loanApplicationIds);

    @Query("""
        select count(g)
        from GuarantorRequest g, LoanApplication l
        where g.loanApplicationId = l.id
          and g.status = com.sacco.mvp.domain.GuarantorRequestStatus.PENDING
          and l.saccoId = :saccoId
          and (cast(:status as string) is null or l.status = :status)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
        """)
    long countPendingForReport(@Param("saccoId") String saccoId,
                               @Param("status") LoanStatus status,
                               @Param("loanType") LoanType loanType,
                               @Param("createdFrom") OffsetDateTime createdFrom,
                               @Param("createdToExclusive") OffsetDateTime createdToExclusive);

    void deleteByLoanApplicationId(UUID loanApplicationId);

    long countByLoanApplicationIdAndStatus(UUID loanApplicationId, GuarantorRequestStatus status);

    @Query("""
        select count(g)
        from GuarantorRequest g
        where g.guarantorMemberId = :guarantorMemberId
          and g.status = com.sacco.mvp.domain.GuarantorRequestStatus.PENDING
          and not exists (
            select superseding.id
            from LoanApplication superseding
            where superseding.topUpSourceLoanId = g.loanApplicationId
              and superseding.status <> com.sacco.mvp.domain.LoanStatus.DRAFT
          )
        """)
    long countVisiblePendingByGuarantorMemberId(@Param("guarantorMemberId") UUID guarantorMemberId);

    List<GuarantorRequest> findByGuarantorMemberIdOrderByCreatedAtDesc(UUID guarantorMemberId);

    @Query("""
        select g
        from GuarantorRequest g, LoanApplication l
        where g.loanApplicationId = l.id
          and g.guarantorMemberId = :guarantorMemberId
          and not exists (
            select superseding.id
            from LoanApplication superseding
            where superseding.topUpSourceLoanId = g.loanApplicationId
              and superseding.status <> com.sacco.mvp.domain.LoanStatus.DRAFT
          )
          and (
            g.status = com.sacco.mvp.domain.GuarantorRequestStatus.PENDING
            or (
              g.status = com.sacco.mvp.domain.GuarantorRequestStatus.APPROVED
              and l.status in (
                com.sacco.mvp.domain.LoanStatus.AWAITING_GUARANTORS,
                com.sacco.mvp.domain.LoanStatus.ALL_GUARANTORS_APPROVED
              )
              and g.decidedAt > :removalCutoff
            )
          )
        order by g.createdAt desc
        """)
    List<GuarantorRequest> findActiveVisibleByGuarantorMemberId(@Param("guarantorMemberId") UUID guarantorMemberId,
                                                                @Param("removalCutoff") OffsetDateTime removalCutoff);

    @Query("""
        select g
        from GuarantorRequest g, LoanApplication l
        where g.loanApplicationId = l.id
          and g.guarantorMemberId = :guarantorMemberId
          and g.status = com.sacco.mvp.domain.GuarantorRequestStatus.APPROVED
          and l.status in (
            com.sacco.mvp.domain.LoanStatus.DISBURSED,
            com.sacco.mvp.domain.LoanStatus.DEFAULTED,
            com.sacco.mvp.domain.LoanStatus.READY_FOR_DISBURSEMENT,
            com.sacco.mvp.domain.LoanStatus.AWAITING_ACCOUNTANT,
            com.sacco.mvp.domain.LoanStatus.AWAITING_BOARD,
            com.sacco.mvp.domain.LoanStatus.AWAITING_CREDIT_COMMITTEE,
            com.sacco.mvp.domain.LoanStatus.AWAITING_LOAN_OFFICER,
            com.sacco.mvp.domain.LoanStatus.READY_FOR_MANAGER
          )
          and not exists (
            select superseding.id
            from LoanApplication superseding
            where superseding.topUpSourceLoanId = g.loanApplicationId
              and superseding.status <> com.sacco.mvp.domain.LoanStatus.DRAFT
          )
        order by g.createdAt desc
        """)
    List<GuarantorRequest> findActiveGuaranteedLoansByGuarantorMemberId(@Param("guarantorMemberId") UUID guarantorMemberId);

    @Query("""
        select g
        from GuarantorRequest g, LoanApplication l
        where g.loanApplicationId = l.id
          and g.guarantorMemberId = :guarantorMemberId
          and not exists (
            select superseding.id
            from LoanApplication superseding
            where superseding.topUpSourceLoanId = g.loanApplicationId
              and superseding.status <> com.sacco.mvp.domain.LoanStatus.DRAFT
          )
          and (
            g.status in (
              com.sacco.mvp.domain.GuarantorRequestStatus.REJECTED,
              com.sacco.mvp.domain.GuarantorRequestStatus.EXPIRED
            )
            or (
              g.status = com.sacco.mvp.domain.GuarantorRequestStatus.APPROVED
              and (
                l.status not in (
                  com.sacco.mvp.domain.LoanStatus.AWAITING_GUARANTORS,
                  com.sacco.mvp.domain.LoanStatus.ALL_GUARANTORS_APPROVED
                )
                or g.decidedAt <= :removalCutoff
              )
            )
          )
          and (cast(:status as string) is null or g.status = :status)
          and (cast(:loanIdQuery as string) is null or lower(cast(g.loanApplicationId as string)) like concat('%', cast(:loanIdQuery as string), '%'))
          and (cast(:reviewedFrom as timestamp) is null or coalesce(g.decidedAt, g.createdAt) >= :reviewedFrom)
          and (cast(:reviewedToExclusive as timestamp) is null or coalesce(g.decidedAt, g.createdAt) < :reviewedToExclusive)
        order by coalesce(g.decidedAt, g.createdAt) desc
        """)
    Page<GuarantorRequest> findArchivePageByGuarantorMemberId(@Param("guarantorMemberId") UUID guarantorMemberId,
                                                              @Param("removalCutoff") OffsetDateTime removalCutoff,
                                                              @Param("status") GuarantorRequestStatus status,
                                                              @Param("loanIdQuery") String loanIdQuery,
                                                              @Param("reviewedFrom") OffsetDateTime reviewedFrom,
                                                              @Param("reviewedToExclusive") OffsetDateTime reviewedToExclusive,
                                                              Pageable pageable);

    @Query("""
        select coalesce(sum(l.amount), 0)
        from GuarantorRequest g, LoanApplication l
        where g.loanApplicationId = l.id
          and g.guarantorMemberId = :guarantorMemberId
          and g.status = com.sacco.mvp.domain.GuarantorRequestStatus.APPROVED
          and l.status in (
            com.sacco.mvp.domain.LoanStatus.DISBURSED,
            com.sacco.mvp.domain.LoanStatus.DEFAULTED,
            com.sacco.mvp.domain.LoanStatus.READY_FOR_DISBURSEMENT,
            com.sacco.mvp.domain.LoanStatus.AWAITING_ACCOUNTANT,
            com.sacco.mvp.domain.LoanStatus.AWAITING_BOARD,
            com.sacco.mvp.domain.LoanStatus.AWAITING_CREDIT_COMMITTEE,
            com.sacco.mvp.domain.LoanStatus.AWAITING_LOAN_OFFICER,
            com.sacco.mvp.domain.LoanStatus.READY_FOR_MANAGER
          )
          and (cast(:saccoId as string) is null or l.saccoId = :saccoId)
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
        """)
    java.math.BigDecimal sumActiveGuaranteedAmount(@Param("guarantorMemberId") UUID guarantorMemberId,
                                                   @Param("saccoId") String saccoId,
                                                   @Param("stationId") String stationId);

    @Query("""
        select count(g)
        from GuarantorRequest g, LoanApplication l
        where g.loanApplicationId = l.id
          and g.guarantorMemberId = :guarantorMemberId
          and g.status = com.sacco.mvp.domain.GuarantorRequestStatus.APPROVED
          and l.status in (
            com.sacco.mvp.domain.LoanStatus.DISBURSED,
            com.sacco.mvp.domain.LoanStatus.DEFAULTED
          )
          and (cast(:saccoId as string) is null or l.saccoId = :saccoId)
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
        """)
    long countActiveGuarantees(@Param("guarantorMemberId") UUID guarantorMemberId,
                               @Param("saccoId") String saccoId,
                               @Param("stationId") String stationId);

    @Query("""
        select count(g)
        from GuarantorRequest g, LoanApplication l
        where g.loanApplicationId = l.id
          and g.guarantorMemberId is null
          and lower(g.externalMemberNo) = lower(:externalMemberNo)
          and lower(g.externalStationId) = lower(:externalStationId)
          and g.status = com.sacco.mvp.domain.GuarantorRequestStatus.APPROVED
          and l.status in (
            com.sacco.mvp.domain.LoanStatus.DISBURSED,
            com.sacco.mvp.domain.LoanStatus.DEFAULTED
          )
          and (cast(:saccoId as string) is null or l.saccoId = :saccoId)
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
        """)
    long countExternalActiveGuarantees(@Param("externalMemberNo") String externalMemberNo,
                                       @Param("externalStationId") String externalStationId,
                                       @Param("saccoId") String saccoId,
                                       @Param("stationId") String stationId);

    Optional<GuarantorRequest> findByIdAndGuarantorMemberId(UUID id, UUID guarantorMemberId);

    Optional<GuarantorRequest> findByLoanApplicationIdAndGuarantorMemberId(UUID loanApplicationId, UUID guarantorMemberId);
}
