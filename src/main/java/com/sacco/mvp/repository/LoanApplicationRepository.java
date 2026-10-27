package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {
    @Query(value="select client_kyc_loan_check(:loan,:institution,:branch,:day)",nativeQuery=true)
    String kycError(@Param("loan") UUID loan,@Param("institution") String institution,@Param("branch") String branch,@Param("day") LocalDate day);
    @Query(value="select lending_collateral_check(:loan,:institution,:branch,:day)",nativeQuery=true)
    String collateralError(@Param("loan") UUID loan,@Param("institution") String institution,@Param("branch") String branch,@Param("day") LocalDate day);
    @Query("""
        select l from LoanApplication l where l.saccoId=:institution and l.stationId=:branch and l.id<>:reference
          and l.status in :statuses and exists(select r.id from LoanApplication r where r.id=:reference
            and r.saccoId=:institution and r.stationId=:branch and r.borrowerPartyId=l.borrowerPartyId)
        order by l.disbursementDate desc nulls last,l.createdAt desc,l.id
        """)
    List<LoanApplication> findActiveBorrowerLoansForReference(@Param("reference") UUID reference,@Param("institution") String institution,
        @Param("branch") String branch,@Param("statuses") Collection<LoanStatus> statuses,Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanApplication l where l.id=:id and exists(select m.id from Member m where m.id=:guarantor and m.saccoId=l.saccoId and lower(m.stationId)=lower(l.stationId))")
    Optional<LoanApplication> findForGuarantorDecision(@Param("id") UUID id,@Param("guarantor") UUID guarantor);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanApplication l where l.id=:id and l.applicantMemberId=:applicant and exists(select m.id from Member m where m.id=:applicant and m.saccoId=l.saccoId)")
    Optional<LoanApplication> findForApplicantDecision(@Param("id") UUID id,@Param("applicant") UUID applicant);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanApplication l where l.id=:id")
    Optional<LoanApplication> findForDisbursement(@Param("id") UUID id);


    interface BorrowerNameRow { UUID getLoanId(); String getName(); }

    interface BorrowerProfileRow {
        UUID getPartyId(); String getKind(); UUID getClientId(); UUID getGroupId();
        String getNumber(); String getName(); String getEmail(); String getPhone();
        String getBranch(); String getLegalForm();
    }

    @Query(value="""
        select b.party_id as "partyId",b.kind,b.client_id as "clientId",b.group_id as "groupId",
          b.member_no as "number",b.full_name as "name",m.email,m.phone,l.station_id as "branch",g.legal_form as "legalForm"
        from loan_applications l
        join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
        left join members m on m.id=b.client_id and m.sacco_id=l.sacco_id
        left join lending_group g on g.id=b.group_id and g.sacco_id=l.sacco_id and g.station_id=l.station_id
        where l.id=:loan and l.sacco_id=:institution and l.station_id=:branch
          and (cast(:stage as text) is null or exists(select 1 from board_reviews r
            where r.loan_application_id=l.id and r.board_member_id=:reviewer and r.review_stage=:stage))
        """,nativeQuery=true)
    Optional<BorrowerProfileRow> findBorrowerProfile(@Param("institution") String institution,@Param("branch") String branch,
        @Param("loan") UUID loan,@Param("reviewer") UUID reviewer,@Param("stage") String stage);

    @Query(value="""
        select l.id as "loanId",b.full_name as "name" from loan_applications l
        join loan_borrower_directory b on b.party_id=l.borrower_party_id and b.sacco_id=l.sacco_id
        where l.sacco_id=:institution and l.station_id=:branch and l.id in :ids
        order by l.id
        """,nativeQuery=true)
    List<BorrowerNameRow> findBorrowerNamesForLoans(@Param("institution") String institution,@Param("branch") String branch,@Param("ids") Collection<UUID> ids);

    interface StatusCountProjection {
        LoanStatus getStatus();
        long getTotal();
    }

    interface LoanTypeCountProjection {
        LoanType getLoanType();
        long getTotal();
    }

    interface SaccoLoanStatsProjection {
        String getSaccoId();
        long getActiveLoanCount();
        long getPaidLoanCount();
        long getOverdueLoanCount();
        java.math.BigDecimal getTotalDisbursedPrincipal();
        java.math.BigDecimal getPaidPrincipal();
        java.math.BigDecimal getActiveExposure();
        java.math.BigDecimal getOverduePrincipal();
    }

    List<LoanApplication> findByApplicantMemberIdOrderByCreatedAtDesc(UUID applicantMemberId);

    long countBySaccoIdAndApplicantMemberIdAndStatusIn(String saccoId, UUID applicantMemberId,
                                                     Collection<LoanStatus> statuses);

    @Query(value = """
        select l
        from LoanApplication l left join LoanProductSetting p on p.id=l.loanProductSettingId
        where l.applicantMemberId = :applicantMemberId
          and l.status in :statuses
          and (cast(:loanIdQuery as string) is null or lower(coalesce(l.loanId, '')) like concat('%', cast(:loanIdQuery as string), '%'))
          and (cast(:updatedFrom as timestamp) is null or l.updatedAt >= :updatedFrom)
          and (cast(:updatedToExclusive as timestamp) is null or l.updatedAt < :updatedToExclusive)
        order by l.updatedAt desc, l.createdAt desc
        """, queryRewriter = com.sacco.mvp.support.ResourceTableSort.MemberArchives.class)
    Page<LoanApplication> findMemberArchivePage(@Param("applicantMemberId") UUID applicantMemberId,
                                                @Param("statuses") Collection<LoanStatus> statuses,
                                                @Param("loanIdQuery") String loanIdQuery,
                                                @Param("updatedFrom") OffsetDateTime updatedFrom,
                                                @Param("updatedToExclusive") OffsetDateTime updatedToExclusive,
                                                Pageable pageable);

    @Query("""
        select l
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
          and l.status in :statuses
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:loanProductId as uuid) is null or l.loanProductSettingId = :loanProductId)
        order by l.createdAt desc
        """)
    List<LoanApplication> findMemberLoansForAnalyticsByStatuses(@Param("applicantMemberId") UUID applicantMemberId,
                                                                @Param("statuses") Collection<LoanStatus> statuses,
                                                                @Param("createdFrom") OffsetDateTime createdFrom,
                                                                @Param("createdToExclusive") OffsetDateTime createdToExclusive,
                                                                @Param("loanType") LoanType loanType,
                                                                @Param("loanProductId") UUID loanProductId);

    @Query("""
        select distinct year(l.disbursementDate)
        from LoanApplication l
        where l.saccoId = :saccoId
          and l.status in :statuses
          and l.disbursementDate is not null
        order by year(l.disbursementDate) desc
        """)
    List<Integer> findDisbursementYears(@Param("saccoId") String saccoId,
                                        @Param("statuses") Collection<LoanStatus> statuses);

    @Query("""
        select l
        from LoanApplication l
        where l.saccoId = :saccoId
          and l.status in :statuses
          and l.disbursementDate is not null
          and l.disbursementDate >= :fromDate
          and l.disbursementDate <= :toDate
        order by l.disbursementDate desc, l.createdAt desc
        """)
    List<LoanApplication> findDisbursedInRange(@Param("saccoId") String saccoId,
                                               @Param("statuses") Collection<LoanStatus> statuses,
                                               @Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate);

    @Query("""
        select l
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:status as string) is null or l.status = :status)
        order by l.createdAt desc
        """)
    List<LoanApplication> findMemberLoansForAnalytics(@Param("applicantMemberId") UUID applicantMemberId,
                                                      @Param("createdFrom") OffsetDateTime createdFrom,
                                                      @Param("createdToExclusive") OffsetDateTime createdToExclusive,
                                                      @Param("loanType") LoanType loanType,
                                                      @Param("status") LoanStatus status);

    @Query("""
        select l
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:status as string) is null or l.status = :status)
        order by l.createdAt desc
        """)
    List<LoanApplication> findScopeLoansForAnalytics(@Param("saccoId") String saccoId,
                                                     @Param("stationId") String stationId,
                                                     @Param("createdFrom") OffsetDateTime createdFrom,
                                                     @Param("createdToExclusive") OffsetDateTime createdToExclusive,
                                                     @Param("loanType") LoanType loanType,
                                                     @Param("status") LoanStatus status);

    interface AnalyticsLoanRow {
        UUID getId();
        LoanStatus getStatus();
        LoanType getLoanType();
        UUID getLoanProductSettingId();
        OffsetDateTime getCreatedAt();
        java.math.BigDecimal getAmount();
        String getSaccoId();
        String getStationId();
        String getFinancialSnapshot();
    }

    @Query("""
        select l.id as id,
               l.status as status,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.createdAt as createdAt,
               l.amount as amount,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.financialSnapshot as financialSnapshot
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:status as string) is null or l.status = :status)
        order by l.createdAt desc
        """)
    List<AnalyticsLoanRow> findMemberAnalyticsRows(@Param("applicantMemberId") UUID applicantMemberId,
                                                   @Param("createdFrom") OffsetDateTime createdFrom,
                                                   @Param("createdToExclusive") OffsetDateTime createdToExclusive,
                                                   @Param("loanType") LoanType loanType,
                                                   @Param("status") LoanStatus status);

    @Query("""
        select l.id as id,
               l.status as status,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.createdAt as createdAt,
               l.amount as amount,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.financialSnapshot as financialSnapshot
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:status as string) is null or l.status = :status)
        order by l.createdAt desc
        """)
    List<AnalyticsLoanRow> findScopeAnalyticsRows(@Param("saccoId") String saccoId,
                                                  @Param("stationId") String stationId,
                                                  @Param("createdFrom") OffsetDateTime createdFrom,
                                                  @Param("createdToExclusive") OffsetDateTime createdToExclusive,
                                                  @Param("loanType") LoanType loanType,
                                                  @Param("status") LoanStatus status);

    @Query("""
        select l.id as id,
               l.status as status,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.createdAt as createdAt,
               l.amount as amount,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.financialSnapshot as financialSnapshot
        from LoanApplication l
        where l.id in :ids
        """)
    List<AnalyticsLoanRow> findAnalyticsRowsById(@Param("ids") Collection<UUID> ids);

    @Query("""
        select l.id as id,
               l.status as status,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.createdAt as createdAt,
               l.amount as amount,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.financialSnapshot as financialSnapshot
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
          and l.status in :statuses
          and (cast(:saccoId as string) is null or l.saccoId = :saccoId)
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
        """)
    List<AnalyticsLoanRow> findActiveAmountRowsForApplicantScope(@Param("applicantMemberId") UUID applicantMemberId,
                                                                 @Param("saccoId") String saccoId,
                                                                 @Param("stationId") String stationId,
                                                                 @Param("statuses") Collection<LoanStatus> statuses);

    interface ReportLoanRow {
        UUID getId();
        Long getApplicationNumber();
        String getLoanId();
        LoanStatus getStatus();
        LoanType getLoanType();
        UUID getLoanProductSettingId();
        OffsetDateTime getCreatedAt();
        OffsetDateTime getUpdatedAt();
        java.math.BigDecimal getAmount();
        String getSaccoId();
        String getStationId();
        UUID getApplicantMemberId();
        LocalDate getDisbursementDate();
        String getFinancialSnapshot();
    }

    @Query("""
        select l.id as id,
               l.applicationNumber as applicationNumber,
               l.loanId as loanId,
               l.status as status,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.createdAt as createdAt,
               l.updatedAt as updatedAt,
               l.amount as amount,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.applicantMemberId as applicantMemberId,
               l.disbursementDate as disbursementDate,
               l.financialSnapshot as financialSnapshot
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:status as string) is null or l.status = :status)
        order by l.createdAt desc
        """)
    List<ReportLoanRow> findMemberReportRows(@Param("applicantMemberId") UUID applicantMemberId,
                                             @Param("createdFrom") OffsetDateTime createdFrom,
                                             @Param("createdToExclusive") OffsetDateTime createdToExclusive,
                                             @Param("loanType") LoanType loanType,
                                             @Param("status") LoanStatus status);

    @Query("""
        select l.id as id,
               l.applicationNumber as applicationNumber,
               l.loanId as loanId,
               l.status as status,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.createdAt as createdAt,
               l.updatedAt as updatedAt,
               l.amount as amount,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.applicantMemberId as applicantMemberId,
               l.disbursementDate as disbursementDate,
               l.financialSnapshot as financialSnapshot
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
          and l.status in :statuses
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:loanProductId as uuid) is null or l.loanProductSettingId = :loanProductId)
        order by l.createdAt desc
        """)
    List<ReportLoanRow> findMemberReportRowsByStatuses(@Param("applicantMemberId") UUID applicantMemberId,
                                                       @Param("statuses") Collection<LoanStatus> statuses,
                                                       @Param("createdFrom") OffsetDateTime createdFrom,
                                                       @Param("createdToExclusive") OffsetDateTime createdToExclusive,
                                                       @Param("loanType") LoanType loanType,
                                                       @Param("loanProductId") UUID loanProductId);

    @Query("""
        select l.id as id,
               l.applicationNumber as applicationNumber,
               l.loanId as loanId,
               l.status as status,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.createdAt as createdAt,
               l.updatedAt as updatedAt,
               l.amount as amount,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.applicantMemberId as applicantMemberId,
               l.disbursementDate as disbursementDate,
               l.financialSnapshot as financialSnapshot
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:status as string) is null or l.status = :status)
        order by l.createdAt desc
        """)
    List<ReportLoanRow> findScopeReportRows(@Param("saccoId") String saccoId,
                                            @Param("stationId") String stationId,
                                            @Param("createdFrom") OffsetDateTime createdFrom,
                                            @Param("createdToExclusive") OffsetDateTime createdToExclusive,
                                            @Param("loanType") LoanType loanType,
                                            @Param("status") LoanStatus status);

    @Query("""
        select l.id as id,
               l.applicationNumber as applicationNumber,
               l.loanId as loanId,
               l.status as status,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.createdAt as createdAt,
               l.updatedAt as updatedAt,
               l.amount as amount,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.applicantMemberId as applicantMemberId,
               l.disbursementDate as disbursementDate,
               l.financialSnapshot as financialSnapshot
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and l.status in :statuses
          and (cast(:loanType as string) is null or l.loanType = :loanType)
        order by l.createdAt desc
        """)
    List<ReportLoanRow> findScopeFinancialReportRows(@Param("saccoId") String saccoId,
                                                     @Param("stationId") String stationId,
                                                     @Param("statuses") Collection<LoanStatus> statuses,
                                                     @Param("loanType") LoanType loanType);

    @Query("""
        select l.id as id,
               l.applicationNumber as applicationNumber,
               l.loanId as loanId,
               l.status as status,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.createdAt as createdAt,
               l.updatedAt as updatedAt,
               l.amount as amount,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.applicantMemberId as applicantMemberId,
               l.disbursementDate as disbursementDate,
               l.financialSnapshot as financialSnapshot
        from LoanApplication l
        where l.id in :ids
        """)
    List<ReportLoanRow> findReportRowsById(@Param("ids") Collection<UUID> ids);

    interface QueueLoanRow {
        UUID getId();
        Long getApplicationNumber();
        String getLoanId();
        LoanType getLoanType();
        UUID getLoanProductSettingId();
        UUID getApplicantMemberId();
        java.math.BigDecimal getAmount();
        LoanStatus getStatus();
        OffsetDateTime getCreatedAt();
        LocalDate getDisbursementDate();
        LocalDate getFinalDueDate();
        String getSaccoId();
        String getStationId();
    }

    interface LoanAccessRow {
        UUID getId();
        UUID getApplicantMemberId();
        String getSaccoId();
        String getStationId();
        LoanStatus getStatus();
    }

    @Query("""
        select l.id as id,
               l.applicantMemberId as applicantMemberId,
               l.saccoId as saccoId,
               l.stationId as stationId,
               l.status as status
        from LoanApplication l
        where l.id = :id
        """)
    Optional<LoanAccessRow> findAccessRowById(@Param("id") UUID id);

    @Query("""
        select l.id as id,
               l.applicationNumber as applicationNumber,
               l.loanId as loanId,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.applicantMemberId as applicantMemberId,
               l.amount as amount,
               l.status as status,
               l.createdAt as createdAt,
               l.disbursementDate as disbursementDate,
               l.finalDueDate as finalDueDate,
               l.saccoId as saccoId,
               l.stationId as stationId
        from LoanApplication l
        where l.id in :ids
        """)
    List<QueueLoanRow> findQueueRowsById(@Param("ids") Collection<UUID> ids);

    @Query("""
        select l
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and l.status in :statuses
          and (cast(:loanType as string) is null or l.loanType = :loanType)
        order by l.createdAt desc
        """)
    List<LoanApplication> findScopeLoansForStationFinancialAnalytics(@Param("saccoId") String saccoId,
                                                                     @Param("stationId") String stationId,
                                                                     @Param("statuses") Collection<LoanStatus> statuses,
                                                                     @Param("loanType") LoanType loanType);

    List<LoanApplication> findBySaccoIdAndStatusOrderByCreatedAtAsc(String saccoId, LoanStatus status);
    List<LoanApplication> findBySaccoIdAndStatusInOrderByCreatedAtAsc(String saccoId, List<LoanStatus> statuses);

    @Query("""
        select l
        from LoanApplication l
        where l.status = :status
          and l.finalDueDate in :dueDates
        order by l.finalDueDate asc, l.id asc
        """)
    Page<LoanApplication> findDueForReminder(@Param("status") LoanStatus status,
                                             @Param("dueDates") Collection<LocalDate> dueDates,
                                             Pageable pageable);
    List<LoanApplication> findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(UUID applicantMemberId, List<LoanStatus> statuses);
    Page<LoanApplication> findByApplicantMemberIdAndStatusInAndLoanIdIsNotNull(UUID applicantMemberId,
                                                                                Collection<LoanStatus> statuses,
                                                                                Pageable pageable);
    long countByApplicantMemberIdAndStatusAndApplicantDisbursementAcknowledgedAtIsNull(UUID applicantMemberId, LoanStatus status);
    long countByApplicantMemberIdAndStatusInAndApplicantRejectionAcknowledgedAtIsNull(UUID applicantMemberId, Collection<LoanStatus> statuses);

    @Query("""
        select l
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
          and (
            l.status in :statuses
            or (
              l.status = com.sacco.mvp.domain.LoanStatus.DISBURSED
              and l.applicantDisbursementAcknowledgedAt is null
            )
            or (
              l.status in :rejectedStatuses
              and l.applicantRejectionAcknowledgedAt is null
            )
          )
        order by l.createdAt desc
        """)
    List<LoanApplication> findVisibleCurrentForApplicant(@Param("applicantMemberId") UUID applicantMemberId,
                                                         @Param("statuses") Collection<LoanStatus> statuses,
                                                         @Param("rejectedStatuses") Collection<LoanStatus> rejectedStatuses);

    @Query("""
        select l
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
          and (
            l.status in :statuses
            or (
              l.status = com.sacco.mvp.domain.LoanStatus.DISBURSED
              and l.applicantDisbursementAcknowledgedAt is null
            )
            or (
              l.status in :rejectedStatuses
              and l.applicantRejectionAcknowledgedAt is null
            )
          )
        order by l.updatedAt desc, l.createdAt desc
        """)
    List<LoanApplication> findLatestVisibleCurrentForApplicant(@Param("applicantMemberId") UUID applicantMemberId,
                                                               @Param("statuses") Collection<LoanStatus> statuses,
                                                               @Param("rejectedStatuses") Collection<LoanStatus> rejectedStatuses,
                                                               Pageable pageable);

    Optional<LoanApplication> findFirstByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(
        UUID applicantMemberId,
        Collection<LoanStatus> statuses
    );
    Optional<LoanApplication> findFirstByApplicantMemberIdAndStatusInOrderByUpdatedAtDescCreatedAtDesc(
        UUID applicantMemberId,
        Collection<LoanStatus> statuses
    );
    Optional<LoanApplication> findByIdAndApplicantMemberId(UUID id, UUID applicantMemberId);

    long countByTopUpSourceLoanIdAndStatusIn(UUID topUpSourceLoanId, Collection<LoanStatus> statuses);

    long countByTopUpSourceLoanIdAndStatusInAndIdNot(UUID topUpSourceLoanId, Collection<LoanStatus> statuses, UUID id);

    boolean existsBySaccoIdAndLoanId(String saccoId, String loanId);

    Optional<LoanApplication> findFirstByApplicantMemberIdAndLoanIdOrderByCreatedAtDesc(UUID applicantMemberId, String loanId);

    Optional<LoanApplication> findBySaccoIdAndApplicationNumber(String saccoId, Long applicationNumber);

    Page<LoanApplication> findByStatusInAndLoanIdIsNotNull(Collection<LoanStatus> statuses, Pageable pageable);

    @Query("""
        select l.saccoId as saccoId,
               sum(case when l.status in (com.sacco.mvp.domain.LoanStatus.DISBURSED, com.sacco.mvp.domain.LoanStatus.PAR, com.sacco.mvp.domain.LoanStatus.DEFAULTED, com.sacco.mvp.domain.LoanStatus.WRITTEN_OFF) then 1 else 0 end) as activeLoanCount,
               sum(case when l.status = com.sacco.mvp.domain.LoanStatus.PAID then 1 else 0 end) as paidLoanCount,
               sum(case when l.status = com.sacco.mvp.domain.LoanStatus.DEFAULTED then 1 else 0 end) as overdueLoanCount,
               coalesce(sum(case when l.status in (com.sacco.mvp.domain.LoanStatus.DISBURSED, com.sacco.mvp.domain.LoanStatus.PAR, com.sacco.mvp.domain.LoanStatus.DEFAULTED, com.sacco.mvp.domain.LoanStatus.WRITTEN_OFF, com.sacco.mvp.domain.LoanStatus.PAID) then l.amount else 0 end), 0) as totalDisbursedPrincipal,
               coalesce(sum(case when l.status = com.sacco.mvp.domain.LoanStatus.PAID then l.amount else 0 end), 0) as paidPrincipal,
               coalesce(sum(case when l.status in (com.sacco.mvp.domain.LoanStatus.DISBURSED, com.sacco.mvp.domain.LoanStatus.PAR, com.sacco.mvp.domain.LoanStatus.DEFAULTED, com.sacco.mvp.domain.LoanStatus.WRITTEN_OFF) then l.amount else 0 end), 0) as activeExposure,
               coalesce(sum(case when l.status = com.sacco.mvp.domain.LoanStatus.DEFAULTED then l.amount else 0 end), 0) as overduePrincipal
        from LoanApplication l
        where l.saccoId in :saccoIds
        group by l.saccoId
        """)
    List<SaccoLoanStatsProjection> summarizeLoansBySacco(@Param("saccoIds") Collection<String> saccoIds);

    @Query("""
        select l.saccoId as saccoId,
               sum(case when l.status in (com.sacco.mvp.domain.LoanStatus.DISBURSED, com.sacco.mvp.domain.LoanStatus.PAR, com.sacco.mvp.domain.LoanStatus.DEFAULTED, com.sacco.mvp.domain.LoanStatus.WRITTEN_OFF) then 1 else 0 end) as activeLoanCount,
               sum(case when l.status = com.sacco.mvp.domain.LoanStatus.PAID then 1 else 0 end) as paidLoanCount,
               sum(case when l.status = com.sacco.mvp.domain.LoanStatus.DEFAULTED then 1 else 0 end) as overdueLoanCount,
               coalesce(sum(case when l.status in (com.sacco.mvp.domain.LoanStatus.DISBURSED, com.sacco.mvp.domain.LoanStatus.PAR, com.sacco.mvp.domain.LoanStatus.DEFAULTED, com.sacco.mvp.domain.LoanStatus.WRITTEN_OFF, com.sacco.mvp.domain.LoanStatus.PAID) then l.amount else 0 end), 0) as totalDisbursedPrincipal,
               coalesce(sum(case when l.status = com.sacco.mvp.domain.LoanStatus.PAID then l.amount else 0 end), 0) as paidPrincipal,
               coalesce(sum(case when l.status in (com.sacco.mvp.domain.LoanStatus.DISBURSED, com.sacco.mvp.domain.LoanStatus.PAR, com.sacco.mvp.domain.LoanStatus.DEFAULTED, com.sacco.mvp.domain.LoanStatus.WRITTEN_OFF) then l.amount else 0 end), 0) as activeExposure,
               coalesce(sum(case when l.status = com.sacco.mvp.domain.LoanStatus.DEFAULTED then l.amount else 0 end), 0) as overduePrincipal
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
        group by l.saccoId
        """)
    Optional<SaccoLoanStatsProjection> summarizeLoansForScope(@Param("saccoId") String saccoId,
                                                              @Param("stationId") String stationId);

    @Query(value = """
        select l.id as id,
               l.applicationNumber as applicationNumber,
               l.loanId as loanId,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.applicantMemberId as applicantMemberId,
               l.amount as amount,
               l.status as status,
               l.createdAt as createdAt,
               l.disbursementDate as disbursementDate,
               l.finalDueDate as finalDueDate,
               l.saccoId as saccoId,
               l.stationId as stationId
        from LoanApplication l
        left join LoanProductSetting p on p.id = l.loanProductSettingId
        left join Member m on m.id = l.applicantMemberId
        where l.saccoId = :saccoId
          and l.status in :statuses
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
        order by l.createdAt asc
        """, queryRewriter = com.sacco.mvp.support.ResourceTableSort.LoanQueue.class)
    Page<QueueLoanRow> findQueuePageForScope(@Param("saccoId") String saccoId,
                                             @Param("stationId") String stationId,
                                             @Param("statuses") Collection<LoanStatus> statuses,
                                             Pageable pageable);

    @Query(value = """
        select l.id as id,
               l.applicationNumber as applicationNumber,
               l.loanId as loanId,
               l.loanType as loanType,
               l.loanProductSettingId as loanProductSettingId,
               l.applicantMemberId as applicantMemberId,
               l.amount as amount,
               l.status as status,
               l.createdAt as createdAt,
               l.disbursementDate as disbursementDate,
               l.finalDueDate as finalDueDate,
               l.saccoId as saccoId,
               l.stationId as stationId
        from LoanApplication l
        left join LoanProductSetting p on p.id = l.loanProductSettingId
        left join Member m on m.id = l.applicantMemberId
        where l.saccoId = :saccoId
          and l.status in :statuses
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and (
            cast(:searchTerm as string) is null
            or (:searchByLoanId = true and lower(coalesce(l.loanId, '')) like concat('%', cast(:searchTerm as string), '%'))
            or (:searchByLoanId = false and str(l.applicationNumber) like concat('%', cast(:searchTerm as string), '%'))
          )
        order by l.createdAt asc
        """, queryRewriter = com.sacco.mvp.support.ResourceTableSort.LoanQueue.class)
    Page<QueueLoanRow> findQueuePage(@Param("saccoId") String saccoId,
                                     @Param("stationId") String stationId,
                                     @Param("statuses") Collection<LoanStatus> statuses,
                                     @Param("searchTerm") String searchTerm,
                                     @Param("searchByLoanId") boolean searchByLoanId,
                                     Pageable pageable);

    @Query(value = """
        select l.* from loan_applications l left join members m on m.id=l.applicant_member_id left join loan_product_settings p on p.id=l.loan_product_setting_id
        where l.sacco_id = :saccoId
          and lower(l.station_id) = lower(:stationId)
          and l.status in :statuses
          and (:search = ''
            or lower(coalesce(l.loan_id, '')) like concat(:search, '%')
            or cast(l.application_number as text) like concat(:search, '%'))
          and (cast(:updatedFrom as timestamptz) is null or l.updated_at >= :updatedFrom)
          and (cast(:updatedToExclusive as timestamptz) is null or l.updated_at < :updatedToExclusive)
          and (cast(:status as text) is null or l.status = :status)
        order by l.updated_at desc, l.id desc
        """, nativeQuery = true, queryRewriter = com.sacco.mvp.support.ResourceTableSort.ProcessedLoans.class)
    Page<LoanApplication> findProcessedLoansPage(@Param("saccoId") String saccoId,
                                                  @Param("stationId") String stationId,
                                                  @Param("statuses") Collection<String> statuses,
                                                  @Param("search") String search,
                                                  @Param("updatedFrom") OffsetDateTime updatedFrom,
                                                  @Param("updatedToExclusive") OffsetDateTime updatedToExclusive,
                                                  @Param("status") String status,
                                                  Pageable pageable);

    @Query("""
        select l from LoanApplication l
        where l.id = :loanId
          and l.saccoId = :saccoId
          and lower(l.stationId) = lower(:stationId)
          and l.status in :statuses
        """)
    Optional<LoanApplication> findProcessedLoan(@Param("loanId") UUID loanId,
                                                 @Param("saccoId") String saccoId,
                                                 @Param("stationId") String stationId,
                                                 @Param("statuses") Collection<LoanStatus> statuses);

    @Query("""
        select l.status as status, count(l) as total
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
        group by l.status
        """)
    List<StatusCountProjection> countByStatusForScope(@Param("saccoId") String saccoId,
                                                      @Param("stationId") String stationId);

    @Query("""
        select l.status as status, count(l) as total
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
        group by l.status
        """)
    List<StatusCountProjection> countByStatusForApplicant(@Param("applicantMemberId") UUID applicantMemberId);

    @Query("""
        select l.status as status, count(l) as total
        from LoanApplication l
        where l.applicantMemberId = :applicantMemberId
          and (cast(:saccoId as string) is null or l.saccoId = :saccoId)
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
        group by l.status
        """)
    List<StatusCountProjection> countByStatusForApplicantScope(@Param("applicantMemberId") UUID applicantMemberId,
                                                               @Param("saccoId") String saccoId,
                                                               @Param("stationId") String stationId);

    @Query("""
        select count(l)
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
        """)
    long countForScope(@Param("saccoId") String saccoId, @Param("stationId") String stationId);

    @Query("""
        select count(l)
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and l.status in :statuses
          and (
            l.disbursementDate >= :yearStartDate
            or (l.disbursementDate is null and l.createdAt >= :yearStartAt)
          )
        """)
    long countDisbursedInYearForScope(@Param("saccoId") String saccoId,
                                      @Param("stationId") String stationId,
                                      @Param("statuses") Collection<LoanStatus> statuses,
                                      @Param("yearStartDate") LocalDate yearStartDate,
                                      @Param("yearStartAt") OffsetDateTime yearStartAt);

    @Query("""
        select count(l)
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and l.status = com.sacco.mvp.domain.LoanStatus.DEFAULTED
          and (
            l.updatedAt >= :yearStartAt
            or (l.updatedAt is null and l.finalDueDate >= :yearStartDate)
            or (l.updatedAt is null and l.finalDueDate is null and l.createdAt >= :yearStartAt)
          )
        """)
    long countDefaultedInYearForScope(@Param("saccoId") String saccoId,
                                      @Param("stationId") String stationId,
                                      @Param("yearStartDate") LocalDate yearStartDate,
                                      @Param("yearStartAt") OffsetDateTime yearStartAt);

    @Query("""
        select l
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(l.stationId) = lower(cast(:stationId as string)))
          and l.status in :statuses
          and (
            l.disbursementDate >= :recentCutoff
            or (l.disbursementDate is null and l.createdAt >= :recentCutoffAt)
          )
        order by coalesce(l.disbursementDate, current_date) desc, l.createdAt desc
        """)
    List<LoanApplication> findRecentDisbursementsForScope(@Param("saccoId") String saccoId,
                                                          @Param("stationId") String stationId,
                                                          @Param("statuses") Collection<LoanStatus> statuses,
                                                          @Param("recentCutoff") LocalDate recentCutoff,
                                                          @Param("recentCutoffAt") OffsetDateTime recentCutoffAt,
                                                          Pageable pageable);

    @Query("""
        select l.status as status, count(l) as total
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:status as string) is null or l.status = :status)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
        group by l.status
        """)
    List<StatusCountProjection> reportStatusCounts(@Param("saccoId") String saccoId,
                                                   @Param("status") LoanStatus status,
                                                   @Param("loanType") LoanType loanType,
                                                   @Param("createdFrom") OffsetDateTime createdFrom,
                                                   @Param("createdToExclusive") OffsetDateTime createdToExclusive);

    @Query("""
        select l.loanType as loanType, count(l) as total
        from LoanApplication l
        where l.saccoId = :saccoId
          and (cast(:status as string) is null or l.status = :status)
          and (cast(:loanType as string) is null or l.loanType = :loanType)
          and (cast(:createdFrom as timestamp) is null or l.createdAt >= :createdFrom)
          and (cast(:createdToExclusive as timestamp) is null or l.createdAt < :createdToExclusive)
        group by l.loanType
        """)
    List<LoanTypeCountProjection> reportTypeCounts(@Param("saccoId") String saccoId,
                                                   @Param("status") LoanStatus status,
                                                   @Param("loanType") LoanType loanType,
                                                   @Param("createdFrom") OffsetDateTime createdFrom,
                                                   @Param("createdToExclusive") OffsetDateTime createdToExclusive);
}
