package com.sacco.mvp.staffoffice.controller;

import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class OfficeNavigationAdvice {
    @ModelAttribute("officeReturnContext")
    public com.sacco.mvp.staffoffice.dto.OfficeWork.Navigation context(HttpServletRequest request,@AuthenticationPrincipal AppUserPrincipal actor) {
        return OfficeNavigation.context(request,actor);
    }
}
