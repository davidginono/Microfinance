package com.sacco.mvp.accounting.statements.service;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import java.math.BigDecimal;
import java.util.*;

/** Presentation of posted movements only: no percentage allocation, posting or inferred purpose. */
public final class EquityStatementCalculator {
    private EquityStatementCalculator() {}
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    public record Result(List<String> columns,List<EquityRow> current,List<EquityRow> previous,List<Check> checks) {}
    public static List<Balance> incomeWithoutClosings(List<Balance> accounts,List<Movement> movements){
        var adjustments=new HashMap<UUID,BigDecimal[]>();
        for(var m:movements)if(m.category()==Category.EARNINGS_TRANSFER){var values=adjustments.computeIfAbsent(m.accountId(),k->new BigDecimal[]{ZERO,ZERO,ZERO,ZERO});int i=m.previous()?2:0;values[i]=values[i].add(m.debit());values[i+1]=values[i+1].add(m.credit());}
        return accounts.stream().map(b->{var v=adjustments.get(b.id());if(v==null||!Set.of("INCOME","EXPENSE").contains(b.type()))return b;
            return new Balance(b.id(),b.code(),b.name(),b.type(),b.purpose(),b.mapping(),b.opening(),b.debit().subtract(v[0]),b.credit().subtract(v[1]),b.closing(),b.previousOpening(),b.previousDebit().subtract(v[2]),b.previousCredit().subtract(v[3]),b.previousClosing());}).toList();
    }
    public static Result calculate(List<Balance> accounts,List<Movement> movements,Setup setup){
        var columns=new LinkedHashSet<String>();var componentByAccount=new HashMap<UUID,String>();String retained="fs.retainedEarnings";
        for(int i=0;i<setup.components().size();i++){var c=setup.components().get(i);String key="component:"+i;columns.add(key);for(UUID id:c.accounts())componentByAccount.put(id,key);if(c.retained())retained=key;}
        boolean unclassifiedComponent=false;
        for(var b:accounts)if(b.type().equals("EQUITY")&&!componentByAccount.containsKey(b.id())){
            String component=b.mapping()!=null&&!b.mapping().equityComponent().isBlank()?"account:"+b.mapping().equityComponent():"fs.unclassified.equity";
            componentByAccount.put(b.id(),component);columns.add(component);
            if(component.equals("fs.unclassified.equity")&&(b.opening().signum()!=0||b.closing().signum()!=0||b.previousOpening().signum()!=0||b.previousClosing().signum()!=0||b.debit().signum()!=0||b.credit().signum()!=0))unclassifiedComponent=true;
        }
        columns.add(retained);if(columns.size()>10)throw new IllegalArgumentException("fs.error.equityColumns");
        var checks=new ArrayList<Check>();if(unclassifiedComponent)checks.add(new Check("equityComponents",null));
        var current=period(accounts,movements,componentByAccount,List.copyOf(columns),retained,false,checks);
        var previous=period(accounts,movements,componentByAccount,List.copyOf(columns),retained,true,checks);
        return new Result(List.copyOf(columns),current,previous,List.copyOf(checks));
    }
    private static List<EquityRow> period(List<Balance> accounts,List<Movement> movements,Map<UUID,String> components,List<String> columns,String retained,boolean previous,List<Check> checks){
        var opening=zero(columns);var closing=zero(columns);var profit=zero(columns);var byId=new HashMap<UUID,Balance>();accounts.forEach(b->byId.put(b.id(),b));
        for(var b:accounts){if(b.type().equals("EQUITY")){String c=components.get(b.id());opening.merge(c,(previous?b.previousOpening():b.opening()).negate(),BigDecimal::add);closing.merge(c,(previous?b.previousClosing():b.closing()).negate(),BigDecimal::add);}
            else if(Set.of("INCOME","EXPENSE").contains(b.type())){opening.merge(retained,(previous?b.previousOpening():b.opening()).negate(),BigDecimal::add);closing.merge(retained,(previous?b.previousClosing():b.closing()).negate(),BigDecimal::add);profit.merge(retained,(previous?b.previousMovement():b.movement()).negate(),BigDecimal::add);}}
        var changes=new EnumMap<Category,Map<String,BigDecimal>>(Category.class);for(var c:Category.values())changes.put(c,zero(columns));
        for(var m:movements)if(m.previous()==previous){var account=byId.get(m.accountId());if(account==null)throw new IllegalArgumentException("fs.error.snapshot");
            if(account.type().equals("EQUITY"))changes.get(m.category()).merge(components.get(account.id()),m.signed().negate(),BigDecimal::add);
            else if(m.category()==Category.EARNINGS_TRANSFER&&Set.of("INCOME","EXPENSE").contains(account.type())){
                // Closing transfers move already-earned profit between components; they are not new profit.
                changes.get(Category.EARNINGS_TRANSFER).merge(retained,m.signed().negate(),BigDecimal::add);profit.merge(retained,m.signed(),BigDecimal::add);
            }}
        var rows=new ArrayList<EquityRow>();rows.add(row("fs.opening",opening,true));rows.add(row("fs.surplus",profit,false));
        for(var c:Category.values()){var values=changes.get(c);if(values.values().stream().anyMatch(v->v.signum()!=0)||c==Category.UNCLASSIFIED&&movements.stream().anyMatch(m->m.previous()==previous&&m.category()==c&&(m.debit().signum()!=0||m.credit().signum()!=0)))rows.add(row("fs.movement."+c,values,false));}
        rows.add(row("fs.closing",closing,true));
        // Check every component, not merely a total that can hide offsetting errors.
        for(String column:columns){BigDecimal difference=opening.get(column).add(profit.get(column));for(var c:Category.values())difference=difference.add(changes.get(c).get(column));difference=difference.subtract(closing.get(column));if(difference.signum()!=0)checks.add(new Check(previous?"previousEquity":"equity",difference));}
        boolean unknown=movements.stream().anyMatch(m->m.previous()==previous&&m.category()==Category.UNCLASSIFIED&&(m.debit().signum()!=0||m.credit().signum()!=0));if(unknown)checks.add(new Check(previous?"previousEquityMovements":"equityMovements",null));
        return List.copyOf(rows);
    }
    private static Map<String,BigDecimal> zero(List<String> columns){var result=new LinkedHashMap<String,BigDecimal>();columns.forEach(c->result.put(c,ZERO));return result;}
    private static EquityRow row(String label,Map<String,BigDecimal> values,boolean total){return new EquityRow(label,Map.copyOf(values),values.values().stream().reduce(ZERO,BigDecimal::add),total);}
}
