package com.sacco.mvp.repository;

import com.sacco.mvp.domain.LoanProductsVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanProductsVersionRepository extends JpaRepository<LoanProductsVersion, UUID> {
    List<LoanProductsVersion> findBySaccoIdOrderByCreatedAtDesc(String saccoId);

    Optional<LoanProductsVersion> findTopBySaccoIdOrderByVersionNumberDesc(String saccoId);
}
