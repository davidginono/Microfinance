package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
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
    @GetMapping("/configure")
    public String configure(@AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(required=false) String from,@RequestParam(required=false) String through,
            @RequestParam(required=false) String recordedThrough,
            @RequestParam(defaultValue="false") boolean institutionWide,Model model) {
        model.addAttribute("scope",reports.configurationScope(actor,institutionWide));
        model.addAttribute("fromInput",from==null?clock.today().withDayOfMonth(1).toString():from);
        model.addAttribute("throughInput",through==null?clock.today().toString():through);
        model.addAttribute("cutoffInput",recordedThrough==null?clock.zoned(clock.now()).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS).toString():recordedThrough);
        model.addAttribute("reportZone",clock.zoneId().toString());
        model.addAttribute("todayInput",clock.today().toString());
        return "reporting/loan-portfolio-configure";
    }
    @GetMapping
    public String report(@AuthenticationPrincipal AppUserPrincipal actor,
            @RequestParam(required=false) String from,@RequestParam(required=false) String through,
            @RequestParam(required=false) String recordedThrough,
            @RequestParam(defaultValue="false") boolean institutionWide,@RequestParam(defaultValue="0") int page,
            Model model,jakarta.servlet.http.HttpServletResponse response,java.util.Locale locale) {
        try {
            var cutoff=recordedThrough==null?clock.now().truncatedTo(ChronoUnit.SECONDS):LocalDateTime.parse(recordedThrough).atZone(clock.zoneId()).toOffsetDateTime();
            var p=new LoanPortfolioReportService.Parameters(from==null?clock.today().withDayOfMonth(1):LocalDate.parse(from),
                through==null?clock.today():LocalDate.parse(through),cutoff,page);
            var report=reports.report(actor,p,institutionWide);model.addAttribute("report",report);
            model.addAttribute("cutoffInput",clock.zoned(cutoff).toLocalDateTime().toString());model.addAttribute("reportZone",clock.zoneId().toString());
            return "reporting/loan-portfolio";
        } catch(DateTimeException | IllegalArgumentException failure) {
            String key=failure.getMessage();if(key==null || !key.startsWith("financial."))key="financial.report.error.dates";
            response.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST.value());response.setHeader("Cache-Control","no-store");
            model.addAttribute("formError",messages.getMessage(key,null,locale));
            return configure(actor,from,through,recordedThrough,institutionWide,model);
        }
    }
}
