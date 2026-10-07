package com.sacco.mvp.accounting.dto;

import lombok.Data;
import java.util.UUID;

/** Only accountant-entered chart metadata; classification and institution are server-derived. */
@Data
public class AccountOnboardingForm {
    private UUID parentId;
    private String code;
    private String name;
    private String nameSw;
    private String normalBalance = "";
    private String kind = "POSTING";
    private String purpose = "OTHER";
    private String description;
}
