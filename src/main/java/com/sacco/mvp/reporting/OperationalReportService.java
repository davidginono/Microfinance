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
    private final SaccoLogoStorageService logos;
    public record Branding(byte[] logo,String mediaType,String sha256){
        public Branding { logo=logo.clone(); }
        @Override public byte[] logo(){return logo.clone();}
    }
    public record Result(OperationalReportDefinition definition, String institution, String branch,
            LocalDate from, LocalDate through, OffsetDateTime recordedCutoff, long rowsInScope,
            long untrackedLoans, List<Map<String, Object>> rows, Map<String, BigDecimal> totals,
            int page, int pageSize, boolean truncated, String coverageKey, Branding branding) {
        public Result(OperationalReportDefinition definition,String institution,String branch,LocalDate from,LocalDate through,
                OffsetDateTime recordedCutoff,long rowsInScope,long untrackedLoans,List<Map<String,Object>> rows,Map<String,BigDecimal> totals,
                int page,int pageSize,boolean truncated,String coverageKey){
            this(definition,institution,branch,from,through,recordedCutoff,rowsInScope,untrackedLoans,rows,totals,page,pageSize,truncated,coverageKey,null);
        }
        public List<Column> getVisibleColumns() { return definition.columns().stream().filter(Column::visible).toList(); }
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public Result execute(OperationalReportDefinition definition,AppUserPrincipal actor,LocalDate from,
            LocalDate through,OffsetDateTime cutoff,int page,int pageSize) {
        definition.validate();validateDates(from,through);authorize(actor,definition.dataset(),UserClaim.REPORT_RUN);
        if(through.isAfter(clock.today())||cutoff==null||cutoff.isAfter(clock.now())||page<0||page>10000||pageSize<1||pageSize>2000)
            throw new IllegalArgumentException("report.error.definition");
        Result result=repository.execute(definition,actor,from,through,cutoff,page,pageSize);
        if(!definition.institutionLogo())return result;
        if(!logos.hasLogo(actor.getSaccoId()))throw new IllegalArgumentException("report.error.logo");
        var logo=logos.load(actor.getSaccoId());byte[] bytes=logo.content();
        if(bytes.length==0||bytes.length>5_000_000||!Set.of("image/png","image/jpeg").contains(logo.contentType().toString()))throw new IllegalArgumentException("report.error.logo");
        String hash;
        try{hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        return new Result(result.definition(),result.institution(),result.branch(),result.from(),result.through(),result.recordedCutoff(),
            result.rowsInScope(),result.untrackedLoans(),result.rows(),result.totals(),result.page(),result.pageSize(),result.truncated(),result.coverageKey(),new Branding(bytes,logo.contentType().toString(),hash));
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
