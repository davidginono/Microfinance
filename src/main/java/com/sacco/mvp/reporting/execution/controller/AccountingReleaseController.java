package com.sacco.mvp.reporting.execution.controller;

import com.sacco.mvp.reporting.execution.dto.AccountingReleaseDtos.*;
import com.sacco.mvp.reporting.execution.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/accounting-release")
public class AccountingReleaseController {
 private final AccountingReleaseService releases;
 private final StatementOutputService outputs;
 private final ReportRunService runs;
 private final ObjectMapper mapper;
 private final StatementTypedExporter exporter;
 @GetMapping @PreAuthorize("@access.has(principal,'ACCOUNTING_RELEASE_VIEW')")
 public String index(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0")int page,Model model){model.addAttribute("releases",releases.list(actor,page));model.addAttribute("page",page);if(actor.getClaims().contains("ACCOUNTING_RELEASE_REQUEST")){model.addAttribute("outputs",outputs.list(actor,0));model.addAttribute("samples",runs.list(actor,0).stream().filter(r->r.status().equals("APPROVED")).toList());}if(!model.containsAttribute("requestKey"))model.addAttribute("requestKey",UUID.randomUUID());return "reports/accounting-release";}
 @PostMapping @PreAuthorize("@access.has(principal,'ACCOUNTING_RELEASE_REQUEST')")
 public String propose(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,@RequestParam UUID statementOutput,@RequestParam UUID firstSample,@RequestParam UUID secondSample,@RequestParam String evidence,RedirectAttributes redirect){try{UUID id=releases.propose(actor,new Proposal(requestKey,statementOutput,firstSample,secondSample,evidence));return "redirect:/finance/accounting-release/"+id;}catch(IllegalArgumentException ex){redirect.addFlashAttribute("releaseError",key(ex));redirect.addFlashAttribute("requestKey",requestKey);redirect.addFlashAttribute("selectedOutput",statementOutput);redirect.addFlashAttribute("selectedFirst",firstSample);redirect.addFlashAttribute("selectedSecond",secondSample);redirect.addFlashAttribute("proposalEvidence",evidence);return "redirect:/finance/accounting-release";}}
 @GetMapping("/{id}") @PreAuthorize("@access.has(principal,'ACCOUNTING_RELEASE_VIEW')")
 public String view(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Locale locale,Model model){var display=releases.display(actor,id);var release=display.release();var dependencies=display.dependencies();model.addAttribute("release",release);model.addAttribute("dependencies",dependencies);var layout=new com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.Layout(1,locale.getLanguage().equals("sw")?"sw":"en",false,false);var disclosures=new ArrayList<String>();if(dependencies.get("mandatoryDisclosures") instanceof List<?> values)for(var value:values)disclosures.add(exporter.disclosure(value.toString(),layout));model.addAttribute("releaseDisclosures",disclosures);model.addAttribute("releaseActor",actor.getMemberId());model.addAttribute("independentReleaseActor",!release.requester().equals(actor.getMemberId())&&release.decisions().stream().noneMatch(d->d.reviewer().equals(actor.getMemberId())));return "reports/accounting-release";}
 @PostMapping("/{id}/decide") @PreAuthorize("(#stage.name() == 'ACCOUNTANT' and @access.has(principal,'ACCOUNTING_RELEASE_APPROVE')) or (#stage.name() == 'COMPLIANCE' and @access.has(principal,'ACCOUNTING_COMPLIANCE_RELEASE_APPROVE')) or (#stage.name() == 'STAFF' and @access.has(principal,'ACCOUNTING_RELEASE_ACCEPT'))")
 public String decide(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam Stage stage,@RequestParam boolean approved,@RequestParam String evidence,RedirectAttributes redirect){try{releases.decide(actor,id,stage,approved,evidence);redirect.addFlashAttribute("releaseSuccess","accounting.release.updated");}catch(IllegalArgumentException ex){redirect.addFlashAttribute("releaseError",key(ex));}return "redirect:/finance/accounting-release/"+id;}
 @PostMapping("/{id}/withdraw") @PreAuthorize("@access.has(principal,'ACCOUNTING_RELEASE_APPROVE')")
 public String withdraw(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String reason,RedirectAttributes redirect){try{releases.withdraw(actor,id,reason);redirect.addFlashAttribute("releaseSuccess","accounting.release.updated");}catch(IllegalArgumentException ex){redirect.addFlashAttribute("releaseError",key(ex));}return "redirect:/finance/accounting-release/"+id;}
 private static String key(IllegalArgumentException ex){return ex.getMessage()!=null&&ex.getMessage().startsWith("accounting.release.error.")?ex.getMessage():"accounting.release.error.source";}
}
