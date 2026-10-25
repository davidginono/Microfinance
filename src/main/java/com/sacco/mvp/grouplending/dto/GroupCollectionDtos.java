package com.sacco.mvp.grouplending.dto;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.AllocationPreview;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class GroupCollectionDtos {
    private GroupCollectionDtos() {}
    @Data public static class Form {
        private UUID requestKey=UUID.randomUUID();
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate effectiveDate;
        private UUID moneyAccountId;
        private String remitter="",reference="",evidence="",previewHash="";
        private List<Row> rows=new ArrayList<>(List.of(new Row()));
    }
    @Data public static class Row {
        private UUID loanId,payerId;
        private BigDecimal amount;
        private String description="";
    }
    public record Cycle(UUID id,String groupCode,String groupName,String model,String state) {}
    public record Beneficiary(UUID loanId,UUID clientId,String clientNumber,String name,String loanNumber) {}
    public record Payer(UUID id,String number,String name) {}
    public record PreviewLoan(Beneficiary beneficiary,BigDecimal amount,AllocationPreview allocation) {}
    public record Preview(String hash,String number,List<PreviewLoan> loans,List<com.sacco.mvp.accounting.dto.VoucherDtos.ReceiptContribution> contributions,BigDecimal total) {}
}
