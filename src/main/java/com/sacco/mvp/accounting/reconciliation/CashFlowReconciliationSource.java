package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Leaf boundary for independently reviewed allocations; no report or closing service dependency. */
public interface CashFlowReconciliationSource {
    Coverage reviewedAllocations(AppUserPrincipal actor,List<UUID> journals,OffsetDateTime recordedCutoff);
    default boolean allocationCoverageCurrentForInstitutionClose(AppUserPrincipal actor,String institution,String branch,
            LocalDate from,LocalDate through,OffsetDateTime recordedCutoff,Coverage frozen) {return false;}
    record Coverage(List<Version> versions,List<UUID> missingJournalIds) {
        public Coverage {if(versions!=null)versions=List.copyOf(versions);if(missingJournalIds!=null)missingJournalIds=List.copyOf(missingJournalIds);}
    }
    record Version(UUID id,UUID journalId,int version,UUID maker,OffsetDateTime madeAt,String evidence,String noncashEvidence,
            String sourceChecksum,String definitionChecksum,Source source,List<Split> splits,UUID checker,
            OffsetDateTime reviewedAt,String reviewEvidence) {
        public Version {if(splits!=null)splits=List.copyOf(splits);}
    }
    record Source(UUID journalId,UUID policyId,int policyVersion,String sourceReference,List<SourceLine> lines) {
        public Source {if(lines!=null)lines=List.copyOf(lines);}
    }
    record SourceLine(UUID id,UUID accountId,String code,String type,String purpose,BigDecimal signedAmount) { }
    record Split(UUID moneyLineId,UUID counterpartAccountId,String activity,BigDecimal signedAmount) { }
}
