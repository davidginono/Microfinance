package com.sacco.mvp.grouplending.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class GroupOrganizationFundingDtos {
    private GroupOrganizationFundingDtos() {}
    public record Context(UUID cycle,String hash,BigDecimal principal,BigDecimal net,LocalDate firstPaymentDate,
        boolean ready) {}
}
