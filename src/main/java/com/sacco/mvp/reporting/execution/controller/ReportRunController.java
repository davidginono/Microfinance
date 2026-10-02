package com.sacco.mvp.reporting.execution.controller;

import com.sacco.mvp.reporting.*;
import com.sacco.mvp.reporting.execution.dto.ReportRunDtos.Request;
import com.sacco.mvp.reporting.execution.service.ReportRunService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.*;
import java.util.*;

@Controller
@RequestMapping("/reports/runs")
@RequiredArgsConstructor
public class ReportRunController {
 private final ReportRunService runs;
 private final OperationalReportTemplateService templates;
 private final OperationalReportExportService exports;
 private final ApplicationClock clock;
 @GetMapping
 @PreAuthorize("@access.has(principal,'REPORT_RUN')")
 public String index(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0")int page,Model model){
  model.addAttribute("runs",runs.list(actor,page));model.addAttribute("listPage",page);model.addAttribute("templates",templates.list(actor,0));model.addAttribute("requestKey",UUID.randomUUID());model.addAttribute("from",clock.today().withDayOfMonth(1));model.addAttribute("through",clock.today());model.addAttribute("cutoff",clock.now());return "reports/runs";
 }
 @PostMapping
 @PreAuthorize("@access.has(principal,'REPORT_RUN') and @access.has(principal,'REPORT_EXPORT')")
 public String request(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam UUID requestKey,@RequestParam UUID templateVersion,@RequestParam LocalDate from,@RequestParam LocalDate through,@RequestParam OffsetDateTime recordedCutoff,@RequestParam Set<OperationalReportExportService.Format> formats,@RequestParam(required=false)UUID restates,@RequestParam(required=false)String reason,RedirectAttributes redirect){
  try{UUID id=runs.request(actor,new Request(requestKey,templateVersion,from,through,recordedCutoff,formats,restates,reason));redirect.addFlashAttribute("runSuccess","report.run.queued");return "redirect:/reports/runs/"+id;}
  catch(IllegalArgumentException ex){redirect.addFlashAttribute("runError",key(ex));return "redirect:/reports/runs";}
 }
 @GetMapping("/{id}")
 @PreAuthorize("@access.has(principal,'REPORT_RUN')")
 public String view(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(defaultValue="0")int page,Model model){
  var run=runs.get(id,actor);model.addAttribute("run",run);model.addAttribute("runActor",actor.getMemberId());model.addAttribute("artifacts",runs.artifacts(id,actor));model.addAttribute("rowPage",page);
  if(Set.of("READY","APPROVED").contains(run.status()))model.addAttribute("frozen",runs.frozen(id,actor,page));
  Map<Object,String> headings=new LinkedHashMap<>();for(var column:run.definition().columns()){String heading=exports.heading(column,run.definition().language());headings.put(column.field(),heading);headings.put(column.field().name(),heading);}model.addAttribute("headings",headings);return "reports/runs";
 }
 @PostMapping("/{id}/cancel") @PreAuthorize("@access.has(principal,'REPORT_RUN')")
 public String cancel(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,RedirectAttributes redirect){try{runs.cancel(id,actor);redirect.addFlashAttribute("runSuccess","report.run.updated");}catch(IllegalArgumentException ex){redirect.addFlashAttribute("runError",key(ex));}return "redirect:/reports/runs/"+id;}
 @PostMapping("/{id}/retry") @PreAuthorize("@access.has(principal,'REPORT_RUN') and @access.has(principal,'REPORT_EXPORT')")
 public String retry(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,RedirectAttributes redirect){try{runs.retry(id,actor);redirect.addFlashAttribute("runSuccess","report.run.queued");}catch(IllegalArgumentException ex){redirect.addFlashAttribute("runError",key(ex));}return "redirect:/reports/runs/"+id;}
 @PostMapping("/{id}/approve") @PreAuthorize("@access.has(principal,'REPORT_RUN_APPROVE')")
 public String approve(@PathVariable UUID id,@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String evidence,RedirectAttributes redirect){try{runs.approve(id,evidence,actor);redirect.addFlashAttribute("runSuccess","report.run.updated");}catch(IllegalArgumentException ex){redirect.addFlashAttribute("runError",key(ex));}return "redirect:/reports/runs/"+id;}
 @GetMapping("/{id}/artifacts/{artifact}") @PreAuthorize("@access.has(principal,'REPORT_RUN') and @access.has(principal,'REPORT_EXPORT')")
 public ResponseEntity<byte[]> download(@PathVariable UUID id,@PathVariable UUID artifact,@AuthenticationPrincipal AppUserPrincipal actor){var result=runs.download(id,artifact,actor);MediaType media=switch(result.format()){case CSV->MediaType.parseMediaType("text/csv;charset=UTF-8");case XLSX->MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");case PDF->MediaType.APPLICATION_PDF;};return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(media).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=report."+result.format().name().toLowerCase(Locale.ROOT)).header("X-Content-Type-Options","nosniff").header("X-Report-SHA256",result.checksum()).body(result.bytes());}
 private static String key(IllegalArgumentException ex){return ex.getMessage()!=null&&ex.getMessage().startsWith("report.run.error.")?ex.getMessage():"report.run.error.invalid";}
}
