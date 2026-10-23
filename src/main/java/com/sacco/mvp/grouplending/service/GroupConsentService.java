package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page;
import com.sacco.mvp.grouplending.repository.GroupCycleRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Participant-owned acceptance of an immutable loan/guarantee packet; never a staff approval. */
@Service @RequiredArgsConstructor
public class GroupConsentService {
    private final GroupCycleRepository cycles;
    private final MemberDirectoryService members;
    private final AccessControlService access;
    private final UserClaimService claims;
    private final SaccoRegistryService institutions;
    private final ApplicationClock clock;
    private final AuditService audit;
    public record Packet(UUID id,String group,Policy policy,Participant borrower,Terms terms,List<Guarantee> guarantees,boolean accepted) {}
    private void authorize(AppUserPrincipal actor,UserClaim... required){
        if(actor==null || !actor.isMemberAccess())throw new AccessDeniedException("Participant access required");
        var current=members.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Participant unavailable"));
        if(current.getStatus()!=MemberStatus.ACTIVE || !current.isMemberAccess() || !Objects.equals(current.getSaccoId(),actor.getSaccoId())
            || !Objects.equals(current.getStationId(),actor.getStationId()))throw new AccessDeniedException("Participant unavailable");
        var effective=claims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),current.isMemberAccess());
        for(var claim:required)if(!access.has(actor,claim) || !effective.contains(claim))throw new AccessDeniedException("Participant permission required");
        if(institutions.findActiveSacco(actor.getSaccoId()).isEmpty() || institutions.findStation(actor.getSaccoId(),actor.getStationId())
            .filter(s->s.isActive() && s.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty())throw new AccessDeniedException("Institution or branch unavailable");
    }
    @Transactional(readOnly=true) public Page<ClientCycle> list(AppUserPrincipal a,int page){
        authorize(a,UserClaim.MEMBER_LOANS_VIEW);if(page<0 || page>10000)throw new IllegalArgumentException("group.error.query");
        var rows=cycles.clientCycles(a,page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);
    }
    @Transactional(readOnly=true) public Packet packet(AppUserPrincipal a,UUID id){
        authorize(a,UserClaim.MEMBER_LOANS_VIEW);return loadPacket(a,id);
    }
    private Packet loadPacket(AppUserPrincipal a,UUID id){
        if(!cycles.participant(a,id))throw new AccessDeniedException("Group agreement unavailable");var c=cycles.cycle(a,id,false);
        if(!"SUBMITTED".equals(c.state()))throw new AccessDeniedException("Group agreement unavailable");
        var own=cycles.participants(a,id).stream().filter(p->p.clientId().equals(a.getMemberId())).findFirst().orElseThrow();
        return new Packet(id,c.groupName(),cycles.policy(a,c.policyId()),own,cycles.ownTerms(a,id),cycles.guarantees(a,id),cycles.consented(a,id));
    }
    @Transactional public void accept(AppUserPrincipal a,UUID id,boolean accepted){
        authorize(a,UserClaim.MEMBER_LOANS_VIEW,UserClaim.GUARANTOR_REQUESTS_APPROVE);if(!accepted)throw new AccessDeniedException("Personal acceptance required");
        var packet=loadPacket(a,id);if(packet.accepted())return;
        cycles.consent(a,id,clock.now());audit.logEvent("GROUP_LENDING",id,"GROUP_TERMS_ACCEPTED",a.getMemberId(),AuditEventStatus.SUCCESS,
            "Group loan and guarantee terms accepted","GROUP_CYCLE",id.toString(),a.getSaccoId(),a.getStationId(),Map.of("policy",packet.policy().id(),"client",a.getMemberId()));
    }
}
