package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupConsentDtos.Delivery;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page;
import com.sacco.mvp.grouplending.repository.GroupCycleRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final StationOtpSettingsService otpSettings;
    private final PasswordEncoder passwords;
    private final org.springframework.core.env.Environment configuration;
    private static final java.security.SecureRandom RANDOM=new java.security.SecureRandom();
    public record Packet(UUID id,String group,Policy policy,Participant borrower,Terms terms,List<Guarantee> guarantees,boolean accepted,boolean otpRequired,boolean signatureReady,TerminationNotice termination,SharedObligation shared) {
        public Packet(UUID id,String group,Policy policy,Participant borrower,Terms terms,List<Guarantee> guarantees,boolean accepted,boolean otpRequired,boolean signatureReady,TerminationNotice termination){this(id,group,policy,borrower,terms,guarantees,accepted,otpRequired,signatureReady,termination,null);}
    }
    Member authorize(AppUserPrincipal actor,UserClaim... required){
        if(actor==null || !actor.isMemberAccess())throw new AccessDeniedException("Participant access required");
        var current=members.find(actor.getMemberId()).orElseThrow(()->new AccessDeniedException("Participant unavailable"));
        if(current.getStatus()!=MemberStatus.ACTIVE || !current.isMemberAccess() || !Objects.equals(current.getSaccoId(),actor.getSaccoId())
            || !Objects.equals(current.getStationId(),actor.getStationId()))throw new AccessDeniedException("Participant unavailable");
        var effective=claims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),current.isMemberAccess());
        for(var claim:required)if(!access.has(actor,claim) || !effective.contains(claim))throw new AccessDeniedException("Participant permission required");
        if(institutions.findActiveSacco(actor.getSaccoId()).isEmpty() || institutions.findStation(actor.getSaccoId(),actor.getStationId())
            .filter(s->s.isActive() && s.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty())throw new AccessDeniedException("Institution or branch unavailable");
        return current;
    }
    @Transactional(readOnly=true) public Page<ClientCycle> list(AppUserPrincipal a,int page){
        authorize(a,UserClaim.MEMBER_LOANS_VIEW);if(page<0 || page>10000)throw new IllegalArgumentException("group.error.query");
        var rows=cycles.clientCycles(a,page);return new Page<>(rows.stream().limit(25).toList(),page,rows.size()>25);
    }
    @Transactional(readOnly=true) public Packet packet(AppUserPrincipal a,UUID id){
        var current=authorize(a,UserClaim.MEMBER_LOANS_VIEW);return loadPacket(a,id,current);
    }
    private Packet loadPacket(AppUserPrincipal a,UUID id,Member current){
        if(!cycles.participant(a,id))throw new AccessDeniedException("Group agreement unavailable");var c=cycles.cycle(a,id,false);
        if(!Set.of("SUBMITTED","CANCELLED").contains(c.state()))throw new AccessDeniedException("Group agreement unavailable");
        var own=cycles.participants(a,id).stream().filter(p->p.clientId().equals(a.getMemberId())).findFirst().orElseThrow();
        return new Packet(id,c.groupName(),cycles.policy(a,c.policyId()),own,cycles.ownTerms(a,id),cycles.guarantees(a,id),cycles.consented(a,id),otpSettings.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId()),signatureReady(current),cycles.termination(a,id).map(t->new TerminationNotice(t.kind(),t.recordedAt())).orElse(null),c.model()==Model.SHARED?cycles.sharedObligation(a,id).orElseThrow():null);
    }
    private Member signer(AppUserPrincipal a,UUID id){var current=authorize(a,UserClaim.MEMBER_LOANS_VIEW,UserClaim.MEMBER_LOANS_UPDATE);if(cycles.cycle(a,id,false).model()!=Model.SHARED)authorize(a,UserClaim.GUARANTOR_REQUESTS_APPROVE);return current;}
    /** Returns a validation key without rolling back failed-code attempt accounting. */
    @Transactional public String accept(AppUserPrincipal a,UUID id,boolean accepted,String code){
        var current=signer(a,id);if(!accepted)throw new AccessDeniedException("Personal acceptance required");
        consentLock(a,id);var packet=loadPacket(a,id,current);if(packet.termination()!=null)return "group.error.endedCycle";if(packet.accepted())return null;
        if(!packet.signatureReady())return "group.error.signature";UUID challengeId=null;var signedAt=clock.now();
        if(packet.otpRequired()){
            var challenges=cycles.challenges(a,id);if(challenges.isEmpty())return "group.error.otpRequired";var challenge=challenges.getFirst();
            if(challenge.delivered()==null || challenge.consumed()!=null || !challenge.expires().isAfter(clock.now()) || challenge.attempts()>=5)return "group.error.otpExpired";
            cycles.challengeAttempt(challenge.id());
            if(code==null || !code.matches("[0-9]{6}") || !passwords.matches(code,challenge.hash()))return "group.error.otpInvalid";
            challengeId=challenge.id();cycles.challengeConsumed(challengeId,signedAt);
        }
        cycles.consent(a,id,packet.policy().id(),current.getSignatureText(),challengeId,signedAt);audit.logEvent("GROUP_LENDING",id,"GROUP_TERMS_ACCEPTED",a.getMemberId(),AuditEventStatus.SUCCESS,
            "Group loan and guarantee terms accepted","GROUP_CYCLE",id.toString(),a.getSaccoId(),a.getStationId(),Map.of("policy",packet.policy().id(),"client",a.getMemberId()));
        return null;
    }
    @Transactional public Delivery prepareCode(AppUserPrincipal a,UUID id){
        var current=signer(a,id);consentLock(a,id);var packet=loadPacket(a,id,current);
        if(packet.termination()!=null)throw new IllegalArgumentException("group.error.endedCycle");
        if(packet.accepted() || !packet.otpRequired())throw new IllegalArgumentException("group.error.otpDisabled");
        if(!packet.signatureReady())throw new IllegalArgumentException("group.error.signature");
        var now=clock.now();var recent=cycles.challenges(a,id);
        if(!recent.isEmpty() && (recent.getFirst().issued().isAfter(now.minusSeconds(60)) || recent.size()==4 && recent.getLast().issued().isAfter(now.minusMinutes(10))))throw new IllegalArgumentException("group.error.otpWait");
        int ttl=Math.max(1,configuration.getProperty("app.auth.otp.ttl-minutes",Integer.class,10));
        String code=Integer.toString(RANDOM.nextInt(900000)+100000);UUID challenge=UUID.randomUUID();cycles.challenge(a,id,challenge,passwords.encode(code),now,now.plusMinutes(ttl));
        return new Delivery(challenge,id,a.getMemberId(),packet.borrower().loanId(),current.getEmail(),current.getPhone(),code,a.getSaccoId(),a.getStationId(),ttl);
    }
    @Transactional public void codeDelivered(AppUserPrincipal a,UUID cycle,UUID id){
        signer(a,cycle);consentLock(a,cycle);if(cycles.termination(a,cycle).isPresent())throw new IllegalArgumentException("group.error.endedCycle");
        var challenge=cycles.challenges(a,cycle).stream().findFirst().filter(c->c.id().equals(id) && c.expires().isAfter(clock.now()) && c.consumed()==null).orElseThrow(()->new IllegalArgumentException("group.error.otpExpired"));
        cycles.challengeDelivered(challenge.id(),clock.now());
    }
    @Transactional public void refuse(AppUserPrincipal a,UUID id,String reason,boolean confirmed){
        authorize(a,UserClaim.MEMBER_LOANS_VIEW,UserClaim.MEMBER_LOANS_UPDATE);
        if(!confirmed || reason==null || reason.isBlank() || reason.length()>500 || reason.chars().anyMatch(c->c<32))throw new IllegalArgumentException("group.error.refusal");
        consentLock(a,id);var c=cycles.cycle(a,id,false);var prior=cycles.termination(a,id);
        if(prior.isPresent()){var t=prior.get();if(t.kind().equals("REFUSED") && t.actorId().equals(a.getMemberId()) && t.reason().equals(reason.strip()))return;throw new IllegalArgumentException("group.error.endedCycle");}
        if(!"SUBMITTED".equals(c.state()) || cycles.consented(a,id))throw new IllegalArgumentException("group.error.alreadySigned");
        if(!cycles.lockUnfundedLoans(a,id))throw new IllegalArgumentException("group.error.fundedCycle");
        cycles.terminate(a,c,"REFUSED",reason.strip(),clock.now());audit.logEvent("GROUP_LENDING",id,"GROUP_TERMS_REFUSED",a.getMemberId(),AuditEventStatus.SUCCESS,
            "Group terms refused","GROUP_CYCLE",id.toString(),a.getSaccoId(),a.getStationId(),Map.of("reason",reason.strip()));
    }
    private void consentLock(AppUserPrincipal a,UUID cycle){if(!cycles.participant(a,cycle))throw new AccessDeniedException("Group agreement unavailable");cycles.cycle(a,cycle,true);cycles.lock(a,"GROUP-CONSENT/"+cycle,a.getMemberId());}
    private boolean signatureReady(Member current){return current.getSignatureText()!=null && !current.getSignatureText().isBlank();}
}
