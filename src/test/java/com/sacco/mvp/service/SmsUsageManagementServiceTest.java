package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.SmsUsageOutcome;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SmsUsageLedgerRepository;
import com.sacco.mvp.repository.StationSmsAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsUsageManagementServiceTest {
    @Mock private SmsUnitTransactionService transactionService;
    @Mock private SmsUsageAlertService alertService;
    @Mock private StationSmsAccountRepository accountRepository;
    @Mock private SmsUsageLedgerRepository ledgerRepository;
    @Mock private MemberRepository memberRepository;

    private SmsUsageManagementService service;

    @BeforeEach
    void setUp() {
        service = new SmsUsageManagementService(
            transactionService,
            alertService,
            accountRepository,
            ledgerRepository,
            memberRepository,
            new ApplicationClock("Africa/Nairobi"),
            new WorkflowStatusPresentationService()
        );
        ReflectionTestUtils.setField(service, "exportMaxRows", 2);
    }

    @Test
    void loanUsageRowsPassesDateApplicantStatusAndScopeFiltersToRepository() {
        UUID applicantId = UUID.randomUUID();
        when(ledgerRepository.summarizeLoanSmsUsage(
            any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(projection(applicantId, "LN-1001", LoanStatus.READY_FOR_MANAGER))));

        Page<SmsUsageManagementService.LoanSmsUsageRow> rows = service.loanUsageRows(
            service.loanUsageCriteria(
                " IAA ",
                " AR704 ",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 15),
                LoanStatus.READY_FOR_MANAGER,
                List.of(applicantId)
            ),
            PageRequest.of(0, 25)
        );

        assertThat(rows.getContent()).hasSize(1);
        assertThat(rows.getContent().getFirst().loanStatusLabel()).isEqualTo("On Review By Manager");
        assertThat(rows.getContent().getFirst().unitsUsed()).isEqualTo(4);

        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> applicantCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(ledgerRepository).summarizeLoanSmsUsage(
            eq("IAA"),
            eq("AR704"),
            fromCaptor.capture(),
            toCaptor.capture(),
            eq(LoanStatus.READY_FOR_MANAGER),
            applicantCaptor.capture(),
            eq(true),
            eq(acceptedUsageOutcomes()),
            any(Pageable.class)
        );
        assertThat(fromCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00+03:00"));
        assertThat(toCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-09-16T00:00:00+03:00"));
        assertThat(applicantCaptor.getValue()).containsExactly(applicantId);
    }

    @Test
    void emptyApplicantSelectionLeavesApplicantFilterInactive() {
        when(ledgerRepository.summarizeLoanSmsUsage(
            any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(Pageable.class)
        )).thenReturn(Page.empty());

        service.loanUsageRows(
            service.loanUsageCriteria("IAA", "AR704", null, null, null, List.of()),
            PageRequest.of(0, 25)
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> applicantCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(ledgerRepository).summarizeLoanSmsUsage(
            eq("IAA"),
            eq("AR704"),
            isNull(),
            isNull(),
            isNull(),
            applicantCaptor.capture(),
            eq(false),
            eq(acceptedUsageOutcomes()),
            any(Pageable.class)
        );
    }

    @Test
    void selectedApplicantsStayInsideRequestedSaccoAndStationScope() {
        UUID inScopeId = UUID.randomUUID();
        UUID otherStationId = UUID.randomUUID();
        when(memberRepository.findAllById(any())).thenReturn(List.of(
            member(inScopeId, "IAA", "AR704", "Asha Nyerere", "MEM-100"),
            member(otherStationId, "IAA", "BR001", "Biko Mwinyi", "MEM-200")
        ));

        List<SmsUsageManagementService.ApplicantFilterOption> selected = service.selectedLoanUsageApplicants(
            service.loanUsageCriteria("IAA", "AR704", null, null, null, List.of(inScopeId, otherStationId))
        );

        assertThat(selected).hasSize(1);
        assertThat(selected.getFirst().id()).isEqualTo(inScopeId);
        assertThat(selected.getFirst().label()).isEqualTo("Asha Nyerere (MEM-100)");
    }

    @Test
    void applicantSearchRequiresShortBoundedQueryBeforeRepositoryAccess() {
        List<SmsUsageManagementService.ApplicantFilterOption> results =
            service.searchLoanUsageApplicants("IAA", "AR704", "a");

        assertThat(results).isEmpty();
        verify(ledgerRepository, never()).searchLoanSmsUsageApplicants(any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void exportStopsWhenRowsExceedConfiguredLimit() {
        ReflectionTestUtils.setField(service, "exportMaxRows", 1);
        when(ledgerRepository.exportLoanSmsUsage(
            any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(Pageable.class)
        )).thenReturn(List.of(
            projection(UUID.randomUUID(), "LN-1001", LoanStatus.SUBMITTED),
            projection(UUID.randomUUID(), "LN-1002", LoanStatus.SUBMITTED)
        ));

        assertThatThrownBy(() -> service.loanUsageExport(
            service.loanUsageCriteria("IAA", "AR704", null, null, null, List.of()),
            "Admin User"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Narrow the filters");
    }

    private List<SmsUsageOutcome> acceptedUsageOutcomes() {
        return List.of(SmsUsageOutcome.ACCEPTED, SmsUsageOutcome.ACCEPTANCE_UNKNOWN);
    }

    private Member member(UUID id, String saccoId, String stationId, String fullName, String memberNo) {
        return Member.builder()
            .id(id)
            .saccoId(saccoId)
            .stationId(stationId)
            .fullName(fullName)
            .memberNo(memberNo)
            .staffNo("STF-" + memberNo)
            .build();
    }

    private SmsUsageLedgerRepository.LoanSmsUsageProjection projection(UUID applicantId,
                                                                       String loanId,
                                                                       LoanStatus status) {
        return new SmsUsageLedgerRepository.LoanSmsUsageProjection() {
            @Override
            public UUID getLoanApplicationId() {
                return UUID.randomUUID();
            }

            @Override
            public Long getApplicationNumber() {
                return 1001L;
            }

            @Override
            public String getLoanId() {
                return loanId;
            }

            @Override
            public LoanStatus getLoanStatus() {
                return status;
            }

            @Override
            public String getSaccoId() {
                return "IAA";
            }

            @Override
            public String getStationId() {
                return "AR704";
            }

            @Override
            public UUID getApplicantMemberId() {
                return applicantId;
            }

            @Override
            public String getApplicantName() {
                return "Asha Nyerere";
            }

            @Override
            public String getApplicantMemberNo() {
                return "MEM-100";
            }

            @Override
            public long getSmsEventCount() {
                return 2;
            }

            @Override
            public long getUnitsUsed() {
                return 4;
            }

            @Override
            public OffsetDateTime getLastSmsAt() {
                return OffsetDateTime.parse("2026-09-23T10:30:00+03:00");
            }
        };
    }
}
