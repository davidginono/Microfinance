package com.sacco.mvp.service;

import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.SaccoStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StationAccessService {
    private final SaccoStationRepository saccoStationRepository;

    public Optional<SaccoStation> suspendedStation(String saccoId, String stationId) {
        if (saccoId == null || saccoId.isBlank() || stationId == null || stationId.isBlank()) {
            return Optional.empty();
        }
        return saccoStationRepository.findBySaccoIdAndStationId(saccoId, stationId)
            .filter(SaccoStation::isAccessSuspended);
    }
}
