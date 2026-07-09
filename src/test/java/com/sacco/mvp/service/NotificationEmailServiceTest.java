package com.sacco.mvp.service;

import com.sacco.mvp.repository.MemberRepository;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEmailServiceTest {

    @Test
    void overrideRecipientRoutesOutgoingEmailToConfiguredAddress() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        NotificationEmailService service = new NotificationEmailService(memberRepository, provider);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@sacco.local");
        ReflectionTestUtils.setField(service, "overrideRecipient", " ginonodavid625@gmail.com ");

        service.sendDirectEmail("member@example.com", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("ginonodavid625@gmail.com");
    }

    @Test
    void blankOverrideUsesRequestedRecipient() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        NotificationEmailService service = new NotificationEmailService(memberRepository, provider);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@sacco.local");
        ReflectionTestUtils.setField(service, "overrideRecipient", " ");

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
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(
            com.sacco.mvp.domain.Member.builder()
                .id(memberId)
                .email("member@example.com")
                .build()
        ));
        NotificationEmailService service = new NotificationEmailService(memberRepository, provider);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@sacco.local");
        ReflectionTestUtils.setField(service, "overrideRecipient", " audit@example.com ");

        service.sendNotificationEmail(memberId, "Loan update", "Plain body", "<strong>HTML body</strong>");

        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("audit@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("Loan update");
        assertThat(message.getValue().getContent().toString()).contains("MimeMultipart");
    }
}
