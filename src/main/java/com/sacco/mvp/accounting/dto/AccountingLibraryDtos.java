package com.sacco.mvp.accounting.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccountingLibraryDtos {
    private AccountingLibraryDtos() { }
    public record Activity(UUID id,String code,String name,String nameSw,String description,boolean active) { }
    public record TransactionCode(UUID id,UUID activityId,String activityCode,String activityName,String activityNameSw,
            String code,String name,String nameSw,String description,String sourceEvent,boolean active,boolean hasTemplate) { }
    public record Template(UUID transactionId,UUID requestKey,List<Rule> rules) { }
    public record Rule(String component,String debitCode,String debitName,String creditCode,String creditName) { }
    public record AccountChoice(String code,String name,String nameSw,String kind) { }
    @Data public static class CodeForm {
        private UUID activityId;
        private String code,name,nameSw,description;
        private String sourceEvent="MANUAL_JOURNAL";
    }
    @Data public static class TemplateForm {
        private UUID requestKey=UUID.randomUUID();
        private UUID expectedRequestKey;
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
