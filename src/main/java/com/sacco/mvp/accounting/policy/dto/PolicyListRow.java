package com.sacco.mvp.accounting.policy.dto;

import com.sacco.mvp.accounting.policy.model.GlAuthority;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyListRow(UUID id, int version, LocalDate effectiveFrom, GlAuthority authority, String state) {}
