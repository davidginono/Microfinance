package com.sacco.mvp.repository;

import com.sacco.mvp.domain.PlatformSessionSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSessionSettingsRepository extends JpaRepository<PlatformSessionSettings, String> {
}
