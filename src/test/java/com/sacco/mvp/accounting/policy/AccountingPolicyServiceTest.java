package com.sacco.mvp.accounting.policy;

import com.sacco.mvp.accounting.repository.GeneralLedgerRepository;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountingPolicyServiceTest {
    @Mock AccountingPolicyRepository policies;
    @Mock AccountingPolicyApprovalRepository approvals;
    @Mock AuditService audit;
    @Mock ApplicationClock clock;
    @Mock UserClaimService userClaims;
    @Mock MemberDirectoryService directory;
    @Mock SaccoRegistryService institutions;
    @Mock GeneralLedgerRepository accounts;
    @Spy AccessControlService access = new AccessControlService();
    @Spy ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    @InjectMocks AccountingPolicyService service;
    final LocalDate date = LocalDate.of(2026, 10, 2);
    final UUID makerId = UUID.randomUUID();
    final UUID checkerId = UUID.randomUUID();

    @BeforeEach void setup() {
        lenient().when(accounts.validPolicyMappings(anyString(),anyCollection())).thenReturn(true);
        lenient().when(institutions.findActiveSacco("I1")).thenReturn(Optional.of(RegisteredSacco.builder().saccoId("I1").active(true).build()));
        lenient().when(institutions.findStation("I1", "B1")).thenReturn(Optional.of(SaccoStation.builder()
            .saccoId("I1").stationId("B1").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        lenient().when(clock.today()).thenReturn(date);
        lenient().when(clock.now()).thenReturn(date.atStartOfDay().atOffset(ZoneOffset.ofHours(3)));
        lenient().when(directory.find(any())).thenAnswer(i -> Optional.of(member(i.getArgument(0), "I1")));
        lenient().when(userClaims.effectiveClaims(any(), anyCollection(), anyBoolean())).thenReturn(Set.of(
            UserClaim.ACCOUNTING_POLICIES_VIEW, UserClaim.ACCOUNTING_POLICIES_CREATE, UserClaim.ACCOUNTING_POLICIES_APPROVE));
        lenient().when(policies.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(approvals.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test void invalidLocalAccountMappingCannotBeProposedOrApproved() {
        var base=command(); var c=new AccountingPolicyService.PolicyCommand(base.requestKey(),base.authoritativeLedger(),base.openingDate(),base.effectiveFrom(),base.decisions(),base.postingMatrix(),Map.of("CASH",UUID.randomUUID()),base.evidenceReference());
        when(accounts.validPolicyMappings(eq("I1"),anyCollection())).thenReturn(false);
        assertThatThrownBy(()->service.create(actor(makerId),c)).hasMessage("accounting.policy.error.accountMappings");
        verify(policies,never()).saveAndFlush(any());
        var p=policy(); when(policies.findByIdAndSaccoId(p.getId(),"I1")).thenReturn(Optional.of(p));
        assertThatThrownBy(()->service.decide(p.getId(),actor(checkerId),decision())).hasMessage("accounting.policy.error.accountMappings");
        verify(approvals,never()).saveAndFlush(any());
    }

    @Test void missingExplicitDecisionsCannotCreatePolicy() {
        var c = command();
        c.decisions().remove(PolicyDecision.REPORTING_FRAMEWORK);
        assertThatThrownBy(() -> service.create(actor(makerId), c)).hasMessage("accounting.policy.error.incomplete");
        verify(policies, never()).saveAndFlush(any());
    }
    @Test void missingEventCannotCreatePolicy() {
        var c = command(); c.postingMatrix().remove(PostingEvent.ADVANCE);
        assertThatThrownBy(() -> service.create(actor(makerId), c)).hasMessage("accounting.policy.error.incomplete");
    }
    @Test void explicitProposalContainsNoApprovalAndRecordsAudit() {
        var result = service.create(actor(makerId), command());
        assertThat(result.approvalDecision()).isNull();
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.decisions()).containsEntry(PolicyDecision.REPORTING_FRAMEWORK, "Synthetic policy fixture only");
        verify(audit).log(eq("ACCOUNTING_POLICY"), eq(result.id()), eq("ACCOUNTING_POLICY_CREATED"), eq(makerId), isNull(), anyMap());
    }
    @Test void sameMakerCannotApprove() {
        var p = policy(); when(policies.findByIdAndSaccoId(p.getId(), "I1")).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.decide(p.getId(), actor(makerId), decision())).hasMessage("accounting.policy.error.checker");
        verify(approvals, never()).saveAndFlush(any());
    }
    @Test void independentCheckerApprovalIsRequiredForRuntimeGate() {
        var p = policy(); when(policies.findByIdAndSaccoId(p.getId(), "I1")).thenReturn(Optional.of(p));
        var result = service.decide(p.getId(), actor(checkerId), decision());
        assertThat(result.approvalDecision()).isEqualTo(AccountingPolicyService.Decision.APPROVED);
        assertThat(result.checkerId()).isEqualTo(checkerId);
        assertThat(result.makerId()).isEqualTo(makerId);
    }
    @Test void absentPolicyNeverLooksApproved() {
        assertThatThrownBy(() -> service.requireApprovedLocalPolicy("I1", date)).hasMessage("accounting.policy.error.unapproved");
    }
    @Test void externalBooksCannotActivateLocalPosting() {
        var p = policy(); p.setAuthoritativeLedger(AccountingPolicyService.AuthoritativeLedger.EXTERNAL_GL);
        when(policies.approvedAt(eq("I1"), eq(date), any())).thenReturn(List.of(p));
        assertThatThrownBy(() -> service.requireApprovedLocalPolicy("I1", date)).hasMessage("accounting.policy.error.external");
    }
    @Test void disabledEventCannotPostEvenWithApproval() {
        var p = policy(); var a = AccountingPolicyApproval.builder().decision(AccountingPolicyService.Decision.APPROVED).build();
        when(policies.approvedAt(eq("I1"), eq(date), any())).thenReturn(List.of(p));
        when(approvals.findById(p.getId())).thenReturn(Optional.of(a));
        var approved = service.requireApprovedLocalPolicy("I1", date);
        assertThatThrownBy(() -> service.requireAllowedPosting(approved, PostingEvent.FEE)).hasMessage("accounting.policy.error.disabled");
    }
    @Test void crossInstitutionAndRevokedPermissionAreDenied() {
        assertThatThrownBy(() -> service.view(UUID.randomUUID(), actor(checkerId))).isInstanceOf(AccessDeniedException.class);
        when(userClaims.effectiveClaims(any(), anyCollection(), anyBoolean())).thenReturn(Set.of());
        assertThatThrownBy(() -> service.create(actor(makerId), command())).isInstanceOf(AccessDeniedException.class);
        verify(policies, never()).saveAndFlush(any());
    }
    @Test void changedPayloadCannotReuseRequestKey() {
        var p = policy(); var c = command();
        c.decisions().put(PolicyDecision.ROUNDING, "Changed decision");
        when(policies.findBySaccoIdAndRequestKey("I1", c.requestKey())).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.create(actor(makerId), c)).hasMessage("accounting.policy.error.retry");
    }
    @Test void freshlyChangedPlatformIdentityCannotUseStaleWorkspaceSession() {
        var current = member(makerId, "I1"); current.setPosition(Position.ADMIN);
        when(directory.find(makerId)).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service.create(actor(makerId), command())).isInstanceOf(AccessDeniedException.class);
        verify(policies, never()).saveAndFlush(any());
    }
    @Test void initialPolicyMustCoverItsOpeningDate() {
        var p = policy(); p.setOpeningDate(date.minusDays(1));
        when(policies.findByIdAndSaccoId(p.getId(), "I1")).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.decide(p.getId(), actor(checkerId), decision())).hasMessage("accounting.policy.error.initialDate");
        verify(approvals, never()).saveAndFlush(any());
    }
    @Test void inactiveInstitutionCannotUseExistingStaffSession() {
        when(institutions.findActiveSacco("I1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(actor(makerId), command())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(policies, approvals, audit);
    }
    @Test void suspendedBranchCannotUseExistingStaffSession() {
        when(institutions.findStation("I1", "B1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(actor(makerId), command())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(policies, approvals, audit);
    }
    @Test void changedBranchCannotReuseInstitutionPolicySession() {
        var current = member(makerId, "I1"); current.setStationId("B2");
        when(directory.find(makerId)).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service.create(actor(makerId), command())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(policies, approvals, audit);
    }
    static Member member(UUID id, String institution) {
        return Member.builder().id(id).saccoId(institution).stationId("B1").memberNo(id.toString())
            .fullName("Synthetic accountant").position(Position.ACCOUNTANT).memberAccount(false).status(MemberStatus.ACTIVE).build();
    }
    AppUserPrincipal actor(UUID id) { return new AppUserPrincipal(member(id, "I1"), Set.of(
        UserClaim.ACCOUNTING_POLICIES_VIEW, UserClaim.ACCOUNTING_POLICIES_CREATE, UserClaim.ACCOUNTING_POLICIES_APPROVE), true); }
    static AccountingPolicyService.PolicyCommand command() {
        var decisions = new EnumMap<PolicyDecision, String>(PolicyDecision.class);
        for (var d : PolicyDecision.values()) decisions.put(d, "Synthetic policy fixture only");
        var matrix = new EnumMap<PostingEvent, AccountingPolicyService.PostingRule>(PostingEvent.class);
        for (var e : PostingEvent.values()) matrix.put(e, new AccountingPolicyService.PostingRule(
            AccountingPolicyService.PostingPermission.DISABLED, "Synthetic disabled fixture only"));
        return new AccountingPolicyService.PolicyCommand(UUID.randomUUID(), AccountingPolicyService.AuthoritativeLedger.LOCAL_GL,
            LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2), decisions, matrix, Map.of(), "Test evidence");
    }
    AccountingPolicy policy() {
        var c = command();
        return AccountingPolicy.builder().id(UUID.randomUUID()).saccoId("I1").policyVersion(1).makerId(makerId)
            .requestKey(c.requestKey()).effectiveFrom(date).openingDate(date).authoritativeLedger(c.authoritativeLedger())
            .decisionsJson(mapper.writeValueAsString(c.decisions())).postingMatrixJson(mapper.writeValueAsString(c.postingMatrix()))
            .accountMappingsJson("{}").evidenceReference("Test evidence").build();
    }
    AccountingPolicyService.ApprovalCommand decision() { return new AccountingPolicyService.ApprovalCommand(
        AccountingPolicyService.Decision.APPROVED, "Reviewed test evidence", "Fixture reviewed", true); }
}
