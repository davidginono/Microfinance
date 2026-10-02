package com.sacco.mvp.accounting.reconciliation;

import com.sacco.mvp.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Business accounting supplies its own scoped historical controls; absence is unknown, never zero. */
public interface BusinessReconciliationSource {
    Optional<BigDecimal> historicalControlBalance(AppUserPrincipal actor,UUID account,LocalDate asOf,String purpose);
}
