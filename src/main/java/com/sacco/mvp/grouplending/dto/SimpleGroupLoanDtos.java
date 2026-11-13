package com.sacco.mvp.grouplending.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Staff-recorded joint borrowing. A person is an obligor, never a second receivable. */
public final class SimpleGroupLoanDtos {
    private SimpleGroupLoanDtos() {}
    @Data public static class SetupForm {
        private int expectedVersion;
        private UUID leaderId;
        private String destinationType="BANK_ACCOUNT",provider="",holder="",identifier="",evidence="";
        private boolean checked;
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    @Data public static class ApplicationForm {
        private UUID groupId;
        private int expectedVersion;
        private String reference="",language="SW";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate signedOn;
        private boolean checked;
        private List<ExternalDebt> debts=new ArrayList<>();
    }
    @Data public static class ExternalDebt {
        private String reference="";
        private BigDecimal monthlyPayment;
        private List<UUID> clientIds=new ArrayList<>();
    }
    public record Exposure(int activeLoans,int riskLoans,int guaranteeCount,BigDecimal monthlyDebt,BigDecimal reserved,int missingHistory,int pendingApplications) {}
    public record Person(UUID id,String number,String name) {}
    public record Setup(UUID groupId,int version,String name,UUID leaderId,String leader,
        String destinationType,String provider,String holder,String identifier,String evidence,List<Person> people) {}
    public record Assessment(UUID clientId,UUID profileRevision,String number,String name,BigDecimal income,BigDecimal expenses,
        BigDecimal debt,String source,String evidence) {}
    public record Prepared(UUID party,Setup setup,List<Assessment> assessments,BigDecimal capacity,String destination) {}
    public record Contract(UUID loanId,UUID groupId,String name,String leader,String destination,String reference,
        String language,List<Borrower> borrowers) {}
    public record Borrower(UUID id,String number,String name,LocalDate signedOn,BigDecimal income,BigDecimal expenses,BigDecimal debt,String evidence) {}
    public record Draft(UUID id,int version,String data) {}
    public record DraftSummary(UUID id,String actor,java.time.OffsetDateTime updated,BigDecimal amount) {}
    public record DraftFile(String filename,String contentType,byte[] bytes,String title,String purpose,String requirement,boolean agreement) {}
    public record DraftFiles(List<org.springframework.web.multipart.MultipartFile> files,org.springframework.web.multipart.MultipartFile agreement) {}
    public record SignedPaper(String filename,byte[] body,String checksum) {}
    public record HistoricalDocument(UUID id,String kind,String filename,java.time.OffsetDateTime recorded,String reference) {}
}
