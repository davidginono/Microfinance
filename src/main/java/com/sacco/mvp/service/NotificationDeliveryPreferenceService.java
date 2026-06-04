package com.sacco.mvp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryPreferenceService {
    private static final TypeReference<Map<String, Map<String, Boolean>>> PREFS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public List<NotificationDeliveryPreferenceView> views(String saccoId) {
        Map<NotificationDeliveryTopic, ChannelPreference> preferences = preferences(saccoId);
        return Arrays.stream(NotificationDeliveryTopic.values())
            .map(topic -> {
                ChannelPreference preference = preferences.get(topic);
                return new NotificationDeliveryPreferenceView(
                    topic.name(),
                    topic.getLabel(),
                    preference.emailEnabled(),
                    preference.smsEnabled()
                );
            })
            .toList();
    }

    public boolean emailEnabled(String saccoId, String eventType) {
        return preferenceForEvent(saccoId, eventType).emailEnabled();
    }

    public boolean smsEnabled(String saccoId, String eventType) {
        return preferenceForEvent(saccoId, eventType).smsEnabled();
    }

    @Transactional
    public void update(String saccoId,
                       boolean loanStatusEmail,
                       boolean loanStatusSms,
                       boolean guaranteeRequestEmail,
                       boolean guaranteeRequestSms,
                       boolean repaymentReminderEmail,
                       boolean repaymentReminderSms) {
        Map<NotificationDeliveryTopic, ChannelPreference> preferences = new LinkedHashMap<>();
        preferences.put(NotificationDeliveryTopic.LOAN_STATUS, new ChannelPreference(loanStatusEmail, loanStatusSms));
        preferences.put(NotificationDeliveryTopic.GUARANTEE_REQUEST, new ChannelPreference(guaranteeRequestEmail, guaranteeRequestSms));
        preferences.put(NotificationDeliveryTopic.REPAYMENT_REMINDER, new ChannelPreference(repaymentReminderEmail, repaymentReminderSms));
        int updated = jdbcTemplate.update("""
            update sacco_settings
            set notification_delivery_prefs = cast(? as jsonb),
                updated_at = ?
            where sacco_id = ?
            """, toJson(preferences), OffsetDateTime.now(), saccoId);
        if (updated == 0) {
            throw new IllegalArgumentException("SACCO settings not found");
        }
    }

    private ChannelPreference preferenceForEvent(String saccoId, String eventType) {
        NotificationDeliveryTopic topic = topicForEvent(eventType);
        if (topic == null) {
            return new ChannelPreference(true, false);
        }
        return preferences(saccoId).getOrDefault(topic, defaultPreference(topic));
    }

    private Map<NotificationDeliveryTopic, ChannelPreference> preferences(String saccoId) {
        try {
            String json = jdbcTemplate.queryForObject("""
                select notification_delivery_prefs::text
                from sacco_settings
                where sacco_id = ?
                """, String.class, saccoId);
            return parse(json);
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("SACCO settings not found");
        }
    }

    private Map<NotificationDeliveryTopic, ChannelPreference> parse(String json) {
        Map<NotificationDeliveryTopic, ChannelPreference> preferences = defaults();
        if (json == null || json.isBlank()) {
            return preferences;
        }
        try {
            Map<String, Map<String, Boolean>> raw = objectMapper.readValue(json, PREFS_TYPE);
            raw.forEach((key, value) -> {
                try {
                    NotificationDeliveryTopic topic = NotificationDeliveryTopic.valueOf(key);
                    preferences.put(topic, new ChannelPreference(
                        Boolean.TRUE.equals(value.get("email")),
                        Boolean.TRUE.equals(value.get("sms"))
                    ));
                } catch (IllegalArgumentException ignored) {
                    // Ignore unknown preference topics from older or future clients.
                }
            });
        } catch (Exception ignored) {
            return preferences;
        }
        return preferences;
    }

    private String toJson(Map<NotificationDeliveryTopic, ChannelPreference> preferences) {
        Map<String, Map<String, Boolean>> raw = new LinkedHashMap<>();
        preferences.forEach((topic, preference) -> raw.put(topic.name(), Map.of(
            "email", preference.emailEnabled(),
            "sms", preference.smsEnabled()
        )));
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to save notification delivery settings", ex);
        }
    }

    private Map<NotificationDeliveryTopic, ChannelPreference> defaults() {
        Map<NotificationDeliveryTopic, ChannelPreference> preferences = new LinkedHashMap<>();
        for (NotificationDeliveryTopic topic : NotificationDeliveryTopic.values()) {
            preferences.put(topic, defaultPreference(topic));
        }
        return preferences;
    }

    private ChannelPreference defaultPreference(NotificationDeliveryTopic topic) {
        return new ChannelPreference(true, false);
    }

    private NotificationDeliveryTopic topicForEvent(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        if ("GUARANTOR_REQUEST_ASSIGNED".equals(eventType)
            || eventType.startsWith("GUARANTOR_")) {
            return NotificationDeliveryTopic.GUARANTEE_REQUEST;
        }
        if ("REPAYMENT_REMINDER".equals(eventType)) {
            return NotificationDeliveryTopic.REPAYMENT_REMINDER;
        }
        return NotificationDeliveryTopic.LOAN_STATUS;
    }

    public record ChannelPreference(boolean emailEnabled, boolean smsEnabled) {
    }

    public static class NotificationDeliveryPreferenceView {
        private final String key;
        private final String label;
        private final boolean emailEnabled;
        private final boolean smsEnabled;

        public NotificationDeliveryPreferenceView(String key, String label, boolean emailEnabled, boolean smsEnabled) {
            this.key = key;
            this.label = label;
            this.emailEnabled = emailEnabled;
            this.smsEnabled = smsEnabled;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public boolean isEmailEnabled() {
            return emailEnabled;
        }

        public boolean isSmsEnabled() {
            return smsEnabled;
        }
    }
}
