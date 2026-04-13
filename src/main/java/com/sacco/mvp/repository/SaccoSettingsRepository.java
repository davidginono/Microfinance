package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SaccoSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaccoSettingsRepository extends JpaRepository<SaccoSettings, String> {
}

