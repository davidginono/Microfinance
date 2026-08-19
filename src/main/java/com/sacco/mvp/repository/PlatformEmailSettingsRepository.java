package com.sacco.mvp.repository;

import com.sacco.mvp.domain.PlatformEmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformEmailSettingsRepository extends JpaRepository<PlatformEmailSettings, String> {
}
