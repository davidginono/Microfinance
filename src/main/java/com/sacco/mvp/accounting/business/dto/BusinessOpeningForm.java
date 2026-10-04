package com.sacco.mvp.accounting.business.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class BusinessOpeningForm {
    private UUID requestKey=UUID.randomUUID();
    private String accountCode;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate through;
    private String purpose;
    private String evidence;
    private boolean completeCoverage;
}
