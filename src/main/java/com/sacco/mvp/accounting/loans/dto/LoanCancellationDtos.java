package com.sacco.mvp.accounting.loans.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public final class LoanCancellationDtos {
    private LoanCancellationDtos() {}

    @Data public static class Form {
        private UUID requestKey;
        private String state;
        private String terms;
        private String reason;
        private String evidence;
        @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate effectiveDate;
        private boolean confirmed;
    }

    public record View(UUID id,String number,String client,String status,String terms,BigDecimal principal,
                       boolean funded,String blockedReason,UUID adjustmentId,String adjustmentState,
                       String reason,String cancelledBy,OffsetDateTime cancelledAt) {
        public boolean actionable() {
            return blockedReason==null && !"PENDING".equals(adjustmentState) && !"APPROVED".equals(adjustmentState);
        }
        public boolean isActionable() {return actionable();}
    }
    public record Result(UUID loanId,UUID adjustmentId) {}
}
