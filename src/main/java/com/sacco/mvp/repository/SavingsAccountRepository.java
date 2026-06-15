package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SavingsAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, UUID> {
    interface SaccoSavingsProjection {
        String getSaccoId();
        java.math.BigDecimal getTotalSavings();
    }

    Optional<SavingsAccount> findByMemberId(UUID memberId);

    List<SavingsAccount> findByMemberIdIn(Collection<UUID> memberIds);

    @org.springframework.data.jpa.repository.Query(
        value = """
            select m.sacco_id as saccoId,
                   coalesce(sum(a.available_balance), 0) as totalSavings
            from accounts_savings a
            join members m on m.id = a.member_id
            where m.sacco_id in (:saccoIds)
            group by m.sacco_id
            """,
        nativeQuery = true
    )
    List<SaccoSavingsProjection> summarizeSavingsBySacco(@org.springframework.data.repository.query.Param("saccoIds") Collection<String> saccoIds);

    @org.springframework.data.jpa.repository.Query(
        value = """
            select coalesce(sum(a.available_balance), 0)
            from accounts_savings a
            join members m on m.id = a.member_id
            where m.sacco_id = :saccoId
              and (cast(:stationId as text) is null or lower(m.station_id) = lower(cast(:stationId as text)))
            """,
        nativeQuery = true
    )
    java.math.BigDecimal sumSavingsForScope(@org.springframework.data.repository.query.Param("saccoId") String saccoId,
                                            @org.springframework.data.repository.query.Param("stationId") String stationId);
}
