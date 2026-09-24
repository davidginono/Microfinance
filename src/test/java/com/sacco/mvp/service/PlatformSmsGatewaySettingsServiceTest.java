package com.sacco.mvp.service;

import com.sacco.mvp.domain.PlatformSmsGatewaySettings;
import com.sacco.mvp.repository.PlatformSmsGatewaySettingsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformSmsGatewaySettingsServiceTest {

    @Test
    void fallsBackToEnvironmentWhenDatabaseBaseUrlIsBlank() {
        PlatformSmsGatewaySettingsRepository repository = Mockito.mock(PlatformSmsGatewaySettingsRepository.class);
        when(repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        PlatformSmsGatewaySettingsService service = service(repository, secretService(), gatewayProvider());
        ReflectionTestUtils.setField(service, "envEnabled", true);
        ReflectionTestUtils.setField(service, "envBaseUrl", "https://api.bentergroup.com");
        ReflectionTestUtils.setField(service, "envSendPath", "/version1/messaging/bulk");
        ReflectionTestUtils.setField(service, "envClientId", "foresight");
        ReflectionTestUtils.setField(service, "envApiKey", "env-key");
        ReflectionTestUtils.setField(service, "envSenderId", "FORESIGHT");

        PlatformSmsGatewaySettingsService.ResolvedSmsGatewayConfig config = service.resolvedConfig();

        assertTrue(config.enabled());
        assertEquals("https://api.bentergroup.com", config.baseUrl());
        assertEquals("env-key", config.apiKey());
        assertEquals("FORESIGHT", config.senderId());
    }

    @Test
    void disabledDatabaseSettingsDoNotFallBackToEnvironment() {
        PlatformSmsGatewaySettings existing = existingSettings();
        existing.setEnabled(false);
        existing.setBaseUrl("https://api.example.com");
        existing.setSendPath("/send");
        existing.setClientId("client");
        existing.setApiKeyEncrypted("enc:v1:stored");
        existing.setSenderId("SENDER");
        PlatformSmsGatewaySettingsRepository repository = Mockito.mock(PlatformSmsGatewaySettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        when(repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenReturn("db-key");
        PlatformSmsGatewaySettingsService service = service(repository, secrets, gatewayProvider());
        ReflectionTestUtils.setField(service, "envEnabled", true);
        ReflectionTestUtils.setField(service, "envApiKey", "env-key");

        PlatformSmsGatewaySettingsService.ResolvedSmsGatewayConfig config = service.resolvedConfig();

        assertTrue(config.configured());
        assertFalse(config.enabled());
        assertEquals("db-key", config.apiKey());
    }

    @Test
    void unreadableStoredApiKeyDoesNotBreakResolvedConfig() {
        PlatformSmsGatewaySettings existing = existingSettings();
        existing.setEnabled(true);
        existing.setBaseUrl("https://api.example.com");
        existing.setSendPath("/send");
        existing.setClientId("client");
        existing.setApiKeyEncrypted("enc:v1:stored");
        existing.setSenderId("SENDER");
        PlatformSmsGatewaySettingsRepository repository = Mockito.mock(PlatformSmsGatewaySettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        when(repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenThrow(new IllegalStateException("Unable to decrypt platform secret."));
        PlatformSmsGatewaySettingsService service = service(repository, secrets, gatewayProvider());

        PlatformSmsGatewaySettingsService.ResolvedSmsGatewayConfig config = service.resolvedConfig();

        assertFalse(config.configured());
        assertFalse(config.enabled());
        assertTrue(config.apiKeyConfigured());
        assertEquals("", config.apiKey());
    }

    @Test
    void updatesValidSettingsEncryptsApiKeyAndAudits() {
        PlatformSmsGatewaySettingsRepository repository = Mockito.mock(PlatformSmsGatewaySettingsRepository.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        when(repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        when(repository.save(any(PlatformSmsGatewaySettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(secrets.encrypt("new-key")).thenReturn("enc:v1:new-key");
        PlatformSmsGatewaySettingsService service = service(repository, secrets, gatewayProvider(), auditService);
        UUID actorId = UUID.randomUUID();

        PlatformSmsGatewaySettings saved = service.updateSettings(
            true,
            " https://api.bentergroup.com ",
            " /version1/messaging/bulk ",
            " foresight ",
            "new-key",
            " FORESIGHT ",
            3,
            8,
            actorId
        );

        assertEquals("https://api.bentergroup.com", saved.getBaseUrl());
        assertEquals("/version1/messaging/bulk", saved.getSendPath());
        assertEquals("enc:v1:new-key", saved.getApiKeyEncrypted());
        verify(auditService).log(eq("PLATFORM_SMS_GATEWAY_SETTINGS"), isNull(), eq("ADMIN_UPDATE_SMS_GATEWAY_SETTINGS"), eq(actorId), any(), any());
    }

    @Test
    void keepsStoredApiKeyWhenBlankKeySubmitted() {
        PlatformSmsGatewaySettings existing = existingSettings();
        existing.setApiKeyEncrypted("enc:v1:existing");
        PlatformSmsGatewaySettingsRepository repository = Mockito.mock(PlatformSmsGatewaySettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        when(repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(PlatformSmsGatewaySettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlatformSmsGatewaySettingsService service = service(repository, secrets, gatewayProvider());

        PlatformSmsGatewaySettings saved = service.updateSettings(
            true, "https://api.example.com", "/send", "client", "  ", "SENDER", 3, 8, UUID.randomUUID()
        );

        assertEquals("enc:v1:existing", saved.getApiKeyEncrypted());
        verify(secrets, never()).encrypt(any());
    }

    @Test
    void rejectsEnableWithoutApiKey() {
        PlatformSmsGatewaySettingsRepository repository = Mockito.mock(PlatformSmsGatewaySettingsRepository.class);
        when(repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        PlatformSmsGatewaySettingsService service = service(repository, secretService(), gatewayProvider());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.updateSettings(true, "https://api.example.com", "/send", "client", "", "SENDER", 3, 8, UUID.randomUUID()));

        assertEquals("Enter the SMS API key before enabling the gateway.", ex.getMessage());
    }

    @Test
    void sendTestSmsUsesGatewayAndAudits() {
        PlatformSmsGatewaySettings existing = existingSettings();
        existing.setEnabled(true);
        existing.setBaseUrl("https://api.example.com");
        existing.setSendPath("/send");
        existing.setClientId("client");
        existing.setApiKeyEncrypted("enc:v1:stored");
        existing.setSenderId("SENDER");
        PlatformSmsGatewaySettingsRepository repository = Mockito.mock(PlatformSmsGatewaySettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        SmsGateway smsGateway = Mockito.mock(SmsGateway.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        when(repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenReturn("db-key");
        when(smsGateway.send("0673054445", "SACCO platform SMS test")).thenReturn(SmsSendResult.sent("message-id"));
        PlatformSmsGatewaySettingsService service = service(repository, secrets, gatewayProvider(smsGateway), auditService);
        UUID actorId = UUID.randomUUID();

        service.sendTestSms("0673054445", actorId);

        verify(smsGateway).send("0673054445", "SACCO platform SMS test");
        verify(auditService).log(eq("PLATFORM_SMS_GATEWAY_SETTINGS"), isNull(), eq("ADMIN_TEST_SMS"), eq(actorId), any(), any());
    }

    @Test
    void sendTestSmsRejectsInvalidPhone() {
        PlatformSmsGatewaySettingsService service = service(
            Mockito.mock(PlatformSmsGatewaySettingsRepository.class),
            secretService(),
            gatewayProvider()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.sendTestSms("123", UUID.randomUUID()));

        assertEquals("Enter a valid test phone number.", ex.getMessage());
    }

    @Test
    void sendTestSmsWrapsGatewayTimeoutAsPlainLanguageFailure() {
        PlatformSmsGatewaySettings existing = existingSettings();
        existing.setEnabled(true);
        existing.setBaseUrl("https://api.example.com");
        existing.setSendPath("/send");
        existing.setClientId("client");
        existing.setApiKeyEncrypted("enc:v1:stored");
        existing.setSenderId("SENDER");
        PlatformSmsGatewaySettingsRepository repository = Mockito.mock(PlatformSmsGatewaySettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        SmsGateway smsGateway = Mockito.mock(SmsGateway.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        when(repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenReturn("db-key");
        when(smsGateway.send("0673054445", "SACCO platform SMS test"))
            .thenReturn(SmsSendResult.acceptanceUnknown("The SMS gateway connection timed out."));
        PlatformSmsGatewaySettingsService service = service(repository, secrets, gatewayProvider(smsGateway), auditService);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.sendTestSms("0673054445", UUID.randomUUID()));

        assertEquals("Test SMS could not be sent. The SMS gateway connection timed out.", ex.getMessage());
        verify(auditService, never()).log(eq("PLATFORM_SMS_GATEWAY_SETTINGS"), isNull(), eq("ADMIN_TEST_SMS"), any(), any(), any());
    }

    @Test
    void sendTestSmsWrapsUnexpectedGatewayExceptionAsPlainLanguageFailure() {
        PlatformSmsGatewaySettings existing = existingSettings();
        existing.setEnabled(true);
        existing.setBaseUrl("https://api.example.com");
        existing.setSendPath("/send");
        existing.setClientId("client");
        existing.setApiKeyEncrypted("enc:v1:stored");
        existing.setSenderId("SENDER");
        PlatformSmsGatewaySettingsRepository repository = Mockito.mock(PlatformSmsGatewaySettingsRepository.class);
        PlatformSecretProtectionService secrets = Mockito.mock(PlatformSecretProtectionService.class);
        SmsGateway smsGateway = Mockito.mock(SmsGateway.class);
        AuditService auditService = Mockito.mock(AuditService.class);
        when(repository.findById(PlatformSmsGatewaySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(secrets.decrypt("enc:v1:stored")).thenReturn("db-key");
        when(smsGateway.send("0673054445", "SACCO platform SMS test"))
            .thenThrow(new RuntimeException("I/O error on POST request"));
        PlatformSmsGatewaySettingsService service = service(repository, secrets, gatewayProvider(smsGateway), auditService);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.sendTestSms("0673054445", UUID.randomUUID()));

        assertEquals("Test SMS could not be sent. The SMS gateway could not be reached.", ex.getMessage());
        verify(auditService, never()).log(eq("PLATFORM_SMS_GATEWAY_SETTINGS"), isNull(), eq("ADMIN_TEST_SMS"), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<SmsGateway> gatewayProvider() {
        return gatewayProvider(Mockito.mock(SmsGateway.class));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<SmsGateway> gatewayProvider(SmsGateway smsGateway) {
        ObjectProvider<SmsGateway> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(smsGateway);
        return provider;
    }

    private PlatformSecretProtectionService secretService() {
        return Mockito.mock(PlatformSecretProtectionService.class);
    }

    private PlatformSmsGatewaySettingsService service(PlatformSmsGatewaySettingsRepository repository,
                                                      PlatformSecretProtectionService secrets,
                                                      ObjectProvider<SmsGateway> gatewayProvider) {
        return service(repository, secrets, gatewayProvider, Mockito.mock(AuditService.class));
    }

    private PlatformSmsGatewaySettingsService service(PlatformSmsGatewaySettingsRepository repository,
                                                      PlatformSecretProtectionService secrets,
                                                      ObjectProvider<SmsGateway> gatewayProvider,
                                                      AuditService auditService) {
        return new PlatformSmsGatewaySettingsService(repository, secrets, gatewayProvider, auditService);
    }

    private PlatformSmsGatewaySettings existingSettings() {
        OffsetDateTime now = OffsetDateTime.now();
        return PlatformSmsGatewaySettings.builder()
            .id(PlatformSmsGatewaySettings.DEFAULT_ID)
            .enabled(false)
            .baseUrl("")
            .sendPath("")
            .clientId("")
            .apiKeyEncrypted("")
            .senderId("")
            .connectTimeoutSeconds(3)
            .readTimeoutSeconds(8)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
