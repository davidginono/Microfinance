package com.sacco.mvp.repository;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    interface SaccoMemberStatsProjection {
        String getSaccoId();
        long getTotalMembers();
        long getActiveMembers();
        long getInactiveMembers();
    }

    Optional<Member> findByMemberNo(String memberNo);
    boolean existsByMemberNoIgnoreCaseAndIdNot(String memberNo, UUID id);

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
          and m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE
          and (:stationId is null or lower(m.stationId) = lower(:stationId))
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

    List<Member> findBySaccoIdIn(Collection<String> saccoIds);

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
          and (m.position in :positions or staffRole in :positions)
        order by m.fullName asc
        """)
    List<Member> findActiveMembersWithAnyRoleInStation(@Param("saccoId") String saccoId,
                                                       @Param("stationId") String stationId,
                                                       @Param("positions") Collection<Position> positions);

    @Query("""
        select distinct m
        from Member m
        left join m.staffRoles staffRole
        where m.status = com.sacco.mvp.domain.MemberStatus.ACTIVE
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

    boolean existsBySaccoIdAndPosition(String saccoId, Position position);

    boolean existsBySaccoIdAndPositionAndIdNot(String saccoId, Position position, UUID id);

    boolean existsBySaccoIdAndStationIdIgnoreCaseAndPosition(String saccoId, String stationId, Position position);

    boolean existsBySaccoIdAndStationIdIgnoreCaseAndPositionAndIdNot(String saccoId, String stationId, Position position, UUID id);

    @Query("""
        select m
        from Member m
        where m.saccoId = :saccoId
          and (
            :query = ''
            or lower(coalesce(m.email, '')) like concat('%', :query, '%')
            or lower(coalesce(m.memberNo, '')) like concat('%', :query, '%')
          )
        """)
    Page<Member> findUserAccessPage(@Param("saccoId") String saccoId,
                                    @Param("query") String query,
                                    Pageable pageable);

    @Query("""
        select m.status as status, count(m) as total
        from Member m
        where m.saccoId = :saccoId
          and (:stationId is null or lower(m.stationId) = lower(:stationId))
        group by m.status
        """)
    List<StatusCountProjection> countByStatusForScope(@Param("saccoId") String saccoId,
                                                      @Param("stationId") String stationId);

    @Query("""
        select count(m)
        from Member m
        where m.saccoId = :saccoId
          and (:stationId is null or lower(m.stationId) = lower(:stationId))
        """)
    long countForScope(@Param("saccoId") String saccoId, @Param("stationId") String stationId);
}
