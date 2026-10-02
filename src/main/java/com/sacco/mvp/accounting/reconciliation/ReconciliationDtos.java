package com.sacco.mvp.accounting.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReconciliationDtos {
    private ReconciliationDtos() { }
    public record Format(UUID id,String name,String version,UUID maker,UUID checker,String evidence) { }
    public record ImportedRow(int number,LocalDate date,String reference,BigDecimal amount,String kind,boolean duplicate) { }
    public record StatementCommand(UUID key,UUID account,UUID format,LocalDate from,LocalDate through,
        BigDecimal opening,BigDecimal closing,String filename,String evidence,String content) { }
    public record Statement(UUID id,UUID account,String accountCode,LocalDate from,LocalDate through,
        BigDecimal opening,BigDecimal closing,String filename,String checksum,UUID maker,String evidence,OffsetDateTime importedAt) { }
    public record StatementRow(UUID id,int number,LocalDate date,String reference,BigDecimal amount,
        String kind,boolean duplicate,BigDecimal matched,String status) { }
    public record Candidate(UUID id,UUID journal,LocalDate date,String reference,String source,BigDecimal amount,BigDecimal matched,boolean reversed) { }
    public record Allocation(UUID statementLine,UUID journalLine,BigDecimal amount) { }
    public record Match(UUID id,String kind,String state,UUID maker,UUID checker,String evidence,UUID reverses) { }
    public record ExceptionRecord(UUID id,UUID statementLine,String kind,UUID assignedTo,String state,UUID maker,UUID checker,String evidence) { }
    public record Certificate(UUID id,UUID account,String code,LocalDate asOf,String kind,BigDecimal sourceBalance,
        BigDecimal ledgerBalance,BigDecimal difference,String state,UUID maker,UUID checker,String evidence) { }
    public record AccountBalance(UUID id,String code,String name,String purpose,BigDecimal balance) { }
    public record Period(UUID id,LocalDate from,LocalDate through,String state) { }
    public record CloseCheck(String key,long blockers) { }
    public record CloseReview(UUID id,UUID period,String branch,int version,String state,String snapshot,String checksum,
        UUID maker,UUID checker,String evidence,OffsetDateTime recordedAt) { }
    public record FinalizedSnapshot(UUID id,UUID period,int version,LocalDate asOf,OffsetDateTime recordedCutoff,
        String checksum,String snapshot,UUID reviewer,boolean periodClosed,boolean restatement,String coverage) { }
    public record BranchClose(UUID id,String branch,int version,String checksum) { }
    public record OpeningEvidence(UUID id,UUID journal,UUID policy,int policyVersion,String payloadChecksum,
        UUID maker,UUID reviewer,String sourceEvidence,String reviewEvidence,LocalDate through,
        OffsetDateTime reviewedAt,OffsetDateTime postedAt) { }
    public record Page<T>(List<T> rows,int page,boolean hasNext) { }
}
