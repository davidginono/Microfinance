package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.reconciliation.CashFlowReconciliationSource;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

/** Closing consumes a leaf proof; it never acquires report-registry or foreign-branch identities. */
@Component
@RequiredArgsConstructor
public class CashFlowClosingAdapter implements CashFlowReconciliationSource {
    private final CashFlowAllocationService allocations;
    @Override public Coverage reviewedAllocations(AppUserPrincipal actor,List<UUID> journals,OffsetDateTime cutoff) {
        return toPort(allocations.reviewedForClosing(actor,journals,cutoff));
    }
    @Override public boolean allocationCoverageCurrentForInstitutionClose(AppUserPrincipal actor,String institution,String branch,
            LocalDate from,LocalDate through,OffsetDateTime cutoff,Coverage frozen) {
        CashFlowAllocation.Coverage retained;
        try{retained=fromPort(frozen);}catch(IllegalArgumentException|NullPointerException failure){return false;}
        return allocations.currentForInstitutionClosingProof(actor,institution,branch,from,through,cutoff,retained);
    }
    static Coverage toPort(CashFlowAllocation.Coverage proof) {
        return new Coverage(proof.versions().stream().map(v->new Version(v.id(),v.journalId(),v.version(),v.maker(),v.madeAt(),v.evidence(),v.noncashEvidence(),v.sourceChecksum(),v.definitionChecksum(),
            new Source(v.source().journalId(),v.source().policyId(),v.source().policyVersion(),v.source().sourceReference(),v.source().lines().stream().map(l->new SourceLine(l.id(),l.accountId(),l.code(),l.type(),l.purpose(),l.signedAmount())).toList()),
            v.splits().stream().map(s->new Split(s.moneyLineId(),s.counterpartAccountId(),s.activity().name(),s.signedAmount())).toList(),v.checker(),v.reviewedAt(),v.reviewEvidence())).toList(),proof.missingJournalIds());
    }
    static CashFlowAllocation.Coverage fromPort(Coverage proof) {
        if(proof==null || proof.versions()==null || proof.missingJournalIds()==null || proof.versions().size()>1000 || proof.missingJournalIds().size()>1000)throw new IllegalArgumentException("Invalid retained coverage");
        return new CashFlowAllocation.Coverage(proof.versions().stream().map(v->new CashFlowAllocation.Version(v.id(),v.journalId(),v.version(),v.maker(),v.madeAt(),v.evidence(),v.noncashEvidence(),v.sourceChecksum(),v.definitionChecksum(),
            new CashFlowAllocation.Source(v.source().journalId(),v.source().policyId(),v.source().policyVersion(),v.source().sourceReference(),v.source().lines().stream().map(l->new CashFlowAllocation.SourceLine(l.id(),l.accountId(),l.code(),l.type(),l.purpose(),l.signedAmount())).toList()),
            v.splits().stream().map(s->new CashFlowAllocation.Split(s.moneyLineId(),s.counterpartAccountId(),CashFlowAllocation.Activity.valueOf(s.activity()),s.signedAmount())).toList(),v.checker(),v.reviewedAt(),v.reviewEvidence())).toList(),proof.missingJournalIds());
    }
}
