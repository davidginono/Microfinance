package com.sacco.mvp.service;

import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final SaccoLogoStorageService saccoLogoStorageService;
    private final SmsUnitTransactionService smsUnitTransactionService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private volatile List<RegisteredSaccoView> registeredSaccoCache;

    public List<RegisteredSaccoView> listRegisteredSaccos() {
        List<RegisteredSaccoView> cached = registeredSaccoCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (registeredSaccoCache == null) {
                registeredSaccoCache = loadRegisteredSaccos();
            }
            return registeredSaccoCache;
        }
    }

    private List<RegisteredSaccoView> loadRegisteredSaccos() {
        return registeredSaccoRepository.findByActiveTrueOrderBySaccoNameAsc().stream()
            .filter(sacco -> !"PLATFORM".equalsIgnoreCase(sacco.getSaccoId()))
            .map(sacco -> {
                List<SaccoStation> stations = saccoStationRepository.findBySaccoIdAndActiveTrueOrderByStationIdAsc(sacco.getSaccoId());
                return new RegisteredSaccoView(
                    sacco.getSaccoId(),
                    sacco.getSaccoName(),
                    stations.stream()
                        .map(station -> new StationView(station.getStationId(), station.getAddressLocation()))
                        .toList(),
                    saccoLogoStorageService.hasLogo(sacco.getSaccoId()),
                    saccoLogoStorageService.publicLogoUrl(sacco.getSaccoId(), sacco.getUpdatedAt()),
                    aggregateStationAccess(stations)
                );
            })
            .toList();
    }

    public void invalidateRegisteredSaccoCache() {
        registeredSaccoCache = null;
    }

    private SaccoAccessStatus aggregateStationAccess(List<SaccoStation> stations) {
        if (stations == null || stations.isEmpty()) {
            return SaccoAccessStatus.ACTIVE;
        }
        if (stations.stream().anyMatch(SaccoStation::isAccessSuspended)) {
            return SaccoAccessStatus.SUSPENDED;
        }
        if (stations.stream().anyMatch(station -> station.getResolvedAccessStatus() == SaccoAccessStatus.PAYMENT_DUE)) {
            return SaccoAccessStatus.PAYMENT_DUE;
        }
        return SaccoAccessStatus.ACTIVE;
    }

    public RegisteredSacco resolveRegisteredSacco(String saccoId) {
        return registeredSaccoRepository.findById(normalizeSaccoId(saccoId))
            .filter(RegisteredSacco::isActive)
            .orElseThrow(() -> new IllegalStateException("Select a SACCO ID from the list."));
    }

    public List<String> activeStations(String saccoId) {
        return saccoStationRepository.findBySaccoIdAndActiveTrueOrderByStationIdAsc(normalizeSaccoId(saccoId)).stream()
            .map(SaccoStation::getStationId)
            .toList();
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
    public RegisteredSacco registerSacco(String saccoName, String stationIdsText, MultipartFile logoFile) {
        String generatedSaccoId = nextGeneratedSaccoId();
        registerSacco(generatedSaccoId, saccoName, stationIdsText, logoFile);
        return registeredSaccoRepository.findById(generatedSaccoId)
            .orElseThrow(() -> new IllegalStateException("SACCO was not saved."));
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
        invalidateRegisteredSaccoCache();
    }

    @Transactional
    public void updateSacco(String saccoId, String saccoName, String stationIdsText) {
        updateSacco(saccoId, saccoName, stationIdsText, null);
    }

    @Transactional
    public void updateSacco(String saccoId, String saccoName, String stationIdsText, MultipartFile logoFile) {
        updateSacco(saccoId, saccoName, stationIdsText, Map.of(), logoFile);
    }

    @Transactional
    public void updateSacco(String saccoId,
                            String saccoName,
                            String stationIdsText,
                            Map<String, String> stationAddressLocations,
                            MultipartFile logoFile) {
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
        updateStationAddressLocations(normalizedSaccoId, stationAddressLocations, OffsetDateTime.now());
        saccoLogoStorageService.store(normalizedSaccoId, logoFile);
        invalidateRegisteredSaccoCache();
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
        invalidateRegisteredSaccoCache();
    }

    @Transactional
    public void updateLogoOnly(String saccoId, MultipartFile logoFile, UUID actorMemberId) {
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        if (normalizedSaccoId == null) {
            throw new IllegalStateException("SACCO ID is required.");
        }
        if (logoFile == null || logoFile.isEmpty()) {
            throw new IllegalStateException("Choose a logo image before saving.");
        }
        RegisteredSacco sacco = registeredSaccoRepository.findById(normalizedSaccoId)
            .filter(RegisteredSacco::isActive)
            .orElseThrow(() -> new IllegalStateException("SACCO not found."));
        Map<String, Object> before = Map.of(
            "saccoId", sacco.getSaccoId(),
            "hasLogo", saccoLogoStorageService.hasLogo(normalizedSaccoId),
            "updatedAt", sacco.getUpdatedAt()
        );
        saccoLogoStorageService.store(normalizedSaccoId, logoFile);
        sacco.setUpdatedAt(OffsetDateTime.now());
        registeredSaccoRepository.save(sacco);
        invalidateRegisteredSaccoCache();
        auditService.log("REGISTERED_SACCO", null, "ADMIN_UPDATE_SACCO_LOGO", actorMemberId, before, Map.of(
            "saccoId", sacco.getSaccoId(),
            "hasLogo", true,
            "updatedAt", sacco.getUpdatedAt()
        ));
    }

    @Transactional
    public void removeLogoOnly(String saccoId, UUID actorMemberId) {
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        if (normalizedSaccoId == null) {
            throw new IllegalStateException("SACCO ID is required.");
        }
        RegisteredSacco sacco = registeredSaccoRepository.findById(normalizedSaccoId)
            .filter(RegisteredSacco::isActive)
            .orElseThrow(() -> new IllegalStateException("SACCO not found."));
        Map<String, Object> before = Map.of(
            "saccoId", sacco.getSaccoId(),
            "hasLogo", saccoLogoStorageService.hasLogo(normalizedSaccoId),
            "updatedAt", sacco.getUpdatedAt()
        );
        saccoLogoStorageService.delete(normalizedSaccoId);
        sacco.setUpdatedAt(OffsetDateTime.now());
        registeredSaccoRepository.save(sacco);
        invalidateRegisteredSaccoCache();
        auditService.log("REGISTERED_SACCO", null, "ADMIN_REMOVE_SACCO_LOGO", actorMemberId, before, Map.of(
            "saccoId", sacco.getSaccoId(),
            "hasLogo", false,
            "updatedAt", sacco.getUpdatedAt()
        ));
    }

    @Transactional
    public void addStation(String saccoId, String stationId, String addressLocation) {
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        String normalizedStationId = normalizeStationId(stationId);
        String normalizedAddressLocation = normalizeAddressLocation(addressLocation);
        if (normalizedSaccoId == null) {
            throw new IllegalStateException("SACCO ID is required.");
        }
        if (normalizedStationId == null) {
            throw new IllegalStateException("Enter a station ID.");
        }
        if (normalizedAddressLocation == null) {
            throw new IllegalStateException("Enter the station address or location.");
        }

        RegisteredSacco sacco = registeredSaccoRepository.findById(normalizedSaccoId)
            .filter(RegisteredSacco::isActive)
            .orElseThrow(() -> new IllegalStateException("SACCO not found."));

        LinkedHashSet<String> stationIds = saccoStationRepository.findBySaccoIdAndActiveTrueOrderByStationIdAsc(normalizedSaccoId).stream()
            .map(SaccoStation::getStationId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!stationIds.add(normalizedStationId)) {
            throw new IllegalStateException("That station ID is already saved.");
        }

        upsertSacco(normalizedSaccoId, sacco.getSaccoName(), stationIds, OffsetDateTime.now(), true);
        SaccoStation station = saccoStationRepository.findBySaccoIdAndStationId(normalizedSaccoId, normalizedStationId)
            .orElseThrow(() -> new IllegalStateException("Station was not saved."));
        station.setAddressLocation(normalizedAddressLocation);
        station.setUpdatedAt(OffsetDateTime.now());
        saccoStationRepository.save(station);
        invalidateRegisteredSaccoCache();
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
                smsUnitTransactionService.ensureAccount(saccoId, stationId);
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
            smsUnitTransactionService.ensureAccount(saccoId, stationId);
        }

        SaccoSettings settings = saccoSettingsRepository.findById(saccoId)
            .orElseGet(() -> SaccoSettings.builder()
                .saccoId(saccoId)
                .requiredGuarantors(3)
                .boardSize(3)
                .boardQuorum(2)
                .maxLoanSavingsRatio(new BigDecimal("0.3333"))
                .applicationFee(new BigDecimal("15000.00"))
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

    private String nextGeneratedSaccoId() {
        for (int attempts = 0; attempts < 8999; attempts++) {
            Integer nextValue = jdbcTemplate.queryForObject("select nextval('public.sacco_numeric_id_seq')", Integer.class);
            if (nextValue == null) {
                throw new IllegalStateException("Unable to generate a SACCO ID.");
            }
            String candidate = String.format(Locale.ROOT, "%04d", nextValue);
            if (!registeredSaccoRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No SACCO IDs are available.");
    }

    private void updateStationAddressLocations(String saccoId, Map<String, String> stationAddressLocations, OffsetDateTime now) {
        if (stationAddressLocations == null || stationAddressLocations.isEmpty()) {
            return;
        }
        Map<String, SaccoStation> existingByStationId = saccoStationRepository.findBySaccoIdOrderByStationIdAsc(saccoId).stream()
            .collect(Collectors.toMap(SaccoStation::getStationId, station -> station, (left, right) -> left, LinkedHashMap::new));
        stationAddressLocations.forEach((stationId, addressLocation) -> {
            String normalizedStationId = normalizeStationId(stationId);
            if (normalizedStationId == null) {
                return;
            }
            SaccoStation station = existingByStationId.get(normalizedStationId);
            if (station == null) {
                return;
            }
            String normalizedAddressLocation = normalizeAddressLocation(addressLocation);
            if (!java.util.Objects.equals(station.getAddressLocation(), normalizedAddressLocation)) {
                station.setAddressLocation(normalizedAddressLocation);
                station.setUpdatedAt(now);
                saccoStationRepository.save(station);
            }
        });
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

    private String normalizeAddressLocation(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    public record StationView(String stationId, String addressLocation) {
        public String getStationId() {
            return stationId;
        }

        public String getAddressLocation() {
            return addressLocation;
        }

        public String getAddressLocationLabel() {
            return addressLocation == null || addressLocation.isBlank() ? "Location not set" : addressLocation;
        }
    }

    public record RegisteredSaccoView(
        String saccoId,
        String saccoName,
        List<StationView> stations,
        boolean hasLogo,
        String logoUrl,
        SaccoAccessStatus accessStatus
    ) {
        public String getSaccoId() {
            return saccoId;
        }

        public String getSaccoName() {
            return saccoName;
        }

        public List<String> stationIds() {
            return stations == null ? List.of() : stations.stream()
                .map(StationView::stationId)
                .toList();
        }

        public List<String> getStationIds() {
            return stationIds();
        }

        public List<StationView> getStations() {
            return stations == null ? List.of() : stations;
        }

        public boolean isHasLogo() {
            return hasLogo;
        }

        public String getLogoUrl() {
            return logoUrl;
        }

        public SaccoAccessStatus getAccessStatus() {
            return accessStatus == null ? SaccoAccessStatus.ACTIVE : accessStatus;
        }

        public boolean isAccessSuspended() {
            return getAccessStatus() == SaccoAccessStatus.SUSPENDED;
        }

        public String getAccessStatusLabel() {
            return switch (getAccessStatus()) {
                case ACTIVE -> "Station Access Active";
                case PAYMENT_DUE -> "Station Payment Due";
                case SUSPENDED -> "Station Access Suspended";
            };
        }

        public String getAccessBadgeClass() {
            return switch (getAccessStatus()) {
                case ACTIVE -> "border-emerald-200 bg-emerald-50 text-emerald-700";
                case PAYMENT_DUE -> "border-amber-200 bg-amber-50 text-amber-700";
                case SUSPENDED -> "border-rose-200 bg-rose-50 text-rose-700";
            };
        }

        public String getStationIdsText() {
            return String.join(System.lineSeparator(), stationIds());
        }

        public String getStationListLabel() {
            return String.join(", ", stationIds());
        }
    }
}
