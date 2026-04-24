package com.sacco.mvp.service;

import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaccoRegistryService {
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final SaccoConfigurationService saccoConfigurationService;
    private final SaccoLogoStorageService saccoLogoStorageService;

    public List<RegisteredSaccoView> listRegisteredSaccos() {
        return registeredSaccoRepository.findByActiveTrueOrderBySaccoNameAsc().stream()
            .map(sacco -> new RegisteredSaccoView(
                sacco.getSaccoId(),
                sacco.getSaccoName(),
                saccoStationRepository.findBySaccoIdAndActiveTrueOrderByStationIdAsc(sacco.getSaccoId()).stream()
                    .map(SaccoStation::getStationId)
                    .toList(),
                saccoLogoStorageService.hasLogo(sacco.getSaccoId()),
                saccoLogoStorageService.publicLogoUrl(sacco.getSaccoId(), sacco.getUpdatedAt())
            ))
            .toList();
    }

    public RegisteredSacco resolveRegisteredSacco(String saccoId) {
        return registeredSaccoRepository.findById(normalizeSaccoId(saccoId))
            .filter(RegisteredSacco::isActive)
            .orElseThrow(() -> new IllegalStateException("Select a SACCO ID from the list."));
    }

    public String requireStationForSacco(String saccoId, String stationId) {
        String normalizedStationId = normalizeStationId(stationId);
        if (normalizedStationId == null) {
            throw new IllegalStateException("Select a station ID.");
        }
        return saccoStationRepository.findBySaccoIdAndStationIdAndActiveTrue(normalizeSaccoId(saccoId), normalizedStationId)
            .map(SaccoStation::getStationId)
            .orElseThrow(() -> new IllegalStateException("Select a valid station ID for the chosen SACCO."));
    }

    @Transactional
    public void registerSacco(String saccoId, String saccoName, String stationIdsText) {
        registerSacco(saccoId, saccoName, stationIdsText, null);
    }

    @Transactional
    public void registerSacco(String saccoId, String saccoName, String stationIdsText, MultipartFile logoFile) {
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        String normalizedSaccoName = normalizeSaccoName(saccoName);
        LinkedHashSet<String> stationIds = parseStationIds(stationIdsText);
        if (normalizedSaccoId == null) {
            throw new IllegalStateException("Enter a SACCO ID.");
        }
        if (normalizedSaccoName == null) {
            throw new IllegalStateException("Enter a SACCO name.");
        }
        if (stationIds.isEmpty()) {
            throw new IllegalStateException("Enter at least one station ID.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        upsertSacco(normalizedSaccoId, normalizedSaccoName, stationIds, now, false);
        saccoLogoStorageService.store(normalizedSaccoId, logoFile);
        saccoConfigurationService.ensureDefaultLoanProducts(normalizedSaccoId);
    }

    @Transactional
    public void updateSacco(String saccoId, String saccoName, String stationIdsText) {
        updateSacco(saccoId, saccoName, stationIdsText, null);
    }

    @Transactional
    public void updateSacco(String saccoId, String saccoName, String stationIdsText, MultipartFile logoFile) {
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        String normalizedSaccoName = normalizeSaccoName(saccoName);
        LinkedHashSet<String> stationIds = parseStationIds(stationIdsText);
        if (normalizedSaccoId == null) {
            throw new IllegalStateException("SACCO ID is required.");
        }
        if (normalizedSaccoName == null) {
            throw new IllegalStateException("Enter a SACCO name.");
        }
        if (stationIds.isEmpty()) {
            throw new IllegalStateException("Enter at least one station ID.");
        }

        upsertSacco(normalizedSaccoId, normalizedSaccoName, stationIds, OffsetDateTime.now(), true);
        saccoLogoStorageService.store(normalizedSaccoId, logoFile);
    }

    @Transactional
    public void updateStationsOnly(String saccoId, String stationIdsText) {
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        LinkedHashSet<String> stationIds = parseStationIds(stationIdsText);
        if (normalizedSaccoId == null) {
            throw new IllegalStateException("SACCO ID is required.");
        }
        if (stationIds.isEmpty()) {
            throw new IllegalStateException("Enter at least one station ID.");
        }

        RegisteredSacco sacco = registeredSaccoRepository.findById(normalizedSaccoId)
            .filter(RegisteredSacco::isActive)
            .orElseThrow(() -> new IllegalStateException("SACCO not found."));
        upsertSacco(normalizedSaccoId, sacco.getSaccoName(), stationIds, OffsetDateTime.now(), true);
    }

    private void upsertSacco(String saccoId,
                             String saccoName,
                             LinkedHashSet<String> stationIds,
                             OffsetDateTime now,
                             boolean requireExisting) {
        RegisteredSacco sacco = registeredSaccoRepository.findById(saccoId)
            .orElseGet(() -> {
                if (requireExisting) {
                    throw new IllegalStateException("SACCO not found.");
                }
                return RegisteredSacco.builder()
                    .saccoId(saccoId)
                    .createdAt(now)
                    .build();
            });
        sacco.setSaccoName(saccoName);
        sacco.setActive(true);
        sacco.setUpdatedAt(now);
        registeredSaccoRepository.save(sacco);

        List<SaccoStation> existingStations = saccoStationRepository.findBySaccoIdOrderByStationIdAsc(saccoId);
        Map<String, SaccoStation> existingByStationId = existingStations.stream()
            .collect(Collectors.toMap(SaccoStation::getStationId, station -> station, (left, right) -> left, LinkedHashMap::new));
        Set<String> requestedStations = stationIds.stream().collect(Collectors.toSet());
        for (SaccoStation station : existingStations) {
            boolean shouldStayActive = requestedStations.contains(station.getStationId());
            if (station.isActive() != shouldStayActive) {
                station.setActive(shouldStayActive);
                station.setUpdatedAt(now);
                saccoStationRepository.save(station);
            }
        }

        for (String stationId : stationIds) {
            if (existingByStationId.containsKey(stationId)) {
                continue;
            }
            SaccoStation station = SaccoStation.builder()
                    .id(UUID.randomUUID())
                    .saccoId(saccoId)
                    .stationId(stationId)
                    .createdAt(now)
                    .build();
            station.setActive(true);
            station.setUpdatedAt(now);
            saccoStationRepository.save(station);
        }

        SaccoSettings settings = saccoSettingsRepository.findById(saccoId)
            .orElseGet(() -> SaccoSettings.builder()
                .saccoId(saccoId)
                .requiredGuarantors(3)
                .boardSize(3)
                .boardQuorum(2)
                .maxLoanSavingsRatio(new BigDecimal("0.3333"))
                .defaultLanguage("en")
                .createdAt(now)
                .build());
        settings.setExternalSaccoName(saccoName);
        if (settings.getExternalStationId() == null || !requestedStations.contains(settings.getExternalStationId())) {
            settings.setExternalStationId(stationIds.iterator().next());
        }
        settings.setUpdatedAt(now);
        saccoSettingsRepository.save(settings);
    }

    private LinkedHashSet<String> parseStationIds(String stationIdsText) {
        LinkedHashSet<String> stationIds = new LinkedHashSet<>();
        if (stationIdsText == null) {
            return stationIds;
        }
        String[] rawParts = stationIdsText.split("[,\\r\\n]+");
        for (String rawPart : rawParts) {
            String normalized = normalizeStationId(rawPart);
            if (normalized != null) {
                stationIds.add(normalized);
            }
        }
        return stationIds;
    }

    private String normalizeSaccoId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeStationId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeSaccoName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    public record RegisteredSaccoView(
        String saccoId,
        String saccoName,
        List<String> stationIds,
        boolean hasLogo,
        String logoUrl
    ) {
        public String getSaccoId() {
            return saccoId;
        }

        public String getSaccoName() {
            return saccoName;
        }

        public List<String> getStationIds() {
            return stationIds;
        }

        public boolean isHasLogo() {
            return hasLogo;
        }

        public String getLogoUrl() {
            return logoUrl;
        }

        public String getStationIdsText() {
            return stationIds == null ? "" : String.join(System.lineSeparator(), stationIds);
        }
    }
}
