package com.sacco.mvp.grouplending.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

/** Institution-approved disclosures supplement the calculator; they never reprice a loan. */
public final class GroupDisclosureDtos {
    private GroupDisclosureDtos() {}
    @Data public static class Rules {
        private boolean annualCostRequired,datedFeesRequired,languageChoiceRequired,approved;
        private String methodologyReference="";
        private BigDecimal maximumAnnualCostPercent;
        private String agreementLanguage="BOTH";
    }
    @Data public static class Declaration {
        private BigDecimal annualCostPercent;
        private String costEvidence="",datedFeesEvidence="";
        private boolean costVerified,datedFeesVerified;
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Snapshot(UUID policyId,long revision,Rules rules,BigDecimal annualCostPercent,
        String costEvidence,String datedFeesEvidence,boolean costVerified,boolean datedFeesVerified) {}
}
