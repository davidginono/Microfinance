package com.sacco.mvp.service;

import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaccoRegistryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void updateSaccoOnlyPersistsChangedAndNewStations() {
        RegisteredSaccoRepository registeredSaccoRepository = Mockito.mock(RegisteredSaccoRepository.class);
        SaccoStationRepository saccoStationRepository = Mockito.mock(SaccoStationRepository.class);
        SaccoSettingsRepository saccoSettingsRepository = Mockito.mock(SaccoSettingsRepository.class);
        SaccoLogoStorageService saccoLogoStorageService = Mockito.mock(SaccoLogoStorageService.class);

        SaccoRegistryService service = new SaccoRegistryService(
            registeredSaccoRepository,
            saccoStationRepository,
            saccoSettingsRepository,
            saccoLogoStorageService,
            Mockito.mock(SmsUnitTransactionService.class),
            Mockito.mock(AuditService.class),
            Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class)
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

    @Test
    void updateSaccoStoresReplacementLogoWhenProvided() throws IOException {
        RegisteredSaccoRepository registeredSaccoRepository = Mockito.mock(RegisteredSaccoRepository.class);
        SaccoStationRepository saccoStationRepository = Mockito.mock(SaccoStationRepository.class);
        SaccoSettingsRepository saccoSettingsRepository = Mockito.mock(SaccoSettingsRepository.class);
        SaccoLogoStorageService saccoLogoStorageService = Mockito.mock(SaccoLogoStorageService.class);

        SaccoRegistryService service = new SaccoRegistryService(
            registeredSaccoRepository,
            saccoStationRepository,
            saccoSettingsRepository,
            saccoLogoStorageService,
            Mockito.mock(SmsUnitTransactionService.class),
            Mockito.mock(AuditService.class),
            Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class)
        );

        OffsetDateTime now = OffsetDateTime.now();
        RegisteredSacco existingSacco = RegisteredSacco.builder()
            .saccoId("SACCO-1")
            .saccoName("Example Sacco")
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
            .thenReturn(List.of(SaccoStation.builder()
                .id(UUID.randomUUID())
                .saccoId("SACCO-1")
                .stationId("STN001")
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build()));
        when(saccoSettingsRepository.findById("SACCO-1")).thenReturn(Optional.of(settings));

        MockMultipartFile logoFile = new MockMultipartFile(
            "logoFile",
            "logo.png",
            "image/png",
            pngBytes(120, 120)
        );

        service.updateSacco("SACCO-1", "Example Sacco", "STN001", logoFile);

        verify(saccoLogoStorageService).store("SACCO-1", logoFile);
    }

    @Test
    void updateLogoOnlyStoresLogoRefreshesSaccoAndAudits() throws IOException {
        RegisteredSaccoRepository registeredSaccoRepository = Mockito.mock(RegisteredSaccoRepository.class);
        SaccoStationRepository saccoStationRepository = Mockito.mock(SaccoStationRepository.class);
        SaccoSettingsRepository saccoSettingsRepository = Mockito.mock(SaccoSettingsRepository.class);
        SaccoLogoStorageService saccoLogoStorageService = Mockito.mock(SaccoLogoStorageService.class);
        AuditService auditService = Mockito.mock(AuditService.class);

        SaccoRegistryService service = new SaccoRegistryService(
            registeredSaccoRepository,
            saccoStationRepository,
            saccoSettingsRepository,
            saccoLogoStorageService,
            Mockito.mock(SmsUnitTransactionService.class),
            auditService,
            Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class)
        );

        OffsetDateTime now = OffsetDateTime.now().minusDays(1);
        RegisteredSacco existingSacco = RegisteredSacco.builder()
            .saccoId("SACCO-1")
            .saccoName("Example Sacco")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        MockMultipartFile logoFile = new MockMultipartFile(
            "logoFile",
            "logo.png",
            "image/png",
            pngBytes(120, 120)
        );
        UUID actorId = UUID.randomUUID();
        when(registeredSaccoRepository.findById("SACCO-1")).thenReturn(Optional.of(existingSacco));

        service.updateLogoOnly("SACCO-1", logoFile, actorId);

        verify(saccoLogoStorageService).store("SACCO-1", logoFile);
        verify(registeredSaccoRepository).save(argThat(sacco -> sacco != null
            && "SACCO-1".equals(sacco.getSaccoId())
            && sacco.getUpdatedAt().isAfter(now)));
        verify(auditService).log(
            org.mockito.ArgumentMatchers.eq("REGISTERED_SACCO"),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("ADMIN_UPDATE_SACCO_LOGO"),
            org.mockito.ArgumentMatchers.eq(actorId),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void updateSaccoStoresStationAddressLocation() {
        RegisteredSaccoRepository registeredSaccoRepository = Mockito.mock(RegisteredSaccoRepository.class);
        SaccoStationRepository saccoStationRepository = Mockito.mock(SaccoStationRepository.class);
        SaccoSettingsRepository saccoSettingsRepository = Mockito.mock(SaccoSettingsRepository.class);
        SaccoLogoStorageService saccoLogoStorageService = Mockito.mock(SaccoLogoStorageService.class);

        SaccoRegistryService service = new SaccoRegistryService(
            registeredSaccoRepository,
            saccoStationRepository,
            saccoSettingsRepository,
            saccoLogoStorageService,
            Mockito.mock(SmsUnitTransactionService.class),
            Mockito.mock(AuditService.class),
            Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class)
        );

        OffsetDateTime now = OffsetDateTime.now();
        RegisteredSacco existingSacco = RegisteredSacco.builder()
            .saccoId("SACCO-1")
            .saccoName("Example Sacco")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        SaccoStation station = SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("STN001")
            .addressLocation("Old Location")
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
            .thenReturn(List.of(station));
        when(saccoSettingsRepository.findById("SACCO-1")).thenReturn(Optional.of(settings));

        service.updateSacco(
            "SACCO-1",
            "Example Sacco",
            "STN001",
            Map.of("STN001", "Arusha CBD"),
            null
        );

        verify(saccoStationRepository).save(argThat(updated -> updated != null
            && "STN001".equals(updated.getStationId())
            && "Arusha CBD".equals(updated.getAddressLocation())));
    }

    @Test
    void addStationStoresAddressLocation() {
        RegisteredSaccoRepository registeredSaccoRepository = Mockito.mock(RegisteredSaccoRepository.class);
        SaccoStationRepository saccoStationRepository = Mockito.mock(SaccoStationRepository.class);
        SaccoSettingsRepository saccoSettingsRepository = Mockito.mock(SaccoSettingsRepository.class);
        SaccoLogoStorageService saccoLogoStorageService = Mockito.mock(SaccoLogoStorageService.class);

        SaccoRegistryService service = new SaccoRegistryService(
            registeredSaccoRepository,
            saccoStationRepository,
            saccoSettingsRepository,
            saccoLogoStorageService,
            Mockito.mock(SmsUnitTransactionService.class),
            Mockito.mock(AuditService.class),
            Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class)
        );

        OffsetDateTime now = OffsetDateTime.now();
        RegisteredSacco existingSacco = RegisteredSacco.builder()
            .saccoId("SACCO-1")
            .saccoName("Example Sacco")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        SaccoStation existingStation = SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-1")
            .stationId("STN001")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();
        SaccoStation addedStation = SaccoStation.builder()
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
        when(saccoStationRepository.findBySaccoIdAndActiveTrueOrderByStationIdAsc("SACCO-1"))
            .thenReturn(List.of(existingStation));
        when(saccoStationRepository.findBySaccoIdOrderByStationIdAsc("SACCO-1"))
            .thenReturn(List.of(existingStation));
        when(saccoStationRepository.findBySaccoIdAndStationId("SACCO-1", "STN002"))
            .thenReturn(Optional.of(addedStation));
        when(saccoSettingsRepository.findById("SACCO-1")).thenReturn(Optional.of(settings));

        service.addStation("SACCO-1", "STN002", "Arusha CBD");

        verify(saccoStationRepository).save(argThat(station -> station != null
            && "STN002".equals(station.getStationId())
            && "Arusha CBD".equals(station.getAddressLocation())));
    }

    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private ArgumentMatcher<SaccoStation> matchesStation(String stationId, boolean active) {
        return station -> station != null
            && stationId.equals(station.getStationId())
            && station.isActive() == active;
    }
}
