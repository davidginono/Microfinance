package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberRegistrationService {
    private final AuditService auditService;
    private final MemberRepository memberRepository;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final NameSignatureService nameSignatureService;
    @Transactional(readOnly=true)
    public String employeeNumber(com.sacco.mvp.security.AppUserPrincipal actor,UUID client) {
        if(actor==null||!actor.isStaffSession())throw new org.springframework.security.access.AccessDeniedException("Staff required");
        return memberRepository.findByIdAndSaccoIdAndStationIdIgnoreCase(client,actor.getSaccoId(),actor.getStationId())
            .map(m->java.util.Objects.toString(m.getEmployeeNumber(),"")).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
    }
    /** Called by the authorized staff profile save, in the same transaction as its revision. */
    @Transactional
    public void employeeNumber(com.sacco.mvp.security.AppUserPrincipal actor,UUID client,String value) {
        if(value==null)return;
        if(actor==null||!actor.isStaffSession())throw new org.springframework.security.access.AccessDeniedException("Staff required");
        String number=EmployeeNumber.normalize(value);
        if(number!=null)memberRepository.lockClientRegistrationIdentity("CLIENT_REGISTRATION_EMPLOYEE/"+actor.getSaccoId()+"/"+actor.getStationId()+"/"+number.toLowerCase(java.util.Locale.ROOT));
        var m=memberRepository.findForClientAccess(client,actor.getSaccoId(),actor.getStationId()).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
        if(java.util.Objects.equals(number,m.getEmployeeNumber()))return;
        if(number!=null&&memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndEmployeeNumberIgnoreCase(actor.getSaccoId(),actor.getStationId(),number))throw new IllegalStateException("payroll.employeeNumber.invalid");
        if(memberRepository.updateEmployeeNumberForActiveClient(client,actor.getSaccoId(),number)!=1)throw new org.springframework.security.access.AccessDeniedException("Client unavailable");
        auditService.logEvent("CLIENT",client,"EMPLOYEE_NUMBER_UPDATED",actor.getMemberId(),AuditEventStatus.SUCCESS,"Employee number updated","CLIENT",m.getMemberNo(),actor.getSaccoId(),actor.getStationId(),java.util.Map.of("employeeNumber",number==null?"":number));
    }

    /** Scoped client details supplied by the authorized staff onboarding service. */

    /** Called before issuing registration OTP; activation must use the client's stored contact and scope. */

    /** The onboarding service supplies an authorized, existing scope; no policy defaults or signature are invented. */
    @Transactional
    public Member registerAssisted(MemberRegistrationForm form,String fullName,String institution,String branch) {
        var registered=resolveRegisteredSacco(institution);
        requireRegisteredStation(institution,branch);
        var verified=new VerifiedExternalMember("",normalizeEmail(form.getEmail()).isEmpty()?null:normalizeEmail(form.getEmail()),
            nameSignatureService.requireFullName(fullName,"Enter your full names."),institution,branch,registered.getSaccoName());
        return registerKnown(form,verified);
    }

    private Member registerKnown(MemberRegistrationForm form,VerifiedExternalMember verified) {
        log.info("Client registration requested: saccoId={}", verified.saccoId());
        lockRegistrationContacts(form, verified);

        String normalizedPhone = normalizePhone(form.getPhone());
        String employeeNumber = EmployeeNumber.normalize(form.getEmployeeNumber());
        ensureLocalUniqueness(verified.memberNo(), verified.email(), normalizedPhone,
            employeeNumber, verified.saccoId(), verified.stationId());
        String memberNo = allocateClientNumber();
        OffsetDateTime now = OffsetDateTime.now();
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId(verified.saccoId())
            .memberNo(memberNo)
            .username(null)
            .employeeNumber(employeeNumber)
            .stationId(verified.stationId())
            .fullName(verified.fullName())
            .phone(normalizedPhone)
            .email(verified.email())
            .signatureText(null)
            .signatureRegisteredAt(null)
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .rank(nextMemberRank(verified.saccoId()))
            .passwordHash("!NO_PORTAL_ACCESS")
            .createdAt(now)
            .profileLastSyncedAt(now)
            .build();

        Member savedMember=memberRepository.saveAndFlush(member);

        log.info("Client registration completed successfully: localMemberId={}, saccoId={}",
            savedMember.getId(), savedMember.getSaccoId());
        return savedMember;
    }

    private void lockRegistrationContacts(MemberRegistrationForm form, VerifiedExternalMember verified) {
        registrationKeys(form,verified).forEach(memberRepository::lockClientRegistrationIdentity);
    }

    private java.util.SortedSet<String> registrationKeys(MemberRegistrationForm form, VerifiedExternalMember verified) {
        var keys = new java.util.TreeSet<String>();
        if (verified.email()!=null&&!verified.email().isBlank()) keys.add("CLIENT_REGISTRATION_EMAIL/" + verified.email());
        if(form.getPhone()!=null&&!form.getPhone().isBlank()) keys.add("CLIENT_REGISTRATION_PHONE/" + normalizePhone(form.getPhone()));
        String employee = EmployeeNumber.normalize(form.getEmployeeNumber());
        if (employee != null) keys.add("CLIENT_REGISTRATION_EMPLOYEE/" + verified.saccoId() + "/" + verified.stationId() + "/" + employee.toLowerCase(java.util.Locale.ROOT));
        return keys;
    }

    /** Onboarding owns the current staff claim check; this operation preserves its exact authorized client scope. */
    @Transactional
    public void correctProvisionalContact(com.sacco.mvp.security.AppUserPrincipal actor,UUID id,String fullName,String phone,String email) {
        if(actor==null||!actor.isStaffSession())throw new org.springframework.security.access.AccessDeniedException("Client unavailable");
        Member before=memberRepository.findByIdAndSaccoIdAndStationIdIgnoreCase(id,actor.getSaccoId(),actor.getStationId()).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
        String name=nameSignatureService.requireFullName(fullName,"Enter your full names."),normalizedPhone=normalizePhone(phone),normalizedEmail=normalizeEmail(email);
        if(normalizedEmail.isEmpty())normalizedEmail=null;
        if(normalizedEmail!=null&&(normalizedEmail.length()>255||!normalizedEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw new IllegalStateException("Enter a valid email address.");
        var oldForm=new MemberRegistrationForm();oldForm.setPhone(before.getPhone());oldForm.setEmployeeNumber(before.getEmployeeNumber());
        var nextForm=new MemberRegistrationForm();nextForm.setPhone(normalizedPhone);nextForm.setEmployeeNumber(before.getEmployeeNumber());
        var oldScope=new VerifiedExternalMember("",before.getEmail(),before.getFullName(),before.getSaccoId(),before.getStationId(),"");
        var newScope=new VerifiedExternalMember("",normalizedEmail,name,before.getSaccoId(),before.getStationId(),"");
        var keys=registrationKeys(oldForm,oldScope);keys.addAll(registrationKeys(nextForm,newScope));keys.forEach(memberRepository::lockClientRegistrationIdentity);
        var current=memberRepository.findForClientAccess(id,actor.getSaccoId(),actor.getStationId()).orElseThrow();
        if(java.util.Objects.equals(before.getFullName(),name)&&java.util.Objects.equals(before.getPhone(),normalizedPhone)&&java.util.Objects.equals(before.getEmail(),normalizedEmail))return;
        if(!current.getStaffRolesResolved().isEmpty())throw new IllegalStateException("Use the account profile to change contact details.");
        if((normalizedEmail!=null&&memberRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail,id))||memberRepository.existsByPhoneAndIdNot(normalizedPhone,id))throw new IllegalStateException("That email address or phone is already registered.");
        if(memberRepository.correctProvisionalContact(id,actor.getSaccoId(),actor.getStationId(),name,normalizedPhone,normalizedEmail,before.getFullName(),java.util.Objects.toString(before.getPhone(),""),java.util.Objects.toString(before.getEmail(),""))!=1)
            throw new IllegalStateException("Client details changed. Refresh and try again.");
        auditService.logEvent("CLIENT",id,"CLIENT_PROVISIONAL_CONTACT_CORRECTED",actor.getMemberId(),AuditEventStatus.SUCCESS,"Provisional client details updated","CLIENT",id.toString(),actor.getSaccoId(),actor.getStationId(),java.util.Map.of());
    }

    /** Boolean-only check against the same system-wide contact keys used when saving. */
    @Transactional(readOnly=true)
    public boolean contactRegistered(com.sacco.mvp.security.AppUserPrincipal actor,UUID excludedClient,String field,String value) {
        if(actor==null || !actor.isStaffSession() || !actor.getClaims().contains("CLIENT_ONBOARDING_VIEW") || !actor.getClaims().contains(excludedClient==null?"CLIENT_ONBOARDING_CREATE":"CLIENT_ONBOARDING_UPDATE"))throw new org.springframework.security.access.AccessDeniedException("Client unavailable");
        if(excludedClient!=null) {
            var current=memberRepository.findByIdAndSaccoIdAndStationIdIgnoreCase(excludedClient,actor.getSaccoId(),actor.getStationId()).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
            if(!current.getStaffRolesResolved().isEmpty())throw new org.springframework.security.access.AccessDeniedException("Client unavailable");
        }
        return switch(field) {
            case "phone" -> excludedClient==null?memberRepository.existsByPhone(value):memberRepository.existsByPhoneAndIdNot(value,excludedClient);
            case "email" -> !value.isBlank() && (excludedClient==null?memberRepository.existsByEmailIgnoreCase(value):memberRepository.existsByEmailIgnoreCaseAndIdNot(value,excludedClient));
            default -> throw new IllegalArgumentException("Unsupported contact field");
        };
    }

    public void ensureLocalUniqueness(String memberNo, String email, String phone,
                                      String employeeNumber, String saccoId, String stationId) {
        String normalizedPhone = normalizePhone(phone);
        if (memberNo != null && !memberNo.isBlank() && memberRepository.findByMemberNo(memberNo).isPresent()) {
            log.info("Client registration blocked: client number already registered");
            throw new IllegalStateException("That client number is already registered in this system.");
        }
        if (email!=null&&!email.isBlank()&&memberRepository.existsByEmailIgnoreCase(email)) {
            log.info("Client registration blocked: email already registered");
            throw new IllegalStateException("That email address is already registered in this system.");
        }
        if (memberRepository.existsByPhone(normalizedPhone)) {
            log.info("Client registration blocked: phone already registered");
            throw new IllegalStateException("That phone number is already registered in this system.");
        }
        String normalizedEmployeeNumber = EmployeeNumber.normalize(employeeNumber);
        if (normalizedEmployeeNumber != null && memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndEmployeeNumberIgnoreCase(
            normalizeSaccoId(saccoId), normalizeStationId(stationId), normalizedEmployeeNumber)) {
            log.info("Client registration blocked: employee number already registered");
            throw new IllegalStateException("That employee number is already registered in this branch.");
        }
    }

    private String allocateClientNumber() {
        // nextval is atomic across transactions and app instances. Never recycle rolled-back values.
        for (int attempt = 0; attempt < 32; attempt++) {
            String number = "CL-" + String.format(java.util.Locale.ROOT, "%08d", memberRepository.nextClientNumberValue());
            if (!memberRepository.existsByMemberNoIgnoreCase(number)) return number;
        }
        throw new IllegalStateException("Unable to assign a client number. Please try again.");
    }

    private int nextMemberRank(String saccoId) {
        return memberRepository.findTopBySaccoIdAndPositionOrderByRankDesc(saccoId, Position.MEMBER)
            .map(Member::getRank)
            .orElse(0) + 1;
    }

    private String normalizeEmail(String value) {
        return normalizeSpaces(value).toLowerCase();
    }

    private String normalizePhone(String value) {
        return TanzaniaPhoneNumber.normalizeRequired(value);
    }

    private String normalizeSaccoId(String value) {
        return normalizeSpaces(value).toUpperCase();
    }

    private String normalizeStationId(String value) {
        return normalizeSpaces(value).toUpperCase();
    }

    private String normalizeSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private RegisteredSacco resolveRegisteredSacco(String saccoId) {
        return registeredSaccoRepository.findById(saccoId)
            .orElseThrow(() -> new IllegalStateException("Select an institution from the list."));
    }

    private void requireRegisteredStation(String saccoId, String stationId) {
        saccoStationRepository.findBySaccoIdAndStationIdAndActiveTrue(saccoId, stationId)
            .orElseThrow(() -> new IllegalStateException("Select a valid branch."));
    }

    public record VerifiedExternalMember(
        String memberNo,
        String email,
        String fullName,
        String saccoId,
        String stationId,
        String institutionName
    ) {
    }
}
