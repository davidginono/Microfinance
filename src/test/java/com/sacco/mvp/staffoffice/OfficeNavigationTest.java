package com.sacco.mvp.staffoffice;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.staffoffice.controller.OfficeNavigation;
import com.sacco.mvp.staffoffice.dto.OfficeWork.Navigation;
import com.sacco.mvp.staffoffice.model.StaffOffice;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OfficeNavigationTest {
    AppUserPrincipal actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).position(Position.CREDIT_OFFICER)
        .status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).saccoId("I1").stationId("B1").build(),Set.of());
    MockHttpServletRequest request() {
        var request=new MockHttpServletRequest();request.setParameter("office","credit-officer");request.setParameter("officeSource","loan-reviews");
        request.setParameter("officeSearch","LN-5 & test");request.setParameter("officeSort","oldest");request.setParameter("officePage","2");return request;
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void queueReturnKeepsOnlyValidatedOfficeAndFilterParameters() {
        var request=request();request.setParameter("returnTo","https://outside.invalid/");request.setParameter("stationId","FOREIGN");
        var context=OfficeNavigation.context(request,actor);
        assertThat(context).isNotNull();
        var uri=URLDecoder.decode(context.getHref(),StandardCharsets.UTF_8);
        assertThat(uri).startsWith("/staff/offices/credit-officer/queue?").contains("source=loan-reviews","search=LN-5 & test","sort=oldest","page=2").doesNotContain("FOREIGN","outside");
        var record=context.attach("/finance/loan-records/123");
        assertThat(UriComponentsBuilder.fromUriString(record).build().getQueryParams().getFirst("officeSearch")).isEqualTo("LN-5%20%26%20test");
    }
    @Test void unassignedOfficesAndInvalidFiltersHaveNoReturnContext() {
        var r=request();r.setParameter("office","chief-accountant");assertThat(OfficeNavigation.context(r,actor)).isNull();
        r=request();r.setParameter("officePage","-1");assertThat(OfficeNavigation.context(r,actor)).isNull();
        r=request();r.setParameter("officeSource","https://outside.invalid");assertThat(OfficeNavigation.context(r,actor)).isNull();
    }
    @Test void commandRedirectPreservesQueueContextAndExistingDestinationParameters() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));
        var view=new ModelAndView("redirect:/finance/loan-records/123?tab=history");
        new OfficeNavigation().postHandle(request(),new MockHttpServletResponse(),new Object(),view);
        assertThat(view.getViewName()).startsWith("redirect:/finance/loan-records/123?tab=history&")
            .contains("office=credit-officer","officeSource=loan-reviews","officePage=2");
        var external=new ModelAndView("redirect:https://outside.invalid");
        new OfficeNavigation().postHandle(request(),new MockHttpServletResponse(),new Object(),external);
        assertThat(external.getViewName()).isEqualTo("redirect:https://outside.invalid");
    }
}
