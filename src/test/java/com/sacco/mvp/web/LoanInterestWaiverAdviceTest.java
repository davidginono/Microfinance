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
    final LoanInterestWaiverAdvice advice=new LoanInterestWaiverAdvice(ledger);
    final AppUserPrincipal actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
        .position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of(UserClaim.LOAN_RECORDING_VIEW));
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
    @Test void fundedDetailsLinkToTheWaiverWorkspaceWithoutLoadingHistory(){
        when(ledger.hasLedger(loan)).thenReturn(true);
        for(String base:List.of("/finance/loan-records/","/repayments/loans/")){
            var model=new ExtendedModelMap();
            advice.details(request(base+"{id}"),actor,model);
            assertThat(model.get("waiverPageLink")).isEqualTo(base+loan+"/interest-waivers");
        }
        verify(ledger,times(2)).hasLedger(loan);
        verifyNoMoreInteractions(ledger);
    }
}
