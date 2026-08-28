package com.sacco.mvp.service;

import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.OtpSelectionPolicy;
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
    private final UserSettingsService userSettingsService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public OtpDeliveryChannel channel(String saccoId, String stationId) {
        return configuration(saccoId, stationId).deliveryChannel();
    }

    @Transactional(readOnly = true)
    public OtpConfiguration configuration(String saccoId, String stationId) {
        return stationRepository.findBySaccoIdAndStationIdAndActiveTrue(saccoId, stationId)
            .filter(station -> !station.isAccessSuspended())
            .map(station -> new OtpConfiguration(
                station.getResolvedOtpDeliveryChannel(),
                station.getResolvedUserOtpSelectionPolicy()
            ))
            .orElseGet(OtpConfiguration::defaults);
    }

    @Transactional(readOnly = true)
    public boolean requiresApprovalOtp(UUID memberId, String saccoId, String stationId) {
        return requiresApprovalOtp(memberId, saccoId, stationId, false);
    }

    @Transactional(readOnly = true)
    public boolean requiresApprovalOtp(UUID memberId,
                                       String saccoId,
                                       String stationId,
                                       boolean platformIdentity) {
        OtpSelectionPolicy policy = selectionPolicy(saccoId, stationId, platformIdentity);
        return policy == OtpSelectionPolicy.BOTH || userSettingsService.requiresApprovalOtp(memberId);
    }

    @Transactional(readOnly = true)
    public boolean requiresLoginMfa(UUID memberId, String saccoId, String stationId) {
        return requiresLoginMfa(memberId, saccoId, stationId, false);
    }

    @Transactional(readOnly = true)
    public boolean requiresLoginMfa(UUID memberId,
                                    String saccoId,
                                    String stationId,
                                    boolean platformIdentity) {
        OtpSelectionPolicy policy = selectionPolicy(saccoId, stationId, platformIdentity);
        if (policy == OtpSelectionPolicy.BOTH) {
            return true;
        }
        UserSettingsService.OtpPreferences preferences = userSettingsService.otpPreferences(memberId);
        return preferences.loginOtpEnabled()
            || (policy == OtpSelectionPolicy.AT_LEAST_ONE && !preferences.approvalOtpEnabled());
    }

    @Transactional(readOnly = true)
    public OtpSelectionPolicy selectionPolicy(String saccoId, String stationId) {
        return configuration(saccoId, stationId).selectionPolicy();
    }

    @Transactional(readOnly = true)
    public OtpSelectionPolicy selectionPolicy(String saccoId,
                                               String stationId,
                                               boolean platformIdentity) {
        return platformIdentity ? OtpSelectionPolicy.NONE : selectionPolicy(saccoId, stationId);
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
                               OtpSelectionPolicy selectionPolicy,
                               UUID actorMemberId) {
        if (channel == null) {
            throw new IllegalArgumentException("Select an OTP delivery channel.");
        }
        OtpSelectionPolicy resolvedSelectionPolicy = selectionPolicy == null
            ? OtpSelectionPolicy.NONE
            : selectionPolicy;
        SaccoStation station = requireStation(saccoId, stationId);
        OtpDeliveryChannel before = station.getResolvedOtpDeliveryChannel();
        OtpSelectionPolicy beforePolicy = station.getResolvedUserOtpSelectionPolicy();
        station.setOtpDeliveryChannel(channel);
        station.setUserOtpSelectionPolicy(resolvedSelectionPolicy);
        station.setUpdatedAt(OffsetDateTime.now());
        SaccoStation saved = stationRepository.save(station);
        auditService.log(
            "SACCO_STATION",
            saved.getId(),
            "MINOR_ADMIN_UPDATE_OTP_SETTINGS",
            actorMemberId,
            Map.of(
                "otpDeliveryChannel", before.name(),
                "userOtpSelectionPolicy", beforePolicy.name()
            ),
            Map.of(
                "otpDeliveryChannel", channel.name(),
                "userOtpSelectionPolicy", resolvedSelectionPolicy.name(),
                "saccoId", saccoId,
                "stationId", stationId
            )
        );
        return saved;
    }

    public record OtpConfiguration(OtpDeliveryChannel deliveryChannel,
                                   OtpSelectionPolicy selectionPolicy) {
        private static OtpConfiguration defaults() {
            return new OtpConfiguration(OtpDeliveryChannel.EMAIL, OtpSelectionPolicy.AT_LEAST_ONE);
        }
    }
}
