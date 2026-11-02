package com.sacco.mvp.service;

import com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.*;
import com.sacco.mvp.clients.applications.repository.ClientApplicationRepository;
import com.sacco.mvp.clients.applications.service.ClientApplicationService;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.OwnStatus;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ClientApplicationReviewTest {
    final ClientApplicationRepository records=mock(ClientApplicationRepository.class);
    final OnboardingService onboarding=mock(OnboardingService.class);
    final LoanWorkflowService loans=mock(LoanWorkflowService.class);
    final LoanAttachmentService attachments=mock(LoanAttachmentService.class);
    final PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    final AuditService audit=mock(AuditService.class);
    final ApplicationClock clock=mock(ApplicationClock.class);
    final UUID client=UUID.randomUUID(),id=UUID.randomUUID(),request=UUID.randomUUID();
    final Member member=Member.builder().id(client).saccoId("I1").stationId("B1").fullName("Synthetic Client").memberAccount(true).clientPortalEnabled(false).position(Position.MEMBER).status(MemberStatus.ACTIVE).build();
    final AppUserPrincipal owner=AppUserPrincipal.forClientPortal(member);
    final ClientApplicationService service=new ClientApplicationService(records,onboarding,mock(FormSchemaService.class),loans,mock(LoanProductRequiredAttachmentService.class),attachments,mock(FinancialDetailsService.class),clock,JsonMapper.builder().findAndAddModules().build(),tx,audit);
    Details detail(String state){return new Details(id,client,"123","Synthetic Client",state,2,"{}","[]","{}");}
    @BeforeEach void setup(){when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());when(onboarding.ownStatus(owner)).thenReturn(new OwnStatus("VERIFY",null));when(records.details(owner,id,client)).thenReturn(detail("ALL_GUARANTORS_APPROVED"));when(attachments.parse("[]")).thenReturn(List.of(Map.of("documentPurpose","SIGNED_APPLICATION")));when(records.latestForm(owner,id,client)).thenReturn(Optional.of("{\"clientId\":\""+client+"\"}"));when(clock.now()).thenReturn(OffsetDateTime.now());}
    @Test void completeGuarantorApprovalsAdvanceOnlyTheSameOwnedVerifiedApplication(){assertThat(service.sendToReview(owner,id,request,2,true)).isEqualTo(id);verify(loans).submitToManager(id,client);verify(records).command(eq(owner),eq(id),eq(client),eq(3),eq(request),anyString(),anyString(),any());verify(audit).logEvent(eq("LOAN_APPLICATION"),eq(id),eq("CLIENT_APPLICATION_SENT_TO_REVIEW"),eq(client),eq(AuditEventStatus.SUCCESS),anyString(),anyString(),anyString(),eq("I1"),eq("B1"),anyMap());}
    @Test void pendingGuarantorsStaleVersionAndRevokedProfilesCannotAdvance(){when(records.details(owner,id,client)).thenReturn(detail("AWAITING_GUARANTORS"));assertThatThrownBy(()->service.sendToReview(owner,id,request,2,true)).hasMessage("applications.error.state");when(records.details(owner,id,client)).thenReturn(detail("ALL_GUARANTORS_APPROVED"));assertThatThrownBy(()->service.sendToReview(owner,id,request,1,true)).hasMessage("applications.error.changed");when(onboarding.ownStatus(owner)).thenReturn(new OwnStatus("REVOKE",null));assertThatThrownBy(()->service.sendToReview(owner,id,request,2,true)).hasMessage("applications.error.verification");verifyNoInteractions(loans);}
    @Test void fullBorrowerUsersCannotUseTheProvisionalWorkspace(){member.setClientPortalEnabled(true);var normal=new AppUserPrincipal(member,Set.of(UserClaim.MEMBER_LOANS_CREATE),false);assertThatThrownBy(()->service.list(normal,"",0)).isInstanceOf(AccessDeniedException.class);}
}
