package com.sacco.mvp.grouplending.controller;

import com.sacco.mvp.grouplending.dto.GroupCoverageDtos.*;
import com.sacco.mvp.grouplending.service.GroupCoverageService;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/groups/coverage")
public class GroupCoverageLegacyController {
    private final GroupCoverageService coverage;
    @ModelAttribute public void privateResponse(jakarta.servlet.http.HttpServletResponse r){r.setHeader("Cache-Control","no-store");}
    @InitBinder("coveragePolicy") public void policyBinder(WebDataBinder b){b.setAllowedFields("requestKey","expectedPreviousId","noticeDays","caseDays","promiseDays","reference","enabled","verified");}
    @InitBinder("coverageCall") public void callBinder(WebDataBinder b){b.setAllowedFields("requestKey","sourceId","sourceKind","policyId","amount","borrowerNoticeDate","personNoticeDate","borrowerNoticeEvidence","personNoticeEvidence","noticeLanguage","noticesVerified");}
    @InitBinder("coverageResponse") public void responseBinder(WebDataBinder b){b.setAllowedFields("requestKey","expectedSequence","kind","reason","confirmed");}
    @InitBinder("coverageLink") public void linkBinder(WebDataBinder b){b.setAllowedFields("requestKey","postingId","contributionId","payerEvidence","payerVerified");}
    @GetMapping public String list(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam UUID cycle,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="0") int sourcePage,Model m){
        m.addAttribute("cycle",cycle);m.addAttribute("cases",coverage.list(a,cycle,page,false));m.addAttribute("sources",coverage.sources(a,cycle,sourcePage));m.addAttribute("policy",coverage.policy(a,cycle).orElse(null));m.addAttribute("coverageActions",coverage.actions(a,false));return "groups/coverage-list";
    }
    @GetMapping("/policy") public String policy(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam UUID cycle,Model m){var f=new PolicyForm();coverage.policy(a,cycle).ifPresent(p->{f.setExpectedPreviousId(p.id());f.setNoticeDays(p.noticeDays());f.setCaseDays(p.caseDays());f.setPromiseDays(p.promiseDays());f.setReference(p.reference());f.setEnabled(p.enabled());});m.addAttribute("coveragePolicy",f);m.addAttribute("cycle",cycle);return "groups/coverage-policy";}
    @PostMapping("/policy") public String savePolicy(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam UUID cycle,@ModelAttribute("coveragePolicy") PolicyForm f,BindingResult errors,Model m){
        if(!errors.hasErrors())try{coverage.configure(a,cycle,f);return "redirect:/finance/groups/coverage?cycle="+cycle;}catch(IllegalArgumentException e){errors.reject(key(e));}
        m.addAttribute("cycle",cycle);return "groups/coverage-policy";
    }
    @GetMapping("/new") public String create(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam UUID cycle,@RequestParam String source,Model m){var parts=source.split("/",-1);if(parts.length!=2)throw new IllegalArgumentException("coverage.error.request");var f=new CallForm();f.setSourceId(UUID.fromString(parts[1]));f.setSourceKind(parts[0]);coverage.policy(a,cycle).ifPresent(p->f.setPolicyId(p.id()));m.addAttribute("coverageCall",f);return callView(a,cycle,f,m);}
    private String callView(AppUserPrincipal a,UUID cycle,CallForm f,Model m){m.addAttribute("cycle",cycle);m.addAttribute("source",coverage.sourceForCall(a,cycle,f.getSourceId(),f.getSourceKind()));m.addAttribute("policy",coverage.policy(a,cycle).orElse(null));return "groups/coverage-call";}
    @PostMapping public String call(@AuthenticationPrincipal AppUserPrincipal a,@RequestParam UUID cycle,@ModelAttribute("coverageCall") CallForm f,BindingResult errors,@RequestParam(required=false) MultipartFile noticePdf,Model m){
        if(!errors.hasErrors())try{return redirect(coverage.call(a,cycle,f,noticePdf));}catch(IllegalArgumentException e){errors.reject(key(e));}
        return callView(a,cycle,f,m);
    }
    @GetMapping("/{id}") public String detail(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int eventPage,@RequestParam(defaultValue="0") int paymentPage,Model m){var d=coverage.detail(a,id,eventPage,paymentPage,false);m.addAttribute("coverage",d);m.addAttribute("coverageActions",coverage.actions(a,false));if(!m.containsAttribute("coverageResponse")){var f=new ResponseForm();f.setExpectedSequence(d.header().sequence());m.addAttribute("coverageResponse",f);}return "groups/coverage-detail";}
    @PostMapping("/{id}/close") public String close(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute("coverageResponse") ResponseForm f,BindingResult errors,Model m){if(!errors.hasErrors())try{coverage.close(a,id,f);return redirect(id);}catch(IllegalArgumentException e){errors.reject(key(e));}return detail(a,id,0,0,m);}
    @GetMapping("/{id}/payments") public String payments(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,Model m){m.addAttribute("coverage",coverage.detail(a,id,0,0,false));m.addAttribute("choices",coverage.paymentChoices(a,id,page));return "groups/coverage-payments";}
    @GetMapping("/{id}/link") public String link(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam String selection,Model m){var parts=selection.split("/",-1);if(parts.length!=2)throw new IllegalArgumentException("coverage.error.payment");var f=new LinkForm();f.setPostingId(UUID.fromString(parts[0]));f.setContributionId(parts[1].isBlank()?null:UUID.fromString(parts[1]));m.addAttribute("coverageLink",f);return linkView(a,id,f,m);}
    private String linkView(AppUserPrincipal a,UUID id,LinkForm f,Model m){m.addAttribute("coverage",coverage.detail(a,id,0,0,false));try{m.addAttribute("selectedPayment",coverage.selectedPayment(a,id,f.getPostingId(),f.getContributionId()));}catch(IllegalArgumentException e){if(!(m.asMap().get("org.springframework.validation.BindingResult.coverageLink") instanceof BindingResult errors)||!errors.hasErrors())throw e;m.addAttribute("selectionUnavailable",true);}return "groups/coverage-link";}
    @PostMapping("/{id}/link") public String recordLink(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@ModelAttribute("coverageLink") LinkForm f,BindingResult errors,Model m){if(!errors.hasErrors())try{coverage.link(a,id,f);return redirect(id);}catch(IllegalArgumentException e){errors.reject(key(e));}return linkView(a,id,f,m);}
    @PostMapping("/{id}/correct") public String correct(@AuthenticationPrincipal AppUserPrincipal a,@PathVariable UUID id,@RequestParam UUID payment,@RequestParam UUID requestKey,@RequestParam String reason,org.springframework.web.servlet.mvc.support.RedirectAttributes flash){try{coverage.correct(a,id,payment,requestKey,reason);}catch(IllegalArgumentException e){flash.addFlashAttribute("coverageError",key(e));flash.addFlashAttribute("correctionPayment",payment);flash.addFlashAttribute("correctionReason",reason);flash.addFlashAttribute("correctionKey",requestKey);}return redirect(id);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) public String invalid(IllegalArgumentException e,Model m){m.addAttribute("groupError",key(e));return "groups/error";}
    static String key(IllegalArgumentException e){String k=e.getMessage();return k!=null&&k.matches("(?:coverage|group)\\.error\\.[A-Za-z]+")?k:"coverage.error.request";}
    private static String redirect(UUID id){return "redirect:/finance/groups/coverage/"+id;}
}
