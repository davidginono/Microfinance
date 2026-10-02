package com.sacco.mvp.accounting.statements;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Versioned financial row language. Labels and layout never participate in calculations. */
public record StatementDefinition(int schemaVersion, int calculationVersion, Kind kind, String titleEn, String titleSw,
        List<Row> rows, List<Exclusion> exclusions, boolean comparison) {
    public StatementDefinition { if(rows!=null)rows=Collections.unmodifiableList(new ArrayList<>(rows));if(exclusions!=null)exclusions=Collections.unmodifiableList(new ArrayList<>(exclusions)); }
    public enum Kind { BALANCE_SHEET, PROFIT_AND_LOSS, CASH_FLOW, CHANGES_IN_EQUITY, NOTES }
    public enum RowKind { HEADING, ACCOUNT_GROUP, SUBTOTAL, RATIO, NOTE }
    public enum Unit { TZS, RATIO, NONE }
    public enum Section { ASSETS, LIABILITIES, EQUITY, INCOME, EXPENSES, OPERATING, INVESTING, FINANCING, NOTES }
    public enum Sign { DEBIT_POSITIVE, CREDIT_POSITIVE }
    public enum Operation { SUM, DIFFERENCE, RATIO }
    public record Expression(Operation operation, List<String> lines) { public Expression { if(lines!=null)lines=Collections.unmodifiableList(new ArrayList<>(lines)); } }
    public record Row(String id, String labelEn, String labelSw, RowKind kind, Section section, Unit unit,
            List<UUID> accounts, Sign sign, Expression expression, String noteEn, String noteSw,
            boolean visible, boolean collapsed, boolean memo, String exceptionEvidence) { public Row { if(accounts!=null)accounts=Collections.unmodifiableList(new ArrayList<>(accounts)); } }
    public record Exclusion(UUID account, String treatmentEn, String treatmentSw, String evidence) { }
    public record Account(UUID id, String code, String name, String type, String purpose) { }
    public record Value(BigDecimal amount, String status) { }

    public void validate() {
        require(schemaVersion==1 && calculationVersion==1 && kind!=null,"version");
        text(titleEn,100);text(titleSw,100);
        require(rows!=null && !rows.isEmpty() && rows.size()<=100 && exclusions!=null && exclusions.size()<=1000,"size");
        var byId=new LinkedHashMap<String,Row>();
        for(Row row:rows) {
            require(row!=null && row.id()!=null && row.id().matches("[A-Z][A-Z0-9_]{0,39}") && byId.put(row.id(),row)==null,"line");
            text(row.labelEn(),120);text(row.labelSw(),120);
            require(row.kind()!=null && row.section()!=null && row.unit()!=null && row.accounts()!=null && row.accounts().size()<=1000,"line");
            require(new HashSet<>(row.accounts()).size()==row.accounts().size() && !row.accounts().contains(null),"overlap");
            if(row.kind()==RowKind.ACCOUNT_GROUP) {
                require(!row.accounts().isEmpty() && row.sign()!=null && row.expression()==null && row.unit()==Unit.TZS,"line");
                if(row.memo()) {require(row.section()==Section.NOTES,"memo");text(row.exceptionEvidence(),500);}
            } else {
                require(row.accounts().isEmpty() && row.sign()==null && !row.memo(),"line");
                if(row.kind()==RowKind.SUBTOTAL || row.kind()==RowKind.RATIO) {
                    require(row.expression()!=null && row.expression().operation()!=null && row.expression().lines()!=null,"expression");
                    require(row.expression().lines().size()>=2 && row.expression().lines().size()<=30
                            && new HashSet<>(row.expression().lines()).size()==row.expression().lines().size(),"expression");
                    require(row.kind()==RowKind.RATIO ? row.unit()==Unit.RATIO && row.expression().operation()==Operation.RATIO
                            : row.unit()==Unit.TZS && row.expression().operation()!=Operation.RATIO,"units");
                    if(row.expression().operation()!=Operation.SUM)require(row.expression().lines().size()==2,"expression");
                } else {
                    require(row.expression()==null && row.unit()==Unit.NONE,"units");
                    if(row.kind()==RowKind.NOTE){text(row.noteEn(),1000);text(row.noteSw(),1000);}
                }
            }
        }
        var excluded=new HashSet<UUID>();for(var x:exclusions){require(x!=null && x.account()!=null && excluded.add(x.account()),"overlap");text(x.treatmentEn(),500);text(x.treatmentSw(),500);text(x.evidence(),500);}
        var memo=new HashMap<String,Set<String>>();var heights=new HashMap<String,Integer>();
        for(Row r:rows)if(!r.memo())dependencies(r.id(),byId,new HashSet<>(),memo,heights,0);
    }

    /** Every eligible account has one primary placement or an independently approved, disclosed exclusion. */
    public void validateMappings(List<Account> catalog) {
        validate();require(catalog!=null && catalog.size()<=1000,"size");
        var accounts=new HashMap<UUID,Account>();catalog.forEach(a->accounts.put(a.id(),a));
        for(var a:catalog){String expected=switch(a.purpose()){case "LOAN_PRINCIPAL","INTEREST_RECEIVABLE","FEE_RECEIVABLE","ALLOWANCE","FIXED_ASSET","PREPAYMENT"->"ASSET";case "PAYABLE","FUNDING","TAX"->"LIABILITY";case "CAPITAL"->"EQUITY";case "INCOME"->"INCOME";case "EXPENSE"->"EXPENSE";default->null;};require(expected==null || expected.equals(a.type()),"section");}
        var used=new HashSet<UUID>();
        for(var row:rows)for(UUID id:row.accounts()) {
            Account account=accounts.get(id);require(account!=null && eligible(account),"account");
            if(!row.memo()) {require(used.add(id),"overlap");require(sectionAllows(row.section(),account.type()),"section");
                require(row.sign()==(Set.of(Section.LIABILITIES,Section.EQUITY,Section.INCOME).contains(row.section())?Sign.CREDIT_POSITIVE:Sign.DEBIT_POSITIVE),"sign");}
        }
        for(var x:exclusions){require(accounts.containsKey(x.account()) && eligible(accounts.get(x.account())) && used.add(x.account()),"account");}
        for(var account:catalog)if(eligible(account))require(used.contains(account.id()),"omitted");
        // Repeated memo placement is an explicit disclosure, never a subtotal operand.
        for(var row:rows)if(row.memo())for(UUID id:row.accounts())require(used.contains(id),"omitted");
    }
    public boolean eligible(Account a) {
        return switch(kind) {
            case PROFIT_AND_LOSS -> Set.of("INCOME","EXPENSE").contains(a.type());
            case CHANGES_IN_EQUITY -> Set.of("EQUITY","INCOME","EXPENSE").contains(a.type());
            case CASH_FLOW -> !Set.of("CASH","BANK","MOBILE_MONEY").contains(a.purpose());
            default -> true;
        };
    }
    private boolean sectionAllows(Section section,String type) {
        if(kind==Kind.CASH_FLOW || kind==Kind.NOTES)return Set.of(Section.OPERATING,Section.INVESTING,Section.FINANCING,Section.NOTES).contains(section);
        return switch(section){case ASSETS->type.equals("ASSET");case LIABILITIES->type.equals("LIABILITY");case EQUITY->Set.of("EQUITY","INCOME","EXPENSE").contains(type);case INCOME->type.equals("INCOME");case EXPENSES->type.equals("EXPENSE");default->false;};
    }
    private static Set<String> dependencies(String id,Map<String,Row> rows,Set<String> visiting,Map<String,Set<String>> cached,Map<String,Integer> heights,int depth) {
        require(depth<=8,"depth");if(cached.containsKey(id)){require(depth+heights.get(id)<=8,"depth");return cached.get(id);}
        Row row=rows.get(id);require(row!=null && visiting.add(id),"cycle");var leaves=new HashSet<String>();int height=0;
        if(row.kind()==RowKind.ACCOUNT_GROUP){require(!row.memo(),"memo");leaves.add(id);}
        else if(row.expression()!=null)for(String ref:row.expression().lines()) {
            Row operand=rows.get(ref);require(operand!=null && operand.unit()==Unit.TZS && !operand.memo(),"units");
            Set<String> children=dependencies(ref,rows,visiting,cached,heights,depth+1);height=Math.max(height,1+heights.get(ref));
            if(row.expression().operation()!=Operation.RATIO)require(Collections.disjoint(leaves,children),"doubleCount");
            leaves.addAll(children);
        }
        visiting.remove(id);cached.put(id,Set.copyOf(leaves));heights.put(id,height);return leaves;
    }
    public Map<String,Value> calculate(Map<UUID,BigDecimal> source) {
        validate();var rowsById=new HashMap<String,Row>();rows.forEach(r->rowsById.put(r.id(),r));var values=new LinkedHashMap<String,Value>();
        for(Row row:rows)calculate(row.id(),rowsById,source,values);return Collections.unmodifiableMap(values);
    }
    private static Value calculate(String id,Map<String,Row> rows,Map<UUID,BigDecimal> source,Map<String,Value> computed) {
        if(computed.containsKey(id))return computed.get(id);Row row=rows.get(id);BigDecimal amount=null;String status="TEXT";
        if(row.kind()==RowKind.ACCOUNT_GROUP) {
            amount=BigDecimal.ZERO.setScale(2);status="KNOWN";
            for(UUID a:row.accounts()){if(source.get(a)==null){amount=null;status="UNAVAILABLE";break;}amount=amount.add(source.get(a));}
            if(amount!=null && row.sign()==Sign.CREDIT_POSITIVE)amount=amount.negate();
        } else if(row.expression()!=null) {
            var args=row.expression().lines().stream().map(ref->calculate(ref,rows,source,computed)).toList();
            if(args.stream().anyMatch(v->v.amount()==null)){status="UNAVAILABLE";}
            else if(row.expression().operation()==Operation.RATIO && args.get(1).amount().signum()==0){status="DIVIDE_BY_ZERO";}
            else {status="KNOWN";amount=switch(row.expression().operation()){
                case SUM->args.stream().map(Value::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
                case DIFFERENCE->args.getFirst().amount().subtract(args.get(1).amount());
                case RATIO->args.getFirst().amount().divide(args.get(1).amount(),6,RoundingMode.HALF_UP);
            };}
        }
        var value=new Value(amount,status);computed.put(id,value);return value;
    }
    static void text(String s,int max){require(s!=null && !s.isBlank() && s.length()<=max && s.codePoints().noneMatch(c->c<32 || c=='<' || c=='>'),"text");}
    static void require(boolean ok,String key){if(!ok)throw new IllegalArgumentException("statement.error."+key);}
}
