package com.sacco.mvp.web;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ChairpersonConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/chairperson/configurations")
@PreAuthorize("@access.canViewSaccoConfigurations(principal)")
public class ChairpersonConfigurationController {
    private final ChairpersonConfigurationService configurationService;

    @GetMapping
    public String overview(@AuthenticationPrincipal AppUserPrincipal principal,
                           @RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        model.addAttribute("configuration", configurationService.overview(principal, search, page));
        return "chairperson/configurations";
    }

    @GetMapping("/loan-products/{productId}")
    public String product(@AuthenticationPrincipal AppUserPrincipal principal,
                          @PathVariable UUID productId,
                          Model model) {
        model.addAttribute("product", configurationService.product(principal, productId));
        return "chairperson/loan-product-configuration";
    }
}
