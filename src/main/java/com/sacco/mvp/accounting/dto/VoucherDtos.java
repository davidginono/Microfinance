package com.sacco.mvp.accounting.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

public final class VoucherDtos {
    private VoucherDtos() {}
    public enum Type { RECEIPT, PAYMENT, JOURNAL }
    @Data public static class Form {
        private UUID requestKey=UUID.randomUUID();
        private LocalDate effectiveDate;
        private String party="",reference="",description="",evidence="";
        private UUID moneyAccountId;
        private List<RowForm> rows=new ArrayList<>(List.of(new RowForm()));
    }
    @Data public static class RowForm {
        private UUID transactionId,templateKey,debitAccountId,creditAccountId;
        private String component="TOTAL",description="";
        private BigDecimal amount;
    }
    public record Account(UUID id,String code,String name,String purpose) {}
    public record Mapping(UUID transactionId,UUID templateKey,String transactionName,String component,Account debit,Account credit) {}
    public record Transaction(UUID id,int lineNo,UUID transactionId,UUID templateKey,String transactionName,String component,
                              String description,BigDecimal amount,Account debit,Account credit) {}
    public record Voucher(UUID id,String number,Type type,LocalDate effectiveDate,String party,String reference,String description,
                          String evidence,UUID moneyAccountId,BigDecimal total,int transactionCount,UUID journalId,UUID postedBy,
                          String postedByName,OffsetDateTime postedAt,UUID reversesId,UUID reversedBy,String institutionName,
                          String branchName,List<Transaction> transactions) {
        public String status(){return reversesId!=null?"REVERSAL":reversedBy!=null?"REVERSED":"POSTED";}
    }
    public record Filter(String search,LocalDate from,LocalDate through,String status,String sort,int page) {}
    public record Page(List<Voucher> rows,int page,boolean hasNext) {}
    public record Preview(List<Transaction> rows,BigDecimal total) {}
    public static class Invalid extends IllegalArgumentException {
        private final String field;
        public Invalid(String field,String code){super("voucher.error."+code);this.field=field;}
        public String field(){return field;}
    }
}
