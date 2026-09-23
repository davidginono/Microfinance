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
        return deliver(saccoId, stationId, email, phone, purpose, subject, introMessage, code, ttlMinutes, null, null, null);
    }

    public DeliveryReceipt deliver(String saccoId,
                                   String stationId,
                                   String email,
                                   String phone,
                                   EmailOtpPurpose purpose,
                                   String subject,
                                   String introMessage,
                                   String code,
                                   int ttlMinutes,
                                   UUID loanApplicationId,
                                   UUID applicantMemberId,
                                   UUID recipientMemberId) {
        OtpDeliveryChannel channel = stationOtpSettingsService.channel(saccoId, stationId);
        String message = introMessage + System.lineSeparator() + System.lineSeparator()
            + "Your OTP code is: " + code + System.lineSeparator()
            + "This code expires in " + ttlMinutes + " minutes.";
        if (channel == OtpDeliveryChannel.EMAIL) {
            sendEmail(email, subject, message);
            return new DeliveryReceipt(OtpDeliveryChannel.EMAIL, "We sent an OTP code to your registered email.");
        }

        SmsAttempt smsAttempt = sendSms(
            saccoId,
            stationId,
            phone,
            purpose,
            introMessage,
            code,
            ttlMinutes,
            new SmsUnitTransactionService.SmsUsageContext(loanApplicationId, applicantMemberId, recipientMemberId)
        );
        if (smsAttempt.delivered()) {
            return new DeliveryReceipt(OtpDeliveryChannel.SMS, "We sent an OTP code to your registered phone.");
        }
        if (channel == OtpDeliveryChannel.SMS_WITH_EMAIL_FALLBACK) {
            sendEmail(email, subject, message);
            return new DeliveryReceipt(OtpDeliveryChannel.EMAIL, "SMS was unavailable, so we sent the OTP code to your registered email.");
        }
        throw new IllegalStateException(smsAttempt.reason());
    }

    public DeliveryReceipt deliverEmailOnly(String email,
                                            String subject,
                                            String introMessage,
                                            String code,
                                            int ttlMinutes) {
        String message = introMessage + System.lineSeparator() + System.lineSeparator()
            + "Your OTP code is: " + code + System.lineSeparator()
            + "This code expires in " + ttlMinutes + " minutes.";
        sendEmail(email, subject, message);
        return new DeliveryReceipt(OtpDeliveryChannel.EMAIL, "We sent an OTP code to your registered email.");
    }

    private SmsAttempt sendSms(String saccoId,
                               String stationId,
                               String phone,
                               EmailOtpPurpose purpose,
                               String introMessage,
                               String code,
                               int ttlMinutes,
                               SmsUnitTransactionService.SmsUsageContext usageContext) {
        String normalizedPhone = TanzaniaPhoneNumber.normalizeOptional(phone);
        if (normalizedPhone == null) {
            return SmsAttempt.failed("This account has no valid phone number for SMS OTP delivery.");
        }
        UUID notificationId = UUID.randomUUID();
        String eventType = "OTP_" + purpose.name();
        SmsUnitTransactionService.ReservationResult reservation = isEmptyContext(usageContext)
            ? smsUnitTransactionService.reserve(saccoId, stationId, notificationId, eventType)
            : smsUnitTransactionService.reserve(saccoId, stationId, notificationId, eventType, usageContext);
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
                smsOtpMessage(introMessage, code, ttlMinutes)
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
        return SmsAttempt.failed("The SMS OTP provider rejected the request. Try again or contact your SACCOS Admin.");
    }

    private String smsOtpMessage(String introMessage, String code, int ttlMinutes) {
        String intro = introMessage == null ? "" : introMessage.trim().replaceAll("\\s+", " ");
        if (intro.isBlank()) {
            return "Your SACCO OTP code is " + code + ". It expires in " + ttlMinutes + " minutes.";
        }
        return "OTP: " + code + ". Expires in " + ttlMinutes + " minutes. " + intro;
    }

    private boolean isEmptyContext(SmsUnitTransactionService.SmsUsageContext usageContext) {
        return usageContext == null
            || (usageContext.loanApplicationId() == null
                && usageContext.applicantMemberId() == null
                && usageContext.recipientMemberId() == null);
    }

    private void sendEmail(String email, String subject, String message) {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("This account has no valid email address for OTP delivery.");
        }
        notificationEmailService.sendRequiredDirectEmail(email.trim().toLowerCase(), subject, message);
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
