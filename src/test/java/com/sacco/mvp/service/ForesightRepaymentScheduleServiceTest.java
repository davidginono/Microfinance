package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.RepaymentFrequency;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
import com.sacco.mvp.integration.foresight.ForesightRepaymentScheduleRow;
import com.sacco.mvp.integration.foresight.UpstreamAvailabilityException;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForesightRepaymentScheduleServiceTest {

    @Mock private ForesightDirectoryService foresightDirectoryService;
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private MemberRepository memberRepository;

    @Test
    void loadLocalLoanScheduleReturnsInterestInclusiveForesightDisplayRows() {
        ForesightRepaymentScheduleService service = service();
        UUID memberId = UUID.randomUUID();
        LoanApplication loan = disbursedLoan(memberId);
        Member member = member(memberId);
        when(foresightDirectoryService.fetchRepaymentSchedule("MEM123", "ST-1", "101"))
            .thenReturn(scheduleRows());

        ForesightRepaymentScheduleService.RepaymentScheduleDisplay display =
            service.loadLocalLoanSchedule(loan, member);

        assertThat(display.status()).isEqualTo("AVAILABLE");
        assertThat(display.rows()).hasSize(2);
        assertThat(display.rows().getFirst())
            .containsEntry("pmtNo", "1")
            .containsEntry("beginningBalance", "TSh 700,000")
            .containsEntry("payment", "TSh 122,500")
            .containsEntry("loanAmount", "TSh 116,666.67")
            .containsEntry("interest", "TSh 5,833.33")
            .containsEntry("endingBalance", "TSh 583,333.33");
        assertThat(display.summary())
            .containsEntry("Installment Amount", "TSh 122,500")
            .containsEntry("Total Interest", "TSh 11,666.66")
            .containsEntry("Total Principal", "TSh 700,000")
            .containsEntry("Total Amount", "TSh 245,000")
            .containsEntry("Final Due Date", "2026-09-30")
            .containsEntry("Interest Method", "Flat schedule");
        assertThat(display.toPayload())
            .containsEntry("scheduleAvailable", true)
            .containsEntry("count", 2);
    }

    @Test
    void refreshLocalLoanSchedulePersistsForesightSnapshotAndDerivedFields() {
        ForesightRepaymentScheduleService service = service();
        UUID memberId = UUID.randomUUID();
        LoanApplication loan = disbursedLoan(memberId);
        Member member = member(memberId);
        when(loanApplicationRepository.findById(loan.getId()))
            .thenReturn(Optional.of(loan), Optional.of(loan));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(foresightDirectoryService.fetchRepaymentSchedule("MEM123", "ST-1", "101"))
            .thenReturn(scheduleRows());

        ForesightRepaymentScheduleService.RefreshResult result =
            service.refreshLocalLoanSchedule(loan.getId());

        assertThat(result.status()).isEqualTo(ForesightRepaymentScheduleService.RefreshStatus.UPDATED);
        ArgumentCaptor<LoanApplication> captor = ArgumentCaptor.forClass(LoanApplication.class);
        verify(loanApplicationRepository).save(captor.capture());
        LoanApplication saved = captor.getValue();
        assertThat(saved.getFirstRepaymentDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(saved.getFinalDueDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(saved.getInstallmentAmount()).isEqualByComparingTo("122500.00");
        assertThat(saved.getRepaymentFrequency()).isEqualTo(RepaymentFrequency.MONTHLY);
        assertThat(saved.getRepaymentScheduleJson())
            .contains("\"source\":\"FORESIGHT\"")
            .contains("\"totalInterest\":11666.66")
            .contains("\"totalAmount\":245000.00")
            .contains("\"principalComponent\":116666.67")
            .contains("\"interestComponent\":5833.33");
    }

    @Test
    void loadLocalLoanScheduleReturnsNoDataPayloadForEmptyForesightSchedule() {
        ForesightRepaymentScheduleService service = service();
        UUID memberId = UUID.randomUUID();
        LoanApplication loan = disbursedLoan(memberId);
        when(foresightDirectoryService.fetchRepaymentSchedule("MEM123", "ST-1", "101"))
            .thenReturn(List.of());

        ForesightRepaymentScheduleService.RepaymentScheduleDisplay display =
            service.loadLocalLoanSchedule(loan, member(memberId));

        assertThat(display.status()).isEqualTo("NO_DATA");
        assertThat(display.rows()).isEmpty();
        assertThat(display.toPayload())
            .containsEntry("scheduleAvailable", false)
            .containsEntry("count", 0);
    }

    @Test
    void loadLocalLoanScheduleReturnsUnavailablePayloadWhenForesightFails() {
        ForesightRepaymentScheduleService service = service();
        UUID memberId = UUID.randomUUID();
        LoanApplication loan = disbursedLoan(memberId);
        when(foresightDirectoryService.fetchRepaymentSchedule("MEM123", "ST-1", "101"))
            .thenThrow(new UpstreamAvailabilityException("down", null));

        ForesightRepaymentScheduleService.RepaymentScheduleDisplay display =
            service.loadLocalLoanSchedule(loan, member(memberId));

        assertThat(display.status()).isEqualTo("UNAVAILABLE");
        assertThat(display.rows()).isEmpty();
        assertThat(display.message()).contains("unavailable");
    }

    @SuppressWarnings("unchecked")
    @Test
    void loadExternalLoanScheduleUsesMemberAndFallbackStationWithoutLocalRecord() {
        ForesightRepaymentScheduleService service = service();
        Member member = member(UUID.randomUUID());
        member.setStationId(null);
        when(foresightDirectoryService.fetchRepaymentSchedule("MEM123", "ST-FALLBACK", "EXT-7"))
            .thenReturn(scheduleRows());

        Map<String, Object> payload = service.loadExternalLoanSchedule(member, "ST-FALLBACK", "EXT-7").toPayload();

        assertThat(payload).containsEntry("scheduleAvailable", true);
        assertThat((List<Map<String, Object>>) payload.get("rows")).hasSize(2);
        assertThat((Map<String, Object>) payload.get("summary"))
            .doesNotContainKey("Interest Method");
        assertThat((List<Map<String, Object>>) payload.get("summaryEntries"))
            .extracting(entry -> entry.get("key"))
            .doesNotContain("Interest Method");
    }

    private ForesightRepaymentScheduleService service() {
        return new ForesightRepaymentScheduleService(
            foresightDirectoryService,
            loanApplicationRepository,
            memberRepository,
            new ObjectMapper()
        );
    }

    private LoanApplication disbursedLoan(UUID memberId) {
        return LoanApplication.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-A")
            .stationId("ST-1")
            .applicantMemberId(memberId)
            .loanType(LoanType.EDUCATION_LOAN)
            .loanId("101")
            .amount(new BigDecimal("700000.00"))
            .depositAmount(new BigDecimal("650000.00"))
            .tenorMonths(2)
            .status(LoanStatus.DISBURSED)
            .disbursementDate(LocalDate.of(2026, 7, 31))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private Member member(UUID memberId) {
        return Member.builder()
            .id(memberId)
            .memberNo("MEM123")
            .stationId("ST-1")
            .saccoId("SACCO-A")
            .build();
    }

    private List<ForesightRepaymentScheduleRow> scheduleRows() {
        return List.of(
            new ForesightRepaymentScheduleRow(
                1,
                LocalDate.of(2026, 8, 31),
                new BigDecimal("700000.00"),
                new BigDecimal("122500.00"),
                new BigDecimal("116666.67"),
                new BigDecimal("5833.33"),
                new BigDecimal("583333.33")
            ),
            new ForesightRepaymentScheduleRow(
                2,
                LocalDate.of(2026, 9, 30),
                new BigDecimal("583333.33"),
                new BigDecimal("122500.00"),
                new BigDecimal("116666.67"),
                new BigDecimal("5833.33"),
                new BigDecimal("466666.66")
            )
        );
    }
}
