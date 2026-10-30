package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.LoginUsername;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberRegistrationService {
    private final MemberRepository memberRepository;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final NameSignatureService nameSignatureService;

    @Transactional
    public Member register(MemberRegistrationForm form) {
        VerifiedExternalMember verified = verifyExternalMember(form);
        return registerKnown(form,verified,false);
    }

    /** The onboarding service supplies an authorized, existing scope; no policy defaults or signature are invented. */
    @Transactional
    public Member registerAssisted(MemberRegistrationForm form,String clientNumber,String fullName,String institution,String branch) {
        var registered=resolveRegisteredSacco(institution);
        requireRegisteredStation(institution,branch);
        var verified=new VerifiedExternalMember(normalizeMemberNo(clientNumber),normalizeEmail(form.getEmail()),
            nameSignatureService.requireFullName(fullName,"Enter your full names."),institution,branch,registered.getSaccoName());
        return registerKnown(form,verified,true);
    }

    private Member registerKnown(MemberRegistrationForm form,VerifiedExternalMember verified,boolean assisted) {
        log.info("Client registration requested: saccoId={}", verified.saccoId());
        String username = assisted ? null : requireUsernameAvailable(form.getUsername());
        String normalizedPhone = normalizePhone(form.getPhone());
        String employeeNumber = EmployeeNumber.normalize(form.getEmployeeNumber());
        ensureLocalUniqueness(verified.memberNo(), verified.email(), normalizedPhone,
            employeeNumber, verified.saccoId(), verified.stationId());
        String memberNo = assisted ? verified.memberNo() : allocateClientNumber();
        if(!assisted)resolveLocalSacco(verified);
        OffsetDateTime now = OffsetDateTime.now();
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId(verified.saccoId())
            .memberNo(memberNo)
            .username(username)
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
            .passwordHash(passwordEncoder.encode(form.getPassword()))
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

        userSettingsRepository.save(UserSettings.builder()
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

    public VerifiedExternalMember verifyExternalMember(MemberRegistrationForm form) {
        String email = normalizeEmail(form.getEmail());
        if (java.util.stream.Stream.of(form.getFirstName(), form.getMiddleName(), form.getLastName())
            .anyMatch(name -> name == null || name.length() > 80 || !name.strip().matches("\\S+"))) {
            throw new IllegalStateException("Enter your first, middle and last name.");
        }
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
        if (memberRepository.existsByEmailIgnoreCase(email)) {
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

    private SaccoSettings resolveLocalSacco(VerifiedExternalMember verified) {
        OffsetDateTime now = OffsetDateTime.now();
        return saccoSettingsRepository.findById(verified.saccoId())
            .map(existing -> {
                existing.setExternalStationId(verified.stationId());
                existing.setExternalSaccoName(normalizeSpaces(verified.institutionName()));
                existing.setUpdatedAt(now);
                return saccoSettingsRepository.save(existing);
            })
            .orElseGet(() -> saccoSettingsRepository.save(SaccoSettings.builder()
                .saccoId(verified.saccoId())
                .externalStationId(verified.stationId())
                .externalSaccoName(normalizeSpaces(verified.institutionName()))
                .requiredGuarantors(3)
                .boardSize(3)
                .boardQuorum(2)
                .maxLoanSavingsRatio(new BigDecimal("0.3333"))
                .applicationFee(new BigDecimal("15000.00"))
                .defaultLanguage("en")
                .createdAt(now)
                .updatedAt(now)
                .build()));
    }

    private String normalizeMemberNo(String value) {
        return normalizeSpaces(value).toUpperCase();
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
