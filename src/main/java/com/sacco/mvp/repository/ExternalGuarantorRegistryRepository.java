package com.sacco.mvp.repository;

import com.sacco.mvp.domain.ExternalGuarantorRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalGuarantorRegistryRepository extends JpaRepository<ExternalGuarantorRegistry, UUID> {
    Optional<ExternalGuarantorRegistry> findBySaccoIdAndExternalStationIdIgnoreCaseAndExternalMemberNoIgnoreCase(
        String saccoId,
        String externalStationId,
        String externalMemberNo
    );
}
