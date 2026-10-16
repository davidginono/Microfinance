package com.sacco.mvp.accounting.management.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.Data;

/** Fixed, typed report layouts. Neither queries nor formulas are supplied by the browser. */
public final class ManagementReports {
    private ManagementReports() {}
    public enum Kind { DAILY_COLLECTIONS, CASHBOOK, LOAN_PORTFOLIO, PORTFOLIO_RISK, PORTFOLIO_ANALYSIS }
    public record Parameters(LocalDate from, LocalDate through, OffsetDateTime cutoff, UUID startAccount,
            UUID endAccount, String client, String product, int page) {}
    public record Choice(UUID id, String code, String name) { public String getLabel(){return name;} }
    public record Column(String key, boolean money, float width) {}
    public record Group(String name, long count, Map<String,BigDecimal> totals) {}
    public record Result(Kind kind, Parameters parameters, String institution, String branch,
            List<Column> columns, List<Map<String,Object>> rows, List<Group> groups,
            Map<String,BigDecimal> totals, long count, long excluded, boolean hasNext, String accountRange) {
        public List<Group> getDisplayedGroups(){var names=new HashSet<Object>();rows.forEach(r->names.add(r.get("groupName")));return groups.stream().filter(g->names.contains(g.name())).toList();}
        public Map<String,Map<String,BigDecimal>> getDailyTotals(){var days=new LinkedHashMap<String,Map<String,BigDecimal>>();for(var group:groups){var day=days.computeIfAbsent(group.name().substring(0,10),k->new LinkedHashMap<>());group.totals().forEach((k,v)->{if(v!=null)day.merge(k,v,BigDecimal::add);});}return days;}
    }
    @Data public static class BudgetForm {
        private UUID requestKey=UUID.randomUUID();
        private UUID accountId;
        private UUID replacesId;
        @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
        private LocalDate date;
        private Integer year;
        private List<BigDecimal> amounts=new ArrayList<>(Collections.nCopies(12, null));
    }
    public record Budget(UUID id, UUID accountId, String code, String account, LocalDate date, int year,
            int revision, List<BigDecimal> amounts, BigDecimal total, String recordedBy, UUID replacesId) {}
    public record BudgetPage(List<Budget> rows, int page, boolean hasNext) {}

    private static Column text(String key,float width){return new Column(key,false,width);}
    private static Column money(String key){return new Column(key,true,1);}
    public static List<Column> columns(Kind kind) {
        return switch(kind) {
            case DAILY_COLLECTIONS -> List.of(text("clientNumber",.8f),text("loanId",.9f),text("name",1.6f),text("documentReference",1),text("documentNumber",1),
                text("receiptDate",1),text("transactionCode",.7f),text("transaction",1.5f),money("debit"),money("credit"),text("phone",1.2f),text("recordedBy",1));
            case CASHBOOK -> List.of(text("date",1),text("clientNumber",.8f),text("name",1.4f),text("particulars",2),text("reference",1.2f),
                money("moneyIn"),money("moneyOut"),money("principalIn"),money("principalOut"),money("interestIn"),money("interestOut"));
            case LOAN_PORTFOLIO -> List.of(text("clientNumber",.8f),text("loanId",1),text("name",1.8f),text("gender",.6f),money("disbursed"),
                money("contractTotal"),text("disbursementDate",1),money("instalment"),text("frequency",1),money("principalBalance"),money("interestBalance"),text("maturity",1),text("periods",.6f));
            case PORTFOLIO_RISK -> List.of(text("clientNumber",.8f),text("loanId",1),text("name",1.6f),money("disbursed"),text("disbursementDate",1),money("principalBalance"),
                money("band0"),money("band1"),money("band2"),money("band3"),money("band4"),money("overdue"),money("referenceProvision"),text("phone",1.2f));
            case PORTFOLIO_ANALYSIS -> List.of(text("clientNumber",.8f),text("name",1.7f),text("loanId",1),money("disbursed"),text("disbursementDate",1),money("totalPrincipal"),
                money("principalPaid"),money("principalBalance"),money("principalArrears"),money("totalInterest"),money("interestPaid"),money("interestBalance"),money("interestArrears"),text("defaultRate",.7f));
        };
    }
}
