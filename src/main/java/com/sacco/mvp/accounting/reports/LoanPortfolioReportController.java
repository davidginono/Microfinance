package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports/financial/portfolio")
public class LoanPortfolioReportController {
    private final LoanPortfolioReportService reports;
    private final ApplicationClock clock;
    private final org.springframework.context.MessageSource messages;
    @GetMapping
    public String report(@AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedThrough,
            @RequestParam(defaultValue="false") boolean institutionWide,@RequestParam(defaultValue="0") int page,Model model) {
        var cutoff=recordedThrough==null?clock.now().truncatedTo(ChronoUnit.SECONDS):recordedThrough.atZone(clock.zoneId()).toOffsetDateTime();
        var p=new LedgerReportService.Parameters(from==null?clock.today().withDayOfMonth(1):from,through==null?clock.today():through,cutoff,page);
        var report=reports.report(actor,p,institutionWide);model.addAttribute("report",report);
        model.addAttribute("cutoffInput",clock.zoned(cutoff).toLocalDateTime().toString());model.addAttribute("reportZone",clock.zoneId().toString());
        return "reporting/loan-portfolio";
    }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public org.springframework.http.ResponseEntity<String> invalid(IllegalArgumentException failure,java.util.Locale locale) {
        String key=failure.getMessage();if(key==null || !key.startsWith("financial."))key="financial.report.error.dates";
        return org.springframework.http.ResponseEntity.badRequest().cacheControl(org.springframework.http.CacheControl.noStore())
            .contentType(org.springframework.http.MediaType.TEXT_PLAIN).body(messages.getMessage(key,null,locale));
    }
}
