package com.sacco.mvp.grouplending.dto;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow;
import com.sacco.mvp.service.PaymentDetailsService.PaymentDetailsView;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class GroupOrganizationAgreementDtos {
    private GroupOrganizationAgreementDtos() {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Organization(UUID partyId,UUID groupId,String name,String code,String legalForm) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Authority(UUID id,int revision,LocalDate validFrom,LocalDate validThrough,BigDecimal maxPrincipal,
        int requiredSignatures,String capacityEvidence,String resolutionEvidence) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Loan(UUID id,String number,String product,BigDecimal principal,int tenorMonths,String frequency,
        LocalDate firstPaymentDate,String purpose,String agreementReference,String policyReference) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Finance(String calculationVersion,BigDecimal principalAmount,BigDecimal interestRate,String interestMethod,
        String repaymentFrequency,String tenureUnit,Integer tenureValue,Integer numberOfPayments,
        BigDecimal periodicRepaymentAmount,BigDecimal maximumInstallmentAmount,BigDecimal interestAmount,
        Plan recordedCharges,BigDecimal recordedChargeTotal,BigDecimal recordedChargeDeductions,
        BigDecimal recordedNetCashEstimate,BigDecimal recordedTotalPayable,Map<String,Object> prepaymentTerms) {}
    public record Snapshot(Organization organization,Authority authority,Loan loan,Finance financial,PaymentDetailsView payout) {}
    public record Row(UUID cycle,Snapshot snapshot,String hash,OffsetDateTime preparedAt,String state,String loanStatus,
        int signedCount,boolean signed,OffsetDateTime signedAt,boolean currentAuthority) {}
    public record Packet(UUID cycle,Snapshot snapshot,String hash,String state,String loanStatus,int signedCount,
        boolean signed,OffsetDateTime signedAt,boolean canSign,boolean signatureReady,boolean otpRequired,
        List<ScheduleRow> schedule,int page,boolean hasNext) {}
}
