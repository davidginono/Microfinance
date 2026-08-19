package com.sacco.mvp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
@RequiredArgsConstructor
public class PlatformMailSenderFactory {
    private final PlatformEmailSettingsService emailSettingsService;

    private volatile JavaMailSender cachedSender;
    private volatile String cacheKey;

    public JavaMailSender getMailSender() {
        PlatformEmailSettingsService.ResolvedEmailConfig config = emailSettingsService.resolvedConfig();
        if (!config.enabled()) {
            return null;
        }
        String key = config.cacheKey();
        JavaMailSender sender = cachedSender;
        if (sender != null && key.equals(cacheKey)) {
            return sender;
        }
        synchronized (this) {
            sender = cachedSender;
            if (sender != null && key.equals(cacheKey)) {
                return sender;
            }
            sender = buildMailSender(config);
            cachedSender = sender;
            cacheKey = key;
            return sender;
        }
    }

    public void invalidate() {
        cachedSender = null;
        cacheKey = null;
    }

    private JavaMailSender buildMailSender(PlatformEmailSettingsService.ResolvedEmailConfig config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.host());
        mailSender.setPort(config.port());
        mailSender.setUsername(config.username());
        mailSender.setPassword(config.password());
        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.enable", Boolean.toString(config.sslEnabled()));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(config.starttlsEnabled()));
        properties.put("mail.smtp.connectiontimeout", Integer.toString(config.connectionTimeoutMs()));
        properties.put("mail.smtp.timeout", Integer.toString(config.readTimeoutMs()));
        properties.put("mail.smtp.writetimeout", Integer.toString(config.writeTimeoutMs()));
        return mailSender;
    }
}
