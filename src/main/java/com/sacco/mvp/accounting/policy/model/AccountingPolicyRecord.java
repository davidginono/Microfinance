package com.sacco.mvp.accounting.policy.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Internal persisted record; controllers and dependent modules receive DTOs. */
public record AccountingPolicyRecord(UUID id, String saccoId, String stationId, int version,
                                    String state, GlAuthority authority, LocalDate effectiveFrom,
                                    String contentJson, String contentHash, UUID createdBy,
                                    OffsetDateTime createdAt, UUID checkedBy, OffsetDateTime checkedAt,
                                    String reviewEvidence, String reviewReason, UUID requestKey) {}
