package com.sacco.mvp.accounting.pettycash;

import com.sacco.mvp.accounting.pettycash.controller.PettyCashNavigationAdvice;
import com.sacco.mvp.accounting.pettycash.service.PettyCashService;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PettyCashNavigationAdviceTest {
    @Test void shellUsesCurrentSetupWhileJsonRequestsSkipNavigationReads(){
        var service=mock(PettyCashService.class);var actor=mock(AppUserPrincipal.class);
        var advice=new PettyCashNavigationAdvice(service);var request=new MockHttpServletRequest();
        when(service.available(actor)).thenReturn(false,true);
        assertThat(advice.available(actor,request)).isFalse();assertThat(advice.available(actor,request)).isTrue();
        request.addHeader("Accept","application/json");assertThat(advice.available(actor,request)).isFalse();
        request.removeHeader("Accept");request.addHeader("X-Requested-With","XMLHttpRequest");assertThat(advice.available(actor,request)).isFalse();
        verify(service,times(2)).available(actor);
    }
}
