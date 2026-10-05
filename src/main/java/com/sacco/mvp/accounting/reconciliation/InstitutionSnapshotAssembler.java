package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.BusinessReconciliationSource.HistoricalControlSnapshot;
import com.sacco.mvp.accounting.reconciliation.CashFlowReconciliationSource.*;
import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Combines trusted frozen financial projections, never foreign reconciliation registries. */
final class InstitutionSnapshotAssembler {
    private static final JsonMapper JSON=JsonMapper.builder()
        .enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .enable(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).findAndAddModules().build();
    record BranchProof(CloseReview review,List<HistoricalControlSnapshot> controls,Coverage cash) { }
    InstitutionSnapshot assemble(String institution,Period period,List<BranchProof> proofs,boolean restated) {
        require(!proofs.isEmpty()&&proofs.size()<=1000);
        var accounts=new TreeMap<String,Map<String,Object>>();var movements=new TreeMap<String,Map<String,Object>>();
        var branches=new ArrayList<BranchCloseSource>();var openings=new ArrayList<Map<String,Object>>();
        var controls=new ArrayList<HistoricalControlSnapshot>();var versions=new TreeMap<String,Version>();
        var missing=new TreeSet<UUID>(Comparator.comparing(UUID::toString));var policies=new TreeMap<String,Map<String,Object>>();
        var transfer=new LinkedHashMap<String,Object>();transfer.put("journal_count",0L);transfer.put("net_cash_movement",new BigDecimal("0.00"));
        transfer.put("ambiguous_journals",0L);transfer.put("noncash_pairs_possible",0L);
        Map<String,Object> approvedPolicy=null;long retainedDifferences=0;int openingCount=0,cashCount=0;var versionIds=new HashSet<UUID>();
        for(var proof:proofs.stream().sorted(Comparator.comparing(p->p.review().branch())).toList()) {
            var r=proof.review();require("APPROVED_CLOSE".equals(r.state())&&r.checker()!=null&&!r.checker().equals(r.maker())
                &&r.period().equals(period.id())&&ReconciliationService.sha(r.snapshot()).equals(r.checksum()));
            var data=object(JSON.readValue(r.snapshot(),Map.class));require(period.id().toString().equals(data.get("period"))
                &&period.from().toString().equals(data.get("from"))&&period.through().toString().equals(data.get("through"))
                &&data.containsKey("businessControlSources")&&data.containsKey("cashFlowAllocations")&&data.containsKey("automaticCashMovements"));
            branches.add(new BranchCloseSource(r.branch(),r.id(),r.version(),r.recordedAt().withOffsetSameInstant(ZoneOffset.UTC),r.checksum(),r.checker()));
            for(var row:rows(data.get("accounts")))mergeAccount(accounts,row);
            for(var row:rows(data.get("automaticCashMovements")))mergeMovement(movements,row);
            var policy=object(data.get("accountingPolicy"));if(approvedPolicy==null)approvedPolicy=policy;else require(approvedPolicy.equals(policy));
            for(var row:rows(data.get("sourcePolicyVersions")))policies.put(row.get("policy_id")+"/"+row.get("policy_version"),row);
            require(data.get("reviewedOpening") instanceof Map<?,?>);openings.add(Map.of("branch",r.branch(),"opening",object(data.get("reviewedOpening"))));
            controls.addAll(proof.controls());require(controls.size()<=1000);
            for(var control:proof.controls()) {openingCount+=control.reviewedOpenings().size();require(openingCount<=1000);}
            for(var version:proof.cash().versions()) {cashCount+=version.source().lines().size()+version.splits().size();require(cashCount<=10000&&versionIds.add(version.id())&&versions.put(version.journalId().toString(),version)==null);}
            for(var id:proof.cash().missingJournalIds())require(missing.add(id)&&!versions.containsKey(id.toString()));
            require(versions.size()+missing.size()<=1000);
            var t=object(data.get("cashTransfers"));
            for(String key:List.of("journal_count","ambiguous_journals","noncash_pairs_possible"))
                transfer.put(key,Math.addExact(number(transfer.get(key)),number(t.get(key))));
            transfer.put("net_cash_movement",decimal(transfer.get("net_cash_movement")).add(decimal(t.get("net_cash_movement"))));
            retainedDifferences=Math.addExact(retainedDifferences,rows(data.get("retainedDifferences")).size());
        }
        require(accounts.size()<=1000&&movements.size()<=1000&&policies.size()<=1000);
        for(var id:missing)require(!versions.containsKey(id.toString()));
        controls.sort(Comparator.comparing(s->s.branch()+"/"+s.account()));
        var out=new LinkedHashMap<String,Object>();out.put("schema",2);out.put("dimension","INSTITUTION");out.put("institution",institution);
        out.put("period",period.id().toString());out.put("from",period.from().toString());out.put("through",period.through().toString());
        out.put("branchSources",List.copyOf(branches));out.put("accounts",List.copyOf(accounts.values()));
        out.put("accountingPolicy",approvedPolicy);out.put("sourcePolicyVersions",List.copyOf(policies.values()));
        out.put("reviewedOpenings",List.copyOf(openings));out.put("businessControlSources",List.copyOf(controls));
        out.put("automaticCashMovements",List.copyOf(movements.values()));out.put("cashTransfers",transfer);
        out.put("cashFlowAllocations",new Coverage(List.copyOf(versions.values()),List.copyOf(missing)));
        out.put("restated",restated);out.put("retainedDifferenceCount",retainedDifferences);out.put("coverage","REVIEWED_DIFFERENCES_RETAINED");
        String snapshot=JSON.writeValueAsString(out);
        return new InstitutionSnapshot("INSTITUTION",institution,period.id(),period.from(),period.through(),
            ReconciliationService.sha(snapshot),snapshot,branches,true,restated);
    }
    private static void mergeAccount(Map<String,Map<String,Object>> result,Map<String,Object> row) {
        String id=text(row.get("id"));UUID.fromString(id);
        var prior=result.get(id);var normalized=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","code","type","normal_balance","purpose")) {normalized.put(key,text(row.get(key)));if(prior!=null)require(prior.get(key).equals(normalized.get(key)));}
        for(String key:List.of("opening","period_debit","period_credit","closing"))normalized.put(key,decimal(row.get(key)));
        require(decimal(row.get("opening")).add(decimal(row.get("period_debit"))).subtract(decimal(row.get("period_credit"))).compareTo(decimal(row.get("closing")))==0);
        if(prior!=null)for(String key:List.of("opening","period_debit","period_credit","closing"))normalized.put(key,decimal(prior.get(key)).add(decimal(row.get(key))));
        result.put(id,normalized);
    }
    private static void mergeMovement(Map<String,Map<String,Object>> result,Map<String,Object> row) {
        String id=text(row.get("account_id"));UUID.fromString(id);require(number(row.get("money_lines"))==1);
        var prior=result.get(id);var normalized=new LinkedHashMap<String,Object>();
        for(String key:List.of("account_id","code","type","purpose")){normalized.put(key,text(row.get(key)));if(prior!=null)require(prior.get(key).equals(normalized.get(key)));}
        normalized.put("counterpart_movement",decimal(row.get("counterpart_movement")).add(prior==null?BigDecimal.ZERO:decimal(prior.get("counterpart_movement"))));
        normalized.put("journal_count",Math.addExact(number(row.get("journal_count")),prior==null?0:number(prior.get("journal_count"))));
        normalized.put("money_lines",1);result.put(id,normalized);
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> object(Object value){require(value instanceof Map<?,?>);return (Map<String,Object>)value;}
    private static List<Map<String,Object>> rows(Object value){require(value instanceof List<?>);var list=(List<?>)value;require(list.size()<=1000);return list.stream().map(InstitutionSnapshotAssembler::object).toList();}
    private static String text(Object value){require(value instanceof String&&!((String)value).isBlank());return (String)value;}
    private static BigDecimal decimal(Object value){require(value instanceof Number);var n=new BigDecimal(value.toString());require(n.scale()<=2);return n.setScale(2);}
    private static long number(Object value){require(value instanceof Number);var n=new BigDecimal(value.toString()).longValueExact();require(n>=0);return n;}
    private static void require(boolean value){if(!value)throw new IllegalArgumentException("reconciliation.error.institutionSource");}
}
