package com.sacco.mvp.accounting.statements.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class StatementModels {
    private StatementModels() {}
    public enum Kind { POSITION, INCOME, CASH_FLOW, EQUITY, NOTES, TRIAL, LEDGER, ALL }
    public static final Set<String> SECTIONS=Set.of("CURRENT_ASSETS","NON_CURRENT_ASSETS","CURRENT_LIABILITIES","NON_CURRENT_LIABILITIES","EQUITY","INCOME","EXPENSE","UNCLASSIFIED");
    public static final Set<String> CASH_SECTIONS=Set.of("OPERATING","INVESTING","FINANCING","UNCLASSIFIED");
    public record Parameters(LocalDate from,LocalDate through,boolean institutionWide) {
        public LocalDate previousFrom(){return from.minusYears(1);}
        public LocalDate previousThrough(){return through.minusYears(1);}
    }
    public record Mapping(UUID accountId,String line,String section,String cashSection,String equityComponent,int note,String noteText) {}
    public record Balance(UUID id,String code,String name,String type,String purpose,Mapping mapping,
                          BigDecimal opening,BigDecimal debit,BigDecimal credit,BigDecimal closing,
                          BigDecimal previousOpening,BigDecimal previousDebit,BigDecimal previousCredit,BigDecimal previousClosing) {
        public BigDecimal movement(){return debit.subtract(credit);}
        public BigDecimal previousMovement(){return previousDebit.subtract(previousCredit);}
        public boolean money(){return Set.of("CASH","BANK","MOBILE_MONEY").contains(purpose);}
    }
    public record Cash(UUID journalId,LocalDate date,UUID accountId,String type,String purpose,BigDecimal debit,BigDecimal credit) {
        public boolean money(){return Set.of("CASH","BANK","MOBILE_MONEY").contains(purpose);}
        public BigDecimal signed(){return debit.subtract(credit);}
    }
    public record Row(String key,String code,String label,int note,String section,List<UUID> accounts,BigDecimal current,BigDecimal previous,boolean total) {}
    public record EquityRow(String label,Map<String,BigDecimal> values,BigDecimal total,boolean summary) {}
    public record Check(String key,BigDecimal difference) {}
    public record Snapshot(int version,UUID id,Parameters parameters,OffsetDateTime generatedAt,String institution,String branch,
                           String institutionName,String branchName,List<Balance> balances,List<Row> position,List<Row> income,
                           List<Row> cashFlow,List<String> equityColumns,List<EquityRow> equity,List<Check> checks) {}
    public record Run(UUID id,LocalDate from,LocalDate through,OffsetDateTime createdAt,boolean institutionWide,boolean saved,Snapshot snapshot) {}
    public record Activity(UUID journalId,LocalDate date,String reference,String description,BigDecimal debit,BigDecimal credit,BigDecimal running,String sourceUrl) {}
    public record MappingRow(UUID id,String code,String name,String type,String purpose,Mapping mapping,UUID revision) {}
}
