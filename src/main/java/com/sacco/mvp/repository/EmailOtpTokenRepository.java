package com.sacco.mvp.repository;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.EmailOtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpTokenRepository extends JpaRepository<EmailOtpToken, UUID> {
    List<EmailOtpToken> findByEmailIgnoreCaseAndPurposeAndConsumedAtIsNull(String email, EmailOtpPurpose purpose);

    Optional<EmailOtpToken> findTopByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(String email, EmailOtpPurpose purpose);
}
