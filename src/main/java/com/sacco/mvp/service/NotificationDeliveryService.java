package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryService {
    private final MemberRepository memberRepository;
    private final NotificationEmailService notificationEmailService;
    private final SmsGateway smsGateway;
    private final SmsUnitTransactionService smsUnitTransactionService;
    private final SmsUsageAlertService smsUsageAlertService;
    private final StationOtpSettingsService stationOtpSettingsService;

    public void deliver(String saccoId,
                        String stationId,
                        UUID notificationId,
                        UUID recipientId,
                        String eventType,
                        String subject,
                        String message) {
        if (recipientId == null) {
            return;
        }
        Member recipient = memberRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }
        OtpDeliveryChannel channel = stationOtpSettingsService.channel(saccoId, stationId);
        if (channel == OtpDeliveryChannel.EMAIL) {
            notificationEmailService.sendNotificationEmail(recipientId, subject, message);
            return;
        }

        boolean smsSent = sendSms(saccoId, stationId, notificationId, recipient, eventType, subject, message);
        if (!smsSent && channel == OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK) {
            notificationEmailService.sendNotificationEmail(recipientId, subject, message);
        }
    }

    private boolean sendSms(String saccoId,
                            String stationId,
                            UUID notificationId,
                            Member recipient,
                            String eventType,
                            String subject,
                            String message) {
        SmsUnitTransactionService.ReservationResult reservation =
            smsUnitTransactionService.reserve(saccoId, stationId, notificationId, eventType);
        if (reservation.alertStatus() != null) {
            smsUsageAlertService.alertStatus(saccoId, stationId, reservation.alertStatus(), reservation.availableUnits());
        }
        if (!reservation.reserved()) {
            if (reservation.invalidScope()) {
                smsUsageAlertService.alertInvalidScope(saccoId, stationId, eventType, reservation.reason());
            }
            log.info("SMS notification blocked for member {} and event {}: {}", recipient.getId(), eventType, reservation.reason());
            return false;
        }

        SmsSendResult result;
        try {
            result = smsGateway.send(recipient.getPhone(), smsMessage(subject, message));
        } catch (RuntimeException ex) {
            result = SmsSendResult.acceptanceUnknown("SMS gateway call ended unexpectedly");
            log.warn("SMS gateway call ended unexpectedly for member {} and event {}", recipient.getId(), eventType, ex);
        }
        SmsUnitTransactionService.CompletionResult completion =
            smsUnitTransactionService.complete(reservation.accountId(), reservation.ledgerId(), result);
        if (completion.alertStatus() != null) {
            smsUsageAlertService.alertStatus(saccoId, stationId, completion.alertStatus(), completion.availableUnits());
        }
        if (!result.sent()) {
            log.info("SMS notification outcome for member {} and event {}: {}", recipient.getId(), eventType, result.message());
        }
        return result.sent();
    }

    private String smsMessage(String subject, String message) {
        String resolvedSubject = subject == null ? "" : subject.trim();
        String resolvedMessage = message == null ? "" : message.trim();
        if (resolvedSubject.isBlank()) {
            return resolvedMessage;
        }
        if (resolvedMessage.isBlank()) {
            return resolvedSubject;
        }
        return resolvedSubject + ": " + resolvedMessage;
    }
}
