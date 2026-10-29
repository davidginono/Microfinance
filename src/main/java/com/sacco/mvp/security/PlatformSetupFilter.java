package com.sacco.mvp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class PlatformSetupFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        String path=request.getServletPath();
        if(authentication!=null&&authentication.getPrincipal() instanceof AppUserPrincipal actor
            &&actor.isPlatformIdentity()&&actor.isPlatformSetupRequired()
            &&!path.equals("/system-admin/setup")&&!path.equals("/logout")
            &&!path.startsWith("/css/")&&!path.startsWith("/js/")&&!path.startsWith("/fonts/")&&!path.startsWith("/images/")) {
            if(request.getMethod().equals("GET"))response.sendRedirect(request.getContextPath()+"/system-admin/setup");
            else response.sendError(403,"Complete administrator setup first");
            return;
        }
        chain.doFilter(request,response);
    }
}
