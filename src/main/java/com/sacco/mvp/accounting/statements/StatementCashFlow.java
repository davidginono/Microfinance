package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.reports.CashFlowAllocation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;

/** Resolves each trusted frozen cash contribution exactly once into an approved activity placement. */
final class StatementCashFlow {
    record Resolved(Map<String,BigDecimal> rows,Map<UUID,BigDecimal> accounts,BigDecimal total,List<CashFlowAllocation.Version> versions) { }
    static Resolved resolve(StatementDefinition d,List<Map<String,Object>> automatic,CashFlowAllocation.Coverage coverage,OffsetDateTime cutoff) {
        require(d.kind()==Kind.CASH_FLOW && d.calculationVersion()==2,"version");require(automatic!=null && automatic.size()<=1000 && coverage!=null && coverage.versions()!=null && coverage.versions().size()<=1000 && coverage.missingJournalIds()!=null,"source");require(coverage.missingJournalIds().isEmpty(),"cashMissing");
        var amounts=new LinkedHashMap<String,BigDecimal>();var accounts=new HashMap<UUID,BigDecimal>();var placements=new HashMap<UUID,List<Row>>();
        for(var row:d.rows())if(row.kind()==RowKind.ACCOUNT_GROUP){amounts.put(row.id(),BigDecimal.ZERO.setScale(2));if(!row.memo())for(var id:row.accounts())placements.computeIfAbsent(id,k->new ArrayList<>()).add(row);}
        BigDecimal total=BigDecimal.ZERO;var automaticAccounts=new HashSet<UUID>();
        for(var movement:automatic){require(movement!=null && movement.get("account_id")!=null && "1".equals(movement.get("money_lines").toString()),"source");UUID account=UUID.fromString(movement.get("account_id").toString());require(automaticAccounts.add(account),"cashMapping");BigDecimal amount=money(movement.get("counterpart_movement")).negate();
            var primary=placements.getOrDefault(account,List.of()).stream().filter(r->r.exceptionEvidence()==null || r.exceptionEvidence().isBlank()).toList();require(primary.size()==1 && Set.of(Section.OPERATING,Section.INVESTING,Section.FINANCING).contains(primary.getFirst().section()),"cashMapping");add(amounts,accounts,primary.getFirst(),account,amount);total=total.add(amount);
        }
        var journals=new HashSet<UUID>();var versions=new HashSet<UUID>();
        for(var version:coverage.versions()){
            require(version!=null && version.id()!=null && versions.add(version.id()) && version.journalId()!=null && journals.add(version.journalId()) && version.version()>0 && version.maker()!=null && version.checker()!=null && !version.maker().equals(version.checker()) && version.reviewedAt()!=null && !version.reviewedAt().isAfter(cutoff) && version.sourceChecksum()!=null && version.sourceChecksum().matches("[0-9a-f]{64}") && version.definitionChecksum()!=null && version.definitionChecksum().matches("[0-9a-f]{64}") && version.source()!=null && version.journalId().equals(version.source().journalId()),"source");
            CashFlowAllocation.validate(version.source(),version.splits(),version.noncashEvidence());BigDecimal internal=BigDecimal.ZERO;
            for(var split:version.splits()){if(split.activity()==CashFlowAllocation.Activity.INTERNAL_TRANSFER){internal=internal.add(split.signedAmount());continue;}
                Section activity=Section.valueOf(split.activity().name());var matched=placements.getOrDefault(split.counterpartAccountId(),List.of()).stream().filter(r->r.section()==activity).toList();require(matched.size()==1,"cashMapping");BigDecimal amount=money(split.signedAmount());add(amounts,accounts,matched.getFirst(),split.counterpartAccountId(),amount);total=total.add(amount);
            }
            require(internal.signum()==0,"cashMapping");
        }
        for(var row:d.rows())if(row.memo()){BigDecimal amount=BigDecimal.ZERO;for(var id:row.accounts())amount=amount.add(accounts.getOrDefault(id,BigDecimal.ZERO));amounts.put(row.id(),amount);}
        return new Resolved(Collections.unmodifiableMap(amounts),Collections.unmodifiableMap(accounts),total,List.copyOf(coverage.versions()));
    }
    private static void add(Map<String,BigDecimal> rows,Map<UUID,BigDecimal> accounts,Row row,UUID account,BigDecimal amount){rows.merge(row.id(),amount,BigDecimal::add);accounts.merge(account,amount,BigDecimal::add);}
    private static BigDecimal money(Object value){require(value!=null,"source");try{return new BigDecimal(value.toString()).setScale(2,java.math.RoundingMode.UNNECESSARY);}catch(ArithmeticException|NumberFormatException e){throw new IllegalArgumentException("statement.error.source");}}
}
