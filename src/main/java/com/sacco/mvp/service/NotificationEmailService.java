package com.sacco.mvp.service;

import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailService {
    private final MemberRepository memberRepository;
    private final PlatformMailSenderFactory mailSenderFactory;
    private final PlatformEmailSettingsService emailSettingsService;
    private final SaccoRegistryService saccoRegistryService;

    public void sendDirectEmail(String email, String subject, String message) {
        sendDirectEmail(null, email, subject, message, null, false);
    }

    public void sendDirectEmail(String saccoId, String email, String subject, String message) {
        sendDirectEmail(saccoId, email, subject, message, null, false);
    }

    public void sendRequiredDirectEmail(String email, String subject, String message) {
        sendDirectEmail(null, email, subject, message, null, true);
    }

    public void sendRequiredDirectEmail(String saccoId, String email, String subject, String message) {
        sendDirectEmail(saccoId, email, subject, message, null, true);
    }

    private void sendDirectEmail(String saccoId, String email, String subject, String message, String html, boolean deliveryRequired) {
        if (email == null || email.isBlank()) {
            if (deliveryRequired) {
                throw new IllegalStateException("A recipient email address is required for OTP delivery.");
            }
            return;
        }
        JavaMailSender mailSender = mailSenderFactory.getMailSender();
        if (mailSender == null) {
            if (deliveryRequired) {
                throw new IllegalStateException("Email delivery is not configured. Contact the system administrator.");
            }
            log.info("Mail sender not configured. Email was not sent.");
            return;
        }

        PlatformEmailSettingsService.ResolvedEmailConfig config = emailSettingsService.resolvedConfig();
        try {
            MimeMessage mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");
            helper.setTo(resolveRecipient(email, config));
            helper.setFrom(config.fromAddress());
            String institutionName = saccoRegistryService.notificationInstitutionName(saccoId).orElse(null);
            helper.setSubject(NotificationMessageTemplate.title(subject));
            helper.setText(NotificationMessageTemplate.text(message),
                NotificationMessageTemplate.html(institutionName, subject, message, html));
            mailSender.send(mail);
        } catch (Exception ex) {
            log.warn("Unable to send direct email to {}: {}", email, ex.getMessage());
            if (deliveryRequired) {
                throw new IllegalStateException("Email could not be sent. Check email settings and try again.", ex);
            }
        }
    }

    public void sendNotificationEmail(UUID memberId, String subject, String message) {
        sendNotificationEmail(memberId, subject, message, null);
    }

    public void sendNotificationEmail(UUID memberId, String subject, String message, String html) {
        sendNotificationEmail(null, memberId, subject, message, html);
    }

    public void sendNotificationEmail(String saccoId, UUID memberId, String subject, String message, String html) {
        if (memberId == null) {
            return;
        }
        var recipient = memberRepository.findById(memberId).orElse(null);
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }
        sendDirectEmail(saccoId == null ? recipient.getSaccoId() : saccoId, recipient.getEmail(), subject, message, html, false);
    }

    private String resolveRecipient(String email, PlatformEmailSettingsService.ResolvedEmailConfig config) {
        String overrideRecipient = config.overrideRecipient();
        if (overrideRecipient != null && !overrideRecipient.isBlank()) {
            return overrideRecipient.trim().toLowerCase();
        }
        return email.trim();
    }
}
