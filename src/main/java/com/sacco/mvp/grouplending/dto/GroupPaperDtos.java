package com.sacco.mvp.grouplending.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class GroupPaperDtos {
    private GroupPaperDtos() {}
    @lombok.Data public static class PaperForm {
        private UUID requestKey=UUID.randomUUID();
        private String target="",expectedHash="",reference="",language="SW";
        private String destinationType="BANK_ACCOUNT",provider="",accountHolderName="",accountIdentifier="";
        @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
        private java.time.LocalDateTime signedAt;
        private boolean checked;
    }
    public record PaperTarget(String key,String kind,UUID client,UUID offer,String name,String number,java.math.BigDecimal amount) {}
    public record PaperProposal(String contract,String row,String hash) {}
    public record Paper(UUID id,String kind,String person,String reference,OffsetDateTime signedAt,String actor,OffsetDateTime recordedAt) {}
}
