package com.sacco.mvp.reporting.operational.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter @Setter
public class OperationalDesignerForm {
    private UUID id;
    private Long expectedVersion;
    @NotBlank @Size(max=120) private String name;
    @NotBlank @Size(max=16384) private String definitionJson;
}
