package com.sacco.mvp.web;

import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ChairpersonProcessedLoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/chairperson/processed-loans")
@PreAuthorize("@access.canViewProcessedLoans(principal)")
public class ChairpersonProcessedLoanController {
    private final ChairpersonProcessedLoanService processedLoanService;

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserPrincipal principal,
                       @RequestParam(required = false) String search,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                       @RequestParam(required = false) LoanStatus status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("processedLoans",
            processedLoanService.list(principal, search, fromDate, toDate, status, page));
        return "chairperson/processed-loans";
    }

    @GetMapping("/{loanId}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal principal,
                         @PathVariable UUID loanId,
                         Model model) {
        model.addAttribute("loan", processedLoanService.detail(principal, loanId));
        return "chairperson/processed-loan-detail";
    }
}
