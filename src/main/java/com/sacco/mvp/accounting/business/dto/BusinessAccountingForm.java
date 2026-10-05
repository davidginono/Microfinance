package com.sacco.mvp.accounting.business.dto;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.*;
import com.sacco.mvp.domain.RepaymentFrequency;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter @Setter
public class BusinessAccountingForm {
    private UUID requestKey=UUID.randomUUID();
    private Kind kind;
    private LocalDate effectiveDate;
    private BigDecimal amount;
    private UUID loanId,relatedDocumentId,supplierId;
    private String description,evidenceReference,channelReference,moneyAccountKey,destinationBranch,loanNumber;
    private LocalDate firstRepaymentDate;
    private RepaymentFrequency frequency;
    private BigDecimal installmentAmount;
    public Command command(){return new Command(requestKey,kind,effectiveDate,amount,loanId,relatedDocumentId,supplierId,description,evidenceReference,blank(channelReference),blank(moneyAccountKey),blank(destinationBranch),blank(loanNumber),firstRepaymentDate,frequency,installmentAmount);}
    private String blank(String s){return s==null || s.isBlank()?null:s;}
}
