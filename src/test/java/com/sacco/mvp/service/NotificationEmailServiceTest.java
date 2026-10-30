package com.sacco.mvp.service;

import com.sacco.mvp.repository.MemberRepository;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
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
    void overrideRecipientRoutesOutgoingEmailToConfiguredAddress() throws Exception {
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationEmailService service = service(memberRepository, mailSender, " ginonodavid625@gmail.com ");

        service.sendDirectEmail("member@example.com", "Subject", "Body");

        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("ginonodavid625@gmail.com");
    }

    @Test
    void blankOverrideUsesRequestedRecipient() throws Exception {
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationEmailService service = service(memberRepository, mailSender, " ");

        service.sendDirectEmail("member@example.com", "Subject", "Body");

        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("member@example.com");
    }

    @Test
    void htmlNotificationUsesOverrideRecipient() throws Exception {
        UUID memberId = UUID.randomUUID();
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
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
        message.getValue().saveChanges();
        assertThat(content(message.getValue(), "text/html")).isEqualTo("<strong>HTML body</strong>");
        assertThat(content(message.getValue(), "text/plain")).isEqualTo("Plain body");
    }

    @Test
    void htmlNotificationDoesNotSendPlainFallbackAfterSmtpSendFailure() {
        UUID memberId = UUID.randomUUID();
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
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
            .when(mailSender).send(any(MimeMessage.class));
        NotificationEmailService service = service(memberRepository, mailSender, "");

        assertThatThrownBy(() -> service.sendRequiredDirectEmail(
            "reviewer@example.com",
            "Approval code",
            "Your code is 123456"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("could not be sent")
            .hasCauseInstanceOf(MailSendException.class);
    }

    @Test
    void requiredOtpHasStyledHtmlAndPlainTextWithoutLegacyBranding() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationEmailService service = service(mock(MemberRepository.class), sender, "");
        service.sendRequiredDirectEmail("client@example.com", "Your Loan Application Portal registration code",
            "Sign in to Loan Application Portal.\n\nYour OTP code is: 123456\nThis code expires in 10 minutes.");

        ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(mail.capture());
        MimeMessage message = mail.getValue();
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Your Microfinance registration code");
        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@sacco.local");
        assertThat(content(message, "text/plain")).contains("Sign in to Microfinance.", "123456", "10 minutes")
            .doesNotContain("Loan Application Portal", "<html>");
        assertThat(content(message, "text/html")).contains("background:#232f3e", "Microfinance", "font-size:32px", "123456", "10 minutes")
            .doesNotContain("Loan Application Portal");
    }

    @Test
    void notificationTemplateEscapesClientTextAndKeepsActivationLinksClickable() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationEmailService service = service(mock(MemberRepository.class), sender, "");
        String body = "Hello Asha & <img src=x onerror=alert(1)>\n"
            + "https://microfinance.example/claim?token=a&next=b\njavascript:alert(1)";
        service.sendDirectEmail("client@example.com", "Activation <script>alert(1)</script>", body);

        ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(mail.capture());
        mail.getValue().saveChanges();
        assertThat(content(mail.getValue(), "text/plain")).isEqualTo(body);
        assertThat(content(mail.getValue(), "text/html"))
            .contains("Asha &amp; &lt;img", "&lt;script&gt;", "href=\"https://microfinance.example/claim?token=a&amp;next=b\"")
            .doesNotContain("<img", "<script>", "href=\"javascript:");
    }

    @Test
    void existingRichNotificationsAlsoReplaceQueuedLegacyBranding() throws Exception {
        UUID memberId = UUID.randomUUID();
        MemberRepository members = mock(MemberRepository.class);
        when(members.findById(memberId)).thenReturn(Optional.of(com.sacco.mvp.domain.Member.builder()
            .id(memberId).email("client@example.com").build()));
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationEmailService service = service(members, sender, "");
        service.sendNotificationEmail(memberId, "LOAN APPLICATION PORTAL update", "Loan Application Portal update",
            "<strong>Loan Application Portal</strong><a href=\"https://microfinance.example/loan/1\">Details</a>");

        ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(mail.capture());
        mail.getValue().saveChanges();
        assertThat(mail.getValue().getSubject()).isEqualTo("Microfinance update");
        assertThat(content(mail.getValue(), "text/plain")).isEqualTo("Microfinance update");
        assertThat(content(mail.getValue(), "text/html")).contains("<strong>Microfinance</strong>", "https://microfinance.example/loan/1")
            .doesNotContain("Loan Application Portal");
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
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
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

    private String content(Part part, String type) throws Exception {
        if (part.isMimeType(type)) {
            return part.getContent().toString();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = content(multipart.getBodyPart(i), type);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }
}
