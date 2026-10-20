package com.sacco.mvp.accounting.repayment.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public final class RepaymentPolicyDtos {
    private RepaymentPolicyDtos() {}
    public enum AllocationOrder { INTEREST_FIRST, PRINCIPAL_FIRST }
    public record Policy(UUID id,int version,LocalDate effectiveFrom,AllocationOrder allocationOrder,
                         UUID interestAccountId,String interestAccount,String reason,OffsetDateTime createdAt) {}
    public record Split(BigDecimal principal,BigDecimal interest,BigDecimal principalBalance) {}
}
