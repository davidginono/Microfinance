package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanProductVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanProductVersionRepository extends JpaRepository<LoanProductVersion, UUID> {
    List<LoanProductVersion> findByLoanProductSettingIdInOrderByCreatedAtDesc(Collection<UUID> loanProductSettingIds);

    Optional<LoanProductVersion> findTopByLoanProductSettingIdOrderByVersionNumberDesc(UUID loanProductSettingId);
}
