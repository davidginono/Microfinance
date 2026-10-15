package com.sacco.mvp.accounting.statements.service;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import java.math.BigDecimal;
import java.util.*;

/** Decimal calculations over a single frozen ledger membership; never posts or reprices loans. */
public final class StatementCalculator {
    private StatementCalculator() {}
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    public record Result(List<Row> position,List<Row> income,List<Row> cash,List<String> equityColumns,List<EquityRow> equity,List<Check> checks) {}
    public static Result calculate(List<Balance> accounts,List<Cash> cashLines,Parameters p) {
        var checks=new ArrayList<Check>();
        // The reset removed verified source-opening/reconciliation workflows. These numbers are recorded-ledger figures only.
        checks.add(new Check("coverage",null));
        if(accounts.isEmpty())checks.add(new Check("empty",null));
        if(accounts.stream().anyMatch(a->a.mapping()==null && (a.closing().signum()!=0||a.previousClosing().signum()!=0||a.movement().signum()!=0)))checks.add(new Check("mappings",null));
        var income=group(accounts,true);var position=group(accounts,false);
        BigDecimal profit=profit(accounts,false,false),previousProfit=profit(accounts,true,false);
        BigDecimal cumulative=profit(accounts,false,true),previousCumulative=profit(accounts,true,true);
        income.add(new Row("surplus","","fs.surplus",0,"TOTAL",List.of(),profit,previousProfit,true));
        position.add(new Row("earnings","","fs.untransferredEarnings",0,"EQUITY",List.of(),cumulative,previousCumulative,false));
        BigDecimal assets=sum(accounts,"ASSET",false),liabilities=sum(accounts,"LIABILITY",false).negate(),equity=sum(accounts,"EQUITY",false).negate().add(cumulative);
        BigDecimal previousAssets=sum(accounts,"ASSET",true),previousLiabilities=sum(accounts,"LIABILITY",true).negate(),previousEquity=sum(accounts,"EQUITY",true).negate().add(previousCumulative);
        position.add(new Row("assets","","fs.assetsTotal",0,"TOTAL",List.of(),assets,previousAssets,true));
        position.add(new Row("liabilitiesEquity","","fs.liabilitiesEquityTotal",0,"TOTAL",List.of(),liabilities.add(equity),previousLiabilities.add(previousEquity),true));
        difference(checks,"trial",accounts.stream().map(Balance::closing).reduce(ZERO,BigDecimal::add));
        difference(checks,"position",assets.subtract(liabilities).subtract(equity));
        difference(checks,"previousTrial",accounts.stream().map(Balance::previousClosing).reduce(ZERO,BigDecimal::add));
        var cash=cash(accounts,cashLines,p,checks);
        var columns=new TreeSet<String>();accounts.stream().filter(a->"EQUITY".equals(a.type())).forEach(a->columns.add(component(a)));
        columns.add("fs.untransferredEarnings");
        if(columns.size()>10)throw new IllegalArgumentException("fs.error.equityColumns");
        var opening=new LinkedHashMap<String,BigDecimal>();var movement=new LinkedHashMap<String,BigDecimal>();var closing=new LinkedHashMap<String,BigDecimal>();
        for(String column:columns){opening.put(column,ZERO);movement.put(column,ZERO);closing.put(column,ZERO);}
        for(var a:accounts)if("EQUITY".equals(a.type())){String c=component(a);opening.merge(c,a.opening().negate(),BigDecimal::add);movement.merge(c,a.movement().negate(),BigDecimal::add);closing.merge(c,a.closing().negate(),BigDecimal::add);}
        opening.put("fs.untransferredEarnings",cumulative.subtract(profit));closing.put("fs.untransferredEarnings",cumulative);
        var surplus=new LinkedHashMap<String,BigDecimal>();for(String c:columns)surplus.put(c,ZERO);surplus.put("fs.untransferredEarnings",profit);
        var rows=List.of(equityRow("fs.opening",opening,true),equityRow("fs.recordedEquityMovements",movement,false),equityRow("fs.surplus",surplus,false),equityRow("fs.closing",closing,true));
        difference(checks,"equity",rows.get(0).total().add(rows.get(1).total()).add(rows.get(2).total()).subtract(rows.get(3).total()));
        return new Result(List.copyOf(position),List.copyOf(income),List.copyOf(cash),List.copyOf(columns),rows,List.copyOf(checks));
    }
    private static String component(Balance a){return "account:"+(a.mapping()!=null&&!a.mapping().equityComponent().isBlank()?a.mapping().equityComponent():a.name());}
    private static EquityRow equityRow(String label,Map<String,BigDecimal> values,boolean summary){return new EquityRow(label,Map.copyOf(values),values.values().stream().reduce(ZERO,BigDecimal::add),summary);}
    private static BigDecimal sum(List<Balance>a,String type,boolean previous){return a.stream().filter(b->type.equals(b.type())).map(b->previous?b.previousClosing():b.closing()).reduce(ZERO,BigDecimal::add);}
    private static BigDecimal profit(List<Balance>a,boolean previous,boolean closing){return a.stream().filter(b->Set.of("INCOME","EXPENSE").contains(b.type())).map(b->closing?(previous?b.previousClosing():b.closing()):(previous?b.previousMovement():b.movement())).reduce(ZERO,BigDecimal::add).negate();}
    private static ArrayList<Row> group(List<Balance> accounts,boolean income){
        var groups=new LinkedHashMap<String,List<Balance>>();
        for(var a:accounts)if(income==Set.of("INCOME","EXPENSE").contains(a.type())){
            String section=a.mapping()==null?(income?a.type():"UNCLASSIFIED"):a.mapping().section();
            String label=a.mapping()==null?a.name():a.mapping().line();int note=a.mapping()==null?0:a.mapping().note();
            groups.computeIfAbsent(section+"\u001f"+label+"\u001f"+note,k->new ArrayList<>()).add(a);
        }
        var result=new ArrayList<Row>();int n=0;
        for(var values:groups.values()){
            var first=values.getFirst();var m=first.mapping();String section=m==null?(income?first.type():"UNCLASSIFIED"):m.section();
            BigDecimal current=ZERO,previous=ZERO;
            for(var a:values){int sign=Set.of("LIABILITY","EQUITY","INCOME").contains(a.type())?-1:1;current=current.add((income?a.movement():a.closing()).multiply(BigDecimal.valueOf(sign)));previous=previous.add((income?a.previousMovement():a.previousClosing()).multiply(BigDecimal.valueOf(sign)));}
            result.add(new Row("line-"+(++n),values.size()==1?first.code():"",m==null?first.name():m.line(),m==null?0:m.note(),section,values.stream().map(Balance::id).toList(),current,previous,false));
        }
        return result;
    }
    private static List<Row> cash(List<Balance> accounts,List<Cash> lines,Parameters p,List<Check> checks){
        var byAccount=new HashMap<UUID,Balance>();accounts.forEach(a->byAccount.put(a.id(),a));
        var journals=new LinkedHashMap<UUID,List<Cash>>();lines.forEach(l->journals.computeIfAbsent(l.journalId(),k->new ArrayList<>()).add(l));
        var values=new LinkedHashMap<String,BigDecimal[]>();var sources=new LinkedHashMap<String,Set<UUID>>();boolean ambiguous=false,unmapped=false;
        for(var journal:journals.values()){
            BigDecimal money=journal.stream().filter(Cash::money).map(Cash::signed).reduce(ZERO,BigDecimal::add);
            var others=journal.stream().filter(l->!l.money()&&l.signed().signum()!=0).toList();
            if(others.isEmpty())continue; // Transfers within cash equivalents never inflate any activity.
            if(money.signum()==0||others.stream().anyMatch(l->l.signed().signum()==money.signum())){ambiguous=true;continue;}
            if(others.stream().map(Cash::signed).reduce(money,BigDecimal::add).signum()!=0){ambiguous=true;continue;}
            for(var line:others){var account=byAccount.get(line.accountId());var m=account==null?null:account.mapping();
                String section=m==null?"UNCLASSIFIED":m.cashSection();if("UNCLASSIFIED".equals(section))unmapped=true;
                String label=m==null?(account==null?"fs.unclassified":account.name()):m.line();String key=section+"\u001f"+label;
                sources.computeIfAbsent(key,k->new LinkedHashSet<>()).add(line.accountId());
                var amount=values.computeIfAbsent(key,k->new BigDecimal[]{ZERO,ZERO});
                if(!line.date().isBefore(p.from())&&!line.date().isAfter(p.through()))amount[0]=amount[0].subtract(line.signed());
                if(!line.date().isBefore(p.previousFrom())&&!line.date().isAfter(p.previousThrough()))amount[1]=amount[1].subtract(line.signed());
            }
        }
        if(ambiguous)checks.add(new Check("cashAmbiguous",null));if(unmapped)checks.add(new Check("cashMappings",null));
        var result=new ArrayList<Row>();int n=0;
        for(var e:values.entrySet()){String[] key=e.getKey().split("\u001f",2);result.add(new Row("cash-"+(++n),"",key[1],0,key[0],List.copyOf(sources.get(e.getKey())),e.getValue()[0],e.getValue()[1],false));}
        BigDecimal movement=result.stream().map(Row::current).reduce(ZERO,BigDecimal::add),previousMovement=result.stream().map(Row::previous).reduce(ZERO,BigDecimal::add);
        BigDecimal opening=ZERO,closing=ZERO,previousOpening=ZERO,previousClosing=ZERO;
        for(var a:accounts)if(a.money()){opening=opening.add(a.opening());closing=closing.add(a.closing());previousOpening=previousOpening.add(a.previousOpening());previousClosing=previousClosing.add(a.previousClosing());}
        result.add(new Row("cashMovement","","fs.cashMovement",0,"TOTAL",List.of(),ambiguous?null:movement,ambiguous?null:previousMovement,true));
        result.add(new Row("cashOpening","","fs.cashOpening",0,"TOTAL",List.of(),opening,previousOpening,false));
        result.add(new Row("cashClosing","","fs.cashClosing",0,"TOTAL",List.of(),closing,previousClosing,true));
        difference(checks,"cash",opening.add(movement).subtract(closing));difference(checks,"previousCash",previousOpening.add(previousMovement).subtract(previousClosing));
        return result;
    }
    private static void difference(List<Check> checks,String key,BigDecimal value){if(value.signum()!=0)checks.add(new Check(key,value));}
}
