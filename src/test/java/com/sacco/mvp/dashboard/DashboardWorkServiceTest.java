package com.sacco.mvp.dashboard;

import com.sacco.mvp.dashboard.repository.DashboardWorkRepository;
import com.sacco.mvp.dashboard.repository.DashboardWorkRepository.Bucket;
import com.sacco.mvp.dashboard.service.DashboardWorkService;
import com.sacco.mvp.accounting.approval.service.VoucherWorkflowService;
import com.sacco.mvp.accounting.service.VoucherService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardWorkServiceTest {
    final DashboardWorkRepository repository=mock(DashboardWorkRepository.class);
    final VoucherWorkflowService vouchers=mock(VoucherWorkflowService.class);
    final VoucherService voucherAccess=mock(VoucherService.class);
    final ApplicationClock clock=mock(ApplicationClock.class);
    final OffsetDateTime now=OffsetDateTime.parse("2026-10-22T09:42:00+03:00");
    final DashboardWorkService service=new DashboardWorkService(repository,new AccessControlService(),vouchers,voucherAccess,clock);
    @BeforeEach void setup(){when(clock.now()).thenReturn(now);}
    AppUserPrincipal actor(Position role,String branch,boolean staff,UserClaim... claims){return (staff ? new com.sacco.mvp.security.AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).position(role).memberAccount(!staff).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),new HashSet<>(List.of(claims))) : com.sacco.mvp.config.MvcSecurityTestConfiguration.formerClient(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).position(role).memberAccount(!staff).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),new HashSet<>(List.of(claims))));}
    @Test void missingScopeIsDeniedAndAnEmptyAssignmentReadsNoBusinessCounts(){
        assertThatThrownBy(()->service.read(actor(Position.ACCOUNTANT,null,true,UserClaim.ACCOUNTANT_QUEUE_VIEW),0,BigDecimal.ZERO)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.read(actor(Position.ACCOUNTANT,"B1",true),0,BigDecimal.ZERO).infolets()).isEmpty();
        verifyNoInteractions(repository,vouchers,voucherAccess);
    }
    @Test void invalidAndExtremeDisplayFiltersFailBeforePersistence(){
        var a=actor(Position.MANAGER,"B1",true,UserClaim.LOAN_RECORDING_VIEW);
        for(var amount:List.of(new BigDecimal("-1"),new BigDecimal("0.001"),new BigDecimal("1E+100000"))){assertThatThrownBy(()->service.read(a,0,amount)).isInstanceOf(IllegalArgumentException.class);}
        assertThatThrownBy(()->service.read(a,3651,BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(repository);
    }
    @Test void managerCountsUseTheActualQueueStatusAndNativeDestination(){
        var a=actor(Position.MANAGER,"B1",true,UserClaim.LOAN_RECORDING_VIEW);
        when(repository.loans(a,List.of("SUBMITTED"),now.minusDays(3),new BigDecimal("400000"))).thenReturn(List.of(new Bucket("SUBMITTED","SUBMITTED",2,new BigDecimal("1200000"),now.minusDays(6))));
        var cards=service.read(a,3,new BigDecimal("400000")).infolets();assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().count()).isEqualTo(2);assertThat(cards.getFirst().oldestDays()).isEqualTo(6);
        assertThat(cards.getFirst().href()).isEqualTo("/finance/loan-records?state=SUBMITTED");

    }
    @Test void boardCountsRetainTheReviewerAndStageScope(){
        var a=actor(Position.BOARD,"B1",true,UserClaim.BOARD_QUEUE_VIEW);
        assertThat(service.read(a,0,BigDecimal.ZERO).infolets()).isEmpty();
        verifyNoInteractions(repository);

    }
    @Test void voucherDraftsStaySeparateFromApprovalsAndPostingAndTypesAreClaimScoped(){
        var a=actor(Position.ACCOUNTANT,"B1",true,UserClaim.VOUCHER_WORKSPACE_VIEW,UserClaim.ACCOUNTING_BUSINESS_VIEW);
        when(repository.vouchers(a,List.of("RECEIPT","PAYMENT"),now,BigDecimal.ZERO)).thenReturn(List.of(new Bucket("INCOMPLETE","RECEIPT",1,BigDecimal.ZERO,now),new Bucket("INITIATED","PAYMENT",2,new BigDecimal("450000"),now.minusDays(2))));
        var cards=service.read(a,0,BigDecimal.ZERO).infolets();assertThat(cards).hasSize(8);
        assertThat(cards.getFirst().id()).isEqualTo("voucher-INCOMPLETE");assertThat(cards.getFirst().count()).isEqualTo(1);
        var approval=cards.stream().filter(c->c.id().equals("voucher-INITIATED")).findFirst().orElseThrow();assertThat(approval.count()).isEqualTo(2);assertThat(approval.groups().getFirst().href()).isEqualTo("/finance/approval?category=INITIATED&type=PAYMENT");
        verify(repository).vouchers(a,List.of("RECEIPT","PAYMENT"),now,BigDecimal.ZERO);verifyNoMoreInteractions(repository);
    }
    @Test void memberCountsRequireOwnershipAndGuaranteeAmountsRemainUnavailable(){
        var a=actor(Position.MEMBER,"B1",false,UserClaim.MEMBER_LOANS_VIEW);
        assertThatThrownBy(()->service.read(a,0,BigDecimal.ZERO)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);

    }
}
