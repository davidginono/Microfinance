package com.sacco.mvp.accounting.payroll.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class PayrollFeedbackDtos {
    private PayrollFeedbackDtos() {}
    public record Input(int line,String batchReference,String employee,String reference,String status,BigDecimal amount,LocalDate date,String employerReference) {}
    public record Matched(Input input,UUID clientId,String clientName,BigDecimal requested) {}
    public record Feedback(UUID id,UUID batchId,String filename,String checksum,String evidence,String actor,OffsetDateTime createdAt,int rows,BigDecimal total,UUID receiptBatchId) {}
    public record Remittance(String account,LocalDate date,BigDecimal amount,String reference,String evidence) {}
    public record Result(int line,UUID clientId,String clientName,String employee,String reference,String status,BigDecimal amount,BigDecimal requested,LocalDate date,String employerReference,String postingState,UUID receiptId) {}
    @Data public static class RemittanceForm {
        private UUID moneyAccountId;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate receivedDate;
        private BigDecimal receivedAmount;
        private String reference="",evidence="";
        private boolean verified;
    }
}
