package com.sacco.mvp.accounting.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface AccountingPolicyApprovalRepository extends JpaRepository<AccountingPolicyApproval, UUID> {
    List<AccountingPolicyApproval> findBySaccoIdAndPolicyIdIn(String saccoId, Collection<UUID> ids);
}
