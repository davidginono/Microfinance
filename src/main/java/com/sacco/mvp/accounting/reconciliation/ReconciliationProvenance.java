package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.accounting.reconciliation.BusinessReconciliationSource.*;
import com.sacco.mvp.accounting.reconciliation.CashFlowReconciliationSource.*;
import com.sacco.mvp.accounting.reconciliation.ReconciliationDtos.*;
import com.sacco.mvp.security.AppUserPrincipal;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Validates source-owned immutable evidence. No browser-provided balance or completeness flag reaches this boundary. */
final class ReconciliationProvenance {
    private static final Set<String> MONEY=Set.of("CASH","BANK","MOBILE_MONEY");
    private static final Set<String> ACTIVITIES=Set.of("OPERATING","INVESTING","FINANCING","INTERNAL_TRANSFER");
    private static final JsonMapper JSON=JsonMapper.builder().enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).findAndAddModules().build();
    private final ReconciliationRepository repo;
    private final Optional<BusinessReconciliationSource> controls;
    private final Optional<CashFlowReconciliationSource> cash;
    ReconciliationProvenance(ReconciliationRepository repo,Optional<BusinessReconciliationSource> controls,Optional<CashFlowReconciliationSource> cash) {
        this.repo=repo;this.controls=controls;this.cash=cash;
    }

    HistoricalControlSnapshot control(AppUserPrincipal actor,UUID account,LocalDate date,String purpose,OffsetDateTime cutoff) {
        var result=controls.flatMap(s->s.historicalControlSnapshot(actor,account,date,purpose,cutoff)).orElseThrow(()->error("controlUnavailable"));
        return validateControl(actor.getSaccoId(),actor.getStationId(),account,date,purpose,cutoff,result);
    }

    List<HistoricalControlSnapshot> prepareControls(AppUserPrincipal actor,Period period,OffsetDateTime cutoff) {
        var accounts=repo.controlAccounts(actor.getSaccoId(),actor.getStationId(),period.through(),cutoff);
        require(accounts.size()<=1000,"snapshotSize");var result=new ArrayList<HistoricalControlSnapshot>();int proofs=0;
        for(var account:accounts) {
            var item=control(actor,(UUID)account.get("id"),period.through(),(String)account.get("purpose"),cutoff);
            require(item.signedBalance().compareTo((BigDecimal)account.get("balance"))==0,"controlBalance");
            proofs+=item.reviewedOpenings().size();require(proofs<=1000,"snapshotSize");result.add(item);
        }
        return List.copyOf(result);
    }

    List<HistoricalControlSnapshot> retainedControls(AppUserPrincipal actor,String branch,Period period,CloseReview review,OffsetDateTime now,boolean global) {
        var frozen=readControls(review.snapshot());var accounts=repo.controlAccounts(actor.getSaccoId(),branch,period.through(),review.recordedAt());
        require(accounts.size()<=1000 && frozen.size()==accounts.size(),"controlUnavailable");
        var seen=new HashSet<UUID>();var validated=new ArrayList<HistoricalControlSnapshot>();int proofs=0;
        for(var item:frozen) {
            require(item!=null && seen.add(item.account()),"controlUnavailable");
            UUID retainedAccount=item.account();var account=accounts.stream().filter(a->a.get("id").equals(retainedAccount)).findFirst().orElseThrow(()->error("controlUnavailable"));
            item=validateControl(actor.getSaccoId(),branch,item.account(),period.through(),(String)account.get("purpose"),review.recordedAt(),item);
            proofs+=item.reviewedOpenings().size();require(proofs<=1000,"snapshotSize");
            require(item.signedBalance().compareTo((BigDecimal)account.get("balance"))==0,"controlBalance");
            var retained=item;
            if(global && !branch.equals(actor.getStationId())) {
                require(controls.map(p->p.historicalControlSnapshotCurrentForInstitutionClose(actor,retained)).orElse(false),"staleEvidence");
            } else {
                var current=control(actor,item.account(),period.through(),item.purpose(),now);
                require(sameControl(item,current),"staleEvidence");
            }
            validated.add(item);
        }
        return validated.stream().sorted(Comparator.comparing(v->v.account().toString())).toList();
    }

    private HistoricalControlSnapshot validateControl(String institution,String branch,UUID account,LocalDate date,String purpose,OffsetDateTime cutoff,HistoricalControlSnapshot s) {
        require(s!=null && s.completeCoverage() && institution.equals(s.institution()) && branch.equals(s.branch())
            && account.equals(s.account()) && date.equals(s.asOf()) && purpose.equals(s.purpose())
            && s.recordedCutoff()!=null && s.recordedCutoff().isEqual(cutoff),"controlUnavailable");
        ReconciliationService.money(s.signedBalance(),true);
        require(s.reviewedOpenings()!=null&&!s.reviewedOpenings().isEmpty()&&s.reviewedOpenings().size()<=1000
            && s.movementCount()>=0 && hash(s.movementDigest()),"controlUnavailable");
        require(s.movementCount()==0?s.latestRecordedAt()==null:s.latestRecordedAt()!=null&&!s.latestRecordedAt().isAfter(cutoff),"controlUnavailable");
        var opening=repo.opening(institution,branch).orElseThrow(()->error("controlUnavailable"));
        require(!opening.postedAt().isAfter(cutoff),"controlUnavailable");var ids=new HashSet<UUID>();var normalized=new ArrayList<SourceOpeningEvidence>();
        for(var e:s.reviewedOpenings()) {
            require(e!=null&&e.id()!=null&&ids.add(e.id())&&e.completeCoverage()&&account.equals(e.account())
                && opening.journal().equals(e.generalLedgerOpeningId())&&opening.through().equals(e.through())&&!date.isBefore(e.through())
                && e.policy()!=null&&e.policyVersion()>0&&repo.approvedSourcePolicy(institution,e.policy(),e.policyVersion())
                && e.maker()!=null&&e.reviewer()!=null&&!e.maker().equals(e.reviewer())&&hash(e.payloadChecksum())
                && e.reviewedAt()!=null&&!e.reviewedAt().isAfter(cutoff)&&!e.reviewedAt().isBefore(opening.postedAt()),"controlUnavailable");
            ReconciliationService.text(e.sourceEvidence(),1000);ReconciliationService.text(e.reviewEvidence(),1000);
            normalized.add(new SourceOpeningEvidence(e.id(),e.generalLedgerOpeningId(),e.account(),e.through(),e.policy(),e.policyVersion(),
                e.maker(),e.reviewer(),e.sourceEvidence(),e.reviewEvidence(),e.payloadChecksum(),utc(e.reviewedAt()),true,e.reviewedZero()));
        }
        normalized.sort(Comparator.comparing(v->v.id().toString()));
        return new HistoricalControlSnapshot(institution,branch,account,date,purpose,utc(cutoff),s.signedBalance().setScale(2),normalized,
            s.movementCount(),s.movementDigest(),utc(s.latestRecordedAt()),true);
    }

    private static boolean sameControl(HistoricalControlSnapshot a,HistoricalControlSnapshot b) {
        return a.institution().equals(b.institution())&&a.branch().equals(b.branch())&&a.account().equals(b.account())&&a.asOf().equals(b.asOf())
            &&a.purpose().equals(b.purpose())&&a.signedBalance().compareTo(b.signedBalance())==0&&a.reviewedOpenings().equals(b.reviewedOpenings())
            &&a.movementCount()==b.movementCount()&&a.movementDigest().equals(b.movementDigest())&&Objects.equals(a.latestRecordedAt(),b.latestRecordedAt());
    }

    Coverage prepareCash(AppUserPrincipal actor,Period period,OffsetDateTime cutoff) {
        var ids=repo.ambiguousCashJournals(actor.getSaccoId(),actor.getStationId(),period.from(),period.through(),cutoff);
        require(ids.size()<=1000,"snapshotSize");
        Coverage proof=ids.isEmpty()?new Coverage(List.of(),List.of()):cash.map(p->p.reviewedAllocations(actor,ids,cutoff)).orElseGet(()->new Coverage(List.of(),ids));
        return validateCash(ids,cutoff,proof);
    }

    Coverage retainedCash(AppUserPrincipal actor,String branch,Period period,CloseReview review,OffsetDateTime now,boolean global) {
        var ids=repo.ambiguousCashJournals(actor.getSaccoId(),branch,period.from(),period.through(),review.recordedAt());
        Coverage frozen=readCash(review.snapshot(),ids);frozen=validateCash(ids,review.recordedAt(),frozen);
        if(global&&!branch.equals(actor.getStationId())) {
            if(!ids.isEmpty()) {var retained=frozen;require(cash.map(p->p.allocationCoverageCurrentForInstitutionClose(actor,actor.getSaccoId(),branch,period.from(),period.through(),review.recordedAt(),retained)).orElse(new HashSet<>(ids).equals(new HashSet<>(frozen.missingJournalIds()))&&frozen.versions().isEmpty()),"staleEvidence");}
        } else {
            Coverage current=prepareCash(actor,period,now);require(current.equals(frozen),"staleEvidence");
        }
        return frozen;
    }

    private Coverage validateCash(List<UUID> ids,OffsetDateTime cutoff,Coverage proof) {
        require(proof!=null&&proof.versions()!=null&&proof.missingJournalIds()!=null&&proof.versions().size()<=1000&&proof.missingJournalIds().size()<=1000,"snapshotSize");
        var requested=new HashSet<>(ids);var included=new HashSet<UUID>();var normalized=new ArrayList<Version>();int size=0;
        for(var v:proof.versions()) {
            require(v!=null&&v.id()!=null&&v.journalId()!=null&&requested.contains(v.journalId())&&included.add(v.journalId())&&v.version()>0
                &&v.maker()!=null&&v.checker()!=null&&!v.maker().equals(v.checker())&&v.madeAt()!=null&&v.reviewedAt()!=null
                &&!v.madeAt().isAfter(v.reviewedAt())&&!v.reviewedAt().isAfter(cutoff)&&hash(v.sourceChecksum())&&hash(v.definitionChecksum()),"cashFlowEvidence");
            ReconciliationService.text(v.evidence(),1000);ReconciliationService.text(v.reviewEvidence(),1000);
            if(v.noncashEvidence()!=null&&!v.noncashEvidence().isBlank())ReconciliationService.text(v.noncashEvidence(),2000);
            var s=v.source();require(s!=null&&v.journalId().equals(s.journalId())&&s.policyId()!=null&&s.policyVersion()>0&&s.lines()!=null&&s.lines().size()>=2&&s.lines().size()<=500,"cashFlowEvidence");
            ReconciliationService.text(s.sourceReference(),160);size+=s.lines().size();require(size<=10000,"snapshotSize");
            var lines=new HashMap<UUID,SourceLine>();var normalizedLines=new ArrayList<SourceLine>();BigDecimal journal=BigDecimal.ZERO;
            for(var l:s.lines()) {
                require(l!=null&&l.id()!=null&&l.accountId()!=null&&lines.put(l.id(),l)==null&&l.signedAmount()!=null&&l.signedAmount().signum()!=0&&l.purpose()!=null,"cashFlowEvidence");
                ReconciliationService.money(l.signedAmount(),true);ReconciliationService.text(l.code(),40);ReconciliationService.text(l.type(),20);
                journal=journal.add(l.signedAmount());normalizedLines.add(new SourceLine(l.id(),l.accountId(),l.code(),l.type(),l.purpose(),l.signedAmount().setScale(2)));
            }
            require(journal.signum()==0&&v.splits()!=null&&!v.splits().isEmpty()&&v.splits().size()<=1000,"cashFlowEvidence");
            size+=v.splits().size();require(size<=10000,"snapshotSize");var totals=new HashMap<UUID,BigDecimal>();var parts=new HashSet<String>();var splits=new ArrayList<Split>();
            for(var part:v.splits()) {
                var money=part==null?null:lines.get(part.moneyLineId());
                require(part!=null&&money!=null&&MONEY.contains(money.purpose())&&part.counterpartAccountId()!=null&&ACTIVITIES.contains(part.activity())
                    &&part.signedAmount()!=null&&part.signedAmount().signum()==money.signedAmount().signum()
                    &&parts.add(part.moneyLineId()+"/"+part.counterpartAccountId()+"/"+part.activity()),"cashFlowEvidence");
                ReconciliationService.money(part.signedAmount(),true);
                require(s.lines().stream().anyMatch(l->l.accountId().equals(part.counterpartAccountId())
                    &&("INTERNAL_TRANSFER".equals(part.activity())?MONEY.contains(l.purpose()):!MONEY.contains(l.purpose()))),"cashFlowEvidence");
                totals.merge(part.moneyLineId(),part.signedAmount(),BigDecimal::add);splits.add(new Split(part.moneyLineId(),part.counterpartAccountId(),part.activity(),part.signedAmount().setScale(2)));
            }
            for(var l:s.lines())if(MONEY.contains(l.purpose()))require(l.signedAmount().compareTo(totals.getOrDefault(l.id(),BigDecimal.ZERO))==0,"cashFlowEvidence");
            normalizedLines.sort(Comparator.comparing(l->l.id().toString()));splits.sort(Comparator.comparing(l->l.moneyLineId()+"/"+l.counterpartAccountId()+"/"+l.activity()));
            normalized.add(new Version(v.id(),v.journalId(),v.version(),v.maker(),utc(v.madeAt()),v.evidence(),v.noncashEvidence(),v.sourceChecksum(),v.definitionChecksum(),
                new Source(s.journalId(),s.policyId(),s.policyVersion(),s.sourceReference(),normalizedLines),splits,v.checker(),utc(v.reviewedAt()),v.reviewEvidence()));
        }
        var missing=new ArrayList<UUID>();
        for(var id:proof.missingJournalIds()){require(id!=null&&requested.contains(id)&&included.add(id),"cashFlowEvidence");missing.add(id);}
        require(included.equals(requested),"cashFlowEvidence");normalized.sort(Comparator.comparing(v->v.journalId().toString()));missing.sort(Comparator.comparing(UUID::toString));
        return new Coverage(normalized,missing);
    }

    private static List<HistoricalControlSnapshot> readControls(String json) {
        var node=JSON.readTree(json).get("businessControlSources");return node==null?List.of():Arrays.asList(JSON.readValue(node.toString(),HistoricalControlSnapshot[].class));
    }
    private static Coverage readCash(String json,List<UUID> ids) {
        var node=JSON.readTree(json).get("cashFlowAllocations");return node==null?new Coverage(List.of(),ids):JSON.readValue(node.toString(),Coverage.class);
    }
    private static OffsetDateTime utc(OffsetDateTime value){return value==null?null:value.withOffsetSameInstant(ZoneOffset.UTC);}
    private static boolean hash(String value){return value!=null&&value.matches("[0-9a-f]{64}");}
    private static void require(boolean condition,String key){if(!condition)throw error(key);}
    private static IllegalArgumentException error(String key){return new IllegalArgumentException("reconciliation.error."+key);}
}
