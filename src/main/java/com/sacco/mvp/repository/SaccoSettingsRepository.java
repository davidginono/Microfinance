package com.sacco.mvp.repository;

import com.sacco.mvp.domain.SaccoSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SaccoSettingsRepository extends JpaRepository<SaccoSettings, String> {
    List<SaccoSettings> findBySaccoIdIn(Collection<String> saccoIds);
}

