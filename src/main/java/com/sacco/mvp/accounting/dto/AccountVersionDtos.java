package com.sacco.mvp.accounting.dto;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Account;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AccountVersionDtos {
    private AccountVersionDtos() { }
    public record Policy(int version, boolean createAutomatic, boolean updateAutomatic,
                         boolean deactivateAutomatic, boolean reactivateAutomatic) {
        public boolean automatic(String operation) {
            return switch(operation) {
                case "CREATE" -> createAutomatic; case "UPDATE" -> updateAutomatic;
                case "DEACTIVATE" -> deactivateAutomatic; case "REACTIVATE" -> reactivateAutomatic;
                default -> throw new IllegalArgumentException("accounting.error.versionInput");
            };
        }
    }
    public record Version(UUID id, UUID accountId, int number, UUID baseId, String operation,
                          String state, Account before, Account proposed, UUID creator, String creatorName,
                          String branch, String reason, int revision, int policyVersion, String mode,
                          UUID reviewer, String reviewerName, String decisionReason,
                          OffsetDateTime createdAt, OffsetDateTime submittedAt, OffsetDateTime decidedAt,
                          UUID requestKey, String requestHash) {
        public String getCreatedLabel(){return displayTime(createdAt);}
        public String getDecidedLabel(){return displayTime(decidedAt);}
    }
    public record Event(UUID id, String action, String actorName, String reason, String state,
                        int policyVersion, String mode, OffsetDateTime recordedAt) {
        public String getRecordedLabel(){return displayTime(recordedAt);}
    }
    private static String displayTime(OffsetDateTime value) {
        return value==null?"":value.atZoneSameInstant(java.time.ZoneId.of("Africa/Nairobi")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    public record Details(Version version, UUID currentId, String parentLabel, Set<String> actions, List<Version> history,
                          int historyPage, boolean historyHasNext, List<Event> events,
                          int eventPage, boolean eventHasNext) { }
    @Data public static class EditForm {
        private String name = "", nameSw = "", description = "", reason = "";
        private int revision;
    }
    @Data public static class PolicyForm {
        private int version;
        private boolean createAutomatic, updateAutomatic, deactivateAutomatic, reactivateAutomatic;
        private String reason = "";
    }
}
