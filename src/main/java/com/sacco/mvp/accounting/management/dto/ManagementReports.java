package com.sacco.mvp.accounting.management.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.Data;

/** Fixed, typed report layouts. Neither queries nor formulas are supplied by the browser. */
public final class ManagementReports {
    private ManagementReports() {}
    public enum Kind { DAILY_COLLECTIONS, CASHBOOK, LOAN_PORTFOLIO, PORTFOLIO_RISK, PORTFOLIO_ANALYSIS, DISBURSED_LOANS_AUDIT, EXPECTED_REPAYMENTS, LOAN_REPAYMENT_TRANSACTIONS }
    public record Parameters(LocalDate from, LocalDate through, OffsetDateTime cutoff, UUID startAccount,
            UUID endAccount, UUID clientId, String product, int page, UUID productId) {
        public Parameters(LocalDate from, LocalDate through, OffsetDateTime cutoff, UUID startAccount,
                UUID endAccount, UUID clientId, String product, int page) {
            this(from,through,cutoff,startAccount,endAccount,clientId,product,page,null);
        }
        public Parameters withProductName(String name) {
            return new Parameters(from,through,cutoff,startAccount,endAccount,clientId,name,page,productId);
        }
    }
    public record Choice(UUID id, String code, String name, String label) {
        public Choice(UUID id,String code,String name){this(id,code,name,name);}
        public String getLabel(){return label;}
    }
    public record Column(String key, boolean money, float width) {}
    public record Group(String name, long count, Map<String,BigDecimal> totals) {}
    public record SubtotalLayout(Set<String> groups,Set<String> periods) {}
    public record Result(Kind kind, Parameters parameters, String institution, String branch,
            List<Column> columns, List<Map<String,Object>> rows, List<Group> groups,
            Map<String,BigDecimal> totals, long count, long excluded, boolean hasNext, String accountSelection, String clientSelection) {
        public List<String> getTotalFields(){return totalFields(kind);}
        public List<Column> getDocumentColumns(){return isReferenceLayout()?documentColumns(kind):columns;}
        public boolean hasSpreadsheet(){return spreadsheetSupported(kind);}
        public boolean isReferenceLayout(){return Set.of(Kind.DISBURSED_LOANS_AUDIT,Kind.EXPECTED_REPAYMENTS,Kind.LOAN_REPAYMENT_TRANSACTIONS).contains(kind);}
        public String getNoteKey(){
            if(excluded>0) return kind==Kind.DAILY_COLLECTIONS?"unlinkedReceipts":"exclusionBasis";
            if(count==0) return "";
            if(totals.values().stream().anyMatch(Objects::isNull)) return "unknownAmounts";
            return kind==Kind.CASHBOOK?"":"coverage."+kind;
        }
        public double getColumnWidthTotal(){return columns.stream().mapToDouble(Column::width).sum();}
        public Map<String,Map<String,Object>> getGroupFacts(){var facts=new LinkedHashMap<String,Map<String,Object>>();if(isReferenceLayout())rows.forEach(row->facts.putIfAbsent(row.get("groupName").toString(),row));return facts;}
        public List<Group> getDisplayedGroups(){var names=new HashSet<Object>();rows.forEach(r->names.add(r.get("groupName")));return groups.stream().filter(g->names.contains(g.name())).toList();}
        public Map<String,Map<String,BigDecimal>> getDailyTotals(){return periodTotals(10);}
        public Map<String,Map<String,BigDecimal>> getMonthlyTotals(){return periodTotals(7);}
        public SubtotalLayout getSubtotalLayout(){
            var groupTotals=new HashSet<String>();var periodTotals=new HashSet<String>();
            int length=kind==Kind.DISBURSED_LOANS_AUDIT?7:kind==Kind.DAILY_COLLECTIONS?10:0;
            if(length==0){
                if(groups.size()>1)groups.stream().filter(g->g.count()>1).forEach(g->groupTotals.add(g.name()));
            }else{
                var periodGroups=new HashMap<String,Integer>();var periodRows=new HashMap<String,Long>();
                for(var group:groups){
                    String period=group.name().substring(0,Math.min(length,group.name().length()));
                    periodGroups.merge(period,1,Integer::sum);periodRows.merge(period,group.count(),Long::sum);
                }
                for(var group:groups){
                    String period=group.name().substring(0,Math.min(length,group.name().length()));
                    if(periodGroups.get(period)>1 && group.count()>1)groupTotals.add(group.name());
                    if(periodGroups.size()>1 && periodRows.get(period)>1)periodTotals.add(period);
                }
            }
            return new SubtotalLayout(Set.copyOf(groupTotals),Set.copyOf(periodTotals));
        }
        private Map<String,Map<String,BigDecimal>> periodTotals(int length){var days=new LinkedHashMap<String,Map<String,BigDecimal>>();for(var group:groups){var day=days.computeIfAbsent(group.name().substring(0,length),k->new LinkedHashMap<>());group.totals().forEach((k,v)->{if(!day.containsKey(k))day.put(k,v);else day.put(k,v==null || day.get(k)==null?null:day.get(k).add(v));});}return days;}
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
    public static boolean spreadsheetSupported(Kind kind){return Set.of(Kind.DISBURSED_LOANS_AUDIT,Kind.EXPECTED_REPAYMENTS,Kind.LOAN_REPAYMENT_TRANSACTIONS,Kind.LOAN_PORTFOLIO,Kind.PORTFOLIO_RISK,Kind.PORTFOLIO_ANALYSIS).contains(kind);}
    public static List<String> totalFields(Kind kind) {
        var nonAdditive = kind==Kind.DISBURSED_LOANS_AUDIT || kind==Kind.LOAN_PORTFOLIO ? Set.of("instalment") : kind==Kind.EXPECTED_REPAYMENTS ? Set.of("principalBalance") :
            kind==Kind.LOAN_REPAYMENT_TRANSACTIONS ? Set.of("openingPrincipal","closingPrincipal") : Set.<String>of();
        var totals=new ArrayList<>(columns(kind).stream().filter(c->c.money() && !nonAdditive.contains(c.key())).map(Column::key).toList());
        if(kind==Kind.DISBURSED_LOANS_AUDIT)totals.add("netDisbursed");
        return List.copyOf(totals);
    }
    public static List<Column> documentColumns(Kind kind) {
        return switch(kind) {
            case DISBURSED_LOANS_AUDIT -> List.of(text("clientNumber",.8f),text("name",1.8f),text("loanId",.8f),
                money("netDisbursed"),money("disbursed"),money("principalInstallment"),money("totalInterest"),money("instalment"),
                text("disbursementDate",1),text("maturity",1),text("reference",1),text("loanRate",.8f),text("periods",.7f));
            case EXPECTED_REPAYMENTS -> List.of(text("clientNumber",.8f),text("loanId",.9f),text("name",1.8f),text("phone",1.2f),text("dueDate",1),
                money("principalBalance"),money("principalDue"),money("interestDue"),money("expectedTotal"));
            case LOAN_REPAYMENT_TRANSACTIONS -> List.of(text("date",1.4f),money("openingPrincipal"),money("principalPaid"),money("interestPaid"),
                money("payment"),money("closingPrincipal"),money("interestExcluded"));
            default -> columns(kind);
        };
    }
    public static List<Column> columns(Kind kind) {
        return switch(kind) {
            case DISBURSED_LOANS_AUDIT -> List.of(text("clientNumber",.8f),text("name",1.8f),text("loanId",.8f),money("disbursed"),
                money("totalInterest"),money("instalment"),text("disbursementDate",1),text("maturity",1),text("reference",1.1f),text("periods",1),text("frequency",.8f));
            case EXPECTED_REPAYMENTS -> List.of(text("clientNumber",.8f),text("loanId",.9f),text("name",1.8f),text("phone",1.2f),text("dueDate",1),
                money("principalBalance"),money("principalDue"),money("interestDue"),money("expectedTotal"));
            case LOAN_REPAYMENT_TRANSACTIONS -> List.of(text("date",1.4f),money("openingPrincipal"),money("principalPaid"),money("interestPaid"),
                money("payment"),money("closingPrincipal"),money("interestExcluded"));
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
