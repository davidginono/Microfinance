package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailService {
    private final MemberRepository memberRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.username:no-reply@sacco.local}")
    private String fromAddress;

    public void sendDirectEmail(String email, String subject, String message) {
        if (email == null || email.isBlank()) {
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("Mail sender not configured. OTP/notification email for {}: {}", email, message);
            return;
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(email);
            mail.setFrom(fromAddress);
            mail.setSubject(subject);
            mail.setText(message);
            mailSender.send(mail);
        } catch (Exception ex) {
            log.warn("Unable to send direct email to {}: {}", email, ex.getMessage());
        }
    }

    public void sendNotificationEmail(UUID memberId, String subject, String message) {
        if (memberId == null) {
            return;
        }
        Member recipient = memberRepository.findById(memberId).orElse(null);
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }
        sendDirectEmail(recipient.getEmail(), subject, message);
    }
}
