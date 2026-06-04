package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
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
    private final NotificationDeliveryPreferenceService preferenceService;
    private final SmsGateway smsGateway;

    public void deliver(String saccoId, UUID recipientId, String eventType, String subject, String message) {
        if (recipientId == null) {
            return;
        }
        Member recipient = memberRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }
        String resolvedSaccoId = saccoId == null || saccoId.isBlank() ? recipient.getSaccoId() : saccoId;
        if (preferenceService.emailEnabled(resolvedSaccoId, eventType)) {
            notificationEmailService.sendNotificationEmail(recipientId, subject, message);
        }
        if (preferenceService.smsEnabled(resolvedSaccoId, eventType)) {
            SmsSendResult result = smsGateway.send(recipient.getPhone(), smsMessage(subject, message));
            if (!result.sent()) {
                log.info("SMS notification skipped for member {} and event {}: {}", recipientId, eventType, result.message());
            }
        }
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
