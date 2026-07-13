package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanProductSettingRepository extends JpaRepository<LoanProductSetting, UUID> {
    Optional<LoanProductSetting> findBySaccoIdAndLoanTypeAndActiveTrue(String saccoId, LoanType loanType);

    Optional<LoanProductSetting> findBySaccoIdAndLoanType(String saccoId, LoanType loanType);

    Optional<LoanProductSetting> findByIdAndSaccoId(UUID id, String saccoId);

    Optional<LoanProductSetting> findByIdAndSaccoIdAndActiveTrue(UUID id, String saccoId);

    List<LoanProductSetting> findBySaccoIdAndActiveTrue(String saccoId);

    List<LoanProductSetting> findBySaccoIdOrderByLoanTypeAsc(String saccoId);

    boolean existsBySaccoId(String saccoId);

    boolean existsBySaccoIdAndLoanType(String saccoId, LoanType loanType);

    boolean existsBySaccoIdAndProductCodeIgnoreCase(String saccoId, String productCode);

    boolean existsBySaccoIdAndProductCodeIgnoreCaseAndIdNot(String saccoId, String productCode, UUID id);
}

