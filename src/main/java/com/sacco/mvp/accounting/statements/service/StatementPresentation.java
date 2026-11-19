package com.sacco.mvp.accounting.statements.service;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

@Service @RequiredArgsConstructor
public class StatementPresentation {
    private final MessageSource messages;
    public record Column(String label,boolean numeric,float weight) {}
    public record Cell(String text,BigDecimal amount) {}
    public record DisplayRow(List<Cell> cells,boolean total,boolean heading,String details) {}
    public record Document(String title,String style,List<Column> columns,List<DisplayRow> rows,int detailsBefore,boolean asAt) {}
    private String title(Snapshot snapshot,Kind kind,Locale locale){
        String title=label("fs.title."+kind,locale);
        if(kind==Kind.EQUITY)return label("fs.equity.referenceTitle",locale);
        return kind==Kind.POSITION||kind==Kind.TRIAL?title+" "+snapshot.parameters().through().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu",locale)):title;
    }
    public String label(String text,Locale locale){return text!=null&&text.startsWith("fs.")?messages.getMessage(text,null,text,locale):text;}
    public Document document(Snapshot s,Kind kind,List<Balance> selected,Locale locale){
        var rows=new ArrayList<DisplayRow>();var columns=new ArrayList<Column>();String base="/reports/financial/statements/"+s.id();
        if(kind==Kind.TRIAL){
            for(String key:List.of("account","description","opening","debit","credit","netChange","closing"))columns.add(new Column(label("fs."+key,locale),!Set.of("account","description").contains(key),key.equals("description")?.26f:key.equals("account")?.12f:.124f));
            BigDecimal opening=BigDecimal.ZERO,debit=BigDecimal.ZERO,credit=BigDecimal.ZERO,closing=BigDecimal.ZERO;
            for(var b:selected){rows.add(row(false,false,s.parameters().institutionWide()?null:base+"/ledger/"+b.id(),b.code(),b.name(),b.opening(),b.debit(),b.credit(),b.movement(),b.closing()));opening=opening.add(b.opening());debit=debit.add(b.debit());credit=credit.add(b.credit());closing=closing.add(b.closing());}
            rows.add(row(true,false,null,label("fs.total",locale),"",opening,debit,credit,debit.subtract(credit),closing));
            return new Document(title(s,kind,locale),"trial",columns,rows,2,true);
        }
        if(kind==Kind.EQUITY){
            float descriptionWidth=.235f;
            columns.add(new Column("",false,descriptionWidth));float width=(1-descriptionWidth)/(s.equityColumns().size()+1);
            for(String c:s.equityColumns())columns.add(new Column(component(s,c,locale).replace(" / "," /\n"),true,width));columns.add(new Column(label("fs.equityTotal",locale),true,width));
            equityRows(s,s.equity(),s.parameters().from(),s.parameters().through(),rows,locale);
            if(s.version()>=3&&s.previousEquity()!=null){
                rows.add(period(s.parameters().previousFrom(),s.parameters().previousThrough(),columns.size(),locale));
                equityRows(s,s.previousEquity(),s.parameters().previousFrom(),s.parameters().previousThrough(),rows,locale);
            }
            return new Document(title(s,kind,locale),"equity",columns,rows,-1,false);
        }
        for(var c:List.of(new Column(label("fs.account",locale),false,.12f),new Column(label("fs.description",locale),false,.40f),new Column(label("fs.notes",locale),false,.08f),new Column(label("fs.current",locale),true,.20f),new Column(label("fs.previous",locale),true,.20f)))columns.add(c);
        if(kind==Kind.NOTES&&s.version()>=3&&s.setup()!=null&&s.setup().hasLayoutNotes()){
            columns.remove(2);columns.set(1,new Column(label("fs.description",locale),false,.48f));
            configuredNotes(s,rows,base,locale);
            return new Document(title(s,kind,locale),"statement",columns,rows,2,false);
        }else if(kind==Kind.NOTES){
            var notes=new TreeMap<Integer,List<Balance>>();for(var b:s.balances())if(b.mapping()!=null&&b.mapping().note()>0)notes.computeIfAbsent(b.mapping().note(),k->new ArrayList<>()).add(b);
            for(var entry:notes.entrySet()){
                rows.add(row(false,true,null,"",label("fs.note",locale)+" "+entry.getKey(),"","",""));
                BigDecimal current=BigDecimal.ZERO,previous=BigDecimal.ZERO;var texts=new LinkedHashSet<String>();
                for(var b:entry.getValue()){
                    int sign=Set.of("LIABILITY","EQUITY","INCOME").contains(b.type())?-1:1;boolean flow=Set.of("INCOME","EXPENSE").contains(b.type());
                    BigDecimal c=(flow?b.movement():b.closing()).multiply(BigDecimal.valueOf(sign)),p=(flow?b.previousMovement():b.previousClosing()).multiply(BigDecimal.valueOf(sign));
                    rows.add(row(false,false,s.parameters().institutionWide()?null:base+"/ledger/"+b.id(),b.code(),b.name(),"",c,p));current=current.add(c);previous=previous.add(p);if(!b.mapping().noteText().isBlank())texts.add(b.mapping().noteText());
                }
                for(String text:texts)for(int offset=0;offset<text.length();offset+=500)rows.add(row(false,false,null,"",text.substring(offset,Math.min(text.length(),offset+500)),"","",""));
                rows.add(row(true,false,null,"",label("fs.total",locale),"",current,previous));
            }
        }else{
            List<Row> source=switch(kind){case POSITION->s.position();case INCOME->s.income();case CASH_FLOW->s.cashFlow();default->throw new IllegalArgumentException("fs.error.selection");};
            List<String> sections=switch(kind){case POSITION->s.version()<2?List.of("NON_CURRENT_ASSETS","CURRENT_ASSETS","EQUITY","NON_CURRENT_LIABILITIES","CURRENT_LIABILITIES","UNCLASSIFIED","TOTAL"):List.of("CURRENT_ASSETS","NON_CURRENT_ASSETS","CURRENT_LIABILITIES","NON_CURRENT_LIABILITIES","EQUITY","UNCLASSIFIED","TOTAL");case INCOME->List.of("INCOME","EXPENSE","UNCLASSIFIED","TOTAL");default->List.of("OPERATING","INVESTING","FINANCING","UNCLASSIFIED","TOTAL");};
            for(String section:sections){
                if(kind==Kind.POSITION&&section.equals(s.version()<2?"EQUITY":"CURRENT_LIABILITIES")){var assets=source.stream().filter(r->r.key().equals("assets")).findFirst().orElseThrow();rows.add(row(true,false,null,"",label(assets.label(),locale),"",assets.current(),assets.previous()));}
                var members=source.stream().filter(r->section.equals(r.section())&&!(kind==Kind.POSITION&&r.key().equals("assets"))).toList();if(members.isEmpty() && !(s.version()>=2 && kind==Kind.INCOME && Set.of("INCOME","EXPENSE").contains(section)))continue;
                if(!"TOTAL".equals(section))rows.add(row(false,true,null,"",label("fs.section."+section,locale),"","",""));
                BigDecimal current=BigDecimal.ZERO,previous=BigDecimal.ZERO;
                for(var r:members){rows.add(row(r.total(),false,r.accounts().isEmpty()?null:base+"/trial?kind="+kind+"&line="+r.key(),r.code(),cashLabel(r,locale),r.note()==0?"":Integer.toString(r.note()),r.current()==null?label("fs.unavailable",locale):r.current(),r.previous()==null?label("fs.unavailable",locale):r.previous()));if(r.current()!=null)current=current.add(r.current());if(r.previous()!=null)previous=previous.add(r.previous());}
                if(!Set.of("TOTAL","UNCLASSIFIED").contains(section)){
                    boolean uncertain=kind==Kind.CASH_FLOW&&s.version()>=4&&s.checks().stream().anyMatch(c->c.key().equals("cashAmbiguous"));
                    String totalLabel=kind==Kind.CASH_FLOW&&s.version()>=4?"fs.cashTotal."+section:s.version()>=2&&kind==Kind.INCOME?(section.equals("INCOME")?"fs.incomeTotal":"fs.expensesTotal"):"fs.total";
                    rows.add(row(true,false,null,"",label(totalLabel,locale),"",uncertain?label("fs.unavailable",locale):current,uncertain?label("fs.unavailable",locale):previous));
                }
            }
        }
        return new Document(title(s,kind,locale),"statement",columns,rows,3,kind==Kind.POSITION);
    }
    private String cashLabel(Row row,Locale locale){
        String prefix=row.key().startsWith("cashAdjustment-")?"fs.cashAdjustment":row.key().startsWith("cashOperatingBalance-")?"fs.cashOperatingBalance":null;
        return prefix==null?label(row.label(),locale):label(prefix,locale)+" — "+label(row.label(),locale);
    }
    public String equityPeriod(Snapshot s,Locale locale){return s.parameters().through().format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-uuuu",locale)).toUpperCase(locale);}
    private static String equityDate(java.time.LocalDate date,Locale locale){return date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu",locale));}
    private void equityRows(Snapshot snapshot,List<EquityRow> source,java.time.LocalDate from,java.time.LocalDate through,List<DisplayRow> rows,Locale locale){
        for(var r:source){
            String name=r.customRow()!=null?local(r.customRow().name(),r.customRow().nameSw(),locale):switch(r.label()){
                case "fs.opening" -> label("fs.equity.balanceAt",locale)+" "+equityDate(from,locale);
                case "fs.closing" -> label("fs.equity.balanceAt",locale)+" "+equityDate(through,locale);
                case "fs.surplus" -> label("fs.equity.profit",locale);
                default -> movement(snapshot,r.label(),locale);
            };
            var cells=new ArrayList<Cell>();cells.add(cell(name));
            for(String column:snapshot.equityColumns())cells.add(equityAmount(r.values().get(column),locale));
            cells.add(equityAmount(r.total(),locale));
            rows.add(new DisplayRow(cells,r.label().equals("fs.closing"),false,null));
        }
    }
    private Cell equityAmount(BigDecimal amount,Locale locale){return amount==null?cell(label("fs.unavailable",locale)):amount.signum()==0?new Cell("",amount):cell(amount);}
    private String component(Snapshot s,String key,Locale locale){
        if(key.startsWith("component:")&&s.setup()!=null){var c=s.setup().components().get(Integer.parseInt(key.substring(10)));return local(c.name(),c.nameSw(),locale);}
        return key.startsWith("account:")?key.substring(8):label(key,locale);
    }
    private String movement(Snapshot s,String key,Locale locale){
        if(key.startsWith("fs.movement.")&&s.setup()!=null){String category=key.substring(12);for(var c:s.setup().categories())if(c.category().name().equals(category)&&!c.name().isBlank())return local(c.name(),c.nameSw(),locale)+" ("+label(key,locale)+")";}
        return label(key,locale);
    }
    private static String local(String en,String sw,Locale locale){return "sw".equals(locale.getLanguage())&&sw!=null&&!sw.isBlank()?sw:en;}
    private DisplayRow period(java.time.LocalDate from,java.time.LocalDate through,int size,Locale locale){var cells=new ArrayList<Cell>();cells.add(cell(label("fs.period",locale)+" "+from.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"))+" – "+through.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu"))));while(cells.size()<size)cells.add(cell(""));return new DisplayRow(cells,false,true,null);}
    private void configuredNotes(Snapshot s,List<DisplayRow> rows,String base,Locale locale){
        for(var note:s.setup().notes()){
            rows.add(row(false,true,null,"",label("fs.note",locale)+" "+note.number()+" — "+local(note.title(),note.titleSw(),locale),"",""));
            if(!note.basis().equals("NARRATIVE"))rows.add(row(false,false,null,"",label("fs.noteBasis."+note.basis(),locale),"",""));
            BigDecimal current=BigDecimal.ZERO,previous=BigDecimal.ZERO;var ids=new HashSet<>(note.accounts());
            for(var b:EquityStatementCalculator.incomeWithoutClosings(s.balances(),s.equityMovements()==null?List.of():s.equityMovements()))if(ids.contains(b.id())){int sign=Set.of("LIABILITY","EQUITY","INCOME").contains(b.type())?-1:1;boolean flow=note.basis().equals("MOVEMENT");BigDecimal c=(flow?b.movement():b.closing()).multiply(BigDecimal.valueOf(sign)),p=(flow?b.previousMovement():b.previousClosing()).multiply(BigDecimal.valueOf(sign));rows.add(row(false,false,s.parameters().institutionWide()?null:base+"/ledger/"+b.id(),b.code(),b.name(),c,p));current=current.add(c);previous=previous.add(p);}
            if(note.includeProfit()){
                BigDecimal c=BigDecimal.ZERO,p=BigDecimal.ZERO;for(var b:s.balances())if(Set.of("INCOME","EXPENSE").contains(b.type())){c=c.subtract(b.closing());p=p.subtract(b.previousClosing());}
                rows.add(row(false,false,null,"",label("fs.untransferredEarnings",locale),c,p));current=current.add(c);previous=previous.add(p);
            }
            if(!note.basis().equals("NARRATIVE"))rows.add(row(true,false,null,"",label("fs.total",locale),current,previous));
            String text=local(note.text(),note.textSw(),locale);for(int offset=0;offset<text.length();offset+=500)rows.add(row(false,false,null,"",text.substring(offset,Math.min(text.length(),offset+500)),"",""));
        }
    }
    public DisplayRow row(boolean total,boolean heading,String details,Object... values){return new DisplayRow(Arrays.stream(values).map(this::cell).toList(),total,heading,details);}
    public Document ledger(Snapshot s,Balance b,List<Activity> activity,Locale locale){
        var columns=List.of(new Column(label("fs.date",locale),false,.12f),new Column(label("fs.accountNumber",locale),false,.16f),new Column(label("fs.description",locale),false,.32f),new Column(label("fs.debit",locale),true,.13f),new Column(label("fs.credit",locale),true,.13f),new Column(label("fs.balance",locale),true,.14f));
        var rows=new ArrayList<DisplayRow>();rows.add(row(true,false,null,"","",label("fs.opening",locale),"","",b.opening()));
        for(var a:activity)rows.add(row(false,false,a.sourceUrl(),a.date(),a.accountNumber()==null||a.accountNumber().isBlank()?b.code():a.accountNumber(),a.description(),a.debit(),a.credit(),b.opening().add(a.running())));
        rows.add(row(true,false,null,"","",label("fs.periodTotal",locale),b.debit(),b.credit(),b.closing()));
        return new Document(label("fs.title.LEDGER",locale)+" — "+b.code()+" "+b.name(),"trial",columns,rows,-1,false);
    }
    private Cell cell(Object value){if(value instanceof BigDecimal amount){var f=new DecimalFormat("#,##0.00;(#,##0.00)",DecimalFormatSymbols.getInstance(Locale.UK));return new Cell(f.format(amount),amount);}return new Cell(value==null?"":com.sacco.mvp.service.ApplicationTimestamps.display(value),null);}
}
