package com.sacco.mvp.clients.portal.service;

import com.sacco.mvp.clients.portal.dto.ClientPortalDtos.*;
import com.sacco.mvp.clients.portal.repository.ClientPortalRepository;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.SelfForm;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.ProfileDraft;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ClientPortalService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final ClientPortalRepository records;
    private final PasswordEncoder passwords;
    private final StationOtpDeliveryService delivery;
    private final MemberRegistrationService registration;
    private final OnboardingService onboarding;
    private final ApplicationClock clock;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper json;
    private final AuditService audit;
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${app.client-portal.cleanup-ms:60000}")
    @Transactional(timeout=10) public void pruneExpiredChallenges(){records.prune(clock.now().minusDays(1));}

    @Transactional(readOnly=true) public List<Choice> choices(String institution,String search,int page){check(search!=null&&search.length()<=100&&page>=0&&page<=10000);check(institution==null||institution.length()<=255);return records.choices(institution,search,page);}
    public ProfileDraft registrationDraft(EntryForm f) {
        check(f.getInstitution()!=null&&f.getInstitution().length()<=255&&f.getBranch()!=null&&f.getBranch().length()<=255);
        return new ProfileDraft(onboarding.encodeProfileDraft(f.getRegistration().getProfile()),null,f.getInstitution(),f.getBranch(),f.getRegistration().getRequestKey());
    }
    /** Delivery runs after the short challenge/rate transaction, never under a database lock. */
    public UUID request(EntryForm f,String address) {
        check(f!=null&&Set.of("REGISTER","PROFILE","LOAN").contains(f.getIntent()));
        var tx=new TransactionTemplate(transactions);tx.setTimeout(15);
        UUID id=UUID.randomUUID();
        if(!Boolean.TRUE.equals(tx.execute(status->records.rate(digest("request/ip/"+Objects.toString(address,"")),30,0,clock.now()))))return id;
        String code=Integer.toString(100000+RANDOM.nextInt(900000));String encoded=passwords.encode(code);
        Member[] target=new Member[1];
        Challenge q=tx.execute(status->{
            var now=clock.now();
            Member member;
            if(f.getIntent().equals("REGISTER")) {
                var p=f.getRegistration().getProfile();String name=f.getRegistration().getName();
                check(name!=null&&name.length()<=255&&!p.getFirstName().isBlank()&&!p.getLastName().isBlank());
                check(f.getInstitution()!=null&&f.getInstitution().length()<=255&&f.getBranch()!=null&&f.getBranch().length()<=255);
                if(!records.scope(f.getInstitution(),f.getBranch()))return null;
                String phone;try{phone=TanzaniaPhoneNumber.normalizeRequired(p.getPhone());}catch(IllegalStateException e){throw new IllegalArgumentException("portal.error.contact");}
                check(p.getEmail()!=null&&p.getEmail().length()<=255&&(p.getEmail().isBlank()||p.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")));
                p.setPhone(phone);p.setEmail(p.getEmail().strip().toLowerCase(Locale.ROOT));
                onboarding.validateRegistrationProfile(p);
                member=Member.builder().saccoId(f.getInstitution()).stationId(f.getBranch()).fullName(name).phone(phone).email(p.getEmail()).build();
            } else {
                check(f.getClientNumber()!=null&&f.getClientNumber().length()<=80);member=records.client(f.getClientNumber().strip().toUpperCase(Locale.ROOT)).orElse(null);
                if(member==null||!records.scope(member.getSaccoId(),member.getStationId()))return null;
                if(f.getIntent().equals("LOAN")&&!onboarding.ownStatus(AppUserPrincipal.forClientPortal(member)).state().equals("VERIFY"))return null;
            }
            if(!records.rate(digest("request/contact/"+(member.getId()==null?member.getPhone():member.getId())),5,60,now))return null;
            target[0]=member;
            var challenge=new Challenge(id,member.getId(),member.getSaccoId(),member.getStationId(),f.getIntent(),contact(member),f.getIntent().equals("REGISTER")?json.writeValueAsString(f):"{}",encoded,now.plusMinutes(5),null,null,0);
            records.create(challenge,now);return challenge;
        });
        if(q!=null)try {
            var m=target[0];delivery.deliver(m.getSaccoId(),m.getStationId(),m.getEmail(),m.getPhone(),q.intent().equals("REGISTER")?EmailOtpPurpose.PROVISIONAL_REGISTRATION:EmailOtpPurpose.PROVISIONAL_ACCESS,"Microfinance client portal","Use this code only to access your client portal.",code,5);
            tx.executeWithoutResult(status->records.delivered(id,clock.now()));
        } catch(RuntimeException ignored) { /* Public responses do not expose identity or delivery configuration. */ }
        return id;
    }
    public Optional<Session> verify(UUID id,String code,String address) {
        if(id==null||code==null||!code.matches("[0-9]{6}"))return Optional.empty();
        var tx=new TransactionTemplate(transactions);tx.setTimeout(20);
        Challenge q=tx.execute(status->{
            var now=clock.now();if(!records.rate(digest("verify/ip/"+Objects.toString(address,"")),50,0,now))return null;
            var c=records.challenge(id).orElse(null);if(c==null||c.deliveredAt()==null||c.consumedAt()!=null||!c.expiresAt().isAfter(now)||c.attempts()>=5)return null;
            records.attempted(id);if(!passwords.matches(code,c.codeHash()))return null;
            records.consumed(id,now);return c;
        });
        if(q==null)return Optional.empty();
        try {
            return Optional.ofNullable(tx.execute(status->{
                Member member;
                if(q.intent().equals("REGISTER")) {
                    var f=json.readValue(q.payload(),EntryForm.class);var form=new MemberRegistrationForm();form.setEmail(f.getRegistration().getEmail());form.setPhone(f.getRegistration().getPhone());
                    member=registration.registerAssisted(form,f.getRegistration().getName(),q.institution(),q.branch());
                    var draft=new SelfForm();draft.setProfile(f.getRegistration().getProfile());onboarding.saveOwn(AppUserPrincipal.forClientPortal(member),draft);
                    audit.logEvent("CLIENT",member.getId(),"CLIENT_SELF_REGISTRATION",member.getId(),AuditEventStatus.SUCCESS,"Client self registration","CLIENT",member.getId().toString(),q.institution(),q.branch(),Map.of());
                } else {
                    member=records.client(q.clientId()).orElse(null);if(member==null||!contact(member).equals(q.contactHash()))return null;
                }
                if(!records.scope(member.getSaccoId(),member.getStationId()))return null;
                if(q.intent().equals("LOAN")&&!onboarding.ownStatus(AppUserPrincipal.forClientPortal(member)).state().equals("VERIFY"))return null;
                return new Session(member.getId(),member.getSaccoId(),member.getStationId(),contact(member),q.intent().equals("LOAN"),clock.now().plusMinutes(15));
            }));
        }catch(IllegalStateException|IllegalArgumentException|org.springframework.dao.DataIntegrityViolationException e){return Optional.empty();}
    }
    @Transactional(readOnly=true) public AppUserPrincipal actor(Session session,boolean loan) {
        if(session==null||!session.expiresAt().isAfter(clock.now())||loan&&!session.loanAccess())throw new AccessDeniedException("Portal access required");
        var m=records.client(session.clientId()).orElseThrow(()->new AccessDeniedException("Client unavailable"));
        if(!Objects.equals(m.getSaccoId(),session.institution())||!Objects.equals(m.getStationId(),session.branch())||!contact(m).equals(session.contactHash())||!records.scope(m.getSaccoId(),m.getStationId()))throw new AccessDeniedException("Client unavailable");
        var actor=AppUserPrincipal.forClientPortal(m);if(loan&&!onboarding.ownStatus(actor).state().equals("VERIFY"))throw new AccessDeniedException("Profile verification required");return actor;
    }
    private String contact(Member m){return digest(Objects.toString(m.getId(),"")+"/"+m.getSaccoId()+"/"+m.getStationId()+"/"+m.getFullName()+"/"+m.getPhone()+"/"+Objects.toString(m.getEmail(),""));}
    private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static void check(boolean value){if(!value)throw new IllegalArgumentException("portal.error.invalid");}
}
