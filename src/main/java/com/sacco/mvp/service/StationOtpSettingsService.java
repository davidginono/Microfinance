package com.sacco.mvp.service;

import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.OtpRequirementMode;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.SaccoStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StationOtpSettingsService {
    private final SaccoStationRepository stationRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public OtpDeliveryChannel channel(String saccoId, String stationId) {
        return stationRepository.findBySaccoIdAndStationIdAndActiveTrue(saccoId, stationId)
            .filter(station -> !station.isAccessSuspended())
            .map(SaccoStation::getResolvedOtpDeliveryChannel)
            .orElse(OtpDeliveryChannel.EMAIL);
    }

    @Transactional(readOnly = true)
    public OtpRequirementMode requirementMode(String saccoId, String stationId) {
        return stationRepository.findBySaccoIdAndStationIdAndActiveTrue(saccoId, stationId)
            .filter(station -> !station.isAccessSuspended())
            .map(SaccoStation::getResolvedOtpRequirementMode)
            .orElse(OtpRequirementMode.LOGIN_MFA_ONLY);
    }

    @Transactional(readOnly = true)
    public boolean requiresApprovalOtp(String saccoId, String stationId) {
        return requirementMode(saccoId, stationId).requiresApprovalOtp();
    }

    @Transactional(readOnly = true)
    public boolean requiresLoginMfa(String saccoId, String stationId) {
        return requirementMode(saccoId, stationId).requiresLoginMfa();
    }

    @Transactional(readOnly = true)
    public SaccoStation requireStation(String saccoId, String stationId) {
        return stationRepository.findBySaccoIdAndStationIdAndActiveTrue(saccoId, stationId)
            .filter(station -> !station.isAccessSuspended())
            .orElseThrow(() -> new IllegalStateException("The selected station is inactive or unavailable."));
    }

    @Transactional
    public SaccoStation update(String saccoId,
                               String stationId,
                               OtpDeliveryChannel channel,
                               OtpRequirementMode requirementMode,
                               UUID actorMemberId) {
        if (channel == null) {
            throw new IllegalArgumentException("Select an OTP delivery channel.");
        }
        if (requirementMode == null) {
            throw new IllegalArgumentException("Select when OTP verification is required.");
        }
        SaccoStation station = requireStation(saccoId, stationId);
        OtpDeliveryChannel before = station.getResolvedOtpDeliveryChannel();
        OtpRequirementMode beforeMode = station.getResolvedOtpRequirementMode();
        station.setOtpDeliveryChannel(channel);
        station.setOtpRequirementMode(requirementMode);
        station.setUpdatedAt(OffsetDateTime.now());
        SaccoStation saved = stationRepository.save(station);
        auditService.log(
            "SACCO_STATION",
            saved.getId(),
            "MINOR_ADMIN_UPDATE_OTP_DELIVERY_CHANNEL",
            actorMemberId,
            Map.of("otpDeliveryChannel", before.name(), "otpRequirementMode", beforeMode.name()),
            Map.of("otpDeliveryChannel", channel.name(), "otpRequirementMode", requirementMode.name(), "saccoId", saccoId, "stationId", stationId)
        );
        return saved;
    }
}
