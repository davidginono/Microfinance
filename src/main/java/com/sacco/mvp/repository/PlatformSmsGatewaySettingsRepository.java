package com.sacco.mvp.repository;

import com.sacco.mvp.domain.PlatformSmsGatewaySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSmsGatewaySettingsRepository extends JpaRepository<PlatformSmsGatewaySettings, String> {
}
