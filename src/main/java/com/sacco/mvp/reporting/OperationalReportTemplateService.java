package com.sacco.mvp.reporting;

import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OperationalReportTemplateService {
    private final OperationalReportTemplateRepository repository;
    private final OperationalReportService reports;
    private final AccessControlService access;
    private final ApplicationClock clock;
    private final AuditService audit;
    public record Version(UUID id, UUID templateId, int version, String state, String visibility,
            UUID madeBy, UUID checkedBy, OperationalReportDefinition definition) { }

    @Transactional
    public UUID save(OperationalReportDefinition definition, UUID templateId, boolean shared, AppUserPrincipal actor) {
        definition.validate();
        reports.authorize(actor, definition.dataset(), UserClaim.REPORT_TEMPLATE_DESIGN);
        actor=reports.currentActor(actor);
        if (shared && !access.has(actor, UserClaim.REPORT_TEMPLATE_SHARE)) throw new AccessDeniedException("Sharing requires a separate permission");
        int version = 1;
        if (templateId == null) {
            templateId = UUID.randomUUID();
            repository.create(templateId,actor.getSaccoId(),actor.getMemberId(),clock.now());
        } else {
            List<UUID> owners=repository.lockOwners(templateId,actor.getSaccoId());
            if (owners.size() != 1 || !owners.getFirst().equals(actor.getMemberId())) throw new AccessDeniedException("Only the creator can revise; clone another template instead");
            version=repository.nextVersion(templateId,actor.getSaccoId());
        }
        UUID id = UUID.randomUUID();
        repository.add(id,templateId,actor.getSaccoId(),version,definition,shared,actor.getMemberId(),clock.now());
        log(id, "DESIGN", actor, version);
        return id;
    }

    @Transactional(readOnly = true)
    public List<Version> list(AppUserPrincipal actor, int page) {
        requireStaff(actor);
        actor=reports.currentActor(actor);
        if (!access.hasAny(actor, UserClaim.REPORT_TEMPLATE_DESIGN, UserClaim.REPORT_TEMPLATE_PUBLISH, UserClaim.REPORT_RUN)
            || page < 0 || page > 10000) throw new AccessDeniedException("Report access required");
        return repository.list(actor.getSaccoId(),actor.getMemberId(),access.has(actor,UserClaim.REPORT_TEMPLATE_PUBLISH),page);
    }

    @Transactional(readOnly = true)
    public Version get(UUID id, AppUserPrincipal actor, boolean executable) {
        requireStaff(actor);
        actor=reports.currentActor(actor);
        Version v = scoped(id, actor, false);
        if (!v.madeBy().equals(actor.getMemberId()) && !(v.state().equals("DRAFT") && access.has(actor, UserClaim.REPORT_TEMPLATE_PUBLISH))
            && !(v.visibility().equals("INSTITUTION") && v.state().equals("PUBLISHED"))) throw new AccessDeniedException("Report template unavailable");
        if (executable && !v.state().equals("PUBLISHED")) throw new IllegalArgumentException("report.error.unpublished");
        v.definition().validate();
        return v;
    }

    @Transactional
    public void publish(UUID id, AppUserPrincipal actor) {
        requireStaff(actor);
        actor=reports.currentActor(actor);
        Version v = scoped(id, actor, true);
        reports.authorize(actor, v.definition().dataset(), UserClaim.REPORT_TEMPLATE_PUBLISH);
        if (v.madeBy().equals(actor.getMemberId())) throw new IllegalArgumentException("report.error.checker");
        if (!v.state().equals("DRAFT")) throw new IllegalArgumentException("report.error.state");
        v.definition().validate();
        repository.publish(id,actor.getSaccoId(),actor.getMemberId(),clock.now());
        log(id, "PUBLISH", actor, v.version());
    }

    @Transactional
    public void retire(UUID id, AppUserPrincipal actor) {
        requireStaff(actor);
        actor=reports.currentActor(actor);
        Version v = scoped(id, actor, true);
        reports.authorize(actor, v.definition().dataset(), UserClaim.REPORT_TEMPLATE_PUBLISH);
        if (!v.state().equals("PUBLISHED")) throw new IllegalArgumentException("report.error.state");
        repository.retire(id,actor.getSaccoId());
        log(id, "RETIRE", actor, v.version());
    }
    private Version scoped(UUID id, AppUserPrincipal actor, boolean lock) {
        List<Version> versions=repository.find(id,actor.getSaccoId(),lock);
        if (versions.size() != 1) throw new AccessDeniedException("Report template unavailable");
        return versions.getFirst();
    }
    private void requireStaff(AppUserPrincipal actor) {
        if (actor == null || !actor.isStaffSession() || actor.isPlatformIdentity() || actor.getSaccoId() == null || actor.getStationId() == null
            || actor.getSaccoId().isBlank() || actor.getStationId().isBlank()) throw new AccessDeniedException("Report branch scope is required");
    }
    private void log(UUID id, String action, AppUserPrincipal actor, int version) {
        audit.log("OPERATIONAL_REPORT_TEMPLATE", id, action, actor.getMemberId(), null,
            Map.of("saccoId",actor.getSaccoId(),"stationId",actor.getStationId(),"version",version));
    }
}
