package com.sacco.mvp.branches.controller;

import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.management.dto.ManagementReports.Kind;
import com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters;
import com.sacco.mvp.accounting.management.service.ManagementReportService;
import com.sacco.mvp.accounting.statements.dto.StatementModels;
import com.sacco.mvp.accounting.statements.service.StatementPresentation;
import com.sacco.mvp.accounting.statements.service.StatementService;
import com.sacco.mvp.branches.service.BranchService;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Controller
@RequestMapping("/staff/branches")
@RequiredArgsConstructor
public class BranchOversightController {
    private static final List<StatementModels.Kind> STATEMENTS=List.of(StatementModels.Kind.POSITION,StatementModels.Kind.INCOME,
        StatementModels.Kind.CASH_FLOW,StatementModels.Kind.EQUITY,StatementModels.Kind.NOTES,StatementModels.Kind.TRIAL);
    private final BranchService branches;
    private final OnboardingService clients;
    private final LoanRecordingService loans;
    private final ManagementReportService reports;
    private final StatementService statements;
    private final StatementPresentation presentation;
    private final ApplicationClock clock;
    private final org.springframework.context.MessageSource messages;

    @ModelAttribute void noCache(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }

    @GetMapping
    public String overview(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam(required=false) String branch,Model model) {
        var scope=context(actor,branch,model);
        model.addAttribute("branchChoices",branches.visibleBranches(scope));
        return "staff/branches";
    }

    @GetMapping("/{section:clients|loans}")
    public String register(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable String section,@RequestParam String branch,
                           @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="number_asc") String sort,
                           @RequestParam(defaultValue="0") int page,Model model) {
        var scope=context(actor,branch,model);
        if(section.equals("clients")) {
            var result=clients.branchClients(scope,search,page,sort);
            model.addAttribute("rows",result.rows());
            model.addAttribute("hasNext",result.hasNext());
        } else {
            var result=loans.branchLoans(scope,search,sort,page);
            model.addAttribute("rows",result.rows());
            model.addAttribute("hasNext",result.hasNext());
        }
        model.addAttribute("search",search);
        model.addAttribute("sort",sort);
        model.addAttribute("page",page);
        model.addAttribute("section",section);
        return "staff/branch-records";
    }

    @GetMapping("/{section:clients|loans}/{id}")
    public String detail(@AuthenticationPrincipal AppUserPrincipal actor,@PathVariable String section,@PathVariable UUID id,
                         @RequestParam String branch,Model model) {
        var scope=context(actor,branch,model);
        model.addAttribute("record",section.equals("clients")?clients.branchClient(scope,id):loans.branchLoan(scope,id));
        model.addAttribute("section",section);
        return "staff/branch-record";
    }

    @GetMapping("/reports")
    public String reports(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String branch,@RequestParam(required=false) Kind kind,
                          @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate through,
                          @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime cutoff,
                          @RequestParam(defaultValue="0") int page,Locale locale,Model model) {
        var scope=context(actor,branch,model);
        scope.require(UserClaim.FINANCIAL_REPORTS_VIEW);
        model.addAttribute("reportKinds",Arrays.stream(Kind.values()).filter(k -> scope.permits(ManagementReportService.viewClaim(k))).toList());
        LocalDate today=clock.today();
        LocalDate start=from==null?today.withDayOfMonth(1):from;
        LocalDate end=through==null?today:through;
        model.addAttribute("from",start);
        model.addAttribute("through",end);
        model.addAttribute("kind",kind);
        if(kind!=null) {
            var report=reports.branchReport(scope,kind,new Parameters(start,end,cutoff==null?clock.now():cutoff,null,null,null,"",page));
            model.addAttribute("report",report);
            if(report.isReferenceLayout()) {
                java.util.function.Function<String,String> label=key -> messages.getMessage("management."+key,null,key,locale);
                model.addAttribute("referencePeriod",com.sacco.mvp.accounting.management.service.LoanReportReferenceText.documentPeriod(kind,report.parameters(),locale,label));
                model.addAttribute("referenceStamp",com.sacco.mvp.accounting.management.service.LoanReportReferenceText.printed(kind,clock.now(),locale,label));
                model.addAttribute("referenceDetails",com.sacco.mvp.accounting.management.service.LoanReportReferenceText.headerDetails(report,locale,label));
            }
        }
        return "staff/branch-reports";
    }

    @GetMapping("/statements")
    public String statementList(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String branch,
                                @RequestParam(defaultValue="0") int page,Model model) {
        var rows=statements.branchSaved(context(actor,branch,model),page);
        model.addAttribute("runs",rows.stream().limit(30).toList());
        model.addAttribute("hasNext",rows.size()>30);
        model.addAttribute("page",page);
        return "staff/branch-statements";
    }

    @GetMapping("/statements/{id}")
    public String statement(@AuthenticationPrincipal AppUserPrincipal actor,@RequestParam String branch,@PathVariable UUID id,
                            @RequestParam(defaultValue="POSITION") StatementModels.Kind kind,Locale locale,Model model) {
        if(!STATEMENTS.contains(kind)) throw new IllegalArgumentException("branches.error.filter");
        var run=statements.branchSaved(context(actor,branch,model),id);
        model.addAttribute("run",run);
        model.addAttribute("kind",kind);
        model.addAttribute("kinds",STATEMENTS);
        model.addAttribute("document",presentation.document(run.snapshot(),kind,run.snapshot().balances(),locale));
        return "staff/branch-statement";
    }

    private BranchService.ReadScope context(AppUserPrincipal actor,String branch,Model model) {
        var scope=branches.oversight(actor,branch);
        model.addAttribute("branch",scope.branch());
        model.addAttribute("showClients",scope.permits(UserClaim.CLIENT_ONBOARDING_VIEW));
        model.addAttribute("showLoans",scope.permits(UserClaim.LOAN_RECORDING_VIEW));
        boolean financial=scope.permits(UserClaim.FINANCIAL_REPORTS_VIEW);
        model.addAttribute("showFinancial",financial);
        model.addAttribute("showReports",financial && Arrays.stream(Kind.values()).anyMatch(k -> scope.permits(ManagementReportService.viewClaim(k))));
        return scope;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalid(IllegalArgumentException invalid,Model model,HttpServletResponse response) {
        response.setStatus(400);
        model.addAttribute("branchError","branches.error.filter");
        return "staff/branches";
    }
}
