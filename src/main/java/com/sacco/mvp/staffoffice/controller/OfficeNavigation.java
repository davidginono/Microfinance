package com.sacco.mvp.staffoffice.controller;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.staffoffice.model.StaffOffice;
import com.sacco.mvp.staffoffice.dto.OfficeWork.Navigation;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.*;
import java.util.*;

/** Allowlisted navigation context only; destination services still authorize every read and command. */
public class OfficeNavigation implements HandlerInterceptor {
    private static final Set<String> SOURCES=Set.of("loan-reviews","voucher-reviews","vouchers","account-reviews","accounts",
        "clients","loans","funding","collections","collateral","staff","support");
    public static Navigation context(HttpServletRequest request,AppUserPrincipal actor) {
        if(actor==null||!actor.isStaffSession())return null;
        var office=StaffOffice.fromSlug(Objects.toString(request.getParameter("office"),"")).orElse(null);
        String source=Objects.toString(request.getParameter("officeSource"),"");
        String search=Objects.toString(request.getParameter("officeSearch"),"");
        String sort=Objects.toString(request.getParameter("officeSort"),"number");
        int page;
        try {page=Integer.parseInt(Objects.toString(request.getParameter("officePage"),"0"));}catch(NumberFormatException invalid){return null;}
        if(office==null||!StaffOffice.assigned(actor.getStaffRoles()).contains(office)||!SOURCES.contains(source)
            ||search.length()>100||!Set.of("number","number_desc","newest","oldest").contains(sort)||page<0||page>10000)return null;
        return new Navigation(office,source,search,sort,page);
    }
    @Override public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView view) {
        if(view==null||view.getViewName()==null||!view.getViewName().startsWith("redirect:/")||view.getViewName().startsWith("redirect://"))return;
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        var context=context(request,authentication!=null&&authentication.getPrincipal() instanceof AppUserPrincipal a?a:null);
        if(context!=null)view.setViewName("redirect:"+context.attach(view.getViewName().substring("redirect:".length())));
    }
}
