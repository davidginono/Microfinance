package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SavingsAccountRepository;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {

    @Mock
    private SavingsAccountRepository savingsAccountRepository;
    @Mock
    private SaccoSettingsRepository saccoSettingsRepository;
    @Mock
    private LoanProductSettingRepository loanProductSettingRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ForesightDirectoryService foresightDirectoryService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EligibilityService eligibilityService;

    @Test
    void enforcesOneThirdSavingsRule() {
        String saccoId = "CIRCLE-1001";
        UUID memberId = UUID.randomUUID();

        when(savingsAccountRepository.findByMemberId(memberId)).thenReturn(Optional.of(
            SavingsAccount.builder().id(UUID.randomUUID()).memberId(memberId)
                .availableBalance(new BigDecimal("300000.00")).updatedAt(OffsetDateTime.now()).build()));
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        when(loanProductSettingRepository.findBySaccoIdAndLoanTypeAndActiveTrue(saccoId, LoanType.EDUCATION_LOAN))
            .thenReturn(Optional.of(LoanProductSetting.builder().id(UUID.randomUUID()).saccoId(saccoId)
                .loanType(LoanType.EDUCATION_LOAN).guarantorsRequired(3).active(true)
                .maxLoanSavingsRatio(new BigDecimal("0.3333")).formSchema("{}").createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now()).build()));

        EligibilityService.EligibilityResult ok = eligibilityService.check(saccoId, memberId, LoanType.EDUCATION_LOAN,
            new BigDecimal("99990.00"));
        EligibilityService.EligibilityResult notOk = eligibilityService.check(saccoId, memberId, LoanType.EDUCATION_LOAN,
            new BigDecimal("100010.00"));

        assertThat(ok.eligible()).isTrue();
        assertThat(notOk.eligible()).isFalse();
        assertThat(ok.maxAllowed()).isEqualByComparingTo("99990.00");
    }
}
