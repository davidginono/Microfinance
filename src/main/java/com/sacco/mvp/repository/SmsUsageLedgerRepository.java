package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SmsUsageLedger;
import com.sacco.mvp.domain.SmsUsageOutcome;
import com.sacco.mvp.domain.LoanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SmsUsageLedgerRepository extends JpaRepository<SmsUsageLedger, UUID> {
    interface LoanSmsUsageProjection {
        UUID getLoanApplicationId();
        Long getApplicationNumber();
        String getLoanId();
        com.sacco.mvp.domain.LoanStatus getLoanStatus();
        String getSaccoId();
        String getStationId();
        UUID getApplicantMemberId();
        String getApplicantName();
        String getApplicantMemberNo();
        long getSmsEventCount();
        long getUnitsUsed();
        OffsetDateTime getLastSmsAt();
    }

    interface LoanSmsApplicantProjection {
        UUID getApplicantMemberId();
        String getApplicantName();
        String getApplicantMemberNo();
        String getApplicantStaffNo();
        String getSaccoId();
        String getStationId();
    }

    Optional<SmsUsageLedger> findByIdAndAccountId(UUID id, UUID accountId);

    Page<SmsUsageLedger> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    @Query(
        value = """
            select ledger.loanApplicationId as loanApplicationId,
                   app.applicationNumber as applicationNumber,
                   app.loanId as loanId,
                   app.status as loanStatus,
                   app.saccoId as saccoId,
                   app.stationId as stationId,
                   app.applicantMemberId as applicantMemberId,
                   applicant.fullName as applicantName,
                   applicant.memberNo as applicantMemberNo,
                   coalesce(sum(ledger.eventCount), 0) as smsEventCount,
                   coalesce(sum(-ledger.unitChange), 0) as unitsUsed,
                   max(ledger.createdAt) as lastSmsAt
            from SmsUsageLedger ledger
            join LoanApplication app on app.id = ledger.loanApplicationId
            left join Member applicant on applicant.id = app.applicantMemberId
            where ledger.loanApplicationId is not null
              and ledger.unitChange < 0
              and ledger.outcome in :outcomes
              and (cast(:fromAt as timestamp) is null or ledger.createdAt >= :fromAt)
              and (cast(:toAt as timestamp) is null or ledger.createdAt < :toAt)
              and (cast(:loanStatus as string) is null or app.status = :loanStatus)
              and (:applicantFilterActive = false or app.applicantMemberId in :applicantIds)
              and (cast(:saccoId as string) is null or ledger.saccoId = :saccoId)
              and (cast(:stationId as string) is null or lower(ledger.stationId) = lower(cast(:stationId as string)))
            group by ledger.loanApplicationId,
                     app.applicationNumber,
                     app.loanId,
                     app.status,
                     app.saccoId,
                     app.stationId,
                     app.applicantMemberId,
                     applicant.fullName,
                     applicant.memberNo
            order by max(ledger.createdAt) desc
            """,
        countQuery = """
            select count(distinct ledger.loanApplicationId)
            from SmsUsageLedger ledger
            where ledger.loanApplicationId is not null
              and ledger.unitChange < 0
              and ledger.outcome in :outcomes
              and (cast(:fromAt as timestamp) is null or ledger.createdAt >= :fromAt)
              and (cast(:toAt as timestamp) is null or ledger.createdAt < :toAt)
              and (cast(:loanStatus as string) is null or exists (
                  select 1 from LoanApplication app
                  where app.id = ledger.loanApplicationId
                    and app.status = :loanStatus
              ))
              and (:applicantFilterActive = false or exists (
                  select 1 from LoanApplication applicantFilteredApp
                  where applicantFilteredApp.id = ledger.loanApplicationId
                    and applicantFilteredApp.applicantMemberId in :applicantIds
              ))
              and (cast(:saccoId as string) is null or ledger.saccoId = :saccoId)
              and (cast(:stationId as string) is null or lower(ledger.stationId) = lower(cast(:stationId as string)))
            """
    )
    Page<LoanSmsUsageProjection> summarizeLoanSmsUsage(@Param("saccoId") String saccoId,
                                                       @Param("stationId") String stationId,
                                                       @Param("fromAt") OffsetDateTime fromAt,
                                                       @Param("toAt") OffsetDateTime toAt,
                                                       @Param("loanStatus") LoanStatus loanStatus,
                                                       @Param("applicantIds") Collection<UUID> applicantIds,
                                                       @Param("applicantFilterActive") boolean applicantFilterActive,
                                                       @Param("outcomes") Collection<SmsUsageOutcome> outcomes,
                                                       Pageable pageable);

    @Query("""
        select ledger.loanApplicationId as loanApplicationId,
               app.applicationNumber as applicationNumber,
               app.loanId as loanId,
               app.status as loanStatus,
               app.saccoId as saccoId,
               app.stationId as stationId,
               app.applicantMemberId as applicantMemberId,
               applicant.fullName as applicantName,
               applicant.memberNo as applicantMemberNo,
               coalesce(sum(ledger.eventCount), 0) as smsEventCount,
               coalesce(sum(-ledger.unitChange), 0) as unitsUsed,
               max(ledger.createdAt) as lastSmsAt
        from SmsUsageLedger ledger
        join LoanApplication app on app.id = ledger.loanApplicationId
        left join Member applicant on applicant.id = app.applicantMemberId
        where ledger.loanApplicationId is not null
          and ledger.unitChange < 0
          and ledger.outcome in :outcomes
          and (cast(:fromAt as timestamp) is null or ledger.createdAt >= :fromAt)
          and (cast(:toAt as timestamp) is null or ledger.createdAt < :toAt)
          and (cast(:loanStatus as string) is null or app.status = :loanStatus)
          and (:applicantFilterActive = false or app.applicantMemberId in :applicantIds)
          and (cast(:saccoId as string) is null or ledger.saccoId = :saccoId)
          and (cast(:stationId as string) is null or lower(ledger.stationId) = lower(cast(:stationId as string)))
        group by ledger.loanApplicationId,
                 app.applicationNumber,
                 app.loanId,
                 app.status,
                 app.saccoId,
                 app.stationId,
                 app.applicantMemberId,
                 applicant.fullName,
                 applicant.memberNo
        order by max(ledger.createdAt) desc
        """)
    List<LoanSmsUsageProjection> exportLoanSmsUsage(@Param("saccoId") String saccoId,
                                                    @Param("stationId") String stationId,
                                                    @Param("fromAt") OffsetDateTime fromAt,
                                                    @Param("toAt") OffsetDateTime toAt,
                                                    @Param("loanStatus") LoanStatus loanStatus,
                                                    @Param("applicantIds") Collection<UUID> applicantIds,
                                                    @Param("applicantFilterActive") boolean applicantFilterActive,
                                                    @Param("outcomes") Collection<SmsUsageOutcome> outcomes,
                                                    Pageable pageable);

    @Query("""
        select app.applicantMemberId as applicantMemberId,
               applicant.fullName as applicantName,
               applicant.memberNo as applicantMemberNo,
               applicant.staffNo as applicantStaffNo,
               app.saccoId as saccoId,
               app.stationId as stationId
        from SmsUsageLedger ledger
        join LoanApplication app on app.id = ledger.loanApplicationId
        left join Member applicant on applicant.id = app.applicantMemberId
        where ledger.loanApplicationId is not null
          and ledger.unitChange < 0
          and ledger.outcome in :outcomes
          and (cast(:saccoId as string) is null or ledger.saccoId = :saccoId)
          and (cast(:stationId as string) is null or lower(ledger.stationId) = lower(cast(:stationId as string)))
          and (
              :query = ''
              or lower(coalesce(applicant.fullName, '')) like concat('%', cast(:query as string), '%')
              or lower(coalesce(applicant.memberNo, '')) like concat(cast(:query as string), '%')
              or lower(coalesce(applicant.staffNo, '')) like concat(cast(:query as string), '%')
              or lower(cast(app.applicantMemberId as string)) like concat(cast(:query as string), '%')
          )
        group by app.applicantMemberId,
                 applicant.fullName,
                 applicant.memberNo,
                 applicant.staffNo,
                 app.saccoId,
                 app.stationId
        order by applicant.fullName asc, applicant.memberNo asc
        """)
    List<LoanSmsApplicantProjection> searchLoanSmsUsageApplicants(@Param("saccoId") String saccoId,
                                                                  @Param("stationId") String stationId,
                                                                  @Param("query") String query,
                                                                  @Param("outcomes") Collection<SmsUsageOutcome> outcomes,
                                                                  Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SmsUsageLedger> findFirstByAccountIdAndEventTypeAndOutcomeAndNoteAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
        UUID accountId,
        String eventType,
        SmsUsageOutcome outcome,
        String note,
        OffsetDateTime createdAt
    );
}
