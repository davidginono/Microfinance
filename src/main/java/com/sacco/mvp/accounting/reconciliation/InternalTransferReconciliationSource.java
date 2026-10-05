package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Trusted owning-source projection; no controller-supplied proof and no foreign registry access. */
public interface InternalTransferReconciliationSource {
    enum Direction {OUT,IN}
    record Leg(UUID transferId,UUID documentId,UUID journalId,String branch,Direction direction,String currency,
        BigDecimal amount,LocalDate effectiveDate,OffsetDateTime postedAt,UUID maker,UUID checker,String sourceDigest,
        UUID moneyLineId,UUID moneyAccountId,BigDecimal signedMoneyAmount,UUID counterpartAccountId,
        BigDecimal signedCounterpartAmount,UUID policyId,int policyVersion,String destinationBranch,UUID relatedDocumentId) { }
    record Coverage(List<Leg> legs,List<UUID> unknownJournalIds,boolean complete) {
        public Coverage {legs=List.copyOf(legs);unknownJournalIds=List.copyOf(unknownJournalIds);}
    }
    /** Own branch only, cumulative from reviewed cutover through asOf, under the exact recorded cutoff. */
    Coverage reviewedTransfers(AppUserPrincipal actor,LocalDate cutoverDate,LocalDate asOf,OffsetDateTime recordedCutoff);
    /** Explicit institution permission and current scope; return only a minimal proof comparison. */
    default boolean currentForInstitutionClose(AppUserPrincipal actor,String branch,LocalDate cutoverDate,LocalDate asOf,
        OffsetDateTime recordedCutoff,Coverage frozen) {return false;}
}
