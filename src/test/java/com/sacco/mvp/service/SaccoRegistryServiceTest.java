package com.sacco.mvp.service;

import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaccoRegistryServiceTest {

    @Test
    void updateSaccoOnlyPersistsChangedAndNewStations() {
        RegisteredSaccoRepository registeredSaccoRepository = Mockito.mock(RegisteredSaccoRepository.class);
        SaccoStationRepository saccoStationRepository = Mockito.mock(SaccoStationRepository.class);
        SaccoSettingsRepository saccoSettingsRepository = Mockito.mock(SaccoSettingsRepository.class);

        SaccoRegistryService service = new SaccoRegistryService(
            registeredSaccoRepository,
            saccoStationRepository,
            saccoSettingsRepository,
            null
        );

        OffsetDateTime now = OffsetDateTime.now();
        RegisteredSacco existingSacco = RegisteredSacco.builder()
            .saccoId("SACCO-1")
            .saccoName("Example Sacco")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        SaccoStation retainedStation = SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("STN001")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        SaccoStation removedStation = SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("STN002")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        SaccoSettings settings = SaccoSettings.builder()
            .saccoId("SACCO-1")
            .externalStationId("STN001")
            .externalSaccoName("Example Sacco")
            .requiredGuarantors(3)
            .boardSize(3)
            .boardQuorum(2)
            .maxLoanSavingsRatio(new BigDecimal("0.3333"))
            .defaultLanguage("en")
            .createdAt(now)
            .updatedAt(now)
            .build();

        when(registeredSaccoRepository.findById("SACCO-1")).thenReturn(Optional.of(existingSacco));
        when(saccoStationRepository.findBySaccoIdOrderByStationIdAsc("SACCO-1"))
            .thenReturn(List.of(retainedStation, removedStation));
        when(saccoSettingsRepository.findById("SACCO-1")).thenReturn(Optional.of(settings));

        service.updateSacco("SACCO-1", "Example Sacco", "STN001\nSTN003");

        verify(saccoStationRepository, times(2)).save(any(SaccoStation.class));
        verify(saccoStationRepository).save(argThat(matchesStation("STN002", false)));
        verify(saccoStationRepository).save(argThat(matchesStation("STN003", true)));
        verify(saccoStationRepository, never()).save(argThat(matchesStation("STN001", true)));
    }

    private ArgumentMatcher<SaccoStation> matchesStation(String stationId, boolean active) {
        return station -> station != null
            && stationId.equals(station.getStationId())
            && station.isActive() == active;
    }
}
