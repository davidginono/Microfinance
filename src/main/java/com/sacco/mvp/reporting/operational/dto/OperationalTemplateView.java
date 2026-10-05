package com.sacco.mvp.reporting.operational.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OperationalTemplateView(UUID id, String name, int reportVersion, String state, Long lockVersion,
        String visibility, UUID creatorId, OffsetDateTime createdAt, UUID publisherId, OffsetDateTime publishedAt,
        ReportDefinition definition) { }
