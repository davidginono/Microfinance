package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SavingsAccount;
import com.sacco.mvp.domain.UserSettings;
import com.sacco.mvp.integration.foresight.ForesightAccountSummary;
import com.sacco.mvp.integration.foresight.ExternalDirectoryLookupException;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.ForesightMemberProfile;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.repository.SavingsAccountRepository;
import com.sacco.mvp.repository.UserSettingsRepository;
import com.sacco.mvp.web.form.MemberRegistrationForm;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberRegistrationService {
    private final MemberRepository memberRepository;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final ForesightDirectoryService foresightDirectoryService;

    @Transactional
    public Member register(MemberRegistrationForm form) {
        log.info("Member registration requested: memberNo={}, email={}, saccoId={}",
            form.getMemberNo(), form.getEmail(), form.getSaccoId());
        VerifiedExternalMember verified = verifyExternalMember(form);
        String normalizedPhone = normalizePhone(form.getPhone());
        ensureLocalUniqueness(verified.memberNo(), verified.email(), normalizedPhone);
        SaccoSettings sacco = resolveLocalSacco(verified);
        OffsetDateTime now = OffsetDateTime.now();
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId(sacco.getSaccoId())
            .memberNo(verified.memberNo())
            .stationId(verified.stationId())
            .fullName(verified.fullName())
            .phone(normalizedPhone)
            .email(verified.email())
            .signatureText(normalizeSignatureText(form.getSignatureText()))
            .signatureRegisteredAt(now)
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .rank(nextMemberRank(sacco.getSaccoId()))
            .passwordHash(passwordEncoder.encode(form.getPassword()))
            .createdAt(now)
            .profileLastSyncedAt(now)
            .build();

        Member savedMember = memberRepository.save(member);

        savingsAccountRepository.save(SavingsAccount.builder()
            .id(UUID.randomUUID())
            .memberId(savedMember.getId())
            .availableBalance(defaultAmount(verified.accountSummary().savingsBalance()))
            .sharesBalance(defaultAmount(verified.accountSummary().sharesBalance()))
            .depositsBalance(defaultAmount(verified.accountSummary().depositsBalance()))
            .updatedAt(now)
            .summaryLastSyncedAt(now)
            .build());

        userSettingsRepository.save(UserSettings.builder()
            .memberId(savedMember.getId())
            .language("en")
            .notificationPrefs("{}")
            .createdAt(now)
            .updatedAt(now)
            .build());

        log.info("Member registration completed successfully: memberNo={}, localMemberId={}, saccoId={}",
            savedMember.getMemberNo(), savedMember.getId(), savedMember.getSaccoId());
        return savedMember;
    }

    public VerifiedExternalMember verifyExternalMember(MemberRegistrationForm form) {
        String memberNo = normalizeMemberNo(form.getMemberNo());
        String email = normalizeEmail(form.getEmail());
        String fullName = normalizeSpaces(form.getFullName());
        String saccoId = normalizeSaccoId(form.getSaccoId());
        String stationId = normalizeStationId(form.getStationId());
        RegisteredSacco registeredSacco = resolveRegisteredSacco(saccoId);
        requireRegisteredStation(saccoId, stationId);

        log.info("Starting external registration verification: memberNo={}, email={}, enteredSaccoId={}",
            memberNo, email, saccoId);
        ForesightMemberProfile emailProfile;
        try {
            emailProfile = requireProfile(foresightDirectoryService.fetchMemberProfileByEmailV2(email),
                "Wrong email or not a SACCO member.");
        } catch (ExternalDirectoryLookupException ex) {
            if (ex.getStatusCode() == 404) {
                throw new IllegalStateException("Wrong email or not a SACCO member.");
            }
            if (ex.getStatusCode() >= 500) {
                throw new IllegalStateException("Server issue, try again later.");
            }
            throw new IllegalStateException("We could not check your details right now. Please try again.");
        }
        validateProfileAgainstForm(emailProfile, memberNo, registeredSacco, stationId);
        log.info("Email-based external verification matched memberNo={} and saccoName={}",
            emailProfile.memberNo(), emailProfile.saccoName());

        ForesightAccountSummary accountSummary = requireAccountSummary(
            foresightDirectoryService.fetchAccountSummary(emailProfile.memberNo(), emailProfile.stationId()));
        log.info("External registration verification succeeded: memberNo={}, stationId={}, saccoName={}",
            emailProfile.memberNo(), emailProfile.stationId(), emailProfile.saccoName());

        return new VerifiedExternalMember(
            memberNo,
            email,
            fullName,
            saccoId,
            stationId,
            emailProfile,
            accountSummary
        );
    }

    public void ensureLocalUniqueness(String memberNo, String email, String phone) {
        String normalizedPhone = normalizePhone(phone);
        if (memberRepository.findByMemberNo(memberNo).isPresent()) {
            log.info("Member registration blocked because member number already exists locally: {}", memberNo);
            throw new IllegalStateException("That member number is already registered in the MVP system.");
        }
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            log.info("Member registration blocked because email already exists locally: {}", email);
            throw new IllegalStateException("That email address is already registered in the MVP system.");
        }
        if (memberRepository.existsByPhone(normalizedPhone)) {
            log.info("Member registration blocked because phone already exists locally: {}", normalizedPhone);
            throw new IllegalStateException("That phone number is already registered in the MVP system.");
        }
    }

    private ForesightMemberProfile requireProfile(ForesightMemberProfile profile, String message) {
        if (profile == null || isBlank(profile.memberNo()) || isBlank(profile.stationId())) {
            throw new IllegalStateException(message);
        }
        return profile;
    }

    private ForesightAccountSummary requireAccountSummary(ForesightAccountSummary summary) {
        if (summary == null) {
            throw new IllegalStateException("The external account summary could not be loaded for that member.");
        }
        return summary;
    }

    private void validateProfileAgainstForm(ForesightMemberProfile profile, String memberNo,
                                            RegisteredSacco registeredSacco, String stationId) {
        java.util.List<String> issues = new java.util.ArrayList<>();
        if (!Objects.equals(memberNo, normalizeMemberNo(profile.memberNo()))) {
            issues.add("member number");
        }
        if (isBlank(profile.saccoName()) || !normalizeSpaces(registeredSacco.getSaccoName()).equalsIgnoreCase(normalizeSpaces(profile.saccoName()))) {
            issues.add("SACCO name");
        }
        if (!Objects.equals(stationId, normalizeStationId(profile.stationId()))) {
            issues.add("station ID");
        }
        if (!issues.isEmpty()) {
            if (issues.size() == 1) {
                throw new IllegalStateException("The " + issues.getFirst() + " does not match that email.");
            }
            if (issues.size() == 2) {
                throw new IllegalStateException("The " + issues.get(0) + " and " + issues.get(1) + " do not match that email.");
            }
            throw new IllegalStateException("The member number, SACCO name, and station ID do not match that email.");
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
                existing.setExternalSaccoName(normalizeSpaces(verified.profile().saccoName()));
                existing.setUpdatedAt(now);
                return saccoSettingsRepository.save(existing);
            })
                .orElseGet(() -> saccoSettingsRepository.save(SaccoSettings.builder()
                .saccoId(verified.saccoId())
                .externalStationId(verified.stationId())
                .externalSaccoName(normalizeSpaces(verified.profile().saccoName()))
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

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeMemberNo(String value) {
        return normalizeSpaces(value).toUpperCase();
    }

    private String normalizeEmail(String value) {
        return normalizeSpaces(value).toLowerCase();
    }

    private String normalizePhone(String value) {
        String normalized = normalizeSpaces(value);
        if (normalized.isBlank()) {
            throw new IllegalStateException("Enter your phone number.");
        }
        boolean hasPlus = normalized.startsWith("+");
        String digitsOnly = normalized.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 7) {
            throw new IllegalStateException("Enter a valid phone number.");
        }
        return hasPlus ? "+" + digitsOnly : digitsOnly;
    }

    private String normalizeSaccoId(String value) {
        return normalizeSpaces(value).toUpperCase();
    }

    private String normalizeStationId(String value) {
        return normalizeSpaces(value).toUpperCase();
    }

    private String normalizeSignatureText(String value) {
        String normalized = normalizeSpaces(value);
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 120).trim();
        }
        return normalized;
    }

    private String normalizeSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private RegisteredSacco resolveRegisteredSacco(String saccoId) {
        return registeredSaccoRepository.findById(saccoId)
            .orElseThrow(() -> new IllegalStateException("Select a SACCO name from the list."));
    }

    private void requireRegisteredStation(String saccoId, String stationId) {
        saccoStationRepository.findBySaccoIdAndStationIdAndActiveTrue(saccoId, stationId)
            .orElseThrow(() -> new IllegalStateException("Select a valid station ID."));
    }

    public record VerifiedExternalMember(
        String memberNo,
        String email,
        String fullName,
        String saccoId,
        String stationId,
        ForesightMemberProfile profile,
        ForesightAccountSummary accountSummary
    ) {
    }
}
