package com.sacco.mvp.reporting.execution.controller;

import com.sacco.mvp.reporting.execution.dto.StatementOutputDtos.Layout;
import com.sacco.mvp.reporting.execution.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/finance/statement-outputs")
public class StatementOutputController {
 private final StatementOutputService outputs;
 private final StatementTypedExporter exporter;
 @GetMapping @PreAuthorize("@access.has(principal,'STATEMENT_VIEW')")
 public String index(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0")int page,@RequestParam(required=false)UUID result,Model model){model.addAttribute("outputs",outputs.list(actor,page));model.addAttribute("page",page);model.addAttribute("resultId",result);return "reports/statement-outputs";}
 @PostMapping @PreAuthorize("@access.has(principal,'STATEMENT_EXPORT')")
 public String capture(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID resultId,@RequestParam String language,@RequestParam(defaultValue="false")boolean landscape,@RequestParam(defaultValue="false")boolean institutionLogo,RedirectAttributes redirect){try{UUID id=outputs.capture(actor,resultId,new Layout(1,language,landscape,institutionLogo));return "redirect:/finance/statement-outputs/"+id;}catch(IllegalArgumentException ex){redirect.addFlashAttribute("outputError",key(ex));redirect.addFlashAttribute("outputLanguage",language);redirect.addFlashAttribute("outputLandscape",landscape);redirect.addFlashAttribute("outputLogo",institutionLogo);return "redirect:/finance/statement-outputs?result="+resultId;}}
 @GetMapping("/{id}") @PreAuthorize("@access.has(principal,'STATEMENT_VIEW')")
 public String view(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,Model model){var output=outputs.get(actor,id);model.addAttribute("output",output);model.addAttribute("outputActor",actor.getMemberId());model.addAttribute("artifacts",outputs.artifacts(actor,id));Map<String,String> labels=new LinkedHashMap<>(),disclosures=new LinkedHashMap<>(),current=new LinkedHashMap<>(),comparison=new LinkedHashMap<>(),units=new LinkedHashMap<>();for(var row:output.result().getVisibleRows()){labels.put(row.id(),exporter.label(row,output.layout()));units.put(row.id(),exporter.unit(row.unit(),output.layout()));current.put(row.id(),exporter.value(row.current(),row.unit()));comparison.put(row.id(),exporter.value(row.comparison(),row.unit()));}for(var disclosure:output.result().disclosures())disclosures.put(disclosure,exporter.disclosure(disclosure,output.layout()));model.addAttribute("rowLabels",labels);model.addAttribute("rowUnits",units);model.addAttribute("currentValues",current);model.addAttribute("comparisonValues",comparison);model.addAttribute("disclosures",disclosures);return "reports/statement-outputs";}
 @PostMapping("/{id}/review") @PreAuthorize("@access.has(principal,'REPORT_RUN_APPROVE') and @access.has(principal,'STATEMENT_EXPORT')")
 public String review(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@RequestParam String evidence,RedirectAttributes redirect){try{outputs.review(actor,id,evidence);redirect.addFlashAttribute("outputSuccess","accounting.release.updated");}catch(IllegalArgumentException ex){redirect.addFlashAttribute("outputError",key(ex));}return "redirect:/finance/statement-outputs/"+id;}
 @GetMapping("/{id}/artifacts/{artifact}") @PreAuthorize("@access.has(principal,'STATEMENT_EXPORT')")
 public ResponseEntity<byte[]> download(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable UUID id,@PathVariable UUID artifact){var data=outputs.download(actor,id,artifact);String media=switch(data.format()){case CSV->"text/csv;charset=UTF-8";case XLSX->"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";case PDF->"application/pdf";};return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(media)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=statement."+data.format().name().toLowerCase(Locale.ROOT)).header("X-Content-Type-Options","nosniff").header("X-Report-SHA256",data.checksum()).body(data.bytes());}
 private static String key(IllegalArgumentException ex){return ex.getMessage()!=null&&ex.getMessage().startsWith("accounting.release.error.")?ex.getMessage():"accounting.release.error.source";}
}
