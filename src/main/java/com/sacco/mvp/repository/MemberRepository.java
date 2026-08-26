package com.sacco.mvp.repository;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    interface StatusCountProjection {
        MemberStatus getStatus();
        long getTotal();
    }

    interface BroadcastRecipientProjection {
        UUID getId();
        String getMemberNo();
    }

    interface SaccoMemberStatsProjection {
        String getSaccoId();
        long getTotalMembers();
        long getActiveMembers();
        long getInactiveMembers();
    }

    Optional<Member> findByMemberNo(String memberNo);
    Optional<Member> findByMemberNoIgnoreCase(String memberNo);
    boolean existsByMemberNoIgnoreCase(String memberNo);
    boolean existsByMemberNoIgnoreCaseAndIdNot(String memberNo, UUID id);
    Optional<Member> findByStaffNo(String staffNo);
    boolean existsByStaffNoIgnoreCase(String staffNo);

    @Query(value = "select nextval('public.staff_number_seq')", nativeQuery = true)
    long nextStaffNumberValue();

    Optional<Member> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    Optional<Member> findByPhone(String phone);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, UUID id);

    Optional<Member> findTopBySaccoIdAndPositionOrderByRankDesc(String saccoId, Position position);

    Optional<Member> findBySaccoIdAndStatusAndMemberNoIgnoreCase(String saccoId, MemberStatus status, String memberNo);

    List<Member> findBySaccoIdAndPositionAndStatus(String saccoId, Position position, MemberStatus status);

    List<Member> findByPositionAndStatus(Position position, MemberStatus status);

    List<Member> findBySaccoIdOrderByFullNameAsc(String saccoId);

    List<Member> findBySaccoIdAndStationIdIgnoreCaseOrderByFullNameAsc(String saccoId, String stationId);

    List<Member> findBySaccoIdAndStatusOrderByFullNameAsc(String saccoId, MemberStatus status);

    @Query("""
        select m
        from Member m
        where m.saccoId = :saccoId
          and m.status = :status
          and (cast(:stationId as string) is null or lower(m.stationId) = lower(cast(:stationId as string)))
        order by m.fullName asc
        """)
    List<Member> findByScopeAndStatusOrderByFullNameAsc(@Param("saccoId") String saccoId,
                                                        @Param("stationId") String stationId,
                                                        @Param("status") MemberStatus status);

    /**
     * Broadcast fan-out only needs the recipient id and member number, so this avoids
     * materialising whole {@code Member} graphs (including the eager staff-role collection)
     * for every member in the scope.
     */
    @Query("""
        select m.id as id, m.memberNo as memberNo
        from Member m
        where m.saccoId = :saccoId
          and m.status = :status
          and (cast(:stationId as string) is null or lower(m.stationId) = lower(cast(:stationId as string)))
        order by m.id asc
        """)
    Slice<BroadcastRecipientProjection> findBroadcastRecipients(@Param("saccoId") String saccoId,
                                                                @Param("stationId") String stationId,
                                                                @Param("status") MemberStatus status,
                                                                Pageable pageable);

    @Query("""
        select m
        from Member m
        where m.saccoId = :saccoId
          and m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE
          and (cast(:stationId as string) is null or lower(m.stationId) = lower(cast(:stationId as string)))
          and m.id <> :applicantId
          and (m.memberAccount = true or (m.memberAccount is null and m.position = com.sacco.mvp.domain.Position.MEMBER))
          and lower(coalesce(m.fullName, '')) like concat('%', :query, '%')
        order by m.fullName asc
        """)
    Page<Member> findGuarantorCandidatesByName(@Param("saccoId") String saccoId,
                                               @Param("stationId") String stationId,
                                               @Param("applicantId") UUID applicantId,
                                               @Param("query") String query,
                                               Pageable pageable);

    @Query(
        value = """
            select m.*
            from members m
            where m.sacco_id = :saccoId
              and m.status = 'ACTIVE'
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
              and m.id <> :applicantId
              and (m.member_account = true or (m.member_account is null and m.position = 'MEMBER'))
              and regexp_replace(coalesce(m.member_no, ''), '[^0-9]', '', 'g') like concat('%', :digits)
            order by m.full_name asc
            """,
        countQuery = """
            select count(*)
            from members m
            where m.sacco_id = :saccoId
              and m.status = 'ACTIVE'
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
              and m.id <> :applicantId
              and (m.member_account = true or (m.member_account is null and m.position = 'MEMBER'))
              and regexp_replace(coalesce(m.member_no, ''), '[^0-9]', '', 'g') like concat('%', :digits)
            """,
        nativeQuery = true
    )
    Page<Member> findGuarantorCandidatesByNumberSuffix(@Param("saccoId") String saccoId,
                                                       @Param("stationId") String stationId,
                                                       @Param("applicantId") UUID applicantId,
                                                       @Param("digits") String digits,
                                                       Pageable pageable);

    @Query("""
        select m.saccoId as saccoId,
               count(m) as totalMembers,
               sum(case when m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE then 1 else 0 end) as activeMembers,
               sum(case when m.status = com.sacco.mvp.domain.MemberStatus.INACTIVE then 1 else 0 end) as inactiveMembers
        from Member m
        where m.saccoId in :saccoIds
        group by m.saccoId
        """)
    List<SaccoMemberStatsProjection> summarizeMembersBySacco(@Param("saccoIds") Collection<String> saccoIds);

    @Query("""
        select distinct m
        from Member m
        left join m.staffRoles staffRole
        where m.saccoId = :saccoId
          and m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE
          and m.staffAccessStatus = com.sacco.mvp.domain.StaffAccessStatus.ACTIVE
          and (m.position = :position or staffRole = :position)
        order by m.fullName asc
        """)
    List<Member> findActiveRoleMembers(@Param("saccoId") String saccoId,
                                       @Param("position") Position position);

    @Query("""
        select distinct m
        from Member m
        left join m.staffRoles staffRole
        where m.saccoId = :saccoId
          and lower(m.stationId) = lower(:stationId)
          and m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE
          and m.staffAccessStatus = com.sacco.mvp.domain.StaffAccessStatus.ACTIVE
          and (m.position = :position or staffRole = :position)
        order by m.fullName asc
        """)
    List<Member> findActiveRoleMembersInStation(@Param("saccoId") String saccoId,
                                                @Param("stationId") String stationId,
                                                @Param("position") Position position);

    @Query("""
        select distinct m
        from Member m
        left join m.staffRoles staffRole
        where m.saccoId = :saccoId
          and m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE
          and m.staffAccessStatus = com.sacco.mvp.domain.StaffAccessStatus.ACTIVE
          and (m.position in :positions or staffRole in :positions)
        order by m.fullName asc
        """)
    List<Member> findActiveMembersWithAnyRole(@Param("saccoId") String saccoId,
                                              @Param("positions") Collection<Position> positions);

    @Query("""
        select distinct m
        from Member m
        left join m.staffRoles staffRole
        where m.saccoId = :saccoId
          and lower(m.stationId) = lower(:stationId)
          and m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE
          and m.staffAccessStatus = com.sacco.mvp.domain.StaffAccessStatus.ACTIVE
          and (m.position in :positions or staffRole in :positions)
        order by m.fullName asc
        """)
    List<Member> findActiveMembersWithAnyRoleInStation(@Param("saccoId") String saccoId,
                                                       @Param("stationId") String stationId,
                                                       @Param("positions") Collection<Position> positions);

    @Query(
        value = """
            select distinct m.*
            from members m
            where m.sacco_id = :saccoId
              and m.status = 'ACTIVE'
              and m.staff_access_status = 'ACTIVE'
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
              and exists (
                select 1
                from member_access_claims mac
                where mac.member_id = m.id
                  and mac.claim_name = :claimName
              )
            order by m.full_name asc
            """,
        nativeQuery = true
    )
    List<Member> findActiveMembersWithClaimInStation(@Param("saccoId") String saccoId,
                                                     @Param("stationId") String stationId,
                                                     @Param("claimName") String claimName);

    @Query(
        value = """
            select distinct m.*
            from members m
            where m.sacco_id = :saccoId
              and m.status = 'ACTIVE'
              and m.staff_access_status = 'ACTIVE'
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
              and exists (
                select 1
                from member_access_claims mac
                where mac.member_id = m.id
                  and mac.claim_name in (:claimNames)
              )
            order by m.full_name asc
            """,
        nativeQuery = true
    )
    List<Member> findActiveMembersWithAnyClaimInStation(@Param("saccoId") String saccoId,
                                                        @Param("stationId") String stationId,
                                                        @Param("claimNames") Collection<String> claimNames);

    @Query(
        value = """
            select count(distinct m.id)
            from members m
            where m.sacco_id = :saccoId
              and m.status = 'ACTIVE'
              and m.staff_access_status = 'ACTIVE'
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
              and exists (
                select 1
                from member_access_claims mac
                where mac.member_id = m.id
                  and mac.claim_name in (:claimNames)
              )
            """,
        nativeQuery = true
    )
    long countActiveMembersWithAnyClaimInStation(@Param("saccoId") String saccoId,
                                                 @Param("stationId") String stationId,
                                                 @Param("claimNames") Collection<String> claimNames);

    @Query("""
        select distinct m
        from Member m
        left join m.staffRoles staffRole
        where m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE
          and m.staffAccessStatus = com.sacco.mvp.domain.StaffAccessStatus.ACTIVE
          and (m.position = :position or staffRole = :position)
        order by m.fullName asc
        """)
    List<Member> findActiveGlobalRoleMembers(@Param("position") Position position);

    @Query("""
        select distinct m
        from Member m
        left join m.staffRoles staffRole
        where m.position = :position or staffRole = :position
        """)
    List<Member> findAllWithRole(@Param("position") Position position);

    @Query("""
        select count(m)
        from Member m
        left join m.staffRoles staffRole
        where m.saccoId = :saccoId
          and (m.position = :position or staffRole = :position)
        """)
    long countBySaccoIdAndRole(@Param("saccoId") String saccoId,
                               @Param("position") Position position);

    @Query("""
        select count(m)
        from Member m
        left join m.staffRoles staffRole
        where m.saccoId = :saccoId
          and (m.position = :position or staffRole = :position)
          and m.id <> :existingAccountId
        """)
    long countBySaccoIdAndRoleAndIdNot(@Param("saccoId") String saccoId,
                                       @Param("position") Position position,
                                       @Param("existingAccountId") UUID existingAccountId);

    boolean existsBySaccoIdAndPosition(String saccoId, Position position);

    boolean existsBySaccoIdAndPositionAndIdNot(String saccoId, Position position, UUID id);

    boolean existsBySaccoIdAndStationIdIgnoreCaseAndPosition(String saccoId, String stationId, Position position);

    boolean existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot(String saccoId, String stationId, Position position, UUID id);

    Optional<Member> findBySaccoIdAndStationIdIgnoreCaseAndPosition(String saccoId, String stationId, Position position);

    @Query(
        value = """
            select m.*
            from members m
            where m.sacco_id = :saccoId
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
              and (
                :query = ''
                or cast(m.id as text) ilike concat(cast(:query as text), '%')
              )
            order by m.full_name asc
            """,
        countQuery = """
            select count(*)
            from members m
            where m.sacco_id = :saccoId
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
              and (
                :query = ''
                or cast(m.id as text) ilike concat(cast(:query as text), '%')
              )
            """,
        nativeQuery = true
    )
    Page<Member> findUserAccessPageByUserId(@Param("saccoId") String saccoId,
                                            @Param("stationId") String stationId,
                                            @Param("query") String query,
                                            Pageable pageable);

    @Query("""
        select m
        from Member m
        where m.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(m.stationId) = lower(cast(:stationId as string)))
          and (
            :query = ''
            or lower(coalesce(m.fullName, '')) like concat('%', :query, '%')
          )
        order by m.fullName asc
        """)
    Page<Member> findUserAccessPageByName(@Param("saccoId") String saccoId,
                                          @Param("stationId") String stationId,
                                          @Param("query") String query,
                                          Pageable pageable);

    @Query("""
        select m
        from Member m
        where m.id = :accountId
          and m.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(m.stationId) = lower(cast(:stationId as string)))
        """)
    Optional<Member> findUserAccessByScope(@Param("saccoId") String saccoId,
                                           @Param("stationId") String stationId,
                                           @Param("accountId") UUID accountId);

    @Query(
        value = """
            select m.*
            from members m
            where m.sacco_id = :saccoId
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
              and (
                :query = ''
                or cast(m.id as text) ilike concat(cast(:query as text), '%')
                or lower(coalesce(m.member_no, '')) like concat(cast(:query as text), '%')
                or lower(coalesce(m.staff_no, '')) like concat(cast(:query as text), '%')
                or lower(coalesce(m.full_name, '')) like concat('%', cast(:query as text), '%')
              )
            order by m.full_name asc, coalesce(m.staff_no, m.member_no) asc
            """,
        countQuery = """
            select count(*)
            from members m
            where m.sacco_id = :saccoId
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
              and (
                :query = ''
                or cast(m.id as text) ilike concat(cast(:query as text), '%')
                or lower(coalesce(m.member_no, '')) like concat(cast(:query as text), '%')
                or lower(coalesce(m.staff_no, '')) like concat(cast(:query as text), '%')
                or lower(coalesce(m.full_name, '')) like concat('%', cast(:query as text), '%')
              )
            """,
        nativeQuery = true
    )
    Page<Member> findSaccoDetailMemberPage(@Param("saccoId") String saccoId,
                                           @Param("stationId") String stationId,
                                           @Param("query") String query,
                                           Pageable pageable);

    @Query("""
        select m.status as status, count(m) as total
        from Member m
        where m.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(m.stationId) = lower(cast(:stationId as string)))
        group by m.status
        """)
    List<StatusCountProjection> countByStatusForScope(@Param("saccoId") String saccoId,
                                                      @Param("stationId") String stationId);

    @Query("""
        select count(m)
        from Member m
        where m.saccoId = :saccoId
          and (cast(:stationId as string) is null or lower(m.stationId) = lower(cast(:stationId as string)))
        """)
    long countForScope(@Param("saccoId") String saccoId, @Param("stationId") String stationId);

    @Query("""
        select count(m)
        from Member m
        where m.saccoId = :saccoId
          and m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE
          and (cast(:stationId as string) is null or lower(m.stationId) = lower(cast(:stationId as string)))
          and (m.memberAccount = true or (m.memberAccount is null and m.position = com.sacco.mvp.domain.Position.MEMBER))
        """)
    long countActiveMemberAccountsForScope(@Param("saccoId") String saccoId, @Param("stationId") String stationId);
}
