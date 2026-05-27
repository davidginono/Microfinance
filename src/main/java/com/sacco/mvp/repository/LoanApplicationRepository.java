package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
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
    interface StatusCountProjection {
        LoanStatus getStatus();
        long getTotal();
    }

    List<LoanApplication> findByApplicantMemberIdOrderByCreatedAtDesc(UUID applicantMemberId);

    List<LoanApplication> findBySaccoIdOrderByCreatedAtDesc(String saccoId);
    List<LoanApplication> findBySaccoIdAndStatusOrderByCreatedAtAsc(String saccoId, LoanStatus status);
    List<LoanApplication> findBySaccoIdAndStatusInOrderByCreatedAtAsc(String saccoId, List<LoanStatus> statuses);
    List<LoanApplication> findByStatusAndFinalDueDateIsNotNull(LoanStatus status);
    List<LoanApplication> findByApplicantMemberIdAndStatusOrderByCreatedAtDesc(UUID applicantMemberId, LoanStatus status);
    List<LoanApplication> findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(UUID applicantMemberId, List<LoanStatus> statuses);
    List<LoanApplication> findByTopUpSourceLoanIdIn(Collection<UUID> topUpSourceLoanIds);

    Optional<LoanApplication> findByIdAndApplicantMemberId(UUID id, UUID applicantMemberId);

    boolean existsBySaccoIdAndLoanId(String saccoId, String loanId);

    Optional<LoanApplication> findFirstByApplicantMemberIdAndLoanIdOrderByCreatedAtDesc(UUID applicantMemberId, String loanId);

    Optional<LoanApplication> findBySaccoIdAndApplicationNumber(String saccoId, Long applicationNumber);

    List<LoanApplication> findByStatusInAndLoanIdIsNotNull(Collection<LoanStatus> statuses);

    @Query("""
        select l
        from LoanApplication l
        where l.saccoId = :saccoId
          and l.status in :statuses
          and (:stationId is null or lower(l.stationId) = lower(:stationId))
          and (
            :searchTerm is null
            or (:searchByLoanId = true and lower(coalesce(l.loanId, '')) like concat('%', :searchTerm, '%'))
            or (:searchByLoanId = false and str(l.applicationNumber) like concat('%', :searchTerm, '%'))
          )
        order by l.createdAt asc
        """)
    Page<LoanApplication> findQueuePage(@Param("saccoId") String saccoId,
                                        @Param("stationId") String stationId,
                                        @Param("statuses") Collection<LoanStatus> statuses,
                                        @Param("searchTerm") String searchTerm,
                                        @Param("searchByLoanId") boolean searchByLoanId,
                                        Pageable pageable);

    @Query("""
        select l.status as status, count(l) as total
        from LoanApplication l
        where l.saccoId = :saccoId
          and (:stationId is null or lower(l.stationId) = lower(:stationId))
        group by l.status
        """)
    List<StatusCountProjection> countByStatusForScope(@Param("saccoId") String saccoId,
                                                      @Param("stationId") String stationId);

    @Query("""
        select count(l)
        from LoanApplication l
        where l.saccoId = :saccoId
          and (:stationId is null or lower(l.stationId) = lower(:stationId))
        """)
    long countForScope(@Param("saccoId") String saccoId, @Param("stationId") String stationId);

    @Query("""
        select count(l)
        from LoanApplication l
        where l.saccoId = :saccoId
          and (:stationId is null or lower(l.stationId) = lower(:stationId))
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
          and (:stationId is null or lower(l.stationId) = lower(:stationId))
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
          and (:stationId is null or lower(l.stationId) = lower(:stationId))
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
}
