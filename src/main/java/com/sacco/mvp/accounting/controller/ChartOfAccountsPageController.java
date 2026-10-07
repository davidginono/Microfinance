package com.sacco.mvp.accounting.controller;

import com.sacco.mvp.accounting.dto.AccountOnboardingForm;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.AccountFilter;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/finance/accounts")
public class ChartOfAccountsPageController {
    private final GeneralLedgerService ledger;
    @InitBinder("accountForm") void bind(WebDataBinder binder) {
        binder.setAllowedFields("parentId","code","name","nameSw","normalBalance","kind","purpose","description");
    }
    @GetMapping
    String chart(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String type,
            @RequestParam(defaultValue="") String kind,@RequestParam(defaultValue="") String state,
            @RequestParam(required=false) UUID parentId,Model model) {
        var filter=new AccountFilter(search.strip(),type,kind,state,parentId);
        if(parentId!=null) {
            var path=ledger.chartPath(actor,parentId);
            model.addAttribute("groupPath",path);model.addAttribute("selectedGroup",path.getLast());
        }
        model.addAttribute("accounts",ledger.chart(actor,filter,page));model.addAttribute("filter",filter);
        model.addAttribute("accountTypes",List.of("ASSET","LIABILITY","EQUITY","EXPENSE","INCOME"));
        model.addAttribute("accountKinds",List.of("HEADING","POSTING","CONTROL"));
        return "accounting/accounts";
    }
    @GetMapping({"/groups/new","/posting/new"})
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String form(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("accountForm") AccountOnboardingForm form,
            BindingResult errors,@RequestParam(defaultValue="") String parentSearch,@RequestParam(defaultValue="0") int parentPage,
            jakarta.servlet.http.HttpServletRequest request,Model model) {
        boolean group=request.getRequestURI().endsWith("/groups/new");
        if(form.getParentId()!=null && !errors.hasErrors()) {
            var parent=ledger.chartParent(actor,form.getParentId(),group);
            if(form.getCode()==null || form.getCode().isBlank())form.setCode(ledger.suggestChartCode(actor,parent.id(),group));
            if(form.getNormalBalance()==null || form.getNormalBalance().isBlank())form.setNormalBalance(
                List.of("ASSET","EXPENSE").contains(parent.type())?"DEBIT":"CREDIT");
        }
        return populateForm(actor,form,group,parentSearch,parentPage,model);
    }
    @PostMapping({"/groups","/posting"})
    @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String save(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("accountForm") AccountOnboardingForm form,
            BindingResult errors,jakarta.servlet.http.HttpServletRequest request,Model model,RedirectAttributes flash) {
        boolean group=request.getRequestURI().endsWith("/groups");
        if(!errors.hasErrors()) {
            try {
                ledger.onboardAccount(actor,form,group);
                flash.addFlashAttribute("accountingSuccess","coa.saved");return "redirect:/finance/accounts?parentId="+form.getParentId();
            } catch(IllegalArgumentException failure) {
                String key=errorKey(failure);
                String field=switch(key) {
                    case "accounting.error.chartCode","accounting.error.duplicate","accounting.error.accountCode" -> "code";
                    case "accounting.error.name" -> "name";
                    case "accounting.error.nameSw" -> "nameSw";
                    case "accounting.error.description" -> "description";
                    case "accounting.error.parent","accounting.error.chartParent" -> "parentId";
                    case "accounting.error.loanControl" -> "kind";
                    default -> null;
                };
                if(field==null)errors.reject(key);else errors.rejectValue(field,key);
            } catch(DataIntegrityViolationException failure) {errors.rejectValue("code","accounting.error.duplicate");}
        }
        return populateForm(actor,form,group,"",0,model);
    }
    private String populateForm(AppUserPrincipal actor,AccountOnboardingForm form,boolean group,String search,int page,Model model) {
        model.addAttribute("groupMode",group);model.addAttribute("parentSearch",search);
        model.addAttribute("parents",ledger.chartParents(actor,group,search,page));
        if(form.getParentId()!=null) {
            try {model.addAttribute("selectedParent",ledger.chartParent(actor,form.getParentId(),group));}
            catch(IllegalArgumentException failure) {model.addAttribute("parentError",errorKey(failure));}
        }
        model.addAttribute("accountPurposes",List.of("CASH","BANK","MOBILE_MONEY","CLEARING","SUSPENSE","LOAN_PRINCIPAL","INTEREST_RECEIVABLE","FEE_RECEIVABLE","ALLOWANCE","PAYABLE","FUNDING","CAPITAL","INCOME","EXPENSE","FIXED_ASSET","PREPAYMENT","TAX","INTERNAL_TRANSFER","OTHER"));
        return "accounting/account-form";
    }
    @PostMapping("/{id}/deactivate")
    String deactivate(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,RedirectAttributes flash) {
        ledger.deactivateAccount(actor,id);flash.addFlashAttribute("accountingSuccess","coa.deactivated");return "redirect:/finance/accounts";
    }
    @PostMapping("/{id}/reactivate")
    String reactivate(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,RedirectAttributes flash) {
        ledger.reactivateAccount(actor,id);flash.addFlashAttribute("accountingSuccess","coa.reactivated");return "redirect:/finance/accounts";
    }
    @ExceptionHandler(IllegalArgumentException.class)
    String error(IllegalArgumentException failure,RedirectAttributes flash) {
        flash.addFlashAttribute("accountingError",errorKey(failure));return "redirect:/finance/accounts";
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    String conflict(RedirectAttributes flash) {
        flash.addFlashAttribute("accountingError","accounting.error.duplicate");return "redirect:/finance/accounts";
    }
    private static String errorKey(IllegalArgumentException failure) {
        return failure.getMessage()!=null && failure.getMessage().startsWith("accounting.error.")?failure.getMessage():"accounting.error.validation";
    }
}
