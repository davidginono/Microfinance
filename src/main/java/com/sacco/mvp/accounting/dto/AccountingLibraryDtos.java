package com.sacco.mvp.accounting.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccountingLibraryDtos {
    private AccountingLibraryDtos() { }
    public record Activity(UUID id,String code,String name,String nameSw,String description,boolean active) { }
    public record TransactionCode(UUID id,UUID activityId,String activityCode,String activityName,String activityNameSw,
            String code,String name,String nameSw,String description,String sourceEvent,boolean active,int revision,UUID templateId) { }
    public record Template(UUID id,UUID transactionId,int version,String sourceEvent,String reason,OffsetDateTime createdAt,List<Rule> rules) { }
    public record Rule(String component,String debitCode,String debitName,String creditCode,String creditName) { }
    public record Version(UUID id,int version,String reason,OffsetDateTime createdAt) { }
    public record AccountChoice(String code,String name,String nameSw,String kind) { }
    @Data public static class CodeForm {
        private UUID activityId;
        private String code,name,nameSw,description;
        private String sourceEvent="MANUAL_JOURNAL";
    }
    @Data public static class TemplateForm {
        private UUID requestKey=UUID.randomUUID();
        private int expectedRevision;
        private String reason;
        private List<RuleForm> rules=new ArrayList<>();
    }
    @Data @lombok.EqualsAndHashCode(callSuper=true) public static class TransactionForm extends CodeForm {
        private String activityCode;
        private TemplateForm template=new TemplateForm();
    }
    @Data public static class RuleForm {
        private String component="TOTAL",debitCode,creditCode;
    }
}
