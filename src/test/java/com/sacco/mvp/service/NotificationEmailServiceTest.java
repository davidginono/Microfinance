package com.sacco.mvp.service;

import com.sacco.mvp.repository.MemberRepository;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEmailServiceTest {

    @Test
    void overrideRecipientRoutesOutgoingEmailToConfiguredAddress() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationEmailService service = service(memberRepository, mailSender, " ginonodavid625@gmail.com ");

        service.sendDirectEmail("member@example.com", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("ginonodavid625@gmail.com");
    }

    @Test
    void blankOverrideUsesRequestedRecipient() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationEmailService service = service(memberRepository, mailSender, " ");

        service.sendDirectEmail("member@example.com", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("member@example.com");
    }

    @Test
    void htmlNotificationUsesOverrideRecipient() throws Exception {
        UUID memberId = UUID.randomUUID();
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(
            com.sacco.mvp.domain.Member.builder()
                .id(memberId)
                .email("member@example.com")
                .build()
        ));
        NotificationEmailService service = service(memberRepository, mailSender, " audit@example.com ");

        service.sendNotificationEmail(memberId, "Loan update", "Plain body", "<strong>HTML body</strong>");

        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("audit@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("Loan update");
        assertThat(message.getValue().getContent().toString()).contains("MimeMultipart");
    }

    @Test
    void htmlNotificationDoesNotSendPlainFallbackAfterSmtpSendFailure() {
        UUID memberId = UUID.randomUUID();
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(
            com.sacco.mvp.domain.Member.builder()
                .id(memberId)
                .email("member@example.com")
                .build()
        ));
        doThrow(new MailSendException("SMTP unavailable"))
            .when(mailSender).send(any(MimeMessage.class));
        NotificationEmailService service = service(memberRepository, mailSender, "");

        service.sendNotificationEmail(memberId, "Loan update", "Plain body", "<strong>HTML body</strong>");

        verify(mailSender).send(any(MimeMessage.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void requiredApprovalEmailFailsWhenMailSenderIsUnavailable() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        PlatformMailSenderFactory factory = mock(PlatformMailSenderFactory.class);
        PlatformEmailSettingsService emailSettings = mock(PlatformEmailSettingsService.class);
        when(factory.getMailSender()).thenReturn(null);
        NotificationEmailService service = new NotificationEmailService(memberRepository, factory, emailSettings);

        assertThatThrownBy(() -> service.sendRequiredDirectEmail(
            "reviewer@example.com",
            "Approval code",
            "Your code is 123456"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not configured");
    }

    @Test
    void requiredApprovalEmailPropagatesSmtpFailure() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP unavailable"))
            .when(mailSender).send(any(SimpleMailMessage.class));
        NotificationEmailService service = service(memberRepository, mailSender, "");

        assertThatThrownBy(() -> service.sendRequiredDirectEmail(
            "reviewer@example.com",
            "Approval code",
            "Your code is 123456"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("could not be delivered")
            .hasCauseInstanceOf(MailSendException.class);
    }

    @Test
    void mailConfigurationUsesRuntimeCredentialsAndBoundedSmtpTimeouts() throws Exception {
        String configuration = Files.readString(Path.of("src/main/resources/application.yml"));
        String mailConfiguration = configuration.substring(
            configuration.indexOf("  mail:"),
            configuration.indexOf("  security:")
        );

        assertThat(mailConfiguration)
            .contains("username: ${SPRING_MAIL_USERNAME:}")
            .contains("password: ${SPRING_MAIL_PASSWORD:}")
            .contains("connectiontimeout: ${SPRING_MAIL_CONNECTION_TIMEOUT_MS:10000}")
            .contains("timeout: ${SPRING_MAIL_READ_TIMEOUT_MS:10000}")
            .contains("writetimeout: ${SPRING_MAIL_WRITE_TIMEOUT_MS:10000}")
            .doesNotContain("password: qwerty1!");
        assertThat(configuration.replace("\r\n", "\n"))
            .contains("encryption-key: ${APP_SECRETS_ENCRYPTION_KEY:}")
            .contains("management:\n  health:\n    mail:\n      enabled: false");
    }

    private NotificationEmailService service(MemberRepository memberRepository, JavaMailSender mailSender, String overrideRecipient) {
        PlatformMailSenderFactory factory = mock(PlatformMailSenderFactory.class);
        PlatformEmailSettingsService emailSettings = mock(PlatformEmailSettingsService.class);
        when(factory.getMailSender()).thenReturn(mailSender);
        when(emailSettings.resolvedConfig()).thenReturn(new PlatformEmailSettingsService.ResolvedEmailConfig(
            true,
            "smtp.example.com",
            465,
            "user",
            "secret",
            "no-reply@sacco.local",
            overrideRecipient,
            true,
            false,
            10_000,
            10_000,
            10_000,
            true,
            true,
            true
        ));
        return new NotificationEmailService(memberRepository, factory, emailSettings);
    }
}
