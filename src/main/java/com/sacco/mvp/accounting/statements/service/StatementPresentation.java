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
            columns.add(new Column(label("fs.description",locale),false,.28f));float width=.72f/(s.equityColumns().size()+1);
            for(String c:s.equityColumns())columns.add(new Column(c.startsWith("account:")?c.substring(8):label(c,locale),true,width));columns.add(new Column(label("fs.equityTotal",locale),true,width));
            for(var r:s.equity()){var cells=new ArrayList<Cell>();cells.add(cell(label(r.label(),locale)));for(String c:s.equityColumns())cells.add(cell(r.values().get(c)));cells.add(cell(r.total()));rows.add(new DisplayRow(cells,r.summary(),false,null));}
            return new Document(title(s,kind,locale),"equity",columns,rows,-1,false);
        }
        for(var c:List.of(new Column(label("fs.account",locale),false,.12f),new Column(label("fs.description",locale),false,.40f),new Column(label("fs.notes",locale),false,.08f),new Column(label("fs.current",locale),true,.20f),new Column(label("fs.previous",locale),true,.20f)))columns.add(c);
        if(kind==Kind.NOTES){
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
            List<String> sections=switch(kind){case POSITION->List.of("NON_CURRENT_ASSETS","CURRENT_ASSETS","EQUITY","NON_CURRENT_LIABILITIES","CURRENT_LIABILITIES","UNCLASSIFIED","TOTAL");case INCOME->List.of("INCOME","EXPENSE","UNCLASSIFIED","TOTAL");default->List.of("OPERATING","INVESTING","FINANCING","UNCLASSIFIED","TOTAL");};
            for(String section:sections){
                if(kind==Kind.POSITION&&section.equals("EQUITY")){var assets=source.stream().filter(r->r.key().equals("assets")).findFirst().orElseThrow();rows.add(row(true,false,null,"",label(assets.label(),locale),"",assets.current(),assets.previous()));}
                var members=source.stream().filter(r->section.equals(r.section())&&!(kind==Kind.POSITION&&r.key().equals("assets"))).toList();if(members.isEmpty())continue;
                if(!"TOTAL".equals(section))rows.add(row(false,true,null,"",label("fs.section."+section,locale),"","",""));
                BigDecimal current=BigDecimal.ZERO,previous=BigDecimal.ZERO;
                for(var r:members){rows.add(row(r.total(),false,r.accounts().isEmpty()?null:base+"/trial?kind="+kind+"&line="+r.key(),r.code(),r.accounts().isEmpty()?label(r.label(),locale):r.label(),r.note()==0?"":Integer.toString(r.note()),r.current()==null?label("fs.unavailable",locale):r.current(),r.previous()==null?label("fs.unavailable",locale):r.previous()));if(r.current()!=null)current=current.add(r.current());if(r.previous()!=null)previous=previous.add(r.previous());}
                if(!Set.of("TOTAL","UNCLASSIFIED").contains(section))rows.add(row(true,false,null,"",label("fs.total",locale),"",current,previous));
            }
        }
        return new Document(title(s,kind,locale),"statement",columns,rows,3,kind==Kind.POSITION);
    }
    public DisplayRow row(boolean total,boolean heading,String details,Object... values){return new DisplayRow(Arrays.stream(values).map(this::cell).toList(),total,heading,details);}
    public Document ledger(Snapshot s,Balance b,List<Activity> activity,Locale locale){
        var columns=List.of(new Column(label("fs.date",locale),false,.12f),new Column(label("fs.accountNumber",locale),false,.16f),new Column(label("fs.description",locale),false,.32f),new Column(label("fs.debit",locale),true,.13f),new Column(label("fs.credit",locale),true,.13f),new Column(label("fs.balance",locale),true,.14f));
        var rows=new ArrayList<DisplayRow>();rows.add(row(true,false,null,"","",label("fs.opening",locale),"","",b.opening()));
        for(var a:activity)rows.add(row(false,false,a.sourceUrl(),a.date(),a.accountNumber()==null||a.accountNumber().isBlank()?label("fs.unavailable",locale):a.accountNumber(),a.description(),a.debit(),a.credit(),b.opening().add(a.running())));
        rows.add(row(true,false,null,"","",label("fs.periodTotal",locale),b.debit(),b.credit(),b.closing()));
        return new Document(label("fs.title.LEDGER",locale)+" — "+b.code()+" "+b.name(),"trial",columns,rows,-1,false);
    }
    private Cell cell(Object value){if(value instanceof BigDecimal amount){var f=new DecimalFormat("#,##0.00;(#,##0.00)",DecimalFormatSymbols.getInstance(Locale.UK));return new Cell(f.format(amount),amount);}return new Cell(value==null?"":com.sacco.mvp.service.ApplicationTimestamps.display(value),null);}
}
