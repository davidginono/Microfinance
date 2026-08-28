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
        deliver(saccoId, stationId, notificationId, recipientId, eventType, DeliveryContent.plain(subject, message));
    }

    public void deliver(String saccoId,
                        String stationId,
                        UUID notificationId,
                        UUID recipientId,
                        String eventType,
                        DeliveryContent content) {
        if (recipientId == null) {
            return;
        }
        Member recipient = memberRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }
        DeliveryContent resolvedContent = content == null ? DeliveryContent.plain("", "") : content;
        OtpDeliveryChannel channel = stationOtpSettingsService.channel(saccoId, stationId);
        if (channel == OtpDeliveryChannel.EMAIL) {
            notificationEmailService.sendNotificationEmail(
                recipientId,
                resolvedContent.subject(),
                resolvedContent.plainText(),
                resolvedContent.html()
            );
            return;
        }

        boolean smsSent = sendSms(saccoId, stationId, notificationId, recipient, eventType, resolvedContent);
        if (!smsSent && channel == OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK) {
            notificationEmailService.sendNotificationEmail(
                recipientId,
                resolvedContent.subject(),
                resolvedContent.plainText(),
                resolvedContent.html()
            );
        }
    }

    private boolean sendSms(String saccoId,
                            String stationId,
                            UUID notificationId,
                            Member recipient,
                            String eventType,
                            DeliveryContent content) {
        if (recipient.getPhoneVerifiedAt() == null) {
            log.info("SMS notification blocked for member {} and event {}: recipient phone is not verified",
                recipient.getId(), eventType);
            return false;
        }
        String normalizedPhone = TanzaniaPhoneNumber.normalizeOptional(recipient.getPhone());
        if (normalizedPhone == null) {
            log.info("SMS notification blocked for member {} and event {}: recipient phone is missing or invalid",
                recipient.getId(), eventType);
            return false;
        }
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
            result = smsGateway.send(normalizedPhone, smsText(content));
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

    private String smsText(DeliveryContent content) {
        String smsText = content.smsText() == null ? "" : content.smsText().trim();
        if (!smsText.isBlank()) {
            return smsText;
        }
        return smsMessage(content.subject(), content.plainText());
    }

    public record DeliveryContent(String subject, String plainText, String html, String smsText) {
        public static DeliveryContent plain(String subject, String message) {
            return new DeliveryContent(subject, message, null, null);
        }
    }
}
