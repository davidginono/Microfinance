package com.sacco.mvp.accounting.controller;

import com.sacco.mvp.accounting.dto.AccountingLibraryDtos.*;
import com.sacco.mvp.accounting.policy.PostingEvent;
import com.sacco.mvp.accounting.service.AccountingCodeLibraryService;
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
import java.util.Arrays;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/library")
@PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_VIEW')")
public class AccountingCodeLibraryController {
    private final AccountingCodeLibraryService library;
    @InitBinder("codeForm") void bindCode(WebDataBinder binder) {binder.setAllowedFields("code","name","nameSw","description","sourceEvent");}
    @InitBinder("templateForm") void bindTemplate(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(5);binder.setAllowedFields("requestKey","expectedRevision","reason","rules[*].component","rules[*].debitCode","rules[*].creditCode");
    }
    @GetMapping
    String activities(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String state,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("activities",library.activities(actor,search,state,page));filters(model,search,state);return "accounting/library";
    }
    @GetMapping("/activities/{id}")
    String activity(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String state,@RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("activity",library.activity(actor,id));model.addAttribute("transactions",library.transactions(actor,id,search,state,page));
        filters(model,search,state);return "accounting/library";
    }
    @GetMapping("/activities/new") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String newActivity(@ModelAttribute("codeForm") CodeForm form,Model model) {return codeForm(model,null);}
    @GetMapping("/activities/{id}/transactions/new") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String newTransaction(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@ModelAttribute("codeForm") CodeForm form,Model model) {
        return codeForm(model,library.activity(actor,id));
    }
    @PostMapping("/activities") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String saveActivity(@AuthenticationPrincipal AppUserPrincipal actor,@ModelAttribute("codeForm") CodeForm form,BindingResult errors,Model model,RedirectAttributes flash) {
        if(!errors.hasErrors())try {UUID id=library.createActivity(actor,form);success(flash);return "redirect:/finance/library/activities/"+id;}
        catch(IllegalArgumentException failure){codeError(errors,key(failure));}catch(DataIntegrityViolationException failure){errors.rejectValue("code","library.error.duplicate");}
        return codeForm(model,null);
    }
    @PostMapping("/activities/{id}/transactions") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_CREATE')")
    String saveTransaction(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@ModelAttribute("codeForm") CodeForm form,BindingResult errors,Model model,RedirectAttributes flash) {
        form.setActivityId(id);
        if(!errors.hasErrors())try {UUID transaction=library.createTransaction(actor,form);success(flash);return "redirect:/finance/library/transactions/"+transaction;}
        catch(IllegalArgumentException failure){codeError(errors,key(failure));}catch(DataIntegrityViolationException failure){errors.rejectValue("code","library.error.duplicate");}
        return codeForm(model,library.activity(actor,id));
    }
    @GetMapping("/transactions/{id}")
    String transaction(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam(required=false) UUID version,
            @RequestParam(defaultValue="0") int page,Model model) {
        model.addAttribute("transaction",library.transaction(actor,id));model.addAttribute("template",library.template(actor,id,version));
        model.addAttribute("versions",library.versions(actor,id,page));return "accounting/library-transaction";
    }
    @GetMapping("/transactions/{id}/template") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String newTemplate(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,
            @RequestParam(defaultValue="") String accountSearch,@RequestParam(defaultValue="0") int accountPage,Model model) {
        var code=library.transaction(actor,id);var form=new TemplateForm();form.setExpectedRevision(code.revision());
        var current=library.template(actor,id,null);
        if(current!=null)for(var rule:current.rules()) {var r=new RuleForm();r.setComponent(rule.component());r.setDebitCode(rule.debitCode());r.setCreditCode(rule.creditCode());form.getRules().add(r);}
        if(form.getRules().isEmpty())form.getRules().add(new RuleForm());
        model.addAttribute("templateForm",form);return templateForm(actor,id,accountSearch,accountPage,model);
    }
    @PostMapping("/transactions/{id}/templates") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String saveTemplate(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@ModelAttribute("templateForm") TemplateForm form,
            BindingResult errors,Model model,RedirectAttributes flash) {
        if(!errors.hasErrors())try {UUID version=library.saveTemplate(actor,id,form);success(flash);return "redirect:/finance/library/transactions/"+id+"?version="+version;}
        catch(IllegalArgumentException failure){errors.reject(key(failure));}catch(DataIntegrityViolationException failure){errors.reject("library.error.conflict");}
        return templateForm(actor,id,"",0,model);
    }
    @PostMapping("/activities/{id}/{action:deactivate|reactivate}") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String activityState(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@PathVariable String action,RedirectAttributes flash) {
        library.activityState(actor,id,action.equals("reactivate"));success(flash);return "redirect:/finance/library/activities/"+id;
    }
    @PostMapping("/transactions/{id}/{action:deactivate|reactivate}") @PreAuthorize("@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')")
    String transactionState(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@PathVariable String action,RedirectAttributes flash) {
        library.transactionState(actor,id,action.equals("reactivate"));success(flash);return "redirect:/finance/library/transactions/"+id;
    }
    private String templateForm(AppUserPrincipal actor,UUID id,String search,int page,Model model) {
        if(model.getAttribute("templateForm") instanceof TemplateForm form && (form.getRules()==null || form.getRules().isEmpty()))
            form.setRules(new java.util.ArrayList<>(java.util.List.of(new RuleForm())));
        model.addAttribute("transaction",library.transaction(actor,id));model.addAttribute("components",AccountingCodeLibraryService.COMPONENTS);
        model.addAttribute("accountChoices",library.accounts(actor,search,page));model.addAttribute("accountSearch",search);return "accounting/library-template-form";
    }
    private String codeForm(Model model,Activity activity) {
        model.addAttribute("activity",activity);model.addAttribute("sourceEvents",Arrays.stream(PostingEvent.values()).map(Enum::name).toList());return "accounting/library-code-form";
    }
    private static void filters(Model model,String search,String state) {model.addAttribute("search",search);model.addAttribute("state",state);}
    private static void success(RedirectAttributes flash) {flash.addFlashAttribute("librarySuccess","library.saved");}
    private static void codeError(BindingResult errors,String key) {
        String field=switch(key) {case "library.error.code","library.error.name","library.error.nameSw","library.error.description","library.error.sourceEvent"->key.substring("library.error.".length());default->null;};
        if(field==null)errors.reject(key);else errors.rejectValue(field,key);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    String error(IllegalArgumentException failure,RedirectAttributes flash) {flash.addFlashAttribute("libraryError",key(failure));return "redirect:/finance/library";}
    @ExceptionHandler(org.springframework.beans.InvalidPropertyException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    String invalidFields(Model model) {model.addAttribute("accountingError","library.error.rules");return "accounting/error";}
    private static String key(IllegalArgumentException failure) {return failure.getMessage()!=null && failure.getMessage().startsWith("library.error.")?failure.getMessage():"library.error.validation";}
}
