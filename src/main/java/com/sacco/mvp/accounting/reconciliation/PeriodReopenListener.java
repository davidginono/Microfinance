package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.security.AppUserPrincipal;
import java.util.UUID;
import java.time.LocalDate;

/** Owning downstream features invalidate period-wide publication eligibility in the reopening transaction. */
public interface PeriodReopenListener {
    /** Caller has checked current institution-wide reopening permission and locked its period. Implementations join this transaction. */
    void periodReopened(AppUserPrincipal actor,UUID period,String reason);
    /** Frozen cumulative publications through this date or later also need invalidation. */
    default void periodReopened(AppUserPrincipal actor,UUID period,LocalDate affectedFrom,String reason) {
        periodReopened(actor,period,reason);
    }
}
