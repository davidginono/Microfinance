package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformEmailSettings;
import com.sacco.mvp.repository.PlatformEmailSettingsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformEmailSettingsServiceTest {

    @Test
    void createsDefaultSettingsWhenMissing() {
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.empty());
        when(repository.save(any(PlatformEmailSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformEmailSettingsService service = service(repository, secretService(), factoryProvider());

        PlatformEmailSettings settings = service.settings();

        assertEquals("", settings.getHost());
        assertFalse(settings.isEnabled());
        verify(repository).save(any(PlatformEmailSettings.class));
    }

    @Test
    void fallsBackToEnvironmentWhenDatabaseHostIsBlank() {
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        PlatformEmailSettingsService service = service(repository, secretService(), factoryProvider());
        ReflectionTestUtils.setField(service, "envHost", "smtp.stackmail.com");
        ReflectionTestUtils.setField(service, "envUsername", "noreply@sacco.local");
        ReflectionTestUtils.setField(service, "envPassword", "env-secret");
        ReflectionTestUtils.setField(service, "envFromAddress", "noreply@sacco.local");

        PlatformEmailSettingsService.ResolvedEmailConfig config = service.resolvedConfig();

        assertTrue(config.enabled());
        assertTrue(config.configured());
        assertEquals("smtp.stackmail.com", config.host());
        assertEquals("env-secret", config.password());
        assertEquals("noreply@sacco.local", config.fromAddress());
    }

    @Test
    void savedDatabaseSettingsOverrideEnvironmentFallback() {
        PlatformEmailSettings existing = existingSettings();
        existing.setEnabled(true);
        existing.setHost("smtp.example.com");
        existing.setUsername("mailer");
        existing.setPasswordEncrypted("enc:v1:stored");
        existing.setFromAddress("alerts@example.com");
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenReturn("db-secret");
        PlatformEmailSettingsService service = service(repository, secrets, factoryProvider());
        ReflectionTestUtils.setField(service, "envHost", "smtp.stackmail.com");
        ReflectionTestUtils.setField(service, "envPassword", "env-secret");

        PlatformEmailSettingsService.ResolvedEmailConfig config = service.resolvedConfig();

        assertEquals("smtp.example.com", config.host());
        assertEquals("db-secret", config.password());
        assertEquals("alerts@example.com", config.fromAddress());
        assertTrue(config.enabled());
    }

    @Test
    void disabledDatabaseSettingsDoNotFallBackToEnvironment() {
        PlatformEmailSettings existing = existingSettings();
        existing.setEnabled(false);
        existing.setHost("smtp.example.com");
        existing.setUsername("mailer");
        existing.setPasswordEncrypted("enc:v1:stored");
        existing.setFromAddress("alerts@example.com");
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenReturn("db-secret");
        PlatformEmailSettingsService service = service(repository, secrets, factoryProvider());
        ReflectionTestUtils.setField(service, "envHost", "smtp.stackmail.com");
        ReflectionTestUtils.setField(service, "envPassword", "env-secret");

        PlatformEmailSettingsService.ResolvedEmailConfig config = service.resolvedConfig();

        assertTrue(config.configured());
        assertFalse(config.enabled());
    }

    @Test
    void unreadableStoredPasswordDoesNotBreakResolvedConfig() {
        PlatformEmailSettings existing = existingSettings();
        existing.setEnabled(true);
        existing.setHost("smtp.example.com");
        existing.setUsername("mailer");
        existing.setPasswordEncrypted("enc:v1:stored");
        existing.setFromAddress("alerts@example.com");
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenThrow(new IllegalStateException("Unable to decrypt platform secret."));
        PlatformEmailSettingsService service = service(repository, secrets, factoryProvider());

        PlatformEmailSettingsService.ResolvedEmailConfig config = service.resolvedConfig();

        assertFalse(config.configured());
        assertFalse(config.enabled());
        assertTrue(config.passwordConfigured());
        assertEquals("", config.password());
    }

    @Test
    void updatesValidSettingsEncryptsPasswordAndAudits() {
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        PlatformMailSenderFactory factory = Mockito.mock(PlatformMailSenderFactory.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        when(repository.save(any(PlatformEmailSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(secrets.encrypt("new-secret")).thenReturn("enc:v1:new-secret");
        PlatformEmailSettingsService service = service(repository, secrets, factoryProvider(factory), auditService);
        UUID actorId = UUID.randomUUID();

        PlatformEmailSettings saved = service.updateSettings(
            true,
            " smtp.example.com ",
            465,
            " mailer ",
            "new-secret",
            " Alerts@Example.com ",
            " Override@Example.com ",
            true,
            false,
            10_000,
            10_000,
            10_000,
            actorId
        );

        assertEquals("smtp.example.com", saved.getHost());
        assertEquals("mailer", saved.getUsername());
        assertEquals("alerts@example.com", saved.getFromAddress());
        assertEquals("override@example.com", saved.getOverrideRecipient());
        assertEquals("enc:v1:new-secret", saved.getPasswordEncrypted());
        verify(factory).invalidate();
        verify(auditService).log(eq("PLATFORM_EMAIL_SETTINGS"), isNull(), eq("ADMIN_UPDATE_EMAIL_SETTINGS"), eq(actorId), any(), any());
    }

    @Test
    void keepsStoredPasswordWhenBlankPasswordSubmitted() {
        PlatformEmailSettings existing = existingSettings();
        existing.setPasswordEncrypted("enc:v1:existing");
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(PlatformEmailSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformEmailSettingsService service = service(repository, secrets, factoryProvider());

        PlatformEmailSettings saved = service.updateSettings(
            true, "smtp.example.com", 465, "mailer", "  ", "alerts@example.com", "",
            true, false, 10_000, 10_000, 10_000, UUID.randomUUID()
        );

        assertEquals("enc:v1:existing", saved.getPasswordEncrypted());
        verify(secrets, never()).encrypt(any());
    }

    @Test
    void allowsUnauthenticatedSmtpForMailpit() {
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        when(repository.save(any(PlatformEmailSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformEmailSettingsService service = service(repository, secretService(), factoryProvider());

        PlatformEmailSettings saved = service.updateSettings(true, "localhost", 1025, "", "", "alerts@example.com", "",
            false, false, 10_000, 10_000, 10_000, UUID.randomUUID());
        PlatformEmailSettingsService.ResolvedEmailConfig config = service.resolvedConfig();

        assertEquals("", saved.getUsername());
        assertEquals("", saved.getPasswordEncrypted());
        assertTrue(config.enabled());
        assertTrue(config.configured());
        assertEquals("localhost", config.host());
        assertEquals(1025, config.port());
        assertEquals("alerts@example.com", config.fromAddress());
    }

    @Test
    void rejectsInvalidFromAddress() {
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        PlatformEmailSettingsService service = service(repository, secretService(), factoryProvider());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.updateSettings(true, "smtp.example.com", 465, "mailer", "secret", "not-an-email", "",
                true, false, 10_000, 10_000, 10_000, UUID.randomUUID()));

        assertEquals("Enter a valid from address.", ex.getMessage());
    }

    @Test
    void sendTestEmailUsesCurrentMailSender() {
        PlatformEmailSettings existing = existingSettings();
        existing.setEnabled(true);
        existing.setHost("smtp.example.com");
        existing.setUsername("mailer");
        existing.setPasswordEncrypted("enc:v1:stored");
        existing.setFromAddress("alerts@example.com");
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        PlatformMailSenderFactory factory = Mockito.mock(PlatformMailSenderFactory.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenReturn("db-secret");
        when(factory.getMailSender()).thenReturn(mailSender);
        PlatformEmailSettingsService service = service(repository, secrets, factoryProvider(factory), auditService);
        UUID actorId = UUID.randomUUID();

        service.sendTestEmail(" Admin@Example.com ", actorId);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(auditService).log(eq("PLATFORM_EMAIL_SETTINGS"), isNull(), eq("ADMIN_TEST_EMAIL"), eq(actorId), any(), any());
    }

    @Test
    void sendTestEmailWrapsSmtpTimeoutAsPlainLanguageFailure() {
        PlatformEmailSettings existing = existingSettings();
        existing.setEnabled(true);
        existing.setHost("smtp.foresight.co.tz");
        existing.setUsername("mailer");
        existing.setPasswordEncrypted("enc:v1:stored");
        existing.setFromAddress("alerts@example.com");
        PlatformEmailSettingsRepository repository = Mockito.mock(PlatformEmailSettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        PlatformMailSenderFactory factory = Mockito.mock(PlatformMailSenderFactory.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        when(repository.findById(PlatformEmailSettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenReturn("db-secret");
        when(factory.getMailSender()).thenReturn(mailSender);
        doThrow(new MailSendException(
            "Mail server connection failed. Couldn't connect to host, port: smtp.foresight.co.tz, 465; timeout 10000",
            new SocketTimeoutException("Connect timed out")
        )).when(mailSender).send(any(SimpleMailMessage.class));
        PlatformEmailSettingsService service = service(repository, secrets, factoryProvider(factory), auditService);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.sendTestEmail("admin@example.com", UUID.randomUUID()));

        assertEquals(
            "Test email could not be sent. Connection to smtp.foresight.co.tz on port 465 timed out.",
            ex.getMessage()
        );
        verify(auditService, never()).log(eq("PLATFORM_EMAIL_SETTINGS"), isNull(), eq("ADMIN_TEST_EMAIL"), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PlatformMailSenderFactory> factoryProvider() {
        return factoryProvider(Mockito.mock(PlatformMailSenderFactory.class));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PlatformMailSenderFactory> factoryProvider(PlatformMailSenderFactory factory) {
        ObjectProvider<PlatformMailSenderFactory> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(factory);
        Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<PlatformMailSenderFactory> consumer = invocation.getArgument(0);
            consumer.accept(factory);
            return null;
        }).when(provider).ifAvailable(any());
        return provider;
    }

    private PlatformSecretProtectionService secretService() {
        return Mockito.mock(PlatformSecretProtectionService.class);
    }

    private PlatformEmailSettingsService service(PlatformEmailSettingsRepository repository,
                                                 PlatformSecretProtectionService secrets,
                                                 ObjectProvider<PlatformMailSenderFactory> factoryProvider) {
        return service(repository, secrets, factoryProvider, Mockito.mock(AuditService.class));
    }

    private PlatformEmailSettingsService service(PlatformEmailSettingsRepository repository,
                                                 PlatformSecretProtectionService secrets,
                                                 ObjectProvider<PlatformMailSenderFactory> factoryProvider,
                                                 AuditService auditService) {
        return new PlatformEmailSettingsService(repository, secrets, factoryProvider, auditService);
    }

    private PlatformEmailSettings existingSettings() {
        OffsetDateTime now = OffsetDateTime.now();
        return PlatformEmailSettings.builder()
            .id(PlatformEmailSettings.DEFAULT_ID)
            .enabled(false)
            .host("")
            .port(465)
            .username("")
            .passwordEncrypted("")
            .fromAddress("")
            .overrideRecipient("")
            .sslEnabled(true)
            .starttlsEnabled(false)
            .connectionTimeoutMs(10_000)
            .readTimeoutMs(10_000)
            .writeTimeoutMs(10_000)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
