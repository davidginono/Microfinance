package com.sacco.mvp.accounting.payroll.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class PayrollDtos {
    private PayrollDtos() {}
    @Data public static class EmployerForm {
        @NotNull(message="{payroll.error.input}")
        private UUID requestKey = UUID.randomUUID();
        @NotBlank(message="{payroll.error.input}") @Size(max=160,message="{payroll.error.input}")
        private String name = "";
        @NotBlank(message="{payroll.error.input}") @Size(max=250,message="{payroll.error.input}")
        private String agreementReference = "";
    }
    @Data public static class InstructionForm {
        private UUID requestKey = UUID.randomUUID();
        @NotNull(message="{payroll.error.input}") private UUID employerId;
        @NotNull(message="{payroll.error.loan}") private UUID loanId;
        @NotBlank(message="{payroll.error.input}") @Size(max=500,message="{payroll.error.input}")
        private String evidence = "";
        @NotNull(message="{payroll.error.limit}") @DecimalMin(value="0.01",message="{payroll.error.limit}") @Digits(integer=16,fraction=2,message="{payroll.error.limit}")
        private BigDecimal monthlyLimit;
        @NotNull(message="{payroll.error.dates}") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate effectiveFrom;
        @NotNull(message="{payroll.error.dates}") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate effectiveTo;
    }
    @Data public static class BatchForm {
        private UUID requestKey = UUID.randomUUID();
        @NotNull(message="{payroll.error.input}") private UUID employerId;
        @Pattern(regexp="[0-9]{4}-[0-9]{2}",message="{payroll.error.dates}")
        private String month = "";
        @NotNull(message="{payroll.error.dates}") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate deductionDate;
        private boolean includeArrears;
        private boolean acceptExceptions;
        private String previewHash = "";
        private UUID replacesId;
    }
    public record Choice(UUID id, String label) {}
    public record Page<T>(List<T> rows, int page, boolean hasNext) {}
    public record Employer(UUID id, String name, String agreementReference) {}
    public record Instruction(UUID id, UUID employerId, String employer, UUID loanId, String loanNumber,
        String client, String employeeNumber, BigDecimal monthlyLimit, LocalDate effectiveFrom, LocalDate effectiveTo,
        String evidence, boolean stopped) {}
    public record LoanChoice(UUID id, UUID clientId, String label, String employeeNumber) {}
    public record Line(UUID instructionId, UUID loanId, UUID clientId, String employeeNumber, String clientName,
        String clientNumber, String loanNumber, BigDecimal principal, BigDecimal interest, BigDecimal amount, String issue) {}
    public record Preview(Employer employer, List<Line> rows, BigDecimal total, long employees, long exceptions, String hash) {}
    public record Batch(UUID id, String employer, LocalDate month, LocalDate deductionDate, boolean includeArrears,
        OffsetDateTime cutoff, String actor, String institution, String branch, String snapshotHash,
        UUID replacesId, String state, BigDecimal total, long employees, long exceptions) {
        public String reference() { return "PAY-" + id.toString().toUpperCase(Locale.ROOT); }
        public String getReference() { return reference(); }
    }
    public record Event(String kind, String actor, String evidence, OffsetDateTime date) {}
    public record Document(byte[] content, String checksum) {}
    public record ExportRow(String employeeNumber, String clientName, String reference, BigDecimal amount) {}
}
