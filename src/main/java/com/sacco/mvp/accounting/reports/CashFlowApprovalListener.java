package com.sacco.mvp.accounting.reports;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Repository-only consumers invalidate older live releases in the approving transaction. */
@FunctionalInterface
public interface CashFlowApprovalListener {
    void approved(String institution, UUID journal, UUID allocationId, int version, UUID reviewer, OffsetDateTime at);
}
