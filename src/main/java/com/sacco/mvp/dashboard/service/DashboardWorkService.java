package com.sacco.mvp.dashboard.service;

import com.sacco.mvp.dashboard.repository.DashboardWorkRepository;
import com.sacco.mvp.dashboard.repository.DashboardWorkRepository.Bucket;
import com.sacco.mvp.accounting.approval.service.VoucherWorkflowService;
import com.sacco.mvp.accounting.service.VoucherService;
import com.sacco.mvp.accounting.dto.VoucherDtos.Type;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonFormat;

@Service @RequiredArgsConstructor
public class DashboardWorkService {
    private final DashboardWorkRepository repository;
    private final AccessControlService access;
    private final VoucherWorkflowService vouchers;
    private final VoucherService voucherAccess;
    private final ApplicationClock clock;
    public record Group(String labelKey, long count, @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount, String href) {}
    public record Infolet(String id, String labelKey, String tone, long count, @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount, Long oldestDays, String href, List<Group> groups) {}
    public record Work(List<Infolet> infolets, String capturedAt) {}

    @Transactional(readOnly=true)
    public Work read(AppUserPrincipal actor, int days, BigDecimal minimum) {
        if (actor == null || actor.getSaccoId() == null || actor.getStationId() == null || actor.getStationId().isBlank()
            || !(access.staffDashboardAccess(actor)
                || (actor.isStaffSession() && access.has(actor,"VOUCHER_WORKSPACE_VIEW")))) throw new AccessDeniedException("Dashboard unavailable");
        if (days < 0 || days > 3650 || minimum == null || minimum.signum() < 0 || minimum.scale() > 2 || minimum.scale() < -16 || minimum.precision() - minimum.scale() > 16)
            throw new IllegalArgumentException("Invalid display filters");
        var before=clock.now().minusDays(days);
        var cards=new ArrayList<Infolet>();
        if (actor.isStaffSession() && !access.isPlatformIdentity(actor)) {
            if (access.has(actor,"VOUCHER_WORKSPACE_VIEW")) {
                vouchers.authorize(actor,"VOUCHER_WORKSPACE_VIEW");
                var types=Arrays.stream(Type.values()).filter(t->access.has(actor,t==Type.JOURNAL?"ACCOUNTING_JOURNALS_VIEW":"ACCOUNTING_BUSINESS_VIEW")).toList();
                for (var type:types) voucherAccess.authorize(actor,type,"VIEW");
                var rows=repository.vouchers(actor,types.stream().map(Enum::name).toList(),before,minimum);
                for (var category:List.of("INCOMPLETE","NOT_VALIDATED","ERROR","REQUIRED","INITIATED","READY","FAILED","REJECTED")) {
                    String href="/finance/approval?category="+category;
                    var groups=rows.stream().filter(r->r.category().equals(category)).map(r->new Group("voucher.title."+r.group(),r.count(),r.amount(),href+"&type="+r.group())).toList();
                    cards.add(card("voucher-"+category,"dashboard.work.voucher."+category,Set.of("ERROR","FAILED").contains(category)?"error":"neutral",rows.stream().filter(r->r.category().equals(category)).toList(),href,groups));
                }
            }
            queue(cards,actor,access.has(actor,"LOAN_RECORDING_VIEW"),"loan-groups","SUBMITTED","/finance/loan-records?state=SUBMITTED",before,minimum);
            queue(cards,actor,access.canAccessDisbursementArea(actor),"disbursement","READY_FOR_DISBURSEMENT","/disbursement/loan-applications?filter=READY_FOR_DISBURSEMENT",before,minimum);
        }
        return new Work(List.copyOf(cards),clock.now().toString());
    }
    private void queue(List<Infolet> cards,AppUserPrincipal actor,boolean permitted,String role,String status,String href,java.time.OffsetDateTime before,BigDecimal minimum) {
        if (!permitted) return;
        var rows=repository.loans(actor,List.of(status),before,minimum);
        cards.add(card("queue-"+role,"dashboard.work.queue."+role,"neutral",rows,href,List.of(new Group("loan.status."+status,rows.stream().mapToLong(Bucket::count).sum(),rows.stream().map(Bucket::amount).reduce(BigDecimal.ZERO,BigDecimal::add),href))));
    }
    private Infolet card(String id,String label,String tone,List<Bucket> rows,String href,List<Group> groups) {
        var oldest=rows.stream().map(Bucket::oldest).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal amount=rows.stream().anyMatch(r->r.amount()==null)?null:rows.stream().map(Bucket::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Infolet(id,label,tone,rows.stream().mapToLong(Bucket::count).sum(),amount,oldest==null?null:Math.max(0,ChronoUnit.DAYS.between(oldest,clock.now())),href,groups);
    }
}
