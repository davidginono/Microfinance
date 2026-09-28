package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
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
        log.info("Client registration requested: memberNo={}, email={}, saccoId={}",
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
            .signatureText(nameSignatureService.signatureFromFullName(verified.fullName()))
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

        userSettingsRepository.save(UserSettings.builder()
            .memberId(savedMember.getId())
            .language("en")
            .notificationPrefs("{}")
            .createdAt(now)
            .updatedAt(now)
            .build());

        log.info("Client registration completed successfully: memberNo={}, localMemberId={}, saccoId={}",
            savedMember.getMemberNo(), savedMember.getId(), savedMember.getSaccoId());
        return savedMember;
    }

    public VerifiedExternalMember verifyExternalMember(MemberRegistrationForm form) {
        String memberNo = normalizeMemberNo(form.getMemberNo());
        String email = normalizeEmail(form.getEmail());
        String fullName = nameSignatureService.requireFullName(form.getFullName(), "Enter your full names.");
        String saccoId = normalizeSaccoId(form.getSaccoId());
        String stationId = normalizeStationId(form.getStationId());
        RegisteredSacco registeredSacco = resolveRegisteredSacco(saccoId);
        requireRegisteredStation(saccoId, stationId);
        return new VerifiedExternalMember(
            memberNo,
            email,
            fullName,
            saccoId,
            stationId,
            registeredSacco.getSaccoName()
        );
    }

    public void ensureLocalUniqueness(String memberNo, String email, String phone) {
        String normalizedPhone = normalizePhone(phone);
        if (memberRepository.findByMemberNo(memberNo).isPresent()) {
            log.info("Client registration blocked because client number already exists locally: {}", memberNo);
            throw new IllegalStateException("That client number is already registered in this system.");
        }
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            log.info("Client registration blocked because email already exists locally: {}", email);
            throw new IllegalStateException("That email address is already registered in this system.");
        }
        if (memberRepository.existsByPhone(normalizedPhone)) {
            log.info("Client registration blocked because phone already exists locally: {}", normalizedPhone);
            throw new IllegalStateException("That phone number is already registered in this system.");
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
