package com.sacco.mvp.accounting.pettycash.controller;

import com.sacco.mvp.accounting.pettycash.service.PettyCashService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.web.WebRequestClassifier;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice @RequiredArgsConstructor
public class PettyCashNavigationAdvice {
    private final PettyCashService pettyCash;

    @ModelAttribute("pettyCashAvailable")
    public boolean available(@AuthenticationPrincipal AppUserPrincipal actor,HttpServletRequest request){
        return !WebRequestClassifier.isJsonRequest(request) && !WebRequestClassifier.isStatementReportFragment(request)
            && pettyCash.available(actor);
    }
}
