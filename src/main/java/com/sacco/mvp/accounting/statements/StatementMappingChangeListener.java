package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.security.AppUserPrincipal;
import java.util.UUID;

/** Transactional notification to independent release-evidence owners. */
public interface StatementMappingChangeListener {
    void mappingChanged(AppUserPrincipal actor, UUID templateId, UUID versionId, String reason);
}
