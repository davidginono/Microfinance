package com.sacco.mvp.repository;

import com.sacco.mvp.domain.PlatformSmsSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSmsSettingsRepository extends JpaRepository<PlatformSmsSettings, String> {
}
