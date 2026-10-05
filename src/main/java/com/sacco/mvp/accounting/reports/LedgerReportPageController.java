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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports/financial")
public class LedgerReportPageController {
    private final LedgerReportService reports;
    private final ApplicationClock clock;

    @GetMapping({"", "/accounts/{accountId}"})
    public String report(@AuthenticationPrincipal AppUserPrincipal actor,
            @PathVariable(required=false) UUID accountId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime recordedThrough,
            @RequestParam(defaultValue="false") boolean institutionWide,
            @RequestParam(defaultValue="0") int page, Model model) {
        // Authorization precedes recoverable policy/date errors and any report data.
        var scope=reports.authorize(actor,institutionWide);
        var cutoff=recordedThrough==null?clock.now().truncatedTo(ChronoUnit.SECONDS)
            :recordedThrough.atZone(clock.zoneId()).toOffsetDateTime();
        var parameters=new LedgerReportService.Parameters(from==null?clock.today().withDayOfMonth(1):from,
            through==null?clock.today():through,cutoff,page);
        model.addAttribute("scope",scope);model.addAttribute("parameters",parameters);
        model.addAttribute("cutoffInput",clock.zoned(cutoff).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        model.addAttribute("reportZone",clock.zoneId().toString());
        model.addAttribute("activityMode",accountId!=null);
        model.addAttribute("reportRoute",accountId==null?"/reports/financial":"/reports/financial/accounts/"+accountId);
        try {
            if(accountId==null) {
                var result=reports.trialBalance(actor,parameters,institutionWide);
                model.addAttribute("trial",result);model.addAttribute("coverage",result.coverage());
                model.addAttribute("hasNext",result.hasNext());
            } else {
                var result=reports.accountActivity(actor,accountId,parameters,institutionWide);
                model.addAttribute("activity",result);model.addAttribute("coverage",result.coverage());
                model.addAttribute("hasNext",result.hasNext());
            }
        } catch(IllegalArgumentException failure) {
            String key=failure.getMessage();
            model.addAttribute("reportError",key!=null && key.startsWith("financial.report.error.")
                ?key:"financial.report.error.policy");
        }
        return "reporting/financial-ledger";
    }
}
