package com.sacco.mvp.staffoffice.service;

import com.sacco.mvp.accounting.approval.service.VoucherWorkflowService;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Filter;
import com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.AccountOfficeWorkService;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.lending.controls.service.CreditControlService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.staffoffice.dto.OfficeWork.*;
import com.sacco.mvp.staffoffice.model.StaffOffice;
import com.sacco.mvp.staffoffice.dto.PersonalDashboard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class StaffOfficeService {
    private final MemberDirectoryService members;
    private final UserClaimService claims;
    private final SaccoRegistryService institutions;
    private final AccessControlService access;
    private final LoanGroupApprovalService reviews;
    private final LoanRecordingService loans;
    private final VoucherWorkflowService vouchers;
    private final AccountOfficeWorkService accounts;
    private final OnboardingService clients;
    private final CreditControlService credit;
    private final AdminOfficeWorkService administration;
    private final AdminService admin;

    public record View(StaffOffice office,String section,List<Source> sources,Source source,Page work,List<Report> reports) {}

    /** Validate the live assignment even for staff with an explicitly empty permission set. */
    private AppUserPrincipal authorize(AppUserPrincipal actor) {
        if(!access.staffDashboardAccess(actor))
            throw new AccessDeniedException("Office unavailable");
        var current=members.find(actor.getMemberId()).filter(Member::isStaffAccessActive)
            .filter(m->m.getStatus()==MemberStatus.ACTIVE && Objects.equals(m.getSaccoId(),actor.getSaccoId())
                && Objects.equals(m.getStationId(),actor.getStationId())
                && StaffOffice.assigned(m.getActiveStaffRolesResolved()).equals(StaffOffice.assigned(actor.getStaffRoles())))
            .orElseThrow(()->new AccessDeniedException("Staff assignment changed; sign in again"));
        if(institutions.findActiveSacco(actor.getSaccoId()).isEmpty()
            || institutions.findStation(actor.getSaccoId(),actor.getStationId()).filter(SaccoStation::isActive)
                .filter(s->s.getAccessStatus()==SaccoAccessStatus.ACTIVE).isEmpty())
            throw new AccessDeniedException("Assigned office unavailable");
        var live=EnumSet.noneOf(UserClaim.class);
        claims.effectiveClaims(current.getId(),current.getActiveStaffRolesResolved(),false).stream()
            .filter(c->actor.getClaims().contains(c.name())).forEach(live::add);
        return new AppUserPrincipal(current,live);
    }

    @Transactional(readOnly=true)
    public void requireOffice(AppUserPrincipal principal,String slug) {
        var actor=authorize(principal);
        if(StaffOffice.fromSlug(slug).filter(o->StaffOffice.assigned(actor.getStaffRoles()).contains(o)).isEmpty())
            throw new AccessDeniedException("Office unavailable");
    }

    @Transactional(readOnly=true)
    public PersonalDashboard.View dashboard(AppUserPrincipal principal) {
        var actor=authorize(principal);
        var overview=access.canAccessAdminArea(actor)&&has(actor,"ADMIN_DASHBOARD_VIEW")
            ?admin.dashboard(actor.getSaccoId(),actor.getStationId(),actor.getMemberId()):null;
        return new PersonalDashboard.View(activities(actor),overview,
            actor.getMemberId()+"|"+actor.getSaccoId()+"|"+actor.getStationId()+"|personal");
    }

    /** Role-specific review counts remain separate; shared work is read only once. */
    @Transactional(readOnly=true)
    public PersonalDashboard.Summary activity(AppUserPrincipal principal,String source) {
        var actor=authorize(principal);
        var definition=activities(actor).stream().filter(s->s.key().equals(source)).findFirst()
            .orElseThrow(()->new AccessDeniedException("Activity unavailable"));
        var counts=new ArrayList<PersonalDashboard.Count>();
        for(var destination:definition.destinations()) {
            var office=StaffOffice.fromSlug(destination.office()).orElseThrow();
            var first=work(actor,office,source,"","number",0);
            counts.add(new PersonalDashboard.Count(destination.office(),first.rows().size(),first.hasNext()));
        }
        return new PersonalDashboard.Summary(source,List.copyOf(counts));
    }

    private List<PersonalDashboard.Activity> activities(AppUserPrincipal actor) {
        var definitions=new LinkedHashMap<String,List<PersonalDashboard.Destination>>();
        var reviewGroups=reviewGroups(actor);
        for(var office:StaffOffice.assigned(actor.getStaffRoles()))for(var source:sources(actor,office,reviewGroups)) {
            var destinations=definitions.computeIfAbsent(source.key(),key->new ArrayList<>());
            if(destinations.isEmpty()||Set.of("loan-reviews","voucher-reviews").contains(source.key()))
                destinations.add(new PersonalDashboard.Destination(office.getSlug(),office.getLabelKey(),
                    office.getBase()+"/queue?source="+source.key()));
        }
        var priority=List.of("loan-reviews","voucher-reviews","account-reviews");
        return definitions.entrySet().stream()
            .sorted(Comparator.comparingInt(e->{int n=priority.indexOf(e.getKey());return n<0?priority.size():n;}))
            .map(e->new PersonalDashboard.Activity(e.getKey(),"office.source."+e.getKey(),List.copyOf(e.getValue()))).toList();
    }

    @Transactional(readOnly=true)
    public View read(AppUserPrincipal principal,String slug,String section,String source,String search,String sort,int page) {
        var office=StaffOffice.fromSlug(slug).orElseThrow(()->new AccessDeniedException("Office unavailable"));
        var actor=authorize(principal);
        if(!StaffOffice.assigned(actor.getStaffRoles()).contains(office))throw new AccessDeniedException("Office unavailable");
        if(!Set.of("queue","reports").contains(section) || search==null || search.length()>100
            || page<0 || page>10000 || !Set.of("","number","number_desc","newest","oldest").contains(sort))
            throw new IllegalArgumentException("office.invalidFilter");
        var sources=sources(actor,office,reviewGroups(actor));
        Source selected=source==null||source.isBlank()?sources.stream().findFirst().orElse(null)
            :sources.stream().filter(s->s.key().equals(source)).findFirst().orElseThrow(()->new AccessDeniedException("Queue unavailable"));
        if(selected!=null && !selected.chronological() && !sort.isBlank() && !Set.of("number","number_desc").contains(sort))
            throw new IllegalArgumentException("office.invalidFilter");
        var work=section.equals("queue")&&selected!=null?work(actor,office,selected.key(),search,sort.isBlank()?"number":sort,page):Page.empty(page);
        if(section.equals("queue")&&selected!=null) {
            var navigation=new Navigation(office,selected.key(),search,sort.isBlank()?"number":sort,page);
            work=new Page(work.rows().stream().map(t->new Task(t.number(),t.name(),t.stateKey(),t.amount(),t.href()==null?null:navigation.attach(t.href()))).toList(),page,work.hasNext());
        }
        var reportNavigation=new Navigation(office,"reports","","number",0);
        var catalogue=reports(actor,office).stream().map(r->new Report(r.labelKey(),reportNavigation.attach(r.href()))).toList();
        return new View(office,section,sources,selected,work,catalogue);
    }

    private Set<Position> reviewGroups(AppUserPrincipal a) {
        if(!has(a,"LOAN_RECORDING_VIEW"))return Set.of();
        if(hasAny(a,"LOAN_RECORDING_APPROVE","LOAN_RECORDING_REJECT"))
            return StaffOffice.assigned(a.getStaffRoles()).stream().map(StaffOffice::getGroup).collect(java.util.stream.Collectors.toSet());
        return reviews.assignedReviewGroups(a);
    }
    private List<Source> sources(AppUserPrincipal a,StaffOffice office,Set<Position> reviewGroups) {
        var result=new ArrayList<Source>();
        if(reviewGroups.contains(office.getGroup()))add(result,"loan-reviews",true);
        if(has(a,"VOUCHER_WORKSPACE_VIEW") && hasAny(a,"ACCOUNTING_BUSINESS_VIEW","ACCOUNTING_JOURNALS_VIEW")) {
            add(result,"voucher-reviews",true);add(result,"vouchers",true);
        }
        if(has(a,"ACCOUNTING_ACCOUNTS_VIEW")) {
            if(has(a,"ACCOUNTING_ACCOUNT_APPROVAL_VIEW")&&hasAny(a,"ACCOUNTING_ACCOUNT_APPROVAL_APPROVE","ACCOUNTING_ACCOUNT_APPROVAL_REJECT"))add(result,"account-reviews",true);
            add(result,"accounts",true);
        }
        if(has(a,"CLIENT_ONBOARDING_VIEW"))add(result,"clients",false);
        if(has(a,"LOAN_RECORDING_VIEW"))add(result,"loans",true);
        if(hasAny(a,"DISBURSEMENT_QUEUE_VIEW","DISBURSEMENT_QUEUE_DISBURSE"))add(result,"funding",true);
        if(has(a,"COLLECTIONS_VIEW"))add(result,"collections",false);
        if(has(a,"COLLATERAL_VIEW"))add(result,"collateral",false);
        if(has(a,"USER_ACCESS_VIEW"))add(result,"staff",true);
        if(has(a,"SUPPORT_VIEW"))add(result,"support",true);
        var priority=switch(office) {
            case ICT_MANAGER->List.of("staff","support");
            case CUSTOMER_CARE->List.of("clients","support");
            case CHIEF_ACCOUNTANT,FINANCE_MANAGER,ACCOUNTS_CLERK,ACCOUNTS_CLERK_2->List.of("voucher-reviews","account-reviews","vouchers","accounts","funding");
            default->List.of("loan-reviews","loans","collections");
        };
        result.sort(Comparator.comparingInt(s->{if(s.key().equals("loan-reviews"))return -1;int n=priority.indexOf(s.key());return n<0?priority.size():n;}));
        return List.copyOf(result);
    }
    private static void add(List<Source> list,String key,boolean chronological) {list.add(new Source(key,"office.source."+key,chronological));}
    private static boolean has(AppUserPrincipal a,String claim) {return a.getClaims().contains(claim);}
    private static boolean hasAny(AppUserPrincipal a,String... values) {return Arrays.stream(values).anyMatch(c->has(a,c));}

    private Page work(AppUserPrincipal a,StaffOffice office,String source,String search,String sort,int page) {
        return switch(source) {
            case "loan-reviews"->reviews.officeReviews(a,office.getGroup(),search,sort,page);
            case "voucher-reviews","vouchers"->vouchers.officeWork(a,office,source.equals("voucher-reviews"),search,sort,page);
            case "account-reviews","accounts"->accounts.read(a,source.equals("account-reviews"),search,sort,page);
            case "staff","support"->administration.read(a,source.equals("staff"),search,sort,page);
            case "clients"->{var p=clients.clients(a,search,"",page,sort.equals("number_desc")?"number_desc":"number_asc");yield new Page(p.rows().stream().map(r->new Task(r.number(),r.name(),"kyc.state."+r.state(),null,"/finance/clients/onboarding/"+r.id())).toList(),page,p.hasNext());}
            case "loans"->{
                var p=loans.list(a,new Filter(search,null,null,"",sort.equals("number")?"number_asc":sort,page,null));
                var intake=loans.intakeIds(a,p.rows().stream().map(r->r.id()).toList());
                yield new Page(p.rows().stream().map(r->new Task(r.number(),r.client(),"recording.status."+r.status(),r.principal(),
                    "/finance/loan-records/"+(intake.contains(r.id())?"applications/":"")+r.id())).toList(),page,p.hasNext());
            }
            case "funding"->loans.officeFunding(a,search,sort,page);
            case "collections"->{var p=credit.cases(a,search,page,sort.equals("number_desc")?"loan_desc":"loan_asc");yield new Page(p.rows().stream().map(r->new Task(r.loanNumber(),r.borrower(),"credit.state."+r.state(),r.arrears(),"/finance/credit/collections/"+r.id())).toList(),page,p.hasNext());}
            case "collateral"->{var p=credit.assets(a,search,page,sort.equals("number_desc")?"code_desc":"code_asc");yield new Page(p.rows().stream().map(r->new Task(r.code(),r.description(),"office.recorded",r.value(),"/finance/credit/collateral/"+r.id())).toList(),page,p.hasNext());}
            default->throw new AccessDeniedException("Queue unavailable");
        };
    }

    private List<Report> reports(AppUserPrincipal a,StaffOffice office) {
        var result=new ArrayList<Report>();
        if(has(a,"STAFF_ANALYTICS_VIEW"))result.add(new Report("menu.staffReportsAnalytics","/staff/analytics"));
        if(has(a,"FINANCIAL_REPORTS_VIEW")) {
            if(has(a,"ACCOUNTING_BUSINESS_VIEW"))report(result,"DAILY_COLLECTIONS");
            if(has(a,"ACCOUNTING_JOURNALS_VIEW"))report(result,"CASHBOOK");
            if(has(a,"LOAN_REPORTS_VIEW")) {
                for(var kind:List.of("LOAN_PORTFOLIO","PORTFOLIO_RISK","PORTFOLIO_ANALYSIS","DISBURSED_LOANS_AUDIT","EXPECTED_REPAYMENTS","LOAN_REPAYMENT_TRANSACTIONS"))report(result,kind);
                if(has(a,"ACCOUNTING_RECONCILIATION_VIEW"))result.add(new Report("reconciliation.title","/reports/financial/sl-gl-reconciliation"));
            }
            result.add(new Report("fs.workspace","/reports/financial/statements"));
            result.add(new Report("fs.history","/reports/financial/statements/history"));
        }
        if(hasAny(a,"DISBURSEMENT_QUEUE_VIEW","DISBURSEMENT_QUEUE_DISBURSE")&&has(a,"DISBURSEMENT_QUEUE_EXPORT"))
            result.add(new Report("menu.disbursementReports","/disbursement/reports"));
        // Presentation order only; all access checks remain at the existing report endpoint.
        if(office==StaffOffice.FINANCE_MANAGER||office==StaffOffice.CHIEF_ACCOUNTANT)
            result.sort(Comparator.comparingInt(r->r.href().startsWith("/reports/financial/statements")?0:1));
        return List.copyOf(result);
    }
    private static void report(List<Report> list,String kind) {list.add(new Report("management.nav."+kind,"/reports/financial/operations/"+kind.toLowerCase(Locale.ROOT).replace('_','-')));}
}
