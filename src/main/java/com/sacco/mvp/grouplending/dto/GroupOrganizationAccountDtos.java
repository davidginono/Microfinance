package com.sacco.mvp.grouplending.dto;
import java.util.UUID;
public final class GroupOrganizationAccountDtos {
    private GroupOrganizationAccountDtos() {}
    public record Account(UUID cycle,String group,String loan,String status) {}
}
