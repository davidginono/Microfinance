package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanProductSettingRepository extends JpaRepository<LoanProductSetting, UUID> {
    Optional<LoanProductSetting> findBySaccoIdAndLoanTypeAndActiveTrue(String saccoId, LoanType loanType);

    List<LoanProductSetting> findBySaccoIdAndActiveTrue(String saccoId);

    List<LoanProductSetting> findBySaccoIdOrderByLoanTypeAsc(String saccoId);

    boolean existsBySaccoId(String saccoId);
}

