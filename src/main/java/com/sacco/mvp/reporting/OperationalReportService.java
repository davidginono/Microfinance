package com.sacco.mvp.reporting;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.sacco.mvp.reporting.OperationalReportDefinition.*;

@Service
@RequiredArgsConstructor
public class OperationalReportService {
    private final OperationalReportRepository repository;
    private final AccessControlService access;
    private final ApplicationClock clock;
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private final SaccoRegistryService institutions;
    public record Result(OperationalReportDefinition definition, String institution, String branch,
            LocalDate from, LocalDate through, OffsetDateTime recordedCutoff, long rowsInScope,
            long untrackedLoans, List<Map<String, Object>> rows, Map<String, BigDecimal> totals,
            int page, int pageSize, boolean truncated, String coverageKey) {
        public List<Column> getVisibleColumns() { return definition.columns().stream().filter(Column::visible).toList(); }
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public Result execute(OperationalReportDefinition definition,AppUserPrincipal actor,LocalDate from,
            LocalDate through,OffsetDateTime cutoff,int page,int pageSize) {
        definition.validate();validateDates(from,through);authorize(actor,definition.dataset(),UserClaim.REPORT_RUN);
        if(through.isAfter(clock.today())||cutoff==null||cutoff.isAfter(clock.now())||page<0||page>10000||pageSize<1||pageSize>2000)
            throw new IllegalArgumentException("report.error.definition");
        return repository.execute(definition,actor,from,through,cutoff,page,pageSize);
    }
    public AppUserPrincipal currentActor(AppUserPrincipal actor) {
        if(actor==null||!actor.isStaffSession()||actor.isPlatformIdentity()||actor.getSaccoId()==null||actor.getStationId()==null
            ||actor.getSaccoId().isBlank()||actor.getStationId().isBlank()) denied();
        Member member=members.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Report access unavailable"));
        if(member.getStatus()!=MemberStatus.ACTIVE||!member.isStaffAccessActive()||!Objects.equals(member.getSaccoId(),actor.getSaccoId())||!Objects.equals(member.getStationId(),actor.getStationId())
            ||institutions.findActiveSacco(actor.getSaccoId()).isEmpty()
            ||institutions.findStation(actor.getSaccoId(),actor.getStationId()).filter(SaccoStation::isActive)
                .filter(station->station.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty()) denied();
        return new AppUserPrincipal(member,claims.effectiveClaims(member.getId(),member.getActiveStaffRolesResolved(),member.isMemberAccess()),true);
    }
    public void authorize(AppUserPrincipal actor,Dataset dataset,UserClaim command) {
        AppUserPrincipal current=currentActor(actor);
        if(current.isPlatformIdentity()||!access.has(current,command)
            ||!access.has(current,dataset==Dataset.COLLECTIONS?UserClaim.LOAN_REPAYMENTS_VIEW:UserClaim.LOAN_REPORTS_VIEW)) denied();
    }
    private static void denied(){throw new AccessDeniedException("Report permission and active branch scope required");}
}
