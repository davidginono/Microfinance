package com.sacco.mvp.accounting.approval.dto;

import com.sacco.mvp.accounting.dto.VoucherDtos.Form;
import com.sacco.mvp.accounting.dto.VoucherDtos.Type;
import com.sacco.mvp.domain.Position;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public final class VoucherWorkflowDtos {
    private VoucherWorkflowDtos() {}
    public enum Approval {
        NOT_REQUIRED, REQUIRED, INITIATED, WITHDRAWN, HELD_FROM_APPROVAL, REJECTED,
        RESUBMIT_FOR_APPROVAL, WORKFLOW_APPROVED, MANUALLY_APPROVED;
        public boolean approved() { return this == NOT_REQUIRED || this == WORKFLOW_APPROVED || this == MANUALLY_APPROVED; }
    }
    public record Stage(String claim, UUID memberId, Position role, Integer approvals) {
        public Stage {if(approvals==null)approvals=1;}
        public Stage(String claim, UUID memberId) {this(claim,memberId,null,1);}
        public int required() {return Math.max(1,approvals);}
    }
    public record Rule(String name, BigDecimal minimum, BigDecimal maximum, boolean automatic, List<Stage> stages,
                       String scope, Boolean autoPost) {
        public Rule {if(autoPost==null)autoPost=false;}
        public Rule(String name,BigDecimal minimum,BigDecimal maximum,boolean automatic,List<Stage> stages) {this(name,minimum,maximum,automatic,stages,null,false);}
    }
    public record Configuration(Type type, int version, boolean individuals, boolean batches, boolean forceAllowed, List<Rule> rules, Integer modelVersion) {
        public Configuration {if(modelVersion==null)modelVersion=1;}
        public Configuration(Type type,int version,boolean individuals,boolean batches,boolean forceAllowed,List<Rule> rules) {this(type,version,individuals,batches,forceAllowed,rules,1);}
        public boolean enabled(String scope) { return "SINGLE".equals(scope) ? individuals : batches; }
    }
    public record Workflow(UUID id, String number, Type type, String scope, String title, UUID owner,
                           int version, Approval approval, String validation, String posting, BigDecimal total,
                           BigDecimal assessmentAmount, int itemCount, int stage, int round, int configVersion,
                           List<Stage> stages, boolean forceAllowed, String failure, OffsetDateTime createdAt,
                           OffsetDateTime updatedAt, boolean autoPost, int modelVersion, String ruleName) {}
    public record Issue(String field, String code) {}
    public record Item(UUID id, UUID workflowId, int line, int version, Form form, List<Issue> issues,
                       String planHash, UUID voucherId) {}
    public record History(UUID id, String action, String actorName, String comments, OffsetDateTime time) {}
    public record Document(UUID id, String filename, String mediaType, byte[] content) {}
    public record Details(Workflow workflow, List<Item> items, List<History> history, Set<String> actions, Map<Integer,Long> responses) {}
    public record Page(List<Workflow> rows, int page, int size, boolean hasNext, Map<String, Long> counts) {}
    public record Proof(String planHash) {}
    public record AutomaticRequest(UUID id,UUID owner,String institution,String branch,int version) {}
    @Data public static class RuleForm {
        private Type type = Type.JOURNAL;
        private int version;
        private List<RuleRow> rules = new ArrayList<>(List.of(new RuleRow()));
    }
    @Data public static class RuleRow {
        private BigDecimal minimum = BigDecimal.ZERO, maximum;
        private String scope = "SINGLE";
        private boolean autoPost;
        private List<StepRow> stages = new ArrayList<>(List.of(new StepRow()));
    }
    @Data public static class StepRow {
        private Position role = Position.MANAGER;
        private int approvals = 1;
    }
    public static class Invalid extends IllegalArgumentException {
        public Invalid(String key) { super("approval.error." + key); }
    }
}
