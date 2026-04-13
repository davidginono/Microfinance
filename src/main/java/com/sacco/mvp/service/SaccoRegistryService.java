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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaccoRegistryService {
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final SaccoConfigurationService saccoConfigurationService;

    public List<RegisteredSaccoView> listRegisteredSaccos() {
        return registeredSaccoRepository.findByActiveTrueOrderBySaccoNameAsc().stream()
            .map(sacco -> new RegisteredSaccoView(
                sacco.getSaccoId(),
                sacco.getSaccoName(),
                saccoStationRepository.findBySaccoIdAndActiveTrueOrderByStationIdAsc(sacco.getSaccoId()).stream()
                    .map(SaccoStation::getStationId)
                    .toList()
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
        return saccoStationRepository.findBySaccoIdAndStationId(normalizeSaccoId(saccoId), normalizedStationId)
            .filter(SaccoStation::isActive)
            .map(SaccoStation::getStationId)
            .orElseThrow(() -> new IllegalStateException("Select a valid station ID for the chosen SACCO."));
    }

    @Transactional
    public void registerSacco(String saccoId, String saccoName, String stationIdsText) {
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
        RegisteredSacco sacco = registeredSaccoRepository.findById(normalizedSaccoId)
            .orElseGet(() -> RegisteredSacco.builder()
                .saccoId(normalizedSaccoId)
                .createdAt(now)
                .build());
        sacco.setSaccoName(normalizedSaccoName);
        sacco.setActive(true);
        sacco.setUpdatedAt(now);
        registeredSaccoRepository.save(sacco);

        for (String stationId : stationIds) {
            SaccoStation station = saccoStationRepository.findBySaccoIdAndStationId(normalizedSaccoId, stationId)
                .orElseGet(() -> SaccoStation.builder()
                    .id(UUID.randomUUID())
                    .saccoId(normalizedSaccoId)
                    .stationId(stationId)
                    .createdAt(now)
                    .build());
            station.setActive(true);
            station.setUpdatedAt(now);
            saccoStationRepository.save(station);
        }

        SaccoSettings settings = saccoSettingsRepository.findById(normalizedSaccoId)
            .orElseGet(() -> SaccoSettings.builder()
                .saccoId(normalizedSaccoId)
                .requiredGuarantors(3)
                .boardSize(3)
                .boardQuorum(2)
                .maxLoanSavingsRatio(new BigDecimal("0.3333"))
                .defaultLanguage("en")
                .createdAt(now)
                .build());
        settings.setExternalSaccoName(normalizedSaccoName);
        settings.setExternalStationId(stationIds.iterator().next());
        settings.setUpdatedAt(now);
        saccoSettingsRepository.save(settings);

        saccoConfigurationService.ensureDefaultLoanProducts(normalizedSaccoId);
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
        List<String> stationIds
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
    }
}
