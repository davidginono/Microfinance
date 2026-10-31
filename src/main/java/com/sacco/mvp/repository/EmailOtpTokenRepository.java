package com.sacco.mvp.repository;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.EmailOtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpTokenRepository extends JpaRepository<EmailOtpToken, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update EmailOtpToken t set t.consumedAt=:now where t.memberId=:id and t.purpose=com.sacco.mvp.domain.EmailOtpPurpose.REGISTRATION and t.consumedAt is null")
    int invalidateRegistration(@org.springframework.data.repository.query.Param("id") UUID id,
        @org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now);
    List<EmailOtpToken> findByEmailIgnoreCaseAndPurposeAndConsumedAtIsNull(String email, EmailOtpPurpose purpose);

    List<EmailOtpToken> findByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNull(String email, EmailOtpPurpose purpose, UUID memberId);

    Optional<EmailOtpToken> findTopByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(String email, EmailOtpPurpose purpose);

    Optional<EmailOtpToken> findTopByEmailIgnoreCaseAndPurposeAndMemberIdAndConsumedAtIsNullOrderByCreatedAtDesc(String email, EmailOtpPurpose purpose, UUID memberId);
}
