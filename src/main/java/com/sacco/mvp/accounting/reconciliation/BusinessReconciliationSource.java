package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Source-owned historical controls. Absence, including an unreviewed empty history, is unknown. */
public interface BusinessReconciliationSource {
    Optional<BigDecimal> historicalControlBalance(AppUserPrincipal actor,UUID account,LocalDate asOf,String purpose);

    /** Must recheck current scope/claims, authenticate immutable reviewed coverage and apply both effective and recorded cutoffs. */
    default Optional<HistoricalControlSnapshot> historicalControlSnapshot(AppUserPrincipal actor,UUID account,
            LocalDate asOf,String purpose,OffsetDateTime recordedCutoff) {return Optional.empty();}

    /** Minimal internal proof check; another branch requires current explicit institution closing authorization. No registry rows escape. */
    default boolean historicalControlSnapshotCurrentForInstitutionClose(AppUserPrincipal actor,HistoricalControlSnapshot frozen) {return false;}

    record SourceOpeningEvidence(UUID id,UUID generalLedgerOpeningId,UUID account,LocalDate through,UUID policy,
            int policyVersion,UUID maker,UUID reviewer,String sourceEvidence,String reviewEvidence,String payloadChecksum,
            OffsetDateTime reviewedAt,boolean completeCoverage,boolean reviewedZero) { }

    record HistoricalControlSnapshot(String institution,String branch,UUID account,LocalDate asOf,String purpose,
            OffsetDateTime recordedCutoff,BigDecimal signedBalance,List<SourceOpeningEvidence> reviewedOpenings,
            long movementCount,String movementDigest,OffsetDateTime latestRecordedAt,boolean completeCoverage) {
        public HistoricalControlSnapshot {if(reviewedOpenings!=null)reviewedOpenings=List.copyOf(reviewedOpenings);}
    }
}
