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
    AppUserPrincipal actor(Position role,String branch,boolean staff,UserClaim... claims){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId(branch).position(role).memberAccount(!staff).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),new HashSet<>(List.of(claims)),staff);}
    @Test void missingScopeAndUnrelatedClaimsCannotReadCounts(){
        for(var a:List.of(actor(Position.ACCOUNTANT,null,true,UserClaim.ACCOUNTANT_QUEUE_VIEW),actor(Position.ACCOUNTANT,"B1",true))){
            assertThatThrownBy(()->service.read(a,0,BigDecimal.ZERO)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(repository,vouchers,voucherAccess);
    }
    @Test void invalidAndExtremeDisplayFiltersFailBeforePersistence(){
        var a=actor(Position.MANAGER,"B1",true,UserClaim.MANAGER_QUEUE_VIEW);
        for(var amount:List.of(new BigDecimal("-1"),new BigDecimal("0.001"),new BigDecimal("1E+100000"))){assertThatThrownBy(()->service.read(a,0,amount)).isInstanceOf(IllegalArgumentException.class);}
        assertThatThrownBy(()->service.read(a,3651,BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(repository);
    }
    @Test void managerCountsUseTheActualQueueStatusAndNativeDestination(){
        var a=actor(Position.MANAGER,"B1",true,UserClaim.MANAGER_QUEUE_VIEW);
        when(repository.loans(a,List.of("READY_FOR_MANAGER"),false,null,now.minusDays(3),new BigDecimal("400000"))).thenReturn(List.of(new Bucket("READY_FOR_MANAGER","READY_FOR_MANAGER",2,new BigDecimal("1200000"),now.minusDays(6))));
        var cards=service.read(a,3,new BigDecimal("400000")).infolets();assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().count()).isEqualTo(2);assertThat(cards.getFirst().oldestDays()).isEqualTo(6);assertThat(cards.getFirst().href()).isEqualTo("/manager/loan-applications");
        verify(repository).loans(a,List.of("READY_FOR_MANAGER"),false,null,now.minusDays(3),new BigDecimal("400000"));verifyNoMoreInteractions(repository);
    }
    @Test void boardCountsRetainTheReviewerAndStageScope(){
        var a=actor(Position.BOARD,"B1",true,UserClaim.BOARD_QUEUE_VIEW);
        assertThat(service.read(a,0,BigDecimal.ZERO).infolets()).extracting(DashboardWorkService.Infolet::id).containsExactly("queue-board");
        verify(repository).loans(a,List.of("AWAITING_BOARD"),false,"BOARD",now,BigDecimal.ZERO);verifyNoMoreInteractions(repository);
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
        var a=actor(Position.MEMBER,"B1",false,UserClaim.MEMBER_LOANS_VIEW,UserClaim.GUARANTOR_REQUESTS_VIEW);
        when(repository.guarantees(a,now)).thenReturn(List.of(new Bucket("PENDING","PENDING",1,null,now.minusDays(1))));
        var cards=service.read(a,0,BigDecimal.ZERO).infolets();assertThat(cards).extracting(DashboardWorkService.Infolet::id).containsExactly("loan-drafts","loan-applications","guarantees");
        assertThat(cards.getLast().amount()).isNull();assertThat(cards.getLast().href()).isEqualTo("/app/guarantee-requests");
        verify(repository).loans(eq(a),anyList(),eq(true),isNull(),eq(now),eq(BigDecimal.ZERO));verify(repository).guarantees(a,now);verifyNoMoreInteractions(repository);verifyNoInteractions(vouchers,voucherAccess);
    }
}
