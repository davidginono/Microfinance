package com.sacco.mvp.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class DataSeeder {
    private static final String IAA_SACCO_ID = "SACCO-ARUSHA-001";
    private static final String IAA_SACCO_NAME = "IAA SACCOS LTD";
    private static final String IAA_PRIMARY_STATION_ID = "STN789";
    private static final String IAA_SECONDARY_STATION_ID = "AR704";
    private static final String IAA_PRIMARY_STATION_LOCATION = "IAA Main Campus, Njiro Road, Arusha";
    private static final String IAA_SECONDARY_STATION_LOCATION = "Arusha Central Branch";
    private static final String DEVELOPMENT_SHARED_EMAIL = "ginonodavid625@gmail.com";
    private static final String MINOR_ADMIN_MEMBER_NO_PREFIX = "MINDEV";
    private static final String LOCAL_DEV_WORKFLOW_PASSWORD = "Pass123!";
    private static final String LOCAL_DEV_TAHA_BOARD003_PASSWORD = "Board003!";
    private static final Map<String, String> LEGACY_MEMBER_NO_BY_SEED_ID = Map.of(
        "66b82e0c-e983-4fc2-aad5-13157516bb77", "BRD003",
        "9713ec57-271c-4f54-82ba-8c009ba36e4d", "MEM007"
    );

    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.3333");
    private static final BigDecimal DEFAULT_INSURANCE_RATE = new BigDecimal("0.0150");
    private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("0.1000");
    private static final BigDecimal DEFAULT_MINIMUM_AMOUNT = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CHAPCHAP_EXTENDED_INTEREST_RATE = new BigDecimal("0.1200");
    private static final BigDecimal APPLICATION_FEE = new BigDecimal("15000.00");

    private final SaccoSettingsRepository saccoSettingsRepository;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final MemberRepository memberRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Value("${app.auth.local-dev-minor-admin-password:}")
    private String localDevMinorAdminPassword;

    @Bean
    @ConditionalOnProperty(prefix = "app.seed", name = "demo-data-enabled", havingValue = "true")
    CommandLineRunner seedApplicationData() {
        return args -> {
            OffsetDateTime now = OffsetDateTime.now();
            seedIaaSacco(now);
            seedLoanProducts(now);

            seedAccount(row(
                    "9ec44d7a-3a70-4f22-bfa0-9f1bd0b5a301", "2026-01-18T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Amina Admin", "ADM001",
                    "$2a$10$olMY7sGh3063PaHhSoyxXOGFSi3GEJqfN30Vp.melpQZAL/HYF/ry",
                    "0757000001", Position.ADMIN, "2026-03-23T14:51:02.637961+03:00", 1,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, false, null, null),
                null, null, null);
            seedAccount(row(
                    "22f4818f-d378-4d3e-99e0-6a5fa9d73f11", "2026-01-20T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Mary Manager", "MGR001",
                    "$2a$10$EqrpMnyom9NWaS.ysJGawe5teNMpRjwJUND7RDfNCtDKgPoE2suJG",
                    "0757000002", Position.MANAGER, "2026-03-23T14:51:03.09653+03:00", 1,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, false, null, null),
                null, null, null);
            seedAccount(row(
                    "5d25ee8d-1cc2-4f24-9f38-f74083e7cf21", "2026-01-22T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Ben Board One", "BRD001",
                    "$2a$10$.F8koOE8R5uBkAeTm80Hpe65/kt60kPCXTz/GgbrUw.dvTwrD7boe",
                    "0757000003", Position.BOARD, "2026-03-23T14:51:03.649947+03:00", 1,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, false, null, "Board B One"),
                null, null, null);
            seedAccount(row(
                    "1bf74fe9-2850-4627-a2aa-147e24f0c2a6", "2026-01-23T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Bea Board Two", "BRD002",
                    "$2a$10$3Z79EO5sqSeRfIquwDWZoeVjcYntMBPDYDkioJcv9GT.yUrW8ovVS",
                    "0757000004", Position.BOARD, "2026-03-23T14:51:04.234834+03:00", 2,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, false, null, "Ben B Two"),
                null, null, null);
            seedAccount(row(
                    "66b82e0c-e983-4fc2-aad5-13157516bb77", "2026-01-24T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Bob Board Three", "0101",
                    passwordEncoder.encode(LOCAL_DEV_TAHA_BOARD003_PASSWORD),
                    "0757000005", Position.BOARD, "2026-03-23T14:51:04.593618+03:00", 3,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_SECONDARY_STATION_ID, true, null, null),
                new BigDecimal("275000.00"), new BigDecimal("99000.00"), new BigDecimal("126000.00"));
            seedAccount(row(
                    "de54fdfb-b44b-4e09-973c-fbc008ea55b8", "2026-01-25T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Cathy Loan Officer", "CHR001",
                    passwordEncoder.encode(LOCAL_DEV_WORKFLOW_PASSWORD),
                    "0757000006", Position.LOAN_OFFICER, "2026-03-23T14:51:05.003517+03:00", 1,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, false, null, null),
                null, null, null);
            seedAccount(row(
                    "a7181a84-4fb7-4d48-953f-06d9e6e84c1a", "2026-01-26T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Andy Accountant", "ACC001",
                    passwordEncoder.encode(LOCAL_DEV_WORKFLOW_PASSWORD),
                    "0757000007", Position.ACCOUNTANT, "2026-03-23T14:51:05.403517+03:00", 1,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, false, null, null),
                null, null, null);
            seedAccount(row(
                    "f11a6997-568d-4699-a672-e5e1f6c6c1ef", "2026-01-27T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Doris Disbursement", "DIS001",
                    passwordEncoder.encode(LOCAL_DEV_WORKFLOW_PASSWORD),
                    "0757000008", Position.DISBURSEMENT_OFFICER, "2026-03-23T14:51:05.803517+03:00", 1,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, false, null, null),
                null, null, null);
            seedAccount(row(
                    "65a7f80e-641f-4b12-a232-d80d2103201a", "2026-03-23T13:03:33.355981+03:00",
                    "ginonodavid625@gmail.com", "JAMES KUMJA", "1111",
                    "$2a$10$m4gNiPA1bMrGPAcZNfb/wO8Q/c8K1MBAkWSAHnDlfNZnIlsMFpJ4i",
                    "0673054847", Position.BOARD, null, 4,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, null, false, null, null),
                null, null, null);

            Member alice = seedAccount(row(
                    "6c7ff573-a3c8-4cd1-91f9-b0a19aef9101", "2026-01-28T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Alice Member", "MEM001",
                    "$2a$10$EISvjLP9Y57pGYtgg2Jm/uxmCJ5PKabU1M/aamifC/puQBoUlWzRS",
                    "0757000101", Position.MEMBER, "2026-04-15T13:53:13.752713+03:00", 1,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, true, "2026-04-15T13:53:13.625321+03:00", "Alice Member"),
                new BigDecimal("450000.00"), new BigDecimal("180000.00"), new BigDecimal("250000.00"));
            Member alex = seedAccount(row(
                    "92d4ae9d-d6ca-42d0-ac4a-1ce1cf527969", "2026-01-29T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Alex Member", "MEM002",
                    "$2a$10$h4q3Wy22rhButWYokfCh9eC06G7Cn9zRn6YsG0PeZUYHchTvL3sLS",
                    "0757000102", Position.MEMBER, "2026-04-15T13:53:13.903739+03:00", 2,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, true, "2026-04-15T13:53:13.773177+03:00", "Alex Member"),
                new BigDecimal("380000.00"), new BigDecimal("150000.00"), new BigDecimal("210000.00"));
            Member asha = seedAccount(row(
                    "4f0d8da6-0931-45ad-b005-8e8cde536594", "2026-01-30T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Asha Member", "MEM003",
                    "$2a$10$SuUxpRjfbrwUnDNVh6evXu6QrezYm.zDmGg7hVpuFgLBdhuTQJMu2",
                    "0757000103", Position.MEMBER, "2026-04-15T13:53:14.05379+03:00", 3,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, true, "2026-04-15T13:53:13.916088+03:00", "Asha Member"),
                new BigDecimal("320000.00"), new BigDecimal("120000.00"), new BigDecimal("160000.00"));
            Member amos = seedAccount(row(
                    "7ee64686-e9b9-41e6-85bf-f119495b94b6", "2026-01-31T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Amos Member", "MEM004",
                    "$2a$10$9HYnvNRpVsk3OyeqtUOk4eYbMQoi5hxuWuJPlOn/DhKOeBpGL3DIi",
                    "0757000104", Position.MEMBER, "2026-04-15T13:53:14.191118+03:00", 4,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, true, "2026-04-15T13:53:14.067001+03:00", "Amos Member"),
                new BigDecimal("300000.00"), new BigDecimal("110000.00"), new BigDecimal("140000.00"));
            Member amina = seedAccount(row(
                    "95d06d4c-9fa2-46d4-a574-8ad4f66f5af7", "2026-02-01T23:20:43.514392+03:00",
                    "gpt872793@gmail.com", "Amina Member", "MEM005",
                    "$2a$10$SoKvs8gwlhdJLs4ojGfEuO/.ipuUfVkYZc.OMFK5LMebwzW0Xgbt2",
                    "0757000105", Position.MEMBER, "2026-04-15T13:53:14.302741+03:00", 5,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, true, "2026-04-15T13:53:14.202497+03:00", "Amina Member"),
                new BigDecimal("295000.00"), new BigDecimal("108000.00"), new BigDecimal("138000.00"));
            Member abdi = seedAccount(row(
                    "52fa43e5-3ff0-40f2-bbf6-290f9f8b153e", "2026-02-02T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Abdi Member", "MEM006",
                    "$2a$10$He01Lc23rx7.3eWaf4f4POO5OsCaq14GefT2Q8bafJt8heHqlRDMa",
                    "0757000106", Position.MEMBER, "2026-04-15T13:53:14.427274+03:00", 6,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, true, "2026-04-15T13:53:14.313712+03:00", "Abdi Member"),
                new BigDecimal("280000.00"), new BigDecimal("100000.00"), new BigDecimal("130000.00"));
            seedAccount(row(
                    "9713ec57-271c-4f54-82ba-8c009ba36e4d", "2026-02-03T23:20:43.514392+03:00",
                    "ginonodavid625@gmail.com", "Agnes Member", "0100",
                    "$2a$10$T9rOngjJsRFqhHn1FSs9hOtbjhpUR1Po2SSSxkAotp751DJ6nA6bW",
                    "0757000107", Position.MANAGER, "2026-04-15T13:53:14.56361+03:00", 8,
                    "TAHA SACCOS", MemberStatus.ACTIVE, IAA_SECONDARY_STATION_ID, true, "2026-04-15T13:53:14.442379+03:00", "Agnes Member"),
                new BigDecimal("270000.00"), new BigDecimal("98000.00"), new BigDecimal("125000.00"));
            seedAccount(row(
                    "b48d0a9e-2992-4251-ac61-4711239f3499", "2026-03-20T10:08:29.077462+03:00",
                    "ginonodavid625@gmail.com", "JOSEPH HAMAD URASSA", "1145",
                    "$2a$10$OYT3oVQZcOFGdbUpEwcyQOGmrAdQpC8sSFl1ilRFvvO8RHWalBUj2",
                    "+255657822049", Position.MEMBER, "2026-03-20T10:08:29.077462+03:00", 1,
                    "TAHA SACCOS", MemberStatus.ACTIVE, IAA_SECONDARY_STATION_ID, null, null, "Joseph U Urasa"),
                new BigDecimal("265000.00"), new BigDecimal("90000.00"), new BigDecimal("118000.00"));
            Member david = seedAccount(row(
                    "26d14862-f350-4424-8416-2572882a9557", "2026-03-26T09:53:23.771062+03:00",
                    "ginonodavid625@gmail.com", "David Ginono", "MEM008",
                    "OTP_ONLY_LOGIN",
                    null, Position.MEMBER, "2026-03-26T09:53:23.771062+03:00", 8,
                    "SACCO-ARUSHA-001", MemberStatus.ACTIVE, IAA_PRIMARY_STATION_ID, true, null, null),
                new BigDecimal("240000.00"), new BigDecimal("86000.00"), new BigDecimal("112000.00"));

            seedSampleLoans(now, alice, alex, asha, amos, amina, abdi, david);
            seedLocalDevelopmentMinorAdminAccess();
        };
    }

    private void seedIaaSacco(OffsetDateTime now) {
        registeredSaccoRepository.findById(IAA_SACCO_ID)
            .orElseGet(() -> registeredSaccoRepository.save(RegisteredSacco.builder()
                .saccoId(IAA_SACCO_ID)
                .saccoName(IAA_SACCO_NAME)
                .active(true)
                .createdAt(now.minusDays(90))
                .updatedAt(now)
                .build()));

        seedStation(IAA_PRIMARY_STATION_ID, IAA_PRIMARY_STATION_LOCATION, now);
        seedStation(IAA_SECONDARY_STATION_ID, IAA_SECONDARY_STATION_LOCATION, now);

        SaccoSettings sacco = saccoSettingsRepository.findById(IAA_SACCO_ID)
            .orElseGet(() -> SaccoSettings.builder()
                .saccoId(IAA_SACCO_ID)
                .createdAt(now.minusDays(90))
                .build());

        sacco.setExternalStationId(IAA_PRIMARY_STATION_ID);
        sacco.setExternalSaccoName(IAA_SACCO_NAME);
        sacco.setRequiredGuarantors(3);
        sacco.setBoardSize(3);
        sacco.setBoardQuorum(2);
        sacco.setMaxLoanSavingsRatio(DEFAULT_RATIO);
        sacco.setApplicationFee(APPLICATION_FEE);
        sacco.setDefaultLanguage("en");
        if (sacco.getCreatedAt() == null) {
            sacco.setCreatedAt(now.minusDays(90));
        }
        sacco.setUpdatedAt(now);
        saccoSettingsRepository.save(sacco);
    }

    private void seedStation(String stationId, String addressLocation, OffsetDateTime now) {
        if (stationId == null || stationId.isBlank()) {
            return;
        }
        SaccoStation station = saccoStationRepository.findBySaccoIdAndStationId(IAA_SACCO_ID, stationId)
            .orElseGet(() -> saccoStationRepository.save(SaccoStation.builder()
                .id(UUID.randomUUID())
                .saccoId(IAA_SACCO_ID)
                .stationId(stationId)
                .active(true)
                .createdAt(now.minusDays(90))
                .updatedAt(now)
                .build()));
        if (station.getAddressLocation() == null || station.getAddressLocation().isBlank()) {
            station.setAddressLocation(addressLocation);
            station.setUpdatedAt(now);
            saccoStationRepository.save(station);
        }
    }

    private void seedLoanProducts(OffsetDateTime now) {
        seedLoanProduct(LoanType.LOAN_ADVANCE, 0, DEFAULT_RATIO, DEFAULT_INSURANCE_RATE, DEFAULT_INTEREST_RATE, 3, now);
        seedLoanProduct(LoanType.EDUCATION_LOAN, 3, DEFAULT_RATIO, DEFAULT_INSURANCE_RATE, DEFAULT_INTEREST_RATE, 12, now);
        seedLoanProduct(LoanType.EMERGENCY_LOAN, 3, DEFAULT_RATIO, DEFAULT_INSURANCE_RATE, DEFAULT_INTEREST_RATE, 12, now);
        seedLoanProduct(LoanType.DEVELOPMENT_LOAN, 3, DEFAULT_RATIO, DEFAULT_INSURANCE_RATE, DEFAULT_INTEREST_RATE, 12, now);
    }

    private void seedLoanProduct(LoanType loanType,
                                 int guarantorsRequired,
                                 BigDecimal savingsRatio,
                                 BigDecimal insuranceRate,
                                 BigDecimal interestRate,
                                 int maxRepaymentMonths,
                                 OffsetDateTime now) {
        Optional<LoanProductSetting> existingProduct = loanProductSettingRepository.findAll().stream()
            .filter(existing -> Objects.equals(existing.getSaccoId(), IAA_SACCO_ID) && existing.getLoanType() == loanType)
            .findFirst();
        LoanProductSetting product = existingProduct
            .orElseGet(() -> LoanProductSetting.builder()
                .id(UUID.randomUUID())
                .saccoId(IAA_SACCO_ID)
                .loanType(loanType)
                .createdAt(now.minusDays(80))
                .build());

        if (product.getGuarantorsRequired() == null) {
            product.setGuarantorsRequired(guarantorsRequired);
        }
        if (product.getProductCode() == null || product.getProductCode().isBlank()) {
            product.setProductCode(loanType.defaultProductCode());
        }
        if (product.getProductDescription() == null || product.getProductDescription().isBlank()) {
            product.setProductDescription(loanType.defaultDescription());
        }
        if (product.getDisplayOrder() == null) {
            product.setDisplayOrder(loanType.getDisplayOrder());
        }
        if (product.getMinimumAmount() == null) {
            product.setMinimumAmount(DEFAULT_MINIMUM_AMOUNT);
        }
        if (product.getMaxLoanSavingsRatio() == null) {
            product.setMaxLoanSavingsRatio(savingsRatio);
        }
        if (product.getInsuranceRate() == null) {
            product.setInsuranceRate(insuranceRate);
        }
        if (product.getInterestRate() == null) {
            product.setInterestRate(interestRate);
        }
        if (product.getInterestMethod() == null) {
            product.setInterestMethod(InterestMethod.FLAT_RATE);
        }
        if (product.getMinRepaymentMonths() == null) {
            product.setMinRepaymentMonths(1);
        }
        if (product.getMaxRepaymentMonths() == null) {
            product.setMaxRepaymentMonths(maxRepaymentMonths);
        }
        if (product.getAllowApplicationWithActiveLoan() == null) {
            product.setAllowApplicationWithActiveLoan(false);
        }
        if (product.getManagerReviewRequired() == null) {
            product.setManagerReviewRequired(true);
        }
        if (product.getManagerPriority() == null) {
            product.setManagerPriority(1);
        }
        if (product.getLoanOfficerPriority() == null) {
            product.setLoanOfficerPriority(2);
        }
        if (product.getCommitteeReviewRequired() == null) {
            product.setCommitteeReviewRequired(true);
        }
        if (product.getCommitteePriority() == null) {
            product.setCommitteePriority(3);
        }
        if (product.getCommitteeMinimumVotes() == null) {
            product.setCommitteeMinimumVotes(2);
        }
        if (product.getCommitteeApprovalThreshold() == null) {
            product.setCommitteeApprovalThreshold(2);
        }
        if (product.getAccountantReviewRequired() == null) {
            product.setAccountantReviewRequired(true);
        }
        if (product.getAccountantPriority() == null) {
            product.setAccountantPriority(4);
        }
        if (product.getDisbursementOfficerRequired() == null) {
            product.setDisbursementOfficerRequired(true);
        }
        if (product.getProductStatus() == null) {
            product.setProductStatus(LoanProductStatus.ACTIVE);
        }
        if (product.getFormSchema() == null || product.getFormSchema().isBlank()) {
            product.setFormSchema(defaultLoanFormSchema());
        }
        if (product.getActive() == null) {
            product.setActive(true);
        }
        if (product.getCreatedAt() == null) {
            product.setCreatedAt(now.minusDays(80));
        }
        if (existingProduct.isEmpty()) {
            product.setUpdatedAt(now);
        }
        loanProductSettingRepository.save(product);
    }

    private Member seedAccount(SeedMemberRow row,
                               BigDecimal availableBalance,
                               BigDecimal sharesBalance,
                               BigDecimal depositsBalance) {
        Optional<Member> existingMember = resolveExistingSeedMember(row);
        Member member = existingMember
            .orElseGet(() -> Member.builder()
                .id(UUID.fromString(row.id()))
                .createdAt(row.createdAt())
                .build());
        boolean isNewMember = existingMember.isEmpty();

        if (isNewMember || member.getSaccoId() == null || member.getSaccoId().isBlank()) {
            member.setSaccoId(IAA_SACCO_ID);
        }
        member.setMemberNo(row.memberNo());
        if (isNewMember || member.getStationId() == null || member.getStationId().isBlank()) {
            member.setStationId(row.stationId());
        }
        if (isNewMember || member.getFullName() == null || member.getFullName().isBlank()) {
            member.setFullName(row.fullName());
        }
        if (isNewMember || member.getPhone() == null || member.getPhone().isBlank()) {
            member.setPhone(row.phone());
        }
        if (isNewMember || member.getEmail() == null || member.getEmail().isBlank()) {
            member.setEmail(resolveSeedEmail(row));
        }
        if (isNewMember || member.getSignatureText() == null || member.getSignatureText().isBlank()) {
            member.setSignatureText(row.signatureText());
        }
        if (isNewMember || member.getSignatureRegisteredAt() == null) {
            member.setSignatureRegisteredAt(row.signatureRegisteredAt());
        }
        if (isNewMember || !Objects.equals(member.getMemberAccount(), row.memberAccount())) {
            member.setMemberAccount(row.memberAccount());
        }
        if (isNewMember || member.getStatus() == null) {
            member.setStatus(row.status());
        }
        if (isNewMember || member.getPosition() == null) {
            member.setPosition(row.position());
        }
        if (isNewMember || member.getRank() == null) {
            member.setRank(row.rank());
        }
        if (isNewMember || member.getPasswordHash() == null || member.getPasswordHash().isBlank()) {
            member.setPasswordHash(row.passwordHash());
        }
        if (isNewMember || member.getProfileLastSyncedAt() == null) {
            member.setProfileLastSyncedAt(row.profileLastSyncedAt());
        }
        if (member.getCreatedAt() == null) {
            member.setCreatedAt(row.createdAt());
        }
        if (isNewMember || member.getStaffRolesResolved().isEmpty()) {
            member.setStaffRoles(resolveStaffRoles(row.position()));
        }

        Member saved = memberRepository.save(member);
        if (saved.isMemberAccess()) {
            seedSavingsAccount(saved.getId(), availableBalance, sharesBalance, depositsBalance);
            seedUserSettings(saved.getId());
        }
        return saved;
    }

    private Optional<Member> resolveExistingSeedMember(SeedMemberRow row) {
        UUID seedId = UUID.fromString(row.id());
        Optional<Member> byId = memberRepository.findById(seedId);
        if (byId.isPresent()) {
            return byId;
        }
        Optional<Member> byCurrentMemberNo = memberRepository.findByMemberNo(row.memberNo());
        if (byCurrentMemberNo.isPresent()) {
            return byCurrentMemberNo;
        }
        String legacyMemberNo = LEGACY_MEMBER_NO_BY_SEED_ID.get(row.id());
        if (legacyMemberNo == null || legacyMemberNo.isBlank()) {
            return Optional.empty();
        }
        return memberRepository.findByMemberNo(legacyMemberNo);
    }

    private void seedLocalDevelopmentMinorAdminAccess() {
        String password = blankToNull(localDevMinorAdminPassword);
        if (password == null) {
            return;
        }

        List<Member> minorAdmins = memberRepository.findAll().stream()
            .filter(member -> member.getPosition() == Position.MINOR_ADMIN)
            .sorted(Comparator.comparing(Member::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        int sequence = 1;
        for (Member member : minorAdmins) {
            if (member.getMemberNo() == null || member.getMemberNo().isBlank()) {
                member.setMemberNo(MINOR_ADMIN_MEMBER_NO_PREFIX + String.format("%03d", sequence));
            }
            member.setPasswordHash(passwordEncoder.encode(password));
            memberRepository.save(member);
            sequence++;
        }
    }

    private String resolveSeedEmail(SeedMemberRow row) {
        if (row.position() == Position.MINOR_ADMIN && row.email() != null && !row.email().isBlank()) {
            return row.email();
        }
        return DEVELOPMENT_SHARED_EMAIL;
    }

    private LinkedHashSet<Position> resolveStaffRoles(Position position) {
        if (position == null || !position.isStaffRole()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(List.of(position));
    }

    private void seedSavingsAccount(UUID memberId,
                                    BigDecimal availableBalance,
                                    BigDecimal sharesBalance,
                                    BigDecimal depositsBalance) {
        OffsetDateTime now = OffsetDateTime.now();
        SavingsAccount account = savingsAccountRepository.findByMemberId(memberId)
            .orElseGet(() -> SavingsAccount.builder()
                .id(UUID.randomUUID())
                .memberId(memberId)
                .build());

        account.setAvailableBalance(zeroIfNull(availableBalance));
        account.setSharesBalance(zeroIfNull(sharesBalance));
        account.setDepositsBalance(zeroIfNull(depositsBalance));
        account.setUpdatedAt(now);
        account.setSummaryLastSyncedAt(now);
        savingsAccountRepository.save(account);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value;
    }

    private void seedUserSettings(UUID memberId) {
        if (userSettingsRepository.existsById(memberId)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        userSettingsRepository.save(UserSettings.builder()
            .memberId(memberId)
            .language("en")
            .notificationPrefs("{}")
            .createdAt(now)
            .updatedAt(now)
            .build());
    }

    private void seedSampleLoans(OffsetDateTime now,
                                 Member alice,
                                 Member alex,
                                 Member asha,
                                 Member amos,
                                 Member amina,
                                 Member abdi,
                                 Member david) {
        seedAcceptedLoan(
            "e94127fd-b8fd-48ec-a0d6-c53ed2ddb4db",
            alice,
            LoanType.EMERGENCY_LOAN,
            new BigDecimal("30000.00"),
            2,
            now.minusDays(18),
            now.plusDays(14));

        seedArchivedAcceptedLoan(
            "d6456f8e-64ae-4df2-ac8d-39d8ef3dce30",
            alice,
            LoanType.LOAN_ADVANCE,
            new BigDecimal("12000.00"),
            1,
            now.minusDays(90),
            now.minusDays(30));

        seedRejectedLoan(
            "e4b04814-6ebd-488d-b5ac-34cd8c4dca65",
            alex,
            LoanType.DEVELOPMENT_LOAN,
            new BigDecimal("90000.00"),
            12,
            LoanStatus.MANAGER_REJECTED,
            "Increase your loan please",
            now.minusDays(7));

        seedReadyForManagerLoan(
            "d3dbb7c1-d295-4dbc-b14b-f2d0bded0f46",
            alex,
            LoanType.EDUCATION_LOAN,
            new BigDecimal("45000.00"),
            6,
            now.minusDays(4),
            List.of(amos, amina, abdi));

        seedAwaitingGuarantorsLoan(
            "321f3127-a5a9-4cc2-a9ce-14d77a9460f4",
            asha,
            LoanType.DEVELOPMENT_LOAN,
            6,
            new BigDecimal("60000.00"),
            now.minusDays(2),
            List.of(amos, amina, abdi));

        seedAllGuarantorsApprovedLoan(
            "62f735b0-4ef2-4d19-a52c-0adad8d53398",
            david,
            LoanType.EDUCATION_LOAN,
            8,
            new BigDecimal("50000.00"),
            now.minusDays(3),
            List.of(amos, amina, abdi));
    }

    private void seedAcceptedLoan(String loanId,
                                  Member member,
                                  LoanType loanType,
                                  BigDecimal amount,
                                  int tenorMonths,
                                  OffsetDateTime createdAt,
                                  OffsetDateTime finalDueAt) {
        if (loanApplicationRepository.existsById(UUID.fromString(loanId))) {
            return;
        }
        BigDecimal installmentAmount = monthlyRepayment(amount, tenorMonths, effectiveInterestRate(loanType, tenorMonths));
        LocalDate disbursementDate = createdAt.toLocalDate().plusDays(3);
        LoanApplication app = baseLoan(loanId, member, loanType, amount, tenorMonths, createdAt, LoanStatus.FINAL_APPROVED);
        app.setSubmittedAt(createdAt.plusHours(6));
        app.setDisbursementDate(disbursementDate);
        app.setFirstRepaymentDate(disbursementDate.plusMonths(1));
        app.setFinalDueDate(finalDueAt.toLocalDate());
        app.setRepaymentFrequency(RepaymentFrequency.MONTHLY);
        app.setInstallmentAmount(installmentAmount);
        app.setDisbursementReference("DISB-" + member.getMemberNo());
        app.setDisbursementNotes("Seeded active loan for dashboard repayment testing.");
        app.setRepaymentScheduleJson("[]");
        loanApplicationRepository.save(app);
    }

    private void seedArchivedAcceptedLoan(String loanId,
                                          Member member,
                                          LoanType loanType,
                                          BigDecimal amount,
                                          int tenorMonths,
                                          OffsetDateTime createdAt,
                                          OffsetDateTime finalDueAt) {
        if (loanApplicationRepository.existsById(UUID.fromString(loanId))) {
            return;
        }
        BigDecimal installmentAmount = monthlyRepayment(amount, tenorMonths, effectiveInterestRate(loanType, tenorMonths));
        LocalDate disbursementDate = createdAt.toLocalDate().plusDays(2);
        LoanApplication app = baseLoan(loanId, member, loanType, amount, tenorMonths, createdAt, LoanStatus.PAID);
        app.setSubmittedAt(createdAt.plusHours(4));
        app.setDisbursementDate(disbursementDate);
        app.setFirstRepaymentDate(disbursementDate.plusMonths(1));
        app.setFinalDueDate(finalDueAt.toLocalDate());
        app.setRepaymentFrequency(RepaymentFrequency.MONTHLY);
        app.setInstallmentAmount(installmentAmount);
        app.setDisbursementReference("ARCH-" + member.getMemberNo());
        app.setDisbursementNotes("Seeded archived approved loan.");
        app.setPaidAt(finalDueAt.plusDays(2));
        app.setRepaymentScheduleJson("[]");
        loanApplicationRepository.save(app);
    }

    private void seedRejectedLoan(String loanId,
                                  Member member,
                                  LoanType loanType,
                                  BigDecimal amount,
                                  int tenorMonths,
                                  LoanStatus status,
                                  String reason,
                                  OffsetDateTime createdAt) {
        if (loanApplicationRepository.existsById(UUID.fromString(loanId))) {
            return;
        }
        LoanApplication app = baseLoan(loanId, member, loanType, amount, tenorMonths, createdAt, status);
        app.setSubmittedAt(createdAt.plusHours(2));
        app.setUpdatedAt(createdAt.plusDays(1));
        app.setFormData(toJson(Map.of(
            "purpose", "Business growth",
            "nationalId", "ID-" + member.getMemberNo(),
            "employerName", "Self employed",
            "hasExistingLoan", false,
            "decisionReason", reason
        )));
        loanApplicationRepository.save(app);
    }

    private void seedReadyForManagerLoan(String loanId,
                                         Member member,
                                         LoanType loanType,
                                         BigDecimal amount,
                                         int tenorMonths,
                                         OffsetDateTime createdAt,
                                         List<Member> guarantors) {
        if (loanApplicationRepository.existsById(UUID.fromString(loanId))) {
            return;
        }
        LoanApplication app = baseLoan(loanId, member, loanType, amount, tenorMonths, createdAt, LoanStatus.READY_FOR_MANAGER);
        app.setSubmittedAt(createdAt.plusHours(3));
        app.setSelectedGuarantors(toJson(guarantors.stream().map(Member::getId).toList()));
        loanApplicationRepository.save(app);
    }

    private void seedAwaitingGuarantorsLoan(String loanId,
                                            Member applicant,
                                            LoanType loanType,
                                            int tenorMonths,
                                            BigDecimal amount,
                                            OffsetDateTime createdAt,
                                            List<Member> guarantors) {
        UUID applicationId = UUID.fromString(loanId);
        if (loanApplicationRepository.existsById(applicationId)) {
            return;
        }
        LoanApplication app = baseLoan(loanId, applicant, loanType, amount, tenorMonths, createdAt, LoanStatus.AWAITING_GUARANTORS);
        app.setSubmittedAt(createdAt.plusHours(1));
        app.setSelectedGuarantors(toJson(guarantors.stream().map(Member::getId).toList()));
        loanApplicationRepository.save(app);

        for (Member guarantor : guarantors) {
            guarantorRequestRepository.save(GuarantorRequest.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(applicationId)
                .guarantorMemberId(guarantor.getId())
                .status(GuarantorRequestStatus.PENDING)
                .requestedAmount(null)
                .createdAt(createdAt.plusHours(1))
                .build());
        }
    }

    private void seedAllGuarantorsApprovedLoan(String loanId,
                                               Member applicant,
                                               LoanType loanType,
                                               int tenorMonths,
                                               BigDecimal amount,
                                               OffsetDateTime createdAt,
                                               List<Member> guarantors) {
        UUID applicationId = UUID.fromString(loanId);
        if (loanApplicationRepository.existsById(applicationId)) {
            return;
        }
        LoanApplication app = baseLoan(loanId, applicant, loanType, amount, tenorMonths, createdAt, LoanStatus.ALL_GUARANTORS_APPROVED);
        app.setSubmittedAt(createdAt.plusHours(1));
        app.setSelectedGuarantors(toJson(guarantors.stream().map(Member::getId).toList()));
        loanApplicationRepository.save(app);

        for (Member guarantor : guarantors) {
            guarantorRequestRepository.save(GuarantorRequest.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(applicationId)
                .guarantorMemberId(guarantor.getId())
                .status(GuarantorRequestStatus.APPROVED)
                .requestedAmount(null)
                .committedAmount(null)
                .createdAt(createdAt.plusHours(1))
                .decidedAt(createdAt.plusHours(8))
                .build());
        }
    }

    private LoanApplication baseLoan(String loanId,
                                     Member member,
                                     LoanType loanType,
                                     BigDecimal amount,
                                     int tenorMonths,
                                     OffsetDateTime createdAt,
                                     LoanStatus status) {
        int requiredGuarantors = loanType == LoanType.LOAN_ADVANCE ? 0 : 3;
        return LoanApplication.builder()
            .id(UUID.fromString(loanId))
            .saccoId(IAA_SACCO_ID)
            .stationId(member.getStationId())
            .applicantMemberId(member.getId())
            .loanType(loanType)
            .amount(amount)
            .tenorMonths(tenorMonths)
            .status(status)
            .formData(defaultFormData(member, loanType))
            .requiredGuarantors(requiredGuarantors)
            .policySnapshot(defaultPolicySnapshot(member, amount, requiredGuarantors))
            .selectedGuarantors("[]")
            .financialSnapshot(defaultFinancialSnapshot(loanType, amount, tenorMonths))
            .attachmentsJson("[]")
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    }

    private String defaultLoanFormSchema() {
        return """
            {
              "type": "object",
              "properties": {}
            }
            """;
    }

    private String defaultFormData(Member member, LoanType loanType) {
        return "{}";
    }

    private String defaultPolicySnapshot(Member member, BigDecimal amount, int requiredGuarantors) {
        SavingsAccount account = savingsAccountRepository.findByMemberId(member.getId()).orElse(null);
        BigDecimal savingsBalance = account == null ? BigDecimal.ZERO : account.getAvailableBalance();
        BigDecimal maximumLoanAmount = savingsBalance.multiply(DEFAULT_RATIO).setScale(2, RoundingMode.HALF_UP);
        return toJson(new LinkedHashMap<>(Map.of(
            "eligible", amount.compareTo(maximumLoanAmount) <= 0,
            "savingsBalance", savingsBalance,
            "maximumLoanAmount", maximumLoanAmount,
            "requiredGuarantors", requiredGuarantors,
            "ratio", DEFAULT_RATIO
        )));
    }

    private String defaultFinancialSnapshot(LoanType loanType, BigDecimal amount, int tenorMonths) {
        BigDecimal safeAmount = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal insuranceFee = safeAmount.multiply(DEFAULT_INSURANCE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal interestRate = effectiveInterestRate(loanType, tenorMonths);
        BigDecimal interestAmount = safeAmount.multiply(interestRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDeductions = APPLICATION_FEE.add(insuranceFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal principalAmount = safeAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal principalPlusInterest = principalAmount.add(interestAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyRepayment = principalPlusInterest.divide(BigDecimal.valueOf(tenorMonths), 2, RoundingMode.HALF_UP);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("applicationFee", APPLICATION_FEE);
        snapshot.put("insuranceFee", insuranceFee);
        snapshot.put("totalDeductions", totalDeductions);
        snapshot.put("loanBalance", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        snapshot.put("principalAmount", principalAmount);
        snapshot.put("loanToBePaid", principalAmount);
        snapshot.put("principalPlusInterest", principalPlusInterest);
        snapshot.put("loanPlusInterest", principalPlusInterest);
        snapshot.put("interestAmount", interestAmount);
        snapshot.put("monthlyRepaymentAmount", monthlyRepayment);
        snapshot.put("applicationFeeRate", APPLICATION_FEE);
        snapshot.put("insuranceRate", DEFAULT_INSURANCE_RATE);
        snapshot.put("interestRate", interestRate);
        snapshot.put("topUpSourceLoanId", "");
        return toJson(snapshot);
    }

    private BigDecimal monthlyRepayment(BigDecimal amount, int tenorMonths, BigDecimal interestRate) {
        BigDecimal interestAmount = amount.multiply(interestRate).setScale(2, RoundingMode.HALF_UP);
        return amount.add(interestAmount)
            .divide(BigDecimal.valueOf(tenorMonths), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal effectiveInterestRate(LoanType loanType, int tenorMonths) {
        if (loanType == LoanType.LOAN_ADVANCE) {
            return tenorMonths <= 1 ? BigDecimal.ZERO : CHAPCHAP_EXTENDED_INTEREST_RATE;
        }
        return DEFAULT_INTEREST_RATE;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to prepare seed data JSON", ex);
        }
    }

    private SeedMemberRow row(String id,
                              String createdAt,
                              String email,
                              String fullName,
                              String memberNo,
                              String passwordHash,
                              String phone,
                              Position position,
                              String profileLastSyncedAt,
                              Integer rank,
                              String ignoredSaccoId,
                              MemberStatus status,
                              String stationId,
                              Boolean memberAccount,
                              String signatureRegisteredAt,
                              String signatureText) {
        return new SeedMemberRow(
            id,
            OffsetDateTime.parse(createdAt),
            email,
            fullName,
            memberNo,
            passwordHash,
            blankToNull(phone),
            position,
            parseNullable(profileLastSyncedAt),
            rank,
            IAA_SACCO_ID,
            status,
            blankToNull(stationId),
            memberAccount,
            parseNullable(signatureRegisteredAt),
            blankToNull(signatureText)
        );
    }

    private OffsetDateTime parseNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record SeedMemberRow(
        String id,
        OffsetDateTime createdAt,
        String email,
        String fullName,
        String memberNo,
        String passwordHash,
        String phone,
        Position position,
        OffsetDateTime profileLastSyncedAt,
        Integer rank,
        String saccoId,
        MemberStatus status,
        String stationId,
        Boolean memberAccount,
        OffsetDateTime signatureRegisteredAt,
        String signatureText
    ) {
    }
}
