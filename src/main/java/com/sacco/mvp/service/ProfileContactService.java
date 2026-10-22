package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProfileContactService {
    private final MemberRepository members;
    private final PasswordEncoder passwords;
    private final PhoneOtpService phoneOtp;
    private final AuditService audit;
    private final ApplicationClock clock;

    @Transactional
    public void update(AppUserPrincipal actor,String email,String phone,String password) {
        Member member=owned(actor,true);confirm(member,password);
        String normalizedEmail=email==null?"":email.trim().toLowerCase(Locale.ROOT);
        if(normalizedEmail.length()>160 || !normalizedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new IllegalArgumentException("profile.contact.emailInvalid");
        if(phone!=null && phone.length()>32)throw new IllegalArgumentException("profile.contact.phoneInvalid");
        String normalizedPhone=TanzaniaPhoneNumber.normalizeOptional(phone);
        if(phone!=null && !phone.isBlank() && normalizedPhone==null)throw new IllegalArgumentException("profile.contact.phoneInvalid");
        if(members.existsByEmailIgnoreCaseAndIdNot(normalizedEmail,member.getId()) || (normalizedPhone!=null && members.existsByPhoneAndIdNot(normalizedPhone,member.getId())))throw new IllegalArgumentException("profile.contact.duplicate");
        var before=state(member);
        if(!Objects.equals(member.getPhone(),normalizedPhone))member.setPhoneVerifiedAt(null);
        member.setEmail(normalizedEmail);member.setPhone(normalizedPhone);members.save(member);
        audit.log("USER",member.getId(),"USER_UPDATE_OWN_CONTACT",actor.getMemberId(),before,state(member));
    }

    public boolean requestPhoneOtp(AppUserPrincipal actor,String password) {
        Member member=owned(actor,false);confirm(member,password);
        if(member.getPhoneVerifiedAt()!=null)return false;
        if(TanzaniaPhoneNumber.normalizeOptional(member.getPhone())==null)throw new IllegalArgumentException("profile.contact.phoneRequired");
        phoneOtp.issueClaimOtp(member.getPhone(),member.getId());return true;
    }

    @Transactional
    public void verifyPhone(AppUserPrincipal actor,String code,String password) {
        Member member=owned(actor,true);confirm(member,password);
        if(member.getPhoneVerifiedAt()!=null)return;
        if(TanzaniaPhoneNumber.normalizeOptional(member.getPhone())==null)throw new IllegalArgumentException("profile.contact.phoneRequired");
        phoneOtp.consumeClaimOtp(member.getPhone(),member.getId(),code);
        var before=state(member);member.setPhoneVerifiedAt(clock.now());members.save(member);
        audit.log("USER",member.getId(),"USER_VERIFY_OWN_PHONE",actor.getMemberId(),before,state(member));
    }

    private Member owned(AppUserPrincipal actor,boolean lock) {
        if(actor==null)throw new AccessDeniedException("Profile unavailable");
        Member member=(lock?members.findForSignatureRegistration(actor.getMemberId()):members.findById(actor.getMemberId())).orElseThrow(()->new AccessDeniedException("Profile unavailable"));
        if(!actor.getMemberId().equals(member.getId()) || member.getStatus()!=MemberStatus.ACTIVE || !Objects.equals(actor.getSaccoId(),member.getSaccoId()) || !Objects.equals(actor.getStationId(),member.getStationId()))throw new AccessDeniedException("Profile unavailable");
        return member;
    }
    private void confirm(Member member,String password) {
        if(password==null || password.length()>128 || member.getPasswordHash()==null || !passwords.matches(password,member.getPasswordHash()))throw new IllegalArgumentException("profile.contact.passwordInvalid");
    }
    private Map<String,Object> state(Member member) {
        var result=new LinkedHashMap<String,Object>();result.put("email",member.getEmail());result.put("phone",member.getPhone());result.put("phoneVerifiedAt",member.getPhoneVerifiedAt());result.put("saccoId",member.getSaccoId());result.put("stationId",member.getStationId());return result;
    }
}
