package com.sacco.mvp.accounting.policy.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PolicyView(UUID id, int version, String state, String originatingBranch,
                         UUID createdBy, OffsetDateTime createdAt, UUID checkedBy,
                         OffsetDateTime checkedAt, String reviewEvidence, String reviewReason,
                         String contentHash, PolicyContent content) {}
