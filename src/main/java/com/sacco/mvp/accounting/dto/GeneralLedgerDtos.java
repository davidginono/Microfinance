package com.sacco.mvp.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Money enters as BigDecimal; no binary floating-point conversion is permitted. */
public final class GeneralLedgerDtos {
    private GeneralLedgerDtos() { }
    public record AccountCommand(String code, String name, String type, String normalBalance,
                                 String kind, String purpose, UUID parentId) { }
    public record Account(UUID id, String code, String name, String type, String normalBalance,
                          String kind, String purpose, UUID parentId, boolean active) { }
    public record DisplayLine(String code,String name,BigDecimal debit,BigDecimal credit) { }
    public record Line(UUID accountId, BigDecimal debit, BigDecimal credit) { }
    public record JournalCommand(UUID requestKey, String sourceReference, LocalDate effectiveDate,
                                 String evidenceReference, String reason, List<Line> lines) { }
    public record Journal(UUID id, String institutionId, String branchId, UUID policyId, int policyVersion,
                          UUID periodId, String sourceType, String sourceReference, UUID requestKey,
                          String payloadHash, String state, String evidenceReference, String reason,
                          LocalDate effectiveDate, UUID makerId, UUID checkerId, OffsetDateTime recordedAt,
                          OffsetDateTime postedAt, UUID reversesId, List<Line> lines, boolean reversed) { }
    public record Period(UUID id, String institutionId, LocalDate startsOn, LocalDate endsOn,
                         String state, UUID policyId) { }
    public record Coverage(boolean reviewedOpening, Long unbridgedOperationalVouchers,
                           Long uncoveredLegacyLoans, String status) { }
    public record Page<T>(List<T> rows, int page, boolean hasNext) { }
}
