package com.sacco.mvp.clients.portal.security;

import com.sacco.mvp.clients.portal.dto.ClientPortalDtos.Session;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public final class ClientPortalIsolationFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();String path=request.getServletPath();
        if(path.isEmpty())path=request.getRequestURI().substring(request.getContextPath().length());
        if(auth!=null&&auth.getPrincipal() instanceof Session && !(path.equals("/client-portal")||path.startsWith("/client-portal/")||path.equals("/logout")||path.startsWith("/css/")||path.startsWith("/js/")||path.startsWith("/fonts/")||path.startsWith("/images/")||path.equals("/error"))) {
            response.setHeader("Cache-Control","no-store");response.sendError(403);return;
        }
        chain.doFilter(request,response);
    }
}
