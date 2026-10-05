package com.sacco.mvp.accounting.policy;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

interface AccountingPolicyRepository extends JpaRepository<AccountingPolicy, UUID> {
    Optional<AccountingPolicy> findByIdAndSaccoId(UUID id, String saccoId);
    boolean existsBySaccoId(String saccoId);
    boolean existsByMakerId(UUID makerId);
    Optional<AccountingPolicy> findBySaccoIdAndRequestKey(String saccoId, UUID requestKey);
    Page<AccountingPolicy> findBySaccoIdOrderByPolicyVersionDesc(String saccoId, Pageable pageable);
    @Query("select coalesce(max(p.policyVersion), 0) from AccountingPolicy p where p.saccoId = :institution")
    int latestVersion(@Param("institution") String institution);
    @Query("select p from AccountingPolicy p, AccountingPolicyApproval a where p.id = a.policyId " +
        "and p.saccoId = :institution and a.saccoId = :institution and a.decision = 'APPROVED' " +
        "and p.effectiveFrom <= :date order by p.effectiveFrom desc, p.policyVersion desc")
    List<AccountingPolicy> approvedAt(@Param("institution") String institution, @Param("date") LocalDate date, Pageable pageable);
}
