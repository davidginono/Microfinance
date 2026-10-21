package com.sacco.mvp.accounting.controller;

import com.sacco.mvp.accounting.service.VoucherService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/finance/vouchers")
public class VoucherLaunchController {
    private final VoucherService vouchers;

    @GetMapping("/new")
    public String create(@AuthenticationPrincipal AppUserPrincipal actor, Model model) {
        var types = vouchers.creatableTypes(actor);
        if (types.isEmpty()) throw new AccessDeniedException("Voucher creation is not available to this user");
        model.addAttribute("creatableVoucherTypes", types);
        return "accounting/vouchers/launch";
    }
}
