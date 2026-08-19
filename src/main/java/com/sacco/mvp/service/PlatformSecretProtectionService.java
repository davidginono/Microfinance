package com.sacco.mvp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PlatformSecretProtectionService {
    private static final String PREFIX = "enc:v1:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;
    private final boolean encryptionAvailable;

    public PlatformSecretProtectionService(@Value("${app.secrets.encryption-key:}") String encryptionKeyBase64) {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            this.secretKey = null;
            this.encryptionAvailable = false;
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64.trim());
        if (keyBytes.length != 32) {
            throw new IllegalStateException("APP_SECRETS_ENCRYPTION_KEY must decode to 32 bytes.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.encryptionAvailable = true;
    }

    public boolean encryptionAvailable() {
        return encryptionAvailable;
    }

    public String encrypt(String plaintext) {
        requireEncryptionAvailable();
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt platform secret.", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        requireEncryptionAvailable();
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt platform secret.", ex);
        }
    }

    private void requireEncryptionAvailable() {
        if (!encryptionAvailable) {
            throw new IllegalStateException("Set APP_SECRETS_ENCRYPTION_KEY before saving credentials in Platform Settings.");
        }
    }
}
