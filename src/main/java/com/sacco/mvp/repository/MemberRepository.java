package com.sacco.mvp.repository;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {
    interface StatusCountProjection {
        MemberStatus getStatus();
        long getTotal();
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

    List<Member> findByStationIdIgnoreCaseOrderByFullNameAsc(String stationId);

    List<Member> findBySaccoIdAndStatusOrderByFullNameAsc(String saccoId, MemberStatus status);

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
