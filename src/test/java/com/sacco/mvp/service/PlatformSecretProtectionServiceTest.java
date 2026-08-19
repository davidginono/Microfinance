package com.sacco.mvp.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSecretProtectionServiceTest {

    @Test
    void encryptsAndDecryptsWhenKeyIsConfigured() {
        PlatformSecretProtectionService service = new PlatformSecretProtectionService(key());

        String stored = service.encrypt("smtp-secret");

        assertTrue(service.encryptionAvailable());
        assertTrue(stored.startsWith("enc:v1:"));
        assertEquals("smtp-secret", service.decrypt(stored));
    }

    @Test
    void decryptsLegacyPlaintextValues() {
        PlatformSecretProtectionService service = new PlatformSecretProtectionService(key());

        assertEquals("legacy-secret", service.decrypt("legacy-secret"));
        assertEquals("", service.decrypt(""));
    }

    @Test
    void encryptRequiresConfiguredKey() {
        PlatformSecretProtectionService service = new PlatformSecretProtectionService("");

        assertFalse(service.encryptionAvailable());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.encrypt("smtp-secret"));
        assertEquals("Set APP_SECRETS_ENCRYPTION_KEY before saving credentials in Platform Settings.", ex.getMessage());
    }

    private String key() {
        return Base64.getEncoder().encodeToString("platform-secret-key-32-bytes!!xy".getBytes());
    }
}
