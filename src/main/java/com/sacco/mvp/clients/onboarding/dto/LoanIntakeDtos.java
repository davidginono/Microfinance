package com.sacco.mvp.clients.onboarding.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class LoanIntakeDtos {
    private LoanIntakeDtos() {}
    @Data public static class Details {
        private UUID productId;
        private BigDecimal principal, income, expenses, otherDebt;
        private Integer tenureValue;
        private String tenureUnit="";
        private String purpose="", business="";
    }
    @Data public static class Form {
        private UUID requestKey=UUID.randomUUID();
        private int expectedSequence;
        private String action="DRAFT";
        private Details details=new Details();
    }
    public record Request(UUID id,int sequence,String mode,Details details,OffsetDateTime recordedAt) {}
    public record Product(UUID id,String label,String unit,BigDecimal minimum,BigDecimal maximum,int minTenure,int maxTenure) {}
}
