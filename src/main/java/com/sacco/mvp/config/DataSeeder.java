package com.sacco.mvp.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    private static final String SACCO_ID = "SACCO-ARUSHA-001";
    private static final String EXTERNAL_STATION_ID = "STN789";
    private static final String EXTERNAL_SACCO_NAME = "IAA SACCOS LTD";
    private static final String DEFAULT_PASSWORD = "Password@123";
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.3333");
    private static final BigDecimal DEFAULT_INSURANCE_RATE = new BigDecimal("0.0150");
    private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("0.1000");
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

    @Bean
    @ConditionalOnProperty(prefix = "app.seed", name = "demo-data-enabled", havingValue = "true")
    CommandLineRunner seedApplicationData() {
        return args -> {
            OffsetDateTime now = OffsetDateTime.now();
            seedSacco(now);
            seedLoanProducts(now);

            seedStaffUser(
                "9ec44d7a-3a70-4f22-bfa0-9f1bd0b5a301", "ADM001", "Amina Admin", "0757000001",
                "adm001@sacco.local", Position.ADMIN, now.minusDays(60));
            seedStaffUser(
                "22f4818f-d378-4d3e-99e0-6a5fa9d73f11", "MGR001", "Mary Manager", "0757000002",
                "mgr001@sacco.local", Position.MANAGER, now.minusDays(58));
            seedStaffUser(
                "5d25ee8d-1cc2-4f24-9f38-f74083e7cf21", "BRD001", "Ben Board One", "0757000003",
                "brd001@sacco.local", Position.BOARD, now.minusDays(56));
            seedStaffUser(
                "1bf74fe9-2850-4627-a2aa-147e24f0c2a6", "BRD002", "Bea Board Two", "0757000004",
                "brd002@sacco.local", Position.BOARD, now.minusDays(55));
            seedStaffUser(
                "66b82e0c-e983-4fc2-aad5-13157516bb77", "BRD003", "Bob Board Three", "0757000005",
                "brd003@sacco.local", Position.BOARD, now.minusDays(54));
            seedStaffUser(
                "de54fdfb-b44b-4e09-973c-fbc008ea55b8", "CHR001", "Cathy Chairperson", "0757000006",
                "chr001@sacco.local", Position.CHAIRPERSON, now.minusDays(53));

            Member alice = seedMember(
                "6c7ff573-a3c8-4cd1-91f9-b0a19aef9101", "MEM001", "Alice Member", "0757000101",
                "mem001@sacco.local", Position.MEMBER, 1, now.minusDays(50), new BigDecimal("450000.00"),
                new BigDecimal("180000.00"), new BigDecimal("250000.00"));
            Member alex = seedMember(
                "92d4ae9d-d6ca-42d0-ac4a-1ce1cf527969", "MEM002", "Alex Member", "0757000102",
                "ginonodavid625@gmail.com", Position.MEMBER, 2, now.minusDays(49), new BigDecimal("380000.00"),
                new BigDecimal("150000.00"), new BigDecimal("210000.00"));
            Member asha = seedMember(
                "4f0d8da6-0931-45ad-b005-8e8cde536594", "MEM003", "Asha Member", "0757000103",
                "mem003@sacco.local", Position.MEMBER, 3, now.minusDays(48), new BigDecimal("320000.00"),
                new BigDecimal("120000.00"), new BigDecimal("160000.00"));
            Member amos = seedMember(
                "7ee64686-e9b9-41e6-85bf-f119495b94b6", "MEM004", "Amos Member", "0757000104",
                "mem004@sacco.local", Position.MEMBER, 4, now.minusDays(47), new BigDecimal("300000.00"),
                new BigDecimal("110000.00"), new BigDecimal("140000.00"));
            Member amina = seedMember(
                "95d06d4c-9fa2-46d4-a574-8ad4f66f5af7", "MEM005", "Amina Member", "0757000105",
                "mem005@sacco.local", Position.MEMBER, 5, now.minusDays(46), new BigDecimal("295000.00"),
                new BigDecimal("108000.00"), new BigDecimal("138000.00"));
            Member abdi = seedMember(
                "52fa43e5-3ff0-40f2-bbf6-290f9f8b153e", "MEM006", "Abdi Member", "0757000106",
                "mem006@sacco.local", Position.MEMBER, 6, now.minusDays(45), new BigDecimal("280000.00"),
                new BigDecimal("100000.00"), new BigDecimal("130000.00"));
            Member agnes = seedMember(
                "9713ec57-271c-4f54-82ba-8c009ba36e4d", "MEM007", "Agnes Member", "0757000107",
                "mem007@sacco.local", Position.MEMBER, 7, now.minusDays(44), new BigDecimal("270000.00"),
                new BigDecimal("98000.00"), new BigDecimal("125000.00"));

            seedSampleLoans(now, alice, alex, asha, amos, amina, abdi, agnes);
        };
    }

    private void seedSacco(OffsetDateTime now) {
        registeredSaccoRepository.findById(SACCO_ID)
            .orElseGet(() -> registeredSaccoRepository.save(RegisteredSacco.builder()
                .saccoId(SACCO_ID)
                .saccoName(EXTERNAL_SACCO_NAME)
                .active(true)
                .createdAt(now.minusDays(90))
                .updatedAt(now)
                .build()));

        saccoStationRepository.findBySaccoIdAndStationId(SACCO_ID, EXTERNAL_STATION_ID)
            .orElseGet(() -> saccoStationRepository.save(SaccoStation.builder()
                .id(UUID.randomUUID())
                .saccoId(SACCO_ID)
                .stationId(EXTERNAL_STATION_ID)
                .active(true)
                .createdAt(now.minusDays(90))
                .updatedAt(now)
                .build()));

        SaccoSettings sacco = saccoSettingsRepository.findById(SACCO_ID)
            .orElseGet(() -> SaccoSettings.builder()
                .saccoId(SACCO_ID)
                .createdAt(now.minusDays(90))
                .build());

        sacco.setExternalStationId(EXTERNAL_STATION_ID);
        sacco.setExternalSaccoName(EXTERNAL_SACCO_NAME);
        sacco.setRequiredGuarantors(3);
        sacco.setBoardSize(3);
        sacco.setBoardQuorum(2);
        sacco.setMaxLoanSavingsRatio(DEFAULT_RATIO);
        sacco.setDefaultLanguage("en");
        if (sacco.getCreatedAt() == null) {
            sacco.setCreatedAt(now.minusDays(90));
        }
        sacco.setUpdatedAt(now);
        saccoSettingsRepository.save(sacco);
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
        LoanProductSetting product = loanProductSettingRepository.findAll().stream()
            .filter(existing -> existing.getSaccoId().equals(SACCO_ID) && existing.getLoanType() == loanType)
            .findFirst()
            .orElseGet(() -> LoanProductSetting.builder()
                .id(UUID.randomUUID())
                .saccoId(SACCO_ID)
                .loanType(loanType)
                .createdAt(now.minusDays(80))
                .build());

        product.setGuarantorsRequired(guarantorsRequired);
        product.setMaxLoanSavingsRatio(savingsRatio);
        product.setInsuranceRate(insuranceRate);
        product.setInterestRate(interestRate);
        product.setMaxRepaymentMonths(maxRepaymentMonths);
        product.setFormSchema(defaultLoanFormSchema());
        product.setActive(true);
        if (product.getCreatedAt() == null) {
            product.setCreatedAt(now.minusDays(80));
        }
        product.setUpdatedAt(now);
        loanProductSettingRepository.save(product);
    }

    private Member seedMember(String id,
                              String memberNo,
                              String fullName,
                              String phone,
                              String email,
                              Position position,
                              int rank,
                              OffsetDateTime createdAt,
                              BigDecimal availableBalance,
                              BigDecimal sharesBalance,
                              BigDecimal depositsBalance) {
        Member member = memberRepository.findByMemberNo(memberNo)
            .orElseGet(() -> Member.builder()
                .id(UUID.fromString(id))
                .createdAt(createdAt)
                .build());

        member.setSaccoId(SACCO_ID);
        member.setMemberNo(memberNo);
        member.setStationId(EXTERNAL_STATION_ID);
        member.setFullName(fullName);
        member.setPhone(phone);
        preserveExistingEmail(member, email);
        member.setSignatureText(fullName);
        member.setSignatureRegisteredAt(OffsetDateTime.now());
        member.setMemberAccount(position == Position.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setPosition(position);
        member.setRank(rank);
        member.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        if (member.getCreatedAt() == null) {
            member.setCreatedAt(createdAt);
        }
        member.setProfileLastSyncedAt(OffsetDateTime.now());
        Member saved = memberRepository.save(member);

        seedSavingsAccount(saved.getId(), availableBalance, sharesBalance, depositsBalance);
        seedUserSettings(saved.getId());
        return saved;
    }

    private Member seedStaffUser(String id,
                                 String memberNo,
                                 String fullName,
                                 String phone,
                                 String email,
                                 Position position,
                                 OffsetDateTime createdAt) {
        Member member = memberRepository.findByMemberNo(memberNo)
            .orElseGet(() -> Member.builder()
                .id(UUID.fromString(id))
                .createdAt(createdAt)
                .build());

        member.setSaccoId(SACCO_ID);
        member.setMemberNo(memberNo);
        member.setFullName(fullName);
        member.setPhone(phone);
        preserveExistingEmail(member, email);
        member.setMemberAccount(false);
        member.setStatus(MemberStatus.ACTIVE);
        member.setPosition(position);
        member.setStaffRoles(new LinkedHashSet<>(List.of(position)));
        member.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        if (member.getCreatedAt() == null) {
            member.setCreatedAt(createdAt);
        }
        return memberRepository.save(member);
    }

    private void preserveExistingEmail(Member member, String seededEmail) {
        if (member.getEmail() == null || member.getEmail().isBlank()) {
            member.setEmail(seededEmail);
        }
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

        account.setAvailableBalance(availableBalance);
        account.setSharesBalance(sharesBalance);
        account.setDepositsBalance(depositsBalance);
        account.setUpdatedAt(now);
        account.setSummaryLastSyncedAt(now);
        savingsAccountRepository.save(account);
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
                                 Member agnes) {
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
            now.minusDays(4));

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
            agnes,
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
            "hasExistingLoan", false
        )));
        loanApplicationRepository.save(app);
    }

    private void seedReadyForManagerLoan(String loanId,
                                         Member member,
                                         LoanType loanType,
                                         BigDecimal amount,
                                         int tenorMonths,
                                         OffsetDateTime createdAt) {
        if (loanApplicationRepository.existsById(UUID.fromString(loanId))) {
            return;
        }
        List<UUID> guarantors = List.of(
            UUID.fromString("7ee64686-e9b9-41e6-85bf-f119495b94b6"),
            UUID.fromString("95d06d4c-9fa2-46d4-a574-8ad4f66f5af7"),
            UUID.fromString("52fa43e5-3ff0-40f2-bbf6-290f9f8b153e")
        );
        LoanApplication app = baseLoan(loanId, member, loanType, amount, tenorMonths, createdAt, LoanStatus.READY_FOR_MANAGER);
        app.setSubmittedAt(createdAt.plusHours(3));
        app.setSelectedGuarantors(toJson(guarantors));
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

        BigDecimal split = amount.divide(BigDecimal.valueOf(guarantors.size()), 2, RoundingMode.HALF_UP);
        for (Member guarantor : guarantors) {
            guarantorRequestRepository.save(GuarantorRequest.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(applicationId)
                .guarantorMemberId(guarantor.getId())
                .status(GuarantorRequestStatus.PENDING)
                .requestedAmount(split)
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

        BigDecimal split = amount.divide(BigDecimal.valueOf(guarantors.size()), 2, RoundingMode.HALF_UP);
        for (Member guarantor : guarantors) {
            guarantorRequestRepository.save(GuarantorRequest.builder()
                .id(UUID.randomUUID())
                .loanApplicationId(applicationId)
                .guarantorMemberId(guarantor.getId())
                .status(GuarantorRequestStatus.APPROVED)
                .requestedAmount(split)
                .committedAmount(split)
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
            .saccoId(SACCO_ID)
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
        BigDecimal loanToBePaid = safeAmount.add(APPLICATION_FEE).add(insuranceFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal loanPlusInterest = loanToBePaid.add(interestAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyRepayment = loanPlusInterest.divide(BigDecimal.valueOf(tenorMonths), 2, RoundingMode.HALF_UP);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("applicationFee", APPLICATION_FEE);
        snapshot.put("insuranceFee", insuranceFee);
        snapshot.put("loanBalance", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        snapshot.put("loanToBePaid", loanToBePaid);
        snapshot.put("loanPlusInterest", loanPlusInterest);
        snapshot.put("interestAmount", interestAmount);
        snapshot.put("monthlyRepaymentAmount", monthlyRepayment);
        snapshot.put("applicationFeeRate", APPLICATION_FEE);
        snapshot.put("insuranceRate", DEFAULT_INSURANCE_RATE);
        snapshot.put("interestRate", interestRate);
        snapshot.put("topUpSourceLoanId", "");
        return toJson(snapshot);
    }

    private BigDecimal monthlyRepayment(BigDecimal amount, int tenorMonths, BigDecimal interestRate) {
        BigDecimal insuranceFee = amount.multiply(DEFAULT_INSURANCE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal loanToBePaid = amount.add(APPLICATION_FEE).add(insuranceFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal interestAmount = amount.multiply(interestRate).setScale(2, RoundingMode.HALF_UP);
        return loanToBePaid.add(interestAmount)
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
}
