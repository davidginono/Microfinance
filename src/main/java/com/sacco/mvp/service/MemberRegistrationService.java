package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.AuditEventStatus;
import com.sacco.mvp.domain.LoginUsername;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberRegistrationService {
    private final MemberRepository memberRepository;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final NameSignatureService nameSignatureService;
    private final AuditService auditService;
    private final EmailOtpService emailOtpService;

    @Transactional
    public Member register(MemberRegistrationForm form) {
        return register(form, null);
    }

    /** The controller supplies this context only after consuming an OTP bound to the same client. */
    @Transactional
    public Member register(MemberRegistrationForm form, UUID activationClientId) {
        VerifiedExternalMember verified = verifyExternalMember(form);
        lockRegistrationContacts(form, verified);
        var existing = provisionalForActivation(form, verified);
        if (!java.util.Objects.equals(existing.map(Member::getId).orElse(null), activationClientId))
            throw new IllegalStateException("Registration details changed. Request a new code.");
        if (existing.isPresent()) {
            Member client = memberRepository.findForClientAccess(existing.get().getId(), verified.saccoId(), verified.stationId()).orElseThrow();
            requireProvisionalMatch(client, form, verified);
            String username = requireUsernameAvailable(form.getUsername()), password = passwordEncoder.encode(form.getPassword());
            try {
                if (memberRepository.activateClientPortal(client.getId(), verified.saccoId(), verified.stationId(), username, password) != 1)
                    throw new IllegalStateException("This client already has portal access. Sign in instead.");
            } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
                if (java.util.Objects.toString(conflict.getMostSpecificCause().getMessage(), "").contains("uq_members_username")) throw new UsernameTakenException();
                throw conflict;
            }
            // Portal activation does not invent KYC, a signature, or a new client identity.
            client.setUsername(username);client.setPasswordHash(password);client.setClientPortalEnabled(true);
            userSettingsRepository.save(UserSettings.builder().memberId(client.getId()).language("en")
                .notificationPrefs("{}").createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build());
            auditService.logEvent("CLIENT_PORTAL",client.getId(),"CLIENT_PORTAL_ACTIVATED",client.getId(),AuditEventStatus.SUCCESS,
                "Client portal activation","CLIENT",client.getId().toString(),client.getSaccoId(),client.getStationId(),java.util.Map.of());
            return client;
        }
        return registerKnown(form,verified,false);
    }

    /** Called before issuing registration OTP; activation must use the client's stored contact and scope. */
    @Transactional(readOnly = true)
    public UUID validatePublicRegistration(MemberRegistrationForm form, VerifiedExternalMember verified) {
        var existing=provisionalForActivation(form, verified);
        if (existing.isEmpty())
            ensureLocalUniqueness("", verified.email(), form.getPhone(), form.getEmployeeNumber(), verified.saccoId(), verified.stationId());
        return existing.map(Member::getId).orElse(null);
    }

    private java.util.Optional<Member> provisionalForActivation(MemberRegistrationForm form, VerifiedExternalMember verified) {
        var matches = memberRepository.findTop2ByEmailIgnoreCase(verified.email());
        if (matches.isEmpty()) return java.util.Optional.empty();
        if (matches.size() != 1) throw new IllegalStateException("Registration details do not match an available client record.");
        Member client = matches.getFirst();
        requireProvisionalMatch(client, form, verified);
        return java.util.Optional.of(client);
    }

    private void requireProvisionalMatch(Member client, MemberRegistrationForm form, VerifiedExternalMember verified) {
        if (client.isClientPortalEnabled() || !client.isMemberAccess() || client.getStatus() != MemberStatus.ACTIVE
            || !client.getStaffRolesResolved().isEmpty() || !java.util.Objects.equals(client.getSaccoId(), verified.saccoId())
            || !java.util.Objects.equals(client.getStationId(), verified.stationId())
            || !normalizeSpaces(client.getFullName()).equalsIgnoreCase(normalizeSpaces(verified.fullName()))
            || !java.util.Objects.equals(client.getPhone(), normalizePhone(form.getPhone()))
            || !normalizeEmail(client.getEmail()).equals(verified.email())
            || !java.util.Objects.equals(EmployeeNumber.normalize(client.getEmployeeNumber()), EmployeeNumber.normalize(form.getEmployeeNumber())))
            throw new IllegalStateException("Registration details do not match an available client record.");
    }

    /** The onboarding service supplies an authorized, existing scope; no policy defaults or signature are invented. */
    @Transactional
    public Member registerAssisted(MemberRegistrationForm form,String fullName,String institution,String branch) {
        var registered=resolveRegisteredSacco(institution);
        requireRegisteredStation(institution,branch);
        var verified=new VerifiedExternalMember("",normalizeEmail(form.getEmail()).isEmpty()?null:normalizeEmail(form.getEmail()),
            nameSignatureService.requireFullName(fullName,"Enter your full names."),institution,branch,registered.getSaccoName());
        return registerKnown(form,verified,true);
    }

    private Member registerKnown(MemberRegistrationForm form,VerifiedExternalMember verified,boolean assisted) {
        log.info("Client registration requested: saccoId={}", verified.saccoId());
        if (assisted) lockRegistrationContacts(form, verified);
        String username = assisted ? null : requireUsernameAvailable(form.getUsername());
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
            .username(username)
            .clientPortalEnabled(!assisted)
            .employeeNumber(employeeNumber)
            .stationId(verified.stationId())
            .fullName(verified.fullName())
            .phone(normalizedPhone)
            .email(verified.email())
            .signatureText(assisted?null:nameSignatureService.signatureFromFullName(verified.fullName()))
            .signatureRegisteredAt(assisted?null:now)
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .rank(nextMemberRank(verified.saccoId()))
            .passwordHash(assisted ? "!NO_PORTAL_ACCESS" : passwordEncoder.encode(form.getPassword()))
            .createdAt(now)
            .profileLastSyncedAt(now)
            .build();

        Member savedMember;
        try {
            savedMember = memberRepository.saveAndFlush(member);
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            if (conflict.getMostSpecificCause().getMessage() != null
                && conflict.getMostSpecificCause().getMessage().contains("uq_members_username")) {
                throw new UsernameTakenException();
            }
            throw conflict;
        }

        if (!assisted) userSettingsRepository.save(UserSettings.builder()
            .memberId(savedMember.getId())
            .language("en")
            .notificationPrefs("{}")
            .createdAt(now)
            .updatedAt(now)
            .build());

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
        if(current.isClientPortalEnabled()||!current.getStaffRolesResolved().isEmpty())throw new IllegalStateException("Use the account profile to change contact details.");
        if(java.util.Objects.equals(before.getFullName(),name)&&java.util.Objects.equals(before.getPhone(),normalizedPhone)&&java.util.Objects.equals(before.getEmail(),normalizedEmail))return;
        if((normalizedEmail!=null&&memberRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail,id))||memberRepository.existsByPhoneAndIdNot(normalizedPhone,id))throw new IllegalStateException("That email address or phone is already registered.");
        if(memberRepository.correctProvisionalContact(id,actor.getSaccoId(),actor.getStationId(),name,normalizedPhone,normalizedEmail,before.getFullName(),java.util.Objects.toString(before.getPhone(),""),java.util.Objects.toString(before.getEmail(),""))!=1)
            throw new IllegalStateException("Client details changed. Refresh and try again.");
        emailOtpService.invalidateClientRegistration(id);
        auditService.logEvent("CLIENT",id,"CLIENT_PROVISIONAL_CONTACT_CORRECTED",actor.getMemberId(),AuditEventStatus.SUCCESS,"Provisional client details updated","CLIENT",id.toString(),actor.getSaccoId(),actor.getStationId(),java.util.Map.of());
    }

    public VerifiedExternalMember verifyExternalMember(MemberRegistrationForm form) {
        String email = normalizeEmail(form.getEmail());
        if (java.util.stream.Stream.of(form.getFirstName(), form.getLastName())
            .anyMatch(name -> name == null || name.length() > 80 || !name.strip().matches("\\S+"))) {
            throw new IllegalStateException("Enter your first and last name.");
        }
        if(form.getMiddleName()!=null&&!form.getMiddleName().isBlank()&&(form.getMiddleName().length()>80||!form.getMiddleName().strip().matches("\\S+")))
            throw new IllegalStateException("Enter one middle name, or leave it blank.");
        String fullName = nameSignatureService.requireFullName(form.getFullName(), "Enter your full names.");
        String saccoId = normalizeSaccoId(form.getSaccoId());
        String stationId = normalizeStationId(form.getStationId());
        RegisteredSacco registeredSacco = resolveRegisteredSacco(saccoId);
        requireRegisteredStation(saccoId, stationId);
        return new VerifiedExternalMember(
            "",
            email,
            fullName,
            saccoId,
            stationId,
            registeredSacco.getSaccoName()
        );
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

    public boolean employeeNumberAvailable(String employeeNumber, String saccoId, String stationId) {
        String normalizedEmployeeNumber;
        try {
            normalizedEmployeeNumber = EmployeeNumber.normalize(employeeNumber);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        String normalizedSaccoId = normalizeSaccoId(saccoId);
        String normalizedStationId = normalizeStationId(stationId);
        return normalizedEmployeeNumber != null
            && !normalizedSaccoId.isBlank()
            && !normalizedStationId.isBlank()
            && registeredSaccoRepository.existsById(normalizedSaccoId)
            && saccoStationRepository.findBySaccoIdAndStationIdAndActiveTrue(normalizedSaccoId, normalizedStationId).isPresent()
            && !memberRepository.existsBySaccoIdAndStationIdIgnoreCaseAndEmployeeNumberIgnoreCase(
                normalizedSaccoId, normalizedStationId, normalizedEmployeeNumber);
    }

    @Transactional(readOnly = true)
    public boolean usernameAvailable(String value) {
        String username = LoginUsername.normalize(value);
        return LoginUsername.isValid(username) && !memberRepository.existsByUsername(username);
    }

    public String requireUsernameAvailable(String value) {
        String username = LoginUsername.normalize(value);
        if (!LoginUsername.isValid(username)) {
            throw new IllegalStateException("Use 3–32 letters, numbers, dots, underscores or hyphens, starting with a letter.");
        }
        if (memberRepository.existsByUsername(username)) {
            throw new UsernameTakenException();
        }
        return username;
    }

    public static final class UsernameTakenException extends IllegalStateException {
        public UsernameTakenException() {
            super("This username is already taken.");
        }
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
