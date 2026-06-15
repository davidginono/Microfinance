package com.sacco.mvp.service;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StationOtpDeliveryService {
    private final StationOtpSettingsService stationOtpSettingsService;
    private final NotificationEmailService notificationEmailService;
    private final SmsGateway smsGateway;
    private final SmsUnitTransactionService smsUnitTransactionService;
    private final SmsUsageAlertService smsUsageAlertService;

    public DeliveryReceipt deliver(String saccoId,
                                   String stationId,
                                   String email,
                                   String phone,
                                   EmailOtpPurpose purpose,
                                   String subject,
                                   String introMessage,
                                   String code,
                                   int ttlMinutes) {
        OtpDeliveryChannel channel = stationOtpSettingsService.channel(saccoId, stationId);
        String message = introMessage + System.lineSeparator() + System.lineSeparator()
            + "Your OTP code is: " + code + System.lineSeparator()
            + "This code expires in " + ttlMinutes + " minutes.";
        if (channel == OtpDeliveryChannel.EMAIL) {
            sendEmail(email, subject, message);
            return new DeliveryReceipt(OtpDeliveryChannel.EMAIL, "We sent an OTP code to your registered email.");
        }

        SmsAttempt smsAttempt = sendSms(saccoId, stationId, phone, purpose, code, ttlMinutes);
        if (smsAttempt.delivered()) {
            return new DeliveryReceipt(OtpDeliveryChannel.SMS, "We sent an OTP code to your registered phone.");
        }
        if (channel == OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK) {
            sendEmail(email, subject, message);
            return new DeliveryReceipt(OtpDeliveryChannel.EMAIL, "SMS was unavailable, so we sent the OTP code to your registered email.");
        }
        throw new IllegalStateException(smsAttempt.reason());
    }

    private SmsAttempt sendSms(String saccoId,
                               String stationId,
                               String phone,
                               EmailOtpPurpose purpose,
                               String code,
                               int ttlMinutes) {
        String normalizedPhone = TanzaniaPhoneNumber.normalizeOptional(phone);
        if (normalizedPhone == null) {
            return SmsAttempt.failed("This account has no valid phone number for SMS OTP delivery.");
        }
        UUID notificationId = UUID.randomUUID();
        String eventType = "OTP_" + purpose.name();
        SmsUnitTransactionService.ReservationResult reservation =
            smsUnitTransactionService.reserve(saccoId, stationId, notificationId, eventType);
        if (reservation.alertStatus() != null) {
            smsUsageAlertService.alertStatus(saccoId, stationId, reservation.alertStatus(), reservation.availableUnits());
        }
        if (!reservation.reserved()) {
            if (reservation.invalidScope()) {
                smsUsageAlertService.alertInvalidScope(saccoId, stationId, eventType, reservation.reason());
            }
            return SmsAttempt.failed("SMS OTP could not be sent because " + reservation.reason().toLowerCase() + ".");
        }

        SmsSendResult result;
        try {
            result = smsGateway.send(
                normalizedPhone,
                "Your SACCO OTP code is " + code + ". It expires in " + ttlMinutes + " minutes."
            );
        } catch (RuntimeException ex) {
            result = SmsSendResult.acceptanceUnknown("SMS gateway call ended unexpectedly");
            log.warn("SMS OTP gateway call ended unexpectedly for {}", purpose, ex);
        }
        SmsUnitTransactionService.CompletionResult completion =
            smsUnitTransactionService.complete(reservation.accountId(), reservation.ledgerId(), result);
        if (completion.alertStatus() != null) {
            smsUsageAlertService.alertStatus(saccoId, stationId, completion.alertStatus(), completion.availableUnits());
        }
        if (result.consumesUnit()) {
            return SmsAttempt.sent();
        }
        return SmsAttempt.failed("The SMS OTP provider rejected the request. Try again or contact your Minor Admin.");
    }

    private void sendEmail(String email, String subject, String message) {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("This account has no valid email address for OTP delivery.");
        }
        notificationEmailService.sendDirectEmail(email.trim().toLowerCase(), subject, message);
    }

    public record DeliveryReceipt(OtpDeliveryChannel deliveredBy, String userMessage) {
    }

    private record SmsAttempt(boolean delivered, String reason) {
        static SmsAttempt sent() {
            return new SmsAttempt(true, null);
        }

        static SmsAttempt failed(String reason) {
            return new SmsAttempt(false, reason);
        }
    }
}
