package com.sacco.mvp.grouplending.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GroupLendingDtos {
    private GroupLendingDtos() {}
    public record Page<T>(List<T> rows, int page, boolean hasNext) {}
    public record Group(UUID id, String code, String name, String legalForm, String evidence,
                        String status, int version, long members) {}
    public record Membership(UUID id, UUID clientId, String clientNumber, String name, String office,
                             LocalDate startsOn, LocalDate endsOn, String evidence, String endReason) {}
    public record Choice(UUID id, String label) {}
    @Data public static class GroupForm {
        private UUID requestKey = UUID.randomUUID();
        private String name = "";
        private String legalForm = "INFORMAL";
        private String evidence = "";
    }
    @Data public static class MemberForm {
        private UUID requestKey = UUID.randomUUID();
        private UUID clientId;
        private String office = "MEMBER";
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate startsOn;
        private String evidence = "";
    }
    @Data public static class EndForm {
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate date;
        private String reason = "";
    }
}
