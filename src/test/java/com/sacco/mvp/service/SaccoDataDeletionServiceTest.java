package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.reporting.OperationalReportTemplateService;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaccoDataDeletionServiceTest {
    @Mock JdbcTemplate jdbc;
    @Mock RegisteredSaccoRepository institutions;
    @Mock MemberRepository members;
    @Mock SaccoRegistryService registry;
    @Mock SaccoLogoStorageService logos;
    @Mock StoredUploadStorageService uploads;
    @Mock AccountingPolicyService accountingPolicies;
    @Mock GeneralLedgerService generalLedger;
    @Mock OperationalReportTemplateService reportTemplates;
    @InjectMocks SaccoDataDeletionService service;

    @Test
    void institutionWithFinancialHistoryIsBlockedBeforeFilesOrRowsAreDeleted() {
        when(institutions.findById("I-PG")).thenReturn(Optional.of(RegisteredSacco.builder()
            .saccoId("I-PG").saccoName("Preview Finance").active(true).build()));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("I-PG"))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSacco("I-PG", "delete Preview Finance and all its data"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("posted financial records");
        verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq("I-PG"));
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(uploads, logos, registry);
    }

    @Test
    void formerOfficerWithFinancialHistoryCannotBeDeleted() {
        UUID id = UUID.randomUUID();
        when(members.findById(id)).thenReturn(Optional.of(Member.builder().id(id).fullName("Former Officer")
            .position(Position.MINOR_ADMIN).status(MemberStatus.INACTIVE).build()));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(id), eq(id))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteRevokedMinorAdmin(id, "delete Former Officer"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("posted financial records");
        verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq(id), eq(id));
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(uploads, logos, registry);
    }

    @Test
    void confirmationIsStillRequiredBeforeFinancialHistoryLookup() {
        when(institutions.findById("I-PG")).thenReturn(Optional.of(RegisteredSacco.builder()
            .saccoId("I-PG").saccoName("Preview Finance").active(true).build()));
        assertThatThrownBy(() -> service.deleteSacco("I-PG", "wrong confirmation"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("confirmation phrase");
        verifyNoInteractions(jdbc, uploads, logos, registry, accountingPolicies, generalLedger, reportTemplates);
    }

    @Test
    void institutionWithOnlyPolicyEvidenceIsBlockedBeforeFilesystemDeletion() {
        when(institutions.findById("I-PG")).thenReturn(Optional.of(RegisteredSacco.builder()
            .saccoId("I-PG").saccoName("Preview Finance").active(true).build()));
        when(accountingPolicies.hasInstitutionHistory("I-PG")).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSacco("I-PG", "delete Preview Finance and all its data"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be deleted");
        verifyNoInteractions(jdbc, uploads, logos, registry, generalLedger);
    }

    @Test
    void institutionWithChartButNoLoanLedgerIsBlockedBeforeFilesystemDeletion() {
        when(institutions.findById("I-PG")).thenReturn(Optional.of(RegisteredSacco.builder()
            .saccoId("I-PG").saccoName("Preview Finance").active(true).build()));
        when(generalLedger.hasInstitutionHistory("I-PG")).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSacco("I-PG", "delete Preview Finance and all its data"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be deleted");
        verifyNoInteractions(jdbc, uploads, logos, registry);
    }

    @Test
    void formerOfficerWithOnlyPolicyReviewEvidenceCannotBeDeleted() {
        UUID id = UUID.randomUUID();
        when(members.findById(id)).thenReturn(Optional.of(Member.builder().id(id).fullName("Former Officer")
            .position(Position.MINOR_ADMIN).status(MemberStatus.INACTIVE).build()));
        when(accountingPolicies.hasMemberHistory(id)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteRevokedMinorAdmin(id, "delete Former Officer"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be deleted");
        verifyNoInteractions(jdbc, uploads, logos, registry, generalLedger);
    }

    @Test
    void formerOfficerWithOnlyJournalEvidenceCannotBeDeleted() {
        UUID id = UUID.randomUUID();
        when(members.findById(id)).thenReturn(Optional.of(Member.builder().id(id).fullName("Former Officer")
            .position(Position.MINOR_ADMIN).status(MemberStatus.INACTIVE).build()));
        when(generalLedger.hasMemberHistory(id)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteRevokedMinorAdmin(id, "delete Former Officer"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be deleted");
        verifyNoInteractions(jdbc, uploads, logos, registry);
    }

    @Test
    void institutionWithOnlyReportDefinitionsIsBlockedBeforeFilesystemDeletion() {
        when(institutions.findById("I-PG")).thenReturn(Optional.of(RegisteredSacco.builder()
            .saccoId("I-PG").saccoName("Preview Finance").active(true).build()));
        when(reportTemplates.hasInstitutionHistory("I-PG")).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSacco("I-PG", "delete Preview Finance and all its data"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be deleted");
        verifyNoInteractions(jdbc, uploads, logos, registry);
    }

    @Test
    void formerOfficerWithOnlyReportReviewEvidenceCannotBeDeleted() {
        UUID id = UUID.randomUUID();
        when(members.findById(id)).thenReturn(Optional.of(Member.builder().id(id).fullName("Former Officer")
            .position(Position.MINOR_ADMIN).status(MemberStatus.INACTIVE).build()));
        when(reportTemplates.hasMemberHistory(id)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteRevokedMinorAdmin(id, "delete Former Officer"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be deleted");
        verifyNoInteractions(jdbc, uploads, logos, registry);
    }
}
