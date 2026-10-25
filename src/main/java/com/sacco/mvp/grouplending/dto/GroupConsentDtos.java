package com.sacco.mvp.grouplending.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class GroupConsentDtos {
    private GroupConsentDtos() {}
    public record Challenge(UUID id,String hash,OffsetDateTime issued,OffsetDateTime expires,OffsetDateTime delivered,OffsetDateTime consumed,int attempts) {}
    /** Service-to-service delivery only; never return this record to an HTTP caller or log it. */
    public record Delivery(UUID id,UUID cycle,UUID client,UUID loan,String email,String phone,String code,String institution,String branch,int ttlMinutes) {}
}
