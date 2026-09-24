package com.sacco.mvp.service;

import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
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

    public void sendDirectEmail(String email, String subject, String message) {
        sendDirectEmail(email, subject, message, false);
    }

    public void sendRequiredDirectEmail(String email, String subject, String message) {
        sendDirectEmail(email, subject, message, true);
    }

    private void sendDirectEmail(String email, String subject, String message, boolean deliveryRequired) {
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
            log.info("Mail sender not configured. OTP/notification email for {}: {}", email, message);
            return;
        }

        PlatformEmailSettingsService.ResolvedEmailConfig config = emailSettingsService.resolvedConfig();
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(resolveRecipient(email, config));
            mail.setFrom(config.fromAddress());
            mail.setSubject(subject);
            mail.setText(message);
            mailSender.send(mail);
        } catch (Exception ex) {
            log.warn("Unable to send direct email to {}: {}", email, ex.getMessage());
            if (deliveryRequired) {
                throw new IllegalStateException("The approval email could not be delivered. Please try again.", ex);
            }
        }
    }

    public void sendNotificationEmail(UUID memberId, String subject, String message) {
        sendNotificationEmail(memberId, subject, message, null);
    }

    public void sendNotificationEmail(UUID memberId, String subject, String message, String html) {
        if (memberId == null) {
            return;
        }
        var recipient = memberRepository.findById(memberId).orElse(null);
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }
        if (html == null || html.isBlank()) {
            sendDirectEmail(recipient.getEmail(), subject, message);
            return;
        }
        sendDirectHtmlEmail(recipient.getEmail(), subject, message, html);
    }

    private void sendDirectHtmlEmail(String email, String subject, String text, String html) {
        JavaMailSender mailSender = mailSenderFactory.getMailSender();
        if (mailSender == null) {
            log.info("Mail sender not configured. HTML notification email for {}: {}", email, text);
            return;
        }

        PlatformEmailSettingsService.ResolvedEmailConfig config = emailSettingsService.resolvedConfig();
        MimeMessage mail;
        try {
            mail = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, "UTF-8");
            helper.setTo(resolveRecipient(email, config));
            helper.setFrom(config.fromAddress());
            helper.setSubject(subject);
            helper.setText(text == null ? "" : text, html);
        } catch (Exception ex) {
            log.warn("Unable to prepare HTML notification email to {}: {}", email, ex.getMessage());
            sendDirectEmail(email, subject, text);
            return;
        }

        try {
            mailSender.send(mail);
        } catch (Exception ex) {
            log.warn("Unable to send HTML notification email to {}: {}", email, ex.getMessage());
        }
    }

    private String resolveRecipient(String email, PlatformEmailSettingsService.ResolvedEmailConfig config) {
        String overrideRecipient = config.overrideRecipient();
        if (overrideRecipient != null && !overrideRecipient.isBlank()) {
            return overrideRecipient.trim().toLowerCase();
        }
        return email.trim();
    }
}
