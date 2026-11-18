package com.sacco.mvp.accounting.statements.dto;

import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.Reporting;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class StatementModels {
    private StatementModels() {}
    public enum Kind { POSITION, INCOME, CASH_FLOW, EQUITY, NOTES, TRIAL, LEDGER }
    public record Parameters(LocalDate from,LocalDate through,boolean institutionWide) {
        public LocalDate previousFrom(){return from.minusYears(1);}
        public LocalDate previousThrough(){return through.minusYears(1);}
    }
    public record Mapping(UUID accountId,String line,String section,String cashSection,String equityComponent,int note,String noteText,UUID groupId,String groupCode) {}
    public record Balance(UUID id,String code,String name,String type,String purpose,Mapping mapping,
                          BigDecimal opening,BigDecimal debit,BigDecimal credit,BigDecimal closing,
                          BigDecimal previousOpening,BigDecimal previousDebit,BigDecimal previousCredit,BigDecimal previousClosing) {
        public BigDecimal movement(){return debit.subtract(credit);}
        public BigDecimal previousMovement(){return previousDebit.subtract(previousCredit);}
        public boolean money(){
            return Set.of("CASH","BANK","MOBILE_MONEY").contains(purpose)
                || "ASSET".equals(type) && mapping!=null && Reporting.CASH_EQUIVALENTS.equals(mapping.cashSection());
        }
    }
    public record Cash(UUID journalId,LocalDate date,UUID accountId,String type,String purpose,BigDecimal debit,BigDecimal credit) {
        public BigDecimal signed(){return debit.subtract(credit);}
    }
    public record Row(String key,String code,String label,int note,String section,List<UUID> accounts,BigDecimal current,BigDecimal previous,boolean total) {}
    public record EquityRow(String label,Map<String,BigDecimal> values,BigDecimal total,boolean summary) {}
    public record Check(String key,BigDecimal difference) {}
    public record Snapshot(int version,UUID id,Parameters parameters,OffsetDateTime generatedAt,String institution,String branch,
                           String institutionName,String branchName,List<Balance> balances,List<Row> position,List<Row> income,
                           List<Row> cashFlow,List<String> equityColumns,List<EquityRow> equity,List<Check> checks,
                           StatementSetupModels.Setup setup,List<EquityRow> previousEquity,List<StatementSetupModels.Movement> equityMovements) {
        public Snapshot(int version,UUID id,Parameters parameters,OffsetDateTime generatedAt,String institution,String branch,
                        String institutionName,String branchName,List<Balance> balances,List<Row> position,List<Row> income,
                        List<Row> cashFlow,List<String> equityColumns,List<EquityRow> equity,List<Check> checks) {
            this(version,id,parameters,generatedAt,institution,branch,institutionName,branchName,balances,position,income,cashFlow,equityColumns,equity,checks,null,null,null);
        }
    }
    public record Run(UUID id,LocalDate from,LocalDate through,OffsetDateTime createdAt,boolean institutionWide,boolean saved,Snapshot snapshot) {}
    public record Activity(UUID journalId,LocalDate date,String accountNumber,String description,BigDecimal debit,BigDecimal credit,BigDecimal running,String sourceUrl) {}
}
