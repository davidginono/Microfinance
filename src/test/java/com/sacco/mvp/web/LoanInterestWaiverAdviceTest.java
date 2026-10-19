package com.sacco.mvp.web;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.HandlerMapping;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanInterestWaiverAdviceTest {
    final UUID loan=UUID.randomUUID();
    final LoanRepaymentLedgerService ledger=mock(LoanRepaymentLedgerService.class);
    final ApplicationClock clock=mock(ApplicationClock.class);
    final LoanInterestWaiverAdvice advice=new LoanInterestWaiverAdvice(ledger,clock);
    final AppUserPrincipal actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
        .position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of(UserClaim.LOAN_RECORDING_VIEW),true);
    MockHttpServletRequest request(String pattern){
        var r=new MockHttpServletRequest("GET",pattern.replace("{id}",loan.toString()));
        r.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,pattern);
        r.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,Map.of("id",loan.toString()));return r;
    }
    @Test void draftsWithoutALedgerStillOpen(){
        var model=new ExtendedModelMap();advice.details(request("/finance/loan-records/{id}"),actor,model);
        assertThat(model).isEmpty();verify(ledger).hasLedger(loan);verifyNoMoreInteractions(ledger);
    }
    @Test void otherPageAndJsonRoutesDoNotLoadWaiverData(){
        advice.details(request("/finance/loan-recording/choices/{id}"),actor,new ExtendedModelMap());verifyNoInteractions(ledger);
    }
    @Test void scopeDenialIsPreserved(){
        when(ledger.hasLedger(loan)).thenReturn(true);
        when(ledger.interestWaivers(loan,actor,0)).thenThrow(new org.springframework.security.access.AccessDeniedException("Forbidden"));
        assertThatThrownBy(()->advice.details(request("/finance/loan-records/{id}"),actor,new ExtendedModelMap()))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void preservesFailedFormAndUsesIndependentBoundedHistoryPage(){
        when(ledger.hasLedger(loan)).thenReturn(true);when(ledger.interestWaivers(loan,actor,2)).thenReturn(List.of());
        var request=request("/finance/loan-records/{id}");request.setParameter("waiverPage","2");
        var model=new ExtendedModelMap();var form=new LoanPrepaymentController.WaiverForm();form.setReason("Retained evidence");model.addAttribute("waiverForm",form);
        advice.details(request,actor,model);
        assertThat(model.get("waiverForm")).isSameAs(form);assertThat(model.get("waiverPage")).isEqualTo(2);
        assertThat(model.get("waiverBase")).isEqualTo("/finance/loan-records/"+loan);
        verify(ledger).interestWaivers(loan,actor,2);
    }
}
