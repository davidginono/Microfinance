package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SmsUsageLedger;
import com.sacco.mvp.domain.SmsUsageOutcome;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
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

    Optional<SmsUsageLedger> findByIdAndAccountId(UUID id, UUID accountId);

    Page<SmsUsageLedger> findBySaccoIdAndStationIdOrderByCreatedAtDesc(String saccoId, String stationId, Pageable pageable);

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
              and (cast(:saccoId as string) is null or ledger.saccoId = :saccoId)
              and (cast(:stationId as string) is null or lower(ledger.stationId) = lower(cast(:stationId as string)))
            """
    )
    Page<LoanSmsUsageProjection> summarizeLoanSmsUsage(@Param("saccoId") String saccoId,
                                                       @Param("stationId") String stationId,
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
