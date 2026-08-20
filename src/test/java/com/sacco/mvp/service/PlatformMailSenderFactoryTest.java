package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformMailSenderFactoryTest {

    @Test
    void disablesSmtpAuthWhenCredentialsAreBlank() {
        PlatformEmailSettingsService emailSettingsService = mock(PlatformEmailSettingsService.class);
        when(emailSettingsService.resolvedConfig()).thenReturn(config("", ""));
        PlatformMailSenderFactory factory = new PlatformMailSenderFactory(emailSettingsService);

        JavaMailSenderImpl sender = (JavaMailSenderImpl) factory.getMailSender();

        assertThat(sender.getUsername()).isNull();
        assertThat(sender.getPassword()).isNull();
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("false");
    }

    @Test
    void enablesSmtpAuthWhenCredentialsArePresent() {
        PlatformEmailSettingsService emailSettingsService = mock(PlatformEmailSettingsService.class);
        when(emailSettingsService.resolvedConfig()).thenReturn(config("mailer", "secret"));
        PlatformMailSenderFactory factory = new PlatformMailSenderFactory(emailSettingsService);

        JavaMailSenderImpl sender = (JavaMailSenderImpl) factory.getMailSender();

        assertThat(sender.getUsername()).isEqualTo("mailer");
        assertThat(sender.getPassword()).isEqualTo("secret");
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    private PlatformEmailSettingsService.ResolvedEmailConfig config(String username, String password) {
        return new PlatformEmailSettingsService.ResolvedEmailConfig(
            true,
            "localhost",
            1025,
            username,
            password,
            "no-reply@sacco.local",
            "",
            false,
            false,
            10_000,
            10_000,
            10_000,
            true,
            true,
            !password.isBlank()
        );
    }
}
