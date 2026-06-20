package com.sacco.mvp.service;

import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

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
}
