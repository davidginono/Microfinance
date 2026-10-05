package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.*;

/** Internal source proof access does not grant business registry or export access. */
@Component @RequiredArgsConstructor
public class BusinessSourceProofAuthorization {
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private final SaccoRegistryService institutions;

    /** Registry and approval commands require their own current business claim. Proof access grants none of these. */
    public void authorizeBusiness(AppUserPrincipal actor,UserClaim claim) {
        if(actor==null||!actor.isStaffSession()||actor.isPlatformIdentity()||actor.getMemberId()==null
            ||actor.getSaccoId()==null||actor.getStationId()==null||claim==null||!actor.getClaims().contains(claim.name()))deny();
        var current=members.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Source staff unavailable"));
        if(current.getStatus()!=MemberStatus.ACTIVE||!current.isStaffAccessActive()
            ||current.getActiveStaffRolesResolved().contains(Position.ADMIN)
            ||!Objects.equals(current.getSaccoId(),actor.getSaccoId())||!Objects.equals(current.getStationId(),actor.getStationId())
            ||!claims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),current.isMemberAccess()).contains(claim)
            ||institutions.findActiveSacco(actor.getSaccoId()).isEmpty()
            ||institutions.findStation(actor.getSaccoId(),actor.getStationId()).filter(SaccoStation::isActive)
                .filter(s->s.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty())deny();
    }

    public void authorize(AppUserPrincipal actor,String branch,boolean institutionWide) {
        if(actor==null || !actor.isStaffSession() || actor.isPlatformIdentity() || actor.getMemberId()==null
                || actor.getSaccoId()==null || actor.getSaccoId().isBlank() || actor.getStationId()==null
                || branch==null || branch.isBlank() || branch.length()>255
                || !institutionWide && !branch.equals(actor.getStationId()))deny();
        var current=members.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Source proof staff unavailable"));
        if(current.getStatus()!=MemberStatus.ACTIVE || !current.isStaffAccessActive()
                || current.getActiveStaffRolesResolved().contains(Position.ADMIN)
                || !Objects.equals(current.getSaccoId(),actor.getSaccoId())
                || !Objects.equals(current.getStationId(),actor.getStationId())
                || institutions.findActiveSacco(actor.getSaccoId()).isEmpty()
                || institutions.findStation(actor.getSaccoId(),actor.getStationId()).filter(SaccoStation::isActive)
                    .filter(s->s.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty())deny();
        var effective=claims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),current.isMemberAccess());
        boolean permitted=institutionWide
                ?effective.contains(UserClaim.ACCOUNTING_CLOSING_APPROVE) && effective.contains(UserClaim.ACCOUNTING_CLOSING_INSTITUTION)
                    || effective.contains(UserClaim.FINANCIAL_REPORTS_VIEW) && effective.contains(UserClaim.FINANCIAL_REPORTS_INSTITUTION)
                :effective.contains(UserClaim.ACCOUNTING_CLOSING_VIEW) || effective.contains(UserClaim.ACCOUNTING_CLOSING_CREATE)
                    || effective.contains(UserClaim.ACCOUNTING_CLOSING_APPROVE) || effective.contains(UserClaim.FINANCIAL_REPORTS_VIEW);
        // Historical source proof may include a retained inactive branch; the current actor's workspace remains active.
        if(!permitted || institutions.findStation(actor.getSaccoId(),branch).isEmpty())deny();
    }
    private static void deny(){throw new AccessDeniedException("Business source proof scope required");}
}
