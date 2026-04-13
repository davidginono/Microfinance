package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SavingsAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount, UUID> {
    Optional<SavingsAccount> findByMemberId(UUID memberId);
}
