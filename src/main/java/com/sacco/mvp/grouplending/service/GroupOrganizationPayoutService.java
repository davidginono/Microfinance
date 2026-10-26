package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.grouplending.dto.GroupConsentDtos.Delivery;
import com.sacco.mvp.grouplending.dto.GroupOrganizationPayoutDtos.Packet;
import com.sacco.mvp.grouplending.repository.GroupOrganizationPayoutRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class GroupOrganizationPayoutService {
    private final GroupOrganizationPayoutRepository payouts;
    private final GroupOrganizationAgreementService people;
    private final StationOtpSettingsService otp;
    private final PasswordEncoder passwords;
    private final ApplicationClock clock;
    private final AuditService audit;
    private final org.springframework.core.env.Environment configuration;
    private static final java.security.SecureRandom RANDOM=new java.security.SecureRandom();
    @Transactional(readOnly=true) public Packet packet(AppUserPrincipal a,UUID cycle){
        var person=people.authorize(a,false);var p=payouts.own(a,cycle,clock.today());return new Packet(p.cycle(),p.loan(),p.number(),p.organization(),p.hash(),p.principal(),p.deductions(),p.net(),p.destination(),p.signedCount(),p.requiredSignatures(),p.state(),p.loanStatus(),p.currentAuthority(),p.fullDocument(),person.getSignatureText()!=null&&!person.getSignatureText().isBlank(),otp.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId()),p.confirmation());
    }
    /** A validation key lets a wrong-code attempt commit without recording a payout instruction. */
    @Transactional public String confirm(AppUserPrincipal a,UUID cycle,String hash,boolean accepted,String code){
        var person=people.authorize(a,true);if(!accepted)throw new AccessDeniedException("Payout acceptance required");payouts.lock(a,cycle);var p=payouts.own(a,cycle,clock.today());check(Objects.equals(p.hash(),hash),"changed");if(p.confirmation()!=null)return null;
        if(!p.canConfirm())return "group.error.payoutClosed";if(person.getSignatureText()==null||person.getSignatureText().isBlank())return "group.error.signature";
        UUID challenge=null;var at=clock.now();if(otp.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId())){
            var recent=payouts.challenges(a,cycle);if(recent.isEmpty())return "group.error.otpRequired";var c=recent.getFirst();
            if(c.delivered()==null||c.consumed()!=null||!c.expires().isAfter(at)||c.attempts()>=5)return "group.error.otpExpired";
            payouts.attempt(c.id());if(code==null||!code.matches("[0-9]{6}")||!passwords.matches(code,c.hash()))return "group.error.otpInvalid";
            challenge=c.id();payouts.consumed(challenge,at);
        }
        payouts.confirm(a,cycle,hash,person.getSignatureText(),challenge,at);audit.logEvent("GROUP_LENDING",cycle,"ORGANIZATION_PAYOUT_AUTHORIZED",a.getMemberId(),AuditEventStatus.SUCCESS,
            "Named authority holder confirmed the frozen organization payout instruction","GROUP_CYCLE",cycle.toString(),a.getSaccoId(),a.getStationId(),Map.of("loan",p.loan(),"packet",p.hash(),"net",p.net()));return null;
    }
    @Transactional public Delivery prepareCode(AppUserPrincipal a,UUID cycle,String hash){
        var person=people.authorize(a,true);payouts.lock(a,cycle);var p=payouts.own(a,cycle,clock.today());check(Objects.equals(p.hash(),hash),"changed");check(p.canConfirm(),"payoutClosed");
        check(person.getSignatureText()!=null&&!person.getSignatureText().isBlank(),"signature");check(otp.requiresApprovalOtp(a.getMemberId(),a.getSaccoId(),a.getStationId()),"otpDisabled");
        var at=clock.now();var recent=payouts.challenges(a,cycle);check(recent.isEmpty()||(!recent.getFirst().issued().isAfter(at.minusSeconds(60))&&(recent.size()<4||!recent.getLast().issued().isAfter(at.minusMinutes(10)))),"otpWait");
        int ttl=Math.max(1,Math.min(30,configuration.getProperty("app.auth.otp.ttl-minutes",Integer.class,10)));String code=Integer.toString(RANDOM.nextInt(900000)+100000);var id=UUID.randomUUID();
        payouts.challenge(a,cycle,hash,id,passwords.encode(code),at,at.plusMinutes(ttl));return new Delivery(id,cycle,a.getMemberId(),p.loan(),person.getEmail(),person.getPhone(),code,a.getSaccoId(),a.getStationId(),ttl);
    }
    @Transactional public void codeDelivered(AppUserPrincipal a,UUID cycle,UUID id){
        people.authorize(a,true);payouts.lock(a,cycle);check(payouts.own(a,cycle,clock.today()).canConfirm(),"payoutClosed");var c=payouts.challenges(a,cycle).stream().findFirst().filter(x->x.id().equals(id)&&x.expires().isAfter(clock.now())&&x.consumed()==null).orElseThrow(()->new IllegalArgumentException("group.error.otpExpired"));payouts.delivered(c.id(),clock.now());
    }
    private static void check(boolean value,String key){if(!value)throw new IllegalArgumentException("group.error."+key);}
}
